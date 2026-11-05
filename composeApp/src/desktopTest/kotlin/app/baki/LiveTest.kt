package app.baki

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

/** The server broadcasts after a write; the client's event flow must receive it. */
class LiveTest {

    private val base = System.getProperty("baki.testServer") ?: "http://127.0.0.1:8810"

    @Test
    fun sseDeliversAChangeEvent() = runBlocking {
        val client = TrackerClient(base)
        val store = try { client.pull()!! } catch (e: Exception) {
            println("[live] no server at $base — skipping (${e.message})")
            return@runBlocking
        }

        val got = CompletableDeferred<Boolean>()
        val job = launch {
            runCatching { client.events().collect { got.complete(true) } }
        }
        delay(700)                       // let the stream connect
        client.push(store)               // a write makes the server broadcast
        val arrived = withTimeoutOrNull(6_000) { got.await() } ?: false
        job.cancel()
        assertTrue(arrived, "expected a change event from /api/events")
    }
}
