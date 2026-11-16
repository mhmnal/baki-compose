# Baki — Material Design 3 Migration

Version: **0.40.6 / code 127 — signed and installed**

## Design direction

Baki now uses Material Design 3 as its visual and interaction foundation. The financial model,
ledger data, shift tracking, budgets, reserves, reconciliation and sync are unchanged. This was a
presentation and interaction migration, not a data migration.

The design system is now:

- **Dynamic Material You colour** on Android 12+, with Material baseline light/dark fallback.
- **Tonal surfaces** through `surface`, `surfaceContainerLow`, `surfaceContainer`,
  `surfaceContainerHigh`, `primaryContainer` and `onSurfaceVariant`.
- **MD3 shape scale**: 4dp extra-small, 8dp small, 12dp medium, 16dp large, 28dp extra-large.
- **Material type roles**: display/headline/title/body/label, all using the Inter sans family.
  Money uses tabular sans figures, not a serif override. No Source Serif, Space Grotesk or
  JetBrains Mono is loaded by the phone UI.
- **Standard MD3 components**: TopAppBar, NavigationBar, NavigationRail, morphing FAB, Cards,
  PrimaryTabRow, SegmentedButtons, filled search, FilterChips, tonal buttons, dialogs and bottom sheets.
- **Expressive containers**: primaryContainer statement cover, secondaryContainer week card,
  tertiaryContainer reserve card, surfaceContainerHigh chart and cash cards.
- **Interactive charts**: rounded tonal bars, solid target line, no dashed floating rules, 7D/30D
  FilterChips and tap-to-inspect day details.

## Features removed or simplified

These were Paper-Ledger presentation features that clashed with MD3:

| Removed / simplified | Reasoning |
|---|---|
| Liquid-glass navigation bar and floating `+` | Replaced by standard NavigationBar and FAB with proper state layers and tonal roles. |
| Glass compact header overlay | Replaced by one stable MD3 TopAppBar. Avoids double-overlay hierarchy and contrast drift. |
| Paper grain canvas | Removed. MD3 hierarchy comes from tonal surfaces, not texture. |
| Hairline-only page architecture | Cards and tonal containers now group content; dividers remain only as separators inside cards/lists. |
| Paper Ledger / Sage / Night colour-pack selector | Removed from Appearance. Dynamic colour plus light/dark is the system. |
| Liquid-glass preference | Removed from Appearance; glass is no longer an active surface. |
| Match-wallpaper switch | Dynamic colour is now the default; no separate opt-in is necessary. |
| Old More underline tabs | Replaced with MD3 PrimaryTabRow. |
| Old custom bottom-bar geometry | Replaced with NavigationBar on phones and NavigationRail on tablets. |
| Paper-specific section abbreviations and all-caps labels | Section titles now normalise to sentence case and use MD3 title roles. |

No financial feature was removed.

## Features added or redesigned

### Theme and colour
- Dynamic light/dark schemes from the wallpaper on Android 12+.
- Material baseline schemes as the desktop/preview and unsupported-Android fallback.
- Semantic income/expense colours come from `tertiary`/`error` inside the active scheme.
- Dialog surfaces use `surfaceContainerHigh`; cards use `surfaceContainerLow`;
  navigation uses `surfaceContainer`.
- Dark mode checked in preview and on-device.

### Navigation and shell
- One MD3 TopAppBar with brand, privacy action and sync status.
- NavigationBar on compact screens with five destinations and one-line labels at large type.
- NavigationRail at ≥600dp, with its own primary FAB and a 960dp content max width.
- Standard 56dp FAB on Today for adding entries.
- Modal surfaces hide navigation and FAB while open.

### Typography and shape
- MD3 shape scale wired through `MaterialTheme.shapes` and `Radius`.
- Cards use 12dp medium shapes; sheets/dialogs 28dp; chips/buttons 20dp.
- Monetary hero figures keep an expressive serif face; controls and body use Inter.
- Type hierarchy uses title/body/label roles rather than paper-specific display labels.

### Content and data visualisation
- Summary converted to tonal cards: statement cover, month-in-ink chart, week review,
  category breakdown, calendar, reserves, cash truth and best-hours evidence.
- Category names now resolve through the configured category catalogue instead of showing raw IDs.
- Month-in-ink opens at 7D to reduce sparse empty chart space.
- Week review, category shares, calendar cells and reserve meters use the same MD3 colour roles.
- Day Page entries and shifts are individual tonal cards with an overflow action menu; attaching,
  replacing or removing a receipt no longer consumes two permanent text lines per row.
- Running balance is explicitly labelled **entry balance**.

### Forms, sheets and dialogs
- Bottom sheets use 28dp top corners and `surfaceContainerLow`.
- Dialog containers use `surfaceContainerHigh`.
- Search uses an OutlinedTextField with a leading search affordance.
- Appearance uses a Material SegmentedButton row.
- Destructive and primary actions use tonal buttons rather than heavy dark pills.
- Day Page, reserve history and statement history use the same dialog/sheet language.

## Screen-by-screen

### Today
- Fixed TopAppBar; date pager card; net hero; operating block; in/out chips.
- Seven-day food strip uses tonal containers with semantic positive/negative colours.
- Shift and live cards are tonal MD3 cards.
- Quick Add chips remain; entry list uses cards.
- FAB opens the add-entry flow.
- Empty state remains a proper MD3 empty page.

### Spending
- Month header, large spent hero, MD3 OutlinedTextField search and FilterChips.
- Envelope and entry rows are tonal cards with consistent 8dp separation.
- Swipe/long-press actions retained; overflow and accessibility actions preserved.
- Entry titles use category display names.

### Shifts
- Net hero, tonal Add shift action and individually spaced shift cards.
- Cards show date, km, RM/km and band; long-press edit and swipe delete preserved.
- Empty state explains the page purpose.

### Summary
- Statement cover, month chart, week strip, category breakdown, calendar, reserves,
  cash truth and best hours are now MD3 cards on `surface` with tonal panels.
- Charts use dynamic `primary` and semantic negative colours.
- Reserve Manage opens a 28dp dialog with target and transaction history.
- Chart default is 7D; 30D remains available.

### More
- TopAppBar and PrimaryTabRow are fixed; only the settings body scrolls.
- Bills, statements, vehicle, categories, safety and device sections remain.
- Appearance now contains only Auto/Light/Dark and a dynamic-colour explanation.
- Standard SegmentedButtons, switches, outlined actions and tonal CTAs.
- Statement history is a Material dialog, not a sheet behind the navigation bar.

### Day Page
- Full-screen MD3 page with tonal entry and shift cards.
- Per-row overflow menu contains attach/replace/view/remove receipt and delete.
- Day note, running balances, close/reopen and shift editing remain.
- Receipt preview stays a full-screen image surface.

### Add entry / shift / budget / reserve flows
- Standard MD3 sheet geometry; OutlinedTextFields; segmented choices; tonal primary action.
- Budget envelope carry and monthly move remain.
- Reserve target, top-up, withdrawal and history remain.
- Read-only guards remain visible and actionable.


## Expressive pass (0.40.0)

- Removed the remaining divider/rule shim; all separation now comes from tonal surfaces.
- Deleted the unused glass and paper-texture source files and removed the QR paper grain.
- Statement cover now uses `primaryContainer` / `onPrimaryContainer`.
- Week summary uses `secondaryContainer`; reserves use `tertiaryContainer`; chart/cash use
  `surfaceContainerHigh`.
- Month-in-ink selectors are MD3 FilterChips; 7D now ends at today instead of showing future days.
- Extended Add entry FAB morphs into a compact FAB as the page scrolls.
- Spending search is a filled `TextField` on `surfaceContainerHighest`; category filters are
  FilterChips with state-layer ripple.
- Day entry rows support long-press actions and a receipt thumbnail badge.
- Screen transitions use the emphasized cubic-bezier `(0.2, 0, 0, 1.0)` with short critically
  damped fades.
- All hardcoded palette hex was removed from common source. Legacy palette names now alias Material
  baseline roles for persisted-config compatibility; the active scheme is dynamic/baseline MD3.
- All custom `BorderStroke` card outlines were removed. Cards use single 16dp tonal surfaces.
- Both charts sit inside explicit `surfaceContainerHigh` cards with **24dp** corners and
  24dp content padding, with tonal tracks and rounded `primary`/`primaryContainer` bars. Bars sit in tonal tracks; active positive bars are `primary`, weekly
  non-today bars use `primaryContainer`, and negatives use `error`. The target is a solid line.
- Chart range selector and category chips have no outline: they are filled tonal FilterChips.
- Spend/Shift rows use roomier 20dp vertical card padding, 16dp single surfaces and 12dp gaps.
- Verbose bills/statement/vehicle helper copy was shortened.
- Live-start and add-shift actions are tonal rather than high-contrast dark pills.

## Data integrity

- No schema changes were introduced by this migration.
- Existing Phase 1–3 fields, server merge logic and tombstones are untouched.
- Theme packs remain in persisted config for backward compatibility but no longer control the active
  scheme.
- No live ledger data was edited during verification.

## Verification

- **225 desktop tests, 0 failures.**
- Dynamic light/dark previews rendered.
- Phone: 360dp at 1.3× font scale checked; nav labels are single-line.
- Tablet: 800dp logical width checked with NavigationRail and 960dp content cap.
- Signed 0.40.0 release installed and launched without fatal errors.
- Expanded and collapsed FAB states verified on-device.
- Release signed with the existing certificate.

## Known residuals

- Legacy Paper/Glass composables and palette data remain in source for compatibility but are no
  longer on active screens.
- Some outline text-field shapes still inherit Material3's default extra-small shape; the main
  forms use MD3 shape roles, and a full per-field shape sweep remains desirable.
- The month chart is still a custom Canvas inside a Material card; a future pass could replace it
  with a dedicated chart component with accessible data table semantics.
- Widgets and a desktop Material shell were not part of this migration.

## Hard cleanup (0.40.2)

- Deleted `Glass.kt` and `PaperTexture.kt`; removed QR paper grain.
- Removed every custom `BorderStroke` and `.border(...)` from cards, buttons and calendar cells.
- All cards now use the single-surface 16dp `MaterialTheme.shapes.large` treatment. The
  swipe-to-delete background is transparent when settled, removing the last card rim.
- Calendar today/best markers use tonal fills, not outlines.
- Removed the forced serif source for display and money. All text is sans; money remains tabular.
- Rebuilt Month in Ink with rounded primary/error bars, a solid target line, FilterChip ranges,
  tap-to-select state and a selected-day detail card.
- Rebuilt weekly bars with rounded tonal bars and baseline dots for empty days.
- NavigationBar keeps explicit 80dp geometry, standard active indicator, tonal container and
  one-line labels. NavigationRail/FAB remain adaptive.
- Shortened explanatory copy and removed dead layout hacks.
- 225 tests pass; signed 0.40.4, code 125.

## Live Shift and sheets (0.40.5)

- Live Shift is now a single `surfaceContainerHigh` card with 24dp corners and 20dp padding.
- The dashed animated live trace and all ledger rules were deleted from the card.
- Net-so-far uses a sans `headlineLarge` tabular figure; status is a tonal dot + label.
- Payout and Pause are `FilledTonalButton`/`Button`; End is a destructive `Button` using
  `errorContainer` / `onErrorContainer`.
- `BakiSheet` was rewritten as the standard Material 3 `ModalBottomSheet`: 28dp top corners,
  standard drag handle, standard scrim, tonal container. The app-level 18dp background blur was
  removed entirely.
- Deleted the dead glass bottom-bar code and the animated dashed LiveTrace file.
- The Add chooser uses tonal surfaces; its primary choice is primaryContainer, not a stark white tile.
- 225 tests pass; signed 0.40.6, code 127.

## Material 3 Expressive upgrade (0.41.0)

### Verified APIs and versions

- Compose plugin: `org.jetbrains.compose` **1.12.2**
- Material 3 artifact: **`org.jetbrains.compose.material3:material3:1.12.0-alpha03`**
- Previous resolved artifact `1.9.0` contains `MotionScheme` and Expressive tokens but **not** the
  public ButtonGroup/SplitButton/FABMenu/FloatingToolbar/LoadingIndicator composables.
- `1.12.0-alpha03` ships `ExperimentalMaterial3ExpressiveApi`, `MotionScheme.expressive()`,
  `ButtonGroup`, `ButtonGroupDefaults`, `SplitButtonLayout`, `FloatingActionButtonMenu`,
  `LoadingIndicator`, `ContainedLoadingIndicator` and all `MaterialShapes`.
- Its Compose dependencies are 1.12.0-beta01; the project’s 1.12.2 Compose artifacts supersede them.

### Five Expressive pillars applied

1. **Real components**
   - Month-in-ink 7D/30D range: `ButtonGroup` with toggleable items and overflow indicator.
   - More Money/Shift/App: full-width `ButtonGroup`.
   - Primary add action: `FloatingActionButtonMenu` with tonal spending/income/shift items.
   - Sync state: morphing `LoadingIndicator`.
2. **Spring physics**
   - `MaterialTheme(..., motionScheme = MotionScheme.expressive())`.
   - Screen transitions read `defaultSpatialSpec` / `defaultEffectsSpec` from the active scheme.
   - Expressive ButtonGroup/FABMenu components own their width/shape springs.
3. **Shape tension**
   - Live Shift status badge uses `MaterialShapes.Cookie9Sided.toShape()` with secondary/tertiary
     container roles.
   - Rounded 16/24/28 containers remain for hierarchy; the cookie badge is the focal exception.
4. **Editorial typography**
   - Display figures now install at bold weight; money remains tabular sans.
   - Monthly Statement, Today net and Live Shift net are the three editorial hero moments.
5. **Multi-role colour**
   - Statement cover primaryContainer; week secondaryContainer; reserves tertiaryContainer.
   - FABMenu items use primary/secondary/tertiary container roles.
   - Live badge uses secondaryContainer when active and tertiaryContainer when paused.

### Verification

- Compile: Android + desktop.
- 225 tests, 0 failures.
- Signed and installed 0.41.0, code 128.

## Unified M3 Expressive architecture (0.42.0)

### P0 — token purge
- Deleted `BakiPalette`, `LocalBaki`, all legacy palette packs and the theme-pack config.
- Deleted `BakiType`, all font assets and the custom font installer. Every screen now uses
  `MaterialTheme.typography` roles, including tabular numeric styles.
- Deleted `BakiMotion`; all custom animation now reads `MaterialTheme.motionScheme`.
- Deleted `Radius`; all shapes use `MaterialTheme.shapes`.
- Deleted `baki.paper` branches and `LedgerRule`; no constant-false UI branches remain.
- Deleted the separate desktop shell, Neumorphism, glass, paper texture and DeviceTier code.
  Desktop now renders the same adaptive Compose UI as phones/tablets.
- Removed the backdrop/glass dependency and all custom font resources.

### P1 — system chrome
- Phone: `ShortNavigationBar` with five Expressive items.
- Tablet/desktop: stateful `WideNavigationRail` with collapse/expand; the content offset tracks
  the measured rail width and the 960dp reading width is centred.
- A FAB in the rail header provides the primary add action on wide screens.
- `ModalBottomSheet` remains the only sheet implementation.

### P2 — expressive controls
- Transaction type is a connected `ButtonGroup` (Spending / Income).
- Appearance is a `ButtonGroup` (Auto / Light / Dark).
- Chart range and More tabs remain Expressive ButtonGroups.
- Add action is a `FloatingActionButtonMenu` driven by a morphing
  `ToggleFloatingActionButton`.

### P3 — state and motion
- Sync and server discovery use the morphing `LoadingIndicator`.
- All custom animation uses MotionScheme specs.
- MaterialShapes Cookie9 focal badges appear on Live Shift and the Statement streak.
- EasedMoney uses the theme’s spatial spring.
- Hero figures are bold and tabular.

### P4 — screen polish
- Live Shift is a 24dp `surfaceContainerHigh` card with expressive tonal actions.
- Summary chart cards use 24dp containers, tonal tracks and primary/error bars.
- FABMenu hides during a live shift so Pause/End remain reachable.
- Charts do not override `surfaceContainerHighest`, keeping tracks visible.
- Spend, Shifts, Today, Summary and More were rendered in light/dark, phone/desktop.
- The desktop preview is now the unified app rather than a separate shell.

### Verification
- Android + desktop compile clean.
- 194 tests, 0 failures.
- No `BakiPalette`, `BakiType`, `BakiMotion`, `Radius`, `LocalBaki`, `LedgerRule` or
  constant-paper branches remain.
- Signed 0.42.0, code 129.

### Deliberate omissions
- `FlexibleTopAppBar` is not present in the pinned Material3 artifact; a standard TopAppBar remains.
- `FloatingToolbar` is not forced because no multi-select/contextual-action flow exists.
- `SplitButton` is not duplicated because FABMenu already owns the quick-add action.

## 0.42.1 recovery

- The unified release exposed an existing local-encryption edge case during first launch:
  `loadLive()` did not handle an invalid Keystore/GCM live snapshot. The app now quarantines that
  snapshot, records the error in StoreHealth and continues; `loadLocal()` already had this recovery.
- Both home-screen widgets now catch local decrypt/render failures and show a safe fallback instead
  of crashing the app process.
- Verified on-device: the signed app launches to Today instead of the crash dialog.
- Desktop/phone unified UI, 194 tests, Material3 1.12.0-alpha03 pinned.

## 0.43.0 final gates

- Gate 3: verified spring-driven shape morphs for categories, live/paused status, reserve badges and receipt press; upgraded shared and Day Page headers to scroll-aware MediumTopAppBars.
- Gate 4: added structured chart/map semantics, tab role/selected state, explicit navigation labels, system-respecting reduced-motion toggle, 360dp/1.3x auto-fitting hero text, and full semantic contrast coverage.
- Final checks: 194 tests, Android debug, desktop tests and R8 release compile all pass.
- Known verification limits: varied wallpaper-seed contrast and true 120fps were not reproducible in this device session; see Gate 4 report.

## 0.43.1 UX refinement

- Collapsed the main header to a single-line TopAppBar; removed the tagline and scroll-height split.
- Painted the transparent status-bar inset with the same surface as the app bar.
- Removed persistent MonthInk bar tracks; the chart surface stays clean and only the selected day gets a soft primary-container backdrop.
- Kept the official M3 Expressive ShortNavigationBar instead of a custom floating pill nav.

## 0.43.2 spacing pass

- Single-line compact header with a matching status-bar surface.
- FAB gap over the short navigation bar reduced from 16dp to 8dp.
- Summary card padding normalized from 24dp to the shared 20dp step.
- MonthInk tracks removed; selected bars now receive a soft primary-container backdrop.
- Bottom navigation remains the official M3E ShortNavigationBar.

## 0.43.3 live trace

- Restored the earlier live-shift route trace: moving dashed line and travelling head dot, frozen when paused or when reduced motion is enabled.
- Hid the target-streak badge for a one-day streak; it now appears only from two consecutive days.
- Centered the Today “day page” action inside its 48dp target so it aligns with the entry-count label.

## 0.43.4 dynamic colour

- The Monet scheme was cached by light/dark alone, so wallpaper palette changes while the app remained alive were ignored. Added a WallpaperManager colour listener and wallpaper-change receiver to recompute the Material scheme in place.

## 0.43.5 contrast and validation

- Tinted cards now use their content colour for streak/positive/reserve text instead of unsafe outer-scheme accents.
- Entry and shift dates must parse as real ISO dates; shift starts must be valid 24-hour HH:MM.
- Save is gated on validation, with inline errors, and the repository rejects invalid dates defensively.
- 196 tests, including validation coverage.

## 0.43.6 portability and empty states

- JSON backup now carries the ledger, device config, calibration and local routes in a versioned bundle; legacy store-only backups still import.
- Restores force writes off and merge non-destructively.
- MonthInk draws low positive values as dots instead of dash-like pills, and shows “No activity in this range” when empty.
- Empty offline installs get a first-run server/write setup card.
- Added card-scheme contrast regression coverage, backup round-trip coverage, and render-size/blank-image guards.
- 198 tests, 0 failures.

## 0.43.7 security and map discoverability

- Optional server token: run the tracker with `BAKI_TOKEN=...`; enter the same token in More → App. Without a token, the original open-LAN mode still works.
- Token is sent on data, route and SSE requests.
- Server logs a warning when the ledger API is open.
- Backups include the token (private data warning in the UI).
- Today shows a “No movement recorded” card when a day has shifts but no route, instead of silently omitting the map.

## 0.43.8 golden suite

- Added deterministic, date-pinned golden screenshots for Today and Stats at 360dp light and 390dp×1.3 dark.
- Added pixel-tolerance comparison, with a documented update switch (`-Dbaki.golden.update=true`).
- Goldens immediately exposed and fixed a wrapped “+ Payout” label at 360dp, a stale live-shift fixture, and a positive worst-day shown in error red.
- 199 tests, 0 failures.

## 0.43.9 geometry and motion tests

- Added Compose UI contract tests using `runSkikoComposeUiTest`:
  - FAB sits 0–12dp above the measured ShortNavigationBar.
  - Connected button segments meet 48dp dimensions.
  - Wide rail starts expanded and compact nav is absent.
- Added a clock-controlled connected-group shape-morph test.
- The geometry test exposed a real 40dp FAB gap caused by a hard-coded 80dp nav height; the FAB now anchors to the measured bar height.
- 203 tests, 0 failures.

## 0.43.10 Day Page removal

- Removed the separate Day Page and its Today link.
- Removed the receipt picker/preview UI and platform receipt helpers.
- Day notes, closes and receipt fields remain in the schema so existing servers/backups stay compatible; they are no longer exposed in the UI.
- Today keeps the compact entry-count header only.

## 0.43.11 expressive art direction

- Added Baki’s soft-burst shape motif across the logo, hero backdrop, target sticker, Stats marker and live card.
- Today is now a colour-blocked take stage with a huge net, target-beaten sticker, integrated in/out stats and playful copy.
- Food is a wavy “FOOD FUND” meter with snack-left copy.
- Live shift is a bolder on-the-clock card with a larger trace, status pill and a running-meter line.
- Stats opens with a rotating burst, editorial statement line and best/worst copy.
- Spend/Shifts use the same shape marker and human labels.
- Goldens now freeze motion and cover a seeded live shift.
- 203 tests, 0 failures.

## 0.43.12 final expressive copy

- Shift card now reads “WHAT THE SHIFT PAID”, target delta is human copy, and the live action is “Start the meter”.
- Fixed singular/plural copy in shifts, payouts, purchases and week totals.
- Regenerated the full golden set, including the seeded live-shift state.
- 203 tests, 0 failures.

## 0.43.13 sheets and stats

- Entry sheet: soft-burst header, huge amount stage, icon category chips and “Log it” action.
- Shift sheet: soft-burst header and a colour-blocked “ESTIMATED TAKE” preview card.
- Stats: star markers on week/spending/funds/cash sections; wavy category and reserve meters.
- Empty Stats copy is now “A quiet month.”
- 203 tests, 0 failures.

## 0.43.14 global expressive pass

- Entry sheet amount stage now shows RM and a 0.00 placeholder before typing.
- Form section labels use the burst marker.
- Empty states have a burst mark.
- Bottom sheets use a larger 36dp top shape and tinted drag handle.
- 203 tests, 0 failures.

## 0.43.15 section sweep

- Every More/settings section heading now carries the burst marker.
- Calendar and Best Hours get the motif too.
- Empty states and form section labels carry the mark.
- The major sheets now use the expressive 36dp top shape and tinted handle.
- Primary screens, sheets, charts, empty states and section headings share one visual language.
- 203 tests, 0 failures.

## 0.43.16 ledger and payouts

- Ledger row icons now use the burst motif and carry the entry tint; shift rows get a shaped marker.
- Today’s selected day uses a tonal pill with a burst dot.
- Payouts sheet has a shaped header, coin rows and a giant amount field; quick payout dialog matches.
- First-run and map-empty cards now carry the motif and playful copy.
- Tab change gets a slight scale in/out.
- 203 tests, 0 failures.

## 0.43.17 navigation

- Replaced the stock compact bar with a floating Baki dock: shaped active burst, bold labels, spring scale.
- Replaced the stock wide rail with a floating shaped panel, same active burst, animated width and labelled collapse toggle.
- Preserved tab semantics, 48dp targets and the FAB-to-nav geometry contract.
- 203 tests, 0 failures.

## 0.43.18 navigation finish

- Removed the duplicate Baki wordmark from the wide rail; the logo + collapse control remain.
- Compact floating dock and wide rail share the same burst selection language.
- Navigation geometry contracts remain green.
- 203 tests, 0 failures.

## 0.43.19 last dialog shells

- Odometer prompt is now a Baki sheet: burst header, giant dashboard-km field, “Save reading”.
- Reserves sheet is now a Baki sheet: burst header, large move-money field, wavy reserve bars, shaped movement rows.
- Remaining conventional shells are settings dialogs; non-settings flows now share the language.
- 203 tests, 0 failures.

## 0.43.20 header and date chrome

- Header is now a custom row: burst logo, bold Baki, coloured status pill, burst discreet control.
- Day navigator is a shaped stage with burst arrows, TODAY marker, large date and playful subtitle.
- Spend month navigator and Stats statement month navigation use the same shaped arrows.
- Quick-hit icons sit in burst bubbles; ledger count has a burst marker.
- All 203 tests and goldens green.

## 0.43.21 max layer

- Target-beaten badge fires a one-shot mini-burst celebration (suppressed by reduced motion).
- FAB menu action icons sit in burst bubbles.
- Calendar heat cells are circles; today is a soft burst.
- Final 203 tests, 0 failures.

## 0.43.22 complete chrome

- Custom quick-action popover with shaped items and icons.
- Settings dialogs now use the burst header language.
- Switch rows carry the motif; hours heatmap cells are shaped.
- 203 tests, 0 failures.

## 0.43.23 hero typeface

- Bundled Barlow Condensed Bold (SIL OFL) for the Today amount only.
- Condensed skeleton, tabular figures, tight tracking; RM and body remain plain Roboto.
- Font is a Compose resource; no runtime download.
- 203 tests, 0 failures.

## 0.43.24 budget save race

- Fixed monthly-limits save being cancelled when the sheet dismissed.
- BudgetSheet and EnvelopeMoveDialog now dismiss only after their writes complete.
- Clarified that the Daily food fund (Today) is separate from the monthly Food envelope.
- 203 tests, 0 failures.

## 0.43.25 app-scope limits

- Limit writes now run in the app scope, not the sheet scope, so dismissing the sheet cannot cancel them.
- 203 tests, 0 failures.

## 0.43.26 route sync

- Sync now pulls sealed routes from the server as well as uploading them.
- Ending a live shift triggers an immediate route upload after finalization.
- Stopping the tracker flushes buffered points before dropping the provider.
- 203 tests, 0 failures.

## 0.43.27 cleanup

- Removed dead Chip, DayHeader, PillButton and tactileClickable code.
- Removed the unused Source Serif license resource.
- 203 tests, 0 failures.

## 0.43.28 sync loop

- Server merges no longer mark the durable outbox dirty.
- Post-push saves no longer trigger another upload.
- Removed dead UI/helpers and the unused Source Serif license.
- 203 tests, 0 failures.

## 0.43.29 fixed themes

- Added Fig Milk (light) and Midnight Fig (dark) fixed schemes.
- Added Colour source Wallpaper / Baki; Wallpaper remains default where dynamic colour is available.
- Android below 12 and desktop automatically use the fixed Baki palette.
- Added fixed-palette contrast test; 204 tests, 0 failures.

## 0.43.30 outlined icons

- Replaced filled Material Round glyphs with Material Icons Outlined.
- Kept Baki's custom toll barrier and all category mappings.
- Removed unused Settings/Mic/ArrowRight/Trend vectors.
- 204 tests, 0 failures.

## 0.43.31 burst launcher icon

- Replaced the old sage check launcher with a fig/gold ten-lobe burst.
- Added a cut-out centre and check, matching the in-app soft-burst motif.
- Updated adaptive foreground, monochrome layer, legacy PNGs and desktop icon.
- 204 tests, 0 failures.

## 0.43.32 calmer hierarchy

- Removed the normal-state burst backdrop from the Today hero.
- Food Fund is now a neutral surface with a gold wavy data line.
- Date arrows are round, not soft-bursts; the date stage is shallower.
- Week strip highlights the day being viewed with a thin outline, not a tinted burst cell.
- 204 tests, 0 failures; capped build peak 3.8 GB.

## 0.43.33 neutral summary

- Monthly statement header is a neutral surface; the chart is the only coloured focal point.
- Summary month arrows are quiet tonal circles.
- ExpressiveBurst is now static everywhere; motion is reserved for state changes/celebrations.
- 204 tests, 0 failures; capped peak 4.1 GB.

## 0.43.34 accessibility/motion

- Removed the last decorative burst backdrops from live/estimated-take cards.
- ExpressiveBurst was fully removed; no decorative continuous rotation remains.
- Day strip now exposes one merged spoken summary per day.
- Added a 200% font-scale Today golden; date/food/IN-OUT layouts adapt.
- 205 tests, 0 failures; capped peak 4.2 GB.

## 0.43.35 calendar/heatmap a11y

- Calendar days and heatmap blocks announce their own date/hours and net/rate.
- Added a 200% Summary golden and responsive month/chart header layouts.
- Golden fixtures now clear the main and backup stores, preventing real data bleed.
- 205 tests, 0 failures; capped peak 3.8 GB.

## 0.43.36 hero emphasis

- Today hero now devotes almost the whole tile to the take-home amount.
- RM moved above the figure; the amount uses auto-sized Barlow Condensed up to 132sp.
- IN/OUT/operating moved to a quiet facts card below the hero.
- 205 tests, 0 failures; capped peak 4.2 GB.

## 0.43.37 split ink

- Today hero now uses Archivo Expanded 800, auto-sized to fill the tile.
- Hero card contains only the amount; RM/label/badge and facts live outside it.
- Horizontal split ink: white numeral on fig above, fig numeral on lavender below, gold seam.
- Removed the old Barlow font and license; added Archivo Expanded 800 + OFL.
- 205 tests, 0 failures.

## 0.43.38 compact Today

- Date stage compressed to a single short row.
- Hero number now sits in a 60% left tile; field split remains horizontal.
- Food Fund is a ring in the right rail with remaining ringgit.
- Right rail ends in selected + previous day cards; the 7-day strip is gone.
- Removed IN/OUT facts and the DAMAGE REPORT title; operating is one caption.
- 200% font scale stacks the mosaic cleanly.
- 205 tests, 0 failures.

## 0.43.39 aligned Today grid

- Hero, Food and day cards now share a 206dp grid with 8dp gutters and 28dp corners.
- Food is a real card again with ring + remaining + cover footer.
- Day card is one tappable card containing previous + selected days.
- Removed BEATEN, RM, IN/OUT and the separate cover row.
- Hero numerals get a 1.48 vertical stretch.
- Shift card defaults to a compact summary and expands for details.
- 205 tests, 0 failures.

## 0.43.40 8dp grid spacing

- Day card cells now use the same 8dp inner gutter as the grid.
- All Today cards share 8dp outer gutters and 28dp corners.
- 205 tests, 0 failures.

## 0.43.41 tall hero/start action

- Hero amount vertical stretch increased to 2.05× (1.8× at 200% font).
- Start live shift is always visible inside the collapsed shift card.
- Explicit no-shift start card added.
- 205 tests, 0 failures.

## 0.43.42 auto-fill hero

- Hero numeral now measures its line height and scales vertically to fill the card.
- No fixed stretch factors.
- Start live shift is always visible; explicit no-shift start card.
- 205 tests, 0 failures.

## 0.43.43 P0/P1 fixes

- Start live shift is a standalone card, independent of shift history.
- Quick Add More now opens the entry sheet instead of hiding the nav.
- Latest two-day strip is fixed and locally selectable; it no longer walks backwards.
- Removed the chart zero label and clipped negative bars to the plot area.
- Day header matches the Spending month-header pattern.
- Cover status is now a check/x mark only.
- FAB and quick-action icons are bare.
- Shift band uses a Cookie9Sided status shape; route key save shows confirmation.
- 205 tests, 0 failures.

## 0.43.44 second batch

- Payout rows no longer show the SwipeToDismissBox rim (the "legacy ledger" edges).
- Standalone Start card remains above shift history.
- Save feedback added for Theme/Colour/Reduced motion/Budgets/Food cap, plus payouts.
- Food ring changed to a segmented gauge.
- Day cards default to today and only switch local selection.
- 205 tests, 0 failures.

## 0.43.45 wavy food ring/shape roles

- Food ring is now a smooth track with a wavy gold progress and head dot.
- Payout rows have the SwipeToDismissBox rim removed.
- Global save feedback overlay for theme/color/motion/budgets/food cap/payouts.
- Section/day/heatmap markers use circles; Cookie9Sided is the status shape; SoftBurst remains brand/selection.
- 205 tests, 0 failures.

## 0.43.46 shape/motion/contrast

- Corner scale extraLarge moved 28 -> 32dp.
- SoftBurst reduced to brand/header and selected navigation only.
- Hero card gets one asymmetric 8dp corner for tension.
- Food progress spring now respects reduced motion.
- Bottom sheets use 28dp top radius and 640dp max width.
- Added Baki high-contrast light/dark palettes with an Appearance switch.
- 205 tests, 0 failures.

## 0.43.47 FAB/menu and More

- FAB is 96dp, menu actions 56dp with larger type/icons.
- Quick Hits is one horizontally scrollable row.
- More has a tonal mode hero (Money/Shift/App).
- Spending's normal hero moved from error red to secondary.
- 205 tests, 0 failures.

## 0.43.48 hero hierarchy

- Hero amount uses measured canvas text: integer dominant, decimal smaller, sign separate.
- Width is solved before drawing; vertical fill is clamped to the card.
- Split seam and Archivo Expanded unchanged.
- 205 tests, 0 failures.

## 0.43.49 seam-free hero

- Removed the split seam; hero is one solid primary field.
- Integer fills the card with fixed font size and transform fit; decimal stays small and gold.
- Longer values compress horizontally instead of shrinking the type role.
- 205 tests, 0 failures.

## 0.43.50 solid hero/minus bar

- Split seam removed; hero is a single primary field.
- Integer fills the card; decimal is small gold.
- Negative values use a drawn minus bar, not a tiny glyph.
- 205 tests, 0 failures.

## 0.43.51 Roast & Pine

- Replaced Fig Milk/Midnight Fig with Oat & Pine and Roast & Pine.
- Light hero: deep pine card, matcha numerals, amber decimal.
- Dark hero: soft mint card, dark roast numerals, deep gold decimal.
- Terracotta secondary, amber tertiary; signature is default, System/dynamic optional.
- 205 tests, 0 failures.

## 0.43.52 two-day strip

- Selecting the previous day in the Today strip now drives the whole page: date, hero, operating, food, shift and entries.
- Past-day view becomes read-only; quick add and FAB are hidden.
- 205 tests, 0 failures.

## 0.43.55 Desk shell

- Desktop now renders a separate read-only Desk surface instead of the phone UI.
- Overview, Ledger table, Shifts master-detail, Routes, Compare, Year, Settings.
- Statement preview/tax/print, CSV/JSON/GPX export, route pull and sync.
- No phone add/edit controls, FAB, bottom navigation or financial forms.
- 205 tests, 0 failures.

## 0.43.57 Phone Preview/Motion Lab

- Desktop can launch the real phone shell in an isolated preview home with `BAKI_SHELL=phone`.
- Side panel: appearance, palette, high contrast, expressive/standard/slow motion, reduced motion.
- No phone, server, or real ledger writes required.
- 205 tests, 0 failures.
