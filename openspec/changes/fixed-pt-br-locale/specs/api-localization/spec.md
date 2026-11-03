## ADDED Requirements

### Requirement: API messages use a fixed Brazilian Portuguese locale

The API MUST resolve request-scoped messages with the `pt-BR` locale independently of the
JVM default locale. The API MUST use the fixed locale resolver supported by Spring Boot 4 and
MUST NOT negotiate another locale from the request `Accept-Language` header.

The production and test configurations MUST use `spring.web.locale=pt_BR` and
`spring.web.locale-resolver=fixed`. They MUST NOT rely on the removed `spring.mvc.locale` or
`spring.mvc.locale-resolver` properties.

#### Scenario: Request without a language header on an English JVM

- **GIVEN** the API runs with the JVM default locale set to `en-US`
- **WHEN** a request that produces a localized message omits `Accept-Language`
- **THEN** the message is resolved with `pt-BR`

#### Scenario: Request asks for another language

- **WHEN** a request that produces a localized message sends `Accept-Language: en-US`
- **THEN** the message is still resolved with `pt-BR`

#### Scenario: Test profile matches production localization

- **WHEN** the Spring test profile loads the web locale configuration
- **THEN** its locale resolver is fixed to `pt-BR` in the same way as the production profile
