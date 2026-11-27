# Baki 0.70 — four-material audit

## Batch 1 — Today + Spend

| Surface | Material | Result |
|---|---|---|
| Today net | Cover | square deep-pine field, no discs/border/shadow |
| Date header | Open row | no card wrapper; mono `/ DAY IN PLAY` |
| Food fund | Instrument | 16 dp flat; secondary label now sans |
| Live shift | Instrument + Action | readout panel; Start button is standalone outside it |
| Shift pay / Where you went | Instrument | 16 dp flat; secondary labels sans |
| Today ledger | Paper | record only, no visible actions |
| Spend month header | Open row | no card wrapper |
| Spend hero | Page hero | `/ MONEY OUT` only |
| Spend day groups | Paper | record only; swipe actions remain gestures |

Fixes: removed secondary `/` labels, removed action wrappers around Start, removed transparent header wrappers.

Known limitation: the fixed quick-add FAB can overlap lower content at XL type until scroll.

## Batch 2 — Shifts + Stats

| Surface | Material | Result |
|---|---|---|
| Monthly Statement | Paper | month navigation moved above the page; no controls inside |
| Week Close | Paper + Action | record only; Review/Weeks now below the receipt |
| Month in Ink | Instrument + Action | 7D/30D moved outside the panel; chart is read-only |
| Shifts week | Paper | no “Add payout” inside; row opens edit/payout |
| Add shift | Action | standalone above the records |

Fixes: Stats subtitle corrected from “spending, monthly” to “the month at a glance”; monthly header no longer wraps; Week Close labels are sentence case.

Week Close UI/flow tests passed.

## Batch 3 — More + modal/statement

| Surface | Material | Result |
|---|---|---|
| Money Desk | Open navigation | square stamp + mono label; no mint card |
| Data Health | Instrument + Action | readout panel; Review button outside |
| Bills / Weekly Statement | Open sections | actions remain separate |
| Desktop Statement | Paper + Action | Print/PDF and Close moved to modal chrome; page pure |

Status: More preview passed. The desktop Statement modal render was not captured because of the Skiko/xdotool window targeting issue; the page compiles and no longer contains actions.

## Batch 4 — global surface and layout audit

- `Md3Card` default and route panel normalized to 16 dp instruments.
- Action controls remain M3E; forms/sheets soft.
- Labels: top-level only slash; secondary labels sans.
- 360, 390, 412 and XL type swept; dark and light checked.
- Desktop Compare/Year actual 1080 check passed; 1440 previews checked.
- Full suite: **253 tests, 0 failures**.
- Test service reaped all workers; no Java left.

## Release

```text
0.70.0 / code 274
SHA-256 e64df3a45d2ab3058ed27dea94769cafcf20abbe3028e9656dcb2230d6d1b209
Phone 0.70.0 installed
Desktop 0.70.0 installed
```

## Remaining caveats

- XL type: fixed FAB can cover lower content until scroll.
- Desktop Statement modal: visual capture blocked; source compiled and verified structurally.
- Motion is still not visible in stills.
- Settings/forms remain Action material by design.
