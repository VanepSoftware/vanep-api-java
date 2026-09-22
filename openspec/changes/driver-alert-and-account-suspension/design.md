## Context

Os CRUDs existentes tratam CNH e documentos como recursos independentes. `driver_document` já tem status de revisão e `notified_at`, mas uma única data não consegue representar os três marcos de alerta. `driver_cnh` contém apenas validade e URL da foto, sem dados de revisão. A busca já centraliza o filtro final em `DriverRepository.findSearchableByIds`, mas não existe ainda no backend o fluxo de criação de propostas mencionado pela regra de negócio.

O Admin fará a revisão manual pela entrega #47; este change fornece o contrato e as transições que essa interface consome. Arquivos de documento são dados pessoais sensíveis e não podem continuar acessíveis por URL pública.

## Goals / Non-Goals

**Goals:**

- Determinar uma única regularidade documental por motorista, reutilizável pela busca, propostas, app e painel administrativo.
- Alertar cada documento válido uma vez nos marcos D-60, D-30 e D0, sem renotificação em reexecuções do job.
- Suspender apenas a elegibilidade para novas oportunidades por irregularidade documental, sem cancelar ou alterar contratos em curso.
- Requerer aprovação manual após a substituição de CNH e expor reprovação com motivo ao motorista.
- Armazenar binários em bucket privado e nunca devolver URL permanente ou pública.

**Non-Goals:**

- Construir a tela administrativa de revisão, a integração FCM concreta ou o fluxo de contratos/propostas ainda inexistente neste backend.
- Alterar regras de bloqueio por plano (RN-16), cancelar contratos ativos ou decidir documentos obrigatórios além dos tipos já definidos pelo produto.
- Fazer verificação automática de autenticidade, OCR ou antivírus do arquivo.

## Decisions

### D1 — Política central de regularidade, sem novo estado persistido no motorista

Criar uma política pura de regularidade documental e um serviço que carrega, em lote, a CNH e os documentos ativos de um motorista. O resultado contém `regular`, os impedimentos documentais e uma mensagem/código por motivo. Busca e criação de propostas consultam esse serviço/predicado; o endpoint apenas serializa o mesmo resultado.

Isso evita duplicar regras em controller, consulta de busca e painel. Não será adicionado um booleano `document_compliant` ao `driver`: ele ficaria desatualizado entre o job e alterações manuais. O filtro de busca será aplicado na consulta final de motoristas, preservando a ordenação atual e impedindo que motoristas bloqueados apareçam em qualquer página.

O bloqueio documental será distinto do bloqueio por plano. O agregado expõe motivos em categorias separadas; o consumidor de propostas escolhe a mensagem específica quando ambos se aplicam.

### D2 — Transições de revisão explícitas para CNH e documentos

Estender `driver_cnh` em uma nova migration com `status`, `reviewed_by`, `reviewed_at`, `rejection_reason` e os dados de alerta necessários, alinhando-o ao ciclo de `driver_document`. Criar/atualizar CNH e atualizar o arquivo ou a validade de um documento coloca o recurso em `PENDING`, limpa a decisão anterior e suspende a elegibilidade até uma aprovação manual. A aprovação só restaura o acesso se nenhum outro requisito documental estiver pendente, rejeitado ou vencido.

Rejeição exige `rejection_reason` não vazio no DTO e no serviço; aprovação limpa eventual motivo anterior. A atualização de status continuará autorizada somente para Admin pela integração #47. O status `APPROVED` não torna um item vencido válido: validade é uma dimensão separada.

### D3 — Marcos de alerta persistidos por documento e evento

Criar uma tabela de auditoria de alertas com chave única por tipo de recurso, identificador do recurso e marco (`DAYS_60`, `DAYS_30`, `EXPIRATION`). O job diário usa `Clock` injetável, localiza itens ativos com validade e persiste o marco antes/de forma transacional com o disparo enfileirado de notificação. A restrição única torna o job idempotente inclusive em execuções concorrentes; `notified_at` passa a registrar a última emissão no recurso para compatibilidade/auditoria, sem ser a fonte da deduplicação.

Os marcos são avaliados pela data local `America/Sao_Paulo`: D-60 e D-30 são 60 e 30 dias antes de `valid_until`/`expires_at`; D0 é a própria data de validade. A data de validade continua válida durante D0 e torna-se bloqueante em D+1. Caso o job fique indisponível em uma data, ele envia o marco pendente na próxima execução, uma única vez, em vez de o perder silenciosamente.

### D4 — Storage privado por chaves de objeto, URLs assinadas de curta duração

Substituir URLs persistentes por `object_key`/identificador privado. O backend emite URL assinada de upload para um prefixo não adivinhável limitado ao motorista e ao tipo de documento; após o upload, o CRUD recebe a chave emitida e a valida contra o escopo do solicitante. A leitura é feita por endpoint autorizado que retorna uma URL assinada de curta duração; Admin e proprietário usam as mesmas regras de acesso já existentes para o recurso.

Será usado um cliente S3-compatível configurado exclusivamente por ambiente (endpoint, bucket, região, credenciais e TTL). MinIO é apenas a implementação de staging. A alternativa de bucket público com URL salva na tabela foi descartada por expor dados LGPD; proxy de streaming foi adiado para evitar custo e complexidade inicial, mantendo a autorização no emissor da URL.

### D5 — Job observável e entrega desacoplada

O scheduler só identifica eventos e os registra de modo transacional. A entrega de push é uma porta/adaptador, permitindo FCM em produção e fake nos testes. Falhas de entrega não criam uma segunda notificação sem política explícita; elas ficam observáveis por log/métrica e deverão ter estratégia de retry definida com o provedor. O job pode ser acionado por scheduler diário e por teste de integração com `Clock` fixo.

## Risks / Trade-offs

- [Mudança de schema da CNH] → criar nova migration e migrar dados existentes para `PENDING`, exigindo revisão administrativa antes de nova elegibilidade; validar o rollout com o time de operações.
- [Execução perdida do scheduler] → processar marcos já vencidos ainda não registrados e manter unicidade no banco.
- [Consulta de busca mais cara] → filtrar a elegibilidade em lote/consulta, usar índices por driver, status e validade, e cobrir paginação em teste.
- [URLs assinadas vazarem] → TTL curto, bucket privado, prefixo restrito e nenhuma URL persistida ou retornada como URL pública.
- [Falha entre registrar e entregar push] → introduzir porta de notificação e definir outbox/retry antes de depender de entrega com garantia forte.
- [Regras de documentos obrigatórios incompletas] → parametrizar a lista após confirmação de produto; até lá, a política avalia CNH e os documentos ativos que possuem validade/revisão.

## Migration Plan

1. Publicar migration Flyway nova para os campos de revisão/notificação da CNH, tabela de marcos de alerta, chaves privadas de arquivo e índices de varredura; não modificar V17 ou V21.
2. Implantar as leituras tolerantes aos dados migrados e disponibilizar o endpoint de regularidade para Admin/app.
3. Configurar bucket privado e variáveis de ambiente em cada ambiente antes de habilitar emissão de URLs assinadas; migrar referências legadas de arquivo de forma controlada.
4. Habilitar o job diário e monitorar alertas, bloqueios e falhas de entrega.
5. Integrar o predicado de regularidade à busca e ao ponto de novas propostas. Rollback de código desabilita o job/filtro; migrations permanecem aditivas e não exigem rollback destrutivo.

## Open Questions

- Quais `DocumentTypeEnum` são obrigatórios para cada perfil de motorista e se algum deles não expira?
- Qual TTL de upload/leitura, limites de tamanho e MIME types serão aceitos para CNH e demais documentos?
- A entrega FCM exige outbox e retentativa garantida já neste change ou a primeira versão apenas registra/observa falhas?
- Onde o fluxo de novas propostas será introduzido, já que não há módulo correspondente no checkout atual?
