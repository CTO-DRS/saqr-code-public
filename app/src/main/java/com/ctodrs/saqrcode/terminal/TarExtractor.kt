package com.ctodrs.saqrcode.terminal

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

/** Minimal tar(.gz) extractor: ustar, GNU long names, pax path/linkpath, files, dirs, symlinks, hard links. */
object TarExtractor {

    fun extractGz(input: InputStream, dest: File, onEntry: (String) -> Unit = {}) =
        extract(GZIPInputStream(BufferedInputStream(input), 64 * 1024), dest, onEntry)

    fun extract(input: InputStream, dest: File, onEntry: (String) -> Unit = {}) {
        dest.mkdirs()
        val destCanon = dest.canonicalPath
        val header = ByteArray(512)
        var longName: String? = null
        var longLink: String? = null
        val hardLinks = ArrayList<Pair<File, String>>()
        while (true) {
            if (!readFully(input, header)) break
            if (header.all { it == 0.toByte() }) break
            var name = str(header, 0, 100)
            val prefix = str(header, 345, 155)
            if (prefix.isNotEmpty() && header[257] == 'u'.code.toByte()) name = "$prefix/$name"
            val mode = oct(header, 100, 8).toInt()
            val size = oct(header, 124, 12)
            val type = header[156].toInt().toChar()
            var link = str(header, 157, 100)
            longName?.let { name = it; longName = null }
            longLink?.let { link = it; longLink = null }

            when (type) {
                'L' -> { longName = String(readBytes(input, size), Charsets.UTF_8).trimEnd('\u0000'); skipPad(input, size); continue }
                'K' -> { longLink = String(readBytes(input, size), Charsets.UTF_8).trimEnd('\u0000'); skipPad(input, size); continue }
                'x' -> {
                    val pax = parsePax(String(readBytes(input, size), Charsets.UTF_8)); skipPad(input, size)
                    pax["path"]?.let { longName = it }; pax["linkpath"]?.let { longLink = it }
                    continue
                }
                'g' -> { skip(input, size); skipPad(input, size); continue }
            }

            name = name.removePrefix("./")
            if (name.isEmpty() || name == ".") { skip(input, size); skipPad(input, size); continue }
            val out = File(dest, name)
            if (!out.canonicalPath.startsWith(destCanon) && type != '2') throw IOException("Unsafe tar path: $name")
            onEntry(name)
            when (type) {
                '5' -> out.mkdirs()
                '2' -> {
                    out.parentFile?.mkdirs()
                    out.delete()
                    runCatching { Files.createSymbolicLink(Paths.get(out.path), Paths.get(link)) }
                }
                '1' -> hardLinks += out to link
                '0', '\u0000', '7' -> {
                    out.parentFile?.mkdirs()
                    if (Files.isSymbolicLink(out.toPath())) out.delete()
                    out.outputStream().use { os ->
                        var remaining = size
                        val buf = ByteArray(64 * 1024)
                        while (remaining > 0) {
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n < 0) throw IOException("Truncated tar")
                            os.write(buf, 0, n); remaining -= n
                        }
                    }
                    skipPad(input, size)
                    applyMode(out, mode)
                    continue
                }
                else -> {}
            }
            skip(input, size); skipPad(input, size)
        }
        for ((out, target) in hardLinks) {
            val src = File(dest, target.removePrefix("./"))
            if (src.exists()) { out.parentFile?.mkdirs(); src.copyTo(out, overwrite = true); out.setExecutable(src.canExecute(), false) }
        }
    }

    private fun applyMode(f: File, mode: Int) {
        if (mode and 0b001_001_001 != 0) f.setExecutable(true, false)
        f.setReadable(true, mode and 0b000_000_100 == 0)
    }

    private fun parsePax(s: String): Map<String, String> {
        val m = HashMap<String, String>()
        s.lineSequence().forEach { line ->
            val sp = line.indexOf(' '); val eq = line.indexOf('=')
            if (sp in 0 until eq) m[line.substring(sp + 1, eq)] = line.substring(eq + 1)
        }
        return m
    }

    private fun str(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun oct(b: ByteArray, off: Int, len: Int): Long {
        if (b[off].toInt() and 0x80 != 0) { // base-256
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (b[i].toLong() and 0xff)
            return v
        }
        val s = str(b, off, len).trim().trim('\u0000')
        return if (s.isEmpty()) 0 else s.toLong(8)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) return if (read == 0) false else throw IOException("Truncated header")
            read += n
        }
        return true
    }

    private fun readBytes(input: InputStream, size: Long): ByteArray {
        val b = ByteArray(size.toInt()); readFully(input, b); return b
    }

    private fun skip(input: InputStream, n: Long) {
        var left = n
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) break
            left -= r
        }
    }

    private fun skipPad(input: InputStream, size: Long) {
        val pad = (512 - (size % 512)) % 512
        skip(input, pad)
    }
}
