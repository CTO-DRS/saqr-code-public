package com.ctodrs.saqrcode.agent

import org.json.JSONObject
import java.io.File

/** One image attached to a user message, already downscaled and encoded (see [ImageMath]). */
data class ImagePart(val base64: String, val mediaType: String = "image/jpeg") {
    val dataUrl get() = "data:$mediaType;base64,$base64"
    override fun toString() = "ImagePart($mediaType, ${base64.length} b64 chars)"
}

/** Does a model accept image input? Heuristic by model id, overridable per model in Settings. */
object Vision {
    /** Settings value per model: [AUTO] uses [heuristic]; [ON] / [OFF] force it. */
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"

    private val yes = listOf(
        Regex("""^gpt-4o"""), Regex("""^chatgpt-4o"""), Regex("""^gpt-4\.1"""), Regex("""^gpt-4\.5"""), Regex("""^gpt-4-turbo"""),
        Regex("""^gpt-4-vision"""), Regex("""^gpt-5"""), Regex("""^o1(?!-mini)(-|$)"""), Regex("""^o3"""), Regex("""^o4"""),
        Regex("""^claude-3"""), Regex("""^claude-(opus|sonnet|haiku)-4"""), Regex("""^claude-4"""), Regex("""^claude-(opus|sonnet|haiku)-[3-9]"""),
        Regex("""^gemini"""), Regex("""^gemma-3"""), Regex("""^pixtral"""), Regex("""^grok-.*vision"""), Regex("""^grok-4"""),
        Regex("""llama.*vision"""), Regex("""llama-4"""), Regex("""^qwen.*vl"""), Regex("""^qvq"""), Regex("""llava"""),
        Regex("""^mistral-(small|medium)-3"""), Regex("""^glm-4(\.\d)?v"""), Regex("""^kimi-vl"""), Regex("""^minicpm-v"""),
    )
    private val no = listOf(Regex("""^o1-mini"""), Regex("""^o3-mini"""), Regex("""audio"""), Regex("""embedding"""), Regex("""-tts"""), Regex("""^gpt-4o-.*transcribe"""))

    /** "openai/gpt-4o-mini" → "gpt-4o-mini"; ":free" / ":beta" suffixes dropped; lowercase. */
    fun normalize(modelId: String): String =
        modelId.trim().lowercase().substringAfterLast('/').substringBefore(':')

    fun heuristic(modelId: String): Boolean {
        val m = normalize(modelId)
        if (m.isEmpty()) return false
        if (no.any { it.containsMatchIn(m) }) return false
        return yes.any { it.containsMatchIn(m) }
    }

    fun supports(modelId: String, overrides: Map<String, String>): Boolean =
        when (overrides[normalizeKey(modelId)]) {
            ON -> true
            OFF -> false
            else -> heuristic(modelId)
        }

    /** Overrides are stored per exact model id (case-insensitive, trimmed). */
    fun normalizeKey(modelId: String) = modelId.trim().lowercase()

    fun decode(json: String?): Map<String, String> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it == ON || it == OFF }
    }.getOrDefault(emptyMap())

    fun encode(map: Map<String, String>): String =
        JSONObject().apply { map.filterValues { it == ON || it == OFF }.forEach { (k, v) -> put(k, v) } }.toString()

    /** First vision-capable model from [candidates] (e.g. the provider's suggestions), or null. */
    fun suggestSwitch(candidates: List<String>, overrides: Map<String, String>): String? =
        candidates.firstOrNull { supports(it, overrides) }
}

/** Downscale math for attachments: longest side at most [MAX_SIDE] px, aspect kept, never upscaled. */
object ImageMath {
    const val MAX_SIDE = 1568
    const val JPEG_QUALITY = 85

    fun targetSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "empty image" }
        val long = maxOf(width, height)
        if (long <= maxSide) return width to height
        val scale = maxSide.toDouble() / long
        val w = if (width >= height) maxSide else Math.round(width * scale).toInt().coerceAtLeast(1)
        val h = if (height > width) maxSide else Math.round(height * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    /** Largest power-of-two sample size that keeps the decoded image >= target (cheap first decode pass). */
    fun inSampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var s = 1
        while (maxOf(width, height) / (s * 2) >= maxSide) s *= 2
        return s
    }

    /** Element crop: [rect] in CSS px of a viewport [cssWidth] wide, mapped onto a bitmap [bmpW]×[bmpH] with [pad] px. */
    fun cropRect(x: Double, y: Double, w: Double, h: Double, cssWidth: Double, bmpW: Int, bmpH: Int, pad: Int = 8): IntArray? {
        if (cssWidth <= 0 || w <= 0 || h <= 0) return null
        val k = bmpW / cssWidth
        val l = (x * k - pad).toInt().coerceIn(0, bmpW - 1)
        val t = (y * k - pad).toInt().coerceIn(0, bmpH - 1)
        val r = ((x + w) * k + pad).toInt().coerceIn(l + 1, bmpW)
        val b = ((y + h) * k + pad).toInt().coerceIn(t + 1, bmpH)
        return intArrayOf(l, t, r - l, b - t)
    }
}

/** Only the newest image-bearing turns keep their images when re-sent; older ones get a placeholder. */
object ImagePruner {
    const val KEEP = 2
    const val OMITTED = "[image omitted]"

    fun prune(history: List<Msg>, keep: Int = KEEP): List<Msg> {
        var kept = 0
        val out = ArrayList<Msg>(history.size)
        for (m in history.asReversed()) {
            if (m is Msg.User && m.images.isNotEmpty()) {
                if (kept < keep) { kept++; out += m }
                else out += Msg.User(m.text + "\n" + List(m.images.size) { OMITTED }.joinToString(" "))
            } else out += m
        }
        return out.asReversed()
    }
}

/** Which UI stack an "Image → Code" request should target. */
enum class Stack { COMPOSE, WEB, UNKNOWN }

object StackDetector {
    fun detect(dir: File): Stack {
        val gradle = listOf("build.gradle.kts", "build.gradle", "app/build.gradle.kts", "app/build.gradle")
            .map { File(dir, it) }.filter { it.isFile }
        if (gradle.any { runCatching { it.readText() }.getOrDefault("").contains("compose", ignoreCase = true) }) return Stack.COMPOSE
        val files = dir.walkTopDown().maxDepth(4).onEnter { it.name !in setOf("node_modules", ".git", "build", ".gradle") }
            .filter { it.isFile }.take(2000).toList()
        if (files.any { it.extension == "kt" && runCatching { it.readText().contains("androidx.compose") }.getOrDefault(false) }) return Stack.COMPOSE
        if (files.any { it.extension in setOf("html", "htm", "jsx", "tsx", "vue", "svelte") || it.name == "package.json" }) return Stack.WEB
        return Stack.UNKNOWN
    }

    /** The prompt prefix for "Image → Code". The model replies in the user's language (system prompt). */
    fun imageToCodePrompt(stack: Stack, userText: String): String {
        val target = when (stack) {
            Stack.COMPOSE -> "This is a Jetpack Compose project: recreate the UI as Compose code (Material 3), following the project's existing theme, package and file layout."
            Stack.WEB -> "This is a web project: recreate the UI in HTML/CSS/JS (match the project's existing setup; plain HTML/CSS/JS if there is none), with an index.html the app can preview."
            Stack.UNKNOWN -> "The project's stack is not clear yet: before writing code, call ask_user with one question asking which stack to use (recommend HTML/CSS/JS for a quick preview; offer Jetpack Compose)."
        }
        return buildString {
            appendLine("[Image → Code] Recreate the UI in the attached screenshot as faithfully as you can: layout, spacing, colors, typography, icons (use close equivalents) and all visible text.")
            appendLine(target)
            appendLine("Be Arabic/RTL aware: if the screenshot shows Arabic or right-to-left text, keep it RTL (dir=\"rtl\" / LayoutDirection.Rtl) with mirrored layout, and use a font with good Arabic glyphs.")
            appendLine("Make it responsive for a phone screen first. Use real content from the image, no lorem ipsum.")
            if (stack == Stack.WEB) appendLine("When done, tell the user it is ready in the Preview tab.")
            else if (stack == Stack.COMPOSE) appendLine("When done, add a @Preview for the main composable so it can be checked.")
            if (userText.isNotBlank()) { appendLine(); append("User notes: ").append(userText.trim()) }
        }.trimEnd()
    }
}
