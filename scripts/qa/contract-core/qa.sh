#!/usr/bin/env bash
# Manual QA for the contract-core change (constitution rule 55).
# Runs against a live API backed by a THROWAWAY database that was seeded on an empty schema: it
# writes rows directly, flips seeded drivers and links, and re-applies migrations. See qa.md next
# to the change.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:?set QA_DB_CONTAINER to the throwaway Postgres container}"
QA_DB_USER="${QA_DB_USER:-vanep}"
QA_DB_NAME="${QA_DB_NAME:-vanep}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@vanep.com.br}"
MOBILE_CLIENT_ID="${MOBILE_CLIENT_ID:-vanep-mobile}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MIGRATIONS="$SCRIPT_DIR/../../../src/main/resources/db/migration"
BODY_FILE="$(mktemp)"
RESP_STATUS=""
RESP_BODY=""
PASSED=0
WEEKDAYS=(MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY)

sql() {
  docker exec "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$QA_DB_NAME" -Atqc "$1"
}

sql_error() {
  docker exec -i "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$QA_DB_NAME" -Atq -v ON_ERROR_STOP=1 \
    <<<"begin; $1; rollback;" 2>&1 >/dev/null || true
}

apply_migration() {
  docker exec -i "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$QA_DB_NAME" -q -v ON_ERROR_STOP=1 \
    <"$(ls "$MIGRATIONS"/"$1"__*.sql)" >/dev/null
}

fail() {
  echo "FAIL | $1 | expected: $2 | observed: $3" >&2
  echo "response body: $RESP_BODY" >&2
  exit 1
}

pass() {
  PASSED=$((PASSED + 1))
  printf '| %s | %s | %s | PASS |\n' "$1" "$2" "$3"
}

request() {
  local method=$1 path=$2 token=$3 body=${4-} content_type=${5:-application/json}
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  if [[ -n $token ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n $body ]]; then args+=(-H "Content-Type: $content_type" --data-binary "$body"); fi
  RESP_STATUS=$(curl "${args[@]}")
  RESP_BODY=$(cat "$BODY_FILE")
}

expect() {
  local id=$1 scenario=$2 status=$3 assertion=${4-}
  [[ $RESP_STATUS == "$status" ]] || fail "$id $scenario" "HTTP $status" "HTTP $RESP_STATUS"
  if [[ -n $assertion ]]; then
    jq -e "$assertion" <<<"$RESP_BODY" >/dev/null || fail "$id $scenario" "$assertion" "$RESP_BODY"
  fi
  pass "$id" "$scenario" "$status"
}

check() {
  local id=$1 scenario=$2 expected=$3 observed=$4
  RESP_BODY="$observed"
  [[ $observed == "$expected" ]] || fail "$id $scenario" "$expected" "$observed"
  pass "$id" "$scenario" "$expected"
}

check_sql_rejected() {
  local id=$1 scenario=$2 constraint=$3 statement=$4 error
  error=$(sql_error "$statement")
  RESP_BODY="$error"
  [[ $error == *"$constraint"* ]] || fail "$id $scenario" "rejected by $constraint" "${error:-accepted}"
  pass "$id" "$scenario" "rejected by $constraint"
}

check_sql_accepted() {
  local id=$1 scenario=$2 statement=$3 error
  error=$(sql_error "$statement")
  RESP_BODY="$error"
  [[ -z $error ]] || fail "$id $scenario" "accepted" "$error"
  pass "$id" "$scenario" "accepted"
}

login() {
  local response
  response=$(curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=urn:vanep:params:oauth:grant-type:password" \
    --data-urlencode "username=$1" -d "password=$SEED_PASSWORD" -d "client_id=$MOBILE_CLIENT_ID")
  jq -er '.access_token' <<<"$response" 2>/dev/null || {
    echo "SETUP FAIL | login as $1 | $response" >&2
    return 1
  }
}

claims() {
  local payload
  payload=$(cut -d. -f2 <<<"$1" | tr '_-' '/+')
  while (( ${#payload} % 4 )); do payload="$payload="; done
  base64 -d <<<"$payload" 2>/dev/null || base64 -D <<<"$payload"
}

table() {
  echo
  echo "== $1"
  echo "| id | scenario | expected | result |"
  echo "|---|---|---|---|"
}

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
  request POST /api/client-drivers "$ADMIN" "$(jq -nc --arg c "$1" --arg d "$2" '{clientToken: $c, driverToken: $d}')"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | link | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

link_status() {
  request GET "/api/client-drivers/$1" "$ADMIN"
  jq -r .status <<<"$RESP_BODY"
}

count() {
  sql "select count(*) from $1"
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
[[ $(count client_driver) == 1 && $(count contract) -le 1 && $(count dependent) == 2 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (one link, two dependents)" >&2
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

table "Operation shift on trips and school shift on dependents"
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
request POST /api/dependent "$BRUNO" "$(jq -nc '{name: "Davi Lima", shift: "FULLTIME"}')"
expect S19 "a client creates a FULLTIME dependent" 201 '.shift == "FULLTIME"'
FULLTIME_DEPENDENT=$(jq -r .token <<<"$RESP_BODY")
request GET "/api/dependent/$FULLTIME_DEPENDENT" "$BRUNO"
expect S20 "the dependent reads back as FULLTIME" 200 '.shift == "FULLTIME"'
request PATCH "/api/dependent/$FULLTIME_DEPENDENT" "$BRUNO" '{"shift":"EVENING"}'
expect S21 "unknown school shift on a dependent" 400
sql "insert into trip (token, driver_id, service_date, shift, status) select 'qalegacyfulltime', d.id, date '2027-03-02', 'FULLTIME', 'SCHEDULED' from driver d join users u on u.id = d.user_id where u.email = 'helena.costa@seed.vanep.com.br'" >/dev/null
apply_migration V56
check S22 "re-applying V56 turns a stored FULLTIME trip into MORNING" MORNING "$(sql "select shift from trip where token = 'qalegacyfulltime'")"
request GET /api/trips/qalegacyfulltime "$ADMIN"
expect S23 "the converted trip is readable through the API" 200 '.shift == "MORNING"'
check S24 "V56 left the other shifts alone" AFTERNOON "$(sql "select shift from trip where token = '$HELENA_TRIP'")"
check S25 "trip.shift and dependent.shift are documented" "yes" \
  "$(sql "select case when col_description('trip'::regclass, (select attnum from pg_attribute where attrelid = 'trip'::regclass and attname = 'shift')) is not null and col_description('dependent'::regclass, (select attnum from pg_attribute where attrelid = 'dependent'::regclass and attname = 'shift')) is not null then 'yes' else 'no' end")"

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

echo
echo "ALL PASSED: $PASSED scenarios"
