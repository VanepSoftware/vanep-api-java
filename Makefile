GRADLEW = ./gradlew
COMPOSE = docker compose
SERVICE = vanep

.PHONY: up down nuke restart logs shell test test-coverage check boot-run build clean docker-build lint lint-fix

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

# Estilo de código (Spotless + Google Java Format); equivalente ao Pint no Laravel
lint:
	$(GRADLEW) spotlessCheck --no-daemon

lint-fix:
	$(GRADLEW) spotlessApply --no-daemon
