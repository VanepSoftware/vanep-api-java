## Why

O app mobile autentica abrindo `/oauth2/authorize` num **WebView**. O cadastro, o login e a recuperação de senha aparecem como um site dentro do app, e o login Google **não funciona em dispositivo real**: o Google bloqueia OAuth em WebView (`403 disallowed_useragent`), e a RFC 8252 proíbe esse modelo. O app da Vanep é first-party, então todos os fluxos de autenticação do mobile passam a ser telas nativas falando com o backend por API. O backend continua sendo a autoridade que confere credenciais e emite os JWT. Prioridade **Alta**, issue #177.

Enquanto fechávamos o escopo, surgiu um fato que muda a premissa da issue. No Spring Authorization Server 7.0.5, um cliente público (`ClientAuthenticationMethod.NONE`) **não recebe refresh token** no `authorization_code`, e um `grant_type=refresh_token` só com `client_id` responde `401 invalid_client`. O "refresh continua valendo sem alteração" não é verdade hoje para o `vanep-mobile`, e os grants customizados também seriam barrados na autenticação do cliente.

## What Changes

- **Mesmas APIs OAuth.** `/oauth2/token`, `/oauth2/revoke`, JWKS, persistência em `oauth2_authorization` e claims do `JwtTokenCustomizer` não mudam de contrato. O front web continua com `authorization_code` + PKCE em `/oauth2/authorize`.
- **Autenticação de cliente público para o `vanep-mobile`**: o `client_id` sozinho passa a ser aceito nos grants customizados e no `refresh_token`, e o cliente passa a receber refresh token com rotação. Restringir grants ao `vanep-mobile` é organização de configuração, **não** barreira de segurança. A defesa real são o lockout e o rate limit.
- **Grant `urn:vanep:params:oauth:grant-type:password`** no `/oauth2/token`, só para o `vanep-mobile`:
  - e-mail inexistente, senha errada e conta só Google respondem o mesmo `invalid_grant`;
  - `email_not_verified` só aparece **depois** de a senha ser validada;
  - `account_locked` sai de forma uniforme para qualquer e-mail bloqueado, exista a conta ou não;
  - o lockout e o `last_login_at` voltam a funcionar nesse caminho.
- **Grant `urn:vanep:params:oauth:grant-type:google`**, só para o `vanep-mobile`:
  - valida o `id_token` do SDK nativo: assinatura, `iss`, `aud` configurável via env, `exp` e `email_verified`;
  - usuário novo recebe `400 registration_required` com um `signup_ticket` de uso único e TTL curto.
- **APIs públicas `/api/auth/**`**, que **nunca devolvem token**:
  - `POST /api/auth/signup/{client|driver|assistant}`;
  - `POST /api/auth/signup/complete`: cria o registro de papel como o cadastro normal (motorista exige os campos de motorista);
  - `POST /api/auth/email/verify` e `POST /api/auth/email/verify/resend`;
  - `POST /api/auth/password/forgot` e `POST /api/auth/password/reset`.
- **Código de 6 dígitos** nos e-mails de verificação e reset, **junto com o link atual**:
  - guardado como HMAC com segredo do servidor;
  - no máximo 5 tentativas por código;
  - novo código invalida o anterior, com cooldown de reenvio;
  - **no máximo 10 códigos por conta em 24 h**; acima disso a resposta continua `202`, só que sem envio.
  - **BREAKING (config):** o TTL do reset cai de 60 para **15 min** e vale também para o link web.
- **Correção no rate limit**: a chave passa a usar o endereço resolvido pelo servidor em vez do primeiro `X-Forwarded-For`, que o cliente controla. Afeta também as rotas web.
- **Refatoração prévia do cadastro**:
  - a checagem de duplicidade de e-mail e CPF sai do controller web e vai para o service;
  - o `RegistrationService` deixa de depender dos formulários Thymeleaf;
  - as mensagens passam a vir de `MessageSource`.
  - O web não pode regredir.
- **Correção no "completar cadastro" do Google**: hoje a conta nasce sem `ClientModel`/`DriverModel`. O conserto vale para API e web; no web, o motorista Google passa a receber erro de validação até a tela ganhar os campos de motorista.
- **Fase final (depois do mobile)**: remover `authorization_code` e `redirect-uris` do cliente `vanep-mobile`. **BREAKING** para builds antigas do app.

**Aceito conscientemente:** o cadastro revela e-mail/CPF já usado, que é o padrão de mercado, com rate limit.

**Fora de escopo:** Sign in with Apple e publicação iOS; mudanças no `vanep-frontend` e nas páginas Thymeleaf; confirmação de troca de e-mail por código no perfil mobile; DPoP, MFA, passkeys, App Links/Universal Links, Play Integrity/App Attest. As fases mobile (5–8 da issue) são implementadas em `vanep-mobile` e aparecem aqui só como dependentes no plano.

## Capabilities

### New Capabilities

- `mobile-token-grants`: emissão de token para o app nativo no `/oauth2/token`. Cobre a autenticação do cliente público `vanep-mobile`, o refresh com rotação, o grant de senha (sem enumeração, com lockout uniforme e `last_login_at`), o grant Google (validação do `id_token`, `signup_ticket`) e os códigos de erro OAuth.
- `auth-email-codes`: códigos de 6 dígitos para verificação de e-mail e reset de senha, com geração, armazenamento, TTL, tentativas, cooldown, limite diário por conta e e-mails com código + link.
- `auth-public-api`: API REST pública de conta (`/api/auth/**`): cadastro por tipo, conclusão de cadastro Google, verificação, reenvio, esqueci/reset de senha, envelope de erro, liberação no Security e rate limit por endereço confiável.

### Modified Capabilities

- _(nenhuma; não existe spec main de auth em `openspec/specs/`)_

## Impact

- **Código (backend):**
  - `auth/config`: `AuthorizationServerConfig` e `SecurityConfig`;
  - `auth/oauth`: `OAuthAccountService` e o novo pacote de grants;
  - `auth/web`: `RegistrationService`, `RegistrationController` e `SignupController`;
  - `auth/verification` e `auth/password`: services e repositórios de token;
  - `auth/security`: `VanepUserDetailsService`, `LoginAttemptService`, `RateLimitingFilter` e `AuthenticationEventsListener`;
  - templates `email/verification.html` e `email/password-reset.html`.
- **Schema:** nova migration nas tabelas `email_verification_token` e `password_reset_token` (hash do código + tentativas); nova tabela `signup_ticket`.
- **Config/env:** allowlist de `aud` do Google, JWKS URI do Google, TTL do ticket, limites dos códigos (tentativas, cooldown, máximo por 24 h). O default de `VANEP_MAIL_RESET_TTL_MINUTES` passa a 15. Tudo documentado em `.env.example`; testes com endereços inalcançáveis (regra 50).
- **APIs:** novos grants em `/oauth2/token`; novas rotas públicas `/api/auth/**`.
- **Dependências:** nenhuma nova. A validação do `id_token` usa o JOSE que o Spring Security já traz.
- **Consumidores:**
  - `vanep-mobile`: fases 5–8, que dependem das fases de backend;
  - `vanep-frontend`: sem mudança;
  - operação: tokens de reset emitidos antes do deploy continuam valendo até expirar.
