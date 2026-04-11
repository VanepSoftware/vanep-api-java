SAIL ?= ./vendor/bin/sail
COMPOSE ?= docker compose
args ?=

.DEFAULT_GOAL := help

.PHONY: help
help:
	@echo "Vanep API ($(SAIL))"
	@grep -E '^[a-zA-Z][a-zA-Z0-9_.-]*:' $(MAKEFILE_LIST) | sed 's/:.*//' | sort -u

.PHONY: install
install:
	composer install

.PHONY: env
env:
	@test -f .env || cp .env.example .env

.PHONY: up
up:
	$(SAIL) up -d

.PHONY: down
down:
	$(SAIL) down

.PHONY: nuke
nuke:
	$(SAIL) down -v --remove-orphans

.PHONY: restart
restart:
	$(SAIL) restart

.PHONY: logs
logs:
	$(SAIL) logs -f

.PHONY: build-sail
build-sail:
	$(SAIL) build --no-cache

.PHONY: shell
shell:
	$(SAIL) shell

.PHONY: root-shell
root-shell:
	$(SAIL) root-shell

.PHONY: psql
psql:
	$(SAIL) psql

.PHONY: artisan
artisan:
	$(SAIL) php artisan $(args)

.PHONY: composer
composer:
	$(SAIL) composer $(args)

.PHONY: npm
npm:
	$(SAIL) npm $(args)

.PHONY: npm-install
npm-install:
	$(SAIL) npm install

.PHONY: vite-dev
vite-dev:
	$(SAIL) npm run dev

.PHONY: vite-build
vite-build:
	$(SAIL) npm run build

.PHONY: migrate
migrate:
	$(SAIL) php artisan migrate

.PHONY: migrate-fresh
migrate-fresh:
	$(SAIL) php artisan migrate:fresh

.PHONY: seed
seed:
	$(SAIL) php artisan db:seed

.PHONY: migrate-fresh-seed
migrate-fresh-seed:
	$(SAIL) php artisan migrate:fresh --seed

.PHONY: passport-install
passport-install:
	$(SAIL) php artisan passport:install

.PHONY: passport-keys
passport-keys:
	$(SAIL) php artisan passport:keys

.PHONY: optimize-clear
optimize-clear:
	$(SAIL) php artisan optimize:clear

.PHONY: key
key:
	$(SAIL) php artisan key:generate --ansi

.PHONY: test
test:
	$(SAIL) php artisan test

.PHONY: test-coverage
test-coverage:
	$(SAIL) php artisan test --coverage

.PHONY: test-host
test-host:
	composer run test

.PHONY: lint
lint:
	$(SAIL) pint --test $(PINT_ARGS)

.PHONY: lint-fix
lint-fix:
	$(SAIL) pint $(PINT_ARGS)

.PHONY: pail
pail:
	$(SAIL) php artisan pail

.PHONY: dev-host
dev-host:
	composer run dev

.PHONY: queue-work
queue-work:
	$(SAIL) php artisan queue:work

.PHONY: worker-up
worker-up:
	$(COMPOSE) exec -d laravel.test php artisan queue:work

.PHONY: worker-down
worker-down:
	$(COMPOSE) exec -T laravel.test sh -c 'pkill -TERM -f "queue:work" 2>/dev/null || true'

.PHONY: worker-restart
worker-restart: worker-down worker-up

.PHONY: install-api
install-api:
	$(SAIL) php artisan install:api

.PHONY: setup
setup: install env up key migrate
	$(SAIL) php artisan passport:install -n
	$(SAIL) npm install
	$(SAIL) npm run build
