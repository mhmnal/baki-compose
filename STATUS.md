# Baki — current status

As of **2026-11-25**.

## Release

| | |
|---|---|
| Version | **0.64.4** |
| versionCode | **263** |
| APK | `dist/baki-compose-0.64.4.apk` · R8 fullMode=false |
| SHA-256 | `39d4f33a540b98272807b986cd71b0e208db328aef8522976f95baad7a171ef3` |
| Signature | Baki release key, SHA-256 `f73c832f0bd750c77571f219049a4db68e72b0e8351e4c7f0889a510b128c9dc` |
| Phone | 0.64.4 installed · direct-Downloads backup verified |
| Desktop | 0.64.4 installed · 0.64.1 and 0.63.1 rollbacks retained |

## Data

- encrypted SQLite authoritative
- schema version **4**
- server ledger: **149 entries · 18 shifts · 4 statements**
- payout gap cleared: 1 Nov statement-funded RM 140.26; 2 Nov statement-funded RM 164.95
- W44/W45/W46/W47 received amounts align to their weekly statements
- W47 received: RM 387.70
- Data Health: 1 open (Celcomdigi has no active payment link)
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
- full restore verified in an isolated profile: 146 entries · 18 shifts · 4 statements, writes off

User moved the backup and passphrase off-device on 25 Nov 2026.

## Verification

- **243 JVM tests, 0 failures**
- 0.64.4 R8 APK built, signed, installed and backup path verified
- Android compile passed
- phone install verified: bank screens gone, weekly statement and Data Health present
- W47 applied through `Repo.updateStatementBreakdown`

## Next gates

1. Mark or unlink Celcomdigi’s remaining payment.
2. Run a 6+ hour shift soak with screen off.
3. Keep the week of data, then retire the JSON write path (G6).
4. Keep R8 and desktop packaging inside capped systemd services.

## Known gaps

- Data Health backup age reports recent even when no portable file exists.
- Summary chart is oversized for a one-point week.
- FAB can overlap mid-scroll content.
- Some desktop card typography remains small.
- Single-cipher SQLite dependency unresolved.
- No automatic portable backup; local snapshot is not off-device.
- TalkBack: payout delete action and desk chart summary added; broader focus-order/row audit still pending.
