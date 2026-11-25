package com.ctodrs.saqrcode.agent

import org.json.JSONObject

/**
 * v0.13.3 «مدرّب صقر» · Saqr Coach: a short in-app learning path that walks a brand-new user
 * from zero to their **first successful on-device build** with Arabic command examples.
 *
 * The core is pure Kotlin: the UI feeds a [CoachSnapshot] of *real* signals (a stored key, an
 * existing project, the first message the agent received, a build attempt, a successful build)
 * and reads back the ordered steps. Nothing can be faked — every step completes only when the
 * underlying signal exists, so the coach's promise matches reality.
 */
data class CoachSnapshot(
    val hasKey: Boolean = false,
    val hasProject: Boolean = false,
    val hasFirstMessage: Boolean = false,
    val hasBuildAttempt: Boolean = false,
    val hasSuccessfulBuild: Boolean = false,
)

/** Step → epoch ms when it was first observed done; persisted so the coach remembers its story. */
object CoachProgressCodec {

    fun encode(p: Map<String, Long>): String = JSONObject().apply { p.forEach { (k, v) -> put(k, v) } }.toString()

    fun decode(json: String?): Map<String, Long> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        val out = LinkedHashMap<String, Long>()
        o.keys().forEach { k -> val v = o.optLong(k, 0L); if (v > 0L) out[k] = v }
        out
    }.getOrDefault(emptyMap())

    /** Stamps newly-completed steps with [now]; keeps the first-recorded time for the rest. */
    fun merge(cur: Map<String, Long>, doneIds: Collection<String>, now: Long): Map<String, Long> {
        val out = cur.toMutableMap()
        doneIds.forEach { id -> if (id !in out) out[id] = now }
        return out
    }
}

object SaqrCoach {
    const val STEP_KEY = "key"
    const val STEP_PROJECT = "project"
    const val STEP_MESSAGE = "message"
    const val STEP_BUILD = "build"
    const val STEP_FIRST_BUILD = "first_build"

    /** The path, in order. The coach never lets the user skip ahead. */
    val STEPS = listOf(STEP_KEY, STEP_PROJECT, STEP_MESSAGE, STEP_BUILD, STEP_FIRST_BUILD)

    data class Step(val id: String, val done: Boolean)

    fun steps(s: CoachSnapshot): List<Step> = listOf(
        Step(STEP_KEY, s.hasKey),
        Step(STEP_PROJECT, s.hasProject),
        Step(STEP_MESSAGE, s.hasFirstMessage),
        Step(STEP_BUILD, s.hasBuildAttempt || s.hasSuccessfulBuild),
        Step(STEP_FIRST_BUILD, s.hasSuccessfulBuild),
    )

    /** The first pending step — the coach's focus. Null once the path is complete. */
    fun current(s: CoachSnapshot): String? = steps(s).firstOrNull { !it.done }?.id

    /** Completed step count (0..[STEPS].size). */
    fun progress(s: CoachSnapshot): Int = steps(s).count { it.done }

    /** The gate of the whole feature: a first *successful* build, not merely an attempt. */
    fun complete(s: CoachSnapshot): Boolean = s.hasSuccessfulBuild

    /** Home-card visibility: once hidden by the user it stays hidden (re-openable from settings). */
    fun visible(dismissed: Boolean): Boolean = !dismissed

    /** Which starter-prompt family fits a project, guessed from its marker files. */
    fun guessTemplate(files: List<String>): String = when {
        files.any { it.equals("ArabicImeService.kt", ignoreCase = true) } -> "IME"
        files.any { it.equals("MainActivity.kt", ignoreCase = true) } -> "COMPOSE"
        files.any { it.equals("main.py", ignoreCase = true) } -> "PYTHON"
        files.any { it.equals("index.js", ignoreCase = true) || it.equals("package.json", ignoreCase = true) } -> "NODE"
        else -> "WEB"
    }
}

/** Short Arabic starter commands the coach offers with a copy button — the «أمثلة أوامر» library. */
object StarterPrompts {

    /** Work for any project, in any template. */
    val universal = listOf(
        "اشرح لي ملفات هذا المشروع بإيجاز، ثم اقترح أول تحسين صغير.",
        "اقترح خطة عمل من ثلاث خطوات لهذا المشروع ثم نفّذ الخطوة الأولى.",
        "راجع الكود الحالي واذكر مشكلتين واقعيتين مع إصلاحهما.",
    )

    /** Per template id ([com.ctodrs.saqrcode.data.Template.name]). Arabic-first, concrete, small. */
    val byTemplate: Map<String, List<String>> = mapOf(
        "WEB" to listOf(
            "سوّ لي صفحة هبوط لمتجر قهوة عربي مع قسم لآراء العملاء.",
            "أضف نموذج تواصل يتحقق من البريد قبل الإرسال ويعرض رسالة نجاح.",
            "اجعل الصفحة متوافقة مع الجوال والاتجاه من اليمين لليسار.",
        ),
        "PYTHON" to listOf(
            "اكتب سكربت يرتّب ملفات مجلد حسب النوع ويطبع ملخصًا.",
            "أضف اختبارات وحدة لكل دالة في المشروع وشغّلها.",
            "حوّل السكربت إلى أداة سطر أوامر بـ argparse مع help بالعربية.",
        ),
        "NODE" to listOf(
            "ابنِ واجهة API صغيرة بـ Express ترجع قائمة مهام بصيغة JSON.",
            "أضف تحققًا من المدخلات ورسائل خطأ واضحة عند كل مسار.",
            "اربط قاعدة بيانات SQLite واحفظ المهام فيها.",
        ),
        "COMPOSE" to listOf(
            "سوّ لي شاشة عدّاد بزر يزيد وقيمة تُعرض بخط عربي جميل.",
            "أضف شاشة ثانية وانتقالًا إليها من الزر في الأسفل.",
            "احفظ قيمة العدّاد عند إغلاق التطبيق واسترجعها عند الفتح.",
        ),
        "IME" to listOf(
            "أضف شريط اقتراحات للكلمات الشائعة فوق اللوحة.",
            "اجعل ارتفاع اللوحة يتكيف مع وضع العرض العمودي والأفقي.",
            "أضف اهتزازًا خفيفًا عند الضغط على كل مفتاح.",
        ),
        "LITE_WEB" to listOf(
            "أضف صفحة ثانية وقائمة تنقّل بين الصفحتين.",
            "حسّن الخطوط والمسافات لتكون مقروءة على شاشة صغيرة.",
            "أضف زر مشاركة ينسخ رابط الصفحة.",
        ),
        "EMPTY" to listOf(
            "ابنِ هيكل مشروع بسيط: README وملف رئيسي ومجلد مصادر.",
            "اقترح خطة مشروع «قائمة مهام» وابدأ بتنفيذ أول خطوة.",
        ),
    )

    /** Template-specific examples first, topped up with universal ones, deduplicated, capped. */
    fun forTemplate(template: String, limit: Int = 4): List<String> =
        (byTemplate[template].orEmpty() + universal).distinct().take(limit.coerceAtLeast(1))
}
