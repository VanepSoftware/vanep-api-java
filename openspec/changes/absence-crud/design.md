## Context
Atualmente as viagens geram rotas com base nos dependentes ativos e presentes. O caso de uso UC10 (Cancelamento de Presença) exige que clientes e motoristas possam sinalizar que um aluno não vai embarcar. O cliente pode escolher faltar o dia todo ou apenas em uma viagem (ida ou volta). Isso afeta o checklist da rota (RN-15).

A stack: Java 25 / Spring Boot 4 / Flyway. Banco PostgreSQL. Testes H2. Clean Architecture. Padrão de feature por pacote (`br.com.vanep.absence`).

## Goals / Non-Goals
**Goals:**
- Tabela `absence` e migração Flyway com `trip_id` obrigatório e constraint unique `(dependent_id, trip_id)`.
- API REST para Cliente (source CLIENT) enviar ausência para o dia atual. O backend converte o desejo (ida, volta, ambas) nos respectivos registros atrelados ao `trip_id`.
- API REST para Motorista (source DRIVER) enviar ausência para a viagem atual.
- Remover do checklist (`ChecklistEntry`) da viagem afetada o aluno associado, marcando a parada para exclusão de recálculo.
- Disparar notificação (Push) para atualizar o aplicativo do motorista/cliente (RN-15).
- Garantir a idempotência.

**Non-Goals:**
- Desfazer o "Avisar Ausência" (não suportado na V1 para evitar complexidade de re-inserção na rota).
- Notificações de SMS ou Email.

## Schema
```sql
CREATE TYPE absence_source AS ENUM ('CLIENT', 'DRIVER', 'ASSISTANT');

CREATE TABLE absence (
    id SERIAL PRIMARY KEY,
    token VARCHAR NOT NULL UNIQUE,
    contract_id INTEGER NOT NULL REFERENCES contracts(id),
    dependent_id INTEGER NOT NULL REFERENCES dependents(id),
    trip_id INTEGER NOT NULL REFERENCES trips(id),
    absence_date DATE NOT NULL,
    source absence_source NOT NULL,
    reason VARCHAR(255),
    notified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_absence_dependent_trip ON absence (dependent_id, trip_id);
```

## API Changes
`POST /api/absences/client`
- Body: `{ "dependentId": 123, "absenceDate": "2026-10-05", "period": "MORNING" | "AFTERNOON" | "ALL_DAY" }`
- Auth: Cliente (dono do dependente).
- Response: 201 Created

`POST /api/absences/driver`
- Body: `{ "dependentId": 123, "tripId": 456, "reason": "Não estava na parada", "absenceDate": "2026-10-05" }`
- Auth: Motorista/Assistente.
- Response: 201 Created

## Implementation Details
1. **Entidade**: `Absence` mapeada no JPA. Enum `AbsenceSource` e `AbsencePeriod`.
2. **Controller/Service**:
   - `ClientAbsenceController` e `DriverAbsenceController`
   - `AbsenceService` processa a regra de negócio:
     - Valida ownership / vínculo ativo.
     - Se `period == ALL_DAY`, busca as viagens de ida e volta do dia e salva duas `Absence`s. Se for `MORNING` ou `AFTERNOON`, salva apenas uma.
     - Idempotência: Se houver violação de constraint `(dependent_id, trip_id)`, ignora ou retorna a existente.
     - Interage com `ChecklistService` para invalidar/excluir as entries PENDING das trips afetadas para este aluno.
     - Chama `NotificationService` para enviar push à contraparte.
