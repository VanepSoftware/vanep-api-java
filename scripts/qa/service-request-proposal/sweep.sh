#!/usr/bin/env bash
# Manual QA of the expiry sweep for the service-request-proposal change (constitution rule 55).
# Runs against a live API with the sweep on at its default one-minute interval and
# EXPIRY_SWEEP_BATCH_SIZE=2, backed by a THROWAWAY database seeded on an empty schema: it moves
# deadlines into the past by SQL and waits for the rounds. See qa.md next to the change.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

CLIENT_SR=/api/clients/me/service-requests

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

request_status() {
  sql "select status from service_request where token = '$1'"
}

wait_for() {
  local _
  for _ in $(seq 1 "$2"); do
    [[ $(sql "$1") == t ]] && return 0
    sleep 1
  done
  return 1
}

sr_body() {
  jq -nc --arg d "$1" --arg s "$START" --arg l "$LUCAS" '{driverToken: $d, desiredStartDate: $s,
    items: [{dependentToken: $l, days: [("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
      | {weekday: ., needsOutbound: true, needsReturn: true}]}]}'
}

send_request() {
  request POST "$CLIENT_SR" "$ANA" "$(sr_body "$1")"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | send a request | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

echo "== setup"
SEED_REQUEST=$(sql "select token from service_request order by id limit 1")
SEED_PROPOSAL=$(sql "select token from proposal order by id limit 1")
[[ $(count service_request) == 1 && $(request_status "$SEED_REQUEST") == ANSWERED
  && $(sql "select status from proposal where token = '$SEED_PROPOSAL'") == SENT ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (the seeded request ANSWERED with its SENT proposal)" >&2
  exit 1
}
ANA=$(login ana.souza@seed.vanep.com.br)
LUCAS=$(sql "select token from dependent where name = 'Lucas Souza'")
FABIO_D=$(sql "select d.token from driver d join users u on u.id = d.user_id where u.email = 'fabio.teixeira@seed.vanep.com.br'")
HELENA_D=$(sql "select d.token from driver d join users u on u.id = d.user_id where u.email = 'helena.costa@seed.vanep.com.br'")
START=$(sql "select current_date + 7")
# While ACTIVE, the seeded contract refuses Ana's requests to Fabio.
sql "update contract set status = 'ENDED' where status = 'ACTIVE'" >/dev/null

table "Expiry sweep (one-minute interval, batch of 2)"
FABIO_REQ=$(send_request "$FABIO_D")
HELENA_REQ=$(send_request "$HELENA_D")
sql "update proposal set valid_until = now() - interval '1 minute' where token = '$SEED_PROPOSAL';
     update service_request set expires_at = now() - interval '1 minute' where token = '$FABIO_REQ'" >/dev/null
wait_for "select status = 'EXPIRED' from proposal where token = '$SEED_PROPOSAL'" 75 || true
wait_for "select status = 'EXPIRED' from service_request where token = '$FABIO_REQ'" 5 || true
check SW1 "within one interval the overdue proposal is EXPIRED" EXPIRED \
  "$(sql "select status from proposal where token = '$SEED_PROPOSAL'")"
check SW2 "its request is OPEN again for 72 hours instead of expiring in the same round" "OPEN 72" \
  "$(sql "select status || ' ' || round(extract(epoch from expires_at - now()) / 3600) from service_request
          where token = '$SEED_REQUEST'")"
check SW3 "the overdue OPEN request is EXPIRED in the same round" EXPIRED "$(request_status "$FABIO_REQ")"
check SW4 "the request still on time is untouched" OPEN "$(request_status "$HELENA_REQ")"
request POST "/api/clients/me/proposals/$SEED_PROPOSAL/accept" "$ANA"
expect SW5 "accepting the expired proposal" 409 '.detail == "Esta proposta não é mais a versão atual."'
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D")"
expect SW6 "Ana asks Fabio again: the expired request no longer counts as open" 201 '.status == "OPEN"'
FABIO_REQ=$(jq -r .token <<<"$RESP_BODY")
THREE="'$SEED_REQUEST', '$FABIO_REQ', '$HELENA_REQ'"
sql "update service_request set expires_at = now() - case token when '$SEED_REQUEST' then interval '3 minutes'
       when '$FABIO_REQ' then interval '2 minutes' else interval '1 minute' end where token in ($THREE)" >/dev/null
wait_for "select count(*) >= 1 from service_request where token in ($THREE) and status = 'EXPIRED'" 75 || true
check SW7 "three overdue requests: a round expires only the two oldest" "EXPIRED EXPIRED OPEN" \
  "$(request_status "$SEED_REQUEST") $(request_status "$FABIO_REQ") $(request_status "$HELENA_REQ")"
wait_for "select status = 'EXPIRED' from service_request where token = '$HELENA_REQ'" 75 || true
check SW8 "the next round expires the third" EXPIRED "$(request_status "$HELENA_REQ")"
check SW9 "the two rounds ran about one interval apart (55 to 65 seconds)" t \
  "$(sql "select extract(epoch from third.updated_at - first.updated_at) between 55 and 65
          from service_request first, service_request third
          where first.token = '$SEED_REQUEST' and third.token = '$HELENA_REQ'")"
echo
echo "ALL PASSED: $PASSED scenarios"
