# HH Clicker

Monorepo для многопользовательского HH Clicker.

Текущий этап миграции: подготовлен monorepo и выполнено первичное облегчение `hh-adapter`. Python adapter теперь предоставляет internal stateless API для HH/LLM операций; Java backend начал владеть пользовательской моделью, HH-аккаунтами, резюме и feature flag генерации писем.

## Структура

```text
hh-clicker/
├── backend/      # Java 21, Spring Boot 3, PostgreSQL, Liquibase
├── frontend/     # React, TypeScript, Vite
├── hh-adapter/   # Python FastAPI adapter для HH и временно LLM
├── docker-compose.yml
├── .env.example
└── README.md
```

Java backend строится по техническим слоям:

```text
backend/src/main/java/com/hhclicker/
├── config
├── controller
├── dto
│   ├── request
│   └── response
├── entity
├── enumeration
├── exception
├── repository
├── service
└── HhClickerApplication.java
```

Новые Java-классы нужно добавлять в эту layered-структуру, без верхнеуровневых package-by-feature каталогов.

## Запуск

Создайте локальный `.env`, если его ещё нет:

```bash
cp .env.example .env
nano .env
```

Заполните секреты в `.env`. Настоящие ключи нельзя коммитить.

Для первого администратора можно задать:

```env
FIRST_ADMIN_EMAIL=admin@example.com
FIRST_ADMIN_PASSWORD=change-me
```

При старте backend создаст или повысит этого пользователя до `ADMIN`, включит ему генерацию писем и не запишет пароль в открытом виде.

```bash
docker compose up -d --build
```

Снаружи доступен только frontend Nginx:

```text
http://localhost
```

На VPS:

```text
http://SERVER_IP
```

Nginx раздаёт React и проксирует `/api/**` в Java backend. Backend, Python adapter и PostgreSQL наружу не публикуются.

## Основные env

```text
POSTGRES_DB=hh_clicker
POSTGRES_USER=hh_clicker
POSTGRES_PASSWORD=

JWT_ACCESS_SECRET=
JWT_REFRESH_SECRET=
HH_SESSION_ENCRYPTION_KEY=
FIRST_ADMIN_EMAIL=
FIRST_ADMIN_PASSWORD=
HH_ADAPTER_API_KEY=
APP_COOKIE_SECURE=false
APP_COOKIE_SAME_SITE=Lax

LLM_PROVIDER=komapi
KOMAPI_API_KEY=
KOMAPI_BASE_URL=https://www.komapi.top
KOMAPI_MODEL=claude-haiku-4-5
KOMAPI_ANTHROPIC_VERSION=2023-06-01
```

## Целевая архитектура

Java backend владеет пользователями, авторизацией, HH-аккаунтами, зашифрованными cookies, резюме, поисками, кампаниями, вакансиями, письмами, историей откликов и фоновыми задачами.

Python adapter должен быть stateless. Он выполняет только отдельные операции:

- разобрать cURL;
- проверить HH-сессию;
- получить резюме;
- получить вакансии;
- загрузить вакансию;
- отправить один отклик;
- сгенерировать одно письмо;
- вернуть результат Java.

## Python internal API

`hh-adapter` не публикуется наружу через Docker `ports`. Все `/internal/v1/**` endpoints требуют заголовок:

```http
X-Internal-Api-Key: <HH_ADAPTER_API_KEY>
```

`/health` открыт для healthcheck.

Доступные операции:

```text
GET  /health
POST /internal/v1/curl/parse
POST /internal/v1/hh/session/validate
POST /internal/v1/hh/resumes/list
POST /internal/v1/hh/resumes/load
POST /internal/v1/hh/vacancies/search
POST /internal/v1/hh/vacancies/load
POST /internal/v1/hh/applications/apply
POST /internal/v1/cover-letters/generate
GET  /internal/v1/llm/status
```

Каждый запрос приносит cookies/headers или данные резюме/вакансии в теле. Adapter не знает `userId`, `hhAccountId`, campaign ID, роли и JWT пользователя.

Из `hh-adapter` удалены старые Jinja-страницы, публичные `/api/...` routes, файловый storage, snapshot store и thread worker.

## Генерация писем

У пользователя есть флаг:

```text
cover_letter_generation_enabled=false
```

Обычные пользователи по умолчанию не имеют доступа к LLM-генерации. Java backend должен проверять флаг перед массовой генерацией и регенерацией письма. Ручной ввод общего или персонального письма этим флагом не запрещается.

Admin scaffold:

```text
GET   /api/admin/users
GET   /api/admin/users/{id}
PATCH /api/admin/users/{id}/features
PATCH /api/admin/users/{id}/status
```

## Java API MVP

Auth:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

HH accounts:

```text
GET    /api/hh-accounts
POST   /api/hh-accounts
GET    /api/hh-accounts/{id}
PUT    /api/hh-accounts/{id}
DELETE /api/hh-accounts/{id}
POST   /api/hh-accounts/{id}/check
POST   /api/hh-accounts/{id}/refresh-session
POST   /api/hh-accounts/{id}/resumes/sync
```

Resumes:

```text
GET  /api/resumes
GET  /api/resumes/{id}
POST /api/resumes/{id}/refresh?hhAccountId=...
PUT  /api/resumes/{id}/profile
```

Saved searches:

```text
GET    /api/saved-searches
POST   /api/saved-searches
GET    /api/saved-searches/{id}
PUT    /api/saved-searches/{id}
DELETE /api/saved-searches/{id}
```

Campaigns:

```text
GET    /api/campaigns
POST   /api/campaigns
GET    /api/campaigns/{id}
DELETE /api/campaigns/{id}
POST   /api/campaigns/{id}/preview
GET    /api/campaigns/{id}/state
POST   /api/campaigns/{id}/cover-letters/generate
POST   /api/campaigns/{id}/apply
POST   /api/campaigns/{id}/stop
GET    /api/campaigns/{campaignId}/vacancies
PUT    /api/campaigns/{campaignId}/vacancies/{vacancyId}
PUT    /api/campaigns/{campaignId}/vacancies/{vacancyId}/cover-letter
POST   /api/campaigns/{campaignId}/vacancies/{vacancyId}/cover-letter/regenerate
```

System:

```text
GET /api/system/status
GET /api/system/adapter/status
GET /api/system/llm/status
```

Тело обновления:

```json
{
  "coverLetterGenerationEnabled": true
}
```

`/api/auth/me` возвращает:

```json
{
  "id": "...",
  "email": "...",
  "role": "USER",
  "features": {
    "coverLetterGenerationEnabled": false
  }
}
```

## Статус миграции

Сделано:

- создана monorepo-структура;
- текущий Python код перенесён в `hh-adapter` и очищен от public UI/stateful orchestration;
- добавлен Java Spring Boot backend skeleton;
- добавлен React/Vite frontend skeleton;
- добавлен PostgreSQL в compose;
- добавлены Liquibase migrations для основных таблиц;
- Python adapter скрыт внутри Docker network.
- добавлен internal API key для adapter;
- добавлены JPA entities `User`, `HhAccount`, `Resume`, `SavedSearch`, `ApplicationCampaign`;
- добавлен флаг доступа к LLM-генерации и admin endpoint scaffold для его переключения.
- реализованы JWT auth, refresh rotation и logout;
- реализовано AES-GCM шифрование HH cookies/headers;
- реализованы HH accounts, resumes, saved searches, campaign preview, генерация писем и apply worker;
- добавлен рабочий React MVP frontend;
- добавлены admin status/feature endpoints и seed первого admin через env.

Следующие этапы:

1. Углубить test coverage до полного списка acceptance-сценариев.
2. Добавить rate limiting для login/register/import/generation/apply.
3. Довести frontend UX до production-уровня и добавить frontend tests.
4. Добавить отдельные интеграционные тесты с MockWebServer/WireMock для adapter client.
5. Добавить pagination/filtering для больших списков кампаний и вакансий.

## Проверки

```bash
docker run --rm -v "$PWD/backend:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -q test
docker run --rm -e PYTHONPATH=/work -v "$PWD/hh-adapter:/work" -w /work hh-clicker-clean-hh-adapter:latest pytest -q
docker compose build
docker compose up -d
docker compose ps
```

## Ручной сценарий

1. Открыть `http://127.0.0.1:5173/register` и создать пользователя.
2. Войти через `/login`.
3. Под admin включить пользователю генерацию писем на `/admin/users`.
4. На `/hh-accounts` добавить HH account через `Copy as cURL`.
5. На `/resumes` синхронизировать резюме и заполнить `candidateProfile`.
6. На `/searches` сохранить HH search URL.
7. На `/campaigns/new` создать campaign.
8. В campaign нажать `Preview`, дождаться вакансий.
9. Включить/исключить вакансии, сгенерировать или вручную сохранить письма.
10. Нажать `Отправить`, при необходимости `Stop`.

## Ограничения MVP

- Rate limiting пока не реализован.
- Тесты покрывают критические compile/unit smoke, но не весь длинный acceptance matrix.
- Frontend рабочий, но минимальный: без полноценных toast notifications и сложной валидации форм.
- Старый orphan-контейнер прежнего сервиса может оставаться локально, если он был запущен до миграции; удалить можно вручную через `docker compose up -d --remove-orphans`.
