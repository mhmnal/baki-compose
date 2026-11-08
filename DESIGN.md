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
  (`Radius.bar = 6.dp`), no shadow, no blur — the cavity alone implies the depth. Clip before fill
  (`clip` then `background`) or the rounding never reaches the fill.
- **Bars stand on a baseline.** Each plot has a 1 dp ink-at-12% line under the bars and 16 dp of
  floor padding inside the cavity, so nothing collides with the inner shadow.
- **Shadows stay inside.** Views draw inside `clipToBounds()`, so a card's glow can never escape
  into the window margin below the wordmark.
- **Labels are solid ink.** Chart values, dates and category figures use `neu.ink` at full opacity,
  never the blended secondary.
- **Hairline edges.** Raised faces carry a 1 dp ink-at-5% border so they separate crisply from the
  canvas instead of glowing into it.
- **One instance.** A file lock in `~/.local/share/baki/instance.lock`; a second launch prints
  "Baki is already running" and exits, because stacked windows produce hard overlapping edges.

## Cards drop, they do not bloom (v0.15.4)

A card is one downward shadow (`cardSurface`): no highlight, nothing above the top edge. On a large
face the dual-shadow highlight bloomed into the whitespace under the header and read as a grey
smudge. Small controls (buttons, chips, the segmented thumb) keep the dual emboss, where it reads as
embossing rather than bloom. Probe: canvas is flat `#D6DCD1` above a card, shade `#D3D9CC` below.

## Read-only desk (v0.16)

The line between the clients is now explicit:

| | Phone | Desktop |
|---|---|---|
| Role | capture | understand |
| May | entry, shifts, GPS, routes, widget | read, sort, filter, compare, print, export |
| May not | — | create, edit or delete a record |

`ReadOnlyShellTest` fails the build if `addEntry/updateEntry/deleteEntry/**Shift` or the write dialogs
appear in the desktop shell. The desk keeps **Statement**, **Refresh** (pull), **Settings**
(theme/config) and CSV export.

Destinations: Overview · Entries · Shifts · **Routes** · **Compare** · Year. Comparison logic lives in
common `Compare.kt` (`periodStats`, `compareMonths`, `categoryTotals`) so the phone can reuse it.

Routes are **device-local**: the route store never leaves the phone, so the desktop Routes view shows
what exists on that machine (today: nothing) and an honest empty state.

## Route sync (v0.17)

Routes were device-local on purpose. They can now be reviewed at the desk, **opt-in and sealed**:

- **Off by default.** `Settings → Route traces` on both clients: a toggle and a shared *route key*.
- The phone seals each trace with **AES-GCM**, key = SHA-256 of the shared passphrase, envelope
  `RT1 | ivLen | iv | ciphertext`. Only the two clients know the key.
- `POST /api/routes` stores the opaque blob in `routes.json`, keyed by shift id; `GET
  /api/routes?since=YYYY-MM-DD` returns them. The server never parses a coordinate, and route blobs
  never enter `data.json`, so ledger backups stay free of movement history.
- Upload happens after a normal sync (last 45 days, only shifts that have a route); the desk pulls
  when the Routes tab opens or when **Pull** is pressed.
- Sharing the key is manual: type the same passphrase on both devices. Blank key disables sync.

`RouteCryptoTest` covers round-trip, wrong key, tampering and the envelope; `RouteSyncLiveTest` is an
opt-in end-to-end check against a running server (`BAKI_LIVE_SYNC=1`).

## Signposting pass (v0.17.1)

Reading at a glance is the desktop's only job, so every ambiguous signal got sharpened:

- **Funds**: a shortfall is larger (`subtitleNumeric`), terracotta, and labelled `SHORTFALL`; a surplus
  stays accent.
- **Entries**: category chips (top eight by spend) beside the filter, so scanning a category is one click.
- **Shifts**: the header now carries averages — `avg RM/h`, `avg km` — next to total net.
- **Routes**: the selected drive gets a deeper inset well and a 3 dp accent spine.
- **Compare**: changes carry `↑`/`↓` and keep the direction-aware colour.
- **Year**: idle months drop to 55% opacity; the best month's figure is written in accent.
- **Settings**: the veil is deeper (0.60 light / 0.70 dark) so the modal is unmistakably the focus.

## Shadow library leak (v0.17.2)

The scrim covered only part of the window; the untouched strip was exactly the width of everything
drawn after a shadow. Root cause: `dev.lennartegb.compose:shadow` 0.1.2 leaks a canvas translate on
desktop, so every shadow shifted everything painted after it (the dialog ended up drawn at +303,+14
while its layout stayed at 0,0 — which is why clicks missed).

Fix: each `boxShadow` call now sits inside its own `graphicsLayer()`, so a leaked transform is
confined to that layer. `ShadowEngineTest.everyShadowIsDrawnInItsOwnLayer` fails the build if a bare
`.boxShadow(` appears. Verified by a magenta marker: the dialog's drawn bounds went from
(573,147)-(1709,1015) to the window's own (270,133)-(1709,1015), and the scrim is uniform
`#969B91` on both sides.

## Mist: the soft-UI pack (v0.18)

A fourth palette, taken from the classic soft-UI board: a neutral cool-grey canvas, near-white raised
faces and a blue accent, with **no green tint**.

- `MistLight` surface `#E9EDF2`, canvas derived as `blend(surface, onSurface, 0.06)` → `#DEE2E7`,
  face one hair above it; `onSurface` `#2A3038`; accent `#2E6BD6` (the board's `#5B8DEF` darkened so
  white text clears AA); terracotta `#A8432C`; green `#1F6B4B`.
- `MistDark` is the same recipe at night: `#14171C` canvas, `#8FB0F2` accent.
- `BakiPalette.softUi` switches the material recipe: neutral canvas instead of accent-toned, and
  shadows tinted grey-blue instead of olive. The derived secondary is mixed 0.74 (not 0.66) so it
  clears 4.5:1 on the neutral canvas.
- Available on both clients: Settings → Colour → **Mist**. Contrast and material tests cover both
  variants.

## Critique pass (v0.18.1)

- **A crisp emboss rim.** Cards gain a tight top-left highlight (offset −2 dp, blur 3 dp, so it rises
  1 dp past the edge) — the dual-shadow read without the bloom that used to smear into the header.
- **Secondary text one notch darker.** Canvas secondary mixed 0.72 (sage/paper) / 0.78 (mist); 0.64 in
  dark packs. Section labels, captions, inactive tabs all inherit it.
- **Signed bars.** Losses hang *below* a zero line instead of being drawn upward: −60 no longer looks
  bigger than +39. `SignedBars` is pure and unit-tested; rounding flips with the sign.
- **Trend is now a trajectory.** The old daily bars duplicated "Last 7 Days"; the card plots
  **cumulative net through the month** as a line, ending at `now RM …`.
- **"shortfall" unwrapped** onto its own caption line under Fuel/Wear.
- **Clearer affordance.** Raised controls carry a stronger hairline (ink 10% light / 14% dark) and
  secondary button labels are semibold.

## True neumorphism (v0.19)

The desk now implements the material literally instead of a soft approximation.

| Tier | Distance / blur |
|---|---|
| Card | **9 dp / 16 dp** |
| Control (button, chip, segmented thumb) | **5 dp / 10 dp** |
| Well, input, track | **4 dp / 8 dp, inset** |
| Floating layer (dialog, menu) | one downward drop |

- **Face == canvas.** `Neu.panel` equals `Neu.base` in every pack; a test asserts it. Depth is only
  light and shade.
- **Light source top-left, always.** Highlight up-left, shade down-right, tinted: near-white
  `#F8FBFF` mixed into the canvas, and a blue-grey shade (`#2E3C4C`, or `#070A0E` in dark packs).
  No black-with-alpha anywhere.
- **Dark packs reduce distances by 40%** (`Elevation.distance/blur` scale by 0.6).
- **Selected states are carved:** the active segment and chip are *inset* with the flat accent fill;
  pressed buttons are inset; Refresh, the active tab and bars keep flat solid fills.
- **Room to breathe:** 32 dp gutter and 32 dp between cards (≥ blur + distance = 25 dp), and the
  content pane no longer clips, so a card's emboss is never cut.
- The emboss-rim experiment is gone.

Shadow-clipping ancestors, audited: the content `Box.clipToBounds()` was removed; the only remaining
clip is the **vertical scroll viewport** (inherent to scrolling — a card scrolled to the edge loses
its outer shadow), plus components clipping their own content (`NeuPanel`, wells, dialogs), which is
self-clipping, not an ancestor.

Captured all four packs in both modes (`BAKI_PALETTE`/`BAKI_MODE`): above every card the first
deviation from the canvas is **lighter** (+0.008…+0.011 luminance), never a dark smudge; dark packs
show no deviation at all. Paper and Night are single-mode packs by design — Colour and Appearance are
separate axes, which the Settings explainer now states.

## Shadow room at the scroll edge (v0.19.1)

The first card's 16 dp blur was being cut by the scroll viewport's top edge, which read as a bar
clipping the design. The header was never the cause — it has no bar. Fixes:

- the scroll content carries a 32 dp top inset **inside** the scroll (so it moves with the content),
  giving the 9/16 emboss its full 25 dp of room. v0.19.2: Overview and the right rail were missed by
  the first pass (they use their own `verticalScroll` call); every scroll container is now audited;
- the header's own 5/10 capsules get 16 dp of window padding (their reach is 15 dp);
- an 18 dp gradient from canvas to transparent sits at the viewport edge, so a card scrolled under it
  fades instead of being sliced.

The header stays fixed. If it should scroll away with the page instead, moving `Header` inside the
scrolling `Column` is the only change needed.

## Clipped insets (v0.19.3)

The shadow library's `inset = true` leaked outside rounded corners: a dark crescent plus a bright
seam at every well. The inset tier is therefore drawn locally (the only hand-rolled shadow left):
two blurred strokes, dark from the top-left and light from the bottom-right, **clipped to the
rounded path**, balanced save/restore so no canvas state leaks. Strength is `Elevation.INSET_STRENGTH` (0.72 × the outer tier), one constant; `BAKI_INSET`
overrides it at launch (e.g. `BAKI_INSET=0.55 baki`) so it can be judged live. Outer shadows (cards, controls,
floating layers) still come from the library, inside their own graphics layers.
`ShadowEngineTest` allows blur code only within `inset()` and requires the `clipPath`.

Also: outer shadow order is now **shade first, highlight last**, so the lit corner is never tinted by
the shade — the previous order produced a dark arc at card corners.

## Flat plots (v0.19.6)

The chart plots (Last 7 days, Month trajectory) are **flat trays**: `NeuWell(inset = false)`. The
cavity added no information there and was the only place the corner artifact showed. Every other
well keeps its inset — text fields, the segmented nav track, table hover, pressed controls and the
scrollbar groove.

## Insurance backfill (v0.19.7)

The renewal rule changed, but historical fees were derived under the old start-only rule and are only
recomputed on a write. Audited against the live store: 8 shifts, 4 fees (RM 30.00) recorded, while the
renewal rule wants 6 windows (RM 45.00). Backfilled with `recomputeCover` through the normal merge path
(`InsuranceBackfillLiveTest`, opt-in via `BAKI_BACKFILL=1`), after discovering one needed fee entry was
**tombstoned** from the old rule and therefore rejected by the server.

`pushWithTombs` now undeletes any `grabfee` entry that recompute legitimately re-creates, so a fee can
never be silently dropped by an old tombstone again.

## Instant tabs (v0.19.8)

Tab switches were crossfaded (140 ms in / 90 ms out). A fade composites **two complete pages**, each
with its own emboss, so the incoming shadows ramped from 0 to full alpha while the outgoing ones
faded — the "settling" that read as jank. Neumorphic depth is geometry, not opacity, so it cannot be
faded without looking wrong. Tabs now swap instantly, and `DesktopConsistencyTest` fails the build if
`AnimatedContent`, `Crossfade`, `fadeIn` or `togetherWith` reappears in the shell.

Motion that remains: the animated money counter (numbers only) and hover/selection state changes
(instant). Neither touches shadow geometry.

## Weekly statement import (v0.20)

The Grab statement is the income side of the books; the app is the expense side. `StatementParse` reads
the PDF text (BM or English labels), the phone picks the file, and `PdfText` extracts it with PDFBox
(`pdfbox-android` on the phone, `pdfbox` on the desk).

Parsed: week range, total income, total deductions, weekly payout, transport net, pickup/trip fares,
other income, incentives, tips, daily-insurance count, and a consistency check
(`income + deductions = weekly`).

**Tolls are not in the statement.** Verified against a real 14-page statement: there is no toll line,
Grab folds the reimbursement into the trip fare. So tolls stay an app-side estimate for route
decisions, never a reconciliation figure.

The More screen shows the last statement and compares it with what was logged for that week
(gross income gap, insurance count vs the cover rule).

## Tax pack (v0.21)

Year → **Tax pack** produces the Borang B picture from the records the app already holds:

- income: logged shift earnings + standalone income entries; imported Grab statements shown as a
  cross-check (not added, to avoid double counting);
- expenses by category, each marked deductible or not — fuel, toll, parking, car wash and maintenance
  are vehicle costs (pro-rated by business use), phone, Grab insurance and road tax are full business
  costs, food and “other” are never deducted;
- mileage: business km from shifts, an optional total-km field for the business-use share (blank =
  all business), and the insurance window count;
- net business income, printable HTML (A4) and CSV.

`taxYear()` is pure and tested; `taxPackHtml`/`taxPackCsv` are rendered from it.

## Bills (v0.22)

Recurring commitments tracked by **period**, not due date, so a month can be settled before it starts:

- `Bill` (monthly or annual, amount, category, business flag, optional goal) and `BillPayment` (links a
  period to the ordinary expense entry created when it is paid) live in the store and sync; the server
  whitelists and merges both.
- Paying a bill creates a normal expense dated **the day you paid**, with a note naming the period;
  the payment link marks that period covered. Paying in November for December still records November cash.
- The phone's Bills card shows what is covered this month, lets you mark each bill paid, and has one
  **Settle all for <month>** action that pays every open committed bill for that month. When a month is
  fully covered it offers the next one, and so on (prepay ahead).
- Optional bills (the car loan the family covers) are never due and never in the survival total; they
  appear only when the month's net can absorb them.
- Tax: subscriptions and phone are business costs; rent and car-loan principal are never deductible
  (LHDN allows capital allowance on the vehicle, not the loan principal).

Seeded: Rent 270 monthly (personal), Spotify 17.50 monthly (business), CelcomDigi 100 monthly
(business), Google 100 annual in January (business), Car loan 200 monthly (optional).

## Linking an existing entry (v0.22.1)

Bills can be marked covered by an expense that already exists, without recording new money:
`Bills.linkCandidates()` offers expenses in that period whose **amount matches** or whose **category
matches**, excluding entries already linked to any bill. This is how the November rent (logged as
"Other") and the November Spotify (logged under "Phone") can be attached to their bills instead of
being paid twice.

`Repo.linkBillPayment()` writes only the payment link (amount taken from the entry, paid date from the
entry), so the ledger does not change.

Known gap: bills themselves still have no add/edit screen — the five seeded commitments are fixed
until an editor exists.

## Bills-only sync fix (v0.22.2)

Bills were seeded server-side while the phone still ran a build without the bill model. After updating,
the phone kept showing "0 of 0" even after Sync now: `sync()` decided whether to save the merged store
by comparing only `updatedAt`, entry count, shift count and tombstone count. A merge that changed
**only bills / bill payments / statements / settings** compared equal, so the merged store was thrown
away — the data had arrived and been discarded.

`changed` is now `merged != store`, a full data-class comparison, so any field a future version adds is
saved too. `SyncChangeTest` guards the bills-only case. The Bills card also shows a "No bills loaded yet
· tap Sync now" state instead of inventing a far-future month for the settle button.

## Bill editor and monthly commitments (v0.23)

- **Add / edit / delete** from the Bills card: name, amount, monthly or annual (with renewal month),
  category chosen from the existing categories, a **business** flag for the tax pack, and an
  **optional** flag for leftover goals. Tapping a bill opens it for editing.
- Unlinking a payment is a **soft removal** too (`BillPayment.active = false`): the expense stays in the
  ledger, only the covered mark goes, and the change syncs because the newest row wins the merge. A
  link whose expense was deleted stops counting as covered on its own.
- Deletion is a **soft delete** (`active = false`), because bills have no tombstone on the server; the
  flag syncs and the bill disappears from every list. A bill with payments cannot be deleted.
- The card header now states the month's commitments: `November 2026 · RM 387.50 committed`, the
  average including annuals (`RM 395.83/month`), and `0 of 3 covered`. The desk shows the same two
  figures in its BILLS block.
- `Bills.committed()` counts active, non-optional bills falling due in that month; `averageMonthly()`
  spreads annual bills over twelve months.

## History and category rename (v0.24)

- **Bill payments history**: `Bills.history()` resolves every payment against its bill and expense,
  newest first, and marks broken links (`unlinked`, `expense deleted`) so a covered period with no
  money behind it is visible rather than silent. Reached from **History** on the Bills card.
- **Rename categories**: tapping a category name opens a rename dialog; the id never changes, so
  history keeps pointing at the same category. The id-keyed merge means the new name wins on every
  device. Add and remove (only when unused) behave as before.

## Desk payment log (v0.24.1)

The BILLS block on the desk now lists the five most recent payments (`Bills.history()`), each with bill,
period, amount and paid date; broken links show `unlinked` / `deleted` in the danger colour instead of
being hidden. Read-only, like the rest of the desk.

## Layout revision (v0.25)

Phone:
- quick-add chips and the category filter now use `FlowRow`, so "Car wash" and "Deepseek" are visible
  instead of being clipped at the card edge (both rows were technically scrollable, which is not
  discoverable). Only categories used in the month are offered as filters, and presets are capped at the
  eight most-used, so filters stay two rows instead of five. Presets skip categories already offered as
  one-tap amounts above them; the same chip twice was pure noise.
- the floating **+** is gone; it covered the first entry's amount. Adding lives in the ledger header
  ("+ Add") next to "see all", which is where the list is.
- the sync dot carries its state as text ("synced", "offline", "cached", "syncing", "local") instead of
  being an unexplained coloured dot.

Desk:
- Year lists only months with activity; idle months collapse to one caption ("8 months without
  activity") instead of eight `RM 0.00` rows and a future dash.
- Entries group by day: the date becomes a header with the day's totals, the repeated per-row date is
  gone, and the note column gets the space.
- Bills reads "open now: November 2026" when the month still has unpaid bills, "next open: …" otherwise.

## Inset repair (v0.26)

`NeuWell` painted `background(neu.base)` **after** `inset()`, covering the cavity it had just drawn. Every
inset well — text fields, segmented tracks, chart plots — was therefore flat in practice, even though the
code asked for depth. When `inset` is true the well now clips only; the modifier's own fill and blurred
inner rings do the rest.

- Month stepper rebuilt as one recessed capsule with flat chevrons inside; it used to stack a raised pill,
  two raised icon buttons and an inset label, which read as three separate bubbles.
- The two Overview plots and Compare's side-by-side bars sit in cavities again (`Elevation.inset`), now
  that the material can show them.
- A selected drive row uses an `accentSoft` tint instead of `inset`: selection is a state, not an input,
  and the ring showed as a dark strip along the row's left edge.

## Soft UI 2.0 compliance (v0.27)

The desktop brief is now implemented literally; the desk is not Material and never was, but three
parts of the material did not match the brief:

- **Tokens.** Light canvas `#E0E5EC`, dark `#1E222B`; ink `#1E293B`/`#F8FAFC`; Electric Blue accent
  `#3B82F6`; Emerald `#10B981`; danger red. The brief's `#64748B` secondary is 3.76:1 on `#E0E5EC`
  (below AA), so it is blended 20% toward ink to 4.68:1. The desktop palette picker is gone: one canvas
  and one accent per mode, Appearance still chooses light/dark. The phone keeps its own themes.
- **Shadow engine.** The JitPack `shadow` library is removed. `raised` and `elevated` draw their own
  dual blurred rounded rectangles with Skia `MaskFilter`, dark first and light last, each inside a
  `graphicsLayer`; `cardSurface` delegates to `raised`, and `inset` keeps its clipped inner rings.
- **Overlays.** Dialogs are frosted glass: translucent face, thin white border, no neumorphic double
  shadow, over a scrim. (True backdrop blur would need a backdrop capture; the translucency and border
  are the practical part of the spec.)
- Material chrome is still forbidden by `DesktopConsistencyTest`; `material3.Text`/`Icon` remain only as
  text/glyph primitives.

## Surface depth rules (v0.28)

The desk now follows the extruded/sunken rulebook literally:

- **Extruded:** header bar, Overview sidebar, boundary cards, resting buttons and unselected chips.
- **Sunken:** text inputs and search fields, segmented tracks, active chips/tabs, the month stepper, and
  progress/stacked bars (`NeuBar` is an inset groove; the fill runs inside it).
- **Flat/hybrid:** canvas background, icons inside buttons, and primary CTAs — accent fill with a single
  soft outer drop (`elevated`), not a second neumorphic emboss. Pressed/disabled primaries sink.
- No extruded card inside another: the sidebar used to wrap its content in a raised `NeuPanel`; that
  panel is gone and the sidebar itself is the structural surface.
- The sidebar's scroll now lives in an inner column. Applying `verticalScroll` to the same chain as
  `raised` clipped the first ~70dp of content; separating surface and scroll fixed it.

The rulebook also clarified why the header and rail were raised: they were previously bare canvas.

## Chart bay rules (v0.29)

- Every data-visualisation bay (Last 7 Days, Month Trajectory, Compare bars) uses one inset depth:
  **offset 3dp, blur 4dp** (`Elevation.viz` / `Elevation.vizBlur`). `NeuBar` tracks share it, so no screen
  mixes two chart depths.
- Bay content is padded **16dp on all sides** and clipped by the well's rounded shape, so negative bars
  and edge labels never sit on the inner shadow. Text fields and empty states got the same 16dp inner
  padding because they also hold long text inside a sunken well.
- `ChartBayTest` guards the shared depth and the 16dp padding.

## Selection is depth, not colour (v0.30)

Selected controls are pressed into the surface instead of filled with an accent:

- active tab (segmented thumb), active filter chip, and the selected drive in Routes all use an inset
  with a face darkened by 6% toward the shade, plus bold ink. No accent fill, no accent bar.
- the Routes row keeps a 4dp side margin so its inset ring is not clipped into a dark strip at the card edge.
- primary CTAs remain accent-filled + soft drop (they are actions, not selections); focus rings stay
  Electric Blue.

## Obsidian Slate dark mode (v0.31)

Light mode is unchanged. Dark mode now follows the Obsidian Slate brief exactly:

- canvas `#1E222B`; text `#F1F5F9` / `#94A3B8`; Electric Blue `#3B82F6`, Emerald `#10B981`, red `#EF4444`;
- raised surfaces use `#282D39` at 0.90 (top-left) and `#12151B` at 0.95 (bottom-right), offset 6dp, blur 10dp;
- every sunken surface (inputs, tracks, chart bays, selected rows) uses `#14171D` at 0.90 top-left and
  `#282D39` at 0.70 bottom-right, offset 3dp, blur 5dp, clipped to the rounded shape;
- selected rows are sunken with a 4dp Electric Blue indicator (no flat colour overlay); selected tabs and
  chips are sunken with bold ink;
- primary CTAs are extruded accent keys filled Electric Blue with white text (light mode keeps the hybrid
  accent + single drop).

Theme resolution fix: the desk previously derived dark from the *colour pack*. With a stored `paper`
pack it stayed light even when Appearance was Dark. The desk is palette-independent, so it now resolves
`paletteFor("sage", dark)` from the mode; the phone keeps its own packs.

## Stepper arrow discs (v0.31.1)

Each chevron in the month stepper painted `background(neu.base)` over the inset capsule, so the arrows
read as two separate circular buttons sitting inside the groove. The arrows now paint nothing at rest
(only a soft accent tint on hover), leaving one continuous sunken capsule with the chevrons carved into it.

## Desk polish (v0.32)

- sync status is a flat dot + label on the header, not a raised chip on a raised bar;
- the sidebar has its own scrollbar (the last metric no longer looks chopped at rest) and 16dp bottom
  breathing room; hairline rules separate spending / funds / bills / recent metrics;
- the header-to-content gap tightened from 32dp to 16dp.

## Signed bar scaling (v0.32.1)

The 7-day chart scaled every bar against the full plot height while positioning zero wherever the
positive/negative peaks met, so the negative peak ran past the bottom of the plot and straight through
its day label. Each sign is now scaled into the space it owns above or below zero, and the bar is clamped
to that space. Bars are also narrow (30dp, centred) with rounded data ends and a stronger zero baseline;
the bay stays the tactile surface, bars stay flat data ink (extruding seven bars inside a sunken bay
would fight its inner shadow).

## Trajectory redraw (v0.33)

The month trajectory is a sparkline rather than a bare polyline:

- Catmull-Rom curve (1/6 tension) so a running balance reads smoothly;
- gradient area under the line (accent 34% -> transparent), clipped by the sunken bay;
- quiet low/mid/high grid and a stronger zero line when the balance crosses it;
- a dot per day, an Emerald dot on the peak, and a haloed "now" endpoint;
- taller canvas (168dp) so the curve has room; labels keep 11-01 / now / 11-08.

## No desktop scrollbars (v0.33.1)

The desk is mouse-driven: the wheel and trackpad already scroll, so the drawn scrollbars were noise.
Removed from the sidebar and from Entries, Shifts and Compare. Scroll state and bottom padding remain,
so every item is still reachable; there is simply no persistent track/thumb.

## Statement import friction (v0.34)

- **Share straight into Baki**: the Android activity accepts `ACTION_SEND` and `ACTION_VIEW` for
  `application/pdf`; the bytes land in `SharedStatement`, and the app parses/saves them and shows a Toast.
- **Bulk import**: the picker is multi-select (up to 12 PDFs); each is parsed, valid weeks are saved
  (keyed by week, so re-imports replace), and the card reports imported vs skipped plus "weeks on file".
- WorkManager's Room database is opened reflectively; R8 stripped it in the first release and the app
  crashed at startup. Keep rules for `WorkManager`/`RoomDatabase` are now in `proguard-rules.pro`.
- **Wednesday reminder**: a WorkManager periodic task fires the next Wednesday at 10:00 and posts a
  notification; the device flag `statementReminder` (default on) is exposed as a switch on the statement
  card. Desktop compiles the same contract as a no-op.

## Current state (v0.36.4)

- **Phone**: the pre-minimal glass interface (v0.34.1) is the shipped design. The "less, but better"
  Today screen and its M3/WCAG pass are archived at tag `v0.35.4`; they are not in the build.
- Natural-language quick entry and voice were removed in 0.35.6 (unused); quick-add chips and the full
  Add sheet remain. The parser and its tests stay in the codebase.
- The floating glass **+** was restored above the nav in 0.35.8; it is glass, so it refracts the content
  directly behind it.
- PDF statement import: share/open PDFs, multi-select, Wednesday 10:00 reminder (v0.34/0.35).
- **Desk**: neumorphic Soft UI 2.0 in light and Obsidian Slate in dark, read-only, unchanged since 0.34.

## Durability pass (v0.36.0)

Storage and sync fixes after the feasibility audit:

- The dirty/outbox bit now lives in `ClientConfig.pendingPush` (device-local, encrypted). Every
  `saveLocal` marks it; only a successful push clears it. `sync()` therefore retries after a restart,
  and the retry push carries `store.tomb.ids` so deletions propagate too. The sync 304 path and the
  fresh-data path both go through one `pushAll()`.
- Local writes fsync the temp file, copy the previous file to `.bak`, and verify the rename. A store that
  cannot be decrypted or parsed is quarantined as `store.json.corrupt-<stamp>`; the app tries the local
  `.bak`, then the server, and surfaces the result in Diagnostics instead of opening empty.
- `StoreHealth` exposes load/save errors; Diagnostics shows them.
- Backup rotation only copies an existing store to `.bak` when it still carries the sealed envelope
  (`looksSealed`); a corrupt or empty file can no longer overwrite the last good backup.
- Verified on a side-by-side debug build against an isolated server copy: an entry added offline and a
  deletion made offline both survived an app restart and reached the server after it returned (tombstone
  included); a zeroed store was quarantined and the `.bak` restored, with the reason shown in Diagnostics.
- Verified on the phone debug build: offline add and delete both survived restart and reached the server;
  a zeroed store was quarantined and the backup restored. Release 0.36.0 carried the first pass; 0.36.1
  completes it with the all-tombstones push and the quiet background sync.
- Post-0.36.0 fix: every push path now sends the union of its explicit tombstones and `Store.tomb`, through
  one `pushWithAllTombs`; a successful add/edit can no longer clear the outbox while an older delete is
  still unknown to the server. Background syncs no longer set the header to "syncing" (manual syncs do).
- Still outstanding: server authentication, off-machine backups, and server-side fsync/error propagation.

## Totals correctness (unreleased)

- `dayTotals`, `monthTotals` and `weekTotals` now include `shift.incentive`; they were silently leaving
  incentive pay out of every day/month total.
- `Shift.reimbursedTolls` makes a toll a pass-through: it stays recorded in the shift and the ledger, but
  it is excluded from cash and operating costs.
- `dayOperating` / `monthOperating` are the canonical operating aggregates from `shiftMetrics`; they never
  include linked `fee-<shiftId>` cash entries, so insurance can't be counted twice. Shipped in 0.36.2.
- Today shows a secondary “Operating · after running costs” line so the recorded total and the operating
  total are no longer confused. The desktop overview is labelled “recorded, not operating”.
- Tests: incentive income, reimbursed tolls, fee-entry non-double-count, hours/rate.

## Hero pair and list cleanup (v0.36.3)

- Today's recorded net is still the hero; operating net/rate sits beside its label, on two lines,
  so the tall money figure never gets squeezed into a column.
- Spending/Shifts use human dates; repeated swipe/hold copy was removed; Shifts keeps one hint.
- A one-day month gets one "only day" card instead of identical best/worst cards.
- Desktop labels its recorded per-hour and operating last-week figures explicitly.
- The preview harness clears live.json so frames cannot inherit a stale live shift.

## Statement history and permission state (v0.36.4)

- Every imported week is listed behind View all weeks, with income/deductions, insurance reconciliation,
  logged gap and imported time.
- The Wednesday reminder shows a blocked-notification warning with Open settings / Check again.
- Share/VIEW accepts application/octet-stream as well as application/pdf, then sniffs %PDF within the first
  KiB; a renamed non-PDF is rejected.
