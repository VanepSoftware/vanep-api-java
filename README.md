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

---

## Makefile (atalhos)

Na raiz do repositório (com `make` instalado):

| Alvo | O que faz |
| --- | --- |
| `make up` | `docker compose up -d` — sobe os containers em segundo plano |
| `make down` | `docker compose down` |
| `make nuke` | `docker compose down -v` — para e remove volumes |
| `make restart` | `down` + `up` |
| `make logs` | Acompanha logs do serviço `vanep` (`docker compose logs -f`) |
| `make shell` | Shell no container (`docker compose exec vanep sh`) |
| `make docker-build` | `docker compose build` |
| `make lint` | Verifica formatação Spotless (`./gradlew spotlessCheck`), igual ideia do Pint `--test` |
| `make lint-fix` | Aplica formatação (`./gradlew spotlessApply`), igual ideia do Pint sem `--test` |
| `make test` | Só testes (`./gradlew test`) |
| `make test-coverage` | Lint Spotless + testes + JaCoCo + cobertura ≥ 75 % (`./gradlew check`) |
| `make check` | Igual a `make test-coverage` |
| `make boot-run` | Sobe a API localmente (`./gradlew bootRun`, porta **8080**) |
| `make build` | Gera o JAR (`./gradlew bootJar`) |
| `make clean` | `./gradlew clean` |

---

## Desenvolvimento local com Gradle

Clone o repositório e, na raiz:

```bash
chmod +x gradlew   # apenas se o arquivo não estiver executável
```

### Como rodar os testes

| Objetivo | Com Make | Com Gradle direto |
| --- | --- | --- |
| Rodar **só** a suíte de testes | `make test` | `./gradlew test` |
| Rodar **lint**, testes **e** validar cobertura (**≥ 75 %** em linhas) + relatório JaCoCo | `make test-coverage` ou `make check` | `./gradlew check` |

Relatório HTML do JaCoCo (gerado após `check` ou `test`; o HTML completo costuma aparecer após `check` por causa da ordem das tarefas):

- **`build/reports/jacoco/test/html/index.html`** — abra no navegador para ver linhas cobertas por arquivo.

Outros comandos úteis:

| Comando | Descrição |
| --- | --- |
| `./gradlew bootRun` | Sobe a aplicação em modo desenvolvimento (porta padrão **8080**) |
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

```bash
docker compose build
docker compose up
```

A API fica em **`http://localhost:8080`** (ajuste com a variável **`APP_PORT`** se precisar). O endpoint raiz **`GET /`** retorna o texto `vanep`.

Para gerar só a imagem:

```bash
docker build -t vanep-api:local .
docker run --rm -p 8080:8080 vanep-api:local
```

**Nota:** alterações no código exigem **rebuild** da imagem (`docker compose build` ou `docker compose up --build`) para entrarem no container; no dia a dia o fluxo típico é desenvolver com **`./gradlew`** e usar Docker quando for validar/deployar.

---

## CI no GitHub Actions

O workflow **`.github/workflows/ci.yml`** roda em `push` e `pull_request` para `main` e `master`:

1. **`./gradlew check`** — **Spotless** (estilo Google Java Format), testes e falha se a cobertura JaCoCo ficar abaixo de **75 %** (linhas).
2. **`docker build`** — garante que a imagem Docker continua buildando.
3. **Smoke test** — sobe um container e valida **`GET http://127.0.0.1:8080/`** (corpo `vanep`).

Artefato opcional: relatório HTML do JaCoCo é publicado como artifact **`jacoco-html`**.

---

## Licença

MIT.
