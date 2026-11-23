package com.ctodrs.saqrcode.ondevice

import java.io.File

/**
 * v0.9 · إدارة مخزون البناء · Build-cache management for the on-device build tab.
 *
 * Answers «how much storage does each component take?» and «what can I reclaim?» without
 * touching the Android framework: the components are plain directories under the app's
 * filesDir (`linux/…`) plus downloaded artifacts and proot scratch space under cacheDir.
 * Reclaim deletes a component and drops the ready marker so the next `Prepare` re-fetches
 * exactly what is missing (finished steps are skipped by their own file checks).
 *
 * The size/format/resolve helpers are pure and unit-tested in V09FeaturesTest.
 */
object BuildCache {

    /** One reclaimable chunk of the build environment. */
    data class Entry(val id: String, val label: String, val dir: File) {
        val sizeBytes: Long get() = dirSize(dir)
    }

    /** Components in display order. [filesDir]/[cacheDir] are the app's own storage roots. */
    fun components(filesDir: File, cacheDir: File): List<Entry> = listOf(
        Entry("rootfs", "Debian rootfs", File(filesDir, "linux/debian")),
        Entry("sdk", "Android SDK", File(filesDir, "linux/android-sdk")),
        Entry("gradle", "Gradle", File(filesDir, "linux/gradle")),
        Entry("downloads", "Downloads (cache)", cacheDir),
        Entry("linux-home", "Linux runtime (Alpine)", File(filesDir, "linux/alpine")),
    )

    /** Recursive byte size of [dir]; symlinked directories are not followed. */
    fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            val listing = runCatching { f.listFiles() }.getOrNull()
            if (listing == null) {
                total += if (f.isFile) f.length() else 0L
            } else {
                listing.forEach { child ->
                    if (child.isDirectory && !isSymlink(child)) stack.addLast(child)
                    else if (child.isFile) total += child.length()
                }
            }
        }
        return total
    }

    private fun isSymlink(f: File): Boolean = runCatching {
        f.canonicalPath != f.absolutePath
    }.getOrDefault(false)

    /** 0 B / 512 KB / 3.4 MB / 1.2 GB — one decimal below 10, none above. */
    fun formatBytes(b: Long): String {
        if (b <= 0) return "0 B"
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> one(gb) + " GB"
            mb >= 1.0 -> one(mb) + " MB"
            kb >= 1.0 -> one(kb) + " KB"
            else -> "$b B"
        }
    }

    private fun one(v: Double): String {
        val r = Math.round(v * 10) / 10.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }

    /** Sum of every component — the headline number of the storage card. */
    fun total(entries: List<Entry>): Long = entries.sumOf { it.sizeBytes }
}
