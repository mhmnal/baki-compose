package app.baki

import app.baki.db.BakiDatabase

/** G4 spike only: prove encrypted SQLite on both targets. Not wired into LedgerStorage yet. */
class EncryptedSpikeDb internal constructor(
    val db: BakiDatabase,
    private val closer: () -> Unit,
) {
    fun close() = closer()
}

expect fun openEncryptedSpikeDb(passphrase: String): EncryptedSpikeDb
