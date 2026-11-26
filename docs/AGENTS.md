# فريق الوكلاء · Saqr Agent Team (v0.16.0)

> وضع القائد (Chief mode) خلف مفتاح في الإعدادات ← «فريق الوكلاء · Agent team»، ومُطفأ افتراضياً: فريق «مخطّط ← مبرمج ← مراجع» الكلاسيكي يبقى كما هو تماماً حتى تفعّله.
>
> Chief mode sits behind a switch in Settings → «فريق الوكلاء · Agent team» and is **off by default**: the classic Planner → Coder → Reviewer team is unchanged until you turn it on. It applies in team mode only.

Code: `agent/AgentRoles.kt` (registry), `agent/Chief.kt` (classification, selection, graph building, `ChiefRunner`), `agent/TaskGraph.kt` (graph + codec), `agent/TeamCollab.kt` (context scoping, reassignment, escalation, conflicts, per-role cost, merged result), wired into `AgentSession` (`chiefLoop`, `workerLoop`, `readerLoop`, `verdictCall`). Tests: `V016FeaturesTest`, `V016SettingsTest`.

## 1. الأدوار · Roles

Each role has a bilingual name, a focused system-prompt fragment, an allowed tool subset (from `ToolRegistry`), a preferred model tier and its own step cap. How a role runs depends on its kind:

- **WORKER** — the regular agent loop on a scoped history; may change files, always through the existing approval cards, checkpoints, command policy and audit log.
- **READER** — an isolated read-only loop with its own small history; only read-only SAFE tools ever execute, so two readers may run side by side.
- **VERDICT** — one strict-JSON review call (the existing `Reviewer` format) over the team's merged diff.
- **PLANNING** — no tools (the Chief's classification call and the Planner's JSON plan).

| Role · الدور | Kind | Tier | Max steps | Tools allowed |
|---|---|---|---|---|
| Chief · القائد | PLANNING | cheap | 1 | none |
| Planner · المخطِّط | PLANNING | cheap | 1 | none |
| Research · الباحث | READER | cheap | 8 | list_files, read_file, search, project_search, web_search, web_fetch (+ MCP tools) |
| Architect · المعماري | READER | strong | 6 | list_files, read_file, search, project_search, git_status, git_diff, git_log |
| Developer · المطوِّر | WORKER | strong | 25 | read/list/search/project_search, git_status/diff/log, write_file, edit_file, delete_file, run_command, lint_project, run_tests, ask_user, memory_save (+ MCP tools) |
| Debugger · المصحِّح | WORKER | strong | 20 | read/list/search/project_search, git_status/diff/log, write_file, edit_file, run_command, lint_project, run_tests, ask_user (+ MCP tools) |
| Security · الأمن | VERDICT | independent review | 1 | read-only (list/read/search/project_search, git_diff) |
| Tester · المختبِر | WORKER | cheap | 12 | read/list/search/project_search, write_file, edit_file, run_command, lint_project, run_tests |
| Reviewer · المراجِع | VERDICT | independent review | 1 | read-only (list/read/search/project_search, git_diff) |
| Optimizer · المحسِّن | WORKER | strong | 12 | read/list/search/project_search, write_file, edit_file, run_command, lint_project, run_tests |
| Documentation · التوثيق | WORKER | cheap | 8 | read/list/search/project_search, write_file, edit_file |
| Deployment · النشر | WORKER | cheap | 10 | read/list/search/project_search, git_status/diff/log, lint_project, run_tests, run_command, git_commit, git_branch, git_push |

- A call outside a role's subset is refused with an error the model reads, and is audited as `blocked` with the role.
- `git_push` stays HIGH risk and **always** shows the approval card; `git_commit` keeps its card; HIGH-risk shell commands keep the v0.15 command-policy card. No role can approve anything.
- **Model tiers** map onto the user's own models through Falcon Router: *strong* = the coder's model; *cheap* = the router's cheapest capable light model (or the coder's model when the router is off or cannot prove a saving; the Planner/Chief honour `plannerModel`); *independent review* = `reviewModel` when set, else the router's reviewer pick. v0.17: with `smartRouter` on, tiers go through the Smart Router instead — cheapest capable model across configured providers, the coder unless a capability is missing, and a reviewer different from the coder ([docs/ROUTER.md](ROUTER.md)); the Debugger also receives matching error → solution lessons ([docs/MEMORY.md](MEMORY.md)).

## 2. اختيار الأدوار · How the Chief picks roles

1. **Intent, rules first** (`Chief.classify`, Arabic and English keywords): question / edit / new project / bug fix / research / multi-stage. Only when the rules are unsure (e.g. a question form with a change verb, or no signal at all) does the Chief ask the cheap model for one word (`Chief.CLASSIFY_SYSTEM`).
2. **Minimum roles** (`Chief.select`, pure, each pick with its reason):

| Intent | Roles |
|---|---|
| question | **no team** — the classic single loop answers |
| edit | Developer + Reviewer |
| bug fix | Debugger + Tester + Reviewer |
| new project | Planner + Architect + Developer + Tester + Reviewer |
| multi-stage | Planner + Developer + Tester + Reviewer |
| research | Research (+ Developer + Reviewer when it also asks for a change) |

   Added only when the request calls for them: **Security** (auth, passwords, tokens, secrets, network, commands, SQL…), **Research** (official docs, latest versions, libraries, URLs), **Documentation** (README/docs explicitly asked), **Optimizer** (performance), **Deployment** (deploy/release/commit/push).
3. **Your switches win:** a role switched off in Settings is dropped (a switched-off Debugger is replaced by the Developer). The Chief and the Developer are always available.

## 3. مخطط المهام · Task graph

- Subtasks: `id`, `title`, `role`, `dependsOn`, `status` (pending / running / blocked / done / failed), failures, reassigned flag, capped report, files.
- With the Planner selected, the planner's JSON may carry `role` and `dependsOn` per task (`{"id":"t2","title":"…","role":"developer","dependsOn":["t1"]}`); plans without them still parse exactly as before (titles only, max 6). Roles outside the selection become the main worker; missing selected tests/reviews are appended after the work; a cycle or a plan without any work step falls back to the Chief's default graph (with a notice).
- Cycle detection, topological order, ready set, independent read-only set and progress % are pure functions (`TaskGraph`).
- The graph is saved inside `.saqr/tasks/<id>.json` (`"team"` field); older task files without it decode unchanged. After the app is killed, resume continues from the saved graph (a subtask cut off mid-run goes back to pending).
- The plan pill shows the graph: role-prefixed titles, running = in progress, ✗ failed, ⏸ blocked.

## 4. التنفيذ · Execution

- Ready subtasks run in dependency order, **one at a time by default**. With *Parallel read-only roles* on, up to **2** independent read-only subtasks (Research/Architect readers, Reviewer/Security verdicts) run side by side. Workers never run concurrently.
- **Scoped context** (`RoleContext.build`): each role receives only the goal (≤1,500 chars), its subtask, the reports of the subtasks it depends on (≤1,200 chars each, ≤4,000 total), a relevant-files list (≤30) and any feedback it must address — never the whole chat. Hard cap 9,000 chars.
- Reviews read the merged diff of the work they depend on; with nothing changed, the review is skipped (no model call).
- The subtask reports are merged into one honest team result (done / failed / blocked per subtask, files changed).

## 5. التعاون · Collaboration

- **Rejected review:** 1st rejection → the author reworks with the reviewer's issues; 2nd → a conflict: the Chief assigns the Debugger once; still rejected → the user decides on the existing ask_user card (continue / stop).
- **File conflict:** a later role removing lines another role added (or deleting its file) is a conflict → the Debugger reconciles once (dependents wait for it); if the Debugger is off, already used, or part of the conflict → the user decides.
- **Failed subtask** (loop guard, step cap, error): retried once, then reassigned **once** to the Debugger, then marked failed — its dependents are blocked, and the result says so.

## 6. التكلفة · Cost

- Tokens and priced spend are recorded per role and model in the existing Usage tracking (session meter, ledger, Falcon savings for routed calls) and exposed as `RunOutcome.roles`; a compact per-role line ("Developer 12.3k tok $0.0120 · Reviewer 2.1k tok $0.0010") closes each team run in the chat.
- *Cost estimate before start* (on by default in Chief mode): subtasks × typical steps per role kind (workers 6, readers 3, verdicts 1) × ~8k input / 700 output tokens per step × each role's model price. A model without a price is reported, never guessed.

## 7. مع النواة المستقلة · With the Phase 2 core

- The completion gate still runs before the final DONE (lint + tests over everything the team changed); failures go to the Debugger (or the Developer when the Debugger is off), up to 2 rounds, and the status is reported honestly.
- Step / minute / cost budgets apply to the whole team run; a budget stop leaves unfinished subtasks pending (resumable), never failed.
- The audit log records the role of every tool call (`"role":"developer"`); single-agent lines are unchanged.
- The loop guard runs per role (fresh evidence for each role); a role it stops counts as a failed attempt.

## 8. الإعدادات · Settings (فريق الوكلاء · Agent team — shown in team mode)

| Key | Default | Meaning |
|---|---|---|
| `chiefMode` | off | Chief mode; off = the classic Planner → Coder → Reviewer team |
| `teamMaxConcurrent` | 1 | 1 = sequential; 2 = two independent read-only roles side by side |
| `teamCostEstimate` | on | estimate notice before a Chief-mode run |
| `teamDisabledRoles` | none | per-role switches (Chief and Developer cannot be switched off) |

## Skills in the team (v0.18) · المهارات في الفريق
Installed skills ([docs/SKILLS.md](SKILLS.md)) are chosen once per run — the ones the user named as `$skill-id`, plus (opt-in `autoAttachSkills`) the top-N relevant ones by BM25 — and each choice is written to the audit log with its reason. In Chief mode a role-bound skill (`role: "reviewer"`) joins only that role's prompt (worker, reader and verdict calls); an unbound skill joins every working role. The tools a skill may use in a role are its declared permissions ∩ the role's subset (never HIGH, never above its ceiling) — skills never widen a role's tool list, and approvals and the command policy apply unchanged.
