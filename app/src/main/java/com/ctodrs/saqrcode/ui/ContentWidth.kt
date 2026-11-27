package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Wave E: on tablets and foldables, form-like screens stop stretching and sit centred at a readable width. */
object ContentWidth {
    const val MAX_DP = 720

    /** The width a capped screen actually uses on a window [windowDp] wide. */
    fun effective(windowDp: Int): Int = windowDp.coerceIn(0, MAX_DP)

    /** True when the cap changes the layout (the window is wider than the cap). */
    fun isCapped(windowDp: Int): Boolean = windowDp > MAX_DP
}

/** Centres [content] and limits it to [ContentWidth.MAX_DP]; on a phone it is a no-op. */
@Composable
fun Capped(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = ContentWidth.MAX_DP.dp)) { content() }
    }
}
