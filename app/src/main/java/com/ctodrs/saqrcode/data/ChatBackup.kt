package com.ctodrs.saqrcode.data

import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/**
 * Wave D (0.1.33): encrypted backup of every chat session (and its attached images) to one file.
 * Same container as repository sync (AES-GCM, PBKDF2 passphrase, nothing stored), so a lost
 * phone does not lose the history. Restore is a merge: it never overwrites a session that is
 * as new or newer on this device, and it never leaves the chats directory. Plain JVM, unit-tested.
 */
object ChatBackup {
    const val KIND = "saqr-chats"
    const val VERSION = 1
    const val FILE_NAME = "saqr-chats.saqrbackup"
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif")

    data class Restored(val sessions: Int, val skipped: Int, val images: Int)

    class Counts(val projects: Int, val sessions: Int)

    /** The plain JSON for every project directory under [root] (filesDir/chats). */
    fun build(root: File, now: Long): String {
        val projects = JSONObject()
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            val sessions = JSONArray()
            dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") && it.name != "index.json" }?.sortedBy { it.name }?.forEach { f ->
                runCatching { sessions.put(JSONObject(f.readText())) }
            }
            val images = JSONObject()
            File(dir, "img").listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach {
                images.put(it.name, Base64.getEncoder().encodeToString(it.readBytes()))
            }
            if (sessions.length() > 0) projects.put(dir.name, JSONObject().put("sessions", sessions).put("images", images))
        }
        return JSONObject().put("kind", KIND).put("v", VERSION).put("createdAt", now).put("projects", projects).toString()
    }

    fun seal(root: File, passphrase: String, now: Long): String = SyncKit.encrypt(build(root, now), passphrase)

    /** Decrypts and merges [container] into [root]. Throws [SyncException] (`container`, `passphrase`, `short`). */
    fun restore(root: File, container: String, passphrase: String): Restored {
        val o = runCatching { JSONObject(SyncKit.decrypt(container, passphrase)) }.getOrElse {
            if (it is SyncException) throw it else throw SyncException("container")
        }
        if (o.optString("kind") != KIND || o.optInt("v", 0) != VERSION) throw SyncException("container")
        val projects = o.optJSONObject("projects") ?: throw SyncException("container")
        var added = 0; var skipped = 0; var imgs = 0
        for (name in projects.keys().asSequence().toList()) {
            val safe = File(name).name
            if (safe.isBlank() || safe == "." || safe == "..") continue
            val p = projects.getJSONObject(name)
            val dir = File(root, safe)
            val store = SessionStore(dir)
            val images = p.optJSONObject("images")
            if (images != null) {
                val imgDir = File(dir, "img").also { it.mkdirs() }
                for (n in images.keys().asSequence().toList()) {
                    val fn = File(n).name
                    if (fn.substringAfterLast('.', "").lowercase() !in IMAGE_EXT) continue
                    val f = File(imgDir, fn)
                    if (f.isFile) continue
                    val bytes = runCatching { Base64.getDecoder().decode(images.getString(n)) }.getOrNull() ?: continue
                    f.writeBytes(bytes); imgs++
                }
            }
            val sessions = p.optJSONArray("sessions") ?: continue
            for (i in 0 until sessions.length()) {
                val s = sessions.optJSONObject(i) ?: continue
                val meta = runCatching { SessionMeta.fromJson(s.getJSONObject("meta")) }.getOrNull() ?: continue
                val have = store.list().firstOrNull { it.id == meta.id }
                if (have != null && have.updatedAt >= meta.updatedAt) { skipped++; continue }
                store.save(s, makeLast = false)
                added++
            }
        }
        return Restored(added, skipped, imgs)
    }
}
