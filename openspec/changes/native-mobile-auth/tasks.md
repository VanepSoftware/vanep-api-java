## Plano de entrega (regras 36–44)

Grafo de dependências (backend neste repo; fases mobile M5–M8 em `vanep-mobile`):

```
L0:  0a ─────┬──────────────▶ 0c ──────────────┐
     0b ─────┤                                  │
     1a ─────┼──▶ 1b ──────────────┐            │
     2a ─────┼──▶ 2b ──┐           │            │
     3a ─────┼─────────┼──▶ 3b     │            │
             │         │   (2a,3a; │            │
             │         │  após 2b) │            │
L1:          └──▶ 4a (0a,0b) ──────┼──▶ 4b (1b,4a)
                                   └──▶ 4c (0c,3a,4a) ◀┘
Mobile:  M5 ◀ 2b   M6 ◀ 3b,4c   M7 ◀ 1b,2b,4a,4b   M8 ◀ 4b   ──▶  9 ◀ M8
```

Camadas: **L0** = 0a, 0b, 1a, 2a, 3a · **L1** = 0c, 1b, 2b, 4a · **L2** = 3b, 4b, 4c · **L3** = 9 (após o mobile).

| Fase | Conteúdo | Depende de | Paralela com |
|---|---|---|---|
| 0a | Request DTOs de cadastro + duplicidade no `RegistrationService` + formulários web com chaves | — | 0b, 1a, 2a, 3a |
| 0b | Rate limit por endereço confiável (`native` + `CF-Connecting-IP` + API só em loopback) | — (deploy após tarefa 0.5) | 0a, 1a, 2a, 3a |
| 1a | Migration de código + `OneTimeTokenModel` + repositórios + `SecureCodes` + `AuthCodeIssuePolicy` | — | 0a, 0b, 2a, 3a |
| 2a | Autenticação de cliente público mobile + `OAuth2TokenGenerator` com refresh para o mobile | — | 0a, 0b, 1a, 3a |
| 3a | `GoogleIdTokenValidator` + tabela/serviço `signup_ticket` | — | 0a, 0b, 1a, 2a |
| 0c | Conclusão de cadastro Google cria registro de papel (web + base para API) | 0a | 1b, 2b, 4a |
| 1b | Códigos nos services de verificação/reset + templates + TTL 15 min | 1a | 0c, 2b, 4a |
| 2b | Grant de senha + lockout uniforme + registro de login | 2a | 0c, 1b, 4a |
| 4a | Filter chain `/api/auth/**` + envelope de erro + `POST /api/auth/signup/{tipo}` | 0a, 0b | 0c, 1b, 2b |
| 3b | Grant Google + `registration_required` no token endpoint | 2a, 3a (após 2b: mesma config) | 4b, 4c |
| 4b | `/api/auth/email/verify[/resend]`, `/api/auth/password/{forgot,reset}` | 1b, 4a | 3b, 4c |
| 4c | `POST /api/auth/signup/complete` | 0c, 3a, 4a | 3b, 4b |
| 9 | Remover `authorization_code`/`redirect-uris` do `vanep-mobile` | M8 (mobile) | — |

Cada fase: branch própria a partir de `main`, uma PR (pt-BR, `Refs #177`; a última usa `Closes #177`), ≤ ~600 linhas produtivas e ≤ 10 arquivos novos, testes da própria fase, `make lint` e `./mvnw verify` verdes.

## 0. Preparação

- [ ] 0.1 Revisar `proposal.md`, `design.md` e os três specs com o time
- [ ] 0.2 Confirmar o próximo número livre de migration Flyway no momento de cada fase (hoje `V33` é o último; 1a usa o próximo, 3a o seguinte)
- [x] 0.3 Levantar a topologia de produção: Cloudflare Tunnel (`cloudflared` no host) → API na 8080; sem outro proxy; 8080 hoje exposta no IP público (design D9)
- [ ] 0.4 Google Cloud: criar o OAuth client Android (package + SHA-1 de debug/release/Play) no mesmo projeto do client Web e publicar a tela de consentimento. `VANEP_GOOGLE_ID_TOKEN_AUDIENCES` não precisa ser definida até o iOS (default = `GOOGLE_CLIENT_ID`). Contrato com o `vanep-mobile` (M6): `serverClientId` = o mesmo `GOOGLE_CLIENT_ID` do `.env` do backend, e login Google testado em dispositivo real
- [x] 0.5 Conferir (com root) o ingress de `/etc/cloudflared/config.yml`: `api.vanep.com.br` → `http://localhost:8080` (não é o IP público; o bind em loopback da 0b é compatível)
- [ ] 0.6 No deploy da 0b, trocar o ingress para `http://127.0.0.1:8080` e reiniciar o `cloudflared`: o Docker publica só em IPv4, e `localhost` pode resolver primeiro para `::1`

## 1. Fase 0a — Cadastro com regra única (PR 0a)

> Objetivo: `RegistrationService` independente dos formulários Thymeleaf e dono da regra de duplicidade; web sem regressão.
> Depende de: — | Paralela com: 0b, 1a, 2a, 3a
> Ordem: test → request DTO → service → controller web

- [x] 1.1 Testes unitários (`RegistrationServiceTest`) falhando: duplicidade de e-mail e de CPF normalizado lança `SignupDuplicateException` com campo e chave; cadastro válido de cliente/motorista/assistente continua criando conta não verificada + registro de papel + e-mail
- [x] 1.2 Criar `AccountSignupRequestDTO` (base) e `ClientSignupRequestDTO`, `DriverSignupRequestDTO`, `AssistantSignupRequestDTO` em `br.com.vanep.auth.dto` com Bean Validation usando chaves `{auth.signup.*}`
- [x] 1.3 Adicionar as chaves de validação em `messages.properties` e `messages_pt_BR.properties` (mesmos textos pt-BR atuais) e trocar as strings fixas dos `*SignupForm`/`SignupForm` pelas chaves
- [x] 1.4 Criar `SignupDuplicateException` (campo + chave de mensagem)
- [x] 1.5 Alterar `RegistrationService` para receber os request DTOs, normalizar CPF e checar duplicidade antes de salvar
- [x] 1.6 Alterar `RegistrationController` para mapear `*SignupForm` → DTO, remover `rejectDuplicates` e converter `SignupDuplicateException` em `rejectValue`
- [x] 1.7 Rodar `RegistrationControllerTest` sem alteração de asserções de mensagem (garantia de não regressão web)
- [ ] 1.8 `make lint` + `./mvnw verify`; abrir PR 0a

## 2. Fase 0b — Rate limit por endereço confiável (PR 0b)

> Objetivo: `X-Forwarded-For` de par não confiável não altera a chave do rate limit.
> Depende de: — | Paralela com: 0a, 1a, 2a, 3a
> Ordem: test → security/config

- [x] 2.1 Teste em `RateLimitingFilterTest`: requisições do mesmo `remoteAddr` com `X-Forwarded-For` e `CF-Connecting-IP` diferentes compartilham o bucket e recebem `429` ao exceder
- [x] 2.2 `RateLimitingFilter` passa a usar só `request.getRemoteAddr()` (remover leitura manual do header)
- [x] 2.3 `application-prod.properties`: `server.forward-headers-strategy=native` e `server.tomcat.remoteip.remote-ip-header=${VANEP_REMOTE_IP_HEADER:CF-Connecting-IP}` (`internal-proxies` no default do Tomcat); documentar `VANEP_REMOTE_IP_HEADER` em `.env.example`
- [x] 2.4 `docker-compose.yml`: publicar a API em `"${APP_BIND_ADDRESS:-127.0.0.1}:${APP_PORT}:8080"` com comentário do motivo (Tunnel; porta pública contorna a Cloudflare); documentar `APP_BIND_ADDRESS` em `.env.example` (dev com celular físico usa `0.0.0.0`)
- [ ] 2.5 `make lint` + `./mvnw verify`; abrir PR 0b com o checklist de validação pós-deploy do design e a dependência da tarefa 0.5

## 3. Fase 1a — Base dos códigos (PR 1a)

> Objetivo: schema, modelos, consultas e regras puras; nenhum e-mail muda ainda.
> Depende de: — | Paralela com: 0a, 0b, 2a, 3a
> Ordem: test → migration → model → repository → policy/utilitário

- [x] 3.1 Testes unitários falhando: `SecureCodes.generate()` sempre 6 dígitos com zeros à esquerda; `SecureCodes.hmac` determinístico por (purpose, userId, code) e diferente entre usuários; `AuthCodeIssuePolicy` (sem código anterior → emite; dentro do cooldown → pula; ≥ máximo em 24 h → pula)
- [x] 3.2 Migration: `code_hash varchar(64) null` (sem unique), `failed_attempts integer not null default 0` e índice `(user_id, created_at)` em `email_verification_token` e `password_reset_token`
- [x] 3.3 Criar `@MappedSuperclass OneTimeTokenModel` com as colunas comuns e fazer `EmailVerificationTokenModel` e `PasswordResetTokenModel` estenderem
- [x] 3.4 Repositórios: contagem por `user_id` e `created_at > :since`; última linha criada; linha ativa mais recente com `@Lock(PESSIMISTIC_WRITE)`; consumo de ativas já existente
- [x] 3.5 Teste de repositório (H2) das novas consultas
- [x] 3.6 Implementar `SecureCodes` (em `auth/token`, HMAC-SHA256 com `vanep.password.pepper`, comparação em tempo constante) e `AuthCodeIssuePolicy` (classe pura)
- [x] 3.7 Propriedades `vanep.auth.code.max-attempts`, `vanep.auth.code.resend-cooldown-seconds`, `vanep.auth.code.max-per-day` com defaults 5/60/10 em `application.properties` e `.env.example`
- [ ] 3.8 `make lint` + `./mvnw verify`; abrir PR 1a

## 4. Fase 2a — Cliente público mobile + refresh (PR 2a)

> Objetivo: `vanep-mobile` autentica só com `client_id` em refresh e grants mobile e recebe refresh token com rotação; `vanep-frontend` intacto.
> Depende de: — | Paralela com: 0a, 0b, 1a, 3a
> Ordem: test → security (converter/provider) → config (token generator)

- [x] 4.1 Testes de slice falhando: `authorization_code` + PKCE do `vanep-mobile` passa a devolver `refresh_token`; refresh só com `client_id=vanep-mobile` devolve novos tokens; reuso do refresh antigo → `invalid_grant`; `POST /oauth2/revoke` só com `client_id=vanep-mobile` + `token` → `200`, e o refresh seguinte → `invalid_grant` (não `invalid_client`); revoke com `client_id` desconhecido → `401 invalid_client`; `vanep-frontend` continua sem refresh; `client_id` desconhecido → `401 invalid_client`
- [x] 4.2 Teste de claims: token emitido após o bean explícito de `OAuth2TokenGenerator` contém `uid`, `user_type`, `roles`, `permissions` (e `driver_status` para motorista)
- [x] 4.3 Criar `MobileClientAuthenticationConverter` e `MobileClientAuthenticationProvider` (regras do design D2, cobrindo `/oauth2/token` e `/oauth2/revoke`)
- [x] 4.4 Criar `MobileRefreshTokenGenerator` e o bean `OAuth2TokenGenerator` delegante (`JwtGenerator` + `JwtTokenCustomizer`, access token, refresh mobile)
- [x] 4.5 Registrar a autenticação de cliente em `SecurityConfig.authorizationServerSecurityFilterChain` antes dos conversores padrão
- [ ] 4.6 `make lint` + `./mvnw verify`; abrir PR 2a

## 5. Fase 3a — Validação do `id_token` Google + `signup_ticket` (PR 3a)

> Objetivo: peças independentes do grant Google, testáveis sem rede.
> Depende de: — | Paralela com: 0a, 0b, 1a, 2a
> Ordem: test → migration → model → repository → service/validator

- [ ] 5.1 Testes unitários falhando de `GoogleIdTokenValidator` com chave RSA local e tokens assinados no teste: válido; assinatura errada; `iss` inválido; `aud` fora da lista; `aud` = client Web com `azp` = client Android → válido; `aud` = client Android com lista só com o Web → `invalid_grant`; lista vazia; expirado; `email_verified=false`
- [ ] 5.2 Testes unitários falhando de `SignupTicketService`: emite ticket (hash armazenado, TTL), consome uma vez, rejeita expirado/consumido/desconhecido
- [ ] 5.3 Migration da tabela `signup_ticket` (colunas do design D5, `ticket_hash` unique)
- [ ] 5.4 `SignupTicketModel` + `SignupTicketRepository` (busca por hash com `PESSIMISTIC_WRITE`)
- [ ] 5.5 `SignupTicketService` (`SecureTokens`, `vanep.auth.signup-ticket.ttl-minutes` default 15)
- [ ] 5.6 `GoogleIdTokenValidator` com `JwtDecoder` privado (nunca um segundo bean do tipo `JwtDecoder`; construtor de pacote para testes); teste de contexto confirmando que um JWT da Vanep continua aceito em `/api/**`; propriedades `vanep.google.id-token.jwks-uri` (default JWKS do Google) e `vanep.google.id-token.audiences=${VANEP_GOOGLE_ID_TOKEN_AUDIENCES:${GOOGLE_CLIENT_ID:}}`; teste do fallback (sem a variável própria, aceita `aud` igual ao `GOOGLE_CLIENT_ID`); `.env.example` documentado (comentada, usar só quando houver mais de um client ID)
- [ ] 5.7 `application-test.properties`: JWKS URI `http://localhost:1` e audiences de teste falsas (regra 50)
- [ ] 5.8 `make lint` + `./mvnw verify`; abrir PR 3a

## 6. Fase 0c — Conclusão Google cria registro de papel (PR 0c)

> Objetivo: `completeRegistration` cria client/driver/assistant pelo mesmo caminho do cadastro normal.
> Depende de: 0a | Paralela com: 1b, 2b, 4a
> Ordem: test → service → controller web

- [ ] 6.1 Testes unitários falhando (`OAuthAccountServiceTest`): conclusão como CLIENT cria `ClientModel`; como DRIVER com `basePrice` cria `DriverModel` `PENDING`; como ASSISTANT continua criando `AssistantModel`
- [ ] 6.2 Extrair em `RegistrationService` o método público de criação do registro de papel e reusar em `registerClient/Driver/Assistant`
- [ ] 6.3 `OAuthAccountService.completeRegistration` passa a delegar a criação do papel; DRIVER exige os campos de motorista (validação condicional no DTO de conclusão)
- [ ] 6.4 `SignupController` (web): DRIVER sem campos de motorista mostra erro de validação e não cria conta; teste de slice cobrindo cliente e motorista
- [ ] 6.5 `make lint` + `./mvnw verify`; abrir PR 0c (mencionar follow-up da tela `signup-complete` para motorista)

## 7. Fase 1b — Códigos nos fluxos de verificação e reset (PR 1b)

> Objetivo: e-mails com código + link, limites aplicados a web e API, TTL de reset 15 min.
> Depende de: 1a | Paralela com: 0c, 2b, 4a
> Ordem: test → service → templates/config

- [ ] 7.1 Testes unitários falhando em `EmailVerificationServiceTest`: emissão grava `code_hash` e envia `code` + `link` + TTL; troca de e-mail continua só com link; reenvio respeita cooldown e máximo diário (sem envio, sem erro); `verifyByCode` correto marca verificado e consome; errado incrementa tentativas; 5º erro consome a linha (link também); conta verificada ou com `pending_email` → falha; e-mail inexistente → falha
- [ ] 7.2 Testes unitários falhando em `PasswordResetServiceTest`: mesmo conjunto para reset (conta sem senha local não recebe; `resetByCode` troca senha e consome código e link)
- [ ] 7.3 Implementar emissão com código e política em `EmailVerificationService` (`startVerification`, `resend`) e `verifyByCode(email, code)`
- [ ] 7.4 Implementar emissão com código e política em `PasswordResetService.requestReset` e `resetByCode(email, code, newPassword)`
- [ ] 7.5 Templates `email/verification.html` e `email/password-reset.html` exibem `code` e a validade vinda de `ttl` (remover "1 hora" fixo)
- [ ] 7.6 Default de `vanep.mail.reset-ttl-minutes` para 15 em `application.properties` e `VANEP_MAIL_RESET_TTL_MINUTES=15` em `.env.example`
- [ ] 7.7 Rodar `PasswordRecoveryFlowTest` e testes web de verificação: link continua funcionando e respeita o novo TTL
- [ ] 7.8 `make lint` + `./mvnw verify`; abrir PR 1b

## 8. Fase 2b — Grant de senha (PR 2b)

> Objetivo: `urn:vanep:params:oauth:grant-type:password` só no `vanep-mobile`, sem enumeração, lockout uniforme e `last_login_at`.
> Depende de: 2a | Paralela com: 0c, 1b, 4a
> Ordem: test → security (user details, provider) → service (registro de login) → config

- [ ] 8.1 Testes unitários falhando em `VanepUserDetailsServiceTest`: e-mail bloqueado lança `LockedException` antes de buscar usuário (existente, inexistente e sem senha local)
- [ ] 8.2 Testes de slice falhando do grant: sucesso (tokens + `last_login_at` + contador zerado); e-mail inexistente, senha errada e conta só Google com resposta idêntica `invalid_grant`; não verificado com senha certa → `email_not_verified`; com senha errada → `invalid_grant`; 5 falhas → `account_locked` mesmo com senha certa e para e-mail inexistente; lock expira; `vanep-frontend` → `unauthorized_client`
- [ ] 8.3 Teste de form login web: e-mail bloqueado inexistente e existente têm o mesmo resultado
- [ ] 8.4 `VanepUserDetailsService`: checar `LoginAttemptService.isBlocked` antes da busca e lançar `LockedException`
- [ ] 8.5 Extrair o registro de login bem-sucedido (`loginSucceeded` + `last_login_at`) do `AuthenticationEventsListener` para método de service reutilizável; listener passa a delegar
- [ ] 8.6 Criar `MobilePasswordGrantAuthenticationConverter`, `Token` e `Provider` (Dao dedicado com pre/post checks do design D4; mapeamento de erros com descrições via `MessageSource`)
- [ ] 8.7 Registrar o grant no token endpoint e habilitar o `AuthorizationGrantType` só no `RegisteredClient` mobile
- [ ] 8.8 Chaves de mensagem das descrições de erro (EN + pt-BR)
- [ ] 8.9 `make lint` + `./mvnw verify`; abrir PR 2b

## 9. Fase 4a — Base da API pública + cadastro (PR 4a)

> Objetivo: `/api/auth/**` público e sem token, envelope de erro próprio, cadastro por tipo, rate limit nas rotas novas.
> Depende de: 0a, 0b | Paralela com: 0c, 1b, 2b
> Ordem: test → security → request DTO (já existe da 0a) → service (já existe) → controller → response DTO

- [ ] 9.1 Testes de slice falhando: rota `/api/auth/**` sem token e com Bearer expirado não dá `401`; `GET /api/user/me` sem token continua `401`
- [ ] 9.2 Testes de slice falhando de `SignupApiController`: `201` cliente/motorista/assistente com `email` e `emailVerified=false` e e-mail disparado; `400 validation_error` com `errors` por campo (nome em branco + CPF inválido, motorista sem `basePrice`, termos não aceitos); `409 email_duplicate` e `document_duplicate`
- [ ] 9.3 Teste: erro de validação em `ProfileController` continua no envelope do perfil (sem interferência do novo advice)
- [ ] 9.4 Nova `SecurityFilterChain` para `/api/auth/**` (permitAll, stateless, CSRF off, sem resource server) antes da chain `/api/**`
- [ ] 9.5 Adicionar os `POST /api/auth/**` ao `RateLimitingFilter` e teste de `429`
- [ ] 9.6 `AuthErrorResponseDTO(code, message, errors[])`, exceções tipadas e `AuthErrorAdvice` (`assignableTypes` + `@Order`)
- [ ] 9.7 `SignupApiController` com `POST /api/auth/signup/{client|driver|assistant}` e `SignupResponseDTO`
- [ ] 9.8 `make lint` + `./mvnw verify`; abrir PR 4a

## 10. Fase 3b — Grant Google (PR 3b)

> Objetivo: `urn:vanep:params:oauth:grant-type:google` só no `vanep-mobile`, com `registration_required` + ticket.
> Depende de: 2a, 3a (sequencial após 2b por tocar a mesma configuração) | Paralela com: 4b, 4c
> Ordem: test → security (converter/provider) → config (error handler)

- [ ] 10.1 Testes de slice falhando (decoder local, sem rede): conta vinculada → tokens; e-mail existente → vincula e emite tokens; conta desativada → `account_disabled`; usuário novo → `400 registration_required` com `signup_ticket`, `email` e `name`; `id_token` inválido → `invalid_grant`; `vanep-frontend` → `unauthorized_client`
- [ ] 10.2 Criar `RegistrationRequiredException` e o `errorResponseHandler` do token endpoint que inclui `signup_ticket`, `email` e `name` só para ela
- [ ] 10.3 Criar `MobileGoogleGrantAuthenticationConverter`, `Token` e `Provider` (validator → `OAuthAccountService.resolve` → tokens ou ticket)
- [ ] 10.4 Registrar o grant no token endpoint e habilitar o `AuthorizationGrantType` só no `RegisteredClient` mobile
- [ ] 10.5 `make lint` + `./mvnw verify`; abrir PR 3b

## 11. Fase 4b — Endpoints de código (PR 4b)

> Objetivo: verificação, reenvio, esqueci e reset de senha por código, sem enumeração.
> Depende de: 1b, 4a | Paralela com: 3b, 4c
> Ordem: test → request DTO → controller

- [ ] 11.1 Testes de slice falhando: `POST /api/auth/email/verify` `204` com código certo e `400 invalid_code` para e-mail inexistente, código errado/expirado/substituído/esgotado, conta verificada e conta com `pending_email`
- [ ] 11.2 Testes de slice falhando: `resend` e `forgot` sempre `202` (conta elegível, inexistente, cooldown, limite diário), verificando envio ou não via mock do `MailService`
- [ ] 11.3 Testes de slice falhando: `POST /api/auth/password/reset` `204` (senha nova aceita no grant, antiga rejeitada), `400 validation_error` para `newPassword` com 7 caracteres sem consumir o código, `204` com 8 caracteres (mesmo mínimo do reset web), `400 invalid_code` nos demais casos
- [ ] 11.4 Request DTOs `EmailVerifyRequestDTO`, `EmailRequestDTO`, `PasswordResetRequestDTO`
- [ ] 11.5 `EmailCodeApiController` com os quatro endpoints delegando aos services da 1b
- [ ] 11.6 `make lint` + `./mvnw verify`; abrir PR 4b

## 12. Fase 4c — Conclusão de cadastro Google pela API (PR 4c)

> Objetivo: `POST /api/auth/signup/complete` com ticket.
> Depende de: 0c, 3a, 4a | Paralela com: 3b, 4b
> Ordem: test → request DTO → service → controller

- [ ] 12.1 Testes de slice falhando: `201` cliente (grant Google seguinte devolve `ROLE_CLIENT`) e motorista (`driver_status`); ticket desconhecido/expirado/usado → `400 invalid_signup_ticket` sem criar nada; e-mail registrado enquanto o ticket estava aberto → `409 email_duplicate`; CPF duplicado → `409 document_duplicate`; motorista sem `basePrice` → `400 validation_error`
- [ ] 12.2 `GoogleSignupCompleteRequestDTO` (validação condicional de motorista reaproveitando as regras da 0c)
- [ ] 12.3 Service de conclusão: consome ticket (`SignupTicketService`) e delega a `OAuthAccountService.completeRegistration` na mesma transação
- [ ] 12.4 Endpoint `POST /api/auth/signup/complete` no `SignupApiController`
- [ ] 12.5 `make lint` + `./mvnw verify`; abrir PR 4c

## 13. Fase 9 — Limpeza do fluxo legado (PR 9, após o mobile)

> Objetivo: `vanep-mobile` sem `authorization_code` nem redirect.
> Depende de: M8 em `vanep-mobile` e decisão de versão mínima do app | Paralela com: —
> Ordem: test → config

- [ ] 13.1 Teste: `vanep-mobile` não aceita mais `authorization_code` (`unauthorized_client`); grants mobile e refresh continuam funcionando; `vanep-frontend` intacto
- [ ] 13.2 Remover `AUTHORIZATION_CODE` e `redirect-uris` do `RegisteredClient` mobile, a propriedade `vanep.oauth.mobile-client.redirect-uris` e `VANEP_OAUTH_MOBILE_REDIRECT_URIS` do `.env.example`; atualizar `MobileOAuthClientTest`
- [ ] 13.3 `make lint` + `./mvnw verify`; abrir PR 9 com `Closes #177`

## 14. Encerramento

- [ ] 14.1 Abrir issues de follow-up: DPoP para refresh mobile; revogar sessões ao trocar senha; vincular Google a conta não verificada (marcar verificada e descartar senha local); campos de motorista na tela `signup-complete`; confirmação de troca de e-mail por código no perfil mobile
- [ ] 14.2 Arquivar a change (`/opsx:archive`)
