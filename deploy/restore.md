Restore drill (do it once before relying on backups):
1. `gunzip -c db-STAMP.sql.gz | docker exec -i codesentinel-postgres psql -U codesentinel -d codesentinel` (into an empty database)
2. `tar -xzf storage-STAMP.tar.gz -C /var/lib/codesentinel`
3. `systemctl restart codesentinel`
