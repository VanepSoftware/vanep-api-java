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

## Banco de dados (PostgreSQL + Flyway)

- **Flyway** aplica migrações em `src/main/resources/db/migration` na subida da aplicação (`spring.flyway.*` em `application.properties`). O projeto inclui **`flyway-database-postgresql`** no Gradle: a partir do Flyway 10 o suporte ao PostgreSQL não vem só do `flyway-core` — sem esse módulo aparece *Unsupported Database* em versões novas do servidor (ex.: PostgreSQL 17.x).
- **Credenciais locais (Compose):** banco `vanep`, usuário `postgres`, senha `postgres`, porta host **`5432`** (sobrescreva com `POSTGRES_PORT` se a porta estiver ocupada).
- **Perfis Spring:**
  - **Default** (`application.properties`): JDBC em **`127.0.0.1:5432`** (evita `localhost` → IPv6 `::1` quando só o IPv4 do publish Docker está ouvindo no host). Use com `./gradlew bootRun` ou IDE enquanto o Postgres do Compose estiver expondo a porta.
  - **`docker`** (`application-docker.properties`): JDBC em `postgres:5432` — usado pelo serviço `vanep` no Compose (hostname do serviço na rede interna).

Atalhos **Make** (recomendado): veja a tabela abaixo — `make dev` sobe só o Postgres, espera a porta **TCP** em `127.0.0.1` (mapeamento no host) e roda **`bootRun`** na sua máquina; `make db-up` / `make db-psql` cobrem o dia a dia com o banco.

Equivalente direto com Compose + Gradle:

```bash
docker compose up -d postgres
./gradlew bootRun
```

Stack completa (API + Postgres), com rebuild:

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
| `make dev` | Sobe **só o Postgres** (`db-up`), aguarda ficar pronto e roda **`./gradlew bootRun`** (API em **8080** no host) |
| `make db-up` | `docker compose up -d postgres` — banco em background para IDE ou `make boot-run` |
| `make db-down` | `docker compose stop postgres` |
| `make db-logs` | Acompanha logs do **Postgres** (`docker compose logs -f postgres`) |
| `make db-psql` | Abre **`psql`** no banco `vanep` (usuário `postgres`) |
| `make up` | `docker compose up -d` — sobe **Postgres + API** em segundo plano |
| `make up-build` | `docker compose up -d --build` — igual ao `up`, mas rebuild das imagens |
| `make down` | `docker compose down` |
| `make nuke` | `docker compose down -v` — para e remove volumes (**apaga dados do Postgres local**) |
| `make restart` | `down` + `up` |
| `make logs` | Acompanha logs do serviço `vanep` (`docker compose logs -f`) |
| `make shell` | Shell no container da API (`docker compose exec vanep sh`) |
| `make docker-build` | `docker compose build` |
| `make lint` | Verifica formatação Spotless (`./gradlew spotlessCheck`), igual ideia do Pint `--test` |
| `make lint-fix` | Aplica formatação (`./gradlew spotlessApply`), igual ideia do Pint sem `--test` |
| `make test` | Só testes (`./gradlew test`) |
| `make test-coverage` | Lint Spotless + testes + JaCoCo + cobertura ≥ 75 % (`./gradlew check`) |
| `make check` | Igual a `make test-coverage` |
| `make boot-run` | Sobe a API localmente (`./gradlew bootRun`, porta **8080**) — **exige Postgres acessível** (ex.: `make db-up` antes) |
| `make build` | Gera o JAR (`./gradlew bootJar`) |
| `make clean` | `./gradlew clean` |

---

## Desenvolvimento local com Gradle

Clone o repositório e, na raiz:

```bash
chmod +x gradlew   # apenas se o arquivo não estiver executável
```

### Como rodar os testes

Os testes usam o perfil **`test`** (`src/test/resources/application-test.properties`): **H2 em memória** e **Flyway desligado** — não é necessário ter PostgreSQL rodando para `./gradlew test` ou `./gradlew check`.

| Objetivo | Com Make | Com Gradle direto |
| --- | --- | --- |
| Rodar **só** a suíte de testes | `make test` | `./gradlew test` |
| Rodar **lint**, testes **e** validar cobertura (**≥ 75 %** em linhas) + relatório JaCoCo | `make test-coverage` ou `make check` | `./gradlew check` |

Relatório HTML do JaCoCo (gerado após `check` ou `test`; o HTML completo costuma aparecer após `check` por causa da ordem das tarefas):

- **`build/reports/jacoco/test/html/index.html`** — abra no navegador para ver linhas cobertas por arquivo.

Outros comandos úteis:

| Comando | Descrição |
| --- | --- |
| `./gradlew bootRun` | Sobe a aplicação em modo desenvolvimento (porta padrão **8080**); com Postgres no Compose use `make dev` ou `make db-up` antes |
| `./gradlew bootJar` | Gera o JAR executável em `build/libs/` |
| `./gradlew jacocoTestReport` | Regenera só o relatório JaCoCo (normalmente já roda após `test`) |
| `./gradlew spotlessCheck` | Só verifica formatação (também entra no `check`) |
| `./gradlew spotlessApply` | Aplica a formatação em `src/**/*.java` |

### Cobertura de testes (JaCoCo)

- O gate de CI e o alvo **`make test-coverage`** usam **`./gradlew check`**, que inclui **`spotlessCheck`** (formatação) e **`jacocoTestCoverageVerification`**: cobertura mínima de **75 %** em **linhas** no conjunto de classes analisadas.
- A classe **`VanepApplication`** (ponto de entrada com `main`) está **excluída** do relatório e da verificação JaCoCo, pois o `main` não é executado pela suíte de testes — prática comum em apps Spring Boot.

---

## Docker e Compose (deploy / paridade com CI)

A imagem é construída com **multi-stage Dockerfile** (JDK 25 para build, JRE 25 para execução).

O **`docker-compose.yml`** define:

- **`postgres`** — PostgreSQL 17 (Alpine), saúde verificada com `pg_isready`.
- **`vanep`** — API; por padrão **`SPRING_PROFILES_ACTIVE=docker`** e espera o Postgres ficar saudável antes de iniciar.

```bash
make docker-build && make up
# ou: docker compose build && docker compose up -d
```

A API fica em **`http://localhost:8080`** (ajuste com **`APP_PORT`**). O endpoint raiz **`GET /`** retorna o texto `vanep`.

Somente Postgres (útil para desenvolvimento local com Gradle):

```bash
make db-up
```

Para gerar só a imagem da API:

```bash
docker build -t vanep-api:local .
```

**Importante:** o JAR precisa de um PostgreSQL acessível na URL configurada. Um `docker run` isolado sem banco vai falhar na subida; use **Compose** (app + `postgres`) ou passe **`SPRING_DATASOURCE_URL`** / usuário / senha apontando para uma instância real (como no job de smoke do CI).

**Nota:** alterações no código exigem **rebuild** da imagem (`docker compose build` ou `docker compose up --build`) para entrarem no container; no dia a dia o fluxo típico é desenvolver com **`./gradlew`** e usar Docker quando for validar/deployar.

---

## CI no GitHub Actions

O workflow **`.github/workflows/ci.yml`** roda em `push` e `pull_request` para `main` e `master`:

1. **`./gradlew check`** — **Spotless** (estilo Google Java Format), testes e falha se a cobertura JaCoCo ficar abaixo de **75 %** (linhas).
2. **`docker build`** — garante que a imagem Docker continua buildando.
3. **Smoke test** — cria uma rede Docker, sobe **PostgreSQL**, sobe a **API** com `SPRING_DATASOURCE_*` apontando para esse banco, e valida **`GET http://127.0.0.1:8080/`** (corpo `vanep`).

Artefato opcional: relatório HTML do JaCoCo é publicado como artifact **`jacoco-html`**.

---

## Licença

MIT.
