## Contexto

A API usa `LocaleContextHolder` em serviços e advices para resolver mensagens. O locale
deve ser globalmente fixo em português do Brasil, mas as chaves atuais pertencem ao
namespace removido do Spring Boot 4.

## Decisão

Configurar `spring.web.locale=pt_BR` e `spring.web.locale-resolver=fixed` tanto no perfil
principal quanto no perfil de testes. A correção fica na configuração nativa do Spring
Boot, sem criar um `LocaleResolver` próprio.

## Teste de regressão

Um teste de integração carrega o contexto real e resolve o locale para uma requisição sem
`Accept-Language` e para outra com `Accept-Language: en-US`. As duas devem resultar em
`pt-BR`, comprovando que o resolver é fixo e não depende do cabeçalho.

Além do teste focado, a suíte completa será executada com
`JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US`, cobrindo a independência do
locale padrão da JVM.

## Risco e reversão

O impacto é global e intencional: clientes não poderão selecionar outro idioma por
`Accept-Language`. A reversão consiste em restaurar as duas propriedades anteriores, mas
isso reintroduziria a dependência do ambiente no Spring Boot 4.
