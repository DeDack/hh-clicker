#!/usr/bin/env bash
set -euo pipefail

if ! command -v apt-get >/dev/null 2>&1; then
  echo "Поддерживаются Ubuntu/Debian с apt-get." >&2
  exit 1
fi

if [ "$(id -u)" -ne 0 ]; then
  if ! command -v sudo >/dev/null 2>&1; then
    echo "Запустите от root или установите sudo." >&2
    exit 1
  fi
  SUDO=sudo
else
  SUDO=
fi

. /etc/os-release
if [ "${ID}" != "ubuntu" ] && [ "${ID}" != "debian" ]; then
  echo "Поддерживаются Ubuntu/Debian. Обнаружено: ${ID}" >&2
  exit 1
fi

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  echo "Docker и Docker Compose plugin уже установлены."
else
  $SUDO apt-get update
  $SUDO apt-get install -y ca-certificates curl gnupg
  $SUDO install -m 0755 -d /etc/apt/keyrings
  if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
    curl -fsSL "https://download.docker.com/linux/${ID}/gpg" | $SUDO gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    $SUDO chmod a+r /etc/apt/keyrings/docker.gpg
  fi
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
    | $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
  $SUDO apt-get update
  $SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  $SUDO systemctl enable --now docker
fi

if [ ! -f .env ]; then
  echo ".env не найден. Приложение автоматически не запускаю."
  echo "Создайте файл: cp .env.example .env && nano .env"
fi

cat <<'MSG'

Команды запуска:
  docker compose up -d --build
  docker compose ps
  curl http://127.0.0.1/api/system/status

Скрипт firewall не меняет. Убедитесь, что порт 80 открыт и не занят.
MSG
