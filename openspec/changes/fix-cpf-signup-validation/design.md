## Context

Signup de cliente/motorista é Thymeleaf neste repositório (`RegistrationController` + `signup-client.html` / `signup-driver.html`). OAuth complete usa `SignupController` + `SignupForm`. Hoje `AccountSignupForm.document` e `SignupForm.document` só têm `@NotBlank`. `RegistrationController.rejectDuplicates` chama `users.existsByDocument` sem validar CPF — e grava o valor como veio no form (com ou sem máscara).

Issue #59: CPF inválido aparece com mensagem de duplicidade. Correção: validar algoritmo **antes** da consulta de duplicidade.

**Nota sobre o texto da issue:** o critério “CPF válido mas já existe → mostrar CPF inválido” está incorreto. Este design **mantém** a mensagem de duplicidade nesse caso.

## Goals / Non-Goals

**Goals:**

- Validar CPF (com/sem máscara) antes de `existsByDocument`.
- Mensagens claras e distintas no campo `document`.
- Normalizar para 11 dígitos na persistência.
- Cobrir client, driver e OAuth complete.
- Testes unit + MockMvc; Spotless + verify verdes.

**Non-Goals:**

- Alterar `vanep-frontend` (marketing).
- Validação CNPJ / documentos de motorista.
- Job de limpeza de documentos inválidos já no banco.
- Máscara JS obrigatória no HTML.

## Decisions

### 1. Validador puro e testável

- Classe `CpfValidator` (ou `Cpf` util) em pacote shared de validação sob `auth` — ex.: `br.com.vanep.auth.validation.CpfValidator`.
- Métodos: `normalize(String)` (só dígitos), `isValid(String)` (normalize + algoritmo).
- Sem dependência de Spring/JPA (CONSTITUTION 8–9, 23–24).
- Rejeitar: null/blank após normalize, length ≠ 11, todos dígitos iguais, dígitos verificadores incorretos.

### 2. Onde aplicar a regra

| Fluxo | Ponto |
|-------|--------|
| `POST /signup/client` | `RegistrationController` — validar CPF; se inválido `rejectValue`; senão duplicidade; normalizar no form antes do service |
| `POST /signup/driver` | idem |
| `POST /signup/complete` | `SignupController` (ou service OAuth) — mesma ordem |

Preferência: extrair método compartilhado `rejectInvalidOrDuplicateDocument(String raw, BindingResult)` para não duplicar.

Alternativa Bean Validation `@Cpf`: válida, mas a **ordem** válido→duplicidade fica mais clara no controller/service; pode combinar `@Cpf` no form **e** duplicidade no controller.

**Decisão:** `@Cpf` custom constraint no form (mensagem inválido) **+** `rejectDuplicates` só se não houver erro em `document` ainda. Assim Bean Validation roda com `@Valid` antes, e duplicidade não sobrescreve mensagem de inválido.

### 3. Mensagens (MessageSource)

| Key | EN default | pt-BR |
|-----|------------|-------|
| `signup.document.invalid` | Invalid CPF. Check the numbers entered. | CPF inválido. Verifique os números informados. |
| `signup.document.duplicate` | An account with this document already exists. | Já existe uma conta com este documento. |

Não hardcodar pt-BR no `rejectValue` (CONSTITUTION 43). Hoje o controller hardcoda strings pt-BR — nesta change migrar as de documento (e idealmente e-mail) para MessageSource.

### 4. Normalização

Antes de `existsByDocument` e de `user.setDocument`: gravar apenas dígitos (`normalize`). Assim `529.982.247-25` e `52998224725` colidem corretamente.

### 5. Impacto em testes e seeds

- `RegistrationControllerTest` hoje usa `11111111111` (inválido) e espera sucesso → trocar por CPF válido de fixture (ex.: `39053344705`).
- Seeds (`00000000000`, `11111111111`, …) **não** passam pelo form; podem permanecer nesta change **ou** ser atualizados para CPFs válidos distintos para evitar confusão em demos. Preferência: atualizar seeds de client/driver/admin para CPFs válidos únicos (boy scout), se couber no mesmo PR sem estourar escopo.

### 6. PR plan

| Phase | Contents | Depends on | Parallel with |
|-------|----------|------------|---------------|
| PR1 | `CpfValidator` + `@Cpf` + unit tests + MessageSource keys | — | — |
| PR2 | Wire RegistrationController / SignupController + normalize + MockMvc + adjust fixtures | PR1 | — |

Para mudança pequena, **aceitável um único PR** se &lt; ~600 linhas / 10 arquivos (CONSTITUTION 38). Preferência do time: **1 PR** `fix(signup): validar CPF antes da duplicidade`.

```
CpfValidator (puro)
    │
    ▼
@Cpf + MessageSource
    │
    ▼
RegistrationController + SignupController + tests/fixtures
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Testes/seeds com CPF inválido quebram | Atualizar fixtures na mesma change |
| Documentos já inválidos no banco | Fora de escopo; signup novo fica correto |
| Issue pedia mensagem errada no duplicado | Design corrige; documentar no PR |
| Hardcode pt-BR legado no controller | Migrar keys de document (e email se tocado) |

## Migration Plan

Sem Flyway. Deploy = código apenas. Rollback = reverter commit/PR.

## Open Questions

- _(fechado)_ Mensagem de duplicidade permanece “Já existe uma conta com este documento.”
- _(fechado)_ Escopo inclui OAuth `/signup/complete`.
