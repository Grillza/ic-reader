package io.github.grillza.icreader.ui

import androidx.compose.runtime.Composable

/** Release builds have no test cards (see the debug version). */
internal object DebugTools {
    const val AVAILABLE = false
    const val MENU_LABEL = ""

    @Composable
    fun TestCardsDialog(onDismiss: () -> Unit) = Unit
}
