## Context

O sistema tem seis colunas de URL e nenhuma forma de preenchê-las. Esta change entrega a metade que falta.

A #207 foi escrita assumindo Firebase Storage. A revisão mudou o destino para a VPS, mantendo o Firebase como migração futura. O que sobreviveu da issue original foi a parte que importa: a abstração de storage e o metadado em tabela própria. O que caiu foi tudo que era específico do Firebase.

Restrição de fundo, e é ela que aperta: **o disco da VPS tem 50–60 GB e já era o problema** — foi por isso que a #207 nasceu querendo tirar binário do servidor. Colocá-lo de volta ali é decisão consciente e temporária.

## Goals / Non-Goals

**Goals**

- O backend recebe, guarda, serve e apaga arquivo
- Trocar o local de armazenamento é escrever uma classe e mudar o `.env`
- Arquivo privado não sai sem autorização
- Apagar o registro apaga o byte

**Non-Goals**

- Migrar as seis colunas de URL existentes
- Implementar o provedor Firebase
- Upload direto do app para o bucket, sem passar pelo backend
- Transformação de imagem (thumbnail, resize, compressão)

## Decisions

### D1 — A abstração é o contrato, não a implementação

```java
public interface StorageService {
  StoredObject upload(String objectKey, InputStream content, String mimeType, long sizeBytes);
  InputStream open(String objectKey);
  void delete(String objectKey);
}
```

`LocalStorageService` é a implementação de hoje. Nenhum controller ou service de domínio toca em `Path`, `File` ou `Files`.

Isso não é cerimônia: é a única coisa que torna a migração barata. Se `DriverDocumentService` soubesse que existe um diretório, migrar para o Firebase significaria caçar essa suposição por todo o código.

### D2 — O banco guarda `object_key`, nunca URL

URL completa congela o host dentro do banco. No dia da migração, toda linha vira lixo.

Com a key, a URL é montada na leitura, a partir da configuração do provider ativo:

```
{ownerType}/{ownerToken}/{purpose}/{mediaToken}.{ext}
```

O mesmo caminho vale para diretório na VPS e para objeto no bucket. É o que permite copiar os arquivos preservando a estrutura e não tocar em uma linha do banco.

### D3 — `provider` por linha, não global

Cada `media_file` guarda quem a serve: `LOCAL` ou `FIREBASE`.

Poderia ser uma variável de ambiente só. É por linha porque **migração em lote precisa de estado intermediário**: copia-se um bloco de arquivos, marcam-se aquelas linhas como `FIREBASE`, e o sistema passa a servir dos dois lugares ao mesmo tempo, sem downtime.

Com um flag global, a virada é tudo-ou-nada, e qualquer arquivo esquecido vira 404 em produção.

### D4 — Upload direto, sem slot reservado

A #207 descrevia duas arquiteturas ao mesmo tempo: `POST /api/media` recebendo o arquivo (upload direto), e `status PENDING | UPLOADED | FAILED` com o precedente do `containerapp/checklists` (slot reservado, binário depois num `PUT`).

As duas não convivem. Esta change escolhe **upload direto** e **não cria a coluna `status`**.

O slot serve para offline-first e retry: o app salva o formulário agora e envia o byte quando tiver rede. É real, mas é necessidade do mobile, que hoje não tem nem seletor de arquivo. Construir o estado antes do caso de uso é carregar complexidade sem cliente.

Quando o app precisar, o slot entra — e o `provider` por linha já mostra que a tabela aguenta ganhar coluna.

### D5 — Quem serve o byte é a aplicação

O arquivo fica num diretório que **o servidor web não expõe**. Nada de `alias` no nginx apontando para a pasta.

Se o binário sair sem passar pela aplicação, a autorização por dono vira decoração: basta adivinhar o caminho para baixar a CNH de qualquer motorista. O `object_key` tem token opaco justamente para dificultar isso, mas obscuridade não é controle de acesso.

Em troca, o download consome thread do servidor. Para foto de perfil e documento, com o volume desta fase, é irrelevante — e some quando a migração para o bucket acontecer.

### D6 — `<img src>` não manda header, e o painel resolve isso pelo proxy

Uma tag de imagem não envia `Authorization`. Se o painel apontar direto para o backend, toma 401.

A saída já existe no `vanep-frontend`: o proxy do Next anexa o bearer no servidor, e o navegador manda só o cookie de sessão. A tela de clientes já funciona assim.

Rejeitado: URL assinada com HMAC próprio. Seria reimplementar, à mão, o que o Firebase dá pronto — e a assinatura própria tem o mesmo defeito da assinada do Firebase (link vazado é acesso vazado) sem nenhuma das vantagens. Com o proxy, o byte nunca fica acessível sem sessão.

No mobile não há problema: o app manda o header direto.

### D7 — MIME pelos bytes, não pela extensão

`arquivo.jpg` pode ser qualquer coisa. A validação lê a assinatura do conteúdo e compara com a lista permitida por `purpose` — imagem para foto, imagem ou PDF para documento.

Extensão e `Content-Type` do request vêm do cliente e não são confiáveis. Só servem para escolher o sufixo do `object_key`.

### D8 — O arquivo mora fora do container e fora do repositório

Raiz por variável de ambiente, apontando para um volume persistente montado no container.

Dentro do container, todo deploy apagaria tudo. Dentro do repositório, o primeiro `git add .` distribuiria documento pessoal.

### D9 — A migração para o Firebase, desenhada agora

O pedido da revisão foi "fácil de migrar". Concretamente, o dia da migração é:

1. `FirebaseStorageService implements StorageService` — a única classe nova
2. Copiar os arquivos preservando o `object_key` como caminho no bucket
3. Marcar as linhas copiadas como `provider = FIREBASE`
4. Apontar a leitura do provider ativo para o novo padrão

Nenhum `media_file` muda de `object_key`. Nenhum service de domínio é tocado. Nenhum endpoint muda de contrato.

O que **vai** precisar de trabalho no Firebase, e fica registrado para não ser surpresa: URL assinada V4 exige service account com chave privada de verdade — credencial de ambiente não assina. É o item que pode custar um dia (R7).

### D10 — O contrato do cliente **não muda** entre providers

Esta é a decisão que mais barateia a migração, e é fácil de errar.

A tentação é devolver, na leitura, a URL nativa de cada provider: caminho do backend hoje, URL assinada do Firebase amanhã. Isso quebraria painel e app no dia da virada — cada cliente teria que aprender a lidar com duas formas de URL.

Em vez disso, a leitura **sempre** devolve o mesmo endereço:

```
GET /api/media/{token}/download
```

O que muda é só o que o backend faz por dentro:

| provider | comportamento interno |
|---|---|
| `LOCAL` | abre o arquivo e transmite os bytes |
| `FIREBASE` | gera a URL assinada e responde `302` para ela |

O cliente segue redirect sozinho, sem uma linha de código nova. Painel e app não ficam sabendo da migração — nem no dia dela, nem depois, com metade dos arquivos de cada lado.

Efeito colateral bom: a autorização continua num lugar só, antes do redirect. Não existe caminho em que o byte sai sem passar pelo Spring.

### D11 — O `object_key` é **derivado**, nunca recebido

O caminho do arquivo é montado no servidor a partir de enums (`ownerType`, `purpose`), de tokens opacos gerados por nós e de um sufixo escolhido pelo MIME detectado.

**Nada do que o cliente manda entra no caminho.** O nome original do arquivo é guardado em `original_name` como metadado, para exibição, e não toca o sistema de arquivos.

É o que fecha o buraco de travessia de diretório na origem: não existe entrada por onde um `../../etc/passwd` chegue. A checagem de que o caminho final continua sob a raiz (task 1.10) continua valendo, mas como **defesa em profundidade** — se alguém um dia alterar a montagem da key, o `LocalStorageService` ainda recusa.

Duas camadas, porque uma delas é uma convenção que alguém pode quebrar sem perceber, e a outra é uma verificação que falha alto.

### D12 — Autorizar **antes** de abrir o stream

O download resolve a linha, autoriza, e **só então** pede o `InputStream` ao `StorageService`.

Parece óbvio, mas o caminho natural é o inverso: abrir o recurso, começar a escrever na resposta e checar permissão no meio. Aí o status de erro sai depois de bytes já terem ido para o cliente — e a resposta vira um 403 com conteúdo dentro.

Por isso a autorização não mora no controller do download: ela mora antes, e o método que entrega o stream só é chamado quando a decisão já foi tomada. O teste da task 3.3 guarda isso afirmando que o corpo do 403 está **vazio**, não só que o status é 403.

## Risks / Trade-offs

**R1 — O disco da VPS é o gargalo, e era o problema original.** São 50–60 GB dividido com o Postgres. Mitigação: limite por arquivo (5 MB foto, 10 MB documento), e alerta de uso de disco — sem ele, o sintoma será o Postgres parando de escrever, e ninguém vai associar a upload.

**R2 — Backup muda de natureza.** Hoje restaurar o banco restaura o sistema. A partir daqui existe um diretório cuja perda é irrecuperável: CNH e documento não se regeram. Precisa entrar na rotina de backup **antes** do primeiro upload real.

**R3 — `MultipartFile` faz spool em disco.** O Tomcat grava o upload em arquivo temporário assim que passa de `file-size-threshold`. Ou seja, o byte encosta no disco antes de ser movido. Como o destino agora é o mesmo disco, o efeito é menor que seria com bucket — mas o limite de tamanho continua sendo a contenção real.

**R4 — Nenhum teste executa a `V40`.** A suíte roda H2 com `flyway.enabled=false`. Mitigação: aplicar manualmente contra o PostgreSQL, como na `trip`, na #30 e na pilha do rating.

**R5 — Se alguém expuser a pasta pelo nginx, a autorização morre em silêncio.** Não há como o código detectar isso. Mitigação: documentar no `.env.example` e no deploy, e manter a raiz fora de qualquer diretório servido.

**R6 — `V40` pode ser tomada.** A pilha #198–#203 usa `V36` a `V39` e ainda não foi mergeada. Se outra entrar antes, reconfirmar.

**R7 — A migração para o Firebase tem um custo escondido.** A assinatura V4 precisa de chave privada; deploy com credencial implícita falha só no caminho de leitura de arquivo privado, que é o menos testado. Descobrir isso no primeiro dia da migração, não no último.

## Open Questions

**Q1 — Quem, além do dono, lê um documento de motorista?** ✅ **Decidido: o dono e o admin.**

Concretamente, uma permissão única `show_media`, no bundle `ADMIN`, mais o acesso do dono pelo `SecurityEvaluator`.

Rejeitado: herdar a permissão da feature dona (`show_driver_document` para documento, `show_driver_cnh` para CNH). Seria mais granular, mas exige uma tabela de-para entre `owner_type` e permissão, que precisa ser lembrada a cada `owner_type` novo — e esquecer uma entrada falha abrindo, não fechando.

O trade-off aceito: quem tem `show_media` lê arquivo de qualquer feature. Não é escalada de privilégio, porque o admin já lê esses mesmos documentos pelos endpoints das próprias features. Se um dia existir perfil administrativo parcial, a granularidade volta à mesa.

**Q2 — O que limpa arquivo órfão?** `DELETE` apaga o byte, mas um upload interrompido entre gravar o arquivo e commitar a linha deixa lixo. Proposto: aceitar nesta fase e registrar; uma rotina de varredura só se justifica com volume.
