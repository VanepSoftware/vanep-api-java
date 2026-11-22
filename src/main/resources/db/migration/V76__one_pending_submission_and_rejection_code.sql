-- A submission that was decided is final, and a renewal is a new submission: each owner has at most
-- one PENDING submission per type, and every rejection carries a structured reason. The service
-- checks both first and translates a violation in a race into HTTP 409.

-- Only one PENDING submission per owner and type may stay: the newest one.
update driver_document older
set superseded_at = now()
where older.status = 'PENDING'
  and older.deleted_at is null
  and exists (select 1 from driver_document newer
              where newer.driver_id = older.driver_id
                and newer.document_type = older.document_type
                and newer.status = 'PENDING'
                and newer.deleted_at is null
                and (newer.created_at, newer.id) > (older.created_at, older.id));

update vehicle_document older
set superseded_at = now()
where older.status = 'PENDING'
  and older.deleted_at is null
  and exists (select 1 from vehicle_document newer
              where newer.vehicle_id = older.vehicle_id
                and newer.document_type = older.document_type
                and newer.status = 'PENDING'
                and newer.deleted_at is null
                and (newer.created_at, newer.id) > (older.created_at, older.id));

create unique index driver_document_one_pending_key
    on driver_document (driver_id, document_type)
    where deleted_at is null and superseded_at is null and status = 'PENDING';
create unique index vehicle_document_one_pending_key
    on vehicle_document (vehicle_id, document_type)
    where deleted_at is null and superseded_at is null and status = 'PENDING';
create unique index driver_cnh_one_pending_key
    on driver_cnh (driver_id)
    where deleted_at is null and superseded_at is null and status = 'PENDING';

-- Rejections made before the service sent a code.
update driver_document set rejection_reason_code = 'OTHER' where status = 'REJECTED' and rejection_reason_code is null;
update vehicle_document set rejection_reason_code = 'OTHER' where status = 'REJECTED' and rejection_reason_code is null;
update driver_cnh set rejection_reason_code = 'OTHER' where status = 'REJECTED' and rejection_reason_code is null;

alter table driver_document
    add constraint driver_document_rejected_reason_check
    check (status <> 'REJECTED' or rejection_reason_code is not null);

alter table vehicle_document
    add constraint vehicle_document_rejected_reason_check
    check (status <> 'REJECTED' or rejection_reason_code is not null);

alter table driver_cnh
    add constraint driver_cnh_rejected_reason_check
    check (status <> 'REJECTED' or rejection_reason_code is not null);
