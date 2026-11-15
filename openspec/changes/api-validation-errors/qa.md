# QA — api-validation-errors (constitution rule 55)

Latest run: API built from `fix/van-55-validation-errors` against a throwaway PostgreSQL 17
container. Run date: 2026-11-03. Result: **4/4 passed**.

## Scope

The executable QA checks the four HTTP contracts affected by VAN-55 against a running API:

| id | contract |
|---|---|
| G1 | global `ProblemDetail`, including a resolved contract field error |
| G2 | complete nested path for dependent validation |
| P1 | backward-compatible profile e-mail envelope |
| A1 | backward-compatible auth envelope and deterministic advice precedence |

All requests use real OAuth tokens from the mobile password grant. Rejected requests do not modify
application data.

## How to run

Start the branch with the `local` profile and seed enabled on a throwaway PostgreSQL database, then
run:

```bash
BASE_URL=http://localhost:8081 scripts/qa/api-validation-errors/qa.sh
```

The script requires `curl` and `jq` and stops on the first mismatch.

## Results

| id | scenario | expected | observed | result |
|---|---|---|---|---|
| G1 | Global response exposes the resolved contract error | 400 with `dueDay` in `errors` | 400 with resolved pt-BR error | PASS |
| G2 | Nested validation keeps the complete field path | 400 with `address.zipCode` | 400 with `address.zipCode` | PASS |
| P1 | Profile e-mail envelope remains backward compatible | 400 with `email_required` | 400 with legacy profile envelope | PASS |
| A1 | Auth envelope wins on the shared controller | 400 with both field errors | 400 with legacy auth envelope | PASS |
