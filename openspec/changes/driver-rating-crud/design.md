## Context

A funcionalidade de avaliações de motoristas (`driver_rating`) permite relacionar um `Client` com um `Driver`, atribuindo uma nota de 1 a 5 estrelas e um comentário opcional. Além de armazenar cada avaliação individualmente, a média dessas notas deve refletir no campo `rating` do motorista (`DriverModel`).

---

## Goals / Non-Goals

**Goals:**
- Criar a migration `V16__create_driver_rating_table.sql` com Soft Delete e índice único por cliente/motorista ativo.
- Criar a entidade JPA `DriverRatingModel` com suporte a `@SoftDelete`.
- Implementar DTOs para Request e Response com validações Bean Validation (nota entre 1.00 e 5.00).
- Expor os 5 endpoints REST sob `/api/driver-ratings`.
- Recalcular dinamicamente a média (`AVG`) do motorista a cada operação (criação, edição, remoção, restauração).
- Adicionar validação de propriedade no `SecurityEvaluator` (`isDriverRatingOwner`).
- Testes unitários (`DriverRatingServiceTest`) e testes de controle HTTP (`DriverRatingControllerTest`).

**Non-Goals:**
- Auto-avaliação (motorista não pode avaliar a si mesmo).

---

## Decisions

### D1 — Schema do Banco de Dados (`V16`)
```sql
create table driver_rating (
    id          bigint generated always as identity primary key,
    token       varchar(32) not null unique,
    driver_id   bigint not null references driver(id),
    client_id   bigint not null references client(id),
    rating      numeric(3, 2) not null check (rating >= 1.00 and rating <= 5.00),
    comment     text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);

create unique index idx_driver_rating_driver_client_unique
    on driver_rating (driver_id, client_id)
    where deleted_at is null;
```

### D2 — Endpoints REST sob `/api/driver-ratings`

1. **`POST /api/driver-ratings`**
   - **Autorização:** `hasAuthority('create_driver_rating')` (Cliente logado).
   - **Regra:** Valida se o cliente logado é o autor (`clientId`). Garante que um motorista não pode se auto-avaliar.

2. **`GET /api/driver-ratings`**
   - **Autorização:** `hasAuthority('list_driver_ratings')`.
   - **Parâmetros:** `driverToken` (opcional) e `Pageable`.

3. **`GET /api/driver-ratings/{token}`**
   - **Autorização:** `hasAuthority('show_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)`.

4. **`PUT /api/driver-ratings/{token}`**
   - **Autorização:** `hasAuthority('update_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)`.

5. **`DELETE /api/driver-ratings/{token}`**
   - **Autorização:** `hasAuthority('delete_driver_rating') or @sec.isDriverRatingOwner(#token, authentication)`.

6. **`POST /api/driver-ratings/{token}/restore`**
   - **Autorização:** `hasAuthority('restore_driver_rating')` (Apenas `ROLE_ADMIN`).

### D3 — Recálculo da Média do Motorista
A cada alteração na tabela `driver_rating`, o `DriverRatingService` chamará a query agregada no `DriverRatingRepository`:
```java
@Query("SELECT AVG(dr.rating) FROM DriverRatingModel dr WHERE dr.driver.id = :driverId")
Optional<BigDecimal> calculateAverageRatingForDriver(@Param("driverId") Long driverId);
```
O valor calculado será formatado e salvo no atributo `rating` do `DriverModel`.

---

## Risks / Trade-offs

- **Concorrência na Média:** A média é atualizada de forma síncrona a cada avaliação enviada. Como o volume por motorista em curto intervalo é baixo, a performance é excelente.
