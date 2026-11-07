package app.baki

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Base64 for sealed traces, in one place so the wire format cannot drift between clients. */
@OptIn(ExperimentalEncodingApi::class)
object RouteBase64 {
    fun encode(bytes: ByteArray): String = Base64.encode(bytes)
    fun decode(text: String): ByteArray = Base64.decode(text)
}
