-- Each failed job carries when it goes back to the queue. The delay grows per job type (e-mail:
-- 15 min, 1 h, 6 h); once the schedule runs out retry_at stays null and the row is kept for
-- manual analysis, like a permanent failure.

alter table failed_jobs add column retry_at timestamptz;

-- Rows the old replayer would still pick up (non-permanent, under its 5-try limit) keep being
-- replayed: they become due right away.
update failed_jobs set retry_at = now() where permanent = false and tries < 5;

drop index failed_jobs_replayable_idx;

create index failed_jobs_retry_idx on failed_jobs (retry_at, id) where retry_at is not null;
