## PR plan (constitution rules 36–41)

Delivered as **one phase, one PR**, by explicit decision (VAN-106). This deviates from rules 39 and 41 (one dependency layer per PR, no interface and implementation together, ~600 lines / 10 files): the queue, its first handler and the listener switch only make sense together, because a queue with no consumer, or a consumer with no queue, delivers nothing. If review shows the diff is too large, the natural cut is the fallback below.

| Phase | Contents | Depends on | Parallel with |
|-------|----------|------------|---------------|
| 1 | Groups 1–10: queue, worker, replay, scheduling fix, push split, CD, QA | none | none |

Fallback split if needed: 1a = groups 1–6 (scheduling fix, queue, worker, replay; no consumer); 1b = groups 7–10 (push split, Compose worker service, CD, QA).

Per rules 26 and 42, inside each group the test comes first, then migration → model → repository → service. Run `make lint` at the end of every group and before every commit/push (rule 52). Source, comments, logs and test names are English (rules 48, 49); comments only for a non-obvious why (rule 51).

## 1. Scheduling fix

- [x] 1.1 Test first: with `vanep.expiry-sweep.enabled=false` scheduling is still enabled and other scheduled beans run; the sweep bean is absent
- [x] 1.2 Make `SchedulingConfig` unconditional: `@EnableScheduling`, `ThreadPoolTaskScheduler` with 2 threads, `waitForTasksToCompleteOnShutdown`, `awaitTerminationSeconds` from `vanep.worker.shutdown-timeout`
- [x] 1.3 Move the `vanep.expiry-sweep.enabled` condition to `ExpirySweepService` and confirm its existing tests still pass

## 2. Schema and model

- [x] 2.1 Write the repository tests on H2 first (`JobRepositoryTest`, `FailedJobRepositoryTest`): claim due jobs oldest first, find stuck jobs, select replayable failed jobs (not permanent, below the try limit, no job with the same token, least recently tried first)
- [x] 2.2 Add `V80__create_jobs_and_failed_jobs.sql`: `jobs` (token unique, `type`, `payload jsonb`, `status`, `attempts`, `max_attempts`, `run_at`, `locked_at`, `locked_by`, `last_error`, `created_at`, index `(status, run_at)`) and `failed_jobs` (token unique, `type`, `payload jsonb`, `attempts`, `tries`, `permanent`, `exception`, `failed_at`, `last_tried_at`); run `scripts/check-migrations.sh`; no applied migration edited (rule 2)
- [x] 2.3 Add enums `JobStatus` (`PENDING`, `PROCESSING`) and `JobType` with its retry policy (`SEND_PUSH`: 5 attempts, base 10 s, max 2 min)
- [x] 2.4 Add `JobModel` and `FailedJobModel` (token generated like `NotificationModel`; no `@SoftDelete`, infrastructure rows)
- [x] 2.5 Add `JobRepository` and `FailedJobRepository` with the queries above (`FOR UPDATE SKIP LOCKED` on claim and replay selection)

## 3. Configuration and contracts

- [x] 3.1 Add the `vanep.worker.*` properties (`enabled`, `poll-interval`, `batch-size`, `lock-timeout`, `shutdown-timeout`, `replay.cron`, `replay.batch-size`, `replay.max-tries`), read with `@Value` like the rest of the codebase (rule 6; no `@ConfigurationProperties` exists here); fixed defaults in `application.properties`, only `enabled=${WORKER_ENABLED:true}` reads the environment; `vanep.worker.enabled=false` in the test properties (rules 1, 3, 25)
- [x] 3.2 Add `JobHandler<T>` (`type()`, `payloadType()`, `perform(T)`), `PermanentJobFailureException` and `JobRetryException` (carries an optional replacement payload) in `br.com.vanep.job.handler`
- [x] 3.3 Test then add `JobHandlerRegistry`: maps type to handler at startup, fails startup on a duplicate type, returns empty for an unknown type

## 4. Enqueue

- [x] 4.1 Test then add `JobService.enqueue(JobType, payload)` and the `runAt` overload: jsonb payload, `max_attempts` from the type, `Propagation.MANDATORY`; cover commit keeps, rollback discards, no transaction rejected, future `run_at` stored

## 5. Worker

- [x] 5.1 Test then add the claim step: due jobs by `run_at` up to batch size, `PROCESSING`, `locked_at`, `locked_by`, skips rows locked elsewhere
- [x] 5.2 Test then add the settle step: success deletes the job (and its `failed_jobs` row when it was a replay); failure reschedules with the type's capped exponential backoff and does not delay other due jobs; `JobRetryException` with a payload replaces it; exhaustion inserts into `failed_jobs` with `tries` 0, or for a replay updates the existing row (`tries + 1`, exception, `failed_at`); unknown type, unreadable payload and `PermanentJobFailureException` go to `failed_jobs` with `permanent` true
- [x] 5.3 Test then add stuck-job recovery: past `lock-timeout` counts as a failed attempt under the same rules; within it the job is untouched
- [x] 5.4 Test then add `JobWorker` (`@Scheduled(fixedDelay)`, `@ConditionalOnProperty vanep.worker.enabled`): recover, claim, run one transaction per job, settle; logs `Job worker started` once at startup
- [x] 5.5 Graceful shutdown: `SchedulingConfigTest` proves the scheduler waits for running tasks up to `shutdown-timeout`; `JobServiceTest` proves a job left `PROCESSING` is recovered after the lock timeout. The real stop is exercised in the QA (10.3a)
- [x] 5.6 Test that with `vanep.worker.enabled=false` enqueue works and nothing is claimed or replayed

## 6. Replay of failed jobs

- [x] 6.1 Test then add `FailedJobReplayer` (`@Scheduled(cron = vanep.worker.replay.cron, zone = "America/Sao_Paulo")`, conditional on `vanep.worker.enabled`): inserts at most `replay.batch-size` replayable rows into `jobs` with the same token, `attempts` 0, `max_attempts` 1, sets `last_tried_at`, keeps the rows; skips permanent rows, rows at `replay.max-tries` and rows whose token is already in `jobs`
- [x] 6.2 Test the full cycle: fail → `failed_jobs`; replay succeeds → both rows gone; replay fails → same row with `tries + 1`; replay fails permanently → `permanent` true; never two rows for one token

## 7. Push split

- [x] 7.1 Update `NotificationServiceTest` first, then replace `NotificationService.notifyUser` with `store(...)` returning the saved notification, and keep `pushData` for the handler
- [x] 7.2 Update `PushNotificationServiceTest` first, then make `PushNotificationService` report the outcome: stale tokens removed without retry; transient errors (`UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`, batch-level `FirebaseMessagingException`) surfaced with the device tokens that failed; push disabled or no devices succeed
- [x] 7.3 Test then add `SendPushJobPayload(notificationToken, deviceTokens)` and `SendPushJobHandler`: loads the notification (missing → permanent), sends to all devices or only the listed ones, throws `JobRetryException` with the narrowed device list on partial transient failure
- [x] 7.4 Update `NotificationEventListenerTest` and `NotificationAfterCommitTest` first: `BEFORE_COMMIT`, one notification and one job per recipient (contract created: two), rollback leaves neither, missing conversation/link/driver/user logs a warning and the business transaction commits, chat title carries the sender name
- [x] 7.5 Change `NotificationEventListener`: `BEFORE_COMMIT`, no `@Async`, `Optional` lookups instead of `requireByToken`, store the notification and enqueue `SEND_PUSH`
- [x] 7.6 Delete `PushNotificationAsyncConfig` and every reference to `pushNotificationExecutor`; test that no such bean and no `@Async` listener remain
- [x] 7.7 Run the notification, proposal, contract, conversation and driver-review suites and fix regressions caused by the listener now running inside the transaction

## 8. Compose, CD and docs

- [x] 8.1 Add the `vanep-worker` service to `docker-compose.yml`: same `image: ${VANEP_IMAGE:-vanep-api:local}`, `env_file: .env`, no `ports`, `restart: unless-stopped`, `depends_on` postgres healthy, `stop_grace_period` above `vanep.worker.shutdown-timeout`, same logging options and mounts as `vanep`, environment limited to `WORKER_ENABLED: "true"` and `EXPIRY_SWEEP_ENABLED: "false"`
- [x] 8.2 Set `WORKER_ENABLED: "false"` on the `vanep` service; no environment-specific value goes into any `environment:` block (rule 3, exception documented in design D7)
- [x] 8.3 Update `.github/workflows/cd.yml`: `docker compose pull vanep vanep-worker`; after the API health check, a bounded worker check (container `running` with zero restarts and logs containing `Job worker started`); on failure print `docker compose logs --tail=100 vanep-worker` and exit non-zero
- [x] 8.4 Document `WORKER_ENABLED` as a commented placeholder in `.env.example` (rule 4)
- [x] 8.5 Update `docs/project-overview.md`: the job queue, how to add a job type, the replay schedule, and the two-service topology (API with the worker off, `vanep-worker` with it on)

## 9. Quality gates

- [x] 9.1 `make lint` (fix with `make lint-fix`) and `make test-coverage`; JaCoCo gate green (rules 24, 44)
- [x] 9.2 `make audit-n-plus-one` and `make audit-n-plus-one-suite`: the claim, replay and listener paths add no per-row statements (rule 17)
- [x] 9.3 No deprecations in touched code (rule 53)

## 10. QA against a running API (rule 55) and delivery

- [x] 10.1 Start the app from the branch against a throwaway PostgreSQL container so Flyway runs `V80` for real
- [x] 10.2 Write `scripts/qa/async-job-queue/run.sh` on top of `scripts/qa/lib.sh`: real tokens through the mobile password grant; for each event (proposal sent/accepted/declined, chat message, contract created, driver reviewed) assert the notification exists right after the response, the `SEND_PUSH` job is created and then disappears
- [x] 10.3 Cover failure and misuse: rolled-back request leaves no notification or job; missing recipient does not fail the request; a failing handler is retried with growing `run_at` while other jobs keep flowing; exhausted job lands in `failed_jobs`; replay (with a short test cron) sends back only the batch size, success removes the row, failure increments `tries` on the same row, permanent rows are skipped; two app instances against one backlog never double-claim; killing the app mid-job recovers it after the lock timeout; `WORKER_ENABLED=false` leaves jobs `PENDING`
- [x] 10.3a Exercise the Compose topology locally: `docker compose up -d` brings up `vanep` (worker off) and `vanep-worker` (worker on, sweep off); `docker compose stop vanep-worker` lets the running job finish within the grace period; the CD worker-check snippet passes on a healthy worker and fails on a crashing one
- [x] 10.4 Record the run (screenshots/video of rule 56 not captured) in `openspec/changes/async-job-queue/qa.md` (how to run, scenario → request → expected → observed) and capture screenshots and a video for the PR (rule 56)
- [ ] 10.5 Open the PR with `VAN-106` in the branch and title, pt-BR description with test and lint status, link to `qa.md` with the pass count and the media (rule 47)
- [ ] 10.6 After merge, confirm the first deploy: `Job worker started` in the API logs and a real push delivered through a job
