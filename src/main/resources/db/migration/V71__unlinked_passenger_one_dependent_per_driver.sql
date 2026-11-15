create unique index unlinked_passenger_driver_dependent_active_key
    on unlinked_passenger (driver_id, dependent_id)
    where dependent_id is not null and deleted_at is null;

comment on index unlinked_passenger_driver_dependent_active_key is
    'One live unlinked passenger per driver and dependent, so a merge cannot duplicate the student.';
