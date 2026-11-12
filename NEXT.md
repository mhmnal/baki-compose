# State & next

As of **0.37.2** (2026-11-08).

## Current direction

- **Phone**: Material 3 + liquid glass. The minimal "less, but better" pass was rejected and is archived
  at tag `v0.35.4`; do not resume it without an explicit request.
- **Desk**: neumorphic Soft UI 2.0 / Obsidian Slate, read-only. Frozen unless a bug appears.
- Statement import (share, bulk, Wednesday reminder) is live.

## Order of work

1. **Phone glass polish** — the user's active design thread.
2. **Statement usability** — list imported weeks (currently only the latest is shown), accept more share
   MIME types, show reminder permission state.
3. **Server hardening** — token auth for `/api/data` and an off-machine backup.
4. **Desk accessibility** — OS font scaling, reduced motion, focus order, hover-only scrollbar if wanted.
5. **Odometer prompts** — ask for a reading on every fuel fill; improves km/L, wear and the tax pack.
6. **Best-hours nudge** — use the existing heatmap data for a daily suggestion.
7. **Maintenance by distance** — only after the first service, starting from zero.

## Available from the archive

`v0.35.4` contains the M3/WCAG phone pass (48 dp targets, selected-tab semantics, custom
Edit/Delete actions, Mist contrast fix). Cherry-pick individual fixes without the rejected layout.

## Do not redo

- Minimalist phone reskin (rejected).
- Quick-entry sentence parser UI + voice (removed as unused; parser kept).
- Desktop visual churn: material, depth, charts and scrollbar decisions are settled.

## 0.37.2 gates

1. Cash truth — model + summary + tests done (Gate 1; transfers still open).
2. Service card + weekly review — done (Gate 2).
3. Discreet amounts (tap-to-toggle) + accessibility/type pass — done (Gate 3).
4. Desktop parity; final sign — next.

### 0.37.2 candidate

- Live yield + daily food cap (committed checkpoint ad58611)
- More Money/Shift/App restructure (this gate)
- Final review before signing; no sign yet.
