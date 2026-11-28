package com.ctodrs.saqrcode.cloud

import org.json.JSONObject

/** v0.1.80 · Live desktop on the cloud machine: the agent looks (text description), clicks, types, presses keys. */
interface DesktopCapable {
    suspend fun desktop(action: String, args: JSONObject): String
}

object CloudDesktop {
    const val W = 1024
    const val H = 768
    val ACTIONS = listOf("start", "look", "click", "type", "key")
    private val keyRe = Regex("^[A-Za-z0-9_+\\- ]{1,40}$")

    /** (path under /s/:id/, JSON body) or null when the arguments are not acceptable. */
    fun build(action: String, a: JSONObject): Pair<String, String?>? = when (action) {
        "start" -> "cu/start" to null
        "look" -> "cu/look" to JSONObject().put("ask", a.optString("ask").take(300)).toString()
        "click" -> {
            val x = a.optInt("x", -1); val y = a.optInt("y", -1)
            if (x !in 0 until W || y !in 0 until H) null
            else "cu/mouse/click" to JSONObject().put("x", x).put("y", y).put("button", if (a.optString("button") == "right") "right" else "left").put("double", a.optBoolean("double", false)).toString()
        }
        "type" -> a.optString("text").takeIf { it.isNotEmpty() && it.length <= 500 }?.let { "cu/keyboard/type" to JSONObject().put("text", it).put("delay", 10).toString() }
        "key" -> a.optString("key").takeIf { keyRe.matches(it) }?.let { "cu/keyboard/key" to JSONObject().put("key", it).toString() }
        else -> null
    }

    fun describe(action: String, response: JSONObject): String = when {
        response.has("error") -> "Error: " + response.optString("detail").ifBlank { response.optString("error") }
        action == "look" -> response.optString("description").ifBlank { "(nothing described)" } + "\n(Coordinates are estimates on a ${W}x$H screen; look again after each click.)"
        else -> "ok"
    }
}
