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

## Colour packs

Three packs share the same `BakiPalette` contract: **Sage** (the app's identity), **Night** (near-black,
cool blue — glare-free after dark), **Paper** (warm off-white, ink-brown). Chosen in More → Appearance
and stored in `ClientConfig.palette`. The mode selector (Auto/Light/Dark) still applies to Sage; Night
is always dark and Paper always light.

## Typeface

Inter variable (`composeResources/font/inter_variable.ttf`) replaces the system face. `BakiType`
exposes `install(family)`, called once at the top of `App()` before any text composes, so every style
and its numeric variant picks up the family without touching call sites.

## Home-screen widgets

Two RemoteViews widgets, both drawn in the sage palette rather than Monet (the colour is the identity):
`BakiWidget` (kept today) and `BakiLiveWidget` (the shift in progress).

- **The clock ticks without the app**: a `Chronometer` keeps its own time inside the launcher, base
  converted from epoch to `elapsedRealtime`, so no periodic updates are needed.
- **Pause works from the home screen**: the button broadcasts to `WidgetActions`, which attaches the
  same encrypted store (`AppCtx.attach`), flips the pause, and refreshes both widgets.
- **Providers must attach the store themselves** — a widget can be updated in a fresh process with no
  Activity, where `LocalStore` is otherwise uninitialised and every read returns empty.
- Idle state shows a headline ("Start a shift") rather than a stopped clock at 00:00.

## Consistency rules

Enforced by `DesignTokensTest`, which fails the build when a literal slips in.

| Rule | Use | Never |
|---|---|---|
| Type | `BakiType.*` (display 57 · displaySmall 45 · headline 28 · title 22 · body 16 · bodySmall 14 · label 12 · caption/section 11 · nano 9, plus `*Numeric` for figures) | raw `fontSize = N.sp` |
| Spacing | `Space.xxs/xs/s/m/l/ml/xl/xxl` (2/4/8/12/16/20/24/32) | raw dp in `padding`, `Spacer`, `spacedBy` |
| Radii | `Radius.*` | raw `RoundedCornerShape(N.dp)` |
| Icon size | `IconSize.inline/row/bar/hero` (16/20/24/40) | ad-hoc `size()` |
| Control height | `Control.primary/secondary` (48/44) | 50 or 46 |
| Money | `EasedMoney` for heroes (animates) · `rm()` inside sentences and rows (never animates) | `%.2f` for an amount |
| Labels | Section headers and hero captions ALL-CAPS via `BakiType.section`; everything else sentence case | mixed casing |
| Empty states | `EmptyState(title, hint)` — title without a full stop, hint a sentence | bespoke copy |

## Desktop shell

The laptop is not a big phone. `DesktopRoot` replaces the touch UI when `Caps.isDesktop`:

- **A window, not a frame**: 1360x900 default, 960x640 minimum, size/position remembered in Java
  preferences; native menu bar (File: new entry, export CSV, statement, quit — View: colour pack).
- **Navigation rail** (Overview / Entries / Shifts / Year) with hover states and pointer cursors.
- **Overview** is a dashboard: month hero, a fixed seven-day strip, the trend chart, the recent
  entries table, and a right rail with spend-by-category, funds and last-week figures.
- **Entries and Shifts are tables**, not cards: aligned columns, hover rows, click-to-edit,
  right-click for edit/delete.
- **Year** shows twelve months at once — impossible on a phone.
- **Statement** writes a print-ready HTML document and opens the browser (Ctrl+P → PDF), no extra
  dependency.
- Phone-only affordances are absent by capability, not by accident: `Caps` reports no GPS, no routes,
  no widgets and no share sheet, so the desktop never renders them.

### Desktop glass needs Java 21

Kyant's Backdrop ships Java 21 bytecode, so the desktop app must run on 21+ (the phone is unaffected:
it compiles to dex). `createDistributable` bundles the runtime it was built with, so the installed app
needs nothing on the machine; only `./gradlew :composeApp:run` needs `JAVA_HOME` pointing at a 21 JDK.
The floating command bar is the single glass surface: M3 for everything else, refraction where the
phone keeps its nav bar.

## Desktop: neumorphism (v0.13)

The laptop is deliberately a different design language from the phone. Phone keeps Material 3 with a
single liquid-glass nav bar; the desktop is **neumorphic** — one base colour, depth from two soft
shadows only, no borders.

- `Neumorphism.kt` derives everything from the active palette: `base` (canvas), `panel` (lifted),
  `light` (base towards white) and `dark` (base towards ink). All three packs, light and dark.
- `Modifier.raised` draws a blurred dark rrect offset down-right and a light one up-left, then paints
  the panel colour on top; `Modifier.inset` clips the same shadows inside the shape for pressed
  states, fields and the selected nav item.
- Consequence: no dividers in tables, no outlines on inputs, no glass on desktop. The glass library
  stays in the phone build only.
- Depth is the only affordance, so selection and hover must remain visible: selected rows are inset
  wells, hover adds a 5.5% accent wash.

### Desktop component set (v0.11)

`Neumorphism.kt` is the whole desktop design system:

| Piece | Use |
|---|---|
| `Neu` / `neuOf(palette)` | base, panel, light, dark, ink, muted, accent, danger — derived per pack |
| `Modifier.raised` / `inset` | extrusion and carving; blur is always twice the offset |
| `Modifier.groove` | dividers as pressed seams, never drawn lines |
| `NeuPanel` / `NeuWell` | raised and inset surfaces |
| `NeuButton` / `NeuIconButton` | raised, sinks when pressed, accent ring on keyboard focus |
| `NeuSegmented` | inset track, raised thumb, accent label |
| `NeuChip` | raised when available, sunk when chosen |
| `NeuField` | inset well with the label above it (a label inside placeholder disappears once filled) |
| `NeuBar` | inset track with a raised fill; segments for the category bar |
| `NeuMetric` / `NeuEmpty` | right-rail figures, and emptiness carved like everything else |
| `NeuDialog` | centred raised panel on a dim of the material's own shade |

Set `BAKI_VIEW=entries|shifts|year|settings|statement` to open the app in that view — used to
capture the design deterministically during review.

### Doctrine (v0.13)

Neumorphism is the *only* design language on the desktop. The rules that make it read:

1. **One material.** `Neu.panel` equals `Neu.base`; every element is the same colour. A test asserts
   it. Depth is shadow, nothing else. No borders, no glass, no second surface.
2. **Elevation means something.** Raised is reserved for content cards, dialogs, menus, the primary
   action and the selected control. Icon buttons and month chevrons are flat until hovered.
3. **Three tiers only** — `Elevation.low` (buttons, chips, rows), `medium` (cards), `high` (dialogs,
   menus). Blur is 2.2x offset; the shade uses a tighter blur than the highlight so it does not
   smudge the top-left corner.
4. **A toned canvas.** Light packs blend the palette surface with a soft grey-green, because pure
   white makes neumorphism look like paper with smudges. Dark packs keep the palette hue.
5. **One accent, filled.** The active nav segment, primary button, selected chips and the rail logo
   use the palette accent with `onAccent` text; everything else is ink on base.
6. **Floating chrome (v0.15.2).** There is no bar at all — not even a header band. Every control is
   its own object resting on the canvas: the month is a raised capsule with a recessed display, sync
   state is a raised chip, the navigation is an inset track with a raised thumb, and every icon
   button and action is raised. The wordmark is the only flat thing, because it is a brand, not a
   control.
7. **A veil, not a shade.** Modal scrims are a darkened canvas (`Neu.scrim`); the shade colour is
   lighter than the base in light packs and would not dim anything.
8. **Labels above fields.** A placeholder label disappears the moment you type; the label sits on
   the canvas above an inset well.

## Typefaces (v0.14)

Three faces, three jobs — the hardware-panel flavour neumorphism asks for:

| Face | Job | Why |
|---|---|---|
| **Space Grotesk** | display, headline, title | retro-futurist hardware lettering; distinctive `R`/`G`/`2` |
| **JetBrains Mono** | every `*Numeric` style | money and chart labels read like an instrument readout |
| **Inter** | body, labels, captions, sections | screen legibility at 11–16 sp |

Weights follow the research: **body 500**, **labels 600**, **headings 700** — Regular and Thin bleed into
soft backgrounds. Labels and section headers carry **+0.02–0.04 em** tracking so letters stay defined
over shadow gradients (`section` = 0.44 sp at 11 sp). All numerals are tabular.

## Shadow hierarchy (v0.14)

There are exactly three levels, and each has one shadow model:

| Level | What | Shadow |
|---|---|---|
| 0 | canvas | none |
| 1 | cards | **dual emboss** — highlight up-left, shade down-right, both clipped to their half-planes so neither smudges the other corner. The face is one hair above the canvas (blend towards white: 0.10 light / 0.035 dark) so edges read without leaving the material; shadows are tinted — the shade runs to deep olive, the highlight to warm white |
| 2 | recessed wells — chart plots, inputs, progress tracks, scrollbar groove | **inset** — the same pair drawn inside the shape |
| 3 | floating — dialogs, menus, tooltips | **drop shadow only**, downward; a floating layer is above the canvas, not moulded from it |

Data inside a cavity carries **no shadow** (chart bars are flat fills); that is what keeps a dense
dashboard from turning into visual noise.

The header is responsive: one row at ≥ 1180 dp, two rows below, window minimum 1080×700. The
scrollbar is a first-class material object: inset groove, raised draggable thumb.

## Material facts (v0.14, measured)

Probed on the running app, light pack: canvas `#D6DCD1`, card face `#DAE0D6`, ink `#14180F`,
secondary `#60655B`, no dark bleed above the card's lit corner. The same probes are repeatable with
`/tmp/shot/Probe.java` against any screenshot.

## Shadow engine (v0.15)

The material delegates all shadow drawing to **`dev.lennartegb.compose:shadow` 0.1.2** (KMP, served
via JitPack as `shadow-jvm`): CSS-style `boxShadow(blur, spread, offset, colour, shape, clip, inset)`.
`raised` is two box shadows (highlight up-left, shade down-right), `inset` is the same pair drawn
inside the shape, `elevated` is one downward shadow for floating layers. `ShadowEngineTest` fails the
build if hand-rolled blur shaders reappear. Verified by pixel probe: highlight halo `#E2E6DF` above
cards, shade `#CED3CB` below, face `#DAE0D6` over canvas `#D6DCD1`.

## Crisp rules (v0.15.1)

- **No drawn lines.** Separators are spacing (`Space.xs`), never 1 dp rectangles; the scrollbar is a
  thumb only, with no groove, because a groove reads as a hard vertical line at the card edge.
- **Bars are flat data.** Solid accent/danger fills, rounded on the *top* corners only
  (`Radius.bar = 6.dp`), no shadow, no blur — the cavity alone implies the depth.
- **Labels are solid ink.** Chart values, dates and category figures use `neu.ink` at full opacity,
  never the blended secondary.
- **Hairline edges.** Raised faces carry a 1 dp ink-at-5% border so they separate crisply from the
  canvas instead of glowing into it.
- **One instance.** A file lock in `~/.local/share/baki/instance.lock`; a second launch prints
  "Baki is already running" and exits, because stacked windows produce hard overlapping edges.
