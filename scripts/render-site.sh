#!/usr/bin/env bash
# Render a real webpage to images for visual design review.
#   ./scripts/render-site.sh https://m3.material.io/styles/spacing/overview
#   RENDER_DARK=1 ./scripts/render-site.sh <url>
set -euo pipefail

URL="${1:?usage: $0 <url> [output-dir]}"
OUT="${2:-design-reference}"
mkdir -p "$OUT"
SLUG="$(printf '%s' "$URL" | sed -E 's#^https?://##; s#[^A-Za-z0-9._-]+#-#g; s#-+$##' | cut -c1-80)"

echo "==> desktop 1440px: $URL"
npx --yes "${PLAYWRIGHT_PKG:-playwright@1.49.1}" screenshot \
  --browser chromium \
  --viewport-size "1440,1000" \
  --full-page \
  --wait-for-timeout 3500 \
  --color-scheme light \
  --timeout 45000 \
  "$URL" "$OUT/$SLUG-desktop.png"

echo "==> phone 390px: $URL"
npx --yes "${PLAYWRIGHT_PKG:-playwright@1.49.1}" screenshot \
  --browser chromium \
  --viewport-size "390,844" \
  --wait-for-timeout 3500 \
  --color-scheme light \
  --timeout 45000 \
  "$URL" "$OUT/$SLUG-phone.png"

if [[ "${RENDER_DARK:-0}" == "1" ]]; then
  echo "==> desktop dark: $URL"
  npx --yes "${PLAYWRIGHT_PKG:-playwright@1.49.1}" screenshot \
    --browser chromium \
    --viewport-size "1440,1000" \
    --full-page \
    --wait-for-timeout 3500 \
    --color-scheme dark \
    --timeout 45000 \
    "$URL" "$OUT/$SLUG-desktop-dark.png"
fi

printf '\nrendered:\n'
ls -lh "$OUT/$SLUG"*.png
