#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
deploy_user=${1:?Pass the deployment user}
deploy_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
id "$deploy_user" >/dev/null
cat > /etc/systemd/system/wardrowbe-backup.service <<EOF
[Unit]
Description=Back up Wardrowbe database and clothing photos
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
User=$deploy_user
SupplementaryGroups=docker
WorkingDirectory=$deploy_dir
ExecStart=/usr/bin/bash $deploy_dir/backup.sh
UMask=0077
EOF
cat > /etc/systemd/system/wardrowbe-backup.timer <<'EOF'
[Unit]
Description=Daily Wardrowbe backup at 03:30 Asia/Shanghai

[Timer]
OnCalendar=*-*-* 03:30:00 Asia/Shanghai
Persistent=true
RandomizedDelaySec=0

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now wardrowbe-backup.timer
systemctl list-timers wardrowbe-backup.timer --no-pager
