# G5b — normalized core tables

Branch: `feature/g5-sqlite-storage`.

Status: **normalized importer/storage passed. Cutover still off.**

## Tables

- `entries`
- `shifts`
- `bills`
- `bill_payments`
- `week_closes`
- `reserve_txns`
- `odometer_readings`
- `store_settings`
- `store_aux` for statements/day notes/statement marks/envelope moves/accounts/imported rows/month closes
- `tombstones`

Each core table has indexed identity/date/amount columns and a JSON payload column. Payloads keep schema evolution safe while rows are independently stored and queried.

## Importer

- G5a `store_snapshot` is used as migration input when present.
- Otherwise the encrypted JSON snapshot is imported.
- Writes all core rows and aux rows in one transaction.
- Reads the Store back and requires exact equality before accepting the import.
- JSON remains the rollback snapshot.
- SQLite passphrase is 32 random bytes sealed by the existing platform cipher.

## Verified

- exact Store round-trip
- G5a snapshot → normalized tables
- entities, payouts, bills, payments, week close, reserve, odometer, tombstones
- row counts
- outbox operation on normalized save
- **258 JVM tests, 0 failures** target
- Android compile/build target

## Still missing before cutover

1. SQLite → JSON export
2. dual-read count/hash comparison
3. rollback runbook
4. real phone migration
5. production switch of `BakiRepo` storage
6. app-level storage-engine flag

Production remains on:

```text
JSON snapshot + operation outbox + incremental sync
```
