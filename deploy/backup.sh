#!/usr/bin/env bash
# Run as the deployment user. Only writes to deploy/backups by default.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
umask 077
compose=(docker compose --env-file .env.server -f compose.server.yml)
mkdir -p backups
exec 9>backups/.lock
flock -n 9 || exit 0
backup_stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="backups/$backup_stamp"
mkdir "$backup_dir"
# Stop only running writers and restore their original running state on failure.
mapfile -t writers < <("${compose[@]}" ps --services --status running | awk '/^(backend|worker|image-worker)$/')
restart_writers() {
    if ((${#writers[@]})); then "${compose[@]}" start "${writers[@]}" >/dev/null; fi
}
trap restart_writers EXIT
if ((${#writers[@]})); then "${compose[@]}" stop "${writers[@]}"; fi
"${compose[@]}" exec -T postgres pg_dump -U wardrobe -d wardrobe -Fc > "$backup_dir/database.dump"
"${compose[@]}" run --rm --no-deps -T --entrypoint tar backend -C /data/wardrobe -czf - . > "$backup_dir/uploads.tar.gz"
cp .env.server "$backup_dir/env.server"
cp compose.server.yml "$backup_dir/compose.server.yml"
cp Caddyfile "$backup_dir/Caddyfile"
touch "$backup_dir/COMPLETE"
restart_writers
trap - EXIT
# Only prune complete snapshots after a new snapshot has succeeded.
python3 - "$PWD/backups" <<'PY'
import shutil
import sys
from pathlib import Path
backups = sorted(p for p in Path(sys.argv[1]).iterdir() if p.is_dir() and (p / 'COMPLETE').is_file())
for old in backups[:-7]:
    shutil.rmtree(old)
PY
echo "Backup complete: $PWD/$backup_dir"
