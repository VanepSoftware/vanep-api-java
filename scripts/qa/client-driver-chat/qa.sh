#!/usr/bin/env bash
# Manual QA for the client-driver-chat change (constitution rule 55).
# Runs against a live API backed by a THROWAWAY database: it wipes the chat tables while it
# runs. See qa.md next to the change.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:?set QA_DB_CONTAINER to the throwaway Postgres container}"
QA_DB_USER="${QA_DB_USER:-vanep}"
QA_DB_NAME="${QA_DB_NAME:-vanep}"
SEED_PASSWORD="${SEED_PASSWORD:-password}"
MOBILE_CLIENT_ID="${MOBILE_CLIENT_ID:-vanep-mobile}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
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
  PASSED=$((PASSED + 1))
  printf '| %s | %s | %s | PASS |\n' "$id" "$scenario" "$status"
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

driver_token_named() {
  request GET /api/drivers/recommended "$ANA"
  jq -er --arg name "$1" '(if type == "array" then . else .content end)[] | select(.name == $name) | .token' <<<"$RESP_BODY" || {
    echo "SETUP FAIL | driver token for $1 | HTTP $RESP_STATUS $RESP_BODY" >&2
    return 1
  }
}

message_count() {
  request GET "/api/conversations/$1/messages?size=1" "$2"
  jq -r '.totalElements' <<<"$RESP_BODY"
}

# A conversation opens only with the pair's first service request; that path is exercised by
# scripts/qa/service-request-proposal/conversation.sh, so here the conversations are inserted.
open_conversation() {
  sql "insert into conversation (token, client_id, driver_id)
       select substr(replace(gen_random_uuid()::text, '-', ''), 1, 25), client.id, driver.id
       from client join users on users.id = client.user_id, driver
       where users.email = '$1' and driver.token = '$2'
       returning token"
}

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

echo "== setup"
sql "delete from conversation_message; delete from conversation;"
ANA=$(login ana.souza@seed.vanep.com.br)
BRUNO=$(login bruno.lima@seed.vanep.com.br)
CARLA=$(login carla.nunes@seed.vanep.com.br)
FABIO=$(login fabio.teixeira@seed.vanep.com.br)
GUSTAVO=$(login gustavo.santos@seed.vanep.com.br)
FABIO_DRIVER=$(driver_token_named "Fabio Teixeira")
GUSTAVO_DRIVER=$(driver_token_named "Gustavo Santos")
CONV_ANA=$(open_conversation ana.souza@seed.vanep.com.br "$FABIO_DRIVER")
CONV_BRUNO=$(open_conversation bruno.lima@seed.vanep.com.br "$FABIO_DRIVER")
CONV_CARLA=$(open_conversation carla.nunes@seed.vanep.com.br "$GUSTAVO_DRIVER")
echo "tokens and conversations ok"

echo
echo "== POST /api/conversations (removed: the service request opens the conversation)"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
request POST /api/conversations "" "{\"driverToken\":\"$GUSTAVO_DRIVER\"}"
expect M1 "no token" 401
request POST /api/conversations "$ANA" "{\"driverToken\":\"$GUSTAVO_DRIVER\"}"
expect M35 "the client can no longer open a conversation directly" 404
[[ $(sql "select count(*) from conversation") == 3 ]] || fail "M35 nothing created" "3 conversations" "$(sql "select count(*) from conversation")"

echo
echo "== POST /api/conversations/{token}/messages"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":"Tem vaga para a manhã?"}'
expect H4 "the client sends" 201 '.sender == "CLIENT" and .body == "Tem vaga para a manhã?" and .token != "" and (.sentAt | test("Z$"))'
FIRST_MESSAGE=$(jq -r .token <<<"$RESP_BODY")
request POST "/api/conversations/$CONV_ANA/messages" "$FABIO" '{"body":"Tenho sim, saio às 6h30."}'
expect H5 "the driver replies" 201 '.sender == "DRIVER"'
AT_LIMIT=$(printf 'a%.0s' $(seq 1 2000))
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" "{\"body\":\"$AT_LIMIT\"}"
expect H6 "exactly 2000 characters" 201 '.body | length == 2000'
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":"Combinado 👍 até amanhã"}'
expect H7 "emoji and accents round-trip" 201 '.body == "Combinado 👍 até amanhã"'
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":"<script>alert(1)</script>"}'
expect H8 "markup is stored as plain text" 201 '.body == "<script>alert(1)</script>"'
BEFORE=$(message_count "$CONV_ANA" "$ANA")
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" "{\"body\":\"${AT_LIMIT}a\"}"
expect M13 "2001 characters" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":"   "}'
expect M14 "only whitespace" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":""}'
expect M15 "empty string" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{}'
expect M16 "missing body" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":null}'
expect M17 "null body" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":{"text":"oi"}}'
expect M18 "body as an object" 400
request POST "/api/conversations/$CONV_ANA/messages" "$ANA" '{"body":"oi"'
expect M19 "malformed JSON" 400
request POST "/api/conversations/$CONV_ANA/messages" "$BRUNO" '{"body":"Oi, Fabio"}'
expect M20 "another client cannot write here" 403
request POST "/api/conversations/$CONV_ANA/messages" "$GUSTAVO" '{"body":"Oi, Ana"}'
expect M21 "another driver cannot write here" 403
request POST "/api/conversations/doesnotexist/messages" "$ANA" '{"body":"Oi"}'
expect M22 "unknown conversation is forbidden, not found is not leaked" 403
request POST "/api/conversations/$CONV_ANA/messages" "" '{"body":"Oi"}'
expect M23 "no token" 401
AFTER=$(message_count "$CONV_ANA" "$ANA")
RESP_STATUS="$AFTER"
[[ $BEFORE == "$AFTER" ]] || fail "M24 rejected sends stored nothing" "$BEFORE messages" "$AFTER messages"
PASSED=$((PASSED + 1))
printf '| %s | %s | %s | PASS |\n' M24 "the rejected sends above stored nothing" "$BEFORE messages before and after"
request POST "/api/conversations/$CONV_BRUNO/messages" "$BRUNO" '{"body":"Bom dia!"}'
expect H9 "a later message from another client" 201

echo
echo "== GET /api/conversations/{token}/messages"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
request GET "/api/conversations/$CONV_ANA/messages" "$ANA"
expect H10 "the client reads the history, newest first" 200 '.totalElements == 5 and .content[0].body == "<script>alert(1)</script>" and .content[4].body == "Tem vaga para a manhã?" and (.content | map(.sentAt) | . == (sort | reverse))'
request GET "/api/conversations/$CONV_ANA/messages" "$FABIO"
expect H11 "the driver reads the same history" 200 '.totalElements == 5 and ([.content[].sender] | unique == ["CLIENT", "DRIVER"])'
request GET "/api/conversations/$CONV_ANA/messages?page=1&size=2" "$ANA"
expect H12 "explicit page and size" 200 '.number == 1 and .size == 2 and (.content | length == 2) and .totalPages == 3'
request GET "/api/conversations/$CONV_ANA/messages?sort=body,asc" "$ANA"
expect M25 "a client sort is ignored" 200 '.content[0].body == "<script>alert(1)</script>"'
request GET "/api/conversations/$CONV_ANA/messages?page=-1" "$ANA"
expect M26 "negative page falls back to the first page" 200 '.number == 0'
request GET "/api/conversations/$CONV_ANA/messages?page=abc&size=xyz" "$ANA"
expect M27 "non-numeric paging falls back to the defaults" 200 '.number == 0 and .size == 30'
request GET "/api/conversations/$CONV_ANA/messages?size=100000" "$ANA"
expect M28 "an oversized page is capped" 200 '.size <= 2000'
request GET "/api/conversations/$CONV_ANA/messages" "$BRUNO"
expect M29 "another client cannot read it" 403
request GET "/api/conversations/$CONV_ANA/messages" "$GUSTAVO"
expect M30 "another driver cannot read it" 403
request GET "/api/conversations/doesnotexist/messages" "$ANA"
expect M31 "unknown conversation" 403
request GET "/api/conversations/$CONV_ANA/messages" ""
expect M32 "no token" 401

echo
echo "== GET /api/conversations/me (each party's conversations)"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
request GET /api/conversations/me "$FABIO"
expect H13 "the driver sees both clients, latest message first" 200 "length == 2 and .[0].token == \"$CONV_BRUNO\" and .[1].token == \"$CONV_ANA\" and .[0].clientName == \"Bruno Lima\" and .[0].lastMessageAt != null"
request GET /api/conversations/me "$GUSTAVO"
expect H14 "another driver sees only their own" 200 "length == 1 and .[0].token == \"$CONV_CARLA\" and .[0].lastMessageAt == null"
request GET /api/conversations/me "$ANA"
expect H15 "the client sees only their own conversation" 200 "length == 1 and .[0].token == \"$CONV_ANA\" and .[0].driverName == \"Fabio Teixeira\" and .[0].lastMessageAt != null"
request GET /api/conversations/me "$BRUNO"
expect H16 "another client sees only theirs" 200 "length == 1 and .[0].token == \"$CONV_BRUNO\""
request GET /api/conversations/me ""
expect M34 "no token" 401

echo
echo "== Encryption at rest (database checks)"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
STORED=$(sql "select body from conversation_message where token = '$FIRST_MESSAGE'")
RESP_BODY="$STORED"
[[ $STORED == v1:* && $STORED != *vaga* ]] || fail "E1 the stored body is ciphertext" "v1: prefix, no plaintext" "$STORED"
PASSED=$((PASSED + 1))
printf '| %s | %s | %s | PASS |\n' E1 "the stored body is ciphertext with the v1: prefix" "v1:..."
PLAINTEXT_ROWS=$(sql "select count(*) from conversation_message where body like '%vaga%' or body like '%script%' or body like '%Bom dia%' or body not like 'v1:%'")
RESP_BODY="$PLAINTEXT_ROWS rows"
[[ $PLAINTEXT_ROWS == 0 ]] || fail "E2 no row holds plaintext" "0 rows" "$PLAINTEXT_ROWS rows"
PASSED=$((PASSED + 1))
printf '| %s | %s | %s | PASS |\n' E2 "no row in the table holds plaintext" "0 rows"
request POST "/api/conversations/$CONV_CARLA/messages" "$CARLA" '{"body":"Oi"}'
expect E3a "same text, first send" 201
request POST "/api/conversations/$CONV_CARLA/messages" "$CARLA" '{"body":"Oi"}'
expect E3b "same text, second send" 201
DISTINCT_CIPHERTEXTS=$(sql "select count(distinct m.body) from conversation_message m join conversation c on c.id = m.conversation_id where c.token = '$CONV_CARLA'")
RESP_BODY="$DISTINCT_CIPHERTEXTS distinct values"
[[ $DISTINCT_CIPHERTEXTS == 2 ]] || fail "E3 same text, different ciphertext" "2 distinct values" "$RESP_BODY"
PASSED=$((PASSED + 1))
printf '| %s | %s | %s | PASS |\n' E3 "the same text twice is stored as two different values" "2 distinct"
request GET "/api/conversations/$CONV_CARLA/messages" "$GUSTAVO"
expect E4 "both are read back as the original text" 200 '[.content[].body] == ["Oi", "Oi"]'
sql "insert into conversation_message (token, conversation_id, sender, body, sent_at) select 'legacyqa', id, 'CLIENT', 'Mensagem antiga', now() - interval '1 day' from conversation where token = '$CONV_CARLA'" >/dev/null
request GET "/api/conversations/$CONV_CARLA/messages" "$GUSTAVO"
expect E5 "a body stored before encryption is still readable" 200 '.content[-1].body == "Mensagem antiga"'
sql "update conversation_message set body = 'v1:' || translate(substr((select body from conversation_message where token = '$FIRST_MESSAGE'), 4), 'ABCDEFGH', 'BCDEFGHA') where token = 'legacyqa'" >/dev/null
request GET "/api/conversations/$CONV_CARLA/messages" "$GUSTAVO"
expect E6 "a tampered ciphertext is never returned as text" 500 '(tostring | contains("vaga") | not)'
sql "delete from conversation_message where token = 'legacyqa'" >/dev/null
request GET "/api/conversations/$CONV_CARLA/messages" "$GUSTAVO"
expect E7 "the history is readable again once the bad row is gone" 200

echo
echo "== WebSocket /api/ws"
BASE_URL="$BASE_URL" ANA="$ANA" BRUNO="$BRUNO" FABIO="$FABIO" CONV_ANA="$CONV_ANA" \
  node "$SCRIPT_DIR/realtime.mjs"
REALTIME_PASSED=$(cat "${TMPDIR:-/tmp}/client-driver-chat-realtime.count")

echo
echo "ALL PASSED: $((PASSED + REALTIME_PASSED)) scenarios ($PASSED HTTP, $REALTIME_PASSED realtime)"
