package app.baki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A PDF handed to Baki by the Android share sheet or a VIEW intent. */
object SharedStatement {
    var pending by mutableStateOf<ByteArray?>(null)
}
