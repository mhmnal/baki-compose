package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No system dictation on the laptop: the field is typed into. */
@Composable
actual fun VoiceMicButton(onText: (String) -> Unit, modifier: Modifier) = Unit
