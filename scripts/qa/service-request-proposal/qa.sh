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

dependent_token() {
  sql "select token from dependent where name = '$1'"
}

school_days_body() {
  jq -nc '{days: [$ARGS.positional[] | split(":") | {weekday: .[0], schoolShift: .[1]}]}' --args "$@"
}

grid_of() {
  sql "select coalesce(string_agg(day.weekday || ':' || day.school_shift, ',' order by array_position(
         array['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'], day.weekday::text)), '')
       from dependent_school_day day join dependent on dependent.id = day.dependent_id
       where dependent.token = '$1' and day.deleted_at is null"
}

cleanup() {
  restore_approval
  restore_availability
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

echo "== setup"
[[ $(count client_driver) == 2 && $(count contract) -le 1 && $(count service_request) == 1 \
  && $(count driver_service_area) == 0 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (the seed link, the seeded request and its link, no service areas)" >&2
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
# The seeded contract would lock Lucas (D17); the grid and readiness scenarios below edit him.
SEED_CONTRACT=$(sql "select token from contract where status = 'ACTIVE' and deleted_at is null limit 1")
if [[ -n $SEED_CONTRACT ]]; then
  request PATCH "/api/contracts/$SEED_CONTRACT" "$ADMIN" '{"status":"ENDED"}'
  [[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | end the seeded contract | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
fi

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

table "Weekly school days of a dependent"
LUCAS=$(dependent_token "Lucas Souza")
MARINA=$(dependent_token "Marina Souza")
GRID='map(.weekday + ":" + .schoolShift)'
request GET "/api/dependent/$LUCAS/school-days" "$ANA"
expect G1 "the seeded dependent is full time on Tuesday and Thursday, Monday first" 200 \
  "$GRID == [\"MONDAY:MORNING\", \"TUESDAY:FULLTIME\", \"WEDNESDAY:MORNING\", \"THURSDAY:FULLTIME\", \"FRIDAY:MORNING\"]"
request GET "/api/dependent/$MARINA/school-days" "$ANA"
expect G2 "the other seeded dependent has mornings from Monday to Friday" 200 \
  "$GRID == [\"MONDAY:MORNING\", \"TUESDAY:MORNING\", \"WEDNESDAY:MORNING\", \"THURSDAY:MORNING\", \"FRIDAY:MORNING\"]"
request GET "/api/dependent/$MARINA" "$ANA"
MARINA_BEFORE=$RESP_BODY
request PUT "/api/dependent/$MARINA/school-days" "$ANA" "$(school_days_body FRIDAY:AFTERNOON THURSDAY:FULLTIME MONDAY:AFTERNOON)"
expect G3 "the client replaces the grid out of order and gets it back Monday first" 200 \
  "$GRID == [\"MONDAY:AFTERNOON\", \"THURSDAY:FULLTIME\", \"FRIDAY:AFTERNOON\"]"
request GET "/api/dependent/$MARINA/school-days" "$ANA"
expect G4 "reading the grid returns the replaced days" 200 \
  "$GRID == [\"MONDAY:AFTERNOON\", \"THURSDAY:FULLTIME\", \"FRIDAY:AFTERNOON\"]"
check G5 "the database holds only the replaced days as active" "MONDAY:AFTERNOON,THURSDAY:FULLTIME,FRIDAY:AFTERNOON" "$(grid_of "$MARINA")"
check G6 "the five replaced days were soft-deleted, not removed" 5 \
  "$(sql "select count(*) from dependent_school_day day join dependent on dependent.id = day.dependent_id where dependent.token = '$MARINA' and day.deleted_at is not null")"
request GET "/api/dependent/$MARINA" "$ANA"
check G7 "replacing the grid changed no other field of the dependent" "$MARINA_BEFORE" "$RESP_BODY"
request PUT "/api/dependent/$MARINA/school-days" "$ANA" \
  "$(school_days_body SUNDAY:MORNING SATURDAY:MORNING FRIDAY:NIGHT THURSDAY:NIGHT WEDNESDAY:FULLTIME TUESDAY:AFTERNOON MONDAY:MORNING)"
expect G8 "all seven weekdays are accepted" 200 'length == 7 and .[0].weekday == "MONDAY" and .[6].weekday == "SUNDAY"'
request GET "/api/dependent/$LUCAS/school-days" "$ADMIN"
expect G9 "the admin reads the grid of any dependent" 200 'length == 5'
request PUT "/api/dependent/$MARINA/school-days" "$ADMIN" "$(school_days_body MONDAY:MORNING)"
expect G10 "the admin replaces the grid of any dependent" 200 "$GRID == [\"MONDAY:MORNING\"]"
request PUT "/api/dependent/$MARINA/school-days" "$ANA" '{"days": []}'
expect G11 "an empty list clears the grid" 200 'length == 0'
request GET "/api/dependent/$MARINA/school-days" "$ANA"
expect G12 "the cleared grid reads empty" 200 'length == 0'
request PUT "/api/dependent/$MARINA/school-days" "$ANA" "$(school_days_body TUESDAY:NIGHT)"
expect G13 "the client sets a grid on the dependent it is about to remove" 200
request DELETE "/api/dependent/$MARINA" "$ANA"
expect G14 "the client removes the dependent" 204
request GET "/api/dependent/$MARINA/school-days" "$ANA"
expect G15 "the grid of a removed dependent is not found" 404 '.code == "dependent.not_found"'
request PUT "/api/dependent/$MARINA/school-days" "$ANA" "$(school_days_body MONDAY:MORNING)"
expect G16 "nor can it be replaced" 404
check G17 "the rows of the removed dependent stay as they were" "TUESDAY:NIGHT" "$(grid_of "$MARINA")"
request POST "/api/dependent/$MARINA/restore" "$ANA"
expect G18 "the client restores the dependent" 200
request GET "/api/dependent/$MARINA/school-days" "$ANA"
expect G19 "the restored dependent comes back with its grid" 200 "$GRID == [\"TUESDAY:NIGHT\"]"
LUCAS_GRID_BODY=$(school_days_body MONDAY:MORNING TUESDAY:FULLTIME WEDNESDAY:MORNING THURSDAY:FULLTIME FRIDAY:MORNING)
check G20 "six concurrent replacements of the same grid all succeed" "200 200 200 200 200 200" \
  "$(in_parallel PUT "$ANA" "$LUCAS_GRID_BODY" /api/dependent/$LUCAS/school-days /api/dependent/$LUCAS/school-days \
    /api/dependent/$LUCAS/school-days /api/dependent/$LUCAS/school-days /api/dependent/$LUCAS/school-days \
    /api/dependent/$LUCAS/school-days)"
check G21 "and leave exactly one active grid" "MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING" \
  "$(grid_of "$LUCAS")"
request GET "/api/dependent/$LUCAS" "$ANA"
expect G22 "the dependent has no single shift anymore" 200 'has("shift") | not'
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Teo Souza", shift: "FULLTIME"}')"
expect G23 "a shift sent on creation is ignored" 201 'has("shift") | not'
request DELETE "/api/dependent/$(jq -r .token <<<"$RESP_BODY")" "$ANA"
expect G24 "the client removes the dependent created to check the shift" 204

table "Weekly school days — misuse"
LUCAS_GRID=$(grid_of "$LUCAS")
MONDAY_BODY=$(school_days_body MONDAY:NIGHT)
request GET "/api/dependent/$LUCAS/school-days" ""
expect W1 "reading without a token" 401
request GET "/api/dependent/$LUCAS/school-days" "not-a-jwt"
expect W2 "reading with an invalid token" 401
request PUT "/api/dependent/$LUCAS/school-days" "" "$MONDAY_BODY"
expect W3 "replacing without a token" 401
request GET "/api/dependent/$LUCAS/school-days" "$BRUNO"
expect W4 "another client cannot read the grid" 403
request PUT "/api/dependent/$LUCAS/school-days" "$BRUNO" "$MONDAY_BODY"
expect W5 "another client cannot replace the grid" 403
request GET "/api/dependent/$LUCAS/school-days" "$GUSTAVO"
expect W6 "a driver cannot read the grid" 403
request PUT "/api/dependent/$LUCAS/school-days" "$GUSTAVO" "$MONDAY_BODY"
expect W7 "a driver cannot replace the grid" 403
request GET /api/dependent/doesnotexist/school-days "$ANA"
expect W8 "the grid of an unknown dependent" 404 '.code == "dependent.not_found"'
request PUT /api/dependent/doesnotexist/school-days "$ANA" "$MONDAY_BODY"
expect W9 "replacing the grid of an unknown dependent" 404
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" "$(school_days_body MONDAY:MORNING MONDAY:FULLTIME)"
expect W10 "the same weekday twice, pt-BR message" 400 '.detail == "O mesmo dia da semana não pode aparecer duas vezes na grade escolar."'
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" \
  "$(school_days_body MONDAY:MORNING TUESDAY:MORNING WEDNESDAY:MORNING THURSDAY:MORNING FRIDAY:MORNING SATURDAY:MORNING SUNDAY:MORNING MONDAY:NIGHT)"
expect W11 "eight entries, one past the seven weekdays" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": [{"schoolShift": "MORNING"}]}'
expect W12 "a day without weekday" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": [{"weekday": "MONDAY"}]}'
expect W13 "a day without schoolShift" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" "$(school_days_body FUNDAY:MORNING)"
expect W14 "an unknown weekday" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" "$(school_days_body MONDAY:EVENING)"
expect W15 "an unknown schoolShift" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" "$(school_days_body monday:MORNING)"
expect W16 "a weekday in lower case" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": [null]}'
expect W17 "a null day" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": null}'
expect W18 "days null" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{}'
expect W19 "no days field" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": "MONDAY"}'
expect W20 "days as a string" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": {"weekday": "MONDAY", "schoolShift": "MORNING"}}'
expect W21 "days as an object" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": [{"weekday": "MONDAY", "schoolShift": true}]}'
expect W22 "schoolShift as a boolean" 400
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": [{"weekday": "MONDAY",'
expect W23 "malformed JSON" 400
check W24 "the refused requests left the grid unchanged" "$LUCAS_GRID" "$(grid_of "$LUCAS")"

table "V62: dependent_school_day (database checks)"
LUCAS_ID="(select id from dependent where token = '$LUCAS')"
check_sql_rejected K1 "a second active row for the same weekday" dependent_school_day_weekday_active_key \
  "insert into dependent_school_day (dependent_id, weekday, school_shift) values ($LUCAS_ID, 'MONDAY', 'NIGHT')"
check_sql_accepted K2 "the same weekday once the active row is soft-deleted" \
  "update dependent_school_day set deleted_at = now() where dependent_id = $LUCAS_ID and weekday = 'MONDAY' and deleted_at is null;
   insert into dependent_school_day (dependent_id, weekday, school_shift) values ($LUCAS_ID, 'MONDAY', 'NIGHT')"
check_sql_accepted K3 "the same weekday on another dependent" \
  "insert into dependent_school_day (dependent_id, weekday, school_shift) select id, 'SATURDAY', 'MORNING' from dependent where token = '$LUCAS'
   union all select id, 'SATURDAY', 'MORNING' from dependent where token = '$MARINA'"
check_sql_rejected K4 "a row without school_shift" '"school_shift"' \
  "insert into dependent_school_day (dependent_id, weekday) values ($LUCAS_ID, 'SUNDAY')"
check_sql_rejected K5 "a row for a dependent that does not exist" dependent_school_day_dependent_id_fkey \
  "insert into dependent_school_day (dependent_id, weekday, school_shift) values (-1, 'SUNDAY', 'MORNING')"
check K6 "the table, weekday and school_shift are documented" 3 \
  "$(sql "select (obj_description('dependent_school_day'::regclass, 'pg_class') is not null)::int + count(col_description('dependent_school_day'::regclass, attnum)) from pg_attribute where attrelid = 'dependent_school_day'::regclass and attname in ('weekday', 'school_shift')")"
check K7 "the checks above were rolled back" "$LUCAS_GRID" "$(grid_of "$LUCAS")"
check K8 "V62 dropped dependent.shift" 0 \
  "$(sql "select count(*) from information_schema.columns where table_name = 'dependent' and column_name = 'shift'")"

table "Contract readiness"
READINESS=/api/clients/me/contract-readiness
DEPENDENT_FIELDS='[.missing[] | select(.subject == "DEPENDENT") | .dependentToken + ":" + .field]'
CLIENT_FIELDS='[.missing[] | select(.subject == "CLIENT") | .field]'
ANA_USER="(select id from users where email = 'ana.souza@seed.vanep.com.br')"
# The seed makes Ana ready to ask for a quote; these scenarios start from a client without phone and address.
sql "update users set phone = null, address_id = null where id = $ANA_USER" >/dev/null
request GET "$READINESS?dependentTokens=$LUCAS&dependentTokens=$MARINA" "$ANA"
expect C1 "the seeded client misses phone and address; the restored dependent misses its address" 200 \
  ".ready == false and $CLIENT_FIELDS == [\"PHONE\", \"ADDRESS\"] and $DEPENDENT_FIELDS == [\"$MARINA:ADDRESS\"]"
expect_client_entries_without_token() { jq -e '[.missing[] | select(.subject == "CLIENT") | has("dependentToken")] | all(. == false)' <<<"$RESP_BODY" >/dev/null; }
check C2 "client entries carry no dependentToken" yes "$(expect_client_entries_without_token && echo yes || echo no)"
request GET "$READINESS?dependentTokens=$LUCAS,$MARINA" "$ANA"
expect C3 "the tokens separated by a comma give the same answer" 200 \
  "$DEPENDENT_FIELDS == [\"$MARINA:ADDRESS\"]"
request GET "$READINESS?dependentTokens=$MARINA&dependentTokens=$MARINA" "$ANA"
expect C4 "a token sent twice is checked once" 200 "$DEPENDENT_FIELDS == [\"$MARINA:ADDRESS\"]"
request GET "$READINESS" "$ANA"
expect C5 "without dependent tokens only the client is checked" 200 \
  "$CLIENT_FIELDS == [\"PHONE\", \"ADDRESS\"] and $DEPENDENT_FIELDS == []"
request PATCH "/api/dependent/$LUCAS" "$ANA" '{"birthDate": null}'
request GET "$READINESS?dependentTokens=$LUCAS" "$ANA"
expect C6 "clearing the birth date makes the dependent miss it" 200 "$DEPENDENT_FIELDS == [\"$LUCAS:BIRTH_DATE\"]"
request PATCH "/api/dependent/$LUCAS" "$ANA" '{"birthDate": "2016-04-02"}'
sql "insert into address (token, city_id, zip_code, street, number) select 'qaanahome', city_id, zip_code, street, '99' from address where id = (select address_id from dependent where token = '$LUCAS');
     update users set phone = '11999990000', address_id = (select id from address where token = 'qaanahome') where id = $ANA_USER" >/dev/null
request GET "$READINESS?dependentTokens=$LUCAS" "$ANA"
expect C7 "with phone, address and a complete dependent the client is ready" 200 '.ready == true and .missing == []'
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" '{"days": []}'
request GET "$READINESS?dependentTokens=$LUCAS" "$ANA"
expect C8 "clearing the grid makes the dependent miss the school days" 200 \
  ".ready == false and $DEPENDENT_FIELDS == [\"$LUCAS:SCHOOL_DAYS\"]"
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" "$LUCAS_GRID_BODY"
expect C9 "the client sets the grid back" 200
request GET "$READINESS?dependentTokens=$LUCAS" "$ANA"
expect C10 "and the dependent is ready again" 200 '.ready == true'

table "Contract readiness — misuse"
request GET "$READINESS?dependentTokens=$LUCAS" ""
expect C11 "no token" 401
request GET "$READINESS?dependentTokens=$LUCAS" "not-a-jwt"
expect C12 "invalid token" 401
request GET "$READINESS?dependentTokens=$LUCAS" "$GUSTAVO"
expect C13 "a driver cannot ask" 403
request GET "$READINESS" "$ADMIN"
expect C14 "the admin is not a client" 403
request GET "$READINESS?dependentTokens=$LUCAS" "$BRUNO"
expect C15 "another client's dependent" 404
request GET "$READINESS?dependentTokens=$LUCAS&dependentTokens=doesnotexist" "$ANA"
expect C16 "an unknown token among known ones" 404
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Rafa Souza"}')"
REMOVED_KID=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/dependent/$REMOVED_KID" "$ANA"
expect C17 "the client removes a dependent" 204
request GET "$READINESS?dependentTokens=$REMOVED_KID" "$ANA"
expect C18 "a removed dependent" 404

table "CPF of a dependent"
DEPENDENTS_BEFORE=$(count dependent)
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Cpf Souza", document: "52998224724"}')"
expect F1 "a CPF with wrong check digits on creation" 400
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Cpf Souza", document: "11111111111"}')"
expect F2 "a CPF of repeated digits on creation" 400
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Cpf Souza", document: "5299822472"}')"
expect F3 "a CPF one digit short" 400
check F4 "the refused creations stored nothing" "$DEPENDENTS_BEFORE" "$(count dependent)"
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Cpf Souza", document: "529.982.247-25"}')"
expect F5 "a masked CPF is stored as digits" 201 '.document == "52998224725"'
CPF_KID=$(jq -r .token <<<"$RESP_BODY")
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Cpf Copia", document: "52998224725"}')"
expect F6 "the same CPF without the mask is a duplicate" 409 '.detail == "Já existe um dependente cadastrado com este documento."'
request PATCH "/api/dependent/$CPF_KID" "$ANA" '{"document": "39053344704"}'
expect F7 "an invalid CPF on update" 400
check F8 "the refused update kept the stored CPF" 52998224725 "$(sql "select document from dependent where token = '$CPF_KID'")"
request PATCH "/api/dependent/$CPF_KID" "$ANA" '{"document": null}'
expect F9 "document null clears the CPF" 200 '.document == null'
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Sem Cpf", document: ""}')"
expect F10 "an empty document is stored as no document" 201 '.document == null'
EMPTY_DOC_KID=$(jq -r .token <<<"$RESP_BODY")
for kid in "$CPF_KID" "$EMPTY_DOC_KID"; do request DELETE "/api/dependent/$kid" "$ANA"; done
expect F11 "the client removes the dependents created to check the CPF" 204

table "School address on resolve (D21)"
NO_DISTRICT='(has("district") | not)'
NO_DISTRICT_FIELDS='(has("districtName") | not) and (has("districtToken") | not)'
request POST /api/schools/resolve "$ANA" '{"placeId": "sp-escola-bandeirantes"}'
expect N1 "a new school gets the address of its place" 201 \
  ".address.street == \"Rua Estela\" and .address.number == \"268\" and .address.zipCode == \"04011001\"
   and .address.neighborhood == \"Vila Mariana\" and .address.cityName == \"São Paulo\" and (.address | $NO_DISTRICT)"
BANDEIRANTES=$(jq -r .token <<<"$RESP_BODY")
BANDEIRANTES_ADDRESS=$(jq -r .address.token <<<"$RESP_BODY")
request POST /api/schools/resolve "$ANA" '{"placeId": "df-escola-objetivo"}'
expect N2 "a place with no route takes its deepest subdivision as the street and its shallowest sublocality as the neighborhood, and the school has no districtName nor districtToken" 201 \
  ".address.street == \"QI 21\" and .address.neighborhood == \"Taguatinga Norte\" and .address.zipCode == \"72145450\" and $NO_DISTRICT_FIELDS"
request POST /api/schools/resolve "$ANA" '{"placeId": "sp-escola-bandeirantes"}'
expect N3 "resolving the school again keeps its address" 200 \
  ".token == \"$BANDEIRANTES\" and .address.token == \"$BANDEIRANTES_ADDRESS\""
check N4 "no second address was stored for the school" 1 \
  "$(sql "select count(*) from address where google_place_id = (select google_place_id from school where token = '$BANDEIRANTES')")"
sql "update address set deleted_at = now() where token = '$BANDEIRANTES_ADDRESS';
     update school set address_id = null, google_place_id = 'sp-escola-bandeirantes' where token = '$BANDEIRANTES'" >/dev/null
request POST /api/schools/resolve "$ANA" '{"placeId": "sp-escola-bandeirantes", "sessionToken": "qa-session"}'
expect N5 "a known school without an address is completed on resolve" 200 \
  ".token == \"$BANDEIRANTES\" and .address.street == \"Rua Estela\" and .address.token != \"$BANDEIRANTES_ADDRESS\""
check N6 "the school now points to the new address" "$(jq -r .address.token <<<"$RESP_BODY")" \
  "$(sql "select address.token from school join address on address.id = school.address_id where school.token = '$BANDEIRANTES'")"
request POST /api/dependent "$ANA" "$(jq -nc --arg s "$BANDEIRANTES" '{name: "Escola Souza", schoolToken: $s}')"
expect N7 "the dependent's school comes with its address" 201 \
  ".school.token == \"$BANDEIRANTES\" and .school.address.street == \"Rua Estela\" and .school.address.neighborhood == \"Vila Mariana\""
SCHOOL_KID=$(jq -r .token <<<"$RESP_BODY")
request GET /api/dependent "$ANA"
expect N8 "the dependent list carries the school address" 200 \
  "any(.[]; .token == \"$SCHOOL_KID\" and .school.address.street == \"Rua Estela\")"
request DELETE "/api/dependent/$SCHOOL_KID" "$ANA"
expect N9 "the client removes the dependent created for the school" 204

table "Neighborhood instead of district (D20)"
request GET "/api/dependent/$LUCAS" "$ANA"
expect N10 "the dependent address has a neighborhood and no district" 200 \
  ".address.neighborhood == \"Centro\" and (.address | $NO_DISTRICT)"
request GET /api/user/me/address "$ANA"
expect N11 "the personal address has no districtName nor districtToken" 200 \
  "has(\"neighborhood\") and $NO_DISTRICT_FIELDS"
SEED_CONTRACT=$(sql "select contract.token from contract join contract_item item on item.contract_id = contract.id order by contract.id limit 1")
request GET "/api/contracts/$SEED_CONTRACT" "$ADMIN"
expect N12 "the contract item shows the pickup neighborhood and no pickupDistrictName" 200 \
  '.items[0].pickupNeighborhood == "Centro" and (.items[0] | has("pickupDistrictName") | not)'
check N13 "V63 removed address.district_id, contract_item.pickup_district_id and school.district_id" 0 \
  "$(sql "select count(*) from information_schema.columns where (table_name, column_name) in (('address', 'district_id'), ('contract_item', 'pickup_district_id'), ('school', 'district_id'))")"
request GET "/api/dependent/$LUCAS" "$ANA"
expect N14 "the seeded school comes with an address of its own, in its city" 200 \
  '.school.address.street == "Rua Vergueiro" and .school.address.neighborhood == "Liberdade"
   and .school.address.cityName == "São Paulo" and .school.address.token != .address.token'

table "V63 re-applied on rows that pointed to a district"
SEED_ITEM_NEIGHBORHOOD=$(sql "select pickup_neighborhood from contract_item where id = (select min(id) from contract_item)")
sql "alter table address add column district_id bigint references district (id);
     alter table contract_item add column pickup_district_id bigint references district (id);
     alter table school add column district_id bigint references district (id);
     insert into district (token, city_id, name, normalized_name) select 'qadistrict', city_id, 'Setor L Norte', 'setor l norte' from address where token = '$BANDEIRANTES_ADDRESS';
     insert into address (token, city_id, street, district_id) select 'qaaddrnull', city_id, 'Rua Sem Bairro', (select id from district where token = 'qadistrict') from address where token = '$BANDEIRANTES_ADDRESS';
     insert into address (token, city_id, street, neighborhood, district_id) select 'qaaddrkeep', city_id, 'Rua Com Bairro', 'Centro', (select id from district where token = 'qadistrict') from address where token = '$BANDEIRANTES_ADDRESS';
     insert into address (token, city_id, street) select 'qaaddrschool', city_id, 'Rua da Escola' from address where token = '$BANDEIRANTES_ADDRESS';
     insert into school (token, name, city_id, address_id, district_id) select 'qaschool', 'Escola QA', city_id, id, (select id from district where token = 'qadistrict') from address where token = 'qaaddrschool';
     update contract_item set pickup_neighborhood = null, pickup_district_id = (select id from district where token = 'qadistrict') where id = (select min(id) from contract_item)" >/dev/null
apply_migration V63
check H1 "an address without a neighborhood takes the name of its district" "Setor L Norte" \
  "$(sql "select neighborhood from address where token = 'qaaddrnull'")"
check H2 "an address with a neighborhood keeps it" Centro "$(sql "select neighborhood from address where token = 'qaaddrkeep'")"
check H3 "a contract item without a pickup neighborhood takes the name of its district" "Setor L Norte" \
  "$(sql "select pickup_neighborhood from contract_item where id = (select min(id) from contract_item)")"
check H4 "a school address without a neighborhood takes the name of the school's district" "Setor L Norte" \
  "$(sql "select neighborhood from address where token = 'qaaddrschool'")"
check H5 "the three columns are gone again" 0 \
  "$(sql "select count(*) from information_schema.columns where (table_name, column_name) in (('address', 'district_id'), ('contract_item', 'pickup_district_id'), ('school', 'district_id'))")"
sql "update contract_item set pickup_neighborhood = '$SEED_ITEM_NEIGHBORHOOD' where id = (select min(id) from contract_item);
     delete from school where token = 'qaschool';
     delete from address where token in ('qaaddrnull', 'qaaddrkeep', 'qaaddrschool');
     delete from district where token = 'qadistrict'" >/dev/null
request GET "/api/contracts/$SEED_CONTRACT" "$ADMIN"
expect H6 "the API reads the contract after the migration ran again" 200 '.items[0].pickupNeighborhood == "Centro"'


table "V64: service_request tables (database checks)"
insert_request() {
  echo "insert into service_request (token, client_driver_id, status, desired_start_date, expires_at)
        select '$1', id, '$3', date '2027-02-01', now() + interval '72 hours' from client_driver where token = '$2'"
}
insert_item() {
  echo "insert into service_request_item (service_request_id, dependent_id, school_id, pickup_city_id, snapshot)
        select request.id, dependent.id, dependent.school_id, address.city_id, $QA_SNAPSHOT
        from service_request request, dependent join address on address.id = dependent.address_id
        where request.token = '$1' and dependent.token = '${2:-$LUCAS}'"
}
QA_SNAPSHOT="jsonb_build_object('version', 1, 'dependent', jsonb_build_object('name', dependent.name,
  'birthDate', dependent.birth_date), 'school', jsonb_build_object('name', 'QA'), 'pickup', jsonb_build_object('cityName', 'QA'))"
insert_day() {
  echo "insert into service_request_day (service_request_item_id, weekday, school_shift, needs_outbound, needs_return)
        select item.id, '$2', 'MORNING', $3, $4 from service_request_item item
        join service_request request on request.id = item.service_request_id where request.token = '$1'"
}
REQUESTS_BEFORE=$(count service_request)
check_sql_rejected Q1 "a second OPEN request of the same pair" service_request_client_driver_open_key \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN); $(insert_request qasr2 "$DIEGO_GUSTAVO" OPEN)"
check_sql_rejected Q2 "an OPEN request while the pair has an ANSWERED one" service_request_client_driver_open_key \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" ANSWERED); $(insert_request qasr2 "$DIEGO_GUSTAVO" OPEN)"
check_sql_accepted Q3 "an OPEN request after an EXPIRED one of the same pair" \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" EXPIRED); $(insert_request qasr2 "$DIEGO_GUSTAVO" OPEN)"
check_sql_accepted Q4 "OPEN requests of two different pairs" \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN); $(insert_request qasr2 "$BRUNO_HELENA" OPEN)"
check_sql_rejected Q5 "a repeated token" service_request_token_key \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" EXPIRED); $(insert_request qasr1 "$BRUNO_HELENA" OPEN)"
check_sql_rejected Q6 "the same weekday twice in one item" service_request_day_weekday_key \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN); $(insert_item qasr1);
   $(insert_day qasr1 MONDAY true true); $(insert_day qasr1 MONDAY true false)"
check_sql_rejected Q7 "a day with neither outbound nor return" service_request_day_leg_check \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN); $(insert_item qasr1); $(insert_day qasr1 MONDAY false false)"
check_sql_accepted Q8 "a day with only the outbound and a day with only the return" \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN); $(insert_item qasr1);
   $(insert_day qasr1 MONDAY true false); $(insert_day qasr1 TUESDAY false true)"
check Q9 "the item keeps no district id" 0 \
  "$(sql "select count(*) from information_schema.columns where table_name = 'service_request_item' and column_name = 'pickup_district_id'")"
check_sql_rejected Q10 "an item for a dependent that does not exist" service_request_item_dependent_id_fkey \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN);
   insert into service_request_item (service_request_id, dependent_id, school_id, pickup_city_id, snapshot)
   select id, -1, (select min(id) from school), (select min(id) from city),
     '{\"version\": 1, \"dependent\": {}, \"school\": {}, \"pickup\": {}}' from service_request where token = 'qasr1'"
check Q11 "the overdue sweep reads the partial index" yes \
  "$(sql "set enable_seqscan = off; explain select id from service_request where status = 'OPEN' and expires_at <= now()" \
       | grep -q service_request_open_expires_at_idx && echo yes || echo no)"
check Q12 "the tables, the status and the copied and per-day columns are documented" 8 \
  "$(sql "select (select count(*) from unnest(array['service_request', 'service_request_item', 'service_request_day']) name
                  where obj_description(name::regclass, 'pg_class') is not null)
             + (select count(*) from (values ('service_request', 'status'), ('service_request_item', 'snapshot'), ('service_request_item', 'school_id'),
                  ('service_request_day', 'school_shift'), ('service_request_day', 'weekday')) column_name (tbl, col)
                where col_description(tbl::regclass, (select attnum from pg_attribute where attrelid = tbl::regclass and attname = col)) is not null)")"
check Q13 "the checks above were rolled back" "$REQUESTS_BEFORE" "$(count service_request)"
check Q14 "the negotiation tables have no deleted_at" 0 \
  "$(sql "select count(*) from information_schema.columns where table_name like 'service_request%' and column_name = 'deleted_at'")"
check_sql_rejected Q15 "a snapshot without the dependent" service_request_item_snapshot_check \
  "$(insert_request qasr1 "$DIEGO_GUSTAVO" OPEN);
   insert into service_request_item (service_request_id, dependent_id, school_id, pickup_city_id, snapshot)
   select request.id, dependent.id, dependent.school_id, address.city_id,
     '{\"version\": 1, \"school\": {}, \"pickup\": {}}'
   from service_request request, dependent join address on address.id = dependent.address_id
   where request.token = 'qasr1' and dependent.token = '$LUCAS'"
table "V65: proposal tables (database checks)"
insert_proposal() {
  echo "insert into proposal (token, client_driver_id, service_request_id, version, status, valid_until, starts_on, ends_on,
          total_amount, installments, due_day, adjustment_policy, cancellation_policy, vehicle_id, snapshot)
        select '$1', request.client_driver_id, request.id, $4, '$3', now() + interval '7 days', date '2027-02-01',
          date '2027-12-17', 4200, 12, 5, 'NONE', 'NOTICE_30_DAYS', vehicle.id,
          jsonb_build_object('version', 1, 'vehicle', jsonb_build_object('plate', vehicle.plate, 'capacity', vehicle.capacity))
        from service_request request, (select * from vehicle order by id limit 1) vehicle where request.token = '$2'"
}
insert_proposal_item() {
  echo "insert into schedule default values;
        insert into proposal_item (proposal_id, dependent_id, monthly_amount, schedule_id)
        select proposal.id, (select id from dependent where token = '$LUCAS'), $2, (select max(id) from schedule)
        from proposal where token = '$1'"
}
insert_adjustment() {
  echo "insert into proposal_adjustment (proposal_id, field, suggested_value, note)
        select id, 'PRICE', $2, $3 from proposal where token = '$1'"
}
ANSWERED_REQUEST="$(insert_request qasr1 "$DIEGO_GUSTAVO" ANSWERED)"
OTHER_REQUEST="$(insert_request qasr2 "$BRUNO_HELENA" CONVERTED)"
SENT_PROPOSAL="$ANSWERED_REQUEST; $(insert_proposal qap1 qasr1 SENT 1)"
on_qap1() { echo "update proposal set $1 where token = 'qap1'"; }
PROPOSALS_BEFORE=$(count proposal)
check_sql_rejected P1 "a second SENT proposal of the same pair" proposal_client_driver_sent_key \
  "$SENT_PROPOSAL; $(insert_proposal qap2 qasr1 SENT 2)"
check_sql_accepted P2 "a SENT version after a SUPERSEDED one of the same request" \
  "$ANSWERED_REQUEST; $(insert_proposal qap1 qasr1 SUPERSEDED 1); $(insert_proposal qap2 qasr1 SENT 2)"
check_sql_accepted P3 "SENT proposals of two different pairs" \
  "$SENT_PROPOSAL; $(insert_request qasr2 "$BRUNO_HELENA" ANSWERED); $(insert_proposal qap2 qasr2 SENT 1)"
check_sql_rejected P4 "a repeated version in the same request" proposal_request_version_key \
  "$ANSWERED_REQUEST; $(insert_proposal qap1 qasr1 SUPERSEDED 1); $(insert_proposal qap2 qasr1 SENT 1)"
check_sql_rejected P5 "ACCEPTED without accepted_at" proposal_accepted_at_check \
  "$ANSWERED_REQUEST; $(insert_proposal qap1 qasr1 ACCEPTED 1)"
check_sql_rejected P6 "accepted_at on a SENT proposal" proposal_accepted_at_check \
  "$SENT_PROPOSAL; $(on_qap1 "accepted_at = now()")"
check_sql_accepted P7 "ACCEPTED with accepted_at" \
  "$SENT_PROPOSAL; $(on_qap1 "status = 'ACCEPTED', accepted_at = now()")"
check_sql_rejected P8 "a resulting contract on a proposal that is not ACCEPTED" proposal_resulting_contract_check \
  "$SENT_PROPOSAL; $(on_qap1 "resulting_contract_id = (select min(id) from contract)")"
check_sql_rejected P9 "the same resulting contract on two proposals" proposal_resulting_contract_key \
  "$SENT_PROPOSAL; $(on_qap1 "status = 'ACCEPTED', accepted_at = now(), resulting_contract_id = (select min(id) from contract)");
   $OTHER_REQUEST; $(insert_proposal qap2 qasr2 SENT 1);
   update proposal set status = 'ACCEPTED', accepted_at = now(), resulting_contract_id = (select min(id) from contract) where token = 'qap2'"
check_sql_accepted P10 "every numeric range at its limits" \
  "$SENT_PROPOSAL; $(insert_proposal_item qap1 0.01);
   $(on_qap1 "installments = 1, due_day = 1"); $(on_qap1 "installments = 12, due_day = 28");
   $(on_qap1 "total_amount = 0.01, enrollment_fee = 0, ends_on = starts_on + 1")"
check_sql_rejected P11 "version 0" proposal_version_check "$ANSWERED_REQUEST; $(insert_proposal qap1 qasr1 SENT 0)"
check_sql_rejected P12 "installments 0" proposal_installments_check "$SENT_PROPOSAL; $(on_qap1 "installments = 0")"
check_sql_rejected P13 "installments 13" proposal_installments_check "$SENT_PROPOSAL; $(on_qap1 "installments = 13")"
check_sql_rejected P14 "due day 0" proposal_due_day_check "$SENT_PROPOSAL; $(on_qap1 "due_day = 0")"
check_sql_rejected P15 "due day 29" proposal_due_day_check "$SENT_PROPOSAL; $(on_qap1 "due_day = 29")"
check_sql_rejected P16 "total amount 0" proposal_total_amount_check "$SENT_PROPOSAL; $(on_qap1 "total_amount = 0")"
check_sql_rejected P17 "enrollment fee -0.01" proposal_enrollment_fee_check "$SENT_PROPOSAL; $(on_qap1 "enrollment_fee = -0.01")"
check_sql_rejected P18 "ends_on equal to starts_on" proposal_period_check "$SENT_PROPOSAL; $(on_qap1 "ends_on = starts_on")"
check_sql_rejected P19 "monthly amount 0" proposal_item_monthly_amount_check "$SENT_PROPOSAL; $(insert_proposal_item qap1 0)"
check_sql_rejected P20 "two items on the same schedule" proposal_item_schedule_id_key \
  "$SENT_PROPOSAL; $(insert_proposal_item qap1 350);
   insert into proposal_item (proposal_id, dependent_id, monthly_amount, schedule_id)
   select proposal_id, dependent_id, 300, schedule_id from proposal_item where proposal_id = (select id from proposal where token = 'qap1')"
check_sql_rejected P21 "an adjustment with neither value nor note" proposal_adjustment_content_check \
  "$SENT_PROPOSAL; $(insert_adjustment qap1 null null)"
check_sql_accepted P22 "an adjustment with only a value and one with only a note" \
  "$SENT_PROPOSAL; $(insert_adjustment qap1 "'320,00'" null); $(insert_adjustment qap1 null "'Só ida às quartas'")"
# The empty table gives the planner no reason to prefer one SENT index over the other, so the
# pair index is set aside inside a rolled-back transaction.
check P23 "the overdue sweep can read the partial index on valid_until" yes \
  "$(docker exec -i "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$QA_DB_NAME" -Atq <<<"begin; set local enable_seqscan = off;
       drop index proposal_client_driver_sent_key;
       explain select id from proposal where status = 'SENT' and valid_until <= now() order by valid_until, id; rollback;" \
       | grep -q "Index Scan using proposal_sent_valid_until_idx" && echo yes || echo no)"
check P24 "the tables, the status, the snapshot, both contract links and both menus are documented" 10 \
  "$(sql "select (select count(*) from unnest(array['proposal', 'proposal_item', 'proposal_adjustment']) name
                  where obj_description(name::regclass, 'pg_class') is not null)
             + (select count(*) from unnest(array['status', 'snapshot', 'adjustment_policy', 'cancellation_policy',
                  'source_contract_id', 'resulting_contract_id']) col
                where col_description('proposal'::regclass, (select attnum from pg_attribute
                  where attrelid = 'proposal'::regclass and attname = col)) is not null)
             + (select count(*) from pg_attribute where attrelid = 'proposal_adjustment'::regclass
                  and attname = 'suggested_value' and col_description(attrelid, attnum) is not null)")"
check P25 "the checks above were rolled back" "$PROPOSALS_BEFORE" "$(count proposal)"
check P26 "the proposal tables have no deleted_at" 0 \
  "$(sql "select count(*) from information_schema.columns where table_name like 'proposal%' and column_name = 'deleted_at'")"
check_sql_rejected P27 "a snapshot without the vehicle" proposal_snapshot_check \
  "$SENT_PROPOSAL; $(on_qap1 "snapshot = snapshot - 'vehicle'")"

table "Seeded service request"
SEED_REQUEST=$(sql "select token from service_request where token not like 'qa%' order by id limit 1")
check S1 "the seed sent one request from Ana to Gustavo, answered by the seeded proposal, on a PENDING link from the search" \
  "ANSWERED PENDING CLIENT_SEARCH" \
  "$(sql "select request.status || ' ' || link.status || ' ' || link.origin from service_request request
          join client_driver link on link.id = request.client_driver_id join driver on driver.id = link.driver_id
          where request.token = '$SEED_REQUEST' and driver.token = '$GUSTAVO_D'")"
check S2 "its item copies Lucas, his school, his city and his neighborhood" \
  "Lucas Souza|2016-04-02|Escola Municipal Seed|São Paulo|SP|Centro" \
  "$(sql "select concat_ws('|', snapshot->'dependent'->>'name', snapshot->'dependent'->>'birthDate', snapshot->'school'->>'name',
            snapshot->'pickup'->>'cityName', snapshot->'pickup'->>'stateUf', coalesce(snapshot->'pickup'->>'neighborhood', ''))
          from service_request_item item join service_request request on request.id = item.service_request_id
          where request.token = '$SEED_REQUEST'")"
check S3 "the days keep the grid of the sending time, although the grid changed since" \
  "MONDAY:MORNING,TUESDAY:FULLTIME,WEDNESDAY:MORNING,THURSDAY:FULLTIME,FRIDAY:MORNING" \
  "$(sql "select string_agg(day.weekday || ':' || day.school_shift, ',' order by array_position(
            array['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'], day.weekday::text))
          from service_request_day day join service_request_item item on item.id = day.service_request_item_id
          join service_request request on request.id = item.service_request_id where request.token = '$SEED_REQUEST'")"
check S4 "every seeded day asks for the way to school and back" t \
  "$(sql "select bool_and(day.needs_outbound and day.needs_return) from service_request_day day
          join service_request_item item on item.id = day.service_request_item_id
          join service_request request on request.id = item.service_request_id where request.token = '$SEED_REQUEST'")"
check S5 "it expires 72 hours after it was sent" 72 \
  "$(sql "select round(extract(epoch from expires_at - created_at) / 3600) from service_request where token = '$SEED_REQUEST'")"
request DELETE "/api/dependent/$LUCAS" "$ANA"
expect S6 "Lucas is not removed while the seeded request is open" 422 \
  '.code == "dependent.open_negotiation" and .detail == "Este dependente está numa solicitação de orçamento em aberto. Retire a solicitação antes de removê-lo."'
SEED_PROPOSAL="(select proposal.id from proposal join service_request request on request.id = proposal.service_request_id
                where request.token = '$SEED_REQUEST')"
check S7 "the seed answered it with one SENT version 1, without a parent, on the same link" \
  "1 SENT 1 true true" \
  "$(sql "select count(*) || ' ' || min(proposal.status) || ' ' || min(proposal.version) || ' '
            || bool_and(proposal.parent_proposal_id is null) || ' ' || bool_and(proposal.client_driver_id = request.client_driver_id)
          from proposal join service_request request on request.id = proposal.service_request_id where request.token = '$SEED_REQUEST'")"
check S8 "it offers Gustavo's van, copied with its plate and capacity" \
  "VAN2B34 true true" \
  "$(sql "select proposal.snapshot->'vehicle'->>'plate' || ' ' || (vehicle.plate = proposal.snapshot->'vehicle'->>'plate') || ' '
            || ((proposal.snapshot->'vehicle'->>'capacity')::int = vehicle.capacity)
          from proposal join vehicle on vehicle.id = proposal.vehicle_id where proposal.id = $SEED_PROPOSAL")"
check S9 "one item for Lucas, with the schedule built from the requested grid" \
  "1 10 MONDAY:OUTBOUND:06:40,MONDAY:RETURN:12:10,TUESDAY:OUTBOUND:06:40,TUESDAY:RETURN:17:20" \
  "$(sql "select count(distinct item.id) || ' ' || count(slot.id) || ' ' || string_agg(slot.weekday || ':' || slot.leg || ':' || to_char(slot.window_start, 'HH24:MI'), ','
            order by array_position(array['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], slot.weekday::text), slot.leg)
            filter (where slot.weekday in ('MONDAY', 'TUESDAY'))
          from proposal_item item join schedule_slot slot on slot.schedule_id = item.schedule_id
          where item.proposal_id = $SEED_PROPOSAL and item.dependent_id = (select id from dependent where token = '$LUCAS')")"
check S10 "valid for seven days, ten installments of the item's monthly amount" \
  "7 10 4200.00 420.00" \
  "$(sql "select round(extract(epoch from proposal.valid_until - proposal.created_at) / 86400) || ' ' || proposal.installments || ' '
            || proposal.total_amount || ' ' || item.monthly_amount
          from proposal join proposal_item item on item.proposal_id = proposal.id where proposal.id = $SEED_PROPOSAL")"
check S11 "the period starts on the desired start and stays within twelve months" "true true" \
  "$(sql "select (proposal.starts_on = request.desired_start_date) || ' ' || (proposal.ends_on <= proposal.starts_on + interval '12 months')
          from proposal join service_request request on request.id = proposal.service_request_id where proposal.id = $SEED_PROPOSAL")"
check S12 "no contract came from it, and the link did not change" "0 PENDING" \
  "$(sql "select count(contract.id) || ' ' || min(link.status) from proposal join client_driver link on link.id = proposal.client_driver_id
          left join contract on contract.client_driver_id = link.id where proposal.id = $SEED_PROPOSAL")"
SEED_PROPOSAL_TOKEN=$(sql "select token from proposal where id = $SEED_PROPOSAL")
SEED_STARTS_ON=$(sql "select starts_on from proposal where id = $SEED_PROPOSAL")
SEED_SUMMARY="(.token == \"$SEED_PROPOSAL_TOKEN\" and .version == 1 and .status == \"SENT\" and .totalAmount == 4200
               and .installments == 10 and .installmentAmount == 420 and .validUntil != null
               and .startsOn == \"$SEED_STARTS_ON\" and .hasPendingAdjustment == false)"
request GET "/api/clients/me/service-requests?status=ANSWERED" "$ANA"
expect S13 "Ana's list carries the seeded proposal as the request's current one: ten installments of 420.00, no adjustment pending" 200 \
  ".totalElements == 1 and .content[0].token == \"$SEED_REQUEST\" and (.content[0].currentProposal | $SEED_SUMMARY)"
request GET "/api/drivers/me/service-requests/$SEED_REQUEST" "$GUSTAVO"
expect S14 "Gustavo reads the same summary in the detail" 200 "(.currentProposal | $SEED_SUMMARY)"

table "Dependent locks (D17)"
ANA_C=$(client_token ana.souza)
ANA_FABIO=$(sql "select link.token from client_driver link join client on client.id = link.client_id join driver on driver.id = link.driver_id
                 where client.token = '$ANA_C' and driver.token = '$FABIO_D'")
SEED_SCHOOL=$(sql "select token from school where name = 'Escola Municipal Seed' order by id limit 1")
SEED_CITY=$(sql "select token from city where ibge_code = '3550308'")
OPEN_CONTRACT_DETAIL="Este dependente tem um contrato em andamento: ele não pode ser removido nem mudar de escola, endereço ou grade escolar."
request POST /api/dependent "$ANA" "$(jq -nc '{name: "Rafa Souza", birthDate: "2018-05-10"}')"
RAFA=$(jq -r .token <<<"$RESP_BODY")
sql "insert into address (token, city_id, zip_code, street, number)
       select 'qarafahome', city_id, zip_code, street, '12' from address where id = (select address_id from dependent where token = '$LUCAS');
     update dependent set school_id = (select school_id from dependent where token = '$LUCAS'),
       address_id = (select id from address where token = 'qarafahome') where token = '$RAFA'" >/dev/null
request PUT "/api/dependent/$RAFA/school-days" "$ANA" "$(school_days_body MONDAY:MORNING TUESDAY:MORNING)"
expect T1 "a new dependent gets school, address and grid" 200
sql "$(insert_request qalock "$ANA_FABIO" OPEN); $(insert_item qalock "$RAFA"); $(insert_day qalock MONDAY true true)" >/dev/null
request DELETE "/api/dependent/$RAFA" "$ANA"
expect T2 "a dependent in an OPEN request is not removed" 422 \
  '.code == "dependent.open_negotiation" and .detail == "Este dependente está numa solicitação de orçamento em aberto. Retire a solicitação antes de removê-lo."'
request PUT "/api/dependent/$RAFA/school-days" "$ANA" "$(school_days_body MONDAY:MORNING TUESDAY:FULLTIME)"
expect T3 "during the negotiation the grid still changes (the accept re-checks it)" 200
sql "update service_request set status = 'ANSWERED' where token = 'qalock'" >/dev/null
request DELETE "/api/dependent/$RAFA" "$ANA"
expect T4 "a dependent in an ANSWERED request is not removed" 422 '.code == "dependent.open_negotiation"'
sql "update service_request set status = 'WITHDRAWN' where token = 'qalock'" >/dev/null
RAFA_GRID=$(grid_of "$RAFA")
request POST /api/contracts "$ADMIN" "$(jq -nc --arg l "$ANA_FABIO" --arg d "$RAFA" '{clientDriverToken: $l, startsOn: "2027-02-01",
  endsOn: "2027-12-17", totalAmount: 3500, installments: 10, dueDay: 5, items: [{dependentToken: $d, monthlyAmount: 350,
  slots: [{weekday: "MONDAY", leg: "OUTBOUND", shift: "MORNING", windowStart: "06:40"}]}]}')"
expect T5 "after the request is withdrawn, the admin contracts the dependent" 201 '.status == "ACTIVE"'
RAFA_CONTRACT=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/dependent/$RAFA" "$ANA"
expect T6 "a dependent with an ACTIVE contract is not removed" 422 \
  ".code == \"dependent.open_contract\" and .detail == \"$OPEN_CONTRACT_DETAIL\""
request DELETE "/api/dependent/$RAFA" "$ADMIN"
expect T7 "not even by the admin" 422 '.code == "dependent.open_contract"'
request PATCH "/api/dependent/$RAFA" "$ANA" "$(jq -nc --arg s "$SEED_SCHOOL" '{schoolToken: $s}')"
expect T8 "the school is locked, even resent with its current value" 422 '.code == "dependent.open_contract"'
request PATCH "/api/dependent/$RAFA" "$ANA" "$(jq -nc --arg c "$SEED_CITY" \
  '{address: {cityToken: $c, street: "Rua Nova", number: "1", zipCode: "01001000", neighborhood: "Centro"}}')"
expect T9 "the address is locked" 422 '.code == "dependent.open_contract"'
request PATCH "/api/dependent/$RAFA" "$ANA" '{"address": null}'
expect T10 "clearing the address is locked too" 422 '.code == "dependent.open_contract"'
request PUT "/api/dependent/$RAFA/school-days" "$ANA" "$(school_days_body MONDAY:AFTERNOON)"
expect T11 "the grid is locked" 422 '.code == "dependent.open_contract"'
request PATCH "/api/dependent/$RAFA" "$ANA" '{"name": "Rafael Souza"}'
expect T12 "the name still changes" 200 '.name == "Rafael Souza"'
check T13 "the dependent kept its school, address and grid, and was not removed" \
  "t t t $RAFA_GRID" \
  "$(sql "select (school_id = (select school_id from dependent where token = '$LUCAS'))::text::char || ' ' ||
            (address_id = (select id from address where token = 'qarafahome'))::text::char || ' ' || (deleted_at is null)::text::char
          from dependent where token = '$RAFA'") $(grid_of "$RAFA")"
request GET "/api/contracts/$RAFA_CONTRACT" "$ADMIN"
expect T14 "the contract still lists the dependent's item" 200 ".items | map(.dependentToken) == [\"$RAFA\"]"
request PATCH "/api/contracts/$RAFA_CONTRACT" "$ADMIN" '{"status":"ENDED"}'
expect T15 "the contract ends" 200 '.status == "ENDED"'
check T16 "two removals at once after the contract ended: one 204, one 404" "204 404" \
  "$(in_parallel DELETE "$ANA" "" "/api/dependent/$RAFA" "/api/dependent/$RAFA")"
request GET "/api/contracts/$RAFA_CONTRACT" "$ADMIN"
expect T17 "the ended contract stays readable, without the removed dependent's item until phase 3 (R14)" 200 \
  '.status == "ENDED" and .items == []'
request DELETE "/api/dependent/$MARINA" "$BRUNO"
expect T18 "another client still cannot remove the dependent" 403
create_qa_driver() {
  sql "insert into users (token, type, name, email, document, verified, terms_accepted_at)
         values ('qauser$1', 'DRIVER', '$2', 'qa.$1@qa.vanep.com.br', 'qa$1', true, now());
       insert into driver (token, user_id, base_price, approval_status, is_available)
         select 'qadriver$1', id, 150, 'APPROVED', true from users where token = 'qauser$1'" >/dev/null
  echo "qadriver$1"
}

sr_item() {
  local dependent=$1
  shift
  jq -nc --arg d "$dependent" '{dependentToken: $d, days: [$ARGS.positional[] | split(":")
    | {weekday: .[0], needsOutbound: (.[1] == "1"), needsReturn: (.[2] == "1")}]}' --args "$@"
}

sr_body() {
  local driver=$1 notes=$2
  shift 2
  jq -nc --arg d "$driver" --arg n "$notes" --arg s "$START" \
    '{driverToken: $d, desiredStartDate: $s, notes: $n, items: [$ARGS.positional[] | fromjson]}' --args "$@"
}

request_rows() {
  sql "select (select count(*) from service_request) || ' ' || (select count(*) from client_driver)"
}

pair_links() {
  sql "select count(*) from client_driver link join client on client.id = link.client_id
       join driver on driver.id = link.driver_id where client.token = '$1' and driver.token = '$2'"
}

request_status() {
  sql "select status from service_request where token = '$1'"
}

route_statuses() {
  local token=$1 route body codes=()
  for route in "${SR_ROUTES[@]}"; do
    body=""
    if [[ $route == "POST $CLIENT_SR" ]]; then body=$VALID_BODY; fi
    request "${route%% *}" "${route#* }" "$token" "$body"
    codes+=("$RESP_STATUS")
  done
  echo "${codes[*]}"
}

send_request() {
  request POST "$CLIENT_SR" "$ANA" "$1"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | send a request | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

CLIENT_SR=/api/clients/me/service-requests
DRIVER_SR=/api/drivers/me/service-requests
START=$(sql "select current_date + 7")
YESTERDAY=$(sql "select (now() at time zone 'America/Sao_Paulo')::date - 1")
IGOR_D=$(create_qa_driver igor "Igor Prado")
JULIA_D=$(create_qa_driver julia "Julia Ramos")
KAIO_D=$(create_qa_driver kaio "Kaio Melo")
LARA_D=$(create_qa_driver lara "Lara Pires")
WEEK=(MONDAY:1:1 TUESDAY:1:1 WEDNESDAY:1:1 THURSDAY:1:1 FRIDAY:1:1)
LUCAS_WEEK=$(sr_item "$LUCAS" "${WEEK[@]}")
VALID_BODY=$(sr_body "$IGOR_D" "Portão azul" "$LUCAS_WEEK")
SR_ROUTES=("POST $CLIENT_SR" "GET $CLIENT_SR" "GET $CLIENT_SR/$SEED_REQUEST" "POST $CLIENT_SR/$SEED_REQUEST/withdraw"
  "GET $DRIVER_SR" "GET $DRIVER_SR/$SEED_REQUEST" "POST $DRIVER_SR/$SEED_REQUEST/decline")
NOT_OPEN='.detail == "Esta solicitação não está mais em aberto."'
NOT_READY='.detail == "Faltam dados que o contrato vai precisar. Complete o seu cadastro e o dos dependentes antes de pedir orçamento."'

table "Service requests over HTTP — misuse"
ROWS_BEFORE=$(request_rows)
check U1 "the seven routes without a token" "401 401 401 401 401 401 401" "$(route_statuses "")"
check U2 "the seven routes with an invalid token" "401 401 401 401 401 401 401" "$(route_statuses not-a-jwt)"
check U3 "the admin on both surfaces" "403 403 403 403 403 403 403" "$(route_statuses "$ADMIN")"
check U4 "a driver: the client routes are forbidden, another driver's request is not found" \
  "403 403 403 403 200 404 404" "$(route_statuses "$FABIO")"
check U5 "another client: Lucas and Ana's request are not found, the driver routes are forbidden" \
  "404 200 404 404 403 403 403" "$(route_statuses "$BRUNO")"
expect_bad_body() {
  request POST "$CLIENT_SR" "$ANA" "$3"
  expect "$1" "$2" 400 "${4-}"
}
expect_bad_body U6 "without driverToken" "$(jq -c 'del(.driverToken)' <<<"$VALID_BODY")"
expect_bad_body U7 "a blank driverToken" "$(jq -c '.driverToken = "  "' <<<"$VALID_BODY")"
expect_bad_body U8 "without desiredStartDate" "$(jq -c 'del(.desiredStartDate)' <<<"$VALID_BODY")"
expect_bad_body U9 "desiredStartDate yesterday" "$(jq -c --arg y "$YESTERDAY" '.desiredStartDate = $y' <<<"$VALID_BODY")"
expect_bad_body U10 "desiredStartDate that is not a date" "$(jq -c '.desiredStartDate = "amanhã"' <<<"$VALID_BODY")"
expect_bad_body U11 "notes with 1001 characters" "$(jq -c '.notes = ("a" * 1001)' <<<"$VALID_BODY")"
expect_bad_body U12 "an empty item list" "$(jq -c '.items = []' <<<"$VALID_BODY")"
expect_bad_body U13 "without items" "$(jq -c 'del(.items)' <<<"$VALID_BODY")"
expect_bad_body U14 "items that are not a list" "$(jq -c '.items = "Lucas"' <<<"$VALID_BODY")"
expect_bad_body U15 "an item with no days" "$(jq -c '.items[0].days = []' <<<"$VALID_BODY")"
expect_bad_body U16 "a blank dependentToken" "$(jq -c '.items[0].dependentToken = ""' <<<"$VALID_BODY")"
expect_bad_body U17 "a day without needsReturn" "$(jq -c 'del(.items[0].days[0].needsReturn)' <<<"$VALID_BODY")"
expect_bad_body U18 "needsOutbound that is not a boolean" "$(jq -c '.items[0].days[0].needsOutbound = "sim"' <<<"$VALID_BODY")"
expect_bad_body U19 "a weekday that does not exist" "$(jq -c '.items[0].days[0].weekday = "SEGUNDA"' <<<"$VALID_BODY")"
expect_bad_body U20 "malformed JSON" '{"driverToken": '
expect_bad_body U21 "the same dependent twice" "$(sr_body "$IGOR_D" "" "$LUCAS_WEEK" "$LUCAS_WEEK")" \
  '.detail == "O mesmo dependente não pode aparecer duas vezes na solicitação."'
expect_bad_body U22 "the same weekday twice in one item" "$(sr_body "$IGOR_D" "" "$(sr_item "$LUCAS" MONDAY:1:1 MONDAY:1:0)")" \
  '.detail == "O mesmo dia da semana não pode aparecer duas vezes para um dependente."'
expect_bad_body U23 "a day that needs no van" "$(sr_body "$IGOR_D" "" "$(sr_item "$LUCAS" MONDAY:0:0)")" \
  '.detail == "Cada dia pedido precisa da ida, da volta ou das duas."'
check U24 "nothing above created a request or a link, and the seeded request is still ANSWERED" \
  "$ROWS_BEFORE ANSWERED" "$(request_rows) $(request_status "$SEED_REQUEST")"

request POST "$CLIENT_SR" "$ANA" "$(sr_body unknown "" "$LUCAS_WEEK")"
expect U25 "an unknown driver" 404 '.detail == "Motorista não encontrado."'
put_under_review "$LARA_D"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$LARA_D" "" "$LUCAS_WEEK")"
expect U26 "a driver under review" 404 '.detail == "Motorista não encontrado."'
sql "update driver set approval_status = 'APPROVED', is_available = false where token = '$LARA_D'" >/dev/null
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$LARA_D" "" "$LUCAS_WEEK")"
expect U27 "a driver not taking new students" 422 '.detail == "Este motorista não está aceitando novos alunos no momento."'
restore_availability
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$IGOR_D" "" "$(sr_item unknown MONDAY:1:1)")"
expect U28 "an unknown dependent" 404 '.detail == "Dependente não encontrado."'
request POST /api/dependent "$BRUNO" '{"name": "Davi Lima"}'
DAVI=$(jq -r .token <<<"$RESP_BODY")
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$IGOR_D" "" "$(sr_item "$DAVI" MONDAY:1:1)")"
expect U29 "another client's dependent" 404 '.detail == "Dependente não encontrado."'
request POST "$CLIENT_SR" "$BRUNO" "$(sr_body "$IGOR_D" "" "$(sr_item "$DAVI" MONDAY:1:1)")"
expect U30 "a client without phone and address" 422 "$NOT_READY"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$IGOR_D" "" "$(sr_item "$MARINA" MONDAY:1:1)")"
expect U31 "a dependent without address" 422 "$NOT_READY"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$IGOR_D" "" "$(sr_item "$LUCAS" MONDAY:1:1 SATURDAY:1:1)")"
expect U32 "a day without school" 422 '.detail == "Um dos dias pedidos não está na grade escolar do dependente."'
sql "update client_driver set status = 'BLOCKED', blocked_at = now(),
       blocked_by = (select user_id from driver where token = '$FABIO_D') where token = '$ANA_FABIO'" >/dev/null
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D" "" "$LUCAS_WEEK")"
expect U33 "a blocked link" 422 '.detail == "Este motorista não pode receber solicitações suas."'
sql "update client_driver set status = 'INACTIVE', blocked_at = null, blocked_by = null where token = '$ANA_FABIO'" >/dev/null
insert_contract "$ANA_FABIO" ACTIVE qasrhttpcontract
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D" "" "$LUCAS_WEEK")"
expect U34 "a pair with an ACTIVE contract" 422 '.detail == "Você já tem um contrato em andamento com este motorista."'
sql "update contract set status = 'ENDED' where token = 'qasrhttpcontract'" >/dev/null
check U35 "no refusal created a request or a link" "$ROWS_BEFORE" "$(request_rows)"

table "Service requests over HTTP — client"
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" \
  "$(school_days_body MONDAY:MORNING TUESDAY:FULLTIME WEDNESDAY:MORNING THURSDAY:FULLTIME FRIDAY:MORNING)"
expect E1 "Lucas is full time on Tuesday and Thursday" 200
request PATCH "/api/dependent/$MARINA" "$ANA" "$(jq -nc --arg c "$SEED_CITY" \
  '{address: {cityToken: $c, street: "Rua das Flores", number: "20", zipCode: "01001000", neighborhood: "Vila Mariana"}}')"
expect E2 "Marina gets an address" 200
request PUT "/api/dependent/$MARINA/school-days" "$ANA" \
  "$(school_days_body MONDAY:MORNING TUESDAY:MORNING WEDNESDAY:MORNING THURSDAY:MORNING FRIDAY:MORNING)"
expect E3 "and mornings from Monday to Friday" 200
MARINA_BIRTH=$(sql "select birth_date from dependent where token = '$MARINA'")
LINKS_BEFORE=$(count client_driver)
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$HELENA_D" "Portão azul" "$LUCAS_WEEK" "$(sr_item "$MARINA" "${WEEK[@]}")")"
expect E4 "two siblings, Monday to Friday, both ways" 201 \
  ".status == \"OPEN\" and .driverToken == \"$HELENA_D\" and .notes == \"Portão azul\" and (.items | length) == 2
   and all(.items[]; (.days | length) == 5) and (.items | map(.dependentName)) == [\"Lucas Souza\", \"Marina Souza\"]
   and .items[1].dependentBirthDate == \"$MARINA_BIRTH\" and .items[1].pickupNeighborhood == \"Vila Mariana\"
   and .items[0].schoolName == \"Escola Municipal Seed\" and .items[0].pickupStateUf == \"SP\" and (has(\"id\") | not)"
SIBLINGS=$(jq -r .token <<<"$RESP_BODY")
check E5 "it expires 72 hours after it was sent, on a new PENDING link from the search" \
  "72 PENDING CLIENT_SEARCH $((LINKS_BEFORE + 1))" \
  "$(sql "select round(extract(epoch from request.expires_at - request.created_at) / 3600) || ' ' || link.status || ' ' || link.origin
          from service_request request join client_driver link on link.id = request.client_driver_id
          where request.token = '$SIBLINGS'") $(count client_driver)"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D" "" "$(sr_item "$LUCAS" "${WEEK[@]:0:2}" WEDNESDAY:1:0 "${WEEK[@]:3}")")"
expect E6 "full time on some days only, no return on Wednesday" 201 \
  '(.items[0].days | map(.weekday + ":" + .schoolShift + ":" + (.needsOutbound | tostring) + ":" + (.needsReturn | tostring)))
   == ["MONDAY:MORNING:true:true", "TUESDAY:FULLTIME:true:true", "WEDNESDAY:MORNING:true:false",
       "THURSDAY:FULLTIME:true:true", "FRIDAY:MORNING:true:true"]'
FABIO_REQUEST=$(jq -r .token <<<"$RESP_BODY")
check E7 "the ended pair's link is reused, with its origin" "$ANA_FABIO CLIENT_SEARCH $((LINKS_BEFORE + 1))" \
  "$(jq -r .clientDriverToken <<<"$RESP_BODY") $(link_origin "$ANA_FABIO") $(count client_driver)"
request PUT "/api/dependent/$LUCAS/school-days" "$ANA" \
  "$(school_days_body MONDAY:MORNING TUESDAY:FULLTIME WEDNESDAY:MORNING THURSDAY:MORNING FRIDAY:MORNING)"
request GET "$CLIENT_SR/$FABIO_REQUEST" "$ANA"
expect E8 "editing the grid does not change the request: Thursday is still full time" 200 \
  '.items[0].days[3] | .weekday == "THURSDAY" and .schoolShift == "FULLTIME"'
OTHER_SCHOOL=$(sql "select token from school where name <> 'Escola Municipal Seed' and deleted_at is null order by id limit 1")
request PATCH "/api/dependent/$LUCAS" "$ANA" "$(jq -nc --arg s "$OTHER_SCHOOL" '{schoolToken: $s}')"
[[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | change Lucas's school | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
request GET "$CLIENT_SR/$FABIO_REQUEST" "$ANA"
expect E9 "editing the dependent does not change the request: the school it was sent with" 200 \
  '.items[0].schoolName == "Escola Municipal Seed"'
request PATCH "/api/dependent/$LUCAS" "$ANA" "$(jq -nc --arg s "$SEED_SCHOOL" '{schoolToken: $s}')"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$IGOR_D" "$(printf 'a%.0s' $(seq 1000))" "$LUCAS_WEEK")"
expect E10 "notes with exactly 1000 characters" 201 '(.notes | length) == 1000'
IGOR_REQUEST=$(jq -r .token <<<"$RESP_BODY")
ROWS_BEFORE=$(request_rows)
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$GUSTAVO_D" "" "$LUCAS_WEEK")"
expect E11 "a second request to a driver whose request is ANSWERED (the seeded one)" 409 \
  '.detail == "Você já tem uma solicitação em aberto com este motorista."'
check E12 "nothing was created" "$ROWS_BEFORE" "$(request_rows)"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$JULIA_D" "" "$LUCAS_WEEK")"
expect E13 "the fifth open request" 201 '.status == "OPEN"'
JULIA_REQUEST=$(jq -r .token <<<"$RESP_BODY")
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$KAIO_D" "" "$LUCAS_WEEK")"
expect E14 "the sixth open request" 422 \
  '.detail == "Você já tem cinco solicitações em aberto. Aguarde uma resposta ou retire uma delas."'
check E15 "the refused request left no link" 0 "$(pair_links "$ANA_C" "$KAIO_D")"
ANA_TOTAL=$(sql "select count(*) from service_request request join client_driver link on link.id = request.client_driver_id
                 where link.client_id = (select id from client where token = '$ANA_C')")
request GET "$CLIENT_SR" "$ANA"
expect E16 "the list shows every request of Ana, newest first" 200 \
  ".totalElements == $ANA_TOTAL and .content[0].token == \"$JULIA_REQUEST\""
ANA_OPEN=$(sql "select count(*) from service_request request join client_driver link on link.id = request.client_driver_id
                where link.client_id = (select id from client where token = '$ANA_C') and request.status = 'OPEN'")
request GET "$CLIENT_SR?status=OPEN" "$ANA"
expect E17 "filtered by OPEN (the seeded one is ANSWERED)" 200 \
  ".totalElements == $ANA_OPEN and .totalElements == 4 and all(.content[]; .status == \"OPEN\")"
request GET "$CLIENT_SR?size=2&page=1" "$ANA"
expect E18 "paginated" 200 '(.content | length) == 2 and .size == 2 and .number == 1'
request GET "$CLIENT_SR?status=PENDING" "$ANA"
expect E19 "a status that does not exist" 400
request GET "$CLIENT_SR/$SIBLINGS" "$ANA"
expect E20 "reads one of her requests" 200 ".token == \"$SIBLINGS\" and .status == \"OPEN\""
request GET "$CLIENT_SR/unknown" "$ANA"
expect E21 "an unknown token" 404 '.detail == "Solicitação não encontrada."'
request POST "$CLIENT_SR/$SIBLINGS/withdraw" "$ANA"
expect E22 "withdraws the request for the two siblings" 200 '.status == "WITHDRAWN"'
request POST "$CLIENT_SR/$SIBLINGS/withdraw" "$ANA"
expect E23 "withdrawing it again" 409 "$NOT_OPEN"
request DELETE "/api/dependent/$MARINA" "$ANA"
expect E24 "Marina is removed after the request closed" 204
request GET "$CLIENT_SR/$SIBLINGS" "$ANA"
expect E25 "Ana still reads both items, Marina with the name and birth date she was sent with" 200 \
  "(.items | map(.dependentName)) == [\"Lucas Souza\", \"Marina Souza\"] and .items[1].dependentBirthDate == \"$MARINA_BIRTH\""
request GET "$DRIVER_SR/$SIBLINGS" "$HELENA"
expect E26 "and so does Helena" 200 '(.items | map(.dependentFirstName)) == ["Lucas", "Marina"]'
sql "update service_request set status = 'ANSWERED' where token = '$IGOR_REQUEST'; $(insert_proposal qahttpp1 "$IGOR_REQUEST" SENT 1)" >/dev/null
request POST "$CLIENT_SR/$IGOR_REQUEST/withdraw" "$ANA"
expect E27 "withdraws a request while a proposal is waiting" 200 '.status == "WITHDRAWN"'
check E28 "the proposal is DECLINED" DECLINED "$(sql "select status from proposal where token = 'qahttpp1'")"
sql "update service_request set expires_at = now() - interval '1 minute' where token = '$JULIA_REQUEST'" >/dev/null
request POST "$CLIENT_SR/$JULIA_REQUEST/withdraw" "$ANA"
expect E29 "an OPEN request past its deadline, before the sweep" 409 "$NOT_OPEN"
check E30 "it was not withdrawn" OPEN "$(request_status "$JULIA_REQUEST")"
sql "update service_request set expires_at = now() + interval '72 hours' where token = '$JULIA_REQUEST'" >/dev/null
check E31 "two requests to the same new driver at once: one 201, one 409" "201 409" \
  "$(in_parallel POST "$ANA" "$(sr_body "$KAIO_D" "" "$LUCAS_WEEK")" "$CLIENT_SR" "$CLIENT_SR")"
check E32 "one link and one request for the pair" "1 1" \
  "$(pair_links "$ANA_C" "$KAIO_D") $(sql "select count(*) from service_request request join client_driver link on link.id = request.client_driver_id
       where link.driver_id = (select id from driver where token = '$KAIO_D')")"
ANA_OPEN_OR_WITHDRAWN=$(sql "select count(*) from service_request request join client_driver link on link.id = request.client_driver_id
                          where link.client_id = (select id from client where token = '$ANA_C') and request.status in ('OPEN', 'WITHDRAWN')")
request GET "$CLIENT_SR?status=OPEN,WITHDRAWN" "$ANA"
expect E33 "filtered by two statuses, comma-separated" 200 \
  ".totalElements == $ANA_OPEN_OR_WITHDRAWN and all(.content[]; .status == \"OPEN\" or .status == \"WITHDRAWN\")"
request GET "$CLIENT_SR?status=OPEN&status=WITHDRAWN" "$ANA"
expect E34 "and with the parameter repeated" 200 ".totalElements == $ANA_OPEN_OR_WITHDRAWN"
request GET "$CLIENT_SR?status=OPEN,PENDING" "$ANA"
expect E35 "a list with a status that does not exist" 400
ANA_RATINGS=$(sql "select coalesce(json_object_agg(request.token, driver.rating), '{}') from service_request request
                    join client_driver link on link.id = request.client_driver_id join driver on driver.id = link.driver_id
                    where link.client_id = (select id from client where token = '$ANA_C')")
request GET "$CLIENT_SR?size=100" "$ANA"
expect E36 "every request in Ana's list carries its driver's rating, as stored" 200 \
  "any(.content[]; .driverRating != null) and all(.content[]; .driverRating == ($ANA_RATINGS)[.token])"
request GET "$CLIENT_SR/$SIBLINGS" "$ANA"
expect E37 "and so does the detail" 200 ".driverRating == ($ANA_RATINGS)[\"$SIBLINGS\"]"

table "Service requests over HTTP — driver"
LUCAS_AGE=$(sql "select extract(year from age(current_date, birth_date))::int from dependent where token = '$LUCAS'")
request GET "$DRIVER_SR?status=OPEN" "$FABIO"
expect J1 "Fabio lists his OPEN requests" 200 ".totalElements == 1 and .content[0].token == \"$FABIO_REQUEST\""
request GET "$DRIVER_SR/$FABIO_REQUEST" "$FABIO"
expect J2 "Fabio reads the client's first name and the dependent's first name and age" 200 \
  ".clientFirstName == \"Ana\" and .items[0].dependentFirstName == \"Lucas\" and .items[0].dependentAge == $LUCAS_AGE
   and .items[0].schoolName == \"Escola Municipal Seed\" and .items[0].pickupCityName == \"São Paulo\"
   and .items[0].pickupNeighborhood == \"Centro\" and (.items[0].days | length) == 5 and .expiresAt != null
   and ([paths | last | strings] | map(select(IN(\"dependentName\", \"dependentBirthDate\", \"street\", \"zipCode\",
     \"googlePlaceId\", \"document\", \"phone\", \"email\", \"driverName\"))) | length) == 0"
LEAKS="Souza|$(sql "select concat_ws('|', dependent.birth_date, address.street, address.zip_code, users.document, users.phone, users.email)
                  from dependent join address on address.id = dependent.address_id join client on client.id = dependent.client_id
                  join users on users.id = client.user_id where dependent.token = '$LUCAS'")"
check J3 "the response has no surname, birth date, street, zip code, document, phone or e-mail" "" \
  "$(grep -oE "$LEAKS" <<<"$RESP_BODY" | sort -u | paste -sd, - || true)"
request GET "$DRIVER_SR/$FABIO_REQUEST" "$GUSTAVO"
expect J4 "another driver's request" 404 '.detail == "Solicitação não encontrada."'
request POST "$DRIVER_SR/$FABIO_REQUEST/decline" "$FABIO" "$(jq -nc '{reason: ("a" * 501)}')"
expect J5 "a reason with 501 characters" 400
request POST "$DRIVER_SR/$FABIO_REQUEST/decline" "$FABIO" '{"reason": {"text": "x"}}'
expect J6 "a reason that is not text" 400
request POST "$DRIVER_SR/$FABIO_REQUEST/decline" "$FABIO" '{"reason": '
expect J7 "malformed JSON" 400
check J8 "the request is still OPEN" OPEN "$(request_status "$FABIO_REQUEST")"
request POST "$DRIVER_SR/$FABIO_REQUEST/decline" "$FABIO" '{"reason": "  Sem vaga  "}'
expect J9 "Fabio declines with a reason" 200 '.status == "DECLINED" and .declineReason == "Sem vaga"'
request GET "$CLIENT_SR/$FABIO_REQUEST" "$ANA"
expect J10 "Ana reads it declined, with the reason" 200 '.status == "DECLINED" and .declineReason == "Sem vaga"'
request POST "$DRIVER_SR/$FABIO_REQUEST/decline" "$FABIO"
expect J11 "declining it again" 409 "$NOT_OPEN"
request POST "$DRIVER_SR/$SEED_REQUEST/decline" "$GUSTAVO" "$(jq -nc '{reason: ("a" * 500)}')"
expect J12 "a reason with exactly 500 characters, on the seeded request" 200 '.status == "DECLINED" and (.declineReason | length) == 500'
check J13 "the seeded proposal is WITHDRAWN with it" WITHDRAWN "$(sql "select status from proposal where id = $SEED_PROPOSAL")"
HELENA_REQUEST=$(send_request "$(sr_body "$HELENA_D" "" "$LUCAS_WEEK")")
request POST "$DRIVER_SR/$HELENA_REQUEST/decline" "$HELENA"
expect J14 "Helena declines without a body" 200 '.status == "DECLINED" and .declineReason == null'
HELENA_REQUEST=$(send_request "$(sr_body "$HELENA_D" "" "$LUCAS_WEEK")")
sql "update service_request set status = 'ANSWERED' where token = '$HELENA_REQUEST'; $(insert_proposal qahttpp2 "$HELENA_REQUEST" SENT 1)" >/dev/null
request POST "$DRIVER_SR/$HELENA_REQUEST/decline" "$HELENA"
expect J15 "Helena declines while her proposal is waiting" 200 '.status == "DECLINED"'
check J16 "her proposal is WITHDRAWN" WITHDRAWN "$(sql "select status from proposal where token = 'qahttpp2'")"
GUSTAVO_REQUEST=$(send_request "$(sr_body "$GUSTAVO_D" "" "$LUCAS_WEEK")")
sql "update service_request set status = 'CONVERTED' where token = '$GUSTAVO_REQUEST'" >/dev/null
request POST "$DRIVER_SR/$GUSTAVO_REQUEST/decline" "$GUSTAVO"
expect J17 "declining a CONVERTED request" 409 "$NOT_OPEN"
check J18 "it stays CONVERTED" CONVERTED "$(request_status "$GUSTAVO_REQUEST")"
p_item() {
  jq -nc --arg d "$1" --argjson s "$P_SLOTS" '{dependentToken: $d, monthlyAmount: 420.00, slots: $s}'
}

p_body() {
  local vehicle=$1
  shift
  jq -nc --arg v "$vehicle" --arg s "$START" --arg e "$P_END" \
    '{startsOn: $s, endsOn: $e, totalAmount: 4200.00, installments: 10, dueDay: 5, enrollmentFee: 100.00,
      adjustmentPolicy: "NONE", cancellationPolicy: "NOTICE_30_DAYS", vehicleToken: $v, message: "Saio às 6h20",
      items: [$ARGS.positional[] | fromjson]}' --args "$@"
}

send_proposal() {
  request POST "$DRIVER_SR/$2/proposals" "$1" "$3"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | send a proposal | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

adjustments_of() {
  sql "select count(*) from proposal_adjustment where proposal_id = (select id from proposal where token = '$1')"
}

link_contracts() {
  sql "select count(contract.id) || ' ' || min(link.status) from client_driver link left join contract on contract.client_driver_id = link.id
       where link.id = (select client_driver_id from service_request where token = '$1')"
}

proposal_status() {
  sql "select status from proposal where token = '$1'"
}

chain_rows() {
  sql "select (select count(*) from proposal) || ' ' || (select count(*) from proposal_adjustment) || ' '
         || string_agg(proposal.status, ',' order by proposal.version) || ' ' || min(request.status)
       from proposal join service_request request on request.id = proposal.service_request_id where request.token = '$1'"
}

proposal_route_statuses() {
  local token=$1 route body codes=()
  shift
  for route in "$@"; do
    body=""
    if [[ $route == "POST $DRIVER_SR/"* ]]; then body=$P_BODY; fi
    if [[ $route == *"/adjustments" ]]; then body=$PRICE_ADJUSTMENT; fi
    request "${route%% *}" "${route#* }" "$token" "$body"
    codes+=("$RESP_STATUS")
  done
  echo "${codes[*]}"
}

expect_bad_proposal() {
  request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$3"
  expect "$1" "$2" "${4:-400}" "${5-}"
}

expect_bad_adjustment() {
  request POST "$CLIENT_P/$G_P4/adjustments" "$ANA" "$3"
  expect "$1" "$2" "${4:-400}" "${5-}"
}

# Runs two requests of different callers at the same moment and prints their statuses in order.
race() {
  local dir first second
  dir="$(mktemp -d)"
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL$1" -H "Authorization: Bearer $2" \
    -H "Content-Type: application/json" ${3:+--data-binary "$3"} >"$dir/first" &
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL$4" -H "Authorization: Bearer $5" \
    -H "Content-Type: application/json" ${6:+--data-binary "$6"} >"$dir/second" &
  wait
  first=$(cat "$dir/first")
  second=$(cat "$dir/second")
  rm -rf "$dir"
  echo "$first $second"
}

CLIENT_P=/api/clients/me/proposals
DRIVER_P=/api/drivers/me/proposals
NOT_CURRENT='.detail == "Esta proposta não é mais a versão atual."'
NOT_OPEN_FOR_PROPOSALS='.detail == "Esta solicitação não está mais aberta para propostas."'
STALE='.detail == "Algo na solicitação ou na proposta mudou desde o envio. Peça uma nova versão ao motorista."'
VEHICLE_NOT_FOUND='.detail == "Veículo não encontrado."'
MISMATCH='.detail == "A proposta precisa de um item para cada dependente da solicitação."'
PRICE_ADJUSTMENT='{"field": "PRICE", "suggestedValue": "320,00"}'
GUSTAVO_VAN=$(sql "select token from vehicle where plate = 'VAN2B34'")
FABIO_VAN=$(sql "select token from vehicle where plate = 'VAN1A23'")
HELENA_VAN=$(sql "select token from vehicle where plate = 'VAN3C45'")
P_END=$(sql "select (current_date + 7 + interval '10 months' - interval '1 day')::date")
TWELVE_MONTHS=$(sql "select (current_date + 7 + interval '12 months')::date")
TWELVE_MONTHS_AND_A_DAY=$(sql "select (current_date + 7 + interval '12 months' + interval '1 day')::date")
P_SLOTS=$(jq -nc '[("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY") as $d
  | {weekday: $d, leg: "OUTBOUND", shift: "MORNING", windowStart: "06:40"},
    {weekday: $d, leg: "RETURN", shift: "AFTERNOON", windowStart: "12:10"}]')
LUCAS_ITEM=$(p_item "$LUCAS")
P_BODY=$(p_body "$GUSTAVO_VAN" "$LUCAS_ITEM")
for open in $(sql "select request.token from service_request request join client_driver link on link.id = request.client_driver_id
                   where link.client_id = (select id from client where token = '$ANA_C') and request.status = 'OPEN'"); do
  request POST "$CLIENT_SR/$open/withdraw" "$ANA"
done

table "Proposals over HTTP — driver"
G_REQ=$(send_request "$(sr_body "$GUSTAVO_D" "" "$LUCAS_WEEK")")
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$P_BODY"
expect X1 "Gustavo sends the first version" 201 \
  ".version == 1 and .status == \"SENT\" and .serviceRequestToken == \"$G_REQ\" and .startsOn == \"$START\" and .endsOn == \"$P_END\"
   and .totalAmount == 4200 and .installments == 10 and .dueDay == 5 and .enrollmentFee == 100
   and .adjustmentPolicy == \"NONE\" and .cancellationPolicy == \"NOTICE_30_DAYS\" and .message == \"Saio às 6h20\"
   and .vehicle == {plate: \"VAN2B34\", brand: \"Renault\", model: \"Master Minibus\", manufactureYear: 2020, color: \"Prata\", capacity: 16}
   and (.items | length) == 1 and .items[0].dependentToken == \"$LUCAS\" and .items[0].dependentFirstName == \"Lucas\"
   and .items[0].dependentName == null and .items[0].monthlyAmount == 420 and (.items[0].slots | length) == 10
   and (.items[0].slots[1] | .weekday == \"MONDAY\" and .leg == \"RETURN\" and .shift == \"AFTERNOON\"
     and (.windowStart | startswith(\"12:10\")) and .windowEnd == null)
   and .adjustments == [] and .acceptedAt == null and (has(\"id\") | not)"
G_P1=$(jq -r .token <<<"$RESP_BODY")
check X2 "the driver's view has no surname" "" "$(grep -o Souza <<<"$RESP_BODY" | sort -u || true)"
check X3 "valid for 7 days by default, and the request is ANSWERED" "7 ANSWERED" \
  "$(sql "select round(extract(epoch from valid_until - created_at) / 86400) from proposal where token = '$G_P1'") $(request_status "$G_REQ")"
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" \
  "$(jq -c --arg e "$TWELVE_MONTHS" '.endsOn = $e | .installments = 1 | .dueDay = 1 | .validDays = 1' <<<"$P_BODY")"
expect X4 "a second version for exactly twelve months, at the lower limits (1 installment, due day 1, 1 day)" 201 \
  ".version == 2 and .endsOn == \"$TWELVE_MONTHS\" and .installments == 1 and .dueDay == 1"
G_P2=$(jq -r .token <<<"$RESP_BODY")
check X5 "version 2 points to version 1, now SUPERSEDED, and is valid for one day" "$G_P1 SUPERSEDED 1" \
  "$(sql "select parent.token || ' ' || parent.status || ' ' || round(extract(epoch from child.valid_until - child.created_at) / 86400)
          from proposal child join proposal parent on parent.id = child.parent_proposal_id where child.token = '$G_P2'")"
request GET "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO"
expect X6 "Gustavo lists the chain, oldest first" 200 \
  "map(.token) == [\"$G_P1\", \"$G_P2\"] and map(.status) == [\"SUPERSEDED\", \"SENT\"]"
request GET "$DRIVER_P/$G_P2" "$GUSTAVO"
expect X7 "Gustavo reads version 2" 200 ".token == \"$G_P2\" and .status == \"SENT\" and .validUntil != null"
request POST "$DRIVER_P/$G_P2/withdraw" "$GUSTAVO"
expect X8 "Gustavo withdraws version 2 to correct the offer" 200 '.status == "WITHDRAWN"'
check X9 "the request is OPEN again, for 72 hours" "OPEN 72" \
  "$(sql "select status || ' ' || round(extract(epoch from expires_at - now()) / 3600) from service_request where token = '$G_REQ'")"
request POST "$DRIVER_P/$G_P2/withdraw" "$GUSTAVO"
expect X10 "withdrawing it again" 409 "$NOT_CURRENT"
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$(jq -c '.enrollmentFee = 0 | del(.message)' <<<"$P_BODY")"
expect X11 "the next version after the withdrawal, with a zero enrollment fee and no message" 201 \
  '.version == 3 and .enrollmentFee == 0 and .message == null'
G_P3=$(jq -r .token <<<"$RESP_BODY")
check X12 "version 3 points to the withdrawn version 2, which stays WITHDRAWN" "$G_P2 WITHDRAWN ANSWERED" \
  "$(sql "select parent.token || ' ' || parent.status from proposal child join proposal parent on parent.id = child.parent_proposal_id
          where child.token = '$G_P3'") $(request_status "$G_REQ")"
request POST "$DRIVER_SR/$FABIO_REQUEST/proposals" "$FABIO" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM")"
expect X13 "a proposal for a request the driver declined" 409 "$NOT_OPEN_FOR_PROPOSALS"
H_REQ=$(send_request "$(sr_body "$HELENA_D" "" "$LUCAS_WEEK")")
sql "update service_request set expires_at = now() - interval '1 minute' where token = '$H_REQ'" >/dev/null
request POST "$DRIVER_SR/$H_REQ/proposals" "$HELENA" "$(p_body "$HELENA_VAN" "$LUCAS_ITEM")"
expect X14 "a proposal for an OPEN request past its deadline, before the sweep" 409 "$NOT_OPEN_FOR_PROPOSALS"
check X15 "nothing was sent and the request is still OPEN" "0 OPEN" \
  "$(sql "select count(*) from proposal where service_request_id = (select id from service_request where token = '$H_REQ')") $(request_status "$H_REQ")"
sql "update service_request set expires_at = now() + interval '72 hours' where token = '$H_REQ'" >/dev/null

table "Proposals over HTTP — misuse"
P_DRIVER_ROUTES=("POST $DRIVER_SR/$G_REQ/proposals" "GET $DRIVER_SR/$G_REQ/proposals" "GET $DRIVER_P/$G_P3"
  "POST $DRIVER_P/$G_P3/withdraw")
P_CLIENT_ROUTES=("GET $CLIENT_SR/$G_REQ/proposals" "GET $CLIENT_P/$G_P3" "POST $CLIENT_P/$G_P3/accept"
  "POST $CLIENT_P/$G_P3/decline" "POST $CLIENT_P/$G_P3/adjustments")
G_ROWS=$(chain_rows "$G_REQ")
check B1 "the nine routes without a token" "401 401 401 401 401 401 401 401 401" \
  "$(proposal_route_statuses "" "${P_DRIVER_ROUTES[@]}" "${P_CLIENT_ROUTES[@]}")"
check B2 "the nine routes with an invalid token" "401 401 401 401 401 401 401 401 401" \
  "$(proposal_route_statuses not-a-jwt "${P_DRIVER_ROUTES[@]}" "${P_CLIENT_ROUTES[@]}")"
check B3 "the admin on both surfaces" "403 403 403 403 403 403 403 403 403" \
  "$(proposal_route_statuses "$ADMIN" "${P_DRIVER_ROUTES[@]}" "${P_CLIENT_ROUTES[@]}")"
check B4 "the client on the driver routes" "403 403 403 403" "$(proposal_route_statuses "$ANA" "${P_DRIVER_ROUTES[@]}")"
check B5 "the driver on the client routes" "403 403 403 403 403" "$(proposal_route_statuses "$GUSTAVO" "${P_CLIENT_ROUTES[@]}")"
check B6 "another driver: the request and the proposal are not found" "404 404 404 404" \
  "$(proposal_route_statuses "$FABIO" "${P_DRIVER_ROUTES[@]}")"
check B7 "another client: the request and the proposal are not found" "404 404 404 404 404" \
  "$(proposal_route_statuses "$BRUNO" "${P_CLIENT_ROUTES[@]}")"
request GET "$CLIENT_P/$G_P3" "$BRUNO"
expect B8 "another client's proposal, with its message" 404 '.detail == "Proposta não encontrada."'
request GET "$DRIVER_P/unknown" "$GUSTAVO"
expect B9 "an unknown proposal" 404 '.detail == "Proposta não encontrada."'
request POST "$DRIVER_SR/unknown/proposals" "$GUSTAVO" "$P_BODY"
expect B10 "a proposal for an unknown request" 404 '.detail == "Solicitação não encontrada."'
check B11 "no route changed the chain" "$G_ROWS" "$(chain_rows "$G_REQ")"
expect_bad_proposal B12 "without startsOn" "$(jq -c 'del(.startsOn)' <<<"$P_BODY")"
expect_bad_proposal B13 "startsOn yesterday" "$(jq -c --arg y "$YESTERDAY" '.startsOn = $y' <<<"$P_BODY")"
expect_bad_proposal B14 "startsOn that is not a date" "$(jq -c '.startsOn = "amanhã"' <<<"$P_BODY")"
expect_bad_proposal B15 "without endsOn" "$(jq -c 'del(.endsOn)' <<<"$P_BODY")"
expect_bad_proposal B16 "totalAmount zero" "$(jq -c '.totalAmount = 0' <<<"$P_BODY")"
expect_bad_proposal B17 "a negative totalAmount" "$(jq -c '.totalAmount = -1' <<<"$P_BODY")"
expect_bad_proposal B18 "totalAmount with three decimals" "$(jq -c '.totalAmount = 4200.001' <<<"$P_BODY")"
expect_bad_proposal B19 "totalAmount as text" "$(jq -c '.totalAmount = "caro"' <<<"$P_BODY")"
expect_bad_proposal B20 "zero installments" "$(jq -c '.installments = 0' <<<"$P_BODY")"
expect_bad_proposal B21 "13 installments" "$(jq -c '.installments = 13' <<<"$P_BODY")"
expect_bad_proposal B22 "installments as text" "$(jq -c '.installments = "dez"' <<<"$P_BODY")"
expect_bad_proposal B23 "due day 0" "$(jq -c '.dueDay = 0' <<<"$P_BODY")"
expect_bad_proposal B24 "due day 29" "$(jq -c '.dueDay = 29' <<<"$P_BODY")"
expect_bad_proposal B25 "a negative enrollment fee" "$(jq -c '.enrollmentFee = -1' <<<"$P_BODY")"
expect_bad_proposal B26 "an adjustment policy that does not exist" "$(jq -c '.adjustmentPolicy = "ALWAYS"' <<<"$P_BODY")"
expect_bad_proposal B27 "without cancellationPolicy" "$(jq -c 'del(.cancellationPolicy)' <<<"$P_BODY")"
expect_bad_proposal B28 "without vehicleToken" "$(jq -c 'del(.vehicleToken)' <<<"$P_BODY")"
expect_bad_proposal B29 "a blank vehicleToken" "$(jq -c '.vehicleToken = "  "' <<<"$P_BODY")"
expect_bad_proposal B30 "validDays 0" "$(jq -c '.validDays = 0' <<<"$P_BODY")"
expect_bad_proposal B31 "validDays 8" "$(jq -c '.validDays = 8' <<<"$P_BODY")"
expect_bad_proposal B32 "a message with 1001 characters" "$(jq -c '.message = ("a" * 1001)' <<<"$P_BODY")"
expect_bad_proposal B33 "an empty item list" "$(jq -c '.items = []' <<<"$P_BODY")"
expect_bad_proposal B34 "without items" "$(jq -c 'del(.items)' <<<"$P_BODY")"
expect_bad_proposal B35 "items that are not a list" "$(jq -c '.items = "Lucas"' <<<"$P_BODY")"
expect_bad_proposal B36 "a monthly amount of zero" "$(jq -c '.items[0].monthlyAmount = 0' <<<"$P_BODY")"
expect_bad_proposal B37 "an item without dependentToken" "$(jq -c 'del(.items[0].dependentToken)' <<<"$P_BODY")"
expect_bad_proposal B38 "an item with no slots" "$(jq -c '.items[0].slots = []' <<<"$P_BODY")"
expect_bad_proposal B39 "a slot without windowStart" "$(jq -c 'del(.items[0].slots[0].windowStart)' <<<"$P_BODY")"
expect_bad_proposal B40 "a shift that does not exist" "$(jq -c '.items[0].slots[0].shift = "MADRUGADA"' <<<"$P_BODY")"
expect_bad_proposal B41 "a weekday that does not exist" "$(jq -c '.items[0].slots[0].weekday = "SEGUNDA"' <<<"$P_BODY")"
expect_bad_proposal B42 "a time that is not a time" "$(jq -c '.items[0].slots[0].windowStart = "6h40"' <<<"$P_BODY")"
expect_bad_proposal B43 "the same weekday and leg twice" \
  "$(jq -c '.items[0].slots[1] = .items[0].slots[0]' <<<"$P_BODY")" 400 \
  '.detail == "O mesmo dia e trecho não pode aparecer duas vezes na agenda."'
expect_bad_proposal B44 "a window that ends before it starts" \
  "$(jq -c '.items[0].slots[0].windowEnd = "06:00"' <<<"$P_BODY")" 400 \
  '.detail == "O fim da janela deve ser posterior ao início."'
expect_bad_proposal B45 "malformed JSON" '{"totalAmount": '
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO"
expect B46 "no body" 400
expect_bad_proposal B47 "endsOn on startsOn" "$(jq -c --arg s "$START" '.endsOn = $s' <<<"$P_BODY")" 422 \
  '.detail == "A data de término deve ser posterior à data de início."'
expect_bad_proposal B48 "endsOn before startsOn" "$(jq -c --arg s "$(sql "select current_date + 6")" '.endsOn = $s' <<<"$P_BODY")" 422 \
  '.detail == "A data de término deve ser posterior à data de início."'
expect_bad_proposal B49 "twelve months and one day" "$(jq -c --arg e "$TWELVE_MONTHS_AND_A_DAY" '.endsOn = $e' <<<"$P_BODY")" 422 \
  '.detail == "O período da proposta não pode passar de doze meses."'
expect_bad_proposal B50 "one item too many: Lucas twice" "$(jq -c '.items += .items' <<<"$P_BODY")" 422 "$MISMATCH"
expect_bad_proposal B51 "an item for an unknown dependent instead of Lucas" "$(jq -c '.items[0].dependentToken = "unknown"' <<<"$P_BODY")" 422 "$MISMATCH"
expect_bad_proposal B52 "the vehicle of another driver" "$(jq -c --arg v "$HELENA_VAN" '.vehicleToken = $v' <<<"$P_BODY")" 404 "$VEHICLE_NOT_FOUND"
expect_bad_proposal B53 "an unknown vehicle" "$(jq -c '.vehicleToken = "unknown"' <<<"$P_BODY")" 404 "$VEHICLE_NOT_FOUND"
sql "update vehicle set is_active = false where token = '$GUSTAVO_VAN'" >/dev/null
expect_bad_proposal B54 "an inactive vehicle of his own" "$P_BODY" 404 "$VEHICLE_NOT_FOUND"
sql "update vehicle set is_active = true where token = '$GUSTAVO_VAN'" >/dev/null
check B55 "no refusal sent a version or touched the chain" "$G_ROWS" "$(chain_rows "$G_REQ")"
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" \
  "$(jq -c '.installments = 12 | .dueDay = 28 | .validDays = 7 | .message = ("a" * 1000)' <<<"$P_BODY")"
expect B56 "every field at its upper limit (12 installments, due day 28, 7 days, 1000 characters)" 201 \
  '.version == 4 and .installments == 12 and .dueDay == 28 and (.message | length) == 1000'
G_P4=$(jq -r .token <<<"$RESP_BODY")
request POST /api/dependent "$ANA" "$(jq -nc --arg s "$SEED_SCHOOL" --arg c "$SEED_CITY" '{name: "Nina Souza", birthDate: "2017-03-02",
  schoolToken: $s, address: {cityToken: $c, street: "Rua das Flores", number: "30", zipCode: "01001000", neighborhood: "Centro"}}')"
[[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | create Nina | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
NINA=$(jq -r .token <<<"$RESP_BODY")
request PUT "/api/dependent/$NINA/school-days" "$ANA" \
  "$(school_days_body MONDAY:MORNING TUESDAY:MORNING WEDNESDAY:MORNING THURSDAY:MORNING FRIDAY:MORNING)"
[[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | Nina's grid | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
NINA_ITEM=$(p_item "$NINA")
F_REQ=$(send_request "$(sr_body "$FABIO_D" "" "$LUCAS_WEEK" "$(sr_item "$NINA" "${WEEK[@]}")")")
request POST "$DRIVER_SR/$F_REQ/proposals" "$FABIO" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM")"
expect B57 "one item too few: only Lucas for Lucas and Nina" 422 "$MISMATCH"
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$(p_body "$GUSTAVO_VAN" "$LUCAS_ITEM" "$NINA_ITEM")"
expect B58 "an item for Nina, who is not in Gustavo's request" 422 "$MISMATCH"
sql "update dependent set deleted_at = now() where token = '$NINA'" >/dev/null
request POST "$DRIVER_SR/$F_REQ/proposals" "$FABIO" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM" "$NINA_ITEM")"
expect B59 "a dependent of the request removed since (set by SQL: the API refuses it during a negotiation)" 422 \
  '.detail == "Um dependente desta solicitação não está mais cadastrado."'
sql "update dependent set deleted_at = null where token = '$NINA'" >/dev/null
check B60 "Fabio's request has no proposal and is still OPEN" "0 OPEN" \
  "$(sql "select count(*) from proposal where service_request_id = (select id from service_request where token = '$F_REQ')") $(request_status "$F_REQ")"
check B61 "a proposal cannot be edited or removed on either surface" "405 405 405 405 405 405" \
  "$(for route in "PATCH $CLIENT_P/$G_P4 $ANA" "PUT $CLIENT_P/$G_P4 $ANA" "DELETE $CLIENT_P/$G_P4 $ANA" \
       "PATCH $DRIVER_P/$G_P4 $GUSTAVO" "PUT $DRIVER_P/$G_P4 $GUSTAVO" "DELETE $DRIVER_P/$G_P4 $GUSTAVO"; do
       read -r method path token <<<"$route"
       request "$method" "$path" "$token" '{"totalAmount": 1}'
       printf '%s ' "$RESP_STATUS"
     done | sed 's/ $//')"

table "Proposals over HTTP — client"
request GET "$CLIENT_SR/$G_REQ/proposals" "$ANA"
expect Y1 "Ana lists the four versions, oldest first, with Lucas's full name" 200 \
  "map(.version) == [1, 2, 3, 4] and map(.status) == [\"SUPERSEDED\", \"WITHDRAWN\", \"SUPERSEDED\", \"SENT\"]
   and all(.[]; .items[0].dependentName == \"Lucas Souza\" and .items[0].dependentFirstName == \"Lucas\")"
request GET "$CLIENT_P/$G_P4" "$ANA"
expect Y2 "Ana reads the current version, with the vehicle it was sent with" 200 \
  ".token == \"$G_P4\" and .status == \"SENT\" and .vehicle.plate == \"VAN2B34\" and .items[0].dependentName == \"Lucas Souza\""
request POST "$CLIENT_P/$G_P4/adjustments" "$ANA" "$PRICE_ADJUSTMENT"
expect Y3 "Ana asks to lower the price" 201 \
  '.status == "SENT" and (.adjustments | length) == 1 and .adjustments[0].field == "PRICE"
   and .adjustments[0].suggestedValue == "320,00" and .adjustments[0].note == null and .adjustments[0].createdAt != null'
request POST "$CLIENT_P/$G_P4/adjustments" "$ANA" '{"field": "SCHEDULE", "note": "  Pode buscar às 6h50?  "}'
expect Y4 "and for another time, with a note only" 201 \
  '(.adjustments | map(.field)) == ["PRICE", "SCHEDULE"] and .adjustments[1].note == "Pode buscar às 6h50?" and .adjustments[1].suggestedValue == null'
request GET "$CLIENT_SR?status=ANSWERED" "$ANA"
expect Y39 "Ana's list carries version 4 as the request's current proposal, with an adjustment pending" 200 \
  "any(.content[]; .token == \"$G_REQ\" and .currentProposal.token == \"$G_P4\" and .currentProposal.version == 4
     and .currentProposal.hasPendingAdjustment == true)"
request GET "$DRIVER_SR?status=ANSWERED" "$GUSTAVO"
expect Y40 "and so does Gustavo's" 200 \
  "any(.content[]; .token == \"$G_REQ\" and .currentProposal.token == \"$G_P4\" and .currentProposal.hasPendingAdjustment == true)"
expect_bad_adjustment Y5 "without field" '{"suggestedValue": "320,00"}'
expect_bad_adjustment Y6 "a field that does not exist" '{"field": "COLOR", "note": "Outra cor"}'
expect_bad_adjustment Y7 "a field that is an object" '{"field": {"name": "PRICE"}, "note": "x"}'
expect_bad_adjustment Y8 "a suggested value with 101 characters" "$(jq -nc '{field: "PRICE", suggestedValue: ("1" * 101)}')"
expect_bad_adjustment Y9 "a note with 501 characters" "$(jq -nc '{field: "OTHER", note: ("a" * 501)}')"
expect_bad_adjustment Y10 "malformed JSON" '{"field": '
request POST "$CLIENT_P/$G_P4/adjustments" "$ANA"
expect Y11 "no body" 400
EMPTY_ADJUSTMENT='.detail == "Diga o que você gostaria de mudar: um valor sugerido, uma observação ou os dois."'
expect_bad_adjustment Y12 "neither a value nor a note" '{"field": "PRICE"}' 422 "$EMPTY_ADJUSTMENT"
expect_bad_adjustment Y13 "a blank value and a blank note" '{"field": "PRICE", "suggestedValue": " ", "note": "  "}' 422 "$EMPTY_ADJUSTMENT"
check Y14 "the refusals saved no adjustment" 2 "$(adjustments_of "$G_P4")"
request POST "$CLIENT_P/$G_P4/adjustments" "$ANA" "$(jq -nc '{field: "PRICE", suggestedValue: ("1" * 100)}')"
expect Y15 "a suggested value with exactly 100 characters" 201 '(.adjustments[2].suggestedValue | length) == 100'
request POST "$CLIENT_P/$G_P4/adjustments" "$ANA" "$(jq -nc '{field: "OTHER", note: ("a" * 500)}')"
expect Y16 "a note with exactly 500 characters" 201 '(.adjustments[3].note | length) == 500 and .status == "SENT"'
request GET "$DRIVER_P/$G_P4" "$GUSTAVO"
expect Y17 "Gustavo sees the four adjustments, in order" 200 \
  '(.adjustments | map(.field)) == ["PRICE", "SCHEDULE", "PRICE", "OTHER"] and .status == "SENT"'
DRIVER_SENT_VIEW=$RESP_BODY
request POST "$CLIENT_P/$G_P1/accept" "$ANA"
expect Y18 "accepting version 1, superseded" 409 "$NOT_CURRENT"
request POST "$CLIENT_P/$G_P2/decline" "$ANA"
expect Y19 "declining version 2, withdrawn" 409 "$NOT_CURRENT"
request POST "$CLIENT_P/$G_P3/adjustments" "$ANA" "$PRICE_ADJUSTMENT"
expect Y20 "asking to adjust version 3, superseded" 409 "$NOT_CURRENT"
request POST "$CLIENT_P/$G_P4/decline" "$ANA" "$(jq -nc '{reason: ("a" * 501)}')"
expect Y21 "a decline reason with 501 characters" 400
request POST "$CLIENT_P/$G_P4/decline" "$ANA" '{"reason": {"text": "x"}}'
expect Y22 "a decline reason that is not text" 400
check Y23 "the old versions and the current one did not change" "SUPERSEDED,WITHDRAWN,SUPERSEDED,SENT ANSWERED 4" \
  "$(chain_rows "$G_REQ" | cut -d' ' -f3-) $(adjustments_of "$G_P4")"
request GET "$CLIENT_P/$G_P4" "$ANA"
CLIENT_SENT_VIEW=$RESP_BODY
G_LINK=$(link_contracts "$G_REQ")
request POST "$CLIENT_P/$G_P4/accept" "$ANA"
expect Y24 "Ana accepts version 4" 200 '.status == "ACCEPTED" and .acceptedAt != null'
check Y25 "the request is CONVERTED, no contract was created and the link status did not change" \
  "CONVERTED $G_LINK" "$(request_status "$G_REQ") $(link_contracts "$G_REQ")"
request GET "$CLIENT_SR/$G_REQ" "$ANA"
expect Y41 "the CONVERTED request carries the accepted version, with nothing pending" 200 \
  ".status == \"CONVERTED\" and .currentProposal.token == \"$G_P4\" and .currentProposal.status == \"ACCEPTED\"
   and .currentProposal.hasPendingAdjustment == false"
UNCHANGED='del(.status, .acceptedAt, .updatedAt)'
request GET "$CLIENT_P/$G_P4" "$ANA"
check Y26 "Ana reads the accepted proposal exactly as it was sent" "$(jq -S "$UNCHANGED" <<<"$CLIENT_SENT_VIEW")" \
  "$(jq -S "$UNCHANGED" <<<"$RESP_BODY")"
request GET "$DRIVER_P/$G_P4" "$GUSTAVO"
check Y27 "and so does Gustavo" "$(jq -S "$UNCHANGED" <<<"$DRIVER_SENT_VIEW")" "$(jq -S "$UNCHANGED" <<<"$RESP_BODY")"
check Y28 "accept, decline, adjustment and withdrawal after the accept" "409 409 409 409" \
  "$(for route in "$CLIENT_P/$G_P4/accept $ANA" "$CLIENT_P/$G_P4/decline $ANA" "$CLIENT_P/$G_P4/adjustments $ANA" \
       "$DRIVER_P/$G_P4/withdraw $GUSTAVO"; do
       read -r path token <<<"$route"
       if [[ $path == *"/adjustments" ]]; then request POST "$path" "$token" "$PRICE_ADJUSTMENT"; else request POST "$path" "$token"; fi
       printf '%s ' "$RESP_STATUS"
     done | sed 's/ $//')"
request POST "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$P_BODY"
expect Y29 "a new version for the converted request" 409 "$NOT_OPEN_FOR_PROPOSALS"
F_P1=$(send_proposal "$FABIO" "$F_REQ" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM" "$NINA_ITEM")")
request GET "$CLIENT_P/$F_P1" "$ANA"
expect Y30 "Fabio's proposal for both siblings" 200 '(.items | map(.dependentName)) == ["Lucas Souza", "Nina Souza"]'
request GET "$DRIVER_P/$F_P1" "$FABIO"
expect Y31 "Fabio sees them by first name only" 200 \
  '(.items | map(.dependentFirstName)) == ["Lucas", "Nina"] and all(.items[]; .dependentName == null)'
request POST "$CLIENT_P/$F_P1/decline" "$ANA" '{"reason": "  Caro demais  "}'
expect Y32 "Ana declines it with a reason" 200 '.status == "DECLINED" and .declineReason == "Caro demais"'
check Y33 "the request is WITHDRAWN" WITHDRAWN "$(request_status "$F_REQ")"
request GET "$CLIENT_SR/$F_REQ" "$ANA"
expect Y42 "and has no current proposal" 200 '.status == "WITHDRAWN" and .currentProposal == null'
request GET "$DRIVER_P/$F_P1" "$FABIO"
expect Y34 "Fabio reads it declined, with the reason" 200 '.status == "DECLINED" and .declineReason == "Caro demais"'
request POST "$DRIVER_SR/$F_REQ/proposals" "$FABIO" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM" "$NINA_ITEM")"
expect Y35 "a new version after the decline" 409 "$NOT_OPEN_FOR_PROPOSALS"
H_P1=$(send_proposal "$HELENA" "$H_REQ" "$(p_body "$HELENA_VAN" "$LUCAS_ITEM")")
sql "update proposal set valid_until = now() - interval '1 minute' where token = '$H_P1'" >/dev/null
request POST "$CLIENT_P/$H_P1/accept" "$ANA"
expect Y36 "accepting a proposal past its validity, before the sweep" 409 '.detail == "Esta proposta venceu."'
check Y37 "it is still SENT and the request ANSWERED" "SENT ANSWERED" "$(proposal_status "$H_P1") $(request_status "$H_REQ")"
request POST "$DRIVER_P/$H_P1/withdraw" "$HELENA"
expect Y38 "Helena withdraws it" 200 '.status == "WITHDRAWN"'
request GET "$CLIENT_SR/$H_REQ" "$ANA"
expect Y43 "the request the withdrawal reopened has no current proposal either" 200 '.status == "OPEN" and .currentProposal == null'
H_START=$(sql "select current_date + 8")
request POST "$DRIVER_SR/$H_REQ/proposals" "$HELENA" \
  "$(p_body "$HELENA_VAN" "$LUCAS_ITEM" | jq -c --arg s "$H_START" '.totalAmount = 1000.00 | .installments = 3 | .startsOn = $s')"
expect Y44 "Helena sends 1,000.00 in three installments, starting a day after the desired date: each installment is 333.33, the cents truncated" 201 \
  ".totalAmount == 1000 and .installments == 3 and .installmentAmount == 333.33 and .startsOn == \"$H_START\""
H_ROUNDING=$(jq -r .token <<<"$RESP_BODY")
request GET "$CLIENT_SR?status=ANSWERED" "$ANA"
expect Y45 "and Ana's list shows the same installment and the start Helena proposed, not the one Ana asked for" 200 \
  "any(.content[]; .token == \"$H_REQ\" and .currentProposal.token == \"$H_ROUNDING\" and .currentProposal.installmentAmount == 333.33
     and .currentProposal.startsOn == \"$H_START\" and .desiredStartDate == \"$START\")"
request GET "$CLIENT_SR?status=OPEN,ANSWERED" "$ANA"
expect Y46 "the open tab (OPEN and ANSWERED) in one call: a summary on every ANSWERED request, none on the OPEN ones" 200 \
  '(.content | length) > 0 and any(.content[]; .status == "ANSWERED")
   and all(.content[]; (.status == "OPEN" and .currentProposal == null) or (.status == "ANSWERED" and .currentProposal.status == "SENT"))'
request POST "$DRIVER_P/$H_ROUNDING/withdraw" "$HELENA"
expect Y47 "Helena withdraws it, and the request is OPEN again" 200 '.status == "WITHDRAWN"'

table "Proposals over HTTP — races and the re-check on accept"
H_P2=$(send_proposal "$HELENA" "$H_REQ" "$(p_body "$HELENA_VAN" "$LUCAS_ITEM")")
request GET "/api/vehicles/$HELENA_VAN" "$HELENA"
HELENA_VAN_BODY=$(jq -c '{plate, brand, model, manufactureYear, color, capacity}' <<<"$RESP_BODY")
request PUT "/api/vehicles/$HELENA_VAN" "$HELENA" "$(jq -c '.plate = "VAN9Z99"' <<<"$HELENA_VAN_BODY")"
expect Z1 "Helena changes the plate of the offered van" 200 '.plate == "VAN9Z99"'
request POST "$CLIENT_P/$H_P2/accept" "$ANA"
expect Z2 "Ana accepts after the plate changed" 409 "$STALE"
request GET "$CLIENT_P/$H_P2" "$ANA"
expect Z3 "the proposal is still SENT and shows the plate it was sent with" 200 '.status == "SENT" and .vehicle.plate == "VAN3C45"'
request PUT "/api/vehicles/$HELENA_VAN" "$HELENA" "$HELENA_VAN_BODY"
[[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | restore Helena's plate | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
request POST "$CLIENT_P/$H_P2/decline" "$ANA" "$(jq -nc '{reason: ("a" * 500)}')"
expect Z4 "Ana declines it with a reason of exactly 500 characters" 200 '.status == "DECLINED" and (.declineReason | length) == 500'
F_REQ=$(send_request "$(sr_body "$FABIO_D" "" "$LUCAS_WEEK")")
F_P2=$(send_proposal "$FABIO" "$F_REQ" "$(p_body "$FABIO_VAN" "$LUCAS_ITEM")")
LUCAS_ADDRESS=$(sql "select json_build_object('cityToken', city.token, 'street', address.street, 'number', address.number,
                       'zipCode', address.zip_code, 'neighborhood', address.neighborhood)
                     from dependent join address on address.id = dependent.address_id join city on city.id = address.city_id
                     where dependent.token = '$LUCAS'")
request PATCH "/api/dependent/$LUCAS" "$ANA" "$(jq -c '{address: (.neighborhood = "Vila Mariana")}' <<<"$LUCAS_ADDRESS")"
expect Z5 "the family moves Lucas to another neighborhood after the proposal" 200
request POST "$CLIENT_P/$F_P2/accept" "$ANA"
expect Z6 "Ana accepts after the move" 409 "$STALE"
check Z7 "the proposal stays SENT and the request ANSWERED" "SENT ANSWERED" "$(proposal_status "$F_P2") $(request_status "$F_REQ")"
request PATCH "/api/dependent/$LUCAS" "$ANA" "$(jq -c '{address: .}' <<<"$LUCAS_ADDRESS")"
[[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | move Lucas back | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
LUCAS_PERSONAL=$(sql "select json_build_object('name', name, 'phone', phone, 'birthDate', birth_date) from dependent where token = '$LUCAS'")
request PATCH "/api/dependent/$LUCAS" "$ANA" \
  "$(jq -c '{name: "Lucas Souza Prado", phone: "11988887777", birthDate: (.birthDate | split("-") | .[2] = (if .[2] == "02" then "03" else "02" end) | join("-"))}' <<<"$LUCAS_PERSONAL")"
expect Z8 "Ana corrects Lucas's name, phone and birth date" 200 '.name == "Lucas Souza Prado"'
check Z9 "two accepts at once after the correction: one 200, one 409" "200 409" \
  "$(in_parallel POST "$ANA" "" "$CLIENT_P/$F_P2/accept" "$CLIENT_P/$F_P2/accept")"
check Z10 "the corrected name did not block it: ACCEPTED, the request CONVERTED" "ACCEPTED CONVERTED" \
  "$(proposal_status "$F_P2") $(request_status "$F_REQ")"
G_REQ=$(send_request "$(sr_body "$GUSTAVO_D" "" "$LUCAS_WEEK")")
send_proposal "$GUSTAVO" "$G_REQ" "$P_BODY" >/dev/null
check Z11 "two new versions at once: one 201, one 409" "201 409" \
  "$(in_parallel POST "$GUSTAVO" "$P_BODY" "$DRIVER_SR/$G_REQ/proposals" "$DRIVER_SR/$G_REQ/proposals")"
check Z12 "the pair has a single SENT proposal, version 2" "1 2" \
  "$(sql "select count(*) || ' ' || max(version) filter (where status = 'SENT') from proposal
          where client_driver_id = (select client_driver_id from service_request where token = '$G_REQ') and status = 'SENT'")"
G_CURRENT=$(sql "select token from proposal where status = 'SENT'
                 and service_request_id = (select id from service_request where token = '$G_REQ')")
RACE=$(race "$CLIENT_P/$G_CURRENT/accept" "$ANA" "" "$DRIVER_SR/$G_REQ/proposals" "$GUSTAVO" "$P_BODY")
check Z13 "Ana accepts while Gustavo sends a new version: exactly one wins, the other gets 409" "ok" \
  "$([[ $RACE == "200 409" || $RACE == "409 201" ]] && echo ok || echo "$RACE")"
if [[ $RACE == "200 409" ]]; then RACE_WINNER="ACCEPTED CONVERTED"; else RACE_WINNER="SENT ANSWERED"; fi
check Z14 "never an ACCEPTED and a SENT proposal for the request" "1 $RACE_WINNER" \
  "$(sql "select count(*) || ' ' || min(proposal.status) || ' ' || min(request.status) from proposal
          join service_request request on request.id = proposal.service_request_id
          where request.token = '$G_REQ' and proposal.status in ('ACCEPTED', 'SENT')")"
request PATCH "/api/dependent/$LUCAS" "$ANA" "$LUCAS_PERSONAL"
[[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | restore Lucas's name, phone and birth date | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
echo
echo "ALL PASSED: $PASSED scenarios"
