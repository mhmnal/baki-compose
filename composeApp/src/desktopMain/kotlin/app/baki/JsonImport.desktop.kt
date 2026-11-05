package app.baki

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberJsonImport(onText: (String?) -> Unit): () -> Unit = remember(onText) {
    {
        val chooser = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            onText(runCatching { chooser.selectedFile.readText() }.getOrNull())
        } else onText(null)
    }
}
