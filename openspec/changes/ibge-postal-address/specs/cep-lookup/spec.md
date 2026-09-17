## Purpose

Consulta um CEP brasileiro no ViaCEP e devolve a cidade do catálogo mais o prefill postal para o app preencher o formulário de endereço sem fazer o save depender dos Correios.

## ADDED Requirements

### Requirement: Lookup autenticado de CEP por ibge_code

O sistema SHALL expor `GET /api/cep/{cep}` para o chamador autenticado. O path `{cep}` MUST ter oito dígitos. O sistema MUST chamar o ViaCEP no servidor, mapear o campo `ibge` da resposta para `city.ibge_code` e devolver um DTO explícito com `cityToken`, nome da cidade, UF e o logradouro e bairro do ViaCEP quando presentes (nullable). O sistema MUST NOT persistir uma linha de `address` a partir deste endpoint.

Controllers MUST permanecer magros; o HTTP para o ViaCEP MUST viver num client dedicado, stubado nos testes. O `application-test` MUST cravar a URL base do ViaCEP num endereço local inroteável (regra 50 da constituição).

O endpoint MUST ter rate limit por usuário autenticado.

#### Scenario: CEP conhecido em Brasília

- **WHEN** um usuário autenticado consulta um CEP cujo `ibge` do ViaCEP é `5300108`
- **THEN** o sistema devolve `200` com o `cityToken` de Brasília, UF `DF` e qualquer logradouro/bairro que o ViaCEP tiver fornecido

#### Scenario: Formato de CEP inválido

- **WHEN** o path não tem oito dígitos
- **THEN** o sistema devolve `400`

#### Scenario: CEP não encontrado no ViaCEP

- **WHEN** o ViaCEP informa o CEP como desconhecido
- **THEN** o sistema devolve `404` com mensagem pt-BR resolvida pelo MessageSource
- **AND** não persiste um endereço

#### Scenario: ViaCEP indisponível

- **WHEN** o ViaCEP dá timeout ou a conexão falha
- **THEN** o sistema devolve `503` com mensagem pt-BR resolvida pelo MessageSource
- **AND** não persiste um endereço

#### Scenario: Código IBGE ausente no catálogo

- **WHEN** o ViaCEP devolve um `ibge` que não casa com nenhum `city.ibge_code`
- **THEN** o sistema devolve `404` com mensagem pt-BR resolvida pelo MessageSource

#### Scenario: Lookup sem autenticação

- **WHEN** uma requisição sem Bearer token válido chama `GET /api/cep/{cep}`
- **THEN** o sistema devolve `401 Unauthorized`

#### Scenario: Rate limit

- **WHEN** um usuário ultrapassa o rate limit configurado de lookup de CEP
- **THEN** o sistema recusa a requisição sem chamar o ViaCEP

#### Scenario: Testes nunca chamam ViaCEP

- **WHEN** a suíte automatizada roda
- **THEN** nenhum teste faz HTTP para viacep.com.br nem para qualquer host real de ViaCEP
