# Known issues / parked work

Updated 2026-11-08 for **0.36.2**. Ordered by what costs money or trust first.

## Security / operations

- **The sync API is unauthenticated.** Anyone who can reach the port can read and merge the store.
  Route traces are sealed (AES-GCM) but the ledger is not. Keep it on a trusted LAN or put it behind
  a reverse proxy with auth.
- **Backups are local only.** The server rotates 30 JSON backups in `../grab-tracker/backups/`; nothing
  leaves the machine. Local clients now keep one `.bak` and recover from it, but a disk loss is still a
  server-side problem.
- **`/usr/bin/baki` is a root-owned leftover** from an early install. The current app lives in
  `~/.local/opt/baki`; removing the old binary needs sudo.

## Phone

- **Natural-language quick entry and voice were removed** in 0.35.6 because they went unused. The parser
  and its tests remain (`QuickEntry.kt`, `QuickEntryTest`); the UI/mic are gone.
- **TalkBack coverage is incomplete.** Custom canvases (week strip, sparkline, route map) and the old
  swipe-to-edit/delete ledger rows lack custom accessibility actions; a screen-reader user cannot edit or
  delete an entry without gestures.
- **Dynamic colour is not contrast-checked.** Fixed palettes pass `ContrastTest`; wallpaper-derived
  colours can fall below 4.5:1.
- **Statement reminder needs notification permission.** If it was denied at first launch, the reminder
  switch silently produces no notification.
- **Share target accepts `application/pdf` only**; some providers send `application/octet-stream`.
  Bulk import is capped at 12 files, 25 MB each. No OCR.
- **Odometer discipline is partial**: most fuel fills still lack a reading, so measured km/L only
  applies after the "Use measured" action.
- The Today header/status and glass nav are custom; 48 dp targets were not exhaustively re-audited after
  the minimal pass was reverted.

## Desk

- **Read-only by design** (capture is the phone's job). There is no add/edit/delete.
- No OS font scaling or reduced-motion handling; charts are canvas with summary descriptions only.
- Scrollbars were removed by request; the wheel is the only affordance.
- Automated UI clicking is impossible from this Wayland session (compositor drops synthetic input).
  Visual QA uses `scripts/shots.sh`; this is a tooling limitation, not an app bug.

## Parked, deliberately

- Maintenance reminders by distance (start counting from the first service).
- Receipt photos (receipts are rarely kept).
- Best-hours earning nudge.
- Phone neumorphism (the glass design is the current direction).
- Bill notifications and tax-pack year/PDF export were discarded by request.
