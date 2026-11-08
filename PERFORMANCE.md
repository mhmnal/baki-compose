# Performance audit

> **Baseline note (2026-11-08):** measured 2026-11-06 around v0.18 on the current hardware. The
> algorithms and test harness have not changed materially; rerun the benchmark before quoting numbers
> for a newer build.

Measured on this machine and on the Nothing Phone, 2026-11-06.
Benchmark harness: `composeApp/src/desktopTest/.../Benchmark.kt` — a synthetic year
(2,400 entries, 520 shifts) so the numbers are about the algorithms, not the current 6 shifts.

Run it with:

```
./gradlew :composeApp:desktopTest --tests "app.baki.Benchmark" --rerun-tasks
```

## Stage 1 — Algorithms and primary bottlenecks

**`hourHeatmap` was O(n²).** For every shift it called `overheadPerHour(store, month)`, which scans
every shift. 520 shifts meant 270,400 iterations per redraw.

| | before | after |
|---|---|---|
| `hourHeatmap` | **12.71 ms** | **1.33 – 3.04 ms** (median ≈ 2.5) |

The rewrite does one pass to total hours per month, then a map lookup per shift — O(n). Date parsing
and `"HH:MM"` splitting are memoised per distinct date. Even the worst observed run is ~4× faster;
complexity is linear now.

**Recomposition was the bigger real-world cost.** Every sync tick (≈20 s) flipped `repo.status`,
and because the status was read in `AppContent`, the whole tree recomposed and re-ran every
aggregation. Fixed by:

- `Header` reads `repo.status` itself — a sync tick now re-draws one row
- `dayTotals` / `entriesOn` / `shiftsOn` / `monthTotals` / `fundStats` / `expensesByCategory` /
  `monthCost(Series)` / `budgetUsage` / heatmap blocks memoised with `remember(store, …)`
- `LazyColumn` items keyed by id in Today, Spending and Shifts

## Stage 2 — Data pipelines and memory

**The 20-second loop uploaded the entire store every time.** Each `sync()` pushed the full JSON
(≈12 KB today, ≈200 KB at a year of data) whether or not anything had changed — every 20 seconds,
on a mobile connection.

- Pushes are now **event-driven**: a mutation pushes; success clears a `pendingPush` flag; the
  polling loop only pushes when a previous push failed.
- `sync()` skips the disk write entirely when the merge changed nothing (encode + AES-GCM + write
  is ~5 ms and a flash write).
- The GPS tracker coalesced `live.json` writes from *every fix* to **25 m or 3 s**, whichever comes
  first. Each write was a JSON encode + encrypt + file write.

## Stage 3 — Network, caching, delivery

Server (`grab-tracker/server.mjs`):

- **ETag + 304** on `GET /api/data`. An unchanged store now costs a header round-trip:
  `304 Not Modified`, **0 bytes**.
- **gzip** when the client accepts it: **12,034 → 3,233 bytes** (−73%).

Client:

- `TrackerClient` keeps the last ETag and sends `If-None-Match`; `pull()` returns `null` on 304 and
  `Repo.sync()` returns early without merging or writing.

Delivery:

- **R8 + resource shrinking** enabled for release.
  **10,377,655 B → 2,701,566 B (−74%)**.
- Verified on a real device under a separate package id (`app.baki.compose.verify`): cold launch ok,
  synced from the server, rendered live data, no `AndroidRuntime` errors. The test package was
  uninstalled afterwards; nothing was distributed.

## Stage 4 — Rendering and responsiveness

- Aggregations memoised (above), list keys, status reads scoped.
- Release build cold start: **1,237 – 1,260 ms (debug) → 352 – 437 ms (R8)**.

## Stage 5 — Final numbers

| metric | before | after |
|---|---|---|
| `hourHeatmap` (520 shifts) | 12.71 ms | 1.33 – 3.04 ms |
| pull payload (gzip) | 12,034 B | 3,233 B |
| pull when unchanged | full body | 304, 0 B |
| uploads per hour idle | ~180 full-store pushes | 0 |
| APK size | 10.38 MB | 2.70 MB |
| cold start | ~1.25 s | ~0.39 s |
| tests | 52 | **53, all green** |

Non-regressions checked: 53 tests, the writing paths (add/edit/delete/shift/payout), cover logic,
merge/tombstones, and the app running against the real server.

## Known remaining opportunities

1. **Baseline profile** — would cut the remaining ~350 ms startup further. Needs a device-trace run;
   worth doing before any public release.
2. **Incremental sync protocol** — POST currently sends the whole store. A `{since}` delta would
   matter only well beyond a year of data.
3. **Haze/glass cost** — the blur re-renders on scroll. Tiering already disables it on weak GPUs
   (and on this phone it is off); a "pause while scrolling" would help mid-range devices.
4. **Storage** — JSON + full rewrite is fine at this size. If entries reach tens of thousands,
   SQLite with indices replaces `saveLocal`.

## Deployment note

The server changes (ETag, gzip) only take effect after the service is restarted:

```
systemctl --user restart baki-sync
```
