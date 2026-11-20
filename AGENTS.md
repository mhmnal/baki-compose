# Working in this repo

## Checking UI changes

After changing any screen, component, theme token or layout, **render the preview and look at the
affected frames before reporting the change done.** The phone and the desktop window are not the
first check; they are the second.

Render both shells and both palettes (26 frames):

```
gradle :composeApp:desktopTest --tests app.baki.PhoneShot --offline
```

Frames land in `composeApp/build/preview/{dark,light}/{phone,desk}/*.png`. In the Harness, the
**App Preview** page serves the same files and can re-render, and the `preview_app` tool runs the
same capture.

Check at least: the screen you changed, in dark **and** light, plus anything that shares the
component you touched.

## What the preview does not prove

A rendered frame is a still of seeded state. It cannot show:

- interaction — taps, typing, scrolling, navigation paths
- motion — transitions, animations, gestures
- device fidelity — real Android Skia, system bars, insets, keyboard, permissions
- performance — jank, frame timing
- platform behaviour — GPS, services, widgets, share intents

Open the device or the desktop window when the question is one of those. Say which one it is;
"check it on the phone" without a reason is not a review.

## Fixtures

`PhoneShot` seeds two shifts and a few expenses against a dead server URL (`127.0.0.1:1`), so
renders never touch the real ledger. The amber "cached" pill in those frames is that fixture, not a
bug in the app.

## Reliability contract

- **Live state first.** Before answering any ledger, fee or location question, run
  `./scripts/preflight.sh` (or query `/api/data` and check `../grab-tracker/data.json` mtime).
  Never conclude "not there" from one cached file.
- **Money is integer cents.** Never introduce floating-point currency.
- **Derived rows stay derived.** `grabfee` is generated from the shift timeline
  (`isDerivedCategory`); Quick Add, manual category chips and `Repo.addEntry` must reject it.
- **Cover is wall-clock.** A shift's end is `start + hours + breakMinutes`; use `shiftEndMs()`.
  Test breaks that cross a 24h boundary.
- **The liquid-glass nav and `+` are protected.** Do not restyle their geometry, tint, blur,
  refraction or highlight; Paper Ledger applies to the content around them.
- **Preview is not device proof.** Render dark/light, then test interaction, GPS, services,
  permissions and sync on the phone. State which checks were actually done.
- **Sign/install only after explicit instruction.** Build, test and commit first.

## Build workflow

Use the two-speed workflow in [WORKFLOW.md](WORKFLOW.md).

- Fast iteration: `./scripts/fastcheck.sh <test class>`
- Complete gate: `./scripts/gate.sh`
- Signing/publishing only after explicit instruction: `./scripts/release.sh <version>`

The fast loop uses the warm Gradle daemon and build cache. Do not use `--no-daemon`, clear build caches, or build/sign a release APK during UI iterations. Clean only after a genuine incremental-compilation failure.

## Environment

This sandbox has no network. Use the checked-in toolchain:

- `JAVA_HOME=.tools/jdk21` (workspace-level), `GRADLE_USER_HOME=.tools/gradle`, `ANDROID_HOME=.tools/sdk`
- always pass `--offline` to Gradle; dependencies are cached
- `scripts/shots.sh` is the old screen-grab path (desktop only, captures the whole screen); prefer
  the offscreen renderer
