## ADDED Requirements

### Requirement: Cidade Google sem match não é busca vazia

Ao resolver place ids da busca, se o componente de cidade do Google não casar com um município IBGE, o sistema MUST devolver `400 Bad Request` com mensagem MessageSource pt-BR. O sistema MUST NOT devolver HTTP 200 com página vazia nesse caso.

Quando a cidade casa com o IBGE e nenhum motorista cobre o ponto, o sistema SHALL ainda devolver HTTP 200 com página vazia (sem oferta local — não é erro de formulário).

#### Scenario: Cidade Google sem correspondência na busca

- **WHEN** um cliente autenticado busca com um `placeId` cujo componente de cidade não casa com nenhuma cidade IBGE naquela UF
- **THEN** o sistema devolve `400`
- **AND** não dispara a query de match de motoristas

#### Scenario: Cidade casada sem motoristas

- **WHEN** um cliente autenticado busca com um `placeId` que casa com uma cidade IBGE
- **AND** nenhum motorista tem área de atuação cobrindo aquele ponto
- **THEN** o sistema devolve `200` com página vazia
