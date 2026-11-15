#!/usr/bin/env bash
# Manual QA for VAN-57 driver-students-family (constitution rule 55): vans of unlinked passengers,
# family invites, the guardian's acceptance, merge/dismiss and GET /api/drivers/me/students.
# Runs against a live API backed by a THROWAWAY database seeded on an empty schema (local-env.sh
# start). It creates passengers, dependents, links and a contract.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8082}"
QA_DB_CONTAINER="${QA_DB_CONTAINER:-vanep-van57-qa-db}"
# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

PASSENGERS=/api/drivers/me/unlinked-passengers
STUDENTS=/api/drivers/me/students

FABIO=$(login fabio.teixeira@seed.vanep.com.br)
GUSTAVO=$(login gustavo.santos@seed.vanep.com.br)
BRUNO=$(login bruno.lima@seed.vanep.com.br)
CARLA=$(login carla.nunes@seed.vanep.com.br)
ADMIN=$(login "$ADMIN_EMAIL")

SCHOOL=$(sql "select token from school where deleted_at is null order by id limit 1")
CITY=$(sql "select c.token from school s join city c on c.id = s.city_id where s.token = '$SCHOOL'")

passenger_body() {
  local name=$1 vehicle=${2-}
  jq -nc --arg n "$name" --arg s "$SCHOOL" --arg c "$CITY" --arg v "$vehicle" '
    {name: $n, schoolToken: $s, schoolShift: "MORNING",
     address: {cityToken: $c, street: "Rua QA", zipCode: "01001000", number: "1"},
     slots: [{weekday: "MONDAY", leg: "OUTBOUND", shift: "MORNING", pickupTime: "06:40"},
             {weekday: "MONDAY", leg: "RETURN", shift: "AFTERNOON", pickupTime: "12:10"}]}
    + (if $v == "" then {} else {vehicleToken: $v} end)'
}

create_passenger() {
  request POST "$PASSENGERS" "$FABIO" "$(passenger_body "$@")"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | passenger $1 | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

create_dependent() {
  local client=$1 name=$2 school=$3
  request POST /api/dependent "$client" \
    "$(jq -nc --arg n "$name" --arg s "$school" --arg c "$CITY" '{name: $n, birthDate: "2017-03-10", schoolToken: $s,
      address: {cityToken: $c, street: "Rua da Família", zipCode: "01001000", number: "9"}}')"
  [[ $RESP_STATUS == 201 ]] || { echo "SETUP FAIL | dependent $name | HTTP $RESP_STATUS $RESP_BODY" >&2; return 1; }
  jq -r .token <<<"$RESP_BODY"
}

invite_code() {
  request POST "$PASSENGERS/$1/invite" "$FABIO"
  jq -r .code <<<"$RESP_BODY"
}

accept_body() {
  jq -nc --arg d "$1" '{dependentToken: $d}'
}

student_status() {
  request GET "$STUDENTS" "$FABIO"
  jq -r --arg t "$1" '.[] | select(.token == $t) | .familyStatus' <<<"$RESP_BODY"
}

VAN_A=$(sql "select v.token from vehicle v join driver d on d.id = v.driver_id join users u on u.id = d.user_id where u.email = 'fabio.teixeira@seed.vanep.com.br' and v.deleted_at is null limit 1")
GUSTAVO_VAN=$(sql "select v.token from vehicle v join driver d on d.id = v.driver_id join users u on u.id = d.user_id where u.email = 'gustavo.santos@seed.vanep.com.br' and v.deleted_at is null limit 1")

table "Migration"
check M1 "V69 and V70 applied by Flyway" "69 70" "$(sql "select string_agg(version, ' ' order by installed_rank) from flyway_schema_history where version in ('69','70')")"

table "Van of the student"
request POST "$PASSENGERS" "$FABIO" "$(passenger_body "Ana Van" "$VAN_A")"
expect A1 "create in own van" 201 ".vehicleToken == \"$VAN_A\""
ANA_VAN=$(jq -r .token <<<"$RESP_BODY")
before=$(count unlinked_passenger)
request POST "$PASSENGERS" "$FABIO" "$(passenger_body "Bia" "$GUSTAVO_VAN")"
expect A2 "another driver's van refused" 422
check A2b "nothing created" "$before" "$(count unlinked_passenger)"
request POST "$PASSENGERS" "$FABIO" "$(passenger_body "Bia" "nope")"
expect A3 "unknown van refused" 422
request PATCH "$PASSENGERS/$ANA_VAN" "$FABIO" '{"vehicleToken": null}'
expect A4 "null vehicleToken clears the van, keeps the rest" 200 '.vehicleToken == null and .name == "Ana Van" and (.slots | length) == 2'
request PATCH "$PASSENGERS/$ANA_VAN" "$FABIO" "$(jq -nc --arg v "$VAN_A" '{vehicleToken: $v}')"
expect A5 "vehicle-only patch sets the van" 200 ".vehicleToken == \"$VAN_A\" and .schoolShift == \"MORNING\""
request PATCH "$PASSENGERS/$ANA_VAN" "$FABIO" '{"vehicleToken": 12}'
expect A6 "wrong JSON type refused" 400
request GET "/api/drivers/me/occupancy?vehicleToken=$GUSTAVO_VAN" "$FABIO"
expect A7 "occupancy with another driver's van" 404
request GET "/api/drivers/me/occupancy?vehicleToken=$VAN_A" "$FABIO"
expect A8 "occupancy filtered by own van" 200 '.students == 2'

table "Invite"
LAURA=$(create_passenger "Laura Mendes")
request POST "$PASSENGERS/$LAURA/invite" "$FABIO"
expect B1 "first invite" 200 '.code as $code | (.inviteUrl == "https://vanep.com.br/convite/" + $code) and ($code | test("^[A-HJKMNP-Z2-9]{12}$"))'
FIRST_CODE=$(jq -r .code <<<"$RESP_BODY")
FIRST_SENT=$(jq -r .sentAt <<<"$RESP_BODY")
sleep 1
request POST "$PASSENGERS/$LAURA/invite" "$FABIO"
expect B2 "resend keeps the code, refreshes sentAt" 200 ".code == \"$FIRST_CODE\" and .sentAt > \"$FIRST_SENT\""
request GET "$PASSENGERS/$LAURA" "$FABIO"
expect B3 "invitedAt on the passenger" 200 '.invitedAt != null'
request POST "$PASSENGERS/$LAURA/invite" "$GUSTAVO"
expect B4 "another driver" 404
request POST "$PASSENGERS/$LAURA/invite" "$BRUNO"
expect B5 "a client" 403
request POST "$PASSENGERS/$LAURA/invite" ""
expect B6 "no token" 401
request POST "$PASSENGERS/$LAURA/invite" "not-a-jwt"
expect B7 "invalid token" 401
request POST "$PASSENGERS/unknown/invite" "$FABIO"
expect B8 "unknown passenger" 404
check B9 "one invite row for the passenger" "1" "$(sql "select count(*) from family_invite where unlinked_passenger_id = (select id from unlinked_passenger where token = '$LAURA')")"
request GET /api/drivers/me/invite-link "$FABIO"
expect B10 "group link" 200 '.code != null'
GROUP_CODE=$(jq -r .code <<<"$RESP_BODY")
request GET /api/drivers/me/invite-link "$FABIO"
expect B11 "group link is reused" 200 ".code == \"$GROUP_CODE\""
request GET /api/drivers/me/invite-link "$BRUNO"
expect B12 "group link as a client" 403

CONCURRENT=$(create_passenger "Concorrente")
statuses=$(in_parallel POST "$FABIO" "" "$PASSENGERS/$CONCURRENT/invite" "$PASSENGERS/$CONCURRENT/invite" "$PASSENGERS/$CONCURRENT/invite")
check B13 "concurrent first invites never answer 500" "ok" "$([[ $statuses != *500* && $statuses == *200* ]] && echo ok || echo "$statuses")"
check B14 "concurrent first invites leave one invite" "1" "$(sql "select count(*) from family_invite where unlinked_passenger_id = (select id from unlinked_passenger where token = '$CONCURRENT') and deleted_at is null")"

table "Preview"
request GET "/api/family-invites/$FIRST_CODE" ""
expect C1 "anonymous preview" 200 '.driverFirstName == "Fabio" and .studentFirstName == "Laura" and .schoolName != null and (tostring | contains("Rua") | not) and (tostring | contains("06:40") | not)'
request GET "/api/family-invites/$GROUP_CODE" ""
expect C2 "group preview has no student" 200 '.driverFirstName == "Fabio" and .studentFirstName == null'
request GET /api/family-invites/NOPE2345NOPE ""
expect C3 "unknown code" 404

table "Acceptance"
RAFAEL=$(create_dependent "$BRUNO" "Rafael Lima" "$SCHOOL")
RAFA=$(create_passenger "Rafa")
RAFA_CODE=$(invite_code "$RAFA")
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" "$(accept_body "$RAFAEL")"
expect D1 "client accepts for own dependent" 200 '.clientDriverToken != null and .familyMatchToken != null'
MATCH=$(jq -r .familyMatchToken <<<"$RESP_BODY")
check D1b "link is PENDING from DRIVER_INVITE" "PENDING|DRIVER_INVITE" "$(sql "select cd.status || '|' || cd.origin from client_driver cd join client c on c.id = cd.client_id join users u on u.id = c.user_id where u.email = 'bruno.lima@seed.vanep.com.br'")"
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" "$(accept_body "$RAFAEL")"
expect D2 "repeated acceptance is idempotent" 200 ".familyMatchToken == \"$MATCH\""
check D2b "one match for the passenger" "1" "$(sql "select count(*) from family_match where unlinked_passenger_id = (select id from unlinked_passenger where token = '$RAFA')")"
links_before=$(count client_driver)
request POST "/api/family-invites/$RAFA_CODE/accept" "$CARLA" "$(accept_body "$RAFAEL")"
expect D3 "another client's dependent" 404
check D3b "no link created" "$links_before" "$(count client_driver)"
request POST "/api/family-invites/$RAFA_CODE/accept" "$FABIO" "$(accept_body "$RAFAEL")"
expect D4 "a driver cannot accept" 403
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" '{}'
expect D5 "missing dependentToken" 400
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" '{"dependentToken": 7}'
expect D6 "wrong JSON type" 400
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" '{"dependentToken": '
expect D7 "malformed JSON" 400
request POST "/api/family-invites/NOPE2345NOPE/accept" "$BRUNO" "$(accept_body "$RAFAEL")"
expect D8 "unknown code" 404
request POST "/api/family-invites/$RAFA_CODE/accept" "" "$(accept_body "$RAFAEL")"
expect D9 "accept without token" 401

DIEGO=$(login diego.alves@seed.vanep.com.br)
TWIN_DEP=$(create_dependent "$DIEGO" "Theo Alves" "$SCHOOL")
TWIN=$(create_passenger "Theo")
TWIN_CODE=$(invite_code "$TWIN")
statuses=$(in_parallel POST "$DIEGO" "$(accept_body "$TWIN_DEP")" "/api/family-invites/$TWIN_CODE/accept" "/api/family-invites/$TWIN_CODE/accept" "/api/family-invites/$TWIN_CODE/accept")
check D10 "concurrent acceptances never answer 500" "ok" "$([[ $statuses != *500* && $statuses == *200* ]] && echo ok || echo "$statuses")"
check D11 "concurrent acceptances leave one link and one match" "1|1" "$(sql "select (select count(*) from client_driver cd join client c on c.id = cd.client_id join users u on u.id = c.user_id where u.email = 'diego.alves@seed.vanep.com.br') || '|' || (select count(*) from family_match where unlinked_passenger_id = (select id from unlinked_passenger where token = '$TWIN'))")"

table "Match"
check E1 "student shows FAMILY_JOINED" "FAMILY_JOINED" "$(student_status "$RAFA")"
request GET "$PASSENGERS/$RAFA" "$FABIO"
expect E2 "match summary on the passenger" 200 '.familyMatch.dependentName == "Rafael Lima" and .familyMatch.clientName == "Bruno Lima" and (.familyMatch.dependentAge | type) == "number"'
request POST "$PASSENGERS/$RAFA/merge" "$GUSTAVO"
expect E3 "another driver cannot merge" 404
request POST "$PASSENGERS/$RAFA/family-match/dismiss" "$FABIO"
expect E4 "dismiss" 200 '.familyMatch == null and .invitedAt != null'
check E4b "back to INVITED" "INVITED" "$(student_status "$RAFA")"
request POST "$PASSENGERS/$RAFA/merge" "$FABIO"
expect E5 "merge without pending match" 409
request POST "$PASSENGERS/$RAFA/family-match/dismiss" "$FABIO"
expect E6 "dismiss without pending match" 409
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" "$(accept_body "$RAFAEL")"
expect E7 "a new acceptance suggests again" 200 '.familyMatchToken != null'
with_family_before=$(request GET /api/drivers/me/occupancy "$FABIO"; jq -r .studentsWithFamily <<<"$RESP_BODY")
statuses=$(in_parallel POST "$FABIO" "" "$PASSENGERS/$RAFA/merge" "$PASSENGERS/$RAFA/merge" "$PASSENGERS/$RAFA/merge")
check E8 "concurrent merges: one wins" "200 409 409" "$statuses"
request GET "$PASSENGERS/$RAFA" "$FABIO"
expect E9 "merged keeps token, takes the dependent's name" 200 ".token == \"$RAFA\" and .name == \"Rafael Lima\" and .dependentToken == \"$RAFAEL\" and .familyMatch == null"
request GET /api/drivers/me/occupancy "$FABIO"
expect E10 "occupancy counts the merged student with family" 200 ".studentsWithFamily == $((with_family_before + 1))"
check E11 "student is WITH_FAMILY" "WITH_FAMILY" "$(student_status "$RAFA")"
request POST "$PASSENGERS/$RAFA/invite" "$FABIO"
expect E12 "invite after merge" 409
request POST "/api/family-invites/$RAFA_CODE/accept" "$BRUNO" "$(accept_body "$RAFAEL")"
expect E13 "accept after merge" 409

table "Group link"
SOFIA_DEP=$(create_dependent "$CARLA" "Sofía Nunes" "$SCHOOL")
SOFIA=$(create_passenger "sofia")
request POST "/api/family-invites/$GROUP_CODE/accept" "$CARLA" "$(accept_body "$SOFIA_DEP")"
expect G1 "group link matches the only same first name at the school" 200 '.familyMatchToken != null'
check G1b "match is on that passenger" "FAMILY_JOINED" "$(student_status "$SOFIA")"
OTHER_DEP=$(create_dependent "$CARLA" "Pedro Nunes" "$SCHOOL")
request POST "/api/family-invites/$GROUP_CODE/accept" "$CARLA" "$(accept_body "$OTHER_DEP")"
expect G2 "no passenger with that first name: no match, link kept" 200 '.familyMatchToken == null and .clientDriverToken != null'

table "Students list"
request GET "$STUDENTS" "$FABIO"
expect F1 "both sources, ordered by name" 200 '([.[].source] | index("CONTRACT_ITEM") != null) and ([.[].source] | index("UNLINKED_PASSENGER") != null) and ([.[].name] == ([.[].name] | sort_by(ascii_downcase)))'
request GET "$STUDENTS?vehicleToken=$VAN_A" "$FABIO"
expect F2 "filter by van keeps the van and the unassigned" 200 "all(.[]; .vehicleToken == null or .vehicleToken == \"$VAN_A\")"
request GET "$STUDENTS?vehicleToken=$GUSTAVO_VAN" "$FABIO"
expect F3 "another driver's van" 404
request GET "$STUDENTS" "$BRUNO"
expect F4 "a client" 403
request GET "$STUDENTS" ""
expect F5 "no token" 401
request GET "$STUDENTS" "$GUSTAVO"
expect F6 "another driver sees none of Fabio's" 200 "all(.[]; .token != \"$RAFA\")"

table "Contract replaces the merged passenger"
BRUNO_LINK=$(sql "select cd.token from client_driver cd join client c on c.id = cd.client_id join users u on u.id = c.user_id where u.email = 'bruno.lima@seed.vanep.com.br'")
contract=$(jq -nc --arg l "$BRUNO_LINK" --arg d "$RAFAEL" --arg s "$(date -v-1m +%F 2>/dev/null || date -d '-1 month' +%F)" --arg e "$(date -v+6m +%F 2>/dev/null || date -d '+6 months' +%F)" '
  {clientDriverToken: $l, startsOn: $s, endsOn: $e, totalAmount: 4200, installments: 12, dueDay: 5,
   items: [{dependentToken: $d, slots: [{weekday: "MONDAY", leg: "OUTBOUND", shift: "MORNING", pickupTime: "06:40"}]}]}')
request POST /api/contracts "$ADMIN" "$contract"
expect H1 "admin activates a contract for the merged dependent" 201 '.status == "ACTIVE"'
request GET "$STUDENTS" "$FABIO"
expect H2 "student listed once, from the contract" 200 "([.[] | select(.dependentToken == \"$RAFAEL\")] | length) == 1 and ([.[] | select(.dependentToken == \"$RAFAEL\")][0].source == \"CONTRACT_ITEM\")"
check H3 "merged passenger soft deleted" "1" "$(sql "select count(*) from unlinked_passenger where token = '$RAFA' and deleted_at is not null")"

table "Database rules"
LAURA_ID=$(sql "select id from unlinked_passenger where token = '$LAURA'")
FABIO_DRIVER=$(sql "select d.id from driver d join users u on u.id = d.user_id where u.email = 'fabio.teixeira@seed.vanep.com.br'")
check_sql_rejected I1 "one live invite per passenger" family_invite_passenger_active_key \
  "insert into family_invite (token, code, driver_id, unlinked_passenger_id, sent_at) values ('qa-dup', 'QADUPQADUPQA', $FABIO_DRIVER, $LAURA_ID, now())"
check_sql_rejected I2 "one live group link per driver" family_invite_driver_group_active_key \
  "insert into family_invite (token, code, driver_id, sent_at) values ('qa-grp', 'QAGRPQAGRPQA', $FABIO_DRIVER, now())"
SOFIA_ID=$(sql "select id from unlinked_passenger where token = '$SOFIA'")
check_sql_rejected I3 "one pending match per passenger" family_match_pending_passenger_key \
  "insert into family_match (token, unlinked_passenger_id, dependent_id, family_invite_id, status) select 'qa-m', $SOFIA_ID, dependent_id, family_invite_id, 'PENDING' from family_match where unlinked_passenger_id = $SOFIA_ID"

echo
echo "All $PASSED scenarios passed."
