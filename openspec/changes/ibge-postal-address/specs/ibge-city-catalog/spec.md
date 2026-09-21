## Purpose

Oferece um catálogo curado de municípios brasileiros (IBGE) para o app escolher cidade por UF e nome sem texto livre, e para ViaCEP e resolução Google compartilharem as mesmas linhas de `city`.

## ADDED Requirements

### Requirement: Todo município brasileiro é uma linha de city

O sistema SHALL persistir cada município IBGE como uma linha de `city` sob o `state` brasileiro curado da mesma UF, com `ibge_code` único (identificador IBGE de sete dígitos) e `normalized_name` no escopo daquele estado. O sistema MUST semear este catálogo a partir de um dump commitado no repositório. O sistema MUST NOT buscar a API HTTP do IBGE em runtime nem na suíte de testes. Uma linha de `city` MUST NOT carregar `place_id` do Google; a identidade do município é `ibge_code`.

O seed MUST ser idempotente por `ibge_code`: rodar duas vezes MUST NOT duplicar linhas. O sistema MUST NOT importar distritos, subdistritos, microrregião, mesorregião nem região imediata. De cada objeto do dump o seeder MUST ler só `id` (sete dígitos → `ibge_code`), `nome` (`name`) e a UF. A UF MUST vir de `microrregiao.mesorregiao.UF.sigla`; se essa árvore for nula, MUST usar `regiao-imediata.regiao-intermediaria.UF.sigla`. Se `id`, `nome` ou UF ainda faltarem, o seeder MUST pular o município e registrar um log, sem abortar o seed. `regiao-imediata` MUST NOT virar um segundo município. `normalized_name` MUST ser derivado de `nome` no model. `requires_district` MUST ficar nulo (herda do estado).

Exemplo de um item do dump (`localidades/municipios`) e o que vira `city`:

```json
{
  "id": 5206206,
  "nome": "Cristalina",
  "microrregiao": {
    "id": 52012,
    "nome": "Entorno de Brasília",
    "mesorregiao": {
      "id": 5204,
      "nome": "Leste Goiano",
      "UF": {
        "id": 52,
        "sigla": "GO",
        "nome": "Goiás",
        "regiao": {
          "id": 5,
          "sigla": "CO",
          "nome": "Centro-Oeste"
        }
      }
    }
  },
  "regiao-imediata": {
    "id": 520019,
    "nome": "Luziânia",
    "regiao-intermediaria": {
      "id": 5206,
      "nome": "Luziânia - Águas Lindas de Goiás",
      "UF": {
        "id": 52,
        "sigla": "GO",
        "nome": "Goiás",
        "regiao": {
          "id": 5,
          "sigla": "CO",
          "nome": "Centro-Oeste"
        }
      }
    }
  }
}
```

| JSON IBGE | Nosso lado |
|-----------|------------|
| `id` `5206206` | `city.ibge_code` = `5206206` (mesmo código do campo `ibge` do ViaCEP) |
| `nome` `"Cristalina"` | `city.name` = `Cristalina`; `normalized_name` = `cristalina` |
| `microrregiao.mesorregiao.UF.sigla` `"GO"` | `city.state` = linha do `StateSeeder` com UF `GO` |
| `regiao-imediata.regiao-intermediaria.UF.sigla` | UF fallback se `microrregiao` for nula |
| `microrregiao` id/nome, `mesorregiao`, `UF.id`/`nome`, `regiao`, nomes de `regiao-imediata` | ignorados; **não** criam `city` nem `district` |

Isso MUST persistir **uma** linha de `city` (Cristalina/GO), não duas. `token` é gerado pelo model. `google_place_id` não existe nessa tabela após a migration desta change.

#### Scenario: Seed cria Brasília sob DF

- **WHEN** o seeder do catálogo de cidades roda contra uma tabela `city` vazia
- **THEN** existe uma linha com `ibge_code` `5300108`, nome Brasília e estado UF `DF`

#### Scenario: Seed mapeia um município IBGE para uma city

- **WHEN** o dump contém o município `id` `5206206`, `nome` Cristalina, UF `GO`, com `microrregiao` e `regiao-imediata` preenchidos
- **THEN** o sistema persiste exatamente uma linha de `city` com `ibge_code` `5206206`, name Cristalina e estado UF `GO`
- **AND** não cria cidade nem distrito a partir de "Entorno de Brasília" ou "Luziânia"

#### Scenario: Identidade do catálogo é código IBGE, não place Google

- **WHEN** um município é semeado
- **THEN** a linha é identificada por `ibge_code`
- **AND** `city` não tem coluna `google_place_id`

#### Scenario: Seed é idempotente

- **WHEN** o seeder roda uma segunda vez
- **THEN** o número de linhas de `city` permanece o mesmo

#### Scenario: Seed usa UF da região imediata quando microrregião é nula

- **WHEN** o dump contém o município `id` `5101837`, `nome` Boa Esperança do Norte, `microrregiao` nula e `regiao-imediata.regiao-intermediaria.UF.sigla` `MT`
- **THEN** o sistema persiste uma linha de `city` com `ibge_code` `5101837`, name Boa Esperança do Norte e estado UF `MT`

#### Scenario: Seed pula município sem UF após o fallback

- **WHEN** um item do dump não tem `id`, `nome` ou UF nem em `microrregiao` nem em `regiao-imediata`
- **THEN** o sistema não persiste essa linha
- **AND** o seed dos demais municípios continua

#### Scenario: Testes nunca chamam IBGE

- **WHEN** a suíte automatizada roda
- **THEN** nenhum teste faz HTTP para hosts do IBGE

### Requirement: Picker autenticado de cidades por UF

O sistema SHALL expor `GET /api/cities` para o chamador autenticado. A query `uf` (código de duas letras) MUST ser obrigatória. A query `search` MAY filtrar pelo `normalized_name`, ignorando acento e caixa. O sistema MUST listar apenas cidades ativas: uma cidade inativa MUST NOT aparecer, com ou sem `search`. A resposta MUST usar DTOs explícitos com tokens opacos de `city`, nunca ids numéricos. O sistema MUST NOT exigir as permissões `list_cities` ou `list_states`. O sistema MUST NOT criar um path `/api/geo`.

O sistema SHALL expor `GET /api/states` para o mesmo chamador autenticado (código + nome + token). UF desconhecida em `GET /api/cities` MUST devolver `404` com chave MessageSource. `GET /api/cities` sem `uf` MUST devolver `400` com chave MessageSource.

Sem `search`, o sistema MUST ainda devolver municípios daquela UF (paginado). O sistema MUST NOT aceitar nome de cidade em texto livre como forma de criar uma linha de `city`. `GET /api/countries` MUST permanecer restrito às permissões admin.

#### Scenario: Busca cidades no DF

- **WHEN** um chamador autenticado chama `GET /api/cities?uf=DF&search=brasilia`
- **THEN** o sistema devolve o token e o nome do município Brasília
- **AND** não devolve municípios de outra UF

#### Scenario: Lista da UF sem search

- **WHEN** um chamador autenticado chama `GET /api/cities?uf=DF` sem `search`
- **THEN** o sistema devolve a página de municípios do DF

#### Scenario: Cidade inativa não aparece

- **WHEN** existe uma cidade inativa na UF
- **AND** um chamador autenticado chama `GET /api/cities?uf=DF`, com ou sem `search` que a casaria
- **THEN** o sistema não devolve essa cidade

#### Scenario: uf ausente

- **WHEN** um chamador autenticado chama `GET /api/cities` sem `uf`
- **THEN** o sistema devolve `400` com mensagem pt-BR resolvida pelo MessageSource

#### Scenario: UF desconhecida

- **WHEN** um chamador autenticado chama `GET /api/cities?uf=XX`
- **THEN** o sistema devolve `404` com mensagem pt-BR resolvida pelo MessageSource

#### Scenario: Lista de UFs autenticada

- **WHEN** um chamador autenticado sem `list_states` chama `GET /api/states`
- **THEN** o sistema devolve `200` com as UFs brasileiras

#### Scenario: Picker sem autenticação

- **WHEN** uma requisição sem Bearer token válido chama `GET /api/states` ou `GET /api/cities`
- **THEN** o sistema devolve `401 Unauthorized`
