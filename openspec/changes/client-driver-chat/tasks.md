> **Pilha de branches.** Cada fase depende da anterior, então cada branch nasce da anterior e cada PR aponta a anterior como `--base`.
>
> ```
> main ─ feat/161-chat-schema ─ feat/161-chat-http ─ feat/161-chat-realtime ─ feat/161-chat-encryption
>        PR B1 → main                     PR B2 → B1                     PR B3 → B2                PR B4 → B3
> ```
>
> **Mergear pelo `merge stack`**, ou apagando a branch a cada merge: o repo tem `delete_branch_on_merge: false`, e sem apagar a branch o GitHub não reaponta a base das filhas.

## Plano

| Fase | Conteúdo | Depende de | Paralela com |
|---|---|---|---|
| B1 | `V53`, models, enum, repositories, `@sec.isConversationParty`, permissões | — | Mobile M1 |
| B2 | REST: abrir/retomar, inbox do motorista, histórico, envio, evento de mensagem enviada | B1 | Mobile M1/M2 (stubs) |
| B3 | WebSocket: `/api/ws`, broker simples, autorização de `SUBSCRIBE`, publicação após commit | B2 | Mobile M2 |
| B4 | Texto das mensagens cifrado no banco (AES-256-GCM) | B3 | — |

Teto por PR: ~600 linhas produtivas e 10 arquivos novos (regra 41).

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `client-driver-chat`
- [x] 0.3 **Reconfirmar o número de Flyway** na `main` atualizada: termina em `V52`, então `V53`
- [x] 0.4 Issue: #161. O corpo dela (chat só com contrato, 1:1 com `client_driver`, não lidas, push) está desatualizado; esta spec é a fonte da verdade. Os PRs usam `Refs #161`

## 1. Phase B1 — schema, modelo e autorização por parte (PR B1)

> Goal: conversa e mensagem existem no banco e no domínio, e `@sec` sabe quem é parte de uma conversa.
> Depends on: — | Parallel with: Mobile M1
> Order: test → migration → model → repository → security

- [x] 1.1 Criar branch `feat/161-chat-schema` a partir de `main`
- [x] 1.2 Testes: `SecurityEvaluator.isConversationParty` aceita o usuário do cliente e o do motorista e recusa um terceiro e um token inexistente. A checagem dos bundles fica nos testes de controller da B2 (regra 54: seeder não tem teste)
- [x] 1.3 Teste de repository (H2): a query da inbox devolve só as conversas do motorista, ordenadas por `coalesce(last_message_at, created_at) desc`, e o histórico vem paginado por `sent_at desc`
- [x] 1.4 Migration `V53__create_conversation_tables.sql`: `conversation` (D1, D7) e `conversation_message` (D8, D10), com índices únicos parciais por `token` e par, `(driver_id, last_message_at)` e `(conversation_id, sent_at desc)`. Adicionar `create_conversation` ao `CLIENT` e `list_driver_conversations` ao `DRIVER`, de forma idempotente, como em `V51`
- [x] 1.5 `ConversationParticipant` (enum `CLIENT`, `DRIVER`) em `conversation.enums`
- [x] 1.6 `ConversationModel` e `ConversationMessageModel` com `@SoftDelete`, token gerado no `@PrePersist`, como nos outros models. Relações to-one `EAGER`: o Hibernate não aceita `LAZY` para entidade com `@SoftDelete`
- [x] 1.7 `ConversationRepository` (por token, por par, inbox com fetch join de cliente e usuário, usuários das duas partes por token) e `ConversationMessageRepository` (página por conversa)
- [x] 1.8 `PermissionEnum`: `CREATE_CONVERSATION`, `LIST_DRIVER_CONVERSATIONS`. `DataSeeder`: incluir nos bundles `CLIENT` e `DRIVER`
- [x] 1.9 `SecurityEvaluator.isConversationParty(token, authentication)`, no mesmo formato de `isClientDriverLinkParty`
- [x] 1.10 Aplicar a `V53` manualmente no PostgreSQL local, em transação com rollback: índice único do par (os testes rodam em H2 sem Flyway, então só aqui ele é exercitado) e permissões, rodando o `update` duas vezes
- [x] 1.11 `make lint` + `./mvnw verify`. Abrir PR em pt-BR, `--base main`, `Refs #161`

## 2. Phase B2 — rotas REST (PR B2)

> Goal: o cliente abre a conversa, as duas partes trocam mensagens por REST e o motorista lista as suas.
> Depends on: B1 | Parallel with: Mobile M1/M2
> Order: test → request DTO → service → controller → response DTO

- [x] 2.1 Criar branch `feat/161-chat-http` a partir de `feat/161-chat-schema`
- [x] 2.2 Testes unitários do `ConversationService`: abrir cria (201) ou devolve a existente (200); motorista não pesquisável gera 404 `conversation.driver.not_found`; violação do índice único na corrida relê a existente (D2); enviar resolve `sender` pelo chamador, atualiza `last_message_at` e publica `ConversationMessageSentEvent`
- [x] 2.3 Testes de slice (`MockMvc` + segurança) para todos os cenários do spec: 403 do motorista no `POST /api/conversations`, 403 do cliente no `GET /me`, 403 de terceiro nas duas rotas de mensagem, 400 para texto em branco e para 2001 caracteres, histórico paginado do mais novo para o mais antigo
- [x] 2.4 `ConversationCreateRequestDTO` (`driverToken`) e `ConversationMessageCreateRequestDTO` (`body`, `@NotBlank`, `@Size(max = 2000)`). `ConversationMessageSentEvent` em `conversation.event`
- [x] 2.5 Chaves `conversation.driver.not_found` (e outras que surgirem) em `messages.properties` e `messages_pt_BR.properties`
- [x] 2.6 `ConversationService`: `openConversation`, `listDriverConversations`, `listMessages`, `sendMessage`. O envio publica `ConversationMessageSentEvent` (record) via `ApplicationEventPublisher`
- [x] 2.7 `ConversationController` em `/api/conversations` com `@PreAuthorize`: `hasAuthority('create_conversation')`, `hasAuthority('list_driver_conversations')` e `@sec.isConversationParty(#token, authentication)`
- [x] 2.8 `ConversationResponseDTO` (token, token e nome do motorista, nome do cliente, `lastMessageAt`), `ConversationMessageResponseDTO` (token, body, sender, sentAt) e `ConversationMapper`
- [x] 2.9 `make lint` + `./mvnw verify`. Abrir PR em pt-BR, `--base feat/161-chat-schema`, `Refs #161`

## 3. Phase B3 — tempo real (PR B3)

> Goal: a mensagem salva chega na hora para quem está com a conversa aberta.
> Depends on: B2 | Parallel with: Mobile M2
> Order: test → dependency → config → security (interceptor) → listener

- [x] 3.1 Criar branch `feat/161-chat-realtime` a partir de `feat/161-chat-http`
- [x] 3.2 Testes do interceptor: `SUBSCRIBE` em `/topic/conversations/{token}` passa para a parte e falha para um terceiro, para outro destino e para um frame `SEND` (D3, D5)
- [x] 3.3 Teste do listener: o evento publica `ConversationMessageResponseDTO` em `/topic/conversations/{token}` via `SimpMessagingTemplate` mockado. Teste de integração: uma mensagem salva em transação com rollback não publica nada (D6)
- [x] 3.4 Teste de slice: handshake em `/api/ws` sem Bearer responde 401 (D4)
- [x] 3.5 Adicionar `spring-boot-starter-websocket` ao `pom.xml`
- [x] 3.6 `WebSocketConfig` em `config`: `@EnableWebSocketMessageBroker`, endpoint `/api/ws`, `enableSimpleBroker("/topic")` e registro do interceptor no `clientInboundChannel`. Nenhuma URL fixa (regra 1); se precisar de allowed origins, ler de property com env var
- [x] 3.7 `ConversationSubscriptionInterceptor` (em `conversation.realtime`) (`ChannelInterceptor`) que delega a `SecurityEvaluator.isConversationParty`
- [x] 3.8 `ConversationMessagePublisher` com `@TransactionalEventListener(phase = AFTER_COMMIT)`
- [x] 3.9 Verificar se o `RateLimitingFilter` atinge `/api/ws` e o envio de mensagens, e se os limites aceitam uma conversa normal (risco do design). Resultado: ele só limita POSTs de autenticação; o chat não é afetado
- [x] 3.10 Smoke: virou o `ConversationRealtimeIntegrationTest`, com sessões STOMP reais em porta aleatória e JWT assinado pelo `JWKSource` da aplicação. O motorista recebe ao vivo o que o cliente envia por REST, o intruso e outros destinos são recusados, o handshake sem token dá 401 e mensagem com rollback não é publicada
- [x] 3.11 **QA manual da spec inteira (regra 55, adicionada nesta fase):** API da branch contra um Postgres descartável, tokens reais pelo grant do mobile, caminho feliz e mau uso em REST e WebSocket. Script em `scripts/qa/client-driver-chat/`, resultado em `qa.md`: 59/59
- [x] 3.12 `make lint` + `./mvnw verify`. Abrir PR em pt-BR, `--base feat/161-chat-http`, `Refs #161`

## 4. Phase B4 — texto cifrado no banco (PR B4)

> Goal: banco, backups e dumps só guardam o texto das mensagens cifrado; a API e o app não percebem a diferença.
> Depends on: B3 | Parallel with: —
> Order: test → migration → model (converter) → service (cipher) → config

- [x] 4.1 Criar branch `feat/161-chat-encryption` a partir de `feat/161-chat-realtime`
- [x] 4.2 Testes do `ConversationMessageCipher`: ida e volta (com emoji e 2000 caracteres), mesmo texto gera valores diferentes, valor adulterado e chave errada falham, linha sem prefixo volta como está, chave ausente ou inválida impede a criação
- [x] 4.3 Teste de repository: o valor gravado começa com `v1:` e não contém o texto; a entidade lida volta com o texto
- [x] 4.4 Teste do `ProductionSecretsValidator`: recusa chave vazia e chave de dev
- [x] 4.5 Migration `V54__conversation_message_body_as_text.sql`: `body` → `text`
- [x] 4.6 `ConversationMessageCipher` (AES-256-GCM, IV de 96 bits, formato `v1:`) e `ConversationMessageBodyConverter` no campo `body`
- [x] 4.7 Chave `vanep.chat.encryption-key` / `VANEP_CHAT_ENCRYPTION_KEY`: `application.properties`, valor de dev em `application-local.properties`, valor fixo em `application-test.properties`, placeholder em `.env.example`, geração no `make env`, checagem no `ProductionSecretsValidator`
- [x] 4.8 QA manual (regra 55): `qa.sh` ganha a checagem do banco cifrado, rodar a spec inteira de novo e anotar no `qa.md` (incluindo subir sem chave). Resultado: 68/68 em cinco rodadas seguidas
- [x] 4.9 `make lint` + `./mvnw verify`. Abrir PR em pt-BR, `--base feat/161-chat-realtime`, `Refs #161`

