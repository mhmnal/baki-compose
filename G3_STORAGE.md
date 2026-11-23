# G3 — storage seam

Status: **passed**.

## What changed

- `LedgerStorage` interface:
  - load with operation replay
  - save with outbox recording
  - path
- `JsonLedgerStorage` adapter uses the existing snapshot/replay code.
- `BakiRepo` accepts a storage implementation, defaulting to JSON.
- All repository snapshot writes go through `storage.save`.
- UI diagnostics read `repo.storagePath`; no UI code imports `LocalStore` directly.
- A fake `MemoryStorage` test proves the repository writes only through the interface.

## Gate

- [x] JSON adapter keeps existing behavior
- [x] repository accepts alternate storage
- [x] fake-storage test
- [x] no UI access to storage internals
- [x] operation outbox still works
- [x] 250 tests, 0 failures (target)
- [x] Android lane
- [x] full gate

## Next

G4 is the **SQLDelight + SQLCipher spike** on both Android and desktop. It is not a production migration:

1. add the dependencies on a branch
2. create the schema
3. open an encrypted DB on both platforms
4. verify key persistence and wrong-key behavior
5. verify schema v1→v2
6. benchmark 10k entries
7. go/no-go: SQLDelight, Room KMP fallback, or keep JSON + outbox

Only after G4 passes does the JSON→SQLite importer/cutover begin.
