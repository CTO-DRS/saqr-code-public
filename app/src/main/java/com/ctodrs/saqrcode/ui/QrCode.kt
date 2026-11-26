package com.ctodrs.saqrcode.ui

/**
 * Minimal on-device QR encoder: byte mode, error correction M, versions 1..9 (up to 180 bytes), best of 8 masks.
 * Pure Kotlin, no dependencies. `encode` returns a square matrix where `true` is a dark module (no quiet zone).
 */
object QrCode {
    // Per version 1..9, level M: data codewords, ecc codewords per block, block count.
    private val DATA = intArrayOf(16, 28, 44, 64, 86, 108, 124, 154, 182)
    private val ECC = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22)
    private val BLOCKS = intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5)
    private val ALIGN = arrayOf(
        intArrayOf(), intArrayOf(6, 18), intArrayOf(6, 22), intArrayOf(6, 26), intArrayOf(6, 30),
        intArrayOf(6, 34), intArrayOf(6, 22, 38), intArrayOf(6, 24, 42), intArrayOf(6, 26, 46),
    )

    const val MAX_BYTES = 180

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "too long for QR (${bytes.size} bytes)" }
        val ver = (1..9).first { bytes.size <= DATA[it - 1] - 2 }
        val dataCw = DATA[ver - 1]
        val bits = ArrayList<Int>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 }
        put(0b0100, 4); put(bytes.size, 8); bytes.forEach { put(it.toInt() and 0xFF, 8) }
        put(0, minOf(4, dataCw * 8 - bits.size))
        while (bits.size % 8 != 0) bits += 0
        val cw = ArrayList<Int>()
        for (i in bits.indices step 8) { var b = 0; for (j in 0 until 8) b = (b shl 1) or bits[i + j]; cw += b }
        var pad = 0xEC
        while (cw.size < dataCw) { cw += pad; pad = pad xor 0xEC xor 0x11 }

        // split into blocks, add Reed-Solomon, interleave
        val nb = BLOCKS[ver - 1]; val eccLen = ECC[ver - 1]
        val short = dataCw / nb; val longCount = dataCw % nb
        val blocks = ArrayList<IntArray>(); val eccs = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until nb) {
            val len = short + if (i >= nb - longCount) 1 else 0
            val d = IntArray(len) { cw[k + it] }; k += len
            blocks += d; eccs += rs(d, eccLen)
        }
        val out = ArrayList<Int>()
        for (i in 0..short) for (b in blocks) if (i < b.size) out += b[i]
        for (i in 0 until eccLen) for (e in eccs) out += e[i]

        val size = 17 + 4 * ver
        val m = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) } // function modules
        fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }
        // finders + separators
        for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx; val y = cy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val d = maxOf(Math.abs(dx), Math.abs(dy))
                set(x, y, d != 2 && d != 4)
            }
        }
        // timing
        for (i in 8 until size - 8) { set(i, 6, i % 2 == 0); set(6, i, i % 2 == 0) }
        // alignment
        val ap = ALIGN[ver - 1]
        for (a in ap) for (b in ap) {
            if ((a == 6 && b == 6) || (a == 6 && b == ap.last()) || (a == ap.last() && b == 6)) continue
            for (dy in -2..2) for (dx in -2..2) set(a + dx, b + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        }
        // reserve format area + dark module
        for (i in 0..8) { fn[8][i] = true; fn[i][8] = true }
        for (i in 0 until 8) { fn[size - 1 - i][8] = true; fn[8][size - 1 - i] = true }
        set(8, size - 8, true)
        if (ver >= 7) {
            var rem = ver
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val v = (ver shl 12) or rem
            for (i in 0 until 18) {
                val bit = (v ushr i) and 1 == 1
                val a = size - 11 + i % 3; val b = i / 3
                set(a, b, bit); set(b, a, bit)
            }
        }
        // data placement
        var idx = 0
        var x = size - 1
        var up = true
        while (x > 0) {
            if (x == 6) x--
            for (n in 0 until size) {
                val y = if (up) size - 1 - n else n
                for (dx in 0..1) {
                    val xx = x - dx
                    if (!fn[y][xx]) {
                        m[y][xx] = idx < out.size * 8 && ((out[idx ushr 3] ushr (7 - (idx and 7))) and 1) == 1
                        idx++
                    }
                }
            }
            up = !up; x -= 2
        }
        // choose mask
        var best = -1; var bestPen = Int.MAX_VALUE; var bestM: Array<BooleanArray>? = null
        for (mask in 0 until 8) {
            val t = Array(size) { m[it].clone() }
            for (y in 0 until size) for (xx in 0 until size) if (!fn[y][xx] && maskBit(mask, xx, y)) t[y][xx] = !t[y][xx]
            drawFormat(t, size, mask)
            val p = penalty(t, size)
            if (p < bestPen) { bestPen = p; best = mask; bestM = t }
        }
        return bestM!!
    }

    private fun maskBit(mask: Int, x: Int, y: Int) = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0
        5 -> x * y % 2 + x * y % 3 == 0
        6 -> (x * y % 2 + x * y % 3) % 2 == 0
        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
    }

    private fun drawFormat(t: Array<BooleanArray>, size: Int, mask: Int) {
        val data = (0 shl 3) or mask // level M = 00
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412
        fun b(i: Int) = (bits ushr i) and 1 == 1
        for (i in 0..5) t[i][8] = b(i)
        t[7][8] = b(6); t[8][8] = b(7); t[8][7] = b(8)
        for (i in 9..14) t[8][14 - i] = b(i)
        for (i in 0..7) t[8][size - 1 - i] = b(i)
        for (i in 8..14) t[size - 15 + i][8] = b(i)
        t[size - 8][8] = true
    }

    private fun penalty(t: Array<BooleanArray>, size: Int): Int {
        var p = 0
        for (pass in 0..1) for (a in 0 until size) {
            var run = 1
            for (b in 1 until size) {
                val cur = if (pass == 0) t[a][b] else t[b][a]
                val prev = if (pass == 0) t[a][b - 1] else t[b - 1][a]
                if (cur == prev) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else run = 1
            }
        }
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = t[y][x]
            if (c == t[y][x + 1] && c == t[y + 1][x] && c == t[y + 1][x + 1]) p += 3
        }
        val pat1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val pat2 = pat1.reversedArray()
        for (pass in 0..1) for (a in 0 until size) for (s in 0..size - 11) {
            var m1 = true; var m2 = true
            for (i in 0 until 11) {
                val v = if (pass == 0) t[a][s + i] else t[s + i][a]
                if (v != pat1[i]) m1 = false
                if (v != pat2[i]) m2 = false
            }
            if (m1) p += 40
            if (m2) p += 40
        }
        var dark = 0
        for (r in t) for (v in r) if (v) dark++
        val total = size * size
        p += (((Math.abs(dark * 20 - total * 10) + total - 1) / total) - 1).coerceAtLeast(0) * 10
        return p
    }

    private val EXP = IntArray(512).also { e ->
        var x = 1
        for (i in 0 until 255) { e[i] = x; x = x shl 1; if (x >= 256) x = x xor 0x11D }
        for (i in 255 until 512) e[i] = e[i - 255]
    }
    private val LOG = IntArray(256).also { l -> for (i in 0 until 255) l[EXP[i]] = i }
    private fun mul(a: Int, b: Int) = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    private fun rs(data: IntArray, n: Int): IntArray {
        var gen = intArrayOf(1)
        for (i in 0 until n) {
            val next = IntArray(gen.size + 1)
            for (j in gen.indices) { next[j] = next[j] xor gen[j]; next[j + 1] = next[j + 1] xor mul(gen[j], EXP[i]) }
            gen = next
        }
        val res = IntArray(n)
        for (d in data) {
            val f = d xor res[0]
            for (i in 0 until n - 1) res[i] = res[i + 1]
            res[n - 1] = 0
            for (i in 0 until n) res[i] = res[i] xor mul(gen[i + 1], f)
        }
        return res
    }
}
