#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
[ -f .env ] || cp .env.example .env
docker compose up --build -d
printf '%s\n' 'Abre http://localhost:3000. Espera unos segundos al primer inicio.'
