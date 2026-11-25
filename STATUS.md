# Baki — current status

As of **2026-11-25**.

## Release

| | |
|---|---|
| Version | **0.64.1** |
| versionCode | **260** |
| APK | `dist/baki-compose-0.64.1.apk` · R8 fullMode=false |
| SHA-256 | `3163782ae664f8d3b101fcdde01723fab625de22f83b84146a4843460a823a06` |
| Signature | Baki release key, SHA-256 `f73c832f0bd750c77571f219049a4db68e72b0e8351e4c7f0889a510b128c9dc` |
| Phone | 0.64.1 installed · verified 25 Nov 2026 |
| Desktop | 0.64.1 installed · 0.63.1 rollback retained |

## Data

- encrypted SQLite authoritative
- schema version **4**
- server ledger: **149 entries · 18 shifts · 4 statements**
- W47 breakdown applied: incentives 16.80, tips 4.00, delivery 1.50, Net GSF 101.71, rate 19.9%
- W47 weekly payout remains **387.70**
- W47 auto entries:
  - `statement-auto-2026-11-16-incentive` RM 16.80
  - `statement-auto-2026-11-16-tips` RM 4.00
  - `statement-auto-2026-11-16-delivery` RM 1.50
- JSON snapshot remains rollback/import only

## Portable backup

- file: `~/baki-backups/baki-20261125-backup.bak`
- contents: **146 entries · 18 shifts · 4 statements** (before W47)
- size: **2.6 MB**
- passphrase: separate local file `~/baki-backups/baki-backup-passphrase-20261125.txt`
- backup SHA-256: `c2285574e677be0052845116d93f9f63564d2665d0324d162f09171446796a73`
- decryption verified by the app’s own `encryptBackup`/`decryptBackup`

The backup and passphrase **must be moved to separate off-device locations**.

## Verification

- **242 JVM tests, 0 failures**
- 0.64.1 R8 build passed in capped systemd service
- Android compile passed
- phone install verified: bank screens gone, weekly statement and Data Health present
- W47 applied through `Repo.updateStatementBreakdown`

## Next gates

1. Move backup and passphrase to separate off-device storage.
2. Force phone sync and confirm W47 breakdown/auto entries on device.
3. Desktop 0.64.1 installed; verify a real desk session.
4. Fix the misleading backup-age health line.
5. Investigate the remaining payout gap reported by Data Health.
6. Run a 6+ hour shift soak with screen off.
7. Keep the week of data, then retire the JSON write path (G6).

## Known gaps

- Data Health backup age reports recent even when no portable file exists.
- Summary chart is oversized for a one-point week.
- FAB can overlap mid-scroll content.
- Some desktop card typography remains small.
- Single-cipher SQLite dependency unresolved.
- No automatic portable backup; local snapshot is not off-device.
- TalkBack coverage of custom canvases and swipe rows incomplete.
