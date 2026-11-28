package com.ctodrs.saqrcode.cloud

import com.ctodrs.saqrcode.data.KeyValue

/**
 * v0.1.78 · Cloud secrets. Named values the user saves once (API keys, deploy tokens). They are sent only
 * to the user's own cloud machine, as environment variables for cloud_run and cloud_test. They are never
 * uploaded with the project, never shown to the model (it can only see names), and always redacted from output.
 */
object ProjectSecrets {
    private val nameRe = Regex("^[A-Z_][A-Z0-9_]{0,63}$")
    fun validName(n: String) = nameRe.matches(n)

    /** `export A='x'; export B='y'; ` with single-quote escaping, or "" when empty. */
    fun envPrefix(env: Map<String, String>): String =
        env.entries.filter { validName(it.key) }.joinToString("") { "export ${it.key}='" + it.value.replace("'", "'\\''") + "'; " }

    /** Hides every secret value of 4+ characters. Longest first so a value that contains another is hidden whole. */
    fun redact(text: String, env: Map<String, String>): String {
        var out = text
        env.entries.filter { it.value.length >= 4 }.sortedByDescending { it.value.length }.forEach { out = out.replace(it.value, "***${it.key}***") }
        return out
    }
}

class ProjectSecretStore(private val kv: KeyValue) {
    private fun names(): List<String> = (kv.get(INDEX) ?: "").split(",").filter { ProjectSecrets.validName(it) }

    fun list(): List<String> = names().sorted()

    fun put(name: String, value: String): Boolean {
        if (!ProjectSecrets.validName(name) || value.isBlank() || value.length > 4000) return false
        kv.put(PREFIX + name, value)
        kv.put(INDEX, (names() + name).distinct().joinToString(","))
        return true
    }

    fun remove(name: String) {
        kv.put(PREFIX + name, null)
        kv.put(INDEX, names().filter { it != name }.joinToString(",").ifEmpty { null })
    }

    fun all(): Map<String, String> = names().mapNotNull { n -> kv.get(PREFIX + n)?.let { n to it } }.toMap()

    private companion object { const val INDEX = "csecrets_index"; const val PREFIX = "csecret:" }
}
