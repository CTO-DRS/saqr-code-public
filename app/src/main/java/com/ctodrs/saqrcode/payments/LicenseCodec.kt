package com.ctodrs.saqrcode.payments

import com.ctodrs.saqrcode.billing.PlanTier
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * v0.11 · تراخيص دون اتصال · Offline license tokens for the Moyasar/manual payment path.
 *
 * Token format: `SQ1.<base64url(payload JSON)>.<base64url(HMAC-SHA256)>` — newline-free so it can
 * be pasted anywhere. The HMAC key comes from `BuildConfig.SAQR_LICENSE_KEY` (set from
 * `keystore.properties` → `licenseKey`; the public dev fallback is **not** secret, see NOTICE.md).
 * Tokens are minted by the developer after a Moyasar payment lands — `scripts/license_tool.py`
 * or Settings → Billing → merchant tools.
 *
 * This is convenience licensing, not crypto enforcement (the app is open code); strong billing
 * stays with Play Billing. Pure and unit-tested in V011FeaturesTest.
 */
object LicenseCodec {

    const val VERSION = "SQ1"
    const val TIER_PRO = "pro"
    const val TIER_STUDENT = "student"
    const val TIER_LIFETIME = "lifetime"

    data class Verified(val tier: PlanTier, val expiresAt: Long?, val email: String?, val orderId: String)

    fun generateKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun encodeKey(key: ByteArray): String = key.joinToString("") { "%02x".format(it) }

    fun decodeKey(hex: String): ByteArray? = runCatching {
        val h = hex.trim().lowercase()
        require(h.length >= 64 && h.length % 2 == 0) { "key must be 32 bytes of hex" }
        ByteArray(h.length / 2) { i -> ((Character.digit(h[i * 2], 16) shl 4) + Character.digit(h[i * 2 + 1], 16)).toByte() }
    }.getOrNull()

    private fun hmac(payload: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(payload)
    }

    private fun b64url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    private fun unb64url(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

    /** Builds the payload JSON for one license. [tier] is one of the TIER_* constants. */
    fun payloadJson(tier: String, email: String?, orderId: String, expiresAt: Long?, issuedAt: Long): JSONObject =
        JSONObject()
            .put("v", 1)
            .put("tier", tier)
            .put("email", email ?: JSONObject.NULL)
            .put("order", orderId)
            .put("exp", expiresAt ?: JSONObject.NULL)
            .put("iat", issuedAt)

    /** Signs a payload into a `SQ1.` token. */
    fun sign(payload: JSONObject, key: ByteArray): String {
        val body = payload.toString().toByteArray(Charsets.UTF_8)
        return "$VERSION.${b64url(body)}.${b64url(hmac(body, key))}"
    }

    /** Convenience: sign straight from fields. */
    fun sign(tier: String, email: String?, orderId: String, expiresAt: Long?, issuedAt: Long, key: ByteArray): String =
        sign(payloadJson(tier, email, orderId, expiresAt, issuedAt), key)

    /** Verifies signature + expiry. Returns the license or an error ("malformed"/"bad signature"/"expired"). */
    fun verify(token: String, key: ByteArray, now: Long): Result<Verified> = runCatching {
        val parts = token.trim().split('.')
        require(parts.size == 3 && parts[0] == VERSION) { "malformed" }
        val body = unb64url(parts[1])
        val sig = unb64url(parts[2])
        require(MessageDigest.isEqual(sig, hmac(body, key))) { "bad signature" }
        val o = JSONObject(String(body, Charsets.UTF_8))
        val tier = when (o.optString("tier")) {
            TIER_PRO -> PlanTier.PRO
            TIER_STUDENT -> PlanTier.STUDENT
            TIER_LIFETIME -> PlanTier.LIFETIME
            else -> error("malformed")
        }
        val exp = if (o.isNull("exp")) null else o.optLong("exp", 0L).takeIf { it > 0 }
        if (exp != null && now > exp) error("expired")
        Verified(
            tier = tier,
            expiresAt = exp,
            email = o.optString("email").takeIf { it.isNotBlank() && !o.isNull("email") },
            orderId = o.optString("order", ""),
        )
    }
}
