#!/usr/bin/env python3
"""Create a persistent personal APK key outside Git without printing any passwords."""

import os
import secrets
import shutil
import subprocess
from pathlib import Path

project = Path(__file__).resolve().parent
private = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local/share")) / "wardrowbe/android-signing"
private.mkdir(parents=True, exist_ok=True, mode=0o700)
private.chmod(0o700)
keystore, password_file = private / "wardrobe.p12", private / "password"
properties = project / "signing.properties"
if properties.exists():
    raise SystemExit("signing.properties already exists; keeping the existing signing identity.")
keytool = str(Path(os.environ["JAVA_HOME"]) / "bin/keytool") if os.environ.get("JAVA_HOME") else shutil.which("keytool")
if not keytool:
    raise SystemExit("Set JAVA_HOME to a JDK 17 installation first.")
if keystore.exists() != password_file.exists():
    raise SystemExit("Incomplete signing files found. Restore the original key/password before continuing.")
if not keystore.exists():
    with open(password_file, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
        stream.write(secrets.token_urlsafe(36) + "\n")
    subprocess.run([
        keytool, "-genkeypair", "-alias", "wardrobe", "-keyalg", "RSA", "-keysize", "3072",
        "-validity", "10000", "-storetype", "PKCS12", "-keystore", str(keystore),
        "-storepass:file", str(password_file), "-keypass:file", str(password_file),
        "-dname", "CN=My Wardrobe, OU=Personal, O=Hikomorebi, C=CN",
    ], check=True)
    keystore.chmod(0o600)
password = password_file.read_text().strip()
with open(properties, "x", opener=lambda path, flags: os.open(path, flags, 0o600)) as stream:
    stream.write(f"storeFile={keystore}\nstorePassword={password}\nkeyAlias=wardrobe\nkeyPassword={password}\n")
print("Personal signing identity ready. Back up the key and password outside this server.")
print(f"Private directory: {private}")
