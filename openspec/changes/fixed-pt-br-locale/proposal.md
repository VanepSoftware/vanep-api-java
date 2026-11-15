## Por quê

O Spring Boot 4 ignora as propriedades legadas `spring.mvc.locale` e
`spring.mvc.locale-resolver`. Com isso, requisições sem `Accept-Language` usam o locale da
JVM e podem devolver mensagens em inglês em produção e na CI.

## O que muda

- Migrar as propriedades de locale da aplicação e dos testes para o namespace
  `spring.web` suportado pelo Spring Boot 4.
- Adicionar uma regressão automatizada que comprove que o resolver permanece fixo em
  `pt-BR`, inclusive quando a requisição pede outro idioma.
- Validar a suíte com a JVM configurada como `en_US`.

## Fora de escopo

- Adicionar negociação de idioma por requisição.
- Alterar os catálogos de mensagens ou seus textos.
- Alterar endpoints, contratos HTTP ou regras de negócio.
