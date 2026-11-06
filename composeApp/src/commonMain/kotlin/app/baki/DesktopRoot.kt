package app.baki

import androidx.compose.runtime.Composable

/**
 * Renders the desktop shell when this platform has one. Returns false on phones, so App() can fall
 * through to the touch UI. Kept as expect/actual so the desktop view may use desktop-only pointers.
 */
@Composable
expect fun DesktopRoot(repo: BakiRepo): Boolean
