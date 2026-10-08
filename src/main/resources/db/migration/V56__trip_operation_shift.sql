-- A trip runs in an operation shift, which has no FULLTIME: a full-time student
-- rides the MORNING trip outbound and the AFTERNOON trip back. The Java type of
-- trip.shift no longer has FULLTIME, so any stored row would fail to load.
-- MORNING because a "full-time" route started in the morning.

update trip
set shift = 'MORNING'
where shift = 'FULLTIME';

comment on column trip.shift is
    'Operation shift (MORNING, AFTERNOON, NIGHT). Never FULLTIME: a full-time student generates two trips.';

comment on column dependent.shift is
    'School shift, including FULLTIME. Not the route: the weekdays and legs served come from the contract schedule.';
