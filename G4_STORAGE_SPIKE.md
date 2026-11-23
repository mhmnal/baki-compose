# G4 — encrypted SQLDelight spike

Status: **technical pass on branch `spike/g4-encrypted-sqldelight`. Not shipped in production.**

## Stack

- SQLDelight 2.4.1
- Android: `net.zetetic:sqlcipher-android:4.20.0`
- Desktop: `io.github.willena:sqlite-jdbc:3.53.4.0` (SQLite3MultipleCiphers)

## Verified

- encrypted database creates and reopens
- desktop file does not expose plaintext probe values
- wrong passphrase fails
- Android SQLCipher native library loads via `System.loadLibrary("sqlcipher")`
- Android round-trip insert/select works
- schema v1 → v2 migration adds a column
- 10,000-row encrypted database opens and queries in **466 ms**
- SQLCipher V4 defaults allow a desktop-written encrypted database to open on Android
- 255 JVM tests, 0 failures
- Android `readsDesktopWrittenCipherDatabase` passed

## Decision

**SQLDelight is technically viable on both targets.**

Production cutover is still not approved:

- desktop encryption uses the Willena SQLite3MultipleCiphers fork, Android uses Zetetic SQLCipher
- cross-platform desktop → Android with SQLCipher V4 defaults passed; Android → desktop still should be checked
- different cipher implementations remain a support and dependency risk
- SQLCipher native libraries add APK size
- SQLCipher dependencies remain on the spike branch only

## Next

1. Android → desktop reverse compatibility test.
2. Decide whether mixed cipher implementations are acceptable.
3. If yes, G5 importer/cutover.
4. If no, stay on JSON + outbox and revisit Room KMP/SQLCipher.

No production storage path uses this database.
