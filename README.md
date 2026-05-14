# Vanep API

API Spring Boot do projeto Vanep. Este repositório usa **Gradle (DSL Groovy)** para desenvolvimento local e **Docker** para imagem de execução em CI/CD e deploy.

---

## Versões principais

| Componente | Versão |
| --- | --- |
| Java | **25** |
| Spring Boot | **4.0.6** |
| Gradle (wrapper) | **9.4.1** (`gradle/wrapper/gradle-wrapper.properties`) |
| Build script | **Groovy** (`build.gradle`, `settings.gradle`) |

Outras dependências transitivas seguem o BOM do Spring Boot via `io.spring.dependency-management`.

**Estilo de código:** [Spotless](https://github.com/diffplug/spotless) com **Google Java Format** (via Gradle). O `./gradlew check` inclui **`spotlessCheck`** — o CI falha se o código não estiver formatado.

---

## Requisitos

### Desenvolvimento (Gradle na máquina)

- **JDK 25** (alinhado ao `java.toolchain` no `build.gradle`)
- Nada mais obrigatório: o **Gradle Wrapper** (`./gradlew`) baixa o Gradle correto

### Docker (CI, deploy, ambiente containerizado)

- **Docker** e **Docker Compose** v2 (`docker compose`)

### Opcional

- **GNU Make** — para usar os atalhos do `Makefile` na raiz do projeto
- **PostgreSQL** — em desenvolvimento costuma rodar via **Docker Compose** (serviço `postgres`); os testes Gradle usam **H2 em memória** e não precisam do banco

---

## Configuração: `.env`, perfil `local` e arquivos Spring

### `.env` na raiz (Docker Compose)

O **`docker-compose.yml`** não incorpora credenciais no repositório. Os serviços **postgres** e **vanep** usam **`env_file: .env`**: você precisa ter um arquivo **`.env`** na raiz (copiado de `.env.example` e preenchido).

| Variável | Uso |
| --- | --- |
| `POSTGRES_DB` | Nome do banco no Postgres |
| `POSTGRES_USER` | Usuário do Postgres |
| `POSTGRES_PASSWORD` | Senha do Postgres |
| `POSTGRES_PORT` | Porta no **host** mapeada para o Postgres no container (ex.: `5432`) |
| `APP_PORT` | Porta no **host** mapeada para a API no container (ex.: `8080`) |

O `.env` está listado no **`.gitignore`** — não versione segredos. O **`.env.example`** versionado contém só os nomes dos campos (vazios); copie e preencha antes do primeiro `docker compose up` ou `make db-up`.

No serviço **`vanep`**, o Compose ainda define **`POSTGRES_HOST=postgres`** (nome do serviço na rede Docker) e **`SPRING_PROFILES_ACTIVE=docker`** — não precisa repetir isso no `.env`.

### API na máquina com Gradle (`bootRun`) — perfil `local`

Para rodar a app **fora** do container com `./gradlew bootRun` ou `make dev` / `make boot-run`, o Gradle ativa o perfil **`local`** (`spring.profiles.active=local` no `bootRun`).

- **`application.properties`** — configuração comum (nome da app, JPA, Flyway), **sem** datasource.
- **`application-local.properties`** — JDBC no host (`127.0.0.1`) com placeholders **`${POSTGRES_*}`** (sem senhas no Git). O **`./gradlew bootRun`** lê **`.env`** na raiz (via `build.gradle`) e injeta essas variáveis no processo; sem `.env`, exporte **`POSTGRES_*`** manualmente ou defina-as na IDE.

Alinhe **`POSTGRES_PORT`** no `.env` com o mapeamento do Compose (por padrão `jdbc:postgresql://127.0.0.1:<POSTGRES_PORT>/<POSTGRES_DB>`).

### Cursor / VS Code

O **`.vscode/launch.json`** passa **`-Dspring.profiles.active=local`** para alinhar com o Gradle.

---

## Banco de dados (PostgreSQL + Flyway)

- **Flyway** aplica migrações em `src/main/resources/db/migration` na subida da aplicação. O projeto inclui **`flyway-database-postgresql`** no Gradle (PostgreSQL 17.x com Flyway 10+).
- **Perfis Spring:**
  - **`local`** — datasource em **`application-local.properties`** (Gradle na máquina, Postgres normalmente em `127.0.0.1`).
  - **`docker`** — datasource em **`application-docker.properties`** (porta **5432** na rede entre containers; não use `POSTGRES_PORT` do host no JDBC). Variáveis `POSTGRES_*` vêm do ambiente (`.env` no Compose / CI).
  - **`test`** — H2 em memória; Flyway desligado nos testes.

Evite usar **`localhost`** no JDBC no host se o Postgres do Docker só estiver escutando em **IPv4** no mapeamento da porta — **`127.0.0.1`** costuma ser mais previsível que **`localhost`** (IPv6).

Atalhos **Make** (recomendado): `make dev` sobe só o Postgres (via Compose), espera a porta TCP em **`127.0.0.1`** conforme o **`POSTGRES_PORT`** no seu `.env`, e roda **`bootRun`** com perfil **`local`**.

Equivalente manual:

```bash
cp .env.example .env   # preencher POSTGRES_* , POSTGRES_PORT e APP_PORT
docker compose up -d postgres
./gradlew bootRun
```

Stack completa (API + Postgres em Docker), com rebuild:

```bash
make up-build
# ou: docker compose up -d --build
```

Dados do Postgres persistem no volume Docker **`vanep-postgres-data`**.

---

## Makefile (atalhos)

Na raiz do repositório (com `make` instalado):

| Alvo | O que faz |
| --- | --- |
| `make setup-env` | Falha com mensagem útil se **`.env`** não existir (obrigatório para Compose e recomendado para `bootRun`) |
| `make dev` | `setup-env` → sobe Postgres (`db-up`) → espera porta → **`./gradlew bootRun`** (perfil **local**, `.env` carregado pelo Gradle) |
| `make db-up` | Exige **`.env`**; `docker compose up -d postgres` |
| `make db-down` | `docker compose stop postgres` |
| `make db-logs` | Logs do Postgres (`docker compose logs -f postgres`) |
| `make db-psql` | **`psql`** no container com o usuário e o banco definidos no `.env` |
| `make up` | Exige **`.env`**; `docker compose up -d` — Postgres + API |
| `make up-build` | Igual ao `up`, com `--build` |
| `make down` | `docker compose down` |
| `make nuke` | `docker compose down -v` — remove volumes (**apaga dados locais do Postgres**) |
| `make restart` | `down` + `up` |
| `make logs` | Logs do serviço `vanep` |
| `make shell` | Shell no container da API (`docker compose exec vanep sh`) |
| `make docker-build` | `docker compose build` |
| `make lint` | `./gradlew spotlessCheck` |
| `make lint-fix` | `./gradlew spotlessApply` |
| `make test` | `./gradlew test` |
| `make test-coverage` / `make check` | `./gradlew check` (Spotless + testes + JaCoCo ≥ 75 % linhas) |
| `make boot-run` | `setup-env` → `./gradlew bootRun` (perfil **local**) — Postgres precisa estar acessível (ex.: `make db-up`) |
| `make build` | `./gradlew bootJar` |
| `make clean` | `./gradlew clean` |

---

## Desenvolvimento local com Gradle

Clone o repositório e, na raiz:

```bash
chmod +x gradlew   # apenas se o arquivo não estiver executável
cp .env.example .env
# Edite .env (POSTGRES_* , POSTGRES_PORT e APP_PORT).
```

### Como rodar os testes

Os testes usam o perfil **`test`** (`src/test/resources/application-test.properties`): **H2 em memória** e **Flyway desligado** — não é necessário PostgreSQL para `./gradlew test` ou `./gradlew check`.

| Objetivo | Com Make | Com Gradle direto |
| --- | --- | --- |
| Rodar só os testes | `make test` | `./gradlew test` |
| Lint + testes + cobertura JaCoCo (≥ 75 % linhas) | `make test-coverage` ou `make check` | `./gradlew check` |

Relatório HTML do JaCoCo:

- **`build/reports/jacoco/test/html/index.html`**

Outros comandos úteis:

| Comando | Descrição |
| --- | --- |
| `./gradlew bootRun` | Perfil **local** por padrão (ver `build.gradle`); precisa de **`.env`** na raiz (ou variáveis **`POSTGRES_*`** no ambiente) e Postgres alinhado |
| `./gradlew bootJar` | Gera o JAR em `build/libs/` |
| `./gradlew spotlessCheck` / `spotlessApply` | Formatação |

### Cobertura de testes (JaCoCo)

- **`./gradlew check`** inclui **`jacocoTestCoverageVerification`**: mínimo **75 %** em **linhas**.
- **`VanepApplication`** está excluída do JaCoCo (só `main`).

---

## Docker e Compose

A imagem usa **multi-stage Dockerfile** (JDK 25 build, JRE 25 runtime). O **`Dockerfile`** define **`SPRING_PROFILES_ACTIVE=docker`** por padrão; o Compose pode reforçar o mesmo valor.

O **`docker-compose.yml`** define **postgres** e **vanep**, com **`env_file: .env`** para credenciais e portas no host. O serviço **vanep** usa **`POSTGRES_HOST=postgres`** na rede interna.

```bash
cp .env.example .env   # preencher
make docker-build && make up
```

**GET /** devolve o texto **`vanep`** (nome da app em `application.properties`).

Só Postgres para desenvolvimento com Gradle na máquina:

```bash
make db-up
```

---

## CI no GitHub Actions

O workflow **`.github/workflows/ci.yml`** roda em `push` e `pull_request` para `main` e `master`:

1. **`./gradlew check`** — Spotless, testes e cobertura JaCoCo ≥ **75 %** (linhas).
2. **`docker build`** — a imagem continua sendo construída.
3. **Smoke test** — rede Docker, Postgres com **`POSTGRES_*`**, API com perfil **`docker`** e variáveis **`POSTGRES_*`** / **`POSTGRES_HOST`**, validação de **`GET http://127.0.0.1:8080/`** (corpo `vanep`).

Valores do Postgres de CI **não estão fixos no YAML**: o workflow lê **variáveis** e **secret** do repositório (Settings → Secrets and variables → Actions), com **fallback** (`vanep` / `postgres` / `5432`) quando não estão definidos — necessário para **PRs a partir de forks**, onde secrets customizados não são expostos ao runner.

| Onde definir | Nome | Uso |
| --- | --- | --- |
| Variables | `CI_POSTGRES_DB` | Nome do banco (fallback: `vanep`) |
| Variables | `CI_POSTGRES_USER` | Usuário (fallback: `postgres`) |
| Variables | `CI_POSTGRES_PORT` | Porta no smoke (fallback: `5432`) |
| Secrets | `CI_POSTGRES_PASSWORD` | Senha (fallback: `postgres`) |

Artefato opcional: **`jacoco-html`**.

---

## Licença

MIT.
