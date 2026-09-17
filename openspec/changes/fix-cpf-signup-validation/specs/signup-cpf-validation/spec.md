## ADDED Requirements

### Requirement: CPF algorithm validation on signup

The system MUST validate Brazilian CPF check digits before accepting a signup document. Validation MUST strip non-digit characters (mask) and require exactly 11 digits after normalization. The system MUST reject CPFs where all digits are identical and MUST reject CPFs with incorrect check digits.

#### Scenario: Invalid CPF rejected with clear message

- **WHEN** a user submits signup with an invalid CPF (wrong check digits or all identical digits), with or without mask
- **THEN** the system does not create the account
- **AND** the document field shows the message key resolved to pt-BR: `CPF inválido. Verifique os números informados.`

#### Scenario: Masked valid CPF accepted for format check

- **WHEN** a user submits a valid CPF with punctuation (e.g. `390.533.447-05`)
- **THEN** the system treats it as the same value as the digit-only form for validation purposes

### Requirement: Validate before duplicate check

The system MUST run CPF validity checks before querying whether the document already exists. The system MUST NOT report a duplicate-document error when the CPF is invalid.

#### Scenario: Invalid CPF never reported as duplicate

- **WHEN** an invalid CPF is submitted and that string might or might not exist in the database
- **THEN** the user-facing error for the document field is the invalid-CPF message
- **AND** not `Já existe uma conta com este documento.`

### Requirement: Distinct message for duplicate valid CPF

When the CPF is valid and an active user already has that document, the system MUST reject signup and show the duplicate-document message on the document field: `Já existe uma conta com este documento.`

#### Scenario: Valid CPF already registered

- **WHEN** a user submits a valid CPF that already belongs to an active account
- **THEN** the system does not create another account
- **AND** the document field shows the duplicate message (not the invalid-CPF message)

### Requirement: Persist normalized digits only

The system MUST persist and look up `users.document` using digits only (no dots, dashes, or spaces).

#### Scenario: Masked and unmasked collide

- **WHEN** an account exists with document `39053344705`
- **AND** a new signup submits `390.533.447-05`
- **THEN** the system detects a duplicate

### Requirement: Coverage of signup entry points

The same CPF validation and ordering rules MUST apply to:

- `POST /signup/client`
- `POST /signup/driver`
- `POST /signup/complete` (OAuth profile completion)

#### Scenario: Client signup with invalid CPF

- **WHEN** `POST /signup/client` is submitted with an invalid CPF and otherwise valid fields
- **THEN** the response re-renders the signup form with HTTP 200 (no redirect to login)
- **AND** the document field has the invalid-CPF error

#### Scenario: Client signup with valid unused CPF

- **WHEN** `POST /signup/client` is submitted with a valid unused CPF and otherwise valid fields
- **THEN** the system creates the user and redirects to `/login?registered`

### Requirement: Automated tests

The change MUST include unit tests for the CPF validator (valid, invalid check digits, all-same digits, masked input) and integration/MockMvc tests for signup covering: invalid CPF, valid already registered, valid not registered.

#### Scenario: Unit tests cover core cases

- **WHEN** the test suite runs
- **THEN** CPF validator tests assert acceptance of known-valid CPFs and rejection of invalid ones including masked invalid input
