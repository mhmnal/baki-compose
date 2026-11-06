package app.baki

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Dictation through the system recogniser, so Baki never needs RECORD_AUDIO itself: the recogniser
 * activity owns the microphone.
 */
@Composable
actual fun VoiceMicButton(onText: (String) -> Unit, modifier: Modifier) {
    val baki = LocalBaki.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) onText(spoken)
    }
    Box(
        modifier.clip(RoundedCornerShape(Radius.pill)).clickable {
            PlatformEffects.haptic(Haptic.Tick)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-MY")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you spent")
            }
            runCatching { launcher.launch(intent) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(BakiIcons.Mic, contentDescription = "Speak", tint = baki.muted, modifier = Modifier.size(18.dp))
    }
}
