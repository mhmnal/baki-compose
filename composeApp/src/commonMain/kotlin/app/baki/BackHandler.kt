package app.baki

import androidx.compose.runtime.Composable

/** Android's back gesture; a no-op on desktop. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
