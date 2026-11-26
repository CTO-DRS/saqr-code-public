# خطة التنفيذ · Execution plan

المصدر الوحيد للاتجاه: [ROADMAP.md](../ROADMAP.md) — هذه الوثيقة تحمل تفاصيل تنفيذ الإصدار الجاري فقط، وتُفتح من جديد عند كل شحن.

The single source of direction is [ROADMAP.md](../ROADMAP.md); this file holds the current release's execution details only.

---

## الإصدار الجاري · Current release: 0.14 «أجنحة مفتوحة · Open Wings» (فبراير 2027)

الترتيب حسب الأولوية: P0 أولًا ثم P1 ثم P2، وكل بند يُشحن إصدارًا فرعيًا كاملًا (0.14.0، 0.14.1 …) ببنود قبوله.

Order follows priority: P0 first, then P1, then P2; each item ships as its own complete minor release (0.14.0, 0.14.1 …).

### 1. حزم المهارات · Skill packs (P0)
- حزمة `.saqrpack` تنصب قالبًا وأوامر مخصصة وقواعد AGENTS.md وأدوات مفضلة بنقرة.
- A `.saqrpack` bundle installs a template, custom commands, AGENTS.md rules and preferred tools in one tap.
- [x] تثبيت حزمة «Flask» يضف قالبًا وأوامرًا جاهزة في ≤ 5 ثوانٍ. — شُحن في 0.14.0
- [x] Installing a «Flask» pack adds a template and ready commands in ≤ 5 seconds. — shipped in 0.14.0
- [x] القالب يظهر في الشاشة الرئيسية ويُنشئ مشروعًا كامل الملفات، والقواعد تُحقن في AGENTS.md ويقرأها الوكيل تلقائيًا. — شُحن في 0.14.0
- [x] The template shows on the home screen and creates a full project; rules land in AGENTS.md and the agent picks them up automatically. — shipped in 0.14.0
- [x] الأوامر تُدمج دون كسر المدمجة أو تكرار الاسم (توحيد عربي)، والاستيراد بالصق أو بعينة جاهزة، والإزالة تحذف القالب لا الأوامر المثبتة. — شُحن في 0.14.0
- [x] Commands merge without breaking builtins or duplicating names (Arabic-aware); import by paste or a ready sample; removal deletes the template only, not the installed commands. — shipped in 0.14.0

### 2. MCP عبر OAuth · MCP over OAuth (P1)
- دخول PKCE لخوادم MCP المحمية من الإعدادات.
- PKCE sign-in for protected MCP servers from Settings.
- [x] ربط خادم يتطلب OAuth واكتشاف أدواته كاملة. — شُحن في 0.14.1
- [x] Connect a server that requires OAuth and discover its full toolset. — shipped in 0.14.1

### 3. بناء سحابي اختياري (BYOC) · Bring-your-own-compute cloud build (P1)
- للهواتف الضعيفة: تشغيل البناء على GitHub Actions في مستودع المستخدم نفسه، وإعادة APK للمعاينة والتثبيت داخل التطبيق.
- For weak phones: run the build on GitHub Actions inside the user's own repository, and return the APK for in-app preview and install.
- [x] بناء قالب Compose سحابيًا في ≤ 15 دقيقة مع سجل مباشر. — شُحن في 0.14.2
- [x] Build a Compose template in the cloud in ≤ 15 minutes with a live log. — shipped in 0.14.2

### 4. معرض المجتمع · Community gallery (P1)
- معرض قوالب ومشاريع عربية يُسحب من مستودع GitHub عام، والمساهمة عبر PR.
- A gallery of Arabic templates and projects pulled from a public GitHub repository, with PR-based contribution.
- [x] تصفح وتثبيت قالب من المعرض بنقرة. — شُحن في 0.14.3
- [x] Browse and install a template from the gallery in one tap. — shipped in 0.14.3

### 5. مزامنة عبر المستودع · Repository-based sync (P2)
- تصدير/استيراد الإعدادات والثيمات والأوامر المخصصة عبر ملف أو مستودع خاص مشفر — بلا خادم مركزي.
- Export/import settings, themes and custom commands via a file or an encrypted private repository — no central server.
- [x] إعداد جهاز ثانٍ في ≤ دقيقتين. — شُحن في 0.14.4
- [x] Set up a second device in ≤ 2 minutes. — shipped in 0.14.4

### 6. صقر الحارس · Saqr Guard (P2)
- مهام مجدولة محلية: اختبارات ليلية، تقرير صباحي بإشعار، مراقبة فروقات مستودع المستخدم.
- Local scheduled tasks: nightly tests, a morning report notification, watching the user's repository for changes.
- [ ] مهمة مجدولة تعمل في وقتها ويصل تقريرها.
- [ ] A scheduled task runs on time and its report arrives.

### 7. ويجت وبلاطة · Widget and Quick Tile (P2)
- ويجت حالة المهمة الجارية على الشاشة الرئيسية + Quick Tile لتشغيل/إيقاف الوكيل.
- A home-screen widget for the running task + a Quick Tile to start/stop the agent.
- [ ] تحديث لحظي وإيقاف بنقرة.
- [ ] Live updates and one-tap stop.

### 8. استوديو بصري v1 · Visual studio v1 (P2)
- توسيع انتقاء العناصر إلى تحرير مباشر (لون، مسافة، نص) من المعاينة يُترجم بدقة إلى الكود.
- Extend element picking into direct editing (color, spacing, text) from the preview, translated faithfully to code.
- [ ] تعديل لون/مسافة من المعاينة يعدّل السطر الصحيح.
- [ ] A color/spacing edit from the preview lands on the right line.

---

## المرحلة 2 · Phase 2: 0.15.0 «النواة المستقلة · Autonomous Core»

امتداد لنواة الوكيل الموجودة — لا إعادة بناء: AgentSession وTools وSafetyPolicy وLoopDetector وTeam وFalconRouter وMemoryBank وCheckpoints وLlmClient كما هي، والجديد طبقات صغيرة نقية مختبرة في `V015FeaturesTest` وكل ميزة خلف مفتاح في الإعدادات.

Extends the existing agent core — nothing rebuilt; every new piece is a small pure object tested in `V015FeaturesTest`, and every feature sits behind its own Settings switch.

| # | البند · Item | الملف · File | المفتاح · Setting (default) | الحالة |
|---|---|---|---|---|
| 1 | سجل الأدوات · Tool registry (risk, readOnly, timeout, retries; unknown names refused) | `agent/ToolRegistry.kt` | — (always on, no behaviour change) | ✅ |
| 2 | قراءات متوازية · Parallel read-only calls (≤3, per-tool timeout, ordered results) | `ToolRegistry.kt` (`ParallelPlan`), `AgentSession.executeTools` | `parallelTools` (on) | ✅ |
| 3 | سياسة أوامر متدرجة · Tiered command policy + approval card | `agent/CommandPolicy.kt` | `confirmRiskyCommands` (on) | ✅ |
| 4 | حارس الحلقات v2 · LoopDetector v2 | `agent/LoopDetector.kt` | `smartLoopGuard` (on) | ✅ |
| 5 | بوابة الإنهاء · Completion gate (team mode) | `agent/Autonomy.kt` (`CompletionGate`) | `verifyBeforeDone` (on, team only) | ✅ |
| 6 | إعادة المحاولة والبديل · Retry + fallback model | `Autonomy.kt` (`LlmRetry`) | `retryLlm` (on), `fallbackModel` (none) | ✅ |
| 7 | حالة المهمة والميزانيات · TaskState + budgets | `agent/TaskState.kt`, `Autonomy.kt` (`Budgets`) | `taskJournal` (on), `maxSteps` 40, `maxMinutes` 0, `maxCostUsd` 0 | ✅ |
| 8 | سجل التدقيق · Audit log | `TaskState.kt` (`AuditLog`) | `auditLog` (on) | ✅ |
| 9 | المحتوى غير الموثوق · Untrusted-content envelope | `Autonomy.kt` (`Untrusted`) | `untrustedMarking` (on) | ✅ |

**التحقق · Verification:** CI على GitHub Actions (`.github/workflows/ci.yml`: unit tests + lint + debug APK) — مع ملاحظة أن تشغيلات المستودع الخاص محجوبة حالياً بحدّ فوترة الحساب؛ نتائج هذا الإصدار من تشغيل Gradle فعلي في بيئة البناء.

**المتبقي · Remaining (Phase 3):** Chief/dispatcher by capability & cost, debugger/security/tester roles; cross-project long-term memory with relevance retrieval and an errors→fixes store; delete_file approval under the risky-command switch; gallery pack versioning/permissions; LICENSE file and repo hygiene (tracked APKs in dist/).

## المرحلة 3 · Phase 3: 0.16.0 «فريق الوكلاء · Agent Team & Orchestration»

امتداد لفريق «مخطّط ← مبرمج ← مراجع» دون إعادة بنائه: وضع القائد اختياري ومُطفأ افتراضياً، والقطع الجديدة نقية مختبرة في `V016FeaturesTest`. التفاصيل: [docs/AGENTS.md](AGENTS.md).

Extends the classic Planner → Coder → Reviewer team without rebuilding it: Chief mode is optional and off by default; the new pieces are pure and tested in `V016FeaturesTest`. Details: [docs/AGENTS.md](AGENTS.md).

| # | البند · Item | الملف · File | المفتاح · Setting (default) | الحالة |
|---|---|---|---|---|
| 1 | سجل الأدوار · Roles registry (12 roles, tools, tier, steps) | `agent/AgentRoles.kt` | `teamDisabledRoles` (none) | ✅ |
| 2 | القائد · Chief: intent + minimal roles | `agent/Chief.kt` | `chiefMode` (off) | ✅ |
| 3 | مخطط المهام · Task graph (+ planner role/dependsOn, TaskState) | `agent/TaskGraph.kt`, `Planner.kt`, `TaskState.kt` | — | ✅ |
| 4 | التنفيذ · Execution (dependency order, ≤2 read-only in parallel, scoped context) | `Chief.kt` (`ChiefRunner`), `TeamCollab.kt` (`RoleContext`), `AgentSession` | `teamMaxConcurrent` (1) | ✅ |
| 5 | التعاون · Reviews, conflicts, reassignment | `TeamCollab.kt`, `ChiefRunner` | — | ✅ |
| 6 | التكلفة · Cost per role + estimate | `TeamCollab.kt` (`TeamCost`), `RunOutcome.roles` | `teamCostEstimate` (on) | ✅ |
| 7 | مع المرحلة 2 · Gate, budgets, audit role, loop guard per role | `AgentSession` | (Phase 2 switches) | ✅ |
| 8 | الإعدادات · Settings «فريق الوكلاء · Agent team» | `ui/TeamSettings.kt` | — | ✅ |

**المتبقي · Remaining:** on-device QA of Chief mode with real providers (the runner is tested with a scripted fake LLM; no instrumented UI test ran); CI confirmation once GitHub Actions minutes are available; Phase 4 — cross-project memory with relevance retrieval and an errors→fixes store, model-performance-aware dispatch, gallery pack versioning/permissions.

## المرحلة 4 · Phase 4: 0.17.0 «محرك الذاكرة والموجّه · Memory Engine & Model Router»

امتداد لبنك الذاكرة (v0.13) وموجّه الصقر (v0.13.2) دون إعادة بنائهما: كل ما يغيّر ما يراه النموذج أو يوجّه إلى نموذج آخر خلف مفتاح مُطفأ افتراضياً، والقطع الجديدة نقية مختبرة في `V017FeaturesTest`. التفاصيل: [docs/MEMORY.md](MEMORY.md) · [docs/ROUTER.md](ROUTER.md).

Extends the memory bank (v0.13) and Falcon Router (v0.13.2) without rebuilding them: anything that changes what the model sees or routes to another model is behind a switch that is off by default; the new pieces are pure and tested in `V017FeaturesTest`.

| # | البند · Item | الملف · File | المفتاح · Setting (default) | الحالة |
|---|---|---|---|---|
| 1 | طبقة الجلسة · Rolling session summary (stored with the session) | `agent/memory/MemoryEngine.kt` (`SessionMemory`), `ChatPersistence.kt` | `sessionSummary` (off) | ✅ |
| 2 | طبقة المشروع · Typed entries, files, source, migration v1→v2, cap 200 | `agent/MemoryBank.kt` | — | ✅ |
| 3 | الدروس · Error→solution lessons on gate FAIL→PASS, dedupe, Debugger surfacing | `MemoryEngine.kt` (`Lessons`), `AgentSession` | `memoryLessons` (on) | ✅ |
| 4 | الطبقة العامة · Long-term cross-project preferences, isolation | `MemoryBank` (GLOBAL layer), `MemoryEngine` | `globalMemory` (off) | ✅ |
| 5 | الاسترجاع · BM25 top-K with Arabic normalization | `agent/memory/MemoryRecall.kt` | `memoryRelevance` (off), `memoryTopK` (8) | ✅ |
| 6 | الملفات القديمة · Stale file refs via ProjectIndex invalidation | `ProjectIndex.kt`, `MemoryBank.refreshStale` | — | ✅ |
| 7 | ورقة الذاكرة · Layers, search, type filter, delete, clear, make global | `ui/chat/MemorySheet.kt` | — | ✅ |
| 8 | حارس الأسرار · Secret guard on every layer | `MemoryEngine.kt` (`MemorySafety`) | — | ✅ |
| 9 | الموجّه الذكي · Capability table, tiers, independent reviewer, vision, context, availability | `agent/ModelRouter.kt`, `SettingsStore.routableProviders` | `smartRouter` (off) | ✅ |
| 10 | سلسلة البدائل · Failover chain (+ acceptance 12) | `ModelRouter.kt` (`Failover`), `AgentSession.completeStep` | `smartRouter` (off) | ✅ |
| 11 | الإنفاق · Daily/monthly caps, real-price estimate range, decision log | `ModelRouter.kt` (`SpendCap`, `CostEstimate`), `AgentSession` | `dailySpendCap`, `monthlySpendCap` (0 = off) | ✅ |
| 12 | الإعدادات · Settings «محرك الذاكرة والموجّه» | `ui/MemoryRouterSettings.kt` | — | ✅ |

**المتبقي · Remaining:** optional provider embeddings (not built — BM25 only); on-device QA of the router, failover, session summary and lesson capture with real providers (unit-tested with synthetic data and a fake client only); adding the new settings to the sync whitelist; CI confirmation once GitHub Actions minutes are available.

## المرحلة 5 · Phase 5: 0.18.0 «مهارات صقر والمعرض · Agent Skills & Gallery»

امتداد لحزم المهارات (v0.14) ومعرض المجتمع (v0.14.3) دون إعادة بنائهما: قسم `skills` اختياري في الصيغة نفسها، ومسار تثبيت واحد لكل المصادر، وكل ما يغيّر ما يراه النموذج تلقائياً خلف مفتاح مُطفأ افتراضياً. التفاصيل: [docs/SKILLS.md](SKILLS.md).

Extends skill packs (v0.14) and the community gallery (v0.14.3) without rebuilding them: an optional `skills` section in the same format, one install pipeline for every source, and anything that changes what the model sees automatically behind a switch that is off by default.

| # | البند · Item | الملف · File | المفتاح · Setting (default) | الحالة |
|---|---|---|---|---|
| 1 | صيغة المهارة · Skill format in .saqrpack (v2 only with skills), semver, changelog | `agent/skills/SkillModel.kt`, `data/SaqrPack.kt` | — | ✅ |
| 2 | المدير · Install/update/uninstall/enable/private/rate/usage/export/import/search | `agent/skills/SkillManager.kt` | — | ✅ |
| 3 | الأمان · Static checks, permission ceiling, SHA-256 install/update | `agent/skills/SkillSafety.kt`, `CanonicalJson` | — | ✅ |
| 4 | التشغيل · `$id` mentions, BM25 auto-attach, envelope, role intersection, audit | `agent/skills/SkillRuntime.kt`, `AgentSession`, `SystemPrompt` | `skillsEnabled` (on), `autoAttachSkills` (off), `skillsTopN` (2) | ✅ |
| 5 | المعرض · Optional entry fields, integrity, update available | `data/Gallery.kt` | — | ✅ |
| 6 | الواجهة · «المهارات · Skills» section + manager sheet; gallery card | `ui/SkillsSettings.kt`, `ui/SettingsScreen.kt` | — | ✅ |
| 7 | الاختبارات · V018FeaturesTest + V018SettingsTest incl. acceptance 11 | `app/src/test/…` | — | ✅ |
| 8 | مستودع المعرض · LICENSE (MIT), CI validator, first real skill (PR) | `CTO-DRS/saqr-gallery` | — | ✅ (merged; 4 more Arabic skills added) |

**المتبقي · Remaining:** skills in the encrypted sync whitelist; on-device QA of the manager sheet and of skills in real runs (unit-tested; the gallery fetch is tested against MockWebServer only); CI confirmation once GitHub Actions minutes are available (the gallery workflow included).

## المرحلة 6 · Phase 6: 0.19.0 «المزامنة الكاملة والتحقق الميداني · Full Sync & Field Proof»

| # | البند · Item | الملف · File | الحالة |
|---|---|---|---|
| 1 | مزامنة مفاتيح v0.15–v0.18 (27 مفتاحًا سلوكيًا وحدّيًا؛ لا أسرار ولا نصوص مهارات) · Sync the v0.15–v0.18 behaviour switches and limits (27 keys; no secrets, no skill bodies) | `data/Sync.kt` | ✅ |
| 2 | اختبارات المزامنة · round trip, older-snapshot safety, broken values, secret probe | `V019SyncTest.kt` | ✅ |
| 3 | مهارات المزامنة المشفّرة · skills in the encrypted snapshot (needs a size cap and a safety re-check on apply) | `data/Sync.kt`, `agent/skills/SkillSafety.kt` | ✅ v0.19.2 |
| 4 | تضمينات المزوّد الاختيارية للاسترجاع · optional provider embeddings for memory retrieval | `agent/memory/` | ☐ |
| 5 | توجيه حسب الأداء الفعلي · routing by measured model performance | `agent/ModelRouter.kt` | ☐ |
| 6 | اختبار ميداني على جهاز · on-device QA of memory, router and skills with real providers | — | ☐ (needs a device) |
| 7 | فحص lint لـ 0.18/0.19 · lint run (needs a build machine with more RAM than 2 GB) | — | ☐ |

## معايير الجودة لكل إصدار · Quality bar for every release
- كل ميزة جديدة باختبارات وحدة (ملف `V0xFeaturesTest.kt` مكانه القائمة).
- Every feature ships with unit tests (keep the `V0xFeaturesTest.kt` pattern).
- السلاسل نصية ثنائية اللغة قبل الدمج (values + values-ar).
- Bilingual strings (values + values-ar) land in the same commit.
- تحديث README وCHANGELOG وROADMAP في نفس الـ commit الأخير.
- README / CHANGELOG / ROADMAP updated with the release commit.

## بعد 0.14 · After 0.14
- تُفتح هذه الوثيقة مجددًا لـ 1.0 «الانقضض»: إثبات ميداني نهائي، إطلاق Google Play، أداء واستقرار، تدقيق أمني، توثيق وإطلاق، ترحيب جديد.
- This file reopens for 1.0 «The Strike»: final field proof, Google Play launch, performance & stability, security audit, docs & launch, a fresh onboarding.

---

## Phase 7 · خارطة الطريق حسب النظام · Roadmap by system (approved 2026-11-26)

Names: **Majlis** (مجلس, chat) · **Raqeem** (editor/AI edit) · **Diwan** (git) · **Hisn** (security) · **Souq** (skill gallery) · **Warsha** (project workshop/build) · **Wasl** (sync/backup) · **Bawsala** (growth/learn).
Rules for every item: small verified commits, tests, EN+AR, RTL, a11y roles, 48dp targets, mirror synced, AR+EN screenshots per milestone; device-bound parts go on the device-QA list.

### Majlis (chat), in order
1. Deliverable file card (zip/apk/export; open, share, save; `sandbox:` link resolver) — priority bug. 2. Per-message action bar (copy, like/dislike, read aloud via a `TtsSpeaker` interface, share) + sources row (المصادر). 3. Regenerate, edit-and-resend. 4. In-conversation search, transcript export. 5. Render audit (RTL, streaming, scroll, input bar). 6. Prompt library. 7. Smart suggestion chips. 8. Chat branching. 9. Attachments to the model (images, files; vision per provider). 10. Completion notifications (display is device-QA). 11. Live preview in chat (WebView, device-QA).
Done: v0.19.4 link-in-bold RTL fix.

### Top 8 (after the Majlis queue)
1. Plan Mode · 2. Arabic voice input · 3. Project memory file · 4. Secret scanner before commit/push (Hisn/Diwan) · 5. AI commit messages (Diwan) · 6. Checkpoints and one-tap rollback · 7. Diff review with per-hunk accept/reject · 8. One-tap deploy.

### Remaining groups (ordered by impact and dependency)
- **Majlis:** chat memory, pins, share.
- **Warsha:** templates, image-to-UI, backend presets, APK build.
- **Raqeem:** inline AI edit, repo map.
- **Diwan:** merge-conflict resolver.
- **Souq:** ratings and badges.
- **Hisn:** biometric lock, keystore vault, audit log.
- **Wasl:** sync/backup.
- **Bawsala:** growth items, including Arabic learn mode.

Choices to record when built: STT/TTS use the Android system engines behind interfaces (most reliable, no key); deploy target picked and noted at implementation.
