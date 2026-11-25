#!/usr/bin/env bash
# Run the R8 release build inside a transient systemd service with a hard memory cap.
# Any R8 overrun kills the build unit, not the desktop session.
#
# Close Firefox and other heavy apps first. If the service is killed, inspect:
#   journalctl --user -u baki-r8-build -n 100 --no-pager
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="${BAKI_TOOLS:-$ROOT/../.tools}"
UNIT="${BAKI_R8_UNIT:-baki-r8-build}"
LOG="${BAKI_R8_LOG:-/tmp/baki-r8-build.log}"
MEM_MAX="${BAKI_R8_MEMORY_MAX:-9G}"
MEM_HIGH="${BAKI_R8_MEMORY_HIGH:-8G}"
SWAP_MAX="${BAKI_R8_SWAP_MAX:-2G}"

cd "$ROOT"
./gradlew --stop >/dev/null 2>&1 || true
rm -f "$LOG"

RUNNER="$(mktemp /tmp/baki-r8-run.XXXXXX.sh)"
trap 'rm -f "$RUNNER"' EXIT
cat > "$RUNNER" <<RUNNER_EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$ROOT"
export JAVA_HOME="$TOOLS/jdk21"
export PATH="\$JAVA_HOME/bin:\$PATH"
export GRADLE_USER_HOME="$TOOLS/gradle"
export ANDROID_HOME="$TOOLS/sdk"
exec ./gradlew :composeApp:assembleRelease \
  -PbakiMinify=true \
  -Pandroid.enableR8.fullMode=false \
  -Pandroid.r8.maxWorkers=1 \
  --offline --no-daemon --max-workers=1 \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease \
  > "$LOG" 2>&1
RUNNER_EOF
chmod +x "$RUNNER"

echo "==> R8 service: max=${MEM_MAX}, high=${MEM_HIGH}, swap=${SWAP_MAX}"
echo "==> log: ${LOG}"

set +e
systemd-run --user \
  --unit="$UNIT" \
  --service-type=exec \
  --wait --collect \
  --property=WorkingDirectory="$ROOT" \
  --property=MemoryMax="$MEM_MAX" \
  --property=MemoryHigh="$MEM_HIGH" \
  --property=MemorySwapMax="$SWAP_MAX" \
  --property=KillMode=control-group \
  -- "$RUNNER"
status=$?
set -e

echo "==> R8 service exit: $status"
tail -20 "$LOG" 2>/dev/null || true
if [[ $status -eq 137 || $status -eq 143 ]]; then
  echo "==> R8 was killed by the memory cap. The session is safe; close more apps or lower R8 scope."
fi
exit "$status"
