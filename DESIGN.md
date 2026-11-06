# Design: PWA vs Compose

Decisions taken: port the PWA Today in full, keep the 5-tab bar + floating FAB, do the native
interaction pass, **no collapsing top bar** (the header stays as it is).

## Today, ported

- Date pill: "Today / 5 November" (was the ISO date)
- Hero: small `RM` baseline + 58sp number, letterspaced caps caption below
- Week strip: weekday-free day numbers, bar + amount, today highlighted
- Cover line: "Cover paid until HH:MM — starting before then adds no RM 7.50" (only while paid)
- Shift card: `SHIFT NET · AFTER CAR COST`, band pill, RM/hr, meta, `+ Add shift / ▶ Live / All →`
- Quick presets: category chips on Today, tapping opens the sheet with that category chosen
- Entry rows: icon tile + note + category
- `see all →` into Spending

## Native interaction

- **BakiSheet** — our own sheet component instead of Material's: it lives in the app's composition
  (not a separate window), springs in with `spring(dampingRatio=.78, stiffness=420)`, slides out on
  a 200ms tween, has 28dp top corners that square off as you drag it, and dismisses by dragging the
  handle (the body scrolls, so the two gestures never fight)
- **Animated hero** — the number eases to its new value with a spring
- **Pull to refresh** — sync by pulling Today
- **Predictive back** — `enableOnBackInvokedCallback`, back closes a sheet, then returns to Today
- Long-press a row to edit; delete lives inside the editor with an Undo snackbar
- **Swipe right to edit, swipe left to delete** on every list (Today, Spending, Shifts), with a
  coloured action background and an Undo snackbar; the row always springs back
- **Expressive tab transitions** — M3 emphasised curve `cubic-bezier(.05,.7,.1,1)`, 260ms,
  content rises a few pixels as it fades in
- Pull-to-refresh has no spinner overlay: the header dot already reports sync state

## Deliberately different

- Navigation stays a standard 5-tab bar with a floating FAB (not the PWA's pill-with-docked-plus)
- Top bar stays static — no scroll-collapse
- Dialogs for destructive confirmations, BakiSheet for input
- Not done: a true shared-element morph from row to sheet. BakiSheet makes it possible later
  (same composition), but it needs `SharedTransitionLayout` plus matching keys on every row


## Liquid glass — Haze, integrated and building

**I was wrong that SDK 37 could not be installed.** `sdkmanager` fails here ("Failed to download
any source lists") because its own networking is broken, but `curl` reaches `dl.google.com` fine
and the canary repository (`repository2-3.xml`) lists `platforms;android-37.0`. Installed by hand:

```
curl -O https://dl.google.com/android/repository/platform-37.0_r02.zip
unzip -> $ANDROID_HOME/platforms/android-37     # verified: Android 17, API 37
```

With that, the full stack builds:

| | |
|---|---|
| Kotlin | 2.2.10 -> **2.4.21** (Haze 2.1.0 ships 2.4 metadata) |
| Compose MP | 1.10.3 -> **1.12.2** |
| compileSdk | 36 -> **37** (+ `android.suppressUnsupportedCompileSdk=37`) |
| Haze | `dev.chrisbanes.haze:haze:2.1.0` + `haze-glass:2.1.0` |

All **26 tests still pass** on the upgraded toolchain, and the APK builds, installs and runs.

### Current state of the effect

`GlassSurface` calls `hazeGlass(input = HazeInput.Sources(hazeState), style = GlassStyle.regular)`
and the content carries `hazeSource(hazeState)`. It renders, but **the refraction is not visible** —
with no tint floor the bar reads as plain transparent, and with one it reads as a flat surface. So
the effect is not sampling what it should yet.

`glassMode` therefore defaults to **auto = Material** (no Haze cost). More -> Liquid glass -> **On**
forces the Haze path so it can be iterated on.

### Next steps for the glass, if wanted

1. Compare against Haze's own sample (`sample/shared`) — same Compose/Haze versions, to see whether
   the effect works at all on this device or whether our node placement is wrong.
2. Try `HazeInput.Content` instead of `Sources`, and `GlassStyle.clear` vs `regular`.
3. Watch `HazeState` after capture: if it holds no sources, the `hazeSource` node is not being
   picked up (most likely cause).

## GPS distance

`DistanceTracker` (Android `LocationManager`, no Play Services):
- radio wakes on movement: `minTime 4s`, `minDistance 25m`
- a fix is used only if accuracy <= 40m and implied speed <= 150 km/h
- movement under 8m is ignored, so standing still adds nothing
- distance is written into `live.json`; the UI polls it every 4s; ending the shift uses the
  measured km when it is >= 0.3 km, otherwise falls back to hours x average speed

CPU cost is a few haversines per minute — negligible. The cost, such as it is, is the GPS radio,
and it only runs while a live shift is running.

### Why a shader is not possible here (verified)

- `android.graphics.Paint` has **no** `RenderEffect` (only `PathEffect`) — checked against
  `android-36/android.jar`.
- `RenderEffect.createRuntimeShaderEffect()` exists, but an effect attaches to a **RenderNode**.
- Compose's `GraphicsLayer` never exposes its native node: no `asRenderNode()`, no bridge in the
  public API.
- The only public bridge is `GraphicsLayer.toImageBitmap()` — a full-screen GPU readback
  (~10 MB at 1080x2392) **per frame**. Unusable on a low-end device.

So the refraction is approximated with nested magnification bands + specular + rim light.
A true AGSL pass would mean rendering the backdrop into a native RenderNode outside Compose's
control, or accepting the readback cost.

### Why not a shader

True liquid glass bends *each pixel* by its distance from the edge, with chromatic fringing. That
needs an AGSL `RuntimeShader` sampling the backdrop, running per frame over the bar. On a low-end
GPU that is the one piece of this design that could cost frames, so the layered-lensing approach
was chosen deliberately.

## Shared elements (done)

`SharedTransitionLayout` wraps the app; the tab `AnimatedContent` provides the visibility scope via
a composition local, and `BakiSheet` publishes its own. An entry row carries
`sharedBounds("entry-<id>")`, and the sheet header carries the same key — so editing an entry morphs
the row into the sheet header instead of just sliding a panel up.


## Glass

The nav bar and FAB use **Backdrop** (`io.github.kyant0:backdrop`, Apache-2.0) for the liquid-glass
effect: a lens effect that refracts the content at the rim, vibrancy, a light blur, and a specular
highlight. It is multiplatform (Android + desktop) and renders on the test device — a Nothing Phone 3a
(A059, Nothing OS 5.0, Android 17, 12 GB / 8 cores, Adreno) — where Haze produced only a flat
tint. That is a Haze shader/sampling failure on capable hardware, not a weak-GPU fallback:
Backdrop's AGSL lens renders on the same device at full quality (`DeviceTier` = 2).

Content is captured once with `Modifier.layerBackdrop(backdrop)`; each glass surface samples it.
`GlassTier` still decides: tier 0 falls back to a plain Material surface.


## Routes

Each completed shift can carry its recorded GPS path. Paths live in their own encrypted file
(`routes.json`) and are **never synced** — a movement history is the most sensitive thing the app
holds, and it would bloat every upload.

- Captured by `DistanceTracker` at the same 2 s cadence as the distance maths (the phone charges in
  the car), buffered and written in batches of 20 points / 30 s.
- Filed under the **live shift key** while driving, then renamed onto the id of the shift that is
  stored when the shift ends. No route identity is synced: the server whitelists shift fields, so a
  synced key would silently be stripped on the way back.
- Simplified on shift end (Douglas–Peucker, 4 m) so a three-hour shift stays a few dozen KB.
- Retention: 90 days, swept on app start and on every finalise.
- Drawn read-only from OpenStreetMap raster tiles on a Compose Canvas (`RouteMap`): no gestures, no
  pan/zoom, auto-fit to the day's bounds, A/B markers per shift, own colour per shift.
