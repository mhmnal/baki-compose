# Working in this repo

Read [STATUS.md](STATUS.md) first for current release, data and pending installs. Read [DESIGN_LAW.md](DESIGN_LAW.md) before changing visual surfaces.

## Current handoff — 27 November 2026

```text
Version       0.69.0
Code          273
Tests         253, 0 failures (full suite)
R8 APK        dist/baki-compose-0.69.0.apk
SHA-256       ebc61711e898257859ef5800091cf5f166d2d2c1c6d8d890dcd7f7c9851bf886
Unminified    not built; R8 APK is the release
Phone         0.67.0 installed; 0.69.0 published, ADB unavailable
Desktop       0.69.0 installed; four-material overhaul
```

Standing decisions:

- Bank reconciliation was removed in 0.64.7. Do not reintroduce AEON/TNG accounts, imported bank
  rows, batches, matching screens or `/api/statements`.
- Weekly Grab statements (`Store.statements`) and Data Health stay.
- Statement incentives, tips and delivery incentives auto-post as stable weekly income entries
  through `postStatementIncome`.
- Explicit `Shift.incentive` is subtracted from the statement incentive total to avoid double
  counting.
- Net GSF is informational. Never subtract it a second time from earnings.
- SQLite is authoritative; schema stays version 4. The JSON snapshot is rollback only.
- Data Health reads weekly statements and ledger facts only. It does not use bank sidecar data.

## Build safety — critical

A plain R8 `assembleRelease` once reached **8.6 GB RSS** and the global OOM killer terminated the
desktop session. Never run it unbounded.

### R8 release build

Use only:

```bash
./scripts/release-r8-safe.sh
```

Before a build, check it without starting R8:

```bash
./scripts/release-r8-safe.sh --check
```

The wrapper now has three guards:

1. **Preflight** — refuses to start with less than 6 GB available, or any `java` process over 1 GB.
   It stops Gradle and Kotlin daemons first. Other app processes are not counted.
2. **Service cap** — R8 runs in a unique transient systemd service with `MemoryMax=6G`,
   `MemoryHigh=5G`, `MemorySwapMax=2G`, `KillMode=control-group`.
3. **Host monitor** — polls `/proc/meminfo`; if available memory falls below 1.5 GB, it stops the
   service before the kernel OOM killer can target the session.

The previous crash was a **6.6 GB Java process outside the build cgroup**, not the R8 service.
The cage protected the build; it could not protect an unrelated JVM. Preflight is now the missing
part of that guard.

Do not use `gradle-capped.sh` or a terminal scope for R8.

**Tests and previews must use `./scripts/test-safe.sh`.** It runs Gradle in a persistent transient
systemd service with a 6 GB cap, a host-memory monitor and a stray-JVM preflight. A scope wrapper is
not enough: a test worker once escaped/interrupted and reached 9 GB outside the unit, causing a global
OOM kill. Builds and packaging use `./scripts/gradle-capped.sh`. Raw `./gradlew` may only be used for
`--stop`.

### Non-R8 builds

For tests, previews and non-minified local APKs:

```bash
./scripts/gradle-capped.sh :composeApp:assembleRelease \
  -PbakiMinify=false --offline --no-daemon --max-workers=1
```

After every build:

```bash
./gradlew --stop
```

Never run two Gradle builds at once. During quick iteration prefer targeted tests over the full
gate. Do not clear caches unless a genuine incremental-compilation failure occurs.

## Data and privacy rules

Never commit:

```text
../grab-tracker/data.json
../grab-tracker/backups/
../grab-tracker/tls/
../grab-tracker/ops.json
../grab-tracker/routes.json
../grab-tracker/server.log
*.key / *.crt / *.srl
```

The server repository is public. Keep the runtime-data and TLS ignore rules intact. If ledger data
is ever staged, remove it from history and make the repository private immediately.

Current bank-removal archive:

```text
~/baki-backups/bank-removal-20261125/
```

## Checking UI changes

After changing any screen, component, theme token or layout, **render the preview and look at the
affected frames before reporting the change done.** The phone and desktop window are not the first
check; they are the second.

Render both shells and both palettes:

```bash
./scripts/test-safe.sh :composeApp:desktopTest \
  --tests app.baki.PhoneShot --no-build-cache
```

Frames land in `composeApp/build/preview/{dark,light}/{phone,desk}/*.png`. The **App Preview** page
serves the same files; `preview_app` reruns the capture.

Check the changed screen in dark **and** light, plus anything sharing the component. `PhoneShot`
uses a seeded fixture and dead server URL (`127.0.0.1:1`); it never touches the real ledger. The
amber “cached” pill is fixture state.

## What the preview does not prove

A rendered frame is a still of seeded state. It cannot show:

- interaction — taps, typing, scrolling, navigation paths
- motion — transitions, animations, gestures
- device fidelity — real Android Skia, system bars, insets, keyboard, permissions
- performance — jank, frame timing
- platform behaviour — GPS, services, widgets, share intents

Open the device or desktop window for those. State which checks were actually performed.

## Reliability contract

- **Live state first.** Before answering a ledger, fee or location question, run
  `./scripts/preflight.sh` or query `/api/data` and check `../grab-tracker/data.json` mtime.
  Never conclude “not there” from one cached file.
- **Money is integer cents.** Never introduce floating-point currency.
- **Derived rows stay derived.** `grabfee` is generated from the shift timeline
  (`isDerivedCategory`); quick add, category chips and `Repo.addEntry` reject it.
- **Cover is wall-clock.** A shift ends at `start + hours + breakMinutes`; use `shiftEndMs()` and
  test breaks crossing 24h.
- **No bank sidecar.** Do not add back a second reconciliation system.
- **Preview is not device proof.** Render dark/light, then test interaction, GPS, permissions and
  sync on the phone. State what was actually checked.
- **Sign/install only after explicit instruction.** Build, test and commit first.
- **Stop Gradle daemons after releases.** Memory pressure kills the whole session, not just the
  build.

## R8 fallback

If the 6 GB service still gets killed by its cap, do not raise it blindly. The safe alternatives are:

1. keep the unminified APK for local use;
2. run standard R8 on a GitHub Actions 16 GB runner;
3. run standard R8 on a machine with 32 GB.

## Next work

1. Trial import review + allocation preview on the phone.
2. Trial Shifts weekly receipts on phone.
3. Mark or unlink Celcomdigi’s remaining payment.
4. Run a 6+ hour shift soak with screen off.
5. Keep R8 and desktop packaging inside capped systemd services.
6. G6 is implemented in source; release it.
7. Keep the week of data; SQLite is now the only production writer.
