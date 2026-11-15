-- A slot has one pickup time: drivers agree on a time, not on a window.

alter table schedule_slot drop constraint schedule_slot_window_check;
alter table schedule_slot drop column window_end;
alter table schedule_slot rename column window_start to pickup_time;

comment on column schedule_slot.pickup_time is
    'When the driver picks the student up: at home on the outbound leg, at school on the return.';
