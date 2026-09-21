## Why

O backend **não recebe arquivo nenhum**. Não existe `MultipartFile`, `@RequestPart`, abstração de storage nem endpoint de download em todo o `src/main/java`.

O que existe são seis colunas `varchar` esperando uma URL que ninguém consegue produzir:

| Tabela | Coluna |
|---|---|
| `client`, `driver`, `assistant` | `photo` |
| `vehicle` | `photo_front_url`, `photo_side_url`, `photo_document_url` |
| `driver_cnh` | `photo_url` |
| `driver_document` | `file_url` |

O app não tem seletor de arquivo, o login Google não preenche `photo`, e nada recebe bytes. Os campos estão vazios porque **falta a outra metade**.

Isso já bloqueia trabalho: a validação manual de documento no painel (`vanep-frontend#20`) depende de ver um documento que não tem como chegar ao sistema.

### Duas decisões de revisão que moldam esta change

**Onde os arquivos ficam.** A #207 especifica Firebase Storage. Na revisão a decisão mudou: **ficam no disco da VPS por enquanto**, porque o Firebase exige o plano Blaze e ativá-lo é decisão de faturamento que o time não quer tomar agora. O pedido foi desenhar de um jeito que a migração depois seja fácil.

**Quem aponta para quem.** A primeira versão desta change tinha uma tabela `media_file` polimórfica (`owner_type` + `owner_id`) e uma API genérica `POST /api/media`. A revisão das PRs #217 e #218 derrubou isso:

> *"você fez uma API pra retornar as mídias, isso acaba gerando duas requests toda vez que formos ter que renderizar uma imagem no front ou mobile. O certo seria o arquivo já vir retornado na API que tem a imagem."*
>
> *"não pode ser um endpoint só que retorna tudo com base num filtro"*
>
> *"você não deveria precisar de um enum de media owner pra essa task"*

A direção se inverte: **o dono aponta para a mídia**, não o contrário. `GET /api/drivers/{token}` passa a devolver a foto, e o upload vira `POST /api/drivers/{token}/photo`.

Isso ganha mais do que a requisição economizada. Um `owner_id` polimórfico **não tem chave estrangeira**: o banco não consegue impedir mídia apontando para um dono que já foi apagado. Com `driver.photo_media_id`, a integridade é do banco — e é exatamente o tipo de dívida que a pilha #198–#203 está pagando nas avaliações.

E a cardinalidade confirma: os seis campos são **um arquivo por slot**. Nenhum é "vários arquivos da mesma finalidade". A tabela polimórfica resolveria um problema que não existe aqui.

## What Changes

- Interface **`StorageService`** + **`LocalStorageService`**; nenhum service de domínio toca no sistema de arquivos
- Tabela **`media_file`** com `token`, `provider`, `object_key`, `mime_type`, `size_bytes`, `original_name`, `visibility` — **sem dono**
- As seis colunas `varchar` viram **`*_media_id` com FK** para `media_file`
- **Upload por dono**: `POST /api/drivers/{token}/photo`, `POST /api/driver-documents/{token}/file`, e assim por diante
- Os DTOs de resposta existentes passam a devolver a **URL pronta** no mesmo campo de hoje (`photo`), sem mudar o formato do JSON
- `GET /api/media/{token}/download` entrega os bytes — é o **único** endpoint genérico que sobra, e é o que mantém o endereço estável quando o storage mudar
- Os cinco DTOs de request perdem o campo de URL: quem grava passa a ser o upload

**Fora de escopo:**

- **A implementação Firebase.** Esta change entrega a abstração e o caminho de migração desenhado, não o código do provedor.
- **Slot reservado** (`status = PENDING`, binário num `PUT` posterior). Serve a offline-first do app, que hoje não tem nem seletor de arquivo.
- Transformação de imagem: thumbnail, resize, compressão.

## Capabilities

### New Capabilities

- `media-storage`: o sistema recebe, guarda, serve e apaga arquivo binário, com o dono apontando para a mídia, metadado rastreável e local de armazenamento trocável por configuração.

## Impact

- **Schema:** cria `media_file` e troca seis colunas `varchar` por FK. É alteração em cinco tabelas com registro vivo.
- **⚠️ Breaking change nos DTOs de request.** `ClientUpdateRequestDTO.photo`, `DriverUpdateRequestDTO.photo`, `VehicleRequestDTO.photo*Url`, `DriverCnhRequestDTO.photoUrl` e `DriverDocumentRequestDTO.fileUrl` deixam de aceitar string. Quem gravava por ali passa a usar o upload.
- **⚠️ O painel admin é afetado.** `ClientUpdateRequestDTO.photo` veio da #90, mergeada há poucos dias, e o formulário de edição de cliente (`vanep-frontend#24`) manda esse campo. O campo "Foto" daquela tela precisa virar upload de arquivo.
- **⚠️ `driver_document.file_url` é `@NotBlank` e `not null`.** Criar documento hoje exige a URL. O fluxo inverte: cria-se o documento e o arquivo sobe depois.
- **Os DTOs de resposta não mudam de forma.** `photo` continua sendo uma string; passa a conter uma URL que funciona.
- **Disco da VPS entra no caminho crítico.** É o mesmo disco de 50–60 GB que motivou a issue.
- **Backup muda de natureza.** Existe um diretório cuja perda é irrecuperável: CNH e documento não se regeram.
- **Novo requisito de deploy:** volume persistente montado fora do container.
