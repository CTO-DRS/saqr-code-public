package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Mcp
import com.ctodrs.saqrcode.data.AccountStore
import com.ctodrs.saqrcode.data.MemoryKeyValue
import com.ctodrs.saqrcode.git.Forge
import com.ctodrs.saqrcode.git.GitLabApi
import com.ctodrs.saqrcode.ondevice.ApkVerify
import com.ctodrs.saqrcode.ondevice.BuildCache
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** v0.10 · in-app APK signature verification, forge support, MCP allow/deny, keep-last-APK. */
class V010FeaturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------ Forge detection

    @Test
    fun `forge detect resolves hosts`() {
        assertEquals(Forge.Kind.GITLAB, Forge.detect("gitlab.com", ""))
        assertEquals(Forge.Kind.GITLAB, Forge.detect("gitlab.example.com", ""))
        assertEquals(Forge.Kind.GITLAB, Forge.detect("my-gitlab.internal", "auto"))
        assertEquals(Forge.Kind.GITEA, Forge.detect("codeberg.org", ""))
        assertEquals(Forge.Kind.GITEA, Forge.detect("git.example.com", "gitea"))
        assertEquals(Forge.Kind.GITLAB, Forge.detect("git.example.com", "gitlab"))
        // unknown self-hosted host with no pick → null (user must choose)
        assertNull(Forge.detect("git.example.com", ""))
        assertNull(Forge.detect("git.example.com", "auto"))
    }

    @Test
    fun `forge repo path parses https and ssh urls`() {
        assertEquals("group/sub/proj", Forge.repoPath("https://gitlab.com/group/sub/proj.git"))
        assertEquals("owner/repo", Forge.repoPath("https://codeberg.org/owner/repo.git"))
        assertEquals("owner/repo", Forge.repoPath("git@codeberg.org:owner/repo.git"))
        assertEquals("owner/repo", Forge.repoPath("https://git.example.com:3000/owner/repo"))
        assertNull(Forge.repoPath("https://gitlab.com/onlyname"))
        assertNull(Forge.repoPath("nonsense"))
    }

    @Test
    fun `gitlab project id encodes slashes and visibility maps`() {
        assertEquals("group%2Fsub%2Fproj", GitLabApi.projectId("group/sub/proj"))
        assertEquals("proj", GitLabApi.projectId("proj"))
        assertEquals("private", GitLabApi.visibility(true))
        assertEquals("public", GitLabApi.visibility(false))
    }

    @Test
    fun `account store keeps a per-host api kind`() {
        val store = AccountStore(MemoryKeyValue())
        store.saveHost("gitlab.com", "u", "token-value-1")
        assertEquals("", store.hostKind("gitlab.com"))
        store.setHostKind("gitlab.com", "gitlab")
        assertEquals("gitlab", store.hostKind("https://GitLab.com/x"))
        store.setHostKind("gitlab.com", "gitea")
        assertEquals("gitea", store.hostKind("gitlab.com"))
        store.setHostKind("gitlab.com", "nonsense")
        assertEquals("", store.hostKind("gitlab.com"))
    }

    // ------------------------------------------------------------ MCP allow/deny

    @Test
    fun `mcp disabled tools round-trip and filter`() {
        val json = Mcp.encodeDisabled(mapOf("context7" to setOf("delete_docs"), "gh" to setOf("push_files", "force_push")))
        val back = Mcp.decodeDisabled(json)
        assertEquals(setOf("delete_docs"), back["context7"])
        assertEquals(setOf("push_files", "force_push"), back["gh"])
        assertEquals(emptyMap<String, Set<String>>(), Mcp.decodeDisabled(null))
        assertEquals(emptyMap<String, Set<String>>(), Mcp.decodeDisabled("{bad"))
        // empty lists are not stored
        assertFalse(json.contains("empty"))

        val tools = listOf(
            Mcp.Tool(Mcp.toolName("context7", "get_docs"), "d", JSONObject()),
            Mcp.Tool(Mcp.toolName("context7", "delete_docs"), "d", JSONObject()),
        )
        val visible = Mcp.filterTools(tools, back["context7"]!!)
        assertEquals(1, visible.size)
        assertEquals(Mcp.toolName("context7", "get_docs"), visible[0].name)
        assertEquals(2, Mcp.filterTools(tools, emptySet()).size)
    }

    @Test
    fun `mcp slug keys match server slugs`() {
        assertEquals("myserver", Mcp.slugify("My Server"))
        val slug = Mcp.slugify("My Server")
        assertTrue(Mcp.decodeDisabled("""{"$slug":["t1"]}""").containsKey("myserver"))
    }

    // ------------------------------------------------------------ keep-last-APK helpers

    @Test
    fun `stale apks picks every apk except the fresh one`() {
        val dist = tmp.newFolder("dist")
        val fresh = File(dist, "app-release.apk").apply { writeBytes(byteArrayOf(1)) }
        val old1 = File(dist, "app-debug.apk").apply { writeBytes(byteArrayOf(2)) }
        val old2 = File(dist, "app-release-OLD.apk").apply { writeBytes(byteArrayOf(3)) }
        File(dist, "notes.txt").writeText("keep me")
        val stale = BuildCache.staleApks(dist, fresh)
        assertEquals(setOf(old1.path, old2.path), stale.map { it.path }.toSet())
        assertTrue(BuildCache.staleApks(dist, fresh).none { it.path == fresh.path })

        val build = tmp.newFolder("proj")
        assertEquals(
            listOf("app/build/intermediates", "app/build/tmp", ".gradle"),
            BuildCache.clearableIntermediates(build).map { it.path.removePrefix(build.path + "/") },
        )
    }

    @Test
    fun `freedBy sums files and directory trees`() {
        val root = tmp.newFolder("x")
        File(root, "a.bin").apply { writeBytes(ByteArray(1000)) }
        val sub = File(root, "sub").apply { mkdirs() }
        File(sub, "b.bin").apply { writeBytes(ByteArray(500)) }
        assertEquals(1500L, BuildCache.freedBy(listOf(File(root, "a.bin"), sub)))
    }

    // ------------------------------------------------------------ APK signature verification

    private fun keytoolKey(alg: String, dname: String): Triple<PrivateKey, X509Certificate, ByteArray> {
        // NB: the keystore path must NOT exist — keytool refuses to write into an empty file.
        val ks = File(tmp.root, "k${System.nanoTime()}.p12")
        val keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool"
        val p = ProcessBuilder(
            keytool, "-genkeypair", "-keyalg", alg, if (alg == "EC") "-groupname" else "-keysize",
            if (alg == "EC") "secp256r1" else "2048",
            "-alias", "t", "-dname", dname, "-validity", "30",
            "-keystore", ks.absolutePath, "-storepass", "password", "-keypass", "password", "-storetype", "PKCS12",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val ok = p.waitFor() == 0
        assertTrue("keytool failed:\n$out", ok)
        val store = KeyStore.getInstance("PKCS12")
        ks.inputStream().use { store.load(it, "password".toCharArray()) }
        val key = store.getKey("t", "password".toCharArray()) as PrivateKey
        val cert = store.getCertificate("t") as X509Certificate
        return Triple(key, cert, cert.encoded)
    }

    private fun le32(v: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (v and 0xFF).toByte(); b[1] = ((v shr 8) and 0xFF).toByte()
        b[2] = ((v shr 16) and 0xFF).toByte(); b[3] = ((v shr 24) and 0xFF).toByte()
        return b
    }

    private fun le64(v: Long): ByteArray {
        val b = ByteArray(8)
        for (i in 0 until 8) b[i] = ((v shr (8 * i)) and 0xFF).toByte()
        return b
    }

    private fun lp(v: ByteArray) = le32(v.size) + v

    private fun chunkedSha256(sections: List<ByteArray>): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        var count = 0L
        sections.forEach { if (it.isNotEmpty()) count += (it.size + 1048575L) / 1048576L }
        val digests = ArrayList<ByteArray>()
        sections.forEach { s ->
            var off = 0
            while (off < s.size) {
                val len = minOf(1048576, s.size - off)
                md.update(0xa5.toByte()); md.update(le32(len)); md.update(s, off, len)
                digests.add(md.digest())
                off += len
            }
        }
        md.update(0x5a.toByte()); md.update(le32(count.toInt()))
        digests.forEach { md.update(it) }
        return md.digest()
    }

    private fun eocdOffset(b: ByteArray): Int {
        var i = b.size - 22
        while (i >= 0) {
            if (b[i] == 0x50.toByte() && b[i + 1] == 0x4b.toByte() && b[i + 2] == 0x05.toByte() && b[i + 3] == 0x06.toByte()) {
                val c = (b[i + 20].toInt() and 0xFF) or ((b[i + 21].toInt() and 0xFF) shl 8)
                if (i + 22 + c == b.size) return i
            }
            i--
        }
        error("no eocd")
    }

    private fun plainZip(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("AndroidManifest.xml")); z.write("""<manifest package="com.t"/>""".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("classes.dex")); z.write(ByteArray(3000) { (it % 251).toByte() }); z.closeEntry()
            z.putNextEntry(ZipEntry("res/values.arsc")); z.write("arabic صقر".toByteArray(StandardCharsets.UTF_8)); z.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun signedData(
        digestAlgoId: Int,
        digest: ByteArray,
        certDer: ByteArray,
        extraAttrs: Boolean = false,
    ): ByteArray {
        val digestEntry = lp(le32(digestAlgoId) + lp(digest))
        val digestsField = lp(digestEntry)
        val certsField = lp(lp(certDer))
        val attrsField = if (extraAttrs) lp(lp(le32(0xbeef01) + lp(byteArrayOf(1)))) else lp(ByteArray(0))
        return digestsField + certsField + attrsField
    }

    private fun signerBlockV2(signedData: ByteArray, sigAlgoId: Int, sig: ByteArray, pubKey: ByteArray): ByteArray =
        lp(signedData) + lp(lp(le32(sigAlgoId) + lp(sig))) + lp(pubKey)

    private fun signerBlockV3(signedData: ByteArray, sigAlgoId: Int, sig: ByteArray, pubKey: ByteArray): ByteArray =
        lp(signedData) + le32(24) + le32(Int.MAX_VALUE) + lp(lp(le32(sigAlgoId) + lp(sig))) + lp(pubKey)

    private fun buildApk(
        key: PrivateKey,
        cert: X509Certificate,
        sigAlgoId: Int,
        jcaSigAlg: String,
        v3: Boolean,
        alsoV2: Boolean = false,
        corruptSig: Boolean = false,
        corruptContent: Boolean = false,
        signingKey: PrivateKey = key,
    ): ByteArray {
        val zip = plainZip()
        val eocdOff = eocdOffset(zip)
        val cdOff = readLE(zip, eocdOff + 16)
        val cdSize = readLE(zip, eocdOff + 12)
        val cd = zip.copyOfRange(cdOff, cdOff + cdSize)
        val contents = zip.copyOfRange(0, cdOff)
        val eocdOriginal = zip.copyOfRange(eocdOff, zip.size)

        val digest = chunkedSha256(listOf(contents, cd, eocdOriginal))
        val data = signedData(sigAlgoId, digest, cert.encoded)
        val sig = run {
            val s = Signature.getInstance(jcaSigAlg)
            s.initSign(signingKey)
            s.update(data)
            s.sign()
        }
        val sigBytes = if (corruptSig) sig.copyOf().also { it[3] = (it[3].toInt() xor 0x41).toByte() } else sig
        val pubKey = cert.publicKey.encoded
        val blockBytes = ArrayList<Pair<Int, ByteArray>>()
        // the block value is a length-prefixed *sequence* of length-prefixed signers — two wraps
        if (alsoV2 || !v3) blockBytes.add(0x7109871a to lp(lp(signerBlockV2(data, sigAlgoId, sigBytes, pubKey))))
        if (v3) blockBytes.add(0xf05368c0.toInt() to lp(lp(signerBlockV3(data, sigAlgoId, sigBytes, pubKey))))

        val pairs = blockBytes.map { (id, payload) -> le64((payload.size + 4).toLong()) + le32(id) + payload }
        val pairsBlob = pairs.reduce { a, b -> a + b }
        val sizeValue = pairsBlob.size + 24
        val block = le64(sizeValue.toLong()) + pairsBlob + le64(sizeValue.toLong()) + ApkVerify.BLOCK_MAGIC

        val newCdOff = cdOff + block.size
        val eocd2 = eocdOriginal.copyOf().also { System.arraycopy(le32(newCdOff), 0, it, 16, 4) }
        val out = contents + block + cd + eocd2
        if (corruptContent) {
            // flip a byte inside the ZIP entry contents (before the signing block) —
            // deterministic: half-way into the contents region
            val i = (cdOff - 4) / 2
            out[i] = (out[i].toInt() xor 0x21).toByte()
        }
        return out
    }

    private fun readLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or ((b[off + 3].toInt() and 0xFF) shl 24)

    @Test
    fun `apk verify rejects a plain zip`() {
        val r = ApkVerify.verify(plainZip())
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("no APK Signing Block"))
    }

    @Test
    fun `apk verify accepts a v2 signed apk`() {
        val (key, cert, _) = keytoolKey("RSA", "CN=Saqr Test")
        val apk = buildApk(key, cert, 0x0103, "SHA256withRSA", v3 = false)
        val r = ApkVerify.verify(apk)
        assertTrue("expected ok, got: " + r.error, r.ok)
        assertEquals("v2", r.scheme)
        val fp = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
        assertEquals(fp, r.certSha256)
        assertTrue(r.subject!!.contains("Saqr Test"))
        assertNotNull(r.validUntil)
        // spaced fingerprint helper
        assertTrue(r.fingerprintLine!!.contains(" "))
    }

    @Test
    fun `apk verify prefers v3 when both schemes are present`() {
        val (key, cert, _) = keytoolKey("RSA", "CN=Saqr Two")
        val apk = buildApk(key, cert, 0x0103, "SHA256withRSA", v3 = true, alsoV2 = true)
        val r = ApkVerify.verify(apk)
        assertTrue("expected ok, got: " + r.error, r.ok)
        assertEquals("v3", r.scheme)
    }

    @Test
    fun `apk verify accepts an ecdsa signed apk`() {
        val (key, cert, _) = keytoolKey("EC", "CN=Saqr EC")
        val apk = buildApk(key, cert, 0x0201, "SHA256withECDSA", v3 = false)
        val r = ApkVerify.verify(apk)
        assertTrue("expected ok, got: " + r.error, r.ok)
        assertEquals("v2", r.scheme)
    }

    @Test
    fun `apk verify rejects tampered contents`() {
        val (key, cert, _) = keytoolKey("RSA", "CN=Saqr Tamper")
        val apk = buildApk(key, cert, 0x0103, "SHA256withRSA", v3 = false, corruptContent = true)
        val r = ApkVerify.verify(apk)
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("do NOT match") || r.error!!.contains("signature did not verify"))
    }

    @Test
    fun `apk verify rejects a corrupted signature`() {
        val (key, cert, _) = keytoolKey("RSA", "CN=Saqr Corrupt")
        val apk = buildApk(key, cert, 0x0103, "SHA256withRSA", v3 = false, corruptSig = true)
        val r = ApkVerify.verify(apk)
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("did not verify") || r.error!!.contains("do NOT match"))
    }

    @Test
    fun `apk verify rejects a signature made with another key`() {
        val (key, cert, _) = keytoolKey("RSA", "CN=Saqr Real")
        val (other, _, _) = keytoolKey("RSA", "CN=Saqr Other")
        val apk = buildApk(key, cert, 0x0103, "SHA256withRSA", v3 = false, signingKey = other)
        val r = ApkVerify.verify(apk)
        assertFalse(r.ok)
    }

    // ------------------------------------------------------------ JSON safety

    @Test
    fun `mcp disabled config survives truncated json`() {
        assertEquals(emptyMap<String, Set<String>>(), Mcp.decodeDisabled("{\"a\": [\"t\""))
        assertEquals(emptyMap<String, Set<String>>(), Mcp.decodeDisabled("[]"))
    }
}
