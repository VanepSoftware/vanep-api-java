## ADDED Requirements

### Requirement: Jobs are enqueued atomically with the caller's transaction
The system SHALL persist a job in the same database transaction as the business change that requests it. Enqueuing outside an active transaction SHALL be rejected.

#### Scenario: Commit keeps the job
- **WHEN** a service enqueues a job and its transaction commits
- **THEN** the job exists in `jobs` with status `PENDING`, `attempts` 0, `max_attempts` from its type's policy and `run_at` set to now

#### Scenario: Rollback discards the job
- **WHEN** a service enqueues a job and its transaction rolls back
- **THEN** no job exists for that request

#### Scenario: Enqueue without a transaction
- **WHEN** a job is enqueued with no active transaction
- **THEN** the enqueue is rejected and no job is stored

#### Scenario: Delayed job
- **WHEN** a job is enqueued with a `run_at` in the future
- **THEN** the worker SHALL NOT run it before that instant

### Requirement: The worker runs due jobs through the handler of their type
The system SHALL poll for jobs with status `PENDING` and `run_at` not in the future, oldest `run_at` first and up to the configured batch size, run each with the `JobHandler` registered for its type, and delete the job when the handler returns normally.

#### Scenario: Successful job
- **WHEN** a due job's handler completes without error
- **THEN** the job is deleted from `jobs`

#### Scenario: Job is not due yet
- **WHEN** a job has `run_at` in the future
- **THEN** it stays `PENDING` and untouched

### Requirement: A job is claimed by one worker at a time
The system SHALL claim jobs so that two worker instances never run the same job concurrently. A claimed job SHALL be `PROCESSING` with `locked_at` and `locked_by` set.

#### Scenario: Two workers, one job
- **WHEN** two workers poll while one job is due
- **THEN** exactly one of them claims and runs it

#### Scenario: Claim is recorded
- **WHEN** a worker claims a job
- **THEN** the job shows status `PROCESSING`, the claim time and an identifier of the claiming instance

### Requirement: Failed jobs are retried by rescheduling, under their type's policy
The system SHALL, when a handler throws, increment `attempts`, store the error in `last_error`, return the job to `PENDING` and set `run_at` to now plus an exponential backoff capped by the job type's policy. A rescheduled job SHALL NOT delay other due jobs.

#### Scenario: First failure
- **WHEN** a handler throws on the first attempt
- **THEN** the job is `PENDING` with `attempts` 1, `last_error` filled and `run_at` after the type's base delay

#### Scenario: Backoff is capped
- **WHEN** the computed delay would exceed the type's maximum
- **THEN** `run_at` uses the maximum delay

#### Scenario: A failing job does not block the queue
- **WHEN** one job is waiting for its retry and other jobs are due
- **THEN** the other jobs are claimed and run in the same cycle

#### Scenario: Retry with a narrowed payload
- **WHEN** a handler asks for a retry with a new payload
- **THEN** the job is rescheduled with that payload replacing the old one

### Requirement: Exhausted jobs move to failed_jobs
The system SHALL move a job to `failed_jobs`, with its token, type, payload, attempts, `tries` 0 and the exception text, and delete it from `jobs`, when `attempts` reaches `max_attempts`.

#### Scenario: Last attempt fails
- **WHEN** a handler throws and the job has reached `max_attempts`
- **THEN** a row exists in `failed_jobs` with the job's token, type, payload, attempts, `tries` 0 and exception, and the job is gone from `jobs`

### Requirement: Jobs that can never succeed are marked permanent
The system SHALL move a job straight to `failed_jobs` with `permanent` true, without retries, when its type has no handler, its payload cannot be read, or its handler signals a permanent failure.

#### Scenario: Unknown type
- **WHEN** a claimed job has a type with no handler
- **THEN** it moves to `failed_jobs` as permanent on the first attempt

#### Scenario: Corrupt payload
- **WHEN** a claimed job's payload cannot be read as the handler's payload type
- **THEN** it moves to `failed_jobs` as permanent on the first attempt

#### Scenario: Handler signals permanent failure
- **WHEN** a handler throws a permanent failure
- **THEN** the job moves to `failed_jobs` as permanent without further attempts

### Requirement: Failed jobs are replayed in small batches at fixed times
The system SHALL, at each configured replay time, send back to the queue at most the configured replay batch of `failed_jobs` rows that are not permanent, are below the try limit and have no job with the same token in `jobs`, least recently tried first. A replayed job SHALL get one attempt. The replayer SHALL NOT run handlers itself.

#### Scenario: Replay run
- **WHEN** a replay time arrives and 100 replayable rows exist with a batch size of 20
- **THEN** 20 jobs are inserted into `jobs` with the same tokens, `attempts` 0 and `max_attempts` 1, their rows get `last_tried_at` set, and the other 80 wait for the next run

#### Scenario: Row stays while it is replayed
- **WHEN** a failed job is sent back to the queue
- **THEN** its row stays in `failed_jobs` and is not selected again while its job is in `jobs`

#### Scenario: Permanent and exhausted rows are skipped
- **WHEN** a row is permanent or has `tries` at the try limit
- **THEN** the replayer never selects it

#### Scenario: Outside the replay times
- **WHEN** the current time is not a configured replay time
- **THEN** no failed job is sent back to the queue

### Requirement: The outcome of a replay is recorded on the same failed_jobs row
The system SHALL delete the `failed_jobs` row when its replayed job succeeds, and SHALL update the same row with `tries` incremented, the new exception and `failed_at` when it fails, without creating a second row.

#### Scenario: Replay succeeds
- **WHEN** a replayed job's handler completes
- **THEN** the job and its `failed_jobs` row are both deleted

#### Scenario: Replay fails
- **WHEN** a replayed job's handler throws
- **THEN** the job is deleted, its `failed_jobs` row has `tries` increased by one with the new exception, and no other row exists for that token

#### Scenario: Replay fails permanently
- **WHEN** a replayed job fails with a permanent failure
- **THEN** its `failed_jobs` row is updated with `permanent` true

### Requirement: Stuck jobs are recovered
The system SHALL treat a job `PROCESSING` for longer than the configured lock timeout as a failed attempt, applying the same retry, failed and replay rules.

#### Scenario: Worker died mid-job
- **WHEN** a job is `PROCESSING` with `locked_at` older than the lock timeout and attempts remain
- **THEN** it becomes `PENDING` with `attempts` increased by one, its lock cleared and `run_at` after the backoff

#### Scenario: Stuck job with no attempts left
- **WHEN** a stuck job has reached `max_attempts`
- **THEN** it moves to `failed_jobs` (or updates its existing row when it was a replay)

#### Scenario: Job still within the timeout
- **WHEN** a job is `PROCESSING` and `locked_at` is within the lock timeout
- **THEN** it is left alone

### Requirement: Scheduling is always on and shuts down gracefully
The system SHALL enable scheduling regardless of any single task's switch, run scheduled tasks on a dedicated scheduler with 2 threads, give each scheduled task its own switch, and wait up to the configured timeout for running tasks on shutdown.

#### Scenario: Sweep off, worker on
- **WHEN** `vanep.expiry-sweep.enabled` is false and `vanep.worker.enabled` is true
- **THEN** the worker and the replayer run and the expiry sweep does not

#### Scenario: Shutdown during a batch
- **WHEN** the application is stopped while jobs are running
- **THEN** no new cycle starts and the running jobs are given the timeout to finish

#### Scenario: Job outlives the timeout
- **WHEN** a job is still running when the shutdown timeout expires
- **THEN** it stays `PROCESSING` and is recovered by the lock-timeout rule after the next start

### Requirement: The worker can be switched on or off by configuration
The system SHALL run the worker and the replayer only when `vanep.worker.enabled` is true (default true). With it off, the application SHALL still accept enqueues and SHALL NOT claim or replay jobs.

#### Scenario: Worker off
- **WHEN** `vanep.worker.enabled` is false
- **THEN** jobs are enqueued normally and remain `PENDING`

#### Scenario: Worker on
- **WHEN** `vanep.worker.enabled` is true
- **THEN** the worker starts with the application, logs `Job worker started` and polls at the configured interval

### Requirement: The worker runs in its own container
The deployment SHALL run the worker as a separate Compose service built from the same image as the API, with no published port and automatic restart. The API service SHALL run with the worker off, and the worker service SHALL run with the expiry sweep off.

#### Scenario: Roles split
- **WHEN** the stack is started with `docker compose up -d`
- **THEN** `vanep` serves HTTP without claiming jobs, `vanep-worker` claims and replays jobs without running the expiry sweep, and only `vanep` publishes a port

#### Scenario: Worker stopped by the deploy
- **WHEN** `vanep-worker` is stopped during a deploy
- **THEN** it is given a grace period longer than the worker's shutdown timeout before being killed

### Requirement: The deploy confirms the worker started
The CD pipeline SHALL, after the API health check, fail the deploy when the worker container is not running, has restarted, or does not log that the worker started.

#### Scenario: Healthy deploy
- **WHEN** the API responds and the `vanep-worker` container is running with zero restarts and its logs contain `Job worker started`
- **THEN** the deploy is reported successful

#### Scenario: Worker did not start
- **WHEN** the worker container is not running, restarts, or `Job worker started` does not appear in the check window
- **THEN** CD prints the worker's recent logs and exits with a failure
