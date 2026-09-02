# Tasks & Implementation Plan: Client Dependent Management (S14)

## PR Plan Table

| Phase | Contents | Depends on | Parallel with |
| :--- | :--- | :--- | :--- |
| **Phase 1** | OpenSpec proposal & specification validation for Screen S14 | None | None |
| **Phase 2** | UI Component Foundation: Frame S14 Header, Card, Avatar & Badge | Phase 1 | None |
| **Phase 3** | Dependent Form (Create / Edit) & Address Input Component | Phase 2 | None |
| **Phase 4** | API Integration (`/api/dependent`), State Management & RN12 Default Toggle | Phase 3 | None |
| **Phase 5** | End-to-End Verification, Responsive Polish & Integration Tests | Phase 4 | None |

---

## Detailed Task Breakdown

### Phase 1: OpenSpec & Specification Setup
- [x] Create change folder `openspec/changes/client-dependent-management/`
- [x] Write `proposal.md` aligning Figma plugin code for S14 with backend Issue #25
- [x] Write `specs/client-dependent-management/spec.md` with Gherkin BDD scenarios
- [x] Write `design.md` detailing Figma design tokens, components, and API payload schemas
- [x] Write `tasks.md` with phased implementation plan

### Phase 2: UI Component Foundation (Screen S14)
- [ ] Implement `ScreenS14Dependentes` container frame (Padding: 32px, Header Title: "Dependentes", Subtitle: "Cada contrato é vinculado a um dependente.")
- [ ] Implement `DependentCard` component (Height: 80px, Avatar: 48px, Name, BirthDate `Nasc.: DD/MM/AAAA`)
- [ ] Implement `DefaultBadge` ("Padrão" badge in primary blue style when `isDefault == true`)
- [ ] Implement `AddDependentButton` (`+ Adicionar dependente` button fixed at bottom)
- [ ] Implement `BottomNavigation` bar with "Início" active tab

### Phase 3: Add & Edit Form (Modal / Screen)
- [ ] Implement `DependentModalForm` for creation and editing
- [ ] Add personal info inputs: Name (required), Birth Date, Gender picker, Document, Phone, Email, Shift
- [ ] Add `AddressSection` inputs: CEP lookup, Street, Number, Complement, Neighborhood, City, State
- [ ] Add `SetDefaultToggle` checkbox/switch to flag dependent as default (RN12)
- [ ] Add front-end form validation rules (required name, valid date format)

### Phase 4: API Integration & RN12 Logic
- [ ] Connect `GET /api/dependent` to populate the list on Screen S14
- [ ] Connect `POST /api/dependent` to handle new dependent creation
- [ ] Connect `PATCH /api/dependent/{token}` for editing dependent info and toggling default status
- [ ] Connect `DELETE /api/dependent/{token}` for soft deletion with confirmation modal
- [ ] Implement automatic UI update when default dependent is changed or promoted (RN12)
- [ ] Handle error states (400, 401, 403, 409) with friendly user messages in `pt-BR`

### Phase 5: Verification & Polish
- [ ] Test layout responsiveness on Android and iOS screen dimensions
- [ ] Validate empty state ("Nenhum dependente cadastrado ainda")
- [ ] Validate loading skeletons/spinners during API fetch
- [ ] Run end-to-end user flow test (List -> Add -> Toggle Default -> Edit -> Delete)
