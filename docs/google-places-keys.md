# Como gerar suas chaves do Google Places

Guia para quem vai **desenvolver ou testar** a Vanep e precisa que a resolução de endereços funcione na sua máquina.

Leva uns 15 minutos. Você vai sair daqui com até 4 chaves, dependendo do que for mexer.

---

## Antes de começar: por que são várias chaves

Essa é a parte que confunde todo mundo, então vale entender antes de clicar.

O Google tem **dois campos de restrição** que parecem a mesma coisa e não são:

| Campo | Responde | Quantos valores |
|---|---|---|
| **Restrições de aplicação** | *quem* pode usar esta chave | **um tipo só** — IP **ou** Sites **ou** Android **ou** iOS |
| **Restrições de API** | *quais APIs* esta chave pode chamar | quantas quiser |

O "é um ou é outro" vale só para o **primeiro** campo, e é limitação do Google. Por isso o backend (restrito por IP) não pode usar a mesma chave que o app (restrito por package). Não é escolha nossa.

E "Places API (New)" **não pertence** a uma chave — é uma permissão que você concede a quantas quiser.

## Quais você precisa

| Você vai mexer em | Precisa de |
|---|---|
| `vanep-api-java` | chave de **servidor** |
| `vanep-frontend` | chave **web** |
| `vanep-mobile` no Android | chave **Android** |
| `vanep-mobile` no iOS | chave **iOS** |

Se for rodar a stack inteira, precisa das quatro.

---

## Passo 0 — Acesso ao projeto

Peça ao Joao acesso ao projeto do Google Cloud (é o **mesmo projeto do Firebase**). Você precisa do papel que permita criar credenciais.

Confirme duas coisas antes de seguir, senão nada funciona:

1. **Billing vinculada.** `Billing` no menu lateral. As APIs do Maps Platform não respondem sem billing **mesmo dentro da cota gratuita**.
2. **Places API (New) habilitada.** `APIs & Services → Enabled APIs`. Precisa aparecer **Places API (New)** — se aparecer só "Places API", é a legada e não serve.

> Se você vir ~35 APIs habilitadas no projeto, está normal: um projeto Firebase habilita várias. Não desabilite nada, o Firebase precisa delas.

---

## Passo 1 — Chave de servidor (backend)

`APIs & Services → Credentials → Create credentials → API key`. Renomeie para algo como `vanep-server-SEUNOME`.

**Restrições de aplicação → `Endereços IP`.** Descubra os seus:

```bash
echo "IPv4: $(curl -s -4 ifconfig.me)"
echo "IPv6: $(curl -s -6 ifconfig.me)"
```

Coloque **os dois**. Se você tem IPv6, a chamada para o Google normalmente sai por ele — restringir só ao IPv4 dá `403` com tudo o mais certo.

No IPv6, use só o **prefixo `/64`**, não o endereço completo:

```
189.10.20.30
2804:214:3c:2c6f::/64      ← os últimos blocos rotacionam por privacidade
```

**Restrições de API → `Restrict key` → `Places API (New)`.**

No `.env` do `vanep-api-java`:

```
GOOGLE_PLACES_API_KEY=<sua chave>
```

**Teste:**

```bash
cd vanep-api-java && set -a && source .env && set +a
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://places.googleapis.com/v1/places/ChIJiQLoU9TMW5MRbx2OMMN5r-o" \
  -H "X-Goog-Api-Key: $GOOGLE_PLACES_API_KEY" \
  -H "X-Goog-FieldMask: id"
```

Esperado: `200`.

> ⚠️ **Seu IP residencial é dinâmico.** Quando o roteador renovar, essa chave para de funcionar sozinha e o sintoma no app é um genérico "algo deu errado". Se acontecer, rode `curl -4 ifconfig.me` de novo e atualize a chave. Considere autorizar o range do seu provedor (ex.: `189.10.0.0/16`) para não precisar mexer toda semana — em dev, com quota diária baixa, o risco é pequeno.

---

## Passo 2 — Chave web (frontend)

Crie outra chave. **Restrições de aplicação → `Sites`:**

```
http://localhost:3000/*
```

**Restrições de API → `Restrict key` → `Places API (New)`.**

Em `vanep-frontend/.env`:

```
GOOGLE_PLACES_API_KEY=<sua chave web>
```

**Teste:**

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://places.googleapis.com/v1/places/ChIJiQLoU9TMW5MRbx2OMMN5r-o" \
  -H "X-Goog-Api-Key: <sua chave web>" \
  -H "X-Goog-FieldMask: id" \
  -H "Referer: http://localhost:3000/"
```

Esperado: `200` com o `Referer`, e `403` sem ele. **Teste os dois** — se passar sem `Referer`, a restrição não está aplicada e a chave está exposta.

> Chave web é **pública por natureza**: vai no bundle JavaScript e qualquer um lê no DevTools. A restrição por referrer não impede a cópia — impede que a cópia **funcione** fora do domínio. É a única proteção que ela tem, e é por isso que nunca pode ser a mesma do servidor.

---

## Passo 3 — Chaves mobile (uma por plataforma)

### Android

Pegue o SHA-1 do seu keystore de debug:

```bash
keytool -J-Duser.language=en -J-Duser.country=US -list -v \
  -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android | grep SHA1
```

> O `-J-Duser.language=en` é obrigatório: o `keytool` do JDK 25 quebra com locale pt-BR (`MissingFormatArgumentException`).

Crie a chave. **Restrições de aplicação → `Apps Android`:**

| Campo | Valor |
|---|---|
| Nome do pacote | `com.vanep.vanep_mobile` |
| SHA-1 | o que saiu do comando acima |

### iOS

Outra chave. **Restrições de aplicação → `Apps iOS`**, bundle id `com.vanep.vanepMobile`.

> Repare: Android usa `vanep_mobile` com underscore, iOS usa `vanepMobile` em camelCase. Trocar os dois dá `403` sem mensagem clara.

Nas duas: **`Restrict key` → `Places API (New)`.**

Em `vanep-mobile/.env`:

```
GOOGLE_PLACES_API_KEY_ANDROID=<chave android>
GOOGLE_PLACES_API_KEY_IOS=<chave ios>

GOOGLE_PLACES_ANDROID_PACKAGE=com.vanep.vanep_mobile
GOOGLE_PLACES_ANDROID_CERT_SHA1=<seu SHA-1, com os dois-pontos mesmo>
GOOGLE_PLACES_IOS_BUNDLE_ID=com.vanep.vanepMobile
```

As três últimas não são opcionais. O app chama o Places por HTTP direto, e **o SDK nativo é quem normalmente anexa a identidade do app** — numa chamada HTTP ela precisa ir à mão, senão a chave restrita recusa com `403 API_KEY_ANDROID_APP_BLOCKED`.

**Teste (Android):**

```bash
cd vanep-mobile && set -a && source .env && set +a
SHA1=$(echo "$GOOGLE_PLACES_ANDROID_CERT_SHA1" | tr -d ':')
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://places.googleapis.com/v1/places/ChIJiQLoU9TMW5MRbx2OMMN5r-o" \
  -H "X-Goog-Api-Key: $GOOGLE_PLACES_API_KEY_ANDROID" \
  -H "X-Goog-FieldMask: id" \
  -H "X-Android-Package: com.vanep.vanep_mobile" \
  -H "X-Android-Cert: $SHA1"
```

Esperado: `200` com os headers, `403` sem eles.

---

## Passo 4 — Quota diária

Para cada chave: `APIs & Services → Places API (New) → Quotas`, defina um teto diário.

**300/dia** é uma boa referência: a cota gratuita é de 10.000/mês por SKU, e 10.000 ÷ 30 ≈ 333. Ficar abaixo disso te tranca dentro do gratuito **por construção** — se algo entrar em loop, o sintoma é erro de quota, não boleto.

---

## Por que o mask `id` nos testes acima

Todos os `curl` deste guia pedem só `id`, que cai no SKU *Place Details Essentials **IDs Only*** — **gratuito e ilimitado**. Você pode testar à vontade sem consumir cota.

Pedir `addressComponents` já sobe para *Essentials* (10.000/mês grátis).

---

## Quando algo der 403

Leia o `reason` do JSON **antes** de suspeitar do código:

```bash
curl -s "https://places.googleapis.com/v1/places/ChIJiQLoU9TMW5MRbx2OMMN5r-o" \
  -H "X-Goog-Api-Key: $SUA_CHAVE" -H "X-Goog-FieldMask: id" \
  | python3 -m json.tool
```

| `reason` | Causa | O que fazer |
|---|---|---|
| `API_KEY_IP_ADDRESS_BLOCKED` | seu IP mudou, ou falta o IPv6 | atualize os IPs na chave |
| `API_KEY_ANDROID_APP_BLOCKED` | faltam os headers de identidade do app | confira as 3 variáveis do passo 3 |
| `API_KEY_IOS_APP_BLOCKED` | idem, bundle id | idem |
| `API_KEY_HTTP_REFERRER_BLOCKED` | referrer fora da lista | adicione a origem na chave web |
| `SERVICE_DISABLED` | Places API (New) não habilitada | habilite e espere ~2 min |
| quota | teto diário estourado | espere o reset ou aumente |

Nenhum desses é bug de código. Vale checar sempre antes de abrir issue.

---

## Nunca commite as chaves

Os `.env` estão no `.gitignore` dos três repositórios. Cada pessoa cria as suas — não compartilhe por chat nem reuse a chave de outra pessoa: a restrição dela é do IP/keystore **dela** e não vai funcionar na sua máquina de qualquer forma.

---

## Para publicar (só quando chegar a hora)

O SHA-1 do passo 3 é do **keystore de debug** e vale só para builds locais.

Antes de publicar na Play Store, o SHA-1 do keystore de **release** precisa entrar na chave Android do console. Sem isso o autocomplete funciona em desenvolvimento e falha no app publicado, com `403` e nenhuma pista da causa.
