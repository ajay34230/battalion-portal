#!/bin/bash
# Run from the host (WSL2) or via `docker compose exec db bash backup.sh`.
# Usage: ./backup.sh  ->  writes ./backups/battalion_portal_<timestamp>.sql.gz
set -euo pipefail

BACKUP_DIR="$(dirname "$0")/../backups"
mkdir -p "$BACKUP_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/battalion_portal_${TIMESTAMP}.sql.gz"

docker compose exec -T db pg_dump -U "${DB_USER:-battalion_admin}" battalion_portal | gzip > "$FILE"

echo "Backup written to $FILE"

# Retention: keep the last 30 daily backups
find "$BACKUP_DIR" -name "battalion_portal_*.sql.gz" -mtime +30 -delete
