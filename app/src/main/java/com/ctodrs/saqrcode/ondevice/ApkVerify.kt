package com.ctodrs.saqrcode.ondevice

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * v0.10 · التحقق من توقيع الـ APK داخل التطبيق · In-app verification of a built APK's signature.
 *
 * Implements APK Signature Scheme v2 and v3 verification without any external tool (no
 * apksigner download): the APK Signing Block is located before the ZIP Central Directory, the
 * signature over `signed-data` is checked with the embedded public key, and the chunked
 * content digests (1 MB chunks, 0xa5/0x5a prefixes — the exact wire format Android itself
 * verifies) are recomputed over the ZIP sections with the EoCD's Central-Directory offset
 * patched to the start of the signing block. On success the signer certificate is exposed
 * (SHA-256 fingerprint, subject, expiry) so the user can confirm it is *their* key before
 * tapping Install.
 *
 * Byte-level details follow the Android platform verifier (apksig):
 *  - signature algorithms 0x0101–0x0104 (RSA PSS / PKCS1), 0x0201–0x0202 (ECDSA), 0x0301 (DSA);
 *  - chunk digest = hash(0xa5 ‖ uint32LE(chunkLen) ‖ chunk); chunks never span the three ZIP
 *    sections (contents-of-entries / Central Directory / patched EoCD), empty sections produce
 *    no chunks;
 *  - final digest = hash(0x5a ‖ uint32LE(totalChunkCount) ‖ all chunk digests in file order);
 *  - signatures cover the signed-data payload *without* its length prefix;
 *  - the digested EoCD carries the Central-Directory offset of the *signing-block start*.
 *
 * Pure parts are unit-tested in V010FeaturesTest with a synthetic APK signed on the JVM.
 */
object ApkVerify {

    // APK Signing Block ids (V2SchemeConstants / V3SchemeConstants on the platform).
    // 0xf05368c0 overflows Int, so it needs an explicit narrowing to the Int used on the wire.
    const val BLOCK_ID_V2 = 0x7109871a
    val BLOCK_ID_V3: Int = 0xf05368c0.toInt()
    val BLOCK_MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    /** Result of [verify] — [ok] means signature *and* content digests checked out. */
    data class Result(
        val ok: Boolean,
        val error: String? = null,
        val scheme: String? = null,
        val certSha256: String? = null,
        val subject: String? = null,
        val validUntil: String? = null,
    ) {
        /** Cert fingerprint as the classic spaced hex pairs. */
        val fingerprintLine: String?
            get() = certSha256?.chunked(2)?.joinToString(" ") { it.uppercase() }
    }

    fun verify(apk: File): Result = try {
        verify(apk.readBytes())
    } catch (e: Exception) {
        Result(false, e.message ?: e.javaClass.simpleName)
    }

    // ------------------------------------------------------------ verification

    fun verify(apk: ByteArray): Result {
        // 1. ZIP End of Central Directory — the last plausible 0x06054b50 record in the file.
        val eocdOffset = findEocd(apk) ?: return Result(false, "not a ZIP/APK file (no EOCD record)")
        val cdOffset = uint32LE(apk, eocdOffset + 16)
        val cdSize = uint32LE(apk, eocdOffset + 12)
        if (cdOffset <= 0L || cdOffset + cdSize != eocdOffset.toLong()) {
            return Result(false, "ZIP layout not supported (CD at $cdOffset, size $cdSize, EoCD at $eocdOffset)")
        }

        // 2. APK Signing Block — its 24-byte footer sits right before the Central Directory.
        if (cdOffset < 32) return Result(false, "no APK Signing Block (unprotected APK?)")
        val footer = apk.copyOfRange((cdOffset - 24).toInt(), cdOffset.toInt())
        if (!endsWithMagic(footer)) return Result(false, "no APK Signing Block (unprotected APK?)")
        val blockSize = uint64LE(footer, 0)
        if (blockSize < 24 || cdOffset - (blockSize + 8) < 0) return Result(false, "APK Signing Block size is invalid")
        val blockOffset = cdOffset - (blockSize + 8)

        // 3. ID-value pairs inside the block → v2 / v3 payloads; v3 wins when both exist.
        val pairsFrom = blockOffset + 8
        val pairsTo = cdOffset - 24
        val v2 = findBlock(apk, pairsFrom, pairsTo, BLOCK_ID_V2)
        val v3 = findBlock(apk, pairsFrom, pairsTo, BLOCK_ID_V3)
        val candidates = listOfNotNull(
            v3?.let { "v3" to it },
            v2?.let { "v2" to it },
        )
        var lastError: String? = null
        for ((scheme, block) in candidates) {
            val r = verifyScheme(apk, block, blockOffset, cdOffset, cdSize, eocdOffset, scheme)
            if (r.ok) return r else lastError = r.error
        }
        return Result(false, lastError ?: "no v2/v3 signature found (v1-only APK?)")
    }

    /** Verifies one scheme block: signatures over signed-data, then the chunked content digest. */
    private fun verifyScheme(
        apk: ByteArray,
        block: ByteBuffer,
        blockOffset: Long,
        cdOffset: Long,
        cdSize: Long,
        eocdOffset: Int,
        scheme: String,
    ): Result {
        return try {
            // The block value is a length-prefixed sequence of length-prefixed signers.
            val seq = lenPrefixed(block) ?: return Result(false, "$scheme: truncated signers sequence")
            var lastError: String? = null
            while (seq.hasRemaining()) {
                val signer = lenPrefixed(seq) ?: return Result(false, "$scheme: truncated signer list")
                val r = verifySigner(signer, apk, blockOffset, cdOffset, cdSize, eocdOffset, scheme)
                if (r.ok) return r
                lastError = r.error
            }
            Result(false, lastError ?: "$scheme: no usable signer")
        } catch (e: Exception) {
            Result(false, "$scheme: " + (e.message ?: e.javaClass.simpleName) + " [" + e.javaClass.simpleName + "]")
        }
    }

    private fun verifySigner(
        signer: ByteBuffer,
        apk: ByteArray,
        blockOffset: Long,
        cdOffset: Long,
        cdSize: Long,
        eocdOffset: Int,
        scheme: String,
    ): Result {
        val signedData = lenPrefixed(signer) ?: return Result(false, "$scheme: truncated signed-data")
        // v3 signer = signed-data ‖ minSdk ‖ maxSdk ‖ signatures ‖ publicKey (after the prefixes).
        if (scheme == "v3") {
            signer.int; signer.int // min/max SDK versions — parsed but not enforced here
        }
        val signatures = lenPrefixed(signer) ?: return Result(false, "$scheme: truncated signatures")
        val publicKeyBytes = lenPrefixedBytes(signer) ?: return Result(false, "$scheme: truncated public key")

        // First supported signature that verifies over the signed-data payload (no length prefix).
        var verifiedAlgo = -1
        while (signatures.hasRemaining()) {
            val s = lenPrefixed(signatures) ?: break
            val algoId = s.int
            // the signature itself is length-prefixed inside the entry
            val sigBytes = lenPrefixedBytes(s) ?: return Result(false, "$scheme: truncated signature")
            val algo = byId(algoId) ?: continue
            val publicKey: PublicKey = try {
                KeyFactory.getInstance(algo.keyAlg).generatePublic(X509EncodedKeySpec(publicKeyBytes))
            } catch (e: Exception) {
                return Result(false, "$scheme: public key unreadable (${e.javaClass.simpleName})")
            }
            val ok = try {
                val sig = Signature.getInstance(algo.jca)
                algo.pss?.let { sig.setParameter(it) }
                sig.initVerify(publicKey)
                val payload = signedData.duplicate(); payload.position(0)
                sig.update(payload)
                sig.verify(sigBytes)
            } catch (e: Exception) {
                false
            }
            if (ok) { verifiedAlgo = algoId; break }
        }
        if (verifiedAlgo < 0) return Result(false, "$scheme: signature did not verify with the embedded key")

        // signed-data → digests (for the verified algorithm) + certificates.
        signedData.position(0)
        val digestsSeq = lenPrefixed(signedData) ?: return Result(false, "$scheme: truncated digests")
        val expectedDigest = digestFor(digestsSeq, verifiedAlgo)
            ?: return Result(false, "$scheme: no digest for the verified signature algorithm")
        val certsSeq = lenPrefixed(signedData) ?: return Result(false, "$scheme: truncated certificates")
        val cert = firstCert(certsSeq) ?: return Result(false, "$scheme: no certificate in signed-data")

        // Content digest over the three ZIP sections (EoCD patched to the signing-block start).
        val algo = byId(verifiedAlgo)!!
        val actual = contentDigest(apk, blockOffset, cdOffset, cdSize, eocdOffset, algo.digest)
            ?: return Result(false, "$scheme: failed to compute the APK content digest")
        if (!actual.contentEquals(expectedDigest)) {
            return Result(false, "$scheme: APK contents do NOT match the signed digest — the file was modified")
        }
        return Result(
            ok = true,
            scheme = scheme,
            certSha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded).toHex(),
            subject = cert.subjectX500Principal.name,
            validUntil = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(cert.notAfter),
        )
    }

    /** digests sequence: entries are len-prefixed { uint32 sigAlgoId, len-prefixed digest }. */
    private fun digestFor(seq: ByteBuffer, algoId: Int): ByteArray? {
        while (seq.hasRemaining()) {
            val e = lenPrefixed(seq) ?: break
            val id = e.int
            val d = lenPrefixedBytes(e) ?: return null
            if (id == algoId) return d
        }
        return null
    }

    private fun firstCert(seq: ByteBuffer): X509Certificate? {
        val factory = CertificateFactory.getInstance("X.509")
        while (seq.hasRemaining()) {
            val der = lenPrefixedBytes(seq) ?: return null
            return factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        }
        return null
    }

    // ------------------------------------------------------------ content digest (1 MB chunks)

    private const val CHUNK = 1024L * 1024L

    private fun contentDigest(
        apk: ByteArray,
        blockOffset: Long,
        cdOffset: Long,
        cdSize: Long,
        eocdOffset: Int,
        hashAlg: String,
    ): ByteArray? {
        // The digested EoCD carries the Central-Directory offset of the signing-block start.
        val eocdBytes = apk.copyOfRange(eocdOffset, apk.size)
        putUint32LE(eocdBytes, 16, blockOffset)
        val sections = listOf(
            longArrayOf(0, blockOffset),
            longArrayOf(cdOffset, cdSize),
            longArrayOf(eocdOffset.toLong(), eocdBytes.size.toLong()),
        )

        var totalCount = 0L
        for (s in sections) if (s[1] > 0) totalCount += (s[1] + CHUNK - 1) / CHUNK
        if (totalCount > Int.MAX_VALUE / 8) return null

        val md = MessageDigest.getInstance(hashAlg)
        val chunkDigests = ArrayList<ByteArray>()
        for (s in sections) {
            if (s[1] <= 0) continue
            val isEocd = s[0] == eocdOffset.toLong()
            // the EoCD section is a standalone byte array — offsets inside it start at 0
            val data = if (isEocd) eocdBytes else apk
            var off = if (isEocd) 0L else s[0]
            var remaining = s[1]
            while (remaining > 0) {
                val size = minOf(remaining, CHUNK).toInt()
                md.update(0xa5.toByte())
                val lenBuf = ByteArray(4)
                putUint32LE(lenBuf, 0, size.toLong())
                md.update(lenBuf)
                md.update(data, off.toInt(), size)
                chunkDigests.add(md.digest())
                off += size
                remaining -= size
            }
        }
        md.update(0x5a.toByte())
        val countBuf = ByteArray(4)
        putUint32LE(countBuf, 0, totalCount)
        md.update(countBuf)
        chunkDigests.forEach { md.update(it) }
        return md.digest()
    }

    // ------------------------------------------------------------ parsing helpers

    /** Last `PK·05·06` in the file that is followed exactly by the declared comment → offset. */
    private fun findEocd(apk: ByteArray): Int? {
        if (apk.size < 22) return null
        val lo = maxOf(0, apk.size - 0x10000 - 22)
        var i = apk.size - 22
        while (i >= lo) {
            if (apk[i] == 0x50.toByte() && apk[i + 1] == 0x4b.toByte() &&
                apk[i + 2] == 0x05.toByte() && apk[i + 3] == 0x06.toByte()
            ) {
                val commentLen = uint16LE(apk, i + 20)
                if (i + 22 + commentLen == apk.size) return i
            }
            i--
        }
        return null
    }

    private fun endsWithMagic(footer: ByteArray): Boolean {
        for (i in 0 until 16) if (footer[8 + i] != BLOCK_MAGIC[i]) return false
        return true
    }

    /** ID-value pair scan between [from] and [to]; a read-only view of the value bytes for [id]. */
    private fun findBlock(apk: ByteArray, from: Long, to: Long, id: Int): ByteBuffer? {
        var pos = from
        while (pos + 12 <= to) {
            val len = uint64LE(apk, pos.toInt())
            if (len < 4 || pos + 8 + len > to) return null
            if (readIntLE(apk, (pos + 8).toInt()) == id) {
                return ByteBuffer.wrap(apk, (pos + 12).toInt(), (len - 4).toInt())
                    .slice().order(ByteOrder.LITTLE_ENDIAN)
            }
            pos += 8 + len
        }
        return null
    }

    /** One length-prefixed field: uint32 LE length followed by that many bytes; advances [b]. */
    private fun lenPrefixed(b: ByteBuffer): ByteBuffer? {
        if (b.remaining() < 4) return null
        val len = b.int
        if (len < 0 || len > b.remaining()) return null
        val out = b.slice().order(ByteOrder.LITTLE_ENDIAN)
        out.limit(len)
        b.position(b.position() + len)
        return out
    }

    private fun lenPrefixedBytes(b: ByteBuffer): ByteArray? {
        if (b.remaining() < 4) return null
        val len = b.int
        if (len < 0 || len > b.remaining()) return null
        val out = ByteArray(len)
        b.get(out)
        return out
    }

    // ------------------------------------------------------------ little-endian readers

    private fun uint32LE(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun uint64LE(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun uint16LE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(b: ByteArray, off: Int): Int {
        var v = 0
        for (i in 3 downTo 0) v = (v shl 8) or (b[off + i].toInt() and 0xFF)
        return v
    }

    private fun putUint32LE(dst: ByteArray, off: Int, v: Long) {
        dst[off] = (v and 0xFF).toByte()
        dst[off + 1] = ((v shr 8) and 0xFF).toByte()
        dst[off + 2] = ((v shr 16) and 0xFF).toByte()
        dst[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------ signature algorithms

    private class Algo(val id: Int, val jca: String, val keyAlg: String, val digest: String, val pss: PSSParameterSpec?)

    private val ALGOS = listOf(
        Algo(0x0101, "SHA256withRSA/PSS", "RSA", "SHA-256",
            PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)),
        Algo(0x0102, "SHA512withRSA/PSS", "RSA", "SHA-512",
            PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1)),
        Algo(0x0103, "SHA256withRSA", "RSA", "SHA-256", null),
        Algo(0x0104, "SHA512withRSA", "RSA", "SHA-512", null),
        Algo(0x0201, "SHA256withECDSA", "EC", "SHA-256", null),
        Algo(0x0202, "SHA512withECDSA", "EC", "SHA-512", null),
        Algo(0x0301, "SHA256withDSA", "DSA", "SHA-256", null),
    )

    private fun byId(id: Int): Algo? = ALGOS.firstOrNull { it.id == id }
}
