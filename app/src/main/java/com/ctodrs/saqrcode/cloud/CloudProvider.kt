package com.ctodrs.saqrcode.cloud

/**
 * v0.1.65 · Cloud workspace: the provider interface.
 *
 * The agent never talks to a vendor directly. It talks to a [CloudProvider], and the real
 * one (Daytona behind our own token proxy) is a thin HTTP client. Tests use a fake.
 * Nothing here holds a vendor key: the proxy holds it and hands out short-lived tokens.
 */
data class CloudFile(val path: String, val bytes: ByteArray) {
    override fun equals(other: Any?) = other is CloudFile && other.path == path && other.bytes.contentEquals(bytes)
    override fun hashCode() = path.hashCode() * 31 + bytes.contentHashCode()
}

data class CloudExecResult(val exitCode: Int, val output: String, val timedOut: Boolean, val millis: Long)

interface CloudSandbox {
    /** Replaces the sandbox's /workspace with these files. */
    suspend fun upload(files: List<CloudFile>)

    /** Runs one shell command in /workspace; [onLine] gets output as it streams. */
    suspend fun exec(command: String, timeoutSec: Int, onLine: (String) -> Unit = {}): CloudExecResult

    /** Always safe to call twice. Stops billing. */
    suspend fun close()
}

fun interface CloudProvider {
    /** Starts a fresh throwaway sandbox. Throws [CloudException] with a plain-words message. */
    suspend fun open(): CloudSandbox
}

class CloudException(message: String) : Exception(message)
