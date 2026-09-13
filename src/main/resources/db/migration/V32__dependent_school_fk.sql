-- dependent.school_id was added in V9 before the school table existed.
-- School is now a real catalog (V11 + Places resolve); the FK blocks invented ids.

alter table dependent
    add constraint dependent_school_id_fkey
    foreign key (school_id) references school (id);

comment on column dependent.school_id is
    'FK to school.id. Nullable: a dependent may be stored before a school is chosen.';
