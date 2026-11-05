package app.baki

import androidx.compose.runtime.Composable

/** Returns a lambda that opens a file picker and hands back the chosen file's text. */
@Composable
expect fun rememberJsonImport(onText: (String?) -> Unit): () -> Unit
