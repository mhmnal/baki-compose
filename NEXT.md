# Baki (Compose) — state of play

Compose Multiplatform rewrite of the tracker. The web app (`../grab-tracker`) is still the
one in daily use and is **untouched**. Both clients share one server, so there is no data
migration: point the new app at the same server and it pulls everything.

## Layout

```
composeApp/src/
├── commonMain/            model, money maths, cover rule, merge, sync, UI, theme
│   ├── Model.kt           Store/Entry/Shift/Statement — field-for-field with /api/data
│   ├── Money.kt           costRates, shiftKm, shiftMetrics (net, RM/hr, band)
│   ├── Cover.kt           24h insurance rule + linked fee-<id> entries
│   ├── Totals.kt          dayTotals/monthTotals, rm() formatting
│   ├── Merge.kt           newest-wins, tombstones, per-week statements
│   ├── Sync.kt            TrackerClient: pull/push/events (SSE), 8s timeouts
│   ├── Repo.kt            state machine: sync loop, addExpense, addShift, live shift
│   ├── Theme.kt           sage palette (light+dark), LocalBaki, opt-in Monet
│   └── App.kt             Today / Spending / Shifts / More + dialogs
├── androidMain/           MainActivity, LiveShiftService, BakiWidget, Storage, effects
├── desktopMain/           window entry, file store, no-op effects
└── desktopTest/           Golden, Cover, Sync, Write, ShiftWrite, Live, LiveShift
```

## Tests (26, all green)

`./gradlew :composeApp:desktopTest`

- **GoldenTest** — maths must equal what index.html produces (fixture from the real store)
- **CoverTest** — 24h window, exact boundary, and *idempotency on the real store*
- **SyncTest / LiveTest** — pull 23 entries, SSE event arrives
- **WriteTest / ShiftWriteTest / LiveShiftTest** — writes reach the server, then clean up
- **MergeTombstoneTest** — a record deleted on the server can never be resurrected by a stale copy
- **DeleteTest** — shift + its fee entry tombstoned everywhere, entries too, and read-only refuses writes
- **ParityTest** — income vs expense, edits win the merge, cover re-derived, costs re-price every shift
- **CoverCrossCheckTest** — the 24h rule matched against the web app on 8 awkward cases
  (same-day, exactly 24h, 23h59, 24h01, out of order, overnight, two-day spread, gap)

Write tests run against a **throwaway server** (PORT=88xx, seeded with the fixture) and tombstone
their records afterwards. Start one with:

```bash
cd ../grab-tracker && PORT=8812 DATA_FILE=/tmp/t.json NO_MDNS=1 node server.mjs
```

## Safety

`ClientConfig.allowWrites` is **false by default**. While false the app never pushes: `sync()` only
pulls, every mutation returns early with a message, and the FAB / End shift / category × are
disabled. Turn it on in More → Safety → Allow writes. Keep it off until the app is ready to
replace the web one.

## Repo hygiene (please do this)

`git init` — there is no version control here. A `/tmp` scratch file once cost the top of App.kt
and it had to be reconstructed by hand.

## Built

- Read + write sync, live updates over SSE, offline-tolerant (8s timeouts, local-first writes)
- Today / Spending / Shifts / **Summary** (totals, best & worst day, funds, spend by category)
- Add expense, log shift (cover fee derived, never typed), live shift with payouts
- Live shift **foreground service + notification** (chronometer, Payout/End actions → deep links)
- Light/dark following the system; opt-in wallpaper colour (Android 12+)
- Haptics (VIBRATE permission), home-screen widget (registered, not yet placed on a launcher)
- More: server address, statement panel, category management, **backup JSON / CSV export** (share sheet)
- Summary: month totals, week-over-week, best & worst day, funds, spend by category, **calendar heat map**
- **Read-only by default** (Safety switch) so unfinished screens can't touch the ledger
- **Delete** shifts and entries by long-pressing a row (writes must be on); a shift takes its
  insurance entry with it and the cover rule re-runs for the rest
- Income entries, edit either kind, day/month navigation, search + category filter, undo snackbar
- 7-day strip, trend bars, calendar heat map, week review
- Vehicle costs & target editor, per-shift km/L, odometer start/end
- Theme Auto/Light/Dark, JSON import, reset device

## Not built

1. **Statement PDF import** — the Rust parser (pdftext.rs: ToUnicode CMaps, glyph layout) needs a
   Kotlin port on PdfBox. Biggest remaining item; do it with the real PDF as a fixture and assert
   the parsed numbers, because a silent misparse corrupts the ledger.
2. Category icons & ordering, statement panel per week, odometer entry on a shift, editing a
   logged shift (currently: delete and re-add)
3. Removing the AGP 9 escape hatch: split into `:shared` (KMP library) + `:androidApp`

## Gotchas learned the hard way

- AGP 9 refuses `com.android.application` + KMP → `android.builtInKotlin=false`, `android.newDsl=false`
- `kotlinx-datetime` resolves 0.7.x transitively: compile against 0.7.1 or `atStartOfDayIn` NoSuchMethodErrors
- SSE is inside `ktor-client-core`; `io.ktor:ktor-client-sse` does not exist
- `LocalBaki` must be read *inside* `BakiTheme` or every colour silently falls back to dark
- Haptics without `VIBRATE` throws SecurityException and kills the calling coroutine
- The repo mutex is held across network calls: without HTTP timeouts the UI blocks forever
- **Tombstones must be applied in the client merge.** Without it a record deleted on the server
  comes back from any stale client on its next push (server refuses it, client keeps showing it)
- Testing against the live server writes real records. Seed a throwaway one:
  `PORT=88xx DATA_FILE=/tmp/t.json`, and clean up with `deletedIds` in the push body
