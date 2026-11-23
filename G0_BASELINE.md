# G0 baseline — storage migration

Captured **2026-11-23 20:15** before the operation outbox work.

## Ledger data

Source: `grab-tracker/data.json` (the phone is synced to it).

```text
entries            131
shifts              18
payouts            113
bills                9
bill payments        7
statements           3
week closes          3
accounts             2
imported txns        0
statement batches    0
odometer readings    0
```

```text
file bytes   57,901
sha256       a7fdafb90da0963c79a775f36d9b9f83bc9d3c75a48237d81dc7e79b27ac5dcd
```

## App baseline

```text
app version     0.51.1 (versionCode 241)
JVM tests       237, 0 failures
Android lane    passed
restore lane    passed against 2026-11-19 backup
desktop         running
phone           app.baki.compose 0.51.1
```

## Known unresolved data

```text
payout gaps           RM 463.29 across 4 shifts
bills missing due     1 (Spotify)
inactive bill links   3
backup                missing on this device
open week             current week
week-close variance   present
```

## Baseline storage behavior

- encrypted JSON snapshot rewritten on each local save
- `.bak` retained
- add/delete entries local-first
- `pendingPush` is one durable bit
- success of a full-store push clears the outbox bit
- no operation-level replay log yet

## G0 gate

- [x] full suite green at 0.51.1
- [x] Android lane green
- [x] phone data synced
- [x] restore lane verified
- [ ] fresh portable backup from current data (still missing)
- [x] counts/checksum recorded
