package com.ctodrs.saqrcode.cloud

/**
 * v0.1.65 · Where providers come from. The real one (Daytona behind our token proxy) is wired
 * next; until a proxy address is set this returns null and the agent says so plainly.
 * Tests replace [factory] with a fake.
 */
object CloudProviders {
    /** The shared token for the proxy; set by the session before [create]. */
    @Volatile var token: String = ""
    @Volatile var factory: (String) -> CloudProvider? = { url -> if (url.isBlank() || token.isBlank()) null else ProxyCloudProvider(url, token) }
    fun create(proxyUrl: String): CloudProvider? = factory(proxyUrl)
}
