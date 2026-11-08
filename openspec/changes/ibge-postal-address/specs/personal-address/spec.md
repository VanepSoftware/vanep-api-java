## ADDED Requirements

### Requirement: Endereço pessoal criado a partir de formulário postal

O sistema SHALL expor `PUT /api/user/me/address` e `GET /api/user/me/address` para o chamador autenticado. O body do PUT MUST aceitar `cityToken`, `street` e `zipCode` como obrigatórios (`zipCode` MUST ser exatamente oito dígitos). MAY aceitar `number`, `complement` e `neighborhood`. O sistema MUST resolver `cityToken` para uma linha de `city` existente e MUST NOT criar cidade a partir de nome ou UF enviados pelo cliente. O sistema MUST persistir `street`, `zipCode`, campos postais opcionais e `city_id`; `district_id` e `google_place_id` MUST ficar nulos neste caminho. A coluna `address.zip_code` MAY permanecer nullable no schema; a obrigatoriedade MUST ser do DTO, não de um `NOT NULL` restaurado.

O sistema MUST NOT chamar Google Place Details e MUST NOT chamar ViaCEP ao tratar o PUT. Nomes de cidade ou UF enviados pelo cliente, quando presentes, MUST ser ignorados.

Controllers MUST permanecer magros; a persistência MUST viver num `@Service`.

#### Scenario: Endereço criado a partir de cidade do catálogo e rua

- **WHEN** um usuário autenticado envia `PUT /api/user/me/address` com `cityToken` válido, `street` não em branco e `zipCode` de oito dígitos
- **THEN** o sistema persiste o endereço ligado a `users.address_id`
- **AND** devolve `200 OK` com DTO de resposta explícito expondo tokens opacos, nome da cidade, UF, rua e neighborhood quando gravado

#### Scenario: CEP omitido

- **WHEN** o body tem `cityToken` e `street` e omite `zipCode`
- **THEN** o sistema devolve `400`
- **AND** não persiste o endereço

#### Scenario: Formato de CEP inválido

- **WHEN** `zipCode` está presente e não tem oito dígitos
- **THEN** o sistema devolve `400`

#### Scenario: Rua em branco

- **WHEN** `street` falta ou está em branco
- **THEN** o sistema devolve `400`

#### Scenario: Token de cidade desconhecido

- **WHEN** `cityToken` não casa com nenhuma cidade do catálogo
- **THEN** o sistema devolve `404` com chave MessageSource `city.not_found`

#### Scenario: Nome de cidade do cliente é ignorado

- **WHEN** o body inclui `cityName` ou `uf` além de `cityToken`, `street` e `zipCode` válidos
- **THEN** o sistema persiste a cidade daquele token
- **AND** não cria cidade a partir dos campos extras

#### Scenario: Place id não é obrigatório

- **WHEN** o body não tem `placeId`
- **THEN** o sistema ainda aceita a requisição se `cityToken`, `street` e `zipCode` forem válidos

#### Scenario: Bairro gravado como texto

- **WHEN** a requisição inclui `neighborhood`
- **THEN** o sistema persiste no registro de endereço
- **AND** não cria nem atualiza um nó de `district` a partir desse valor

#### Scenario: Acesso sem autenticação

- **WHEN** uma requisição sem Bearer token válido chama `PUT /api/user/me/address` ou `GET /api/user/me/address`
- **THEN** o sistema devolve `401 Unauthorized`

## MODIFIED Requirements

### Requirement: Dono pode limpar o endereço pessoal

O sistema SHALL expor `DELETE /api/user/me/address` para o chamador autenticado identificado pelo `uid` do JWT. Em sucesso o sistema MUST fazer soft-delete da linha de `address` do chamador, setar `users.address_id` para null e devolver HTTP 204. Quando o chamador não tem endereço, o sistema MUST devolver HTTP 204 (idempotente) para o app sempre poder DELETE. Controllers MUST permanecer magros; esta lógica MUST viver em `PersonalAddressService`. Limpar MUST NOT alterar o endereço de embarque de um dependente.

#### Scenario: Delete de endereço existente

- **WHEN** um usuário autenticado que tem endereço pessoal chama `DELETE /api/user/me/address`
- **THEN** o sistema devolve HTTP 204
- **AND** `users.address_id` é null
- **AND** um `GET /api/user/me/address` seguinte devolve HTTP 404

#### Scenario: Delete quando não há endereço

- **WHEN** um usuário autenticado com `users.address_id` nulo chama `DELETE /api/user/me/address`
- **THEN** o sistema devolve HTTP 204

#### Scenario: Put depois de limpar cria linha nova

- **WHEN** um usuário autenticado faz DELETE do endereço pessoal
- **AND** em seguida faz PUT com `cityToken`, `street` e `zipCode` válidos
- **THEN** o sistema devolve HTTP 200
- **AND** persiste uma nova linha de `address` ligada a `users.address_id`

#### Scenario: Delete sem autenticação

- **WHEN** `DELETE /api/user/me/address` é chamado sem Bearer token válido
- **THEN** o sistema devolve HTTP 401

#### Scenario: Passo de onboarding volta depois de limpar

- **WHEN** um cliente autenticado que tinha concluído `PERSONAL_ADDRESS` deleta o endereço
- **AND** chama `GET /api/user/me`
- **THEN** `onboarding.pendingSteps` contém `PERSONAL_ADDRESS`

### Requirement: Endereço pessoal é privado

O sistema MUST NOT expor rua, número, complemento, CEP ou neighborhood de um endereço pessoal em qualquer resposta voltada a motorista ou de busca. Dados de endereço pessoal MUST ser legíveis somente pelo dono.

#### Scenario: Endereço ausente nos resultados da busca

- **WHEN** um cliente faz uma busca de motorista
- **THEN** nenhuma rua, número, complemento, CEP ou neighborhood de qualquer motorista aparece na resposta

#### Scenario: Dono lê o próprio endereço

- **WHEN** um usuário autenticado lê `GET /api/user/me/address`
- **THEN** o sistema devolve o endereço completo só daquele chamador

## REMOVED Requirements

### Requirement: Endereço pessoal criado a partir de um place resolvido

**Reason**: Google Place Details não é fonte postal confiável para endereço residencial no Brasil. O contrato precisa de um formulário editável chaveado pela cidade IBGE.

**Migration**: Clientes MUST enviar `cityToken`, `street` e `zipCode` (8 dígitos) no `PUT /api/user/me/address`. O `GET /api/cep/{cep}` opcional preenche o form. `placeId` deixa de ser o contrato de escrita.
