package app.baki

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.math.min

private const val PROBE_PORT = 43872
private const val PROBE_TIMEOUT_MS = 1_600

private fun onLan(): Boolean {
    val context = AppCtx.ctx ?: return true
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

private fun localIpv4s(): List<Inet4Address> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.interfaceAddresses }
        .mapNotNull { ia -> ia.address as? Inet4Address }
        .filter { it.isSiteLocalAddress && !it.isLoopbackAddress }
        .distinctBy { it.hostAddress }
}.getOrDefault(emptyList())

/** The server answers any UDP payload on 43872; scan the local /24 and wait for the JSON reply. */
actual suspend fun discoverBakiServer(): DiscoveredServer? = withContext(Dispatchers.IO) {
    if (!onLan()) return@withContext null
    val locals = localIpv4s().take(2)
    if (locals.isEmpty()) return@withContext null

    for (local in locals) {
        val parts = local.hostAddress?.split(".")
        if (parts == null || parts.size != 4) continue
        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
        val own = local.hostAddress
        val payload = "baki?".toByteArray()
        val socket = runCatching { DatagramSocket() }.getOrNull() ?: continue
        try {
            socket.soTimeout = 120
            for (n in 1..254) {
                val host = "$base.$n"
                if (host == own) continue
                runCatching {
                    val target = InetAddress.getByName(host)
                    socket.send(DatagramPacket(payload, payload.size, target, PROBE_PORT))
                }
            }

            val deadline = System.currentTimeMillis() + PROBE_TIMEOUT_MS
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) break
                socket.soTimeout = min(remaining, 250)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: continue
                if (obj.optString("app") != "baki") continue
                val port = obj.optInt("plainPort", obj.optInt("port", 8787))
                if (port <= 0) continue
                val host = packet.address.hostAddress ?: continue
                return@withContext DiscoveredServer("http://$host:$port", host, port)
            }
        } catch (_: Exception) {
            // try the next local interface
        } finally {
            runCatching { socket.close() }
        }
    }
    null
}
