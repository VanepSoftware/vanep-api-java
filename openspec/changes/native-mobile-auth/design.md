## Context

A motivação está em `proposal.md` (seção Why) e o comportamento esperado nos specs `mobile-token-grants`, `auth-email-codes` e `auth-public-api`. Estado atual que molda o desenho:

- **Authorization Server** (Spring Security 7.0.5, SAS embutido):
  - `AuthorizationServerConfig` registra `vanep-frontend` e `vanep-mobile` como clientes públicos (`NONE`, PKCE), ambos só com `authorization_code` + `refresh_token`.
  - Não há `OAuth2TokenGenerator` nem conversores customizados: o `JwtTokenCustomizer` é aplicado pelo gerador padrão.
- **Cliente público no SAS 7.0.5** (verificado no bytecode):
  - `OAuth2RefreshTokenGenerator.isPublicClientForAuthorizationCodeGrant` devolve `null` para cliente `NONE`, ou seja, **nenhum refresh token é emitido**.
  - `PublicClientAuthenticationConverter` só reconhece `authorization_code` com `code_verifier`. Qualquer outro grant só com `client_id` cai em `invalid_client`.
  - O mobile hoje trata `refresh_token` como anulável (`TokenResponseDto`) e nenhum teste cobre refresh.
- **Login por senha**:
  - `DaoAuthenticationProvider` (form login) com `VanepUserDetailsService`, que marca `disabled` quando não verificado e `accountLocked` só para usuário existente com senha local.
  - Lockout e `last_login_at` dependem de eventos (`AuthenticationEventsListener`).
- **Google**:
  - `VanepOidcUserService` + `OAuthAccountService.resolve`.
  - O `completeRegistration` só cria `AssistantModel`, então cliente e motorista Google ficam sem registro de papel.
- **Cadastro**:
  - A duplicidade é checada no `RegistrationController` (web).
  - O `RegistrationService` recebe os `*SignupForm` Thymeleaf, que têm mensagens pt-BR fixas nas anotações.
- **Tokens de e-mail**:
  - V5 cria `email_verification_token` e `password_reset_token` com `token_hash` **unique** (SHA-256 de token de 256 bits) e `created_at`.
  - A mesma tabela de verificação serve a troca de e-mail (`pending_email`).
- **Rate limit**:
  - `RateLimitingFilter` usa como chave o primeiro `X-Forwarded-For`, com fallback para `getRemoteAddr()`.
  - Prod usa `server.forward-headers-strategy=framework`, que também confia no header vindo de qualquer origem.
  - O proxy da VPS não está versionado; o compose publica `APP_PORT`.
- **Última migration**: `V33`.

## Goals / Non-Goals

**Goals:**

- Emitir tokens para o app pelo mesmo `/oauth2/token`, com a mesma assinatura, as mesmas claims e a mesma persistência, apenas estendendo a autenticação de cliente, os grants e o gerador de refresh.
- Uma única regra por fluxo para web e mobile: cadastro, códigos, lockout e rate limit passam pelos mesmos services.
- Tudo configurável por env com default (regras 1 e 3); testes sem rede (regra 50).

**Non-Goals:**

- DPoP / sender-constrained tokens. O SAS 7.0.5 já valida prova DPoP se presente, mas o app não a envia nesta change.
- Revogar sessões ativas ao trocar a senha.
- Endurecer a vinculação automática Google ↔ conta local não verificada (ver Riscos).
- Refresh token para o `vanep-frontend`: o gerador só libera o cliente mobile.
- Campos de motorista na tela Thymeleaf `signup-complete`.

## Decisions

### D1: Grants de extensão no SAS, não endpoints que geram JWT

Cada grant tem três peças: um `AuthenticationConverter`, que lê o form do `/oauth2/token`, um `AuthenticationToken` e um `AuthenticationProvider`. Todas são registradas em `OAuth2AuthorizationServerConfigurer.tokenEndpoint(...)`. O provider:
1. resolve o principal (e-mail);
2. monta o `OAuth2TokenContext` com o `RegisteredClient`;
3. gera access e refresh token pelo `OAuth2TokenGenerator`;
4. salva o `OAuth2Authorization`;
5. devolve `OAuth2AccessTokenAuthenticationToken`.

Refresh, revoke e introspecção continuam nativos. Pacote: `br.com.vanep.auth.oauth.grant`. Nomes explícitos: `MobilePasswordGrantAuthenticationConverter`/`Provider`/`Token` e `MobileGoogleGrantAuthenticationConverter`/`Provider`/`Token`.

- **Alternativa rejeitada:** `POST /api/auth/login` montando JWT com `JwtEncoder`. Duplicaria rotação, revogação, persistência e claims, e criaria dois emissores.
- **Alternativa rejeitada:** grant `password` padrão do OAuth 2.0. Foi removido do OAuth 2.1, e o SAS não oferece.

### D2: Autenticação de cliente público só para o `vanep-mobile`

`MobileClientAuthenticationConverter` é registrado em `clientAuthentication(...)` antes dos conversores padrão. Ele só reconhece a requisição quando todas estas condições valem:
- `POST /oauth2/token`;
- `grant_type` ∈ {password URN, google URN, `refresh_token`};
- exatamente um `client_id`, sem `client_secret` e sem header `Authorization`;
- `client_id` igual a `vanep.oauth.mobile-client.id`.

Nesse caso produz `OAuth2ClientAuthenticationToken(clientId, NONE)`. `MobileClientAuthenticationProvider` carrega o `RegisteredClient`, exige `NONE` entre os métodos e autentica. Qualquer outra requisição segue o fluxo padrão, e o `vanep-frontend` não é afetado. O `OAuth2RefreshTokenAuthenticationProvider` do 7.0.5 aceita cliente `NONE` e só verifica DPoP quando há prova (bytecode, linhas 293–338). Um teste de slice na Fase 2a trava esse comportamento.

**Isso não é barreira de segurança.** O `client_id` de app público é extraível, e restringir grants ao `vanep-mobile` só evita configuração acidental. A defesa real do grant de senha são o lockout uniforme (D4) e o rate limit (D9).

- **Alternativa rejeitada:** cliente confidencial com secret no app. O secret é extraível, então a proteção é só aparente.
- **Adiado:** DPoP. É o caminho certo para amarrar o refresh ao dispositivo, mas exige chave em secure enclave no Flutter. Fica como follow-up.

### D3: Gerador de refresh que libera o cliente mobile

É um `OAuth2TokenGenerator` explícito: `DelegatingOAuth2TokenGenerator(JwtGenerator + JwtTokenCustomizer, OAuth2AccessTokenGenerator, MobileRefreshTokenGenerator)`. `MobileRefreshTokenGenerator` emite o refresh (TTL do `TokenSettings`) quando o `RegisteredClient` é o mobile, em qualquer grant, inclusive o `authorization_code` legado, o que ajuda builds antigas até a Fase 9. Para os demais clientes delega ao `OAuth2RefreshTokenGenerator` padrão, e o web continua como está. `reuseRefreshTokens(false)` já garante a rotação.

**Cuidado:** ao declarar o bean `OAuth2TokenGenerator`, o `JwtTokenCustomizer` passa a ser ligado manualmente. Um teste de claims (Fase 2a) impede que um token saia sem `uid`, `roles` e `permissions`.

### D4: Grant de senha reaproveitando `DaoAuthenticationProvider`

O provider do grant usa uma **instância própria** de `DaoAuthenticationProvider` (mesmo `VanepUserDetailsService` e `PasswordEncoder`), chamada direto, fora do `ProviderManager`, para não disparar eventos nem contar a falha em dobro. Configuração:
- `preAuthenticationChecks`: só `locked` e `expired`, **sem** `disabled`;
- `postAuthenticationChecks`: lança `DisabledException` quando a conta não está verificada. Assim `email_not_verified` só sai depois de a senha ser validada.

A mitigação de timing do Dao para usuário inexistente continua valendo.

**Lockout uniforme.** `VanepUserDetailsService.loadUserByUsername` passa a checar `LoginAttemptService.isBlocked(email)` **antes** de buscar o usuário e lança `LockedException`. O Dao só esconde `UsernameNotFoundException`, então a `LockedException` se propaga igual para e-mail existente, inexistente ou só Google. A mudança vale também para o form login web, que ganha o mesmo comportamento sem código extra.

Mapeamento de erros (`OAuth2Error`, descrição via `MessageSource`):

| Exceção | `error` | Efeito colateral |
|---|---|---|
| `BadCredentialsException` (inclui usuário inexistente e sem senha) | `invalid_grant` | `loginFailed(email)` |
| `LockedException` | `account_locked` | nenhum |
| `DisabledException` (pós-senha) | `email_not_verified` | nenhum |
| sucesso | — | `loginSucceeded(email)` + `last_login_at` |

O registro de sucesso (`loginSucceeded` + `last_login_at`) é extraído do `AuthenticationEventsListener` para um método de service reutilizado pelo listener e pelo provider (regra 6).

### D5: Grant Google e `signup_ticket`

`GoogleIdTokenValidator` (em `auth/oauth`) recebe um `JwtDecoder` dedicado com estas partes:
- `NimbusJwtDecoder.withJwkSetUri(vanep.google.id-token.jwks-uri)`, com default `https://www.googleapis.com/oauth2/v3/certs` e cache de chaves do Nimbus;
- validadores `JwtTimestampValidator`, emissor ∈ {`accounts.google.com`, `https://accounts.google.com`}, `aud` ∩ `vanep.google.id-token.audiences` ≠ ∅ (lista por vírgula, `${VANEP_GOOGLE_ID_TOKEN_AUDIENCES:${GOOGLE_CLIENT_ID:}}`). Sem a variável própria, o default é o client ID Web, que já é o `aud` do Android. A variável própria existe porque o login web do Spring aceita um único ID, e o iOS vai precisar de mais de um na lista e `email_verified == true`.

Com a lista vazia, todo pedido do grant Google recebe `invalid_grant`. Como o decoder é injetado como bean, os testes montam um decoder com chave RSA local e `id_token` assinado no próprio teste. `application-test.properties` fixa a JWKS URI em `http://localhost:1` (regra 50).

O provider chama `OAuthAccountService.resolve(GOOGLE, sub, email, true, name)`:
- **registrado:** emite tokens como no D1;
- **pendente:** `SignupTicketService.issue(...)` e lança `RegistrationRequiredException` (subclasse de `OAuth2AuthenticationException`) carregando ticket, e-mail e nome.

Um `errorResponseHandler` customizado no token endpoint serializa `error`/`error_description` e, só para essa exceção, `signup_ticket`, `email` e `name`. Os demais erros continuam no formato padrão.

`signup_ticket` é uma tabela nova:

| Coluna | Tipo |
|---|---|
| `id` | identity |
| `ticket_hash` | `varchar(64)` unique |
| `provider` | `varchar(16)` |
| `provider_uid` | `varchar(255)` |
| `email` | `varchar(255)` |
| `name` | `varchar(255)` |
| `expires_at`, `consumed_at`, `created_at` | `timestamptz` |

- O ticket é `SecureTokens.generate()` (256 bits) armazenado com `SecureTokens.hash`. Não precisa de HMAC porque tem entropia alta.
- TTL em `vanep.auth.signup-ticket.ttl-minutes` (default 15).
- O consumo usa `@Lock(PESSIMISTIC_WRITE)` para garantir uso único sob concorrência.
- Não usa soft delete: é credencial consumível, como os tokens do V5.

### D6: Códigos de 6 dígitos na mesma linha do token de link

Migration `V34__add_code_to_auth_token_tables.sql` (ou o próximo número livre no merge), nas duas tabelas:
- `code_hash varchar(64) null`, **sem unique**: o espaço é de 10⁶ e há colisão entre usuários;
- `failed_attempts integer not null default 0`;
- índice `(user_id, created_at)`.

Linhas antigas ficam com `code_hash` nulo e só valem pelo link.

- **Geração:** `SecureCodes.generate()` usa `SecureRandom.nextInt(1_000_000)` formatado com 6 dígitos.
- **Hash:** `SecureCodes.hmac(purpose, userId, code)` = HMAC-SHA256 com `vanep.password.pepper` sobre `purpose|userId|code`. Sem o pepper, ler o banco não revela o código. Incluir `purpose|userId` impede comparar hashes entre linhas.
- **Comparação:** tempo constante (`MessageDigest.isEqual`).
- **Colunas comuns:** o que as duas entidades têm em comum sobe para um `@MappedSuperclass OneTimeTokenModel`. As entidades e repositórios continuam separados por feature.

`AuthCodeIssuePolicy` é uma classe pura (regra 8) que recebe o `created_at` do último código e a contagem em 24 h, e decide `ISSUE` / `SKIP`. A contagem é uma query `count(*) where user_id = ? and created_at > now - 24h`, sem coluna nova. Limites:
- `vanep.auth.code.resend-cooldown-seconds` = 60;
- `vanep.auth.code.max-per-day` = 10;
- `vanep.auth.code.max-attempts` = 5.

A política vale para a primeira emissão no cadastro, para o reenvio e para o "esqueci senha", e cobre os formulários web automaticamente, porque estes chamam os mesmos services.

Validação por código:
1. `findByEmail`;
2. linha ativa mais recente do usuário (`consumed_at is null and expires_at > now`) com `PESSIMISTIC_WRITE`, o que impede palpites paralelos acima do limite;
3. se o HMAC confere: consome e aplica o efeito;
4. se não: `failed_attempts++`, e ao atingir o máximo `consumed_at = now`, o que mata o link também (aceito).

Qualquer ramo sem sucesso, incluindo usuário inexistente, devolve `invalid_code`.

- **Verificação:** só vale se `verified=false` e `pending_email` nulo. O e-mail de troca (`email/email-change.html`) continua só com link.
- **TTL:** o reset passa a ter default 15 min (`VANEP_MAIL_RESET_TTL_MINUTES`); a verificação continua em 24 h.
- **Templates:** `verification.html` e `password-reset.html` recebem `code` e `ttl`. O "O link expira em 1 hora" fixo sai do template.

- **Alternativa rejeitada:** tabela separada só para códigos. Duplicaria a invalidação cruzada código ↔ link, e a issue pede um fluxo só.

### D7: Fase 0, cadastro com regra única para web e API

- **Request DTOs novos** em `br.com.vanep.auth.dto`:
  - `AccountSignupRequestDTO` como base, com `ClientSignupRequestDTO`, `DriverSignupRequestDTO` e `AssistantSignupRequestDTO`;
  - Bean Validation com chaves `{auth.signup.*}`, resolvidas pelo `MessageSource` do validator do Spring (padrão já usado em `auth.signup.type.required`).
- **`RegistrationService`**:
  - recebe esses DTOs, e o controller web mapeia `*SignupForm` → DTO;
  - passa a checar duplicidade (`existsByEmail`, `existsByDocument` com CPF normalizado) e a lançar `SignupDuplicateException(field, messageKey)`;
  - o controller web captura e faz `rejectValue`, mantendo as mesmas mensagens.
- **Formulários web:** trocam as strings pt-BR fixas por chaves (regras 46 e 49).
- **Papel na conclusão Google:** a criação do registro de papel (`ClientModel`/`DriverModel`/`AssistantModel`) vira um método público do `RegistrationService`, usado pelo `OAuthAccountService.completeRegistration`. Consequência no web: motorista Google sem os campos de motorista recebe erro de validação (aceito; a tela fica para follow-up).

### D8: API pública `/api/auth/**`

- **Filter chain:** nova `SecurityFilterChain` com `securityMatcher("/api/auth/**")`, `permitAll`, stateless, CSRF off e **sem** `oauth2ResourceServer`. Ordem antes da chain de `/api/**`, para que um Bearer inválido não gere `401`.
- **Controllers finos** (regra 7):
  - `SignupApiController`: `/signup/{client|driver|assistant}` e `/signup/complete`;
  - `EmailCodeApiController`: `/email/verify`, `/email/verify/resend`, `/password/forgot` e `/password/reset`.
- **Envelope de erro:** `AuthErrorResponseDTO(code, message, errors[])` + `AuthErrorAdvice` com `@RestControllerAdvice(assignableTypes = {…})` e `@Order` alto, para não disputar `MethodArgumentNotValidException` com o `ProfileErrorAdvice`, que é global.
  - **Por que não reusar `ProfileErrorResponseDTO`:** ele tem um único `field`, e formulários de cadastro precisam de erros por campo.

Mapeamento de status:

| Situação | Status | `code` |
|---|---|---|
| Bean Validation | 400 | `validation_error` |
| duplicidade | 409 | `email_duplicate` / `document_duplicate` |
| código inválido | 400 | `invalid_code` |
| ticket inválido | 400 | `invalid_signup_ticket` |

Duplicidade revelando e-mail/CPF é **aceita conscientemente**: é o padrão de mercado e há rate limit.

### D9: Rate limit por endereço confiável (Cloudflare Tunnel)

**Topologia de produção** (verificada em 2026-09-15):
- `api.vanep.com.br` é um `CNAME` para `cfargotunnel.com`, e o TLS termina na Cloudflare.
- O `cloudflared` roda no host via systemd (`tunnel run`) e entrega o tráfego na API.
- Não há outro proxy na VPS.
- O compose publica `0.0.0.0:8080` e `[::]:8080`, então a API responde direto no IP público e **contorna a Cloudflare**. Pelo IPv6, o `docker-proxy` ainda faz o container ver o gateway do Docker como origem.

**Decisões:**
- **Chave:** `RateLimitingFilter` passa a usar só `request.getRemoteAddr()` e ganha todos os `POST /api/auth/**`.
- **IP real pelo `RemoteIpValve` do Tomcat**, em `application-prod.properties`:
  - `server.forward-headers-strategy=native` no lugar de `framework`;
  - `server.tomcat.remoteip.remote-ip-header=${VANEP_REMOTE_IP_HEADER:CF-Connecting-IP}`. A borda da Cloudflare sempre sobrescreve esse header, então o cliente não consegue falsificá-lo;
  - `internal-proxies` fica no default do Tomcat (loopback e redes privadas), que cobre a origem vista pelo container quando o `cloudflared` conecta na porta local (gateway do Docker).
- **Fechar o acesso direto:** o compose passa a publicar `"${APP_BIND_ADDRESS:-127.0.0.1}:${APP_PORT}:8080"`, e só processos do host (o `cloudflared`) alcançam a API. É isso que torna seguro o default de `internal-proxies`: nenhum par externo chega na porta para enviar headers. Em dev, quem testa em celular físico pela rede local define `APP_BIND_ADDRESS=0.0.0.0` no `.env`.

**Por que não as alternativas:**
- **`framework`:** o `ForwardedHeaderFilter` confia em headers de qualquer origem.
- **Confiar nas faixas de IP da Cloudflare:** com Tunnel, o container nunca vê IPs da Cloudflare.
- **Firewall na 8080:** exige root, e o Docker ignora o UFW. O bind em loopback resolve sem root.

**Validação:** ver Migration Plan.

## Risks / Trade-offs

- **`client_id` público permite chamar o grant de senha de fora do app** → lockout uniforme por e-mail + rate limit por IP; DPoP como follow-up.
- **Lockout por e-mail permite a terceiros bloquear uma conta por 15 min (DoS direcionado)** → comportamento que já existe no web; janela curta e configurável.
- **Esquecer de ligar o `JwtTokenCustomizer` no `OAuth2TokenGenerator` explícito** → teste de claims na Fase 2a, cobrindo `authorization_code` e o grant de senha.
- **CGNAT de operadoras (muitos usuários num IP) com 30 req/min por IP e rota, e o refresh dividindo o bucket do `/oauth2/token`** → capacidade por env; acompanhar `429` após o lançamento; se necessário, bucket separado para `refresh_token`.
- **Ingress do `cloudflared` apontando para o IP público em vez da porta local** → com o bind em loopback, a API sai do ar. Conferir `/etc/cloudflared/config.yml` antes do deploy da 0b (tarefa 0.5).
- **`CF-Connecting-IP` não chegar ao container, ou o valve não aceitar o gateway como proxy** → todos os clientes cairiam no bucket do gateway e tomariam `429` juntos. O teste pós-deploy do Migration Plan confere antes de liberar o app.
- **Pré-sequestro de conta:** atacante cria conta local não verificada com o e-mail da vítima; o login Google da vítima vincula a essa conta, e a senha do atacante fica lá → comportamento já existente no `resolve`. Recomendação de follow-up: ao vincular Google a conta não verificada, marcar verificada e descartar a senha local.
- **Queimar o código de outra pessoa com 5 palpites** → aceito; reenviar resolve, limitado a 10 por dia.
- **Tokens de reset emitidos antes do deploy com TTL de 60 min** → continuam valendo pelo TTL gravado; nenhuma ação.
- **Motorista Google no web recebe erro de validação** → aceito; follow-up para a tela `signup-complete`.
- **`ProfileErrorAdvice` global e o novo advice concorrendo** → `assignableTypes` + `@Order` + slice test de erro de validação nos dois controllers.

## Migration Plan

1. **Fases 0 → 4 no backend**, uma PR por fase (plano em `tasks.md`). Todas as migrations são aditivas (colunas nulas/default e tabela nova), então o rollback de versão da aplicação é seguro sem reverter o schema.
2. **Antes do deploy de cada fase:**
   - **Fase 0b:** confirmar (com root) que o ingress do `cloudflared` aponta para `localhost:8080` ou `127.0.0.1:8080`.
   - **Fase 3b:** `GOOGLE_CLIENT_ID` (client ID Web) presente no `.env`, porque ele é o default da lista de `aud`. `VANEP_GOOGLE_ID_TOKEN_AUDIENCES` só passa a ser definida quando o iOS entrar.
3. **Validação pós-deploy da Fase 0b**, antes de liberar o app:
   - `http://2.25.221.158:8080` deixa de responder (IPv4 e IPv6);
   - via `api.vanep.com.br`, dois clientes em redes distintas ficam em buckets separados;
   - um `CF-Connecting-IP` enviado pelo próprio cliente não altera a chave, porque a Cloudflare sobrescreve;
   - o form login web continua redirecionando com `https`, já que `native` passa a ler `X-Forwarded-Proto` pelo valve.
4. **Mobile (fases 5–8 em `vanep-mobile`)** consome os grants. A build nova passa a exigir versão mínima.
5. **Fase 9** só depois que nenhuma build ativa usar o WebView: remover `authorization_code` e `redirect-uris` do `vanep-mobile`. Rollback = reintroduzir as propriedades.

## Open Questions

- A versão mínima do app para a Fase 9 é decisão de release do mobile e não altera este backend.
