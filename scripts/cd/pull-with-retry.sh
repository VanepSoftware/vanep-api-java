#!/usr/bin/env bash
# Pull the API and worker images, retrying when a registry transfer stalls.
# `docker compose pull` does not abort a transfer that is still trickling, and the
# CD SSH session used to share one timeout with boot. Each attempt is capped so a
# stall is killed and the next attempt can use a recovered link. Layers that
# already landed stay in the local Docker cache.
set -euo pipefail

attempts="${PULL_ATTEMPTS:-3}"
pull_timeout="${PULL_TIMEOUT:-10m}"
backoff="${PULL_BACKOFF_SECONDS:-20}"
services="${PULL_SERVICES:-vanep vanep-worker}"

if ! command -v timeout >/dev/null 2>&1; then
	echo "timeout (coreutils) não encontrado; o pull não tem como ser interrompido" >&2
	exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
	echo "docker não encontrado" >&2
	exit 1
fi

read -r -a service_args <<<"$services"

attempt=1
while [ "$attempt" -le "$attempts" ]; do
	echo "docker compose pull ${services} (tentativa ${attempt}/${attempts}, teto ${pull_timeout})"
	if timeout -k 30s "$pull_timeout" docker compose pull "${service_args[@]}"; then
		exit 0
	fi
	status=$?
	if [ "$attempt" -eq "$attempts" ]; then
		echo "docker compose pull falhou após ${attempts} tentativas (último exit ${status})" >&2
		exit 1
	fi
	echo "pull interrompido (exit ${status}); nova tentativa em ${backoff}s"
	sleep "$backoff"
	backoff=$((backoff * 2))
	attempt=$((attempt + 1))
done
