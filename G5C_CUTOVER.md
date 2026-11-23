# G5c — export, parity and storage flag

Branch: `feature/g5-sqlite-storage`.

Status: **G5c tooling passed. Production cutover not enabled.**

## Implemented

- `LedgerStorage.exportJson()`:
  - JSON storage returns its snapshot
  - SQLite storage reconstructs the normalized Store and serializes it
- exact Store parity checks on import/export
- `ClientConfig.storageEngine`:
  - `"json"` default
  - `"sqlite"` opt-in
- `BakiRepo` selects normalized SQLite when configured
- rollback path:
  - backup from the SQLite-backed app
  - set storage engine back to JSON
  - restore the backup
- G5a snapshot and direct JSON import paths

## Verified

- SQLite export reconstructs the exact Store
- repository runs on normalized SQLite behind the seam
- add → export → decode has the correct entry
- direct JSON import with no G5a database
- G5a snapshot import
- row counts and outbox
- **260 JVM tests, 0 failures**
- Android build passed
- Android instrumentation imported 131 entries into normalized SQLite and exported them back

## Rollback runbook

1. From the SQLite-backed app: More → Download, create a fresh portable backup.
2. Reinstall/launch the JSON-backed build.
3. Set `storageEngine` to `json`.
4. Restore the portable backup.
5. Keep the SQLite file untouched for inspection.

The JSON snapshot from before cutover is also retained on the device.

## Cutover gate still required

- [x] real phone import test with production-sized data (131 entries)
- [ ] backup from SQLite and restore into JSON build on device
- [ ] dual-read run for one release
- [ ] explicit cutover release that flips `storageEngine` default
- [ ] post-cutover diagnostics show engine and migration status

`BakiRepo` defaults to JSON. No production data has been migrated.
