## Context

Today `NotificationEventListener` handles six domain events (chat message, proposal sent/accepted/declined, contract created, driver reviewed) with `@Async("pushNotificationExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`. Each handler resolves the recipient and calls `NotificationService.notifyUser`, which **saves the in-app `notification` row and then sends the FCM push**. The executor is in-memory (2–4 threads, queue 1000) and drops work when full. `PushNotificationService` catches FCM errors and only logs them.

The only scheduled work is `ExpirySweepService`. `SchedulingConfig` enables scheduling **only when `vanep.expiry-sweep.enabled` is true** and gives every `@Scheduled` method one single-thread scheduler.

Production is Docker Compose on one VPS with 1 vCPU (`postgres` + `vanep`). The image is built in GitHub Actions, pulled from GHCR, started with `docker compose up -d --no-build` and health-checked through `/login`.

## Goals / Non-Goals

**Goals:**
- No notification is lost to a restart, a full queue or an FCM outage.
- The in-app notification and the push job are created atomically with the business change.
- A failing job never delays the others.
- Failures that outlive their retries are kept, counted and replayed in small doses at fixed times, never all at once.
- Generic: VAN-107 and the daily jobs reuse it by adding a `JobType` and a `JobHandler`.
- The worker runs in its own container, built from the same image as the API, so job processing does not compete inside the API's JVM.

**Non-Goals:**
- Broker, `workers` table, Supervisor.
- E-mail migration, daily jobs, admin UI/endpoint for failed jobs.
- Exactly-once delivery. The guarantee is at-least-once.
- Distributed locking for `ExpirySweepService`.

## Decisions

### D1. Queue in PostgreSQL, two tables
`jobs`: `id`, `token` (unique), `type`, `payload` (jsonb), `status` (`PENDING`, `PROCESSING`), `attempts`, `max_attempts`, `run_at`, `locked_at`, `locked_by`, `last_error`, `created_at`. Index on `(status, run_at)`.

`failed_jobs`: `id`, `token` (unique, the job's token), `type`, `payload`, `attempts` (attempts of the last cycle), `tries` (replay attempts so far), `permanent` (boolean), `exception` (text), `failed_at`, `last_tried_at`.

A finished job is **deleted**: the queue stays small, no cleanup job is needed, and the delivered notification is already recorded in `notification`. `locked_by` (host name + random suffix) records which instance claimed a job, for diagnosis; it does not take part in locking.

Soft delete (rule 19) does not apply: these are infrastructure rows, not removable domain models.

*Alternatives:* RabbitMQ/Kafka (new service, still needs an outbox to avoid the dual write); JobRunr/db-scheduler (dependency whose tables and retry model we do not control). Rejected for volume and operational weight.

### D2. Retry by rescheduling, policy per job type
When a handler throws, the job goes back to `PENDING` with `attempts + 1`, `last_error`, and `run_at = now + backoff`. The worker claims by `run_at <= now ORDER BY run_at`, so a rescheduled job simply is not due yet and the rest of the queue keeps flowing: **a failure never blocks the queue**.

The policy lives on the `JobType` enum (rule 14): `maxAttempts`, `backoffBase`, `backoffMax`, exponential `min(base * 2^(attempts-1), max)`. `SEND_PUSH`: 5 attempts, base 10 s, max 2 min (about 4 minutes of retrying; a push that late is still useful, a push hours late is not). Each future type sets its own.

Handlers signal two kinds of failure:
- Any exception → retry under the policy.
- `PermanentJobFailureException` → straight to `failed_jobs` with `permanent = true`. The worker also uses this path for an unknown type or an unreadable payload.

### D3. `failed_jobs` and the throttled replay
When `attempts` reaches `max_attempts`, the job is inserted into `failed_jobs` (`tries = 0`) and deleted from `jobs`.

`FailedJobReplayer` is a `@Scheduled(cron = vanep.worker.replay.cron, zone = "America/Sao_Paulo")` task, default every 2 hours from 08:00 to 22:00. Each run:
1. Selects at most `vanep.worker.replay.batch-size` rows (default 20) with `permanent = false`, `tries < vanep.worker.replay.max-tries` (default 5) and no job with the same token currently in `jobs`, ordered by `last_tried_at` nulls first, `FOR UPDATE SKIP LOCKED`.
2. Inserts each back into `jobs` with the **same token**, `attempts = 0`, `max_attempts = 1`, `run_at = now`, and sets `last_tried_at`. The row **stays** in `failed_jobs`.

The replayer never runs a handler itself; it only feeds the normal worker, which processes at its usual pace. A thousand failures become 20 jobs every two hours, never a spike. A replayed job gets **one attempt**, not a new retry cycle, so a replay cannot multiply load.

When a replayed job settles:
- success → the job is deleted and the `failed_jobs` row with that token is deleted;
- failure → the job is deleted and the existing `failed_jobs` row is updated: `tries + 1`, `exception`, `failed_at`. No second row is created (the token is unique).

Rows with `permanent = true` or `tries >= max-tries` are never replayed and wait for a person. Manual replay is out of scope here (it will need an admin endpoint), but it is the same operation as step 2 for one row.

### D4. Enqueue inside the caller's transaction
`JobService.enqueue(JobType, payload)` (and an overload with `runAt`) serializes the payload record to jsonb with the existing `ObjectMapper` and inserts with `Propagation.MANDATORY`: called outside a transaction it fails loudly.

`NotificationEventListener` moves from `AFTER_COMMIT` + `@Async` to `BEFORE_COMMIT`. An exception in a `BEFORE_COMMIT` listener **rolls back the business transaction**, so the listener must not throw for notification reasons:
- recipient lookups use `Optional` and a missing entity logs a warning and skips (no `requireByToken`);
- it builds the pt-BR title and body with `MessageSource`, stores the notification through `NotificationService.store(...)` and enqueues `SEND_PUSH` with the notification's token.

*Alternative:* keep `AFTER_COMMIT` and enqueue with `REQUIRES_NEW`. Rejected: it keeps the window where the commit succeeded and the enqueue did not.

### D5. Notification split: store now, push in the job
`NotificationService.notifyUser` is replaced by `store(userId, type, title, body, data)`, which saves and returns the notification. `SendPushJobPayload(notificationToken)` is all the job needs: `SendPushJobHandler` loads the notification (missing → permanent failure), builds the push data (type + notification token, as today) and calls `PushNotificationService.sendToUser`.

`PushNotificationService` changes:
- stale-token errors (`UNREGISTERED`, `INVALID_ARGUMENT`): tokens removed, no retry, as today;
- transient errors (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`, or a `FirebaseMessagingException` for the whole batch): thrown as `PushDeliveryException`, so the job retries;
- partial failure: only the devices that failed transiently are retried. The handler throws `JobRetryException` carrying a narrowed payload (`SendPushJobPayload(notificationToken, deviceTokens)`), and the worker stores that payload when it reschedules, so devices that already received the push are not hit again.
- push disabled or no devices: the job completes.

### D6. Scheduling and the worker
`SchedulingConfig` is no longer conditional: `@EnableScheduling` always, a `ThreadPoolTaskScheduler` with **2 threads** (one is enough for the worker, the second keeps the sweep and future daily jobs from waiting behind a batch; the threads sleep between runs, so 1 vCPU is not a concern), `waitForTasksToCompleteOnShutdown = true` and `awaitTerminationSeconds` from `vanep.worker.shutdown-timeout`.

Each scheduled task gets its own switch with `@ConditionalOnProperty` on its bean: `ExpirySweepService` keeps `vanep.expiry-sweep.enabled`; `JobWorker` and `FailedJobReplayer` use `vanep.worker.enabled`.

`JobWorker` runs `@Scheduled(fixedDelayString = "${vanep.worker.poll-interval}")`. One cycle:
1. **Recover:** `PROCESSING` jobs with `locked_at` older than `vanep.worker.lock-timeout` are treated as a failed attempt (D2/D3 rules apply).
2. **Claim** in a short transaction: due jobs by `run_at`, up to `batch-size`, `FOR UPDATE SKIP LOCKED`; set `PROCESSING`, `locked_at`, `locked_by`; commit.
3. **Run** each job in its own transaction around the handler.
4. **Settle** in a short transaction (success, retry, failed, replay outcome).

On shutdown the scheduler waits for the running cycle; a job cut off by the timeout stays `PROCESSING` and is recovered by step 1.

Defaults in `application.properties` (fixed values, not env, except the switch): poll 1 s, batch 20, lock timeout 5 min, shutdown timeout 30 s, replay cron `0 0 8-22/2 * * *`, replay batch 20, replay max tries 5. Only `vanep.worker.enabled=${WORKER_ENABLED:true}` reads the environment; test properties set it to `false`.

### D7. Deployment topology
`docker-compose.yml`:
- `vanep` keeps its role and sets `WORKER_ENABLED: "false"`.
- New `vanep-worker`: same `image: ${VANEP_IMAGE:-vanep-api:local}` and `env_file: .env`, **no published port**, `restart: unless-stopped`, `depends_on` postgres healthy, `stop_grace_period` above `vanep.worker.shutdown-timeout` (Docker's default 10 s would kill a running batch), the same logging options and the same mounts as the API (Firebase credentials for the push; media and logs so a future handler does not fail on a missing path). Environment: `WORKER_ENABLED: "true"` and `EXPIRY_SWEEP_ENABLED: "false"` (the sweep has no distributed lock and must run in one place only, the API).

These two pinned variables are a deliberate, narrow exception to rule 3: they describe the **role** of each service built from one image and one shared `.env`, so they cannot live in that `.env`. No environment-specific value (host, port, credential) is pinned.

The worker's Spring context still starts the embedded web server inside the container; it is unreachable because no port is published. `SKIP LOCKED` makes extra worker replicas safe if they are ever added.

Both containers run Flyway at boot; Flyway serializes through a PostgreSQL lock, so the concurrent start is safe.

### D8. CD
The deploy script in `cd.yml` changes as follows:
- `docker compose pull vanep vanep-worker` (same image, one fetch); `up -d --no-build` already starts every service.
- After the existing API health check, a bounded worker check: the `vanep-worker` container is `running` with `RestartCount 0` and its logs contain `Job worker started` (logged by `JobWorker` at startup). The worker has no HTTP port and the JRE image has no `curl`, so a log marker avoids a new dependency or an actuator endpoint.
- On failure it prints `docker compose logs --tail=100 vanep-worker` and exits non-zero, like the API check.

## Risks / Trade-offs

- **The listener now runs inside the business transaction** → an unexpected exception there aborts the request. Mitigated by keeping it to an insert of the notification plus an insert of the job, with `Optional` lookups, and a test that a missing recipient does not abort.
- **Duplicate push** if the process dies between FCM accepting and settle → accepted for push.
- **Push replayed hours later** (from `failed_jobs`) is of little use → the in-app notification is already stored; push replay is a safety net. A type can opt out by setting a replay policy later if needed.
- **H2 (tests) vs PostgreSQL (prod) for `SKIP LOCKED` and jsonb** → logic tested on H2; concurrency and the real migration proven in the rule 55 QA against a throwaway PostgreSQL.
- **1 vCPU with two JVMs**: about 300–500 MB more memory and two Spring boots competing at each deploy (boot already takes over 2 minutes on the VPS) → accepted; CD's check windows are long enough, and `JAVA_OPTS` can cap the worker's heap if memory gets tight. Push sending is I/O-bound and batched at 20; the replay is throttled.
- **Two instances could run the expiry sweep** → the worker service pins `EXPIRY_SWEEP_ENABLED=false`.
- **Worker container down** → jobs accumulate durably and flush when it returns; a crash loop is caught by the CD check. No queue-depth alert yet (follow-up).
- **Rollback**: reverting the image leaves `jobs`/`failed_jobs` unread; pushes enqueued after the deploy would not be sent (the in-app notifications are stored). Tables are additive and can stay.

## Migration Plan

1. Merge; CD builds the image and runs `up -d`: Flyway creates the tables, `vanep` starts with the worker off, `vanep-worker` starts with it on, CD checks both.
2. No new mandatory variable in the VPS `.env`.
3. Verify with a real notification (QA script under `scripts/qa/async-job-queue/`).
4. Rollback as above.

## Changes made during implementation

- Shutdown relies on the Spring 7 lifecycle (stopping the scheduler waits for the running batch, up to `spring.lifecycle.timeout-per-shutdown-phase`). `waitForTasksToCompleteOnShutdown` was removed: with paused executors it held every shutdown for the full timeout. `vanep.worker.shutdown-timeout` was dropped.
- `jobs.type` / `failed_jobs.type` are mapped as text with `JobModel.findJobType()`, so a removed type cannot break the claim query.
- A per-device FCM failure without an error code (network, token endpoint) counts as transient.
- `NotificationService.notifyUser` kept its name: it stores and enqueues `SEND_PUSH`.
- Compose: the worker also disables the seeders, writes `vanep-worker.log`, and caps its heap at 25% of host RAM.
