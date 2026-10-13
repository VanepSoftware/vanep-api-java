## Context

O app já mostra o botão "Conversar com o motorista" no perfil que o cliente abre a partir da busca, ou seja, **antes** de qualquer vínculo `client_driver`. Hoje a API não tem nada de chat nem de WebSocket.

O que já existe e é reaproveitado:

- `client_driver` com `@sec.isClientDriverLinkParty`: mesmo formato de checagem de "uma das duas partes", que a conversa replica.
- `driverRepository.existsSearchableByToken` (`@sec.isSearchableDriver`): a regra de "motorista que o cliente pode encontrar".
- `apiSecurityFilterChain` (`/api/**`, resource server JWT, stateless) e os beans `JwtDecoder` e `JwtAuthenticationConverter`.
- `SecurityHelper.getCallerUid`, para resolver o usuário pelo claim `uid`.
- Bundles de permissão em `role_permissions` (jsonb), mantidos por migration idempotente e pelo `DataSeeder`.
- Paginação com `Page<DTO>` e `@PageableDefault`, como em `CityController`.

## Goals / Non-Goals

**Goals:**

- Cliente e motorista trocam mensagens de texto, com entrega em tempo real enquanto o app está aberto.
- Autorização completa: só as duas partes leem, escrevem e assinam a conversa.
- Entregar em 3 PRs de até ~600 linhas e 10 arquivos novos cada.

**Non-Goals:**

- Push com app fechado, leitura, "digitando...", não lidas, anexos, editar ou apagar, inbox do cliente, frontend web e broker externo (ver proposal).

## Decisions

### D1 — A conversa pertence ao par (cliente, motorista), não ao vínculo

`conversation` tem `client_id` e `driver_id`, com índice único parcial `(client_id, driver_id) where deleted_at is null`, como em `client_driver_pair_active_key`.

**Alternativa descartada:** pendurar em `client_driver`. A conversa começa antes do vínculo, e criar um vínculo `PENDING` só para conversar misturaria "estou perguntando" com "pedi para contratar", além de poluir os fluxos de proposta e avaliação que leem esse status.

### D2 — Abrir é idempotente: `POST` devolve 201 ou 200

O cliente toca em "Conversar" quantas vezes quiser e sempre cai na mesma conversa. O app não precisa saber se ela já existe, e não há uma rota de busca separada. Uma corrida entre dois `POST` simultâneos esbarra no índice único (D1). O serviço trata a violação relendo a conversa existente.

### D3 — Envio por REST, recebimento por WebSocket

O cliente STOMP só faz `CONNECT` e `SUBSCRIBE`. Não existe `@MessageMapping`, e frames `SEND` vindos do cliente são rejeitados.

**Por quê:** validação (`@Valid`), autorização (`@PreAuthorize` com `@sec`), mensagens pt-BR (`MessageSource`) e o tratamento de erro HTTP já existem e são testados com MockMvc. Um `@MessageMapping` exigiria refazer tudo isso no canal STOMP, onde o erro chega ao cliente de outra forma. O WebSocket vira só um canal de push.

### D4 — O handshake é autenticado pela cadeia `/api/**` que já existe

O endpoint STOMP fica em `/api/ws`, dentro do `securityMatcher("/api/**")`. O Bearer JWT do header do handshake é validado pelo resource server como em qualquer rota, e o `Principal` autenticado passa para a sessão STOMP. **Nenhuma mudança no `SecurityConfig`.**

**Alternativa descartada:** liberar o handshake e validar o JWT num `ChannelInterceptor` no frame `CONNECT`. Isso duplicaria a validação de token. Só é necessário quando o cliente não consegue enviar header no upgrade, como o browser, e o chat web está fora de escopo. O Flutter em Android e iOS envia o header (`webSocketConnectHeaders`).

### D5 — `SUBSCRIBE` é autorizado por interceptor, reaproveitando `@sec`

Um `ChannelInterceptor` em `clientInboundChannel` aceita `SUBSCRIBE` apenas no formato `/topic/conversations/{token}` e quando `SecurityEvaluator.isConversationParty(token, principal)` é verdadeiro. Qualquer outro destino, ou um `SEND`, é rejeitado com exceção, que o Spring converte em frame `ERROR`.

**Por quê:** sem isso, qualquer usuário logado assina o tópico de qualquer conversa. É a falha clássica de chat com STOMP. A regra continua centralizada em `@sec` (regra 22 da constitution).

### D6 — Publicar só depois do commit

O serviço salva a mensagem e publica um evento de aplicação. Um `@TransactionalEventListener(phase = AFTER_COMMIT)` envia via `SimpMessagingTemplate` para `/topic/conversations/{token}`.

**Alternativa descartada:** chamar o `SimpMessagingTemplate` dentro do método transacional. A mensagem chegaria ao outro lado antes do commit, e sumiria do histórico se a transação desse rollback.

### D7 — `last_message_at` desnormalizado na conversa

Enviar mensagem atualiza `conversation.last_message_at` na mesma transação. A inbox do motorista ordena por `coalesce(last_message_at, created_at) desc` com fetch join do cliente e do usuário, em uma query e sem N+1 (regra 17).

**Alternativa descartada:** calcular a última mensagem por subquery ou `max()` agrupado. É mais caro e mais difícil de paginar, e a coluna também servirá ao contador de não lidas quando ele vier.

### D8 — Lado do remetente como enum, não usuário

`conversation_message.sender` é `ConversationParticipant { CLIENT, DRIVER }`, resolvido no serviço comparando o `uid` do chamador com o usuário do cliente da conversa. A mensagem publicada no tópico é a mesma para os dois assinantes, e cada app decide de que lado desenhar a bolha comparando `sender` com o próprio papel. Um campo `mine` não funcionaria no broadcast.

### D9 — Permissões

- `create_conversation` no bundle `CLIENT`, que só o cliente tem (decisão de produto: só ele inicia).
- `list_driver_conversations` no bundle `DRIVER`.
- Ler e enviar mensagens não usam permissão: valem só `@sec.isConversationParty`, porque a relação com a conversa é o que autoriza.

As duas permissões entram por migration idempotente (`||` com `@>`, como em `V51`) e pelo `DataSeeder`. O bundle `ADMIN` as recebe automaticamente via `PermissionEnum`.

### D10 — Limites

Texto de 1 a 2000 caracteres (`@NotBlank` e `@Size`). Histórico com `@PageableDefault(size = 30)`, ordenado por `sent_at desc`, com índice `(conversation_id, sent_at desc)`.

### Modelo

```
conversation                          conversation_message
─────────────────────────────         ──────────────────────────────────
id, token                             id, token
client_id  → client                   conversation_id → conversation
driver_id  → driver                   sender  varchar(16)  CLIENT|DRIVER
last_message_at  timestamptz null     body    varchar(2000)
created_at, updated_at, deleted_at    sent_at timestamptz, deleted_at
unique (client_id, driver_id)         index (conversation_id, sent_at desc)
  where deleted_at is null
index (driver_id, last_message_at)
```

Ambos com `@SoftDelete` (regra 19), embora nenhuma rota apague nesta change.

### Fluxo

```
 app cliente                         API                                app motorista
     │  POST /api/conversations         │                                     │
     │ ───────────────────────────────▶ │ abre ou retoma (201/200)            │
     │  CONNECT /api/ws (Bearer)        │                                     │
     │  SUBSCRIBE /topic/conv/{t} ────▶ │ @sec.isConversationParty?           │
     │  POST /{t}/messages ───────────▶ │ salva ─ commit ─▶ publica ────────▶ │
     │ ◀── 201 + mensagem ──────────────│                   /topic/conv/{t}   │
```

## Risks / Trade-offs

- **[Broker em memória]** O tempo real só funciona com uma instância da API. Com duas, quem estiver conectado à outra não recebe. → Aceito para o MVP. Trocar por `enableStompBrokerRelay` (RabbitMQ) não muda o contrato do cliente.
- **[JWT expira com o socket aberto]** O handshake valida o token uma vez, e a sessão STOMP continua viva depois que ele expira. → O app reconecta com o token renovado. Como o envio é por REST, nenhuma escrita acontece com token vencido, e o pior caso é seguir recebendo mensagens de uma conversa da qual o usuário ainda é parte.
- **[Mensagem perdida durante uma reconexão]** O que for publicado enquanto o socket está caído não é reentregue. → O app recarrega a primeira página do histórico ao reconectar. O histórico REST é a fonte da verdade.
- **[Corrida no `POST /api/conversations`]** → índice único mais a releitura descrita em D2.
- **[Rate limit]** O `RateLimitingFilter` pode contar o handshake em `/api/ws` e cada `POST` de mensagem. → Verificar na B3 se os limites atuais aceitam uma conversa normal. Nenhuma mudança está prevista.

## Migration Plan

- `V53` cria as tabelas e adiciona as permissões aos bundles. É aditiva e não toca dados existentes.
- **Rollback:** reverter o deploy. As tabelas novas ficam sem uso e nenhuma rota antiga depende delas.

## Open Questions

Nenhuma bloqueante. O ícone de mensagens no `driver_shell` e a ordem da inbox já foram decididos para o mobile.

## Phased delivery

| Phase | Contents | Depends on | Parallel with |
|---|---|---|---|
| B1 | Migration `V53`, `ConversationModel`, `ConversationMessageModel`, `ConversationParticipant`, repositories, `@sec.isConversationParty`, permissões em `PermissionEnum` e `DataSeeder` | — | Mobile M1 |
| B2 | DTOs, mapper, `ConversationService`, `ConversationController` (4 rotas), mensagens pt-BR, evento `ConversationMessageSentEvent` publicado no envio | B1 merged | Mobile M1/M2 (com stubs) |
| B3 | `spring-boot-starter-websocket`, `WebSocketConfig` (`/api/ws`, simple broker `/topic`), interceptor de `SUBSCRIBE`/`SEND`, listener `AFTER_COMMIT` que publica no tópico | B2 merged | Mobile M2 |

Estimativa por fase (linhas de produção / testes): B1 ~200 / ~150, B2 ~350 / ~250, B3 ~150 / ~150.
