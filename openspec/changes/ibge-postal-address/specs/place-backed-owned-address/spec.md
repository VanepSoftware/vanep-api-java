## MODIFIED Requirements

### Requirement: An owned address is submitted as a Google place

O sistema SHALL aceitar o endereço de uma **escola** (`address` em `POST /api/schools` e `PATCH /api/schools/{token}`) como `AddressRequestDTO` carregando `placeId`, `sessionToken` opcional e `number` e `complement` informados pelo chamador. O sistema MUST NOT aceitar `cityToken`, `zipCode` ou `street` do chamador, porque resolve os três a partir do Place Details e o cliente não é confiável para componentes que o sistema já possui.

O sistema MUST resolver o place por `PlacesClient.findPlaceDetails(placeId, sessionToken)` e persistir a geografia por `LocationResolverService.resolveAndPersist`, que casa a cidade contra o catálogo IBGE e nunca a cria (capability `geography-tree`). O sistema MUST preencher cidade, distrito mais profundo, rua, CEP e `google_place_id` na linha de `address`.

O endereço de **dependente** deixa de seguir este contrato e passa a seguir `dependent-owned-address` (`cityToken` + formulário postal). `AddressRequestDTO` MUST continuar sendo usado só por escola.

#### Scenario: Escola criada com endereço

- **WHEN** um usuário com `create_school` cria uma escola com `address` carregando um `placeId` válido
- **THEN** o sistema resolve o place e persiste uma linha de `address`
- **AND** essa linha carrega a cidade, o distrito, a rua e o CEP devolvidos pelo Place Details
- **AND** a resposta `address` é o `AddressResponseDTO` resolvido

#### Scenario: Componentes enviados pelo chamador são ignorados

- **WHEN** o body carrega `cityToken`, `zipCode` ou `street` junto com `placeId`
- **THEN** o sistema persiste só o que o Place Details resolveu
- **AND** nenhum componente enviado pelo chamador chega à linha de `address`

#### Scenario: O session token fecha a sessão de autocomplete

- **WHEN** o body carrega `placeId` e `sessionToken`
- **THEN** o sistema repassa o `sessionToken` ao Place Details

#### Scenario: O número do chamador vem antes do número do place

- **WHEN** o chamador envia `number` e o Place Details também devolve um número de rua
- **THEN** a linha de `address` guarda o `number` do chamador

#### Scenario: O número cai para o do place

- **WHEN** o chamador não envia `number` e o Place Details devolve um número de rua
- **THEN** a linha de `address` guarda o número do place

### Requirement: Creating an address requires a place

O sistema MUST devolver HTTP 400 quando um objeto `address` de **escola** está presente, a escola ainda não tem endereço e `placeId` falta ou está em branco. A mensagem MUST vir da chave MessageSource `address.place_required`.

O sistema MUST NOT criar uma linha de `address` sem cidade resolvida, porque `address.city_id` é NOT NULL e uma linha sem geografia não pode ser buscada.

#### Scenario: Objeto address sem place numa escola nova

- **WHEN** uma escola sem endereço é criada ou atualizada com `address` carregando só `number`
- **THEN** o sistema devolve HTTP 400
- **AND** a mensagem vem de `address.place_required`
- **AND** nenhuma linha de `address` é criada

### Requirement: An existing address is amended without reselecting the place

O sistema SHALL aceitar um `address` de escola carregando só `number` e `complement` quando a escola já tem endereço, e MUST atualizar essas duas colunas mantendo inalterados cidade, distrito, rua, CEP e `google_place_id`.

Enviar `placeId` para uma escola que já tem endereço MUST re-resolver e substituir a linha inteira no mesmo lugar, mantendo o mesmo `address.id` para que o ponteiro do dono e o índice unique fiquem intocados.

#### Scenario: Só o número muda

- **WHEN** uma escola com endereço é atualizada com `address` carregando `number` e nenhum `placeId`
- **THEN** rua, cidade, distrito, CEP e `google_place_id` armazenados ficam inalterados
- **AND** o número armazenado é o novo
- **AND** o Place Details não é chamado

#### Scenario: O place é trocado

- **WHEN** uma escola com endereço é atualizada com `address` carregando outro `placeId`
- **THEN** a linha é re-resolvida a partir do novo place
- **AND** o `address.id` é o mesmo de antes

#### Scenario: Limpar o endereço não muda

- **WHEN** uma escola é atualizada com `address` presente e JSON `null`
- **THEN** o sistema faz soft-delete da linha e zera `school.address_id`, como já fazia

### Requirement: Place failures are reported, not swallowed

O sistema MUST devolver HTTP 400 quando o Place Details não resolve o `placeId`, MUST devolver HTTP 400 com a chave `location.address.street_required` quando o place resolvido não tem rua (um endereço de escola sem rua não é um endereço), e MUST devolver HTTP 400 com a mensagem de município sem correspondência no IBGE quando o componente de cidade do place não casa com nenhuma `city` da UF.

O sistema MUST NOT persistir um endereço parcialmente resolvido.

#### Scenario: Place desconhecido

- **WHEN** o body carrega um `placeId` que o Place Details não resolve
- **THEN** o sistema devolve HTTP 400
- **AND** nenhuma linha de `address` é criada ou alterada

#### Scenario: Place sem rua

- **WHEN** o place resolvido não traz componente de rua
- **THEN** o sistema devolve HTTP 400 com a mensagem de `location.address.street_required`
- **AND** nenhuma linha de `address` é criada ou alterada

#### Scenario: Cidade do place sem correspondência no IBGE

- **WHEN** o place resolve para uma UF e um nome de cidade que não casam com nenhuma `city` daquela UF
- **THEN** o sistema devolve HTTP 400 com mensagem pt-BR resolvida pelo MessageSource
- **AND** nenhuma linha de `city`, `district` ou `address` é criada ou alterada

## ADDED Requirements

### Requirement: Um só colaborador resolve endereço por place

O sistema MUST resolver um place do Google em colunas de `address` em exatamente um lugar do código: `AddressPlaceResolverService`. Depois desta change, o único chamador desse colaborador para escrita de endereço é `AddressService.upsertForSchool`. `PersonalAddressService` e `AddressService.upsertForDependent` MUST NOT chamá-lo; ambos resolvem por catálogo (`AddressCatalogResolverService`).

#### Scenario: Escola continua resolvendo pelo colaborador de place

- **WHEN** uma escola é criada com `placeId`
- **THEN** a resolução passa por `AddressPlaceResolverService.applyPlace`
- **AND** os testes de escola que já existiam continuam verdes depois de inserirem a cidade IBGE de que dependem

## REMOVED Requirements

### Requirement: One place-resolution path for every address

**Reason**: O requisito exigia que `PersonalAddressService` e `AddressService` chamassem, os dois, o `AddressPlaceResolverService`. Com o endereço pessoal (fase 6) e o de dependente (fase 7) resolvendo por `cityToken`, nenhum dos dois passa mais por place. O colaborador segue sendo o único caminho de place, agora só para escola (requisito "Um só colaborador resolve endereço por place").

**Migration**: Nenhuma para clientes. No código, `PersonalAddressService` e `AddressService.upsertForDependent` passam a depender de `AddressCatalogResolverService`; `AddressPlaceResolverService` só fica ligado ao caminho de escola.
