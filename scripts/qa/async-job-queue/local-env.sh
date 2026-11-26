#!/usr/bin/env bash
# Throwaway environment for VAN-106 (async job queue): a disposable Postgres container, the API
# from the current branch with the worker OFF, and a separate worker process with it ON, the same
# split docker-compose runs in production. Never points at the shared dev database.
#   local-env.sh start disabled|unreachable   build, start Postgres, the API and one worker
#                                              (unreachable: a fake service account whose token
#                                              endpoint is http://localhost:1, so every push fails)
#   local-env.sh start-worker [name]           start one more worker (default name: worker)
#   local-env.sh stop-worker [name]            SIGTERM: graceful stop
#   local-env.sh kill-worker [name]            SIGKILL: crash, no cleanup
#   local-env.sh stop                          stop everything and drop the container
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van106-qa-db}"
DB_PORT="${QA_DB_PORT:-5437}"
API_PORT="${QA_API_PORT:-8084}"
STATE_DIR="${TMPDIR:-/tmp}/vanep-van106"
JAR="$STATE_DIR/api.jar"
CREDENTIALS="$STATE_DIR/fake-service-account.json"
MODE_FILE="$STATE_DIR/mode"

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

credentials_path() {
  if [[ $(cat "$MODE_FILE") == unreachable ]]; then echo "$CREDENTIALS"; fi
}

# Starts the jar with the branch's .env plus the throwaway database. Extra VAR=value pairs set
# the role of the process (API or worker).
launch() {
  local name=$1 port=$2
  shift 2
  (
    cd "$ROOT"
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
    env SPRING_PROFILES_ACTIVE=local SERVER_PORT="$port" POSTGRES_PORT="$DB_PORT" \
      POSTGRES_USER=vanep POSTGRES_PASSWORD=vanep POSTGRES_DB=vanep VANEP_MAIL_ENABLED=false \
      VANEP_OAUTH_ISSUER= MEDIA_STORAGE_ROOT="$STATE_DIR/media" \
      FIREBASE_CREDENTIALS_PATH="$(credentials_path)" "$@" \
      nohup java -jar "$JAR" >"$STATE_DIR/$name.log" 2>&1 &
    echo $! >"$STATE_DIR/$name.pid"
  )
}

wait_for_log() {
  local name=$1 pattern=$2
  until grep -qE "$pattern|APPLICATION FAILED" "$STATE_DIR/$name.log" 2>/dev/null; do sleep 2; done
  if grep -q 'APPLICATION FAILED' "$STATE_DIR/$name.log"; then
    echo "$name failed to start, see $STATE_DIR/$name.log" >&2
    exit 1
  fi
}

start_worker() {
  local name=${1:-worker} port
  port=$((API_PORT + 1 + $(ls "$STATE_DIR"/*.pid 2>/dev/null | wc -l)))
  : >"$STATE_DIR/$name.log"
  # Short replay cron, batch and lock timeout so the QA sees replays and recovery in seconds.
  launch "$name" "$port" WORKER_ENABLED=true EXPIRY_SWEEP_ENABLED=false VANEP_SEED_ENABLED=false \
    VANEP_GEOGRAPHIC_DATA_SEED_ENABLED=false VANEP_WORKER_REPLAY_CRON='*/15 * * * * *' \
    VANEP_WORKER_REPLAY_BATCH_SIZE=2 VANEP_WORKER_LOCK_TIMEOUT=PT20S
  wait_for_log "$name" 'Job worker started'
  echo "$name up (log: $STATE_DIR/$name.log)"
}

signal_worker() {
  local signal=$1 name=${2:-worker} pid_file
  pid_file="$STATE_DIR/$name.pid"
  [[ -f $pid_file ]] || return 0
  kill "-$signal" "$(cat "$pid_file")" 2>/dev/null || true
  while kill -0 "$(cat "$pid_file")" 2>/dev/null; do sleep 1; done
  rm -f "$pid_file"
}

start() {
  local mode=${1:?usage: local-env.sh start disabled|unreachable}
  rm -rf "$STATE_DIR"
  mkdir -p "$STATE_DIR/media"
  echo "$mode" >"$MODE_FILE"
  if [[ $mode == unreachable ]]; then write_fake_credentials; fi
  (cd "$ROOT" && ./mvnw -q -o -DskipTests package && cp target/vanep-0.0.1-SNAPSHOT.jar "$JAR")
  docker run -d --rm --name "$DB_CONTAINER" -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
    -e POSTGRES_DB=vanep -p "127.0.0.1:$DB_PORT:5432" postgres:17-alpine >/dev/null
  until docker exec "$DB_CONTAINER" pg_isready -U vanep -d vanep >/dev/null 2>&1; do sleep 1; done
  launch api "$API_PORT" WORKER_ENABLED=false VANEP_SEED_ENABLED=true
  wait_for_log api 'Seed: trips created'
  echo "API up on http://localhost:$API_PORT, worker off (log: $STATE_DIR/api.log)"
  start_worker worker
  echo "run: BASE_URL=http://localhost:$API_PORT QA_DB_CONTAINER=$DB_CONTAINER QA_STATE_DIR=$STATE_DIR QA_FCM_MODE=$mode $(dirname "$0")/qa.sh"
}

stop() {
  local pid_file
  for pid_file in "$STATE_DIR"/*.pid; do
    [[ -f $pid_file ]] || continue
    kill "$(cat "$pid_file")" 2>/dev/null || true
    rm -f "$pid_file"
  done
  docker stop "$DB_CONTAINER" >/dev/null 2>&1 || true
  echo "stopped"
}

case "${1:-}" in
  start) start "${2:-}" ;;
  start-worker) start_worker "${2:-worker}" ;;
  stop-worker) signal_worker TERM "${2:-worker}" ;;
  kill-worker) signal_worker KILL "${2:-worker}" ;;
  stop) stop ;;
  *) echo "usage: $0 start disabled|unreachable | start-worker|stop-worker|kill-worker [name] | stop" >&2; exit 2 ;;
esac
