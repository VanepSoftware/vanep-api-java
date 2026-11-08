create table dependent_school_day (
    id           bigint      generated always as identity primary key,
    dependent_id bigint      not null references dependent (id),
    weekday      varchar(16) not null,
    school_shift varchar(16) not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz
);

comment on table dependent_school_day is
    'Weekly school grid of a dependent: one row per weekday with school, none for a day without school. The period at school is a fact of the child, the same for every driver, and changes from one day to another (full time on some days only).';
comment on column dependent_school_day.weekday is 'java.time.DayOfWeek name (MONDAY to SUNDAY).';
comment on column dependent_school_day.school_shift is 'SchoolShift: the period at school on that weekday.';

-- Also serves the read of a dependent's grid, which always filters out the removed rows.
create unique index dependent_school_day_weekday_active_key
    on dependent_school_day (dependent_id, weekday) where deleted_at is null;

-- The grid is the only source of the school period. A single shift per dependent could contradict
-- it, and nothing read it. Not converted into a grid: a Monday-to-Friday grid would state school
-- days nobody informed.
alter table dependent
    drop column shift;
