.PHONY: up down restart logs ps build backup smoke deploy

up:
	docker compose up -d --build

down:
	docker compose down

restart:
	scripts/restart.sh

logs:
	scripts/logs.sh

ps:
	docker compose ps

build:
	docker compose build

backup:
	scripts/backup-db.sh

smoke:
	curl -fsS http://127.0.0.1/api/system/status
	curl -fsS http://127.0.0.1/dashboard >/dev/null

deploy:
	scripts/deploy.sh
