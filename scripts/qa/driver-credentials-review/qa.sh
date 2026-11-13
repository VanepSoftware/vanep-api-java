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
send_doc /api/driver-documents "$DRIVER" '{"documentType": "CRLV", "expiresAt": "2027-01-31"}'
expect D1 "a CRLV as a driver document is refused" 400
send_doc /api/driver-documents "$DRIVER" '{"documentType": "PROFILE_PHOTO"}'
expect D2 "a profile photo as a driver document is refused" 400
before=$(count driver_document)
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE"}'
expect D3 "the specialized course is accepted with its file and no data" 201 \
  '.documentType == "SCHOOL_DRIVER_COURSE" and .status == "PENDING" and .file != null and .documentNumber == null'
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

# ---------------------------------------------------------------------------------------------
# Part 3 — PR 2: the van's documents, the review, and the driver's documents
# ---------------------------------------------------------------------------------------------

OTHER_EMAIL=$(sql "select u.email from users u join driver d on d.user_id = u.id
                   where u.email like '%@seed.vanep.com.br' and u.email <> '$SEED_DRIVER_EMAIL'
                   and u.deleted_at is null order by u.id limit 1")
OTHER=$(login "$OTHER_EMAIL")
ADMIN_UID=$(claims "$ADMIN" | jq -r .uid)
docs() { sql "select count(*) from vehicle_document"; }

table "The life of a van document"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV", "documentNumber": " RENAVAM-00912 ", "expiresAt": "2027-03-31"}'
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
decide "/api/vehicle-documents/$DOC/status" "$DRIVER" '{"status": "APPROVED"}'
expect L4 "the owner tries to approve their own document" 403
check L5 "the status did not change" "PENDING" "$(sql "select status from vehicle_document where token = '$DOC'")"
decide "/api/vehicle-documents/$DOC/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect L6 "the admin approves it with the number and the expiry read from the file" 200 \
  '.status == "APPROVED" and .reviewMethod == "MANUAL" and .reviewedByUserToken == "'"$ADMIN_UID"'" and .reviewedAt != null and .documentNumber == "RENAVAM-00912" and .expiresAt != null'
upload_pdf "/api/vehicle-documents/$DOC/file" "$DRIVER"
expect L7 "the owner swaps the file" 201
request GET "/api/vehicle-documents/$DOC" "$DRIVER"
expect L8 "the swapped document is pending with no reviewer and without the approved data" 200 \
  '.status == "PENDING" and .reviewedByUserToken == null and .reviewedAt == null and .reviewMethod == null and .documentNumber == null and .expiresAt == null'
decide "/api/vehicle-documents/$DOC/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect L9 "the admin approves it again" 200 '.status == "APPROVED"'
request PUT "/api/vehicle-documents/$DOC" "$DRIVER" '{"documentType": "CRLV", "expiresAt": "2028-03-31"}'
expect L10 "the owner cannot edit the data: there is no PUT of a document" 405
decide "/api/vehicle-documents/$DOC/status" "$ADMIN" '{"status": "REJECTED", "rejectionReason": "  Ilegível  "}'
expect L11 "the admin rejects with a reason, stored trimmed" 200 '.status == "REJECTED" and .rejectionReason == "Ilegível"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "2026-12-31"}'
OLD_INSPECTION=$(jq -r .token <<<"$RESP_BODY")
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "2027-06-30"}'
NEW_INSPECTION=$(jq -r .token <<<"$RESP_BODY")
request GET "/api/vehicles/$VAN/documents?documentType=VEHICLE_INSPECTION" "$DRIVER"
expect L12 "the van keeps both inspections, the newer first" 200 \
  '[.content[].token] | index("'"$NEW_INSPECTION"'") < index("'"$OLD_INSPECTION"'")'
request GET "/api/vehicles/$VAN/documents?status=REJECTED" "$ADMIN"
expect L13 "the admin filters the van's documents by status" 200 \
  '([.content[].token] | index("'"$DOC"'") != null) and all(.content[]; .status == "REJECTED")'
request GET /api/drivers/me/onboarding "$DRIVER"
expect L14 "the onboarding shows the van document with its file URL" 200 \
  '[.documentsStep.vehicles[] | select(.vehicleToken == "'"$VAN"'") | .uploadedDocuments[] | select(.token == "'"$DOC"'") | .fileUrl] | .[0] | startswith("/api/vehicle-documents/")'

table "Who may touch a van's documents"
before=$(docs)
request GET "/api/vehicles/$VAN/documents" "$OTHER"
expect A1 "another driver lists the van's documents" 403
send_doc "/api/vehicles/$VAN/documents" "$OTHER" '{"documentType": "CRLV", "expiresAt": "2027-03-31"}'
expect A2 "another driver adds a document to the van" 403
request GET "/api/vehicle-documents/$DOC" "$OTHER"
expect A3 "another driver reads the document" 403
download "/api/vehicle-documents/$DOC/file" "$OTHER"
check A4 "another driver reads the file and gets no content" "403 0" "$RESP_STATUS $(wc -c <"$BODY_FILE" | tr -d ' ')"
upload_pdf "/api/vehicle-documents/$DOC/file" "$OTHER"
expect A5 "another driver replaces the file" 403
request PUT "/api/vehicle-documents/$DOC" "$OTHER" '{"documentType": "CRLV", "expiresAt": "2030-01-01"}'
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
send_doc "/api/vehicles/$VAN/documents" "$ADMIN" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION", "documentNumber": "AUT-118/2026", "expiresAt": "2027-01-31"}'
expect A16 "the admin sends a document for the van" 201 '.status == "PENDING" and .documentNumber == null'


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
  -F documentNumber=RENAVAM-1 -F expiresAt=2027-03-31)
check B10 "the number and the expiry sent with the file are not read" "PENDING null null" \
  "$(jq -r '"\(.status) \(.documentNumber) \(.expiresAt)"' <<<"$UNREAD")"
request DELETE "/api/vehicle-documents/$(jq -r .token <<<"$UNREAD")" "$DRIVER"

table "What the approval must carry"
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION"}'
AUTH=$(jq -r .token <<<"$RESP_BODY")
request PUT "/api/vehicle-documents/$AUTH/status" "$ADMIN" '{"status": "APPROVED", "documentNumber": "  ", "expiresAt": "2027-03-31"}'
expect P1 "approving an authorization without number" 400 '.field == "documentNumber" and .detail == "Preencha os dados obrigatórios do documento para aprovar."'
request PUT "/api/vehicle-documents/$AUTH/status" "$ADMIN" '{"status": "APPROVED", "documentNumber": "AUT-118/2026"}'
expect P2 "approving without the expiry" 400 '.field == "expiresAt"'
request PUT "/api/vehicle-documents/$AUTH/status" "$ADMIN" "{\"status\": \"APPROVED\", \"documentNumber\": \"$(printf 'A%.0s' {1..65})\", \"expiresAt\": \"2027-03-31\"}"
expect P3 "a number of 65 characters" 400 '.field == "documentNumber"'
request PUT "/api/vehicle-documents/$AUTH/status" "$ADMIN" '{"status": "APPROVED", "documentNumber": "AUT-1", "expiresAt": "31/03/2027"}'
expect P4 "an expiry in the wrong format" 400
check P5 "the refused approvals changed nothing" "PENDING::" \
  "$(sql "select status || ':' || coalesce(document_number, '') || ':' || coalesce(expires_at::text, '') from vehicle_document where token = '$AUTH'")"
request PUT "/api/vehicle-documents/$AUTH/status" "$ADMIN" "{\"status\": \"APPROVED\", \"documentNumber\": \"  $(printf 'A%.0s' {1..64}) \", \"expiresAt\": \"2027-03-31\"}"
expect P6 "a number of exactly 64 characters, stored without the outer spaces" 200 '.status == "APPROVED" and (.documentNumber | length == 64) and .expiresAt == "2027-03-31"'
send_doc "/api/vehicles/$VAN/documents" "$DRIVER" '{"documentType": "CRLV"}'
REJECTED_CRLV=$(jq -r .token <<<"$RESP_BODY")
request PUT "/api/vehicle-documents/$REJECTED_CRLV/status" "$ADMIN" '{"status": "REJECTED", "rejectionReason": "Ilegível", "documentNumber": "X", "expiresAt": "2027-03-31"}'
expect P7 "a rejection does not store the data of its body" 200 '.status == "REJECTED" and .documentNumber == null and .expiresAt == null'

table "The review of a van document"
NOFILE="qa-nofile-$(date +%s)"
sql "insert into vehicle_document (token, vehicle_id, document_type) select '$NOFILE', id, 'CRLV' from vehicle where token = '$VAN'" >/dev/null
decide "/api/vehicle-documents/$NOFILE/status" "$ADMIN" '{"status": "APPROVED"}'
expect R1 "approving a document left without file before this change" 422 '.detail == "O documento só pode ser aprovado depois do envio do arquivo."'
decide "/api/vehicle-documents/$NOFILE/status" "$ADMIN" '{"status": "REJECTED", "rejectionReason": " "}'
expect R2 "rejecting without reason" 400 '.detail == "Informe o motivo da recusa."'
decide "/api/vehicle-documents/$NOFILE/status" "$ADMIN" '{"reviewMethod": "MANUAL"}'
expect R3 "a review without status" 400 '.field == "status"'
decide "/api/vehicle-documents/$NOFILE/status" "$ADMIN" '{"status": "DONE"}'
expect R4 "an unknown status" 400
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
request POST /api/vehicles/me "$DRIVER" '{"plate": "QAD9F01", "brand": "Renault", "model": "Master", "manufactureYear": 2021, "color": "Prata", "capacity": 16}'
expect X7 "the owner adds a second van" 201
VAN2=$(jq -r .token <<<"$RESP_BODY")
send_doc "/api/vehicles/$VAN2/documents" "$DRIVER" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "2027-05-31"}'
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
request PUT "/api/driver-documents/$MUNI/status" "$ADMIN" '{"status": "APPROVED"}'
expect DD1 "approving a municipal authorization without number" 400 '.field == "documentNumber"'
request DELETE "/api/driver-documents/$MUNI" "$DRIVER"
send_doc /api/driver-documents "$DRIVER" '{"documentType": "SCHOOL_DRIVER_COURSE", "documentNumber": " CERT-2024-118 "}'
expect DD4 "the specialized course is sent with its file and no number" 201 '.status == "PENDING" and .documentNumber == null and .file != null'
COURSE=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/driver-documents/$COURSE/file" "$DRIVER"
expect DD5 "the owner replaces the course's file while it is pending" 201
decide "/api/driver-documents/$COURSE/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
expect DD6 "the admin approves it with the number read from the file, resolved by uid" 200 \
  '.status == "APPROVED" and .reviewedByUserToken == "'"$ADMIN_UID"'" and .documentNumber == "CERT-2024-118"'
upload_pdf "/api/driver-documents/$COURSE/file" "$DRIVER"
request GET "/api/driver-documents/$COURSE" "$DRIVER"
expect DD7 "swapping the file returns it to pending without the number" 200 '.status == "PENDING" and .reviewedByUserToken == null and .documentNumber == null'
decide "/api/driver-documents/$COURSE/status" "$ADMIN" '{"status": "REJECTED"}'
expect DD8 "rejecting without reason" 400
decide "/api/driver-documents/$COURSE/status" "$DRIVER" '{"status": "APPROVED"}'
expect DD9 "the owner cannot review" 403
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
request GET "/api/driver-documents?status=PENDING" "$ADMIN"
expect G2 "the admin lists pending driver documents" 200 '.totalElements >= 1'

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
request POST /api/driver-cnhs "$NEW" "{\"registrationNumber\": \"$CNH_NUMBER\", \"category\": \"B\", \"issueDate\": \"2018-04-10\", \"validUntil\": \"$SOON\"}"
expect N4 "a category B license is refused on the category field" 400 '.field == "category" and (.detail | contains("D, E, AD"))'
check N4b "the refused license was not saved" "0" \
  "$(sql "select count(*) from driver_cnh c join driver d on d.id = c.driver_id where d.token = '$NEW_DRIVER'")"
request POST /api/driver-cnhs "$NEW" "{\"registrationNumber\": \"$CNH_NUMBER\", \"category\": \"D\", \"issueDate\": \"2018-04-10\", \"validUntil\": \"$SOON\"}"
expect N4c "the driver registers a category D license" 201
CNH=$(jq -r .token <<<"$RESP_BODY")
# A license saved before the rule with category B is set directly: the API no longer accepts it.
sql "update driver_cnh set category = 'B' where token = '$CNH'" >/dev/null
request GET "/api/driver-cnhs/$CNH" "$NEW"
expect N4d "a license saved before the rule with category B is still read" 200 '.category == "B"'
request GET /api/drivers/me/onboarding "$NEW"
expect N5 "the onboarding reports cnh_category" 200 '.cnhStep.completed == false and .cnhStep.pendingItems == ["cnh_category"]'
credentials "$NVAN" "$NEW"
check N6 "category B does not satisfy DRIVER_LICENSE" "false" "$(satisfied_of DRIVER_LICENSE)"
request PUT "/api/driver-cnhs/$CNH" "$NEW" "{\"registrationNumber\": \"$CNH_NUMBER\", \"category\": \"B\", \"issueDate\": \"2018-04-10\", \"validUntil\": \"$SOON\"}"
expect N6b "changing the license to category B is refused" 400 '.field == "category"'
request PUT "/api/driver-cnhs/$CNH" "$NEW" "{\"registrationNumber\": \"$CNH_NUMBER\", \"category\": \"AD\", \"issueDate\": \"2018-04-10\", \"validUntil\": \"$SOON\"}"
expect N7 "the driver corrects the license to category AD" 200
request GET /api/drivers/me/onboarding "$NEW"
expect N8 "the license step is complete" 200 '.cnhStep.completed == true'
credentials "$NVAN" "$NEW"
check N9 "category AD satisfies DRIVER_LICENSE" "true" "$(satisfied_of DRIVER_LICENSE)"

table "Documents one at a time"
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "CRLV", "documentNumber": "RENAVAM-QA", "expiresAt": "2027-09-30"}'
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
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "2027-05-31"}'
NINSP=$(jq -r .token <<<"$RESP_BODY")
upload_pdf "/api/vehicle-documents/$NINSP/file" "$NEW"
request POST /api/drivers/me/submit-onboarding "$NEW"
expect E6 "still missing the school authorization of any van" 422 '.detail | contains("'"$NEW_PLATE"': SCHOOL_VEHICLE_AUTHORIZATION")'
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "SCHOOL_VEHICLE_AUTHORIZATION", "documentNumber": "AUT-QA-77", "expiresAt": "2027-02-28"}'
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
send_doc "/api/vehicles/$NVAN/documents" "$NEW" '{"documentType": "VEHICLE_INSPECTION", "expiresAt": "2027-11-30"}'
expect E10 "a driver under review can still add a document" 201
EXTRA=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/vehicle-documents/$EXTRA" "$NEW"

table "Approving the driver"
request POST "/api/drivers/$NEW_DRIVER/approve" "$ADMIN"
expect A1x "approving with the documents still pending" 422 \
  '.detail == "O motorista só pode ser aprovado quando pelo menos uma van ativa tiver todos os documentos de transporte escolar aprovados e válidos."'
check A2x "the driver is still under review" "UNDER_REVIEW" "$(sql "select approval_status from driver where token = '$NEW_DRIVER'")"
request POST "/api/drivers/$NEW_DRIVER/reject" "$ADMIN" '{"reason": "Vistoria ilegível"}'
expect A3x "the admin rejects the driver: the inspection is unreadable" 200 '.approvalStatus == "REJECTED"'
upload_pdf "/api/vehicle-documents/$NINSP/file" "$NEW"
request POST /api/drivers/me/submit-onboarding "$NEW"
expect A4x "the driver uploads a new file and submits again" 200 '.approvalStatus == "UNDER_REVIEW"'
for token in $(sql "select dd.token from driver_document dd join driver d on d.id = dd.driver_id where d.token = '$NEW_DRIVER' and dd.deleted_at is null"); do
  decide "/api/driver-documents/$token/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
done
for token in "$NCRLV" "$NAUT"; do
  decide "/api/vehicle-documents/$token/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
done
credentials "$NVAN" "$NEW"
expect A5x "with the inspection still pending only VEHICLE_INSPECTION fails" 200 \
  '.satisfied == false and ([.requirements[] | select((.satisfied | not) and (.optional | not)) | .requirement] == ["VEHICLE_INSPECTION"])'
request POST "/api/drivers/$NEW_DRIVER/approve" "$ADMIN"
expect A6x "approving with the inspection of the only complete van pending" 422
decide "/api/vehicle-documents/$NINSP/status" "$ADMIN" '{"status": "APPROVED", "reviewMethod": "MANUAL"}'
credentials "$NVAN" "$NEW"
expect A7x "everything approved: the van passes, with each number and expiry" 200 \
  '.satisfied == true and ([.requirements[] | select(.requirement == "SCHOOL_VEHICLE_AUTHORIZATION")][0] | .number == "AUT-QA-77" and .validUntil == "2027-02-28")
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

echo
echo "TOTAL: $PASSED passed"
