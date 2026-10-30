#!/usr/bin/env bash
# Manual QA for the service-request-proposal change (constitution rule 55).
# Runs against a live API backed by a THROWAWAY database seeded on an empty schema, with Google
# Places pointed at places-stub.py: it inserts contracts directly, backdates links and flips driver
# availability. See qa.md next to the change.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

AREA_PLACE="df-taguatinga-qnl5"

client_token() {
  sql "select c.token from client c join users u on u.id = c.user_id where u.email = '$1@seed.vanep.com.br'"
}

driver_token() {
  sql "select d.token from driver d join users u on u.id = d.user_id where u.email = '$1@seed.vanep.com.br'"
}

create_link() {
  request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$1" --arg d "$2" '{clientToken: $c, driverToken: $d}')"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | link | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

backdate_link() {
  sql "update client_driver set created_at = now() - interval '1 day' where token = '$1'" >/dev/null
}

set_link_status() {
  sql "update client_driver set status = '$2' where token = '$1'" >/dev/null
}

insert_contract() {
  local link=$1 status=$2 token=$3
  sql "insert into contract (token, client_driver_id, status, starts_on, ends_on, total_amount, installments, due_day)
       select '$token', id, '$status', date '2027-02-01', date '2027-12-17', 4200, 12, 5
       from client_driver where token = '$link'" >/dev/null
}

driver_rating() {
  jq -nc --arg d "$1" --argjson r "${2:-4.50}" '{driverToken: $d, rating: $r, comment: "QA"}'
}

client_rating() {
  jq -nc --arg c "$1" --argjson r "${2:-4.50}" '{clientToken: $c, rating: $r, comment: "QA"}'
}

rating_counts() {
  sql "select (select count(*) from driver_rating) || ' ' || (select count(*) from client_rating)"
}

is_available() {
  sql "select is_available from driver where token = '$1'"
}

availability_body() {
  request GET "/api/drivers/$1" "$2"
  jq -c --argjson a "$3" '{bio, cnpj, experienceYears, basePrice, workStartTime, workEndTime,
    workDays, waitToleranceMinutes, available: $a}' <<<"$RESP_BODY"
}

restore_availability() {
  sql "update driver set is_available = true where is_available = false" >/dev/null || true
}

restore_approval() {
  sql "update driver set approval_status = 'APPROVED', rejection_reason = null where approval_status <> 'APPROVED'" >/dev/null || true
}

put_under_review() {
  sql "update driver set approval_status = 'UNDER_REVIEW', is_available = false, rejection_reason = null where token = '$1'" >/dev/null
}

cleanup() {
  restore_approval
  restore_availability
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

echo "== setup"
[[ $(count client_driver) == 1 && $(count contract) -le 1 && $(count driver_service_area) == 0 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (one link, no service areas)" >&2
  exit 1
}
ADMIN=$(login "$ADMIN_EMAIL")
BRUNO=$(login bruno.lima@seed.vanep.com.br)
CARLA=$(login carla.nunes@seed.vanep.com.br)
DIEGO=$(login diego.alves@seed.vanep.com.br)
ELENA=$(login elena.rocha@seed.vanep.com.br)
ANA=$(login ana.souza@seed.vanep.com.br)
FABIO=$(login fabio.teixeira@seed.vanep.com.br)
GUSTAVO=$(login gustavo.santos@seed.vanep.com.br)
HELENA=$(login helena.costa@seed.vanep.com.br)

BRUNO_C=$(client_token bruno.lima)
CARLA_C=$(client_token carla.nunes)
DIEGO_C=$(client_token diego.alves)
ELENA_C=$(client_token elena.rocha)
FABIO_D=$(driver_token fabio.teixeira)
GUSTAVO_D=$(driver_token gustavo.santos)
HELENA_D=$(driver_token helena.costa)

BRUNO_GUSTAVO=$(create_link "$BRUNO_C" "$GUSTAVO_D")
insert_contract "$BRUNO_GUSTAVO" ACTIVE qasrpactive
set_link_status "$BRUNO_GUSTAVO" ACTIVE
BRUNO_HELENA=$(create_link "$BRUNO_C" "$HELENA_D")
insert_contract "$BRUNO_HELENA" ENDED qasrpended
set_link_status "$BRUNO_HELENA" INACTIVE
CARLA_GUSTAVO=$(create_link "$CARLA_C" "$GUSTAVO_D")
insert_contract "$CARLA_GUSTAVO" CANCELLED qasrpcancelled
set_link_status "$CARLA_GUSTAVO" INACTIVE
CARLA_HELENA=$(create_link "$CARLA_C" "$HELENA_D")
insert_contract "$CARLA_HELENA" ACTIVE qasrpdeleted
set_link_status "$CARLA_HELENA" ACTIVE
DIEGO_GUSTAVO=$(create_link "$DIEGO_C" "$GUSTAVO_D")
for link in "$BRUNO_GUSTAVO" "$BRUNO_HELENA" "$CARLA_GUSTAVO" "$CARLA_HELENA" "$DIEGO_GUSTAVO"; do
  backdate_link "$link"
done
ELENA_HELENA=$(create_link "$ELENA_C" "$HELENA_D")
insert_contract "$ELENA_HELENA" ACTIVE qasrpyoung
set_link_status "$ELENA_HELENA" ACTIVE
SEED_RATINGS=$(rating_counts)
read -r SEED_DRIVER_RATINGS SEED_CLIENT_RATINGS <<<"$SEED_RATINGS"

table "Ratings require a contract that reached ACTIVE"
request GET "/api/driver-ratings/status?driverToken=$GUSTAVO_D" "$BRUNO"
expect R1 "status with an ACTIVE contract" 200 '.rated == false and .canRate == true'
request POST /api/driver-ratings "$BRUNO" "$(driver_rating "$GUSTAVO_D" 5.00)"
expect R2 "the client rates the driver of an ACTIVE contract, rating at the maximum" 201
request GET "/api/driver-ratings/status?driverToken=$GUSTAVO_D" "$BRUNO"
expect R3 "after rating, the button goes away" 200 '.rated == true and .canRate == false'
request POST /api/driver-ratings "$BRUNO" "$(driver_rating "$GUSTAVO_D")"
expect R4 "rating the same link again" 409
request POST /api/client-ratings "$GUSTAVO" "$(client_rating "$BRUNO_C" 1.00)"
expect R5 "the driver rates the client of an ACTIVE contract, rating at the minimum" 201
request GET "/api/driver-ratings/status?driverToken=$HELENA_D" "$BRUNO"
expect R6 "status of an INACTIVE link whose contract ENDED" 200 '.canRate == true'
request POST /api/driver-ratings "$BRUNO" "$(driver_rating "$HELENA_D")"
expect R7 "the client rates the driver after the contract ENDED" 201
request POST /api/client-ratings "$HELENA" "$(client_rating "$BRUNO_C")"
expect R8 "the driver rates the client after the contract ENDED" 201
request POST /api/driver-ratings "$CARLA" "$(driver_rating "$GUSTAVO_D")"
expect R9 "a CANCELLED contract does not let the client rate" 422 \
  '.detail == "Só é possível avaliar um motorista com quem você já teve um contrato em andamento."'
request POST /api/client-ratings "$GUSTAVO" "$(client_rating "$CARLA_C")"
expect R10 "a CANCELLED contract does not let the driver rate" 422 \
  '.detail == "Só é possível avaliar um cliente com quem você já teve um contrato em andamento."'
request GET "/api/driver-ratings/status?driverToken=$GUSTAVO_D" "$CARLA"
expect R11 "status with only a CANCELLED contract" 200 '.canRate == false'
request DELETE /api/contracts/qasrpdeleted "$ADMIN"
expect R12 "the admin deletes the only ACTIVE contract of a link" 204
request POST /api/driver-ratings "$CARLA" "$(driver_rating "$HELENA_D")"
expect R13 "a deleted contract does not let the client rate" 422
request POST /api/client-ratings "$HELENA" "$(client_rating "$CARLA_C")"
expect R14 "a deleted contract does not let the driver rate" 422
request GET "/api/driver-ratings/status?driverToken=$GUSTAVO_D" "$DIEGO"
expect R15 "status of a PENDING link with no contract" 200 '.rated == false and .canRate == false'
request POST /api/driver-ratings "$DIEGO" "$(driver_rating "$GUSTAVO_D")"
expect R16 "no contract: the client cannot rate" 422 \
  '.detail == "Só é possível avaliar um motorista com quem você já teve um contrato em andamento."'
request POST /api/client-ratings "$GUSTAVO" "$(client_rating "$DIEGO_C")"
expect R17 "no contract: the driver cannot rate" 422 \
  '.detail == "Só é possível avaliar um cliente com quem você já teve um contrato em andamento."'
request POST /api/driver-ratings "$ELENA" "$(driver_rating "$GUSTAVO_D")"
expect R18 "a third client with no link to the driver" 404
request POST /api/client-ratings "$FABIO" "$(client_rating "$BRUNO_C")"
expect R19 "a third driver with no link to the client" 404
request POST /api/driver-ratings "$ELENA" "$(driver_rating "$HELENA_D")"
expect R20 "an ACTIVE contract on a link younger than five minutes" 422 \
  '.detail == "Seu vínculo com este motorista é recente demais para avaliá-lo. Tente novamente em alguns minutos."'
request POST /api/client-ratings "$HELENA" "$(client_rating "$ELENA_C")"
expect R21 "the client rating has no minimum link age" 201
check R22 "refused ratings stored nothing (driver, client)" \
  "$((SEED_DRIVER_RATINGS + 2)) $((SEED_CLIENT_RATINGS + 3))" "$(rating_counts)"

table "Ratings — misuse"
request POST /api/driver-ratings "" "$(driver_rating "$GUSTAVO_D")"
expect M1 "no token" 401
request POST /api/driver-ratings "not-a-jwt" "$(driver_rating "$GUSTAVO_D")"
expect M2 "invalid token" 401
request POST /api/driver-ratings "$GUSTAVO" "$(driver_rating "$HELENA_D")"
expect M3 "a driver cannot rate a driver" 403
request POST /api/client-ratings "$BRUNO" "$(client_rating "$CARLA_C")"
expect M4 "a client cannot rate a client" 403
request POST /api/driver-ratings "$DIEGO" "$(driver_rating "$GUSTAVO_D" 5.01)"
expect M5 "rating just above the maximum" 400
request POST /api/driver-ratings "$DIEGO" "$(driver_rating "$GUSTAVO_D" 0.99)"
expect M6 "rating just below the minimum" 400
request POST /api/driver-ratings "$DIEGO" "{\"driverToken\":\"$GUSTAVO_D\",\"rating\":\"cinco\"}"
expect M7 "rating with the wrong JSON type" 400
request POST /api/driver-ratings "$DIEGO" '{"driverToken":'
expect M8 "malformed JSON" 400
request POST /api/driver-ratings "$DIEGO" '{"rating":4}'
expect M9 "missing driverToken" 400
request POST /api/driver-ratings "$DIEGO" "$(driver_rating doesnotexist)"
expect M10 "unknown driver" 404
request GET "/api/driver-ratings/status?driverToken=$GUSTAVO_D" "$GUSTAVO"
expect M11 "a driver cannot ask the client's rating status" 403
check M12 "misuse stored nothing" "$((SEED_DRIVER_RATINGS + 2)) $((SEED_CLIENT_RATINGS + 3))" "$(rating_counts)"

table "Search and recommended show only drivers accepting new students"
AREA_BODY=$(jq -nc --arg p "$AREA_PLACE" '{areas: [{placeId: $p}]}')
request PUT /api/drivers/me/service-areas "$GUSTAVO" "$AREA_BODY"
expect A1 "Gustavo covers Taguatinga" 200
request PUT /api/drivers/me/service-areas "$HELENA" "$AREA_BODY"
expect A2 "Helena covers Taguatinga" 200
request GET "/api/drivers/search?placeId=$AREA_PLACE" "$ANA"
expect A3 "both available drivers are found, and counted" 200 \
  '([.content[].name] | sort) == ["Gustavo Santos", "Helena Costa"] and .totalElements == 2'
request GET /api/drivers/recommended "$ANA"
expect A4 "the three seeded drivers are recommended" 200 '.totalElements == 3 and (.content | length) == 3'
request PUT "/api/drivers/$HELENA_D" "$HELENA" "$(availability_body "$HELENA_D" "$HELENA" false)"
expect A5 "Helena stops accepting new students" 200 '.available == false'
check A6 "is_available is stored false" f "$(is_available "$HELENA_D")"
request GET "/api/drivers/search?placeId=$AREA_PLACE" "$ANA"
expect A7 "search leaves Helena out and the total does not count her" 200 \
  '([.content[].name]) == ["Gustavo Santos"] and .totalElements == 1'
request GET "/api/drivers/search?placeId=$AREA_PLACE&size=1&page=1" "$ANA"
expect A8 "the second page of size 1 is empty" 200 '(.content | length) == 0 and .totalElements == 1'
request GET /api/drivers/recommended "$ANA"
expect A9 "recommended leaves Helena out and counts two" 200 \
  '.totalElements == 2 and ([.content[].name] | index("Helena Costa")) == null'
request GET "/api/drivers/$HELENA_D/profile" "$ANA"
expect A10 "Helena's profile stays reachable and says she is not available" 200 '.available == false'
request GET "/api/drivers/$HELENA_D/profile" "$FABIO"
expect A11 "another driver reads the same profile" 200 '.available == false'
request PUT "/api/drivers/$HELENA_D" "$BRUNO" "$(availability_body "$HELENA_D" "$HELENA" true)"
expect A12 "another user cannot turn her availability back on" 403
request PUT "/api/drivers/$HELENA_D" "$HELENA" \
  "$(availability_body "$HELENA_D" "$HELENA" true | jq -c 'del(.available)')"
expect A13 "availability missing" 400
request PUT "/api/drivers/$HELENA_D" "$HELENA" \
  "$(availability_body "$HELENA_D" "$HELENA" true | jq -c '.available = "sim"')"
expect A14 "availability with the wrong JSON type" 400
check A15 "the refused updates left her unavailable" f "$(is_available "$HELENA_D")"
request GET "/api/drivers/search?placeId=$AREA_PLACE" "$ANA"
expect A16 "still hidden after the refused updates" 200 '.totalElements == 1'
request PUT "/api/drivers/$HELENA_D" "$HELENA" "$(availability_body "$HELENA_D" "$HELENA" true)"
expect A17 "Helena accepts new students again" 200 '.available == true'
request GET "/api/drivers/search?placeId=$AREA_PLACE" "$ANA"
expect A18 "and she is back in the search" 200 '.totalElements == 2'
request GET "/api/drivers/search?placeId=$AREA_PLACE" ""
expect A19 "search without token" 401
request GET /api/drivers/recommended "not-a-jwt"
expect A20 "recommended with an invalid token" 401

table "Approval makes the driver available"
put_under_review "$FABIO_D"
request GET /api/drivers/recommended "$ANA"
expect V1 "a driver under review is not recommended" 200 \
  '.totalElements == 2 and ([.content[].name] | index("Fabio Teixeira")) == null'
request POST "/api/drivers/$FABIO_D/reject" "$ADMIN" '{"reason":"Unreadable document"}'
expect V2 "the admin rejects the driver" 200 '.approvalStatus == "REJECTED" and .available == false'
check V3 "a rejected driver stays unavailable" f "$(is_available "$FABIO_D")"
put_under_review "$FABIO_D"
request POST "/api/drivers/$FABIO_D/approve" "$FABIO"
expect V4 "the driver cannot approve themselves" 403
request POST "/api/drivers/$FABIO_D/approve" "$ANA"
expect V5 "a client cannot approve a driver" 403
request POST "/api/drivers/$FABIO_D/approve" ""
expect V6 "approving without token" 401
check V7 "the refused approvals changed nothing" "UNDER_REVIEW false" \
  "$(sql "select approval_status || ' ' || is_available from driver where token = '$FABIO_D'")"
request POST "/api/drivers/$FABIO_D/approve" "$ADMIN"
expect V8 "the admin approves the driver, who becomes available" 200 \
  '.approvalStatus == "APPROVED" and .available == true'
check V9 "is_available is stored true" t "$(is_available "$FABIO_D")"
request GET /api/drivers/recommended "$ANA"
expect V10 "the approved driver is recommended right away" 200 \
  '.totalElements == 3 and ([.content[].name] | index("Fabio Teixeira")) != null'
request POST "/api/drivers/$FABIO_D/approve" "$ADMIN"
expect V11 "approving an approved driver again" 400
request POST /api/drivers/doesnotexist/approve "$ADMIN"
expect V12 "approving an unknown driver" 404

echo
echo "ALL PASSED: $PASSED scenarios"
