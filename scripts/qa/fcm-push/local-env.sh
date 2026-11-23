#!/usr/bin/env bash
# Throwaway environment for VAN-18 (FCM push): a disposable Postgres container plus the API from
# the current branch, seeded on an empty schema. Never points at the shared dev database.
#   local-env.sh start disabled      no FIREBASE_CREDENTIALS_PATH: push off, app must still boot
#   local-env.sh start unreachable   a generated service account whose token endpoint is
#                                    http://localhost:1, so every send fails without leaving the host
#   local-env.sh stop                stop the API and drop the container (all data is lost)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van18-qa-db}"
DB_PORT="${QA_DB_PORT:-5436}"
API_PORT="${QA_API_PORT:-8083}"
STATE_DIR="${TMPDIR:-/tmp}/vanep-van18"
JAR="$STATE_DIR/api.jar"
LOG="$STATE_DIR/api.log"
PID_FILE="$STATE_DIR/api.pid"
CREDENTIALS="$STATE_DIR/fake-service-account.json"

write_fake_credentials() {
  local key
  key=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null | awk '{printf "%s\\n", $0}')
  cat >"$CREDENTIALS" <<JSON
{
  "type": "service_account",
  "project_id": "vanep-qa",
  "private_key_id": "qa",
  "private_key": "$key",
  "client_email": "push@vanep-qa.iam.gserviceaccount.com",
  "client_id": "1",
  "token_uri": "http://localhost:1/token"
}
JSON
}

start() {
  local mode=${1:?usage: local-env.sh start disabled|unreachable} credentials=""
  mkdir -p "$STATE_DIR"
  if [[ $mode == unreachable ]]; then
    write_fake_credentials
    credentials="$CREDENTIALS"
  fi
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
      FIREBASE_CREDENTIALS_PATH="$credentials" \
      nohup java -jar "$JAR" >"$LOG" 2>&1 &
    echo $! >"$PID_FILE"
  )
  until grep -qE 'Seed: trips created|APPLICATION FAILED' "$LOG"; do sleep 2; done
  if grep -q 'APPLICATION FAILED' "$LOG"; then
    echo "API failed to start, see $LOG" >&2
    exit 1
  fi
  echo "API up on http://localhost:$API_PORT (push $mode), log: $LOG"
  echo "run: BASE_URL=http://localhost:$API_PORT QA_DB_CONTAINER=$DB_CONTAINER QA_API_LOG=$LOG QA_FCM_MODE=$mode $(dirname "$0")/qa.sh"
}

stop() {
  if [[ -f $PID_FILE ]]; then kill "$(cat "$PID_FILE")" 2>/dev/null || true; rm -f "$PID_FILE"; fi
  docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
  echo "stopped"
}

case "${1:-}" in
  start) start "${2:-}" ;;
  stop) stop ;;
  *) echo "usage: $0 start disabled|unreachable | stop" >&2; exit 2 ;;
esac
