# Tasks — absence-crud

Entrega faseada em PRs empilhados, cada fase com testes + `make lint` e `make test-coverage`.

## Fase 1 - DB, Model e Repository
- [ ] Criar enum `AbsenceSource` (CLIENT, DRIVER, ASSISTANT)
- [ ] Criar enum `AbsencePeriod` (MORNING, AFTERNOON, ALL_DAY) para o DTO do cliente
- [ ] Criar migration Flyway para a tabela `absence` (`trip_id` NOT NULL, índice único `(dependent_id, trip_id)`)
- [ ] Criar entidade JPA `Absence`
- [ ] Criar `AbsenceRepository` e `AbsenceRepositoryTest`

## Fase 2 - Service e regras de negócio
- [ ] Implementar `AbsenceService` (lógica central)
- [ ] Implementar a lógica de conversão do `AbsencePeriod` para buscar as viagens corretas do dia
- [ ] Tratar idempotência (`DataIntegrityViolationException`)
- [ ] Integrar com Trip/Checklist: remover `ChecklistEntry` PENDING
- [ ] Integrar com Notificação (FCM)
- [ ] Testes unitários do `AbsenceService` (testar cenários de Ida, Volta e Dia Todo)

## Fase 3 - API (Controllers e DTOs)
- [ ] Criar Request / Response DTOs
- [ ] Criar endpoints `POST /api/absences/client` e `POST /api/absences/driver`
- [ ] Configurar segurança (autorização por Role e Ownership)
- [ ] Testes integrados com `MockMvc` cobrindo o fluxo completo
