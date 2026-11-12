#!/usr/bin/env bash
# Manual QA for the driver-credentials-review change (constitution rule 55). Two parts:
#  - V72 against legacy data: a separate database in the throwaway container gets V1..V71 through
#    psql, then the rows V72 must remove or keep, then V72, and each outcome is checked.
#  - the live API from the branch, on the throwaway database it migrated with Flyway and seeded.
# Never point it at a shared database: it creates and drops a database and writes rows directly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

MIGRATION_DB="${QA_MIGRATION_DB:-v72check}"
SEED_DRIVER_EMAIL="fabio.teixeira@seed.vanep.com.br"
SEED_CLIENT_EMAIL="ana.souza@seed.vanep.com.br"

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
request POST /api/driver-documents "$DRIVER" '{"documentType": "CRLV", "expiresAt": "2027-01-31"}'
expect D1 "a CRLV as a driver document is refused" 400
request POST /api/driver-documents "$DRIVER" '{"documentType": "PROFILE_PHOTO"}'
expect D2 "a profile photo as a driver document is refused" 400
before=$(count driver_document)
request POST /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
expect D3 "the specialized course is accepted" 201 '.documentType == "SCHOOL_DRIVER_COURSE" and .status == "PENDING"'
check D4 "only the accepted one was written" "$((before + 1))" "$(count driver_document)"

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

echo
echo "TOTAL: $PASSED passed"
