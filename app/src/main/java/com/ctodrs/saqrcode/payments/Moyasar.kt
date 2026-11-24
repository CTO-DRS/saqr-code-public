package com.ctodrs.saqrcode.payments

import com.ctodrs.saqrcode.billing.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * v0.11 · مويسر · Moyasar (api.moyasar.com) invoices for the manual-payment path.
 *
 * Invoices are the mobile-safe integration: Moyasar hosts a payment page that takes mada, credit
 * cards, STC Pay and Apple Pay; the app just opens [Invoice.url] and later polls the status.
 * The **secret key** belongs to the merchant only — it lives in SecureStore on the developer's
 * own device (Settings → Billing → merchant tools); end users pay the hosted page and receive a
 * [LicenseCodec] token instead. Pure helpers + JSON mapping are unit-tested in V011FeaturesTest.
 */
object Moyasar {

    const val API = "https://api.moyasar.com/v1"

    enum class InvoiceStatus { INITIATED, PAID, FAILED, EXPIRED, CANCELED, REFUNDED, ON_HOLD, VOIDED }

    data class Invoice(
        val id: String,
        val status: InvoiceStatus,
        /** The hosted checkout page the payer must visit. */
        val url: String,
        val amountHalalas: Int,
        val currency: String,
        val description: String,
    )

    /** SAR → halalas: `15.0` → `1500` (rounded, never truncated). */
    fun amountHalalas(sar: Double): Int = Math.round(sar * 100.0).toInt()

    /** `1500` → `SAR 15.00`. */
    fun formatSar(halalas: Int): String = "SAR %,.2f".format(halalas / 100.0)

    /** Moyasar API auth: HTTP Basic with the secret key as username and an empty password. */
    fun authHeader(secretKey: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$secretKey:".toByteArray(Charsets.UTF_8))

    /** POST /v1/invoices body. Amounts must be ≥ 100 halalas per Moyasar's rules. */
    fun createInvoiceBody(
        plan: Plan,
        description: String,
        successUrl: String? = null,
        backUrl: String? = null,
        expiredAt: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): JSONObject {
        require(plan.priceHalalas >= 100) { "amount below Moyasar minimum (1.00 SAR)" }
        val body = JSONObject()
            .put("amount", plan.priceHalalas)
            .put("currency", "SAR")
            .put("description", description)
        successUrl?.takeIf { it.isNotBlank() }?.let { body.put("success_url", it) }
        backUrl?.takeIf { it.isNotBlank() }?.let { body.put("back_url", it) }
        expiredAt?.takeIf { it.isNotBlank() }?.let { body.put("expired_at", it) }
        if (metadata.isNotEmpty()) {
            val md = JSONObject()
            metadata.forEach { (k, v) -> md.put(k, v) }
            body.put("metadata", md)
        }
        return body
    }

    /** Maps a raw status string to [InvoiceStatus]; unknown values stay INITIATED so polling continues. */
    fun statusOf(raw: String?): InvoiceStatus = when (raw?.lowercase()) {
        "paid" -> InvoiceStatus.PAID
        "failed" -> InvoiceStatus.FAILED
        "expired" -> InvoiceStatus.EXPIRED
        "canceled" -> InvoiceStatus.CANCELED
        "refunded" -> InvoiceStatus.REFUNDED
        "on_hold" -> InvoiceStatus.ON_HOLD
        "voided" -> InvoiceStatus.VOIDED
        else -> InvoiceStatus.INITIATED
    }

    /** Parses an invoice object (create/fetch response). */
    fun parseInvoice(o: JSONObject): Invoice = Invoice(
        id = o.optString("id"),
        status = statusOf(o.optString("status", "initiated")),
        url = o.optString("url"),
        amountHalalas = o.optInt("amount", 0),
        currency = o.optString("currency", "SAR"),
        description = o.optString("description"),
    )

    /** A user-facing invoice error from a Moyasar error body: `{"message":"…","errors":{…}}`. */
    fun errorOf(body: String?): String? = runCatching {
        val o = JSONObject(body ?: return null)
        val msg = o.optString("message").takeIf { it.isNotBlank() }
        if (msg != null) {
            val errors = o.optJSONObject("errors")
            if (errors != null && errors.length() > 0) "$msg (${errors.toString().take(120)})" else msg
        } else null
    }.getOrNull()
}

/**
 * Runtime half: real HTTPS calls. One instance per merchant session; created lazily.
 */
class MoyasarClient(private val secretKey: String, http: OkHttpClient? = null) {

    private val client: OkHttpClient = (http ?: OkHttpClient())
        .newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Creates an invoice; returns it with the hosted [Invoice.url]. */
    suspend fun createInvoice(body: JSONObject): Result<Moyasar.Invoice> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${Moyasar.API}/invoices")
                .header("Authorization", Moyasar.authHeader(secretKey))
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(Moyasar.errorOf(text) ?: "HTTP ${resp.code}")
                Moyasar.parseInvoice(JSONObject(text))
            }
        }
    }

    /** Fetches one invoice — used to poll until PAID/EXPIRED/FAILED. */
    suspend fun fetchInvoice(id: String): Result<Moyasar.Invoice> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${Moyasar.API}/invoices/${id.trim()}")
                .header("Authorization", Moyasar.authHeader(secretKey))
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(Moyasar.errorOf(text) ?: "HTTP ${resp.code}")
                Moyasar.parseInvoice(JSONObject(text))
            }
        }
    }
}
