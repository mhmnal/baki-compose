# G1 — entry operation outbox

Completed: **all Store mutations** on top of the JSON snapshot.

## What is durable now

Every Store mutation writes:

1. an encrypted operation record to `operations.log`
2. the JSON snapshot
3. the snapshot sequence in `operations.meta`

Entry add/edit/delete use a compact entry operation. All other mutations (shifts, payouts, bills, payments, week closes, reserves, settings, statements, categories, notes, receipts) use a generic encrypted store replacement. This is intentionally coarse for now; G2 can add entity-specific operations.

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

## Known limits

- non-entry operations carry a full Store payload; this is durable but larger than entity-specific operations.
- entry operations are compact.
- server acknowledgement is still a full-store push; G2 adds an operation endpoint.
- routes, calibration, live shift and client config are separate files and are not part of the Store outbox.

## G1 gate status

- [x] entry operations append before snapshot
- [x] replay after crash
- [x] 10k operations
- [x] idempotent replay
- [x] encrypted at rest
- [x] diagnostics outbox depth
- [x] no UI behavior change
- [x] all Store mutations covered (entry compact; others generic store replacement)
- [ ] server operation endpoint (G2)

**G1 is complete for local durability. G2 is the sync endpoint.**
