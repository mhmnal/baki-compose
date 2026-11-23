# G1 — entry operation outbox

Completed slice: **entry upserts/deletes** on top of the JSON snapshot.

## What is durable now

Every entry add, multi-add, edit and delete writes:

1. an encrypted operation record to `operations.log`
2. the JSON snapshot
3. the snapshot sequence in `operations.meta`

The log is separate from the snapshot. On launch:

- a torn tail is repaired to the last valid record
- operations newer than the snapshot sequence are replayed
- replay is idempotent by entry id + `updated` timestamp
- operations stay in the server outbox until a successful full-store push

Server acknowledgement and snapshot coverage are separate sequences:

```text
snapshotSeq  operation is represented in the local JSON
ackedSeq     operation was covered by a successful server push
```

A successful full-store push clears the log and advances both sequences.

## Verified

- 10,000 append/read operations in the test
- operation payloads and metadata are encrypted at rest
- sequence ordering survives a missing/partial metadata file
- torn-tail startup repair
- replay after a crash between operation append and snapshot save
- idempotent replay twice
- encrypted log does not expose entry id or entity type
- add + delete leave two durable outbox operations
- outbox depth is visible in Diagnostics
- replayed operations force `pendingPush = true` even though the repository callback is installed later

## Test files

- `OperationLogTest.kt`
- `OutboxRepoTest.kt`

## Known limits / next G1 slice

Only `entry` operations are logged. These still use snapshot + `pendingPush` only:

- shifts and shift payouts
- bill payments and links
- week closes
- reserve movements
- settings/category edits
- statement mutations
- receipts

The next G1 slice should add a generic operation payload for those entities, or one operation type at a time, before G2 incremental sync.

## G1 gate status

- [x] entry operations append before snapshot
- [x] replay after crash
- [x] 10k operations
- [x] idempotent replay
- [x] encrypted at rest
- [x] diagnostics outbox depth
- [x] no UI behavior change
- [ ] all mutations covered
- [ ] server operation endpoint (G2)

**G1 is an entry-slice pass, not the full mutation coverage gate.**
