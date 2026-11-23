# G5a — SQLite importer and storage

Branch: `feature/g5-sqlite-storage` (based on G4).

Status: **importer + transactional SQLite storage passed. Production cutover not enabled.**

## What is implemented

- `store_snapshot` table in encrypted SQLite
- schema v2 → v3 migration
- `SqliteLedgerStorage` implementation of `LedgerStorage`
- SQLite passphrase:
  - 32 random bytes
  - sealed by the platform `StoreCipher`
  - stored in `sqlite.key`
- first launch import from the existing encrypted JSON snapshot
- exact round-trip verification after import
- one transactional SQLite row per snapshot write
- operation-log recording on SQLite saves
- JSON fallback if SQLite cannot open or verify
- JSON snapshot retained as rollback

## Verified

- JSON → SQLite import equals the original Store exactly
- new snapshot saves and reloads
- outbox operation appended on SQLite save
- v2 → v3 migration adds `store_snapshot`
- v1 → v2 migration still passes
- encrypted database round-trip, wrong-key rejection, 10k-row query
- Android encrypted SQLCipher build
- **257 JVM tests, 0 failures**
- Android debug build succeeds

## Important boundary

This is **G5a**, not full normalization.

The Store is still serialized as one JSON document inside SQLite:

```text
store_snapshot(id=1, json, updated)
```

That gives:

- encrypted file at rest
- atomic transactional writes
- a path away from whole-file JSON replacement
- an exact importer and rollback

It does **not** yet give:

- entity tables
- per-entity queries
- relational conflict handling
- normalized migration

Those are G5b.

## Cutover

Cutover is deliberately not enabled in production. `BakiRepo` still defaults to `JsonLedgerStorage` on master.

Reason: SQLite writes after cutover would not appear in the JSON rollback copy. Before enabling cutover we need:

1. SQLite export back to JSON
2. dual-read count/hash comparison for one release
3. rollback runbook
4. real phone migration test
5. decision on normalized tables vs snapshot-in-SQLite

No production data has been migrated.

## Commands

```bash
git switch feature/g5-sqlite-storage
```

Production remains:

```text
master 0.55.0
```
