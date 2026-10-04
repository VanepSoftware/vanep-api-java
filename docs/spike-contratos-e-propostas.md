# Spike — Propostas e Contratos (V2)

Sep 28, 2026 · @João

O chat é o canal certo para negociar, mas não pode ser a fonte da verdade: a solicitação, a proposta e o contrato precisam ser objetos estruturados, versionados e imutáveis, que o chat apenas exibe como cartões.

**V2 (atualizada em 03/10/2026):** 11 das 12 decisões estão fechadas. A assinatura (D6) foi decidida pela Autentique, o modelo de receita (D11) fechou com a comissão pela origem do vínculo, e a carteira própria (D12) entra por alunos não vinculados na rota mais convite para o contrato digital. Não existe contrato externo: o contrato digital é o único tipo de contrato. Fica em aberto só a revisão jurídica (D10).

## Resumo

A ideia inicial era conduzir a contratação pelo chat. O responsável preencheria um formulário antes de abrir a conversa, o motorista mandaria propostas pelo chat e, fechado o acordo, o contrato seria gerado e assinado. Negociar pelo chat está certo: ele vai existir de qualquer jeito, e responsável e motorista já negociam assim no WhatsApp. Mas esse fluxo tem cinco problemas que precisam ser resolvidos antes de virar código:

1. Negociação e termos vinculantes ficam misturados no mesmo lugar. O que foi "combinado no chat" não pode valer como contrato.
2. O motorista "iniciar o contrato" é um passo a mais e deixa ambíguo qual versão foi aceita.
3. Os dados pessoais são pedidos só no fim, o pior momento para perder o usuário.
4. Só existe a porta do responsável. O motorista que chega com 30 clientes informais não tem como trazê-los.
5. O contrato de papel usado como base tem cláusulas com risco alto de serem consideradas abusivas pelo CDC.

| #   | Decisão                                                                                                                                                            | Por quê                                                                                                               | Status    |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- | --------- |
| D1  | Solicitação, Proposta e Contrato são entidades próprias; o chat mostra cartões que apontam para elas                                                               | Uma fonte da verdade, auditável, e o módulo não depende do chat (#161) para ser entregue                              | Decidida  |
| D2  | Só o motorista escreve propostas; o responsável aceita, recusa ou pede ajuste                                                                                      | Uma cadeia com um autor só elimina o "quem aceitou a última versão de quem"                                           | Decidida  |
| D3  | O aceite do responsável gera o contrato automaticamente; ninguém "cria" contrato                                                                                   | O acordo acontece num clique explícito, e a plataforma já tem quase todos os dados                                    | Decidida  |
| D4  | Os dados do contrato são checados antes do envio da solicitação, não depois do aceite                                                                              | O aceite vira contrato na hora, sem formulário no momento de fechar                                                   | Decidida  |
| D5  | O contrato é um snapshot imutável (PDF + hash + JSON dos termos) de um template versionado                                                                         | Editar o cadastro depois não muda o que foi assinado                                                                  | Decidida  |
| D6  | Assinatura pela Autentique, como primeira implementação da interface `ContractSignatureGateway`                                                                    | API no plano gratuito, centavos por contrato e 1 a 2 semanas de integração; estudo em Spike — Assinatura de Contratos | Decidida  |
| D7  | O motorista assina por último                                                                                                                                      | Ele confirma que ainda tem vaga, o que resolve a corrida pela última vaga sem trava no banco                          | Decidida  |
| D8  | Duas portas de entrada, busca do responsável e convite do motorista, no mesmo motor                                                                                | O convite traz a carteira atual dos motoristas, o maior volume inicial possível                                       | Decidida  |
| D9  | Um contrato por par responsável–motorista, com um item por dependente                                                                                              | Irmãos no mesmo motorista cabem num contrato; mudança vira aditivo                                                    | Decidida  |
| D10 | Revisão do contrato-base por advogado                                                                                                                              | Adiada; até lá valem as recomendações provisórias em Pontos jurídicos                                                 | Em aberto |
| D11 | Receita só do motorista: mensalidade + comissão pela origem do vínculo, cerca de R$ 5 para a carteira própria e R$ 10 a 15 para quem veio da busca                 | A Vanep cobra mais só pelo cliente que ela trouxe; a origem é fixa e não depende do motorista                         | Decidida  |
| D12 | Carteira própria: o motorista cadastra alunos não vinculados só para a rota, sem trava, e convida as famílias para o fluxo normal, que termina no contrato digital | A rota funciona completa no primeiro dia sem depender dos pais, e existe um tipo de contrato só                       | Decidida  |

Este documento não é parecer jurídico: os pontos legais são o mapa do que levar a um advogado.

## O que existe hoje

O código já antecipa este módulo: o `client_driver` (V36) foi criado como hub de proposta, contrato, avaliação e chat, e a change declara que ele nasce `PENDING` no primeiro envio de proposta (#42). Proposta, contrato e chat ainda não existem em nenhum dos três repositórios.

| Peça                              | Estado hoje                                                                                                              | O que importa para este spike                                                                              |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| `client_driver` (V36)             | Status `PENDING`/`ACTIVE`/`INACTIVE`/`BLOCKED`, um ativo por par, só o admin cria                                        | Q1 (reusar o vínculo) e Q3 (quem desbloqueia) foram adiadas para a #42; `BLOCKED` não guarda quem bloqueou |
| `driver_rating` / `client_rating` | Penduram em `client_driver_id` (rating-through-link)                                                                     | Se o vínculo nascer na primeira solicitação, quem só conversou já pode avaliar                             |
| `dependent`                       | Um `school_id`, um `address_id`, um `shift`                                                                              | Não diz se o aluno vai e volta de van, em que dias, nem horários                                           |
| `trip` (V31)                      | Âncora do dia por (motorista, data, turno), sem lista de passageiros                                                     | O checklist precisa saber quem embarca; a Q1 da trip (integral = uma ou duas trips) depende do contrato    |
| `driver`                          | `base_price` único, `is_available`, `work_days` jsonb livre, `approval_status`                                           | Base para o "a partir de" na busca e para "aceitando alunos"                                               |
| `vehicle`                         | `capacity` e placa                                                                                                       | Base para vagas e para a qualificação do veículo no contrato                                               |
| `users`                           | `document` (CPF), nome, telefone, nascimento; sem RG                                                                     | Quase tudo que o contrato precisa já existe                                                                |
| `GET /api/drivers/search`         | Origem + destino por contenção na árvore; devolve `basePrice`                                                            | O botão do resultado vira "Solicitar orçamento"                                                            |
| Chat (#161)                       | Não existe                                                                                                               | Este módulo não deve esperar por ele                                                                       |
| App mobile                        | Só as strings `navProposals`, `navContracts` e `navProposalsAndContracts`; a spec de perfil marca Contratos como "later" | Telas novas; nada a desmontar                                                                              |

## Análise crítica do fluxo proposto

O fluxo acerta no canal e erra na fonte da verdade: quase todo problema abaixo vem de deixar termos, aceite e dados soltos dentro da conversa.

| Ponto do fluxo                                           | O que funciona                                                     | O que quebra                                                                                                                   | Ajuste                                                                                     |
| -------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| Chat como meio da negociação                             | Espelha o WhatsApp; o canal já vai existir para o dia a dia        | Termo combinado em texto livre não tem estrutura; o responsável vai achar que "ele falou que buscava 6h40" vale                | Proposta é cartão estruturado; aviso fixo no chat: "só vale o que está na proposta"        |
| Formulário antes do chat                                 | O motorista recebe o que precisa para precificar e filtra curiosos | Se for um formulário de verdade, é atrito no topo do funil, e boa parte dos dados já está no dependente                        | Uma tela de confirmação pré-preenchida; perguntar só o que falta (dias, ida/volta, início) |
| Enviar tudo na solicitação                               | Motorista calcula rota e preço                                     | Endereço exato e nome completo de uma criança indo para quem ainda não foi contratado é exposição desnecessária (LGPD art. 14) | Solicitação leva bairro/quadra, escola, primeiro nome e idade; o resto só depois do aceite |
| Motorista reenvia propostas no chat                      | Negociação iterativa natural                                       | Se o responsável também puder mandar contraproposta estruturada, a máquina de estados dobra                                    | Motorista é o único autor; responsável tem "pedir ajuste" com campo e valor sugerido       |
| Motorista inicia o contrato quando os dois estão prontos | Motorista mantém o controle                                        | "Estar pronto" não é um evento; passo extra; qual versão foi aceita?                                                           | O aceite do responsável na proposta é o evento que gera o contrato                         |
| Dados pessoais depois do aceite, ou contrato com lacunas | —                                                                  | Pede dados no momento de maior atrito; motorista redigindo abre espaço para cláusula arbitrária                                | Dados validados antes; a plataforma gera; ninguém edita o texto                            |
| Contrato de papel como base                              | Reflete a prática real (anuidade em parcelas, julho e dezembro)    | Cláusulas 4, 6 e 8 com risco alto no CDC; campos soltos                                                                        | Reescrever com advogado; o que varia vira parâmetro da proposta                            |
| Assinatura talvez nativa                                 | Menos dependência externa                                          | A Vanep vira custodiante da prova sendo parte interessada (ganha comissão)                                                     | Decidido na V2: Autentique, que custa centavos por contrato                                |
| Vínculo depois da formalização                           | Certo: só vira `ACTIVE` com contrato                               | O fluxo acaba na assinatura; não cobre arrependimento de 7 dias, aditivo, renovação anual nem rescisão                         | Ciclo de vida completo (seção própria)                                                     |
| Só a porta do responsável                                | Marketplace clássico                                               | Motoristas já têm carteira; sem migração, o app começa vazio para eles                                                         | Convite do motorista, no mesmo motor                                                       |

O risco mais sério não está em nenhuma tela: é a desintermediação. O chat é exatamente onde motorista e responsável trocam WhatsApp e fecham por fora, e a comissão só existe com contrato ativo. Isso está tratado em Riscos de produto.

## Fluxo recomendado

[embedded content: fluxo de ponta a ponta · 2 portas de entrada, 1 decisão, 2 assinaturas

As duas portas levam ao mesmo motor: a busca gera uma solicitação que o motorista responde com proposta; o convite já chega como proposta, depois que o responsável cria a conta e cadastra os dependentes. O chat acompanha tudo, mas nenhum passo depende de uma mensagem de texto.

Na prática, cada passo é assim:

1. **Solicitação.** Na busca, o responsável toca "Solicitar orçamento". Uma tela já preenchida a partir dos dependentes pede só o que falta: quais dependentes, dias, ida/volta e início desejado. O envio abre a conversa e cria o vínculo `PENDING`.
2. **Proposta.** O motorista recebe o push, vê o cartão da solicitação e monta a proposta num formulário (não numa mensagem). Ela aparece no chat como cartão com Aceitar, Recusar e Pedir ajuste.
3. **Negociação.** Conversa livre no chat. "Pedir ajuste" manda um cartão estruturado ("valor: R$ 320?"). O motorista responde com a versão 2, e a versão anterior fica marcada como substituída.
4. **Aceite.** O responsável aceita uma versão específica. Antes do botão, uma tela "O que você está contratando" resume valor, parcelas, vencimento, multa e como cancelar.
5. **Contrato.** A plataforma gera o PDF na hora, com os dados que já tem. Endereço exato e nome completo do dependente só chegam ao motorista agora.
6. **Assinaturas.** Primeiro o responsável, depois o motorista, que confirma a vaga. Cada parte tem 48 h.
7. **Ativo.** Na data de início, o vínculo vira `ACTIVE` e os dependentes entram na lista de passageiros da rota. Outras solicitações abertas para os mesmos dependentes são retiradas com aviso aos outros motoristas.

## Estados e prazos

Cada objeto tem sua máquina de estados, com uma policy pura testável sem banco (o mesmo padrão de `TripTransitionPolicy`). Solicitação e proposta são lineares; o contrato é o único com ciclos.

- **Solicitação** (`service_request`): `OPEN` → `ANSWERED` (recebeu proposta) → `CONVERTED` (virou contrato). Saídas: `DECLINED` (motorista não atende), `EXPIRED` (sem resposta no prazo), `WITHDRAWN` (responsável desistiu ou fechou com outro).
- **Proposta** (`proposal`): `SENT` → `ACCEPTED`. Saídas: `SUPERSEDED` (motorista mandou nova versão), `DECLINED`, `EXPIRED`, `WITHDRAWN` (motorista retirou antes do aceite). Nunca é editada; toda mudança é uma nova versão.
- **Contrato** (`contract`): o diagrama abaixo.

[embedded content: ciclo de vida do contrato · 7 estados e a renovação

`Substituído` e `Encerrado` nunca apagam nada: o histórico de contratos do par é a prova de que houve relação, e é ele que libera a avaliação. A renovação é um contrato novo, não a reativação do anterior.

| Prazo                                | Estado que expira              | Efeito                                                   |
| ------------------------------------ | ------------------------------ | -------------------------------------------------------- |
| 72 h sem resposta                    | Solicitação `OPEN`             | `EXPIRED`; o app sugere outros motoristas da mesma busca |
| 7 dias (configurável pelo motorista) | Proposta `SENT`                | `EXPIRED`; a oferta deixa de obrigar o motorista         |
| 48 h por parte                       | Contrato aguardando assinatura | `CANCELLED`; a proposta volta a poder ser reenviada      |
| 7 dias da assinatura                 | Direito de arrependimento      | Depois disso, cancelar segue a cláusula de rescisão      |
| 45 dias antes do fim                 | Contrato `ACTIVE`              | Motorista é lembrado de enviar a proposta de renovação   |

Os números são pontos de partida para validar nas entrevistas, não regras de negócio decididas.

## O que a solicitação e a proposta carregam

Tudo que varia de um contrato para outro nasce aqui como campo, nunca como texto livre: é isso que permite gerar o contrato sem ninguém redigir nada.

### Solicitação (responsável, uma tela pré-preenchida)

| Campo                                                  | De onde vem                                          | O que o motorista vê antes do aceite                                  |
| ------------------------------------------------------ | ---------------------------------------------------- | --------------------------------------------------------------------- |
| Dependentes incluídos (um ou mais)                     | Cadastro                                             | Primeiro nome e idade                                                 |
| Escola e turno escolar                                 | `dependent.school_id` e `shift`                      | Tudo                                                                  |
| Ponto de embarque e desembarque                        | Endereço do dependente; pode ser outro (casa da avó) | Só o bairro/quadra (nó da árvore) e a distância estimada até a escola |
| Agenda: pares (dia da semana, trecho)                  | Grade 5 × 2 na tela, padrão seg–sex ida e volta      | Tudo                                                                  |
| Horários de entrada e saída da escola                  | Pergunta (integral e contraturno mudam tudo)         | Tudo                                                                  |
| Início desejado                                        | Pergunta                                             | Tudo                                                                  |
| Observações (necessidade especial, irmãos, cadeirinha) | Texto livre                                          | Tudo                                                                  |

O "fluxo do aluno" levantado na ideia inicial (integral, volta com a van ou com o pai) é a agenda. Modelar como pares (dia, trecho) resolve o caso comum "às quartas a mãe busca" sem regra especial, e é exatamente a consulta que a trip vai fazer: "quem embarca nesta segunda, no trecho de ida?".

### Proposta (motorista, formulário com cartão no chat)

| Campo                                                    | Exemplo                                    | Vira qual parte do contrato        |
| -------------------------------------------------------- | ------------------------------------------ | ---------------------------------- |
| Itens por dependente: agenda aceita e janela de embarque | Seg–sex, ida 6h40–6h50 e volta 12h30       | Objeto e horários (cl. 1 e 2)      |
| Preço por item                                           | R$ 350 por mês; irmão R$ 300               | Preço (cl. 5)                      |
| Valor anual e número de parcelas                         | R$ 4.200 em 12 parcelas                    | Preço e cronograma (cl. 5, 9 e 10) |
| Dia de vencimento                                        | Dia 5                                      | Pagamento (cl. 6)                  |
| Período                                                  | 1 fev 2027 a 17 dez 2027                   | Vigência (cl. 4)                   |
| Taxa de reserva                                          | R$ 350, abatida da primeira parcela        | Inscrição (cl. 7)                  |
| Regra de reajuste                                        | Anual por IPCA, só na renovação            | Reajuste (cl. 8)                   |
| Rescisão                                                 | Aviso de 30 dias + multa de 10% do saldo   | Rescisão (cl. 4)                   |
| Tolerância de espera                                     | 5 min (já existe `wait_tolerance_minutes`) | Horário (cl. 2)                    |
| Veículo                                                  | Placa do veículo cadastrado                | Qualificação do veículo            |
| Validade                                                 | 7 dias                                     | Não entra; limita a oferta         |
| Mensagem                                                 | Texto livre                                | Não entra no contrato              |

Multa, reajuste e rescisão devem ser opções de um menu fechado (valores permitidos pelo template), não números livres. Assim o motorista escolhe, mas nunca escreve uma cláusula abusiva.

### Modelo de preço

O mercado cobra uma anuidade diluída em 12 parcelas, embora a escola funcione cerca de 10 meses. É por isso que o contrato de papel analisado obriga pagar julho e dezembro (cl. 9 e 10). A recomendação é modelar o valor anual e as parcelas explicitamente e mostrar a "mensalidade" como derivada: o cronograma calculado substitui o cheque pré-datado e deixa a regra visível em vez de escondida.

Sobre o "preço médio" na busca: antes de haver contratos não existe média, e a média de dois ou três contratos expõe o preço de clientes específicos. Usar "a partir de R$ X" declarado pelo motorista (o `base_price` que já existe) e só mostrar uma faixa calculada quando houver pelo menos 5 contratos ativos. Falta confirmar nas entrevistas se o motorista cobra por aluno, por trecho ou por distância; isso muda o formulário da proposta.

## O contrato

Ninguém "cria" o contrato: a plataforma o gera no aceite, a partir de um template versionado, dos dados cadastrais e dos termos da proposta aceita. Nenhuma das partes edita o texto.

### Quem preenche o quê

As duas opções levantadas na ideia inicial têm o mesmo defeito: deixam trabalho para depois do acordo.

| Opção                                                                                     | Problema                                                                                     |
| ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| A. Responsável aceita, depois envia os dados; motorista monta o contrato                  | Dois passos após o acordo; o motorista vira redator; o contrato fica esperando o responsável |
| B. Motorista cria o contrato com lacunas; responsável completa                            | Mesmo atrito; o que o responsável preenche muda o documento depois que o motorista o viu     |
| **C. Dados checados antes da solicitação; aceite gera o contrato completo (recomendada)** | O único trabalho depois do aceite é assinar                                                  |

Na opção C, o botão "Solicitar orçamento" verifica a prontidão contratual e, se faltar algo, pede antes de enviar. O que cada parte precisa ter:

| Parte       | Dados                                                                                      | Já existe?                                            |
| ----------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| Responsável | Nome, CPF, endereço, telefone, e-mail                                                      | Sim (`users` + endereço pessoal)                      |
| Dependente  | Nome completo, data de nascimento, escola, turno                                           | Sim; nascimento é opcional hoje e passa a ser exigido |
| Motorista   | Nome, CPF ou CNPJ, endereço, CNH categoria D, autorização/credencial de transporte escolar | Parcial: falta o número da autorização                |
| Veículo     | Placa, marca, modelo, ano, capacidade, autorização                                         | Parcial: falta a autorização do veículo               |

RG não é necessário: CPF identifica a parte. Deixá-lo de fora segue o princípio da necessidade da LGPD (art. 6º, III).

### Geração

- **Template em HTML → PDF na API.** A API já usa Thymeleaf para e-mails; um renderizador HTML→PDF (por exemplo OpenHTMLtoPDF) fecha o ciclo. Validar acentos, quebra de página e fonte ≥ 12 (CDC art. 54 §3º) no spike.
- **Três tipos de cláusula.** Fixas (texto jurídico, iguais para todos), parametrizadas (valores da proposta) e opcionais (só aparecem se o campo existir, como a taxa de reserva).
- **Template versionado.** `contract_template` com versão e data de publicação; o contrato guarda a versão usada. Mudar o template nunca muda contrato assinado.
- **Snapshot imutável.** O contrato guarda o JSON das partes, itens e termos, o PDF gerado e o SHA-256 dele. Nada é regenerado a partir do cadastro vivo; mudança é aditivo.
- **Resumo antes de assinar.** Uma tela em linguagem simples com valor, parcelas, vencimento, multa, reajuste e como cancelar, com as cláusulas que limitam direitos em destaque (CDC art. 54 §4º).

### Análise do contrato-base, cláusula a cláusula

**Em aberto na V2.** A análise fica como está até a revisão jurídica, que foi adiada. Se o template precisar existir antes disso, as recomendações desta tabela são a versão provisória mais segura.

| Cl. | O que diz                                                                                                                                                     | Risco | Recomendação                                                                                                                                                                                                               |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Transporte casa–escola e volta no horário normal; não responde por aluno dispensado fora do horário                                                           | Baixo | Manter; trechos e horários vêm da proposta                                                                                                                                                                                 |
| 2   | Aluno pronto 5 min antes; motorista não espera                                                                                                                | Baixo | Manter com a tolerância da proposta                                                                                                                                                                                        |
| 3   | Aluno obedece motorista e auxiliar; indisciplina é comunicada                                                                                                 | Baixo | Manter; a comunicação passa pelo chat                                                                                                                                                                                      |
| 4   | Contrato anual; cancelamento obriga a pagar duas mensalidades                                                                                                 | Alto  | Multa fixa que não depende do tempo restante tende a ser vista como desvantagem exagerada (CDC art. 51, IV). Trocar por aviso de 30 dias + multa proporcional ao saldo, e prever rescisão sem multa por falha do motorista |
| 5   | Valor total em N parcelas                                                                                                                                     | Baixo | Manter; vem da proposta                                                                                                                                                                                                    |
| 6   | Multa de 10% após o dia 5; suspensão após o dia 20; paga mesmo em greve                                                                                       | Alto  | Em relação de consumo a multa de mora não pode passar de 2% da prestação (CDC art. 52, §1º). Usar 2% + juros de 1% ao mês; suspensão só com aviso prévio; regra de greve explícita                                         |
| 7   | Inscrição nova paga uma mensalidade de referência                                                                                                             | Médio | Dizer se é abatida da anuidade e devolvida no arrependimento                                                                                                                                                               |
| 8   | Parcelas corrigidas a cada aumento de combustível ou custo fixo                                                                                               | Alto  | Variação unilateral de preço é cláusula abusiva expressa (CDC art. 51, X). Reajuste anual por índice, ou aditivo com direito de rescindir sem multa                                                                        |
| 9   | Quem usa até junho paga julho; até novembro paga dezembro                                                                                                     | Médio | Lógica legítima de anuidade diluída; deixar explícita no cronograma de parcelas                                                                                                                                            |
| 10  | Julho pago com junho e dezembro/reserva com novembro, em cheque pré-datado                                                                                    | Médio | Tirar o cheque; cronograma calculado pela plataforma                                                                                                                                                                       |
| 11  | Responsável avisa ausência com antecedência                                                                                                                   | Baixo | Vira funcionalidade (ausência no app)                                                                                                                                                                                      |
| 12  | Veículo em bom estado; não transita por vias perigosas; aluno espera em local seguro                                                                          | Médio | Manter; ponto alternativo combinado na proposta; prever troca de veículo com aviso                                                                                                                                         |
| —   | Falta: qualificação e autorizações do veículo e do condutor, dados do dependente, LGPD, arrependimento, aceite da assinatura eletrônica, foro, papel da Vanep | —     | Incluir no template novo                                                                                                                                                                                                   |

O contrato de papel analisado não é um mau ponto de partida: ele mostra como o mercado realmente funciona. O problema é que, quando a Vanep o transforma em template, ela passa a ser a autora das cláusulas que todos os motoristas vão usar.

## Assinatura

**Decidido na V2: Autentique.** A Vanep usa a Autentique como primeira implementação da `ContractSignatureGateway`. A comparação completa com ZapSign, Clicksign, D4Sign e o caminho próprio está no documento [Spike — Assinatura de Contratos](https://claude.ai/code/artifact/20506a8c-9240-4b4e-b3f8-3206b6a5c5e7). O que pesou:

- Única dos quatro provedores com API no plano gratuito (10 documentos por mês), o que basta para desenvolver e rodar o piloto.
- Cobrança por uso, de centavos por contrato, e plano pago sem multa de cancelamento, o que encaixa na sazonalidade das matrículas.
- 1 a 2 semanas de integração, contra 5 a 8 semanas para construir um selo próprio equivalente.

Antes de integrar, confirmar por escrito com a Autentique como a API é cobrada no plano pago, porque duas páginas oficiais descrevem isso de jeitos diferentes. As outras perguntas estão no documento de assinatura.

O restante desta seção é a análise da V1, mantida como registro.

A recomendação é um provedor externo de assinatura eletrônica já no MVP, atrás de uma interface `ContractSignatureGateway`. Na tabela pública da Autentique, um contrato com dois signatários custa cerca de R$ 0,09 por e-mail ou R$ 0,30 por WhatsApp.

| Opção                                                         | Validade                                                                                                                       | Esforço                                                             | Custo por contrato                                                                                                                                                          | Experiência               | Veredito        |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- | --------------- |
| Aceite nativo (clique + código por SMS + trilha de auditoria) | Válida entre as partes que a aceitam (MP 2.200-2, art. 10 §2º), mas a prova é produzida pela própria Vanep, que ganha comissão | Alto: código, trilha, hash, custódia e certificado de conclusão     | Custo do SMS                                                                                                                                                                | A melhor, tudo no app     | Não no MVP      |
| Provedor externo (Autentique, ZapSign, Clicksign, D4Sign)     | Válida; integridade conferida por terceiro, o que permite dispensar testemunhas no título executivo (CPC art. 784 §4º)         | Médio-baixo: API, webhook e link aberto em WebView                  | Autentique: R$ 0,06 por documento + R$ 0,013 por assinatura por e-mail ou R$ 0,12 por WhatsApp; ZapSign: plano API de 80, 200 ou 500 documentos por mês, preço sob consulta | Boa, com WebView          | **Recomendado** |
| gov.br                                                        | Assinatura avançada                                                                                                            | Sem integração para empresa privada; o usuário sobe o PDF no portal | Grátis                                                                                                                                                                      | Ruim, sai do app          | Não             |
| Certificado ICP-Brasil                                        | Qualificada                                                                                                                    | Médio                                                               | O usuário compra o certificado                                                                                                                                              | Inviável para responsável | Não             |

Por que não nativo, mesmo sendo mais simples de entender: numa disputa, a trilha de auditoria gerada pela parte que lucra com o contrato vale menos do que a de um terceiro neutro. E o "simples" esconde código que a Vanep teria de manter para sempre (envio de código, custódia do PDF, certificado de conclusão), por uma economia de centavos por contrato.

O clique de aceite da proposta continua nativo. Ele é a prova de que houve acordo sobre aquela versão; a assinatura formaliza o documento que nasceu dele.

Fluxo técnico:

1. A API gera o PDF, cria o documento no provedor com os dois signatários em ordem (responsável, depois motorista) e guarda o id externo.
2. O app abre o link de assinatura num WebView. O responsável se autentica por e-mail ou WhatsApp; o motorista já teve identidade verificada na aprovação.
3. O webhook "documento assinado" baixa o PDF assinado e a trilha, grava no MinIO e move o contrato para `SIGNED`. O webhook precisa ser idempotente, com consulta periódica de reserva caso ele se perca.

O custo do provedor é a dependência e, na Autentique, um compromisso mensal mínimo do plano. A mitigação é a interface: o PDF é gerado pela Vanep, então trocar de provedor é trocar um adaptador. Pelo mesmo motivo, não há razão para usar o editor de templates do provedor.

## Pontos jurídicos

**Em aberto na V2.** A revisão jurídica foi adiada. A tabela de regras fica como está; as perguntas no fim ganharam uma recomendação provisória, que vale até o advogado responder.

A relação responsável–motorista é de consumo, então o CDC governa o contrato; e a Vanep, que escreve o template e cobra comissão, dificilmente fica totalmente fora da cadeia de responsabilidade.

| Regra                                                             | O que diz                                                                                                                  | Efeito no produto                                                                                           |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Oferta vincula (CDC art. 30)                                      | Informação suficientemente precisa obriga o fornecedor e integra o contrato                                                | Proposta tem validade explícita; retirar só antes do aceite; o "a partir de" da busca não pode ser enganoso |
| Arrependimento (CDC art. 49)                                      | 7 dias a partir da assinatura para contratação fora do estabelecimento; o TJDFT já aplicou a contrato fechado por WhatsApp | Estado e botão de arrependimento; devolver a taxa de reserva                                                |
| Cláusulas abusivas (CDC art. 51, IV e X)                          | Nulas se geram desvantagem exagerada ou permitem variação unilateral de preço                                              | Template revisado; multa e reajuste só por menu fechado                                                     |
| Multa de mora (CDC art. 52, §1º)                                  | Não pode passar de 2% da prestação                                                                                         | Template com 2% + juros                                                                                     |
| Contrato de adesão (CDC art. 54, §3º e §4º)                       | Fonte ≥ 12; cláusulas que limitam direitos em destaque                                                                     | PDF com fonte ≥ 12; tela de resumo com destaques                                                            |
| Solidariedade (CDC art. 7º, par. único, e art. 25, §1º)           | Todos os que causam o dano respondem juntos                                                                                | Quanto mais a Vanep participa (template, cobrança, comissão), mais forte o argumento para incluí-la         |
| Dados de crianças (LGPD art. 14, §1º)                             | Melhor interesse da criança; consentimento específico e em destaque de um dos pais ou responsável                          | Consentimento explícito ao cadastrar o dependente e ao compartilhar dados com o motorista                   |
| Necessidade (LGPD art. 6º, III)                                   | Tratar só o mínimo necessário                                                                                              | Solicitação sem endereço exato nem nome completo; sem RG no contrato                                        |
| Condutor escolar (CTB art. 138)                                   | 21 anos, categoria D, sem mais de uma infração gravíssima em 12 meses, curso de 50 h, certidão criminal                    | Documentos vencidos bloqueiam novas propostas e avisam os contratos ativos                                  |
| Veículo escolar (CTB art. 136)                                    | Autorização do órgão de trânsito e inspeção semestral                                                                      | Autorização e validade da inspeção no cadastro do veículo e no contrato                                     |
| Assinatura eletrônica (MP 2.200-2, art. 10 §2º; CPC art. 784 §4º) | Válida se admitida pelas partes; com integridade conferida por provedor, dispensa testemunhas no título executivo          | Cláusula em que as partes aceitam a assinatura eletrônica                                                   |

Perguntas para o advogado, com a recomendação provisória de cada uma. Em todas, escolhi o lado mais conservador para o responsável: se o advogado afrouxar depois, o produto só fica mais simples; o contrário exigiria refazer contratos já assinados.

| #   | Pergunta                                                                                         | Recomendação provisória                                                                                                                                                           | Por quê                                                                                                                                                                              |
| --- | ------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | A Vanep aparece no contrato ou só nos Termos de Uso? Qual o risco de responsabilidade solidária? | A Vanep não é parte. Uma cláusula curta diz que ela fornece a plataforma e não presta o transporte; o detalhe fica nos Termos de Uso                                              | Deixa claro quem presta o serviço sem prometer isenção total, que o CDC não aceitaria. Cobrar a comissão do motorista, e não da família, também afasta a Vanep da relação de consumo |
| 2   | Qual multa rescisória é defensável na anuidade diluída?                                          | Aviso prévio de 30 dias (paga-se a parcela do aviso) + multa de 10% do saldo restante, limitada a uma parcela. Sem multa quando a causa é falha do motorista ou documento vencido | Proporcional ao tempo que falta, o que resolve o problema da cl. 4. O teto de uma parcela mantém a multa abaixo das duas mensalidades do contrato de papel                           |
| 3   | O arrependimento de 7 dias vale se o serviço já começou?                                         | Vale sempre. Cobra-se só o proporcional dos dias rodados e devolve-se a taxa de reserva                                                                                           | A decisão do TJDFT não distingue serviço parcialmente prestado. No produto, sugerir início pelo menos 7 dias depois da assinatura evita o caso na maioria dos contratos              |
| 4   | Há regra local (DF e próximas praças) que precise entrar no contrato?                            | Tratar como configuração por praça: documentos exigidos por cidade ou estado, e uma cláusula genérica em que o motorista declara cumprir a regulamentação local                   | Evita um template por cidade. Antes de abrir o DF, conferir as exigências do DETRAN-DF (ainda não verificado)                                                                        |
| 5   | O consentimento parental da LGPD pode ser colhido só no cadastro?                                | Colher em três momentos: no cadastro do dependente, no envio da solicitação (dados mínimos) e no aceite (dados completos para aquele motorista)                                   | São caixas de seleção em telas que já existem, e deixam registrado o consentimento específico por motorista que o art. 14 pede                                                       |

## Carteira própria do motorista

Decisão da V2 (D12): o motorista cadastra os alunos que já tem como alunos não vinculados (sem vínculo com uma família no app, só para a rota), e convida as famílias para o fluxo normal. Não existe contrato externo: quem entra pelo convite faz o contrato digital, como qualquer cliente.

**Dois tipos de aluno na rota.**

|                    | Aluno não vinculado                                                           | Aluno vinculado (dependente com contrato)           |
| ------------------ | ----------------------------------------------------------------------------- | --------------------------------------------------- |
| Quem cadastra      | O motorista, sozinho                                                          | O responsável, pelo fluxo normal                    |
| Dados              | Poucos: nome, escola, endereço de embarque, turno, dias e trechos, observação | Completos, com conta e consentimento do responsável |
| Vínculo e contrato | Nenhum                                                                        | `client_driver` + contrato digital assinado         |
| Funciona para      | Ordem da rota e checklist do motorista                                        | Rota, checklist e todas as funções dos pais         |
| Avisos aos pais    | Nenhum; o motorista avisa pelo WhatsApp como hoje                             | Embarque, chegada, localização, chat, ausência      |
| Comissão           | Nenhuma; paga pela mensalidade                                                | R$ 5 (carteira própria) ou R$ 10–15 (busca)         |

**Como funciona.**

1. **O motorista monta a rota no primeiro dia** cadastrando todos como alunos não vinculados. A rota e o checklist ficam completos sem depender de nenhum pai.
2. **Cada aluno não vinculado tem um botão "convidar família".** Ele abre o WhatsApp com a mensagem e o link prontos. O motorista também pode mandar o link da van no grupo de pais.
3. **O responsável faz o fluxo normal:** cria a conta, cadastra os dependentes, dá o consentimento e manda a solicitação. O vínculo nasce com origem `DRIVER_INVITE`. O motorista responde com a proposta, o responsável aceita e assina, e o contrato digital fica ativo.
4. **Quando o dependente real é vinculado, o app pergunta** "Esse é o João que você cadastrou?". Um toque apaga o aluno não vinculado e mantém a posição dele na rota. Sem isso, o aluno apareceria duas vezes no checklist.

**Nenhuma trava nos alunos não vinculados.** Nem limite de quantidade, nem data de término. Três motivos:

- **O valor já empurra para o contrato.** O aluno não vinculado só dá a rota. O que o motorista mais ganha com a Vanep (menos mensagens de "embarcou" no WhatsApp, fim do "onde está a van?", falta avisada pelo app, contrato organizado e, depois, cobrança pelo Pagar.me) só aparece quando o pai entra.
- **Trava não gera contrato, gera cancelamento.** Quem fica preso por uma trava é o motorista que não vê valor nas funções dos pais. Ele não faria contrato de qualquer jeito: iria embora e deixaria de pagar a mensalidade.
- **Uma data de término seria burlada em um minuto,** apagando e recriando a parada. Só traria código para manter.

**O aluno não vinculado não é de graça: ele é pago pela mensalidade.** Se a mensalidade for por faixa de alunos, os alunos não vinculados contam na faixa. O único lugar onde um limite faz sentido é um eventual plano gratuito (por exemplo, até 10 alunos não vinculados), como motivo para assinar.

**Empurrar em vez de travar.** A tela inicial do motorista mostra quantos alunos têm os pais no app ("12 de 30"), e cada aluno não vinculado mantém o botão de convite à vista. A conversão de aluno não vinculado em contrato é medida por motorista; se for baixa, o problema é de preço ou de valor, e se corrige nas entrevistas, não com regra.

**O que se aceita com isso.** "Toda renovação passa pela Vanep" é uma meta comercial, não uma regra do sistema. Alguns motoristas vão ficar só com alunos não vinculados, e deles a Vanep recebe a mensalidade.

**Dados e LGPD.** O [Enunciado CD/ANPD nº 1/2023](https://bibliotecadigital.mj.gov.br/bitstream/1/10215/2/Enunciado_ANPD_2023_1.html) admite tratar dados de crianças com outras bases legais além do consentimento, desde que prevaleça o melhor interesse. O motorista é o controlador dos dados dos alunos não vinculados, com base na execução do contrato de transporte, e a Vanep é a operadora. Isso pede uma cláusula nos termos de uso do motorista e dados mínimos; é mais um item para o advogado.

## Depois da assinatura

O contrato de transporte escolar vive um ano inteiro e muda no meio do caminho; se o módulo parar na assinatura, cada mudança volta para o WhatsApp.

| Evento                                                          | Quem dispara                                    | O que acontece                                                                                      |
| --------------------------------------------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Arrependimento (até 7 dias da assinatura)                       | Responsável                                     | Contrato `CANCELLED`, taxa de reserva devolvida, vínculo volta a `INACTIVE`                         |
| Data de início                                                  | Sistema                                         | Contrato `ACTIVE`, vínculo `ACTIVE`, dependentes entram na lista de passageiros                     |
| Mudança de endereço, escola, turno, agenda ou valor; irmão novo | Qualquer parte pede; o motorista propõe         | Nova proposta ligada ao contrato → novo contrato assinado substitui o anterior a partir de uma data |
| Troca de veículo                                                | Motorista                                       | Aviso ao responsável; veículo autorizado não exige aditivo, se a cláusula previr                    |
| Atraso de pagamento                                             | Motorista marca (ou a cobrança, quando existir) | Aviso; após o prazo da cláusula, `SUSPENDED` e alunos saem da lista; volta ao regularizar           |
| Ausência avisada (cl. 11)                                       | Responsável                                     | Registro por item, data e trecho; o aluno aparece como ausente no checklist                         |
| Documento do motorista ou do veículo vence                      | Sistema                                         | Alerta aos dois; sem regularização no prazo, o responsável pode rescindir sem multa                 |
| Rescisão                                                        | Qualquer parte                                  | Aviso prévio, cálculo da multa pela cláusula, `TERMINATED` ao fim do aviso                          |
| Fim do período                                                  | Sistema                                         | `ENDED`; 45 dias antes, o motorista envia a proposta de renovação já preenchida                     |
| Bloqueio                                                        | Qualquer parte                                  | Só sem contrato ativo; grava `blocked_by`; só quem bloqueou desbloqueia                             |

Aditivo e renovação reutilizam o mesmo motor de proposta e contrato: uma proposta com `contract_id` de origem. Não é preciso um segundo fluxo.

## Riscos de produto

A desintermediação é o risco que decide o negócio: a comissão só existe com contrato ativo, e o chat é o lugar mais fácil de trocar WhatsApp e fechar por fora.

| Risco                                         | Chance / impacto | Mitigação                                                                                                                                                                                                                                                                                  |
| --------------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Fechar por fora depois de se conhecer no app  | Alta / alto      | Não dá para impedir; dá para tornar caro sair. Contrato formal, rastreamento, lista de passageiros e controle de inadimplência só existem dentro. Aviso leve quando o chat detecta telefone antes do contrato, sem bloquear. Medir solicitações que morrem logo depois da troca de contato |
| Motorista não responde                        | Alta / alto      | Expira em 72 h com sugestão de outros motoristas; taxa de resposta no perfil numa versão seguinte                                                                                                                                                                                          |
| Responsável negocia com vários e fecha com um | Alta / baixo     | Permitido, até 5 solicitações abertas; ao assinar, as outras que incluem os mesmos dependentes são retiradas com aviso                                                                                                                                                                     |
| Spam de solicitações                          | Média / médio    | Limite de abertas, expiração, e `is_available` como "não estou aceitando alunos"                                                                                                                                                                                                           |
| Corrida pela última vaga                      | Média / médio    | O motorista assina por último e confirma a vaga                                                                                                                                                                                                                                            |
| Cadastro muda depois do contrato              | Alta / alto      | Contrato é snapshot; editar dependente contratado abre pedido de aditivo                                                                                                                                                                                                                   |
| Template com cláusula abusiva                 | Média / alto     | Revisão jurídica e menus fechados para multa e reajuste                                                                                                                                                                                                                                    |
| Assédio ou conteúdo impróprio no chat         | Baixa / alto     | Denúncia e bloqueio com `blocked_by`; o chat é só entre adultos (responsável e motorista)                                                                                                                                                                                                  |
| Chat (#161) atrasar                           | Média / médio    | O módulo funciona por REST e push; o chat só exibe os cartões                                                                                                                                                                                                                              |

### Modelo de receita: mensalidade + comissão pela origem

Decisão da V2 (D11): só o motorista paga; a família não paga nada à Vanep.

| Cobrança                   | Valor                                       | Sobre o quê                                         |
| -------------------------- | ------------------------------------------- | --------------------------------------------------- |
| Mensalidade do motorista   | A definir; plano mensal, semestral ou anual | A ferramenta, incluindo os alunos não vinculados    |
| Comissão, carteira própria | Cerca de R$ 5                               | Cada contrato de vínculo com origem `DRIVER_INVITE` |
| Comissão, cliente da busca | R$ 10 a 15                                  | Cada contrato de vínculo com origem `CLIENT_SEARCH` |

**A comissão segue a origem do vínculo, não o tipo de contrato.** A origem é gravada quando o par nasce, na primeira solicitação ou no primeiro convite, e nunca muda. Um cliente da carteira própria paga R$ 5 em todos os contratos, inclusive renovações; um cliente que veio da busca paga a comissão cheia em qualquer contrato, inclusive convite, renovação e aditivo. Assim trazer a carteira para o contrato digital custa pouco ao motorista, e a Vanep cobra mais só pelo cliente que ela trouxe.

**A brecha "convidar todo mundo" está fechada pelo desenho.** A busca não mostra o telefone do motorista antes do contrato, então quem o encontra na Vanep só consegue falar com ele por solicitação, e a origem `CLIENT_SEARCH` fica gravada antes de qualquer troca de WhatsApp. O que escapa é o cliente que conheceu o motorista fora da Vanep, e esse a Vanep de fato não trouxe.

**Cobrança.** Uma fatura ao motorista, paga por PIX, com a mensalidade do período e as comissões do mês, calculadas direto dos contratos. Se ele não pagar, perde o envio de propostas novas, mas os contratos ativos continuam: a família não é punida pela dívida do motorista.

**Rever quando a cobrança passar pelo Pagar.me.** A comissão pode virar uma porcentagem descontada de cada pagamento, e a diferença entre as duas origens pode deixar de fazer sentido.

| Ainda a definir                                               | Como fecha                                                                                                     |
| ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Valor da mensalidade e dos planos                             | Entrevistas com motoristas (E1)                                                                                |
| Valores exatos das comissões (R$ 5 e R$ 10–15 são referência) | E1, perguntando sobre a conta do mês inteiro, não sobre o valor solto                                          |
| Periodicidade e unidade da comissão                           | Recomendação: todo mês em que o contrato está ativo, por dependente                                            |
| Plano gratuito para o motorista                               | Decidir antes do lançamento; se existir, é o lugar de um limite de alunos não vinculados (por exemplo, até 10) |

## Impacto nos fluxos atuais

O maior retrabalho está na operação diária: a trip e o checklist passam a derivar os passageiros dos contratos ativos. O resto são ajustes pontuais em módulos que já existem.

| Área                        | Hoje                                                              | Mudança                                                                                                                                                                                                                                                                                                                              | Tamanho          |
| --------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------- |
| Trip e checklist (#151)     | Trip sem passageiros; Q1 (integral = uma ou duas trips) em aberto | Passageiros do dia = itens de contratos `ACTIVE` com aquele (dia, trecho) no turno da trip. Isso responde a Q1: a trip é do turno do motorista, e um aluno integral aparece na trip da manhã (ida) e na da tarde (volta). Ausência pendura em item + data + trecho. Alunos não vinculados entram na mesma lista, sem avisos aos pais | Grande           |
| `client_driver`             | Criado pelo admin; status sem transições                          | Nasce `PENDING` na primeira solicitação ou convite; `ACTIVE` com contrato ativo; `INACTIVE` quando nada está em andamento. A mesma linha é reutilizada (responde Q1). Ganha `blocked_by`, e solicitação para par bloqueado é recusada (responde Q3)                                                                                  | Médio            |
| Avaliações                  | Qualquer vínculo avalia                                           | Exigir que o vínculo já tenha tido contrato ativo; sem isso, uma conversa de 5 minutos vira nota pública                                                                                                                                                                                                                             | Pequeno, urgente |
| `dependent`                 | `shift` único; edição e exclusão livres                           | `shift` continua sendo o turno escolar; agenda e horários vão para o item do contrato. Editar endereço ou escola de dependente contratado avisa o motorista e abre aditivo; excluir com contrato ativo → 409. `birth_date` passa a ser exigido para contratar                                                                        | Médio            |
| Chat (#161)                 | Não existe                                                        | Conversa pendura em `client_driver`; mensagens tipadas (`TEXT`, `SYSTEM`, `REQUEST`, `PROPOSAL`, `CONTRACT`) com referência ao token do objeto                                                                                                                                                                                       | Médio (novo)     |
| Busca                       | Devolve `basePrice`; botão genérico                               | Botão "Solicitar orçamento"; filtro por turno; esconder quem não aceita alunos; exibir "a partir de"                                                                                                                                                                                                                                 | Pequeno          |
| `driver`                    | `base_price`, `is_available`, `work_days` livre                   | `is_available` passa a significar "aceitando novos alunos"; documento vencido bloqueia novas propostas; falta o número da autorização escolar                                                                                                                                                                                        | Pequeno          |
| `vehicle`                   | `capacity` e placa                                                | Autorização e validade da inspeção semestral; contrato referencia o veículo; vagas por turno = capacidade − itens ativos (informativo)                                                                                                                                                                                               | Pequeno          |
| Notificações (FCM)          | —                                                                 | Solicitação recebida, proposta recebida, ajuste pedido, contrato para assinar, assinado, prazo acabando, arrependimento, renovação                                                                                                                                                                                                   | Médio            |
| Onboarding (`pendingSteps`) | `PERSONAL_ADDRESS`, `SERVICE_AREA`                                | Passo de prontidão contratual exigido antes da primeira solicitação, não no cadastro                                                                                                                                                                                                                                                 | Pequeno          |
| Assistente                  | Vínculo com o motorista                                           | Opcional: vê a lista de passageiros derivada dos contratos                                                                                                                                                                                                                                                                           | Pequeno          |
| Admin (Next.js)             | CRUDs                                                             | Consulta de contratos e eventos, disputas, publicação de versão do template                                                                                                                                                                                                                                                          | Médio            |
| Termos de Uso e Privacidade | —                                                                 | Papel da Vanep, compartilhamento entre as partes, consentimento parental                                                                                                                                                                                                                                                             | Jurídico         |

A mudança nas avaliações precisa entrar junto com a fase 2: no momento em que o vínculo começar a nascer por solicitação, a regra atual deixa qualquer curioso avaliar.

## Modelo de dados proposto

Tudo pendura no `client_driver`, como o hub já previa. O contrato separa o acordo do documento: o núcleo (`contract`, `contract_item`, agenda e alunos não vinculados) nasce completo na fase 1, criado pelo admin e pelo seeder para destravar a rota e o checklist (#151). As tabelas do núcleo nunca ganham coluna nova: o que as fases seguintes trazem chega como tabela nova pendurada no contrato. A exceção é o documento, que nasce na fase 3 e recebe as colunas da assinatura na fase 4.

| Tabela                       | Pendura em                         | Colunas principais                                                                                                                                                                                                                                                                                  | Fase                                                                                                                                                                                                                                                                     |
| ---------------------------- | ---------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `contract`                   | `client_driver`                    | `status` (enum completo), `starts_on`, `ends_on`, `annual_amount`, `installments`, `due_day`, `supersedes_contract_id` (só em aditivo)                                                                                                                                                              | 1. Índice parcial: um `ACTIVE` por `client_driver`                                                                                                                                                                                                                       |
| `contract_item`              | `contract`, `dependent`            | `school_id`, endereço de embarque copiado (rua, número, complemento, `district_id`, ponto no mapa), preço, `schedule_id`                                                                                                                                                                            | 1. Fonte da lista de passageiros                                                                                                                                                                                                                                         |
| `schedule`                   | —                                  | Só identidade; cada dono aponta para o seu                                                                                                                                                                                                                                                          | 1                                                                                                                                                                                                                                                                        |
| `schedule_slot`              | `schedule`                         | `weekday`, `leg` (`OUTBOUND`, `RETURN`), `shift` (turno da trip em que o horário acontece), `window_start`, `window_end`                                                                                                                                                                            | Fase 1 — índices na tabela abaixo                                                                                                                                                                                                                                        |
| `unlinked_passenger`         | `driver`                           | Nome do aluno, `school_id`, `address_id` (endereço de embarque na tabela `address`), turno, observação, `schedule_id`                                                                                                                                                                               | 1. Sem vínculo, sem contrato, sem data de término                                                                                                                                                                                                                        |
| `service_request`            | `client_driver`                    | `status`, `desired_start_date`, `notes`, `expires_at`                                                                                                                                                                                                                                               | 2. Uma aberta por par                                                                                                                                                                                                                                                    |
| `service_request_item`       | `service_request`, `dependent`     | `school_id`, nó do embarque (`district_id`), horários da escola, `schedule_id`                                                                                                                                                                                                                      | 2. Sem endereço exato                                                                                                                                                                                                                                                    |
| `proposal`                   | `client_driver`, `service_request` | `version`, `parent_proposal_id`, `status`, `valid_until`, `start_date`, `end_date`, `annual_amount`, `installments`, `due_day`, `enrollment_fee`, `adjustment_policy`, `cancellation_policy`, `vehicle_id`, `source_contract_id` (aditivo ou renovação), `resulting_contract_id` (só quando aceita) | 2. Imutável; nova versão substitui                                                                                                                                                                                                                                       |
| `proposal_item`              | `proposal`, `dependent`            | Preço, janela de embarque, `schedule_id`                                                                                                                                                                                                                                                            | 2                                                                                                                                                                                                                                                                        |
| `contract_template`          | —                                  | `contract_type` (contrato, aditivo, renovação), `version`, `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`), `body`, `published_at`                                                                                                                                                                      | Fase 3 — modelo publicado nunca é editado: mudar o texto = nova versão em rascunho, publicar, arquivar a anterior. Só `DRAFT` é editável. Índice único parcial: um `PUBLISHED` por `contract_type`. Garante que `template_version` diga de qual texto cada contrato saiu |
| `contract_document`          | `contract` (1:1)                   | Fase 3: `template_version`, `terms_snapshot` (jsonb), `pdf_media_id`, `pdf_sha256`. Fase 4 acrescenta: `provider`, `provider_document_id`, `signed_pdf_media_id`, `client_signed_at`, `driver_signed_at`                                                                                            | 3 e 4. Contrato criado pelo admin não tem linha aqui. A evidência de cada assinatura está na trilha da Autentique, dentro do PDF assinado                                                                                                                                |
| `contract_event`             | `contract`                         | `type`, `actor_user_id`, `payload`, `created_at`                                                                                                                                                                                                                                                    | 8. Histórico do contrato, inclusive eventos por signatário (link enviado, visualizou, recusou). Sem soft delete                                                                                                                                                          |
| `conversation` / `message`   | `client_driver`                    | `type`, `ref_type`, `ref_token`                                                                                                                                                                                                                                                                     | 6 (#161)                                                                                                                                                                                                                                                                 |
| `client_driver` (altera)     | —                                  | + `origin`, `blocked_by`, `blocked_at`                                                                                                                                                                                                                                                              | 2                                                                                                                                                                                                                                                                        |
| `vehicle`, `driver` (altera) | —                                  | + autorização escolar e validade                                                                                                                                                                                                                                                                    | 2                                                                                                                                                                                                                                                                        |

Sem tabela por signatário: o contrato tem sempre dois, em ordem fixa (D7). Se um dia houver mais (os dois responsáveis, por exemplo), aí entra uma tabela `contract_signature`.

**Índices.** Cada índice vem de uma consulta concreta das fases. Seguem o padrão do repo: únicos parciais com `where deleted_at is null` nas tabelas com soft delete, e um índice único de `token` em toda tabela exposta pela API (omitido abaixo). Como a suíte roda em H2, os índices parciais não são exercitados nos testes; cada change confere manualmente no PostgreSQL, como foi feito na trip.

| Tabela                 | Índice                                                                  | Para quê                                                                                                              | Fase |
| ---------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ---- |
| `contract`             | Único `(client_driver_id) where status = 'ACTIVE'`                      | Um contrato ativo por par                                                                                             | 1    |
| `contract`             | `(client_driver_id)`                                                    | Contratos de um par (histórico, telas do responsável e do motorista)                                                  | 1    |
| `contract`             | `(starts_on) where status = 'SIGNED'`                                   | Job que ativa contratos na data de início                                                                             | 1    |
| `contract`             | `(ends_on) where status = 'ACTIVE'`                                     | Job de encerramento e lembrete de renovação 45 dias antes                                                             | 1    |
| `contract_item`        | `(contract_id)`                                                         | Itens de um contrato                                                                                                  | 1    |
| `contract_item`        | `(dependent_id)`                                                        | Motorista de cada dependente; bloquear exclusão de dependente contratado                                              | 1    |
| `schedule_slot`        | Único `(schedule_id, weekday, leg)`                                     | Impede o mesmo dia e trecho duas vezes na agenda                                                                      | 1    |
| `schedule_slot`        | `(schedule_id, weekday, shift)`                                         | Passageiros da trip: a consulta parte do motorista e chega na agenda, então o índice começa pela agenda, não pelo dia | 1    |
| `unlinked_passenger`   | `(driver_id)`                                                           | Alunos não vinculados de um motorista                                                                                 | 1    |
| `service_request`      | Único `(client_driver_id) where status = 'OPEN'`                        | Uma solicitação aberta por par                                                                                        | 2    |
| `service_request`      | `(expires_at) where status = 'OPEN'`                                    | Job de expiração em 72 h                                                                                              | 2    |
| `service_request_item` | `(service_request_id)`                                                  | Itens de uma solicitação                                                                                              | 2    |
| `service_request_item` | `(dependent_id)`                                                        | Retirar as solicitações abertas de um dependente quando ele é contratado                                              | 2    |
| `proposal`             | Único `(client_driver_id) where status = 'SENT'`                        | Uma proposta vigente por par: garante no banco que só a última versão pode ser aceita                                 | 2    |
| `proposal`             | `(client_driver_id, created_at)`                                        | Cadeia de versões no cartão do chat                                                                                   | 2    |
| `proposal`             | `(valid_until) where status = 'SENT'`                                   | Job de expiração em 7 dias                                                                                            | 2    |
| `proposal`             | Único `(resulting_contract_id) where resulting_contract_id is not null` | Uma proposta gera no máximo um contrato                                                                               | 2    |
| `proposal`             | `(source_contract_id)`                                                  | Aditivos e renovações de um contrato                                                                                  | 2    |
| `proposal_item`        | `(proposal_id)`                                                         | Itens de uma proposta                                                                                                 | 2    |
| `client_driver`        | Já tem `(client_id)` e `(driver_id)` (V36)                              | Vale conferir se um `(driver_id, status)` ajuda na consulta de passageiros                                            | 2    |
| `contract_template`    | Único `(contract_type) where status = 'PUBLISHED'`                      | Um modelo publicado por tipo                                                                                          | 3    |
| `contract_template`    | Único `(contract_type, version)`                                        | Versão não se repete                                                                                                  | 3    |
| `contract_document`    | Único `(contract_id)`                                                   | Relação 1:1 com o contrato                                                                                            | 3    |
| `contract_document`    | Único `(provider_document_id) where provider_document_id is not null`   | O webhook da Autentique chega com o id dela; é a busca de toda notificação                                            | 4    |
| `contract_event`       | `(contract_id, created_at)`                                             | Linha do tempo do contrato, em ordem                                                                                  | 8    |
| `conversation`         | Único `(client_driver_id)`                                              | Uma conversa por par                                                                                                  | 6    |
| `message`              | `(conversation_id, created_at)`                                         | Mensagens paginadas da conversa                                                                                       | 6    |

**Regras do modelo:**

- **Acordo separado do documento.** `contract` guarda o acordo; PDF, envio ao provedor e assinaturas ficam no documento, uma tabela pendurada nele. Um contrato criado pelo admin (fase 1, correções) é um contrato sem documento, não um contrato com colunas vazias.
- **A ligação fica na tabela que nasce depois.** A proposta aponta para o contrato que gerou (`resulting_contract_id`), e não o contrato para a proposta.
- **Agenda sem chave estrangeira polimórfica.** Cada dono (`contract_item`, `unlinked_passenger`, `proposal_item`, `service_request_item`) aponta para o seu `schedule`, e `schedule_slot` pertence ao `schedule`.
- `client_driver.origin` (`CLIENT_SEARCH`, `DRIVER_INVITE`): gravada quando o par nasce e nunca alterada. É ela que define o valor da comissão, então não pode ter caminho de atualização, nem no `PATCH` do admin.
- Passageiros da trip = itens de contratos `ACTIVE` + alunos não vinculados do motorista, numa consulta única (`PassengerQueryService`) que diz de qual fonte cada um veio. Aluno não vinculado nunca dispara aviso a responsável.
- Não existe `contract.source`: o contrato digital é o único tipo.

Duas regras que o banco sozinho não garante e o serviço precisa garantir: um dependente não pode ter o mesmo (dia, trecho) em dois contratos ativos, e uma proposta só pode ser aceita se for a versão vigente da sua cadeia. A segunda vale um teste de concorrência, como o `start` da trip.

Nomes em inglês, como manda a constituição (regra 48); os estados de exibição em português ficam nas strings do app.

## Mudanças em estruturas existentes

O módulo de contratos define as regras; onde o código atual contradiz o modelo, o código muda, não o modelo. A análise do código (03/10/2026) achou 6 estruturas erradas, 8 que precisam de ajuste e 4 que ficam como estão. Cada item entra na fase indicada, como parte do change daquela fase.

### Erradas — precisam mudar

| #   | Estrutura atual                                                                                                                            | Problema                                                                                                                                                                                | Mudança                                                                                                                                                                                            | Fase                                                        |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| E1  | Status de `client_driver` editável por PATCH pelas partes (`@sec.isClientDriverLinkParty`) e pelo admin; o create aceita `status` no corpo | O cliente pode se colocar como ACTIVE sem nunca ter contratado. O status do vínculo passa a ser consequência do contrato (ACTIVE na assinatura, ENDED quando o último contrato termina) | Tirar `status` do `ClientDriverUpdateRequestDTO` e do create; transições só dentro do service de contrato; admin ganha endpoint de correção com motivo registrado                                  | Fase 1 (status derivado) e Fase 2 (criação pelo fluxo)      |
| E2  | POST genérico de `client_driver` para quem tem `create_client_driver`                                                                      | O vínculo nasce só por busca + proposta (`CLIENT_SEARCH`) ou convite (`DRIVER_INVITE`); sem origem não há como calcular a comissão                                                      | Remover o POST genérico, ou restringir ao admin com origem obrigatória; `origin` NOT NULL, imutável e fora de qualquer DTO de update                                                               | Fase 2                                                      |
| E3  | Avaliações (`DriverRatingService`, `ClientRatingService`) exigem só que o vínculo exista (`findByPair`)                                    | Um vínculo PENDING já permite avaliar quem nunca prestou o serviço                                                                                                                      | Exigir que algum contrato do vínculo tenha chegado a ACTIVE                                                                                                                                        | Fase 2, alinhado com as branches `feat/158-driver-rating-*` |
| E4  | `DependentService.delete` sem trava, e ainda chama `addressService.clearForDependent`                                                      | Excluir dependente com contrato ativo deixa contrato, agenda e checklist apontando para aluno sem endereço                                                                              | 422 ao excluir com contrato ACTIVE ou PENDINGSIGNATURE; mesma trava para trocar escola ou turno, que muda o objeto do contrato e exige aditivo ou novo contrato                                    | Fase 5                                                      |
| E5  | Vínculos sem contrato: V38/V39 inseriram `client_driver` a partir das avaliações, e o `ClientDriverSeeder` cria vínculo ACTIVE solto       | Viola a regra "não existe vínculo ACTIVE sem contrato ACTIVE"                                                                                                                           | Seeder cria o contrato junto; migration nova (sem editar V38/V39) rebaixa para ENDED os vínculos ACTIVE sem contrato                                                                               | Fase 1                                                      |
| E6  | `Shift.FULLTIME` no mesmo enum usado por `trip`                                                                                            | Viagem não é integral: aluno integral gera slot MORNING de ida e AFTERNOON de volta. Hoje a API aceita `trip` FULLTIME                                                                  | Separar `SchoolShift` (MORNING, AFTERNOON, NIGHT, FULLTIME; dependente e contrato) de `OperationShift` (MORNING, AFTERNOON, NIGHT; `trip` e `schedule_slot`); no banco, só um CHECK novo em `trip` | Fase 1                                                      |

### Ajustes

| #   | Estrutura atual                                                                                         | Ajuste                                                                                                                                                                  | Fase                                  |
| --- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| A1  | `dependent.shift` com valor único                                                                       | Fica, com significado de turno escolar; dias e horários atendidos vêm da `schedule`. Documentar no comment da coluna para ninguém montar rota com ela                   | Fase 1                                |
| A2  | `driver.base_price` NOT NULL                                                                            | Vira "a partir de" na busca (`startingPrice` no DTO e na tela); o preço que vale é o do contrato                                                                        | Fase 2                                |
| A3  | `driver.work_days` jsonb livre (`List<String>`)                                                         | Diverge da agenda real e não filtra bem; derivar das agendas ativas ou trocar por `smallint[]`/bitmask validado, com índice                                             | Fase 2                                |
| A4  | `driver.is_available` só exibido, a busca não filtra                                                    | Passa a significar "aceitando novos alunos": a busca filtra e a proposta é bloqueada; contratos existentes não mudam                                                    | Fase 2                                |
| A5  | `driver` e `vehicle` sem autorização municipal de transporte escolar nem vistoria (CTB arts. 136 e 138) | O contrato cita autorização e veículo. Adicionar número e validade, como colunas no veículo ou como documento tipado no fluxo de `driver_document`                      | Fase 3                                |
| A6  | Storage só local (`LocalStorageService`)                                                                | O PDF assinado vindo do Autentique precisa de armazenamento durável e privado; disco do container se perde no deploy                                                    | Fase 3, no máximo no começo da Fase 4 |
| A7  | `client_rating` sem `deleted_at`, diferente de `driver_rating`                                          | Alinhar soft delete e imutabilidade das duas avaliações                                                                                                                 | Fase 2, junto com E3                  |
| A8  | Unicidade do vínculo por par ativo (`client_driver_pair_active_key`)                                    | Correto, mas na recontratação o vínculo é reaproveitado e a origem original continua valendo para a comissão. Recomendação: manter, porque a origem é do relacionamento | Fase 2 (registrar a regra)            |

### Ficam como estão

- `trip` única por (motorista, data, turno), com `insertOrReread`: é a âncora certa do dia; só troca o enum (E6).
- Assistente com convite e status próprios (`AssistantInviteModel`): fluxo independente do contrato.
- V47 a V49: o índice de aprovação está bom. Como a V49 tirou `list_drivers` do CLIENT, a busca e o perfil na Fase 2 passam pelo endpoint de perfil, sem devolver a permissão.
- `users.document` e `email` NOT NULL: o aluno não vinculado não cria usuário.

E1, E5 e E6 são os que mais importam na Fase 1. Hoje o banco aceita estados que o modelo novo considera impossíveis, e as fases seguintes partem dessa garantia.

## Plano de entrega e experimentos

O spike deve fechar em cerca de duas semanas com quatro experimentos, já que o jurídico (E2) foi adiado. Nenhuma fase de código espera decisão pendente: a assinatura já está decidida.

### Experimentos do spike

| #   | Experimento                                                                                                                                                                                                                 | Pronto quando                                                                                       |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| E1  | Entrevistar 5 motoristas e 5 responsáveis: como precificam (por aluno, trecho ou distância), anual ou mensal, que contrato usam, como negociam, e a conta do mês inteiro (mensalidade + R$ 5 por aluno da carteira própria) | Campos da proposta, modelo de preço, valor da mensalidade e das comissões confirmados ou corrigidos |
| E2  | Advogado revisa o template e responde as 5 perguntas jurídicas (**adiado**)                                                                                                                                                 | Template aprovado por escrito; antes do primeiro contrato real com uma família, não antes do código |
| E3  | Integração de teste com a Autentique em 3 dias: documento pelo sandbox, assinatura num WebView do Flutter, webhook e PDF final; perguntas por escrito sobre cobrança                                                        | Fluxo completo rodando e cobrança da API confirmada                                                 |
| E4  | Gerar o contrato em HTML→PDF na API                                                                                                                                                                                         | PDF com acentos, fonte ≥ 12 e quebra de página corretas                                             |
| E5  | Protótipo navegável das telas (solicitação, cartão de proposta, pedir ajuste, resumo, assinatura, contrato) testado com 3 responsáveis                                                                                      | Cada um chega ao aceite sem ajuda                                                                   |

### Fases de código

Cada fase é uma change própria no openspec (proposal, design e tasks). Dentro da change, o trabalho se divide em PRs pelas regras 36 a 41 da constituição.

| Fase | Change no openspec         | Conteúdo                                                                                                                                                                                            | Depende de | Paralela com |
| ---- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ------------ |
| 1    | `contract-core`            | Núcleo do contrato: `contract`, `contract_item`, `schedule`, `schedule_slot`, `unlinked_passenger`; criação pelo admin e pelo seeder; consulta de passageiros. Destrava a rota e o checklist (#151) | —          | —            |
| 2    | `service-request-proposal` | Solicitação e proposta (schema, policies de estado, REST, push) + transições e origem do `client_driver` + trava nas avaliações                                                                     | 1          | —            |
| 3    | `contract-generation`      | Geração do contrato a partir da proposta aceita: `contract_template` com o modelo v1 (recomendações provisórias), `contract_document`, PDF, tela de resumo                                          | 2          | 6, 7         |
| 4    | `contract-signature`       | `ContractSignatureGateway` + adaptador da Autentique + colunas da assinatura em `contract_document` + webhook idempotente                                                                           | 3          | 6, 7         |
| 5    | `contract-activation`      | Efeitos da ativação: guardas no dependente, retirada das solicitações concorrentes                                                                                                                  | 4          | 6, 7         |
| 6    | `chat` (#161)              | Chat com cartões tipados                                                                                                                                                                            | 2          | 3, 4, 5, 7   |
| 7    | `driver-family-invite`     | Alunos não vinculados no app do motorista, convite "convidar família" e sugestão de troca pelo dependente real (D12)                                                                                | 1, 2       | 3, 4, 5, 6   |
| 8    | `contract-lifecycle`       | `contract_event`, aditivo, rescisão, renovação, arrependimento, cronograma de parcelas                                                                                                              | 5          | 9            |
| 9    | `driver-billing`           | Fatura ao motorista: mensalidade + comissões pela origem do vínculo, PIX, bloqueio de novas propostas se não pagar                                                                                  | 5          | 8            |

A fase 1 vem primeiro porque destrava a rota e o checklist (#151) antes de qualquer fluxo de proposta existir. A fase 7 é a que mais pesa no lançamento: sem ela, o motorista não consegue trazer as famílias que já tem para o contrato digital. Se o go-to-market for pelos motoristas, vale fazê-la antes do chat (fase 6).

## Decisões

Na V2, todas as recomendações de produto foram aceitas, e assinatura, receita e carteira própria foram fechadas em 03/10. Fica em aberto o jurídico, além de valores que as entrevistas vão calibrar.

### Decididas

| Pergunta                                                         | Decisão                                                                                                                                                      |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| O responsável pode conversar antes de mandar a solicitação?      | Não: a solicitação é a primeira mensagem; a busca mostra vagas e turnos para compensar                                                                       |
| Pode negociar com vários motoristas ao mesmo tempo?              | Sim, até 5 solicitações abertas                                                                                                                              |
| O responsável escreve contraproposta estruturada?                | Não no MVP; usa "pedir ajuste"                                                                                                                               |
| Quem assina primeiro?                                            | Responsável, depois motorista                                                                                                                                |
| Contrato por dependente ou por família?                          | Por par, com itens por dependente                                                                                                                            |
| Endereço exato antes do aceite?                                  | Não; bairro/quadra e distância                                                                                                                               |
| Motorista PJ e PF?                                               | Os dois; muda só a qualificação                                                                                                                              |
| Prazos                                                           | Solicitação 72 h, proposta 7 dias, assinatura 48 h por parte; validar em E1                                                                                  |
| Serviço de assinatura                                            | Autentique, atrás da `ContractSignatureGateway`                                                                                                              |
| Como a Vanep ganha?                                              | Mensalidade do motorista + comissão pela origem do vínculo (cerca de R$ 5 carteira própria, R$ 10–15 busca), numa fatura ao motorista paga por PIX           |
| Como entra a carteira própria?                                   | Alunos não vinculados na rota, sem trava e pagos pela mensalidade; convite para o fluxo normal, que termina no contrato digital. Não existe contrato externo |
| Travar alunos não vinculados (limite de quantidade ou de prazo)? | Não. O valor das funções dos pais empurra para o contrato; trava geraria cancelamento, não contrato. Limite só num eventual plano gratuito                   |

### Em aberto

| Tema                                    | O que falta decidir                                                        | Como fecha                                                                                         |
| --------------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Revisão jurídica (D10)                  | Template final e as 5 perguntas; até lá valem as recomendações provisórias | E2, antes do primeiro contrato real                                                                |
| A Vanep aparece como parte no contrato? | Provisório: não, só uma cláusula de plataforma                             | E2                                                                                                 |
| Valores da receita                      | Mensalidade, valores exatos das comissões, plano gratuito                  | E1 + decisão de negócio                                                                            |
| Modelo de comissão com o Pagar.me       | Porcentagem sobre cada pagamento e se as duas origens continuam            | Quando a cobrança passar pelo Pagar.me                                                             |
| Data de lançamento                      | Perto da matrícula ou no meio do ano                                       | Decisão de negócio; lançando perto da matrícula, mais famílias entram direto pelo contrato digital |

## Fontes

- Código lido em `vanep-api-java` (migrations V1–V46, changes `client-driver-link`, `trip-daily-operation` e `location-system`, `constitution.md`) e `vanep-mobile` (specs e strings)
- [Código de Defesa do Consumidor (Lei 8.078/1990)](https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm) — arts. 7º, 25, 30, 49, 51, 52 e 54
- [LGPD (Lei 13.709/2018)](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm) — arts. 6º e 14
- [MP 2.200-2/2001](https://www.planalto.gov.br/ccivil_03/mpv/antigas_2001/2200-2.htm) — art. 10
- [CPC art. 784 §4º, incluído pela Lei 14.620/2023 (Cescon Barrieu)](https://cesconbarrieu.com.br/alteracao-no-cpc-confere-forca-executiva-aos-contratos-assinados-eletronicamente/)
- [CTB art. 136 (CTB Digital)](https://ctbdigital.com.br/artigo/art136/) e [art. 138 comentado](https://www.ctbdigital.com.br/comentario/comentario138)
- [TJDFT aplica o art. 49 a contrato fechado por WhatsApp (Migalhas)](https://www.migalhas.com.br/quentes/385092/em-contrato-por-whatsapp-segue-valido-direito-de-arrependimento-do-cdc)
- [Preços da API Autentique](https://docs.autentique.com.br/api/api-pricing.md)
- [Planos da ZapSign](https://zapsign.com.br/tabela-funcionalidades-v2)
- [Multa em contratos de transporte escolar (Van Inteligente)](https://blog.vaninteligente.com.br/multa-de-contrato-como-definir-no-transporte-escolar/) — prática de mercado, não referência legal: recomenda multa de atraso de 5% a 10%, acima do teto do CDC
