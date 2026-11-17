# Android verification lane

The phone is the writer; this lane verifies the real Android surface. Xvfb is desktop-only.

## One-time setup

```bash
./scripts/android-avd-setup.sh
```

Creates `baki_api36` (API 36 Google APIs x86_64, Pixel 7 profile). Requires KVM and roughly 4–5 GB.

## Run

```bash
./scripts/android-test.sh
```

Pipeline:

1. boots `baki_api36` headless
2. builds the debug `.verify` application id (`app.baki.compose.verify`)
3. installs it on the emulator only
4. grants location for the permission prompt
5. launches the app and captures `smoke-launch.png`
6. runs Compose instrumentation tests
7. writes artifacts to `/tmp/baki-android-lane`

The verify app uses `http://127.0.0.1:1` and a fresh package, so it cannot touch the real ledger.

## Physical device

```bash
ANDROID_SERIAL=<adb-serial> ./scripts/android-test.sh
```

The script never installs on a connected physical device unless that serial is explicitly selected.

## Current tests

`composeApp/src/androidInstrumentedTest/kotlin/app/baki/BakiSmokeTest.kt`

- shell launch
- Today → Shifts navigation
- More tabs: Money, Work, App

## Not yet covered

- write/edit form validation
- keyboard/IME
- notification absence after reminder removal
- live GPS/service
- widget/share intents
- real hardware insets and frame pacing

## Open the emulator for manual use

```bash
./scripts/android-emulator-gui.sh
```

This opens `baki_api36` in a normal window and installs the signed APK from `dist/`
if present. The app is installed under the normal `app.baki.compose` package in the emulator,
separate from your physical phone.

Stop it with:

```bash
~/.local/opt/baki/bin/Baki  # not this
/home/min/Documents/deepseek-harness/default-workspace/.tools/sdk/platform-tools/adb -s emulator-5554 emu kill
```
