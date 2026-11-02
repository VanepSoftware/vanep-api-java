#!/usr/bin/env bash
# Manual QA for VAN-56 (constitution rule 55): GET /api/drivers/me/occupancy,
# PUT /api/drivers/me/availability and basePrice on GET /api/drivers/me.
# Runs against a live API backed by a THROWAWAY database seeded on an empty schema: it creates
# unlinked passengers, flips the seeded contract status and restores it.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:?set QA_DB_CONTAINER to the throwaway Postgres container}"
QA_DB_USER="${QA_DB_USER:-vanep}"
QA_DB_NAME="${QA_DB_NAME:-vanep}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"
MOBILE_CLIENT_ID="${MOBILE_CLIENT_ID:-vanep-mobile}"

FABIO_EMAIL=fabio.teixeira@seed.vanep.com.br
GUSTAVO_EMAIL=gustavo.santos@seed.vanep.com.br
ANA_EMAIL=ana.souza@seed.vanep.com.br

BODY_FILE="$(mktemp)"
RESP_STATUS=""
RESP_BODY=""
PASSED=0

sql() {
  docker exec "$QA_DB_CONTAINER" psql -U "$QA_DB_USER" -d "$QA_DB_NAME" -Atqc "$1"
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
  local method=$1 path=$2 token=$3 body=${4-}
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  if [[ -n $token ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n $body ]]; then args+=(-H "Content-Type: application/json" --data-binary "$body"); fi
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

table() {
  printf '\n### %s\n\n| id | scenario | expected | result |\n|---|---|---|---|\n' "$1"
}

occupancy_is() {
  printf '[.shifts[] | [.shift, .passengers]] == [["MORNING",%s],["AFTERNOON",%s],["NIGHT",%s]] and .students == %s and .studentsWithFamily == %s' \
    "$1" "$2" "$3" "$4" "$5"
}

passenger_body() {
  local name=$1 shifts=$2 slots="" shift
  for shift in $shifts; do
    local leg=OUTBOUND start=06:40:00
    if [[ $shift == AFTERNOON ]]; then leg=RETURN; start=17:00:00; fi
    for weekday in MONDAY WEDNESDAY FRIDAY; do
      slots+="{\"weekday\":\"$weekday\",\"leg\":\"$leg\",\"shift\":\"$shift\",\"windowStart\":\"$start\"},"
    done
  done
  printf '{"name":"%s","schoolToken":"%s","schoolShift":"MORNING","address":{"cityToken":"%s","street":"Rua QA","zipCode":"01001000","number":"1"},"slots":[%s]}' \
    "$name" "$SCHOOL_TOKEN" "$CITY_TOKEN" "${slots%,}"
}

stored_available() {
  sql "select d.is_available from driver d join users u on u.id = d.user_id where u.email = '$FABIO_EMAIL'"
}

SEEDED_CONTRACTS=$(sql "select count(*) from contract where status = 'ACTIVE' and deleted_at is null")
SEEDED_PASSENGERS=$(sql "select count(*) from unlinked_passenger")
if [[ $SEEDED_CONTRACTS != 1 || $SEEDED_PASSENGERS != 0 ]]; then
  echo "SETUP FAIL | expected a fresh seed (1 active contract, 0 unlinked passengers)," \
    "found $SEEDED_CONTRACTS and $SEEDED_PASSENGERS" >&2
  exit 1
fi
SCHOOL_TOKEN=$(sql "select token from school where deleted_at is null order by id limit 1")
CITY_TOKEN=$(sql "select c.token from school s join city c on c.id = s.city_id where s.token = '$SCHOOL_TOKEN'")

FABIO=$(login "$FABIO_EMAIL")
GUSTAVO=$(login "$GUSTAVO_EMAIL")
ANA=$(login "$ANA_EMAIL")
echo "tokens ok"

table "GET /api/drivers/me/occupancy"
request GET /api/drivers/me/occupancy ""
expect O1 "no token" 401
request GET /api/drivers/me/occupancy "not-a-jwt"
expect O2 "invalid token" 401
request GET /api/drivers/me/occupancy "$ANA"
expect O3 "a client is not a driver" 403
request GET /api/drivers/me/occupancy "$FABIO"
expect O4 "seeded ACTIVE contract (Lucas, morning and return) counts once per shift" 200 \
  "$(occupancy_is 1 1 0 1 1)"
request GET /api/drivers/me/occupancy "$GUSTAVO"
expect O5 "a driver without passengers gets every shift at zero" 200 "$(occupancy_is 0 0 0 0 0)"

request POST /api/drivers/me/unlinked-passengers "$FABIO" "$(passenger_body "QA Integral" "MORNING AFTERNOON")"
expect O6 "setup: unlinked passenger on both shifts" 201
request POST /api/drivers/me/unlinked-passengers "$FABIO" "$(passenger_body "QA Manha" "MORNING")"
expect O7 "setup: unlinked passenger on the morning only" 201
request POST /api/drivers/me/unlinked-passengers "$FABIO" "$(passenger_body "QA Tarde" "AFTERNOON")"
expect O8 "setup: unlinked passenger on the afternoon only" 201
AFTERNOON_ONLY=$(jq -r '.token' <<<"$RESP_BODY")

request GET /api/drivers/me/occupancy "$FABIO"
expect O9 "unlinked passengers add to the shifts and to students, not to studentsWithFamily" 200 \
  "$(occupancy_is 3 3 0 4 1)"
request GET /api/drivers/me/occupancy "$GUSTAVO"
expect O10 "another driver's passengers do not leak" 200 "$(occupancy_is 0 0 0 0 0)"

request DELETE "/api/drivers/me/unlinked-passengers/$AFTERNOON_ONLY" "$FABIO"
expect O11 "setup: delete the afternoon-only passenger" 204
request GET /api/drivers/me/occupancy "$FABIO"
expect O12 "a deleted unlinked passenger leaves the occupancy" 200 "$(occupancy_is 3 2 0 3 1)"

sql "update contract set status = 'SUSPENDED' where status = 'ACTIVE'" >/dev/null
request GET /api/drivers/me/occupancy "$FABIO"
expect O13 "a SUSPENDED contract does not count" 200 "$(occupancy_is 2 1 0 2 0)"
sql "update contract set status = 'ACTIVE' where status = 'SUSPENDED'" >/dev/null
request GET /api/drivers/me/occupancy "$FABIO"
expect O14 "back to ACTIVE, the contracted passenger counts again" 200 "$(occupancy_is 3 2 0 3 1)"

request POST /api/drivers/me/occupancy "$FABIO" '{}'
expect O15 "POST is not allowed" 405

table "PUT /api/drivers/me/availability and basePrice on GET /api/drivers/me"
BEFORE=$(stored_available)
check A0 "seeded driver starts unavailable" "f" "$BEFORE"
request GET /api/drivers/me "$FABIO"
expect A1 "GET /me exposes basePrice" 200 '.basePrice == 50 and .available == false'
request PUT /api/drivers/me/availability "" '{"available":true}'
expect A2 "no token" 401
request PUT /api/drivers/me/availability "not-a-jwt" '{"available":true}'
expect A3 "invalid token" 401
request PUT /api/drivers/me/availability "$ANA" '{"available":true}'
expect A4 "a client is not a driver" 403
check A5 "the rejected client request changed nothing" "f" "$(stored_available)"
request PUT /api/drivers/me/availability "$FABIO" '{}'
expect A6 "missing available" 400
request PUT /api/drivers/me/availability "$FABIO" '{"available":null}'
expect A7 "null available" 400
request PUT /api/drivers/me/availability "$FABIO" '{"available":"maybe"}'
expect A8 "wrong JSON type" 400
request PUT /api/drivers/me/availability "$FABIO" '{"available":'
expect A9 "malformed JSON" 400
request PUT /api/drivers/me/availability "$FABIO" ''
expect A10 "empty body" 400
check A11 "rejected bodies changed nothing" "f" "$(stored_available)"
request PUT /api/drivers/me/availability "$FABIO" '{"available":true}'
expect A12 "driver turns availability on" 200 '.available == true and .basePrice == 50 and (.token | length) > 0'
check A13 "stored as available" "t" "$(stored_available)"
request PUT /api/drivers/me/availability "$FABIO" '{"available":true}'
expect A14 "repeating the same value is idempotent" 200 '.available == true'
request GET /api/drivers/me "$FABIO"
expect A15 "GET /me reflects the change" 200 '.available == true'
request GET /api/drivers/me "$GUSTAVO"
expect A16 "another driver is untouched" 200 '.available == false'
request PUT /api/drivers/me/availability "$FABIO" '{"available":false}'
expect A17 "driver turns availability off" 200 '.available == false'
check A18 "stored as unavailable" "f" "$(stored_available)"
request PATCH /api/drivers/me/availability "$FABIO" '{"available":true}'
expect A19 "PATCH is not allowed" 405

printf '\n%s scenarios passed\n' "$PASSED"
