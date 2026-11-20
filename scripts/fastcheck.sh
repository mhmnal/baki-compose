#!/usr/bin/env bash
# Fast inner loop: targeted test, warm Gradle daemon, no clean, no release build.
#   ./scripts/fastcheck.sh app.baki.PhoneShot
#   ./scripts/fastcheck.sh app.baki.WeekCloseTest
# With no argument it renders the phone/desk preview set.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLS/gradle}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"

cd "$ROOT"
TEST="${1:-app.baki.PhoneShot}"
echo "==> fastcheck: $TEST"
echo "    warm daemon, incremental build, no clean, no signing"
./gradlew :composeApp:desktopTest --tests "$TEST" --offline --daemon
