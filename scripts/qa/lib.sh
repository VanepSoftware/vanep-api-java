# shellcheck shell=bash
# Shared helpers for the rule 55 QA scripts under scripts/qa/<change>/qa.sh. Source it after
# `set -euo pipefail`; the caller owns its own setup, cleanup trap and scenarios.

BASE_URL="${BASE_URL:-http://localhost:8081}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:?set QA_DB_CONTAINER to the throwaway Postgres container}"
QA_DB_USER="${QA_DB_USER:-vanep}"
QA_DB_NAME="${QA_DB_NAME:-vanep}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@vanep.com.br}"
MOBILE_CLIENT_ID="${MOBILE_CLIENT_ID:-vanep-mobile}"

QA_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS="$QA_LIB_DIR/../../src/main/resources/db/migration"
BODY_FILE="$(mktemp)"
RESP_STATUS=""
RESP_BODY=""
PASSED=0

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

count() {
  sql "select count(*) from $1"
}

in_parallel() {
  local method=$1 token=$2 body=$3 dir path
  shift 3
  dir="$(mktemp -d)"
  for path in "$@"; do
    curl -s -o /dev/null -w '%{http_code}\n' -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" ${body:+--data-binary "$body"} >"$(mktemp "$dir/status.XXXXXX")" &
  done
  wait
  cat "$dir"/* | sort | paste -sd ' ' -
  rm -rf "$dir"
}
