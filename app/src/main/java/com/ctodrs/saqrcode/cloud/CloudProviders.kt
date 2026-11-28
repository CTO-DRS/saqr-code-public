package com.ctodrs.saqrcode.cloud

/**
 * v0.1.65 · Where providers come from. The real one (Daytona behind our token proxy) is wired
 * next; until a proxy address is set this returns null and the agent says so plainly.
 * Tests replace [factory] with a fake.
 */
object CloudProviders {
    @Volatile var factory: (String) -> CloudProvider? = { _ -> null }
    fun create(proxyUrl: String): CloudProvider? = factory(proxyUrl)
}
