## Why

As entregas de upload de mídia e aprovação administrativa agora existem, mas não há um estado que represente a validade contínua da documentação. Um motorista aprovado pode ter a CNH, vistoria ou outro documento obrigatório vencido e continuar aparecendo na busca, criando novos vínculos `client_driver` e operando como elegível.

Sem uma fonte única de regularidade, o app, o painel Admin, a busca e o fluxo de propostas podem discordar sobre a mesma documentação. A consequência é risco de conformidade e uma revalidação manual que não suspende o motorista de modo confiável.

## What Changes

- Criar a capability de regularidade documental como estado consolidado e atualizado transacionalmente para cada motorista: `REGULAR`, `PENDING_REVIEW`, `EXPIRED`, `REJECTED` ou `MISSING`.
- Reutilizar as rotas privadas de arquivo e a abstração `media-storage` já entregues; upload S3/MinIO e URLs pré-assinadas não fazem parte deste change, pois contradizem a decisão atual de storage local por provedor.
- Recalcular a regularidade quando CNH/documentos são criados, atualizados, recebem arquivo, são revisados ou expiram; troca de CNH ou documento obrigatório suspende somente a elegibilidade documental e encaminha o motorista para nova revisão Admin.
- Executar job diário para atualizar vencimentos e criar um único alerta auditável em D-60, D-30 e D0 para CNH e documentos ativos com validade.
- Usar a regularidade na busca por localização, recomendação, perfil público e criação/ativação de `client_driver`, preservando vínculos ativos já existentes.
- Expor a regularidade para o próprio motorista e para o Admin, com motivos documentais distintos de qualquer bloqueio de plano.
- Tornar a rejeição de `driver_document` justificada, mantendo o motivo visível ao proprietário; a rejeição/reaprovação da conta continua usando os endpoints de onboarding entregues em #47.

## Capabilities

### New Capabilities

- `driver-document-compliance`: Consolida validade, presença, revisão e rejeição documental; emite alertas e aplica a elegibilidade documental em todos os fluxos que exibem ou criam oportunidades para motoristas.

### Modified Capabilities

<!-- Não há capability principal arquivada para media-storage ou admin-driver-approval. Esta change integra as implementações já entregues sem publicar delta spec para um contrato principal inexistente. -->

## Impact

- **Database:** nova migration aditiva para o estado de regularidade no motorista, último alerta da CNH e uma tabela de eventos únicos de alerta; nenhuma migration V41–V48 será alterada.
- **Backend:** política de regularidade, serviço de atualização, job diário, endpoint de consulta, regras de transição e integração nos serviços de busca, perfil, onboarding e `client_driver`.
- **Existing files:** CNH e documentos continuam usando `media_file`, `StorageService`, `POST /.../photo|file` e `GET /.../photo|file`; eles não voltarão a gravar URL pública.
- **Notifications:** a persistência/idempotência dos alertas é deste change. O canal de push FCM depende da entrega de notificações push; enquanto ela não existir, o adaptador precisa usar o canal transacional aprovado (e-mail) ou permanecer explicitamente bloqueado.
