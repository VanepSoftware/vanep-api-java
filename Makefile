GRADLEW = ./gradlew
COMPOSE = docker compose
SERVICE = vanep
POSTGRES_SERVICE = postgres

LOCAL_PROPS = src/main/resources/application-local.properties
LOCAL_EXAMPLE = src/main/resources/application-local.properties.example
ENV_FILE = .env
ENV_EXAMPLE = .env.example

.PHONY: up down nuke restart logs shell test test-coverage check boot-run build clean \
	docker-build lint lint-fix db-up db-down db-logs db-psql up-build dev setup-local setup-env

# Copia o example na primeira vez (perfil local + bootRun / Make).
setup-local:
	@test -f $(LOCAL_PROPS) || (echo "=> Criando $(LOCAL_PROPS) a partir do example." && cp $(LOCAL_EXAMPLE) $(LOCAL_PROPS))

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

up-build: setup-env
	$(COMPOSE) up -d --build

dev: db-up setup-local
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
	$(GRADLEW) bootRun --no-daemon

test:
	$(GRADLEW) test --no-daemon

test-coverage:
	$(GRADLEW) check --no-daemon

check: test-coverage

boot-run: setup-local
	$(GRADLEW) bootRun --no-daemon

build:
	$(GRADLEW) bootJar --no-daemon

clean:
	$(GRADLEW) clean --no-daemon

lint:
	$(GRADLEW) spotlessCheck --no-daemon

lint-fix:
	$(GRADLEW) spotlessApply --no-daemon
