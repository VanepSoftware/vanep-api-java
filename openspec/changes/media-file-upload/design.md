## Context

O sistema tem seis colunas de URL e nenhuma forma de preenchê-las. Esta change entrega a metade que falta.

Ela foi redesenhada depois da revisão das PRs #217 e #218. A primeira versão tinha uma tabela polimórfica e uma API genérica de mídia; o que sobreviveu foi a tabela de metadado e a abstração de storage. A direção da relação, os endpoints e o enum de dono caíram.

Restrição de fundo: **o disco da VPS tem 50–60 GB e já era o problema** — foi por isso que a #207 nasceu querendo tirar binário do servidor. Colocá-lo de volta ali é decisão consciente e temporária.

## Goals / Non-Goals

**Goals**

- O backend recebe, guarda, serve e apaga arquivo
- Renderizar uma imagem custa **uma** chamada de API
- Trocar o local de armazenamento é escrever uma classe e mudar o `.env`
- Arquivo privado não sai sem autorização
- Apagar o registro apaga o byte

**Non-Goals**

- Implementar o provedor Firebase
- Upload direto do app para o bucket, sem passar pelo backend
- Transformação de imagem

## Decisions

### D1 — O dono aponta para a mídia

É a decisão que a revisão impôs, e ela é melhor por três motivos independentes.

```
driver.photo_media_id ──► media_file
```

**Uma requisição.** `GET /api/drivers/{token}` devolve a foto junto. Na direção oposta, o cliente precisaria de uma segunda chamada por imagem.

**Integridade referencial de verdade.** `owner_id` polimórfico não aceita FK: o banco não impede mídia órfã apontando para um dono apagado. Com a FK, impede.

**Sem enum de dono.** `MediaOwnerType` só existia para compensar a ausência da FK. Some.

O custo aceito: uma coluna por slot nas tabelas donas, e um `media_file` não sabe quem o referencia. Nenhum dos dois incomoda, porque todos os seis slots são de **um arquivo só**.

### D2 — Nada de API genérica de mídia

Some `POST /api/media` e `GET /api/media/{token}`. O upload é sempre do dono:

```
POST /api/drivers/{token}/photo
POST /api/vehicles/{token}/photo-front
POST /api/driver-documents/{token}/file
```

A revisão pediu que o upload seguisse a mesma regra da leitura: *"e o mesmo pra upload de imagem, sem depender de eu puxar uma segunda api so pra isso"*. Um endpoint endereçado por `ownerType` + `ownerToken` é a segunda API, e ainda empurra para o cliente a tarefa de saber o tipo do dono — que o servidor já sabe pela rota.

**Ponto a confirmar com o revisor.** "Sem depender de uma segunda API" também admite a leitura de que o upload deveria entrar no próprio `PATCH /api/drivers/{token}`, em multipart, e não numa sub-rota. Aqui foi escolhida a sub-rota, porque misturar JSON e multipart no mesmo endpoint de atualização parcial confunde o contrato, e porque a queixa central era renderizar imagem com duas chamadas — que a sub-rota já resolve.

### D3 — O download também é por dono; o que se compartilha é a implementação

Não existe endpoint genérico de mídia. Nem para upload, nem para download:

```
GET /api/drivers/{token}/photo
GET /api/vehicles/{token}/photo-front
GET /api/driver-documents/{token}/file
```

A primeira versão deste design propunha um endereço único (`/api/media/{token}/download`) com o argumento de que a migração para o Firebase tocaria um lugar só. **O argumento estava errado.** O que a revisão exige é que a imagem venha da API do próprio dono; disso decorre que o que precisa ser compartilhado é a *implementação*, não a URL.

Um `MediaResponder` decide entre transmitir o byte e responder `302` para a URL assinada. Os oito controllers injetam esse componente e chamam uma linha. No dia da migração, muda o `MediaResponder` — um lugar, exatamente como no desenho anterior.

E há um motivo mais forte, que só apareceu ao escrever a autorização. Com o dono apontando para a mídia (D1), **`media_file` não sabe quem o referencia**. Autorizar um endereço genérico exigiria perguntar, a cada download: existe motorista com este `photo_media_id`? cliente? assistente? veículo, em três colunas? CNH? documento? São oito consultas para descobrir o que a rota `/api/drivers/{token}/photo` informa de graça.

No endereço por dono, a autorização é o caminho normal da feature: resolve o motorista e reusa o `is<Entity>Owner` que já existe.

### D4 — O banco guarda `object_key`, nunca URL

URL completa congela o host dentro do banco: no dia de trocar o storage, toda linha vira lixo. A URL é montada na leitura.

```
{ownerType}/{ownerToken}/{purpose}/{mediaToken}.{ext}
```

O `ownerType` continua no **caminho do arquivo** mesmo sem existir como coluna: ele vem da rota, não do cliente, e serve para o diretório ser navegável por um humano em caso de incidente.

### D5 — `provider` por linha, não global

Cada `media_file` guarda quem a serve. É o que permite copiar os binários em lote, marcar as linhas migradas e servir dos dois lugares ao mesmo tempo. Com flag global a virada é tudo-ou-nada, e qualquer arquivo esquecido vira 404 em produção.

### D6 — A `object_key` é derivada, nunca recebida

Montada de enums, tokens opacos gerados por nós e o sufixo do MIME **detectado**. Nada do que o cliente manda entra no caminho; o nome original vai para `original_name`, como metadado.

É a primeira camada contra travessia de diretório: não existe entrada por onde um `../` chegue. A segunda é o `LocalStorageService` recusar key que escape da raiz — convenção e verificação, porque a primeira alguém quebra sem perceber.

### D7 — MIME pelos bytes, não pela extensão

`arquivo.jpg` pode ser qualquer coisa. A validação lê a assinatura do conteúdo e compara com a lista permitida por slot. Extensão e `Content-Type` do request vêm do cliente e só servem para escolher o sufixo.

### D8 — Autorizar antes de abrir o stream

O download resolve a linha, autoriza, e **só então** pede o `InputStream`. O caminho natural é o inverso — abrir o recurso e checar no meio da escrita — e aí o status de erro sai depois de bytes já terem ido. O teste afirma que o corpo do 403 está **vazio**.

### D9 — O campo de resposta não muda de nome nem de tipo

`DriverResponseDTO.photo` continua sendo uma `String`. O que muda é o conteúdo: antes vazio, agora uma URL que funciona.

Nenhum cliente existente quebra na leitura. Quem quebra é quem **escrevia** o campo — e essa é a mudança que a revisão está pedindo de propósito.

### D10 — O fluxo de criação do documento inverte

`driver_document.file_url` é `@NotBlank` e `not null`: criar um documento hoje exige a URL. Como ninguém consegue produzi-la, na prática o endpoint é inutilizável.

Passa a ser: cria-se o documento sem arquivo, e o arquivo sobe depois por `POST /api/driver-documents/{token}/file`. A coluna vira FK anulável, e o documento sem arquivo é um estado legítimo e visível — que é justamente o que o painel precisa listar como "pendente de envio".

### D11 — A migração para o Firebase, desenhada agora

1. `FirebaseStorageService implements StorageService` — a única classe nova
2. Copiar os arquivos preservando o `object_key`
3. Marcar as linhas copiadas como `provider = FIREBASE`
4. O `MediaResponder` passa a responder `302` em vez de transmitir — um lugar, oito rotas inalteradas

Nenhum `media_file` muda de `object_key`. Nenhum DTO muda. Nenhum cliente é tocado.

Custo escondido, registrado para não ser surpresa: URL assinada V4 exige service account com chave privada — credencial de ambiente não assina (R7).

## Risks / Trade-offs

**R1 — O disco da VPS é o gargalo, e era o problema original.** 50–60 GB divididos com o Postgres. Mitigação: limite por arquivo e alerta de uso de disco — sem ele, o sintoma será o Postgres parando de escrever, e ninguém vai associar a upload.

**R2 — Backup muda de natureza.** Existe um diretório cuja perda é irrecuperável. Precisa entrar na rotina **antes** do primeiro upload real.

**R3 — Breaking change em cinco DTOs de request.** Os campos de URL saem. O mais sensível é `ClientUpdateRequestDTO.photo`, que veio da #90 e é consumido pelo formulário do painel (`vanep-frontend#24`): aquele campo precisa virar upload de arquivo na mesma leva.

**R4 — Alteração de schema em cinco tabelas com registro vivo.** As colunas estão todas vazias hoje, o que torna a migração barata — mas é preciso conferir isso em produção antes, não presumir.

**R5 — Nenhum teste executa as migrations.** A suíte roda H2 com `flyway.enabled=false`. Mitigação: aplicar manualmente contra o PostgreSQL, como na `trip`, na #30 e na pilha do rating.

**R6 — Se alguém expuser a pasta pelo nginx, a autorização morre em silêncio.** Não há como o código detectar. Mitigação: documentar no `.env.example` e no deploy.

**R7 — A migração para o Firebase tem custo escondido.** A assinatura V4 precisa de chave privada; deploy com credencial implícita falha só no caminho de leitura de arquivo privado, que é o menos testado.

**R8 — Dezesseis rotas: oito de upload e oito de download.** É o preço de não ter API genérica, e foi escolha explícita da revisão. Mitigação: `MediaFileService` e `MediaResponder` são o motor compartilhado; cada controller informa o slot e delega. Se a repetição incomodar, o caminho é uma classe base por dono, não um endpoint genérico.

## Open Questions

**Q1 — O download compartilhado sobrevive?** ✅ **Resolvido: não.** A revisão mostrou que compartilhar a implementação basta, e a autorização fica mais barata por dono (D3).

**Q2 — O que limpa arquivo órfão?** Trocar a foto de um motorista deixa a anterior no disco. Proposto: apagar a mídia antiga ao substituir, e registrar varredura só se aparecer volume.
