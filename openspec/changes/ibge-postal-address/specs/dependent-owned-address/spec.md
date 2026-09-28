## ADDED Requirements

### Requirement: Endereço de embarque do dependente criado a partir de formulário postal

O sistema SHALL aceitar o endereço de um dependente (`address` em `POST /api/dependent` e `PATCH /api/dependent/{token}`) como `DependentAddressRequestDTO`, carregando `cityToken`, `street` e `zipCode` como obrigatórios (`zipCode` MUST ser exatamente oito dígitos), e `number`, `complement`, `neighborhood` opcionais. O sistema MUST NOT aceitar `placeId` ou `sessionToken` neste contrato. O sistema MUST resolver `cityToken` para uma linha de `city` existente e MUST NOT criar cidade a partir de nome ou UF enviados pelo cliente. `district_id` e `google_place_id` MUST ficar nulos neste caminho. O sistema MUST NOT chamar Google Place Details nem ViaCEP ao tratar `address` do dependente.

Este contrato substitui, só para dependente, o de `place-backed-owned-address` (ver o delta dessa capability nesta change). Escola continua sob `place-backed-owned-address`.

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

- **WHEN** `cityToken` do `address` do dependente não casa com nenhuma cidade do catálogo
- **THEN** o sistema devolve `404` com chave MessageSource `city.not_found`

#### Scenario: Place id não é mais aceito

- **WHEN** o `address` do dependente carrega `placeId` ou `sessionToken`
- **THEN** o sistema ignora os campos — não fazem parte do contrato
- **AND** a requisição só é aceita se `cityToken`, `street` e `zipCode` forem válidos
- **AND** nenhuma chamada a Place Details é feita

#### Scenario: Bairro gravado como texto

- **WHEN** o `address` do dependente inclui `neighborhood`
- **THEN** o sistema persiste no registro de endereço
- **AND** não cria nem atualiza um nó de `district` a partir desse valor

### Requirement: Reenviar o endereço substitui o do dependente por inteiro

O sistema SHALL tratar `address` num `PATCH /api/dependent/{token}` como a substituição completa do endereço do dependente, no mesmo molde de `PUT /api/user/me/address`. Quando o dependente já tem endereço, o sistema MUST sobrescrever a linha existente, mantendo o mesmo `address.id`, com `city`, `street`, `zipCode`, `number`, `complement` e `neighborhood` vindos todos do body; `number`, `complement` e `neighborhood` omitidos ou nulos MUST ficar nulos. O sistema MUST NOT ter modo de amend parcial: um `address` sem `cityToken`, `street` ou `zipCode` MUST ser recusado com `400` mesmo quando o dependente já tem endereço.

O app remonta o formulário a partir de `address` na resposta de leitura, que já devolve `cityToken`, `street`, `zipCode`, `number`, `complement` e `neighborhood`. Omitir `address` no PATCH MUST deixar o endereço armazenado intacto.

#### Scenario: Reenvio troca a cidade mantendo a linha

- **WHEN** um dependente com endereço recebe `PATCH /api/dependent/{token}` com `address` completo carregando outro `cityToken`
- **THEN** a cidade da linha passa a ser a nova
- **AND** o `address.id` é o mesmo de antes

#### Scenario: Corrigir o número reenvia o formulário completo

- **WHEN** um dependente com endereço recebe `PATCH` com `address` completo (`cityToken`, `street`, `zipCode`) e `number` diferente
- **THEN** o número armazenado é o novo
- **AND** o `address.id` é o mesmo de antes

#### Scenario: Campos opcionais omitidos ficam nulos

- **WHEN** um dependente com `complement` e `neighborhood` gravados recebe `PATCH` com `address` completo sem esses dois campos
- **THEN** `complement` e `neighborhood` armazenados ficam nulos

#### Scenario: Endereço parcial é recusado

- **WHEN** um dependente com endereço recebe `PATCH` com `address` carregando só `number`
- **THEN** o sistema devolve `400`
- **AND** o endereço armazenado permanece inalterado

#### Scenario: PATCH só com o nome não altera o endereço

- **WHEN** um dependente com endereço gravado recebe `PATCH /api/dependent/{token}` com corpo `{ "name": "Novo" }`
- **THEN** o endereço armazenado (cidade, rua, CEP, número, complemento, bairro) permanece inalterado

#### Scenario: Endereço já de outro dono ativo

- **WHEN** a linha de `address` do dependente também é referenciada por uma escola ativa
- **AND** o cliente envia `address` completo no PATCH
- **THEN** o sistema devolve `409` com chave MessageSource `address.already_owned`
- **AND** a linha não é alterada

### Requirement: Endereço do dependente é limpo por null ou pela exclusão do dependente

O sistema SHALL limpar o endereço de embarque do dependente (soft-delete da linha de `address` e `dependent.address_id` nulo) quando `PATCH /api/dependent/{token}` traz `address` com JSON `null` e quando o dependente é excluído. Este comportamento MUST ser o mesmo independente de a linha ter sido criada por `cityToken` (esta capability) ou por `placeId` (contrato histórico da PR #173).

#### Scenario: Limpar o endereço por null

- **WHEN** um dependente com endereço é atualizado com `address` presente e JSON `null`
- **THEN** o sistema faz soft-delete da linha e zera `dependent.address_id`

#### Scenario: Delete do dependente limpa o endereço

- **WHEN** um cliente deleta um dependente que tem endereço gravado por `cityToken`
- **THEN** o sistema faz soft-delete da linha de `address`
- **AND** `dependent.address_id` fica nulo

### Requirement: Resposta do endereço expõe neighborhood

O sistema SHALL incluir `neighborhood` no `AddressResponseDTO` devolvido nos endereços de dependente e de escola. O campo MUST ser nulo quando a linha não tem bairro gravado (todo endereço de escola, que segue por place). A mudança é aditiva e MUST NOT quebrar consumidores existentes.

#### Scenario: Bairro do dependente volta na leitura

- **WHEN** um dependente foi gravado com `neighborhood`
- **AND** o cliente lê o dependente (`GET /api/dependent/{token}` ou a lista)
- **THEN** `address.neighborhood` traz o valor gravado

#### Scenario: Escola devolve neighborhood nulo

- **WHEN** um endereço de escola resolvido por place é lido
- **THEN** `address.neighborhood` é nulo

### Requirement: Um só colaborador resolve endereço por catálogo

O sistema MUST resolver um `cityToken` em colunas de `address` (`city`, `street`, `zipCode`, `number`, `complement`, `neighborhood`, com `district` e `googlePlaceId` nulos) em exatamente um lugar do código: `AddressCatalogResolverService`. `PersonalAddressService` e `AddressService.upsertForDependent` MUST ambos chamar esse colaborador em vez de cada um manter uma cópia da sequência cidade → colunas.

#### Scenario: O endereço pessoal mantém o comportamento

- **WHEN** `PUT /api/user/me/address` é chamado depois da extração do colaborador
- **THEN** a linha gravada carrega a mesma cidade, rua, CEP, número, complemento e `neighborhood` que carregava antes
- **AND** os testes existentes da fase 6 passam sem alteração

#### Scenario: Dependente e endereço pessoal gravam do mesmo jeito

- **WHEN** o mesmo `cityToken`, `street`, `zipCode`, `number`, `complement` e `neighborhood` são enviados pelo endereço pessoal e pelo dependente
- **THEN** as duas linhas de `address` ficam com as mesmas colunas, com `district_id` e `google_place_id` nulos
