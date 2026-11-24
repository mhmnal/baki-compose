# Baki — current status

As of **2026-11-24**.

## Release

| | |
|---|---|
| Version | **0.58.1** |
| versionCode | **250** |
| APK | `dist/baki-compose-0.58.1.apk` |
| SHA-256 | `cc96ff025f34899c3ce76fa7215c912f4e770d3d6139deabd2ab992e63a89efd` |
| Signature | Baki release key, SHA-256 `f73c832f0bd750c77571f219049a4db68e72b0e8351e4c7f0889a510b128c9dc` |
| Phone | 0.58.1 installed |
| Desktop | 0.58.1 installed |

## Data

- encrypted SQLite is authoritative
- schema version **4**
- current phone ledger: **132 entries · 18 shifts**
- portable backup is **stale and must be refreshed**
- JSON snapshot is rollback/import only

## Shipped since cutover

- **0.56.0** normalized encrypted SQLite, JSON rollback retained
- **0.56.1** persistent SQLCipher handle, diff-only writes, off-UI persistence
- desktop jpackage: added `java.sql`, removed duplicate Xerial JDBC jar
- **0.57.0** explicit recovery mode, verified local snapshot after Week Close, statement-funded payout allocation
- **0.58.1** visual clarity pass: neutral Data Health, stronger More hierarchy, shift payout pills, desktop ledger alignment, truth labels

## Verification

- 264 JVM tests, 0 failures
- full gate passed
- Android lane passed
- real phone SQLite cutover passed
- real-device SQLite backup → JSON restore was verified earlier
- payout allocation source survives server sync

## Next gates

1. Fresh portable backup.
2. Close the current week once to create the first automatic recovery snapshot.
3. Test payout allocation against a real statement week.
4. Run a 6+ hour shift soak with screen off.
5. Keep the week of data, then retire the JSON write path (G6).

## Known gaps

- Summary chart is oversized for a one-point week.
- FAB can overlap mid-scroll content.
- Some desktop card typography remains small.
- Single-cipher SQLite dependency is unresolved.
- No automatic portable backup; local snapshot is not off-device.
- TalkBack coverage of custom canvases and swipe rows is incomplete.
