## Why

O perfil do motorista no app já mostra o botão "Conversar com o motorista", mas ele não faz nada (`onPressed: () {}`). Não existe canal entre cliente e motorista dentro da Vanep: o cliente que achou uma van na busca não tem como tirar dúvida antes de contratar, e depois de contratar continua sem canal. Esta change entrega o mínimo para os dois lados trocarem mensagens em tempo real.

## What Changes

- Nova entidade **conversa**, única por par (cliente, motorista). Ela **não depende do vínculo `client_driver`**: existe antes do contrato e continua depois dele.
- Nova entidade **mensagem**, com autor, texto e data, pertencente a uma conversa.
- **Só o cliente inicia.** `POST /api/conversations` abre a conversa com um motorista pesquisável (mesma regra de `@sec.isSearchableDriver`) ou devolve a que já existe para o par.
- **O motorista lista e responde.** `GET /api/conversations/me` devolve as conversas do motorista logado, da mais recente para a mais antiga.
- `GET /api/conversations/{token}/messages`, paginado, e `POST /api/conversations/{token}/messages`, restritos às duas partes da conversa (`@sec.isConversationParty`).
- **Tempo real por WebSocket (STOMP).** O envio continua por REST. Ao salvar uma mensagem, o servidor a publica em `/topic/conversations/{token}`. O handshake em `/api/ws` é autenticado pela cadeia de segurança da API que já existe, e a assinatura de um tópico só é aceita para quem é parte da conversa.
- Novas permissões `create_conversation` (bundle `CLIENT`) e `list_driver_conversations` (bundle `DRIVER`), entregues por migration e pelo `DataSeeder`.
- Nova dependência `spring-boot-starter-websocket`.
- **O texto das mensagens é cifrado no banco** (AES-256-GCM, chave em `VANEP_CHAT_ENCRYPTION_KEY`). Banco, backups e dumps só guardam texto cifrado; a API decifra ao responder para as partes. O contrato da API e o app não mudam.

**Fora de escopo** (pode entrar depois sem quebrar o contrato):

- Push com o app fechado (FCM/APNs)
- Confirmação de leitura, "digitando..." e contador de não lidas
- Anexos, editar ou apagar mensagem
- Inbox do cliente: ele volta para a conversa pelo perfil do motorista
- Chat no frontend web
- Broker externo (RabbitMQ/Redis). O broker em memória atende uma instância

## Capabilities

### New Capabilities
- `client-driver-chat`: conversa única por par cliente–motorista, iniciada só pelo cliente, com histórico paginado, envio por REST, entrega em tempo real por WebSocket e autorização restrita às duas partes.

### Modified Capabilities

Nenhuma.

## Impact

- **Banco:** nova migration `V53` com as tabelas `conversation` e `conversation_message` e as permissões nos bundles `CLIENT` e `DRIVER`. A `V54` muda `conversation_message.body` para `text`, porque o texto cifrado é maior que o original.
- **Configuração:** nova variável obrigatória `VANEP_CHAT_ENCRYPTION_KEY`.
- **Código:** novo feature package `br.com.vanep.conversation`. `SecurityEvaluator` ganha `isConversationParty`. `PermissionEnum` e `DataSeeder` ganham as duas permissões. Nova config de WebSocket em `config`.
- **API:** quatro rotas REST novas sob `/api/conversations` e o endpoint STOMP `/api/ws`.
- **Dependências:** `spring-boot-starter-websocket`.
- **Mobile:** consumido pela change `client-driver-chat` do `vanep-mobile`, que depende deste contrato.
- **Operação:** o broker em memória prende o tempo real a uma instância da API. Escalar horizontalmente exige um broker externo, que está fora de escopo.
