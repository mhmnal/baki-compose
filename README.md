# Baki

A shift logbook for Grab drivers in Malaysia: what a shift actually pays after fuel, wear and the daily
insurance — plus weekly statement reconciliation, bills, and a Borang B tax pack.

Two surfaces, one ledger:

| | Phone (Android) | Desktop (JVM) |
|---|---|---|
| Role | capture and decide in the moment | review, compare, print |
| Design | Material 3 + Paper Ledger, liquid-glass nav/+ | Modern hybrid neumorphism (Soft UI 2.0 / Obsidian Slate) |
| Writes | yes | no — read-only by design |

Current version: **0.43.34** (`versionCode` 165+). Material Design 3. The phone carries the working ledger; the desk reads the
same store through the sync server.

Test suite: **205 JVM tests** (`:composeApp:desktopTest`).

## What it does

**Shift economics**
- live shift with foreground GPS distance, pause/break, payouts, insurance cover countdown
- live yield so far: net RM/h and gross RM/km against the target
- net per shift after fuel, wear and real expenses; rate against a target RM/h
- odometer-based measured economy (km/L) when fills include readings
- Grab insurance is shift-generated only: one fee per rolling 24h window, excluded from Quick Add

**Money**
- entries in and out, categories, search, day/month totals, budgets, funds (fuel/wear accrual)
- optional daily food cap with a compact Today line
- recurring bills tracked by **period**: settle next month before it ends, prepay ahead, link an existing
  expense, unlink without touching the ledger, optional leftovers goals (car loan)

**Income proof**
- import the weekly Grab statement PDF (Bahasa Malaysia or English labels): week, income, deductions,
  fare breakdown, insurance count, consistency check
- bulk import, share a PDF straight into the app, Wednesday 10:00 reminder
- reconcile statement income and insurance count against what was logged

**Tax**
- Borang B pack: income, expenses by category with deductible flags, business-use pro-rata, mileage,
  insurance windows, net business income; printable HTML and CSV

**Desk extras**
- read-only tables, route traces (sealed end-to-end), month comparison, year at a glance, statement preview
- routes never leave the phone unsealed; the server stores ciphertext only

**Phone UI**
- Paper Ledger: warm paper/ink light mode, warm-black/brass night mode
- bundled **Source Serif 4** display/tabular money, paired with Inter controls
- very subtle static paper grain, M3 typography roles, ruled sections and square controls
- continuous ledger rows with inset hairlines; contained paper panels only for action-heavy groups
- ruled in/out metrics, quick actions, filter tabs, budget bars and ledger sparkline
- flat live ticket, ruled payout rows, Paper-sheet forms, ledger charts and statement rows
- Paper QR customer screen, flat Summary/More sections, responsive shift fields
- **liquid-glass nav and `+` are preserved**; Paper Ledger styles the content around them
- More is split into **Money / Shift / App**, with the switcher pinned above the scroll area
- cash truth, service target, weekly review and privacy/discreet amounts
- press feedback, animated sync stamp, animated list placement and adaptive 360–412dp layouts

## Stack

- Kotlin 2.4, Compose Multiplatform 1.12, AGP 9.3, Gradle 9.6, compileSdk 37 / minSdk 24
- Desktop runs on JDK 21 (Backdrop ships Java 21 bytecode)
- Server: Node (`../grab-tracker/server.mjs`), JSON store, merge-only writes, tombstones, 30 rotating backups
- PDF: `pdfbox-android` on the phone, `pdfbox` on the desk
- Phone display: **Source Serif 4** (SIL Open Font License 1.1), bundled under
  `composeApp/src/commonMain/composeResources/files/source-serif-4-OFL.txt`

## Layout

```
composeApp/src/commonMain/   model, money maths, cover rule, merge, sync, statement parser, tax pack, UI
composeApp/src/androidMain/  activity, live service, GPS, widget, statement reminder, PDF picker
composeApp/src/desktopMain/  neumorphic shell, read-only views, PDF, printing
../grab-tracker/             sync server + PWA (still intact)
scripts/                     release.sh, shots.sh (deterministic desk captures)
```

## Running

```bash
# tests (JVM)
./scripts/gradle-capped.sh :composeApp:desktopTest

# desktop app
./scripts/gradle-capped.sh :composeApp:run

# server (same machine or LAN)
cd ../grab-tracker && node server.mjs

# signed phone build -> dist/baki-compose-<version>.apk
./scripts/release.sh 0.43.34
```

Phone install: enable wireless debugging, `adb connect <phone>:<port>`, then
`adb install -r dist/baki-compose-0.43.34.apk`. The release key lives in `../.tools/keystore` and is never
committed.

## Data & sync

- One store per user; the phone and desk share it through `POST/GET /api/data`.
- Writes merge by id, newest wins; deletions are tombstones; statements are keyed by week.
- The desk cannot create, edit or delete — `ReadOnlyShellTest` enforces it.
- Client config (server URL, theme, reminder flag, route key, **durable outbox bit**) is device-local.
- A local save marks the outbox; the sync loop retries it after restarts and sends tombstones with the
  full-store push, so offline edits and deletes cannot sit silently on one device.
- Local writes fsync the temp file, keep a `.bak`, and quarantine an unreadable store instead of
  starting empty; the previous local backup or the server copy is used to recover.
- Server keeps 30 rotating backups in `../grab-tracker/backups/`.

## Docs

- [DESIGN.md](DESIGN.md) — material doctrine, Surface depth, desktop layout, statement/tax decisions
- [ACCESSIBILITY.md](ACCESSIBILITY.md) — enforced contrast/behaviour and current gaps
- [BUGS.md](BUGS.md) — known issues and parked work
- [NEXT.md](NEXT.md) — current priorities
- [PARITY.md](PARITY.md) — PWA → Compose feature comparison
- [PERFORMANCE.md](PERFORMANCE.md) — benchmark baseline and how to rerun

## Known constraints

- The sync API has no user authentication; treat the server as LAN-only and keep route traces sealed.
- The desk is read-only by design.
- Dynamic (wallpaper) colour is not contrast-clamped.
- Phone TalkBack coverage of custom canvases and swipe rows is incomplete.
- Cover renewal uses the saved wall-clock end (`hours + breakMinutes`). Older shifts without a
  break value fall back to worked hours.

## API token

The ledger API is open on the LAN unless the server is started with a shared token:

```bash
BAKI_TOKEN='choose-a-long-random-token' ./sync.sh
```

Enter the same token in **More → App → API token**. Without it, data, route and
live-event requests receive `401`. The token is included in portable backups, so
treat those files as private.


## Build memory

Heavy Gradle builds run in a capped cgroup so a memory spike kills the build, not the desktop:

```bash
./scripts/gradle-capped.sh :composeApp:desktopTest
./scripts/gradle-capped.sh :composeApp:assembleRelease
```

The wrapper serializes builds, caps the Gradle and Kotlin JVMs, and stops idle daemons afterwards.
Direct `./gradlew` builds still inherit the heap/worker caps in `gradle.properties`, but the 6 GB
cgroup cage only applies through the wrapper. Use `BAKI_KEEP_DAEMONS=1` if you need warm daemons.
