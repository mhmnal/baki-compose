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
