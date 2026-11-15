# VAN-81 — Relatório de validação

Data: 2026-11-08. Harness: `make audit-n-plus-one` (`NPlusOneRepositoryAuditTest`, também roda no `make test`).

Critério de hotspot: a contagem de `prepareStatement` **cresce** depois de criar mais 3 linhas. Cada sonda roda dentro de uma transação read-only (como os services) e cria associações **diferentes por linha** (mídia, usuário, revisor, motorista).

## Rodada 1 — sonda original

Linhas criadas sem mídia nem revisor, leitura fora de transação.

| Probe | antes | depois | Hotspot? |
|-------|------:|-------:|:--------:|
| `VehicleRepository.findByDriverId` | 2 | 2 | não |
| `DriverDocumentRepository.findByDriverId` | 2 | 2 | não |
| `DriverCnhRepository.findByDriverId` | 2 | 2 | não |
| `AssistantRepository.findByDriverId` | 3 | 6 | **sim** |
| `ContractRepository.findByClientDriverId` | 1 | 1 | não |

## Rodada 2 — validação extra

A rodada 1 mascarava casos: associações `EAGER` vazias (`null`) não geram select. Repetida com mídia/revisor por linha, foto do assistente (LAZY, lida pelo mapper), página de documentos e lista do admin.

| Probe | antes | depois | Hotspot? |
|-------|------:|-------:|:--------:|
| `VehicleRepository.findByDriverId` (3 fotos) | 5 | 14 | **sim** |
| `DriverDocumentRepository.findByDriverId` (arquivo + revisor) | 4 | 10 | **sim** |
| `DriverDocumentRepository.findByDriverId(Pageable)` | 10 | 16 | **sim** |
| `DriverCnhRepository.findByDriverId` | 2 | 2 | não |
| `AssistantRepository.findByDriverId` (+ foto) | 4 | 10 | **sim** |
| `ContractRepository.findByClientDriverId` | 1 | 1 | não |

`DriverDocumentRepository.findAll(Pageable)` (lista do admin, um motorista por linha) tem o mesmo mapeamento `EAGER` e foi corrigido junto, com uma sonda nova.

## Correção

`@EntityGraph` nas consultas derivadas (funciona com paginação, e o Spring gera a count query):

- `AssistantRepository.findByDriverId`: `user`, `driver.user`, `photo`
- `VehicleRepository.findByDriverId`: `driver.user`, `photoFront`, `photoSide`, `photoDocument`
- `DriverDocumentRepository.findByDriverId` (lista e página), `findByDriverIdAndDocumentType`, `findByDriverIdAndStatus`, `findAll(Pageable)`: `driver.user`, `file`, `reviewedBy`

## Rodada 3 — depois da correção

| Probe | antes | depois |
|-------|------:|-------:|
| `VehicleRepository.findByDriverId` | 1 | 1 |
| `DriverDocumentRepository.findByDriverId` | 1 | 1 |
| `DriverCnhRepository.findByDriverId` | 2 | 2 |
| `DriverDocumentRepository.findByDriverId(Pageable)` | 1 | 1 |
| `DriverDocumentRepository.findAll(Pageable)` | 1 | 1 |
| `AssistantRepository.findByDriverId` | 1 | 1 |
| `ContractRepository.findByClientDriverId` | 1 | 1 |

O audit agora falha (`isEmpty()`) se qualquer sonda crescer.

## Rodada 4 — varredura da suíte inteira

As sondas só cobrem as listas escolhidas. Para cobrir o resto, `make audit-n-plus-one-suite` roda os 2075 testes com um detector (`br.com.vanep.shared.nplusone`): um `StatementInspector` mais um `SessionEventListener` do Hibernate registram os selects de cada sessão (uma transação de service, já que `open-in-view=false`). Todo select que se repete na mesma sessão vai para `target/n-plus-one-report.txt`, com o call site e o teste. Assim entra tudo o que roda nos testes: endpoint, service, mapper e listener.

411 sessões repetiram algum select. Triagem por call site:

| Call site | Repetições | Veredito |
|-----------|-----------:|----------|
| `DependentService.clientTokensById` (lista do admin) | 1 select de `users` por cliente | **N+1**, corrigido |
| `LocationResolverService.findDistrict` | até 4 | loop pelos níveis de bairro do endereço (2 a 4), cada nível depende do pai; limitado |
| `NegotiationEmailService` → `UserService.requireByToken`, `ServiceRequestService.*`, `ProposalService.relatedTo` | 2–3 | o fluxo de envio e o e-mail que sai junto buscam o mesmo dado de novo; fixo por request |
| `SchoolResolveService.resolve` | 2 | `findByGooglePlaceId` antes e depois do Places; fixo |
| `VehiclePhotoService.require` / `VehicleService.requireByToken` | 3 | as 3 fotos `EAGER` de **um** veículo; fixo |
| `AssistantInviteService.createInvite`, `ClientDriverService.create`, `OnboardingService`, `require*Of*` | 2 | mesma busca repetida dentro do fluxo; fixo |
| sem frame da aplicação | 2–3 | código do próprio teste |

**Correção do dependente:** `ClientModel.user` é `EAGER`, e o service só precisava do token do cliente. `clientTokensById` agora usa `ClientRepository.findTokensByIdIn` (projeção id + token, no mesmo padrão de `DependentRepository`/`VehicleRepository`), e ganhou uma sonda no audit.

Repetições em quantidade fixa por request não crescem com N, então não são N+1. Ficam no relatório como candidatas a limpeza, fora do escopo desta task.

## Já cobertos antes por testes com `SqlStatementCounter`

`ClientDriverRepositoryTest`, `ContractRepositoryTest`, `UnlinkedPassengerRepositoryTest`, `ContractReadinessControllerTest`. Listas com `join fetch` explícito: `ClientDriverRepository`, `ConversationRepository`, `ContractRepository`, `ProposalRepository`, `ServiceRequestRepository`, `TripRepository`, `UnlinkedPassengerRepository`, `DriverServiceAreaRepository`. Batch no service: `DependentService.toResponses`, `ServiceRequestService`.

## Revisados e sem risco

- `ConversationMessageRepository.findByConversationIdNewestFirst`: a única associação é a conversa, a mesma em todas as linhas.
- `ContractRepository.findActiveByClientDriverId`: retorna uma linha só.
- `DependentRepository.findByClientId`: o service já busca escolas e endereços em lote.
