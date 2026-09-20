## ADDED Requirements

### Requirement: Endereço de embarque do dependente criado a partir de formulário postal

O sistema SHALL aceitar um endereço de dependente (`address` em `POST /api/dependent` e `PATCH /api/dependent/{token}`) como `DependentAddressRequestDTO`, carregando `cityToken`, `street` e `zipCode` como obrigatórios (`zipCode` MUST ser exatamente oito dígitos), e `number`, `complement`, `neighborhood` opcionais. O sistema MUST NOT aceitar `placeId` ou `sessionToken` neste contrato. O sistema MUST resolver `cityToken` para uma linha de `city` existente e MUST NOT criar cidade a partir de nome ou UF enviados pelo cliente. `district_id` e `google_place_id` MUST ficar nulos neste caminho.

Este requirement substitui, só para dependente, o requirement "An owned address is submitted as a Google place" de `dependent-address-by-place` (capability `place-backed-owned-address`). Escola continua sob aquele requirement, inalterado.

#### Scenario: Dependente criado com endereço do catálogo

- **WHEN** um cliente cria um dependente com `address` carregando `cityToken` válido, `street` não em branco e `zipCode` de oito dígitos
- **THEN** o sistema persiste uma linha de `address` ligada ao dependente
- **AND** a resposta `address` expõe tokens opacos, cidade, UF, rua, CEP e `neighborhood` quando gravado

#### Scenario: CEP omitido

- **WHEN** o `address` do dependente tem `cityToken` e `street` e omite `zipCode`
- **THEN** o sistema devolve `400`
- **AND** não persiste o endereço

#### Scenario: Formato de CEP inválido

- **WHEN** `zipCode` está presente e não tem oito dígitos
- **THEN** o sistema devolve `400`

#### Scenario: Rua em branco

- **WHEN** `street` falta ou está em branco no `address` do dependente
- **THEN** o sistema devolve `400`

#### Scenario: Token de cidade desconhecido

- **WHEN** `cityToken` do `address` do dependente não casa com uma cidade ativa
- **THEN** o sistema devolve `404` com chave MessageSource `city.not_found`

#### Scenario: Place id não é mais aceito

- **WHEN** o `address` do dependente carrega `placeId`
- **THEN** o sistema ignora o campo — não é parte do contrato
- **AND** a requisição só é aceita se `cityToken`, `street` e `zipCode` forem válidos

#### Scenario: Bairro gravado como texto

- **WHEN** o `address` do dependente inclui `neighborhood`
- **THEN** o sistema persiste no registro de endereço
- **AND** não cria nem atualiza um nó de `district` a partir desse valor

### Requirement: Endereço do dependente pode ser amendado sem reenviar a cidade

O sistema SHALL aceitar, quando o dependente já tem endereço, um `address` amendando `street`, `zipCode`, `number`, `complement` ou `neighborhood` sem exigir `cityToken` presente de novo — mas se `cityToken` vier, MUST trocar a cidade da linha existente, mantendo o mesmo `address.id`.

#### Scenario: PATCH só com o nome não altera o endereço

- **WHEN** um dependente com endereço gravado recebe `PATCH /api/dependent/{token}` com corpo `{ "name": "Novo" }`
- **THEN** o endereço armazenado (cidade, rua, CEP, número, complemento, bairro) permanece inalterado

#### Scenario: Limpar o endereço continua igual

- **WHEN** um dependente é atualizado com `address` presente e JSON `null`
- **THEN** o sistema faz soft-delete da linha e zera `dependent.address_id`, como já fazia no contrato `placeId`

### Requirement: Um só colaborador resolve endereço por catálogo

O sistema MUST resolver um `cityToken` em colunas de `address` (`city`, `street`, `zipCode`, `number`, `complement`, `neighborhood`, com `district`/`googlePlaceId` nulos) em exatamente um lugar do código. `PersonalAddressService` e `AddressService.upsertForDependent` MUST ambos chamar esse colaborador em vez de cada um manter uma cópia da sequência cidade → colunas.

#### Scenario: O endereço pessoal mantém o comportamento

- **WHEN** `PUT /api/user/me/address` é chamado depois da extração do colaborador
- **THEN** a linha gravada carrega a mesma cidade, rua, CEP, número, complemento e `neighborhood` que carregava antes
- **AND** os testes existentes da fase 6 passam sem alteração

## MODIFIED Requirements

### Requirement: Dono pode limpar o endereço de embarque do dependente

O sistema SHALL manter o comportamento de limpeza de endereço já existente (soft-delete da linha, `dependent.address_id` para null) independente do contrato de escrita usado para criá-la — `cityToken` (esta capability) ou `placeId` histórico.

#### Scenario: Delete do dependente limpa o endereço

- **WHEN** um cliente deleta um dependente que tem endereço gravado por `cityToken`
- **THEN** o sistema faz soft-delete da linha de `address`
- **AND** `dependent.address_id` fica null

## REMOVED Requirements

### Requirement: An owned address is submitted as a Google place (dependente)

**Reason**: A PR #173 (`dependent-address-by-place`) exigiu `placeId` para dependente porque o app cliente não conseguia obter um `cityToken` — `GET /api/cities` respondia `403` pro papel CLIENT e não tinha busca por nome. As fases 2 e 3 desta change resolvem isso: catálogo IBGE semeado e `GET /api/cities?uf=&search=` com `isAuthenticated()`. Sem o bloqueio original, manter dependente em `placeId` deixaria de ter motivo, e seria o único fluxo de endereço do produto ainda preso ao Google.

**Migration**: Clientes MUST enviar `cityToken`, `street` e `zipCode` (8 dígitos) no `address` de `POST/PATCH /api/dependent`. `placeId` deixa de ser aceito. O branch `feat/8-dependent-address` do `vanep-mobile` precisa migrar do contrato `placeId` da PR #173 para este antes do release (sem contrato duplo, mesmo princípio do D5/`personal-address`).

Escola **não** é afetada — continua sob "An owned address is submitted as a Google place" em `place-backed-owned-address` (`dependent-address-by-place`), inalterado.
