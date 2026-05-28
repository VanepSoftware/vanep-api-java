# Vanep API — visão geral do projeto

Este documento descreve **como pensamos o repositório**, convenções de organização e o que esperamos de contribuições. Detalhes de ambiente, portas e Docker estão no [README](../README.md) na raiz.

---

## O que é

**Vanep API** é uma API HTTP (REST) em **Java** com **Spring Boot**, persistência **JPA/Flyway** sobre **PostgreSQL** em produção/desenvolvimento típico, e testes com **H2** em memória. O objetivo é servir o produto Vanep com endpoints versionados e documentados.

---

## Organização por feature (feature-based)

O código de negócio é organizado **por funcionalidade (feature)**, não só por tipo técnico (`controller`, `service`, …) na raiz do pacote.

- Cada feature vive sob um pacote coeso, por exemplo: `br.com.vanep.users` com subpacotes `controller`, `dto`, `entity`, `enums`, `mapper`, `repository`, `service` (e outros que a feature precisar, como `seed`).
- Isto melhora navegação, ownership e evolução independente de cada domínio.

> **Nota de nomenclatura:** aqui “feature-based” refere-se à **estrutura de pastas/pacotes por funcionalidade**. Não confundir com *feature flags* (liga/desliga comportamento em runtime). Se no futuro existirem toggles de produto, documente-os à parte (config, ADR ou doc específico).

---

## O que é “shared” (fora da feature)

Tudo que **não pertence a um único domínio de negócio** fica fora da pasta da feature, em áreas **partilhadas** (nomes ilustrativos; o projeto pode usar `config`, `entity` para bases JPA comuns, etc.):

- Configuração Spring (`config/`), segurança transversal, WebMvc, OpenAPI.
- Infra partilhada (por exemplo `GenericTimeStampsEntity` enquanto for usada por várias entidades).
- Utilitários realmente genéricos (hash de senha, geração de token, etc.).

**Regra prática:** se só uma feature usa e é conceito dela, fica **dentro** da feature. Se duas ou mais features precisam do mesmo código, **promove** para shared (ou extrai uma dependência mínima comum) em vez de duplicar.

---

## Clean Architecture (quando fizer sentido)

Sempre que possível, respeitar dependências **de fora para dentro**: adaptadores (HTTP, JPA) dependem de regras de aplicação/domínio, e não o contrário.

- Hoje parte da API segue camadas clássicas Spring (controller → service → repository); ao crescer, preferir **extrair regras puras** (validações, políticas) para métodos/classes testáveis sem servlet nem entidade JPA, quando isso reduzir acoplamento sem over-engineering.
- Evitar que “detalhe de framework” (anotações Web, `HttpServletRequest`, etc.) espalhe pela regra de negócio central.

---

## Antes de criar algo novo: procurar e reutilizar

1. **Procurar** no repositório (nome de classe, pacote, migration, propriedade) se já existe solução semelhante.
2. Se existir, **reutilizar ou estender** em vez de duplicar (DRY).
3. Se for quase igual mas não genérico o suficiente, alinhar com o time antes de forkar lógica.

Isto vale para código, testes, padrões de configuração e migrações Flyway.

---

## Testes por feature

- **Cada feature nova** (ou alteração relevante) deve vir acompanhada de **testes** que cubram o comportamento principal (unitários com Mockito, testes de slice com `MockMvc` + segurança quando aplicável, etc.).
- O build exige **cobertura mínima de linhas (JaCoCo)** no `verify`; ver `pom.xml`. Falhar o `verify` localmente antes de abrir PR evita retrabalho no CI.
- Perfis e propriedades de teste estão em `src/test/resources`; reutilizar padrões existentes (por exemplo segurança em testes) em vez de inventar ficheiros paralelos sem necessidade.

Comandos úteis:

```bash
make test           # testes
make test-coverage  # testes + JaCoCo + spotless (equivalente a verify)
```

---

## Lint / formatação (obrigatório no fluxo de validação)

O projeto usa **Spotless** com **Google Java Format**. O `verify` do Maven inclui **`spotless:check`**.

- **Sempre** correr lint/format como parte de “testar” antes de considerar o trabalho pronto (local ou CI).
- Corrigir automaticamente: `make lint-fix` ou `./mvnw spotless:apply`.
- Validar sem alterar ficheiros: `make lint` ou `./mvnw spotless:check`.

Sem código formatado segundo o Spotless, o **CI falha**.

---

## API, segurança e documentação

- Prefixo global **`/api`** para controllers REST em pacotes `*.controller` (ver `ApiWebConfig`).
- **Spring Security** (HTTP Basic provisório, regras por rota) e **OpenAPI/SpringDoc** (Swagger) estão descritos no README; ajustes finos em `SecurityConfig` e propriedades `vanep.security.*`.

---

## Banco de dados e migrações

- **Flyway**: scripts em `src/main/resources/db/migration`; ordem e idempotência importam.
- Não recriar tabelas que já existem noutra migration; estender com novas revisões.

---

## Documentação

| Documento | Conteúdo |
|-----------|-----------|
| [README](../README.md) | Como correr, Docker, perfis Spring, troubleshooting |
| `docs/project-overview.md` (este ficheiro) | Filosofia do repo, convenções, expectativas de contribuição |

Sugestão: para decisões maiores (stack, segurança, modelo de dados), considerar **ADRs** em `docs/adr/` no futuro, se o time quiser histórico explícito.

---

## Resumo rápido (checklist mental)

- [ ] Nova funcionalidade → pacote **feature** com subpastas claras; resto → **shared**.
- [ ] Já existe algo parecido? → **Reutilizar**.
- [ ] Testes cobrindo a feature / fluxo crítico.
- [ ] `make lint` ou `./mvnw spotless:check` (e idealmente `make test-coverage` / `verify`).
- [ ] Clean architecture onde ajudar, sem complicar o desnecessário.

---

*Última atualização alinhada ao estado do repositório (Spring Boot 4, Java 25, Maven). Ajuste este ficheiro quando as convenções do time evoluírem.*
