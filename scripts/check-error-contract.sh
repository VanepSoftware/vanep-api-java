#!/usr/bin/env bash
# Valida as invariantes do contrato RFC 9457 (VAN-89) sem dependências externas.
#
# Regras verificadas:
# 1. Nenhuma ocorrência de ResponseStatusException fora de ApiErrorAdvice.java.
# 2. Apenas um @RestControllerAdvice em src/main/java (ApiErrorAdvice.java).
# 3. Nenhum resquício dos envelopes legados (AuthErrorAdvice/ProfileErrorAdvice/DTOs).
# 4. Toda chave literal passada a ApiProblemException existe em messages.properties e messages_pt_BR.properties.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

MAIN_JAVA="src/main/java"
EN_BUNDLE="src/main/resources/messages.properties"
PT_BUNDLE="src/main/resources/messages_pt_BR.properties"
errors=0

fail() {
  echo "ERRO: $*" >&2
  errors=$((errors + 1))
}

# 1. Zero ResponseStatusException fora de ApiErrorAdvice.java.
bad_rse="$(grep -rn "ResponseStatusException" "$MAIN_JAVA" | grep -v "br/com/vanep/config/ApiErrorAdvice.java:" || true)"
if [[ -n "$bad_rse" ]]; then
  fail "ResponseStatusException encontrado fora de ApiErrorAdvice:"
  echo "$bad_rse" >&2
fi

# 2. Único @RestControllerAdvice.
advices="$(grep -rl "@RestControllerAdvice" "$MAIN_JAVA" || true)"
advice_count="$(printf '%s\n' "$advices" | grep -c '.' || true)"
if [[ "$advice_count" -ne 1 || "$advices" != "src/main/java/br/com/vanep/config/ApiErrorAdvice.java" ]]; then
  fail "Esperado apenas src/main/java/br/com/vanep/config/ApiErrorAdvice.java com @RestControllerAdvice, encontrado:"
  echo "${advices:-(nenhum)}" >&2
fi

# 3. Nenhum envelope legado de auth/profile.
legacy="$(find "$MAIN_JAVA" -type f \( -name "AuthErrorAdvice.java" -o -name "AuthErrorResponseDTO.java" -o -name "ProfileErrorAdvice.java" -o -name "ProfileErrorResponseDTO.java" \) || true)"
if [[ -n "$legacy" ]]; then
  fail "Artefatos legados de erro ainda presentes:"
  echo "$legacy" >&2
fi

# 4. Chaves literais em ApiProblemException.* existem nos dois bundles.
extract_keys() {
  grep -vE '^\s*(#|$)' "$1" | sed -n 's/^\([^=[:space:]]*\)=.*/\1/p'
}

en_keys="$(extract_keys "$EN_BUNDLE")"
pt_keys="$(extract_keys "$PT_BUNDLE")"

while IFS=: read -r file line key; do
  [[ -z "$key" ]] && continue
  if ! grep -qxF "$key" <<<"$en_keys"; then
    fail "$file:$line usa chave '$key' ausente em $EN_BUNDLE"
  fi
  if ! grep -qxF "$key" <<<"$pt_keys"; then
    fail "$file:$line usa chave '$key' ausente em $PT_BUNDLE"
  fi
done < <(
  grep -rnE 'ApiProblemException\.(badRequest|unauthorized|forbidden|notFound|conflict|unprocessable|tooManyRequests|serviceUnavailable|of)\(' "$MAIN_JAVA" \
    | sed -nE 's/^([^:]+:[0-9]+):.*ApiProblemException\.[A-Za-z]+\((HttpStatus\.[A-Z_]+,[[:space:]]*)?"([a-z0-9_.]+)".*/\1:\3/p'
)

if ((errors > 0)); then
  echo "Contrato de erros inválido ($errors erro(s))." >&2
  exit 1
fi

echo "Contrato de erros OK: sem ResponseStatusException em serviços, único @RestControllerAdvice e chaves validadas."
