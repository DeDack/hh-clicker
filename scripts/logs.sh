#!/usr/bin/env bash
set -euo pipefail
docker compose logs -f --tail="${1:-200}"
