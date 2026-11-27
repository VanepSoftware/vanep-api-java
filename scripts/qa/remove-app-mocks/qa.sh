#!/usr/bin/env bash
# Manual QA for VAN-95 remove-app-mocks (constitution rule 55): pending invites on
# GET /api/assistants, GET /api/assistants/me (formerLinkCount), GET /api/assistants/me/vans,
# GET /api/assistants/me/vans/{vehicleToken}/students and GET /api/clients/me/day.
# Runs against a live API backed by a THROWAWAY database seeded on an empty schema (local-env.sh
# start). It creates assistants and changes contracts, schedules, vehicles and documents.
# Run it once per fresh environment.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8082}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van95-qa-db}"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

# jq on Windows ends lines with CRLF.
jq() {
  command jq "$@" | tr -d '\r'
}

FABIO_EMAIL=fabio.teixeira@seed.vanep.com.br
ASSISTANT_EMAIL=qa.assistente@seed.vanep.com.br
OTHER_EMAIL=qa.outro@seed.vanep.com.br
INVITES=/api/assistants/invites
ME=/api/assistants/me
VANS=/api/assistants/me/vans
DAY=/api/clients/me/day

QA_PASSWORD='Qa-Senha-Forte-123!'

# Real sign-up through the public API; only the e-mail verification is flipped in the database,
# because the throwaway environment sends no mail.
create_assistant() {
  local email=$1 name=$2 document=$3
  request POST /api/auth/signup/assistant "" "$(jq -nc --arg n "$name" --arg e "$email" --arg p "$QA_PASSWORD" \
    --arg d "$document" '{name: $n, email: $e, password: $p, document: $d, acceptTerms: true}')"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | sign-up $email | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  sql "update users set verified = true where email = '$email'" >/dev/null
}

vehicle_of() {
  sql "select v.token from vehicle v join driver d on d.id = v.driver_id join users u on u.id = d.user_id
       where u.email = '$1' order by v.created_at, v.id limit 1"
}

create_assistant "$ASSISTANT_EMAIL" "Assistente QA" "93541134780"
create_assistant "$OTHER_EMAIL" "Outro Assistente QA" "15350946056"

FABIO=$(login "$FABIO_EMAIL")
GUSTAVO=$(login gustavo.santos@seed.vanep.com.br)
ANA=$(login ana.souza@seed.vanep.com.br)
BRUNO=$(login bruno.lima@seed.vanep.com.br)
ASSISTANT=$(SEED_PASSWORD="$QA_PASSWORD" login "$ASSISTANT_EMAIL")
OTHER=$(SEED_PASSWORD="$QA_PASSWORD" login "$OTHER_EMAIL")
ASSISTANT_TOKEN=$(sql "select a.token from assistant a join users u on u.id = a.user_id where u.email = '$ASSISTANT_EMAIL'")
VAN=$(vehicle_of "$FABIO_EMAIL")
OTHER_VAN=$(vehicle_of gustavo.santos@seed.vanep.com.br)
students() { echo "$VANS/$1/students${2:+?shift=$2}"; }

table "A6/A7 assistant without a driver"
request GET "$ME" "$ASSISTANT"
expect U1 "profile before any invite" 200 '.status == "UNLINKED" and .formerLinkCount == 0 and .pendingInvite == null'
request GET "$VANS" "$ASSISTANT"
expect U2 "no vans while unlinked" 200 '. == []'
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect U3 "students refused while unlinked" 404

table "A5 pending invites on the driver list"
request GET /api/assistants "$FABIO"
expect P1 "empty list before inviting" 200 '. == []'
request POST "$INVITES" "$FABIO" "{\"email\":\"$ASSISTANT_EMAIL\"}"
expect P2 "driver invites the assistant" 201
request GET /api/assistants "$FABIO"
expect P3 "pending invite listed with the typed e-mail only" 200 \
  "length == 1 and .[0].status == \"PENDING\" and .[0].name == \"$ASSISTANT_EMAIL\" and .[0].email == null and .[0].photo == null and .[0].activatedAt == null"
request GET /api/assistants "$GUSTAVO"
expect P4 "another driver does not see the invite" 200 '. == []'
sql "update assistant_invite set expires_at = now() - interval '1 minute'" >/dev/null
request GET /api/assistants "$FABIO"
expect P5 "expired invite is not listed" 200 '. == []'
sql "update assistant_invite set expires_at = now() + interval '1 day'" >/dev/null
request GET "$VANS" "$ASSISTANT"
expect P6 "no vans while the invite is pending" 200 '. == []'
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect P7 "students refused while pending" 404
request GET /api/assistants ""
expect P8 "list without token" 401
request GET /api/assistants "not-a-jwt"
expect P9 "list with invalid token" 401
request GET /api/assistants "$ANA"
expect P10 "list as a client" 403

request POST "$ME/invite/accept" "$ASSISTANT"
expect P11 "assistant accepts" 204
request GET /api/assistants "$FABIO"
expect P12 "after accepting: one ACTIVE item with the account name" 200 \
  'length == 1 and .[0].status == "ACTIVE" and .[0].name == "Assistente QA"'

table "A6 my vans"
request GET "$ME" "$ASSISTANT"
expect V1 "active profile" 200 '.status == "ACTIVE" and .formerLinkCount == 0'
request GET "$VANS" "$ASSISTANT"
expect V2 "one item per shift, on the driver's main van" 200 \
  "length >= 1 and all(.[]; .token == \"$VAN\" and .plate == \"VAN1A23\" and .studentCount >= 1) and (map(.shift) | unique | length) == length"
expect V3 "the morning runs today with a departure time" 200 \
  'any(.[]; .shift == "MORNING" and .hasRouteToday == true and .departureTime != null)'
echo "response V2: $RESP_BODY"
request GET "$VANS" "$OTHER"
expect V4 "another assistant sees nothing" 200 '. == []'
request GET "$VANS" ""
expect V5 "without token" 401
request GET "$VANS" "not-a-jwt"
expect V6 "invalid token" 401
request GET "$VANS" "$FABIO"
expect V7 "a driver is refused" 403
request GET "$VANS" "$ANA"
expect V8 "a client is refused" 403
statuses=$(in_parallel GET "$ASSISTANT" "" "$VANS" "$VANS" "$VANS" | tr -d '\r')
check V9 "parallel reads" "200 200 200" "$statuses"

table "A7 students of my van"
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect S1 "morning students, numbered from 1" 200 \
  'length >= 1 and .[0].stopNumber == 1 and any(.[]; .name == "Lucas Souza" and .hasFamilyInApp == true and .placeName != null)'
echo "response S1: $RESP_BODY"
request GET "$(students "$VAN" NIGHT)" "$ASSISTANT"
expect S2 "shift without passengers" 200 '. == []'
request GET "$(students "$VAN")" "$ASSISTANT"
expect S3 "missing shift" 400
request GET "$(students "$VAN" "")?shift=" "$ASSISTANT"
expect S4 "blank shift" 400
request GET "$(students "$VAN" BANANA)" "$ASSISTANT"
expect S5 "unknown shift" 400
request GET "$(students "$OTHER_VAN" MORNING)" "$ASSISTANT"
expect S6 "another driver's van" 404
request GET "$(students unknown MORNING)" "$ASSISTANT"
expect S7 "unknown van" 404
request GET "$(students "$VAN" MORNING)" "$OTHER"
expect S8 "assistant of nobody" 404
request GET "$(students "$VAN" MORNING)" ""
expect S9 "without token" 401
request GET "$(students "$VAN" MORNING)" "$FABIO"
expect S10 "a driver is refused" 403
sql "update vehicle set is_active = false where token = '$VAN'" >/dev/null
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect S11 "inactive van" 404
sql "update vehicle set is_active = true where token = '$VAN'" >/dev/null

table "A8/A10 the client's day"
request GET "$DAY" "$ANA"
expect C1 "today's van with the child, vehicle and times" 200 \
  '(.vans | length) >= 1 and all(.vans[]; .plate == "VAN1A23" and any(.riders[]; .name == "Lucas Souza")) and any(.vans[]; .boardingStart == "06:40:00") and .upcoming == []'
expect C2 "dependent without contract is listed" 200 \
  'any(.childrenWithoutVan[]; .name == "Marina Souza") and all(.childrenWithoutVan[]; .name != "Lucas Souza")'
expect C3 "valid inspection is not reported" 200 '.expiredInspections == []'
echo "response C1: $RESP_BODY"
request GET "$DAY" "$BRUNO"
expect C4 "another client sees none of it" 200 \
  '.vans == [] and .upcoming == [] and .expiredInspections == [] and (tostring | contains("Souza") | not)'
request GET "$DAY" ""
expect C5 "without token" 401
request GET "$DAY" "not-a-jwt"
expect C6 "invalid token" 401
request GET "$DAY" "$FABIO"
expect C7 "a driver is refused" 403
request GET "$DAY" "$ASSISTANT"
expect C8 "an assistant is refused" 403
statuses=$(in_parallel GET "$ANA" "" "$DAY" "$DAY" "$DAY" | tr -d '\r')
check C9 "parallel reads" "200 200 200" "$statuses"

table "A12 expired inspection"
VAN_ID=$(sql "select id from vehicle where token = '$VAN'")
sql "update vehicle_document set expires_at = current_date - 10
     where vehicle_id = $VAN_ID and document_type = 'VEHICLE_INSPECTION'" >/dev/null
request GET "$DAY" "$ANA"
expect E1 "expired inspection reported with driver and date" 200 \
  '(.expiredInspections | length) == 1 and .expiredInspections[0].expiredOn != null and (.expiredInspections[0].driverName | startswith("F"))'
sql "update vehicle_document set expires_at = current_date + 180
     where vehicle_id = $VAN_ID and document_type = 'VEHICLE_INSPECTION'" >/dev/null

table "A9 no route today"
TODAY=$(sql "select to_char(now() at time zone 'America/Sao_Paulo', 'FMDAY')")
sql "create table qa_slot_backup as select * from schedule_slot where weekday = '$TODAY';
     delete from schedule_slot where weekday = '$TODAY'" >/dev/null
request GET "$DAY" "$ANA"
expect N1 "no van today, next five days returned" 200 \
  '.vans == [] and (.upcoming | length) == 5 and any(.upcoming[]; .going == true)'
echo "response N1: $RESP_BODY"
sql "insert into schedule_slot overriding system value select * from qa_slot_backup;
     drop table qa_slot_backup" >/dev/null
STARTS_ON=$(sql "select starts_on from contract limit 1")
sql "update contract set starts_on = (now() at time zone 'America/Sao_Paulo')::date + 3" >/dev/null
request GET "$DAY" "$ANA"
expect N3 "contract starting soon: no van today, covered child not listed as without van" 200 \
  '.vans == [] and all(.childrenWithoutVan[]; .name != "Lucas Souza") and any(.childrenWithoutVan[]; .name == "Marina Souza")'
sql "update contract set starts_on = (now() at time zone 'America/Sao_Paulo')::date + 30" >/dev/null
request GET "$DAY" "$ANA"
expect N4 "contract starting beyond the window: child still without van" 200 \
  'any(.childrenWithoutVan[]; .name == "Lucas Souza")'
sql "update contract set starts_on = '$STARTS_ON'" >/dev/null
sql "update contract set status = 'CANCELLED'" >/dev/null
request GET "$DAY" "$ANA"
expect N2 "no ACTIVE contract: nothing today, both children without van" 200 \
  '.vans == [] and .upcoming == [] and (.childrenWithoutVan | length) >= 2'
sql "update contract set status = 'ACTIVE'" >/dev/null

table "A6 paused and former links"
request POST "/api/assistants/$ASSISTANT_TOKEN/pause" "$FABIO"
expect L1 "driver pauses" 204
request GET "$VANS" "$ASSISTANT"
expect L2 "no vans while paused" 200 '. == []'
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect L3 "students refused while paused" 404
request POST "/api/assistants/$ASSISTANT_TOKEN/resume" "$FABIO"
expect L4 "driver resumes" 204
request POST "$ME/revoke" "$ASSISTANT"
expect L5 "assistant leaves" 204
request GET "$ME" "$ASSISTANT"
expect L6 "one former link counted" 200 '.status == "UNLINKED" and .formerLinkCount == 1'
request GET "$VANS" "$ASSISTANT"
expect L7 "no vans after leaving" 200 '. == []'
request GET "$(students "$VAN" MORNING)" "$ASSISTANT"
expect L8 "students refused after leaving" 404

echo
echo "ALL $PASSED CHECKS PASSED"
