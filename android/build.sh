#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
mode=${1:-debug}
case "$mode" in
  debug) tasks=(:app:assembleDebug); apk=app/build/outputs/apk/debug/app-debug.apk ;;
  check) tasks=(:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest); apk=app/build/outputs/apk/debug/app-debug.apk ;;
  release)
    if [[ ! -f signing.properties ]]; then
      echo 'Missing signing.properties. Run android/create-signing-key.py with JAVA_HOME set first.' >&2
      exit 1
    fi
    tasks=(:app:lintRelease :app:assembleRelease); apk=app/build/outputs/apk/release/app-release.apk ;;
  *) echo 'Usage: android/build.sh debug|check|release' >&2; exit 2 ;;
esac
./gradlew --no-daemon "${tasks[@]}"
mkdir -p artifacts
cp "$apk" "artifacts/my-wardrobe-${mode/check/debug}.apk"
cd artifacts
sha256sum "my-wardrobe-${mode/check/debug}.apk" > "my-wardrobe-${mode/check/debug}.apk.sha256"
echo "APK: $PWD/my-wardrobe-${mode/check/debug}.apk"
