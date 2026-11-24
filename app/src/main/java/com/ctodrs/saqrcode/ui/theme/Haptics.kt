package com.ctodrs.saqrcode.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * v0.12.1 «ريش مكمّلة» · توكنات الاهتزاز الدلالي · Semantic haptics tokens.
 *
 * Every touch the app acknowledges maps to one semantic token — never a raw constant sprinkled
 * through the UI. Tokens resolve to `android.view.HapticFeedbackConstants` (view-level feedback,
 * so the system-wide haptics toggle still governs it) with graceful pre-30 fallbacks:
 *
 * | token   | meaning                        | 30+      | <30        |
 * |---------|--------------------------------|----------|------------|
 * | TICK    | subtle scroll/step confirmation| CLOCK_TICK | CLOCK_TICK |
 * | POP     | chip/tab/pack selected         | VIRTUAL_KEY | VIRTUAL_KEY |
 * | CONFIRM | build finished, theme applied  | CONFIRM  | KEYBOARD_TAP |
 * | REJECT  | build failed, invalid input    | REJECT   | LONG_PRESS |
 * | HEAVY   | destructive action             | LONG_PRESS | LONG_PRESS |
 */
enum class HapticKind { TICK, POP, CONFIRM, REJECT, HEAVY }

object HapticTokens {
    // android.view.HapticFeedbackConstants literals — kept local so the mapping is JVM-testable.
    const val LONG_PRESS = 0
    const val VIRTUAL_KEY = 1
    const val KEYBOARD_TAP = 3
    const val CLOCK_TICK = 4
    const val REJECT = 13   // API 30+
    const val CONFIRM = 16  // API 30+

    /** The best constant for [kind] on the given API level. */
    fun constant(kind: HapticKind, sdkInt: Int): Int = when (kind) {
        HapticKind.TICK -> CLOCK_TICK
        HapticKind.POP -> VIRTUAL_KEY
        HapticKind.HEAVY -> LONG_PRESS
        HapticKind.CONFIRM -> if (sdkInt >= 30) CONFIRM else KEYBOARD_TAP
        HapticKind.REJECT -> if (sdkInt >= 30) REJECT else LONG_PRESS
    }
}

/** One facade for semantic touches; constructed once per screen via [rememberSaqrHaptics]. */
class SaqrHaptics(private val view: android.view.View, private val sdkInt: Int) {
    fun perform(kind: HapticKind) {
        runCatching { view.performHapticFeedback(HapticTokens.constant(kind, sdkInt)) }
    }
}

@Composable
fun rememberSaqrHaptics(): SaqrHaptics {
    val view = LocalView.current
    return remember(view) { SaqrHaptics(view, android.os.Build.VERSION.SDK_INT) }
}
