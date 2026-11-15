## Context

A premissa principal da proposta anterior mudou. A entrega `media-file-upload` já substituiu `photo_url` e `file_url` por FKs privadas para `media_file`, implementou `StorageService`/`LocalStorageService` e rotas por dono para upload/download autenticado. Portanto, não há motivo nem espaço seguro para criar outra tabela de arquivo, URL pública ou fluxo de URL pré-assinada neste change.

A entrega `admin-driver-approval` também já existe. Ela usa `DriverApprovalStatus`, o onboarding, `DriverNotificationService` e `ClientDriverService` para bloquear propostas de contas não aprovadas. Ainda faltam a validade contínua, a revisão desencadeada por alteração documental e uma fonte comum para busca/propostas. `driver_document` tem `PENDING`, `APPROVED` e `REJECTED`; CNH é validada pela decisão de onboarding, sem status próprio.

## Goals / Non-Goals

**Goals:**

- Criar uma fonte de verdade de regularidade documental, com estado e motivos disponíveis ao motorista, Admin e regras de elegibilidade.
- Recalcular esse estado após cada alteração documental e diariamente para que o tempo também altere a elegibilidade.
- Manter a CNH no fluxo já entregue de aprovação de conta e usar o status existente do `driver_document` para revisão individual.
- Alertar cada marco de validade exatamente uma vez e nunca alterar vínculos `client_driver` já ativos.

**Non-Goals:**

- Criar bucket MinIO/S3, URLs pré-assinadas, novos endpoints genéricos de mídia ou migrar os arquivos da VPS; isso contraria o contrato ativo de `media-storage` e requer change próprio de provider.
- Construir a tela Admin, tokens/dispositivos FCM, OCR, antivírus ou cancelar vínculos/contratos ativos.
- Duplicar a regra de bloqueio por plano; a resposta expõe categorias separadas para que o consumidor apresente a causa correta.

## Decisions

### D1 — Estado materializado, calculado por uma única política

Adicionar `document_compliance_status`, `document_compliance_reason` e `document_compliance_updated_at` ao motorista. `DriverDocumentCompliancePolicy` é pura e recebe CNH, documentos obrigatórios e data de negócio; `DriverDocumentComplianceService` é o único gravador do estado materializado e do DTO de consulta.

O estado materializado permite que todas as consultas paginadas existentes (`findSearchableByIds`, `findSearchableNewestFirst` e perfil público) filtrem no banco sem recalcular regras ou estragar a contagem/paginação. Alterações de CNH, documento, arquivo e revisão chamam a atualização na mesma transação; o job diário cobre a transição causada pelo tempo. Isso é preferível a repetir subqueries de regularidade em cada busca.

### D2 — Revalidação aproveita o fluxo de onboarding entregue

CNH não receberá um segundo status de revisão. Ao substituir seus dados ou foto, ou alterar um documento obrigatório, o sistema marca o documento como `PENDING` quando aplicável, recalcula a regularidade e move uma conta previamente `APPROVED` para `UNDER_REVIEW`. Esse é o estado que #47 já lista e que os endpoints Admin já aprovam/rejeitam; não há reenvio de onboarding nem novo endpoint de aprovação.

Na aprovação, `DriverOnboardingService` deve recusar a decisão se a política reportar CNH/documento obrigatório ausente, vencido, pendente ou rejeitado. A aprovação de um onboarding legado é a evidência histórica para backfill: documentos obrigatórios anexados e válidos de motoristas já `APPROVED` serão migrados para `APPROVED`, sem suspender a base inteira no deploy. Nova rejeição de documento exige justificativa não vazia; a justificativa permanece no DTO proprietário. A rejeição da conta continua usando `DriverRejectionRequestDTO` e a notificação já entregue.

### D3 — Escopo documental e data de negócio

A política usa a mesma lista de documentos obrigatórios do onboarding (`CRLV`, `VEHICLE_INSPECTION`, `MUNICIPAL_AUTHORIZATION`) e exige CNH ativa, anexada e válida. Para cada tipo obrigatório, exige arquivo privado anexado, status `APPROVED` e validade não anterior à data `America/Sao_Paulo`; documentos opcionais podem receber alertas, mas não suspendem a conta sem uma regra de produto adicional.

No dia D0 o documento ainda é válido e recebe o alerta de vencimento; torna-se irregular em D+1. Essa fronteira é controlada por `Clock` injetável para não depender do fuso do host.

### D4 — Eventos de alerta, não um único timestamp

`driver_document.notified_at` não representa três marcos nem cobre CNH. A migration cria `driver_document_expiry_alert` com recurso, marco (`DAYS_60`, `DAYS_30`, `EXPIRATION`) e `notified_at`, com unicidade por `(resource_type, resource_id, milestone)`. A CNH ganha apenas `notified_at` como último aviso compatível; para documentos, o campo existente recebe o último envio.

O job seleciona CNH e documentos ativos com data de validade, cria o evento de forma transacional e trata violação de unicidade como execução já concluída. Se uma execução diária falhar, marcos anteriores ainda não registrados são enviados uma vez na próxima execução. A entrega usa uma porta: o adaptador pode reutilizar e-mail transacional agora, enquanto FCM permanece uma dependência explícita e não é simulado por chamada de rede nos testes.

### D5 — Integrações de elegibilidade sem tocar em vínculos ativos

As consultas de busca e perfil público passam a exigir `document_compliance_status = REGULAR`, além de `active` e `approval_status = APPROVED`. `ClientDriverService.create` e a transição para `ACTIVE` consultam a mesma regularidade e retornam motivo documental quando ela é a causa. Nenhuma rotina altera `client_driver` existente: o bloqueio vale somente para criação e ativação futuras.

O endpoint de regularidade retorna o estado, motivo e data de cálculo. Ele recebe, quando aplicável, os bloqueios de plano em uma categoria distinta fornecida pelo módulo de plano; enquanto esse módulo não estiver no backend, a categoria de plano é explicitamente vazia, e não inferida.

## Risks / Trade-offs

- [Backfill marca documentos indevidamente] → limitar a migração a motoristas já aprovados, com arquivo e validade válidos, e auditar os totais antes/depois em staging.
- [Job perdido ou concorrente] → ledger único no banco, reprocessamento de marcos pendentes e `Clock` fixo nos testes.
- [Busca vaza motorista irregular] → aplicar o estado materializado a todas as quatro consultas de elegibilidade, com testes de recomendação, geobusca e perfil público.
- [FCM ainda indisponível] → manter o evento persistido e usar somente adaptador aprovado; não declarar push entregue antes de existir configuração/credencial de push.
- [Estado materializado fica obsoleto] → toda escrita documental chama refresh na própria transação e o job recalcula diariamente.

## Migration Plan

1. Criar migration posterior à V51 para estado materializado, último aviso da CNH, ledger de alertas e índices; nunca editar V17, V21 ou V41–V48.
2. Executar e auditar o backfill de documentos de motoristas aprovados antes de ativar o filtro de elegibilidade.
3. Publicar refresh, endpoints e filtros de busca/proposta; habilitar o job diário após a migration.
4. Configurar o adaptador de notificação aprovado e observar alertas, supressões e erros de entrega. A migração de storage para S3/MinIO continua change separado.
5. Rollback desabilita job/filtros por configuração/código; schema e ledger permanecem aditivos e não requerem rollback destrutivo.

## Open Questions

- O canal inicial será e-mail transacional, evento persistido sem envio, ou FCM já disponível? O requisito de push não pode ser concluído sem essa resposta.
- Documentos opcionais vencidos devem bloquear ou apenas alertar? Esta proposta bloqueia somente CNH e os tipos obrigatórios já usados pelo onboarding.
- O módulo de plano já expõe seus motivos de bloqueio? Sem ele, a categoria de plano do endpoint permanece vazia.
