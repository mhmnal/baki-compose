#!/usr/bin/env bash
# Add a desktop-menu entry for the isolated interactive phone preview.
set -euo pipefail
APP="${1:-$HOME/.local/opt/baki}"
mkdir -p "$HOME/.local/share/applications"
cat > "$HOME/.local/share/applications/baki-phone-preview.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Baki Phone Preview
Comment=Interactive phone shell and Motion Lab
Exec=env BAKI_SHELL=phone $APP/bin/Baki
Icon=$APP/lib/Baki.png
Terminal=false
Categories=Development;
StartupNotify=true
DESKTOP
chmod +x "$HOME/.local/share/applications/baki-phone-preview.desktop"
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
echo "Installed Baki Phone Preview in the applications menu."
