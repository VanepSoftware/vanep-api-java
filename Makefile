MVNW = ./mvnw
COMPOSE = docker compose
SERVICE = vanep
POSTGRES_SERVICE = postgres
MAILPIT_SERVICE = mailpit

ENV_FILE = .env
ENV_EXAMPLE = .env.example

.PHONY: up down nuke restart logs shell test test-coverage check boot-run build clean \
	docker-build lint lint-fix db-up db-down db-logs db-psql db-migrate db-seed up-build dev setup-env \
	mail-up mail-down mail-logs

# Docker Compose exige `.env` na raiz (sem defaults sensíveis no compose).
setup-env:
	@test -f $(ENV_FILE) || (echo "=> Crie $(ENV_FILE): cp $(ENV_EXAMPLE) $(ENV_FILE) e preencha POSTGRES_* , POSTGRES_PORT e APP_PORT." >&2 && exit 1)

up: setup-env
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

db-up: setup-env
	$(COMPOSE) up -d $(POSTGRES_SERVICE)

db-down:
	$(COMPOSE) stop $(POSTGRES_SERVICE)

db-logs:
	$(COMPOSE) logs -f $(POSTGRES_SERVICE)

db-psql:
	$(COMPOSE) exec $(POSTGRES_SERVICE) sh -c 'psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'

db-migrate: setup-env
	@bash -euo pipefail -c 'set -a && . ./$(ENV_FILE) && set +a && export POSTGRES_PORT="$${POSTGRES_PORT:-5432}" && $(MVNW) flyway:migrate'

db-seed: db-up db-migrate setup-env
	@bash -euo pipefail -c '\
		port="$$(grep -E "^POSTGRES_PORT=" "$(ENV_FILE)" | cut -d= -f2-)"; \
		port="$${port:-5432}"; \
		for _ in $$(seq 1 60); do \
			if (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
				break; \
			fi; \
			sleep 1; \
		done; \
		if ! (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
			echo "Timeout: Postgres não respondeu em 127.0.0.1:$$port." >&2; \
			exit 1; \
		fi; \
		set -a && . ./$(ENV_FILE) && set +a && \
		$(MVNW) -Dspring-boot.run.jvmArguments="-Dspring.main.web-application-type=none" \
			spring-boot:run -Dspring-boot.run.arguments="--vanep.seed.only=true"'

up-build: setup-env
	$(COMPOSE) up -d --build

dev: db-up mail-up setup-env
	@bash -euo pipefail -c '\
		port="$$(grep -E "^POSTGRES_PORT=" "$(ENV_FILE)" | cut -d= -f2-)"; \
		port="$${port:-5432}"; \
		for _ in $$(seq 1 60); do \
			if (echo >/dev/tcp/127.0.0.1/$$port) 2>/dev/null; then \
				exit 0; \
			fi; \
			sleep 1; \
		done; \
		echo "Timeout: Postgres não respondeu em 127.0.0.1:$$port (mapeamento da porta no host?)." >&2; \
		exit 1'
	@bash -euo pipefail -c 'set -a && . ./$(ENV_FILE) && set +a && $(MVNW) spring-boot:run'

test:
	$(MVNW) test

test-coverage:
	$(MVNW) verify

check: test-coverage

boot-run: setup-env
	@bash -euo pipefail -c 'set -a && . ./$(ENV_FILE) && set +a && $(MVNW) spring-boot:run'

build:
	$(MVNW) package -DskipTests

clean:
	$(MVNW) clean

mail-up: setup-env
	$(COMPOSE) up -d $(MAILPIT_SERVICE)

mail-down:
	$(COMPOSE) stop $(MAILPIT_SERVICE)

mail-logs:
	$(COMPOSE) logs -f $(MAILPIT_SERVICE)

lint:
	$(MVNW) spotless:check

lint-fix:
	$(MVNW) spotless:apply
