#!/usr/bin/env bash
# Manual QA of the negotiation e-mails for the service-request-proposal change (constitution
# rule 55). Runs against a live API with VANEP_MAIL_ENABLED=false, which writes each e-mail to the
# application log, backed by a THROWAWAY database seeded on an empty schema. APP_LOG points to that
# log. See qa.md next to the change.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"
APP_LOG="${APP_LOG:?set APP_LOG to the log of the running API}"

CLIENT_SR=/api/clients/me/service-requests
DRIVER_SR=/api/drivers/me/service-requests
ANA_MAIL=ana.souza@seed.vanep.com.br
FABIO_MAIL=fabio.teixeira@seed.vanep.com.br
GUSTAVO_MAIL=gustavo.santos@seed.vanep.com.br
NEW_REQUEST="Nova solicitação de orçamento — Vanep"
REQUEST_DECLINED="Sua solicitação de orçamento foi recusada — Vanep"
PROPOSAL_SENT="Você recebeu uma proposta — Vanep"
ADJUSTMENT="O cliente pediu um ajuste na proposta — Vanep"
ACCEPTED="Sua proposta foi aceita — Vanep"
PROPOSAL_DECLINED="Sua proposta foi recusada — Vanep"

cleanup() {
  rm -f "$BODY_FILE"
}
trap cleanup EXIT

emails_to() {
  grep -cF "[mail desabilitado] para=$1 assunto=\"$2\"" "$APP_LOG" || true
}

all_emails() {
  grep -cF "[mail desabilitado] para=" "$APP_LOG" || true
}

last_email_to() {
  awk -v header="[mail desabilitado] para=$1 assunto=\"$2\"" '
    index($0, header) { body = ""; reading = 1; next }
    reading && /^[0-9]{4}-[0-9]{2}-[0-9]{2}T/ { reading = 0 }
    reading { body = body $0 "\n" }
    END { printf "%s", body }' "$APP_LOG"
}

has() {
  grep -qF "$3" <<<"$(last_email_to "$1" "$2")" && echo t || echo f
}

leaks() {
  grep -oE "$LEAKS" <<<"$(last_email_to "$1" "$2")" | sort -u | paste -sd, - || true
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

p_body() {
  jq -nc --arg v "$FABIO_VAN" --arg s "$START" --arg e "$END" --arg l "$LUCAS" '{startsOn: $s, endsOn: $e,
    totalAmount: 4200.00, installments: 10, dueDay: 5, adjustmentPolicy: "NONE",
    cancellationPolicy: "NOTICE_30_DAYS", vehicleToken: $v,
    items: [{dependentToken: $l, monthlyAmount: 420.00, slots: [("MONDAY", "TUESDAY", "WEDNESDAY",
      "THURSDAY", "FRIDAY") | {weekday: ., leg: "OUTBOUND", shift: "MORNING", windowStart: "06:40"}]}]}'
}

send_request() {
  request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D")"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | send a request | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

send_proposal() {
  request POST "$DRIVER_SR/$1/proposals" "$FABIO" "$(p_body)"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | send a proposal | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

echo "== setup"
[[ $(count service_request) == 1 && $(count proposal) == 1 ]] || {
  echo "SETUP FAIL | needs a database seeded on an empty schema (the seeded request and its proposal)" >&2
  exit 1
}
ANA=$(login "$ANA_MAIL")
FABIO=$(login "$FABIO_MAIL")
LUCAS=$(sql "select token from dependent where name = 'Lucas Souza'")
FABIO_D=$(sql "select d.token from driver d join users u on u.id = d.user_id where u.email = '$FABIO_MAIL'")
FABIO_VAN=$(sql "select token from vehicle where plate = 'VAN1A23'")
START=$(sql "select current_date + 7")
END=$(sql "select (current_date + 7 + interval '10 months' - interval '1 day')::date")
LEAKS="Souza|$(sql "select concat_ws('|', dependent.birth_date, address.street, address.zip_code)
                  from dependent join address on address.id = dependent.address_id where dependent.token = '$LUCAS'")"
ANA_FABIO=$(sql "select link.token from client_driver link join client on client.id = link.client_id
                 join users on users.id = client.user_id join driver on driver.id = link.driver_id
                 where users.email = '$ANA_MAIL' and driver.token = '$FABIO_D'")
# While ACTIVE, the seeded contract refuses Ana's requests to Fabio.
sql "update contract set status = 'ENDED' where status = 'ACTIVE'" >/dev/null

table "Negotiation e-mails (VANEP_MAIL_ENABLED=false, read from the log)"
check EM1 "the seed went through the services: Gustavo was told of the request, Ana of the proposal" "1 1" \
  "$(emails_to "$GUSTAVO_MAIL" "$NEW_REQUEST") $(emails_to "$ANA_MAIL" "$PROPOSAL_SENT")"
REQUEST=$(send_request)
check EM2 "Ana asks Fabio for a quote: one e-mail to Fabio" 1 "$(emails_to "$FABIO_MAIL" "$NEW_REQUEST")"
check EM3 "it names Lucas and Ana by first name" "t t" \
  "$(has "$FABIO_MAIL" "$NEW_REQUEST" Lucas) $(has "$FABIO_MAIL" "$NEW_REQUEST" Ana)"
check EM4 "and has no surname, birth date, street or zip code" "" "$(leaks "$FABIO_MAIL" "$NEW_REQUEST")"
TOTAL=$(all_emails)
request POST "$CLIENT_SR" "$ANA" "$(sr_body "$FABIO_D")"
expect EM5 "a second request to Fabio while the first is open" 409
check EM6 "the refused request sent nothing" "$TOTAL" "$(all_emails)"
send_proposal "$REQUEST" >/dev/null
check EM7 "Fabio sends version 1: one more e-mail to Ana" 2 "$(emails_to "$ANA_MAIL" "$PROPOSAL_SENT")"
PROPOSAL=$(send_proposal "$REQUEST")
check EM8 "and version 2: another one" 3 "$(emails_to "$ANA_MAIL" "$PROPOSAL_SENT")"
check EM9 "Ana's e-mail is about version 2, with Lucas's full name and the total" "t t t" \
  "$(has "$ANA_MAIL" "$PROPOSAL_SENT" "versão 2") $(has "$ANA_MAIL" "$PROPOSAL_SENT" "Lucas Souza") $(has "$ANA_MAIL" "$PROPOSAL_SENT" "R$")"
request POST "/api/clients/me/proposals/$PROPOSAL/adjustments" "$ANA" '{"field": "PRICE", "suggestedValue": "320,00"}'
expect EM10 "Ana asks for a lower price" 201
check EM11 "one e-mail to Fabio, with the field and the value" "1 t t" \
  "$(emails_to "$FABIO_MAIL" "$ADJUSTMENT") $(has "$FABIO_MAIL" "$ADJUSTMENT" Valor) $(has "$FABIO_MAIL" "$ADJUSTMENT" "320,00")"
check EM12 "and no surname" "" "$(leaks "$FABIO_MAIL" "$ADJUSTMENT")"
request POST "/api/clients/me/proposals/$PROPOSAL/decline" "$ANA" '{"reason": "Muito caro"}'
expect EM13 "Ana declines version 2" 200
check EM14 "one e-mail to Fabio, with the reason" "1 t" \
  "$(emails_to "$FABIO_MAIL" "$PROPOSAL_DECLINED") $(has "$FABIO_MAIL" "$PROPOSAL_DECLINED" "Muito caro")"
REQUEST=$(send_request)
request POST "$DRIVER_SR/$REQUEST/decline" "$FABIO" '{"reason": "Sem vaga à tarde"}'
expect EM15 "Fabio declines the next request" 200
check EM16 "one e-mail to Ana, with Fabio's name and the reason" "1 t t" \
  "$(emails_to "$ANA_MAIL" "$REQUEST_DECLINED") $(has "$ANA_MAIL" "$REQUEST_DECLINED" Fabio) $(has "$ANA_MAIL" "$REQUEST_DECLINED" "Sem vaga à tarde")"
REQUEST=$(send_request)
PROPOSAL=$(send_proposal "$REQUEST")
request POST "/api/clients/me/proposals/$PROPOSAL/accept" "$ANA"
expect EM17 "Ana accepts a proposal" 200
check EM18 "one e-mail to Fabio, naming Lucas" "1 t" \
  "$(emails_to "$FABIO_MAIL" "$ACCEPTED") $(has "$FABIO_MAIL" "$ACCEPTED" Lucas)"
check EM19 "and no surname" "" "$(leaks "$FABIO_MAIL" "$ACCEPTED")"
REQUEST=$(send_request)
PROPOSAL=$(send_proposal "$REQUEST")
TOTAL=$(all_emails)
request POST "/api/drivers/me/proposals/$PROPOSAL/withdraw" "$FABIO"
expect EM20 "Fabio withdraws his proposal" 200
sql "update service_request set expires_at = now() - interval '1 minute' where token = '$REQUEST'" >/dev/null
wait_for "select status = 'EXPIRED' from service_request where token = '$REQUEST'" 75 || true
check EM21 "the sweep expires the reopened request" EXPIRED "$(sql "select status from service_request where token = '$REQUEST'")"
check EM22 "neither the withdrawal nor the expiration sent an e-mail" "$TOTAL" "$(all_emails)"
REQUEST=$(send_request)
TOTAL=$(all_emails)
request POST "$CLIENT_SR/$REQUEST/withdraw" "$ANA"
expect EM23 "Ana withdraws a request" 200
check EM24 "the withdrawal sent nothing" "$TOTAL" "$(all_emails)"
send_request >/dev/null
TOTAL=$(all_emails)
request POST "/api/client-drivers/$ANA_FABIO/block" "$ANA"
expect EM25 "Ana blocks Fabio during a negotiation" 200 '.blockedBy == "CLIENT"'
check EM26 "the block sent nothing" "$TOTAL" "$(all_emails)"
echo
echo "ALL PASSED: $PASSED scenarios"
