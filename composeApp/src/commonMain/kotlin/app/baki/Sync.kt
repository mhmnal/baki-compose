package app.baki

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class DataEnvelope(val ok: Boolean = true, val data: Store? = null, val version: String? = null)

@Serializable
private data class PushBody(
    val data: Store,
    @SerialName("deletedIds") val deletedIds: List<String> = emptyList(),
    @SerialName("undeleteIds") val undeleteIds: List<String> = emptyList(),
)

/** One sealed trace on its way to the desk. The server treats [blob] as opaque text. */
@Serializable
data class RouteBlob(val shiftId: String, val date: String, val blob: String)

@Serializable
private data class RoutesBody(val items: List<RouteBlob>)

@Serializable
private data class RoutesResponse(val ok: Boolean = true, val items: List<RouteBlob> = emptyList())

/**
 * The same tracker server the web app talks to, so both clients share one dataset.
 * `events()` is the live channel: the server broadcasts a change after any write.
 */
class TrackerClient(
    var baseUrl: String,
    var token: String = "",
    private val http: HttpClient = defaultClient(),
) {

    private fun HttpRequestBuilder.auth() {
        if (token.isNotBlank()) header("X-Baki-Token", token)
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient {
            install(ContentNegotiation) { json(BakiJson) }
            install(SSE)
            // never let a dead server hold the write lock: the UI must stay responsive offline
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 8_000
            }
        }
    }

    /** ETag of the last successful pull, so an unchanged store costs a header round-trip only. */
    private var etag: String? = null

    /** null means "nothing changed since the last pull". */
    suspend fun pull(): Store? {
        val res = http.get("$baseUrl/api/data") {
            auth()
            etag?.let { header(HttpHeaders.IfNoneMatch, it) }
        }
        if (res.status == HttpStatusCode.NotModified) return null
        res.headers[HttpHeaders.ETag]?.let { etag = it }
        val env = BakiJson.decodeFromString<DataEnvelope>(res.bodyAsText())
        return env.data ?: throw IllegalStateException("server returned no data")
    }

    suspend fun push(store: Store, deletedIds: List<String> = emptyList(), undeleteIds: List<String> = emptyList()): Store {
        val res = http.post("$baseUrl/api/data") {
            auth()
            contentType(ContentType.Application.Json)
            val ledgerOnly = store.copy(accounts = emptyList(), importedTxns = emptyList(), statementBatches = emptyList())
            setBody(BakiJson.encodeToString(PushBody.serializer(), PushBody(ledgerOnly, deletedIds, undeleteIds)))
        }
        val env = BakiJson.decodeFromString<DataEnvelope>(res.bodyAsText())
        return env.data ?: store
    }

    /** Statement sidecar: separate from the ledger payload. */
    suspend fun pullStatements(): StatementBook {
        val res = http.get("$baseUrl/api/statements") { auth() }
        if (res.status != HttpStatusCode.OK) throw IllegalStateException("statement pull failed: ${res.status}")
        return BakiJson.decodeFromString<StatementBook>(res.bodyAsText())
    }

    suspend fun pushStatements(book: StatementBook, deletedBatchIds: List<String> = emptyList()): StatementBook {
        val res = http.post("$baseUrl/api/statements") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(BakiJson.encodeToString(StatementPush.serializer(), StatementPush(book.accounts, book.batches, book.txns, book.monthCloses, deletedBatchIds)))
        }
        if (res.status != HttpStatusCode.OK) throw IllegalStateException("statement push failed: ${res.status}")
        val value = BakiJson.decodeFromString<StatementBook>(res.bodyAsText())
        return value
    }

    /** Upload sealed traces; the server stores them under the shift id. */
    suspend fun pushRoutes(items: List<RouteBlob>): Int {
        if (items.isEmpty()) return 0
        val res = http.post("$baseUrl/api/routes") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(BakiJson.encodeToString(RoutesBody.serializer(), RoutesBody(items)))
        }
        if (res.status != HttpStatusCode.OK) throw IllegalStateException("route upload failed: ${res.status}")
        return items.size
    }

    /** Traces recorded since [since] (a yyyy-MM-dd date). */
    suspend fun pullRoutes(since: String): List<RouteBlob> {
        val res = http.get("$baseUrl/api/routes?since=$since") { auth() }
        if (res.status != HttpStatusCode.OK) return emptyList()
        return BakiJson.decodeFromString<RoutesResponse>(res.bodyAsText()).items
    }

    /** Emits once per server-side change. Reconnects by the caller's restart policy. */
    fun events(): Flow<String> = flow {
        http.sse("$baseUrl/api/events", request = { auth() }) {
            incoming.collect { event ->
                event.data?.let { emit(it) }
            }
        }
    }
}

@Serializable
data class StatementBook(
    val accounts: List<Account> = emptyList(),
    val batches: List<StatementBatch> = emptyList(),
    val txns: List<ImportedTxn> = emptyList(),
    val monthCloses: Map<String, String> = emptyMap(),
)

@Serializable
private data class StatementPush(
    val accounts: List<Account> = emptyList(),
    val batches: List<StatementBatch> = emptyList(),
    val txns: List<ImportedTxn> = emptyList(),
    val monthCloses: Map<String, String> = emptyMap(),
    val deletedBatchIds: List<String> = emptyList(),
)
