# Запуск HH Clicker на сервере

## 1. Требования

- Ubuntu или Debian.
- Минимум 2 CPU.
- Минимум 4 ГБ RAM.
- Минимум 20 ГБ диска.
- Открытый порт 80.
- Не занятый порт 80: `ss -tulpn | grep ':80'`.

Скрипты не меняют firewall и не трогают VPN. Если на сервере установлен Amnezia VPN, не используйте host network и не ставьте системный reverse proxy поверх него.

## 2. Клонирование

```bash
git clone <repo-url> hh-clicker-clean
cd hh-clicker-clean
```

## 3. Env

Если `.env` отсутствует:

```bash
cp .env.example .env
nano .env
```

Не коммитьте `.env` и не отправляйте его в логи. Обязательные значения: PostgreSQL, JWT secrets, HH session encryption key, HH adapter internal key.

Для первого запуска по IP и HTTP:

```env
APP_COOKIE_SECURE=false
APP_COOKIE_SAME_SITE=Lax
```

Для будущего HTTPS можно поставить `APP_COOKIE_SECURE=true`.

## 4. Docker

Установка Docker на Ubuntu/Debian:

```bash
scripts/install-server.sh
```

Скрипт не запускает приложение, если `.env` отсутствует.

## 5. Запуск

```bash
docker compose up -d --build
```

Или:

```bash
make up
```

## 6. Проверка

```bash
docker compose ps
curl http://127.0.0.1/api/system/status
```

Открыть в браузере:

```text
http://SERVER_IP
```

Локально:

```text
http://localhost
```

## 7. Схема

```text
Браузер -> frontend nginx:80
  /      -> React static files
  /api/  -> backend:8080/api/

backend -> postgres:5432
backend -> hh-adapter:8000
hh-adapter -> HH / KomAPI
```

Наружу опубликован только порт `80`. Backend, Python adapter и PostgreSQL доступны только внутри Docker network.

## 8. Первый admin

Если заполнены `FIRST_ADMIN_EMAIL` и `FIRST_ADMIN_PASSWORD`, backend создаёт первого администратора при старте, если такого пользователя ещё нет.

## 9. Логи

```bash
scripts/logs.sh
docker compose logs -f --tail=200 backend
```

## 10. Обновление

```bash
scripts/deploy.sh
```

Скрипт делает `git pull` только при чистом рабочем дереве, затем `docker compose build`, `docker compose up -d --remove-orphans` и smoke check.

## 11. Backup

```bash
scripts/backup-db.sh
```

Backup сохраняется в `backups/` в сжатом виде.

## 12. Restore

```bash
scripts/restore-db.sh backups/<file>.dump.gz
```

Скрипт запросит подтверждение, остановит backend, восстановит БД и запустит backend обратно.

## 13. Остановка

```bash
scripts/stop.sh
```

## 14. Типовые ошибки

- Порт 80 занят: проверьте `ss -tulpn | grep ':80'`.
- Backend unhealthy: `docker compose logs --tail=200 backend`.
- Database authentication failed: проверьте `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`.
- Adapter unavailable: проверьте `docker compose ps hh-adapter` и `HH_ADAPTER_API_KEY`.
- LLM not configured: заполните `KOMAPI_API_KEY`; сайт при пустом ключе продолжит работать без ИИ.
- HH session expired: обновите сессию HH-аккаунта в интерфейсе.

## 15. Фоновые кампании

После запуска кампании обработка идёт на сервере в Java worker. Закрытие браузера не останавливает кампанию. Java использует `hhAccountId` кампании, загружает зашифрованные cookies конкретного HH-аккаунта, расшифровывает их только перед внутренним запросом и передаёт в Python adapter по Docker network. Python создаёт отдельный HTTP client на запрос и не хранит cookies после завершения запроса. Cookies не отправляются во frontend.
