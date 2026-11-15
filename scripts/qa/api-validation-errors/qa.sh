#!/usr/bin/env bash
# Running-API QA for api-validation-errors (constitution rule 55).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@vanep.com.br}"
CLIENT_EMAIL="${CLIENT_EMAIL:-ana.souza@seed.vanep.com.br}"
MOBILE_CLIENT_ID="${MOBILE_CLIENT_ID:-vanep-mobile}"

BODY_FILE="$(mktemp)"
RESP_STATUS=""
RESP_BODY=""
PASSED=0

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

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

request() {
  local method=$1 path=$2 token=$3 body=${4-}
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  if [[ -n $token ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n $body ]]; then args+=(-H "Content-Type: application/json" --data-binary "$body"); fi
  RESP_STATUS=$(curl "${args[@]}")
  RESP_BODY=$(cat "$BODY_FILE")
}

expect() {
  local id=$1 scenario=$2 status=$3 assertion=$4
  if [[ $RESP_STATUS != "$status" ]] || ! jq -e "$assertion" <<<"$RESP_BODY" >/dev/null; then
    echo "FAIL | $id | $scenario | expected HTTP $status and $assertion" >&2
    echo "observed HTTP $RESP_STATUS | $RESP_BODY" >&2
    exit 1
  fi
  PASSED=$((PASSED + 1))
  printf '| %s | %s | %s | PASS |\n' "$id" "$scenario" "$status"
}

echo "== setup"
ADMIN=$(login "$ADMIN_EMAIL")
CLIENT=$(login "$CLIENT_EMAIL")
request GET /api/dependent "$CLIENT"
DEPENDENT=$(jq -er '.[0].token' <<<"$RESP_BODY")

echo '| id | scenario | expected | result |'
echo '|---|---|---|---|'

request POST /api/contracts "$ADMIN" '{"dueDay":31}'
expect G1 'global response exposes resolved contract error' 400 \
  '.status == 400 and .detail != "Invalid request content." and (.errors | any(.field == "dueDay" and .message == "O dia de vencimento deve ser de 1 a 28."))'

request PATCH "/api/dependent/$DEPENDENT" "$CLIENT" \
  '{"address":{"cityToken":"","street":"Rua QA","zipCode":"123"}}'
expect G2 'nested validation keeps the full field path' 400 \
  '.status == 400 and (.errors | any(.field == "address.zipCode" and .message == "CEP em formato inválido (8 dígitos)."))'

request POST /api/user/me/email-change "$CLIENT" '{"email":""}'
expect P1 'profile e-mail envelope is preserved' 400 \
  '.code == "email_required" and .field == "email" and (.status | not) and (.errors | not)'

request POST /api/auth/email/verify '' '{"email":"","code":""}'
expect A1 'auth envelope wins on the shared controller' 400 \
  '.code == "validation_error" and (.errors | any(.field == "email")) and (.errors | any(.field == "code")) and (.status | not)'

echo
echo "Result: $PASSED/4 passed"
