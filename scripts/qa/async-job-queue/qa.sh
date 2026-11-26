#!/usr/bin/env bash
# Manual QA for VAN-106, async job queue (constitution rule 55). Runs against the throwaway stack
# of local-env.sh: an API with the worker off plus separate worker processes, all on a disposable
# Postgres. It wipes notification, jobs, failed_jobs, device_token and the conversation tables.
# QA_FCM_MODE says how the stack was started: disabled (push off, jobs complete) or unreachable
# (every push fails, jobs retry and dead-letter).
set -euo pipefail

QA_STATE_DIR="${QA_STATE_DIR:?set QA_STATE_DIR to the local-env.sh state directory}"
QA_FCM_MODE="${QA_FCM_MODE:?set QA_FCM_MODE to disabled or unreachable}"
QA_ENV="$(cd "$(dirname "$0")" && pwd)/local-env.sh"
# shellcheck source=../lib.sh
. "$(cd "$(dirname "$0")" && pwd)/../lib.sh"

cleanup() { rm -f "$BODY_FILE"; }
trap cleanup EXIT

ANA_MAIL=ana.souza@seed.vanep.com.br
FABIO_MAIL=fabio.teixeira@seed.vanep.com.br

notifications_of() {
  sql "select count(*) from notification n join users u on u.id = n.user_id where u.email = '$1'"
}
push_jobs_for_notifications() {
  sql "select count(*) from jobs j join notification n on n.token = j.payload->>'notificationToken'
       where j.type = 'SEND_PUSH' and j.status = '${1:-PENDING}'"
}
# Polls a boolean SQL expression until it is true or the timeout (seconds) passes.
wait_until() {
  local condition=$1 timeout=${2:-20} waited=0
  until [[ $(sql "select ($condition)") == t ]] || (( waited >= timeout )); do
    sleep 1
    waited=$((waited + 1))
  done
}
insert_job() {
  sql "insert into jobs (token, type, payload, status, attempts, max_attempts, run_at)
       values ('$1', '$2', '$3'::jsonb, 'PENDING', 0, 5, now())"
}
insert_failed_job() {
  sql "insert into failed_jobs (token, type, payload, attempts, exception, failed_at)
       values ('$1', 'SEND_PUSH', '{\"notificationToken\":\"$2\"}'::jsonb, 5, 'qa', now())"
}
failed_column() { sql "select $2 from failed_jobs where token = '$1'"; }
log_count() { cat "$QA_STATE_DIR"/worker*.log | grep -c "$1" || true; }

echo "== setup"
sql "delete from jobs; delete from failed_jobs; delete from notification; delete from device_token;
     delete from conversation_message; delete from conversation;
     update driver set approval_status = 'UNDER_REVIEW', rejection_reason = null,
       rejection_reason_code = null, reviewed_at = null, reviewed_by = null
     where approval_status = 'REJECTED';"
ANA=$(login "$ANA_MAIL")
FABIO=$(login "$FABIO_MAIL")
ADMIN=$(login "$ADMIN_EMAIL")
CONV=$(sql "insert into conversation (token, client_id, driver_id)
            select substr(replace(gen_random_uuid()::text, '-', ''), 1, 25), client.id, driver.id
            from client join users cu on cu.id = client.user_id,
                 driver join users du on du.id = driver.user_id
            where cu.email = '$ANA_MAIL' and du.email = '$FABIO_MAIL'
            returning token" | head -1)
request POST /api/devices "$FABIO" '{"fcmToken":"qa-fabio-device:APA91b","platform":"ANDROID"}'
request POST /api/devices "$ANA" '{"fcmToken":"qa-ana-device:APA91b","platform":"ANDROID"}'
echo "tokens, conversation and devices ok"

"$QA_ENV" stop-worker worker
table "Enqueue in the request (worker stopped)"
request POST "/api/conversations/$CONV/messages" "$ANA" '{"body":"Tem vaga para a manhã?"}'
expect E1 "client sends a chat message" 201
check E2 "the driver's notification exists when the response returns" 1 "$(notifications_of "$FABIO_MAIL")"
check E3 "one pending SEND_PUSH job points at that notification" 1 "$(push_jobs_for_notifications)"
CHAT_NOTIFICATION=$(sql "select n.token from notification n join users u on u.id = n.user_id where u.email = '$FABIO_MAIL'")
request POST "/api/conversations/$CONV/messages" "$ANA" '{"body":"   "}'
expect E4 "a blank message is rejected" 400
request POST "/api/conversations/$CONV/messages" "" '{"body":"Oi"}'
expect E5 "a message without a token is rejected" 401
request POST "/api/conversations/$CONV/messages" "$ANA" '{"body":'
expect E6 "malformed JSON is rejected" 400
check E7 "rejected requests stored no notification" 1 "$(count notification)"
check E8 "and enqueued no job" 1 "$(count jobs)"
request GET /api/drivers/pending "$ADMIN"
PENDING_DRIVER=$(jq -r '.content[0].token' <<<"$RESP_BODY")
request POST "/api/drivers/$PENDING_DRIVER/reject" "$ADMIN" '{"reasonCode":"OTHER","reason":"QA VAN-106"}'
expect E9 "admin rejects the pending driver" 200
check E10 "the driver got a DRIVER_REJECTED notification" 1 "$(sql "select count(*) from notification where type = 'DRIVER_REJECTED'")"
check E11 "and its push job" 2 "$(push_jobs_for_notifications)"
request POST "/api/drivers/$PENDING_DRIVER/reject" "$ADMIN" '{"reasonCode":"OTHER","reason":"again"}'
[[ $RESP_STATUS == 4* ]] || fail "E12 rejecting twice" "4xx" "$RESP_STATUS"
pass E12 "rejecting the same driver twice is refused" "4xx"
check E13 "and enqueues nothing" 2 "$(count jobs)"
sleep 3
check E14 "with the worker off the jobs stay pending" 2 "$(push_jobs_for_notifications PENDING)"

"$QA_ENV" start-worker worker
table "Worker delivers (QA_FCM_MODE=$QA_FCM_MODE)"
if [[ $QA_FCM_MODE == disabled ]]; then
  wait_until "(select count(*) from jobs) = 0" 20
  check W1 "the worker drains the queue" 0 "$(count jobs)"
  check W2 "nothing failed" 0 "$(count failed_jobs)"
  check W3 "no notification was stored twice" 2 "$(count notification)"
else
  wait_until "(select count(*) from jobs) = 1 and (select count(*) from jobs where attempts >= 1) = 1" 20
  check W0 "the push to a recipient without devices has nothing to retry and completes" 1 "$(count jobs)"
  check W1 "the push to the driver's device failed and was rescheduled" 1 "$(sql "select count(*) from jobs where attempts >= 1 and status = 'PENDING' and run_at > now()")"
  check W2 "the error is the transient FCM failure" 1 "$(sql "select count(*) from jobs where last_error like '%Push failed transiently%'")"
  check W2b "the retry targets only the device that failed" 1 "$(sql "select count(*) from jobs where payload->'fcmTokens' = '[\"qa-fabio-device:APA91b\"]'::jsonb")"
  check W3 "no notification was stored twice" 2 "$(count notification)"
  check W4 "the devices survive the failure" 2 "$(count "device_token where deleted_at is null")"
  for attempt in 2 3 4 5; do
    sql "update jobs set run_at = now() where status = 'PENDING'" >/dev/null
    wait_until "(select count(*) from jobs where attempts < $attempt) = 0" 15
  done
  wait_until "(select count(*) from jobs) = 0" 15
  check W5 "after five attempts the push moved to failed_jobs" 1 "$(sql "select count(*) from failed_jobs where attempts = 5 and tries = 0 and not permanent")"
  check W6 "and left the queue" 0 "$(count jobs)"
  check W7 "still no duplicate notification" 2 "$(count notification)"
  sql "delete from failed_jobs" >/dev/null
fi

table "Queue mechanics"
insert_job qa-removed-type REMOVED_TYPE '{}'
insert_job qa-corrupt SEND_PUSH '{"fcmTokens":"not-a-list"}'
insert_job qa-gone SEND_PUSH '{"notificationToken":"qa-no-such-notification"}'
wait_until "(select count(*) from failed_jobs where permanent) = 3" 15
check M1 "a job of a removed type fails permanently" t "$(failed_column qa-removed-type permanent)"
check M2 "a corrupt payload fails permanently" t "$(failed_column qa-corrupt permanent)"
check M3 "a vanished notification fails permanently" t "$(failed_column qa-gone permanent)"
check M4 "none of them was retried" "1 1 1" "$(sql "select string_agg(attempts::text, ' ' order by token) from failed_jobs where token in ('qa-removed-type','qa-corrupt','qa-gone')")"
check M5 "the queue kept flowing" 0 "$(count jobs)"
sql "insert into jobs (token, type, payload, status, attempts, max_attempts, run_at, locked_at, locked_by)
     values ('qa-crashed', 'SEND_PUSH', '{\"notificationToken\":\"$CHAT_NOTIFICATION\"}'::jsonb,
             'PROCESSING', 0, 5, now(), now() - interval '1 minute', 'qa-dead-worker')" >/dev/null
wait_until "(select attempts from jobs where token = 'qa-crashed') = 1" 15
check M6 "a job left PROCESSING by a dead worker is recovered as a failed attempt" "1 PENDING" "$(sql "select attempts || ' ' || status from jobs where token = 'qa-crashed'")"
check M7 "with the dead worker named in the error" t "$(sql "select last_error like '%qa-dead-worker%' from jobs where token = 'qa-crashed'")"
sql "delete from jobs where token = 'qa-crashed'; delete from failed_jobs" >/dev/null

table "Replay of failed_jobs (cron every 15 s, batch of 2)"
for index in 1 2 3 4 5; do insert_failed_job "qa-replay-$index" "qa-gone-$index"; done
wait_until "(select count(*) from failed_jobs where last_tried_at is not null) >= 2" 20
check R1 "one replay run sends back only the batch size" 2 "$(count "failed_jobs where last_tried_at is not null")"
wait_until "(select count(*) from failed_jobs where tries = 1) = 2" 10
check R2 "a failed replay keeps the row and counts a try" 2 "$(count "failed_jobs where tries = 1")"
check R3 "never two rows for one job" 5 "$(count failed_jobs)"
check R4 "the vanished notification marks the row permanent" 2 "$(count "failed_jobs where tries = 1 and permanent")"
wait_until "(select count(*) from failed_jobs where last_tried_at is not null) >= 4" 20
check R5 "the next run takes the next two, skipping the permanent ones" 4 "$(count "failed_jobs where last_tried_at is not null")"
wait_until "(select count(*) from jobs) = 0" 15
sql "delete from failed_jobs" >/dev/null
insert_failed_job qa-replay-real "$CHAT_NOTIFICATION"
wait_until "(select count(*) from failed_jobs where last_tried_at is not null or tries > 0) = 1 or (select count(*) from failed_jobs) = 0" 20
wait_until "(select count(*) from jobs) = 0" 10
if [[ $QA_FCM_MODE == disabled ]]; then
  check R6 "a successful replay deletes the failed row" 0 "$(count failed_jobs)"
else
  check R6 "a replay that fails again keeps the same row with one more try" "1 1 false" "$(sql "select count(*) || ' ' || max(tries) || ' ' || bool_or(permanent) from failed_jobs")"
fi
check R7 "the replay did not store the notification again" 2 "$(count notification)"
sql "delete from failed_jobs" >/dev/null
insert_failed_job qa-exhausted qa-gone-x
sql "update failed_jobs set tries = 5 where token = 'qa-exhausted'" >/dev/null
sleep 17
check R8 "a row at the try limit is never replayed" "" "$(failed_column qa-exhausted last_tried_at)"
sql "delete from failed_jobs" >/dev/null

table "Two workers, one backlog"
"$QA_ENV" start-worker worker2
STARTED_BEFORE=$(log_count "failed permanently")
sql "insert into jobs (token, type, payload, status, attempts, max_attempts, run_at)
     select 'qa-bulk-' || n, 'SEND_PUSH', jsonb_build_object('notificationToken', 'qa-bulk-gone-' || n),
            'PENDING', 0, 5, now()
     from generate_series(1, 40) n" >/dev/null
wait_until "(select count(*) from jobs) = 0" 30
check T1 "the backlog drains" 0 "$(count jobs)"
check T2 "every job was processed exactly once across both workers" 40 "$(( $(log_count "failed permanently") - STARTED_BEFORE ))"
check T3 "one failed row each, none updated twice" "40 0" "$(sql "select count(*) || ' ' || max(tries) from failed_jobs where token like 'qa-bulk-%'")"
check T4 "both workers took part" 2 "$(for name in worker worker2; do grep -q "qa-bulk-" "$QA_STATE_DIR/$name.log" && echo "$name"; done | wc -l | tr -d ' ')"
sql "delete from failed_jobs" >/dev/null

table "Crash and graceful stop"
"$QA_ENV" kill-worker worker2
insert_job qa-after-crash SEND_PUSH '{"notificationToken":"qa-after-crash-gone"}'
wait_until "(select count(*) from jobs) = 0" 15
check S1 "after a worker is killed the other keeps draining" 0 "$(count jobs)"
"$QA_ENV" stop-worker worker
check S2 "a graceful stop leaves no job PROCESSING" 0 "$(count "jobs where status = 'PROCESSING'")"
check S3 "and logged no error while stopping" 0 "$(grep -cE ' ERROR ' "$QA_STATE_DIR/worker.log" || true)"
"$QA_ENV" start-worker worker
check S4 "a restarted worker announces itself again" t "$(grep -q 'Job worker started' "$QA_STATE_DIR/worker.log" && echo t || echo f)"

echo
echo "ALL PASS: $PASSED scenarios (QA_FCM_MODE=$QA_FCM_MODE)"
