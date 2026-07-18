## Context

A entidade `DriverModel` e a tabela `driver` já existem (migration `V3`). A tabela possui suporte a Soft Delete via coluna `deleted_at`. Entretanto, alguns campos definidos no banco (como `bio`, `work_start_time`, `work_end_time`, `work_days`, `wait_tolerance_minutes` e `service_areas`) não estão mapeados no JPA em `DriverModel.java`. Além disso, não há controller, service, mapper, DTOs ou testes para `driver`.

O CRUD de `driver` seguirá o mesmo padrão arquitetural definido na `constitution.md`:
- `token` como identificador público (25 caracteres).
- DTOs separados para Request e Response.
- Mapper centralizado em `DriverMapper`.
- Lógica de negócio isolada em `@Service`.
- Permissões explícitas mapeadas no `PermissionEnum`.

---

## Goals / Non-Goals

**Goals:**
- Mapear os campos ausentes no `DriverModel` (`bio`, `workStartTime`, `workEndTime`, `workDays`, `waitToleranceMinutes`, `serviceAreas`).
- Expor 5 endpoints REST sob `/api/drivers` para gerenciamento de perfis.
- Proteger rotas com base em papéis (`ROLE_ADMIN`, `ROLE_CLIENT`, `ROLE_DRIVER`) e permissões (`list_drivers`, `show_driver`, `update_driver`, `delete_driver`, `restore_driver`).
- Garantir segurança a nível de método (motoristas só podem atualizar seu próprio perfil).
- Cobrir a feature com testes unitários e de integração HTTP (MockMvc) atingindo JaCoCo >= 75%.

**Non-Goals:**
- Criar motorista via `POST /api/drivers` (responsabilidade exclusiva do fluxo de signup em `RegistrationService`).

---

## Decisions

### D1 — Novas Permissões
Adicionaremos no `PermissionEnum.java`:
- `LIST_DRIVERS("list_drivers")`
- `SHOW_DRIVER("show_driver")`
- `UPDATE_DRIVER("update_driver")`
- `DELETE_DRIVER("delete_driver")`
- `RESTORE_DRIVER("restore_driver")`

### D2 — Mapeamento JSONB
Para os campos `work_days` e `service_areas` no `DriverModel`, utilizaremos o mapeamento de JSON do Hibernate 6:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "work_days", columnDefinition = "jsonb")
private List<String> workDays;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "service_areas", columnDefinition = "jsonb")
private List<String> serviceAreas;
```

### D3 — Endpoints REST sob `/api/drivers`

1. **`GET /api/drivers`**
   - **Descrição:** Lista motoristas de forma paginada (`Page<DriverResponseDTO>`).
   - **Autorização:** Apenas `ROLE_ADMIN` e `ROLE_CLIENT` (clientes precisam ver motoristas disponíveis).
   - **Permissão:** `list_drivers`.

2. **`GET /api/drivers/{token}`**
   - **Descrição:** Detalha um motorista pelo seu token.
   - **Autorização:** `ROLE_ADMIN`, `ROLE_CLIENT` ou o próprio motorista.
   - **Permissão:** `show_driver` ou propriedade (ownership).

3. **`PUT /api/drivers/{token}`**
   - **Descrição:** Atualiza os dados de perfil de um motorista.
   - **Campos Editáveis:** `photo`, `bio`, `cnpj`, `experienceYears`, `city`, `basePrice`, `workStartTime`, `workEndTime`, `workDays`, `waitToleranceMinutes`, `serviceAreas`, `available`.
   - **Nota:** Os campos `approvalStatus`, `rating`, `token` e `user` **não** podem ser alterados pelo motorista. `approvalStatus` só pode ser alterado por administradores.
   - **Autorização:** `ROLE_ADMIN` ou o próprio motorista.
   - **Permissão:** `update_driver` ou propriedade (ownership).

4. **`DELETE /api/drivers/{token}`**
   - **Descrição:** Realiza a deleção lógica (soft delete) do perfil.
   - **Autorização:** `ROLE_ADMIN` ou o próprio motorista.
   - **Permissão:** `delete_driver` ou propriedade (ownership).

5. **`POST /api/drivers/{token}/restore`**
   - **Descrição:** Restaura um perfil deletado.
   - **Autorização:** Apenas `ROLE_ADMIN`.
   - **Permissão:** `restore_driver`.

### D4 — Verificação de Propriedade (Ownership)
Usaremos um `DriverSecurityService` anotado com `@Service("driverSecurity")` para validação no nível de `@PreAuthorize`:
```java
@PreAuthorize("hasAuthority('update_driver') or @driverSecurity.isOwner(#token, authentication)")
```
A verificação compara o `uid` (token do usuário) presente no JWT com o token do usuário associado ao `DriverModel`.

---

## Risks / Trade-offs

- **Campos JSONB no H2 (Testes):** O H2 em testes suporta o tipo JSON/JSONB nativamente no dialeto moderno do Hibernate. Não deve haver problemas de compatibilidade nos testes em memória.
- **Campos não mapeados no signup:** Os novos campos como `workDays` iniciarão como `null`/vazio no signup. O motorista deverá atualizá-los posteriormente no endpoint de `PUT`.
