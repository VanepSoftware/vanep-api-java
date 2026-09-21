> **Pilha de branches.** Cada branch nasce de dentro da anterior e cada PR aponta a anterior
> como `--base`. A fase 1 sai de `main`.
>
> ```
> main ─ feat/207-media-storage ─ …-media-upload ─ …-media-read
>          PR 1 → main            PR 2 → PR 1      PR 3 → PR 2
> ```
>
> **Mergear pelo `merge stack` na última PR.** O repo tem `delete_branch_on_merge: false`,
> e sem apagar a branch o GitHub não reaponta a base das filhas — foi assim que a pilha
> do #150 colapsou e precisou de uma PR de recuperação.

## 0. Preparation

- [x] 0.1 Ler `constitution.md` inteiro antes de qualquer trabalho (`openspec/rules.md` regra 1)
- [x] 0.2 Revisar `proposal.md`, `design.md` e o spec `media-storage`
- [x] 0.3 **Confirmar o próximo número de Flyway.** `V36` a `V39` estão na pilha aberta #198–#203, então o plano assume **`V40`**. A #207 dizia `V36`, escrita antes daquela pilha. Reconfirmar antes de escrever o arquivo (R6)
- [ ] 0.4 **Avisar o time que o backup muda de natureza** (R2). A partir da primeira PR existe um diretório cuja perda é irrecuperável. Isso precisa entrar na rotina **antes** do primeiro upload real, não depois
- [ ] 0.5 Comentar na #207 registrando as três divergências: destino é a VPS e não o Firebase, `V40` e não `V36`, e upload direto em vez de slot reservado (D4)

## 1. Phase 1 — storage e metadado, sem HTTP (PR 1)

> Goal: o sistema sabe guardar e apagar um arquivo, e sabe registrar o que guardou. Sem endpoint.
> Depends on: — | Parallel with: —
> Order: test → migration → model → repository → service

- [ ] 1.1 Criar branch `feat/207-media-storage` a partir de `main`
- [ ] 1.2 Testes do `LocalStorageService`: grava e lê de volta; apaga e some; apagar inexistente não explode; caminho com `../` no `objectKey` é recusado
- [ ] 1.3 Testes de repositório: busca por token; busca por dono e `purpose`; soft delete some das queries
- [ ] 1.4 Criar `MediaOwnerType`, `MediaPurpose`, `MediaVisibility` e `StorageProvider` (`LOCAL`, `FIREBASE`) — o segundo já nasce no enum, porque acrescentar valor depois é barato em Java e caro em `check` de coluna
- [ ] 1.5 Migration `V40__create_media_file_table.sql`: `id`, `token`, `owner_type`, `owner_id`, `purpose`, `provider`, `object_key`, `mime_type`, `size_bytes`, `original_name`, `visibility`, `created_at`, `updated_at`, `deleted_at`
- [ ] 1.6 **Guardar `object_key`, nunca URL** (D2). Índice único parcial em `token` e em `object_key`, ambos `where deleted_at is null` (regra 19)
- [ ] 1.7 **Aplicar a `V40` manualmente contra o PostgreSQL** e conferir os índices em SQL cru. A suíte roda `flyway.enabled=false`, nenhum teste executa esta migration (R4)
- [ ] 1.8 Criar `br.com.vanep.media` com `model/MediaFileModel` (`@SoftDelete`, regra 19) e `repository/MediaFileRepository`
- [ ] 1.9 Criar a interface `StorageService` (`upload`, `open`, `delete`) e `LocalStorageService` (D1)
- [ ] 1.10 **Travessia de diretório, camada 1 (D11): a key é derivada, nunca recebida.** Montar o `object_key` só de enums, tokens gerados por nós e sufixo do MIME **detectado**. O nome original do arquivo vai para `original_name` como metadado e **não** toca o sistema de arquivos — é o que fecha o buraco na origem
- [ ] 1.10b **Travessia de diretório, camada 2: o `LocalStorageService` recusa key que escape da raiz.** Normalizar e conferir que o caminho final continua sob a raiz. É defesa em profundidade: se alguém mudar a montagem da key sem perceber, aqui falha alto
- [ ] 1.10c Teste nomeado com `../`, caminho absoluto e `..%2f` codificado, provando que **nada é escrito fora da raiz**
- [ ] 1.11 Configuração por env: `MEDIA_STORAGE_ROOT`, `MEDIA_STORAGE_PROVIDER`, limites. **Sem default silencioso** — faltando, a aplicação falha explicitamente (regra 1 da constitution, e regra 1 do frontend pelo mesmo motivo)
- [ ] 1.12 Acrescentar `media_file` ao `src/test/resources/db/clean.sql` na ordem correta de FK
- [ ] 1.13 `.env.example` com as variáveis novas, documentando **por que a raiz fica fora do container e fora do repositório** (D8) e que a pasta **não pode ser servida pelo nginx** (D5, R5)
- [ ] 1.14 `make lint` + `./mvnw verify`; abrir PR fase 1 em pt-BR, `--base main` (regras 44 e 47)

## 2. Phase 2 — upload (PR 2)

> Goal: o arquivo entra no sistema, validado.
> Depends on: Phase 1 | Parallel with: —
> Order: test → requestDTO → service → controller → responseDTO → mapper

- [ ] 2.1 Criar branch `feat/207-media-upload` **de dentro de** `feat/207-media-storage`
- [ ] 2.2 Testes: upload válido → 201; arquivo mentindo o tipo → 400; acima do limite → 413; sem JWT → 401; dono inexistente → 404
- [ ] 2.3 **Teste nomeado do arquivo que mente** (D7): nome `.jpg`, bytes de outra coisa. É o caso que a validação por extensão deixaria passar, e o único que impede subir executável disfarçado
- [ ] 2.4 Teste nomeado de que **nada é gravado** quando a validação falha — nem linha, nem arquivo
- [ ] 2.5 Criar `dto/MediaUploadRequestDTO` e `dto/MediaFileResponseDTO`; `mapper/MediaFileMapper` (regra 12 — nunca devolver o model)
- [ ] 2.6 `service/MediaFileService.upload`: resolve o dono, valida MIME real e tamanho, monta o `object_key`, grava o arquivo e **só então** a linha
- [ ] 2.7 **Detectar o MIME pelos bytes** (D7), comparando com o permitido por `purpose`. Extensão e `Content-Type` do request só escolhem o sufixo
- [ ] 2.8 `controller/MediaFileController` com `POST /api/media` em `multipart/form-data`. Controller fino (regra 7)
- [ ] 2.9 Limites de multipart no `application.properties` (`max-file-size`, `max-request-size`), dimensionados pelo caso real: foto e documento não passam de poucos MB (R3)
- [ ] 2.10 Chaves de MessageSource em EN e pt-BR para cada recusa. Nunca hardcodar pt-BR no `throw` (regra 46)
- [ ] 2.11 Permissões novas no `PermissionEnum` e no bundle `ADMIN`, incluindo `show_media` (Q1)
- [ ] 2.12 `make lint` + `./mvnw verify`; abrir PR fase 2 apontando `--base feat/207-media-storage`

## 3. Phase 3 — leitura e exclusão (PR 3)

> Goal: o arquivo sai do sistema para quem pode, e some para valer quando apagado. Fecha a change.
> Depends on: Phase 2 | Parallel with: —
> Order: test → security → service → controller

- [ ] 3.1 Criar branch `feat/207-media-read` **de dentro de** `feat/207-media-upload`
- [ ] 3.2 Testes de autorização: o dono baixa → 200; um terceiro → **403**; sem JWT → 401; arquivo apagado → 404
- [ ] 3.3 **Autorizar ANTES de abrir o stream** (D12). A resolução e a checagem acontecem primeiro; o `StorageService.open` só é chamado com a decisão já tomada. O caminho natural é o inverso — abrir o recurso e checar no meio da escrita — e aí o 403 sai com bytes dentro
- [ ] 3.3b **Teste nomeado afirmando que o corpo do 403 está VAZIO**, não só que o status é 403. Vazar conteúdo junto de um status de erro é o jeito mais fácil de transformar autorização em teatro
- [ ] 3.4 Teste nomeado: apagar remove **a linha e o arquivo**; conferir os dois (D5)
- [ ] 3.5 Acrescentar a posse ao `SecurityEvaluator` (regra 22 — sem service de segurança por feature). Leitura: **dono OU `show_media`** — a decisão do Q1 é que quem mais lê documento é admin
- [ ] 3.5b Teste nomeado dos três casos de leitura: dono → 200, admin com `show_media` → 200, terceiro → 403
- [ ] 3.6 `GET /api/media/{token}` (metadado) e `GET /api/media/{token}/download` (bytes, em stream)
- [ ] 3.6b **O endereço de download é o mesmo para todo provider** (D10). Nunca devolver URL nativa do provedor ao cliente: hoje o endpoint transmite o byte; com o Firebase ele responde `302` para a URL assinada. Painel e app não mudam uma linha no dia da migração, nem durante ela, com metade dos arquivos de cada lado
- [ ] 3.7 `DELETE /api/media/{token}`: soft delete na linha **e** remoção do objeto
- [ ] 3.8 Conferir que `SecurityConfig` não deixa nenhuma rota nova pública por omissão (regras 20 e 21)
- [ ] 3.9 `make lint` + `./mvnw verify`; abrir PR fase 3 apontando `--base feat/207-media-upload`, com `Closes #207`

## 4. Encerramento

- [ ] 4.1 Mergear pelo **`merge stack`** na última PR, ou apagando a branch a cada merge
- [ ] 4.2 **Testar o caminho inteiro contra o backend real**: subir uma imagem, baixar pelo painel, apagar e conferir que o byte sumiu do disco
- [ ] 4.3 Rodar `./mvnw verify` na `main` integrada e confirmar a cobertura mínima do JaCoCo (regra 24)
- [ ] 4.4 **Abrir issue da migração das seis colunas de URL** (`photo` em `client`/`driver`/`assistant`, as três do `vehicle`, `photo_url` da CNH, `file_url` do documento) para referenciarem `media_file`
- [ ] 4.5 **Abrir issue da migração para o Firebase**, com o desenho do D9 e o aviso do R7 (assinatura V4 exige chave privada; credencial de ambiente não assina)
- [ ] 4.6 **Alerta de uso de disco na VPS** (R1). Sem ele, o sintoma de disco cheio vai ser o Postgres parando de escrever, e ninguém vai associar a upload
- [ ] 4.7 Desbloquear o `vanep-frontend#20` (validação manual de documento), que dependia disto para ter o que exibir
- [ ] 4.8 Sincronizar o spec para `openspec/specs/` (`/opsx:sync`) e arquivar a change (`/opsx:archive`)
