package com.ctodrs.saqrcode.agent

import java.io.File

/*
 * v0.1.13 project rules: the AGENTS.md convention as pure logic so it is unit-testable.
 * The root AGENTS.md always applies; when the session has touched files, the AGENTS.md of
 * each touched file's directory chain applies too (the convention coding agents follow).
 * Android wiring: SystemPrompt.build injects [collect]; IdeScreen edits the root file.
 */
object ProjectRules {
    const val FILE = "AGENTS.md"
    /** One file never swallows the prompt. */
    const val PER_FILE_CAP = 4_000
    /** All rule files together stay within this budget. */
    const val TOTAL_CAP = 8_000

    /**
     * The rule files that apply to this session: the project root's AGENTS.md first, then
     * the AGENTS.md of every directory on the path of a touched file (nearest first), each
     * labeled with its directory. Missing files are skipped; unreadable ones are skipped.
     */
    fun collect(projectDir: File, touched: Collection<String>): String {
        val root = projectDir.canonicalFile
        val rootPath = root.absolutePath
        val dirs = LinkedHashSet<File>()
        dirs += root
        for (t in touched) {
            var d = runCatching { File(root, t).canonicalFile.parentFile }.getOrNull()
            // only directories inside the project (a touched path may try to escape with ../)
            while (d != null && (d.absolutePath == rootPath || d.absolutePath.startsWith(rootPath + File.separator))) {
                dirs += d
                if (d == root) break
                d = d.parentFile
            }
        }
        val out = StringBuilder()
        for (dir in dirs) {
            val f = File(dir, FILE)
            if (!f.isFile) continue
            val text = runCatching { f.readText() }.getOrNull()?.trim()?.take(PER_FILE_CAP) ?: continue
            if (text.isEmpty()) continue
            if (out.isNotEmpty()) out.append('\n')
            if (dir != root) {
                val rel = dir.relativeTo(root).path
                out.append("— $rel/$FILE —\n")
            }
            if (out.length + text.length > TOTAL_CAP) break
            out.append(text).append('\n')
        }
        return out.toString().trim().take(TOTAL_CAP)
    }
}
