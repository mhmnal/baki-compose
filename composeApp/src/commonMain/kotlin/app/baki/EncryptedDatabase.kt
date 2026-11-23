package app.baki

import app.baki.db.BakiDatabase

/** Encrypted SQLite handle used by the G5 storage and the G4 spike. */
class EncryptedSpikeDb internal constructor(
    val db: BakiDatabase,
    private val closer: () -> Unit,
) {
    fun close() = closer()
}

expect fun openEncryptedDb(name: String, passphrase: String): EncryptedSpikeDb

fun openEncryptedSpikeDb(passphrase: String): EncryptedSpikeDb =
    openEncryptedDb("baki-spike.db", passphrase)
