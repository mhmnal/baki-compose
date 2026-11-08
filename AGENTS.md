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

## Environment

This sandbox has no network. Use the checked-in toolchain:

- `JAVA_HOME=.tools/jdk21` (workspace-level), `GRADLE_USER_HOME=.tools/gradle`, `ANDROID_HOME=.tools/sdk`
- always pass `--offline` to Gradle; dependencies are cached
- `scripts/shots.sh` is the old screen-grab path (desktop only, captures the whole screen); prefer
  the offscreen renderer
