#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo ".env не найден." >&2
  exit 1
fi

set -a
. ./.env
set +a

mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
file="backups/${POSTGRES_DB:-hh_clicker}-${stamp}.dump.gz"

docker compose exec -T postgres pg_dump -U "${POSTGRES_USER:?POSTGRES_USER is required}" -d "${POSTGRES_DB:?POSTGRES_DB is required}" -Fc | gzip > "$file"
chmod 600 "$file"
echo "Backup создан: $file"
