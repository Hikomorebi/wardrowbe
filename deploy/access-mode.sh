#!/usr/bin/env bash
# Manage private SSH access, public closure, or opening after receiving an ICP number.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mode=${1:-}
if [[ "$mode" != close && "$mode" != open && "$mode" != private ]]; then
    echo 'Usage: bash deploy/access-mode.sh close|private|open' >&2
    exit 2
fi
python3 - "$mode" <<'PY'
import os
import sys
import tempfile
from pathlib import Path

path = Path('.env.server')
if not path.is_file():
    raise SystemExit('Run deploy/prepare.py first')
lines = path.read_text().splitlines()
values = dict(line.split('=', 1) for line in lines if '=' in line and not line.startswith('#'))
if sys.argv[1] == 'open' and not values.get('ICP_RECORD_NUMBER', '').strip().strip('\"\''):
    raise SystemExit('Set the actual approved ICP_RECORD_NUMBER in deploy/.env.server before opening')
changes = {'SITE_CLOSED_FOR_FILING': 'false' if sys.argv[1] == 'open' else 'true'}
if sys.argv[1] == 'private':
    changes['FRONTEND_AUTH_URL'] = 'http://127.0.0.1:18080'
elif sys.argv[1] == 'open':
    domain = values.get('APP_DOMAIN', 'cd.hikomorebi.site').strip().strip('"\'')
    changes['FRONTEND_AUTH_URL'] = 'https://' + domain
for key in changes:
    if sum(line.startswith(key + '=') for line in lines) > 1:
        raise SystemExit('Duplicate access-mode settings; correct deploy/.env.server first')
updated = []
for line in lines:
    key = line.split('=', 1)[0]
    updated.append(key + '=' + changes[key] if key in changes else line)
for key, value in changes.items():
    if key not in values:
        updated.append(key + '=' + value)
with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, prefix='.env.server.', delete=False) as stream:
    temporary = Path(stream.name)
    stream.write('\n'.join(updated) + '\n')
try:
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)
finally:
    temporary.unlink(missing_ok=True)
PY
compose=(docker compose --env-file .env.server -f compose.server.yml)
"${compose[@]}" config --quiet
if [[ "$mode" == private ]]; then
    # Close the public proxy before changing authentication to the SSH-only URL.
    "${compose[@]}" up -d --no-deps caddy
    "${compose[@]}" up -d --no-deps --wait frontend
elif [[ "$mode" == open ]]; then
    # Load the actual ICP number and HTTPS authentication URL before opening the proxy.
    "${compose[@]}" up -d --no-deps --wait frontend
    "${compose[@]}" up -d --no-deps caddy
else
    "${compose[@]}" up -d --no-deps caddy
fi
if [[ "$mode" == private ]]; then
    echo 'SSH-only access ready at http://127.0.0.1:18080. The public application remains closed.'
elif [[ "$mode" == close ]]; then
    echo 'Public application closed for ICP filing; data and scheduled backups are retained.'
else
    echo 'Public application opened. Verify access from an independent external network.'
fi
