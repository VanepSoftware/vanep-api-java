#!/usr/bin/env bash
# Manual QA of the conversation opened by the service request, for the service-request-proposal
# change (constitution rule 55). Runs against a live API backed by a THROWAWAY database seeded on
# an empty schema: it ends the seeded contract, wipes conversations and re-runs V66. See qa.md
# next to the change.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

CLIENT_SR=/api/clients/me/service-requests
DRIVER_SR=/api/drivers/me/service-requests
ANA_MAIL=ana.souza@seed.vanep.com.br
BRUNO_MAIL=bruno.lima@seed.vanep.com.br
FABIO_MAIL=fabio.teixeira@seed.vanep.com.br
GUSTAVO_MAIL=gustavo.santos@seed.vanep.com.br
HELENA_MAIL=helena.costa@seed.vanep.com.br

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

driver_token_of() {
  sql "select d.token from driver d join users u on u.id = d.user_id where u.email = '$1'"
}

conversation_of() {
  sql "select c.token from conversation c join client on client.id = c.client_id
       join users u on u.id = client.user_id join driver d on d.id = c.driver_id
       where u.email = '$ANA_MAIL' and d.token = '$1' and c.deleted_at is null"
}

conversations_of() {
  sql "select count(*) from conversation c join client on client.id = c.client_id
       join users u on u.id = client.user_id join driver d on d.id = c.driver_id
       where u.email = '$ANA_MAIL' and d.token = '$1'"
}

sr_body() {
  jq -nc --arg d "$1" --arg s "$START" --arg l "$LUCAS" '{driverToken: $d, desiredStartDate: $s,
    items: [{dependentToken: $l, days: [("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
      | {weekday: ., needsOutbound: true, needsReturn: true}]}]}'
}

message() {
  jq -nc --arg b "$1" '{body: $b}'
}

echo "== setup"
[[ $(count service_request) == 1 && $(count proposal) == 1 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (the seeded request and its proposal)" >&2
  exit 1
}
ANA=$(login "$ANA_MAIL")
BRUNO=$(login "$BRUNO_MAIL")
FABIO=$(login "$FABIO_MAIL")
GUSTAVO=$(login "$GUSTAVO_MAIL")
LUCAS=$(sql "select token from dependent where name = 'Lucas Souza'")
FABIO_D=$(driver_token_of "$FABIO_MAIL")
GUSTAVO_D=$(driver_token_of "$GUSTAVO_MAIL")
HELENA_D=$(driver_token_of "$HELENA_MAIL")
START=$(sql "select current_date + 7")
SEEDED=$(sql "select token from service_request")
# While ACTIVE, the seeded contract refuses Ana's requests to Fabio.
sql "update contract set status = 'ENDED' where status = 'ACTIVE'" >/dev/null

table "V66: permission and backfill (database checks)"
check CV1 "no bundle holds create_conversation after boot" 0 \
  "$(sql "select count(*) from role_permissions where permissions @> '[\"create_conversation\"]'::jsonb")"
check CV2 "a client token does not carry it" f \
  "$(claims "$ANA" | jq -r '[.. | strings] | any(. == "create_conversation")' | sed 's/true/t/;s/false/f/')"
check CV3 "the seeded request opened Ana's conversation with Gustavo" 1 "$(conversations_of "$GUSTAVO_D")"
sql "delete from conversation_message; delete from conversation;" >/dev/null
apply_migration V66
check CV4 "V66 opens the conversation of a pair whose request had none" 1 "$(conversations_of "$GUSTAVO_D")"
apply_migration V66
check CV5 "V66 run again adds nothing" 1 "$(count conversation)"
sql "update role_permissions set permissions = permissions || '[\"create_conversation\"]'::jsonb where name = 'CLIENT'" >/dev/null
apply_migration V66
check CV6 "V66 removes the permission from a bundle that still has it" 0 \
  "$(sql "select count(*) from role_permissions where permissions @> '[\"create_conversation\"]'::jsonb")"
GUSTAVO_CONV=$(conversation_of "$GUSTAVO_D")
request GET "$CLIENT_SR/$SEEDED" "$ANA"
expect CV7 "the seeded request carries that conversation for Ana" 200 ".conversationToken == \"$GUSTAVO_CONV\""
request GET "$DRIVER_SR/$SEEDED" "$GUSTAVO"
expect CV8 "and for Gustavo" 200 ".conversationToken == \"$GUSTAVO_CONV\""

table "No conversation outside a service request"
request POST /api/conversations "$ANA" "{\"driverToken\":\"$HELENA_D\"}"
expect CV9 "the client can no longer open one directly" 404
request POST /api/conversations "" "{\"driverToken\":\"$HELENA_D\"}"
expect CV10 "no token" 401
request POST /api/conversations "$FABIO" "{\"driverToken\":\"$HELENA_D\"}"
expect CV11 "nor can a driver" 404
check CV12 "nothing was created" 1 "$(count conversation)"
request POST "$CLIENT_SR" "$ANA" '{"driverToken":"'"$HELENA_D"'","items":[]}'
expect CV13 "an invalid request to Helena" 400
request POST "$CLIENT_SR" "$ANA" "$(sr_body doesnotexist)"
expect CV14 "a request to an unknown driver" 404
check CV15 "the refused requests opened no conversation" 1 "$(count conversation)"

table "The request opens the conversation of the pair"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D")"
expect CV16 "Ana asks Fabio for a quote, with the conversation token" 201 '.conversationToken | type == "string" and length > 0'
FIRST=$(jq -r .token <<<"$RESP_BODY")
FABIO_CONV=$(jq -r .conversationToken <<<"$RESP_BODY")
check CV17 "it is the pair's only conversation" "1 $FABIO_CONV" "$(conversations_of "$FABIO_D") $(conversation_of "$FABIO_D")"
request GET /api/conversations/me "$ANA"
expect CV18 "Ana's inbox lists it" 200 "any(.[]; .token == \"$FABIO_CONV\" and .driverName == \"Fabio Teixeira\")"
request GET /api/conversations/me "$FABIO"
expect CV19 "Fabio's inbox lists it" 200 "any(.[]; .token == \"$FABIO_CONV\" and .clientName == \"Ana Souza\")"
request GET "$DRIVER_SR/$FIRST" "$FABIO"
expect CV20 "Fabio reads the request with the same conversation" 200 ".conversationToken == \"$FABIO_CONV\""
request GET "$DRIVER_SR?conversationToken=$FABIO_CONV" "$FABIO"
expect CV21 "and finds it in his list with ?conversationToken=" 200 "[.content[].token] == [\"$FIRST\"]"
request GET "$CLIENT_SR" "$ANA"
expect CV22 "every request in Ana's list carries its conversation" 200 'all(.content[]; .conversationToken != null)'
request POST "/api/conversations/$FABIO_CONV/messages" "$FABIO" "$(message "Tenho vaga de manhã.")"
expect CV23 "Fabio writes first, from the request" 201 '.sender == "DRIVER"'
request GET "/api/conversations/$FABIO_CONV/messages" "$ANA"
expect CV24 "Ana reads it" 200 '.content[0].body == "Tenho vaga de manhã."'
request GET "/api/conversations/$FABIO_CONV/messages" "$BRUNO"
expect CV25 "Bruno, outside the pair, cannot" 403

table "The conversation outlives the request"
request POST "$CLIENT_SR/$FIRST/withdraw" "$ANA"
expect CV26 "Ana withdraws, and the request still carries the conversation" 200 ".status == \"WITHDRAWN\" and .conversationToken == \"$FABIO_CONV\""
request POST "/api/conversations/$FABIO_CONV/messages" "$ANA" "$(message "Vou pensar mais um pouco.")"
expect CV27 "both still talk after the withdrawal" 201
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D")"
expect CV28 "a new request to Fabio keeps the same conversation" 201 ".conversationToken == \"$FABIO_CONV\""
SECOND=$(jq -r .token <<<"$RESP_BODY")
check CV29 "still one conversation for the pair" 1 "$(conversations_of "$FABIO_D")"
request POST "$DRIVER_SR/$SECOND/decline" "$FABIO"
expect CV30 "Fabio declines it, with the conversation" 200 ".status == \"DECLINED\" and .conversationToken == \"$FABIO_CONV\""
request POST "/api/conversations/$FABIO_CONV/messages" "$FABIO" "$(message "Se mudar o horário, me chama.")"
expect CV31 "and still writes after declining" 201
request GET "/api/conversations/$FABIO_CONV/messages" "$ANA"
expect CV32 "the history keeps every message" 200 '.totalElements == 3'

table "Races"
STATUSES=$(in_parallel POST "$ANA" "$(sr_body "$HELENA_D")" "$CLIENT_SR" "$CLIENT_SR" "$CLIENT_SR" "$CLIENT_SR" "$CLIENT_SR" "$CLIENT_SR")
check CV33 "six concurrent first requests to Helena" "201 409 409 409 409 409" "$STATUSES"
check CV34 "open one conversation" 1 "$(conversations_of "$HELENA_D")"
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$HELENA_D")"
expect CV35 "another request while that one is open" 409
check CV36 "opens no other" 1 "$(conversations_of "$HELENA_D")"

table "The lists filter by conversation"
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV" "$ANA"
expect CV37 "Ana lists her requests to Fabio from the conversation, newest first" 200 "[.content[].token] == [\"$SECOND\", \"$FIRST\"] and .totalElements == 2"
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV&status=WITHDRAWN" "$ANA"
expect CV38 "combined with the status" 200 "[.content[].token] == [\"$FIRST\"]"
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV&size=1" "$ANA"
expect CV39 "paged" 200 "[.content[].token] == [\"$SECOND\"] and .totalElements == 2"
request GET "$DRIVER_SR?conversationToken=$FABIO_CONV" "$FABIO"
expect CV40 "Fabio gets the same requests" 200 "[.content[].token] == [\"$SECOND\", \"$FIRST\"]"
request GET "$DRIVER_SR?conversationToken=$FABIO_CONV" "$GUSTAVO"
expect CV41 "Gustavo, with a conversation he is not part of, gets nothing" 200 '.totalElements == 0 and .content == []'
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV" "$BRUNO"
expect CV42 "nor does Bruno" 200 '.totalElements == 0'
request GET "$CLIENT_SR?conversationToken=$GUSTAVO_CONV" "$ANA"
expect CV43 "Ana's other conversation lists only the seeded request" 200 "[.content[].token] == [\"$SEEDED\"]"
request GET "$CLIENT_SR?conversationToken=doesnotexist" "$ANA"
expect CV44 "an unknown conversation" 200 '.totalElements == 0'
request GET "$CLIENT_SR?conversationToken=" "$ANA"
expect CV45 "a blank conversation token" 200 '.totalElements == 0'
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV&status=PENDING" "$ANA"
expect CV46 "with an invalid status" 400
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV" ""
expect CV47 "no token" 401
request GET "$CLIENT_SR?conversationToken=$FABIO_CONV" "$FABIO"
expect CV48 "the driver on the client surface" 403

echo
echo "ALL PASSED: $PASSED scenarios"
