# Vanep API

API Spring Boot do projeto Vanep. Este repositório usa **Maven** para desenvolvimento local e **Docker** para imagem de execução em CI/CD e deploy.

**Visão geral e convenções do código:** [docs/project-overview.md](docs/project-overview.md).

---

## Versões principais

| Componente | Versão |
| --- | --- |
| Java | **25** |
| Spring Boot | **4.0.6** |
| Maven (wrapper) | **3.9.12** (`.mvn/wrapper/maven-wrapper.properties`) |
| Build | **`pom.xml`** |

Outras dependências transitivas seguem o BOM do Spring Boot (`spring-boot-starter-parent`).

**Estilo de código:** [Spotless](https://github.com/diffplug/spotless) com **Google Java Format** (via Maven). O `./mvnw verify` inclui **`spotless:check`** — o CI falha se o código não estiver formatado.

---

## Requisitos

### Desenvolvimento (Maven na máquina)

- **JDK 25** (alinhado a `java.version` no `pom.xml`)
- Nada mais obrigatório: o **Maven Wrapper** (`./mvnw`) baixa o Maven correto

### Docker (CI, deploy, ambiente containerizado)

- **Docker** e **Docker Compose** v2 (`docker compose`)

### Opcional

- **GNU Make** — para usar os atalhos do `Makefile` na raiz do projeto
- **PostgreSQL** — em desenvolvimento costuma rodar via **Docker Compose** (serviço `postgres`); os testes Maven usam **H2 em memória** e não precisam do banco

---

## Passo a passo: rodar o projeto

Objetivo: ter **PostgreSQL acessível em `127.0.0.1`** na porta definida em **`POSTGRES_PORT`** no `.env`, com as mesmas credenciais que o Spring usa no perfil **`local`**.

### 1. Clonar e ir à raiz do repositório

```bash
cd vanep-api-java
```

### 2. Criar o `.env` na raiz

```bash
cp .env.example .env
```

Edite **`.env`** e preencha **`POSTGRES_DB`**, **`POSTGRES_USER`**, **`POSTGRES_PASSWORD`**, **`POSTGRES_PORT`** e **`APP_PORT`**.

Use o formato **`CHAVE=valor`** sem espaços à volta do **`=`** (evita surpresas no Docker Compose e no carregamento do `.env` pelo Make).

### 3. Porta **5432** no host (conflito com PostgreSQL do sistema)

O Compose mapeia **`${POSTGRES_PORT}:5432`** (porta do **host** → porta **interna** do container). Se algo já estiver a ouvir em **`0.0.0.0:5432`** ou **`127.0.0.1:5432`**, o `docker compose up` falha com **address already in use**.

Em Debian/Ubuntu costuma existir um serviço **`postgresql@…-main`** (ex.: `postgresql@18-main`). Para listar e parar **só esse cluster** (liberta a 5432 no host):

```bash
sudo systemctl list-units 'postgresql@*'
sudo systemctl stop postgresql@18-main
```

Substitua **`18-main`** pelo nome que aparecer na lista (pode ser `17-main`, etc.). O unit genérico **`postgresql.service`** muitas vezes só dispara o arranque e **não** mostra o processo que segura a porta.

**Alternativa sem parar o Postgres do sistema:** no `.env`, use outra porta livre no host, por exemplo **`POSTGRES_PORT=5433`**. O perfil **`local`** já usa **`127.0.0.1:${POSTGRES_PORT}`** em `application-local.properties`.

### 4. Caminho recomendado: Postgres no Docker + API com Maven

Sobe o Postgres, espera a porta TCP em **`127.0.0.1`** e inicia a API com perfil **`local`** (o Make carrega o `.env` na raiz):

```bash
make dev
```

Equivalente em dois passos:

```bash
make db-up
make boot-run
```

Confirme que o mapeamento existe no host (troque o nome do container se o Compose usar outro sufixo):

```bash
docker port vanep-api-java-postgres-1 5432
```

Deve aparecer algo como **`0.0.0.0:5432->5432/tcp`** (ou a porta que escolheu em **`POSTGRES_PORT`**). Se aparecer **“no public port … published”**, o Compose não publicou a porta — reveja o `.env` e volte a criar o serviço (`make down` e `make db-up`, ou `make nuke` se aceitar apagar o volume local).

### 5. Tudo no Docker (API + Postgres)

```bash
make up-build
```

A API fica exposta em **`http://127.0.0.1:${APP_PORT}/`** (por defeito **8080**). Não rode **`make boot-run`** em simultâneo na mesma porta HTTP sem mudar **`APP_PORT`** ou a porta do servidor Spring — os dois serviços disputariam a **8080**.

### 6. Testar sem subir o Postgres

Os testes Maven usam **H2**; não precisam de Docker:

```bash
make test
```

### Resolução rápida de erros

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| **address already in use** na **5432** | Postgres do sistema ou outro processo na porta | Parar **`postgresql@…-main`** ou mudar **`POSTGRES_PORT`** no `.env` |
| **password authentication failed** para **`postgres`** | A app está a ligar ao **Postgres errado** (ex.: sistema em vez do container) ou credenciais diferentes | Garantir **`docker port … 5432`** com mapeamento; alinhar **`.env`** com o container; testar com **`make db-psql`** |
| **Connection refused** a **`127.0.0.1:5432`** | Nada a ouvir nessa porta no host | Subir **`make db-up`** e confirmar **`docker port`**; se parou o Postgres do sistema, o Docker **tem** de publicar a porta |
| **`killall 5432`** não faz nada | `killall` usa **nome do executável**, não o número da porta | Usar **`systemctl`** / **`ss`** como acima |

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

### API na máquina com Maven (`spring-boot:run`) — perfil `local`

Para rodar a app **fora** do container com `./mvnw spring-boot:run` ou `make dev` / `make boot-run`, o plugin Spring Boot ativa o perfil **`local`** (configurado no `pom.xml`).

- **`application.properties`** — configuração comum (nome da app, JPA, Flyway), **sem** datasource. Propriedades opcionais **`vanep.security.*`** são lidas por **`SecurityConfig`** (Basic, CORS, Swagger público ou não).
- **`application-local.properties`** — JDBC no host (`127.0.0.1`) com placeholders **`${POSTGRES_*}`** (sem senhas no Git). O **`make boot-run`** / **`make dev`** fazem `source` do **`.env`** na raiz antes de iniciar a JVM; sem `.env`, exporte **`POSTGRES_*`** manualmente ou defina-as na IDE.

Alinhe **`POSTGRES_PORT`** no `.env` com o mapeamento do Compose (por padrão `jdbc:postgresql://127.0.0.1:<POSTGRES_PORT>/<POSTGRES_DB>`).

### Cursor / VS Code

O **`.vscode/launch.json`** passa **`-Dspring.profiles.active=local`** para alinhar com o Maven.

---

## OpenAPI (Swagger)

- **Swagger UI:** `http://127.0.0.1:<porta>/swagger-ui.html`
- **Especificação OpenAPI (JSON):** `http://127.0.0.1:<porta>/v3/api-docs`
- No perfil **`test`** a documentação SpringDoc fica desligada (`springdoc.*.enabled=false`) para reduzir ruído nos logs.

---

## Autenticação (OAuth2 + Spring Authorization Server)

A Vanep replica o esquema de OAuth2 dos checklists (lá feito com Laravel Passport). Aqui o
equivalente é o **Spring Authorization Server**: a própria API é o **Authorization Server** e
também o **Resource Server**.

| Papel | Como |
|---|---|
| **Authorization Server** | Endpoints OAuth2 padrão: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`. Fluxo **authorization code + PKCE** (cliente público, sem secret — igual ao `token_endpoint_auth_method: none` do checklists-frontend). |
| **Tela de login** | Servida pela própria API (Thymeleaf) em **`/login`** — fundo preto, marca Vanep, e-mail + senha. É a tela mostrada durante o fluxo de autorização. |
| **Login social (Google)** | Botão **"Entrar com Google"** (OAuth2 Client / OIDC). Aparece só quando há `GOOGLE_CLIENT_ID` configurado. |
| **Resource Server** | Rotas **`/api/**`** são protegidas por **JWT** (Bearer). Sem token → **401**. Ex.: `GET /api/user/profile` devolve o perfil da conta autenticada (consumido pelo front como "userinfo"). |
| **Senhas** | **Argon2id + pepper** (HMAC-SHA256 com `VANEP_PASSWORD_PEPPER` antes do hash). |

### Login com Google (cadastro em 2 passos)

Como o Google não fornece CPF (e `user.document` é obrigatório), o login social usa **cadastro em
2 passos**:

1. Usuário entra com o Google. Se já existe conta (vínculo em `oauth_account`, ou conta local com o
   mesmo e-mail verificado → vincula automaticamente), entra direto.
2. Se é a primeira vez, vai para **`/signup/complete`** — escolhe **cliente/motorista** e preenche
   CPF, telefone, etc. Só então a conta (`users` + `oauth_account`) é criada e o fluxo OAuth segue.

> Config no Google Cloud Console: OAuth Client tipo *Web application*, redirect URI
> **`http://localhost:8080/login/oauth2/code/google`**. Defina `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` no `.env`.

### Cadastro por e-mail/senha (cliente e motorista)

Telas públicas servidas pela API (Thymeleaf):

- **`/signup`** — escolha entre **cliente** ou **motorista**.
- **`/signup/client`** — cria `users` (tipo `CLIENT`) + perfil `client`.
- **`/signup/driver`** — cria `users` (tipo `DRIVER`) + perfil `driver` (entra como `PENDING`, só recebe propostas após aprovação).

Ao concluir, o usuário é levado a `/login?registered`. E-mail e documento (CPF) são únicos.

### Variáveis de ambiente (ver `.env.example`)

- **`VANEP_PASSWORD_PEPPER`** — segredo do servidor para o hash de senhas. **Obrigatório em runtime.**
- **`VANEP_OAUTH_CLIENT_ID`** — id do cliente público dos frontends (default `vanep-frontend`).
- **`VANEP_OAUTH_REDIRECT_URIS`** — callbacks permitidas (separadas por vírgula).
- **`VANEP_OAUTH_POST_LOGOUT_REDIRECT_URIS`**, **`VANEP_OAUTH_ACCESS_TOKEN_TTL_MINUTES`** (default 15), **`VANEP_OAUTH_REFRESH_TOKEN_TTL_DAYS`** (default 90).
- **`VANEP_REMEMBER_ME_KEY`** — chave do cookie "lembrar-me".
- **`VANEP_SEED_ENABLED`** — `true` semeia um admin de teste no boot.

> **Chave JWT:** nesta fase a chave RSA de assinatura é gerada em memória no boot (tokens deixam de
> ser válidos ao reiniciar). Persistir a chave por env/keystore é um próximo passo para produção.

### Usuário de teste (dev)

No perfil **`local`** o seed vem habilitado. Também dá para semear sob demanda:

```bash
make db-seed   # cria admin@vanep.com.br / password
```

---

## Banco de dados (PostgreSQL + Flyway)

- **Flyway** aplica migrações em `src/main/resources/db/migration` na subida da aplicação. O projeto inclui **`flyway-database-postgresql`** no Maven (PostgreSQL 17.x com Flyway 10+).
- **Perfis Spring:**
  - **`local`** — datasource em **`application-local.properties`** (Maven na máquina, Postgres normalmente em `127.0.0.1`).
  - **`docker`** — datasource em **`application-docker.properties`** (porta **5432** na rede entre containers; não use `POSTGRES_PORT` do host no JDBC). Variáveis `POSTGRES_*` vêm do ambiente (`.env` no Compose / CI).
  - **`test`** — H2 em memória; Flyway desligado e schema gerado pelas entidades JPA (`ddl-auto=create-drop`), já que o SQL das migrações é específico de PostgreSQL.

Evite usar **`localhost`** no JDBC no host se o Postgres do Docker só estiver escutando em **IPv4** no mapeamento da porta — **`127.0.0.1`** costuma ser mais previsível que **`localhost`** (IPv6).

Atalhos **Make** (recomendado): `make dev` sobe só o Postgres (via Compose), espera a porta TCP em **`127.0.0.1`** conforme o **`POSTGRES_PORT`** no seu `.env`, e roda **`spring-boot:run`** com perfil **`local`**.

Equivalente manual:

```bash
cp .env.example .env   # preencher POSTGRES_* , POSTGRES_PORT e APP_PORT
docker compose up -d postgres
set -a && . ./.env && set +a && ./mvnw spring-boot:run
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
| `make setup-env` | Falha com mensagem útil se **`.env`** não existir (obrigatório para Compose e recomendado para `boot-run`) |
| `make dev` | `setup-env` → sobe Postgres (`db-up`) → espera porta → **`./mvnw spring-boot:run`** (perfil **local**, `.env` carregado pelo Make) |
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
| `make lint` | `./mvnw spotless:check` |
| `make lint-fix` | `./mvnw spotless:apply` |
| `make test` | `./mvnw test` |
| `make test-coverage` / `make check` | `./mvnw verify` (Spotless + testes + JaCoCo ≥ 75 % linhas) |
| `make boot-run` | `setup-env` → `./mvnw spring-boot:run` (perfil **local**, `.env` via `source`) — Postgres precisa estar acessível (ex.: `make db-up`) |
| `make build` | `./mvnw package -DskipTests` |
| `make clean` | `./mvnw clean` |

---

## Desenvolvimento local com Maven

Fluxo completo (`.env`, porta **5432**, Docker vs Postgres do sistema, erros comuns): ver a secção **Passo a passo: rodar o projeto** acima.

Clone o repositório e, na raiz:

```bash
chmod +x mvnw   # apenas se o arquivo não estiver executável
cp .env.example .env
# Edite .env (POSTGRES_* , POSTGRES_PORT e APP_PORT).
```

### Como rodar os testes

Os testes usam o perfil **`test`** (`src/test/resources/application-test.properties`): **H2 em memória** e **Flyway desligado** — não é necessário PostgreSQL para `./mvnw test` ou `./mvnw verify`.

| Objetivo | Com Make | Com Maven direto |
| --- | --- | --- |
| Rodar só os testes | `make test` | `./mvnw test` |
| Lint + testes + cobertura JaCoCo (≥ 75 % linhas) | `make test-coverage` ou `make check` | `./mvnw verify` |

Relatório HTML do JaCoCo:

- **`target/site/jacoco/index.html`**

Outros comandos úteis:

| Comando | Descrição |
| --- | --- |
| `./mvnw spring-boot:run` | Perfil **local** por padrão (ver `pom.xml`); precisa de variáveis **`POSTGRES_*`** no ambiente (ex.: `source .env`) e Postgres alinhado |
| `./mvnw package` | Gera o JAR em `target/` |
| `./mvnw spotless:check` / `spotless:apply` | Formatação |

### Cobertura de testes (JaCoCo)

- **`./mvnw verify`** inclui **`jacoco:check`**: mínimo **75 %** em **linhas**.
- **`VanepApplication`** está excluída do JaCoCo (só `main`).

---

## Docker e Compose

A imagem usa **multi-stage Dockerfile** (JDK 25 build, JRE 25 runtime). O **`Dockerfile`** define **`SPRING_PROFILES_ACTIVE=docker`** por padrão; o Compose pode reforçar o mesmo valor.

O **`docker-compose.yml`** define **postgres** e **vanep**, com **`env_file: .env`** para credenciais e portas no host. O serviço **vanep** usa **`POSTGRES_HOST=postgres`** na rede interna.

```bash
cp .env.example .env   # preencher
make docker-build && make up
```

Só Postgres para desenvolvimento com Maven na máquina:

```bash
make db-up
```

---

## CI no GitHub Actions

O workflow **`.github/workflows/ci.yml`** roda em `push` e `pull_request` para `main` e `master`:

1. **`./mvnw verify`** — Spotless, testes e cobertura JaCoCo ≥ **75 %** (linhas).

Artefato opcional: **`jacoco-html`**.

---

## Licença

MIT.
