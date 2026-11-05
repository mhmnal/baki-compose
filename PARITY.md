# PWA vs Compose — feature parity

PWA = `../grab-tracker/index.html` (the app in daily use). Compose = this project.
PDF statement import is excluded by request.

## Same behaviour (verified by tests)

| Area | Notes |
|---|---|
| Money maths | `shiftMetrics`, `shiftKm`, `costRates` — GoldenTest asserts equality on the real store |
| Insurance cover | 24h rolling rule, `fee-<id>` entries, idempotent on the real store — CoverTest |
| Day / month totals | `dayTotals`, `monthTotals`, funds — same formulas |
| Merge + tombstones | newest-wins, tombstone authority, per-week statements — MergeTombstoneTest |
| Live shift | start, payouts, end → shift with derived cover |
| Sync | same server, same JSON, SSE live updates |

## Missing in Compose

### Data entry
| Feature | PWA | Effort |
|---|---|---|
| ~~Income entries~~ | **done** — Spending/Income toggle, income categories | ✓ |
| ~~Edit entry~~ | **done** — long-press a row, change amount/category/note/date | ✓ |
| ~~Edit shift~~ | **done** — long-press, change hours/km/payout/tolls, per-shift km/L | ✓ |
| Quick presets | `preset`, `quickExp` — one-tap frequent expenses | S |
| Calculator | `openCalc`, `calcKey` | S |
| ~~Undo after delete~~ | **done** — snackbar with Undo | ✓ |

### Navigation and finding things
| Feature | PWA | Effort |
|---|---|---|
| ~~Day navigation~~ | **done** — ‹ Today ›, tomorrow disabled | ✓ |
| ~~Month navigation~~ | **done** — ‹ Month › on Spending and Summary | ✓ |
| ~~Search~~ | **done** — note / category / amount, live filtering | ✓ |
| ~~Category filter~~ | **done** — chips on Spending | ✓ |
| ~~7-day strip~~ | **done** — bars on Today, today highlighted | ✓ |

### Insight
| Feature | PWA | Effort |
|---|---|---|
| ~~Trend chart~~ | **done** — bars above/below zero per day | ✓ |
| Calendar heat map | both have it (Compose has it now) | — |
| Week review | both have it | — |
| Statement details | PWA shows every charge; Compose shows a summary card | S |

### Settings
| Feature | PWA | Effort |
|---|---|---|
| ~~Vehicle costs & target~~ | **done** — More → Vehicle & target, re-derives every shift | ✓ |
| ~~Per-shift km/L override~~ | **done** — "km/L (opt)" in the shift dialog | ✓ |
| ~~Odometer entry~~ | **done** — start/end fields; overrides Km when both present | ✓ |
| Measured km/L from fill-ups | `useMeasuredKml` | M |
| ~~Theme choice~~ | **done** — Auto / Light / Dark in More | ✓ |
| Dynamic colour toggle | both have it | — |
| Reminders | `setReminder` — shift-start nudge | M |
| Snapshots + restore | `snapshotNow`, `restoreSnap` — automatic local snapshots | M |
| ~~Import JSON~~ | **done** — file picker, merges, never destructive | ✓ |
| ~~Reset~~ | **done** — forgets the local copy and re-pulls (server untouched) | ✓ |
| Sample data | `loadSample`, `clearSample` | — |
| Storage protection | `protectStorage` (persistent storage) | S |
| Autostart (desktop) | `setAutostart` | S |

### Live shift
| Feature | PWA | Effort |
|---|---|---|
| End at a chosen time | `liveEndAt` / `liveEndAtSave` — backdate the end | S |
| Keep running while saving | `liveKeep` | S |
| Add payout from the shade | both notify; Compose's action opens the payout dialog instead of typing inline | — |

### Sync
| Feature | PWA | Effort |
|---|---|---|
| Device discovery / pairing | `discover`, `syncTest`, QR code | M |
| Sync status detail | both have a dot; PWA has a panel with last sync, peers | S |

## Different by design (not gaps)

- **Navigation**: Compose uses a 5-tab bottom bar; PWA uses a floating pill. Same destinations.
- **Entry point**: Compose FAB is hidden in read-only mode; PWA always shows it.
- **Row actions**: Compose uses long-press to delete (no edit yet); PWA has an overflow menu.
- **Read-only safety switch**: Compose only. PWA has no such guard.

## Suggested order (updated)

Done: income, edit, day/month nav, vehicle costs, per-shift km/L, search + filter, trend chart,
7-day strip, theme choice, import JSON, reset, undo, odometer, delete, backup/CSV.

Remaining:
1. **Measured km/L from fill-ups** (M) — `useMeasuredKml`
2. **Reminders** (M) — shift-start nudge
3. **Snapshots + restore** (M)
4. **Device discovery / QR pairing** (M)
5. Small: category icons/order, statement per-week detail, autostart (desktop), sample data
