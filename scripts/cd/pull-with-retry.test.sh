#!/usr/bin/env bash
# Exercises scripts/cd/pull-with-retry.sh with fake timeout/docker/sleep binaries.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
script="$root/scripts/cd/pull-with-retry.sh"
passed=0

fail() {
	echo "FAIL: $*" >&2
	exit 1
}

setup() {
	work="$(mktemp -d)"
	bin="$work/bin"
	mkdir -p "$bin"
	export DOCKER_LOG="$work/docker.log"
	export SLEEP_LOG="$work/sleep.log"
	export DOCKER_RESULTS="$work/results"
	printf '%s\n' "$1" >"$DOCKER_RESULTS"
	: >"$DOCKER_LOG"
	: >"$SLEEP_LOG"

	cat >"$bin/timeout" <<'EOF'
#!/usr/bin/env bash
args=("$@")
while [[ ${#args[@]} -gt 0 ]]; do
	case "${args[0]}" in
	-k) args=("${args[@]:2}") ;;
	-*) args=("${args[@]:1}") ;;
	*) break ;;
	esac
done
args=("${args[@]:1}")
exec "${args[@]}"
EOF

	cat >"$bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$DOCKER_LOG"
results="$DOCKER_RESULTS"
code="$(head -n 1 "$results")"
tail -n +2 "$results" >"$results.next"
mv "$results.next" "$results"
exit "$code"
EOF

	cat >"$bin/sleep" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$1" >>"$SLEEP_LOG"
EOF

	chmod +x "$bin/timeout" "$bin/docker" "$bin/sleep"
	export PATH="$bin:$PATH"
}

assert_eq() {
	[[ "$1" == "$2" ]] || fail "$3: expected [$2] got [$1]"
}

run_expect() {
	local name="$1" results="$2" expect_status="$3"
	setup "$results"
	set +e
	PULL_ATTEMPTS=3 PULL_TIMEOUT=1s PULL_BACKOFF_SECONDS=1 \
		bash "$script" >"$work/out" 2>"$work/err"
	local status=$?
	set -e
	assert_eq "$status" "$expect_status" "$name status"
	echo "$name"
	passed=$((passed + 1))
}

run_expect "succeeds on the first attempt" "0" 0
assert_eq "$(wc -l <"$DOCKER_LOG")" "1" "first attempt call count"
assert_eq "$(cat "$DOCKER_LOG")" "compose pull vanep vanep-worker" "first attempt args"
assert_eq "$(cat "$SLEEP_LOG")" "" "first attempt does not sleep"

run_expect "retries a stall then succeeds" $'124\n1\n0' 0
assert_eq "$(wc -l <"$DOCKER_LOG")" "3" "retry call count"
assert_eq "$(paste -sd ' ' "$SLEEP_LOG")" "1 2" "backoff doubles"

run_expect "fails when every attempt fails" $'124\n124\n124' 1
assert_eq "$(wc -l <"$DOCKER_LOG")" "3" "exhausted call count"
grep -q "falhou após 3 tentativas" "$work/err"

# Missing timeout is a hard failure before any pull.
work="$(mktemp -d)"
bin="$work/bin"
mkdir -p "$bin"
printf '#!/bin/sh\nexit 0\n' >"$bin/docker"
chmod +x "$bin/docker"
set +e
# Absolute bash: a PATH= prefix is applied before bash looks the command up.
PATH="$bin" PULL_ATTEMPTS=1 /usr/bin/bash "$script" >"$work/out" 2>"$work/err"
status=$?
set -e
assert_eq "$status" "1" "missing timeout status"
grep -q "timeout" "$work/err"

echo "ALL PASS: $passed pull-retry scenarios"
