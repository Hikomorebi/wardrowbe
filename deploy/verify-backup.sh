#!/usr/bin/env bash
# Restore a snapshot into an isolated temporary database; production is untouched.
set -euo pipefail
snapshot=$(realpath "${1:?Usage: verify-backup.sh /absolute/path/to/snapshot}")
[[ -f "$snapshot/COMPLETE" ]] || { echo 'Incomplete backup.' >&2; exit 1; }
test_container="wardrowbe-restore-check-$$"
test_files=$(mktemp -d)
cleanup() {
    docker rm -fv "$test_container" >/dev/null 2>&1 || true
    rm -rf "$test_files"
}
trap cleanup EXIT
docker run -d --name "$test_container" --network none \
    -e POSTGRES_USER=wardrobe -e POSTGRES_DB=wardrobe \
    -e POSTGRES_HOST_AUTH_METHOD=trust postgres:15-alpine >/dev/null
ready=false
for ((attempt=0; attempt<60; attempt++)); do
    if docker exec "$test_container" pg_isready -h 127.0.0.1 -U wardrobe -d wardrobe >/dev/null 2>&1; then
        ready=true; break
    fi
    sleep 1
done
[[ $ready == true ]] || { echo 'Restore database did not start.' >&2; exit 1; }
docker exec -i "$test_container" pg_restore -U wardrobe -d wardrobe --no-owner --exit-on-error < "$snapshot/database.dump"
python3 - "$snapshot/uploads.tar.gz" "$test_files" <<'PY'
import sys
import tarfile
with tarfile.open(sys.argv[1]) as archive:
    archive.extractall(sys.argv[2], filter="data")
PY
docker exec "$test_container" psql -U wardrobe -d wardrobe -At -c '
SELECT image_path FROM clothing_items
UNION SELECT thumbnail_path FROM clothing_items WHERE thumbnail_path IS NOT NULL
UNION SELECT medium_path FROM clothing_items WHERE medium_path IS NOT NULL
UNION SELECT original_image_path FROM clothing_items WHERE original_image_path IS NOT NULL
UNION SELECT image_path FROM item_images
UNION SELECT thumbnail_path FROM item_images WHERE thumbnail_path IS NOT NULL
' > "$test_files/expected-images.txt"
python3 - "$test_files" <<'PY'
import sys
from pathlib import Path
root = Path(sys.argv[1]).resolve()
paths = (root / "expected-images.txt").read_text().splitlines()
missing = [p for p in paths if not (root / p).resolve().is_relative_to(root) or not (root / p).is_file()]
if missing:
    raise SystemExit(f"Restore failed: {len(missing)} referenced image files missing")
print(f"Restored and verified {len(paths)} referenced image files")
PY
docker exec "$test_container" psql -U wardrobe -d wardrobe -At -c "SELECT json_build_object('users', (SELECT count(*) FROM users), 'items', (SELECT count(*) FROM clothing_items), 'outfits', (SELECT count(*) FROM outfits))"
echo 'Isolated backup recovery check passed.'
