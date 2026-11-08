#!/usr/bin/env bash
# Throwaway environment for VAN-57 (driver-students-family): a disposable Postgres container plus
# the API from the current branch, seeded on an empty schema. Never points at the shared dev database.
#   local-env.sh start   build the jar, start Postgres + API, wait for the seed
#   local-env.sh demo    give Fabio unlinked students in every family state, for the app
#   local-env.sh stop    stop the API and drop the container (all data is lost)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van57-qa-db}"
DB_PORT="${QA_DB_PORT:-5435}"
API_PORT="${QA_API_PORT:-8082}"
BASE_URL="http://localhost:$API_PORT"
STATE_DIR="${TMPDIR:-/tmp}/vanep-van57"
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
      VANEP_APP_INVITE_BASE_URL=https://vanep.com.br/convite \
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
  local token=$1 name=$2 school=$3 city=$4
  curl -s -X POST "$BASE_URL/api/drivers/me/unlinked-passengers" \
    -H "Authorization: Bearer $token" -H "Content-Type: application/json" \
    --data-binary "{\"name\":\"$name\",\"schoolToken\":\"$school\",\"schoolShift\":\"MORNING\",\"address\":{\"cityToken\":\"$city\",\"street\":\"Rua Demo\",\"zipCode\":\"01001000\",\"number\":\"1\"},\"slots\":[{\"weekday\":\"MONDAY\",\"leg\":\"OUTBOUND\",\"shift\":\"MORNING\",\"pickupTime\":\"06:40\"},{\"weekday\":\"MONDAY\",\"leg\":\"RETURN\",\"shift\":\"AFTERNOON\",\"pickupTime\":\"12:10\"}]}" |
    jq -r .token
}

demo() {
  local school city fabio bruno rafael invited joined code
  school=$(sql "select token from school where deleted_at is null order by id limit 1")
  city=$(sql "select c.token from school s join city c on c.id = s.city_id where s.token = '$school'")
  fabio=$(login fabio.teixeira@seed.vanep.com.br)
  bruno=$(login bruno.lima@seed.vanep.com.br)
  add_passenger "$fabio" "Laura Mendes" "$school" "$city" >/dev/null
  invited=$(add_passenger "$fabio" "Sofia Lima" "$school" "$city")
  curl -s -o /dev/null -X POST "$BASE_URL/api/drivers/me/unlinked-passengers/$invited/invite" \
    -H "Authorization: Bearer $fabio"
  joined=$(add_passenger "$fabio" "Rafael" "$school" "$city")
  code=$(curl -s -X POST "$BASE_URL/api/drivers/me/unlinked-passengers/$joined/invite" \
    -H "Authorization: Bearer $fabio" | jq -r .code)
  rafael=$(curl -s -X POST "$BASE_URL/api/dependent" -H "Authorization: Bearer $bruno" \
    -H "Content-Type: application/json" \
    --data-binary "{\"name\":\"Rafael Lima\",\"birthDate\":\"2017-03-10\",\"schoolToken\":\"$school\"}" |
    jq -r .token)
  curl -s -o /dev/null -X POST "$BASE_URL/api/family-invites/$code/accept" \
    -H "Authorization: Bearer $bruno" -H "Content-Type: application/json" \
    --data-binary "{\"dependentToken\":\"$rafael\"}"
  echo "demo data ready:"
  curl -s "$BASE_URL/api/drivers/me/students" -H "Authorization: Bearer $fabio" |
    jq -c '[.[] | {name, source, familyStatus}]'
}

stop() {
  if [[ -f $PID_FILE ]]; then kill "$(cat "$PID_FILE")" 2>/dev/null || true; rm -f "$PID_FILE"; fi
  docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
  echo "stopped"
}

case "${1:-}" in
  start) start ;;
  demo) demo ;;
  stop) stop ;;
  *) echo "usage: $0 start|demo|stop" >&2; exit 2 ;;
esac
