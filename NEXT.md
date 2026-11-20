# State & next

As of **0.50.3** (2026-11-21).

## Current direction

- **Phone Today**: full-width Archivo variable hero, shape accent, full-width wavy food strip.
- **Charts**: 7D daily lollipop; 30D cumulative running net; desktop Month/All-time running net.
- **No-judgement pass**: best/worst, streaks, quality bands, best-hours callouts and best-day highlights removed. Targets and truthful loss colours remain.
- **Week Close**: Monday–Sunday close/reopen with week picker and manual received payout.
- **Workflow**: use `scripts/fastcheck.sh` during iteration and `scripts/gate.sh` before shipping. See [WORKFLOW.md](WORKFLOW.md).

## Next

1. Fix the remaining Android empty-state sentence under the bottom navigation.
2. Add Android instrumentation coverage for the Week Close modal sheet if the semantics tree allows it.
3. Re-import old weekly statement PDFs so insurance counts are populated.
4. Real-device long live-shift test: screen-off tracking, notification, distance, end from both surfaces.
5. Then choose vehicle economy/service or Android quick capture.
6. Backlog: holiday/no-work neutral state and streak-gap behavior; variable typography polish.

As of **0.37.3** (2026-11-08).

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

## 0.37.3 gates

1. Cash truth — model + summary + tests done (Gate 1; transfers still open).
2. Service card + weekly review — done (Gate 2).
3. Discreet amounts (tap-to-toggle) + accessibility/type pass — done (Gate 3).
4. Desktop parity; final sign — next.

### 0.37.3 candidate

- Live yield + daily food cap (committed checkpoint ad58611)
- More Money/Shift/App restructure (this gate)
- Final review before signing; no sign yet.

## 0.37.4 candidate

- Reliability contract in AGENTS.md.
- Hard manual-grabfee rejection and hidden category.
- Wall-clock cover end with break minutes + boundary tests.
- Live preflight script. Ready for review/sign; phone remains 0.37.3.

## 0.37.5 candidate

- Paper Ledger phone UI, both light and dark; liquid-glass nav/+ preserved.
- One-time migration from Sage default to Paper (a later Sage choice sticks).
- Animated list placement and sync stamp; 203 tests.
- Ready for sign/install after explicit instruction; phone remains 0.37.3.

## 0.37.6 candidate

- Paper Ledger refinement: Source Serif 4, M3 type mapping, static grain, adaptive hero/layouts.
- 205 tests; viewport sweep at 360/390/412 and 1.3x type.
- No sign yet; phone remains 0.37.3. Nav labels at 360/large type are intentionally untouched.

## Paper Sheet v2

- Continuous ledger rows and inset hairlines across Today, Spending, Shifts and simple More rows.
- Flat paper panels retained only where actions/state require them.
- 360/390/412 and 1.3x viewport sweep still green; 205 tests.
- Ready for sign/install; phone remains 0.37.3.

### Highest-value paper controls

- Ruled metrics/actions/filters, linear budget bars, ledger sparkline, ruled categories/diagnostics, flat snackbar and empty states.
- 205 tests; nav/+ untouched.
- Next review: live card, forms, charts, payout/statement sheets (no code yet).

### Paper Sheet ordered pass

- Live ticket, payout rows, flat sheets, entry/shift/budget forms, ledger charts, statement rows and dialogs.
- 205 tests; live start/end smoke-tested on device.
- No sign; phone remains 0.37.3.

### Completed-shift ticket

- Flattened the latest completed shift on Today into the ruled ledger ticket.
- 205 tests; no sign yet.

### Paper Sheet broad pass

- QR screen, flat Summary/More, responsive forms and final polish.
- 205 tests. QR activity is non-exported and was compile-verified, not shell-launched.
- No sign; phone remains 0.37.3.

### Accessibility gate

- 48dp controls, paper contrast regression test, tab/heading/chart semantics.
- 207 tests. Residual: no Android-tablet max-width container; loading/error banners unchanged.
- No sign yet.

### 0.37.4 signed

- versionCode 109, signed release installed over 0.37.3 with data retained.
- 207 tests. Signed smoke test passed (Today, Add, Shift form, Shifts, More).
- Residual: tablet max width, QR in-app verification, widgets, TalkBack traversal.

### Collapsing header

- Large header scrolls with every tab; compact glass header appears after 56dp.
- Tested on device: Today collapse/expand, More collapse, Spending collapse.
- 207 tests; no sign yet for this header build.

### 0.37.5 signed

- Collapsing header with compact glass state; 207 tests.
- Signed versionCode 110; installed over 0.37.4.
- Pending: frame-time profiling of the second glass surface; More tabs intentionally scroll with header.

### Header glass refraction

- Full-width glass header, stronger shared lens/blur, reduced tint.
- 207 tests; build to sign next.

### 0.37.6 signed

- Full-width glass header with stronger shared refraction; versionCode 111.
- 207 tests; signed collapse/expand verified.
- Pending: frame-time profile for the heavier blur/lens.

### Rolling funds

- Rolling fuel/wear provision across months; monthly contribution/withdrawal shown underneath.
- Latest server snapshot: 12 shifts, 82 entries, 22:09 update.
- 210 tests; release to sign next.

### 0.37.7 signed

- Rolling fuel/wear reserves; 210 tests. Signed versionCode 112.
- Not installed: wireless debugging port refused authentication; needs fresh pairing.

### 0.37.8

- Rolling fund rows split for narrow layouts; 210 tests.

### 0.37.8 signed

- Rolling fuel/wear reserves; 210 tests; installed on signed package.
- Latest: fuel −RM115.45, wear −RM345.76.

### 0.37.9 signed

- Full Summary statement redesign; 210 tests; installed.
- Assumptions: target line is target rate × monthly hours / active days; reserve target is one month of actual/accrued cost.

### Phase 1 complete

- Day page, running margins, day notes and receipt attachments.
- 216 tests; server restarted with receipt/day-note preservation.
- Next: Phase 2, daily close/seal and statement reconciliation.

### 0.38.0 Phase 1 signed

- Day page, running margin, day notes, receipt attachments and server preservation.
- 216 tests; installed.
- Phase 2 not started: daily close/seal + statement reconciliation.

### Phase 2

- Daily close/seal and statement reconciliation implemented; 220 tests.
- Read-only guards added.

### 0.38.2 Phase 2 signed

- Daily close/seal and statement reconciliation installed; 220 tests.
- Next: Phase 3 envelope budgets and reserve targets/top-ups.

### Phase 3

- Envelope carry/moves and reserve targets/top-ups implemented; 223 tests.
- Signed next.

### 0.38.4 Phase 3 signed

- Envelope carry/moves and reserve targets/top-ups installed; 224 tests.
- Next: Phase 4 export/print.
