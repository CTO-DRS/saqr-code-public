package com.ctodrs.saqrcode.agent

import org.json.JSONObject
import java.io.File

/**
 * v0.7 · أدوات الجودة · Project quality checks: `lint_project` (fast syntax checks for JS /
 * Python / JSON / HTML) and `run_tests` (detects and runs the project's test runner).
 * Detection and pure checks are unit-tested in V07FeaturesTest; command execution goes
 * through [LinuxEnv.exec] in the session.
 */
object ProjectChecks {

    private val IGNORED_DIRS = setOf(".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "dist", ".saqr", ".idea")

    fun sourceFiles(root: File, exts: Set<String>, limit: Int = 40): List<File> {
        val out = ArrayList<File>()
        root.walkTopDown().onEnter { it == root || it.name !in IGNORED_DIRS }
            .filter { it.isFile && it.length() < 800_000 }
            .forEach { f -> if (out.size < limit && f.extension.lowercase() in exts) out += f }
        return out
    }

    // ------------------------------------------------------------- run_tests

    data class Runner(val label: String, val command: String)

    /** Detects the test runner(s) of the project from its files. Empty = no tests found. */
    fun detectTests(root: File): List<Runner> {
        val out = ArrayList<Runner>()
        val pkg = File(root, "package.json")
        if (pkg.isFile) {
            val test = runCatching { JSONObject(pkg.readText()).optJSONObject("scripts")?.optString("test").orEmpty() }.getOrDefault("")
            if (test.isNotBlank()) out += Runner("npm test", "npm test --silent")
        }
        if (File(root, "pytest.ini").isFile || File(root, "tests").isDirectory ||
            sourceFiles(root, setOf("py"), 60).any { it.name.endsWith("_test.py") || it.name.startsWith("test_") }
        ) out += Runner("pytest", "python3 -m pytest -q")
        if (out.none { it.label == "pytest" } && sourceFiles(root, setOf("py"), 60).isNotEmpty()) {
            out += Runner("unittest", "python3 -m unittest discover -s . -p 'test_*.py'")
        }
        if (sourceFiles(root, setOf("kt"), 60).any { it.name.matches(Regex(""".*Test\.kt""")) }) {
            out += Runner("kotlin tests", "find . -name '*Test.kt' -not -path '*/build/*' | head -5")
        }
        return out
    }

    // ------------------------------------------------------------- lint_project

    data class Issue(val file: String, val message: String)

    /** Shell commands for the terminal-based checks (node --check, py_compile). */
    fun lintCommands(root: File): List<Pair<String, String>> {
        val cmds = ArrayList<Pair<String, String>>()
        val js = sourceFiles(root, setOf("js", "mjs", "cjs"))
        if (js.isNotEmpty()) cmds += "js" to
            "find . -path ./node_modules -prune -o \\( -name '*.js' -o -name '*.mjs' -o -name '*.cjs' \\) -type f -print | head -40 | while read f; do node --check \"\$f\" 2>&1 || echo \"FAIL: \$f\"; done"
        val py = sourceFiles(root, setOf("py"))
        if (py.isNotEmpty()) cmds += "py" to
            "find . \\( -name node_modules -o -name .venv -o -name __pycache__ \\) -prune -o -name '*.py' -type f -print | head -40 | while read f; do python3 -m py_compile \"\$f\" 2>&1 || echo \"FAIL: \$f\"; done"
        return cmds
    }

    /** Pure JSON validation of the project's .json files (no terminal needed). */
    fun jsonIssues(root: File, limit: Int = 30): List<Issue> {
        val out = ArrayList<Issue>()
        for (f in sourceFiles(root, setOf("json"), limit)) {
            val r = runCatching { JSONObject(f.readText()) }
            r.exceptionOrNull()?.let { out += Issue(f.relativeTo(root).path, "invalid JSON: ${it.message?.take(120) ?: "parse error"}") }
        }
        return out
    }

    /** Pure HTML balance checks: matched tags for the common structural elements. */
    fun htmlIssues(root: File, limit: Int = 20): List<Issue> {
        val out = ArrayList<Issue>()
        val tags = listOf("html", "head", "body", "div", "script", "style", "main", "section", "form", "table", "ul", "ol")
        for (f in sourceFiles(root, setOf("html", "htm"), limit)) {
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            val lower = text.lowercase()
            for (t in tags) {
                val open = Regex("""<$t(\s|>)[^>]*>""").findAll(lower).count() +
                    Regex("""<$t\s+[^>]*/>""").findAll(lower).count() // self-closing counted separately
                val selfClosing = Regex("""<$t\s+[^>]*/>""").findAll(lower).count()
                val opens = Regex("""<$t(\s|>)""").findAll(lower).count() - selfClosing
                val closes = Regex("""</$t\s*>""").findAll(lower).count()
                if (opens > closes) out += Issue(f.relativeTo(root).path, "unclosed <$t> ($opens open vs $closes close)")
                else if (closes > opens) out += Issue(f.relativeTo(root).path, "extra </$t> ($closes close vs $opens open)")
            }
        }
        return out
    }

    /** Formats the lint result block for the tool output. */
    fun formatLint(cmdOutputs: List<Pair<String, String>>, pureIssues: List<Issue>): String = buildString {
        var fails = 0
        for ((kind, out) in cmdOutputs) {
            val clean = out.lines().filter { it.isNotBlank() && !it.contains("no such file") }
            appendLine("[$kind]")
            clean.take(30).forEach { l -> appendLine(l); if (l.startsWith("FAIL") || l.contains("Error", ignoreCase = true)) fails++ }
        }
        if (pureIssues.isNotEmpty()) {
            appendLine("[checks]")
            pureIssues.take(30).forEach { appendLine("FAIL: ${it.file}: ${it.message}"); fails++ }
        }
        appendLine(if (fails == 0) "Lint OK — no issues found." else "$fails issue(s) found. Fix them with edit_file.")
    }
}
