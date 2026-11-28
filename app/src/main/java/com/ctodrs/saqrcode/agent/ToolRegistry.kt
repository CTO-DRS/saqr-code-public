package com.ctodrs.saqrcode.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore

/**
 * v0.15 «النواة المستقلة» · Tool registry.
 *
 * One table of metadata for every tool the agent may call: its risk tier, whether it only
 * reads, how long it may take and how often a transient failure may be retried. The table
 * wraps the existing specs ([Tools.specs] + [GitTools.specs]) without changing them — what
 * the model sees is byte-for-byte the same list — and gives the session one place to ask
 * "is this a real tool?", "may it run in parallel?" and "how risky is it?".
 *
 * Pure logic, unit-tested in V015FeaturesTest.
 */
enum class Risk { SAFE, MEDIUM, HIGH }

enum class ToolCategory { FILES, SEARCH, TERMINAL, WEB, QUALITY, GIT, MEMORY, AGENT, MCP }

data class ToolDef(
    val name: String,
    val spec: ToolSpec,
    val risk: Risk,
    val readOnly: Boolean,
    val timeoutSec: Long,
    val maxRetries: Int,
    val category: ToolCategory,
) {
    /** Read-only SAFE tools are the only ones the session may run side by side. */
    val parallelSafe get() = risk == Risk.SAFE && readOnly
}

object ToolRegistry {

    private data class Meta(val risk: Risk, val readOnly: Boolean, val timeoutSec: Long, val maxRetries: Int, val category: ToolCategory)

    /** Metadata per built-in tool. A built-in spec without a row here fails [builtins] loudly in tests. */
    private val META: Map<String, Meta> = mapOf(
        Tools.LIST to Meta(Risk.SAFE, true, 30, 0, ToolCategory.FILES),
        Tools.READ to Meta(Risk.SAFE, true, 30, 0, ToolCategory.FILES),
        Tools.SEARCH to Meta(Risk.SAFE, true, 60, 0, ToolCategory.SEARCH),
        Tools.PROJECT_SEARCH to Meta(Risk.SAFE, true, 30, 0, ToolCategory.SEARCH),
        Tools.GIT_STATUS to Meta(Risk.SAFE, true, 60, 0, ToolCategory.GIT),
        Tools.GIT_DIFF to Meta(Risk.SAFE, true, 60, 0, ToolCategory.GIT),
        Tools.GIT_LOG to Meta(Risk.SAFE, true, 60, 0, ToolCategory.GIT),
        Tools.WEB_SEARCH to Meta(Risk.SAFE, true, 45, 1, ToolCategory.WEB),
        Tools.WEB_FETCH to Meta(Risk.SAFE, true, 60, 1, ToolCategory.WEB),
        Tools.WRITE to Meta(Risk.MEDIUM, false, 30, 0, ToolCategory.FILES),
        Tools.EDIT to Meta(Risk.MEDIUM, false, 30, 0, ToolCategory.FILES),
        Tools.MULTI_EDIT to Meta(Risk.MEDIUM, false, 30, 0, ToolCategory.FILES),
        Tools.MOVE to Meta(Risk.MEDIUM, false, 30, 0, ToolCategory.FILES),
        Tools.CLOUD_RUN to Meta(Risk.MEDIUM, false, 900, 0, ToolCategory.TERMINAL),
        Tools.CLOUD_TEST to Meta(Risk.MEDIUM, false, 900, 0, ToolCategory.QUALITY),
        Tools.CLOUD_SYNC to Meta(Risk.MEDIUM, false, 300, 0, ToolCategory.TERMINAL),
        Tools.RUN to Meta(Risk.MEDIUM, false, 600, 0, ToolCategory.TERMINAL),
        Tools.LINT to Meta(Risk.MEDIUM, false, 300, 0, ToolCategory.QUALITY),
        Tools.TESTS to Meta(Risk.MEDIUM, false, 600, 0, ToolCategory.QUALITY),
        Tools.GIT_COMMIT to Meta(Risk.MEDIUM, false, 120, 0, ToolCategory.GIT),
        Tools.GIT_BRANCH to Meta(Risk.MEDIUM, false, 60, 0, ToolCategory.GIT),
        Tools.GIT_PULL to Meta(Risk.MEDIUM, false, 180, 0, ToolCategory.GIT),
        Tools.MEMORY_SAVE to Meta(Risk.MEDIUM, false, 10, 0, ToolCategory.MEMORY),
        Tools.MEMORY_FORGET to Meta(Risk.MEDIUM, false, 10, 0, ToolCategory.MEMORY),
        Tools.DELETE to Meta(Risk.HIGH, false, 30, 0, ToolCategory.FILES),
        Tools.GIT_PUSH to Meta(Risk.HIGH, false, 300, 0, ToolCategory.GIT),
        // conversation tools: they change no files, but they wait on the user / the plan pill
        Tools.PLAN to Meta(Risk.SAFE, false, 5, 0, ToolCategory.AGENT),
        Tools.ASK to Meta(Risk.SAFE, false, 0, 0, ToolCategory.AGENT),
    )

    /** Every built-in tool, in the exact order of [Tools.specs]. */
    val builtins: List<ToolDef> = Tools.specs.map { s ->
        val m = META[s.name] ?: error("ToolRegistry: no metadata for built-in tool ${s.name}")
        ToolDef(s.name, s, m.risk, m.readOnly, m.timeoutSec, m.maxRetries, m.category)
    }

    private val byName: Map<String, ToolDef> = builtins.associateBy { it.name }

    /** Built-in names known to the registry. */
    val names: Set<String> get() = byName.keys

    fun get(name: String): ToolDef? = byName[name]

    /** MCP tools are external code: MEDIUM by default, never parallel, never retried blindly. */
    fun forMcp(spec: ToolSpec): ToolDef = ToolDef(spec.name, spec, Risk.MEDIUM, false, 120, 0, ToolCategory.MCP)

    /**
     * The definition of [name] — a built-in, or one of this turn's MCP tools. Null = the model
     * asked for a tool that does not exist (or an MCP tool that is not connected); refuse it.
     */
    fun resolve(name: String, mcp: Collection<ToolSpec>): ToolDef? =
        byName[name] ?: mcp.firstOrNull { it.name == name }?.let { forMcp(it) }

    fun riskOf(name: String, mcp: Collection<ToolSpec> = emptyList()): Risk? = resolve(name, mcp)?.risk

    fun isParallelSafe(name: String): Boolean = byName[name]?.parallelSafe == true

    /** The tool list the model sees: identical in content to `Tools.specs + mcp`. */
    fun specs(mcp: List<ToolSpec>): List<ToolSpec> = builtins.map { it.spec } + mcp

    /** The refusal the model receives for a name outside the registry. */
    fun unknownToolMessage(name: String) =
        "Error: unknown tool '${name.take(80)}' — it is not in the tool registry. Use only the tools listed in your tool specs."
}

/**
 * v0.15: grouping of one turn's tool calls into execution batches. Consecutive calls that are
 * parallel-safe share one batch (run concurrently); every other call is a batch of its own.
 * Results are always written back in the original call order.
 */
object ParallelPlan {
    const val MAX_CONCURRENCY = 3

    /** Indices of [names] grouped into ordered batches. */
    fun batches(names: List<String>, parallelSafe: (String) -> Boolean): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        var run = ArrayList<Int>()
        names.forEachIndexed { i, n ->
            if (parallelSafe(n)) run.add(i)
            else {
                if (run.isNotEmpty()) { out += run; run = ArrayList() }
                out += listOf(i)
            }
        }
        if (run.isNotEmpty()) out += run
        return out
    }

    /**
     * Runs [block] over [items] with at most [limit] in flight; the result list keeps the
     * order of [items] whatever order the work finishes in.
     */
    suspend fun <T, R> mapBounded(items: List<T>, limit: Int = MAX_CONCURRENCY, block: suspend (T) -> R): List<R> =
        coroutineScope {
            val gate = Semaphore(limit.coerceAtLeast(1))
            items.map { item ->
                async { gate.acquire(); try { block(item) } finally { gate.release() } }
            }.map { it.await() }
        }
}
