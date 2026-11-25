package com.ctodrs.saqrcode.agent

/**
 * v0.16 «فريق الوكلاء» · the Chief.
 *
 * The Chief decides what kind of request this is (rules first; a cheap model only when the
 * rules are unsure), staffs it with the minimum set of roles, builds the task graph and then
 * runs it ([ChiefRunner]): ready subtasks in dependency order, reviews after the work they
 * review, retries and reassignment on failure, and conflict resolution — escalating to the user
 * through the existing ask_user card when the team cannot settle it alone.
 *
 * [Chief] and [ChiefRunner] are pure (no Android); the session plugs the real model loop in
 * through [TeamPort]. Unit-tested in V016FeaturesTest, including a scripted planner → developer
 * → reviewer run.
 */
object Chief {

    enum class Intent(val id: String) {
        QUESTION("question"), EDIT("edit"), NEW_PROJECT("new_project"), BUG_FIX("bug_fix"),
        RESEARCH("research"), MULTI_STAGE("multi_stage");

        companion object { fun from(id: String?) = entries.firstOrNull { it.id == id?.trim()?.lowercase() } }
    }

    data class Classification(val intent: Intent, val ambiguous: Boolean, val reason: String)

    // ---- signals. English as whole words; Arabic as stems (prefixes و/ف/ال attach to words).
    private fun en(vararg words: String) = Regex("""(?i)\b(${words.joinToString("|")})\b""")
    private fun ar(text: String, stems: List<String>) = stems.any { text.contains(it) }

    private val bugEn = en("bug", "bugs", "fix", "fixes", "crash", "crashes", "crashing", "error", "errors", "exception", "broken",
        "fails", "failing", "failure", "not working", "doesn't work", "does not work", "stack ?trace", "regression", "debug")
    private val bugAr = listOf("خطأ", "اخطاء", "أخطاء", "خلل", "مشكلة", "مشكله", "يتعطل", "تعطل", "انهيار", "ينهار", "إصلاح", "اصلاح", "أصلح", "اصلح",
        "صلّح", "صلح", "عطل", "لا يعمل", "ما يشتغل", "مايشتغل", "ما يعمل", "باق")
    private val newEn = en("new project", "new app", "from scratch", "scaffold", "bootstrap", "start a project", "create an app", "create a new app",
        "build an app", "build a new app", "build me an app", "make an app", "new website", "build a website", "create a website")
    private val newAr = listOf("مشروع جديد", "تطبيق جديد", "من الصفر", "أنشئ تطبيق", "انشئ تطبيق", "ابن تطبيق", "ابني تطبيق", "سوّ لي تطبيق", "سو لي تطبيق",
        "سوي لي تطبيق", "موقع جديد", "أنشئ موقع", "انشئ موقع", "أنشئ مشروع", "انشئ مشروع")
    private val researchEn = en("research", "look up", "latest", "documentation", "docs", "official", "which library", "compare", "best practice",
        "best practices", "search the web", "web search", "changelog", "release notes", "version of")
    private val researchAr = listOf("ابحث", "بحث", "أحدث", "احدث", "التوثيق الرسمي", "قارن", "مقارنة", "أفضل مكتبة", "افضل مكتبة", "أفضل الممارسات", "الإصدار الأخير")
    private val editEn = en("add", "change", "update", "rename", "edit", "implement", "refactor", "remove", "delete", "make", "create", "write",
        "build", "replace", "move", "convert", "support", "integrate", "wire", "migrate", "improve")
    private val editAr = listOf("أضف", "اضف", "ضيف", "أضيف", "عدّل", "عدل", "غيّر", "غير ", "احذف", "أنشئ", "انشئ", "اكتب", "سوّ", "سوي", "نفّذ", "نفذ",
        "طبّق", "طبق", "حوّل", "حول ", "ادمج", "انقل", "استبدل", "حسّن", "حسن ", "أبي", "ابي", "أبغى", "ابغى", "ابغا")
    private val questionStartEn = Regex("""(?i)^\s*(what|why|how|explain|is|are|does|do|can|could|where|which|when|who|should)\b""")
    private val questionAr = listOf("ما ", "ماذا", "لماذا", "ليش", "ليه", "كيف", "اشرح", "هل ", "وش", "ايش", "إيش", "فسّر", "فسر", "متى", "أين", "وين", "ما هو", "ما هي")
    private val multiEn = en("then", "after that", "afterwards", "finally", "and also", "step 1", "first,")
    private val multiAr = listOf("ثم", "بعدها", "بعد ذلك", "وبعدين", "أخيراً", "اخيرا", "أولاً", "اولا")
    private val securityEn = en("login", "log in", "sign in", "signin", "auth", "authentication", "authorization", "oauth", "password", "passwords",
        "token", "tokens", "secret", "secrets", "api key", "apikey", "credential", "credentials", "permission", "permissions", "encrypt", "encryption",
        "crypto", "jwt", "session", "cookie", "cookies", "http", "https", "network", "socket", "request", "requests", "fetch", "upload", "download",
        "shell", "command", "commands", "exec", "sql", "injection", "keystore")
    private val securityAr = listOf("تسجيل الدخول", "تسجيل دخول", "كلمة المرور", "كلمة السر", "كلمات المرور", "توكن", "رمز الدخول", "مفتاح", "صلاحي",
        "تشفير", "الشبكة", "شبكة", "اتصال", "طلبات", "الأوامر", "أوامر", "رفع ملف", "تنزيل")
    private val externalEn = en("documentation", "docs", "official", "latest", "version", "library", "sdk", "api reference", "search the web", "look up", "according to")
    private val externalAr = listOf("التوثيق", "الوثائق", "أحدث", "احدث", "مكتبة", "الإصدار", "ابحث")
    private val docsEn = en("readme", "docstring", "docstrings", "kdoc", "javadoc", "jsdoc", "(write|update|add|improve) (the )?(docs|documentation|guide)",
        "document (the|this|it|how|all)", "usage guide", "api docs")
    private val docsAr = listOf("وثّق", "وثق ", "اكتب التوثيق", "حدّث التوثيق", "حدث التوثيق", "أضف توثيق", "اضف توثيق", "اقرأني", "ملف README", "دليل الاستخدام")
    private val perfEn = en("optimize", "optimise", "optimization", "performance", "faster", "speed up", "slow", "memory usage", "memory leak", "latency", "lag")
    private val perfAr = listOf("أداء", "الأداء", "اداء", "أسرع", "اسرع", "بطيء", "بطء", "تسريع", "الذاكرة", "تحسين الأداء")
    private val deployEn = en("deploy", "deployment", "release(?! notes)", "publish", "ship it", "git push", "push (it|this|the changes|my changes|to)", "commit",
        "build the apk", "build apk", "upload to")
    private val deployAr = listOf("انشر", "نشر", "ارفع", "إصدار", "اصدار", "ادفع", "كومِت", "كوميت")
    private val urlRe = Regex("""https?://\S+""")

    /** Rule-based classification. [Classification.ambiguous] = ask the cheap model (if any). */
    fun classify(request: String): Classification {
        val t = request.trim()
        if (t.isEmpty()) return Classification(Intent.QUESTION, false, "empty request")
        val low = t.lowercase()
        val bug = bugEn.containsMatchIn(t) || ar(t, bugAr)
        val new = newEn.containsMatchIn(t) || ar(t, newAr)
        val research = researchEn.containsMatchIn(t) || ar(t, researchAr) || urlRe.containsMatchIn(t)
        val edit = editEn.containsMatchIn(t) || ar(t, editAr)
        val qForm = questionStartEn.containsMatchIn(t) || questionAr.any { low.startsWith(it) } || t.endsWith("?") || t.endsWith("؟")
        val listLines = t.lines().count { Regex("""^\s*(\d+[.)]|[-*•])\s+\S""").containsMatchIn(it) }
        val multiWords = multiEn.findAll(t).count() + multiAr.count { t.contains(it) }
        val multi = (listLines >= 3 || multiWords >= 2 || (multiWords >= 1 && t.length > 300)) && (edit || new)
        return when {
            new -> Classification(Intent.NEW_PROJECT, false, "asks for a new project/app")
            bug && !(qForm && !edit && !t.contains("fix", true) && !ar(t, listOf("أصلح", "اصلح", "صلح", "إصلاح", "اصلاح"))) ->
                Classification(Intent.BUG_FIX, false, "mentions a bug, error or failure")
            bug -> Classification(Intent.BUG_FIX, true, "asks about an error — explanation or fix?")
            multi -> Classification(Intent.MULTI_STAGE, false, "several stages ($listLines list items, $multiWords sequencing words)")
            qForm && edit -> Classification(Intent.EDIT, true, "question form with a change verb — answer or change?")
            qForm && research -> Classification(Intent.RESEARCH, false, "a question that needs external information")
            qForm -> Classification(Intent.QUESTION, false, "a question")
            edit && research -> Classification(Intent.EDIT, false, "a change that needs external information")
            edit -> Classification(Intent.EDIT, false, "asks for a change")
            research -> Classification(Intent.RESEARCH, false, "asks for external information")
            t.split(Regex("\\s+")).size <= 3 -> Classification(Intent.QUESTION, false, "short message")
            else -> Classification(Intent.EDIT, true, "no clear signal")
        }
    }

    /** The cheap model's one-word classification prompt (used only when [classify] is ambiguous). */
    const val CLASSIFY_SYSTEM = """You classify a request sent to a coding agent. Reply with exactly ONE word from this list and nothing else:
question (wants an explanation or answer, no code change), edit (a change to existing code), new_project (create a new app/project), bug_fix (something is broken), research (needs external information), multi_stage (several distinct stages of work)."""

    fun parseIntent(text: String): Intent? {
        val low = text.lowercase()
        return Intent.entries.firstOrNull { Regex("""\b${it.id}\b""").containsMatchIn(low) }
            ?: if (low.contains("new project") || low.contains("new-project")) Intent.NEW_PROJECT
            else if (low.contains("bug")) Intent.BUG_FIX else null
    }

    data class Selection(val intent: Intent, val roles: List<AgentRole>, val reasons: Map<AgentRole, String>) {
        val noTeam get() = roles.isEmpty()
        /** The main worker: Debugger for bugs, else Developer. */
        val mainWorker: AgentRole? get() = roles.firstOrNull { it == AgentRole.DEBUGGER } ?: roles.firstOrNull { it == AgentRole.DEVELOPER }
            ?: roles.firstOrNull { it.kind == AgentRole.Kind.WORKER }
    }

    /**
     * The minimum set of roles for [intent] on [request], restricted to [enabled]. Pure.
     * question → no team; edit → Developer + Reviewer; bug → Debugger + Tester + Reviewer;
     * new project → Planner + Architect + Developer + Tester + Reviewer; multi-stage → Planner +
     * Developer + Tester + Reviewer; research → Research (+ Developer + Reviewer when it also asks
     * for a change). Security joins when auth/network/secrets/commands are touched; Research when
     * external information is needed; Documentation/Optimizer/Deployment when explicitly asked.
     */
    fun select(intent: Intent, request: String, enabled: Set<AgentRole> = AgentRole.entries.toSet()): Selection {
        val t = request
        val picked = LinkedHashMap<AgentRole, String>()
        fun add(r: AgentRole, why: String) { picked.putIfAbsent(r, why) }
        when (intent) {
            Intent.QUESTION -> return Selection(intent, emptyList(), emptyMap())
            Intent.EDIT -> { add(AgentRole.DEVELOPER, "makes the change"); add(AgentRole.REVIEWER, "checks the change") }
            Intent.BUG_FIX -> {
                add(AgentRole.DEBUGGER, "finds the root cause and fixes it"); add(AgentRole.TESTER, "proves the fix")
                add(AgentRole.REVIEWER, "checks the fix")
            }
            Intent.NEW_PROJECT -> {
                add(AgentRole.PLANNER, "splits the project into steps"); add(AgentRole.ARCHITECT, "designs the structure")
                add(AgentRole.DEVELOPER, "builds it"); add(AgentRole.TESTER, "verifies it"); add(AgentRole.REVIEWER, "checks it")
            }
            Intent.MULTI_STAGE -> {
                add(AgentRole.PLANNER, "orders the stages"); add(AgentRole.DEVELOPER, "implements the stages")
                add(AgentRole.TESTER, "verifies the stages"); add(AgentRole.REVIEWER, "checks the result")
            }
            Intent.RESEARCH -> {
                add(AgentRole.RESEARCH, "gathers the external information")
                if (editEn.containsMatchIn(t) || ar(t, editAr)) { add(AgentRole.DEVELOPER, "applies what was found"); add(AgentRole.REVIEWER, "checks the change") }
            }
        }
        val work = picked.keys.any { it.kind == AgentRole.Kind.WORKER }
        if (work && (securityEn.containsMatchIn(t) || ar(t, securityAr))) add(AgentRole.SECURITY, "touches auth, secrets, network or commands")
        if (work && intent != Intent.RESEARCH && (externalEn.containsMatchIn(t) || ar(t, externalAr) || urlRe.containsMatchIn(t))) add(AgentRole.RESEARCH, "needs external information")
        if (work && (docsEn.containsMatchIn(t) || ar(t, docsAr))) add(AgentRole.DOCUMENTATION, "documentation was asked for")
        if (work && (perfEn.containsMatchIn(t) || ar(t, perfAr))) add(AgentRole.OPTIMIZER, "performance was asked for")
        if (work && (deployEn.containsMatchIn(t) || ar(t, deployAr))) add(AgentRole.DEPLOYMENT, "build/commit/publish was asked for")

        // honour the user's role switches: the Debugger falls back to the Developer, others are dropped
        val out = LinkedHashMap<AgentRole, String>()
        for ((r, why) in picked) {
            when {
                r in enabled -> out.putIfAbsent(r, why)
                r == AgentRole.DEBUGGER -> out.putIfAbsent(AgentRole.DEVELOPER, "fixes the bug (Debugger is switched off)")
            }
        }
        if (work && out.keys.none { it.kind == AgentRole.Kind.WORKER }) out[AgentRole.DEVELOPER] = "does the work"
        val ordered = AgentRoles.all.filter { it in out }
        return Selection(intent, ordered, ordered.associateWith { out[it]!! })
    }

    private fun title(arabic: Boolean, en: String, ar: String) = if (arabic) ar else en

    /** The graph for a selection without a planner (or when the planner's answer is unusable). */
    fun defaultGraph(sel: Selection, arabic: Boolean): TaskGraph {
        val list = ArrayList<Subtask>()
        var n = 0
        fun add(role: AgentRole, en: String, ar: String, deps: List<String>): String {
            val id = "t${++n}"
            list += Subtask(id, title(arabic, en, ar), role, deps)
            return id
        }
        val has = sel.roles.toSet()
        val pre = ArrayList<String>()
        if (AgentRole.RESEARCH in has) pre += add(AgentRole.RESEARCH, "Research what the task needs", "ابحث عمّا تحتاجه المهمة", emptyList())
        if (AgentRole.ARCHITECT in has) pre += add(AgentRole.ARCHITECT, "Design the solution", "صمّم الحل", pre.toList())
        val workers = ArrayList<String>()
        val main = sel.mainWorker
        if (main != null) workers += add(main,
            if (main == AgentRole.DEBUGGER) "Find the root cause and fix it" else "Implement the request",
            if (main == AgentRole.DEBUGGER) "اعثر على السبب الجذري وأصلحه" else "نفّذ الطلب", pre.toList())
        val afterMain = workers.toList().ifEmpty { pre.toList() }
        if (AgentRole.OPTIMIZER in has) workers += add(AgentRole.OPTIMIZER, "Optimize the changed code", "حسّن أداء الكود المعدّل", afterMain)
        if (AgentRole.DOCUMENTATION in has) workers += add(AgentRole.DOCUMENTATION, "Update the documentation", "حدّث التوثيق", workers.toList().ifEmpty { afterMain })
        if (AgentRole.TESTER in has) workers += add(AgentRole.TESTER, "Run and extend the tests", "شغّل الاختبارات ووسّعها", workers.toList().ifEmpty { afterMain })
        val reviews = ArrayList<String>()
        val reviewed = workers.toList().ifEmpty { pre.toList() }
        if (AgentRole.SECURITY in has && workers.isNotEmpty()) reviews += add(AgentRole.SECURITY, "Security review of the changes", "مراجعة أمنية للتغييرات", reviewed)
        if (AgentRole.REVIEWER in has && workers.isNotEmpty()) reviews += add(AgentRole.REVIEWER, "Review the changes", "راجع التغييرات", reviewed)
        if (AgentRole.DEPLOYMENT in has) add(AgentRole.DEPLOYMENT, "Build, commit and publish", "ابنِ وثبّت وانشر", (reviews.ifEmpty { reviewed }).toList())
        return TaskGraph(list)
    }

    /**
     * The planner's graph made safe for the selection: roles outside it become the main worker,
     * missing selected reviews/tests are appended after the work, reviews with nothing to review
     * depend on all workers. Null when the plan has no worker while the selection needs one, or
     * has a cycle — the caller falls back to [defaultGraph].
     */
    fun graphFromPlan(plan: Planner.Plan, sel: Selection, arabic: Boolean): TaskGraph? {
        val main = sel.mainWorker ?: AgentRole.DEVELOPER
        var g = TaskGraph.fromPlan(plan, main, sel.roles.toSet())
        if (g.findCycle() != null) return null
        if (g.tasks.none { it.role.kind == AgentRole.Kind.WORKER } && sel.mainWorker != null) return null
        var n = g.tasks.size
        fun workers() = g.tasks.filter { it.role.kind == AgentRole.Kind.WORKER }.map { it.id }
        // a verdict subtask must come after the work it reviews
        g = TaskGraph(g.tasks.map { t ->
            if (t.role.kind == AgentRole.Kind.VERDICT && g.tasks.none { it.id in t.dependsOn && it.role.kind == AgentRole.Kind.WORKER })
                t.copy(dependsOn = (t.dependsOn + workers().filter { w -> g.tasks.indexOfFirst { it.id == w } < g.tasks.indexOf(t) }).distinct())
            else t
        })
        if (AgentRole.TESTER in sel.roles && g.tasks.none { it.role == AgentRole.TESTER })
            g = g.add(Subtask("t${++n}", title(arabic, "Run and extend the tests", "شغّل الاختبارات ووسّعها"), AgentRole.TESTER, workers()))
        val reviewed = workers()
        if (AgentRole.SECURITY in sel.roles && g.tasks.none { it.role == AgentRole.SECURITY })
            g = g.add(Subtask("t${++n}", title(arabic, "Security review of the changes", "مراجعة أمنية للتغييرات"), AgentRole.SECURITY, reviewed))
        if (AgentRole.REVIEWER in sel.roles && g.tasks.none { it.role == AgentRole.REVIEWER })
            g = g.add(Subtask("t${++n}", title(arabic, "Review the changes", "راجع التغييرات"), AgentRole.REVIEWER, reviewed))
        g = g.sanitized()
        return if (g.findCycle() != null) null else g
    }

    enum class Resolution { REASSIGN_DEBUGGER, ASK_USER }

    /** How the Chief settles a conflict: the Debugger once (when enabled), otherwise the user decides. */
    fun resolve(conflict: TeamConflict, debuggerEnabled: Boolean, debuggerUsed: Boolean): Resolution =
        if (debuggerEnabled && !debuggerUsed && (conflict.kind == TeamConflict.Kind.REJECTED_TWICE || AgentRole.DEBUGGER !in conflict.roles))
            Resolution.REASSIGN_DEBUGGER else Resolution.ASK_USER

    /** The ask_user arguments for a conflict the team cannot settle (the user's language). */
    fun askArgs(conflict: TeamConflict, arabic: Boolean): org.json.JSONObject {
        val prompt = if (arabic) "تعارض في الفريق: ${conflict.detail}. كيف تريد المتابعة؟"
            else "Team conflict: ${conflict.detail}. How should the team continue?"
        val options = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", ASK_CONTINUE).put("label", if (arabic) "تابع بالنسخة الحالية" else "Continue with the current version").put("recommended", true))
            .put(org.json.JSONObject().put("id", ASK_STOP).put("label", if (arabic) "أوقف الفريق لأراجع بنفسي" else "Stop the team so I can look"))
        val q = org.json.JSONObject().put("id", "resolve").put("prompt", prompt).put("kind", "single").put("options", options).put("default", ASK_CONTINUE)
        return org.json.JSONObject().put("title", if (arabic) "القائد يحتاج قرارك" else "The Chief needs your decision")
            .put("questions", org.json.JSONArray().put(q))
    }

    /** The user's answer to [askArgs]: false only when they chose to stop (a skip continues with the default). */
    fun askContinue(result: String): Boolean = !Regex(""""id"\s*:\s*"$ASK_STOP"""").containsMatchIn(result)

    const val ASK_CONTINUE = "continue"
    const val ASK_STOP = "stop"
}

/** What a WORKER or READER run returned. [stop] = the whole team run must stop (budget, user stop). */
data class WorkResult(
    val ok: Boolean,
    val output: String,
    val changes: List<FileChange> = emptyList(),
    val failure: String? = null,
    val stop: Boolean = false,
)

sealed class TeamEvent {
    data class Reassigned(val task: Subtask, val to: AgentRole) : TeamEvent()
    data class Conflict(val conflict: TeamConflict) : TeamEvent()
    data class Failed(val task: Subtask) : TeamEvent()
    data class Rework(val task: Subtask, val round: Int) : TeamEvent()
}

/** The session side of a team run: the real model loops, the approval/ask cards and the budgets. */
interface TeamPort {
    /** Runs a WORKER or READER subtask with its scoped [context]. */
    suspend fun work(task: Subtask, context: String): WorkResult
    /** One VERDICT call (Reviewer/Security) over [diff]; null = the answer was unreadable or the call failed. */
    suspend fun review(task: Subtask, context: String, diff: String): Reviewer.Report?
    /** Asks the user how to settle [conflict]; true = continue. */
    suspend fun ask(conflict: TeamConflict): Boolean
    /** A run budget (steps/time/cost) is exhausted: stop before the next subtask. */
    fun budgetStop(): Boolean = false
    fun onGraph(graph: TaskGraph) {}
    fun onEvent(event: TeamEvent) {}
}

/**
 * Runs a [TaskGraph] through a [TeamPort]: ready subtasks in dependency order (sequentially, or
 * up to [maxConcurrent] ≤ 2 read-only subtasks side by side), each with only the context it
 * needs; reviews with the author-fix → Debugger → user escalation; failed subtasks retried,
 * reassigned once to the Debugger, then failed honestly with their dependents blocked.
 */
class ChiefRunner(
    private val goal: String,
    initial: TaskGraph,
    private val port: TeamPort,
    private val enabled: Set<AgentRole>,
    maxConcurrent: Int = 1,
    /** Files the session already knows are relevant (touched earlier), added to every context. */
    private val knownFiles: List<String> = emptyList(),
) {
    var graph: TaskGraph = initial; private set
    /** Stopped early: budget, the user's stop answer, or a stop from a role run. */
    var stopped = false; private set
    val edits = ArrayList<ConflictDetector.Edit>()
    val conflicts = ArrayList<TeamConflict>()
    private val concurrency = maxConcurrent.coerceIn(1, MAX_PARALLEL)
    private val rejections = HashMap<String, Int>()
    private val debuggerUsed = HashSet<String>()
    private var fixCount = 0
    private val debuggerOn get() = AgentRole.DEBUGGER in enabled

    private sealed class First {
        class Work(val r: WorkResult) : First()
        class Verdict(val report: Reviewer.Report?, val reviewedSomething: Boolean) : First()
    }

    suspend fun run(): TaskGraph {
        var guard = graph.tasks.size * 6 + 10
        while (!stopped && guard-- > 0) {
            if (port.budgetStop()) { stopped = true; break }
            val batch = graph.independentReady(concurrency).takeIf { concurrency >= 2 && it.size >= 2 } ?: graph.ready().take(1)
            if (batch.isEmpty()) break
            batch.forEach { t -> set(t.id) { it.copy(status = SubtaskStatus.RUNNING) } }
            port.onGraph(graph)
            val firsts = if (batch.size == 1) listOf(first(graph[batch[0].id]!!))
                else ParallelPlan.mapBounded(batch.map { graph[it.id]!! }, concurrency) { first(it) }
            for ((t, f) in batch.zip(firsts)) {
                // finished work is never thrown away; a verdict that would start rework waits for the next run
                if (stopped && f is First.Verdict) { set(t.id) { it.copy(status = SubtaskStatus.PENDING) }; continue }
                settle(graph[t.id]!!, f)
            }
            port.onGraph(graph)
        }
        // pending work that can never start (its dependency failed) is blocked, not silently dropped
        if (!stopped) {
            graph.tasks.filter { it.status == SubtaskStatus.FAILED }.forEach { graph = graph.blockDependentsOf(it.id) }
            port.onGraph(graph)
        }
        return graph
    }

    private fun set(id: String, f: (Subtask) -> Subtask) { graph = graph.update(id, f) }

    fun contextFor(t: Subtask, feedback: String? = null): String {
        val deps = t.dependsOn.mapNotNull { graph[it] }
        val files = (deps.flatMap { it.files } + knownFiles).distinct()
        return RoleContext.build(goal, t, deps, files, feedback)
    }

    /** The merged diff a verdict subtask reviews: edits of its ancestors (or all edits so far). */
    fun diffFor(t: Subtask): String {
        val anc = graph.ancestorsOf(t.id).map { it.id }.toSet()
        val mine = edits.filter { e -> e.taskId in anc || anc.any { e.taskId.startsWith("$it-") } }.ifEmpty { edits }
        val merged = LinkedHashMap<String, FileChange>()
        for (e in mine) {
            val prev = merged[e.change.path]
            merged[e.change.path] = if (prev == null) e.change else FileChange(e.change.path, prev.before, e.change.after)
        }
        return if (merged.isEmpty()) "" else Reviewer.diffOf(merged.values.toList())
    }

    private suspend fun first(t: Subtask): First = if (t.role.kind == AgentRole.Kind.VERDICT) {
        val diff = diffFor(t)
        if (diff.isBlank()) First.Verdict(null, false) else First.Verdict(port.review(t, contextFor(t), diff), true)
    } else First.Work(port.work(t, contextFor(t)))

    private fun record(t: Subtask, changes: List<FileChange>, roleOverride: AgentRole? = null) {
        changes.forEach { edits += ConflictDetector.Edit(t.id, roleOverride ?: t.role, it) }
    }

    private suspend fun settle(t: Subtask, f: First) {
        when (f) {
            is First.Work -> settleWork(t, f.r)
            is First.Verdict -> settleVerdict(t, f.report, f.reviewedSomething)
        }
    }

    private suspend fun settleWork(t: Subtask, r: WorkResult) {
        if (r.stop) {
            record(t, r.changes)
            set(t.id) { it.copy(status = SubtaskStatus.PENDING, note = r.failure ?: "stopped") }
            stopped = true
            return
        }
        if (r.ok) {
            record(t, r.changes)
            set(t.id) { it.copy(status = SubtaskStatus.DONE, output = r.output.take(TaskGraphCodec.OUTPUT_KEEP),
                files = (it.files + r.changes.map { c -> c.path }).distinct(), note = "") }
            for (c in ConflictDetector.newConflicts(edits, t.id)) handleConflict(c, graph[t.id]!!)
            return
        }
        record(t, r.changes)
        val failures = t.failures + 1
        val files = (t.files + r.changes.map { it.path }).distinct()
        when (Reassignment.decide(failures, t.reassigned, t.role, debuggerOn)) {
            Reassignment.Decision.RETRY -> set(t.id) { it.copy(status = SubtaskStatus.PENDING, failures = failures, files = files, note = r.failure.orEmpty()) }
            Reassignment.Decision.REASSIGN_DEBUGGER -> {
                set(t.id) { it.copy(status = SubtaskStatus.PENDING, failures = failures, files = files, role = AgentRole.DEBUGGER, reassigned = true,
                    note = "reassigned to Debugger after: ${r.failure.orEmpty()}".take(300)) }
                port.onEvent(TeamEvent.Reassigned(graph[t.id]!!, AgentRole.DEBUGGER))
            }
            Reassignment.Decision.FAIL -> {
                set(t.id) { it.copy(status = SubtaskStatus.FAILED, failures = failures, files = files, output = r.output.take(TaskGraphCodec.OUTPUT_KEEP),
                    note = r.failure ?: "failed") }
                graph = graph.blockDependentsOf(t.id)
                port.onEvent(TeamEvent.Failed(graph[t.id]!!))
            }
        }
    }

    /** The newest DONE worker this verdict subtask reviews (the author of the change). */
    private fun authorOf(t: Subtask): Subtask? =
        graph.ancestorsOf(t.id).lastOrNull { it.role.kind == AgentRole.Kind.WORKER && it.status == SubtaskStatus.DONE && it.files.isNotEmpty() }
            ?: graph.ancestorsOf(t.id).lastOrNull { it.role.kind == AgentRole.Kind.WORKER && it.status == SubtaskStatus.DONE }

    private suspend fun settleVerdict(t: Subtask, first: Reviewer.Report?, reviewedSomething: Boolean) {
        var report = first
        var round = 0
        while (true) {
            if (report == null || report.ok) {
                val out = when {
                    !reviewedSomething -> "No file changes to review."
                    report == null -> "The review returned no readable verdict."
                    else -> report.summary.ifBlank { "OK" }
                }
                set(t.id) { it.copy(status = SubtaskStatus.DONE, output = out, note = if (report == null && reviewedSomething) "unverified review" else it.note) }
                return
            }
            val rep: Reviewer.Report = report
            if (++round > Collaboration.MAX_REVIEW_ROUNDS) {
                set(t.id) { it.copy(status = SubtaskStatus.FAILED, output = Reviewer.feedbackMessage(rep).take(TaskGraphCodec.OUTPUT_KEEP), note = "issues remain after ${round - 1} reviews") }
                port.onEvent(TeamEvent.Failed(graph[t.id]!!))
                return
            }
            val n = (rejections[t.id] ?: 0) + 1
            rejections[t.id] = n
            val feedback = Reviewer.feedbackMessage(rep)
            val author = authorOf(t)
            var action = Collaboration.onReject(n, t.id in debuggerUsed, debuggerOn)
            if (action == Collaboration.Action.FIX_BY_AUTHOR && author == null) action = if (debuggerOn && t.id !in debuggerUsed) Collaboration.Action.REASSIGN_DEBUGGER else Collaboration.Action.ASK_USER
            when (action) {
                Collaboration.Action.FIX_BY_AUTHOR -> {
                    port.onEvent(TeamEvent.Rework(author!!, n))
                    set(author.id) { it.copy(status = SubtaskStatus.RUNNING) }
                    port.onGraph(graph)
                    val r = port.work(graph[author.id]!!, contextFor(graph[author.id]!!, feedback))
                    record(author, r.changes)
                    set(author.id) { it.copy(status = SubtaskStatus.DONE, files = (it.files + r.changes.map { c -> c.path }).distinct(),
                        output = if (r.ok && r.output.isNotBlank()) r.output.take(TaskGraphCodec.OUTPUT_KEEP) else it.output,
                        note = if (r.ok) it.note else "rework after review failed: ${r.failure.orEmpty()}".take(300)) }
                    if (r.stop) { set(t.id) { it.copy(status = SubtaskStatus.PENDING) }; stopped = true; return }
                }
                Collaboration.Action.REASSIGN_DEBUGGER -> {
                    debuggerUsed += t.id
                    val c = TeamConflict(TeamConflict.Kind.REJECTED_TWICE, null, listOfNotNull(author?.role, t.role),
                        "${t.role.nameEn} rejected the work twice: ${rep.summary}".take(300), listOfNotNull(author?.id, t.id))
                    conflicts += c
                    port.onEvent(TeamEvent.Conflict(c))
                    val fix = Subtask("${t.id}-fix${++fixCount}", "Fix the ${t.role.nameEn.lowercase()} issues: ${rep.summary}".take(140), AgentRole.DEBUGGER,
                        listOfNotNull(author?.id), SubtaskStatus.RUNNING)
                    graph = TaskGraph(graph.tasks.flatMap { if (it.id == t.id) listOf(fix, it.copy(dependsOn = (it.dependsOn + fix.id).distinct())) else listOf(it) })
                    port.onGraph(graph)
                    val r = port.work(fix, contextFor(fix, feedback))
                    record(fix, r.changes)
                    set(fix.id) { it.copy(status = if (r.ok) SubtaskStatus.DONE else SubtaskStatus.FAILED, output = r.output.take(TaskGraphCodec.OUTPUT_KEEP),
                        files = r.changes.map { c -> c.path }.distinct(), note = r.failure.orEmpty()) }
                    if (r.stop) { set(t.id) { it.copy(status = SubtaskStatus.PENDING) }; stopped = true; return }
                }
                Collaboration.Action.ASK_USER -> {
                    val c = TeamConflict(TeamConflict.Kind.REJECTED_TWICE, null, listOfNotNull(author?.role, t.role),
                        "${t.role.nameEn} still reports issues: ${rep.summary}".take(300), listOfNotNull(author?.id, t.id))
                    conflicts += c
                    port.onEvent(TeamEvent.Conflict(c))
                    if (port.ask(c)) {
                        set(t.id) { it.copy(status = SubtaskStatus.DONE, output = feedback.take(TaskGraphCodec.OUTPUT_KEEP), note = "accepted by the user with open issues") }
                    } else {
                        set(t.id) { it.copy(status = SubtaskStatus.FAILED, output = feedback.take(TaskGraphCodec.OUTPUT_KEEP), note = "stopped by the user") }
                        stopped = true
                    }
                    return
                }
            }
            if (port.budgetStop()) { set(t.id) { it.copy(status = SubtaskStatus.PENDING) }; stopped = true; return }
            report = port.review(graph[t.id]!!, contextFor(graph[t.id]!!), diffFor(graph[t.id]!!))
        }
    }

    private suspend fun handleConflict(c: TeamConflict, t: Subtask) {
        conflicts += c
        port.onEvent(TeamEvent.Conflict(c))
        when (Chief.resolve(c, debuggerOn, "file:${c.file}" in debuggerUsed)) {
            Chief.Resolution.REASSIGN_DEBUGGER -> {
                debuggerUsed += "file:${c.file}"
                val fix = Subtask("${t.id}-fix${++fixCount}", "Reconcile the conflicting edits in ${c.file}".take(140), AgentRole.DEBUGGER, listOf(t.id))
                // everything waiting on t now also waits on the reconciliation
                val waiting = graph.dependentsOf(t.id)
                graph = TaskGraph(graph.tasks.flatMap { x ->
                    when {
                        x.id == t.id -> listOf(x, fix)
                        x.id in waiting && t.id in x.dependsOn -> listOf(x.copy(dependsOn = (x.dependsOn + fix.id).distinct()))
                        else -> listOf(x)
                    }
                })
            }
            Chief.Resolution.ASK_USER -> if (!port.ask(c)) stopped = true
        }
    }

    companion object { const val MAX_PARALLEL = 2 }
}
