# QA — async-job-queue (VAN-106)

Constitution rule 55. Everything runs against throwaway infrastructure, never the shared dev database.

## How to run

```bash
# Compose topology (isolated project van106qa, own .env and volume; tears down on exit)
./mvnw -DskipTests package && scripts/qa/async-job-queue/compose-qa.sh
```

The local runs below (`qa.sh` against a disposable Postgres, API and worker processes) were executed during development; those scripts were not kept in the repository.

## Results

| Run | Passed |
|---|---|
| `qa.sh`, `QA_FCM_MODE=disabled` | 40 / 40 |
| `qa.sh`, `QA_FCM_MODE=unreachable` | 46 / 46 |
| `compose-qa.sh` | 13 / 13 |
| `./mvnw verify` (final code) | 2615 tests, coverage gate met |

The two `qa.sh` runs were recorded before the last two fixes (network failures with no FCM error code now retry; a missing handler now logs a warning). Those fixes are covered by unit and integration tests; `compose-qa.sh` and `verify` ran on the final code.

## Scenarios

| Group | Scenario | Expected |
|---|---|---|
| Enqueue (E1–E14) | chat message / driver rejection store the notification before the response and enqueue one `SEND_PUSH` job; blank, unauthenticated and malformed requests store and enqueue nothing; rejecting twice enqueues nothing; with the worker off jobs stay `PENDING` | as stated |
| Delivery, push off (W1–W3) | worker drains the queue, nothing fails, no duplicate notification | 0 jobs, 0 failed |
| Delivery, push failing (W0–W7) | recipient without devices completes; failing device retried with backoff, payload narrowed to that device; after 5 attempts → `failed_jobs`; no duplicate notification; devices kept | as stated |
| Queue mechanics (M1–M7) | removed type, corrupt payload, vanished notification → permanent, 1 attempt, queue keeps flowing; job left `PROCESSING` by a dead worker recovered as a failed attempt | as stated |
| Replay (R1–R8) | one run sends back only the batch size; failed replay keeps the row with `tries + 1`; never two rows per job; permanent rows skipped; successful replay deletes the row; row at the try limit never replayed | as stated |
| Two workers (T1–T4) | 40-job backlog drains, each job processed exactly once, both workers take part | 40 once each |
| Crash / stop (S1–S4) | SIGKILL of one worker, the other keeps draining; graceful stop leaves nothing `PROCESSING` and logs no error | as stated |
| Compose (C1–C9, D1–D4) | API up, both containers running with no restarts, worker publishes no port, worker only in `vanep-worker`, seeders only in the API, separate log files, Flyway once, worker container processes the queue; CD worker check passes when healthy and fails when stopped; `stop` is graceful within the 45 s grace period | as stated |

## Findings fixed during QA

1. Network failures reach the SDK as per-device errors with no FCM error code; they were not retried. Now treated as transient.
2. A job whose type was removed from the code would break every claim (enum mapping). `type` is now stored as text and such a job fails permanently.
3. Two JVMs each sizing the heap at 75% of host RAM were OOM-killed on boot (2 GiB VM). The worker now defaults to `MaxRAMPercentage=25` (`WORKER_JAVA_OPTS` overrides). Check the VPS RAM before deploying.
