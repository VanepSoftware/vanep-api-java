-- Durable background job queue. A finished job is deleted; a job that exhausts its attempts
-- moves to failed_jobs, where it stays (counting replay tries) until a replay succeeds.

create table jobs (
    id           bigint       generated always as identity primary key,
    token        varchar(32)  not null,
    type         varchar(64)  not null,
    payload      jsonb        not null default '{}'::jsonb,
    status       varchar(16)  not null,
    attempts     integer      not null default 0,
    max_attempts integer      not null,
    run_at       timestamptz  not null,
    locked_at    timestamptz,
    locked_by    varchar(128),
    last_error   text,
    created_at   timestamptz  not null default now()
);

create unique index jobs_token_key on jobs (token);

create index jobs_due_idx on jobs (status, run_at);

create table failed_jobs (
    id            bigint       generated always as identity primary key,
    token         varchar(32)  not null,
    type          varchar(64)  not null,
    payload       jsonb        not null default '{}'::jsonb,
    attempts      integer      not null,
    tries         integer      not null default 0,
    permanent     boolean      not null default false,
    exception     text         not null,
    failed_at     timestamptz  not null,
    last_tried_at timestamptz
);

create unique index failed_jobs_token_key on failed_jobs (token);

create index failed_jobs_replayable_idx on failed_jobs (last_tried_at nulls first, id)
    where permanent = false;
