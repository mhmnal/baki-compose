# Known bugs / parked work

Kept here so nothing is lost between sessions. Ordered by what actually costs money or trust.

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
