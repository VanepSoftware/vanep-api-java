#!/usr/bin/env bash
# Manual QA for the driver-credentials-review change (constitution rule 55). Two parts:
#  - V72 and V75 against legacy data: a separate database in the throwaway container gets V1..V71
#    through psql, then the rows V72 must remove or keep, then V72, then the rows V75 must fill,
#    then V75, then the rows V76 must clean, then V76, and each outcome is checked.
#  - the live API from the branch, on the throwaway database it migrated with Flyway and seeded.
# Never point it at a shared database: it creates and drops a database and writes rows directly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

MIGRATION_DB="${QA_MIGRATION_DB:-v72check}"
SEED_DRIVER_EMAIL="fabio.teixeira@seed.vanep.com.br"
SEED_CLIENT_EMAIL="ana.souza@seed.vanep.com.br"
# Dates relative to today in São Paulo, the API's date, so the run never goes stale.
in_days() { TZ=America/Sao_Paulo date -v"$1"d +%F 2>/dev/null || TZ=America/Sao_Paulo date -d "$1 days" +%F; }
TODAY=$(in_days +0)
YESTERDAY=$(in_days -1)
for n in 60 90 120 150 180 200 300 365; do printf -v "IN_${n}_DAYS" '%s' "$(in_days +$n)"; done

mig_sql() {
  docker exec "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$MIGRATION_DB" -Atqc "$1"
}

mig_sql_error() {
  docker exec -i "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$MIGRATION_DB" -Atq -v ON_ERROR_STOP=1 \
    <<<"begin; $1; rollback;" 2>&1 >/dev/null || true
}

mig_apply() {
  docker exec -i "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$MIGRATION_DB" -q -v ON_ERROR_STOP=1 \
    <"$1" >/dev/null
}

mig_check_rejected() {
  local id=$1 scenario=$2 constraint=$3 statement=$4 error
  error=$(mig_sql_error "$statement")
  RESP_BODY="$error"
  [[ $error == *"$constraint"* ]] || fail "$id $scenario" "rejected by $constraint" "${error:-accepted}"
  pass "$id" "$scenario" "rejected by $constraint"
}

mig_check_accepted() {
  local id=$1 scenario=$2 statement=$3 error
  error=$(mig_sql_error "$statement")
  RESP_BODY="$error"
  [[ -z $error ]] || fail "$id $scenario" "accepted" "$error"
  pass "$id" "$scenario" "accepted"
}

png() {
  printf '\x89PNG\r\n\x1a\n\0\0\0\rIHDR\0\0\0\x01\0\0\0\x01\x08\x06\0\0\0\x1f\x15\xc4\x89\0\0\0\rIDATx\x9cc\xf8\x0f\0\0\x01\x01\0\x05\x18\xd8N\0\0\0\0IEND\xaeB`\x82'
}

upload() {
  local path=$1 token=$2 file
  file="$(mktemp).png"
  png >"$file"
  RESP_STATUS=$(curl -s -o "$BODY_FILE" -w '%{http_code}' -X POST "$BASE_URL$path" \
    -H "Authorization: Bearer $token" -F "file=@$file;type=image/png")
  RESP_BODY=$(cat "$BODY_FILE")
  rm -f "$file"
}

pdf() {
  printf '%%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n%%%%EOF\n'
}

upload_pdf() {
  local path=$1 token=$2 file
  file="$(mktemp).pdf"
  pdf >"$file"
  RESP_STATUS=$(curl -s -o "$BODY_FILE" -w '%{http_code}' -X POST "$BASE_URL$path" \
    -H "Authorization: Bearer $token" -F "file=@$file;type=application/pdf")
  RESP_BODY=$(cat "$BODY_FILE")
  rm -f "$file"
}

# The driver sends only the type and the file (D2). What the document says goes with the file: the
# fields of the JSON body are kept per submission, and decide() adds them to an approval the way the
# reviewer types them from the file.
DOC_DATA_DIR=$(mktemp -d)
send_doc() {
  local path=$1 token=$2 json=${3-} file field value created args
  file="$(mktemp).pdf"
  pdf >"$file"
  args=(-s -o "$BODY_FILE" -w '%{http_code}' -X POST "$BASE_URL$path" -F "file=@$file;type=application/pdf")
  send_doc_fields
  RESP_STATUS=$(curl "${args[@]}")
  RESP_BODY=$(cat "$BODY_FILE")
  rm -f "$file"
  created=$(jq -r '.token // empty' <<<"$RESP_BODY" 2>/dev/null)
  if [[ $RESP_STATUS == 201 && -n $created ]]; then
    jq -c 'del(.documentType, .driverToken)' <<<"${json:-"{}"}" >"$DOC_DATA_DIR/$created" 2>/dev/null || echo '{}' >"$DOC_DATA_DIR/$created"
  fi
}
send_doc_without_file() {
  local path=$1 token=$2 json=${3-} args
  args=(-s -o "$BODY_FILE" -w '%{http_code}' -X POST "$BASE_URL$path")
  send_doc_fields
  RESP_STATUS=$(curl "${args[@]}")
  RESP_BODY=$(cat "$BODY_FILE")
}
send_doc_fields() {
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  for field in documentType driverToken; do
    value=$(jq -r --arg f "$field" 'if type == "object" then .[$f] // empty else empty end' <<<"${json:-"{}"}" 2>/dev/null)
    [[ -n $value ]] && args+=(-F "$field=$value")
  done
  return 0
}
decide() {
  local path=$1 token=$2 json=$3 doc
  doc=${path#/api/*/}
  doc=${doc%%/*}
  if [[ $(jq -r '.status // empty' <<<"$json" 2>/dev/null) == APPROVED && -f "$DOC_DATA_DIR/$doc" ]]; then
    json=$(jq -c --slurpfile d "$DOC_DATA_DIR/$doc" '$d[0] + .' <<<"$json")
  fi
  request PUT "$path" "$token" "$json"
}

download() {
  local path=$1 token=$2
  RESP_STATUS=$(curl -s -o "$BODY_FILE" -w '%{http_code} %{content_type}' "$BASE_URL$path" \
    -H "Authorization: Bearer $token")
  RESP_TYPE=${RESP_STATUS#* }
  RESP_STATUS=${RESP_STATUS%% *}
  RESP_BODY="(binary)"
}

# ---------------------------------------------------------------------------------------------
# Part 1 — V72 on legacy data
# ---------------------------------------------------------------------------------------------

sql "drop database if exists $MIGRATION_DB" >/dev/null
sql "create database $MIGRATION_DB" >/dev/null
for migration in $(ls "$MIGRATIONS"/V*__*.sql | sort -V); do
  version=$(basename "$migration" | cut -d_ -f1 | tr -d V)
  if (( version <= 71 )); then mig_apply "$migration"; fi
done

mig_sql "
insert into users (token, type, name, email, document, verified, terms_accepted_at) values
  ('u-admin', 'ADMIN', 'Admin', 'admin@qa', '11144477735', true, now()),
  ('u-driver', 'DRIVER', 'Driver', 'driver@qa', '52998224725', true, now());
insert into driver (token, user_id, base_price)
  select 'd-driver', id, 100 from users where token = 'u-driver';
insert into media_file (token, provider, object_key, mime_type, size_bytes, visibility)
  select 'm-' || n, 'LOCAL', 'qa/' || n || '.pdf', 'application/pdf', 10, 'PRIVATE'
  from unnest(array['crlv', 'inspection', 'profile', 'photo', 'criminal', 'municipal']) as n;
insert into vehicle (token, driver_id, plate, brand, model, manufacture_year, color, capacity,
                     photo_document_media_id)
  select 'v-van', d.id, 'VAN1A23', 'Renault', 'Master', 2022, 'Branca', 15, m.id
  from driver d, media_file m where d.token = 'd-driver' and m.token = 'm-photo';
insert into driver_document (token, driver_id, document_type, file_media_id, status, reviewed_by,
                             reviewed_at)
  select x.token, d.id, x.type, m.id, x.status,
         case when x.status = 'APPROVED' then (select id from users where token = 'u-admin') end,
         case when x.status = 'APPROVED' then now() end
  from (values ('dd-crlv', 'CRLV', 'm-crlv', 'APPROVED'),
               ('dd-inspection', 'VEHICLE_INSPECTION', 'm-inspection', 'PENDING'),
               ('dd-profile', 'PROFILE_PHOTO', 'm-profile', 'PENDING'),
               ('dd-vphoto', 'VEHICLE_PHOTO', null, 'PENDING'),
               ('dd-criminal', 'CRIMINAL_RECORD', 'm-criminal', 'PENDING'),
               ('dd-municipal', 'MUNICIPAL_AUTHORIZATION', 'm-municipal', 'APPROVED'),
               ('dd-residence', 'RESIDENCE_PROOF', null, 'PENDING'),
               ('dd-free', 'OTHER', null, 'APPROVED'))
       as x(token, type, media, status)
  cross join driver d
  left join media_file m on m.token = x.media
  where d.token = 'd-driver';
insert into role_permissions (token, name, permissions)
  values ('rp-admin', 'ADMIN', '[\"list_contracts\"]'), ('rp-driver', 'DRIVER', '[]');
" >/dev/null

mig_apply "$(ls "$MIGRATIONS"/V72__*.sql)"

table "V72 on legacy data"
check M1 "the van, photo, residence proof and free types are removed from driver_document" "0" \
  "$(mig_sql "select count(*) from driver_document where deleted_at is null
              and document_type in ('CRLV', 'VEHICLE_INSPECTION', 'PROFILE_PHOTO', 'VEHICLE_PHOTO',
                                    'RESIDENCE_PROOF', 'OTHER')")"
check M2 "they are removed logically, not deleted" "6" \
  "$(mig_sql "select count(*) from driver_document where deleted_at is not null")"
check M3 "nothing becomes a van document, the document photo included" "0" \
  "$(mig_sql "select count(*) from vehicle_document")"
check M4 "the person's documents stay, the approved municipal authorization without number back in review" \
  "CRIMINAL_RECORD:PENDING,MUNICIPAL_AUTHORIZATION:PENDING" \
  "$(mig_sql "select string_agg(document_type || ':' || status, ',' order by document_type)
              from driver_document where deleted_at is null")"
check M4b "the municipal authorization back in review has no reviewer" "0" \
  "$(mig_sql "select count(*) from driver_document where document_type = 'MUNICIPAL_AUTHORIZATION' and reviewed_at is not null")"
check M5 "the vehicle has no document photo column" "0" \
  "$(mig_sql "select count(*) from information_schema.columns
              where table_name = 'vehicle' and column_name = 'photo_document_media_id'")"
check M6 "the files stay in media_file" "6" "$(mig_sql "select count(*) from media_file")"
check M7 "the van itself is untouched" "VAN1A23:true" \
  "$(mig_sql "select plate || ':' || is_active from vehicle where token = 'v-van' and deleted_at is null")"

table "V72 constraints"
mig_check_rejected C1 "a van document type in driver_document" driver_document_type_check \
  "insert into driver_document (token, driver_id, document_type) select 'x', id, 'CRLV' from driver limit 1"
mig_check_accepted C2 "a removed legacy row keeps its old type" \
  "insert into driver_document (token, driver_id, document_type, deleted_at) select 'x', id, 'CRLV', now() from driver limit 1"
mig_check_rejected C3 "a person's document type in vehicle_document" vehicle_document_type_check \
  "insert into vehicle_document (token, vehicle_id, document_type, expires_at) select 'x', id, 'CRIMINAL_RECORD', '2027-01-31' from vehicle limit 1"
mig_check_rejected C4 "an approved school vehicle authorization without number" vehicle_document_authorization_number_check \
  "insert into vehicle_document (token, vehicle_id, document_type, expires_at, status) select 'x', id, 'SCHOOL_VEHICLE_AUTHORIZATION', '2027-01-31', 'APPROVED' from vehicle limit 1"
mig_check_accepted C5 "an approved school vehicle authorization with number" \
  "insert into vehicle_document (token, vehicle_id, document_type, document_number, expires_at, status) select 'x', id, 'SCHOOL_VEHICLE_AUTHORIZATION', 'AUT-1', '2027-01-31', 'APPROVED' from vehicle limit 1"
mig_check_rejected C6 "an approved van document without expiry" vehicle_document_approved_expiry_check \
  "insert into vehicle_document (token, vehicle_id, document_type, status) select 'x', id, 'CRLV', 'APPROVED' from vehicle limit 1"
mig_check_accepted C6b "a pending van document without number nor expiry, as the driver sends it" \
  "insert into vehicle_document (token, vehicle_id, document_type) select 'x', id, 'SCHOOL_VEHICLE_AUTHORIZATION' from vehicle limit 1"
mig_check_rejected C6c "an approved municipal authorization without number" driver_document_authorization_number_check \
  "insert into driver_document (token, driver_id, document_type, status) select 'x', id, 'MUNICIPAL_AUTHORIZATION', 'APPROVED' from driver limit 1"
mig_check_accepted C6d "a pending municipal authorization without number" \
  "insert into driver_document (token, driver_id, document_type) select 'x', id, 'MUNICIPAL_AUTHORIZATION' from driver limit 1"
mig_check_rejected C7 "a vehicle document without vehicle" vehicle_id \
  "insert into vehicle_document (token, document_type, expires_at) values ('x', 'CRLV', '2027-01-31')"
mig_check_rejected C8 "two live documents with the same token" vehicle_document_token_active_key \
  "insert into vehicle_document (token, vehicle_id, document_type, expires_at) select 't', id, 'CRLV', date '2027-01-31' from vehicle union all select 't', id, 'CRLV', date '2027-01-31' from vehicle"

table "V72 permissions"
check P1 "the ADMIN bundle gets the five vehicle document permissions and keeps the old ones" \
  "delete_vehicle_document,list_contracts,list_vehicle_documents,restore_vehicle_document,show_vehicle_document,update_vehicle_document" \
  "$(mig_sql "select string_agg(p, ',' order by p) from role_permissions, jsonb_array_elements_text(permissions) p where name = 'ADMIN'")"
check P2 "no other bundle gets them" "0" \
  "$(mig_sql "select count(*) from role_permissions where name <> 'ADMIN' and permissions::text like '%vehicle_document%'")"

# ---------------------------------------------------------------------------------------------
# Part 1b — PR 4: V75 on the data V72 left
# ---------------------------------------------------------------------------------------------

mig_sql "
insert into users (token, type, name, email, document, verified, terms_accepted_at) values
  ('u-other', 'DRIVER', 'Other', 'other@qa', '39053344705', true, now());
insert into driver (token, user_id, base_price, approval_status, rejection_reason)
  select 'd-other', id, 90, 'REJECTED', 'Cadastro incompleto' from users where token = 'u-other';
insert into driver_document (token, driver_id, document_type, status, rejection_reason)
  select 'dd-rejected', id, 'CRIMINAL_RECORD', 'REJECTED', 'Ilegível' from driver where token = 'd-driver';
insert into driver_cnh (token, driver_id, registration_number, category, issue_date, valid_until)
  select 'cnh-driver', id, '01234567890', 'D', date '2024-01-10', date '2034-01-10'
  from driver where token = 'd-driver';
" >/dev/null

mig_apply "$(ls "$MIGRATIONS"/V75__*.sql)"

table "V75 on legacy data"
check R1 "a rejected document gets the reason code OTHER and keeps its message" "OTHER:Ilegível" \
  "$(mig_sql "select rejection_reason_code || ':' || rejection_reason from driver_document where token = 'dd-rejected'")"
check R2 "a rejected driver gets the reason code OTHER" "OTHER" \
  "$(mig_sql "select rejection_reason_code from driver where token = 'd-other'")"
check R3 "an existing CNH becomes PENDING, at version 0" "PENDING:0" \
  "$(mig_sql "select status || ':' || version from driver_cnh where token = 'cnh-driver'")"
check R4 "the documents start at version 0 and not superseded" "0" \
  "$(mig_sql "select count(*) from driver_document where version <> 0 or superseded_at is not null")"
check R5 "the other documents get no reason code" "1" \
  "$(mig_sql "select count(*) from driver_document where rejection_reason_code is not null")"
check R6 "btree_gist is installed" "1" \
  "$(mig_sql "select count(*) from pg_extension where extname = 'btree_gist'")"
check R7 "the old one-CNH-per-driver and unique-number indexes are gone" "0" \
  "$(mig_sql "select count(*) from pg_indexes where indexname in ('driver_cnh_driver_active_key', 'driver_cnh_registration_active_key')")"
check R8 "the three submissions keep who last corrected their data, and when" "6" \
  "$(mig_sql "select count(*) from information_schema.columns where table_name in ('driver_document', 'vehicle_document', 'driver_cnh')
              and column_name in ('data_corrected_by', 'data_corrected_at')")"

table "V75 constraints"
mig_check_rejected K1 "an unknown reason code on a document" driver_document_rejection_reason_code_check \
  "update driver_document set rejection_reason_code = 'BLURRY' where token = 'dd-criminal'"
mig_check_accepted K2 "a van document with a valid reason code" \
  "insert into vehicle_document (token, vehicle_id, document_type, expires_at, status, rejection_reason_code) select 'x', id, 'CRLV', date '2027-01-31', 'REJECTED', 'EXPIRED' from vehicle limit 1"
mig_check_accepted K2b "NOT_ELIGIBLE is a reason code: a true document that does not allow school transport" \
  "update driver_cnh set status = 'REJECTED', rejection_reason_code = 'NOT_ELIGIBLE' where token = 'cnh-driver'"
mig_check_rejected K3 "an unknown CNH status" driver_cnh_status_check \
  "update driver_cnh set status = 'EXPIRED' where token = 'cnh-driver'"
mig_check_rejected K4 "an unknown driver rejection code" driver_rejection_reason_code_check \
  "update driver set rejection_reason_code = 'ILLEGIBLE' where token = 'd-other'"
mig_check_rejected K5 "the same CNH number on two drivers" driver_cnh_registration_one_driver_excl \
  "insert into driver_cnh (token, driver_id, registration_number, category, issue_date, valid_until) select 'x', id, '01234567890', 'D', date '2024-01-10', date '2034-01-10' from driver where token = 'd-other'"
mig_check_accepted K6 "the same CNH number again on the same driver (a renewal)" \
  "insert into driver_cnh (token, driver_id, registration_number, category, issue_date, valid_until) select 'x', id, '01234567890', 'D', date '2026-01-10', date '2036-01-10' from driver where token = 'd-driver'"
mig_check_accepted K7 "another driver may reuse a number of a removed CNH" \
  "update driver_cnh set deleted_at = now() where token = 'cnh-driver'; insert into driver_cnh (token, driver_id, registration_number, category, issue_date, valid_until) select 'x', id, '01234567890', 'D', date '2024-01-10', date '2034-01-10' from driver where token = 'd-other'"

table "V75 permissions"
check P3 "the ADMIN bundle gets review_documents" "1" \
  "$(mig_sql "select count(*) from role_permissions where name = 'ADMIN' and permissions @> '[\"review_documents\"]'::jsonb")"
check P4 "no other bundle gets it" "0" \
  "$(mig_sql "select count(*) from role_permissions where name <> 'ADMIN' and permissions @> '[\"review_documents\"]'::jsonb")"

# ---------------------------------------------------------------------------------------------
# Part 1c — PR 5: V76 on the data V75 left
# ---------------------------------------------------------------------------------------------

mig_sql "
insert into driver_document (token, driver_id, document_type, status, created_at)
  select x.token, d.id, 'SCHOOL_DRIVER_COURSE', 'PENDING', x.at
  from (values ('dd-course-old', now() - interval '2 days'), ('dd-course-new', now() - interval '1 day'))
       as x(token, at)
  cross join driver d where d.token = 'd-driver';
insert into vehicle_document (token, vehicle_id, document_type, expires_at, status, rejection_reason)
  select 'vd-rejected', id, 'VEHICLE_INSPECTION', date '2027-01-31', 'REJECTED', 'Ilegível'
  from vehicle where token = 'v-van';
" >/dev/null

mig_apply "$(ls "$MIGRATIONS"/V76__*.sql)"

table "V76 on legacy data"
check Q1 "of two pending submissions of the same owner and type, the newest stays pending" "dd-course-new" \
  "$(mig_sql "select token from driver_document where document_type = 'SCHOOL_DRIVER_COURSE' and superseded_at is null and status = 'PENDING'")"
check Q2 "the older one is superseded" "1" \
  "$(mig_sql "select count(*) from driver_document where token = 'dd-course-old' and superseded_at is not null")"
check Q3 "a rejection without code made before V76 gets OTHER" "OTHER" \
  "$(mig_sql "select rejection_reason_code from vehicle_document where token = 'vd-rejected'")"

table "V76 constraints"
mig_check_rejected U1 "a second pending submission of the same owner and type" driver_document_one_pending_key \
  "insert into driver_document (token, driver_id, document_type) select 'x', id, 'SCHOOL_DRIVER_COURSE' from driver where token = 'd-driver'"
mig_check_rejected U2 "a second pending van document of the same type" vehicle_document_one_pending_key \
  "insert into vehicle_document (token, vehicle_id, document_type, expires_at) select 'x' || n, id, 'CRLV', date '2027-01-31' from vehicle, generate_series(1, 2) n where token = 'v-van'"
mig_check_rejected U3 "a second pending CNH of the same driver" driver_cnh_one_pending_key \
  "insert into driver_cnh (token, driver_id, registration_number, category, issue_date, valid_until) select 'x', id, '01234567890', 'D', date '2026-01-10', date '2036-01-10' from driver where token = 'd-driver'"
mig_check_accepted U4 "a new pending submission next to a superseded and a removed one" \
  "update driver_document set deleted_at = now() where token = 'dd-course-new'; insert into driver_document (token, driver_id, document_type) select 'x', id, 'SCHOOL_DRIVER_COURSE' from driver where token = 'd-driver'"
mig_check_rejected U5 "a rejected document without reason code" driver_document_rejected_reason_check \
  "update driver_document set status = 'REJECTED' where token = 'dd-course-new'"
mig_check_rejected U6 "a rejected van document without reason code" vehicle_document_rejected_reason_check \
  "update vehicle_document set rejection_reason_code = null where token = 'vd-rejected'"
mig_check_rejected U7 "a rejected CNH without reason code" driver_cnh_rejected_reason_check \
  "update driver_cnh set status = 'REJECTED' where token = 'cnh-driver'"

sql "drop database $MIGRATION_DB" >/dev/null

# ---------------------------------------------------------------------------------------------
# Part 2 — the live API
# ---------------------------------------------------------------------------------------------

ADMIN=$(login "$ADMIN_EMAIL")
DRIVER=$(login "$SEED_DRIVER_EMAIL")
CLIENT=$(login "$SEED_CLIENT_EMAIL")
VAN=$(sql "select v.token from vehicle v join driver d on d.id = v.driver_id join users u on u.id = d.user_id
           where u.email = '$SEED_DRIVER_EMAIL' and v.deleted_at is null order by v.id limit 1")

table "Flyway on the live database"
check F1 "V72 is applied by Flyway" "1" \
  "$(sql "select count(*) from flyway_schema_history where version = '72' and success")"
check F3 "V75 is applied by Flyway" "1" \
  "$(sql "select count(*) from flyway_schema_history where version = '75' and success")"
check F5 "V76 is applied by Flyway" "1" \
  "$(sql "select count(*) from flyway_schema_history where version = '76' and success")"
check F4 "review_documents is in the live ADMIN bundle only" "ADMIN" \
  "$(sql "select string_agg(name, ',') from role_permissions where permissions @> '[\"review_documents\"]'::jsonb")"
check F2 "the seed's documents have only the person's types" "0" \
  "$(sql "select count(*) from driver_document where deleted_at is null
          and document_type not in ('MUNICIPAL_AUTHORIZATION', 'SCHOOL_DRIVER_COURSE', 'CRIMINAL_RECORD')")"

table "The van without its document photo"
request GET "/api/vehicles/$VAN" "$DRIVER"
expect V1 "the owner reads the van: no photoDocumentUrl" 200 'has("photoDocumentUrl") | not'
upload "/api/vehicles/$VAN/photo-document" "$DRIVER"
expect V2 "uploading a document photo: the route is gone" 404
request GET "/api/vehicles/$VAN/photo-document" "$DRIVER"
expect V3 "reading the document photo: the route is gone" 404
upload "/api/vehicles/$VAN/photo-front" "$DRIVER"
expect V4 "the front photo still uploads" 201
request GET "/api/vehicles/$VAN" "$ADMIN"
expect V5 "the admin reads the van with the front photo URL" 200 '.photoFrontUrl | startswith("/api/vehicles/")'

table "Driver documents with the person's types only"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "CRLV", "expiresAt": "'"$IN_60_DAYS"'"}'
expect D1 "a CRLV as a driver document is refused" 400
send_doc /api/driver-documents "$DRIVER" '{"documentType": "PROFILE_PHOTO"}'
expect D2 "a profile photo as a driver document is refused" 400
before=$(count driver_document)
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
expect D3 "the specialized course is accepted with its file and no data" 201 \
  '.documentType == "SCHOOL_DRIVER_COURSE" and .status == "PENDING" and .file != null and .documentNumber == null'
ACCEPTED_COURSE=$(jq -r .token <<<"$RESP_BODY")
check D4 "only the accepted one was written" "$((before + 1))" "$(count driver_document)"
request DELETE "/api/driver-documents/$ACCEPTED_COURSE" "$DRIVER"

table "Onboarding reading the van's documents"
request GET /api/drivers/me/onboarding "$DRIVER"
expect O1 "the driver reads the documents step per van" 200 \
  '.documentsStep | has("missingDriverTypes") and (.vehicles | type == "array") and (has("missingTypes") | not)'
expect O2 "each active van is listed with plate and the two van documents of today's set" 200 \
  '[.documentsStep.vehicles[] | select(.vehicleToken == "'"$VAN"'")] | length == 1 and (.[0].plate | length > 0)'
request GET /api/drivers/me/onboarding "$CLIENT"
expect O3 "a client is refused" 403
request GET /api/drivers/me/onboarding ""
expect O4 "no token" 401

# ---------------------------------------------------------------------------------------------
# Part 3 — PR 2: the van's documents, the review, and the driver's documents
# ---------------------------------------------------------------------------------------------

OTHER_EMAIL=$(sql "select u.email from users u join driver d on d.user_id = u.id
                   where u.email like '%@seed.vanep.com.br' and u.email <> '$SEED_DRIVER_EMAIL'
                   and u.deleted_at is null order by u.id limit 1")
OTHER=$(login "$OTHER_EMAIL")
ADMIN_UID=$(claims "$ADMIN" | jq -r .uid)
docs() { sql "select count(*) from vehicle_document"; }
# Reads the submission as the reviewer opens it and sends the decision with the version it saw.
review() {
  local kind=$1 token=$2 decision=$3 who=${4:-$ADMIN} version
  request GET "/api/$kind/$token" "$ADMIN"
  version=$(jq -r .version <<<"$RESP_BODY")
  decide "/api/$kind/$token/status" "$who" "$(jq -c --argjson v "$version" '. + {version: $v}' <<<"$decision")"
}

table "The life of a van document"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV", "documentNumber": " RENAVAM-00912 ", "expiresAt": "'"$IN_120_DAYS"'"}'
expect L1 "the owner sends the CRLV of the van with its file, without typing its data" 201 \
  '.status == "PENDING" and .vehicleToken == "'"$VAN"'" and .documentNumber == null and .expiresAt == null and .file.mimeType == "application/pdf"'
DOC=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$DOC/file" "$DRIVER"
expect L2 "the owner replaces the CRLV's file while it is pending" 201
request GET "/api/vehicle-documents/$DOC" "$DRIVER"
expect L2b "the document describes its file" 200 \
  '.file.mimeType == "application/pdf" and .file.sizeBytes == '"$(pdf | wc -c | tr -d ' ')"' and (.file.originalName | endswith(".pdf"))'
download "/api/vehicle-documents/$DOC/file" "$DRIVER"
check L3 "the owner reads the file back as PDF" "200 application/pdf" "$RESP_STATUS $(cut -d';' -f1 <<<"$RESP_TYPE")"
review vehicle-documents "$DOC" '{"status": "APPROVED"}' "$DRIVER"
expect L4 "the owner tries to approve their own document" 403
check L5 "the status did not change" "PENDING" "$(sql "select status from vehicle_document where token = '$DOC'")"
review vehicle-documents "$DOC" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect L6 "the admin approves it with the number and the expiry read from the file" 200 \
  '.status == "APPROVED" and .reviewMethod == "MANUAL" and .reviewedByUserToken == "'"$ADMIN_UID"'" and .reviewedAt != null and .documentNumber == "RENAVAM-00912" and .expiresAt == "'"$IN_120_DAYS"'"'
upload_pdf "/api/vehicle-documents/$DOC/file" "$DRIVER"
expect L7 "the owner cannot swap the file of the approved document" 409 '.code == "document.submission.reviewed"'
request GET "/api/vehicle-documents/$DOC" "$DRIVER"
expect L8 "the approved document keeps its decision" 200 '.status == "APPROVED" and .reviewedByUserToken != null'
request PUT "/api/vehicle-documents/$DOC" "$DRIVER" '{"documentType": "CRLV", "documentNumber": "RENAVAM-00912", "expiresAt": "'"$IN_365_DAYS"'"}'
expect L9 "the owner cannot edit the data: there is no PUT of a document" 405
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV", "documentNumber": "RENAVAM-00912", "expiresAt": "'"$IN_365_DAYS"'"}'
expect L10 "a renewed CRLV is a new submission" 201 '.status == "PENDING"'
RENEWAL=$(jq -r .token <<<"$RESP_BODY")
review vehicle-documents "$RENEWAL" '{"status": "REJECTED", "rejectionReasonCode": "OTHER", "rejectionReason": "  Ilegível  "}'
expect L11 "the admin rejects it with a code and a message, stored trimmed" 200 \
  '.status == "REJECTED" and .rejectionReasonCode == "OTHER" and .rejectionReason == "Ilegível"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_180_DAYS"'"}'
NEW_INSPECTION=$(jq -r .token <<<"$RESP_BODY")
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_200_DAYS"'"}'
expect L12 "a second pending inspection of the van is refused" 409 '.code == "document.submission.pending_exists"'
request DELETE "/api/vehicle-documents/$NEW_INSPECTION" "$DRIVER"
request GET "/api/vehicles/$VAN/documents?status=REJECTED" "$ADMIN"
expect L13 "the admin filters the van's documents by status" 200 \
  '([.content[].token] | index("'"$RENEWAL"'") != null) and all(.content[]; .status == "REJECTED")'
request GET /api/drivers/me/onboarding "$DRIVER"
expect L14 "the onboarding shows the van document with its file URL" 200 \
  '[.documentsStep.vehicles[] | select(.vehicleToken == "'"$VAN"'") | .uploadedDocuments[] | select(.token == "'"$DOC"'") | .fileUrl] | .[0] | startswith("/api/vehicle-documents/")'

table "Who may touch a van's documents"
before=$(docs)
request GET "/api/vehicles/$VAN/documents" "$OTHER"
expect A1 "another driver lists the van's documents" 403
send_doc "/api/vehicles/$VAN/documents" "$OTHER" '{"documentType": "CRLV", "expiresAt": "'"$IN_120_DAYS"'"}'
expect A2 "another driver adds a document to the van" 403
request GET "/api/vehicle-documents/$DOC" "$OTHER"
expect A3 "another driver reads the document" 403
download "/api/vehicle-documents/$DOC/file" "$OTHER"
check A4 "another driver reads the file and gets no content" "403 0" "$RESP_STATUS $(wc -c <"$BODY_FILE" | tr -d ' ')"
upload_pdf "/api/vehicle-documents/$DOC/file" "$OTHER"
expect A5 "another driver replaces the file" 403
request PUT "/api/vehicle-documents/$DOC" "$OTHER" '{"documentType": "CRLV", "expiresAt": "'"$IN_120_DAYS"'"}'
expect A6 "another driver tries to edit the document: there is no PUT" 405
request DELETE "/api/vehicle-documents/$DOC" "$OTHER"
expect A7 "another driver removes the document" 403
request GET "/api/vehicles/$VAN/documents" "$CLIENT"
expect A8 "a client lists the van's documents" 403
request GET "/api/vehicle-documents/$DOC" "$CLIENT"
expect A9 "a client reads the document" 403
request GET "/api/vehicles/$VAN/documents" ""
expect A10 "no token" 401
request GET "/api/vehicle-documents/$DOC" "not-a-jwt"
expect A11 "an invalid token" 401
request GET /api/vehicles/unknown-van/documents "$ADMIN"
expect A12 "an unknown van" 404
request GET /api/vehicle-documents/unknown-document "$ADMIN"
expect A13 "an unknown document" 404
check A14 "nothing refused was written" "$before" "$(docs)"
request GET "/api/vehicles/$VAN/documents" "$ADMIN"
expect A15 "the admin lists the van's documents" 200 '.totalElements >= 3'
send_doc "/api/vehicles/$VAN/documents" "$ADMIN" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION", "documentNumber": "AUT-118/2026", "expiresAt": "'"$IN_60_DAYS"'"}'
expect A16 "the admin sends a document for the van" 201 '.status == "PENDING" and .documentNumber == null'
request DELETE "/api/vehicle-documents/$(jq -r .token <<<"$RESP_BODY")" "$DRIVER"
expect A17 "the owner removes the pending one, so the run can be repeated" 204


table "What a van document must have"
before=$(docs)
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRIMINAL_RECORD"}'
expect B1 "a person's document type" 400
send_doc_without_file "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV"}'
expect B2 "a document without file" 400 '.field == "file"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{}'
expect B4 "a document without type" 400 '.field == "documentType"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "crlv"}'
expect B6 "a type in lower case" 400
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": 7}'
expect B7 "a type that is not one of the van's" 400
check B9 "nothing refused was written" "$before" "$(docs)"
UNREAD=$(curl -s -X POST "$BASE_URL/api/vehicles/$VAN/documents" -H "Authorization: Bearer $DRIVER" \
  -F "file=@$(f=$(mktemp).pdf; pdf >"$f"; echo "$f");type=application/pdf" -F documentType=CRLV \
  -F documentNumber=RENAVAM-1 -F expiresAt="$IN_120_DAYS")
check B10 "the number and the expiry sent with the file are not read" "PENDING null null" \
  "$(jq -r '"\(.status) \(.documentNumber) \(.expiresAt)"' <<<"$UNREAD")"
request DELETE "/api/vehicle-documents/$(jq -r .token <<<"$UNREAD")" "$DRIVER"
expect B11 "the owner removes the pending one" 204

table "What the approval must carry"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION"}'
AUTH=$(jq -r .token <<<"$RESP_BODY")
review vehicle-documents "$AUTH" '{"status": "APPROVED", "documentNumber": "  ", "expiresAt": "'"$IN_120_DAYS"'"}'
expect P1 "approving an authorization without number" 400 \
  '.code == "document.review.field_required" and .field == "documentNumber" and .detail == "Preencha os dados obrigatórios do documento para aprovar."'
review vehicle-documents "$AUTH" '{"status": "APPROVED", "documentNumber": "AUT-118/2026"}'
expect P2 "approving without the expiry" 400 '.code == "document.review.field_required" and .field == "expiresAt"'
review vehicle-documents "$AUTH" "{\"status\": \"APPROVED\", \"documentNumber\": \"$(printf 'A%.0s' {1..65})\", \"expiresAt\": \"$IN_120_DAYS\"}"
expect P3 "a number of 65 characters" 400 '.field == "documentNumber"'
review vehicle-documents "$AUTH" '{"status": "APPROVED", "documentNumber": "AUT-1", "expiresAt": "31/03/2027"}'
expect P4 "an expiry in the wrong format" 400
check P5 "the refused approvals changed nothing" "PENDING::" \
  "$(sql "select status || ':' || coalesce(document_number, '') || ':' || coalesce(expires_at::text, '') from vehicle_document where token = '$AUTH'")"
review vehicle-documents "$AUTH" "{\"status\": \"APPROVED\", \"documentNumber\": \"  $(printf 'A%.0s' {1..64}) \", \"expiresAt\": \"$IN_365_DAYS\"}"
expect P6 "a number of exactly 64 characters, stored without the outer spaces" 200 \
  '.status == "APPROVED" and (.documentNumber | length == 64) and .expiresAt == "'"$IN_365_DAYS"'"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV"}'
REJECTED_CRLV=$(jq -r .token <<<"$RESP_BODY")
review vehicle-documents "$REJECTED_CRLV" '{"status": "REJECTED", "rejectionReasonCode": "NOT_ELIGIBLE", "documentNumber": "X", "expiresAt": "'"$IN_120_DAYS"'"}'
expect P7 "a rejection as NOT_ELIGIBLE does not store the data of its body" 200 \
  '.status == "REJECTED" and .rejectionReasonCode == "NOT_ELIGIBLE" and .documentNumber == null and .expiresAt == null'

table "Correcting the data of an approved document"
review vehicle-documents "$AUTH" '{"status": "APPROVED"}' >/dev/null
request GET "/api/vehicle-documents/$AUTH" "$ADMIN"
SEEN=$(jq -r .version <<<"$RESP_BODY")
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" "{\"documentNumber\": \" AUT-118/2026 \", \"expiresAt\": \"$IN_300_DAYS\", \"version\": $SEEN}"
expect Q1 "the reviewer corrects a mistyped number and expiry" 200 \
  '.status == "APPROVED" and .documentNumber == "AUT-118/2026" and .expiresAt == "'"$IN_300_DAYS"'" and .reviewedByUserToken == "'"$ADMIN_UID"'" and .dataCorrectedAt != null and .version == '"$((SEEN + 1))"''
check Q2 "the correction records who corrected and keeps the file" "$ADMIN_UID:yes" \
  "$(sql "select u.token || ':' || case when d.file_media_id is not null then 'yes' else 'no' end from vehicle_document d join users u on u.id = d.data_corrected_by where d.token = '$AUTH'")"
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" "{\"documentNumber\": \"AUT-118/2026\", \"expiresAt\": \"$IN_300_DAYS\", \"version\": $SEEN}"
expect Q3 "a correction on the version before is stale" 409 '.code == "document.review.stale"'
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" "{\"expiresAt\": \"$IN_300_DAYS\", \"version\": $((SEEN + 1))}"
expect Q4 "a correction without the required number" 400 '.code == "document.review.field_required" and .field == "documentNumber"'
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" "{\"documentNumber\": \"AUT-118/2026\", \"expiresAt\": \"$YESTERDAY\", \"version\": $((SEEN + 1))}"
expect Q5 "an expiry before today may be corrected: the document is then expired" 200 '.expiresAt == "'"$YESTERDAY"'"'
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" "{\"documentNumber\": \"AUT-118/2026\", \"expiresAt\": \"$IN_365_DAYS\", \"version\": $((SEEN + 2))}"
expect Q6 "the reviewer puts the right expiry back" 200 '.expiresAt == "'"$IN_365_DAYS"'"'
request PUT "/api/vehicle-documents/$AUTH/data" "$DRIVER" "{\"documentNumber\": \"X\", \"expiresAt\": \"$IN_365_DAYS\", \"version\": $((SEEN + 3))}"
expect Q7 "the owner cannot correct" 403
request GET "/api/vehicle-documents/$REJECTED_CRLV" "$ADMIN"
request PUT "/api/vehicle-documents/$REJECTED_CRLV/data" "$ADMIN" "{\"expiresAt\": \"$IN_120_DAYS\", \"version\": $(jq -r .version <<<"$RESP_BODY")}"
expect Q8 "a rejected document is not corrected" 409 '.code == "document.data.not_approved"'
request PUT "/api/vehicle-documents/$AUTH/data" "$ADMIN" '{"documentNumber": "AUT-1"}'
expect Q9 "a correction without version" 400 '.field == "version"'

table "The review of a van document"
NOFILE="qa-nofile-$(date +%s)"
sql "insert into vehicle_document (token, vehicle_id, document_type) select '$NOFILE', id, 'CRLV' from vehicle where token = '$VAN'" >/dev/null
review vehicle-documents "$NOFILE" '{"status": "APPROVED"}'
expect R1 "approving a document left without file before this change" 422 \
  '.code == "document.review.file_required" and .detail == "O documento só pode ser aprovado depois do envio do arquivo."'
review vehicle-documents "$NOFILE" '{"status": "REJECTED", "rejectionReason": "Ilegível"}'
expect R2 "rejecting without a reason code" 400 '.code == "document.review.reason_code_required"'
review vehicle-documents "$NOFILE" '{"status": "REJECTED", "rejectionReasonCode": "OTHER", "rejectionReason": " "}'
expect R2b "rejecting for another reason without a message" 400 \
  '.code == "document.review.reason_required" and .detail == "Informe o motivo da recusa."'
review vehicle-documents "$NOFILE" '{"reviewMethod": "MANUAL"}'
expect R3 "a review without status" 400 '.field == "status"'
review vehicle-documents "$NOFILE" '{"status": "DONE"}'
expect R4 "an unknown status" 400
decide "/api/vehicle-documents/$NOFILE/status" "$ADMIN" '{"status": "APPROVED"}'
expect R4b "a review without the version it saw" 400 '.field == "version"'
check R5 "the refused reviews changed nothing" "PENDING:" \
  "$(sql "select status || ':' || coalesce(reviewed_by::text, '') from vehicle_document where token = '$NOFILE'")"

table "Removing and restoring"
request DELETE "/api/vehicle-documents/$NOFILE" "$DRIVER"
expect X1 "the owner removes a document" 204
request GET "/api/vehicle-documents/$NOFILE" "$DRIVER"
expect X2 "the removed document is gone" 404
request POST "/api/vehicle-documents/$NOFILE/restore" "$DRIVER"
expect X3 "the owner cannot restore it" 403
request POST "/api/vehicle-documents/$NOFILE/restore" "$ADMIN"
expect X4 "the admin restores it" 200 '.token == "'"$NOFILE"'" and .status == "PENDING"'
request POST "/api/vehicle-documents/$NOFILE/restore" "$ADMIN"
expect X5 "restoring a live document" 409
request POST /api/vehicle-documents/unknown-document/restore "$ADMIN"
expect X6 "restoring an unknown document" 404
request DELETE "/api/vehicle-documents/$NOFILE" "$DRIVER"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV", "expiresAt": "'"$IN_120_DAYS"'"}'
ANOTHER=$(jq -r .token <<<"$RESP_BODY")
request POST "/api/vehicle-documents/$NOFILE/restore" "$ADMIN"
expect X6b "restoring a pending document next to another pending one" 409 '.code == "document.submission.pending_exists"'
check X6c "the refused restore left it removed" "1" \
  "$(sql "select count(*) from vehicle_document where token = '$NOFILE' and deleted_at is not null")"
request DELETE "/api/vehicle-documents/$ANOTHER" "$DRIVER"
request POST /api/vehicles/me "$DRIVER" '{"plate": "QAD9F01", "brand": "Renault", "model": "Master", "manufactureYear": 2021, "color": "Prata", "capacity": 16}'
expect X7 "the owner adds a second van" 201
VAN2=$(jq -r .token <<<"$RESP_BODY")
send_doc "/api/vehicles/$VAN2/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_150_DAYS"'"}'
VAN2_DOC=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/vehicles/$VAN2" "$DRIVER"
expect X8 "the owner removes the second van" 204
request GET "/api/vehicles/$VAN2/documents" "$DRIVER"
expect X9 "the removed van's documents cannot be listed" 404
request GET "/api/vehicle-documents/$VAN2_DOC" "$DRIVER"
expect X10 "the removed van's document is unreachable to the owner" 404
request GET "/api/vehicle-documents/$VAN2_DOC" "$ADMIN"
expect X11 "and to the admin" 404
request POST "/api/vehicles/$VAN2/restore" "$DRIVER"
expect X12 "the owner restores the van" 200
request GET "/api/vehicles/$VAN2/documents" "$DRIVER"
expect X13 "the restored van brings its documents back" 200 '[.content[].token] == ["'"$VAN2_DOC"'"]'
request DELETE "/api/vehicles/$VAN2" "$DRIVER"
expect X14 "the second van is removed again, so the run can be repeated" 204

table "Driver documents with number, clean review and the caller by uid"
before=$(count driver_document)
send_doc /api/driver-documents "$DRIVER" '{"documentType": "VEHICLE_INSPECTION"}'
expect DD2 "a van document type" 400
send_doc_without_file /api/driver-documents "$DRIVER" '{"documentType": "CRIMINAL_RECORD"}'
expect DD2b "a driver document without file" 400 '.field == "file"'
check DD3 "nothing refused was written" "$before" "$(count driver_document)"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "MUNICIPAL_AUTHORIZATION"}'
MUNI=$(jq -r .token <<<"$RESP_BODY")
review driver-documents "$MUNI" '{"status": "APPROVED"}'
expect DD1 "approving a municipal authorization without number" 400 '.code == "document.review.field_required" and .field == "documentNumber"'
request DELETE "/api/driver-documents/$MUNI" "$DRIVER"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE", "documentNumber": " CERT-2024-118 "}'
expect DD4 "the specialized course is sent with its file and no number" 201 '.status == "PENDING" and .documentNumber == null and .file != null'
COURSE=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$COURSE/file" "$DRIVER"
expect DD5 "the owner replaces the course's file while it is pending" 201
review driver-documents "$COURSE" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect DD6 "the admin approves it with the number read from the file, resolved by uid" 200 \
  '.status == "APPROVED" and .reviewedByUserToken == "'"$ADMIN_UID"'" and .documentNumber == "CERT-2024-118"'
upload_pdf "/api/driver-documents/$COURSE/file" "$DRIVER"
expect DD7 "the owner cannot swap the file of the approved course" 409 '.code == "document.submission.reviewed"'
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE", "documentNumber": "CERT-2025-007"}'
RECOURSE=$(jq -r .token <<<"$RESP_BODY")
review driver-documents "$RECOURSE" '{"status": "REJECTED"}'
expect DD8 "rejecting without a reason code" 400 '.code == "document.review.reason_code_required"'
review driver-documents "$RECOURSE" '{"status": "APPROVED"}' "$DRIVER"
expect DD9 "the owner cannot review" 403
review driver-documents "$RECOURSE" '{"status": "REJECTED", "rejectionReasonCode": "INCOMPLETE"}'
expect DD9b "the admin rejects the new course as incomplete" 200 '.rejectionReasonCode == "INCOMPLETE" and .rejectionReason == null'
request GET "/api/driver-documents/$COURSE" "$ADMIN"
request PUT "/api/driver-documents/$COURSE/data" "$ADMIN" "{\"documentNumber\": \"CERT-2024-119\", \"version\": $(jq -r .version <<<"$RESP_BODY")}"
expect DD9c "the reviewer corrects the course's number" 200 '.status == "APPROVED" and .documentNumber == "CERT-2024-119" and .dataCorrectedAt != null'
DRIVER_USER=$(sql "select token from users where email = '$SEED_DRIVER_EMAIL'")
sql "update users set email = 'renamed.qa@seed.vanep.com.br' where token = '$DRIVER_USER'" >/dev/null
request GET "/api/driver-documents?size=500" "$DRIVER"
sql "update users set email = '$SEED_DRIVER_EMAIL' where token = '$DRIVER_USER'" >/dev/null
expect DD10 "the token's subject no longer matches an e-mail and the driver still lists their documents" 200 \
  '[.content[].token] | index("'"$COURSE"'") != null'
request GET "/api/driver-documents/$COURSE" "$OTHER"
expect DD11 "another driver reads the document" 403

table "Regression"
upload "/api/vehicles/$VAN/photo-side" "$DRIVER"
expect G1 "the side photo still uploads" 201
request GET "/api/driver-documents?status=REJECTED" "$ADMIN"
expect G2 "the admin lists rejected driver documents" 200 '.totalElements >= 1'

# ---------------------------------------------------------------------------------------------
# Part 4 — PR 3: credentials, the legal onboarding and the approval
# ---------------------------------------------------------------------------------------------

valid_cpf() {
  local digits=() sum index check
  for index in 0 1 2 3 4 5 6 7 8; do digits+=($((RANDOM % 10))); done
  for check in 9 10; do
    sum=0
    for ((index = 0; index < check; index++)); do sum=$((sum + digits[index] * (check + 1 - index))); done
    sum=$(((sum * 10) % 11 % 10))
    digits+=("$sum")
  done
  printf '%s' "${digits[@]}"
}

credentials() { request GET "/api/vehicles/$1/school-transport-credentials" "$2"; }
satisfied_of() { jq -r --arg r "$1" '.requirements[] | select(.requirement == $r) | .satisfied' <<<"$RESP_BODY"; }

RUN=$(date +%s)
NEW_EMAIL="qa.motorista.$RUN@vanep.test"
LETTERS=ABCDEFGHIJKLMNOPQRSTUVWXYZ
random_plate() { printf 'Q%s%s%d%s%02d' "$1" "${LETTERS:RANDOM%26:1}" $((RANDOM % 10)) "${LETTERS:RANDOM%26:1}" $((RANDOM % 100)); }
NEW_PLATE=$(random_plate D)
SECOND_PLATE=$(random_plate E)
CNH_NUMBER="9$(printf '%010d' $((RUN % 10000000000)))"
SOON=$(date -v+1y +%F 2>/dev/null || date -d '+1 year' +%F)
CNH_ISSUED=$(in_days -365)
CNH_REISSUED=$(in_days -364)

table "A new driver from scratch"
NEW_PASSWORD='Senha@Forte2026'
request POST /api/auth/signup/driver "" "{\"name\": \"Motorista QA\", \"email\": \"$NEW_EMAIL\", \"password\": \"$NEW_PASSWORD\", \"document\": \"$(valid_cpf)\", \"acceptTerms\": true, \"basePrice\": 180.00}"
expect N1 "a new driver signs up" 201
# The e-mail code goes by mail (disabled here) and the city comes from Google Places (unreachable):
# both are set directly, they are not what this change delivers.
sql "insert into address (token, city_id, street) select 'qa$RUN', city_id, 'Rua do QA' from address order by id limit 1" >/dev/null
sql "update users set verified = true, address_id = (select id from address where token = 'qa$RUN') where email = '$NEW_EMAIL'" >/dev/null
NEW=$(SEED_PASSWORD=$NEW_PASSWORD login "$NEW_EMAIL")
NEW_DRIVER=$(sql "select d.token from driver d join users u on u.id = d.user_id where u.email = '$NEW_EMAIL'")
request POST /api/vehicles/me "$NEW" "{\"plate\": \"$NEW_PLATE\", \"brand\": \"Fiat\", \"model\": \"Ducato\", \"manufactureYear\": 2023, \"color\": \"Branca\", \"capacity\": 15}"
expect N2 "the driver adds a van" 201
NVAN=$(jq -r .token <<<"$RESP_BODY")
credentials "$NVAN" "$NEW"
expect N3 "a new van: nothing satisfied but the optional municipal authorization is not required" 200 \
  '.satisfied == false and ([.requirements[] | select(.optional | not) | .satisfied] | all(. == false)) and ([.requirements[] | select(.optional)] | length == 1)'
send_doc /api/driver-cnhs "$NEW" "{\"registrationNumber\": \"$CNH_NUMBER\", \"category\": \"B\", \"issueDate\": \"$CNH_ISSUED\", \"validUntil\": \"$SOON\"}"
expect N4 "the driver sends a photo of the license, without typing its data" 201 \
  '.status == "PENDING" and .registrationNumber == null and .category == null and .validUntil == null and .file != null'
CNH=$(jq -r .token <<<"$RESP_BODY")
check N4b "nothing of the license was typed" ":::" \
  "$(sql "select coalesce(registration_number, '') || ':' || coalesce(category, '') || ':' || coalesce(issue_date::text, '') || ':' || coalesce(valid_until::text, '') from driver_cnh where token = '$CNH'")"
request GET /api/drivers/me/onboarding "$NEW"
expect N5 "the license step counts the CNH sent with its file" 200 '.cnhStep.completed == true'
credentials "$NVAN" "$NEW"
check N6 "a pending CNH does not satisfy DRIVER_LICENSE yet" "false" "$(satisfied_of DRIVER_LICENSE)"
review driver-cnhs "$CNH" '{"status": "APPROVED"}'
expect N6b "approving the license as category B is refused on the category field" 400 '.field == "category" and (.detail | contains("D, E, AD"))'
review driver-cnhs "$CNH" '{"status": "APPROVED", "category": "AD", "registrationNumber": null}'
expect N6c "approving without the registration number names the field" 400 '.code == "document.review.field_required" and .field == "registrationNumber"'
check N6d "the refused approvals left the CNH pending, without data" "PENDING:" \
  "$(sql "select status || ':' || coalesce(category, '') from driver_cnh where token = '$CNH'")"
review driver-cnhs "$CNH" '{"status": "APPROVED", "reviewMethod": "MANUAL", "category": " ad "}'
expect N7 "the admin approves the CNH with the data read from it, category AD" 200 \
  '.status == "APPROVED" and .category == "AD" and .registrationNumber == "'"$CNH_NUMBER"'" and .validUntil == "'"$SOON"'"'
request GET /api/drivers/me/onboarding "$NEW"
expect N8 "the license step is complete" 200 '.cnhStep.completed == true'
credentials "$NVAN" "$NEW"
check N9 "the approved category AD satisfies DRIVER_LICENSE" "true" "$(satisfied_of DRIVER_LICENSE)"
# A license approved before this change with category B is set directly: the API no longer takes it.
sql "update driver_cnh set category = 'B' where token = '$CNH'" >/dev/null
request GET /api/drivers/me/onboarding "$NEW"
expect N10 "a license saved before the rule with category B is read and reported as cnh_category" 200 \
  '.cnhStep.completed == false and .cnhStep.pendingItems == ["cnh_category"]'
credentials "$NVAN" "$NEW"
check N11 "category B does not satisfy DRIVER_LICENSE" "false" "$(satisfied_of DRIVER_LICENSE)"
sql "update driver_cnh set category = 'AD' where token = '$CNH'" >/dev/null

table "Documents one at a time"
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "CRLV", "documentNumber": "RENAVAM-QA", "expiresAt": "'"$IN_300_DAYS"'"}'
NCRLV=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$NCRLV/file" "$NEW"
expect E1 "today the driver only sends the CRLV" 201
request GET /api/drivers/me/onboarding "$NEW"
expect E2 "the documents step is incomplete, lists what is left and keeps the CRLV" 200 \
  '.documentsStep.completed == false and .documentsStep.missingDriverTypes == ["SCHOOL_DRIVER_COURSE", "CRIMINAL_RECORD"]
   and (.documentsStep.vehicles[0].missingTypes == ["VEHICLE_INSPECTION", "SCHOOL_VEHICLE_AUTHORIZATION"])
   and ([.documentsStep.vehicles[0].uploadedDocuments[].documentType] == ["CRLV"])'
request POST /api/drivers/me/submit-onboarding "$NEW"
expect E3 "submitting now is refused, naming the types and the plate" 422 \
  '.detail | contains("SCHOOL_DRIVER_COURSE, CRIMINAL_RECORD") and contains("'"$NEW_PLATE"': VEHICLE_INSPECTION, SCHOOL_VEHICLE_AUTHORIZATION")'
check E4 "the refused submission changed nothing" "PENDING 1" \
  "$(sql "select d.approval_status || ' ' || (select count(*) from vehicle_document vd join vehicle v on v.id = vd.vehicle_id where v.token = '$NVAN') from driver d where d.token = '$NEW_DRIVER'")"
for doc in 'SCHOOL_DRIVER_COURSE CURSO-QA' 'CRIMINAL_RECORD CERT-QA'; do
  set -- $doc
  send_doc /api/driver-documents "$NEW" "{\"documentType\": \"$1\", \"documentNumber\": \"$2\"}"
  token=$(jq -r .token <<<"$RESP_BODY")
  upload_pdf "/api/driver-documents/$token/file" "$NEW"
done
expect E5 "a week later the driver sends the course and the criminal record" 201
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_150_DAYS"'"}'
NINSP=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$NINSP/file" "$NEW"
request POST /api/drivers/me/submit-onboarding "$NEW"
expect E6 "still missing the school authorization of any van" 422 '.detail | contains("'"$NEW_PLATE"': SCHOOL_VEHICLE_AUTHORIZATION")'
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION", "documentNumber": "AUT-QA-77", "expiresAt": "'"$IN_90_DAYS"'"}'
NAUT=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$NAUT/file" "$NEW"
request POST /api/vehicles/me "$NEW" "{\"plate\": \"$SECOND_PLATE\", \"brand\": \"Renault\", \"model\": \"Master\", \"manufactureYear\": 2021, \"color\": \"Prata\", \"capacity\": 16}"
expect E7 "the driver adds a second van with no documents" 201
NVAN2=$(jq -r .token <<<"$RESP_BODY")
request GET /api/drivers/me/onboarding "$NEW"
expect E8 "one complete van is enough; the second shows its three types missing" 200 \
  '.documentsStep.completed == true and .canSubmit == true
   and ([.documentsStep.vehicles[] | select(.vehicleToken == "'"$NVAN2"'") | .missingTypes] == [["CRLV", "VEHICLE_INSPECTION", "SCHOOL_VEHICLE_AUTHORIZATION"]])'
request POST /api/drivers/me/submit-onboarding "$NEW"
expect E9 "the driver submits" 200 '.approvalStatus == "UNDER_REVIEW"'
send_doc /api/driver-documents "$NEW" '{"documentType": "MUNICIPAL_AUTHORIZATION", "documentNumber": "AUT-MUN-QA"}'
expect E10 "a driver under review can still add a document" 201
EXTRA=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/driver-documents/$EXTRA" "$NEW"

table "Approving the driver"
request POST "/api/drivers/$NEW_DRIVER/approve" "$ADMIN"
expect A1x "approving with the documents still pending: the blockers name the driver's and the closest van's" 422 \
  '.code == "driver.onboarding.documents_not_approved"
   and .detail == "O motorista só pode ser aprovado quando pelo menos uma van ativa tiver todos os documentos de transporte escolar aprovados e válidos."
   and ([.blockers[] | "\(.kind):\(.requirement):\(.state):\(.plate)"] == [
     "DRIVER_REQUIREMENT:SCHOOL_DRIVER_COURSE:PENDING:null", "DRIVER_REQUIREMENT:CRIMINAL_RECORD:PENDING:null",
     "VEHICLE_REQUIREMENT:VEHICLE_REGISTRATION:PENDING:'"$NEW_PLATE"'", "VEHICLE_REQUIREMENT:VEHICLE_INSPECTION:PENDING:'"$NEW_PLATE"'",
     "VEHICLE_REQUIREMENT:SCHOOL_VEHICLE_AUTHORIZATION:PENDING:'"$NEW_PLATE"'"])'
check A2x "the driver is still under review" "UNDER_REVIEW" "$(sql "select approval_status from driver where token = '$NEW_DRIVER'")"
request POST "/api/drivers/$NEW_DRIVER/reject" "$ADMIN" '{"reasonCode": "OTHER", "reason": "Vistoria ilegível"}'
expect A3x "the admin rejects the driver: the inspection is unreadable" 200 '.approvalStatus == "REJECTED"'
upload_pdf "/api/vehicle-documents/$NINSP/file" "$NEW"
request POST /api/drivers/me/submit-onboarding "$NEW"
expect A4x "the driver uploads a new file and submits again" 200 '.approvalStatus == "UNDER_REVIEW"'
for token in $(sql "select dd.token from driver_document dd join driver d on d.id = dd.driver_id where d.token = '$NEW_DRIVER' and dd.deleted_at is null"); do
  review driver-documents "$token" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
done
for token in "$NCRLV" "$NAUT"; do
  review vehicle-documents "$token" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
done
credentials "$NVAN" "$NEW"
expect A5x "with the inspection still pending only VEHICLE_INSPECTION fails" 200 \
  '.satisfied == false and ([.requirements[] | select((.satisfied | not) and (.optional | not)) | .requirement] == ["VEHICLE_INSPECTION"])'
request POST "/api/drivers/$NEW_DRIVER/approve" "$ADMIN"
expect A6x "approving with the inspection of the only complete van pending: only that blocker" 422 \
  '[.blockers[] | "\(.kind):\(.requirement):\(.state):\(.plate)"] == ["VEHICLE_REQUIREMENT:VEHICLE_INSPECTION:PENDING:'"$NEW_PLATE"'"]'
review vehicle-documents "$NINSP" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
credentials "$NVAN" "$NEW"
expect A7x "everything approved: the van passes, with each number and expiry" 200 \
  '.satisfied == true and ([.requirements[] | select(.requirement == "SCHOOL_VEHICLE_AUTHORIZATION")][0] | .number == "AUT-QA-77" and .validUntil == "'"$IN_90_DAYS"'")
   and ([.requirements[] | select(.requirement == "DRIVER_LICENSE")][0].number == "'"$CNH_NUMBER"'")'
credentials "$NVAN2" "$NEW"
expect A8x "the second van shows the three vehicle requirements missing" 200 \
  '[.requirements[] | select((.satisfied | not) and (.optional | not)) | .requirement] == ["VEHICLE_REGISTRATION", "VEHICLE_INSPECTION", "SCHOOL_VEHICLE_AUTHORIZATION"]'
request POST "/api/drivers/$NEW_DRIVER/approve" "$ADMIN"
expect A9x "the admin approves the driver" 200 '.approvalStatus == "APPROVED"'

table "Credentials over time and access"
# The API counts days in America/Sao_Paulo; current_date is the day of the database (UTC), which
# is already tomorrow after 21:00 in Sao Paulo.
SERVICE_TODAY="(now() at time zone 'America/Sao_Paulo')::date"
sql "update vehicle_document set expires_at = $SERVICE_TODAY - 1 where token = '$NINSP'" >/dev/null
credentials "$NVAN" "$NEW"
check T1 "an inspection expired yesterday makes VEHICLE_INSPECTION fail" "false" "$(satisfied_of VEHICLE_INSPECTION)"
sql "update vehicle_document set expires_at = $SERVICE_TODAY where token = '$NINSP'" >/dev/null
credentials "$NVAN" "$NEW"
check T2 "an inspection valid until today passes" "true" "$(satisfied_of VEHICLE_INSPECTION)"
credentials "$VAN" "$DRIVER"
expect T3 "the seed driver's van passes" 200 '.satisfied == true'
credentials "$VAN" "$ADMIN"
expect T4 "the admin reads it with show_vehicle" 200
credentials "$VAN" "$NEW"
expect T5 "another driver is refused" 403
credentials "$VAN" "$CLIENT"
expect T6 "a client is refused" 403
credentials "$VAN" ""
expect T7 "no token" 401
credentials unknown-van "$ADMIN"
expect T8 "an unknown van" 404
request DELETE "/api/vehicles/$NVAN2" "$NEW"
credentials "$NVAN2" "$NEW"
expect T9 "a removed van" 404

# ---------------------------------------------------------------------------------------------
# Part 5 — PR 5: decisions with version and reason, final submissions and file caching
# ---------------------------------------------------------------------------------------------

MEDIA_ROOT="${QA_MEDIA_ROOT:-/tmp/vanep-vd-media}"
cache_control() {
  curl -s -o /dev/null -D - "$BASE_URL$1" -H "Authorization: Bearer $2" |
    awk 'tolower($1) == "cache-control:" {sub(/^[^:]*: */, ""); sub(/\r$/, ""); print}'
}
file_key_of() { sql "select m.object_key from $1 s join media_file m on m.id = s.file_media_id where s.token = '$2'"; }

table "A rejection has a structured reason"
FIRST_COURSE=""
for code in ILLEGIBLE EXPIRED DATA_MISMATCH WRONG_TYPE INCOMPLETE NOT_ELIGIBLE; do
  send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
  course=$(jq -r .token <<<"$RESP_BODY")
  if [[ -z $FIRST_COURSE ]]; then
    FIRST_COURSE=$course
    upload_pdf "/api/driver-documents/$course/file" "$DRIVER"
  fi
  review driver-documents "$course" "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"$code\"}"
  expect "S-$code" "rejecting with $code needs no message" 200 \
    '.status == "REJECTED" and .rejectionReasonCode == "'"$code"'" and .rejectionReason == null'
done
check S1 "each resend superseded the previous rejection; one rejection stays live" "1" \
  "$(sql "select count(*) from driver_document dd join driver d on d.id = dd.driver_id join users u on u.id = d.user_id
          where u.email = '$SEED_DRIVER_EMAIL' and dd.document_type = 'SCHOOL_DRIVER_COURSE' and dd.status = 'REJECTED'
            and dd.deleted_at is null and dd.superseded_at is null")"
check S2 "the superseded rejection keeps its decision and loses its file" "REJECTED:ILLEGIBLE:true:" \
  "$(sql "select status || ':' || rejection_reason_code || ':' || (reviewed_by is not null) || ':' || coalesce(file_media_id::text, '')
          from driver_document where token = '$FIRST_COURSE' and superseded_at is not null")"
request GET "/api/driver-documents?documentType=SCHOOL_DRIVER_COURSE&status=REJECTED&size=100" "$DRIVER"
expect S3 "the superseded rejections are not listed" 200 '.totalElements == 1'
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
OTHER_COURSE=$(jq -r .token <<<"$RESP_BODY")
review driver-documents "$OTHER_COURSE" '{"status": "REJECTED", "rejectionReasonCode": "OTHER"}'
expect S4 "OTHER without a message" 400 '.code == "document.review.reason_required"'
review driver-documents "$OTHER_COURSE" '{"status": "REJECTED", "rejectionReasonCode": "OTHER", "rejectionReason": "Certificado em nome de outra pessoa"}'
expect S5 "OTHER with a message" 200 '.rejectionReasonCode == "OTHER" and .rejectionReason == "Certificado em nome de outra pessoa"'
review driver-documents "$OTHER_COURSE" '{"status": "REJECTED", "rejectionReasonCode": "BLURRY"}'
expect S6 "an unknown reason code" 400

table "The decision names the version it saw"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
COURSE=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$COURSE/file" "$DRIVER"
request GET "/api/driver-documents/$COURSE" "$ADMIN"
SEEN=$(jq -r .version <<<"$RESP_BODY")
decide "/api/driver-documents/$COURSE/status" "$ADMIN" "{\"status\": \"APPROVED\", \"version\": $SEEN}"
expect V1 "the first admin approves with the version they saw" 200 '.status == "APPROVED" and .version > '"$SEEN"
decide "/api/driver-documents/$COURSE/status" "$ADMIN" "{\"status\": \"REJECTED\", \"rejectionReasonCode\": \"EXPIRED\", \"version\": $SEEN}"
expect V2 "the second admin, with the same version, is stale" 409 \
  '.code == "document.review.stale" and .detail == "Este envio mudou desde que você abriu."'
check V3 "the first decision stands" "APPROVED" "$(sql "select status from driver_document where token = '$COURSE'")"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
SWAPPED=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$SWAPPED/file" "$DRIVER"
request GET "/api/driver-documents/$SWAPPED" "$ADMIN"
OPENED=$(jq -r .version <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$SWAPPED/file" "$DRIVER"
decide "/api/driver-documents/$SWAPPED/status" "$ADMIN" "{\"status\": \"APPROVED\", \"version\": $OPENED}"
expect V4 "the owner swapped the file while the admin looked: approving what they saw is stale" 409 '.code == "document.review.stale"'
check V5 "the document is still pending" "PENDING" "$(sql "select status from driver_document where token = '$SWAPPED'")"
review driver-documents "$SWAPPED" '{"status": "REJECTED", "rejectionReasonCode": "WRONG_TYPE"}'
expect V6 "deciding it with the current version" 200
sql "update role_permissions set permissions = permissions - 'review_documents' where name = 'ADMIN'" >/dev/null
EDITOR=$(login "$ADMIN_EMAIL")
sql "update role_permissions set permissions = permissions || '[\"review_documents\"]'::jsonb where name = 'ADMIN'" >/dev/null
check V7 "a real token with update_driver_document and without review_documents" "true false" \
  "$(claims "$EDITOR" | jq -r '[(.permissions | index("update_driver_document") != null), (.permissions | index("review_documents") != null)] | join(" ")')"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
EDITED=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$EDITED/file" "$DRIVER"
review driver-documents "$EDITED" '{"status": "APPROVED"}' "$EDITOR"
expect V8 "that token cannot decide" 403
review vehicle-documents "$DOC" '{"status": "APPROVED"}' "$EDITOR"
expect V9 "nor decide a van document" 403
review driver-documents "$EDITED" '{"status": "REJECTED", "rejectionReasonCode": "DATA_MISMATCH"}'
expect V10 "the reviewer decides it" 200

table "Renewing is a new submission"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_180_DAYS"'"}'
FIRST_INSP=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$FIRST_INSP/file" "$DRIVER"
review vehicle-documents "$FIRST_INSP" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect W1 "the inspection is approved" 200
FIRST_KEY=$(file_key_of vehicle_document "$FIRST_INSP")
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_200_DAYS"'"}'
expect W2 "the owner sends the renewal before it expires" 201 '.status == "PENDING"'
RENEWED=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$RENEWED/file" "$DRIVER"
credentials "$VAN" "$DRIVER"
check W3 "the van keeps VEHICLE_INSPECTION while the renewal waits" "true" "$(satisfied_of VEHICLE_INSPECTION)"
check W4 "the approved file is on disk before the renewal is approved" "yes" "$([[ -f $MEDIA_ROOT/$FIRST_KEY ]] && echo yes || echo no)"
review vehicle-documents "$RENEWED" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect W5 "the admin approves the renewal" 200
check W6 "the previous approval is superseded, keeps its decision and has no file" "APPROVED:true:" \
  "$(sql "select status || ':' || (superseded_at is not null) || ':' || coalesce(file_media_id::text, '') from vehicle_document where token = '$FIRST_INSP'")"
check W7 "its media row is removed" "0" \
  "$(sql "select count(*) from media_file where object_key = '$FIRST_KEY' and deleted_at is null")"
check W8 "and its object is deleted from the disk after the commit" "no" "$([[ -f $MEDIA_ROOT/$FIRST_KEY ]] && echo yes || echo no)"
credentials "$VAN" "$DRIVER"
expect W9 "the van passes with the renewal" 200 \
  '[.requirements[] | select(.requirement == "VEHICLE_INSPECTION")][0] | .satisfied and .validUntil == "'"$IN_200_DAYS"'"'
request GET "/api/vehicles/$VAN/documents?documentType=VEHICLE_INSPECTION" "$DRIVER"
expect W10 "the listing shows the renewal, not the superseded one" 200 \
  '([.content[].token] | index("'"$RENEWED"'") != null) and ([.content[].token] | index("'"$FIRST_INSP"'") == null)'
request GET "/api/vehicle-documents/$FIRST_INSP/file" "$DRIVER"
expect W11 "the superseded one has nothing to download" 404

table "A reviewed submission is final"
request PUT "/api/vehicle-documents/$RENEWED" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_150_DAYS"'"}'
expect Z1 "editing the approved inspection: there is no PUT of a document" 405
upload_pdf "/api/vehicle-documents/$RENEWED/file" "$DRIVER"
expect Z2 "uploading a file to it" 409 '.code == "document.submission.reviewed"'
request DELETE "/api/vehicle-documents/$RENEWED" "$DRIVER"
expect Z3 "the owner removing it" 409 '.code == "document.submission.reviewed"'
review vehicle-documents "$RENEWED" '{"status": "REJECTED", "rejectionReasonCode": "EXPIRED"}'
expect Z4 "deciding it again" 409 '.code == "document.submission.reviewed"'
check Z5 "nothing changed" "APPROVED:$IN_200_DAYS" \
  "$(sql "select status || ':' || expires_at from vehicle_document where token = '$RENEWED' and deleted_at is null")"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
DOOMED=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$DOOMED/file" "$DRIVER"
review driver-documents "$DOOMED" '{"status": "APPROVED"}'
request DELETE "/api/driver-documents/$DOOMED" "$DRIVER"
expect Z6 "the owner cannot remove an approved driver document" 409 '.code == "document.submission.reviewed"'
request DELETE "/api/driver-documents/$DOOMED" "$ADMIN"
expect Z7 "the admin with delete_driver_document removes it" 204
# Z6 superseded the seed's approved course and Z7 removed its replacement: the seed driver gets an
# approved course back, so the later parts still see a complete driver.
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE", "documentNumber": "CURSO-ESC-QA"}'
COURSE_BACK=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$COURSE_BACK/file" "$DRIVER"
review driver-documents "$COURSE_BACK" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'

table "One pending submission per owner and type"
send_in_parallel() {
  local token=$1 type=$2 dir file path
  shift 2
  dir="$(mktemp -d)"
  file="$(mktemp).pdf"
  pdf >"$file"
  for path in "$@"; do
    curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL$path" -H "Authorization: Bearer $token" \
      -F "file=@$file;type=application/pdf" -F "documentType=$type" >"$(mktemp "$dir/status.XXXXXX")" &
  done
  wait
  cat "$dir"/* | sort | paste -sd ' ' -
  rm -rf "$dir" "$file"
}
statuses=$(send_in_parallel "$DRIVER" SCHOOL_DRIVER_COURSE /api/driver-documents /api/driver-documents)
check Y1 "two simultaneous submissions of the same type: one is created, the other refused" "201 409" "$statuses"
check Y2 "a single pending one was stored" "1" \
  "$(sql "select count(*) from driver_document dd join driver d on d.id = dd.driver_id join users u on u.id = d.user_id
          where u.email = '$SEED_DRIVER_EMAIL' and dd.document_type = 'SCHOOL_DRIVER_COURSE' and dd.status = 'PENDING'
            and dd.deleted_at is null and dd.superseded_at is null")"
LAST_COURSE=$(sql "select dd.token from driver_document dd join driver d on d.id = dd.driver_id join users u on u.id = d.user_id
                  where u.email = '$SEED_DRIVER_EMAIL' and dd.document_type = 'SCHOOL_DRIVER_COURSE' and dd.status = 'PENDING'
                    and dd.deleted_at is null and dd.superseded_at is null")
request DELETE "/api/driver-documents/$LAST_COURSE" "$DRIVER"
expect Y3 "the owner removes their pending submission, so the run can be repeated" 204

table "Dates within the type's limits"
NEXT_YEAR_END="$(($(TZ=America/Sao_Paulo date +%Y) + 1))-12-31"
AFTER_NEXT_YEAR="$(($(TZ=America/Sao_Paulo date +%Y) + 2))-01-01"
# A van of its own, so approving these documents supersedes nothing the later parts read.
request POST /api/vehicles/me "$DRIVER" "{\"plate\": \"$(random_plate K)\", \"brand\": \"Fiat\", \"model\": \"Ducato\", \"manufactureYear\": 2022, \"color\": \"Branca\", \"capacity\": 15}"
KVAN=$(jq -r .token <<<"$RESP_BODY")
send_doc "/api/vehicles/$KVAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION"}'
KINSP=$(jq -r .token <<<"$RESP_BODY")
review vehicle-documents "$KINSP" '{"status": "APPROVED", "expiresAt": "'"$YESTERDAY"'"}'
expect K1 "approving an inspection that expired yesterday" 400 '.code == "document.expires_at.past" and .field == "expiresAt"'
send_doc "/api/vehicles/$KVAN/documents" "$DRIVER" '{"documentType": "CRLV"}'
KCRLV=$(jq -r .token <<<"$RESP_BODY")
review vehicle-documents "$KCRLV" '{"status": "APPROVED", "expiresAt": "'"$AFTER_NEXT_YEAR"'"}'
expect K2 "approving a CRLV after December 31 of next year" 400 '.code == "document.expires_at.too_far" and .field == "expiresAt"'
check K3 "both are still pending, without data" "PENDING: PENDING:" \
  "$(sql "select string_agg(status || ':' || coalesce(expires_at::text, ''), ' ' order by document_type) from vehicle_document where token in ('$KINSP', '$KCRLV')")"
review vehicle-documents "$KCRLV" '{"status": "APPROVED", "expiresAt": "'"$NEXT_YEAR_END"'"}'
expect K4 "approving a CRLV on December 31 of next year, the last day" 200 '.expiresAt == "'"$NEXT_YEAR_END"'"'
request PUT "/api/vehicle-documents/$KCRLV/data" "$ADMIN" "{\"expiresAt\": \"$AFTER_NEXT_YEAR\", \"version\": $(jq -r .version <<<"$RESP_BODY")}"
expect K5 "correcting it past the limit" 400 '.code == "document.expires_at.too_far"'
check K6 "the CRLV kept its date" "$NEXT_YEAR_END" "$(sql "select expires_at from vehicle_document where token = '$KCRLV'")"
request DELETE "/api/vehicles/$KVAN" "$DRIVER"
expect K7 "the owner removes the van of these checks, so the run can be repeated" 204
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
KCOURSE=$(jq -r .token <<<"$RESP_BODY")
review driver-documents "$KCOURSE" '{"status": "APPROVED", "expiresAt": "'"$YESTERDAY"'"}'
expect K8 "approving a driver course that expired yesterday" 400 '.code == "document.expires_at.past" and .field == "expiresAt"'
request DELETE "/api/driver-documents/$KCOURSE" "$DRIVER"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "CRIMINAL_RECORD"}'
review driver-documents "$(jq -r .token <<<"$RESP_BODY")" '{"status": "APPROVED", "expiresAt": "'"$(in_days +3650)"'"}'
expect K9 "approving a criminal record ten years ahead, which has no upper limit" 200 '.status == "APPROVED"'

table "Files are cached while the URL does not change"
request GET "/api/vehicle-documents/$RENEWED" "$DRIVER"
FILE_URL=$(jq -r .fileUrl <<<"$RESP_BODY")
check H1 "a document file with the v of its URL" "max-age=86400, private, immutable" "$(cache_control "$FILE_URL" "$DRIVER")"
check H2 "a document file without v" "no-cache, private" "$(cache_control "/api/vehicle-documents/$RENEWED/file" "$DRIVER")"
check H3 "a document file with an old v" "no-cache, private" "$(cache_control "/api/vehicle-documents/$RENEWED/file?v=old" "$DRIVER")"
request GET "/api/vehicles/$VAN" "$DRIVER"
PHOTO_URL=$(jq -r .photoSideUrl <<<"$RESP_BODY")
check H4 "a van photo with the v of its URL" "max-age=86400, private, immutable" "$(cache_control "$PHOTO_URL" "$DRIVER")"
check H5 "a van photo without v" "no-cache, private" "$(cache_control "${PHOTO_URL%%\?*}" "$DRIVER")"

# ---------------------------------------------------------------------------------------------
# Part 6 — PR 6: the CNH is reviewed like the documents
# ---------------------------------------------------------------------------------------------

cnh_body() { printf '{"registrationNumber": "%s", "category": "D", "issueDate": "%s", "validUntil": "%s"}' "$1" "$2" "$3"; }
LATER=$(date -v+6y +%F 2>/dev/null || date -d '+6 years' +%F)

table "The CNH is reviewed by an admin"
review driver-cnhs "$CNH" '{"status": "REJECTED", "rejectionReasonCode": "ILLEGIBLE"}' "$NEW"
expect C1 "the owner cannot decide their own CNH" 403
request PUT "/api/driver-cnhs/$CNH" "$NEW" "$(cnh_body "$CNH_NUMBER" "$CNH_ISSUED" "$LATER")"
expect C2 "the owner cannot edit the approved CNH: there is no PUT of a CNH" 405
request DELETE "/api/driver-cnhs/$CNH" "$NEW"
expect C3 "nor remove it" 409 '.code == "document.submission.reviewed"'

table "Renewing the CNH keeps the current one"
send_doc /api/driver-cnhs "$NEW" "$(cnh_body "$CNH_NUMBER" "$CNH_ISSUED" "$LATER")"
expect C4 "a renewal is sent with its file and without data" 201 '.status == "PENDING" and .registrationNumber == null and .file != null'
RENEWED_CNH=$(jq -r .token <<<"$RESP_BODY")
credentials "$NVAN" "$NEW"
check C5 "the approved CNH still satisfies DRIVER_LICENSE" "true" "$(satisfied_of DRIVER_LICENSE)"
send_doc /api/driver-cnhs "$NEW" "$(cnh_body "$CNH_NUMBER" "$CNH_REISSUED" "$LATER")"
expect C6 "a second pending CNH" 409 '.code == "document.submission.pending_exists"'
request GET "/api/driver-cnhs/$RENEWED_CNH" "$ADMIN"
request PUT "/api/driver-cnhs/$RENEWED_CNH/status" "$ADMIN" "{\"status\": \"APPROVED\", \"version\": $(jq -r .version <<<"$RESP_BODY")}"
expect C7 "approving the renewal without its data" 400 '.code == "document.review.field_required" and .field == "registrationNumber"'
request GET "/api/driver-cnhs/$RENEWED_CNH" "$ADMIN"
OPENED=$(jq -r .version <<<"$RESP_BODY")
upload_pdf "/api/driver-cnhs/$RENEWED_CNH/file" "$NEW"
expect C7b "the renewal's file is replaced by the digital CNH, a PDF" 201
download "/api/driver-cnhs/$RENEWED_CNH/file" "$ADMIN"
check C7c "the admin reads the CNH file back as a PDF" "200 application/pdf" "$RESP_STATUS $RESP_TYPE"
download "/api/driver-cnhs/$RENEWED_CNH/photo" "$ADMIN"
expect C7d "the old photo route of the CNH is gone" 404
check C7e "the CNH column is file_media_id, not photo_media_id" "file_media_id" \
  "$(sql "select string_agg(column_name, ',') from information_schema.columns
              where table_name = 'driver_cnh' and column_name in ('file_media_id', 'photo_media_id')")"
decide "/api/driver-cnhs/$RENEWED_CNH/status" "$ADMIN" "{\"status\": \"APPROVED\", \"version\": $OPENED}"
expect C8 "approving the version from before the file" 409 '.code == "document.review.stale"'
OLD_FILE=$(sql "select m.object_key from driver_cnh c join media_file m on m.id = c.file_media_id where c.token = '$CNH'")
review driver-cnhs "$RENEWED_CNH" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect C9 "the admin approves the renewal with the same number" 200 '.status == "APPROVED" and .registrationNumber == "'"$CNH_NUMBER"'"'
check C10 "the previous CNH is superseded, keeps its decision and has no file" "APPROVED:true:" \
  "$(sql "select status || ':' || (superseded_at is not null) || ':' || coalesce(file_media_id::text, '') from driver_cnh where token = '$CNH'")"
check C11 "its file is deleted from the disk" "no" "$([[ -f $MEDIA_ROOT/$OLD_FILE ]] && echo yes || echo no)"
credentials "$NVAN" "$NEW"
expect C12 "DRIVER_LICENSE comes from the renewal" 200 \
  '[.requirements[] | select(.requirement == "DRIVER_LICENSE")][0] | .satisfied and .validUntil == "'"$LATER"'"'
request GET /api/driver-cnhs "$NEW"
expect C13 "the driver lists only the renewal" 200 '[.[].token] == ["'"$RENEWED_CNH"'"]'

table "The CNH dates stay within limits"
years_after() { date -j -v+"$2"y -f %F "$1" +%F 2>/dev/null || date -d "$1 +$2 years" +%F; }
day_after() { date -j -v+1d -f %F "$1" +%F 2>/dev/null || date -d "$1 +1 day" +%F; }
cnhs_of_new() { sql "select count(*) from driver_cnh c join driver d on d.id = c.driver_id join users u on u.id = d.user_id where u.email = '$NEW_EMAIL'"; }
send_doc /api/driver-cnhs "$NEW" '{}'
expect J0 "the driver sends another CNH to check the dates on" 201
DATED_CNH=$(jq -r .token <<<"$RESP_BODY")
review driver-cnhs "$DATED_CNH" "$(cnh_body "$CNH_NUMBER" "$(in_days +1)" "$LATER" | jq -c '. + {status: "APPROVED"}')"
expect J1 "approving a CNH issued tomorrow" 400 '.code == "driver_cnh.issue_date.future" and .field == "issueDate"'
review driver-cnhs "$DATED_CNH" "$(cnh_body "$CNH_NUMBER" "$CNH_ISSUED" "$YESTERDAY" | jq -c '. + {status: "APPROVED"}')"
expect J2 "approving a CNH that expired yesterday" 400 '.code == "document.expires_at.past" and .field == "validUntil"'
review driver-cnhs "$DATED_CNH" "$(cnh_body "$CNH_NUMBER" "$CNH_ISSUED" "$(day_after "$(years_after "$CNH_ISSUED" 10)")" | jq -c '. + {status: "APPROVED"}')"
expect J3 "approving a CNH valid for ten years and a day" 400 '.code == "document.expires_at.too_far" and .field == "validUntil"'
check J4 "the CNH is still pending, without data" "PENDING:" \
  "$(sql "select status || ':' || coalesce(valid_until::text, '') from driver_cnh where token = '$DATED_CNH'")"
request DELETE "/api/driver-cnhs/$DATED_CNH" "$NEW"
expect J4b "the owner removes the pending CNH, so the run can be repeated" 204
request GET "/api/driver-cnhs/$RENEWED_CNH" "$NEW"
expect J5 "the CNH describes its PDF" 200 \
  '.file.mimeType == "application/pdf" and .file.sizeBytes == '"$(pdf | wc -c | tr -d ' ')"' and (.file.originalName | endswith(".pdf"))'

table "A registration number belongs to one driver"
new_driver() {
  local email=$1
  request POST /api/auth/signup/driver "" "{\"name\": \"Motorista QA\", \"email\": \"$email\", \"password\": \"$NEW_PASSWORD\", \"document\": \"$(valid_cpf)\", \"acceptTerms\": true, \"basePrice\": 150.00}"
  sql "update users set verified = true where email = '$email'" >/dev/null
  SEED_PASSWORD=$NEW_PASSWORD login "$email"
}
RACE_A=$(new_driver "qa.corrida.a.$RUN@vanep.test")
RACE_B=$(new_driver "qa.corrida.b.$RUN@vanep.test")
send_doc /api/driver-cnhs "$RACE_A" '{}'
DUP_CNH=$(jq -r .token <<<"$RESP_BODY")
review driver-cnhs "$DUP_CNH" "$(cnh_body "$CNH_NUMBER" "$CNH_ISSUED" "$LATER" | jq -c '. + {status: "APPROVED"}')"
expect C14 "approving another driver's CNH with the same number" 409 \
  '.code == "driver_cnh.registration.duplicate" and .detail == "Já existe uma CNH cadastrada com este número de registro."'
check C14b "that CNH is still pending" "PENDING" "$(sql "select status from driver_cnh where token = '$DUP_CNH'")"
request DELETE "/api/driver-cnhs/$DUP_CNH" "$RACE_A"
race_outcomes=""
race_owners=""
for round in 1 2 3 4 5 6 7 8; do
  RACE_NUMBER="8$(printf '%010d' $(( (RUN + 7 + round) % 10000000000 )))"
  race_dir=$(mktemp -d)
  bodies=()
  for who in "$RACE_A" "$RACE_B"; do
    send_doc /api/driver-cnhs "$who" '{}'
    token=$(jq -r .token <<<"$RESP_BODY")
    request GET "/api/driver-cnhs/$token" "$ADMIN"
    bodies+=("$token $(cnh_body "$RACE_NUMBER" "$CNH_ISSUED" "$LATER" | jq -c --argjson v "$(jq .version <<<"$RESP_BODY")" '. + {status: "APPROVED", version: $v}')")
  done
  for entry in "${bodies[@]}"; do
    curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$BASE_URL/api/driver-cnhs/${entry%% *}/status" -H "Authorization: Bearer $ADMIN" \
      -H "Content-Type: application/json" --data-binary "${entry#* }" >"$(mktemp "$race_dir/status.XXXXXX")" &
  done
  wait
  race_outcomes="$race_outcomes$(cat "$race_dir"/* | sort | paste -sd ' ' -);"
  rm -rf "$race_dir"
  race_owners="$race_owners$(sql "select count(distinct driver_id) from driver_cnh where registration_number = '$RACE_NUMBER' and deleted_at is null") "
  for entry in "${bodies[@]}"; do
    request DELETE "/api/driver-cnhs/${entry%% *}" "$ADMIN"
  done
done
check C15 "two admins approve CNHs of two drivers with the same new number at once, 8 times: one is approved, the other refused, every time" \
  "200 409;200 409;200 409;200 409;200 409;200 409;200 409;200 409;" "$race_outcomes"
check C16 "each number belongs to a single driver" "1 1 1 1 1 1 1 1 " "$race_owners"

table "Correcting the data of the approved CNH"
request GET "/api/driver-cnhs/$RENEWED_CNH" "$ADMIN"
SEEN=$(jq -r .version <<<"$RESP_BODY")
request PUT "/api/driver-cnhs/$RENEWED_CNH/data" "$ADMIN" "$(cnh_body "$CNH_NUMBER" "$CNH_REISSUED" "$LATER" | jq -c --argjson v "$SEEN" '. + {category: "E", issuingState: "SP", version: $v}')"
expect CC1 "the reviewer corrects the category and adds the issuing state" 200 \
  '.status == "APPROVED" and .category == "E" and .issuingState == "SP" and .dataCorrectedAt != null'
request PUT "/api/driver-cnhs/$RENEWED_CNH/data" "$ADMIN" "$(cnh_body "$CNH_NUMBER" "$CNH_REISSUED" "$LATER" | jq -c --argjson v "$SEEN" '. + {category: "B", version: ($v + 1)}')"
expect CC2 "a correction to category B is refused" 400 '.field == "category"'
request PUT "/api/driver-cnhs/$RENEWED_CNH/data" "$NEW" "$(cnh_body "$CNH_NUMBER" "$CNH_REISSUED" "$LATER" | jq -c --argjson v "$SEEN" '. + {version: ($v + 1)}')"
expect CC3 "the owner cannot correct" 403
request PUT "/api/driver-cnhs/$RENEWED_CNH/data" "$ADMIN" "$(cnh_body "$CNH_NUMBER" "$CNH_REISSUED" "$LATER" | jq -c --argjson v "$SEEN" '. + {version: ($v + 1)}')"
expect CC4 "the reviewer puts category D back" 200 '.category == "D" and .issuingState == null'

table "The onboarding reads the CNH review"
REJECTED_EMAIL="qa.recusado.$RUN@vanep.test"
request POST /api/auth/signup/driver "" "{\"name\": \"Motorista Recusado\", \"email\": \"$REJECTED_EMAIL\", \"password\": \"$NEW_PASSWORD\", \"document\": \"$(valid_cpf)\", \"acceptTerms\": true, \"basePrice\": 150.00}"
sql "update users set verified = true where email = '$REJECTED_EMAIL'" >/dev/null
REJECTED_DRIVER=$(SEED_PASSWORD=$NEW_PASSWORD login "$REJECTED_EMAIL")
send_doc /api/driver-cnhs "$REJECTED_DRIVER" "$(cnh_body "7$(printf '%010d' $(( (RUN + 3) % 10000000000 )))" "$CNH_ISSUED" "$LATER")"
REJECTED_CNH=$(jq -r .token <<<"$RESP_BODY")
upload "/api/driver-cnhs/$REJECTED_CNH/file" "$REJECTED_DRIVER"
request GET /api/drivers/me/onboarding "$REJECTED_DRIVER"
expect C17 "a pending CNH counts as sent" 200 '.cnhStep.completed == true'
review driver-cnhs "$REJECTED_CNH" '{"status": "REJECTED", "rejectionReasonCode": "ILLEGIBLE"}'
expect C18 "the admin rejects the CNH" 200 '.rejectionReasonCode == "ILLEGIBLE"'
request GET /api/drivers/me/onboarding "$REJECTED_DRIVER"
expect C19 "the onboarding reports cnh_rejected" 200 '.cnhStep.completed == false and .cnhStep.pendingItems == ["cnh_rejected"]'
send_doc /api/driver-cnhs "$REJECTED_DRIVER" "$(cnh_body "7$(printf '%010d' $(( (RUN + 3) % 10000000000 )))" "$CNH_ISSUED" "$LATER")"
expect C20 "a new CNH supersedes the rejected one" 201 '.status == "PENDING"'
upload "/api/driver-cnhs/$(jq -r .token <<<"$RESP_BODY")/file" "$REJECTED_DRIVER"
request GET /api/drivers/me/onboarding "$REJECTED_DRIVER"
expect C21 "and the step counts it as sent again" 200 '.cnhStep.completed == true'

table "The seed passes the credentials"
check C22 "every seed CNH is approved" "0" \
  "$(sql "select count(*) from driver_cnh where registration_number in ('12345678901', '23456789012', '34567890123')
          and deleted_at is null and superseded_at is null and status <> 'APPROVED'")"
credentials "$VAN" "$DRIVER"
expect C23 "the seed driver's van passes" 200 '.satisfied == true'

# ---------------------------------------------------------------------------------------------
# Part 7 — PR 7: one evaluation for the approval, with a lock on the driver
# ---------------------------------------------------------------------------------------------

table "Approving and rejecting at the same time"
SEED_DRIVER_TOKEN=$(sql "select d.token from driver d join users u on u.id = d.user_id where u.email = '$SEED_DRIVER_EMAIL'")
sql "update driver set approval_status = 'UNDER_REVIEW' where token = '$SEED_DRIVER_TOKEN'" >/dev/null
race_dir=$(mktemp -d)
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/drivers/$SEED_DRIVER_TOKEN/approve" \
  -H "Authorization: Bearer $ADMIN" >"$(mktemp "$race_dir/status.XXXXXX")" &
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/drivers/$SEED_DRIVER_TOKEN/reject" \
  -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
  --data-binary '{"reasonCode": "OTHER", "reason": "Corrida do QA"}' >"$(mktemp "$race_dir/status.XXXXXX")" &
wait
check K1x "an approval and a rejection of the same driver at once: one decision and one 400" "200 400" \
  "$(cat "$race_dir"/* | sort | paste -sd ' ' -)"
rm -rf "$race_dir"
check K2x "the driver holds one of the two decisions" "1" \
  "$(sql "select count(*) from driver where token = '$SEED_DRIVER_TOKEN' and approval_status in ('APPROVED', 'REJECTED')")"
sql "update driver set approval_status = 'APPROVED', rejection_reason = null where token = '$SEED_DRIVER_TOKEN'" >/dev/null
request POST "/api/drivers/$SEED_DRIVER_TOKEN/approve" "$ADMIN"
expect K3x "approving a driver that is no longer under review" 400 '.code == "driver.onboarding.not_under_review"'

table "The service date is São Paulo's"
JVM_TODAY=$(sql "select (now() at time zone '${QA_JVM_ZONE:-America/Sao_Paulo}')::date")
SP_TODAY=$(sql "select (now() at time zone 'America/Sao_Paulo')::date")
check Z1x "this run's JVM date differs from São Paulo's (QA_JVM_ZONE=${QA_JVM_ZONE:-unset})" "differ" \
  "$([[ $JVM_TODAY != "$SP_TODAY" ]] && echo differ || echo same)"
sql "update vehicle_document set expires_at = date '$SP_TODAY' - 1 where token = '$NINSP'" >/dev/null
credentials "$NVAN" "$NEW"
check Z2x "valid until yesterday in São Paulo fails, though it is still that day for the JVM" "false" "$(satisfied_of VEHICLE_INSPECTION)"
sql "update vehicle_document set expires_at = date '$SP_TODAY' where token = '$NINSP'" >/dev/null
credentials "$NVAN" "$NEW"
check Z3x "valid until today in São Paulo passes" "true" "$(satisfied_of VEHICLE_INSPECTION)"

# ---------------------------------------------------------------------------------------------
# Part 8 — PR 8: e-mails after the commit and a code on the onboarding rejection
# ---------------------------------------------------------------------------------------------

MAILPIT="${QA_MAILPIT_URL:-http://localhost:8026}"
# The text of the newest e-mail to $1 whose subject contains $2, once it arrives.
mail_text() {
  local id="" attempt
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    id=$(curl -s "$MAILPIT/api/v1/search" --get --data-urlencode "query=to:\"$1\"" |
      jq -r --arg s "$2" '[.messages[] | select(.Subject | contains($s))][0].ID // empty')
    [[ -n $id ]] && break
    sleep 0.5
  done
  [[ -n $id ]] && curl -s "$MAILPIT/api/v1/message/$id" | jq -r '(.Text // "") + (.HTML // "")'
}
mail_count() {
  curl -s "$MAILPIT/api/v1/search" --get --data-urlencode "query=to:\"$1\"" |
    jq --arg s "$2" '[.messages[] | select(.Subject | contains($s))] | length'
}
contains_all() {
  local text=$1 part
  shift
  for part in "$@"; do [[ $text == *"$part"* ]] || { echo "missing: $part"; return; }; done
  echo "yes"
}

table "The driver hears about a rejected submission"
curl -s -X DELETE "$MAILPIT/api/v1/messages" >/dev/null
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "'"$IN_180_DAYS"'"}'
MAIL_INSP=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$MAIL_INSP/file" "$NEW"
review vehicle-documents "$MAIL_INSP" '{"status": "REJECTED", "rejectionReasonCode": "ILLEGIBLE", "rejectionReason": "A foto saiu escura."}'
expect M1 "the admin rejects the van's inspection as ILLEGIBLE" 200
TEXT=$(mail_text "$NEW_EMAIL" "Documento recusado")
check M2 "the e-mail names the inspection, the plate, the reason and the message, in pt-BR" "yes" \
  "$(contains_all "$TEXT" "Vistoria da van" "$NEW_PLATE" "Não conseguimos ler o documento." "A foto saiu escura." "Abra o aplicativo Vanep")"
check M3 "the inspection records notified_at" "t" \
  "$(sql "select notified_at is not null from vehicle_document where token = '$MAIL_INSP'")"
send_doc /api/driver-documents "$NEW" '{"documentType": "MUNICIPAL_AUTHORIZATION", "documentNumber": "AUT-MUN-2019"}'
MAIL_MUNICIPAL=$(jq -r .token <<<"$RESP_BODY")
review driver-documents "$MAIL_MUNICIPAL" '{"status": "REJECTED", "rejectionReasonCode": "OTHER", "rejectionReason": "Autorização de 2019."}'
TEXT=$(mail_text "$NEW_EMAIL" "Documento recusado" | tr '\n' ' ')
check M4 "a driver document rejected for another reason: the name and the admin's message" "yes" \
  "$(contains_all "$TEXT" "Autorização municipal" "Autorização de 2019.")"
check M5 "the driver document records notified_at" "t" \
  "$(sql "select notified_at is not null from driver_document where token = '$MAIL_MUNICIPAL'")"
MAIL_CNH=$(sql "select c.token from driver_cnh c join driver d on d.id = c.driver_id join users u on u.id = d.user_id
                where u.email = '$REJECTED_EMAIL' and c.status = 'PENDING' and c.deleted_at is null and c.superseded_at is null")
upload "/api/driver-cnhs/$MAIL_CNH/file" "$REJECTED_DRIVER"
review driver-cnhs "$MAIL_CNH" '{"status": "REJECTED", "rejectionReasonCode": "EXPIRED"}'
TEXT=$(mail_text "$REJECTED_EMAIL" "Documento recusado")
check M6 "a rejected CNH is mailed with the reason of its code" "yes" "$(contains_all "$TEXT" "CNH" "O documento está vencido.")"
check M7 "the CNH records notified_at" "t" "$(sql "select notified_at is not null from driver_cnh where token = '$MAIL_CNH'")"

table "The rejection of the onboarding has a code"
sql "update driver set approval_status = 'UNDER_REVIEW' where token = '$SEED_DRIVER_TOKEN'" >/dev/null
request POST "/api/drivers/$SEED_DRIVER_TOKEN/reject" "$ADMIN" '{"reason": "Sem código"}'
expect O1 "a rejection without a code" 400 '.field == "reasonCode"'
request POST "/api/drivers/$SEED_DRIVER_TOKEN/reject" "$ADMIN" '{"reasonCode": "OTHER", "reason": "  "}'
expect O2 "OTHER without a reason" 400 '.code == "driver.rejection.reason.required"'
request POST "/api/drivers/$SEED_DRIVER_TOKEN/reject" "$ADMIN" '{"reasonCode": "BANNED"}'
expect O3 "an unknown code" 400
check O4 "the refused rejections changed nothing" "UNDER_REVIEW" "$(sql "select approval_status from driver where token = '$SEED_DRIVER_TOKEN'")"
curl -s -X DELETE "$MAILPIT/api/v1/messages" >/dev/null
request POST "/api/drivers/$SEED_DRIVER_TOKEN/reject" "$ADMIN" '{"reasonCode": "DUPLICATE_ACCOUNT"}'
expect O5 "a duplicate account needs no reason" 200 '.approvalStatus == "REJECTED"'
check O6 "the code is stored" "DUPLICATE_ACCOUNT:" \
  "$(sql "select rejection_reason_code || ':' || coalesce(rejection_reason, '') from driver where token = '$SEED_DRIVER_TOKEN'")"
TEXT=$(mail_text "$SEED_DRIVER_EMAIL" "Atualização sobre seu cadastro")
check O7 "the rejection e-mail, sent after the commit, carries the reason of the code" "yes" \
  "$(contains_all "$TEXT" "Já existe outra conta deste motorista.")"
check O8 "a single rejection e-mail" "1" "$(mail_count "$SEED_DRIVER_EMAIL" "Atualização sobre seu cadastro")"
request GET /api/drivers/me/onboarding "$DRIVER"
expect O9 "the driver's onboarding shows the code and its text, with no message written" 200 \
  '.rejectionReasonCode == "DUPLICATE_ACCOUNT" and .rejectionReason == "Já existe outra conta deste motorista."'
sql "update driver set approval_status = 'APPROVED', rejection_reason_code = null where token = '$SEED_DRIVER_TOKEN'" >/dev/null

echo
echo "TOTAL: $PASSED passed"
