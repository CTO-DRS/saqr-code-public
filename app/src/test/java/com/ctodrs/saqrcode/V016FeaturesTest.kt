package com.ctodrs.saqrcode

import android.content.Context
import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.AgentRoles
import com.ctodrs.saqrcode.agent.AuditEntry
import com.ctodrs.saqrcode.agent.AuditLog
import com.ctodrs.saqrcode.agent.Chief
import com.ctodrs.saqrcode.agent.ChiefRunner
import com.ctodrs.saqrcode.agent.Collaboration
import com.ctodrs.saqrcode.agent.ConflictDetector
import com.ctodrs.saqrcode.agent.FalconRouter
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.ModelTier
import com.ctodrs.saqrcode.agent.Planner
import com.ctodrs.saqrcode.agent.Reassignment
import com.ctodrs.saqrcode.agent.Reviewer
import com.ctodrs.saqrcode.agent.Risk
import com.ctodrs.saqrcode.agent.RoleContext
import com.ctodrs.saqrcode.agent.RunOutcome
import com.ctodrs.saqrcode.agent.Subtask
import com.ctodrs.saqrcode.agent.SubtaskStatus
import com.ctodrs.saqrcode.agent.TaskGraph
import com.ctodrs.saqrcode.agent.TaskGraphCodec
import com.ctodrs.saqrcode.agent.TaskState
import com.ctodrs.saqrcode.agent.TaskStateCodec
import com.ctodrs.saqrcode.agent.TeamConflict
import com.ctodrs.saqrcode.agent.TeamCost
import com.ctodrs.saqrcode.agent.TeamEvent
import com.ctodrs.saqrcode.agent.TeamLang
import com.ctodrs.saqrcode.agent.TeamPort
import com.ctodrs.saqrcode.agent.TeamResult
import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.ToolSpec
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.agent.WorkResult
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** v0.16 «فريق الوكلاء» · Agent Team & Orchestration (Chief mode). */
class V016FeaturesTest {

    @get:Rule val tmp = TemporaryFolder()

    private val R = AgentRole.entries.toSet()

    // ------------------------------------------------------------------ roles registry

    @Test fun twelveRolesWithValidToolSubsets() {
        assertEquals(12, AgentRoles.all.size)
        assertEquals(listOf("chief", "planner", "research", "architect", "developer", "debugger", "security", "tester", "reviewer",
            "optimizer", "documentation", "deployment"), AgentRoles.all.map { it.id })
        for (r in AgentRoles.all) {
            assertTrue(r.nameAr.isNotBlank() && r.nameEn.isNotBlank() && r.prompt.isNotBlank())
            assertTrue("${r.id} lists an unknown tool", r.tools.all { it in ToolRegistry.names })
            assertTrue(r.maxSteps >= 1)
        }
        // read-only roles hold only read-only tools
        for (r in listOf(AgentRole.REVIEWER, AgentRole.SECURITY, AgentRole.RESEARCH, AgentRole.ARCHITECT)) {
            assertTrue(r.readOnly)
            assertTrue("${r.id} has a mutating tool", r.tools.all { ToolRegistry.get(it)!!.readOnly })
        }
        // Deployment may use build/git, and git_push stays HIGH risk (approval card)
        assertTrue(Tools.GIT_PUSH in AgentRole.DEPLOYMENT.tools)
        assertEquals(Risk.HIGH, ToolRegistry.riskOf(Tools.GIT_PUSH))
        assertFalse(Tools.GIT_PUSH in AgentRole.DEVELOPER.tools)
        assertFalse(Tools.WRITE in AgentRole.REVIEWER.tools)
        assertTrue(AgentRole.DEVELOPER.tools.containsAll(listOf(Tools.WRITE, Tools.EDIT, Tools.RUN, Tools.TESTS)))
        // tiers
        assertEquals(ModelTier.STRONG, AgentRole.DEVELOPER.tier)
        assertEquals(ModelTier.INDEPENDENT_REVIEW, AgentRole.REVIEWER.tier)
        assertEquals(ModelTier.CHEAP, AgentRole.RESEARCH.tier)
        // core roles can't be switched off
        assertEquals(R - AgentRole.REVIEWER, AgentRoles.enabled(setOf("reviewer", "chief", "developer")))
    }

    @Test fun roleToolFilteringAndMcp() {
        val mcp = listOf(ToolSpec("mcp_docs_search", "d", JSONObject()))
        assertTrue(AgentRoles.allows(AgentRole.DEVELOPER, Tools.WRITE))
        assertFalse(AgentRoles.allows(AgentRole.REVIEWER, Tools.WRITE))
        assertTrue(AgentRoles.allows(AgentRole.RESEARCH, "mcp_docs_search", mcp))
        assertFalse(AgentRoles.allows(AgentRole.TESTER, "mcp_docs_search", mcp))
        assertFalse(AgentRoles.allows(AgentRole.RESEARCH, "mcp_other_x", mcp)) // not connected this turn
        val specs = AgentRoles.specs(AgentRole.SECURITY, mcp).map { it.name }
        assertTrue(specs.isNotEmpty() && specs.all { it in AgentRole.SECURITY.tools })
        // the reader runner never executes a non-parallel-safe tool, whatever the role lists
        assertTrue(AgentRoles.readerAllows(AgentRole.RESEARCH, Tools.WEB_FETCH))
        assertFalse(AgentRoles.readerAllows(AgentRole.DEVELOPER, Tools.WRITE))
        assertTrue(AgentRoles.refusal(AgentRole.REVIEWER, Tools.WRITE).startsWith("Error"))
    }

    @Test fun roleModelTiersGoThroughFalconRouter() {
        val prices = listOf(ModelPrice("claude-sonnet-4-5", 3.0, 15.0), ModelPrice("claude-haiku-4-5", 1.0, 5.0))
        val cands = listOf("claude-sonnet-4-5", "claude-haiku-4-5")
        assertEquals(AgentRoles.stageFor(ModelTier.CHEAP), FalconRouter.Stage.PLANNER)
        assertNull(AgentRoles.stageFor(ModelTier.STRONG))
        // STRONG stays on the coder
        assertEquals("claude-sonnet-4-5" to false, AgentRoles.modelFor(AgentRole.DEVELOPER, "claude-sonnet-4-5", "", "", true, cands, prices))
        // CHEAP routes to the cheapest capable model
        assertEquals("claude-haiku-4-5" to true, AgentRoles.modelFor(AgentRole.RESEARCH, "claude-sonnet-4-5", "", "", true, cands, prices))
        // router off → coder model
        assertEquals("claude-sonnet-4-5" to false, AgentRoles.modelFor(AgentRole.RESEARCH, "claude-sonnet-4-5", "", "", false, cands, prices))
        // explicit review model wins for the independent-review tier
        assertEquals("gpt-4.1" to false, AgentRoles.modelFor(AgentRole.SECURITY, "claude-sonnet-4-5", "gpt-4.1", "", true, cands, prices))
        assertEquals("p-model" to false, AgentRoles.modelFor(AgentRole.PLANNER, "claude-sonnet-4-5", "", "p-model", true, cands, prices))
    }

    // ------------------------------------------------------------------ Chief: intent + selection

    @Test fun intentClassificationRulesFirst() {
        fun i(t: String) = Chief.classify(t).intent
        assertEquals(Chief.Intent.QUESTION, i("What does the LoopDetector class do?"))
        assertEquals(Chief.Intent.QUESTION, i("لماذا نستخدم Compose هنا؟"))
        assertEquals(Chief.Intent.EDIT, i("Add a dark mode toggle to the settings screen"))
        assertEquals(Chief.Intent.EDIT, i("أضف زر تسجيل خروج في الشاشة الرئيسية"))
        assertEquals(Chief.Intent.BUG_FIX, i("The app crashes when I open the editor, fix it"))
        assertEquals(Chief.Intent.BUG_FIX, i("التطبيق يتعطل عند فتح المحرر، أصلحه"))
        assertEquals(Chief.Intent.NEW_PROJECT, i("Build me an app from scratch to track my reading"))
        assertEquals(Chief.Intent.NEW_PROJECT, i("سوّ لي تطبيق جديد للملاحظات"))
        assertEquals(Chief.Intent.RESEARCH, i("Look up the latest Ktor release notes"))
        assertEquals(Chief.Intent.MULTI_STAGE, i("1. add a login screen\n2. store the token\n3. write tests for both"))
        // ambiguous: question form + change verb → the cheap model decides
        val amb = Chief.classify("how do I add a login screen?")
        assertTrue(amb.ambiguous)
        assertFalse(Chief.classify("Add a dark mode toggle to the settings screen").ambiguous)
        // the model's one-word answer
        assertEquals(Chief.Intent.BUG_FIX, Chief.parseIntent("bug_fix"))
        assertEquals(Chief.Intent.QUESTION, Chief.parseIntent(" Question.\n"))
        assertEquals(Chief.Intent.NEW_PROJECT, Chief.parseIntent("new_project"))
        assertNull(Chief.parseIntent("no idea"))
    }

    @Test fun minimalRoleSelectionScenarios() {
        fun roles(intent: Chief.Intent, t: String, enabled: Set<AgentRole> = R) = Chief.select(intent, t, enabled).roles
        // a question → no team
        assertTrue(Chief.select(Chief.Intent.QUESTION, "what is this?").noTeam)
        // small edit → Developer + Reviewer
        assertEquals(listOf(AgentRole.DEVELOPER, AgentRole.REVIEWER), roles(Chief.Intent.EDIT, "rename the title of the main screen"))
        // bug → Debugger + Tester + Reviewer
        assertEquals(listOf(AgentRole.DEBUGGER, AgentRole.TESTER, AgentRole.REVIEWER), roles(Chief.Intent.BUG_FIX, "the list crashes on scroll"))
        // new project → Planner + Architect + Developer + Tester + Reviewer
        assertEquals(listOf(AgentRole.PLANNER, AgentRole.ARCHITECT, AgentRole.DEVELOPER, AgentRole.TESTER, AgentRole.REVIEWER),
            roles(Chief.Intent.NEW_PROJECT, "a notes app"))
        // Security joins when auth / secrets / network / commands are touched
        val auth = Chief.select(Chief.Intent.EDIT, "add a login screen that stores the auth token", R)
        assertTrue(AgentRole.SECURITY in auth.roles)
        assertTrue(auth.reasons[AgentRole.SECURITY]!!.contains("auth"))
        assertTrue(AgentRole.SECURITY in roles(Chief.Intent.EDIT, "أضف شاشة تسجيل الدخول مع كلمة المرور"))
        // Research joins when external information is needed
        assertTrue(AgentRole.RESEARCH in roles(Chief.Intent.EDIT, "upgrade to the latest version of the library per the official docs"))
        assertFalse(AgentRole.RESEARCH in roles(Chief.Intent.EDIT, "rename the title of the main screen"))
        // research only → just Research; research + change → Research + Developer + Reviewer
        assertEquals(listOf(AgentRole.RESEARCH), roles(Chief.Intent.RESEARCH, "look up the Ktor changelog"))
        assertEquals(listOf(AgentRole.RESEARCH, AgentRole.DEVELOPER, AgentRole.REVIEWER),
            roles(Chief.Intent.RESEARCH, "look up the Ktor changelog and update the dependency"))
        // the user's switches: Reviewer off → dropped; Debugger off → Developer fixes
        assertEquals(listOf(AgentRole.DEVELOPER), roles(Chief.Intent.EDIT, "rename the title", R - AgentRole.REVIEWER))
        assertEquals(listOf(AgentRole.DEVELOPER, AgentRole.TESTER, AgentRole.REVIEWER), roles(Chief.Intent.BUG_FIX, "it crashes", R - AgentRole.DEBUGGER))
        // every picked role says why
        val sel = Chief.select(Chief.Intent.NEW_PROJECT, "a notes app")
        assertEquals(sel.roles.toSet(), sel.reasons.keys)
    }

    @Test fun defaultGraphsAreMinimalAndOrdered() {
        val edit = Chief.defaultGraph(Chief.select(Chief.Intent.EDIT, "rename the title"), false)
        assertEquals(listOf(AgentRole.DEVELOPER, AgentRole.REVIEWER), edit.tasks.map { it.role })
        assertEquals(listOf("t1"), edit["t2"]!!.dependsOn)
        val sec = Chief.defaultGraph(Chief.select(Chief.Intent.EDIT, "add login with a password and the official docs"), true)
        val ids = sec.tasks.associateBy { it.role }
        assertEquals(emptyList<String>(), ids[AgentRole.RESEARCH]!!.dependsOn)
        assertTrue(ids[AgentRole.RESEARCH]!!.id in ids[AgentRole.DEVELOPER]!!.dependsOn)
        // Security and Reviewer both review the Developer's work and are independent of each other
        assertTrue(ids[AgentRole.DEVELOPER]!!.id in ids[AgentRole.SECURITY]!!.dependsOn)
        assertFalse(ids[AgentRole.SECURITY]!!.id in ids[AgentRole.REVIEWER]!!.dependsOn)
        assertNull(sec.findCycle())
        assertTrue(sec.tasks.first().title.any { it in '\u0600'..'\u06FF' }) // Arabic titles for an Arabic request
    }

    // ------------------------------------------------------------------ task graph

    private fun t(id: String, role: AgentRole = AgentRole.DEVELOPER, vararg deps: String, status: SubtaskStatus = SubtaskStatus.PENDING) =
        Subtask(id, "task $id", role, deps.toList(), status)

    @Test fun taskGraphCycleTopoReadyProgress() {
        val cyclic = TaskGraph(listOf(t("a", AgentRole.DEVELOPER, "c"), t("b", AgentRole.DEVELOPER, "a"), t("c", AgentRole.DEVELOPER, "b")))
        val cycle = cyclic.findCycle()
        assertNotNull(cycle)
        assertEquals(cycle!!.first(), cycle.last())
        assertNull(cyclic.topoOrder())

        val g = TaskGraph(listOf(
            t("t4", AgentRole.REVIEWER, "t2", "t3"),
            t("t1", AgentRole.RESEARCH),
            t("t2", AgentRole.DEVELOPER, "t1"),
            t("t3", AgentRole.SECURITY, "t1"),
        ))
        assertNull(g.findCycle())
        assertEquals(listOf("t1", "t2", "t3", "t4"), g.topoOrder())
        assertEquals(listOf("t1"), g.ready().map { it.id })
        assertEquals(0, g.progress())
        val g2 = g.update("t1") { it.copy(status = SubtaskStatus.DONE) }
        assertEquals(listOf("t2", "t3"), g2.ready().map { it.id })
        // independent read-only work: none while a worker is first in line
        assertTrue(g2.independentReady(2).isEmpty())
        val g3 = g2.update("t2") { it.copy(status = SubtaskStatus.DONE) }
        assertEquals(listOf("t3"), g3.independentReady(2).map { it.id })
        assertEquals(50, g3.progress())
        // two independent read-only subtasks may run together, bounded
        val reads = TaskGraph(listOf(t("r1", AgentRole.RESEARCH), t("r2", AgentRole.ARCHITECT), t("r3", AgentRole.RESEARCH)))
        assertEquals(listOf("r1", "r2"), reads.independentReady(2).map { it.id })
        // failure blocks dependents transitively
        val blocked = g.update("t1") { it.copy(status = SubtaskStatus.FAILED) }.blockDependentsOf("t1")
        assertEquals(setOf(SubtaskStatus.BLOCKED), listOf("t2", "t3", "t4").map { blocked[it]!!.status }.toSet())
        assertEquals(100, blocked.progress())
        assertTrue(blocked.anyFailed)
        assertEquals(setOf("t2", "t3", "t4"), g.dependentsOf("t1"))
        assertEquals(listOf("t1", "t2", "t3"), g.ancestorsOf("t4").map { it.id })
        // sanitize: unknown / self deps
        val dirty = TaskGraph(listOf(t("x", AgentRole.DEVELOPER, "x", "nope")))
        assertEquals(listOf("nope"), dirty.unknownDeps())
        assertTrue(dirty.sanitized()["x"]!!.dependsOn.isEmpty())
        // plan pill + resume
        val running = g.update("t1") { it.copy(status = SubtaskStatus.RUNNING) }
        assertEquals("in_progress", running.toPlanItems().first { it.title.contains("task t1") }.status)
        assertEquals(SubtaskStatus.PENDING, running.resetRunning()["t1"]!!.status)
    }

    @Test fun taskGraphPersistsInTaskStateBackwardCompatible() {
        // a v0.15 task file has no "team" field: still decodes, team = null
        val old = """{"v":1,"id":"task-1","session":"s","goal":"g","plan":[{"title":"a","status":"done"}],"step":3,"startedAt":5,"status":"running"}"""
        val d = TaskStateCodec.decode(old)!!
        assertNull(d.team)
        assertEquals(3, d.step)
        val g = TaskGraph(listOf(t("t1", AgentRole.RESEARCH, status = SubtaskStatus.DONE).copy(output = "found X", files = listOf("a.kt")),
            t("t2", AgentRole.DEVELOPER, "t1").copy(failures = 1, reassigned = true)))
        val st = TaskState("task-2", "s", "goal", startedAt = 1, team = g)
        val back = TaskStateCodec.decode(TaskStateCodec.encode(st))!!
        assertEquals(g.tasks, back.team!!.tasks)
        // unknown role ids fall back to Developer, broken entries are dropped
        val arr = org.json.JSONArray("""[{"id":"t1","title":"x","role":"wizard","status":"done"},{"title":"no id"},5]""")
        val lenient = TaskGraphCodec.fromJson(arr)!!
        assertEquals(1, lenient.tasks.size)
        assertEquals(AgentRole.DEVELOPER, lenient["t1"]!!.role)
        assertEquals(SubtaskStatus.DONE, lenient["t1"]!!.status)
    }

    // ------------------------------------------------------------------ planner JSON

    @Test fun plannerJsonBackwardCompatibleAndExtended() {
        // a v0.8 plan parses exactly as before (titles only, max 6)
        val old = Planner.parse("""{"tasks":[{"title":"a"},{"title":"b"},{"title":""},{"title":"c"},{"title":"d"},{"title":"e"},{"title":"f"},{"title":"g"}]}""")!!
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), old.tasks)
        assertFalse(old.hasTeamFields)
        assertNull(old.roleOf(0)); assertTrue(old.depsOf(1).isEmpty())
        assertEquals(Planner.Plan(listOf("x")), Planner.Plan(listOf("x")))
        // v0.16: role + dependsOn (ids, numbers, "tN")
        val p = Planner.parse("""```json
            {"tasks":[{"id":"t1","title":"Design","role":"architect"},
                      {"id":"t2","title":"Build","role":"Developer","dependsOn":["t1"]},
                      {"id":"t3","title":"Review","role":"reviewer","dependsOn":[2]},
                      {"title":"Docs","role":"documentation","depends_on":"t2"}]}
            ```""")!!
        assertTrue(p.hasTeamFields)
        assertEquals(listOf("architect", "developer", "reviewer", "documentation"), p.roles)
        assertEquals(listOf(emptyList(), listOf(0), listOf(1), listOf(1)), p.deps)
        val sel = Chief.Selection(Chief.Intent.MULTI_STAGE, listOf(AgentRole.PLANNER, AgentRole.ARCHITECT, AgentRole.DEVELOPER, AgentRole.REVIEWER), emptyMap())
        val g = Chief.graphFromPlan(p, sel, false)!!
        // documentation was not selected → becomes the main worker
        assertEquals(listOf(AgentRole.ARCHITECT, AgentRole.DEVELOPER, AgentRole.REVIEWER, AgentRole.DEVELOPER), g.tasks.map { it.role })
        assertEquals(listOf("t2"), g["t3"]!!.dependsOn)
        // a plan without dependencies becomes a sequence; a cycle is refused (the Chief falls back)
        val seq = TaskGraph.fromPlan(Planner.Plan(listOf("a", "b", "c")), AgentRole.DEVELOPER, R)
        assertEquals(listOf(emptyList(), listOf("t1"), listOf("t2")), seq.tasks.map { it.dependsOn })
        val cyc = Planner.parse("""{"tasks":[{"id":"a","title":"A","role":"developer","dependsOn":["b"]},{"id":"b","title":"B","role":"developer","dependsOn":["a"]}]}""")!!
        assertNull(Chief.graphFromPlan(cyc, sel, false))
        // missing selected review is appended after the work
        val noReview = Planner.parse("""{"tasks":[{"title":"Build","role":"developer"},{"title":"More","role":"developer","dependsOn":[1]}]}""")!!
        val g2 = Chief.graphFromPlan(noReview, sel, false)!!
        assertEquals(AgentRole.REVIEWER, g2.tasks.last().role)
        assertEquals(listOf("t1", "t2"), g2.tasks.last().dependsOn)
        assertTrue(Planner.teamSystem(sel.roles).contains("developer"))
        assertFalse(Planner.teamSystem(sel.roles).contains("planner,"))
    }

    // ------------------------------------------------------------------ context scoping

    @Test fun roleContextIsScopedAndCapped() {
        val big = "x".repeat(5_000)
        val deps = (1..6).map { Subtask("d$it", "dep $it", AgentRole.RESEARCH, status = SubtaskStatus.DONE, output = big) }
        val task = Subtask("t9", "Implement it", AgentRole.DEVELOPER, deps.map { it.id })
        val ctx = RoleContext.build("g".repeat(4_000), task, deps, (1..50).map { "f$it.kt" }, "fix the bug ".repeat(500))
        assertTrue(ctx.length <= RoleContext.MAX + 40)
        assertTrue(ctx.contains("Implement it"))
        assertTrue(ctx.contains("…(truncated)"))
        assertTrue(ctx.contains("more dependency report(s) omitted"))
        assertTrue(ctx.contains("- f30.kt") && !ctx.contains("- f31.kt") && ctx.contains("(20 more)"))
        assertTrue(ctx.contains("## Feedback you must address now"))
        // only the goal, the subtask, dependency reports and files — nothing else
        val small = RoleContext.build("add a button", Subtask("t1", "Add it", AgentRole.DEVELOPER), emptyList(), emptyList())
        assertFalse(small.contains("Results from the roles"))
        assertFalse(small.contains("Relevant files"))
        assertTrue(small.length < 400)
    }

    // ------------------------------------------------------------------ collaboration rules

    @Test fun reassignmentAndEscalationRules() {
        assertEquals(Reassignment.Decision.RETRY, Reassignment.decide(1, false, AgentRole.DEVELOPER, true))
        assertEquals(Reassignment.Decision.REASSIGN_DEBUGGER, Reassignment.decide(2, false, AgentRole.DEVELOPER, true))
        assertEquals(Reassignment.Decision.FAIL, Reassignment.decide(3, true, AgentRole.DEBUGGER, true))
        assertEquals(Reassignment.Decision.FAIL, Reassignment.decide(2, false, AgentRole.DEVELOPER, false)) // Debugger switched off
        assertEquals(Reassignment.Decision.FAIL, Reassignment.decide(2, false, AgentRole.DEBUGGER, true))
        assertEquals(Collaboration.Action.FIX_BY_AUTHOR, Collaboration.onReject(1, false, true))
        assertEquals(Collaboration.Action.REASSIGN_DEBUGGER, Collaboration.onReject(2, false, true))
        assertEquals(Collaboration.Action.ASK_USER, Collaboration.onReject(2, true, true))
        assertEquals(Collaboration.Action.ASK_USER, Collaboration.onReject(2, false, false))
        assertEquals(Collaboration.Action.ASK_USER, Collaboration.onReject(3, false, true))
        val c = TeamConflict(TeamConflict.Kind.FILE, "a.kt", listOf(AgentRole.DEVELOPER, AgentRole.OPTIMIZER), "x")
        assertEquals(Chief.Resolution.REASSIGN_DEBUGGER, Chief.resolve(c, true, false))
        assertEquals(Chief.Resolution.ASK_USER, Chief.resolve(c, true, true))
        assertEquals(Chief.Resolution.ASK_USER, Chief.resolve(c.copy(roles = listOf(AgentRole.DEVELOPER, AgentRole.DEBUGGER)), true, false))
        // the ask card: a single choice; only "stop" stops, a skip continues
        val args = Chief.askArgs(c, true)
        assertEquals("single", args.getJSONArray("questions").getJSONObject(0).getString("kind"))
        assertTrue(Chief.askContinue("""{"answers":{"resolve":{"id":"continue","label":"x"}}}"""))
        assertFalse(Chief.askContinue("""{"answers":{"resolve":{"id":"stop","label":"x"}}}"""))
        assertTrue(Chief.askContinue("""{"skipped":true}"""))
    }

    @Test fun conflictDetectionOnSameFile() {
        val dev = ConflictDetector.Edit("t1", AgentRole.DEVELOPER, FileChange("a.kt", "fun a() = 1\n", "fun a() = 1\nfun cache() = map\n"))
        val opt = ConflictDetector.Edit("t2", AgentRole.OPTIMIZER, FileChange("a.kt", "fun a() = 1\nfun cache() = map\n", "fun a() = 1\n"))
        val conflicts = ConflictDetector.fileConflicts(listOf(dev, opt))
        assertEquals(1, conflicts.size)
        assertEquals("a.kt", conflicts[0].file)
        assertEquals(listOf(AgentRole.DEVELOPER, AgentRole.OPTIMIZER), conflicts[0].roles)
        assertEquals(1, ConflictDetector.newConflicts(listOf(dev, opt), "t2").size)
        assertTrue(ConflictDetector.newConflicts(listOf(dev, opt), "t1").isEmpty())
        // the same role rewriting its own lines, or another role only adding, is no conflict
        val dev2 = dev.copy(taskId = "t1", change = FileChange("a.kt", "fun a() = 1\nfun cache() = map\n", "fun a() = 2\n"))
        assertTrue(ConflictDetector.fileConflicts(listOf(dev, dev2)).isEmpty())
        val docs = ConflictDetector.Edit("t3", AgentRole.DOCUMENTATION, FileChange("a.kt", "fun a() = 1\nfun cache() = map\n", "// doc\nfun a() = 1\nfun cache() = map\n"))
        assertTrue(ConflictDetector.fileConflicts(listOf(dev, docs)).isEmpty())
        // a different file never conflicts; deleting another role's file does
        val other = ConflictDetector.Edit("t2", AgentRole.OPTIMIZER, FileChange("b.kt", "x = 1\n", null))
        assertTrue(ConflictDetector.fileConflicts(listOf(dev, other)).isEmpty())
        val del = ConflictDetector.Edit("t2", AgentRole.TESTER, FileChange("a.kt", "fun a() = 1\nfun cache() = map\n", null))
        assertTrue(ConflictDetector.fileConflicts(listOf(dev, del)).single().detail.contains("deleted"))
    }

    // ------------------------------------------------------------------ per-role cost

    @Test fun perRoleCostAggregationAndEstimate() {
        val prices = listOf(ModelPrice("claude-sonnet-4-5", 3.0, 15.0), ModelPrice("claude-haiku-4-5", 1.0, 5.0))
        val byRole = mapOf(
            AgentRole.DEVELOPER to mapOf("claude-sonnet-4-5" to Usage(1_000_000, 100_000)),
            AgentRole.REVIEWER to mapOf("claude-haiku-4-5" to Usage(200_000, 10_000), "claude-haiku-4-5-x" to Usage(0, 0)),
            AgentRole.RESEARCH to mapOf("mystery-model" to Usage(1_000, 100)),
        )
        val costs = TeamCost.aggregate(byRole, prices)
        assertEquals(listOf(AgentRole.RESEARCH, AgentRole.DEVELOPER, AgentRole.REVIEWER), costs.map { it.role })
        assertEquals(4.5, costs.first { it.role == AgentRole.DEVELOPER }.costUsd!!, 1e-9)
        assertEquals(0.25, costs.first { it.role == AgentRole.REVIEWER }.costUsd!!, 1e-9)
        assertNull(costs.first { it.role == AgentRole.RESEARCH }.costUsd) // unpriced: never guessed
        assertNull(TeamCost.total(costs))
        assertEquals(4.75, TeamCost.total(costs.filter { it.role != AgentRole.RESEARCH })!!, 1e-9)
        // the persisted line round-trips and renders compactly
        val lines = TeamCost.decode(TeamCost.encode(costs))
        assertEquals(3, lines.size)
        assertEquals(1_100_000L, lines.first { it.role == AgentRole.DEVELOPER }.tokens)
        val line = TeamCost.line(lines)
        assertTrue(line.contains("Developer 1100.0k tok $4.50"))
        assertTrue(line.contains("Research 1.1k tok") && !line.contains("Research 1.1k tok $"))
        // estimate: plan size × role tier price; null when a model is unpriced
        val est = TeamCost.estimate(listOf(AgentRole.DEVELOPER, AgentRole.REVIEWER), { if (it == AgentRole.DEVELOPER) "claude-sonnet-4-5" else "claude-haiku-4-5" }, prices)!!
        val dev = 6 * (8_000 * 3.0 + 700 * 15.0) / 1e6
        val rev = 1 * (8_000 * 1.0 + 700 * 5.0) / 1e6
        assertEquals(dev + rev, est, 1e-9)
        assertNull(TeamCost.estimate(listOf(AgentRole.DEVELOPER), { "mystery" }, prices))
        // exposed on the run outcome (empty outside Chief mode)
        assertTrue(RunOutcome(RunOutcome.Kind.DONE).roles.isEmpty())
        assertEquals(costs, RunOutcome(RunOutcome.Kind.DONE, roles = costs).roles)
    }

    @Test fun auditLineCarriesTheRole() {
        val e = AuditEntry(1, Tools.WRITE, Risk.MEDIUM, AuditEntry.Decision.AUTO, true, 3, "a.kt", role = "developer")
        assertEquals("developer", JSONObject(AuditLog.line(e)).getString("role"))
        assertFalse(JSONObject(AuditLog.line(e.copy(role = null))).has("role")) // single-agent lines unchanged
    }

    @Test fun mergedTeamResultIsHonest() {
        val g = TaskGraph(listOf(
            Subtask("t1", "Build", AgentRole.DEVELOPER, status = SubtaskStatus.DONE, output = "Added a.kt", files = listOf("a.kt")),
            Subtask("t2", "Test", AgentRole.TESTER, listOf("t1"), SubtaskStatus.FAILED, note = "tests fail"),
            Subtask("t3", "Review", AgentRole.REVIEWER, listOf("t2"), SubtaskStatus.BLOCKED),
        ))
        val en = TeamResult.merge(g, false)
        assertTrue(en.startsWith("**Team result** — 1 of 3 subtasks done."))
        assertTrue(en.contains("✗ **Tester** · Test") && en.contains("tests fail") && en.contains("⏸ **Reviewer**"))
        assertTrue(en.contains("Files: a.kt"))
        assertTrue(TeamResult.merge(g, true).contains("المطوِّر"))
        assertTrue(TeamLang.isArabic("أضف زر login"))
        assertFalse(TeamLang.isArabic("add a زر button please"))
    }

    // ------------------------------------------------------------------ the runner, with a scripted fake LLM

    /**
     * A fake LLM at the text level: each role answers from its own script, in order. The port
     * plays the session's part: planner text → Planner.parse, developer "tool calls" → real file
     * writes in a temp project, reviewer text → Reviewer.parse. Every context a role received is
     * kept so the test can check what each role saw.
     */
    private class FakeLlm(scripts: Map<AgentRole, List<String>>) {
        private val queues = scripts.mapValues { ArrayDeque(it.value) }.toMutableMap()
        fun next(role: AgentRole): String = queues[role]?.removeFirstOrNull() ?: error("no scripted answer left for ${role.id}")
    }

    private class FakePort(
        val llm: FakeLlm,
        val dir: File,
        val askAnswer: Boolean = true,
        val failRoles: Map<AgentRole, Int> = emptyMap(),
        val workDelayMs: Long = 0,
    ) : TeamPort {
        val contexts = ArrayList<Pair<String, String>>() // task id → context
        val diffs = ArrayList<String>()
        val events = ArrayList<TeamEvent>()
        val asked = ArrayList<TeamConflict>()
        val inFlight = AtomicInteger(0)
        var maxInFlight = 0
        private val fails = HashMap(failRoles)

        override suspend fun work(task: Subtask, context: String): WorkResult {
            contexts += task.id to context
            val now = inFlight.incrementAndGet()
            synchronized(this) { maxInFlight = maxOf(maxInFlight, now) }
            try {
                if (workDelayMs > 0) delay(workDelayMs)
                val left = fails[task.role] ?: 0
                if (left > 0) { fails[task.role] = left - 1; return WorkResult(false, "", failure = "${task.role.id} failed (scripted)") }
                val answer = llm.next(task.role)
                // "WRITE path=content" lines are the fake model's tool calls; the rest is its report
                val changes = answer.lines().filter { it.startsWith("WRITE ") }.map { l ->
                    val (path, content) = l.removePrefix("WRITE ").split("=", limit = 2)
                    val f = File(dir, path)
                    val before = if (f.isFile) f.readText() else null
                    f.writeText(content.replace("\\n", "\n"))
                    FileChange(path, before, f.readText())
                }
                return WorkResult(true, answer.lines().filterNot { it.startsWith("WRITE ") }.joinToString("\n").trim(), changes)
            } finally { inFlight.decrementAndGet() }
        }

        override suspend fun review(task: Subtask, context: String, diff: String): Reviewer.Report? {
            contexts += task.id to context
            diffs += diff
            return Reviewer.parse(llm.next(task.role))
        }

        override suspend fun ask(conflict: TeamConflict): Boolean { asked += conflict; return askAnswer }
        override fun onEvent(event: TeamEvent) { events += event }
    }

    @Test fun acceptanceScenario6PlannerDeveloperReviewerCooperate() = runBlocking {
        val goal = "Add a greeting function in greet.kt then make it say the user's name, and review it"
        val llm = FakeLlm(mapOf(
            AgentRole.PLANNER to listOf("""{"tasks":[{"id":"t1","title":"Write greet()","role":"developer"},
                {"id":"t2","title":"Review greet()","role":"reviewer","dependsOn":["t1"]}]}"""),
            AgentRole.DEVELOPER to listOf(
                "WRITE greet.kt=fun greet() = \"hi\"\\n\nAdded greet() in greet.kt.",
                "WRITE greet.kt=fun greet(name: String) = \"hi \$name\"\\n\nFixed: greet now takes the name.",
            ),
            AgentRole.REVIEWER to listOf(
                """{"verdict":"issues","summary":"greet ignores the name","issues":[{"file":"greet.kt","line":"1","comment":"take a name parameter"}]}""",
                """{"verdict":"ok","summary":"greet uses the name"}""",
            ),
        ))
        val sel = Chief.select(Chief.Intent.MULTI_STAGE, goal)
        assertEquals(listOf(AgentRole.PLANNER, AgentRole.DEVELOPER, AgentRole.TESTER, AgentRole.REVIEWER), sel.roles)
        val selNoTester = sel.copy(roles = sel.roles - AgentRole.TESTER)
        // planner: the fake model's JSON → plan → graph
        val plan = Planner.parse(llm.next(AgentRole.PLANNER))!!
        val graph = Chief.graphFromPlan(plan, selNoTester, false)!!
        assertEquals(listOf(AgentRole.DEVELOPER, AgentRole.REVIEWER), graph.tasks.map { it.role })

        val dir = tmp.newFolder("proj")
        val port = FakePort(llm, dir)
        val runner = ChiefRunner(goal, graph, port, R)
        val out = runner.run()

        assertTrue(out.allDone)
        assertFalse(runner.stopped)
        assertEquals("fun greet(name: String) = \"hi \$name\"\n", File(dir, "greet.kt").readText())
        // the developer ran twice: once on its own, once with the reviewer's feedback
        val devContexts = port.contexts.filter { it.first == "t1" }.map { it.second }
        assertEquals(2, devContexts.size)
        assertFalse(devContexts[0].contains("[Reviewer]"))
        assertTrue(devContexts[1].contains("[Reviewer] greet ignores the name") && devContexts[1].contains("take a name parameter"))
        // the reviewer saw the developer's real diff and report — not the chat
        assertTrue(port.diffs[0].contains("greet.kt") && port.diffs[0].contains("fun greet() = \"hi\""))
        assertTrue(port.diffs[1].contains("fun greet(name: String)"))
        val revCtx = port.contexts.first { it.first == "t2" }.second
        assertTrue(revCtx.contains("Added greet() in greet.kt."))
        assertTrue(port.events.any { it is TeamEvent.Rework })
        assertTrue(runner.conflicts.isEmpty())
        assertEquals("greet uses the name", out["t2"]!!.output)
        assertEquals(listOf("greet.kt"), out["t1"]!!.files)
        assertEquals(100, out.progress())
        assertTrue(TeamResult.merge(out, false).contains("2 of 2 subtasks done"))
    }

    @Test fun reviewerRejectingTwiceGoesToDebuggerThenToTheUser() = runBlocking {
        val issues = """{"verdict":"issues","summary":"still wrong","issues":[{"file":"a.kt","line":"1","comment":"fix it"}]}"""
        val llm = FakeLlm(mapOf(
            AgentRole.DEVELOPER to listOf("WRITE a.kt=v1\nv1", "WRITE a.kt=v2\nv2"),
            AgentRole.DEBUGGER to listOf("WRITE a.kt=v3\nroot cause fixed"),
            AgentRole.REVIEWER to listOf(issues, issues, issues),
        ))
        val g = Chief.defaultGraph(Chief.select(Chief.Intent.EDIT, "rename the title"), false)
        val port = FakePort(llm, tmp.newFolder("p2"), askAnswer = false)
        val runner = ChiefRunner("rename the title", g, port, R)
        val out = runner.run()
        // rejection 1 → author fixes; 2 → Debugger (a conflict); 3 → the user is asked; they chose stop
        assertTrue(port.events.any { it is TeamEvent.Rework })
        assertTrue(out.tasks.any { it.role == AgentRole.DEBUGGER && it.id.startsWith("t2-fix") && it.status == SubtaskStatus.DONE })
        assertEquals(2, runner.conflicts.size)
        assertEquals(1, port.asked.size)
        assertTrue(runner.stopped)
        assertEquals(SubtaskStatus.FAILED, out["t2"]!!.status)
        assertEquals("stopped by the user", out["t2"]!!.note)
        assertNull(out.findCycle())
    }

    @Test fun failingTaskIsRetriedReassignedOnceThenFailedHonestly() = runBlocking {
        val llm = FakeLlm(emptyMap())
        val g = TaskGraph(listOf(Subtask("t1", "Build", AgentRole.DEVELOPER), Subtask("t2", "Review", AgentRole.REVIEWER, listOf("t1"))))
        val port = FakePort(llm, tmp.newFolder("p3"), failRoles = mapOf(AgentRole.DEVELOPER to 2, AgentRole.DEBUGGER to 1))
        val out = ChiefRunner("build", g, port, R).run()
        assertEquals(SubtaskStatus.FAILED, out["t1"]!!.status)
        assertEquals(AgentRole.DEBUGGER, out["t1"]!!.role)
        assertTrue(out["t1"]!!.reassigned)
        assertEquals(3, out["t1"]!!.failures)
        assertEquals(SubtaskStatus.BLOCKED, out["t2"]!!.status)
        assertEquals(1, port.events.count { it is TeamEvent.Reassigned })
        assertEquals(1, port.events.count { it is TeamEvent.Failed })
        assertEquals(3, port.contexts.size) // two developer attempts + one debugger attempt — never more
        // with the Debugger switched off, two failures end it
        val port2 = FakePort(FakeLlm(emptyMap()), tmp.newFolder("p4"), failRoles = mapOf(AgentRole.DEVELOPER to 5))
        val out2 = ChiefRunner("build", g, port2, R - AgentRole.DEBUGGER).run()
        assertEquals(SubtaskStatus.FAILED, out2["t1"]!!.status)
        assertEquals(2, port2.contexts.size)
    }

    @Test fun fileConflictBetweenRolesGoesToDebugger() = runBlocking {
        val llm = FakeLlm(mapOf(
            AgentRole.DEVELOPER to listOf("WRITE a.kt=keep()\\ncache()\nadded cache"),
            AgentRole.OPTIMIZER to listOf("WRITE a.kt=keep()\nremoved the cache"),
            AgentRole.DEBUGGER to listOf("WRITE a.kt=keep()\\ncache()\nrestored with a bounded cache"),
            AgentRole.REVIEWER to listOf("""{"verdict":"ok","summary":"fine"}"""),
        ))
        val g = TaskGraph(listOf(
            Subtask("t1", "Build", AgentRole.DEVELOPER),
            Subtask("t2", "Optimize", AgentRole.OPTIMIZER, listOf("t1")),
            Subtask("t3", "Review", AgentRole.REVIEWER, listOf("t2")),
        ))
        val port = FakePort(llm, tmp.newFolder("p5"))
        val runner = ChiefRunner("optimize", g, port, R)
        val out = runner.run()
        assertEquals(1, runner.conflicts.size)
        assertEquals(TeamConflict.Kind.FILE, runner.conflicts[0].kind)
        val fix = out.tasks.single { it.id.startsWith("t2-fix") }
        assertEquals(AgentRole.DEBUGGER, fix.role)
        assertTrue(fix.id in out["t3"]!!.dependsOn) // the review waited for the reconciliation
        assertTrue(out.allDone)
    }

    @Test fun independentReadOnlyRolesRunConcurrentlyOnlyWhenAllowed() = runBlocking {
        fun graph() = TaskGraph(listOf(Subtask("r1", "Docs", AgentRole.RESEARCH), Subtask("r2", "Design", AgentRole.ARCHITECT)))
        fun llm() = FakeLlm(mapOf(AgentRole.RESEARCH to listOf("found"), AgentRole.ARCHITECT to listOf("designed")))
        val seq = FakePort(llm(), tmp.newFolder("s"), workDelayMs = 80)
        assertTrue(ChiefRunner("g", graph(), seq, R, maxConcurrent = 1).run().allDone)
        assertEquals(1, seq.maxInFlight)
        val par = FakePort(llm(), tmp.newFolder("p"), workDelayMs = 80)
        assertTrue(ChiefRunner("g", graph(), par, R, maxConcurrent = 2).run().allDone)
        assertEquals(2, par.maxInFlight)
        // workers never run side by side, whatever the setting
        val workers = TaskGraph(listOf(Subtask("w1", "A", AgentRole.DEVELOPER), Subtask("w2", "B", AgentRole.DEVELOPER)))
        val w = FakePort(FakeLlm(mapOf(AgentRole.DEVELOPER to listOf("a", "b"))), tmp.newFolder("w"), workDelayMs = 50)
        assertTrue(ChiefRunner("g", workers, w, R, maxConcurrent = 2).run().allDone)
        assertEquals(1, w.maxInFlight)
    }

    @Test fun budgetStopsTheWholeTeamCleanly() = runBlocking {
        var calls = 0
        val g = TaskGraph(listOf(Subtask("t1", "A", AgentRole.DEVELOPER), Subtask("t2", "B", AgentRole.DEVELOPER, listOf("t1"))))
        val base = FakePort(FakeLlm(mapOf(AgentRole.DEVELOPER to listOf("a", "b"))), tmp.newFolder("b"))
        val port = object : TeamPort by base { override fun budgetStop(): Boolean = calls++ >= 1 }
        val runner = ChiefRunner("g", g, port, R)
        val out = runner.run()
        assertTrue(runner.stopped)
        assertEquals(SubtaskStatus.DONE, out["t1"]!!.status)
        assertEquals(SubtaskStatus.PENDING, out["t2"]!!.status) // resumable, not failed
    }

    @Test fun reviewWithNothingToReviewCostsNoCall() = runBlocking {
        val g = TaskGraph(listOf(Subtask("t1", "Look", AgentRole.DEVELOPER), Subtask("t2", "Review", AgentRole.REVIEWER, listOf("t1"))))
        val port = FakePort(FakeLlm(mapOf(AgentRole.DEVELOPER to listOf("nothing to change"))), tmp.newFolder("n"))
        val out = ChiefRunner("g", g, port, R).run()
        assertTrue(out.allDone)
        assertTrue(port.diffs.isEmpty())
        assertEquals("No file changes to review.", out["t2"]!!.output)
    }
}

/** v0.16 settings: Chief mode is off by default, so the classic team behaves exactly as before. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V016SettingsTest {
    private fun settings(): SettingsStore {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsStore(app, SecureStore(app))
    }

    @Test fun defaultsPreserveBehaviourAndClamp() {
        val s = settings()
        assertFalse(s.chiefMode)
        assertEquals(1, s.teamMaxConcurrent)
        assertTrue(s.teamCostEstimate)
        assertTrue(s.teamDisabledRoles.isEmpty())
        assertEquals(AgentRole.entries.toSet(), s.teamEnabledRoles())
        s.teamMaxConcurrent = 9; assertEquals(2, s.teamMaxConcurrent)
        s.teamMaxConcurrent = 0; assertEquals(1, s.teamMaxConcurrent)
        s.teamDisabledRoles = setOf("security", "developer", "chief")
        assertFalse(AgentRole.SECURITY in s.teamEnabledRoles())
        assertTrue(AgentRole.DEVELOPER in s.teamEnabledRoles() && AgentRole.CHIEF in s.teamEnabledRoles())
        s.chiefMode = true; assertTrue(s.chiefMode)
    }
}
