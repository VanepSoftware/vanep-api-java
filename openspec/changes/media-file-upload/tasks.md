> **Redesenho.** As PRs #217, #218 e #219 implementavam a primeira versão desta change,
> com tabela polimórfica e API genérica de mídia. A revisão do @JoaoBittencourt1 derrubou
> esse desenho. Elas são fechadas e a pilha é refeita.
>
> **Pilha de branches.** Cada branch nasce de dentro da anterior e cada PR aponta a anterior
> como `--base`. A fase 1 sai de `main`.
>
> ```
> main ─ …-media-core ─ …-driver-photo ─ …-people-photo ─ …-vehicle-photos ─ …-driver-files
>         PR 1 → main   PR 2 → PR 1      PR 3 → PR 2       PR 4 → PR 3        PR 5 → PR 4
> ```
>
> **Mergear pelo `merge stack` na última PR.** O repo tem `delete_branch_on_merge: false`,
> e sem apagar a branch o GitHub não reaponta a base das filhas.

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `media-storage`
- [x] 0.3 **Confirmar o número de Flyway livre.** `V36`–`V39` estão na pilha aberta #198–#203 e `V40` era da versão anterior desta change. Reconfirmar com `git ls-tree` em todas as branches remotas antes de escrever
- [x] 0.4 **Q1 respondido pelo @JoaoBittencourt1**: download por dono, compartilhando a implementação e não a URL. O argumento dele venceu o meu, e a autorização ainda fica mais barata (D3)
- [ ] 0.5 **Conferir em produção que as seis colunas de URL estão vazias** (R4). O plano assume isso; se houver dado, a migração precisa de backfill
- [ ] 0.6 **Avisar o time que o backup muda de natureza** (R2), antes do primeiro upload real
- [ ] 0.7 Fechar as PRs #217, #218 e #219 explicando o redesenho

## 1. Phase 1 — o motor compartilhado (PR 1)

> Goal: o motor compartilhado — guardar, validar, responder e apagar. Sem rota.
> Depends on: — | Parallel with: —
> Order: test → migration → model → repository → service

- [x] 1.1 Criar branch `feat/207-media-core` a partir de `main`
- [x] 1.2 Testes do `LocalStorageService`: grava e lê; apaga e some; apagar inexistente não explode; `../`, caminho absoluto e key em branco são recusados **sem escrever fora da raiz**; um nome legítimo como `..seguro.txt` continua aceito
- [x] 1.3 Testes de repositório: busca por token; soft delete some das queries; listagem por provider
- [x] 1.4 Criar `MediaPurpose`, `MediaVisibility` e `StorageProvider` (`LOCAL`, `FIREBASE`). **Não** criar enum de dono — é o que a revisão pediu para tirar (D1)
- [x] 1.5 Migration criando `media_file`: `id`, `token`, `provider`, `object_key`, `mime_type`, `size_bytes`, `original_name`, `visibility`, timestamps, `deleted_at`. **Sem `owner_type` e sem `owner_id`**
- [x] 1.6 Índices únicos parciais em `token` e `object_key`, ambos `where deleted_at is null` (regra 19)
- [x] 1.7 **Aplicar a migration manualmente contra o PostgreSQL** e conferir os índices em SQL cru (R5)
- [x] 1.8 `model/MediaFileModel` com `@SoftDelete` e `repository/MediaFileRepository`
- [x] 1.9 Interface `StorageService` (`upload`, `open`, `delete`, `exists`, `signedUrl` com default vazio) e `LocalStorageService`
- [x] 1.10 **Travessia de diretório, camada 2**: o `LocalStorageService` normaliza e recusa key que escape da raiz (D6)
- [x] 1.11 `MimeTypeDetector` por assinatura, em lista de permissão (D7)
- [x] 1.12 `MediaFileService` com o núcleo compartilhado: validar, gravar, substituir e apagar
- [x] 1.13 `MediaResponder`: decide entre transmitir o byte e responder `302` para a URL assinada. É o **único** lugar que o dia da migração toca (D3, D11)
- [x] 1.14 **Nenhum endpoint nesta fase.** O motor não tem rota própria: quem expõe é cada dono, a partir da fase 2
- [x] 1.15 Configuração por env sem default silencioso; `.env.example` documentando que a raiz fica fora do container e **não pode ser servida pelo nginx** (R6)
- [x] 1.16 Limites de multipart no `application.properties`, acompanhando o maior limite da aplicação
- [x] 1.17 `media_file` no `clean.sql`, na ordem correta de FK
- [ ] 1.18 `make lint` + `./mvnw verify`; abrir PR fase 1 em pt-BR, `--base main` (regras 44 e 47)

## 2. Phase 2 — `driver.photo` estabelece o padrão (PR 2)

> Goal: o primeiro dono aponta para a mídia, de ponta a ponta. É o molde das fases seguintes.
> Depends on: Phase 1 | Parallel with: —

- [x] 2.1 Criar branch `feat/207-driver-photo` **de dentro de** `feat/207-media-core`
- [x] 2.2 Testes: upload → 201 e o motorista passa a devolver a URL; substituir → a mídia anterior some do banco **e do disco**; sem posse → 403; tipo inválido → 400; acima do limite → 413
- [x] 2.2b **Teste nomeado afirmando que o corpo do 403 está VAZIO**, não só que o status é 403 (D8)
- [x] 2.2c `GET /api/drivers/{token}/photo` entrega os bytes, reusando `@sec.isDriverOwner` — a autorização é a da própria feature (D3)
- [x] 2.3 **Teste nomeado provando a chamada única** (D1): `GET /api/drivers/{token}` traz a foto sem nenhuma requisição adicional
- [x] 2.4 Migration: `driver.photo` vira `photo_media_id` com FK anulável para `media_file`
- [x] 2.5 **Aplicar manualmente contra o PostgreSQL** (R5)
- [x] 2.6 `POST /api/drivers/{token}/photo` em `multipart/form-data` e `GET` no mesmo caminho para os bytes (D2, D3)
- [x] 2.7 `DriverResponseDTO.photo` **mantém nome e tipo** e passa a carregar a URL (D9)
- [x] 2.8 **Remover `photo` do `DriverUpdateRequestDTO`** — quem grava agora é o upload (R3)
- [ ] 2.9 `make lint` + `./mvnw verify`; abrir PR apontando `--base feat/207-media-core`

## 3. Phase 3 — `client.photo` e `assistant.photo` (PR 3)

> Goal: repetir o padrão nas duas pessoas restantes.
> Depends on: Phase 2 | Parallel with: —

- [x] 3.1 Criar branch `feat/207-people-photo` **de dentro de** `feat/207-driver-photo`
- [x] 3.2 Testes espelhando a fase 2, para cliente e assistente
- [x] 3.3 Migration `V43`: `client.photo` e `assistant.photo` viram FK. **Aplicada contra o PostgreSQL** (R5): a FK recusa `photo_media_id` inexistente e aceita nulo
- [x] 3.4 `POST /api/clients/{token}/photo` e `POST /api/assistants/{token}/photo`
- [x] 3.5 **⚠️ Remover `photo` do `ClientUpdateRequestDTO`** (R3). Ele veio da #90, mergeada há poucos dias, e o formulário do painel (`vanep-frontend#24`) manda esse campo
- [ ] 3.6 **Abrir issue no `vanep-frontend`**: o campo "Foto" da edição de cliente vira upload de arquivo. Sem isso, aquela tela passa a mandar um campo que o backend não aceita mais
- [ ] 3.7 `make lint` + `./mvnw verify` ✅ (1062 testes, jacoco ok); PR pendente de aprovação para subir — `--base feat/207-driver-photo`

## 4. Phase 4 — as três fotos do veículo (PR 4)

> Goal: o primeiro dono com mais de um slot.
> Depends on: Phase 3 | Parallel with: —

- [ ] 4.1 Criar branch `feat/207-vehicle-photos` **de dentro de** `feat/207-people-photo`
- [ ] 4.2 Testes: os três slots são independentes; subir a lateral não mexe na frontal
- [ ] 4.3 Migration: `photo_front_url`, `photo_side_url` e `photo_document_url` viram FK
- [ ] 4.4 `POST /api/vehicles/{token}/photo-front`, `/photo-side` e `/photo-document`
- [ ] 4.5 Remover os três campos do `VehicleRequestDTO` (R3)
- [ ] 4.6 `make lint` + `./mvnw verify`; abrir PR apontando `--base feat/207-people-photo`

## 5. Phase 5 — CNH e documento do motorista (PR 5)

> Goal: os dois arquivos privados. Fecha a change e destrava o painel.
> Depends on: Phase 4 | Parallel with: —

- [ ] 5.1 Criar branch `feat/207-driver-files` **de dentro de** `feat/207-vehicle-photos`
- [ ] 5.2 Testes de autorização **por arquivo privado**: o motorista lê o próprio documento; um terceiro toma 403 com corpo vazio; o admin lê
- [ ] 5.3 Teste nomeado do PDF: `DOCUMENT` aceita PDF e imagem, e nasce `PRIVATE`
- [ ] 5.4 Migration: `driver_cnh.photo_url` e `driver_document.file_url` viram FK **anulável**
- [ ] 5.5 **O fluxo de criação do documento inverte** (D10): `file_url` era `@NotBlank` e `not null`. Passa a ser criar o documento e subir o arquivo depois. Documento sem arquivo vira estado legítimo — é o "pendente de envio" que o painel precisa listar
- [ ] 5.6 `POST /api/driver-cnhs/{token}/photo` e `POST /api/driver-documents/{token}/file`
- [ ] 5.7 Remover `photoUrl` do `DriverCnhRequestDTO` e `fileUrl` do `DriverDocumentRequestDTO`
- [ ] 5.8 `make lint` + `./mvnw verify`; abrir PR apontando `--base feat/207-vehicle-photos`, com `Closes #207`

## 6. Encerramento

- [ ] 6.1 Mergear pelo **`merge stack`** na última PR, ou apagando a branch a cada merge
- [ ] 6.2 **Testar o caminho inteiro contra o backend real**: subir foto e PDF, ler pelo dono numa chamada, substituir, apagar e conferir que o byte sumiu do disco
- [ ] 6.3 Rodar `./mvnw verify` na `main` integrada e confirmar a cobertura mínima (regra 24)
- [ ] 6.4 **Abrir issue da migração para o Firebase**, com o desenho do D11 e o aviso do R7
- [ ] 6.5 **Alerta de uso de disco na VPS** (R1)
- [ ] 6.6 Desbloquear o `vanep-frontend#20` (validação manual de documento)
- [ ] 6.7 Sincronizar o spec (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
