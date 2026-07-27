#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo ".env не найден. Создайте его из .env.example перед запуском." >&2
  exit 1
fi

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if git diff --quiet && git diff --cached --quiet; then
    git pull --ff-only || true
  else
    echo "Рабочее дерево не чистое, git pull пропущен."
  fi
fi

docker compose build
docker compose up -d --remove-orphans
docker compose ps

if ! curl -fsS http://127.0.0.1/api/system/status >/dev/null; then
  echo "Smoke check не прошёл. Последние логи:" >&2
  docker compose logs --tail=120 backend frontend hh-adapter >&2
  exit 1
fi

echo "Deploy завершён. Smoke check: OK."
