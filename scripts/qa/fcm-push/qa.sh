#!/usr/bin/env bash
# Manual QA for VAN-18, FCM push (constitution rule 55). Runs against a live API backed by a
# THROWAWAY database (see local-env.sh): it wipes device_token, conversation and
# conversation_message and notification. QA_FCM_MODE says how the API was started: disabled or unreachable.
set -euo pipefail

QA_API_LOG="${QA_API_LOG:?set QA_API_LOG to the API log file}"
QA_FCM_MODE="${QA_FCM_MODE:?set QA_FCM_MODE to disabled or unreachable}"
# shellcheck source=../lib.sh
. "$(cd "$(dirname "$0")" && pwd)/../lib.sh"

TOKEN_A="qa-fcm-token-a:APA91b-$(date +%s)"
TOKEN_B="qa-fcm-token-b:APA91b-$(date +%s)"

cleanup() { rm -f "$BODY_FILE"; }
trap cleanup EXIT

register_body() { printf '{"fcmToken":"%s","platform":"%s"}' "$1" "$2"; }
owner_of() {
  sql "select u.email from device_token d join users u on u.id = d.user_id
       where d.fcm_token = '$1' and d.deleted_at is null"
}
notifications_of() {
  sql "select count(*) from notification n join users u on u.id = n.user_id where u.email = '$1'${2:+ and n.read_at is null}"
}
wait_for_notifications() {
  local email=$1 expected=$2 waited=0
  until (( $(notifications_of "$email") >= expected )) || (( waited >= 30 )); do sleep 1; waited=$((waited + 1)); done
}
live_rows() { sql "select count(*) from device_token where fcm_token = '$1' and deleted_at is null"; }
log_lines() { grep -c "$1" "$QA_API_LOG" || true; }
PUSH_FAILED="Push notification to user"
wait_for_log() {
  local pattern=$1 expected=$2 waited=0
  until (( $(log_lines "$pattern") >= expected )) || (( waited >= 30 )); do sleep 1; waited=$((waited + 1)); done
}

echo "== setup"
sql "delete from notification; delete from device_token; delete from conversation_message; delete from conversation;"
ANA=$(login ana.souza@seed.vanep.com.br)
FABIO=$(login fabio.teixeira@seed.vanep.com.br)
ADMIN=$(login "$ADMIN_EMAIL")
CONV=$(sql "insert into conversation (token, client_id, driver_id)
            select substr(replace(gen_random_uuid()::text, '-', ''), 1, 25), client.id, driver.id
            from client join users cu on cu.id = client.user_id,
                 driver join users du on du.id = driver.user_id
            where cu.email = 'ana.souza@seed.vanep.com.br' and du.email = 'fabio.teixeira@seed.vanep.com.br'
            returning token" | head -1)
echo "tokens and conversation ok"

table "Authentication"
request POST /api/devices "" "$(register_body "$TOKEN_A" ANDROID)"
expect A1 "register without a token" 401
request POST /api/devices "not-a-jwt" "$(register_body "$TOKEN_A" ANDROID)"
expect A2 "register with an invalid token" 401
request DELETE "/api/devices/$TOKEN_A" ""
expect A3 "unregister without a token" 401
check A4 "rejected calls stored nothing" 0 "$(count device_token)"

table "Register (happy path)"
request POST /api/devices "$ANA" "$(register_body "$TOKEN_A" ANDROID)"
expect R1 "client registers an Android device" 204
check R2 "the device belongs to the client" ana.souza@seed.vanep.com.br "$(owner_of "$TOKEN_A")"
FIRST_SEEN=$(sql "select last_seen_at from device_token where fcm_token = '$TOKEN_A'")
request POST /api/devices "$ANA" "$(register_body "$TOKEN_A" IOS)"
expect R3 "registering again is idempotent" 204
check R4 "still one live row" 1 "$(live_rows "$TOKEN_A")"
check R5 "platform follows the latest call" IOS "$(sql "select platform from device_token where fcm_token = '$TOKEN_A' and deleted_at is null")"
check R6 "last_seen_at moved forward" t "$(sql "select last_seen_at > '$FIRST_SEEN' from device_token where fcm_token = '$TOKEN_A' and deleted_at is null")"
request POST /api/devices "$FABIO" "$(register_body "$TOKEN_B" ANDROID)"
expect R7 "driver registers a device" 204
request POST /api/devices "$ADMIN" "$(register_body "qa-admin-token" WEB)"
expect R8 "admin registers a web device" 204

table "Device changes hands"
request POST /api/devices "$FABIO" "$(register_body "$TOKEN_A" ANDROID)"
expect H1 "driver signs in on the client's device" 204
check H2 "the device moved to the driver" fabio.teixeira@seed.vanep.com.br "$(owner_of "$TOKEN_A")"
check H3 "no duplicate row" 1 "$(live_rows "$TOKEN_A")"

table "Validation"
request POST /api/devices "$ANA" "$(register_body "   " ANDROID)"
expect V1 "blank fcmToken" 400
request POST /api/devices "$ANA" '{"platform":"ANDROID"}'
expect V2 "missing fcmToken" 400
request POST /api/devices "$ANA" "{\"fcmToken\":\"qa-v3\"}"
expect V3 "missing platform" 400
request POST /api/devices "$ANA" "$(register_body qa-v4 SYMBIAN)"
expect V4 "unknown platform" 400
request POST /api/devices "$ANA" '{"fcmToken":"qa-v5","platform":1}'
expect V5 "platform of the wrong JSON type" 400
request POST /api/devices "$ANA" '{"fcmToken":12345,"platform":"ANDROID"}'
expect V6 "fcmToken of the wrong JSON type" 400
request POST /api/devices "$ANA" '{"fcmToken":"qa-v7",'
expect V7 "malformed JSON" 400
request POST /api/devices "$ANA" "$(register_body "$(printf 'x%.0s' $(seq 1 1025))" ANDROID)"
expect V8 "fcmToken one past the 1024 limit" 400
check V9 "rejected bodies stored nothing" 0 "$(sql "select count(*) from device_token where user_id = (select id from users where email = 'ana.souza@seed.vanep.com.br')")"
request POST /api/devices "$ANA" "$(register_body "$(printf 'y%.0s' $(seq 1 1024))" ANDROID)"
expect V10 "fcmToken exactly at the limit" 204

table "Concurrency"
CONCURRENT="qa-concurrent:APA91b-$(date +%s)"
STATUSES=$(in_parallel POST "$ANA" "$(register_body "$CONCURRENT" ANDROID)" /api/devices /api/devices /api/devices /api/devices /api/devices)
check C1 "five simultaneous registrations never fail with 5xx" "" "$(tr ' ' '\n' <<<"$STATUSES" | grep '^5' || true)"
check C2 "and leave exactly one live row" 1 "$(live_rows "$CONCURRENT")"

table "Unregister"
request DELETE "/api/devices/$TOKEN_A" "$ANA"
expect U1 "client unregisters a device that is no longer theirs" 204
check U2 "the driver's device is untouched" 1 "$(live_rows "$TOKEN_A")"
request DELETE "/api/devices/$TOKEN_A" "$FABIO"
expect U3 "driver unregisters their device" 204
check U4 "no live row left" 0 "$(live_rows "$TOKEN_A")"
check U5 "the row is soft deleted, not erased" 1 "$(sql "select count(*) from device_token where fcm_token = '$TOKEN_A' and deleted_at is not null")"
request DELETE "/api/devices/$TOKEN_A" "$FABIO"
expect U6 "unregistering twice is harmless" 204
request DELETE "/api/devices/qa-never-registered" "$FABIO"
expect U7 "unregistering an unknown token is harmless" 204
request POST /api/devices "$FABIO" "$(register_body "$TOKEN_A" ANDROID)"
expect U8 "a removed token can be registered again" 204
check U9 "with a fresh live row" 1 "$(live_rows "$TOKEN_A")"

table "Push on a business event (QA_FCM_MODE=$QA_FCM_MODE)"
FAILED_BEFORE=$(log_lines "$PUSH_FAILED")
request POST "/api/conversations/$CONV/messages" "$ANA" '{"body":"Tem vaga para a manhã?"}'
expect P1 "the chat message is sent" 201 '.sender == "CLIENT"'
if [[ $QA_FCM_MODE == unreachable ]]; then wait_for_log "$PUSH_FAILED" $((FAILED_BEFORE + 1)); else sleep 3; fi
if [[ $QA_FCM_MODE == disabled ]]; then
  check P2 "startup warned that push is disabled" 1 "$(log_lines "Push notifications are disabled: FIREBASE_CREDENTIALS_PATH is not set.")"
  check P3 "no push was attempted" "$FAILED_BEFORE" "$(log_lines "$PUSH_FAILED")"
else
  check P2 "startup did not warn: push is on" 0 "$(log_lines "Push notifications are disabled")"
  check P3 "the FCM failure was logged after the response" "$((FAILED_BEFORE + 1))" "$(log_lines "$PUSH_FAILED")"
  check P4 "on a push- pool thread" t "$(grep "$PUSH_FAILED" "$QA_API_LOG" | tail -1 | grep -q '\[ *push-' && echo t || echo f)"
fi
check P5 "the driver's devices survive a failed or skipped push" 2 "$(sql "select count(*) from device_token d join users u on u.id = d.user_id where u.email = 'fabio.teixeira@seed.vanep.com.br' and d.deleted_at is null")"
request GET "/api/conversations/$CONV/messages" "$FABIO"
expect P6 "the message was committed regardless of push" 200 '.totalElements == 1'

table "In-app notifications (QA_FCM_MODE=$QA_FCM_MODE)"
FABIO_MAIL=fabio.teixeira@seed.vanep.com.br
ANA_MAIL=ana.souza@seed.vanep.com.br
wait_for_notifications "$FABIO_MAIL" 1
check N1 "the chat message was stored for the driver, push or not" 1 "$(notifications_of "$FABIO_MAIL")"
check N2 "and not for the sender" 0 "$(notifications_of "$ANA_MAIL")"
request GET /api/notifications "$FABIO"
expect N3 "driver lists it" 200 ".totalElements == 1 and .content[0].type == \"CHAT_MESSAGE\" and .content[0].data.conversationToken == \"$CONV\" and (.content[0].title | startswith(\"Nova mensagem de \")) and .content[0].readAt == null"
NOTIFICATION=$(jq -r '.content[0].token' <<<"$RESP_BODY")
request GET /api/notifications "$ANA"
expect N4 "the sender's list is empty" 200 '.totalElements == 0'
request GET /api/notifications ""
expect N5 "listing without a token" 401
request GET /api/notifications/unread-count "$FABIO"
expect N6 "driver has one unread" 200 '.count == 1'
request POST "/api/notifications/$NOTIFICATION/read" "$ANA"
expect N7 "another user cannot mark it read" 403
request POST "/api/notifications/qa-unknown/read" "$FABIO"
expect N8 "an unknown notification cannot be marked read" 403
request POST "/api/notifications/$NOTIFICATION/read" ""
expect N9 "marking read without a token" 401
check N10 "the refused calls left it unread" 1 "$(notifications_of "$FABIO_MAIL" unread)"
request POST "/api/notifications/$NOTIFICATION/read" "$FABIO"
expect N11 "driver marks it read" 204
READ_AT=$(sql "select read_at from notification where token = '$NOTIFICATION'")
request POST "/api/notifications/$NOTIFICATION/read" "$FABIO"
expect N12 "marking read twice is harmless" 204
check N13 "and keeps the first read_at" "$READ_AT" "$(sql "select read_at from notification where token = '$NOTIFICATION'")"
request GET /api/notifications/unread-count "$FABIO"
expect N14 "no unread left" 200 '.count == 0'
request POST "/api/conversations/$CONV/messages" "$ANA" '{"body":"Pode ser às 6h40?"}'
expect N15 "a second message" 201
request POST "/api/conversations/$CONV/messages" "$FABIO" '{"body":"Pode sim."}'
expect N16 "and the driver's reply" 201
wait_for_notifications "$FABIO_MAIL" 2
wait_for_notifications "$ANA_MAIL" 1
request POST /api/notifications/read-all "$ANA"
expect N17 "client marks all of hers read" 204
check N18 "the driver's new one is still unread" 1 "$(notifications_of "$FABIO_MAIL" unread)"
check N19 "the client has none unread" 0 "$(notifications_of "$ANA_MAIL" unread)"
request GET "/api/notifications?page=0&size=1" "$FABIO"
expect N20 "the list pages, newest first" 200 ".totalElements == 2 and (.content | length) == 1 and .content[0].readAt == null"

echo
echo "ALL PASS: $PASSED scenarios (QA_FCM_MODE=$QA_FCM_MODE)"
