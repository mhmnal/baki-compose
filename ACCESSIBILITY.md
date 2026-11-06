# Accessibility

Neumorphism is low-contrast by nature, so the app treats accessibility as a build requirement rather
than a polish pass. Anything below is enforced by a test or is a documented gap.

## Enforced by tests

| Check | Test | Threshold |
|---|---|---|
| Body text on every surface | `ContrastTest.bodyTextIsReadable` | WCAG AA 4.5:1 (onSurface, muted, up, down, warn on surface and container) |
| Filled controls | `ContrastTest.filledControlsStayReadable` | 4.5:1 (onPrimary/primary, onPrimaryContainer, onAccent) |
| Surface hierarchy | `NeumorphismTest.panelsSitAboveTheCanvasPerceptually` | panel luminance above base so a panel is not invisible |
| Shadow separation | `NeumorphismTest.shadowsKeepEnoughSeparationToRead` | light/dark shadow spread > 0.05 luminance |
| Design tokens | `DesignTokensTest` | no raw font size or corner radius outside the design files |

All four palettes (sage light, sage dark, night, paper) are covered.

## Interaction

- **Focus is always visible.** Buttons, icon buttons, rail items and fields draw a 2 dp accent ring
  when focused. Neumorphism cannot show focus by shadow alone, so it is drawn explicitly.
- **Keyboard**: `Ctrl+←/→` month, `Ctrl+F` entries, `Ctrl+N` new entry, `Esc` dismiss;
  in a table `↑ ↓` select, `Enter` edit, `Delete` remove.
- **Targets**: rail items 48 dp, toolbar buttons 36 dp tall with 16 dp padding, chips 32 dp with
  12 dp padding — all at or above the 44 dp touch guidance after padding.
- **Hover** never moves text: rows keep their padding and change surface only.

## Screen readers

- Every icon-only control has a `contentDescription` (rail, gear, month chevrons, row actions).
- The two charts expose a summary through `Modifier.semantics`: the week reads as
  "Last seven days net: Mon 92.61, …" and the trend as "Month trend, peak RM 115.63".
- Tables are text-first, so a reader walks labels and figures in reading order.

## Known gaps

- No OS font-scale support: Compose Desktop has no system text-size setting; a `Config` scale is the
  honest way to add it later.
- No high-contrast theme beyond the Night pack; a true high-contrast mode would need a fourth palette
  with heavier text and near-opaque surfaces.
- Reduced-motion preference is not read yet; the transitions are short (90–140 ms) and only
  fades, but honouring `prefers-reduced-motion` equivalents is still to do.
- Charts are canvas-drawn; their descriptions are summaries, not data tables.
