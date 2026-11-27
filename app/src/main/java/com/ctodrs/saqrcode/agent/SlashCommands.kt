package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Slash commands typed at the start of the chat input ("/fix", "/اشرح why is this slow").
 * A command either expands to a prompt template sent to the agent, or runs a local action
 * (/clear, /model). Templates may use {file}, {selection}, {args} and {lang}.
 */
data class SlashCommand(
    val name: String,
    val template: String,
    val aliases: List<String> = emptyList(),
    val action: Action = Action.PROMPT,
    val builtin: Boolean = false,
) {
    enum class Action { PROMPT, CLEAR, MODEL, COMPARE }
}

/** Context available when a command is expanded. */
data class SlashContext(
    val currentFile: String? = null,
    val selection: String? = null,
    /** The UI is in Arabic: replies to /explain default to Arabic. */
    val arabicUi: Boolean = false,
)

data class SlashInvocation(val command: SlashCommand, val args: String, val typed: String) {
    /** Invoked through an Arabic alias such as /اشرح. */
    val arabicAlias get() = typed.any { it in '\u0600'..'\u06FF' }
}

object SlashCommands {
    const val FIX = "fix"
    const val EXPLAIN = "explain"
    const val TEST = "test"
    const val REVIEW = "review"
    const val PLAN = "plan"
    const val CLEAR = "clear"
    const val MODEL = "model"
    const val COMMIT = "commit"
    const val REFACTOR = "refactor"
    const val DOCS = "document"
    const val PERF = "perf"
    const val SECURE = "secure"
    const val DEBUG = "debug"
    const val COMPARE = "compare"
    const val LEARN = "learn"

    private const val MAX_SELECTION = 6000

    val builtins: List<SlashCommand> = listOf(
        SlashCommand(FIX, "Fix the errors in {file}. Read it first, find the compile, runtime or lint errors (run the build or tests when practical), fix them with minimal edits, then verify the fix.{args}",
            listOf("اصلح"), builtin = true),
        SlashCommand(EXPLAIN, "Explain {target} {lang}. Cover what it does, how the main parts fit together, and anything non-obvious or risky. Read the code first. Do not modify any files.{args}",
            listOf("اشرح"), builtin = true),
        SlashCommand(LEARN, "Teach me {target} {lang}. ${LearningMode.LESSON} Read the code first. Do not modify any files.{args}",
            listOf("علمني"), builtin = true),
        SlashCommand(TEST, "Write unit tests for {file}. Use the project's existing test framework and conventions (or the standard one for the language if there is none), cover normal and edge cases, put the tests where the project keeps them, then run them and fix any failures.{args}",
            listOf("اختبر"), builtin = true),
        SlashCommand(REVIEW, "Review the current uncommitted changes. Call git_status and git_diff first, then give a concise code review: bugs, risks, missing tests and style issues, ordered by severity, with file:line references. Do not modify any files.{args}",
            listOf("راجع"), builtin = true),
        SlashCommand(PLAN, "Produce a plan only, no edits, for: {task}. Explore the code read-only as needed, then call update_plan with the steps and summarize the plan with the files each step touches. Do NOT create, edit or delete files and do not run commands that change anything.",
            listOf("خطة"), builtin = true),
        SlashCommand(COMMIT, "Commit the current changes. Call git_status and git_diff first, write a concise conventional commit message (imperative subject of at most 72 characters, optional short body), then call git_commit with it. The user approves the commit before it is made.{args}",
            builtin = true),
        SlashCommand(REFACTOR, "Refactor {target} for readability and maintainability without changing behavior. Read it first, list the problems you will fix (duplication, long functions, unclear names, dead code), make small safe edits, then run the build or tests to confirm nothing changed.{args}",
            listOf("حسن"), builtin = true),
        SlashCommand(DOCS, "Write documentation for {target}. Add clear doc comments to public functions and classes, and update or create a short README section if one is missing. Match the project's existing comment style. Do not change any logic.{args}",
            listOf("وثق"), builtin = true),
        SlashCommand(PERF, "Find performance problems in {target}. Read the code, point out the slowest or most wasteful parts (loops, repeated work, blocking calls, memory), fix the safe ones with minimal edits, and explain the expected gain of each.{args}",
            listOf("اداء"), builtin = true),
        SlashCommand(SECURE, "Do a security review of {target}. Look for hard-coded secrets, injection, unsafe file or network access, missing input checks and weak storage. List findings by severity with file:line, then fix the clear ones with minimal edits and say what is left for me to decide.{args}",
            listOf("امن"), builtin = true),
        SlashCommand(DEBUG, "Help me debug: {task}. Reproduce or locate the problem first (read the code, run the tests or the app's build, check logs), state the most likely cause with evidence, then fix it with a minimal edit and verify.{args}",
            listOf("تتبع"), builtin = true),
        SlashCommand(CLEAR, "", action = SlashCommand.Action.CLEAR, builtin = true),
        SlashCommand(MODEL, "", action = SlashCommand.Action.MODEL, builtin = true),
        SlashCommand(COMPARE, "", listOf("قارن"), action = SlashCommand.Action.COMPARE, builtin = true),
    )

    private val NAME = Regex("^[\\p{L}\\p{N}_-]{1,32}$")

    /** Arabic-insensitive key: lowercase, alef forms unified, tatweel and diacritics dropped. */
    fun normalize(s: String): String = buildString {
        s.lowercase().forEach { ch ->
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                'ى' -> append('ي')
                'ـ' -> Unit
                in '\u064B'..'\u0652' -> Unit
                else -> append(ch)
            }
        }
    }

    fun all(custom: List<SlashCommand>): List<SlashCommand> =
        builtins + custom.filter { c -> builtins.none { b -> b.matches(c.name) } }

    private fun SlashCommand.keys() = (listOf(name) + aliases).map { normalize(it) }
    private fun SlashCommand.matches(token: String) = normalize(token) in keys()

    /** True when [input] is a command in progress: "/" first and no whitespace yet. */
    fun isTyping(input: String) = input.startsWith("/") && input.none { it.isWhitespace() }

    /** Commands to show in the popup for what has been typed so far (empty when not typing one). */
    fun suggestions(input: String, custom: List<SlashCommand> = emptyList()): List<SlashCommand> {
        if (!isTyping(input)) return emptyList()
        val prefix = normalize(input.drop(1))
        return all(custom).filter { c -> c.keys().any { it.startsWith(prefix) } }
    }

    /** Parse "/name args…" into a known command, or null (the text is then sent as a normal message). */
    fun parse(input: String, custom: List<SlashCommand> = emptyList()): SlashInvocation? {
        val t = input.trimStart()
        if (!t.startsWith("/") || t.length < 2) return null
        val token = t.drop(1).takeWhile { !it.isWhitespace() }
        if (token.isEmpty()) return null
        val cmd = all(custom).firstOrNull { it.matches(token) } ?: return null
        return SlashInvocation(cmd, t.drop(1 + token.length).trim(), token)
    }

    /** The prompt sent to the agent for a PROMPT command. */
    fun expand(inv: SlashInvocation, ctx: SlashContext): String {
        val file = ctx.currentFile?.takeIf { it.isNotBlank() }
        val sel = ctx.selection?.takeIf { it.isNotBlank() }?.take(MAX_SELECTION)
        val fileRef = if (file != null) "@$file" else "the file I'm currently working on (ask me which one if it isn't clear)"
        val target = when {
            sel != null && file != null -> "this selection from @$file:\n```\n$sel\n```\n"
            sel != null -> "this code:\n```\n$sel\n```\n"
            else -> fileRef
        }
        val arabic = inv.arabicAlias || ctx.arabicUi
        val lang = if (arabic) "in Arabic (clear Modern Standard Arabic)" else "in the language I write in"
        val args = inv.args
        val c = inv.command
        var out = c.template
        val hadArgs = out.contains("{args}")
        out = out
            .replace("{file}", if (c.builtin && c.name == FIX && file == null) "the project (build it or run the tests to find the errors)" else fileRef)
            .replace("{target}", target)
            .replace("{selection}", sel ?: "")
            .replace("{lang}", lang)
            .replace("{task}", args.ifBlank { "the task we have been discussing" })
            .replace("{args}", if (args.isNotBlank() && c.builtin) "\n\nAdditional instructions: $args" else args)
        if (!hadArgs && !c.builtin && args.isNotBlank()) out = out.trimEnd() + "\n\n" + args
        if (inv.arabicAlias && c.name != EXPLAIN) out = out.trimEnd() + "\n\nReply in Arabic."
        return out.trim()
    }

    /** Validation for a custom command; returns an error key or null when valid. */
    fun validate(name: String, template: String, existing: List<SlashCommand>): String? {
        val n = name.trim().removePrefix("/")
        return when {
            !NAME.matches(n) -> "name"
            builtins.any { it.matches(n) } -> "builtin"
            existing.any { normalize(it.name) == normalize(n) } -> "duplicate"
            template.isBlank() -> "template"
            else -> null
        }
    }

    fun encode(custom: List<SlashCommand>): String = JSONArray().apply {
        custom.forEach { put(JSONObject().put("name", it.name).put("template", it.template)) }
    }.toString()

    fun decode(json: String?): List<SlashCommand> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val n = o.optString("name").trim().removePrefix("/")
            val t = o.optString("template")
            if (!NAME.matches(n) || t.isBlank()) null else SlashCommand(n, t)
        }
    }.getOrDefault(emptyList())
}

/** Prompt library: the searchable list behind the "Prompt library" sheet. */
object PromptLibrary {
    /** Commands that fill the composer; the local actions (/clear, /model) are not prompts. */
    fun prompts(custom: List<SlashCommand>): List<SlashCommand> =
        SlashCommands.all(custom).filter { it.action == SlashCommand.Action.PROMPT }

    /** Name, alias and description match, Arabic-insensitive; blank query returns everything. */
    fun filter(cmds: List<SlashCommand>, query: String, describe: (SlashCommand) -> String): List<SlashCommand> {
        val q = SlashCommands.normalize(query.trim().removePrefix("/"))
        if (q.isEmpty()) return cmds
        return cmds.filter { c ->
            (listOf(c.name) + c.aliases + describe(c)).any { SlashCommands.normalize(it).contains(q) }
        }
    }

    /** Text placed in the composer when a prompt is picked: the command, ready for arguments. */
    fun insertion(c: SlashCommand) = "/${c.name} "
}
