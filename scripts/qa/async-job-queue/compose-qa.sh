#!/usr/bin/env bash
# Compose topology QA for VAN-106: runs the repository's docker-compose.yml in an isolated project
# (own .env, own Postgres volume, ports 8090/5438) and checks the API/worker split and the CD
# worker check. The image is the branch jar on the Dockerfile's runtime base, user and entrypoint,
# so the check does not rebuild Maven inside Docker. Tears everything down on exit.
# Run after `./mvnw -DskipTests package`.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
# Under $HOME: Colima and Docker Desktop share it with the VM; /tmp may not be.
WORK="${QA_COMPOSE_DIR:-$HOME/.cache/van106-compose}"
PROJECT=van106qa
API_PORT=8090
PASSED=0

compose() { (cd "$WORK" && docker compose -p "$PROJECT" "$@"); }
psql_qa() { compose exec -T postgres psql -U vanep -d vanep -Atqc "$1"; }
fail() { echo "FAIL | $1 | expected: $2 | observed: $3" >&2; exit 1; }
check() {
  [[ $4 == "$3" ]] || fail "$1 $2" "$3" "$4"
  PASSED=$((PASSED + 1))
  printf '| %s | %s | %s | PASS |\n' "$1" "$2" "$3"
}
container_state() { docker inspect -f '{{.State.Status}} {{.RestartCount}}' "$(compose ps -aq "$1")"; }

cleanup() { compose down -v >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== setup"
compose down -v >/dev/null 2>&1 || true
rm -rf "$WORK"
mkdir -p "$WORK/image" "$WORK/logs" "$WORK/media"
chmod 777 "$WORK/logs" "$WORK/media"
cp "$ROOT"/target/vanep-*.jar "$WORK/image/app.jar"
# Same runtime base, fixed UID, writable dirs, layered jar and entrypoint as the
# Dockerfile's final stage. The fat jar is extracted here so this check does not
# rebuild Maven inside Docker.
cat >"$WORK/image/Dockerfile" <<'EOF'
FROM eclipse-temurin:25-jre-jammy AS layers
WORKDIR /extract
COPY app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination /out \
  && appjar="$(find /out/application -maxdepth 1 -type f -name '*.jar' -print -quit)" \
  && if [ "$appjar" != /out/application/app.jar ]; then mv "$appjar" /out/application/app.jar; fi \
  && for dir in /out/snapshot-dependencies /out/spring-boot-loader; do \
       if [ -z "$(find "$dir" -mindepth 1 -print -quit)" ]; then touch "$dir/.keep"; fi; \
     done

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN groupadd --gid 1500 spring && useradd --uid 1500 --gid 1500 --no-create-home spring \
  && mkdir -p /app/.dev /app/logs && chown -R spring:spring /app/.dev /app/logs
USER spring:spring
COPY --from=layers /out/dependencies/ ./
COPY --from=layers /out/spring-boot-loader/ ./
COPY --from=layers /out/snapshot-dependencies/ ./
COPY --from=layers /out/application/ ./
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
EOF
docker build -q -t van106-qa:local "$WORK/image" >/dev/null
cp "$ROOT/docker-compose.yml" "$WORK/docker-compose.yml"
cat >"$WORK/.env" <<EOF
VANEP_IMAGE=van106-qa:local
APP_PORT=$API_PORT
POSTGRES_DB=vanep
POSTGRES_USER=vanep
POSTGRES_PASSWORD=vanep
POSTGRES_PORT=5438
MEDIA_STORAGE_ROOT=$WORK/media
VANEP_LOG_DIR=$WORK/logs
VANEP_MAIL_ENABLED=false
VANEP_SEED_ENABLED=true
EXPIRY_SWEEP_ENABLED=true
VANEP_PASSWORD_PEPPER=qa-pepper
VANEP_REMEMBER_ME_KEY=qa-remember-me
VANEP_CHAT_ENCRYPTION_KEY=7e577e577e577e577e577e577e577e577e577e577e577e577e577e577e577e57
GOOGLE_CLIENT_ID=qa
GOOGLE_CLIENT_SECRET=qa
VANEP_OAUTH_CLIENT_SECRET=qa-web-client-secret
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=40.0
EOF
compose up -d --no-build >/dev/null 2>&1
for _ in $(seq 1 90); do
  status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$API_PORT/login" || true)
  [[ $status =~ ^[23] ]] && break
  sleep 2
done
for _ in $(seq 1 60); do
  compose logs vanep-worker 2>/dev/null | grep >/dev/null 'Job worker started' && break
  sleep 2
done
echo "stack up"

echo
echo "== Topology"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
check C1 "the API answers on its published port" t "$([[ $status =~ ^[23] ]] && echo t || echo f)"
check C2 "both containers run with no restarts" "running 0|running 0" "$(container_state vanep)|$(container_state vanep-worker)"
jvm_flags() { compose exec -T "$1" cat /proc/1/cmdline | tr '\0' ' '; }
check C10 "the API JVM honors JAVA_OPTS" t "$(jvm_flags vanep | grep -q 'MaxRAMPercentage=40.0' && echo t || echo f)"
check C11 "the worker JVM uses its own heap" t "$(jvm_flags vanep-worker | grep -q 'MaxRAMPercentage=25.0' && echo t || echo f)"
check C3 "the worker publishes no port" "" "$(compose port vanep-worker 8080 2>/dev/null || true)"
check C4 "the worker announced itself" 1 "$(compose logs vanep-worker | grep -c 'Job worker started')"
check C5 "the API runs no worker" 0 "$(compose logs vanep | grep -c 'Job worker started' || true)"
check C6 "the seeders ran in the API only" "t 0" "$(compose logs vanep | grep >/dev/null 'Seed:' && echo t || echo f) $(compose logs vanep-worker | grep -c 'Seed:' || true)"
check C7 "each process writes its own log file" "vanep-api.log vanep-worker.log" "$(ls "$WORK/logs" | grep -E '^vanep-(api|worker)\.log$' | sort | paste -sd ' ' -)"
check C8 "Flyway created the queue tables once" "81 2" "$(psql_qa "select max(version::int) from flyway_schema_history") $(psql_qa "select count(*) from information_schema.tables where table_name in ('jobs','failed_jobs')")"
psql_qa "insert into jobs (token, type, payload, status, attempts, max_attempts, run_at)
         values ('compose-removed', 'REMOVED_TYPE', '{}'::jsonb, 'PENDING', 0, 5, now())" >/dev/null
for _ in $(seq 1 45); do [[ $(psql_qa "select count(*) from jobs") == 0 ]] && break; sleep 1; done
check C9 "the worker container processes the queue" "0 1" "$(psql_qa "select count(*) from jobs") $(psql_qa "select count(*) from failed_jobs where permanent")"

echo
echo "== Stop and CD worker check"
echo "| id | scenario | expected | result |"
echo "|---|---|---|---|"
# The CD check, verbatim in spirit: running with no restarts and the start line in the logs.
cd_worker_check() {
  local state
  state=$(container_state vanep-worker 2>/dev/null || true)
  [[ $state == "running 0" ]] || return 1
  compose logs vanep-worker 2>/dev/null | grep >/dev/null "Job worker started"
}
check D1 "the CD worker check passes on a healthy worker" pass "$(cd_worker_check && echo pass || echo fail)"
started=$(date +%s)
compose stop vanep-worker >/dev/null 2>&1
check D2 "docker compose stop ends the worker gracefully, not by SIGKILL" t "$([[ $(docker inspect -f '{{.State.ExitCode}}' "$(compose ps -aq vanep-worker)") != 137 ]] && echo t || echo f)"
check D3 "within the 45 s grace period" t "$([[ $(( $(date +%s) - started )) -lt 45 ]] && echo t || echo f)"
check D4 "the CD worker check fails on a stopped worker" fail "$(cd_worker_check && echo pass || echo fail)"

echo
echo "ALL PASS: $PASSED scenarios (compose topology)"
