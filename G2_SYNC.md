# G2 — incremental operation sync

Status: **G2 gate passed**.

## Server

`POST /api/ops`

```json
{ "ops": [ { "id": "op...", "seq": 1, "entity": "entry", "entityId": "...", "kind": "upsert", "payload": "...", "updated": "..." } ] }
```

Response:

```json
{ "ok": true, "accepted": ["op..."], "version": "...", "entries": 131 }
```

- operation ids are durable in `ops.json` beside `data.json`
- duplicate ids are acknowledged without reapplying
- batch limit 500
- entry operations use the same newest-wins merge as `/api/data`
- generic `store` replacement payloads are merged, never blindly substituted
- statement sidecar fields are stripped from generic operations
- `/api/reset` clears the operation journal

## Client

- pending operations are sent before the pull
- batch size is 100
- only a contiguous accepted prefix advances `ackedSeq`
- later unaccepted operations stay for retry
- full-store push remains the fallback for old servers
- successful full-store fallback calls `acknowledgeAll`
- server-derived statement pulls and normalization do **not** create local operations

## Verified

- server endpoint accepts an entry operation
- duplicate delivery returns accepted but does not duplicate the entry
- operation endpoint preserves an existing entry when a generic store op is merged
- client `pushOps` round-trip
- repository add → outbox → server → compaction
- repository delete → outbox → server tombstone
- partial ack prefix test
- 10k local operation test
- 247 tests, 0 failures
- Android lane and gate

## Limits

- normal sync still pulls with ETag and merges whole stores
- operation payloads for non-entry mutations are full Store replacements
- conflict testing is currently record-level newest-wins / tombstone-wins
- old-server fallback is code-tested only indirectly
- no real 6+ hour shift test

## G2 gate

- [x] `/api/ops`
- [x] idempotent dedupe journal
- [x] accepted acknowledgement
- [x] client batch send
- [x] contiguous prefix compaction
- [x] full-store fallback path
- [x] outbox pollution fixed
- [x] duplicate delivery test
- [x] end-to-end repo sync/compaction test
- [x] explicit old-server 404 fallback integration test
- [x] two-device conflict matrix
- [ ] entity-specific compact operations (generic Store payloads for now)
