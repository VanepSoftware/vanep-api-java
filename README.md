<p align="center">
  <a href="https://laravel.com" target="_blank">
    <img src="https://raw.githubusercontent.com/laravel/art/master/logo-lockup/5%20SVG/2%20CMYK/1%20Full%20Color/laravel-logolockup-cmyk-red.svg" width="200" alt="Laravel" />
  </a>
</p>

# Vanep API

API for the Vanep application (Laravel, Passport, Sail).

---

## Requirements

- PHP **8.3+** (Composer); Sail runtime in this repo uses **PHP 8.5**
- Composer
- Docker e Docker Compose
- Node.js **20+** and npm (for Vite assets; can also run via Sail)

---

## How to install

### Install Docker

#### Linux

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y docker.io docker-compose-plugin

# Adicionar seu usuário ao grupo docker (evita usar sudo)
sudo usermod -aG docker $USER
newgrp docker
```

> Para outras distros, siga a [documentação oficial](https://docs.docker.com/engine/install/).

#### Windows

1. Instale o [Docker Desktop para Windows](https://docs.docker.com/desktop/install/windows-install/)
2. Certifique-se de que o **WSL 2** está habilitado (recomendado)
3. Após instalar, o comando `docker compose` já estará disponível no terminal

---

### Set up the application

#### 1. Copy environment file

```bash
cp .env.example .env
```

Configure database and Redis for Sail (see **Environment for Sail** below) before running migrations.

#### 2. Install dependencies

```bash
composer install
```

#### 3. Start containers

```bash
make up
```

#### 4. Generate application key

```bash
make artisan args="key:generate"
```

#### 5. Run migrations

```bash
make migrate
```

#### 6. Generate Passport encryption keys

```bash
make artisan args="passport:keys"
```

#### 7. Create OAuth clients

Create Passport clients with Artisan, for example a public client (PKCE, no secret):

```bash
make artisan args="passport:client --public"
```

When prompted for the redirect URI, use the callback URL of your SPA (example: `http://localhost:3000/api/auth/callback/your-app`).

Alternatively, `make passport-install` runs `passport:install` (non-interactive shortcut: `make artisan args="passport:install -n"`).

#### 8. Seed the database (optional)

```bash
make artisan args="db:seed"
```

The default `DatabaseSeeder` creates a single demo user (`test@example.com`). There is **no** roles package or role seeder in this repo yet.

#### 9. Front-end assets (optional)

```bash
make npm-install
make vite-build
```

---

## Environment for Sail

`compose.yaml` includes **PostgreSQL**, **Redis**, and **Mailpit**. Example `.env` values:

```env
APP_URL=http://localhost

DB_CONNECTION=pgsql
DB_HOST=pgsql
DB_PORT=5432
DB_DATABASE=laravel
DB_USERNAME=sail
DB_PASSWORD=password

REDIS_HOST=redis

CACHE_STORE=redis
SESSION_DRIVER=database
QUEUE_CONNECTION=database

MAIL_MAILER=smtp
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_SCHEME=null
MAIL_USERNAME=null
MAIL_PASSWORD=null
```

- App: [http://localhost](http://localhost) (port **80**, or `APP_PORT`)
- Mailpit UI: [http://localhost:8025](http://localhost:8025)

Mailpit is **already** in `compose.yaml`; you do not need `sail:add mailpit`.

---

## Makefile (development)

| Command | Description |
|---|---|
| `make up` | Start containers (detached) |
| `make down` | Stop containers |
| `make nuke` | Stop containers and remove volumes |
| `make restart` | Restart containers |
| `make shell` | Shell in the app container |
| `make migrate` | Run migrations |
| `make migrate-fresh` | Drop all tables and re-run migrations |
| `make artisan args="..."` | Run Artisan (e.g. `make artisan args="migrate:status"`) |
| `make composer args="..."` | Run Composer in the container |
| `make npm args="..."` | Run npm in the container |
| `make lint` | Check code style (Laravel Pint, read-only) |
| `make lint-fix` | Auto-fix code style (Pint) |
| `make test` | Run tests |
| `make test-coverage` | Run tests with coverage (needs PCOV or Xdebug) |
| `make worker-up` | Start queue worker in the background |
| `make worker-down` | Stop queue worker processes in the container |
| `make worker-restart` | Restart queue worker |

### Linter (Laravel Pint)

[Pint](https://laravel.com/docs/pint) keeps PHP style consistent.

```bash
make lint
make lint-fix
```

### Queue worker

With `QUEUE_CONNECTION=database`, jobs use the `jobs` table; run a worker to process them.

```bash
make worker-up
make worker-down
make worker-restart
```

---

## Mail in development (Mailpit)

Mailpit receives SMTP mail from the app in development.

### Configure `.env`

```env
MAIL_MAILER=smtp
MAIL_HOST=mailpit
MAIL_PORT=1025
```

### Open the Mailpit UI

[http://localhost:8025](http://localhost:8025)

**Examples**

```bash
make up
make migrate
make test
make artisan args="route:list"
make down
```

---

## License

MIT (Laravel framework and default application structure).