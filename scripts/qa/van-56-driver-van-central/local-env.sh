#!/usr/bin/env bash
# Throwaway environment for VAN-56: a disposable Postgres container plus the API from the current
# branch on port 8081, seeded on an empty schema. Never points at the shared dev database.
#   local-env.sh start   build the jar, start Postgres + API, wait for the seed
#   local-env.sh demo    add demo data for the app (more passengers, a driver without a van)
#   local-env.sh multi-van  give Fabio a second van so the app's van selector can be exercised
#   local-env.sh stop    stop the API and drop the container (all data is lost)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van56-qa-db}"
DB_PORT="${QA_DB_PORT:-5434}"
API_PORT="${QA_API_PORT:-8081}"
BASE_URL="http://localhost:$API_PORT"
STATE_DIR="${TMPDIR:-/tmp}/vanep-van56"
JAR="$STATE_DIR/api.jar"
LOG="$STATE_DIR/api.log"
PID_FILE="$STATE_DIR/api.pid"

sql() {
  docker exec "$DB_CONTAINER" psql -U vanep -d vanep -Atqc "$1"
}

login() {
  curl -s -X POST "$BASE_URL/oauth2/token" \
    -d "grant_type=urn:vanep:params:oauth:grant-type:password" \
    --data-urlencode "username=$1" -d "password=password" -d "client_id=vanep-mobile" |
    jq -er '.access_token'
}

start() {
  mkdir -p "$STATE_DIR"
  (cd "$ROOT" && ./mvnw -q -DskipTests package && cp target/vanep-0.0.1-SNAPSHOT.jar "$JAR")
  docker run -d --rm --name "$DB_CONTAINER" -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
    -e POSTGRES_DB=vanep -p "127.0.0.1:$DB_PORT:5432" postgres:17-alpine >/dev/null
  until docker exec "$DB_CONTAINER" pg_isready -U vanep -d vanep >/dev/null 2>&1; do sleep 1; done
  (
    cd "$ROOT"
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
    SPRING_PROFILES_ACTIVE=local SERVER_PORT="$API_PORT" POSTGRES_PORT="$DB_PORT" \
      POSTGRES_USER=vanep POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false \
      VANEP_SEED_ENABLED=true VANEP_OAUTH_ISSUER= MEDIA_STORAGE_ROOT="$STATE_DIR/media" \
      nohup java -jar "$JAR" >"$LOG" 2>&1 &
    echo $! >"$PID_FILE"
  )
  until grep -qE 'Seed: trips created|APPLICATION FAILED' "$LOG"; do sleep 2; done
  if grep -q 'APPLICATION FAILED' "$LOG"; then
    echo "API failed to start, see $LOG" >&2
    exit 1
  fi
  echo "API up on $BASE_URL (Android emulator: http://10.0.2.2:$API_PORT), log: $LOG"
}

add_passenger() {
  local token=$1 name=$2 shifts=$3 school=$4 city=$5 slots="" shift
  for shift in $shifts; do
    local leg=OUTBOUND start=06:40:00
    if [[ $shift == AFTERNOON ]]; then leg=RETURN; start=17:00:00; fi
    for weekday in MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY; do
      slots+="{\"weekday\":\"$weekday\",\"leg\":\"$leg\",\"shift\":\"$shift\",\"windowStart\":\"$start\"},"
    done
  done
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/drivers/me/unlinked-passengers" \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    --data-binary "{\"name\":\"$name\",\"schoolToken\":\"$school\",\"schoolShift\":\"MORNING\",\"address\":{\"cityToken\":\"$city\",\"street\":\"Rua Demo\",\"zipCode\":\"01001000\",\"number\":\"1\"},\"slots\":[${slots%,}]}" |
    grep -q 201
}

demo() {
  local school city fabio index
  school=$(sql "select token from school where deleted_at is null order by id limit 1")
  city=$(sql "select c.token from school s join city c on c.id = s.city_id where s.token = '$school'")
  fabio=$(login fabio.teixeira@seed.vanep.com.br)
  for index in $(seq 1 10); do
    add_passenger "$fabio" "Aluno Integral $index" "MORNING AFTERNOON" "$school" "$city"
  done
  for index in 1 2; do
    add_passenger "$fabio" "Aluno Manha $index" "MORNING" "$school" "$city"
  done
  sql "update vehicle set deleted_at = now() where plate = 'VAN3C45'" >/dev/null
  echo "demo data ready:"
  curl -s "$BASE_URL/api/drivers/me/occupancy" -H "Authorization: Bearer $fabio" | jq -c .
}

multi_van() {
  local fabio
  fabio=$(login fabio.teixeira@seed.vanep.com.br)
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE_URL/api/vehicles/me" \
    -H "Authorization: Bearer $fabio" -H "Content-Type: application/json" \
    --data-binary '{"plate":"RTA3B45","brand":"Renault","model":"Master","manufactureYear":2022,"color":"Prata","capacity":12}' |
    grep -q 201
  echo "Fabio's vans:"
  curl -s "$BASE_URL/api/vehicles/me" -H "Authorization: Bearer $fabio" | jq -c '[.[] | {plate, model, capacity}]'
}

stop() {
  if [[ -f $PID_FILE ]]; then kill "$(cat "$PID_FILE")" 2>/dev/null || true; rm -f "$PID_FILE"; fi
  docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
  echo "stopped"
}

case "${1:-}" in
  start) start ;;
  demo) demo ;;
  multi-van) multi_van ;;
  stop) stop ;;
  *) echo "usage: $0 start|demo|multi-van|stop" >&2; exit 2 ;;
esac
