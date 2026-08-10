## Why

No cadastro (`/signup/client`, `/signup/driver` e `/signup/complete`), o backend só exige CPF não vazio e depois checa duplicidade em `users.document`. Não há validação dos dígitos verificadores. CPFs inválidos podem ser persistidos; em tentativas seguintes (ou quando o valor já está no banco de teste/seed) o usuário vê “Já existe uma conta com este documento.”, o que mascara o erro real. GitHub issue #59 (N-59). Prioridade: **Média**.

## What Changes

- Novo validador de CPF (algoritmo brasileiro): strip de máscara, 11 dígitos, rejeição de sequências iguais, dígitos verificadores.
- Ordem no cadastro: **1)** validar CPF → **2)** só então checar `existsByDocument`.
- Mensagens distintas (pt-BR):
  - inválido → `CPF inválido. Verifique os números informados.`
  - válido + duplicado → `Já existe uma conta com este documento.` (mantém)
- Aplicar nos fluxos Thymeleaf: client, driver e complete (OAuth).
- Normalizar documento para apenas dígitos antes de persistir/consultar.
- Testes unitários do validador + testes de integração no signup.
- Atualizar testes/seeds que hoje usam CPFs inválidos (ex.: `11111111111`) onde o fluxo de signup passa pela nova validação.

**Fora de escopo:**
- Validação de CPF no frontend Next.js (`vanep-frontend` — não é esta tela)
- Upload/máscara visual avançada no HTML (opcional: strip no server basta)
- Validação de CNPJ do motorista
- Migração de CPFs inválidos já gravados em produção (follow-up se necessário)

## Capabilities

### New Capabilities

- `signup-cpf-validation`: validação de CPF no cadastro (ordem válido → duplicidade), normalização e mensagens corretas no campo documento.

### Modified Capabilities

- _(nenhuma spec main pré-existente de signup)_

## Impact

- **Código:** `RegistrationController`, possivelmente `SignupController` / `OAuthAccountService.completeRegistration`, forms `AccountSignupForm` / `SignupForm`, novo validador compartilhado sob `auth` (ou shared validation).
- **Mensagens:** MessageSource keys (EN default + `messages_pt_BR.properties`) — CONSTITUTION regra 43.
- **Testes:** `RegistrationControllerTest` e novos unit tests; fixtures com CPF válido.
- **Sem migration** Flyway.
- **Delivery:** branch única + 1–2 PRs pequenos; `Closes #59`.
