## Why

O backend **não recebe arquivo nenhum**. Não existe `MultipartFile`, `@RequestPart`, `consumes = multipart/form-data`, abstração de storage nem endpoint de download em todo o `src/main/java`.

O que existe são seis colunas `varchar` esperando uma URL que alguém de fora precisa produzir:

| Tabela | Coluna |
|---|---|
| `client`, `driver`, `assistant` | `photo` |
| `vehicle` | `photo_front_url`, `photo_side_url`, `photo_document_url` |
| `driver_cnh` | `photo_url` |
| `driver_document` | `file_url` |

E **ninguém consegue produzir essas URLs**: o app mobile não tem seletor de arquivo nem cliente de storage, o login Google não preenche `photo`, e não há endpoint que receba bytes. Os campos estão vazios porque falta a outra metade.

Isso já bloqueia trabalho: a validação manual de documento do motorista no painel (`vanep-frontend#20`) depende de **ver** um documento que não tem como chegar ao sistema.

### Onde os arquivos vão morar

A #207 especificou Firebase Storage. Na revisão, a decisão mudou: **os arquivos ficam na VPS por enquanto**, e o Firebase vira migração posterior.

O motivo é que o Firebase Storage exige o plano Blaze, e ativá-lo é decisão de faturamento que o time não quer tomar agora. O pedido que veio junto foi explícito: *desenhar de um jeito que fique fácil migrar depois*.

É o que esta change faz. A facilidade da migração não é promessa, são duas decisões concretas: o banco guarda **`object_key`, não URL** (D2), e cada linha carrega o **`provider`** que a serve (D3), o que permite migrar em lote sem downtime.

## What Changes

- Interface **`StorageService`** (`upload`, `open`, `delete`) com **`LocalStorageService`** como implementação; nenhum service de domínio conhece o sistema de arquivos
- Raiz do storage, provider e limites **todos por variável de ambiente**
- Migration **V40** criando `media_file`, com `provider` e `object_key` em vez de URL
- `POST /api/media` — `multipart/form-data`, valida **MIME real** (pelos bytes, não pela extensão) e tamanho
- `GET /api/media/{token}` — metadado; `GET /api/media/{token}/download` — o byte, servido pelo backend com autorização
- `DELETE /api/media/{token}` — soft delete da linha **e** remoção do arquivo
- Limites de multipart no `application.properties`
- `.env.example` documentando as variáveis novas

**Fora de escopo:**

- **Migrar as seis colunas de URL existentes.** É a parte cara e independente; vira issue própria depois que o upload existir.
- **A implementação Firebase.** Esta change entrega a abstração e o caminho de migração desenhado (D9), não o código do provedor.
- **Slot reservado** (`status = PENDING`, binário num `PUT` posterior). A #207 descrevia as duas arquiteturas ao mesmo tempo; esta change escolhe o upload direto (D4).
- Emulador de storage, `tmpfs` e Security Rules — todos eram específicos do Firebase.

## Capabilities

### New Capabilities

- `media-storage`: o sistema recebe, guarda, serve e apaga arquivo binário, com metadado rastreável no banco e local de armazenamento trocável por configuração.

## Impact

- **Schema:** `V40` cria `media_file`. Nenhuma tabela existente é alterada — as seis colunas de URL seguem intactas e sem uso.
- **Disco da VPS entra no caminho crítico.** É o mesmo disco de 50–60 GB que motivou a issue original. Limite por arquivo deixa de ser higiene e vira contenção (R1).
- **Backup muda de natureza.** Até hoje, restaurar o banco restaurava o sistema. A partir daqui existe um diretório cuja perda é irrecuperável: CNH e documento não se regeram (R2).
- **Novo requisito de deploy:** um volume persistente montado fora do container. Sem ele, todo deploy apaga os arquivos.
- **Sem breaking change.** Nenhum endpoint existente muda de contrato.
- **`V40` é o próximo número livre** — `V36` a `V39` estão na pilha aberta #198–#203. A #207 dizia `V36`, escrita antes daquela pilha.
