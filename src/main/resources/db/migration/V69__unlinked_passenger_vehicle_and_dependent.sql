alter table unlinked_passenger add column vehicle_id bigint references vehicle (id);
alter table unlinked_passenger add column dependent_id bigint references dependent (id);

comment on column unlinked_passenger.vehicle_id is
    'Van the student rides. Null means not assigned: the student counts for every van of the driver.';
comment on column unlinked_passenger.dependent_id is
    'Set when the driver merges the passenger with the dependent a guardian registered. The passenger keeps its row, schedule and operation records, so it keeps its place on the route.';
comment on table unlinked_passenger is
    'Student a driver already transports, registered by the driver for the route. Without dependent_id it has no guardian and triggers no notification; once merged, the guardian of dependent_id is reachable. Holds only what the route needs.';

create index unlinked_passenger_vehicle_idx
    on unlinked_passenger (vehicle_id) where vehicle_id is not null and deleted_at is null;

create index unlinked_passenger_dependent_idx
    on unlinked_passenger (dependent_id) where dependent_id is not null and deleted_at is null;
