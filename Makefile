GRADLEW = ./gradlew
COMPOSE = docker compose
SERVICE = vanep
POSTGRES_SERVICE = postgres

.PHONY: up down nuke restart logs shell test test-coverage check boot-run build clean \
	docker-build lint lint-fix db-up db-down db-logs db-psql up-build dev

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

nuke:
	$(COMPOSE) down -v

restart: down up

logs:
	$(COMPOSE) logs -f $(SERVICE)

shell:
	$(COMPOSE) exec $(SERVICE) sh

docker-build:
	$(COMPOSE) build

db-up:
	$(COMPOSE) up -d $(POSTGRES_SERVICE)

db-down:
	$(COMPOSE) stop $(POSTGRES_SERVICE)

db-logs:
	$(COMPOSE) logs -f $(POSTGRES_SERVICE)

db-psql:
	$(COMPOSE) exec $(POSTGRES_SERVICE) psql -U postgres -d vanep

up-build:
	$(COMPOSE) up -d --build

dev: db-up
	@bash -euo pipefail -c '\
		port="$${POSTGRES_PORT:-5432}"; \
		for _ in $$(seq 1 60); do \
			if (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
				exit 0; \
			fi; \
			sleep 1; \
		done; \
		echo "Timeout: Postgres não respondeu em 127.0.0.1:$$port (mapeamento da porta no host?)." >&2; \
		exit 1'
	$(GRADLEW) bootRun --no-daemon

test:
	$(GRADLEW) test --no-daemon

test-coverage:
	$(GRADLEW) check --no-daemon

check: test-coverage

boot-run:
	$(GRADLEW) bootRun --no-daemon

build:
	$(GRADLEW) bootJar --no-daemon

clean:
	$(GRADLEW) clean --no-daemon

lint:
	$(GRADLEW) spotlessCheck --no-daemon

lint-fix:
	$(GRADLEW) spotlessApply --no-daemon
