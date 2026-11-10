package io.github.grillza.icreader.ui

import io.github.grillza.icreader.data.ScanResult

/** Release builds have no demo card (see the debug version). */
internal object DemoData {
    const val AVAILABLE = false
    const val MENU_LABEL = ""

    fun scan(): ScanResult = error("No demo card in release builds")
}
