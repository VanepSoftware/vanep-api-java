## Why

Notifications are sent from an in-memory `@Async` executor (`push-` pool, queue of 1000) fired after the request commits. A full queue drops the notification with a warning, a JVM restart loses everything still queued, and an FCM failure is logged and forgotten: there is no retry and no trace.

VAN-106 asks for the request to only enqueue and a background job to deliver. VAN-107 (e-mails) and the daily jobs of VAN-66 (document expiry alerts) and VAN-68 (subscription warnings) need the same mechanism, so it is built generically here and the push notification is its first user.

## What Changes

- New durable job queue in PostgreSQL: tables `jobs` and `failed_jobs` (Flyway).
- New `JobService.enqueue`: inserts a job in the **caller's transaction**, so the business change and its job commit or roll back together.
- New `JobWorker`: a `@Scheduled` task that claims due jobs with `FOR UPDATE SKIP LOCKED`, runs the handler of each job's type, and on failure reschedules the job with a `run_at` in the future, following a **retry policy per job type** (max attempts, backoff). A failing job never blocks the others. Jobs stuck in `PROCESSING` return to the queue after a lock timeout.
- A job that exhausts its attempts moves to `failed_jobs` and **stays there** while it keeps failing, with a `tries` column counting every replay attempt. Jobs that can never succeed (unknown type, unreadable payload, recipient gone) are marked permanent.
- New `FailedJobReplayer`: a second `@Scheduled` task that runs at fixed times of the day and sends a **small batch** of failed jobs back to the queue each time, so a backlog of failures never floods the server. Success removes the row from `failed_jobs`; failure keeps it and increments `tries`. Permanent failures and rows past a try limit are skipped and wait for manual action.
- New `JobHandler<T>` contract, one handler per job type. First handler: `SendPushJobHandler`.
- Notifications split in two steps: the listener **stores the in-app notification in the business transaction** and enqueues a `SEND_PUSH` job; the job only sends the push. A retried or replayed push never duplicates the in-app notification.
- `PushNotificationService` stops swallowing transient FCM errors, so they reach the retry policy. Stale device tokens are still removed without a retry.
- `SchedulingConfig` is fixed: scheduling is always on (today the expiry sweep's flag switches off every `@Scheduled` task), the scheduler has 2 threads instead of 1, waits for running tasks on shutdown, and each scheduled task has its own switch.
- Worker switch `vanep.worker.enabled` (env `WORKER_ENABLED`, default `true`). Same jar, same image: the `vanep` (API) service runs with the worker off, and a new **`vanep-worker` Compose service** runs it on, with the expiry sweep off so the sweep keeps running in exactly one place.
- **BREAKING (internal):** `PushNotificationAsyncConfig` and the `push-` executor are removed.
- Deploy: `docker-compose.yml` gets the `vanep-worker` service; `cd.yml` pulls it and fails the deploy when the worker container is not running with the worker started. Tuning values (intervals, batch sizes, retry policies, replay schedule) live in `application.properties`, not in `.env`.
- No Supervisor (Docker `restart: unless-stopped` restarts the process), no Kafka/RabbitMQ, no `workers` table.

Out of scope: moving e-mails to jobs (VAN-107), the daily jobs of VAN-66 and VAN-68, a web UI or endpoint for failed jobs, distributed locking for the expiry sweep.

## Capabilities

### New Capabilities
- `job-queue`: durable queue and worker: enqueue in the caller's transaction, claim without double processing, per-type retry with backoff that does not block the queue, `failed_jobs` with try counter and permanent marker, throttled replay at fixed times, stuck-job recovery, graceful shutdown, worker switch.
- `notification-delivery`: notification events store the in-app notification atomically with the business change and deliver the push through a `SEND_PUSH` job with retries and replay, instead of an in-memory executor.

### Modified Capabilities
<!-- None. No existing spec in openspec/specs/ covers notifications or background execution. -->

## Impact

- Code: new package `br.com.vanep.job` (`enums`, `model`, `repository`, `service`, `handler`, `config`); `config/SchedulingConfig`; `notification/listener/NotificationEventListener`; `notification/service/NotificationService`; `notification/service/PushNotificationService`; `notification/config/PushNotificationAsyncConfig` (deleted).
- Database: one migration after `V79` creating `jobs` and `failed_jobs`.
- Infra: `docker-compose.yml` (new `vanep-worker` service, worker off on `vanep`), `.github/workflows/cd.yml` (pull and check the worker), `.env.example` (`WORKER_ENABLED` documented).
- Server: a second JVM on the 1 vCPU VPS (about 300–500 MB more memory, two Spring boots per deploy). Accepted.
- Behavior: the in-app notification is stored before the request returns; the push arrives up to one poll interval later (default 1 s) and survives restarts and FCM outages.
- Risk: a push can be sent twice if the process dies after FCM accepted it and before the job is settled. Accepted for push; future handlers with non-repeatable side effects must guard themselves.
- Dependencies: none added.
