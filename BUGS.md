# Known bugs / parked work

Kept here so nothing is lost between sessions. Ordered by what actually costs money or trust.

## How distance is decided (v0.6.4)

`odometer > GPS (speed-assisted) > hours x average speed`

- `travelMetres` believes Doppler speed only when the fix reports a good speed accuracy, otherwise it
  trusts position past a noise floor of 0.25 x accuracy. The anchor always advances.
- `Calibration` learns the drift factor: when a finished shift's km is corrected to the car's own
  figure, the raw-vs-real pair is stored locally; the median of the last 5 ratios (clamped 0.8-1.8,
  from 2 samples up) becomes `gpsCorrection` automatically. No odometer at both ends is required.

## Fixed

- **Tracked km under-counted in slow traffic** (v0.6.3). A fixed 3 m step gate ignored the fix's
  Doppler speed and left the anchor stale, so stop-go driving lost real distance (reported: 50 km
  tracked vs 75 km on the odometer). Travel is now speed-assisted with an accuracy-scaled noise floor
  (`travelMetres`, 7 tests).

- **In-progress route deleted on every app launch** (v0.6.2). `Repo.start()` swept orphaned paths
  using only the stored shifts, so opening the app during a shift purged the path being recorded and
  the day's map appeared to restart after a stop. The sweep now keeps the live shift's key.
- **Weekly review counted the wrong fuel category** (v0.6.1). `cat == "petrol"` vs `"fuel"`.

## Bugs

1. **Quick entry ignores fuel aliases when the category id isn't `petrol`.**
   `QuickEntry.ALIASES` registers `petrol/fuel/gas/minyak` only if a category with id `petrol` exists.
   Stores whose fuel category is `fuel` (like ours) lose `minyak`/`gas`; `petrol` only works because
   it is the category *name*. Fix: map the alias group onto whichever fuel category exists
   (`cats.firstOrNull { isFuelCat(it.id) }`).

2. **Fuel accrual uses a default assumption, not measurement.**
   `measuredEconomy()` needs two fuel entries carrying an odometer; with none it returns null and the
   app silently keeps the `kmPerLitre` setting. Our data implies ~8.2 km/L against a configured 11.
   Fix: ask for the odometer at fuel-up (or surface measured vs assumed and offer to adopt it).

3. **`Kept Today` widget has not had the resize pass** the live widget got (2x2 vs 4x2 both just
   stretch the same column).

4. **Desktop app image predates liquid glass.** `~/.local/opt/baki` needs a rebuild to match v0.6.x.

## Parked features (Batch B/C remainder)

- 2.4 undo history (needs an inverse-op log through every mutation)
- 2.3 inline editing of ledger amounts
- 2.7 search everywhere (entries + shifts + notes)
- 2.9 Quick Settings tile long-press
- 2.10 range selection on the week strip

## Blocked on toolchain

- CMP 1.13 stable for M3 Expressive components, shape morphing, adaptive layouts, desktop AOT.
