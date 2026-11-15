## Dependências

```text
1. Teste de regressão (RED)
        |
        v
2. Configuração principal e de teste (GREEN)
        |
        v
3. Validação focada e suíte em en_US
        |
        v
4. Lint e revisão do diff
```

## Plano de entrega

| Fase | Camada | Alteração | Depende de | Validação |
| --- | --- | --- | --- | --- |
| 1 | Testes | Cobrir o `LocaleResolver` fixo em `pt-BR` sem cabeçalho e com `en-US` | — | O novo teste falha com as propriedades legadas |
| 2 | Configuração | Trocar `spring.mvc.*` por `spring.web.*` nos dois arquivos de propriedades | 1 | O teste focado passa |
| 3 | QA | Executar a suíte com a JVM em `en_US` | 2 | `./mvnw verify -B` verde com `JAVA_TOOL_OPTIONS` |
| 4 | Qualidade | Executar o lint equivalente e revisar o escopo | 3 | `spotless:check` verde e diff restrito à VAN-77 |

## Checklist

- [x] 1.1 Adicionar o teste de integração antes da correção.
- [x] 1.2 Registrar a falha causada pelas propriedades legadas.
- [x] 2.1 Atualizar `src/main/resources/application.properties`.
- [x] 2.2 Atualizar `src/test/resources/application-test.properties`.
- [x] 3.1 Executar o teste focado com a JVM em `en_US`.
- [x] 3.2 Executar `./mvnw verify -B` com a JVM em `en_US`.
- [x] 4.1 Executar `./mvnw spotless:check` imediatamente antes do commit.
- [x] 4.2 Revisar o diff e apresentar a branch para revisão antes de qualquer push.

## Entrega

- Branch única: `fix/van-77-spring-web-locale`.
- Uma PR separada para a `main`, fora da pilha da VAN-30.
- Nenhum push antes da revisão do usuário.
