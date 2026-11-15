"""Create local deployment secrets without printing them or overwriting existing secrets."""

import os
import secrets
from datetime import UTC, datetime
from pathlib import Path

target = Path(__file__).resolve().parent / ".env.server"
values = {
    "BUILD_HTTP_PROXY": os.environ.get("HTTPS_PROXY", ""),
    "APP_DOMAIN": "cd.hikomorebi.site",
    "ICP_RECORD_NUMBER": "",
    "SITE_CLOSED_FOR_FILING": "false",
    "FRONTEND_AUTH_URL": "",
    "APP_VERSION": "personal-" + datetime.now(UTC).strftime("%Y%m%d%H%M%S"),
    "POSTGRES_PASSWORD": secrets.token_hex(32),
    "SECRET_KEY": secrets.token_hex(32),
    "NEXTAUTH_SECRET": secrets.token_hex(32),
}
try:
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    print(f"Keeping existing configuration: {target}")
else:
    with os.fdopen(fd, "w") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in values.items()))
    print(f"Created private configuration: {target}")
