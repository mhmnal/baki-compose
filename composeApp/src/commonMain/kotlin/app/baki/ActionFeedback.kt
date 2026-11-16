package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small, global confirmation for settings writes that otherwise dismiss silently. */
object ActionFeedback {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(text: String) { message = text }

    fun clear(text: String? = null) {
        if (text == null || message == text) message = null
    }
}
