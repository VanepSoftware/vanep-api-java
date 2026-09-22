## Why

O cadastro atual de CNH e documentos do motorista não reage à passagem do tempo: uma CNH ou documento de vistoria vencido pode deixar o motorista elegível para busca e novas propostas. Isso cria exposição jurídica direta e não oferece ao motorista aviso ou um caminho consistente de regularização.

Também falta um contrato único de regularidade documental para o app e o painel administrativo. Sem ele, cada consumidor pode interpretar pendências, reprovações e vencimentos de forma diferente.

## What Changes

- Criar o motor de regularidade documental que consolida a situação da CNH e dos documentos obrigatórios, incluindo pendência de revisão, reprovação e vencimento.
- Executar diariamente a avaliação de validade e emitir uma única notificação por documento em cada marco: 60 dias, 30 dias e vencimento.
- Bloquear motoristas documentalmente irregulares de aparecerem em buscas e receberem novas propostas, sem alterar contratos já ativos; restaurar a elegibilidade após renovação e aprovação manual.
- Exigir nova revisão manual quando a CNH for substituída ou atualizada e tornar obrigatório o motivo de rejeição, visível ao motorista.
- Expor endpoint autenticado de regularidade documental, com os motivos de bloqueio separados do bloqueio por plano (RN-16).
- Substituir referências públicas a arquivos de documentos por armazenamento privado S3-compatível, com upload e leitura por URLs assinadas e temporárias.
- Estender o esquema de CNH com os metadados de revisão e notificação necessários, por nova migration Flyway; as migrations já aplicadas não serão alteradas.

## Capabilities

### New Capabilities

- `driver-document-compliance`: Avalia a regularidade documental do motorista, agenda alertas de validade, governa a elegibilidade para busca/propostas e expõe o estado consolidado.
- `private-driver-document-storage`: Recebe e entrega arquivos de CNH e documentos por storage S3-compatível privado, usando URLs assinadas com expiração.

### Modified Capabilities

<!-- Nenhuma capability principal existente cobre o ciclo de vida documental. A busca de motoristas será integrada pela nova capability de regularidade. -->

## Impact

- **Database:** nova migration para metadados de revisão/notificação da CNH e para registrar cada marco de alerta sem ambiguidade; índices para a varredura diária.
- **Backend:** novos serviços/políticas de regularidade e de arquivos privados, job agendado, repositórios/consultas, DTOs, endpoint e mensagens i18n.
- **Existing flows:** atualização de CNH/documento, aprovação/rejeição do Admin, busca de motoristas e o ponto de criação de novas propostas quando essa feature estiver presente no backend.
- **Infrastructure:** configuração por ambiente para endpoint, bucket, região/credenciais e TTL das URLs assinadas; MinIO no staging e S3-compatível em produção.
- **Dependencies:** entrega do fluxo de aprovação manual do Admin (#47) e provedor de push (FCM) para a entrega efetiva das notificações.
