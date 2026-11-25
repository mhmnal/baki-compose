# Baki — current status

As of **2026-11-25**.

## Release

| | |
|---|---|
| Version | **0.64.2** |
| versionCode | **261** |
| APK | `dist/baki-compose-0.64.2.apk` · R8 fullMode=false |
| SHA-256 | `759eb6072ff932ef473438eff75a5cb32732b3862fc3cd0a68f478d42dad1f3a` |
| Signature | Baki release key, SHA-256 `f73c832f0bd750c77571f219049a4db68e72b0e8351e4c7f0889a510b128c9dc` |
| Phone | 0.64.2 installed · direct-Downloads backup verified |
| Desktop | 0.64.2 installed · 0.64.1 and 0.63.1 rollbacks retained |

## Data

- encrypted SQLite authoritative
- schema version **4**
- server ledger: **149 entries · 18 shifts · 4 statements**
- payout gap cleared: 1 Nov statement-funded RM 140.26; 2 Nov statement-funded RM 164.95
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

User moved the backup and passphrase off-device on 25 Nov 2026.

## Verification

- **242 JVM tests, 0 failures**
- 0.64.2 R8 APK built, signed, installed and backup path verified
- Android compile passed
- phone install verified: bank screens gone, weekly statement and Data Health present
- W47 applied through `Repo.updateStatementBreakdown`

## Next gates

1. Run a 6+ hour shift soak with screen off.
2. Review the remaining week-close differences in Data Health.
3. Keep the week of data, then retire the JSON write path (G6).
4. Keep R8 and desktop packaging inside capped systemd services.

## Known gaps

- Data Health backup age reports recent even when no portable file exists.
- Summary chart is oversized for a one-point week.
- FAB can overlap mid-scroll content.
- Some desktop card typography remains small.
- Single-cipher SQLite dependency unresolved.
- No automatic portable backup; local snapshot is not off-device.
- TalkBack coverage of custom canvases and swipe rows incomplete.
