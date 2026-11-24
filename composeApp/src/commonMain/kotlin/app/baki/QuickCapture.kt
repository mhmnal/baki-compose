package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A request from a launcher surface; the app validates and writes it through the normal repo. */
data class QuickAddRequest(
    val type: String = "expense",
    val category: String? = null,
    val source: String = "shortcut",
)

object QuickCaptureRequest {
    var pending by mutableStateOf<QuickAddRequest?>(null)
        private set

    fun open(request: QuickAddRequest) { pending = request }
    fun clear() { pending = null }
}
