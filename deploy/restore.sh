#!/usr/bin/env bash
# Explicitly destructive: stop writers and replace the database and clothing files.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
snapshot=${1:?Usage: restore.sh /absolute/path/to/snapshot --replace-current-data}
[[ ${2:-} == --replace-current-data ]] || { echo 'Pass --replace-current-data to confirm data replacement.' >&2; exit 1; }
snapshot=$(realpath "$snapshot")
[[ -f "$snapshot/COMPLETE" && -s "$snapshot/database.dump" && -s "$snapshot/uploads.tar.gz" ]] || { echo 'Incomplete backup.' >&2; exit 1; }
compose=(docker compose --env-file .env.server -f compose.server.yml)
"${compose[@]}" up -d --wait postgres redis
"${compose[@]}" stop backend worker image-worker
# Keep writers stopped on a failed restore; restarting could overwrite recoverable data.
"${compose[@]}" exec -T postgres pg_restore -U wardrobe -d wardrobe --clean --if-exists --no-owner --exit-on-error < "$snapshot/database.dump"
"${compose[@]}" run --rm --no-deps -T --entrypoint sh backend -c 'find /data/wardrobe -mindepth 1 -delete'
"${compose[@]}" run --rm --no-deps -T --entrypoint tar backend -C /data/wardrobe -xzf - < "$snapshot/uploads.tar.gz"
"${compose[@]}" up -d --wait backend worker image-worker frontend caddy
echo 'Restore completed. Verify login, clothing photos and saved outfits.'
