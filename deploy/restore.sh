#!/bin/bash
# Usage: ./restore.sh backups/battalion_portal_20260101_020000.sql.gz
set -euo pipefail

if [ -z "${1:-}" ]; then
  echo "Usage: $0 <path-to-backup.sql.gz>"
  exit 1
fi

echo "This will REPLACE all data in the battalion_portal database. Ctrl+C to abort."
sleep 5

gunzip -c "$1" | docker compose exec -T db psql -U "${DB_USER:-battalion_admin}" battalion_portal

echo "Restore complete."
