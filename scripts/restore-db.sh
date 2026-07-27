#!/usr/bin/env bash
set -euo pipefail

backup="${1:-}"
if [ -z "$backup" ] || [ ! -f "$backup" ]; then
  echo "Использование: scripts/restore-db.sh backups/file.dump.gz" >&2
  exit 1
fi
if [ ! -f .env ]; then
  echo ".env не найден." >&2
  exit 1
fi

set -a
. ./.env
set +a

echo "Будет восстановлена база ${POSTGRES_DB:?POSTGRES_DB is required} из $backup."
read -r -p "Введите RESTORE для подтверждения: " confirm
if [ "$confirm" != "RESTORE" ]; then
  echo "Восстановление отменено."
  exit 1
fi

docker compose stop backend
if echo "$backup" | grep -qE '\.gz$'; then
  gzip -dc "$backup" | docker compose exec -T postgres pg_restore -U "${POSTGRES_USER:?POSTGRES_USER is required}" -d "${POSTGRES_DB:?POSTGRES_DB is required}" --clean --if-exists
else
  docker compose exec -T postgres pg_restore -U "${POSTGRES_USER:?POSTGRES_USER is required}" -d "${POSTGRES_DB:?POSTGRES_DB is required}" --clean --if-exists < "$backup"
fi
docker compose up -d backend
echo "Восстановление завершено."
