# Working in this repo

Read [STATUS.md](STATUS.md) first for current release, data and pending installs.

## Current handoff — 25 November 2026

```text
Version       0.64.6
Code          265
Tests         243 JVM tests, 0 failures
R8 APK        dist/baki-compose-0.64.6.apk
SHA-256       9d71ad2a7724b3ac7fb8f6e01682bc33de0bd9404d661d8280cd61f67f2d38ee
Unminified    dist/baki-compose-0.64.6-unminified.apk
Phone         0.64.6 installed; Spend receipt trial
Desktop       0.64.6 installed; 0.64.5/0.64.1/0.63.1 rollbacks retained
```

Standing decisions:

- Bank reconciliation was removed in 0.64.6. Do not reintroduce AEON/TNG accounts, imported bank
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

That wrapper runs Gradle in a transient systemd **service**:

```text
MemoryMax     9 GB
MemoryHigh  8 GB
SwapMax       2 GB
KillMode      control-group
R8 fullMode   false
Workers       1
Screen         Firefox may stay open
```

If R8 exceeds the cap, the service dies, not the session. The successful 0.64.6 build used this
exact path and finished in 1m38s.

Do not use `gradle-capped.sh` for R8. A terminal scope previously allowed the R8 process to escape
containment.

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
./scripts/gradle-capped.sh :composeApp:desktopTest \
  --tests app.baki.PhoneShot --offline --no-daemon
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

If the 8 GB service still gets killed by its cap, do not raise it blindly. The safe alternatives are:

1. keep the unminified APK for local use;
2. run standard R8 on a GitHub Actions 16 GB runner;
3. run standard R8 on a machine with 32 GB.

## Next work

1. Install 0.64.6 on the phone from `/dl` and trial Spend receipts.
2. Mark or unlink Celcomdigi’s remaining payment.
3. Run a 6+ hour shift soak with screen off.
4. Keep R8 and desktop packaging inside capped systemd services.
5. Keep the week of data, then retire the JSON write path (G6).
