#!/usr/bin/env bash
# One-time Android emulator setup for the local verification lane.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${TOOLS:-/home/min/Documents/deepseek-harness/default-workspace/.tools}"
export JAVA_HOME="${JAVA_HOME:-$TOOLS/jdk21}"
export PATH="$JAVA_HOME/bin:$TOOLS/sdk/platform-tools:$TOOLS/sdk/emulator:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS/sdk}"
AVD="${AVD:-baki_api36}"
IMAGE="${IMAGE:-system-images;android-36;google_apis;x86_64}"

if [ ! -x "$ANDROID_HOME/emulator/emulator" ] || [ ! -d "$ANDROID_HOME/system-images/android-36/google_apis/x86_64" ]; then
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" emulator "$IMAGE"
fi

if ! "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" list avd | grep -q "Name: $AVD"; then
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" -d pixel_7 --force
fi
echo "AVD ready: $AVD"
