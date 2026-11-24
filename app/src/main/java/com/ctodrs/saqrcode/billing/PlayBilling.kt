package com.ctodrs.saqrcode.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ctodrs.saqrcode.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * v0.11 · متجر جوجل بلاي · Google Play Billing (PBL 8.x) wrapper.
 *
 * Three products: `saqr_pro_monthly`, `saqr_student_monthly` (subscriptions) and `saqr_lifetime`
 * (one-time). Purchases are acknowledged immediately and mapped to a local [Entitlement]; the
 * authoritative state is re-read from Play via [restore] on every connect, so re-installs keep
 * their plan. No server: Play's signed purchase state IS the verification.
 *
 * The class is deliberately thin — all tier/merge/expiry logic lives in [Entitlements] (tested).
 * On devices without Play services, connect() reports unavailable and the paywall falls back to
 * the Moyasar/license path.
 */
class PlayBilling(private val context: Context, private val settings: SettingsStore) {

    var available: Boolean = false; private set
    var lastError: String? = null; private set
    private val _status = MutableStateFlow("idle") // idle | connecting | ready | unavailable | closed
    val status: StateFlow<String> = _status

    private var client: BillingClient? = null

    /** Lazily connects; safe to call repeatedly (the paywall calls it on open). */
    fun connect() {
        if (client != null) return
        _status.value = "connecting"
        val c = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    available = true
                    lastError = null
                    _status.value = "ready"
                    restore()
                } else {
                    available = false
                    lastError = describeCode(result)
                    _status.value = "unavailable"
                }
            }

            override fun onBillingServiceDisconnected() {
                available = false
            }
        })
    }

    /** Re-reads active purchases from Play (subscriptions + one-time) and re-applies entitlement. */
    fun restore() {
        val c = client ?: return
        for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
            c.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build()
            ) { result, purchases ->
                if (result.responseCode == BillingResponseCode.OK) apply(purchases)
            }
        }
    }

    /** Launches the Play purchase sheet for [plan]; errors surface as strings for a snackbar. */
    fun purchase(activity: Activity, plan: Plan, onError: (String) -> Unit) {
        val c = client
        if (c == null || !available) { onError(lastError ?: "Play Billing is not connected"); return }
        val type = if (plan.monthly) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(plan.playProductId)
                    .setProductType(type)
                    .build()
            )
        ).build()
        c.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingResponseCode.OK) {
                onError(describeCode(result)); return@queryProductDetailsAsync
            }
            val pd: ProductDetails = detailsResult.productDetailsList.firstOrNull()
                ?: run { onError("product '${plan.playProductId}' not found in Play Console yet"); return@queryProductDetailsAsync }
            val offerToken = if (plan.monthly) pd.subscriptionOfferDetails?.firstOrNull()?.offerToken else null
            val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .apply { offerToken?.let { setOfferToken(it) } }
                        .build()
                )
            ).build()
            c.launchBillingFlow(activity, flow)
        }
    }

    fun close() {
        runCatching { client?.endConnection() }
        client = null
        available = false
        _status.value = "closed"
    }

    // ------------------------------------------------------------ plumbing

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingResponseCode.OK && purchases != null) apply(purchases)
    }

    private fun apply(purchases: List<Purchase>) {
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!p.isAcknowledged) {
                client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                ) { }
            }
            val productId = p.products.firstOrNull() ?: continue
            val plan = Plans.byPlayProduct(productId) ?: continue
            settings.applyPlayPurchase(plan, p.purchaseTime, p.orderId ?: p.purchaseToken)
        }
    }

    private fun describeCode(result: BillingResult): String {
        val code = result.responseCode
        val name = when (code) {
            BillingResponseCode.OK -> "OK"
            BillingResponseCode.USER_CANCELED -> "canceled"
            BillingResponseCode.BILLING_UNAVAILABLE -> "Play Billing unavailable"
            BillingResponseCode.ITEM_UNAVAILABLE -> "item unavailable"
            BillingResponseCode.DEVELOPER_ERROR -> "setup error (products not configured)"
            BillingResponseCode.NETWORK_ERROR -> "network error"
            else -> "code $code"
        }
        return result.debugMessage?.takeIf { it.isNotBlank() }?.let { "$name — $it" } ?: name
    }
}
