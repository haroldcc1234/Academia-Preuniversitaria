#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -u root --single-transaction --no-tablespaces academia_jauja' > "backups/database-$stamp.sql"
docker compose exec -T backend tar -C /app/private-files -czf - . > "backups/files-$stamp.tar.gz"
printf 'Copias creadas en backups/ con identificador %s\n' "$stamp"
