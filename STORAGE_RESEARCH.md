# Storage durability research

As of **0.51.0** (2026-11-23). No migration has been implemented.

## Current architecture

- One encrypted JSON document (`Store`) is serialised with kotlinx.serialization.
- The whole document is encrypted (AES-GCM) and rewritten through `saveLocal`.
- A `.bak` copy is kept beside the main file.
- Sync pushes the whole store plus tombstones; the server merges by id and newest update.
- Entry add/delete are local-first; the durable `pendingPush` outbox bit uploads later.
- Portable backup is a JSON bundle, optionally passphrase-encrypted.
- 237 JVM tests, Android instrumentation lane and restore lane are green.

This is acceptable at 126 entries. The weaknesses are architectural, not current file size:

1. every local edit rewrites the whole document;
2. sync sends the whole store;
3. conflict handling is whole-record “newest wins”;
4. crash consistency depends on file replace + `.bak`, not transactions;
5. there is no per-operation replay log beyond the single `pendingPush` bit.

## Options considered

### SQLDelight

**Fit**

- Kotlin Multiplatform with Android and JVM/desktop drivers.
- SQL schema in `.sq` files and explicit migrations in `.sqm` files.
- Transactions, indices and foreign keys are first-class.
- Can generate typed queries and Flow wrappers.

**Encryption**

SQLDelight has no built-in encryption; it is “bring your own SQLCipher”. Android can use a SQLCipher `SupportFactory`; desktop needs a SQLCipher JDBC driver and native library packaging. That is the main integration risk.

References:

- [SQLDelight migrations documentation](https://github.com/sqldelight/sqldelight/blob/b4906d9afbb183fea3592c12e7459a7bf772b1e5/docs/common/migrations.md)
- [SQLDelight encryption issue](https://github.com/sqldelight/sqldelight/issues/1353)
- [SQLCipher KMP library listing](https://klibs.io/project/yet300/SQLCipher)
- [Sqkon comparison — SQLDelight raw, Room, encryption trade-offs](https://raw.githubusercontent.com/MercuryTechnologies/sqkon/refs/heads/main/docs/concepts/comparison.md)

**Verdict:** strongest fit for a Compose Multiplatform Android + desktop app, conditional on a SQLCipher desktop spike.

### Room KMP

**Fit**

- Mature relational model, migrations and DAO tooling.
- Room 3.0 is the current modernised line (March 2026).
- KMP support exists in recent Room lines, but Android remains the primary target.
- Desktop/JVM support and SQLCipher integration need a concrete spike, not an assumption.

**Encryption**

Room also requires SQLCipher or encrypted fields for real at-rest encryption. Android `SupportFactory` is established; a desktop path needs separate verification.

References:

- [Room 3.0 — Modernizing the Room](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html)

**Verdict:** viable if desktop/JVM + SQLCipher support is proven. Slightly less appealing than SQLDelight for this two-target layout.

### Realm / Atlas Device SDK

Built-in encryption is attractive, but the KMP target story, desktop support and the Atlas Device SDK lifecycle make it a poor bet for a long-lived personal ledger. Not recommended.

### Sqkon / key-value stores

A typed JSON-object store is close to the current model and has low migration ceremony, but it does not give relational integrity or a path to joins (shifts→payouts→entries→bills). Encryption is still bring-your-own. Not recommended.

## The bigger issue: sync, not SQLite

Moving from JSON to SQLite without changing sync would still send whole-store pushes and still rely on record-level newest-wins. The durability win would be local, but the replication risk would remain.

A durable operation outbox is the higher-priority change:

```text
local transaction:
  write entity
  append operation (op_id, entity, op_type, payload, created_at)
  commit

sync:
  send operations in order
  server deduplicates by op_id
  ack operations
  compact acknowledged operations
```

Operations needed:

- entry create/update/delete
- shift create/update/delete
- payout add/update/delete
- bill/payment link/unlink
- week close/reopen
- reserve movement
- settings update

The current full-store push can remain as a compaction/snapshot path while incremental operations are introduced.

## Migration sequencing

### Phase 0 — make the current file safer

- add an append-only operation log beside the JSON snapshot
- replay unacknowledged operations on launch
- atomic snapshot + log compaction
- add backup checksums and restore count verification
- add a migration-version field to the JSON document

This delivers most of the crash-safety benefit without changing storage.

### Phase 1 — storage interface

- introduce a repository/storage interface
- keep the JSON implementation behind it
- make all reads/writes go through the interface
- add golden store fixtures and round-trip tests

### Phase 2 — SQL bridge

- add SQLDelight schema and SQLCipher
- on first launch, import JSON into SQLite in one transaction
- verify counts and a content hash before deleting/retiring JSON
- keep JSON export as a portable backup format
- dual-read during one release; writes go to SQLite

### Phase 3 — cut over

- SQLite becomes authoritative
- JSON remains export/import only
- operation outbox is the sync source of truth
- full-store push becomes a periodic snapshot/compaction escape hatch

### Phase 4 — remove the old path

- remove document rewrite code after at least one stable release
- keep migration tests for old backup versions

## Suggested schema

- `entries`
- `shifts`
- `shift_payouts`
- `bills`
- `bill_payments`
- `week_closes`
- `statements`
- `statement_rows`
- `accounts`
- `reserve_txns`
- `odometer_readings`
- `categories`
- `settings` (key/value or one JSON document row)
- `tombstones`
- `outbox`

Routes can stay file-backed initially; their point stream is large and independently retained.

## Encryption and keys

- Android: Android Keystore + SQLCipher key derivation.
- Desktop: OS keyring or a local key file with explicit permissions.
- Portable backup: keep the existing passphrase-encrypted JSON; never make the database file the backup format.
- Key rotation must be tested against an existing database and an existing backup.

## Effort estimate

| Piece | Estimate |
|---|---|
| Operation outbox + replay | 2–4 days |
| SQLDelight schema + drivers + encryption spike | 3–5 days |
| JSON→SQLite importer + verification | 2–4 days |
| Incremental sync endpoints | 3–5 days |
| Dual-write/dual-read release | 2–3 days |
| Migration/backup/crash tests | 3–5 days |

This is a multi-week change, not a weekend refactor.

## Recommendation

**Do not migrate yet.**

1. Build the **operation outbox** first, on top of the current JSON snapshot.
2. Make backups **verifiable** and add a fresh-backup check to Data Health.
3. Run a **SQLDelight + SQLCipher desktop/Android spike** on a branch.
4. Only then migrate, with a dual-read release and a verified importer.

If the SQLCipher spike is clean, choose **SQLDelight**. If desktop encryption or driver support is painful, Room KMP is the fallback — but verify it rather than assuming KMP desktop parity.

The report format is a JSON document. That is fine for now because the data volume is small and local-first writes fixed the UI stall. The next durability win is the outbox, not the file extension.
