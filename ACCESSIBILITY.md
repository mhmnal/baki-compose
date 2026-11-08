# Accessibility

Updated 2026-11-08 for **0.36.1**. Two surfaces with different constraints: the desk is a custom
neumorphic canvas; the phone is Material 3 + liquid glass. Accessibility is enforced where tests can
prove it and documented honestly where it cannot.

## Enforced by tests

| Check | Test | Threshold |
|---|---|---|
| Body text on every palette | `ContrastTest.bodyTextIsReadable` | WCAG AA 4.5:1 (onSurface, muted, up, down, warn) |
| Text on the neumorphic canvas | `ContrastTest.canvasTextMeetsTheReadableFloors` | 4.5:1 on the computed canvas tone |
| Non-text UI boundaries | `ContrastTest.nonTextUiMeetsTheThreeToOneFloor` | 3:1 |
| Filled controls / accent text | `ContrastTest.filledControlsStayReadable` | 4.5:1 |
| No Material chrome on the desk | `DesktopConsistencyTest.noMaterialChromeInTheDesktopShell` | source assertion |
| Tab switches are instant | `DesktopConsistencyTest.tabSwitchesAreNotCrossfaded` | source assertion |
| Desk never writes | `ReadOnlyShellTest` | repository assertion |
| Surface depth is native | `ShadowEngineTest` | source assertion (no library, tokens, clipping) |

Run them with `./gradlew :composeApp:desktopTest`.

## Desktop

- Focus is drawn as an Electric Blue ring on custom buttons; tab navigation works without the mouse.
- Contrast is computed per palette and tested; `ContrastTest` is the gate for any palette change.
- Charts expose a spoken summary through `semantics` (week bars, trajectory).
- **Gap:** no OS font scaling, no reduced-motion handling, and canvas charts do not expose per-point
  values.
- Scrollbars were removed by request; the wheel/trackpad is the only scroll affordance.

## Phone

- Material 3 provides semantics, state layers and 48 dp minimum interaction sizes for stock components.
- The glass nav is 74 dp tall with 52 dp tab cells; the add action is 56 dp.
- Palette contrast is covered by the same `ContrastTest` packs the phone uses.
- **Gap:** TalkBack cannot edit/delete a ledger row without the swipe/long-press gestures, and custom
  canvases (week strip, sparkline, map) have no per-element descriptions.
- **Gap:** dynamic colour from the wallpaper is not contrast-clamped.
- **Gap:** large-font layouts have not been audited; fixed display sizes may clip.
- The M3/WCAG phone pass (48 dp audit, selected-tab semantics, custom Edit/Delete actions, Mist contrast
  fix) exists at tag `v0.35.4` but is **not** in the current interface; cherry-pick pieces as needed.

## Known exceptions

- Decorative separators may be below 3:1 because they carry no information.
- Muted text at small sizes on the desk is tested at 4.5:1; placeholders use the same tone.
