#!/usr/bin/env bash
# Valida a numeração das migrations do Flyway antes de chegarem na main.
#
# Os testes rodam com o Flyway desligado (H2 + ddl-auto), então uma versão repetida ou atrasada
# só aparecia no boot de produção ("Found more than one migration with version N").
#
# Uso: scripts/check-migrations.sh [ref-base]   (padrão: origin/main)
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

DIR=src/main/resources/db/migration
BASE="${1:-origin/main}"
errors=0

fail() {
  echo "ERRO: $*" >&2
  errors=$((errors + 1))
}

# 1. Nenhuma versão repetida. O Flyway compara a versão como número: V052 e V52 são a mesma.
declare -A seen
for file in "$DIR"/*; do
  name="$(basename "$file")"
  if [[ ! $name =~ ^V([0-9]+)__.+\.sql$ ]]; then
    fail "$name: nome fora do padrão V<numero>__<descricao>.sql"
    continue
  fi
  version=$((10#${BASH_REMATCH[1]}))
  if [[ -n ${seen[$version]:-} ]]; then
    fail "versão $version repetida: ${seen[$version]} e $name"
  else
    seen[$version]="$name"
  fi
done

# 2. Migration nova tem que ficar acima da última da base: com uma versão maior já aplicada em
#    produção, o Flyway recusa a menor ("Detected resolved migration not applied to database").
if git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null; then
  base_names="$(git ls-tree --name-only "$BASE" "$DIR/" | sed 's|.*/||')"
  base_max=0
  while read -r name; do
    if [[ $name =~ ^V([0-9]+)__ ]] && ((10#${BASH_REMATCH[1]} > base_max)); then
      base_max=$((10#${BASH_REMATCH[1]}))
    fi
  done <<<"$base_names"

  for version in "${!seen[@]}"; do
    name="${seen[$version]}"
    if ! grep -qxF "$name" <<<"$base_names" && ((version <= base_max)); then
      fail "$name: a última migration em $BASE é a V$base_max; renumere para uma versão maior"
    fi
  done
elif [[ $# -gt 0 ]]; then
  fail "ref-base '$BASE' não encontrada"
else
  echo "AVISO: $BASE não encontrada; pulei a comparação com a base (rode 'git fetch origin main')." >&2
fi

if ((errors > 0)); then
  echo "Numeração das migrations inválida ($errors erro(s)) em $DIR." >&2
  exit 1
fi
echo "Migrations OK: ${#seen[@]} arquivos, sem versão repetida nem atrás de $BASE."
