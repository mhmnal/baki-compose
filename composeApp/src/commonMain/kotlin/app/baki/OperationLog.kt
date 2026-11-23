package app.baki

import kotlinx.serialization.Serializable

/**
 * A small durable operation journal beside the JSON snapshot.
 *
 * G1 scope: entry upserts/deletes. Other entity mutations still rely on the snapshot and its
 * `.bak`. Keeping the log entry-only first lets replay/idempotency be proven without rewriting
 * every repository path at once.
 */
@Serializable
data class LedgerOperation(
    val id: String,
    val seq: Long,
    val entity: String,
    val entityId: String,
    /** "upsert" or "delete" */
    val kind: String,
    /** Serialised entity for upserts; blank for deletes. */
    val payload: String = "",
    /** Entity update timestamp, used for idempotent replay. */
    val updated: String = "",
    val createdAt: Long = 0,
)

@Serializable
private data class OperationMeta(
    val snapshotSeq: Long = 0,
    val ackedSeq: Long = 0,
    val nextSeq: Long = 1,
)

class OperationLog(
    private val fileName: String = "operations.log",
    private val metaName: String = "operations.meta",
) {
    private fun meta(): OperationMeta =
        runCatching {
            LocalStore.readText(metaName)?.let {
                BakiJson.decodeFromString<OperationMeta>(it)
            }
        }.getOrNull() ?: OperationMeta()

    private fun writeMeta(value: OperationMeta) {
        LocalStore.writeText(metaName, BakiJson.encodeToString(OperationMeta.serializer(), value))
    }

    /** Append one encrypted record and return its assigned sequence, or null on I/O failure. */
    fun append(entity: String, entityId: String, kind: String, payload: String = "", updated: String = ""): Long? {
        return runCatching {
            val metaText = LocalStore.readText(metaName)
            val currentMeta = metaText?.let {
                BakiJson.decodeFromString<OperationMeta>(it)
            } ?: OperationMeta()
            // If the metadata vanished but records remain, recover the sequence from the log.
            val parsed = if (metaText == null && LocalStore.exists(fileName)) {
                parse(LocalStore.readBytes(fileName))
            } else null
            val maxSeq = parsed?.operations?.maxOfOrNull { it.seq } ?: 0
            val seq = maxOf(currentMeta.nextSeq, maxSeq + 1)
            val op = LedgerOperation(
                id = newId("op"),
                seq = seq,
                entity = entity,
                entityId = entityId,
                kind = kind,
                payload = payload,
                updated = updated,
                createdAt = nowMillis(),
            )
            val record = encodeRecord(op)
            if (!LocalStore.exists(fileName)) {
                LocalStore.writeBytes(fileName, MAGIC + record)
            } else {
                LocalStore.appendBytes(fileName, record)
            }
            writeMeta(currentMeta.copy(nextSeq = seq + 1))
            seq
        }.getOrElse {
            StoreHealth.saveError = "Operation log append failed: ${it.message}"
            null
        }
    }

    fun pendingAfterSnapshot(): List<LedgerOperation> {
        val snap = meta().snapshotSeq
        return runCatching { parse(LocalStore.readBytes(fileName)).operations.filter { it.seq > snap } }
            .getOrDefault(emptyList())
    }

    /** Operations not yet covered by a successful server push. */
    fun pendingOutbox(): List<LedgerOperation> {
        val acked = meta().ackedSeq
        return runCatching { parse(LocalStore.readBytes(fileName)).operations.filter { it.seq > acked } }
            .getOrDefault(emptyList())
    }

    fun pendingCount(): Int = pendingOutbox().size

    /** Mark that the JSON snapshot now contains these operations. */
    fun markSnapshot(seq: Long) {
        if (seq <= 0) return
        val current = meta()
        if (seq > current.snapshotSeq) writeMeta(current.copy(snapshotSeq = seq))
    }

    /** The full-store push succeeded; every current operation is covered by that snapshot. */
    fun acknowledgeAll() {
        val current = meta()
        val ops = runCatching { parse(LocalStore.readBytes(fileName)).operations }.getOrDefault(emptyList())
        val maxSeq = ops.maxOfOrNull { it.seq } ?: 0
        val covered = maxOf(current.snapshotSeq, current.ackedSeq, maxSeq, current.nextSeq - 1)
        runCatching { LocalStore.deleteBytes(fileName) }
        writeMeta(current.copy(snapshotSeq = covered, ackedSeq = covered))
    }

    fun readAll(): List<LedgerOperation> = runCatching { parse(LocalStore.readBytes(fileName)).operations }
        .getOrDefault(emptyList())

    /** Startup repair: drop a torn tail so the next append starts at a valid record boundary. */
    fun repairTornTail() {
        runCatching {
            val raw = LocalStore.readBytes(fileName) ?: return
            val parsed = parse(raw)
            if (parsed.validEnd in 1 until raw.size) {
                val repaired = ByteArrayOutputStream()
                repaired.write(MAGIC)
                parsed.operations.forEach { repaired.write(encodeRecord(it)) }
                LocalStore.writeBytes(fileName, repaired.toByteArray())
            }
        }
    }

    private data class Parsed(val operations: List<LedgerOperation>, val validEnd: Int)

    private fun parse(raw: ByteArray?): Parsed {
        if (raw == null || !hasMagic(raw)) return Parsed(emptyList(), 0)
        val out = mutableListOf<LedgerOperation>()
        var at = MAGIC.size
        while (at + 4 <= raw.size) {
            val len = readInt(raw, at)
            if (len <= 0 || at + 4 + len > raw.size) break
            val sealed = raw.copyOfRange(at + 4, at + 4 + len)
            val op = runCatching {
                BakiJson.decodeFromString<LedgerOperation>(StoreCipher.decrypt(sealed).decodeToString())
            }.getOrNull() ?: break
            out += op
            at += 4 + len
        }
        return Parsed(out, at)
    }

    private fun encodeRecord(op: LedgerOperation): ByteArray {
        val sealed = StoreCipher.encrypt(BakiJson.encodeToString(LedgerOperation.serializer(), op).encodeToByteArray())
        return intBytes(sealed.size) + sealed
    }

    private fun hasMagic(raw: ByteArray): Boolean =
        raw.size >= MAGIC.size && MAGIC.indices.all { raw[it] == MAGIC[it] }

    companion object {
        private val MAGIC = "BAKIOP1".encodeToByteArray()
        private fun intBytes(n: Int) = byteArrayOf(
            (n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte(),
        )
        private fun readInt(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and 0xff) shl 24) or ((b[at + 1].toInt() and 0xff) shl 16) or
                ((b[at + 2].toInt() and 0xff) shl 8) or (b[at + 3].toInt() and 0xff)
    }
}

/** Apply operations in sequence. Store replacements carry every non-entry mutation. */
fun applyOperations(store: Store, ops: List<LedgerOperation>): Store {
    if (ops.isEmpty()) return store
    var current = store
    for (op in ops.sortedBy { it.seq }) {
        when (op.entity) {
            "store" -> if (op.kind == "replace") {
                current = runCatching { BakiJson.decodeFromString<Store>(op.payload) }.getOrDefault(current)
            }
            "entry" -> current = applyEntryOperations(current, listOf(op))
        }
    }
    return current
}

/** Apply entry operations in order. Upserts/deletes are idempotent against a newer snapshot. */
fun applyEntryOperations(store: Store, ops: List<LedgerOperation>): Store {
    if (ops.isEmpty()) return store
    val entries = store.entries.associateBy { it.id }.toMutableMap()
    val tombstones = store.tomb.ids.toMutableSet()
    var changed = false
    for (op in ops.sortedBy { it.seq }) {
        if (op.entity != "entry") continue
        when (op.kind) {
            "delete" -> {
                if (entries.remove(op.entityId) != null) changed = true
                if (tombstones.add(op.entityId)) changed = true
            }
            "upsert" -> {
                val entry = runCatching { BakiJson.decodeFromString<Entry>(op.payload) }.getOrNull() ?: continue
                val old = entries[entry.id]
                if (old == null || entry.updated.isBlank() || old.updated.isBlank() || entry.updated >= old.updated) {
                    if (old != entry) changed = true
                    entries[entry.id] = entry
                    if (tombstones.remove(entry.id)) changed = true
                }
            }
        }
    }
    if (!changed) return store
    return store.copy(
        entries = entries.values.sortedBy { it.loggedAt.ifBlank { it.updated } },
        tomb = store.tomb.copy(ids = tombstones.toList()),
    )
}

private class ByteArrayOutputStream {
    private val chunks = mutableListOf<ByteArray>()
    private var size = 0
    fun write(b: ByteArray) { chunks += b; size += b.size }
    fun toByteArray(): ByteArray {
        val out = ByteArray(size)
        var at = 0
        for (chunk in chunks) { chunk.copyInto(out, at); at += chunk.size }
        return out
    }
}
