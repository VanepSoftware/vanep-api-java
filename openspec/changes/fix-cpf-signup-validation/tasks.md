## 0. Preparation

- [x] 0.1 Create branch `fix/signup-cpf-validation` from `main` (see proposal notes for alternatives)
- [x] 0.2 Review artifacts (`proposal.md`, `design.md`, `specs/signup-cpf-validation/spec.md`)
- [x] 0.3 Confirm with team: duplicate message stays “Já existe uma conta com este documento.” (not “CPF inválido”)

## 1. PR — CPF validation on signup (single PR preferred)

> Goal: validator + wire signup flows + tests. Fits CONSTITUTION size caps as one PR.
> Depends on: — | Parallel with: —
> Order: test → validator → MessageSource → forms/controller → fixtures (CONSTITUTION 39 adapted; no migration).

- [x] 1.1 Add failing unit tests for `CpfValidator` (valid, invalid check digits, all identical, masked valid/invalid, blank)
- [x] 1.2 Implement `br.com.vanep.auth.validation.CpfValidator` (`normalize`, `isValid`)
- [x] 1.3 Add Bean Validation annotation `@Cpf` + constraint validator wired to `CpfValidator`
- [x] 1.4 Add MessageSource keys `signup.document.invalid` and `signup.document.duplicate` (EN + `messages_pt_BR.properties`)
- [x] 1.5 Annotate `AccountSignupForm.document` and `SignupForm.document` with `@Cpf` (keep `@NotBlank`)
- [x] 1.6 Update `RegistrationController.rejectDuplicates` to skip document duplicate check when `document` already has errors; resolve duplicate message via MessageSource; normalize document before `existsByDocument` / register
- [x] 1.7 Apply the same validate → duplicate → normalize behavior on `POST /signup/complete` (SignupController or OAuth complete path)
- [x] 1.8 Update `RegistrationControllerTest` (and related) to use valid CPF fixtures; add cases: invalid CPF → form error message; valid duplicate → duplicate message; valid unique → redirect
- [x] 1.9 Optionally update `DataSeeder` documents to valid unique CPFs (boy scout; avoid invalid seed docs colliding with demos)
- [x] 1.10 Run `./mvnw spotless:apply` then `./mvnw spotless:check` and `./mvnw verify`
- [ ] 1.11 Open PR in pt-BR with test/lint status and `Closes #59`

## 2. Wrap-up

- [ ] 2.1 Team review / merge
- [ ] 2.2 Archive OpenSpec change (`/opsx:archive`)
