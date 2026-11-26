#!/usr/bin/env bash
# Throwaway environment for VAN-95 (remove-app-mocks): a disposable Postgres container plus the API
# image built from the current branch, seeded on an empty schema. Needs only Docker.
# Never points at the shared dev database.
#   local-env.sh start   build the image, start Postgres + API, wait for the seed
#   local-env.sh stop    drop both containers and the network (all data is lost)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
NETWORK=vanep-van95-qa
DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van95-qa-db}"
API_CONTAINER="${QA_API_CONTAINER:-vanep-van95-qa-api}"
API_PORT="${QA_API_PORT:-8082}"
IMAGE=vanep-qa-api:van95

secret() {
  openssl rand -hex 32
}

start() {
  docker build -q -t "$IMAGE" "$ROOT" >/dev/null
  docker network create "$NETWORK" >/dev/null
  docker run -d --rm --name "$DB_CONTAINER" --network "$NETWORK" -e POSTGRES_USER=vanep \
    -e POSTGRES_PASSWORD=vanep -e POSTGRES_DB=vanep postgres:17-alpine >/dev/null
  until docker exec "$DB_CONTAINER" pg_isready -h 127.0.0.1 -U vanep -d vanep >/dev/null 2>&1; do
    sleep 1
  done
  # Keeps Git Bash on Windows from rewriting the container paths below.
  MSYS_NO_PATHCONV=1 docker run -d --name "$API_CONTAINER" --network "$NETWORK" -p "127.0.0.1:$API_PORT:8080" \
    -e SPRING_PROFILES_ACTIVE=docker -e POSTGRES_HOST="$DB_CONTAINER" -e POSTGRES_DB=vanep \
    -e POSTGRES_USER=vanep -e POSTGRES_PASSWORD=vanep \
    -e VANEP_PASSWORD_PEPPER="$(secret)" -e VANEP_CHAT_ENCRYPTION_KEY="$(secret)" \
    -e VANEP_REMEMBER_ME_KEY="$(secret)" -e VANEP_OAUTH_CLIENT_SECRET="$(secret)" \
    -e VANEP_OAUTH_ISSUER= -e GOOGLE_CLIENT_ID=qa -e GOOGLE_CLIENT_SECRET=qa \
    -e VANEP_MAIL_ENABLED=false -e VANEP_RATE_LIMIT_ENABLED=false -e VANEP_SEED_ENABLED=true \
    -e MEDIA_STORAGE_ROOT=/tmp/media -e VANEP_APP_INVITE_BASE_URL=https://vanep.com.br/convite \
    "$IMAGE" >/dev/null
  until docker logs "$API_CONTAINER" 2>&1 | grep -qE 'Seed: trips created|APPLICATION FAILED'; do
    if [[ $(docker inspect -f '{{.State.Running}}' "$API_CONTAINER") != true ]]; then break; fi
    sleep 2
  done
  if ! docker logs "$API_CONTAINER" 2>&1 | grep -q 'Seed: trips created'; then
    echo "API failed to start, see: docker logs $API_CONTAINER" >&2
    exit 1
  fi
  echo "API up on http://localhost:$API_PORT (Android emulator: http://10.0.2.2:$API_PORT)"
}

stop() {
  docker rm -f "$API_CONTAINER" >/dev/null 2>&1 || true
  docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  echo "stopped"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  *) echo "usage: $0 start|stop" >&2; exit 2 ;;
esac
