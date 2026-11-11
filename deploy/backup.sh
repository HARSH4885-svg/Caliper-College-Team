#!/usr/bin/env bash
# Nightly backup of the database and uploaded files. Cron example: 15 2 * * * /opt/codesentinel/deploy/backup.sh
set -euo pipefail
set -a; . /etc/codesentinel/prod.env; set +a
DEST="${BACKUP_DIR:-/var/backups/codesentinel}"; STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$DEST"; umask 077
docker exec codesentinel-postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "$DEST/db-$STAMP.sql.gz"
tar -czf "$DEST/storage-$STAMP.tar.gz" -C "$(dirname "$STORAGE_ROOT")" "$(basename "$STORAGE_ROOT")"
find "$DEST" -type f -mtime +14 -delete
echo "backup written to $DEST ($STAMP). Copy it off this machine too (rclone/restic/scp)."
