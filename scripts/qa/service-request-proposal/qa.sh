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
  request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$1" --arg d "$2" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH"}')"
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

link_body() {
  jq -nc --arg c "$1" --arg d "$2" --argjson o "$3" '{clientToken: $c, driverToken: $d, origin: $o}'
}

link_origin() {
  sql "select origin from client_driver where token = '$1'"
}

link_statuses() {
  sql "select string_agg(token || '=' || status, ',' order by token) from client_driver where token in ($1)"
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

table "Every link has an immutable origin"
check O1 "the seeded link and the links of the setup were created as CLIENT_SEARCH" 0 \
  "$(sql "select count(*) from client_driver where origin <> 'CLIENT_SEARCH'")"
request POST /api/client-drivers "$ADMIN" "$(link_body "$DIEGO_C" "$HELENA_D" '"DRIVER_INVITE"')"
expect O2 "the admin creates a link with origin DRIVER_INVITE" 201 '.status == "PENDING" and .origin == "DRIVER_INVITE"'
DIEGO_HELENA=$(jq -r .token <<<"$RESP_BODY")
check O3 "the origin is stored" DRIVER_INVITE "$(link_origin "$DIEGO_HELENA")"
request GET "/api/client-drivers/$DIEGO_HELENA" "$DIEGO"
expect O4 "the client of the link reads its origin" 200 '.origin == "DRIVER_INVITE"'
request GET /api/client-drivers/me "$HELENA"
expect O5 "the driver's own list carries the origin of every link" 200 \
  "([.[] | select(.token == \"$DIEGO_HELENA\") | .origin] == [\"DRIVER_INVITE\"]) and all(.[]; .origin != null)"
request GET /api/client-drivers "$ADMIN"
expect O6 "the admin's page carries the origin of every link" 200 \
  'all(.content[]; .origin == "CLIENT_SEARCH" or .origin == "DRIVER_INVITE")'
request POST /api/client-drivers "$ADMIN" "$(link_body "$DIEGO_C" "$FABIO_D" '"CLIENT_SEARCH"')"
expect O7 "the admin creates a link with origin CLIENT_SEARCH" 201 '.status == "PENDING" and .origin == "CLIENT_SEARCH"'
DIEGO_FABIO=$(jq -r .token <<<"$RESP_BODY")

table "Link origin — misuse"
LINKS_BEFORE=$(count client_driver)
request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$ELENA_C" --arg d "$GUSTAVO_D" '{clientToken: $c, driverToken: $d}')"
expect O8 "no origin" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" null)"
expect O9 "origin null" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"WALK_IN"')"
expect O10 "unknown origin" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"driver_invite"')"
expect O11 "origin in lower case" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" '""')"
expect O12 "blank origin" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" true)"
expect O13 "origin as a boolean" 400
request POST /api/client-drivers "$ADMIN" "$(link_body "$ELENA_C" "$GUSTAVO_D" '{"value":"DRIVER_INVITE"}')"
expect O14 "origin as an object" 400
request POST /api/client-drivers "$ADMIN" '{"clientToken": "x", "origin": '
expect O15 "malformed JSON" 400
request POST /api/client-drivers "$ELENA" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"CLIENT_SEARCH"')"
expect O16 "a client cannot create a link" 403
request POST /api/client-drivers "$GUSTAVO" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"DRIVER_INVITE"')"
expect O17 "a driver cannot create a link" 403
request POST /api/client-drivers "" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"CLIENT_SEARCH"')"
expect O18 "no token" 401
request POST /api/client-drivers "not-a-jwt" "$(link_body "$ELENA_C" "$GUSTAVO_D" '"CLIENT_SEARCH"')"
expect O19 "invalid token" 401
check O20 "the refused creations wrote nothing" "$LINKS_BEFORE" "$(count client_driver)"
request POST /api/client-drivers "$ADMIN" "$(link_body "$DIEGO_C" "$HELENA_D" '"CLIENT_SEARCH"')"
expect O21 "the same pair again with the other origin" 409 '.detail == "Este cliente e este motorista já estão vinculados."'
check O22 "the existing link keeps DRIVER_INVITE" DRIVER_INVITE "$(link_origin "$DIEGO_HELENA")"
request PATCH "/api/client-drivers/$DIEGO_HELENA" "$ADMIN" '{"origin":"CLIENT_SEARCH"}'
expect O23 "there is no route that writes the origin" 405
check O24 "the origin did not change" DRIVER_INVITE "$(link_origin "$DIEGO_HELENA")"
request DELETE "/api/client-drivers/$DIEGO_HELENA" "$ADMIN"
expect O25 "the admin removes the link" 204
request POST "/api/client-drivers/$DIEGO_HELENA/restore" "$ADMIN" '{"origin":"CLIENT_SEARCH"}'
expect O26 "restoring brings it back with its origin, whatever the body says" 200 '.origin == "DRIVER_INVITE"'
ELENA_GUSTAVO_BODY=$(link_body "$ELENA_C" "$GUSTAVO_D" '"CLIENT_SEARCH"')
check O27 "six concurrent creations of a new pair: one wins, the rest get 409" "201 409 409 409 409 409" \
  "$(in_parallel POST "$ADMIN" "$ELENA_GUSTAVO_BODY" /api/client-drivers /api/client-drivers /api/client-drivers \
    /api/client-drivers /api/client-drivers /api/client-drivers)"
check O28 "and the pair has exactly one link" 1 \
  "$(sql "select count(*) from client_driver l join client c on c.id = l.client_id join driver d on d.id = l.driver_id where c.token = '$ELENA_C' and d.token = '$GUSTAVO_D'")"

table "V61: origin and block columns (database checks)"
BLOCKER="(select id from users where email = 'gustavo.santos@seed.vanep.com.br')"
ON_DIEGO_GUSTAVO="where token = '$DIEGO_GUSTAVO'"
check_sql_rejected D1 "BLOCKED without a blocker" client_driver_blocked_by_check \
  "update client_driver set status = 'BLOCKED', blocked_at = now() $ON_DIEGO_GUSTAVO"
check_sql_rejected D2 "BLOCKED without a block time" client_driver_blocked_at_check \
  "update client_driver set status = 'BLOCKED', blocked_by = $BLOCKER $ON_DIEGO_GUSTAVO"
check_sql_rejected D3 "a blocker on a link that is not BLOCKED" client_driver_blocked_by_check \
  "update client_driver set blocked_by = $BLOCKER $ON_DIEGO_GUSTAVO"
check_sql_rejected D4 "a block time on a link that is not BLOCKED" client_driver_blocked_at_check \
  "update client_driver set blocked_at = now() $ON_DIEGO_GUSTAVO"
check_sql_accepted D5 "BLOCKED with a blocker and a block time" \
  "update client_driver set status = 'BLOCKED', blocked_by = $BLOCKER, blocked_at = now() $ON_DIEGO_GUSTAVO"
check_sql_rejected D6 "a blocker that is not a user" client_driver_blocked_by_fkey \
  "update client_driver set status = 'BLOCKED', blocked_by = -1, blocked_at = now() $ON_DIEGO_GUSTAVO"
check_sql_rejected D7 "a link without origin" '"origin"' \
  "insert into client_driver (token, client_id, driver_id, status) select 'qanoorigin', client_id, driver_id, 'PENDING' from client_driver $ON_DIEGO_GUSTAVO"
check D8 "the checks above were rolled back" "PENDING CLIENT_SEARCH" \
  "$(sql "select status || ' ' || origin || coalesce(blocked_by::text, '') || coalesce(blocked_at::text, '') from client_driver $ON_DIEGO_GUSTAVO")"
check D9 "origin, blocked_by and blocked_at are documented" 3 \
  "$(sql "select count(col_description('client_driver'::regclass, attnum)) from pg_attribute where attrelid = 'client_driver'::regclass and attname in ('origin', 'blocked_by', 'blocked_at')")"

table "V61 re-applied on links blocked before the blocker existed"
LEGACY="'$DIEGO_GUSTAVO','$BRUNO_GUSTAVO','$BRUNO_HELENA','$CARLA_GUSTAVO','$CARLA_HELENA','$DIEGO_FABIO'"
UNBLOCKED_BEFORE=$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver where token not in ($LEGACY)")
sql "alter table client_driver drop constraint client_driver_blocked_by_check, drop constraint client_driver_blocked_at_check,
       drop column origin, drop column blocked_by, drop column blocked_at;
     update client_driver set status = 'BLOCKED' where token in ($LEGACY);
     update client_driver set deleted_at = now() where token = '$DIEGO_FABIO'" >/dev/null
apply_migration V61
check L1 "each legacy BLOCKED link gets the status its contracts give" \
  "$(tr ' ' '\n' <<<"$BRUNO_GUSTAVO=ACTIVE $BRUNO_HELENA=INACTIVE $CARLA_GUSTAVO=INACTIVE $CARLA_HELENA=PENDING $DIEGO_FABIO=PENDING $DIEGO_GUSTAVO=PENDING" | LC_ALL=C sort | paste -sd ',' -)" \
  "$(link_statuses "$LEGACY")"
check L2 "the links that were not BLOCKED keep their status" "$UNBLOCKED_BEFORE" \
  "$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver where token not in ($LEGACY)")"
check L3 "every link, removed ones included, gets CLIENT_SEARCH" "0 0" \
  "$(sql "select count(*) filter (where origin <> 'CLIENT_SEARCH') || ' ' || count(*) filter (where status = 'BLOCKED') from client_driver")"
check_sql_rejected L4 "the checks are back" client_driver_blocked_by_check \
  "update client_driver set status = 'BLOCKED', blocked_at = now() $ON_DIEGO_GUSTAVO"
request GET "/api/client-drivers/$BRUNO_GUSTAVO" "$BRUNO"
expect L5 "the API reads a link the migration brought back" 200 '.status == "ACTIVE" and .origin == "CLIENT_SEARCH"'

echo
echo "ALL PASSED: $PASSED scenarios"
