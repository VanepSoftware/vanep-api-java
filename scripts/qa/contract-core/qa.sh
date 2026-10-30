#!/usr/bin/env bash
# Manual QA for the contract-core change (constitution rule 55).
# Runs against a live API backed by a THROWAWAY database that was seeded on an empty schema: it
# writes rows directly, flips seeded drivers and links, and re-applies migrations. See qa.md next
# to the change.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

WEEKDAYS=(MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY)

slots() {
  local leg=$1 shift_name=$2 start=$3 end=$4
  shift 4
  jq -nc --arg leg "$leg" --arg sh "$shift_name" --arg s "$start" --arg e "$end" \
    '$ARGS.positional | map({weekday: ., leg: $leg, shift: $sh, windowStart: $s}
      + (if $e == "-" then {} else {windowEnd: $e} end))' --args "$@"
}

weekday_round_trip() {
  jq -nc --argjson out "$(slots OUTBOUND MORNING 06:40 06:50 "${WEEKDAYS[@]}")" \
    --argjson back "$(slots RETURN AFTERNOON 12:30 - "${WEEKDAYS[@]}")" '$out + $back'
}

item() {
  jq -nc --arg d "$1" --argjson slots "$2" --argjson m "${3:-350}" \
    '{dependentToken: $d, monthlyAmount: $m, slots: $slots}'
}

contract_body() {
  jq -nc --arg l "$1" --arg s "$2" --arg e "$3" --argjson items "$4" \
    '{clientDriverToken: $l, startsOn: $s, endsOn: $e, totalAmount: 4200, installments: 12,
      dueDay: 5, items: $items}'
}

address() {
  jq -nc --arg c "$CITY" --arg s "$1" --arg n "$2" \
    '{cityToken: $c, street: $s, number: $n, zipCode: "01310100", neighborhood: "Bela Vista"}'
}

create_dependent() {
  local client=$1 name=$2 with_school=$3 with_address=$4 body
  body=$(jq -nc --arg n "$name" '{name: $n, shift: "MORNING"}')
  if [[ $with_school == yes ]]; then body=$(jq -c --arg s "$SCHOOL" '.schoolToken = $s' <<<"$body"); fi
  if [[ $with_address == yes ]]; then
    body=$(jq -c --argjson a "$(address "Rua dos Ipês" 40)" '.address = $a' <<<"$body")
  fi
  request POST /api/dependent "$client" "$body"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | dependent $name | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

create_link() {
  request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$1" --arg d "$2" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH"}')"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | link | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

link_status() {
  request GET "/api/client-drivers/$1" "$ADMIN"
  jq -r .status <<<"$RESP_BODY"
}

restore_drivers() {
  sql "update driver set approval_status = 'APPROVED' where approval_status <> 'APPROVED';" >/dev/null || true
}

cleanup() {
  restore_drivers
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

echo "== setup"
[[ $(count client_driver) == 2 && $(count contract) -le 1 && $(count dependent) == 2 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (the seed link and the seeded request's link, two dependents)" >&2
  exit 1
}
ADMIN=$(login "$ADMIN_EMAIL")
ANA=$(login ana.souza@seed.vanep.com.br)
BRUNO=$(login bruno.lima@seed.vanep.com.br)
CARLA=$(login carla.nunes@seed.vanep.com.br)
FABIO=$(login fabio.teixeira@seed.vanep.com.br)
GUSTAVO=$(login gustavo.santos@seed.vanep.com.br)
HELENA=$(login helena.costa@seed.vanep.com.br)
if [[ $(count school) == 0 ]]; then
  sql "insert into school (token, name, city_id) select 'qaseedschool', 'Escola Municipal Seed', id from city where ibge_code = '3550308'" >/dev/null
fi
SCHOOL=$(sql "select token from school where name = 'Escola Municipal Seed' order by id limit 1")
CITY=$(sql "select token from city where ibge_code = '3550308'")
[[ -n $CITY ]] || { echo "SETUP FAIL | the city catalog is not seeded yet; wait for the cities in the seed log" >&2; exit 1; }
request GET /api/dependent "$ANA"
LUCAS=$(jq -r 'map(select(.name == "Lucas Souza"))[0].token' <<<"$RESP_BODY")
MARINA=$(jq -r 'map(select(.name == "Marina Souza"))[0].token' <<<"$RESP_BODY")
for dependent in $(jq -r 'map(select(.school == null or .address == null))[].token' <<<"$RESP_BODY"); do
  request PATCH "/api/dependent/$dependent" "$ANA" "$(jq -nc --arg s "$SCHOOL" --arg c "$CITY" \
    '{schoolToken: $s, address: {cityToken: $c, street: "Rua das Acácias", number: "250", zipCode: "01001000", neighborhood: "Centro"}}')"
  [[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | complete seeded dependent | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
done
request GET /api/client-drivers "$ADMIN"
SEED_LINK=$(jq -r '.content[0].token' <<<"$RESP_BODY")
ANA_CLIENT=$(jq -r '.content[0].clientToken' <<<"$RESP_BODY")
FABIO_DRIVER=$(jq -r '.content[0].driverToken' <<<"$RESP_BODY")
request GET /api/drivers/recommended "$ANA"
driver_token() { jq -er --arg n "$1" '(if type == "array" then . else .content end)[] | select(.name == $n) | .token' <<<"$RESP_BODY"; }
GUSTAVO_DRIVER=$(driver_token "Gustavo Santos")
HELENA_DRIVER=$(driver_token "Helena Costa")
echo "tokens ok"

table "Operation shift on trips"
GUSTAVO_TRIPS="select count(*) from trip t join driver d on d.id = t.driver_id join users u on u.id = d.user_id where u.email = 'gustavo.santos@seed.vanep.com.br'"
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":"MORNING"}'
expect S1 "a driver starts the morning route" 200 '.shift == "MORNING" and .status == "IN_PROGRESS"'
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":"AFTERNOON"}'
expect S2 "the same driver starts the afternoon route" 200 '.shift == "AFTERNOON"'
TRIPS_BEFORE=$(sql "$GUSTAVO_TRIPS")
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":"FULLTIME"}'
expect S3 "FULLTIME is not an operation shift" 400
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":"EVENING"}'
expect S4 "unknown shift" 400
request POST /api/drivers/me/trips/start "$GUSTAVO" '{}'
expect S5 "missing shift" 400
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":null}'
expect S6 "null shift" 400
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":["MORNING"]}'
expect S7 "shift with the wrong JSON type" 400
request POST /api/drivers/me/trips/start "$GUSTAVO" '{"shift":'
expect S8 "malformed JSON" 400
request POST /api/drivers/me/trips/start "" '{"shift":"NIGHT"}'
expect S9 "no token" 401
request POST /api/drivers/me/trips/start "not-a-jwt" '{"shift":"NIGHT"}'
expect S10 "invalid token" 401
request POST /api/drivers/me/trips/start "$ANA" '{"shift":"NIGHT"}'
expect S11 "a client cannot start a route" 403
check S12 "the rejected starts created no trip" "$TRIPS_BEFORE" "$(sql "$GUSTAVO_TRIPS")"
request POST /api/trips "$ADMIN" "$(jq -nc --arg d "$HELENA_DRIVER" '{driverToken: $d, serviceDate: "2027-03-01", shift: "FULLTIME"}')"
expect S13 "the admin cannot create a FULLTIME trip" 400
request POST /api/trips "$ADMIN" "$(jq -nc --arg d "$HELENA_DRIVER" '{driverToken: $d, serviceDate: "2027-03-01", shift: "NIGHT"}')"
expect S14 "the admin creates a NIGHT trip" 201 '.shift == "NIGHT"'
HELENA_TRIP=$(jq -r .token <<<"$RESP_BODY")
request PATCH "/api/trips/$HELENA_TRIP" "$ADMIN" '{"shift":"FULLTIME"}'
expect S15 "the admin cannot patch a trip to FULLTIME" 400
request PATCH "/api/trips/$HELENA_TRIP" "$HELENA" '{"shift":"FULLTIME"}'
expect S16 "the owner cannot patch a trip to FULLTIME" 400
request GET "/api/trips/$HELENA_TRIP" "$ADMIN"
expect S17 "the rejected patches left the shift unchanged" 200 '.shift == "NIGHT"'
request PATCH "/api/trips/$HELENA_TRIP" "$ADMIN" '{"shift":"AFTERNOON"}'
expect S18 "the admin moves the trip to another operation shift" 200 '.shift == "AFTERNOON"'
sql "insert into trip (token, driver_id, service_date, shift, status) select 'qalegacyfulltime', d.id, date '2027-03-02', 'FULLTIME', 'SCHEDULED' from driver d join users u on u.id = d.user_id where u.email = 'helena.costa@seed.vanep.com.br'" >/dev/null
# V62 dropped dependent.shift, which V56 comments: the column comes back only while V56 runs again.
sql "alter table dependent add column shift varchar(16)" >/dev/null
apply_migration V56
sql "alter table dependent drop column shift" >/dev/null
check S22 "re-applying V56 turns a stored FULLTIME trip into MORNING" MORNING "$(sql "select shift from trip where token = 'qalegacyfulltime'")"
request GET /api/trips/qalegacyfulltime "$ADMIN"
expect S23 "the converted trip is readable through the API" 200 '.shift == "MORNING"'
check S24 "V56 left the other shifts alone" AFTERNOON "$(sql "select shift from trip where token = '$HELENA_TRIP'")"
check S25 "trip.shift is documented" "yes" \
  "$(sql "select case when col_description('trip'::regclass, (select attnum from pg_attribute where attrelid = 'trip'::regclass and attname = 'shift')) is not null then 'yes' else 'no' end")"

table "V57: schedule, schedule_slot and unlinked_passenger (database checks)"
NEW_SCHEDULE="insert into schedule default values"
SLOT="insert into schedule_slot (schedule_id, weekday, leg, shift, window_start, window_end) values"
LAST_SCHEDULE="(select max(id) from schedule)"
check_sql_rejected D1 "the same weekday and leg twice in one schedule" schedule_slot_weekday_leg_active_key \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '06:40', null); $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '07:00', null)"
check_sql_accepted D2 "outbound and return on the same weekday" \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '06:40', null); $SLOT ($LAST_SCHEDULE, 'MONDAY', 'RETURN', 'AFTERNOON', '12:30', null)"
check_sql_accepted D3 "a soft-deleted slot frees its weekday and leg" \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '06:40', null); update schedule_slot set deleted_at = now() where schedule_id = $LAST_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '07:00', null)"
check_sql_rejected D4 "window end before its start" schedule_slot_window_check \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '07:00', '06:50')"
check_sql_rejected D5 "window end equal to its start" schedule_slot_window_check \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'OUTBOUND', 'MORNING', '07:00', '07:00')"
check_sql_accepted D6 "window without an end" \
  "$NEW_SCHEDULE; $SLOT ($LAST_SCHEDULE, 'MONDAY', 'RETURN', 'AFTERNOON', '12:30', null)"
check_sql_rejected D7 "a slot that points to no schedule" schedule_slot_schedule_id_fkey \
  "$SLOT (-1, 'MONDAY', 'OUTBOUND', 'MORNING', '06:40', null)"
PASSENGER="insert into unlinked_passenger (token, driver_id, name, school_id, school_shift, address_id, schedule_id) select"
DRIVER_ID="(select min(id) from driver)"
SCHOOL_ID="(select min(id) from school)"
NEW_ADDRESS="insert into address (token, city_id, street, zip_code) select substr(md5(random()::text), 1, 25), min(id), 'Rua QA', '01001000' from city"
LAST_ADDRESS="(select max(id) from address)"
check_sql_rejected D8 "two unlinked passengers sharing one schedule" unlinked_passenger_schedule_id_active_key \
  "$NEW_SCHEDULE; $NEW_ADDRESS; $PASSENGER 'qap1', $DRIVER_ID, 'A', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE; $NEW_ADDRESS; $PASSENGER 'qap2', $DRIVER_ID, 'B', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE"
check_sql_rejected D9 "two unlinked passengers sharing one address" unlinked_passenger_address_id_active_key \
  "$NEW_ADDRESS; $NEW_SCHEDULE; $PASSENGER 'qap1', $DRIVER_ID, 'A', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE; $NEW_SCHEDULE; $PASSENGER 'qap2', $DRIVER_ID, 'B', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE"
check_sql_accepted D10 "a soft-deleted passenger frees its schedule and address" \
  "$NEW_ADDRESS; $NEW_SCHEDULE; $PASSENGER 'qap1', $DRIVER_ID, 'A', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE; update unlinked_passenger set deleted_at = now() where token = 'qap1'; $PASSENGER 'qap2', $DRIVER_ID, 'B', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE"
check_sql_rejected D11 "two live passengers with the same token" unlinked_passenger_token_active_key \
  "$NEW_ADDRESS; $NEW_SCHEDULE; $PASSENGER 'qap1', $DRIVER_ID, 'A', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE; $NEW_ADDRESS; $NEW_SCHEDULE; $PASSENGER 'qap1', $DRIVER_ID, 'B', $SCHOOL_ID, 'MORNING', $LAST_ADDRESS, $LAST_SCHEDULE"
check D12 "the rolled-back checks left no passenger behind" 0 "$(count unlinked_passenger)"

table "V58: contract and contract_item (database checks)"
CONTRACTS_BEFORE_CHECKS=$(count contract)
QA_LINK="insert into client_driver (token, client_id, driver_id, status, origin) select 'qalink', c.id, d.id, 'PENDING', 'CLIENT_SEARCH' from client c join users uc on uc.id = c.user_id, driver d join users ud on ud.id = d.user_id where uc.email = 'bruno.lima@seed.vanep.com.br' and ud.email = 'gustavo.santos@seed.vanep.com.br'"
LINK_ID="(select id from client_driver where token = 'qalink')"
CONTRACT="insert into contract (token, client_driver_id, status, starts_on, ends_on, total_amount, installments, due_day) select"
ACTIVE_CONTRACT="$CONTRACT 'qac1', $LINK_ID, 'ACTIVE', date '2027-02-01', date '2027-12-17', 4200, 12, 5"
ended() { echo "$CONTRACT '$1', $LINK_ID, 'ENDED', date '${2:-2027-02-01}', date '${3:-2027-12-17}', ${4:-4200}, ${5:-12}, ${6:-5}"; }
check_sql_rejected K1 "a second ACTIVE contract on the same link" contract_client_driver_active_key \
  "$QA_LINK; $ACTIVE_CONTRACT; $CONTRACT 'qac2', $LINK_ID, 'ACTIVE', date '2027-02-01', date '2027-12-17', 4200, 12, 5"
check_sql_accepted K2 "an ENDED contract next to the ACTIVE one" "$QA_LINK; $ACTIVE_CONTRACT; $(ended qac2)"
check_sql_accepted K3 "a new ACTIVE contract once the old one is soft-deleted" \
  "$QA_LINK; $ACTIVE_CONTRACT; update contract set deleted_at = now() where token = 'qac1'; $CONTRACT 'qac2', $LINK_ID, 'ACTIVE', date '2027-02-01', date '2027-12-17', 4200, 12, 5"
check_sql_rejected K4 "end date on the start date" contract_period_check "$QA_LINK; $(ended qac1 2027-02-01 2027-02-01)"
check_sql_rejected K5 "total amount of zero" contract_total_amount_check "$QA_LINK; $(ended qac1 2027-02-01 2027-12-17 0)"
check_sql_rejected K6 "thirteen installments" contract_installments_check "$QA_LINK; $(ended qac1 2027-02-01 2027-12-17 4200 13)"
check_sql_rejected K7 "zero installments" contract_installments_check "$QA_LINK; $(ended qac1 2027-02-01 2027-12-17 4200 0)"
check_sql_rejected K8 "due day 29" contract_due_day_check "$QA_LINK; $(ended qac1 2027-02-01 2027-12-17 4200 12 29)"
check_sql_accepted K9 "due day 28 and one installment" "$QA_LINK; $(ended qac1 2027-02-01 2027-12-17 4200 1 28)"
check_sql_rejected K10 "two live contracts with the same token" contract_token_active_key "$QA_LINK; $(ended qac1); $(ended qac1)"
ITEM="insert into contract_item (token, contract_id, dependent_id, school_id, pickup_city_id, pickup_street, monthly_amount, schedule_id) select"
item_row() { echo "$ITEM '$1', (select id from contract where token = 'qac1'), d.id, d.school_id, a.city_id, $2, $3, $4 from dependent d join address a on a.id = d.address_id order by d.id limit 1"; }
check_sql_rejected K11 "two items sharing one schedule" contract_item_schedule_id_active_key \
  "$QA_LINK; $ACTIVE_CONTRACT; $NEW_SCHEDULE; $(item_row qai1 a.street 350 "$LAST_SCHEDULE"); $(item_row qai2 a.street 350 "$LAST_SCHEDULE")"
check_sql_rejected K12 "an item priced at zero" contract_item_monthly_amount_check \
  "$QA_LINK; $ACTIVE_CONTRACT; $NEW_SCHEDULE; $(item_row qai1 a.street 0 "$LAST_SCHEDULE")"
check_sql_rejected K13 "an item without a pickup street" pickup_street \
  "$QA_LINK; $ACTIVE_CONTRACT; $NEW_SCHEDULE; $(item_row qai1 null 350 "$LAST_SCHEDULE")"
check_sql_accepted K14 "a valid item copying the dependent's school and address" \
  "$QA_LINK; $ACTIVE_CONTRACT; $NEW_SCHEDULE; $(item_row qai1 a.street 350 "$LAST_SCHEDULE")"
check K15 "the rolled-back checks left no contract behind" "$CONTRACTS_BEFORE_CHECKS" "$(count contract)"

table "V59: contract permissions of the ADMIN bundle"
CONTRACT_PERMISSIONS='["list_contracts","show_contract","create_contract","update_contract","delete_contract","restore_contract"]'
check P1 "a real admin token carries the six contract permissions" 6 \
  "$(claims "$ADMIN" | jq --argjson p "$CONTRACT_PERMISSIONS" '[.permissions[] | select(IN($p[]))] | length')"
check P2 "no other bundle has a contract permission" 0 \
  "$(sql "select count(*) from role_permissions where name <> 'ADMIN' and permissions ?| array['list_contracts','show_contract','create_contract','update_contract','delete_contract','restore_contract']")"
ADMIN_PERMISSIONS_BEFORE=$(sql "select jsonb_array_length(permissions) from role_permissions where name = 'ADMIN'")
sql "update role_permissions set permissions = permissions - 'list_contracts' - 'show_contract' - 'create_contract' - 'update_contract' - 'delete_contract' - 'restore_contract' where name = 'ADMIN'" >/dev/null
apply_migration V59
check P3 "re-applying V59 on a bundle without them adds back exactly the six" "$ADMIN_PERMISSIONS_BEFORE" \
  "$(sql "select jsonb_array_length(permissions) from role_permissions where name = 'ADMIN'")"
apply_migration V59
check P4 "re-applying V59 again changes nothing" "$ADMIN_PERMISSIONS_BEFORE" \
  "$(sql "select jsonb_array_length(permissions) from role_permissions where name = 'ADMIN'")"
ADMIN=$(login "$ADMIN_EMAIL")
check P5 "a fresh admin token still carries the six" 6 \
  "$(claims "$ADMIN" | jq --argjson p "$CONTRACT_PERMISSIONS" '[.permissions[] | select(IN($p[]))] | length')"

echo
echo "== setup for the contract endpoints"
PEDRO=$(create_dependent "$BRUNO" "Pedro Lima" yes yes)
JULIA=$(create_dependent "$BRUNO" "Julia Lima" yes yes)
THEO=$(create_dependent "$BRUNO" "Theo Lima" yes yes)
NO_ADDRESS=$(create_dependent "$BRUNO" "Sem Endereco Lima" yes no)
NO_SCHOOL=$(create_dependent "$BRUNO" "Sem Escola Lima" no yes)
CAIO=$(create_dependent "$CARLA" "Caio Nunes" yes yes)
request GET /api/clients/me "$BRUNO"
BRUNO_CLIENT=$(jq -r .token <<<"$RESP_BODY")
request GET /api/clients/me "$CARLA"
CARLA_CLIENT=$(jq -r .token <<<"$RESP_BODY")
# The seeded service request (service-request-proposal) already links Ana to Gustavo, PENDING.
ANA_GUSTAVO=$(sql "select l.token from client_driver l join client c on c.id = l.client_id join driver d on d.id = l.driver_id
                   where c.token = '$ANA_CLIENT' and d.token = '$GUSTAVO_DRIVER'")
[[ -n $ANA_GUSTAVO ]] || { echo "SETUP FAIL | the seeded service request did not link Ana to Gustavo" >&2; exit 1; }
ANA_HELENA=$(create_link "$ANA_CLIENT" "$HELENA_DRIVER")
BRUNO_HELENA=$(create_link "$BRUNO_CLIENT" "$HELENA_DRIVER")
BRUNO_GUSTAVO=$(create_link "$BRUNO_CLIENT" "$GUSTAVO_DRIVER")
BRUNO_FABIO=$(create_link "$BRUNO_CLIENT" "$FABIO_DRIVER")
CARLA_GUSTAVO=$(create_link "$CARLA_CLIENT" "$GUSTAVO_DRIVER")
ROUND_TRIP=$(weekday_round_trip)
if [[ $(count contract) == 0 ]]; then
  SEED_SLOTS=$(jq -nc --argjson out "$(slots OUTBOUND MORNING 06:40 - "${WEEKDAYS[@]}")" \
    --argjson back "$(slots RETURN AFTERNOON 12:10 - "${WEEKDAYS[@]}")" '$out + $back')
  request POST /api/contracts "$ADMIN" "$(contract_body "$SEED_LINK" "$(date +%Y-%m-01)" \
    "$(date -v+11m +%Y-%m-01 2>/dev/null || date -d "+11 months" +%Y-%m-01)" "[$(item "$LUCAS" "$SEED_SLOTS")]")"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | baseline contract | HTTP $RESP_STATUS $RESP_BODY" >&2; exit 1; }
fi
request GET /api/contracts "$ADMIN"
SEED_CONTRACT=$(jq -r '.content[0].token' <<<"$RESP_BODY")
OUTBOUND_WEEK=$(slots OUTBOUND MORNING 06:40 06:50 "${WEEKDAYS[@]}")
RETURN_WEEK=$(slots RETURN AFTERNOON 12:30 - "${WEEKDAYS[@]}")
MONDAY_OUTBOUND=$(slots OUTBOUND MORNING 06:40 06:50 MONDAY)
THEO_BODY=$(contract_body "$BRUNO_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$THEO" "$MONDAY_OUTBOUND")]")
end_contract() {
  request PATCH "/api/contracts/$1" "$ADMIN" '{"status":"ENDED"}'
  [[ $RESP_STATUS == 200 ]] || { echo "SETUP FAIL | end contract | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
}
echo "fixtures ok"

table "GET /api/contracts and GET /api/contracts/{token}"
request GET /api/contracts "$ADMIN"
expect C1 "the admin lists the seeded contract" 200 '.totalElements == 1 and .content[0].status == "ACTIVE"'
request GET "/api/contracts/$SEED_CONTRACT" "$ANA"
expect C2 "the client of the link reads it" 200 '.clientName == "Ana Souza" and (.items | length) == 1 and (.items[0].slots | length) == 10'
request GET "/api/contracts/$SEED_CONTRACT" "$FABIO"
expect C3 "the driver of the link reads it" 200 '.driverName == "Fabio Teixeira"'
expect C4 "the response carries tokens and no id field" 200 \
  '([.. | objects | keys[]] | index("id")) == null and .token != null and .clientDriverToken != null and .items[0].token != null and .items[0].dependentToken != null'
request GET "/api/contracts/$SEED_CONTRACT" "$BRUNO"
expect C5 "another client cannot read it" 403
request GET "/api/contracts/$SEED_CONTRACT" "$GUSTAVO"
expect C6 "another driver cannot read it" 403
request GET "/api/contracts/$SEED_CONTRACT" ""
expect C7 "no token" 401
request GET "/api/contracts/$SEED_CONTRACT" "not-a-jwt"
expect C8 "invalid token" 401
request GET /api/contracts/doesnotexist "$ANA"
expect C9 "a party asking for an unknown contract" 403
request GET /api/contracts/doesnotexist "$ADMIN"
expect C10 "the admin asking for an unknown contract, pt-BR message" 404 '.detail == "Contrato não encontrado."'
request GET /api/contracts "$ANA"
expect C11 "a client cannot list every contract" 403
request GET "/api/client-drivers/$SEED_LINK/contracts" "$ANA"
expect C12 "the client lists the contracts of their link" 200 'length == 1'
request GET "/api/client-drivers/$SEED_LINK/contracts" "$FABIO"
expect C13 "the driver lists the contracts of their link" 200 'length == 1'
request GET "/api/client-drivers/$SEED_LINK/contracts" "$BRUNO"
expect C14 "another client cannot list them" 403
request GET "/api/client-drivers/$SEED_LINK/contracts" "$ADMIN"
expect C15 "the admin lists them" 200 'length == 1'

table "POST /api/contracts — the admin creates active contracts"
request POST /api/contracts "$ADMIN" "$(contract_body "$ANA_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$MARINA" "$OUTBOUND_WEEK")]")"
expect H1 "outbound Monday to Friday with one driver" 201 \
  '.status == "ACTIVE" and .items[0].pickupStreet == "Rua das Acácias" and .items[0].pickupNumber == "250" and .items[0].schoolName == "Escola Municipal Seed" and .items[0].pickupCityName == "São Paulo" and (.items[0].slots | length) == 5'
MARINA_OUTBOUND=$(jq -r .token <<<"$RESP_BODY")
check H2 "creating the contract activated the pending link" ACTIVE "$(link_status "$ANA_GUSTAVO")"
request POST /api/contracts "$ADMIN" "$(contract_body "$ANA_HELENA" 2027-02-01 2027-12-17 "[$(item "$MARINA" "$RETURN_WEEK")]")"
expect H3 "the same student, return only, with another driver" 201 '.items[0].slots | all(.leg == "RETURN" and .windowEnd == null)'
MARINA_RETURN=$(jq -r .token <<<"$RESP_BODY")
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_HELENA" 2027-02-01 2027-12-17 "[$(item "$PEDRO" "$ROUND_TRIP" 350), $(item "$JULIA" "$ROUND_TRIP" 300)]")"
expect H4 "siblings share one contract with their own prices" 201 '(.items | length) == 2 and ([.items[].monthlyAmount] | sort) == [300, 350]'
SIBLINGS=$(jq -r .token <<<"$RESP_BODY")
check H5 "the siblings contract stored two items and twenty slots" "2 20" \
  "$(sql "select count(distinct i.id) || ' ' || count(s.id) from contract_item i join contract c on c.id = i.contract_id join schedule_slot s on s.schedule_id = i.schedule_id where c.token = '$SIBLINGS'")"

table "POST /api/contracts — terms, items and schedule rejected"
CONTRACTS_BEFORE="$(count contract) $(count contract_item) $(count schedule) $(count schedule_slot)"
theo() { jq -c "$1" <<<"$THEO_BODY"; }
request POST /api/contracts "$ADMIN" "$(theo 'del(.endsOn)')"
expect V1 "missing endsOn" 400
request POST /api/contracts "$ADMIN" "$(theo 'del(.startsOn)')"
expect V2 "missing startsOn" 400
request POST /api/contracts "$ADMIN" "$(theo '.endsOn = .startsOn')"
expect V3 "end date on the start date, pt-BR message" 422 '.detail == "A data de término deve ser posterior à data de início."'
request POST /api/contracts "$ADMIN" "$(theo '.endsOn = "2027-01-31"')"
expect V4 "end date before the start date" 422 '.detail == "A data de término deve ser posterior à data de início."'
request POST /api/contracts "$ADMIN" "$(theo '.endsOn = "2028-02-02"')"
expect V5 "one day past twelve months, pt-BR message" 422 '.detail == "O período do contrato não pode passar de doze meses."'
request POST /api/contracts "$ADMIN" "$(theo '.dueDay = 31')"
expect V6 "due day 31" 400
request POST /api/contracts "$ADMIN" "$(theo '.dueDay = 29')"
expect V7 "due day 29" 400
request POST /api/contracts "$ADMIN" "$(theo '.dueDay = 0')"
expect V8 "due day 0" 400
request POST /api/contracts "$ADMIN" "$(theo '.installments = 13')"
expect V9 "thirteen installments" 400
request POST /api/contracts "$ADMIN" "$(theo '.installments = 0')"
expect V10 "zero installments" 400
request POST /api/contracts "$ADMIN" "$(theo '.totalAmount = 0')"
expect V11 "total amount of zero" 400
request POST /api/contracts "$ADMIN" "$(theo '.totalAmount = -10')"
expect V12 "negative total amount" 400
request POST /api/contracts "$ADMIN" "$(theo '.totalAmount = "abc"')"
expect V13 "total amount with the wrong JSON type" 400
request POST /api/contracts "$ADMIN" "$(theo '.items = []')"
expect V14 "no items" 400
request POST /api/contracts "$ADMIN" "$(theo 'del(.items)')"
expect V15 "missing items" 400
request POST /api/contracts "$ADMIN" "$(theo '.items[0].monthlyAmount = 0')"
expect V16 "item priced at zero" 400
request POST /api/contracts "$ADMIN" "$(theo '.items += .items')"
expect V17 "the same dependent twice, pt-BR message" 400 '.detail == "O mesmo dependente não pode aparecer duas vezes no contrato."'
request POST /api/contracts "$ADMIN" "$(jq -c --arg d "$LUCAS" '.items[0].dependentToken = $d' <<<"$THEO_BODY")"
expect V18 "a dependent of another client, pt-BR message" 422 '.detail == "O dependente não pertence ao cliente deste vínculo."'
request POST /api/contracts "$ADMIN" "$(theo '.items[0].dependentToken = "doesnotexist"')"
expect V19 "unknown dependent" 404 '.detail == "Dependente não encontrado."'
request POST /api/contracts "$ADMIN" "$(jq -c --arg d "$NO_ADDRESS" '.items[0].dependentToken = $d' <<<"$THEO_BODY")"
expect V20 "a dependent without an address, pt-BR message" 422 '.detail == "O dependente precisa ter endereço para ser contratado."'
request POST /api/contracts "$ADMIN" "$(jq -c --arg d "$NO_SCHOOL" '.items[0].dependentToken = $d' <<<"$THEO_BODY")"
expect V21 "a dependent without a school, pt-BR message" 422 '.detail == "O dependente precisa ter escola para ser contratado."'
request POST /api/contracts "$ADMIN" "$(theo '.clientDriverToken = "doesnotexist"')"
expect V22 "unknown link" 404 '.detail == "Vínculo não encontrado."'
request POST /api/contracts "$ADMIN" "$(theo '.clientDriverToken = "  "')"
expect V23 "blank link token" 400
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots = []')"
expect V24 "empty schedule, pt-BR message" 400 '.detail == "A agenda precisa de pelo menos um horário."'
request POST /api/contracts "$ADMIN" "$(theo 'del(.items[0].slots)')"
expect V25 "missing schedule" 400 '.detail == "A agenda precisa de pelo menos um horário."'
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots += .items[0].slots')"
expect V26 "the same weekday and leg twice, pt-BR message" 400 '.detail == "O mesmo dia e trecho não pode aparecer duas vezes na agenda."'
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].windowEnd = "06:30"')"
expect V27 "window end before its start, pt-BR message" 400 '.detail == "O fim da janela deve ser posterior ao início."'
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].windowEnd = "06:40"')"
expect V28 "window end equal to its start" 400 '.detail == "O fim da janela deve ser posterior ao início."'
request POST /api/contracts "$ADMIN" "$(theo 'del(.items[0].slots[0].shift)')"
expect V29 "a slot without shift (never derived from the time)" 400
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].shift = "FULLTIME"')"
expect V30 "a slot with the FULLTIME shift" 400
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].weekday = "FUNDAY"')"
expect V31 "unknown weekday" 400
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].windowStart = "25:00"')"
expect V32 "impossible time" 400
request POST /api/contracts "$ADMIN" '{"clientDriverToken":'
expect V33 "malformed JSON" 400
request POST /api/contracts "$ADMIN" "$THEO_BODY" "text/plain"
expect V34 "wrong content type" 415
request POST /api/contracts "" "$THEO_BODY"
expect V35 "no token" 401
request POST /api/contracts "$BRUNO" "$THEO_BODY"
expect V36 "the client of the link cannot create" 403
request POST /api/contracts "$GUSTAVO" "$THEO_BODY"
expect V37 "the driver of the link cannot create" 403
sql "update driver set approval_status = 'PENDING' where user_id = (select id from users where email = 'gustavo.santos@seed.vanep.com.br')" >/dev/null
request POST /api/contracts "$ADMIN" "$THEO_BODY"
expect V38 "a driver pending approval, pt-BR message" 422 '.detail == "O motorista não está aprovado para ser contratado."'
restore_drivers
sql "update client_driver set status = 'BLOCKED', blocked_at = now(), blocked_by = (select id from users where email = 'gustavo.santos@seed.vanep.com.br') where token = '$BRUNO_GUSTAVO'" >/dev/null
request POST /api/contracts "$ADMIN" "$THEO_BODY"
expect V39 "a blocked link, pt-BR message" 422 '.detail == "Um vínculo bloqueado não pode ser contratado."'
sql "update client_driver set status = 'PENDING', blocked_at = null, blocked_by = null where token = '$BRUNO_GUSTAVO'" >/dev/null
request POST /api/contracts "$ADMIN" "$(contract_body "$SEED_LINK" 2027-02-01 2027-12-17 "[$(item "$LUCAS" "$(slots RETURN NIGHT 22:00 - SATURDAY)")]")"
expect V40 "a second active contract on the same link, pt-BR message" 409 '.detail == "Este vínculo já tem um contrato ativo."'
check V41 "the rejected creates stored nothing" "$CONTRACTS_BEFORE" "$(count contract) $(count contract_item) $(count schedule) $(count schedule_slot)"
check V42 "and left the link pending" PENDING "$(link_status "$BRUNO_GUSTAVO")"

table "POST /api/contracts — limits accepted"
request POST /api/contracts "$ADMIN" "$(theo '.endsOn = "2028-02-01"')"
expect L1 "exactly twelve months" 201
end_contract "$(jq -r .token <<<"$RESP_BODY")"
request POST /api/contracts "$ADMIN" "$(theo '.startsOn = "2027-07-01" | .endsOn = "2028-06-30"')"
expect L2 "a school year that crosses the calendar year" 201
end_contract "$(jq -r .token <<<"$RESP_BODY")"
request POST /api/contracts "$ADMIN" "$(theo '.dueDay = 28 | .installments = 1')"
expect L3 "due day 28 and a single installment" 201
end_contract "$(jq -r .token <<<"$RESP_BODY")"
request POST /api/contracts "$ADMIN" "$(theo '.items[0].slots[0].windowEnd = "06:41" | .totalAmount = 0.01 | .items[0].monthlyAmount = 0.01')"
expect L4 "one-minute window and one-cent amounts" 201
end_contract "$(jq -r .token <<<"$RESP_BODY")"
request POST /api/contracts "$ADMIN" "$(theo '.birthDate = "2015-01-01" | .status = "ENDED" | .items[0].pickupStreet = "Rua Falsa"')"
expect L5 "unknown and read-only fields in the body are ignored" 201 '.status == "ACTIVE" and .items[0].pickupStreet == "Rua dos Ipês"'
end_contract "$(jq -r .token <<<"$RESP_BODY")"

table "Weekday and leg collisions"
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$PEDRO" "$MONDAY_OUTBOUND")]")"
expect X1 "the dependent already rides that weekday and leg, pt-BR message" 409 '.detail == "O dependente já tem este dia e trecho em outro contrato assinado e não encerrado."'
request PATCH "/api/contracts/$SIBLINGS" "$ADMIN" '{"status":"SUSPENDED"}'
expect X2 "the admin suspends the siblings contract" 200 '.status == "SUSPENDED"'
check X3 "a suspended contract keeps the link active" ACTIVE "$(link_status "$BRUNO_HELENA")"
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$PEDRO" "$MONDAY_OUTBOUND")]")"
expect X4 "a suspended contract still reserves its slots" 409
request PATCH "/api/contracts/$SIBLINGS" "$ADMIN" '{"status":"ACTIVE"}'
expect X5 "reactivating the suspended contract never collides" 200 '.status == "ACTIVE"'
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$PEDRO" "$(slots OUTBOUND NIGHT 18:00 - SATURDAY)")]")"
expect X6 "a weekday the dependent does not ride yet" 201
end_contract "$(jq -r .token <<<"$RESP_BODY")"

table "PATCH /api/contracts/{token} — status only, through the state machine"
request POST /api/contracts "$ADMIN" "$THEO_BODY"
expect T0 "a new contract on a link whose contracts all ended" 201
THEO_CONTRACT=$(jq -r .token <<<"$RESP_BODY")
check T1 "the link went from INACTIVE back to ACTIVE, same link" "ACTIVE same link" \
  "$(link_status "$BRUNO_GUSTAVO") $([[ $(jq -r .clientDriverToken <<<"$RESP_BODY") == "$BRUNO_GUSTAVO" ]] && echo same link)"
request GET "/api/contracts/$THEO_CONTRACT" "$ADMIN"
BEFORE_PATCH=$(jq -c 'del(.updatedAt)' <<<"$RESP_BODY")
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{}'
expect T2 "an empty PATCH" 200
AFTER_PATCH=$(jq -c 'del(.updatedAt)' <<<"$RESP_BODY")
check T3 "the empty PATCH changed no stored field" unchanged "$([[ $AFTER_PATCH == "$BEFORE_PATCH" ]] && echo unchanged || echo "$AFTER_PATCH")"
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"endsOn":"2030-01-01","totalAmount":1,"installments":1}'
expect T4 "dates and amounts are not patchable" 200 '.endsOn == "2027-12-17" and .totalAmount == 4200 and .installments == 12'
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":null}'
expect T5 "null status, pt-BR message" 400 '.detail == "O status não pode ser nulo."'
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":"FINISHED"}'
expect T6 "unknown status" 400
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":'
expect T7 "malformed JSON" 400
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":"SUPERSEDED"}'
expect T8 "superseding by PATCH, pt-BR message" 422 '.detail == "Um contrato só é substituído por um aditivo."'
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":"AWAITING_SIGNATURES"}'
expect T9 "ACTIVE back to AWAITING_SIGNATURES, pt-BR message" 409 '.detail == "Esta mudança de status não é permitida."'
request PATCH "/api/contracts/$THEO_CONTRACT" "$GUSTAVO" '{"status":"ENDED"}'
expect T10 "the driver of the link cannot end it" 403
request PATCH "/api/contracts/$THEO_CONTRACT" "$BRUNO" '{"status":"ENDED"}'
expect T11 "the client of the link cannot end it" 403
request PATCH "/api/contracts/$THEO_CONTRACT" "" '{"status":"ENDED"}'
expect T12 "no token" 401
request PATCH /api/contracts/doesnotexist "$ADMIN" '{"status":"ENDED"}'
expect T13 "unknown contract" 404
request GET "/api/contracts/$THEO_CONTRACT" "$ADMIN"
expect T14 "the rejected patches left it ACTIVE" 200 '.status == "ACTIVE"'
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":"ENDED"}'
expect T15 "the admin ends the contract" 200 '.status == "ENDED"'
check T16 "ending the only live contract deactivates the link" INACTIVE "$(link_status "$BRUNO_GUSTAVO")"
request PATCH "/api/contracts/$THEO_CONTRACT" "$ADMIN" '{"status":"ACTIVE"}'
expect T17 "reopening an ended contract" 409
request GET "/api/contracts/$THEO_CONTRACT" "$ADMIN"
expect T18 "the ended contract stays ENDED" 200 '.status == "ENDED"'

table "DELETE and POST /restore"
request DELETE "/api/contracts/$MARINA_OUTBOUND" "$GUSTAVO"
expect R1 "the driver of the link cannot delete it" 403
request DELETE "/api/contracts/$MARINA_OUTBOUND" "$ADMIN"
expect R2 "the admin deletes the only contract of a link" 204
check R3 "deleting the only contract returns the link to PENDING" PENDING "$(link_status "$ANA_GUSTAVO")"
request GET /api/contracts "$ADMIN"
expect R4 "the deleted contract left the listing" 200 "[.content[].token] | index(\"$MARINA_OUTBOUND\") == null"
check R5 "the row, its items, schedules and slots keep deleted_at" "true true true true" \
  "$(sql "select bool_and(c.deleted_at is not null) || ' ' || bool_and(i.deleted_at is not null) || ' ' || bool_and(s.deleted_at is not null) || ' ' || bool_and(sl.deleted_at is not null) from contract c join contract_item i on i.contract_id = c.id join schedule s on s.id = i.schedule_id join schedule_slot sl on sl.schedule_id = s.id where c.token = '$MARINA_OUTBOUND'")"
request GET "/api/contracts/$MARINA_OUTBOUND" "$ADMIN"
expect R6 "the admin no longer finds it" 404
request GET "/api/contracts/$MARINA_OUTBOUND" "$ANA"
expect R7 "neither does the client of the link" 403
request DELETE "/api/contracts/$MARINA_OUTBOUND" "$ADMIN"
expect R8 "deleting it again" 404
request POST "/api/contracts/$MARINA_OUTBOUND/restore" "$ANA"
expect R9 "the client cannot restore it" 403
request POST "/api/contracts/$MARINA_OUTBOUND/restore" "$ADMIN"
expect R10 "the admin restores it with items and schedule" 200 '.status == "ACTIVE" and (.items[0].slots | length) == 5'
check R11 "restoring it activates the link again" ACTIVE "$(link_status "$ANA_GUSTAVO")"
request POST "/api/contracts/$MARINA_OUTBOUND/restore" "$ADMIN"
expect R12 "restoring a live contract" 404
request POST /api/contracts/doesnotexist/restore "$ADMIN"
expect R13 "restoring an unknown contract" 404
request DELETE "/api/contracts/$MARINA_OUTBOUND" "$ADMIN"
expect R14 "the admin deletes it again" 204
request POST /api/contracts "$ADMIN" "$(contract_body "$ANA_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$MARINA" "$(slots OUTBOUND MORNING 07:00 - MONDAY)")]")"
expect R15 "a newer active contract on the same link" 201
NEWER=$(jq -r .token <<<"$RESP_BODY")
request POST "/api/contracts/$MARINA_OUTBOUND/restore" "$ADMIN"
expect R16 "restoring over the newer active contract" 409 '.detail == "Este vínculo já tem um contrato ativo."'
request DELETE "/api/contracts/$NEWER" "$ADMIN"
expect R17 "the newer one is deleted" 204
request PATCH "/api/contracts/$MARINA_RETURN" "$ADMIN" '{"status":"ENDED"}'
expect R18 "the return-only contract ends, freeing that link" 200 '.status == "ENDED"'
request POST /api/contracts "$ADMIN" "$(contract_body "$ANA_HELENA" 2027-02-01 2027-12-17 "[$(item "$MARINA" "$MONDAY_OUTBOUND")]")"
expect R19 "meanwhile another driver takes Monday outbound" 201
request POST "/api/contracts/$MARINA_OUTBOUND/restore" "$ADMIN"
expect R20 "restoring a contract whose slots were taken meanwhile" 409 '.detail == "O dependente já tem este dia e trecho em outro contrato assinado e não encerrado."'
check R21 "the contract stays deleted" 1 "$(sql "select count(*) from contract where token = '$MARINA_OUTBOUND' and deleted_at is not null")"

table "Pickup address is a copy"
MOVE_TO_RUA_B=$(jq -nc --argjson a "$(address "Rua B" 20)" '{address: $a}')
request PATCH "/api/dependent/$MARINA" "$ANA" "$MOVE_TO_RUA_B"
expect A1 "a dependent with a contract in progress does not move (D17 of service-request-proposal)" 422 \
  '.detail == "Este dependente tem um contrato em andamento: ele não pode ser removido nem mudar de escola, endereço ou grade escolar."'
for contract in $(sql "select distinct c.token from contract c join contract_item i on i.contract_id = c.id join dependent d on d.id = i.dependent_id
                       where d.token = '$MARINA' and c.deleted_at is null and c.status in ('ACTIVE', 'SUSPENDED')"); do
  end_contract "$contract"
done
request PATCH "/api/dependent/$MARINA" "$ANA" "$MOVE_TO_RUA_B"
expect A2 "once those contracts end, the client moves the dependent to Rua B, 20" 200 '.address.street == "Rua B"'
request GET "/api/client-drivers/$ANA_HELENA/contracts" "$ANA"
expect A3 "the contracts still pick up at Rua das Acácias, 250" 200 '[.[].items[].pickupStreet] | unique == ["Rua das Acácias"]'

table "Concurrent creates on one link"
PARALLEL_DIR="$(mktemp -d)"
CAIO_BODY=$(contract_body "$CARLA_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$CAIO" "$OUTBOUND_WEEK")]")
for attempt in 1 2 3 4 5 6; do
  curl -s -o "$PARALLEL_DIR/$attempt.json" -w '%{http_code}\n' -X POST "$BASE_URL/api/contracts" \
    -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
    --data-binary "$CAIO_BODY" >"$PARALLEL_DIR/$attempt.status" &
done
wait
STATUSES=$(cat "$PARALLEL_DIR"/*.status | sort | uniq -c | awk '{print $2 "x" $1}' | paste -sd ' ' -)
rm -rf "$PARALLEL_DIR"
check Z1 "six concurrent creates for one link: one 201, five 409" "201x1 409x5" "$STATUSES"
check Z2 "and a single active contract was stored" 1 \
  "$(sql "select count(*) from contract c join client_driver l on l.id = c.client_driver_id where l.token = '$CARLA_GUSTAVO' and c.deleted_at is null")"
CAIO_A=$(sql "select c.token from contract c join client_driver l on l.id = c.client_driver_id where l.token = '$CARLA_GUSTAVO' and c.deleted_at is null")
request PATCH "/api/contracts/$CAIO_A" "$ADMIN" '{"status":"SUSPENDED"}'
expect Z3a "the winner is suspended" 200
request POST /api/contracts "$ADMIN" "$(contract_body "$CARLA_GUSTAVO" 2027-02-01 2027-12-17 "[$(item "$CAIO" "$RETURN_WEEK")]")"
expect Z3b "a second contract on the link, return only" 201
CAIO_B=$(jq -r .token <<<"$RESP_BODY")
request PATCH "/api/contracts/$CAIO_B" "$ADMIN" '{"status":"SUSPENDED"}'
expect Z3c "and suspended too" 200
check Z3 "two concurrent reactivations on one link: one 200, one 409" "200 409" \
  "$(in_parallel PATCH "$ADMIN" '{"status":"ACTIVE"}' "/api/contracts/$CAIO_A" "/api/contracts/$CAIO_B")"
check Z4 "a single contract of the link is ACTIVE" 1 \
  "$(sql "select count(*) from contract c join client_driver l on l.id = c.client_driver_id where l.token = '$CARLA_GUSTAVO' and c.status = 'ACTIVE' and c.deleted_at is null")"
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_FABIO" 2027-02-01 2027-12-17 "[$(item "$THEO" "$(slots RETURN NIGHT 22:00 - SATURDAY)")]")"
expect Z5a "an active contract on another link" 201
THEO_C=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/contracts/$THEO_C" "$ADMIN"
expect Z5b "deleted" 204
request POST /api/contracts "$ADMIN" "$(contract_body "$BRUNO_FABIO" 2027-02-01 2027-12-17 "[$(item "$THEO" "$(slots RETURN NIGHT 22:00 - SUNDAY)")]")"
expect Z5c "a second active contract on that link" 201
THEO_D=$(jq -r .token <<<"$RESP_BODY")
request DELETE "/api/contracts/$THEO_D" "$ADMIN"
expect Z5d "deleted too" 204
check Z5 "two concurrent restores of active contracts on one link: one 200, one 409" "200 409" \
  "$(in_parallel POST "$ADMIN" "" "/api/contracts/$THEO_C/restore" "/api/contracts/$THEO_D/restore")"
check Z6 "a single one came back" 1 \
  "$(sql "select count(*) from contract c join client_driver l on l.id = c.client_driver_id where l.token = '$BRUNO_FABIO' and c.deleted_at is null")"

table "The link status has no direct write path"
request GET "/api/client-drivers/$SEED_LINK" "$ADMIN"
expect B1 "the seeded link is ACTIVE through its seeded ACTIVE contract" 200 '.status == "ACTIVE"'
request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$CARLA_CLIENT" --arg d "$HELENA_DRIVER" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH", status: "ACTIVE"}')"
expect B2 "a status sent on creation is ignored" 201 '.status == "PENDING"'
CARLA_HELENA=$(jq -r .token <<<"$RESP_BODY")
request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$CARLA_CLIENT" --arg d "$FABIO_DRIVER" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH", status: "BLOCKED"}')"
expect B3 "BLOCKED sent on creation is ignored too" 201 '.status == "PENDING"'
request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$CARLA_CLIENT" --arg d "$HELENA_DRIVER" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH"}')"
expect B4 "the same pair twice" 409 '.detail == "Este cliente e este motorista já estão vinculados."'
request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg d "$HELENA_DRIVER" '{driverToken: $d, origin: "CLIENT_SEARCH"}')"
expect B5 "missing clientToken" 400
request POST /api/client-drivers "$CARLA" "$(jq -nc --arg c "$CARLA_CLIENT" --arg d "$HELENA_DRIVER" '{clientToken: $c, driverToken: $d, origin: "CLIENT_SEARCH"}')"
expect B6 "a client cannot create a link" 403
request PATCH "/api/client-drivers/$CARLA_HELENA" "$CARLA" '{"status":"ACTIVE"}'
expect B7 "the client of the link cannot activate it" 405
request PATCH "/api/client-drivers/$CARLA_HELENA" "$HELENA" '{"status":"ACTIVE"}'
expect B8 "the driver of the link cannot activate it" 405
request PATCH "/api/client-drivers/$CARLA_HELENA" "$ADMIN" '{"status":"ACTIVE"}'
expect B9 "the admin cannot either" 405
check B10 "the link stayed PENDING" PENDING "$(link_status "$CARLA_HELENA")"
check B11 "update_client_driver is in no bundle" 0 "$(sql "select count(*) from role_permissions where permissions ? 'update_client_driver'")"
check B12 "nor in a real admin token" 0 "$(claims "$ADMIN" | jq '[.permissions[] | select(. == "update_client_driver")] | length')"
request POST /api/driver-ratings "$CARLA" "$(jq -nc --arg d "$HELENA_DRIVER" '{driverToken: $d, rating: 5}')"
expect B13 "accepted effect: a client cannot rate a driver through a link without contract" 422 \
  '.detail == "Só é possível avaliar um motorista com quem você já teve um contrato em andamento."'

table "V60: legacy links brought in line"
sql "update client_driver set status = 'ACTIVE' where token = '$CARLA_HELENA'" >/dev/null
STATUSES_BEFORE=$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver where token <> '$CARLA_HELENA'")
apply_migration V60
check M1 "an ACTIVE link without contract becomes PENDING" PENDING "$(link_status "$CARLA_HELENA")"
check M2 "every other link keeps its status (ACTIVE with contract, INACTIVE, PENDING)" "$STATUSES_BEFORE" \
  "$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver where token <> '$CARLA_HELENA'")"
SNAPSHOT=$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver")
apply_migration V60
check M3 "re-applying V60 changes nothing" "$SNAPSHOT" "$(sql "select string_agg(token || '=' || status, ',' order by token) from client_driver")"
sql "update client_driver set status = 'ACTIVE' where token = '$CARLA_HELENA'; update role_permissions set permissions = permissions || '[\"update_client_driver\"]'::jsonb where name = 'ADMIN'" >/dev/null
apply_migration V60
check M4 "V60 also strips update_client_driver from a bundle that has it" 0 \
  "$(sql "select count(*) from role_permissions where permissions ? 'update_client_driver'")"

table "/api/drivers/me/unlinked-passengers — the driver's own students"
UP=/api/drivers/me/unlinked-passengers
RAFAEL_BODY=$(jq -nc --arg s "$SCHOOL" --argjson a "$(address "Rua das Palmeiras" 77)" --argjson slots "$ROUND_TRIP" \
  '{name: "Rafael Prado", schoolToken: $s, schoolShift: "FULLTIME", address: $a, notes: "Desce no portão B", slots: $slots}')
rafael() { jq -c "$1" <<<"$RAFAEL_BODY"; }
PASSENGERS_BEFORE="$(count unlinked_passenger) $(count schedule_slot)"
request POST "$UP" "$GUSTAVO" "$RAFAEL_BODY"
expect U1 "a driver registers a student they already transport" 201 \
  '.token != null and .name == "Rafael Prado" and .schoolShift == "FULLTIME" and .address.street == "Rua das Palmeiras" and (.slots | length) == 10'
RAFAEL=$(jq -r .token <<<"$RESP_BODY")
request GET "$UP" "$GUSTAVO"
expect U2 "it appears in the driver's list" 200 "map(.token) == [\"$RAFAEL\"]"
request GET "$UP/$RAFAEL" "$GUSTAVO"
expect U3 "the driver reads it" 200 '.notes == "Desce no portão B" and .schoolName == "Escola Municipal Seed"'
BEFORE_NOTES=$(jq -c 'del(.notes, .updatedAt)' <<<"$RESP_BODY")
request GET "$UP" "$FABIO"
expect U4 "another driver's list does not show it" 200 'length == 0'
request GET "$UP/$RAFAEL" "$FABIO"
expect U5 "another driver reading it" 404 '.detail == "Aluno não vinculado não encontrado."'
request PATCH "$UP/$RAFAEL" "$FABIO" '{"notes":"x"}'
expect U6 "another driver patching it" 404
request PUT "$UP/$RAFAEL/schedule" "$FABIO" "$(jq -nc --argjson s "$MONDAY_OUTBOUND" '{slots: $s}')"
expect U7 "another driver replacing its schedule" 404
request DELETE "$UP/$RAFAEL" "$FABIO"
expect U8 "another driver deleting it" 404
request GET "$UP" "$ANA"
expect U9 "a client cannot manage unlinked passengers" 403
request GET "$UP" "$ADMIN"
expect U10 "neither can the admin" 403
request POST "$UP" "$ANA" "$RAFAEL_BODY"
expect U11 "a client cannot create one" 403
request GET "$UP" ""
expect U12 "no token" 401
request GET "$UP" "not-a-jwt"
expect U13 "invalid token" 401
request GET "$UP/doesnotexist" "$GUSTAVO"
expect U14 "unknown token" 404

table "POST — validation"
COUNT_AFTER_CREATE="$(count unlinked_passenger) $(count schedule_slot)"
request POST "$UP" "$GUSTAVO" "$(rafael 'del(.name)')"
expect N1 "missing name" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.name = "   "')"
expect N2 "blank name" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.name = ("a" * 256)')"
expect N3 "a 256-character name" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.notes = ("a" * 501)')"
expect N4 "501-character notes" 400
request POST "$UP" "$GUSTAVO" "$(rafael 'del(.schoolToken)')"
expect N5 "missing school" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.schoolToken = "doesnotexist"')"
expect N6 "unknown school, pt-BR message" 404 '.detail == "Escola não encontrada."'
request POST "$UP" "$GUSTAVO" "$(rafael 'del(.schoolShift)')"
expect N7 "missing school shift" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.schoolShift = "EVENING"')"
expect N8 "unknown school shift" 400
request POST "$UP" "$GUSTAVO" "$(rafael 'del(.address)')"
expect N9 "missing address" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.address.zipCode = "123"')"
expect N10 "invalid zip code" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.address.cityToken = "doesnotexist"')"
expect N11 "unknown city" 404
request POST "$UP" "$GUSTAVO" "$(rafael '.slots = []')"
expect N12 "empty schedule, pt-BR message" 400 '.detail == "A agenda precisa de pelo menos um horário."'
request POST "$UP" "$GUSTAVO" "$(rafael 'del(.slots)')"
expect N13 "missing schedule" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.slots += [.slots[0]]')"
expect N14 "the same weekday and leg twice" 400 '.detail == "O mesmo dia e trecho não pode aparecer duas vezes na agenda."'
request POST "$UP" "$GUSTAVO" "$(rafael '.slots[0].windowEnd = "06:00"')"
expect N15 "window end before its start" 400 '.detail == "O fim da janela deve ser posterior ao início."'
request POST "$UP" "$GUSTAVO" "$(rafael '.slots[0].shift = "FULLTIME"')"
expect N16 "a slot with the FULLTIME shift" 400
request POST "$UP" "$GUSTAVO" "$(rafael '.name = {first: "Rafael"}')"
expect N17 "name with the wrong JSON type" 400
request POST "$UP" "$GUSTAVO" '{"name":'
expect N18 "malformed JSON" 400
request POST "$UP" "$GUSTAVO" "$RAFAEL_BODY" "text/plain"
expect N19 "wrong content type" 415
check N20 "the rejected creates stored nothing" "$COUNT_AFTER_CREATE" "$(count unlinked_passenger) $(count schedule_slot)"
request POST "$UP" "$GUSTAVO" "$(rafael '.name = ("a" * 255) | .notes = ("n" * 500)')"
expect N21 "a 255-character name and 500-character notes" 201
LONG=$(jq -r .token <<<"$RESP_BODY")
request POST "$UP" "$GUSTAVO" "$(rafael '.name = "Bia Prado" | .birthDate = "2015-01-01" | .document = "12345678909" | .phone = "11999990000" | del(.notes)')"
expect N22 "unknown personal fields are not stored" 201 '(has("birthDate") or has("document") or has("phone")) | not'
BIA=$(jq -r .token <<<"$RESP_BODY")
check N23 "the table has no column for them" 0 \
  "$(sql "select count(*) from information_schema.columns where table_name = 'unlinked_passenger' and column_name in ('birth_date', 'document', 'phone', 'email')")"
sql "update driver set approval_status = 'PENDING' where user_id = (select id from users where email = 'helena.costa@seed.vanep.com.br')" >/dev/null
HELENA=$(login helena.costa@seed.vanep.com.br)
request POST "$UP" "$HELENA" "$(rafael '.name = "Davi Costa"')"
expect N24 "a driver pending approval registers one too" 201
restore_drivers
PARALLEL_DIR="$(mktemp -d)"
for attempt in 1 2 3 4 5; do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL$UP" -H "Authorization: Bearer $FABIO" \
    -H "Content-Type: application/json" --data-binary "$(rafael ".name = \"Aluno $attempt\"")" >"$PARALLEL_DIR/$attempt" &
done
wait
check N25 "five concurrent creates: no limit, five 201" "201x5" "$(cat "$PARALLEL_DIR"/* | sort | uniq -c | awk '{print $2 "x" $1}')"
rm -rf "$PARALLEL_DIR"
request GET "$UP" "$FABIO"
expect N26 "with five distinct tokens" 200 '[.[].token] | unique | length == 5'

table "PATCH, PUT /schedule and DELETE"
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"notes":"Portão A"}'
expect W1 "a single-field PATCH (notes)" 200 '.notes == "Portão A"'
check W2 "name, school, shift, address and schedule unchanged" unchanged \
  "$([[ $(jq -c 'del(.notes, .updatedAt)' <<<"$RESP_BODY") == "$BEFORE_NOTES" ]] && echo unchanged || echo "$RESP_BODY")"
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{}'
expect W3 "an empty PATCH changes nothing" 200 '.notes == "Portão A" and .name == "Rafael Prado"'
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"notes":null}'
expect W4 "notes can be cleared" 200 '.notes == null'
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"name":null}'
expect W5 "name cannot be cleared" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"schoolShift":null}'
expect W6 "school shift cannot be cleared" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"schoolToken":null}'
expect W7 "school cannot be cleared" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"address":null}'
expect W8 "address cannot be cleared" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" "$(jq -nc '{name: ("a" * 256)}')"
expect W9 "a 256-character name" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"schoolShift":"EVENING"}'
expect W10 "unknown school shift" 400
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"schoolToken":"doesnotexist"}'
expect W11 "unknown school" 404
request GET "$UP/$RAFAEL" "$GUSTAVO"
expect W12 "the rejected patches changed nothing" 200 '.name == "Rafael Prado" and .schoolShift == "FULLTIME" and .address.street == "Rua das Palmeiras"'
request PATCH "$UP/$RAFAEL" "$GUSTAVO" "$(jq -nc --argjson a "$(address "Rua Nova" 5)" '{name: "Rafael P. Prado", schoolShift: "AFTERNOON", address: $a}')"
expect W13 "name, school shift and address patched together" 200 \
  '.name == "Rafael P. Prado" and .schoolShift == "AFTERNOON" and .address.street == "Rua Nova" and (.slots | length) == 10'
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" "$(jq -nc --argjson s "$(slots OUTBOUND MORNING 06:40 - MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY)" '{slots: $s}')"
expect W14 "the schedule is replaced by five outbound slots" 200 '(.slots | length) == 5 and all(.slots[]; .leg == "OUTBOUND")'
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" "$(jq -nc --argjson s "$(slots OUTBOUND MORNING 07:00 - MONDAY WEDNESDAY FRIDAY)" '{slots: $s}')"
expect W15 "then by three" 200 '[.slots[].weekday] == ["MONDAY", "WEDNESDAY", "FRIDAY"] and all(.slots[]; .windowStart == "07:00:00")'
check W16 "the previous fifteen slots remain soft-deleted, three are live" "15 3" \
  "$(sql "select count(*) filter (where s.deleted_at is not null) || ' ' || count(*) filter (where s.deleted_at is null) from schedule_slot s join unlinked_passenger p on p.schedule_id = s.schedule_id where p.token = '$RAFAEL'")"
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" '{"slots":[]}'
expect W17 "an empty schedule" 400
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" '{}'
expect W18 "a body without slots" 400
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" "$(jq -nc --argjson s "$(slots OUTBOUND MORNING 07:00 06:00 MONDAY)" '{slots: $s}')"
expect W19 "a window ending before it starts" 400
request PUT "$UP/$RAFAEL/schedule" "$GUSTAVO" "$(jq -nc --argjson s "$(slots OUTBOUND MORNING 07:00 - MONDAY MONDAY)" '{slots: $s}')"
expect W20 "the same weekday and leg twice" 400
request GET "$UP/$RAFAEL" "$GUSTAVO"
expect W21 "the rejected replacements kept the three slots" 200 '(.slots | length) == 3'
request DELETE "$UP/$RAFAEL" "$GUSTAVO"
expect W22 "the driver deletes it" 204
request GET "$UP" "$GUSTAVO"
expect W23 "it left the list" 200 "map(.token) | index(\"$RAFAEL\") == null"
request GET "$UP/$RAFAEL" "$GUSTAVO"
expect W24 "reading it is not found" 404
request DELETE "$UP/$RAFAEL" "$GUSTAVO"
expect W25 "deleting it again is not found" 404
request PATCH "$UP/$RAFAEL" "$GUSTAVO" '{"notes":"x"}'
expect W26 "patching it is not found" 404
check W27 "the row, its schedule and slots keep deleted_at" "true true true" \
  "$(sql "select (p.deleted_at is not null) || ' ' || (s.deleted_at is not null) || ' ' || bool_and(sl.deleted_at is not null) from unlinked_passenger p join schedule s on s.id = p.schedule_id join schedule_slot sl on sl.schedule_id = s.id where p.token = '$RAFAEL' group by p.deleted_at, s.deleted_at")"

echo
echo "ALL PASSED: $PASSED scenarios"
