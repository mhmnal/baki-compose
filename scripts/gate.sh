#!/usr/bin/env bash
# Complete release gate: full JVM suite, Android lane, unsigned release build.
# Signing/publishing is a separate explicit step via scripts/release.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$TOOLS/gradle}"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"

cd "$ROOT"
echo "==> gate 1/3: full JVM suite"
./scripts/gradle-capped.sh :composeApp:desktopTest --offline

echo "==> gate 2/3: Android verification lane"
./scripts/android-test.sh

echo "==> gate 3/3: unsigned release build"
./scripts/gradle-capped.sh :composeApp:assembleRelease --offline

echo "==> gate passed. Sign/publish only when explicitly requested:"
echo "    ./scripts/release.sh <version>"
