#!/usr/bin/env bash
set -euo pipefail
docker compose restart
docker compose ps
