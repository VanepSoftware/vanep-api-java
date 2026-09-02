# Design Specification: Client Dependent Management (S14)

## Architecture Overview

The **Client Dependent Management (S14)** feature implements the user interface and frontend state management for the client mobile/web application. It interfaces directly with the existing REST endpoints exposed by `vanep-api-java` (`/api/dependent`).

```
[ Client App (S14 UI) ] 
       │ 
       ├─────── GET /api/dependent ──────────────► [ List Active Dependents ]
       ├─────── POST /api/dependent ─────────────► [ Create Dependent + Address ]
       ├─────── PATCH /api/dependent/{token} ───► [ Update Dependent / Set Default ]
       └─────── DELETE /api/dependent/{token} ──► [ Soft Delete Dependent ]
```

---

## Visual Design & Figma Plugin Alignment (S14)

Based on the Figma plugin specifications (`s14 = mkFrame('14 · Cliente — Dependentes')`):

### 1. Color Palette & Design Tokens
- **Background**: `#FFFFFF` / Dark mode adapted `#0F172A`
- **Text Primary**: `BLACK` (`#1E293B`)
- **Text Secondary**: `GRAY` (`#64748B`)
- **Accent Color / Primary Button**: `BLUE` (`#2563EB`)
- **Badge Background**: `BLUE_LIGHT` (`#DBEAFE`) with `BLUE` text

### 2. Layout Structure & Spacing
- **Frame Title**: "Dependentes" (Font size: 22px, Weight: Bold, X: 32px, Y: 60px)
- **Subtitle**: "Cada contrato é vinculado a um dependente." (Font size: 13px, Color: Gray, X: 32px, Y: 92px)
- **Dependent Cards**:
  - Width: `W - 64px` (Padding 32px on left/right)
  - Height: `80px`
  - Avatar size: `48px` diameter
  - Name: Font size 15px, Medium weight, Y: 12px
  - Birth Date: `Nasc.: DD/MM/AAAA` (Font size 12px, Gray, Y: 34px)
  - Badge "Padrão": Positioned at Y: 52px when `isDefault == true`
- **Add Button (`+ Adicionar dependente`)**:
  - Size: Large, Width `W - 64px`, Primary Blue style
- **Bottom Navigation (`Nav`)**: Fixed at bottom with "Início" active.

---

## Component Architecture

```
ScreenS14Dependentes
├── StatusBar
├── HeaderSection
│   ├── Title ("Dependentes")
│   └── Subtitle ("Cada contrato é vinculado a um dependente.")
├── DependentList (Scrollable)
│   └── DependentCard (Repeated per dependent)
│       ├── Avatar (48px)
│       ├── InfoBlock (Name, BirthDate)
│       ├── DefaultBadge ("Padrão" if isDefault)
│       └── CardActions (Edit, Delete, Set Default)
├── AddDependentButton ("+ Adicionar dependente")
├── DependentModalForm (Add / Edit)
│   ├── PersonalDataFields (Name, BirthDate, Gender, Document, Phone, Email, Shift)
│   ├── AddressSection (CEP, Street, Number, Neighborhood, City, State)
│   └── SetDefaultToggle (RN12)
└── BottomNavigation ("Início", "Contratos", "Perfil")
```

---

## API Request & Response Schemas

### 1. List Dependents (`GET /api/dependent`)
Headers: `Authorization: Bearer <JWT>`  
Response (`HTTP 200 OK`):
```json
[
  {
    "token": "dep_x93ka820a178b664129a012",
    "clientToken": "cli_981a293b7a1122334455661",
    "name": "Pedro Silva",
    "birthDate": "2015-03-12",
    "gender": "MALE",
    "document": "12345678900",
    "phone": "(11) 98765-4321",
    "email": "pedro@email.com",
    "isSelf": false,
    "isDefault": true,
    "shift": "MORNING",
    "address": {
      "token": "addr_91823ab819",
      "cep": "01001-000",
      "street": "Praça da Sé",
      "number": "100",
      "complement": "Apto 12",
      "neighborhood": "Sé",
      "cityToken": "city_sp_01",
      "cityName": "São Paulo",
      "stateToken": "state_sp",
      "stateUf": "SP"
    }
  }
]
```

### 2. Create Dependent (`POST /api/dependent`)
Request Body:
```json
{
  "name": "Luiza Silva",
  "birthDate": "2018-11-08",
  "gender": "FEMALE",
  "shift": "AFTERNOON",
  "isDefault": false,
  "address": {
    "cep": "01001-000",
    "street": "Praça da Sé",
    "number": "100",
    "neighborhood": "Sé",
    "cityToken": "city_sp_01",
    "stateToken": "state_sp"
  }
}
```

### 3. Set Default Dependent (`PATCH /api/dependent/{token}`)
Request Body:
```json
{
  "isDefault": true
}
```

---

## Error Handling Strategy

- **HTTP 400 Bad Request**: Input validation errors (e.g. missing name, invalid CEP) displayed directly next to input fields.
- **HTTP 409 Conflict**: Document (CPF) duplicate alert modal.
- **HTTP 401 / 403**: Token expired or unauthorized — redirect to Login screen.
- **Offline / Network Error**: Toast message with retry action.
