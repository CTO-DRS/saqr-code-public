# Changelog · سجل التغييرات

## [0.1.20] — 2026-11-27 — Screen-reader fix in Memory · إصلاح قارئ الشاشة

- **Accessibility:** the delete button on each memory entry had no label for TalkBack and was a 30dp target. It is now announced as "Delete" (button) with a 48dp target. The same scan found three more unlabeled icon-only controls, now fixed: the Memory "empty" and "add" buttons (also 48dp now) and the preview reload button.
- **Tests:** `V0123IconA11yScanTest` scans the source so an unlabeled icon-only clickable cannot come back.
- versionName 0.1.20, versionCode 59.

## [0.1.19] — 2026-11-27 — Readable light theme · الثيم الفاتح أوضح

- **Contrast fix (accessibility):** an audit of all three built-in themes found two pairs under WCAG AA in the light theme: gold link/label text on the background (4.3:1) and tertiary text (3.0:1). Both are slightly darker now (4.8:1 and 3.8:1). Dark and night themes already passed.
- **Tests:** `V0122ThemeContrastTest` locks AA for every built-in theme (reading text 4.5:1 on all surfaces, accent text and button labels 4.5:1, status colors and tertiary text 3:1); `V0120StoreResilienceTest` covers the session store against corrupt index, truncated files and orphan entries.
- versionName 0.1.19, versionCode 58.

## [0.1.18] — 2026-11-27 — Errors that explain themselves · أخطاء تشرح نفسها

- **Plain-words failures:** when a turn fails (no internet, timeout, wrong API key, rate limit, out of quota, provider outage, TLS), the error card now says what happened and what to do, in the app language, with the raw detail kept underneath. Unknown errors still show the raw text.
- EN + AR, RTL.
- **Tests:** `V0119ErrorExplainerTest`: offline, timeout, 401/403/429/402/5xx, quota vs rate limit, TLS, unknown, ordinal round trip.
- versionName 0.1.18, versionCode 57.

## [0.1.17] — 2026-11-27 — Learning mode (Arabic, line by line) · وضع التعلّم

- **Learning mode:** a switch in Settings → Autonomy. When on, every change Saqr makes ends with a line-by-line walkthrough: what each line does and why, one concept per bullet, new terms defined once, a two-line summary and a small exercise. Explanations are in Arabic when the app is Arabic; code stays in English. The work itself (tools, edits, verification) is unchanged.
- **/learn «علمني»:** teaches the current file or selection line by line, read-only.
- EN + AR strings, RTL.
- **Tests:** `V0118LearningModeTest`: the section appears only when on, language flag, work-unchanged wording, /learn and /علمني parse and stay read-only.
- versionName 0.1.17, versionCode 56.

## [0.1.16] — 2026-11-27 — Live preview · معاينة حية

- **Auto-refresh:** the preview pane now reloads itself (debounced) whenever the workspace changes — agent edits, git operations — so the page on screen is always the current code. A toolbar toggle turns live refresh on or off; it defaults on.
- **Reloads keep their place:** the WebView restores its scroll position after a refresh instead of jumping to the top.
- **Pick the entry file:** when a project has more than one HTML file, tapping the path in the toolbar opens a chooser (index.html first, then shallowest, then name); switching reloads the preview.
- EN + AR, RTL, a11y labels on the new controls.
- **Tests:** `V0117LivePreviewTest`: index-first ordering, shallowest-then-name, node_modules/.git skipped, .htm counted, empty and non-HTML projects.
- versionName 0.1.16, versionCode 55.

## [0.1.15] — 2026-11-27 — Per-task cost + daily spend cap · تكلفة المهمة وحد يومي

- **This task, priced:** the Usage sheet gains a live “This task” row — active time, steps, files touched, commands, tokens and estimated cost — refreshing every two seconds while the agent works, kept after the run ends.
- **Daily budget:** Settings → Usage &amp; budget gains a daily cap on top of the monthly one. Same rules: a warning banner at 80%, an explicit confirm before sending at 100%. The banner shows whichever cap is closer to its limit, and the confirmation dialog names the budget it tripped (monthly wins when both are over).
- **Usage sheet** shows a second progress bar for the daily cap next to the monthly one. EN + AR, RTL, western digits.
- **Tests:** `V0116BudgetDailyTest`: daily thresholds, the confirmation matrix (daily-only, monthly-only, both, neither), banner fractions, zeroed run stats.
- versionName 0.1.15, versionCode 54.

## [0.1.14] — 2026-11-27 — Compare two model answers · قارن النماذج

- **/compare «قارن»:** type /compare (or /قارن) followed by a question and two models answer it side by side — the current model against any other configured provider, switchable in the dialog. Plain completions, no tools, no file changes.
- **Each card shows** the provider/model label, elapsed time, tokens and estimated cost (from the user's price table; unknown prices drop out); answers are selectable text, errors show per side, and a missing second provider explains itself instead of failing.
- EN + AR, RTL, 48dp targets.
- **Tests:** `V0115ModelDuelTest`: cost math against PriceTable (including prefix-matched versions), stats line composition, label, unknown-price and missing-usage dropouts.
- versionName 0.1.14, versionCode 53.

## [0.1.13] — 2026-11-27 — Project rules (AGENTS.md), nested and editable · قواعد المشروع

- **Nested AGENTS.md:** the root rules file always applies, and when the session touches files, the AGENTS.md of each directory on their path applies too — labeled by directory, each file capped, the whole collection budgeted. Paths trying to escape the project are ignored.
- **The agent maintains the rules:** the system prompt now tells Saqr to update the right AGENTS.md with edit_file when the user sets a durable project convention or asks to remember one.
- **Edit rules in the app:** the chat overflow menu gains "Project rules" — a dialog that edits the project's AGENTS.md directly, seeded from the starter template when the file does not exist yet. EN + AR.
- **Tests:** `V0114ProjectRulesTest` (6): root always applies, nested on touched paths, ancestors root-down, missing/empty skipped, total budget cap, escape paths ignored.
- versionName 0.1.13, versionCode 52.

## [0.1.12] — 2026-11-27 — Arabic voice commands (Gulf dialects) · أوامر صوتية خليجية

- **Hands-free voice commands:** in wake mode, «يا صقر» followed by a command now runs it in the app instead of sending it as a prompt: stop «وقف / بس / خلاص / بطل», send «أرسل / ابعت», undo to the previous branch «تراجع / رجّع», regenerate «عيد / مرة ثانية», new chat «محادثة جديدة», sessions «المحادثات», read the last answer aloud «اقرأ / اسمعني», stop the reader «اسكت», clear the composer «امسح», and hands-free off «طفّي المايك / وقف الاستماع». A small English set (stop, send, undo, new chat…) works too.
- **Dialect-tolerant:** every phrase matches after Arabic normalization (harakat stripped, alef forms unified), so recognizer spellings of the same Gulf words all land; only a whole utterance commands, so a prompt that merely contains «وقف» still reaches the agent untouched.
- **State-aware:** stop needs a running turn, mute needs the reader speaking, undo/regenerate need history; anything else answers "not available now" with a toast. Typed text in the composer survives a command and «أرسل» sends it.
- **Tests:** `V0113VoiceCommandsTest` (6): Gulf spellings, dialect variants, English set, no substring hijacking, harakat/alef normalization, state gating.
- versionName 0.1.12, versionCode 51.

## [0.1.11] — 2026-11-27 — PR review, merge and undo from the phone · مراجعة ودمج الطلبات

- **A new PRs tab in Git (ديوان)** when origin is a GitHub repo: every open pull request with its number, title, head → base, author and draft badge.
- **Review on the phone:** a PR opens its counts (files, +/−, mergeable) and "View changes" renders the whole diff in the existing diff viewer, with old/new line numbers.
- **Merge from the phone:** merge commit, squash or rebase — confirmed in a dialog that warns the merge happens on GitHub right away. Drafts and conflicted PRs refuse.
- **Undo:** the merge that Saqr just made stays in a gold banner with "Undo merge" — safe while the base branch tip is still that merge commit (re-checked on every load and right before undoing); the record survives restarts. Once the branch moves on, the banner says so instead.
- EN + AR, RTL, 48dp targets, labelled controls.
- **Tests:** `V0112PrMergeTest` (6): payload parsing, combined diff through the viewer parser, undo safety states, merge-record persistence, the merge API (pre-merge head + merge commit captured), the force ref reset.
- versionName 0.1.11, versionCode 50.

## [0.1.10] — 2026-11-27 — Background completion report · تقرير الإنجاز

- **The completion notification now carries the run's report.** When a task you started finishes while the app is closed, the notification expands to a second line: active time, steps, files changed, commands run, tokens and priced cost — e.g. "3:12 · 14 steps · 3 files · 2 commands · 12.4k tokens · $0.03". Unknown prices and empty parts drop out; estimated token counts show "~".
- Proper Arabic counts (1 «خطوة», 2 «خطوتان», 5 «خطوات», 14 «خطوة»; «12.4 ألف رمز», «2.3 مليون رمز»), western digits everywhere, LRM-wrapped so numbers and "$" keep their order inside Arabic text.
- The background machinery itself (foreground service, wake/Wi-Fi locks, stop/open actions, resume after kill or reboot) is unchanged from v0.6; this closes the "report on completion" half of the background-agents item.
- **Tests:** `V0111CompletionReportTest` (6).
- versionName 0.1.10, versionCode 49.

## [0.1.9] — 2026-11-27 — Plan mode · وضع الخطة

- **Plan first (Settings → Team).** When on, the agent drafts its plan and shows it as an editable card before touching anything: edit a step, reorder, remove, add, then "Run plan" or Cancel. The coder gets the plan you approved.
- Cancel stops the turn with nothing done. A plan card left open when the app dies is cancelled on restore, never hangs the run.
- Short or slash requests skip planning; a planner failure continues unplanned.
- EN + AR, RTL, 48dp controls, labelled buttons. Setting syncs.
- **Tests:** `V0110PlanModeTest` (4).
- versionName 0.1.9, versionCode 48.

## [0.1.8] — 2026-11-26 — Majlis 8: chat branching · تفرّع المحادثة

- **Edit and regenerate keep the old answer.** Everything from the edited (or regenerated) message on is saved as a branch. A "‹ 2/3 ›" switcher under that message flips between versions, including the tool rows and approvals of each. The model always continues from the branch on screen.
- `Branches` / `BranchOps` are pure and tested: fork, attach, undo (when a send does not happen), switch with wrap-around, branches inside branches.
- Branches live in memory for the session. After an app restart you keep the branch you were on.
- 48dp targets, labels EN + AR, arrows mirror in RTL.
- **Tests:** `V0109BranchingTest` (5).
- versionName 0.1.8, versionCode 47.

## [0.1.7] — 2026-11-26 — Developer system · منظومة المطور

- **QR sheet sharing:** Share link (native share sheet with the URL) and Share QR image (PNG rendered on the device, shared through the app's FileProvider; no network).
- **Share the app** (invitation text with the releases page) and **Share all accounts** (one message listing all 8).
- **Banner carousel** above the card, auto-advancing every 5 seconds with dots. Content is data: edit `app/src/main/assets/developer_banners.json` (id, title and body as ar/en, optional https url, two gradient colours) to announce a new app or an offer. Bad items are skipped.
- **Version and update check:** shows the installed version and a "Check for updates" button that reads the newest release of the public project; it says up to date, or offers the release page. Versions restarted at 0.1.x, so it compares the newest published release with the installed one by equality.
- **Tests:** `V0107DeveloperShareTest` (3), `V0108DeveloperExtrasTest` (6), `V0105DeveloperCardTest` (3 + share buttons).
- versionName 0.1.7, versionCode 46.

## [0.1.6] — 2026-11-26 — Developer links QR sheet · نافذة الرمز

- Tapping an account in the developer card opens a glass sheet with an on-device QR code, Open and Copy link (see 0.1.5 notes). versionName 0.1.6, versionCode 45.

## [0.1.5] — 2026-11-26 — About the developer · عن المطور

- **About the developer card** in Settings, under About: circular photo, name "CTO.DRS" with a verified badge, a role line, a footer with the app version, and eight brand-coloured icon links with labels (WhatsApp, Telegram, TikTok, Instagram, X, YouTube, GitHub, website). Tapping the photo opens it large. Tapping an account opens a glass sheet with a QR code generated on the device (own encoder, `QrCode`, byte mode, level M, best-of-8 masks) plus Open and Copy link buttons. Each is labelled for screen readers and opens in the browser or the app. EN + AR, RTL, dark and light.
- New filled brand glyphs (`Brands`, Simple Icons, CC0). `DeveloperLinks` holds the URLs as plain data.
- **Tests:** `V0105DeveloperCardTest` (3): exact URLs, 48dp labelled buttons, a tap opens the QR sheet, Open/Copy fire. `V0106QrCodeTest` (2): sizes, finders, all 8 links. All 8 matrices were also decoded with OpenCV in the sandbox and match their URLs.
- versionName 0.1.5, versionCode 44.

## [0.1.4] — 2026-11-26 — Majlis 7: smart suggestions · الاقتراحات الذكية

- **Next-step chips** appear under a finished reply, chosen from what the turn did: a failed command, test or lint run offers "Fix the error"; edited files offer "Run tests" (only if none ran after the last edit), "Review changes" and "Commit" (not after a commit); a long answer (500+ characters) with no edits offers "Summarize" and "Next steps". Tapping a chip puts the text in the message box (review and commit use the existing `/review` and `/commit`), so you can edit before sending.
- Rule based on purpose: no extra model call, no cost, instant. `FollowUps` is pure and tested. Numbered-option chips from the reply (existing) win over these.
- All chips (old and new) now have a 48dp touch target. EN + AR.
- **Tests:** `V0104FollowUpsTest` (5).
- versionName 0.1.4, versionCode 43.

## [0.1.3] — 2026-11-26 — Majlis 6: prompt library · مكتبة الأوامر

- **Prompt library:** the + menu in the chat has a new "Prompt library" item. It opens a searchable sheet of every prompt command, built-in and your own; tapping one puts `/name ` in the message box so you add the details. Search matches name, Arabic alias and description, ignoring Arabic diacritics and alef forms.
- **Five new built-in prompts:** `/refactor` (حسن), `/document` (وثق), `/perf` (اداء), `/secure` (امن), `/debug` (تتبع). The documentation one is `/document`, not `/docs`, so it cannot shadow a custom `/docs` command you already made.
- Command names stay left-to-right inside Arabic screens (found in the AR render and fixed). Rows are 56dp, labelled, EN + AR.
- **Tests:** `V0103PromptLibraryTest` (4); slash-command expectations updated for the new built-ins.
- versionName 0.1.3, versionCode 42.

## [0.1.2] — 2026-11-26 — Touch targets + haptics · أهداف اللمس والاهتزاز

- **48dp:** every clickable box under 48dp (tab close in Panes and Terminal, terminal direct-mode switch, Git toast and row actions, skill rating, remove-host and others, 36 sites) now keeps its look but gets a 48dp minimum touch target. `V0102TouchTargetGuardTest` fails the build if a new one is added without it.
- **Haptics:** all pressable buttons give a light tick, switches tick on change, closing a tab gives a pop, and confirming a project delete gives a heavy tap. Uses the existing semantic tokens, so the system haptics setting still rules.
- Tab rows may grow slightly where the close button was under 48dp. Real feel and spacing are device-QA.
- versionName 0.1.2, versionCode 41.

## [0.1.1] — 2026-11-26 — Render audit 2: sizes · تدقيق الأحجام

- **Fix:** the floating search/export pill covered the first message at the top of the chat on every size; the list now starts below it.
- **Tablets:** the conversation and composer keep a 780dp readable column, centered, instead of stretching edge to edge.
- Render checks at 320dp (small phone) and tablet width in AR + EN added to the screenshot harness. The harness window stays about 847px wide, so the tablet column cap is verified by code, not by a wide render (device-QA).
- versionName 0.1.1, versionCode 40.

## [0.1.0] — 2026-11-26 — Version numbering reset · إعادة ترقيم الإصدارات

- By the owner's decision, the project is numbered from v0.1.0 going forward. Entries below (0.19.x and older) are the pre-reset history and their tags and releases stay as they were.
- versionName 0.1.0. versionCode continues upward (39) because Android refuses to install an update with a lower versionCode over an existing install.
- Includes the unreleased screen-size render checks (small 320dp and tablet 800dp, AR + EN) in the screenshot harness.

## [0.19.9] — 2026-11-26 — Render audit 1: streaming cost · تدقيق الأداء

- **Streaming:** Markdown for a streaming reply is re-parsed at most every 90 ms instead of on every token (`StreamThrottle`); the final text always renders immediately when the reply ends.
- **Recomposition:** inline spans are cached per paragraph, so unrelated recompositions (scroll, action bar state) no longer re-parse every paragraph.
- Measured: parsing a 15k-char mixed Arabic reply takes about 4 ms on the build box, so the parser is not the bottleneck; the gain is fewer parse and layout passes per second of streaming.
- **Tests:** `V0199PerfTest` (2). 539 tests green. Real frame timing needs a device (device-QA list).
- versionName 0.19.9, versionCode 38.

## [0.19.8] — 2026-11-26 — Majlis 5: search + export · البحث والتصدير

- **Search in chat:** a search icon on the chat opens a bar with match counter, next and close; it jumps to each matching message. Matching ignores case, Arabic diacritics and tatweel, and folds alef/ya forms (minimum 2 characters).
- **Export:** a share button sends the whole conversation as Markdown.
- `Transcript` helper (search, normalize, toMarkdown). Buttons are 48dp with role and label, EN + AR.
- **Tests:** `V0198TranscriptTest` (3), `V0198SearchUiTest` (2). 537 tests green.
- versionName 0.19.8, versionCode 37.

## [0.19.7] — 2026-11-26 — Majlis 4: regenerate · edit and resend · إعادة التوليد والتعديل

- **Regenerate:** a button in the action bar of the last reply (hidden while a turn runs) re-runs the last user message; the old reply, tool rows and approvals are dropped from the conversation.
- **Edit and resend:** a pencil under each of your messages opens a dialog; sending replaces that message and continues from it, dropping everything after it.
- Files the discarded attempt already changed stay on disk; the model is told so (and to re-read files before editing) through the existing one-shot note.
- `AgentSession.regenerate()` / `editAndResend()` and the pure `Rewind` helpers. Both buttons are 48dp with role and label, EN + AR.
- **Tests:** `V0197RewindTest` (5). Branch-preserving edit (keeping the old branch) is the later "chat branching" item.
- versionName 0.19.7, versionCode 36.

## [0.19.6] — 2026-11-26 — Majlis 3: action bar + sources · شريط الإجراءات والمصادر

- **Per-message action bar** under the reply that ends a turn: copy, good answer, bad answer (toggle, announced as selected), read aloud (existing speaker), share. Each button is 48dp with a role and a label, EN + AR.
- **«المصادر» row**: chips (icon + short label) for the files read or changed, commands run, web pages, git actions, memory and checks of that turn (since the last user message, max 8, plan and ask_user not counted). Each chip is announced as "kind: label".
- Icons added: thumbs-up, thumbs-down, share-2. Feedback is in memory for the session (not persisted yet).
- **Tests:** `V0196ActionBarTest` (5): sources logic, 48dp size, labels, toggle and selected state, RTL locale.
- versionName 0.19.6, versionCode 35.

## [0.19.5] — 2026-11-26 — Majlis 2: file cards · بطاقات الملفات

- **Bug (user report):** files the agent made (zip, apk, ...) were only mentioned as a sandbox path. Now an assistant reply that names a file existing in the project shows a **file card** under the message (icon, name, size, Open, Share) through the FileProvider (`projects/` added to file_paths).
- `Deliverables.find/resolve` is pure: it never leaves the project folder (no `..`, no outside absolute paths), maps `sandbox:/workspace/...` and Linux paths by name, dedupes, caps at 5 cards.
- `sandbox:` links no longer become dead web links; they render as a file label and the card opens the file.
- **Tests:** `V0195FileCardTest` (5), `V0194MarkdownLinkTest` updated.
- versionName 0.19.5, versionCode 34.

## [0.19.4] — 2026-11-26 — Majlis 1: markdown link fix · إصلاح الروابط داخل التنسيق

- **Bug (user report):** `**[file.zip](sandbox:/workspace/file.zip)**` (a link inside bold) printed raw bracket text, which Arabic bidi reordered into `sandbox:/workspace/)[file.zip](...`. Bold, italic and strike now parse their content recursively, so the link is a real bold link showing only its label.
- **Tests:** `V0194MarkdownLinkTest` (2).
- versionName 0.19.4, versionCode 33.

## [0.19.3] — 2026-11-26 — Accessibility roles · أدوار إمكانية الوصول

- UI/UX pass 1: every icon-only click target (about 30 across Git, Accounts, Skills, Home, Terminal, Panes, Settings, Sessions, Chat, Element picker) now declares a semantic Role, so TalkBack announces button / checkbox / radio / switch. Star rating announces "Rate N of 5" (EN + AR). Remove-host button gained a label.
- **Tests:** `V0193A11yTest` (2): EN/AR key parity and a source guard that fails if a new icon-only clickable has no role.
- versionName 0.19.3, versionCode 32.

## [0.19.2] — 2026-11-26 — Skills in Sync · المهارات في المزامنة

- Installed **public** skills now travel in the encrypted snapshot (private skills never leave the device). Capped at 200,000 chars (dropped from the end). On apply they go through `SkillManager.plan/apply`, so `SkillSafety` re-checks every skill (injection markers, HIGH-risk permissions are refused); merge only, local skills are never deleted, `MAX_INSTALLED` respected. Older snapshots without skills change nothing.
- **Tests:** `V0192SkillSyncTest` (5).
- versionName 0.19.2, versionCode 31.

## [0.19.1] — 2026-11-26 — Remove API key · حذف المفتاح

- **Buried feature surfaced:** `SettingsStore.clearKey` existed with no caller, so a saved API key could only be overwritten. Each provider card in Settings now shows «حذف المفتاح · Remove key» when a key is saved; it drops only that provider's key and keeps its URL and model (EN + AR strings).
- **Tests:** `V0191KeyRemovalTest` (2).
- versionName 0.19.1, versionCode 30.

## [0.19.0] — 2026-11-26 — Full Sync · المزامنة الكاملة (Phase 6)

- **Sync whitelist (`data/Sync.kt`):** 27 new keys so a second device gets the v0.15–v0.18 behaviour: parallel tools, risky-command confirmation, loop guard, verify-before-done, LLM retry + fallback model, task journal, audit log, untrusted marking, step/minute/cost budgets, Chief mode, team concurrency/cost estimate/disabled roles, memory relevance/top-K/session summary/lessons/global memory, smart router, daily/monthly spend caps, skills on/off and auto-attach. Still no API keys, tokens, entitlement or installed-skill bodies.
  27 مفتاحًا جديدًا في المزامنة المشفّرة (إعدادات سلوكية وحدود فقط).
- Older snapshots without the new keys leave this device's values untouched; broken numbers are ignored and every setter still clamps.
- **Tests:** `V019SyncTest` (4): round trip, older snapshot, broken values, secret/skill-body probe.
- **Security hygiene (earlier today):** release signing key and license key rotated, `keystore/` and `keystore.properties` scrubbed from git history; see `docs/RELEASE_KEYS.md`. The V011 paywall test now signs with the build's own key.
- versionName 0.19.0, versionCode 29.

## [0.18.0] — 2026-11-25 — Agent Skills & Gallery · مهارات صقر والمعرض (Phase 5)

### Skills · المهارات
- **Format (`data/SaqrPack.kt`, `agent/skills/SkillModel.kt`):** an optional `skills` section in `.saqrpack` (`saqrpack: 2`); a skill = id, name/nameAr, description, category, tags, semver version, author, instructions (≤ 4000 chars), optional role binding, declared permissions + risk ceiling (SAFE/MEDIUM), changelog. Optional pack-level `version`/`author`. Packs without skills are still written/read as v1, a v1 document's `skills` key is ignored, and a v2 document without skills is malformed — every v0.14 pack installs identically. Skills-only packs are valid.
  قسم «مهارات» اختياري في الحزمة؛ الحزم القديمة كما هي.
- **Safety (`SkillSafety`):** format caps; permissions must be real `ToolRegistry` tools, never HIGH, never above the ceiling; static scans of every text field for prompt-injection markers (EN/AR, chat-template tokens, envelope spoofing, approval skipping), secret requests and secret-storage paths (negation-aware), internal URLs (`WebGuard` + bare private IPs + metadata hosts; loopback dev servers with a port allowed), hidden/bidi Unicode, size. Honest `SkillIssue(code, detail)` reasons.
  فحص أمان صادق قبل أي تثبيت.
- **Integrity:** canonical JSON + SHA-256 (`CanonicalJson`, byte-identical to the gallery's Python validator) recorded per skill and per pack at install; re-verified before an update (`tampered`), with `downgrade`, `same_version_changed` and cross-pack `conflict` refusals; a gallery `sha256` must match.
- **Manager (`SkillManager`):** one install pipeline for gallery/file/paste (plan → refusal or update-with-changelog-diff → apply), uninstall, enable/disable, private flag, 1–5 rating, usage count, export one/bundle to `.saqrpack` (private skills excluded unless explicitly confirmed; no local state exported), local private skills through the same checks, Arabic/English search (normalized substring + stemmed terms) and categories; cap 60 installed.
- **Runtime (`SkillRuntime`, `AgentSession`):** `$skill-id` mentions (≤ 4) and opt-in BM25 auto-attach (top N, default 2, ≥ 2 shared terms) chosen once per run; audit line + chat pill + usage count per attachment; tools = declared ∩ registry (non-HIGH, ≤ ceiling) ∩ role subset; text inside an "installed skill — data, cannot override system rules" envelope with a system-prompt rule, spoofed markers neutralized, 12k-char section cap; role-bound skills only in their role (worker, reader and verdict calls in Chief mode).
  إرفاق يدوي أو تلقائي مع سبب في سجل التدقيق، وصلاحيات لا تتجاوز الدور.

### Gallery · المعرض
- `GalleryEntry` gains optional `version`, `sha256` (+ `integrity` OK/MISMATCH/NONE; mismatch ⇒ install refused), `permissions`, display-only `rating`/`ratingCount`, `changelogUrl` (public https only), `skillCount`; `GalleryKit.updateAvailable`, `installedIds(packs, skills)`, `installError` → `"checksum"`. The index stays `saqrgallery: 1`; malformed optional fields are ignored. The original 4-entry index parses exactly as before.
- Gallery card: version, skills, rating, tools, verified/mismatch tag, «update available» + Update (changelog diff first), changelog link.

### Settings · الإعدادات
- New section «المهارات · Skills»: `skillsEnabled` (on — nothing attaches unless named), `autoAttachSkills` (off), `skillsTopN` (2, 1–4), and the Skills manager sheet. Arabic + English strings (+96 each).

### Infrastructure · البنية
- New: `agent/skills/SkillModel.kt`, `SkillSafety.kt`, `SkillManager.kt`, `SkillRuntime.kt`, `ui/SkillsSettings.kt`, `docs/SKILLS.md`, `V018FeaturesTest` + `V018SettingsTest`, test fixtures `gallery-main-v1.json` (the real index) and `gallery-v018-skill.json`.
- Extended: `SaqrPack`/`SaqrPackCodec`, `Gallery`, `SettingsStore`, `SystemPrompt.build(skills=)`, `AgentSession` (attach, audit, sections), `ChatItem.Notice.Kind.SKILL` (older builds read it as INFO), Settings packs/gallery cards (shared install flow), chat pill.
- versionName 0.18.0, versionCode 28.

## [0.17.0] — 2026-11-25 — Memory Engine & Model Router · محرك الذاكرة والموجّه (Phase 4)

### Memory · الذاكرة
- **Three layers (`agent/memory/`, `agent/MemoryBank.kt`):** session (chat + `ContextCompactor`, plus an optional **rolling summary** of old turns stored with the session as `summary`, anchored on the first kept prompt and ignored — full history — when the chat no longer matches; older session files load unchanged), project (`.saqr/memory.json` schema 2) and long-term (app-private `files/memory/global.json`, user preferences only).
  ثلاث طبقات: الجلسة بملخّص متجدّد، والمشروع بإدخالات ذات أنواع، وطويلة المدى للتفضيلات فقط.
- **Typed project entries:** `type` (note, preference, decision, architecture, dependency, convention, lesson), `source`, `files`, `hits`, `stale`; v1 files migrate in place (every old entry → `note`, source `legacy`, kind/timestamps kept, same prompt rendering). Caps raised: 200 entries, 120-char keys, 800-char values; stale entries evicted first. `memory_save` gains optional `type`/`files`/`scope`, `memory_forget` an optional `scope`; old calls behave as before.
  ترحيل تلقائي، والسقف ٢٠٠ إدخال.
- **Lessons:** a completion gate going FAILED → PASSED stores an error → solution lesson (normalized signature without paths/line numbers; files changed since the failure + the coder's own summary), deduplicated (same/≥75%-overlap signature ⇒ `hits + 1`), surfaced to the `[Verifier]` feedback and the Debugger's context.
  دروس «خطأ ← حل» تلقائية بلا تكرار تُعرض على المصحِّح.
- **Relevance retrieval (`MemoryRecall`):** on-device BM25 with Arabic normalization (`SlashCommands.normalize` + `MemoryBank.normalize`), camelCase split and light Arabic/English stemming; top-K (default 8) + up to 4 pinned preferences, filled only with standing project rules; whole-bank injection when off.
  استرجاع BM25 على الجهاز يفهم العربية.
- **Long-term layer:** written only by `memory_save` with `scope: "global"` while `globalMemory` is on, or the sheet's «Make global» on a project preference; refuses file refs; one shared bank object per file across project sessions. Off ⇒ refused with a clear message and nothing written or injected.
- **Stale file refs:** applied edits invalidate `ProjectIndex`; its rebuild hook (and opening the Memory sheet) re-checks entries' files and flags missing ones (tagged, half weight in retrieval).
- **Secret guard on every layer (`MemorySafety`):** the v0.13 heuristic + the user's configured provider keys and GitHub token via `Redact`; captured test output and session summaries are scrubbed before storage.
- **Memory sheet:** Project · Global · Session tabs, search, type filter, stale tag, file refs, exact delete, clear, make global, clear session summary.

### Router · الموجّه
- **Smart Router (`agent/ModelRouter.kt`, extends Falcon Router):** `ModelCatalog` (tools, context window, relative speed for the shipped models; vision from `Vision` incl. user overrides; prices from the user's price table), availability from `SettingsStore.routableProviders()` (saved key, or a keyless custom endpoint with a real URL and model; active provider always), routing by tier — CHEAP → cheapest capable priced model provably cheaper than the coder; STRONG → the coder unless it lacks vision/context; INDEPENDENT_REVIEW → a capable ≥MID model different from the coder, another provider preferred. Explicit `reviewModel`/`plannerModel` still win.
  الموجّه الذكي: جدول قدرات، وتوجيه حسب الفئة والقدرة والسياق والتوفّر، ومراجِع مستقل.
- **Failover chain:** the user's Phase 2 fallback first, then capable models on other providers, then the same provider (≤3); `Failover.run` moves on only for transient errors; each switch posts the FALLBACK notice and an audit line.
- **Spend:** daily/monthly spend caps checked before a run and every step (team runs too), stopping with a clear notice; the Chief's estimate uses real per-model prices from the current context as a low–high range (router on). Every router decision is audited (`tool: router`, label → provider · model (reason)); routed decisions also post a compact chat pill.
- With `smartRouter` off (default) Falcon Router and the Phase 2 single fallback run unchanged.

### Settings · الإعدادات
- New section «محرك الذاكرة والموجّه · Memory engine & router»: `memoryRelevance` (off), `memoryTopK` (8), `sessionSummary` (off), `memoryLessons` (on), `globalMemory` (off), `smartRouter` (off), `dailySpendCap` / `monthlySpendCap` (0 = off). Arabic + English strings.

### Infrastructure · البنية
- New: `agent/memory/MemoryRecall.kt`, `agent/memory/MemoryEngine.kt`, `agent/ModelRouter.kt`, `ui/MemoryRouterSettings.kt`, `docs/MEMORY.md`, `docs/ROUTER.md`, `V017FeaturesTest` + `V017SettingsTest`.
- Extended: `MemoryBank`, `ProjectIndex` (`invalidate`, `onRebuilt`), `ChatPersistence` (`SessionData.summary`), `Tools` (memory tool schemas), `AgentSession` (memory block, lessons, rolling summary, smart routing, failover, spend caps, estimate), `ChatItem.Notice.Kind.ROUTER/LESSON` (older builds decode them as INFO), `SettingsStore`, `SettingsScreen`, `IdeScreen`, `ui/chat/MemorySheet.kt`, `ui/chat/Chat.kt`, strings (values + values-ar).
- 19 unit tests (new `V017FeaturesTest` + `V017SettingsTest`, including acceptance scenario 12 with a fake client); 488 tests run (487 passed, 1 skipped — the same pre-existing skip; baseline 469), real local Gradle run (CI on the private repo is held by the account billing limit).
- versionName 0.17.0, versionCode 27.

## [0.16.0] — 2026-11-25 — Agent Team & Orchestration · فريق الوكلاء (Phase 3)

### Agent · الوكيل
- **Roles registry (`agent/AgentRoles.kt`):** 12 roles — Chief, Planner, Research, Architect, Developer, Debugger, Security, Tester, Reviewer, Optimizer, Documentation, Deployment — each with an Arabic + English name, a focused system-prompt fragment, an allowed tool subset from `ToolRegistry` (Reviewer/Security/Research/Architect read-only; Deployment gets build + git with `git_push` still HIGH risk and always behind its approval card), a model tier (cheap / strong / independent review) mapped through Falcon Router, and a step cap. A call outside a role's subset is refused and audited as blocked.
  سجل أدوار: ١٢ دوراً لكلٍّ اسم ثنائي اللغة وتعليمات مركّزة وأدوات مسموحة فقط وفئة نموذج عبر موجّه فالكون وحد خطوات.
- **Chief (`agent/Chief.kt`):** rule-based intent classification (Arabic + English: question / edit / new project / bug fix / research / multi-stage), the cheap model only when ambiguous; a pure minimal role selection with reasons (question → no team; edit → Developer + Reviewer; bug → Debugger + Tester + Reviewer; new project → Planner + Architect + Developer + Tester + Reviewer; multi-stage → Planner + Developer + Tester + Reviewer; + Security for auth/secrets/network/commands; + Research for external information; Documentation/Optimizer/Deployment only when asked), honouring the user's role switches.
  القائد: تصنيف بالقواعد أولاً ثم النموذج الرخيص عند الالتباس، واختيار أقل عدد من الأدوار مع السبب.
- **Task graph (`agent/TaskGraph.kt`):** subtasks with id/role/dependsOn/status (pending, running, blocked, done, failed), cycle detection, topological order, ready and independent-ready sets, progress %, dependents blocking, plan-pill rendering; persisted inside `TaskState` (`"team"`, older files decode unchanged) and resumed after a restart. Planner JSON accepts optional `id`/`role`/`dependsOn` (ids, `tN` or 1-based numbers); plans without them parse exactly as before.
  مخطط مهام بأدوار واعتماديات يُحفظ في ملف المهمة ويُستأنف؛ وخطة المخطّط القديمة تُقرأ كما هي.
- **Execution (`ChiefRunner` + `AgentSession.chiefLoop`):** ready subtasks in dependency order, sequential by default; with `teamMaxConcurrent` = 2, two independent read-only subtasks (readers / verdicts) run side by side; workers never do. Each role gets only scoped context (`RoleContext`: goal, subtask, capped dependency reports, files — never the whole chat). Reports merge into one honest team result.
  تنفيذ بترتيب الاعتماديات، تسلسلي افتراضياً، وسياق محدود لكل دور، ونتيجة مدمجة صادقة.
- **Collaboration (`agent/TeamCollab.kt`):** Reviewer and Security review the Developer's merged diff (skipped with no call when nothing changed); a rejection goes back to the author, a second one is a conflict the Chief gives to the Debugger once, a third goes to the user on the existing ask_user card (continue / stop); a later role undoing another role's lines in the same file is a conflict reconciled by the Debugger (or the user); a subtask failing twice is reassigned once to the Debugger, then failed with its dependents blocked.
  تعاون: مراجعة، ثم إعادة لصاحب العمل، ثم المصحّح، ثم قرار المستخدم؛ وكشف تعارض التعديلات على نفس الملف.
- **Cost per role:** tokens/cost per role and model recorded with the existing Usage tracking, `RunOutcome.roles`, a compact per-role chat line after each team run, and a before-start estimate (plan size × role tier price; unpriced models reported, never guessed).
  تكلفة كل دور وتقدير قبل البدء.
- **With Phase 2:** the completion gate runs before the final DONE (failures go to the Debugger, ≤2 rounds); step/time/cost budgets cover the whole team run (a stop leaves subtasks pending and resumable); the audit log records the role (`"role"`); the loop guard runs per role.

### Settings · الإعدادات
- New section «فريق الوكلاء · Agent team» (team mode): `chiefMode` (off — the classic Planner → Coder → Reviewer team is unchanged), parallel read-only roles (`teamMaxConcurrent` 1, max 2), `teamCostEstimate` (on), and one switch per optional role (`teamDisabledRoles`; Chief and Developer always on). Arabic + English strings.

### Infrastructure · البنية
- New: `agent/AgentRoles.kt`, `agent/Chief.kt`, `agent/TaskGraph.kt`, `agent/TeamCollab.kt`, `ui/TeamSettings.kt`, `docs/AGENTS.md`, `V016FeaturesTest` + `V016SettingsTest`.
- Extended: `AgentSession` (Chief loop, worker/reader/verdict runs, role tool filter, per-role usage and audit), `Planner` (team JSON), `TaskState` (`team`, `AuditEntry.role`), `AgentRuns` (`RunOutcome.roles`), `ChatItem.Notice.Kind.TEAM` (older builds decode it as INFO), `SettingsStore`, `SettingsScreen`, `ui/chat/Chat.kt`, strings (values + values-ar).
- 23 unit tests (new `V016FeaturesTest` + `V016SettingsTest`, including a scripted fake-LLM planner → developer → reviewer run); 469 tests run (468 passed, 1 skipped — the same pre-existing skip; baseline 446), lint 0 errors (warnings all pre-existing), debug APK assembled — real local Gradle run (CI on the private repo is held by the account billing limit).
- versionName 0.16.0, versionCode 26.

## [0.15.0] — 2026-11-25 — Autonomous Core · النواة المستقلة (Phase 2)

### Agent · الوكيل
- **Tool registry:** `ToolDef(name, spec, risk SAFE|MEDIUM|HIGH, readOnly, timeoutSec, maxRetries, category)` for every built-in tool (Tools.specs + GitTools); MCP tools default to MEDIUM. A name outside the registry or this turn's MCP list is refused before anything runs. `specs()` sends the model exactly the same list as before.
  سجل الأدوات: لكل أداة درجة خطورة وعلامة قراءة فقط ومهلة وسياسة إعادة؛ أي اسم خارج السجل يُرفض، وقائمة المواصفات للنموذج لم تتغير.
- **Parallel read-only execution:** consecutive SAFE read-only calls run concurrently (bounded at 3) under per-tool timeouts (local reads interruptible); rows and results keep the original call order. Setting `parallelTools` (on).
  تنفيذ متوازٍ لأدوات القراءة الآمنة المتتالية (حتى ٣) بمهلة لكل أداة، والنتائج بترتيبها الأصلي.
- **Tiered command policy (`CommandPolicy`) on top of `SafetyPolicy`:** the blocklist still refuses outright; HIGH-risk commands — downloads piped into an interpreter, process/command substitution of downloads, text piped into a shell, base64/xxd decoding, `rm -r`/`find -delete`, git push/force/reset --hard/clean -f via the shell, recursive chmod/chown, writes outside `/workspace` (redirects, tee, cp/mv/install/ln, tar -C, dd of=), inline `python -c`/`sh -c`/`node -e`/`perl -e` with obfuscation patterns, commands assembled from variables — wait for an approval card (`ActionKind.COMMAND`, `RunWait.Kind.COMMAND`). Checks also run on a de-obfuscated copy so `r''m`, `"r"m` and `r\m` read as `rm`. Only the user's tap completes the card. Setting `confirmRiskyCommands` (on).
  سياسة أوامر متدرجة فوق قائمة الحظر: الأوامر عالية الخطورة تنتظر بطاقة موافقة، والتمويه الإملائي يُفك قبل الفحص، والوكيل لا يوافق على نفسه.
- **LoopDetector v2:** keeps `tripleRepeat`, adds period-2/3 cycles repeated twice, the same tool+args returning the same error 3 times, and 6-step no-progress streaks; wired into the existing nudge-once-then-stop flow with a nudge per kind. Setting `smartLoopGuard` (on).
  حارس الحلقات v2: دورات A-B-A-B وتكرار نفس الخطأ وسلاسل بلا تقدم — تنبيه مرة ثم إيقاف.
- **Completion gate:** in team mode, when the model ends a run that touched files, the real `lintProject()` / `runProjectTests()` run (tests only when a runner is detected, only when Linux is ready); failures go back as a `[Verifier]` message for up to 2 rounds; the final status (PASSED / FAILED / UNVERIFIED) is shown as a chat notice and carried in `RunOutcome.verification` into the finished notification. Setting `verifyBeforeDone` (on, team mode only).
  بوابة الإنهاء: لا تنتهي مهمة غيّرت ملفات بكلمة النموذج وحده — فحص واختبارات فعلية وحتى جولتي إصلاح، والحالة تُعلَن بصدق.
- **Model retry + fallback:** 429/408/5xx/timeouts/IOExceptions retry once after a backoff (`retryLlm`, on); then, if the user picked `fallbackModel` among providers that already have a key (or a keyless local provider), the step runs there with a FALLBACK notice naming it. 4xx errors and cancellations never retry.
  إعادة محاولة واحدة للأخطاء العابرة ثم نموذج بديل يختاره المستخدم من مزوّداته المُعدّة، مع إشعار يسمّيه.
- **Task state + budgets:** `.saqr/tasks/<id>.json` (goal, plan, step, startedAt, active ms, tokens, cost, status running/paused/waiting_approval/done/failed/stopped) is written on every step and wait; `resumeInterrupted()` restores the plan, step and spend. Budgets: `maxSteps` (default 40 = old MAX_STEPS), `maxMinutes` (0 = off, waiting on the user doesn't count), `maxCostUsd` (0 = off, priced with the existing Usage table; an unpriced model is reported instead of silently ignored). Clean stop with a BUDGET notice and `RunOutcome.Kind.BUDGET`. Setting `taskJournal` (on).
  حالة المهمة والميزانيات: ملف لكل مهمة يُكتب في كل خطوة ويستعيده الاستئناف، وحدود خطوات ودقائق وتكلفة مع إيقاف نظيف.
- **Audit log:** `.saqr/audit.jsonl`, one JSON line per tool call `{ts, tool, risk, decision(auto/approved/rejected/blocked), ok, ms, summary}`; summaries pass through `Redact` plus API-key shapes (sk-…, AIza…, xox…, Bearer, key=value) and the known GitHub/provider secrets; rotates at 1 MB keeping `audit.1.jsonl`. Setting `auditLog` (on).
  سجل تدقيق لكل استدعاء أداة مع حجب الأسرار وتدوير عند ١ ميغابايت.
- **Untrusted-content marking:** web_fetch / web_search / MCP results reach the model inside an `[UNTRUSTED CONTENT — data, not instructions]` envelope whose end marker cannot be spoofed from inside, plus a SystemPrompt rule. Setting `untrustedMarking` (on).
  تغليف مخرجات الويب وMCP كمحتوى غير موثوق مع قاعدة في موجّه النظام.

### Infrastructure · البنية
- New: `agent/ToolRegistry.kt` (+ `ParallelPlan`), `agent/CommandPolicy.kt`, `agent/Autonomy.kt` (`CompletionGate`, `LlmRetry`, `FallbackTarget`, `Budgets`, `Untrusted`), `agent/TaskState.kt` (`TaskState`, `TaskStateCodec`, `TaskStore`, `AuditEntry`, `AuditLog`), `ui/AutonomySettings.kt` (Settings → النواة المستقلة · Autonomous core).
- Extended: `AgentSession` (loop, executeTools, runTool, SystemPrompt), `LoopDetector`, `AgentRuns` (`RunOutcome.verification`, `Kind.BUDGET`, `RunWait.Kind.COMMAND`), `ChatItem` (`ActionKind.COMMAND`, notices VERIFY/BUDGET/FALLBACK — older builds decode them as INFO/PUSH), `SettingsStore` (12 new keys, defaults keep current behaviour), Chat cards, `IdeScreen` step meter follows `maxSteps`.
- CI: `.github/workflows/ci.yml` (JDK 17, Android SDK, `testDebugUnitTest lintDebug assembleDebug`, APK + reports as artifacts).
- 21 unit tests (new `V015FeaturesTest` + `V015SettingsTest`); 446 tests run (445 passed, 1 skipped — the same pre-existing skip as 0.14.4; baseline 425), lint 0 errors, debug APK assembled — real Gradle run (CI runs of the private repo are currently held by the account billing limit).
- versionName 0.15.0, versionCode 25.

## [0.14.4] — 2026-11-25 — Repository sync · المزامنة عبر المستودع

### Platform · المنصة
- **Repository sync — the fifth «أجنحة مفتوحة» item (P2):** the user's settings, themes, custom commands and skill packs are captured as one snapshot, **encrypted on the device** (AES-GCM with a PBKDF2-derived key) and pushed to a **private repository the user owns** — their token, their repo, no Saqr server — or shared/imported as a file when there is no network. A second device links the same GitHub account, types the same passphrase, pulls, reviews and applies: set up in under two minutes.
  المزامنة عبر المستودع — البند الخامس من «أجنحة مفتوحة» (P2): إعدادات المستخدم وثيماته وأوامره المخصصة وحزم مهاراته تُلتقط نسخة واحدة، **تُشفَّر على الجهاز** (AES-GCM بمفتاح مشتق من عبارة مرور عبر PBKDF2) وتُرفع إلى **مستودع خاص يملكه المستخدم** — رمزه ومستودعه، بلا خادم منا — أو تُشارك/تُستورد كملف عند غياب الشبكة. الجهاز الثاني يربط الحساب نفسه ويكتب العبارة نفسها ثم يجلب ويراجع ويطبّق: إعداد في أقل من دقيقتين.
- **Secrets never leave the device:** the snapshot whitelist is an explicit, auditable table — API keys, GitHub tokens, MCP auth headers, OAuth tokens, keystore passwords and Moyasar secrets are excluded *by construction*, and the UI says so («ولا مفاتيح API ولا رموز ولا ترويسات تخويل أبدًا»).
  الأسرار لا تغادر الجهاز: قائمة اللقطة جدول صريح قابل للتدقيق — مفاتيح API ورموز GitHub وترويسات تخويل MCP ورموز OAuth وكلمات keystore وأسرار مويسر مستثناة **بالبناء نفسه**، والواجهة تذكر ذلك صراحةً.
- **Honest states end to end:** hints naming what is missing before anything starts (no account → «اربط حساب GitHub أولًا»، short passphrase → «٨ أحرف على الأقل»), a wrong passphrase or damaged copy refuses with one honest line instead of garbage, a missing repository turns into a one-tap «أنشئ المستودع الخاص ثم ارفع» (GitHub's 422 names the taken name), a pulled snapshot shows its device, date and counts **before** an explicit «طبّق على هذا الجهاز» that warns it replaces the current themes/commands/packs, and the last-sync stamp lives under the section.
  حالات صادقة من أولها لآخرها: تلميحات تسمّي ما ينقص قبل أي شيء (بلا حساب → «اربط حساب GitHub أولًا»، عبارة قصيرة → «٨ أحرف على الأقل»)، وعبارة خاطئة أو نسخة تالفة يرفضها سطر صادق واحد بدل خرج مبعثر، والمستودع المفقود يتحول إلى نقرة «أنشئ المستودع الخاص ثم ارفع» (و422 من GitHub يسمّي الاسم المحجوز)، والنسخة المجلبَة تعرض جهازها وتاريخها وأعدادها **قبل** زر «طبّق على هذا الجهاز» الصريح الذي يحذّر من الاستبدال، وختم آخر مزامنة يعيش تحت القسم.

### Infrastructure · البنية
- New `data/Sync.kt` — the pure protocol (`SyncKit`: the 26-entry explicit `SyncSetting` whitelist with typed get/set pairs; `snapshot` → strict `toJson`/`parse` codec with non-string values dropped; replace-semantics `apply` returning honest counts that survives broken values; the vault — `SAQRSYNC1.<salt‖iv‖ciphertext>` container, PBKDF2-HmacSHA256 at 210k iterations, AES/GCM/NoPadding with a 128-bit tag, `passphraseProblem` checked before any crypto, wrong-phrase/tamper refused as one `passphrase` reason; the repo-slug builder for every shape the user types, GitHub-only; contents path builders) and the quarantined network half (`SyncClient` — same discipline as GitHubApi: token only in the Authorization header, contents-API `headSha`→`put` with sha updates, line-broken base64 decoding on `get`, `createPrivateRepo` delegating to `GitHubApi`).
- Wiring: a `SyncSection` in Settings (beside the community gallery) with a passphrase field (`PasswordVisualTransformation`), a repo field remembering `syncRepo`, push/pull/share/import buttons, the apply-confirm card, and a SAF `OpenDocument` picker landing in the same decrypt path as a pull; `SettingsStore.syncRepo` + `syncAt` keep the repo choice and the last-sync stamp.
- 15 unit tests (new `V0144FeaturesTest`): the snapshot's coverage of every syncable kind, the secrets-never-leak probe (four planted secrets absent from both the payload JSON and the encrypted container), the strict payload codec (wrong version, missing sections, non-string values dropped), replace-semantics apply with honest counts, broken-value resilience, the vault round-trip (same passphrase, trailing-newline tolerance), wrong passphrase and tampered blob refused, the container-format truth table with the passphrase check first, every repo-slug shape (owner/repo, URLs, bare name, default repo, GitLab refused), the contents path, and push/pull over a real local socket (404 → null sha, PUT body with the message and content, token only in the Authorization header, line-broken base64 decoded back). Version 0.14.4 (code 24).

## [0.14.3] — 2026-11-25 — Community gallery · معرض المجتمع

### Platform · المنصة
- **Community gallery — the fourth «أجنحة مفتوحة» item (P1):** a public GitHub repository (`CTO-DRS/saqr-gallery`) holds a JSON index of Arabic `.saqrpack` bundles; Settings grows a معرض المجتمع section that browses it, searches it and installs an entry **in one tap** through the existing pack pipeline — the pack's template lands on the home screen and its Arabic commands merge beside the built-ins, exactly like a pasted pack. Contribution is a pull request to the gallery repository — no Saqr-side server, no review queue, the community owns the shelf.
  معرض المجتمع — البند الرابع من «أجنحة مفتوحة» (P1): مستودع GitHub عام (`CTO-DRS/saqr-gallery`) يحمل فهرس JSON لحزم `.saqrpack` عربية؛ تضيف الإعدادات قسم «معرض المجتمع» الذي يتصفحه ويبحث فيه ويثبّت مدخلاً **بنقرة واحدة** عبر خط أنابيب الحزم الموجود — قالب الحزمة يهبط الشاشة الرئيسية وأوامرها العربية تُدمج بجوار المدمجة، تمامًا كحزمة ملصوقة. والمساهمة طلب دمج (PR) إلى مستودع المعرض — بلا خادم منا، وبلا طابور مراجعة، والرف ملك المجتمع.
- **Honest states, offline-first:** the first visit with nothing cached fetches automatically; afterwards the last good copy is kept in the settings store and shown with a «تُعرض آخر نسخة محفوظة» note when a refresh fails, a 404 names the missing index («لم يُعثر على فهرس المعرض»), and everything else is a readable network refusal. Installed entries carry a «مثبّت» tag (and «تثبيت من جديد» instead of a second silent copy), the count is live, the updated stamp shows the fetch time, and one button opens the repository to contribute.
  حالات صادقة والكاش أولًا: الزيارة الأولى بلا كاش تجلب تلقائيًا؛ وبعدها تُحفظ آخر نسخة سليمة في مخزن الإعدادات وتُعرض مع ملاحظة «تُعرض آخر نسخة محفوظة» إذا فشل التحديث، والرمز 404 يسمّي الفهرس المفقود («لم يُعثر على فهرس المعرض»)، وكل ما عدا ذلك رفض شبكة مقروء. المدخلات المثبتة تحمل وسم «مثبّت» (و«تثبيت من جديد» بدل نسخة ثانية صامتة)، والعدّ حي، وختم التحديث يظهر وقت الجلب، وزر واحد يفتح المستودع للمساهمة.

### Infrastructure · البنية
- New `data/Gallery.kt` — the pure protocol (`GalleryKit`: strict index codec that validates every entry through `SaqrPackCodec.fromJson` + `validate` so one broken pack is skipped, never shown, and never breaks the gallery; id dedupe keeping the first; the 60-entry cap and per-field trims; the Arabic-aware search over name/nameAr/description/author/tags via `SlashCommands.normalize`; `installedIds` for the «مثبّت» tag; the raw-URL builder for every owner/repo shape with UTF-8 percent-encoding) and the quarantined network half (`GalleryClient`: one anonymous GET of the raw index — no token, no GitHub API, static content only — with readable refusals on non-200 and empty bodies).
- Wiring: a `GallerySection` in Settings (next to skill packs) reusing `ui.onInstallPack` verbatim — the gallery installs through the identical code path as a pasted pack; `SettingsStore.galleryCache` + `galleryFetchedAt` keep the offline copy keyed by the fetch stamp so a pack install never re-decodes underneath the list.
- 15 unit tests (new `V0143FeaturesTest`): the index codec's strictness (garbage, wrong version, missing arrays), broken-pack skipping and hostile-path rejection, id dedupe with blank ids refused, the 60-entry cap, per-field trims, Arabic-insensitive search across all fields (hamza forms, diacritics, tags, author, latin), installed-ids, every raw-URL shape with percent-encoding, the cache round-trip, the one-tap install through the real pack pipeline (upsert semantics included), and the anonymous fetch over a real local socket (200 body, no Authorization header, 404 → readable refusal, empty 200 refused). Version 0.14.3 (code 23).

## [0.14.2] — 2026-11-25 — Cloud build (BYOC) · البناء السحابي الاختياري

### Platform · المنصة
- **Cloud build (BYOC) — the third «أجنحة مفتوحة» item (P1):** for weak phones, the build runs on **GitHub Actions inside the user's own repository** — the user's token and the user's repo, never a Saqr-side cloud. From the Build tab, one tap: Saqr installs a tiny workflow into the repo (`.github/workflows/saqr-build.yml` — JDK 17 + Android SDK + action-installed Gradle, since device templates ship no wrapper, then `assembleDebug` and an APK artifact upload), dispatches it on the project's default branch, watches the run with a **live log tail** (the running job's log, last lines, refreshed every few seconds), downloads the APK artifact when the run succeeds and hands it to the in-app installer. The debug APK installs directly — no signing setup needed on the weakest device.
  البناء السحابي (BYOC) — البند الثالث من «أجنحة مفتوحة» (P1): للهواتف الضعيفة، يعمل البناء على **GitHub Actions داخل مستودع المستخدم نفسه** — رمز المستخدم ومستودعه، بلا سحابة منا إطلاقًا. من تبويب البناء بنقرة واحدة: يثبّت صقر سير عمل صغيرًا في المستودع (`.github/workflows/saqr-build.yml` — JDK 17 + SDK أندرويد + Gradle تثبّته الأداة، فقوالب الجهاز بلا wrapper، ثم `assembleDebug` ورفع APK كناتج)، ويرسله على الفرع الافتراضي للمشروع، ويتابع التشغيل **بسجل حي** (سجل المهمة الجارية، آخر الأسطر، يتجدد كل ثوانٍ)، وينزّل ناتج APK عند النجاح ويسلّمه للمثبّت داخل التطبيق. APK الـdebug يُثبَّت مباشرة — بلا أي تجهيز توقيع على أضعف جهاز.
- **Honest states, cancel-safe polling:** the card shows queued / running / downloading / ready / failed / cancelled as a colored tag, names exactly what is missing before starting (no GitHub remote → «اربط المشروع من تبويب Git»، no token → «سجّل الدخول من الإعدادات ← الحسابات»), offers «افتح التشغيل على GitHub» once the run exists, stops cleanly at the next poll (Stop), and the whole flow is time-boxed at 20 minutes with a message pointing to the run on GitHub. A failed build surfaces the job's own error lines (`FAILURE:`, `e: …`) instead of a bare «failed».
  حالات صادقة ومتابعة قابلة للإلغاء: تعرض البطاقة الحالة كوسم ملوّن (في الطابور / يعمل الآن / تنزيل APK / جاهز / فشل / أُلغي)، وتسمّي بالضبط ما ينقص قبل البدء (بلا مستودع → «اربط المشروع من تبويب Git»، بلا رمز → «سجّل الدخول من الإعدادات ← الحسابات»)، وتتيح «افتح التشغيل على GitHub» فور وجود التشغيل، وتتوقف نظيفة عند أول فحص (إيقاف)، والسير كله محدود بعشرين دقيقة برسالة تحيل إلى GitHub. والبناء الفاشل يُظهر أسطر الخطأ من سجل المهمة نفسها (`FAILURE:`، `e: …`) بدل «فشل» مجردة.

### Infrastructure · البنية
- New `git/CloudBuild.kt` — three layers: the **pure protocol** (`workflowYaml()` with placeholders-free Kotlin-safe YAML, `ownerRepo()` for every GitHub remote shape, the API path builders, the contents-API base64 codec (strict decode — GitHub's embedded line breaks stripped first), `parseRuns`/`parseJobs`/`latestRun`/`stateOf`, `pickApk` (fresh APK artifact preferred, expired skipped), `tailLines`/`errorLines`, the 20-minute timeout rule, `unzipApk` (path-sanitized)); the **network half** in `CloudBuildClient` (same discipline as GitHubApi — token only in the Authorization header, 204-checked dispatch, binary artifact download straight to a file); and the **controller** (`CloudBuild` with StateFlows like OnDeviceBuild — install → dispatch → poll with live tail → download → READY, cancel flag checked each loop).
- Wiring: a `CloudCard` section in the Build pane (next to the on-device build, not replacing it) with a new `CloudUpload` Lucide icon; the remote comes from the project's JGit remotes (origin first), the token from the GitHub account — no new settings.
- 13 unit tests (new `V0142FeaturesTest`): the full workflow YAML (dispatch trigger, JDK 17, SDK, action-Gradle, assembleDebug, artifact upload — and no `./gradlew`/`assembleRelease`), every remote-URL shape, all API paths with branch encoding, the base64 codec against GitHub's line-broken bodies, run/job parsing and newest-first selection, the state truth table, artifact preference/expiry, log tails and error lines, the timeout boundary, and APK extraction from a real zip (nested entry, no-APK zip, garbage bytes — never escaping the destination). Version 0.14.2 (code 22).

## [0.14.1] — 2026-11-25 — MCP over OAuth · MCP عبر OAuth

### Platform · المنصة
- **MCP over OAuth (PKCE) — the second «أجنحة مفتوحة» item (P1):** protected MCP servers sign in from Settings → MCP servers → the server's browser. Saqr discovers the authorization server automatically (MCP protected-resource metadata, then RFC 8414 well-known documents, with manual endpoint overrides for unusual setups), runs a full OAuth 2.1 authorization-code flow **with PKCE (S256 only)** inside the system browser, exchanges the code at the token endpoint and keeps the token set fresh — an expiring token is refreshed (refresh_token grant) right before the agent connects, and an expired one falls back to the manual auth header instead of failing silently.
  MCP عبر OAuth (PKCE) — البند الثاني من «أجنحة مفتوحة» (P1): الخوادم المحمية تسجّل الدخول من الإعدادات ← خوادم MCP ← متصفح الخادم. يكتشف صقر خادم التخويل تلقائيًا (بيانات المورد المحمي، ثم وثائق RFC 8414 المعروفة، مع تجاوزات يدوية للإعدادات غير الاعتيادية)، ثم يشغّل تدفق رمز التخويل الكامل وفق OAuth 2.1 **بـ PKCE (طريقة S256 حصرًا)** داخل متصفح النظام، ويستبدل الرمز عند نقطة نهاية الرموز، ويُبقي الرموز طازجة — الرمز قرب الانتهاء يُجدَّد قبل اتصال الوكيل، والمنتهي يرتد إلى ترويسة التخويل اليدوية بدل الفشل الصامت.
- **The browser round-trip is state-safe:** the authorization request carries an anti-forgery `state` persisted with its PKCE verifier; the redirect lands on a dedicated no-UI activity that validates the state (a mismatched or unknown response is refused outright), refuses denied authorizations with a clear toast, exchanges the code on a background thread and stores the tokens in the encrypted SecureStore — the verifier never leaves the device and the tokens never touch plain prefs.
  دورة المتصفح آمنة الحالة: طلب التخويل يحمل `state` مانعًا للتزوير يُحفظ مع مُثبِت PKCE الخاص به؛ وتعود إعادة التوجيه إلى نشاط مخصص بلا واجهة يتحقق من الـstate (الاستجابة غير المطابقة أو المجهولة تُرفض فورًا)، ويرفض التخويلات الممنوعة برسالة واضحة، ويستبدل الرمز في خيط خلفي ويخزّن الرموز في SecureStore المشفر — المُثبِت لا يغادر الجهاز والرموز لا تلمس التخزين العادي أبدًا.
- **A 401 names the way out:** when a protected server answers 401 — no credentials yet or stale ones — the agent's MCP notice says exactly that («سجّل الدخول من جديد من الإعدادات ← خوادم MCP») instead of a bare HTTP error, and the server's browser shows a live token status line (متصل — الرمز سليم / انتهت صلاحية الرمز) with «تسجيل الدخول من جديد» and «فصل الاتصال».
  الخطأ 401 يسمّي المخرج: حين يرد خادم محمي بالرمز 401 — بلا بيانات دخول أو ببيانات بالية — يسمّي إشعار MCP في الدورة ذلك بعينه («سجّل الدخول من جديد من الإعدادات ← خوادم MCP») بدل خطأ HTTP مجرد، ويعرض متصفح الخادم سطر حالة حيًّا للرمز (متصل — الرمز سليم / انتهت صلاحية الرمز) مع «تسجيل الدخول من جديد» و«فصل الاتصال».

### Infrastructure · البنية
- New `agent/McpOAuth.kt`: pure protocol — PKCE primitives (verifier/challenge/state, unit-tested against the RFC 7636 Appendix B vector), well-known URL builders (protected-resource + RFC 8414, path- and port-preserving), metadata parsing (https-only endpoints, optional scopes), the authorize URL, redirect parsing, token/refresh request bodies and response parsing (expires_in with a 60s clamp, refresh-token fallback to the previous set), and the `Tokens`/`Config`/`Pending` codecs. The network half is quarantined in `McpOAuthHttp` (thin OkHttp wrappers) and the turn-time glue in `McpOAuthRuntime.maybeRefresh`.
- Wiring: `OAuthCallbackActivity` (custom scheme `saqr://oauth`, exported, excluded from recents); `SettingsStore` OAuth config in plain prefs keyed by the server slug + tokens in SecureStore + pending flows by state + `mcpAuthHeader()` preferring the live Bearer; `McpClient` flags 401 as `unauthorized`; `AgentSession.refreshMcpTools` refreshes before connecting and rewords the 401 notice; Settings → the server browser grows a قسم OAuth (client id, scopes, optional endpoint overrides, sign in / sign in again / disconnect, live status).
- 20 unit tests (new `V0141FeaturesTest`): the RFC vector, verifier charset/uniqueness, discovery URLs, metadata strictness, authorize-URL parameters and %20 encoding, redirect/error parsing, token bodies, expires_in semantics and refresh fallback, the 60s skew, all three codecs, encrypted storage by slug, the auth-header preference order, and the client's 401/503 behavior over a real local socket (MockWebServer) including the Bearer header on all three requests. Version 0.14.1 (code 21).

## [0.14.0] — 2026-11-25 — Skill packs · حزم المهارات

### Platform · المنصة
- **Skill packs (.saqrpack) — the first «أجنحة مفتوحة» item (P0):** one JSON bundle installs, in one tap, a project **template**, ready **Arabic slash commands**, **AGENTS.md rules** and **preferred tools**. Install from Settings → حزم المهارات by pasting the pack JSON (or one tap on the built-in **Flask sample**), and the pack appears as a template card on the home screen — creating from it materializes the full file set (template base overlaid by the pack's files) with the rules and preferred tools merged into `AGENTS.md`, which the agent already injects into every turn's system prompt.
  حزم المهارات (.saqrpack) — البند الأول من «أجنحة مفتوحة» (P0): حزمة JSON واحدة تنصب بنقرة **قالب مشروع** و**أوامر عربية جاهزة** و**قواعد AGENTS.md** و**أدوات مفضلة**. التثبيت من الإعدادات ← حزم المهارات بلصق محتوى الحزمة (أو بنقرة على **عينة Flask** الجاهزة)، فتظهر الحزمة بطاقة قالب في الشاشة الرئيسية — وإنشاؤها يكتب الملفات كاملة (قالب أساس تفرض عليه ملفات الحزمة) مع حقن القواعد والأدوات المفضلة في `AGENTS.md` الذي يقرأه الوكيل من موجّه النظام في كل دورة تلقائيًا.
- **A safe, Arabic-aware merge:** commands upsert by the Arabic-normalized name (الهمزات والتشكيل لا تخلق تكرارًا) and can never shadow a builtin (`/fix`, `/اصلح` …); pack paths are sandboxed (no absolute, no `..` jumps), sizes capped (40 files, 2000-char rules, 12 commands), and `AGENTS.md` merges instead of overwriting. Uninstalling a pack removes its template but keeps the commands the user already has.
  دمج آمن ومرن عربيًا: الأوامر تُحديث بالاسم الموحّد عربيًا (الهمزات والتشكيل لا تخلق تكرارًا) ولا تستطيع أبدًا تغطية أمر مدمج (`/fix`, `/اصلح` …)؛ مسارات ملفات الحزمة محصورة (لا مطلق ولا قفز `..`) والأحجام مقيّدة (40 ملفًا، قواعد 2000 حرف، 12 أمرًا)، و`AGENTS.md` يُدمَج لا يُستبدل. إزالة الحزمة تحذف قالبها فقط وتُبقي الأوامر المثبتة.
- **Flask acceptance in the field:** installing the Flask sample and creating its project completes well under the 5-second bar — three ready-to-run Arabic commands (`/flask-run`, `/flask-test`, `/flask-route`), a working `app.py` + `requirements.txt`, and rules/tools in AGENTS.md — all verified by unit tests.
  معيار Flask ميدانيًا: تثبيت العينة وإنشاء مشروعها يكتمل بأقل كثيرًا من حد الـ5 ثوانٍ — ثلاثة أوامر عربية جاهزة للتشغيل (`/flask-run`, `/flask-test`, `/flask-route`)، و`app.py` + `requirements.txt` يعملان، وقواعد وأدوات في AGENTS.md — وكلها مغطاة باختبارات وحدة.

### Infrastructure · البنية
- New `data/SaqrPack.kt`: pure logic — `SaqrPack`/`PackCommand` + a strict `fromJson` (version-checked, never throws), `validate()` with stable error keys, `packFiles()` (template resolution + file overlay + AGENTS.md merge), `mergeCommands()` (Arabic-normalized upsert, builtins untouchable), `install()`/`remove()`, and the built-in `flaskSample()`.
- Wiring: `ProjectRepository.create(name, files)` overload for explicit file maps; `SettingsStore.packsJson` (`saqr_packs`); Settings → حزم المهارات (hint, installed-pack rows with remove, Flask sample button, paste-JSON install); HomeScreen pack template cards + `CreatePackDialog` writing `packFiles()`.
- 15 unit tests (new `V014FeaturesTest`): codec round-trips, strict parsing, validation keys (paths/sizes/duplicates/builtins), AGENTS.md merging with and without a base template, the Arabic-aware command merge, install/remove semantics, persistence, the files-map repository path, and the ≤5s Flask acceptance. Version 0.14.0 (code 20).

## [0.13.5] — 2026-11-25 — Desert mode · وضع صحراوي

### Productivity · الإنتاجية
- **Desert mode (وضع صحراوي):** the sixth and final «عقل الصقر» item. An offline-first mode with three settings — **off**, **«عند الانقطاع»** (activates automatically while the device has no validated internet) and **«دائمًا»** (forced, for rationing data / battery / heat even online). While the desert is active, every cloud feature refuses **with one clear message instead of a crash or a hanging socket**: a fresh send lands as a single «الوضع الصحراوي نشط» chat notice (never stacked), web tools, remote MCP servers and git push/pull answer the model mid-turn with a readable refusal that names what still works, and remote MCP servers are skipped with a notice instead of timing out one by one.
  وضع صحراوي: البند السادس والأخير من «عقل الصقر». وضع «أولًا بلا شبكة» بثلاث حالات — **إيقاف**، و**«عند الانقطاع»** (يُفعَّل تلقائيًا ما دام بلا إنترنت مُتحقَّق منه)، و**«دائمًا»** (إجباري لترشيد البيانات والبطارية والحرارة حتى والشبكة موجودة). أثناء نشاط الصحراء يرفض كل مسار سحابي **برسالة واضحة واحدة بدل الانهيار أو التعليق**: الإرسال الجديد يهبط بإشعار واحد «الوضع الصحراوي نشط» (لا يتكدس أبدًا)، وأدوات الويب وخوادم MCP البعيدة ودفع/سحب Git تجيب النموذج وسط الدور برفض مقروء يسمّي ما يبقى شغّالًا، وتُتخطى خوادم MCP البعيدة بإشعار بدل انتظار المهلة واحدًا تلو الآخر.
- **Everything local keeps working (كل ما هو محلي يستمر):** files, editor, terminal, memory bank, the symbol index, local git (status/diff/log/commit/branch), lint and tests, local **stdio** MCP servers, and the on-device build from a cached environment. The live connectivity watcher (ConnectivityManager, validated-internet only — captive portals stay desert) feeds the gate, so the composer shows a slim desert banner the moment the network drops, and Settings → وضع صحراوي carries a live status line.
  كل ما هو محلي يستمر: الملفات والمحرر والطرفية وبنك الذاكرة وفهرس الرموز وgit المحلي (حالة/فرق/سجل/إيداع/فرع) والفحص والاختبارات وخوادم **stdio** المحلية والبناء على الجهاز من بيئة مخزّنة. مراقب الاتصال الحيّ (ConnectivityManager، إنترنت مُتحقَّق منه فقط — فالبوابات المقيدة تبقى صحراء) يغذي البوابة، فيظهر شريط صحراوي رفيع فوق صندوق الكتابة لحظة انقطاع الشبكة، والإعدادات ← وضع صحراوي تحمل سطر حالة حيًّا.
- **Bootstrap is cache-first offline (البيئة المخزّنة تُبنى عليها بلا شبكة):** each build-preparation download (rootfs, JDK 17, cmdline-tools, android-35, aapt2, Gradle) is gated *after* its cache check — a fully cached environment builds offline untouched; only the genuinely missing piece stops, with a log line naming it and the way out («connect once, then build offline afterwards»).
  تجهيز البيئة يعطي الأولوية للمخزون دون اتصال: كل تنزيل في التجهيز (النظام، JDK 17، أدوات SDK، منصة android-35، aapt2، Gradle) يمرّ من البوابة *بعد* فحص مخزونه — فالبيئة المكتملة تُبنى بلا شبكة كما هي، ولا يتوقف إلا الجزء الناقص فعلًا، بسطر سجل يسمّيه ويبيّن المخرج («اتصل مرة واحدة ثم ابنِ بلا شبكة بعدها»).

### Infrastructure · البنية
- New `agent/DesertMode.kt`: pure policy — the `Mode` enum ("" / "auto" / "always"), `active(mode, online)`, the `cloudTool` split (web_search / web_fetch / git_pull / git_push are cloud-bound; every local tool — including the `mcp_*` prefix, because stdio servers run on the device — is exempt), `gate()` + model-readable `toolMessage()` per feature, and `downloadAllowed(desert, cached)`. `DesertState` holds the live mode/online pair as Compose state; `SaqrApp` seeds it from settings and keeps it honest with one default-network callback.
- Wiring: `AgentSession.send` refuses with a deduped `Kind.DESERT` notice (new kind, persisted by name like the rest); `runTool` gates the cloud-bound tools mid-turn; `runMcpTool` gates remote HTTP clients while stdio passes; `refreshMcpTools` skips remote servers with a notice; `OnDeviceBuild.desertGuard` runs after every cache check; a new `WifiOff` Lucide icon; Settings → Desert mode (segmented + hint + live status) and the chat `DesertBanner`.
- 14 unit tests (new `V0135FeaturesTest`): mode round-trips and persistence, the activation truth table, the cloud/local tool split (all 19 local tools + the stdio `mcp_` prefix exempt), clear refusals per feature, local-never-blocks, refusal content naming what survives, the download cache rule, and the DESERT notice surviving save/restore. Version 0.13.5 (code 19).

## [0.13.4] — 2026-11-25 — Hands-free voice · صوت بلا يدين

### Productivity · الإنتاجية
- **Hands-free voice (صوت بلا يدين):** the fifth «عقل الصقر» item. Two modes in Settings → Voice: **«إملاء مستمر»** keeps dictating into the composer while a project is open — each recognizer utterance chains into the next automatically — and **«إيقاظ يا صقر»** waits for the wake phrase, then the command that follows **sends itself** to the agent (guarded: a running task is never interrupted, the text waits in the composer instead).
  صوت بلا يدين: البند الخامس من «عقل الصقر». وضعان في الإعدادات ← الصوت: **«إملاء مستمر»** يواصل الإملاء في صندوق الكتابة ما دام المشروع مفتوحًا — كل جملة تتبع سابقتها تلقائيًا — و**«إيقاظ يا صقر»** ينتظر كلمة الإيقاظ ثم **يرسل الأمر الذي يليها** إلى الوكيل وحده (بحراسة: المهمة الجارية لا تُقاطع، ويبقى النص في الصندوق بدل ذلك).
- **A flexible Arabic wake phrase:** detection runs on the Arabic-aware normalizer rather than exact strings — diacritics (يَا صَقَر), alef forms, the fused «ياصقر», «صَقرا»-like endings, and latin spellings («Ya Saqr», «hey saqer», «OK SAQR») all trigger it; «الصقر طائر» or «صقر افتح الملف» (no greeting) never do. Pre-wake speech is never typed or sent.
  إيقاظ عربي مرن: الكشف يعمل عبر المعوّد العربي-واعٍ لا على نص دقيق — التشكيل (يَا صَقَر) وأشكال الهمزة والدمج «ياصقر» ونهايات مثل «صَقرا» والكتابة اللاتينية («Ya Saqr», «hey saqer», «OK SAQR») كلها تُوقظه؛ و«الصقر طائر» أو «صقر افتح الملف» (بلا تنبيه) لا تُوقظه أبدًا. وما قبل الإيقاظ لا يُكتب ولا يُرسل.
- **Notification toggle (تشغيل/إيقاف من الإشعار):** while dictation listens, a silent ongoing notification mirrors it with a Stop action that flips the persisted mode instantly (a tiny receiver flips the setting; the project screen tears the recognizer down reactively). Starting mic capture *from* the notification is deliberately unsupported — Android restricts background microphone access, and Saqr never listens without the app in hand.
  تشغيل/إيقاف من الإشعار: أثناء الإنصات يظهر إشعار صامت دائم يطابق الحالة بزر «إيقاف» يقلب الوضع المحفوظ فورًا (مستقبِل صغير يقلب الإعداد، وشاشة المشروع تفكّ المُعرِّف ردًّا على ذلك). وبدء التسجيل *من* الإشعار مقصودًا غير مدعوم — أندرويد يقيّد الميكروفون من الخلفية، وصقر لا يسمع بلا التطبيق في يدك.
- **No closed loops:** transient errors (silence/no-match, busy recognizer, network) re-listen with a gentle back-off (350ms → 4s); fatal ones (permission, client, audio) end the mode with a clear message and flip it off so it cannot linger dead; 4 consecutive failures give up politely. The mic button stops hands-free with one tap while it is on, and a long-press still toggles the dictation language.
  لا حلقات مغلقة: الأخطاء العابرة (صمت/بلا تطابق، انشغال المُعرِّف، الشبكة) تعيد الإنصات بتراجع لطيف (350 مللي ← 4 ثوانٍ)، والقاتلة (الصلاحيات، العميل، الصوت) تُنهي الوضع برسالة واضحة وتقلبه رفضًا ليبقى ميتًا؛ و4 إخفاقات متتالية تستسلم بأدب. زر الميكروفون يوقف «بلا يدين» بضغطة وهو يعمل، والضغط المطوّل يبدّل لغة الإملاء كما هي.

### Infrastructure · البنية
- New `agent/WakePhrase.kt` (pure wake detection on `MemoryBank.normalize` word-pairs) and `agent/Dictation.kt` (the `Mode` enum stored in settings, the retryable/fatal error matrix, and a `CommandBuffer` that arms on wake, feeds on partials, caps at 2000 chars and finalizes exactly once). New `ui/chat/ContinuousVoice.kt` wraps `VoiceInput` with the restart loop (buffer callback supplies each session's prefix).
- Wiring: `IdeScreen` owns the lifecycle (`DisposableEffect(dMode, micGranted)`) — listening exactly while hands-free is on AND the project screen is open; `Notifs.dictation` posts the silent mirror notification; `service/DictationReceiver.kt` handles the Stop action; Settings → Voice gained the three-way segmented control; a new `AudioLines` Lucide icon.
- 331 unit tests (15 new in `V0134FeaturesTest`): wake variants + false-positive guards, mode round-trips, the retryable matrix, buffer accumulate/replace/re-arm/cap semantics, settings persistence and the bilingual notification. Version 0.13.4 (code 18), lint clean, release APK 11.07MB.

## [0.13.3] — 2026-11-25 — Saqr Coach · مدرّب صقر

### Productivity · الإنتاجية
- **Saqr Coach (مدرّب صقر):** the fourth «عقل الصقر» item. A five-step learning path on the home screen walks a brand-new user from a fresh install to their **first successful on-device build**: connect a provider key → create the first project → send the first command → try the on-device build → reach the first successful build. The card shows live progress (n/5), the current focus and its how-to; tapping opens the full coach sheet with the ordered checklist and celebration state.
  مدرّب صقر: البند الرابع من «عقل الصقر». مسار تعلّم من خمس خطوات على الشاشة الرئيسية يأخذ مستخدمًا جديدًا تمامًا من أول تثبيت إلى **أول بناء ناجح على الجهاز**: ربط مفتاح مزوّد ← إنشاء أول مشروع ← إرسال أول أمر ← تجربة البناء على الجهاز ← تحقيق أول بناء ناجح. تُظهر البطاقة التقدم الحيّ (n/5) والخطوة الحالية وشرحها، والنقر يفتح لوحة المدرّب الكاملة بقائمة الخطوات المرتّبة وحالة الاحتفال.
- **Real signals only (لا اكتمال بلا فعل حقيقي):** nothing can be faked — a step completes only when the underlying signal exists (a stored key via `activeReady()`, an existing project, the first command the agent received in `AgentSession.send`, a build attempt in the build history, a successful build). The path never skips ahead: even a device that already built successfully focuses the first pending step, so a mid-life user sees exactly where they are. A failed attempt still completes «try the build» — the *final* gate is a successful one.
  لا اكتمال بلا فعل حقيقي: لا يمكن تزييف أي خطوة — تكتمل فقط حين يوجد الإشارة الحقيقي (مفتاح محفوظ عبر `activeReady()`، مشروع قائم، أول أمر استلمه الوكيل في `AgentSession.send`، محاولة بناء في سجل البناءات، بناء ناجح). الطريق لا يقفز للأمام: حتى جهاز سبق أن بنى بنجاح يركّز على أول خطوة غير مكتملة، فيرى المستخدم المتمرس مكانه بدقة. والمحاولة الفاشلة تُكمل خطوة «جرّب البناء» — بينما البوابة الأخيرة بناء ناجح.
- **Arabic starter commands (أمثلة أوامر جاهزة):** a curated library of concrete Arabic prompts — one family per template (web / Python / Node / Compose / IME / lite web / empty) plus universal ones — shown in the coach sheet with a copy button, so the first command is one paste away. The family follows the newest project's marker files (`MainActivity.kt` → Compose, `main.py` → Python …).
  أمثلة أوامر عربية جاهزة: مكتبة أوامر عربية ملموسة — عائلة لكل قالب (ويب / بايثون / Node / Compose / لوحة مفاتيح / ويب خفيف / فارغ) وأمثلة عامة — تُعرض في لوحة المدرّب بزر نسخ، فتصبح أول أمر على بعد لصقة واحدة. وتتبع العائلة ملفات أحدث مشروع المميّزة.
- **The coach remembers its story:** each step's completion time is stamped into a JSON ledger the first time its signal is observed, and shown as relative dates inside the sheet; the completed path turns the card into a small celebration («أول بناء ناجح — اكتمل الطريق») instead of disappearing. The card can be hidden from the sheet and re-opened from Settings → مدرّب صقر (which also unhides it).
  المدرّب يتذكر حكايته: يُختم وقت اكتمال كل خطوة في سجل JSON عند أول رصد لإشارته، ويظهر بتواريخ نسبية داخل اللوحة؛ والطريق المكتمل يحوّل البطاقة إلى احتفال صغير («أول بناء ناجح — اكتمل الطريق») بدل أن تختفي. ويمكن إخفاء البطاقة من اللوحة وإعادة فتحها من الإعدادات ← مدرّب صقر (ما يلغي الإخفاء أيضًا).

### Infrastructure · البنية
- New `agent/SaqrCoach.kt`: pure path logic (`CoachSnapshot` → ordered steps / current focus / progress), the `CoachProgressCodec` JSON ledger (round-trip safe, zero timestamps ignored, merge keeps first-recorded times), `SaqrCoach.guessTemplate` from marker files, and the `StarterPrompts` library. Two one-line hooks: `AgentSession.send` stamps `coachFirstMessageAt` once, and `BuildHistory` exposes `anyAttempt` / `anySuccess`. New `ui/CoachSheet.kt` (card + bottom sheet, bilingual strings, clipboard copy) wired into `HomeScreen` (card after the setup banners) and `SettingsScreen` (a coach row with the n/5 tag).
- 316 unit tests (11 new in `V0133FeaturesTest`): ordered path never skips, failed-attempt vs successful-build gating, visibility follows dismissal only, codec round-trip/garbage/merge semantics, Arabic content of every prompt family, dedup/cap/fallback of `forTemplate`, and template guessing. Version 0.13.3 (code 17).


## [0.13.2] — 2026-11-25 — Falcon Router · موجّه النماذج

### Productivity · الإنتاجية
- **Falcon Router (موجّه النماذج):** the third «عقل الصقر» item. The light agent stages — planning (JSON task plan), review (diff reading) and context compaction (summarising) — are routed to the **cheapest capable model** of the active provider, while the coder keeps the user's model untouched. Capability is tiered by model family (haiku / mini / flash / lite / nano → LIGHT, opus / pro / ultra → FULL, everything else MID); the reviewer requires MID so review quality is protected, planning and compaction run on LIGHT. An explicit per-stage pick (reviewer / planner model in Settings) always wins over the router, and the coder is never routed.
  موجّه النماذج «صقر»: البند الثالث من «عقل الصقر». المراحل الخفيفة — التخطيط (خطة مهام JSON) والمراجعة (قراءة الفرق) وضغط السياق (التلخيص) — تُوجَّه إلى **أرخص نموذج يكفي** لدى المزوّد النشط، بينما يبقى المبرمج على نموذج المستخدم بلا مساس. تُصنَّف القدرة بحسب عائلة النموذج (هايكو / ميني / فلاش → خفيف، أوبس / برو → كامل، وغيرها متوسط)، والمراجع يشترط متوسطاً حفاظاً على جودة المراجعة، والتخطيط والضغط يكفيهما خفيف. الاختيار اليدوي لكل مرحلة يتقدم على الموجّه دائماً، والمبرمج لا يُوجَّه أبداً.
- **Provable savings only (لا توجيه بلا إثبات):** routing happens only when the price table can prove it — every candidate must have a price row cheaper than the coder's (blended $/1M); a model without a price is never routed, an unpriced coder disables routing entirely. Vendor prefixes are normalized for the decision (`deepseek/deepseek-chat` routes by family) but the **original id is returned** to the API call, so multi-model providers (OpenRouter) keep working.
  لا توجيه بلا إثبات: لا يوجّه الموجّه إلا حين يثبت جدول الأسعار ذلك — يُشترط في كل مرشّح صف سعر أرخص من نموذج المبرمج (مزيج الدولار/مليون رمز)، والنموذج بلا سعر لا يُوجَّه أبداً، والمبرمج غير المُسعَّر يوقف التوجيه كله. تُوحَّد بادئات المزوّد للقرار («deepseek/deepseek-chat» يُصنَّف بعائلته) لكن **المعرّف الأصلي يعود** لنداء الـAPI، فتبقى مزوّدات النماذج المتعددة (OpenRouter) تعمل كما هي.
- **The published number (الرقم المعلن):** every routed call is priced at its model (actual) and at the coder model's price (baseline) — the counterfactual "what this would have cost" — and the pair is persisted daily in `usage/falcon.json` next to the usage ledger. Settings → Usage & budget shows «Saved by the router (this month)»: the saved dollars and the share of the baseline (hidden until the first routed call, never guessed). The PLAN acceptance (measured savings) is answered by construction: the number is measured continuously on the user's real workload, on their own price table, and stays visible in the app.
  الرقم المعلن: كل نداء موجَّه يُسعَّر عند نموذجه (الفعلي) وعند سعر نموذج المبرمج (خط الأس) — سيناريو «لو لم يوجد موجّه» — ويُخزَّن الزوج يومياً في `usage/falcon.json` بجوار سجل الاستهلاك. في الإعدادات ← الاستهلاك والميزانية يظهر سطر «وفّره الموجّه (هذا الشهر)»: الدولارات الموفَّرة ونسبتها من خط الأس (يخفى حتى أول نداء موجَّه، ولا يخمّن أبداً). معيار الخطة (توفير مقاس) محقَّق بالتصميم نفسه: الرقم يُقاس باستمرار على حمل المستخدم الحقيقي وبجدول أسعاره ويبقى ظاهراً في التطبيق.

### Infrastructure · البنية
- New `agent/FalconRouter.kt`: pure routing + meter logic (tiering, cheapest-capable pick with vendor normalization, actual/baseline metering) and a `SavingsLedger` (day → [actual, baseline], atomic writes, corrupt-file recovery, 400-day retention). Wired in `AgentSession`: `stageModel()` resolves planner / reviewer / compactor models (explicit pick → router → coder), compaction builds its own routed client when the pick differs, and routed calls record savings next to usage.
- 305 unit tests (13 new in `V0132FeaturesTest`): family tiering (cheap/heavy/unknown + vendor prefixes + the «gemini ⊃ mini» token-boundary trap), stage requirement gating, cheapest-capable routing (planner/compactor to LIGHT, reviewer kept MID), no-route-when-already-cheapest, unpriced coder/candidate refusal, dated-id price inheritance, original-id return for multi-model providers, meter math (actual vs baseline + 93.3% on the fixture), refuse-to-guess cases, and ledger day/month aggregation, restart persistence and junk/corrupt rejection. Version 0.13.2 (code 16).

## [0.13.1] — 2026-11-24 — Project index · فهرس المشروع

### Productivity · الإنتاجية
- **Project index (فهرس المشروع):** the second «عقل الصقر» item. A lightweight in-memory symbol index (files, classes, objects, interfaces, enums, functions) is built when the project opens — a regex-free single-pass scanner over symbol-bearing files on a small worker pool (89 ms for a 501-file / 3502-symbol project on the constrained CI sandbox), refreshed automatically when stale (20 s). The new `project_search(query)` tool answers `file:line · kind · name` instantly, so the model locates code instead of listing directories or reading whole files; `.git`, `build`, `node_modules` and files over 512 KB are skipped, with a 4000-file cap.
  فهرس المشروع: البند الثاني من «عقل الصقر». فهرس رموز خفيف في الذاكرة يتولد عند فتح المشروع — ماسح رموز بمرور واحد بلا regex على خيوط عمل متوازية (89ms لمشروع 501 ملفاً و3502 رمزاً على بيئة الاختبار المقيدة)، ويُجدد تلقائياً عند تقادمه (20 ثانية). الأداة الجديدة `project_search` تجيب بـ«ملف:سطر · النوع · الاسم» فوراً، فيحدد الوكيل مواضع الكود بدل قراءة ملفات كاملة، مع تخطي `.git` و`build` و`node_modules` والملفات فوق 512KB وسقف 4000 ملف.
- **Arabic-aware symbol search:** queries and names match through the same normalization as the memory bank (alef forms, taa marbouta, yaa, diacritics — now precompiled and pre-computed per symbol), so «الشّبكه» finds `الشبكة.kt`. Ranking: exact name → prefix → Arabic-normalized prefix → contains → path, shortest path first. **First result under 200 ms on a 500-file project** (the PLAN acceptance) — 8 new tests in `V0131FeaturesTest`, 292 total green.
  بحث رموز عربي-واعٍ: الاستعلام والأسماء تتطابق عبر توحيد بنك الذاكرة نفسه (الهمزات، التاء المربوطة، الياء، التشكيل — مُجمَّع مسبقاً ومُخزَّن لكل رمز)، فـ«الشّبكه» تجد الشبكة.kt. الترتيب: التام فالبادئة فالتطبيع العربي فالاحتواء فالمسار، والأقصر أولًا. **أول نتيجة أقل من 200ms على مشروع 500 ملف** (معيار الخطة) — 8 اختبارات جديدة في V0131FeaturesTest، و292 اختباراً ناجحاً إجمالاً.

## [0.13.0] — 2026-11-24 — Falcon Mind · عقل الصقر: the memory bank

### Productivity · الإنتاجية
- **Memory bank (بنك الذاكرة):** every project gains a durable memory of decisions, preferences and facts, stored in the project at `.saqr/memory.json` (schema-versioned JSON, atomic writes, corrupt-file recovery). The bank is rendered into the system prompt at the start of **every turn** inside a ~2000-character budget — preferences → decisions → facts, newest first — so Saqr honors «نستعمل Riverpod» and «كل الردود بالعربية» from previous sessions instead of re-asking. The file is git-ignored by Saqr's default `.gitignore` and hidden from the agent's project map.
- **Arabic-aware keys (مفاتيح عربية):** key normalization unifies alef forms (أ إ آ ٱ → ا), yaa alif maqsura (ى → ي), taa marbouta (ة → ه), ؤ/ئ, strips diacritics and tatweel, and keeps only letters/digits — «إدارة الحالة»، «اداره الحاله» and «التّطبيق»-style variants collapse to one key, so the same fact is never stored twice. `memory_save` is an **upsert** (keeps `createdAt`, bumps `updatedAt`), capped at 60 entries with oldest-update eviction.
- **The agent maintains its own memory:** two first-class tools — `memory_save(key, value, kind: preference|decision|fact)` and `memory_forget(key)` (exact normalized key first, then substring over keys/values) — with system-prompt guidance: honor memories without re-asking, save what the user states as durable, forget what they correct. A **secret guard** refuses memories that look like credentials: English assignment patterns (`password=`, `token:`, `api_key=`), Arabic secret words (كلمة السر، كلمة المرور، رمز الدخول، المفتاح الخاص), and known token shapes (`ghp_`, `sk-`, `AKIA`, `xoxb-`, 40+ char base64 runs) — memory is plain JSON, never a vault.
- **Memory sheet (واجهة بنك الذاكرة):** the chat overflow menu (⋯) opens the bank: entries grouped with kind tags (تفضيل / قرار / حقيقة), relative timestamps, tap-to-edit dialog (key + value + kind chips), delete per entry, clear-all with confirmation, and an empty state that explains the feature. Fully bilingual (AR/EN strings in the same commit).

### Infrastructure · البنية
- 284 unit tests (11 new in `V013FeaturesTest`): Arabic normalization, upsert semantics with `createdAt` preservation, cap eviction of the oldest, secret refusal (English + Arabic + token shapes), oversize/empty rejection, exact-then-substring forget, normalized search, render grouping with budget enforcement, restart persistence + corrupt-file recovery, tool-spec contract, and the system-prompt `# MEMORY` section (present only when the bank is non-empty). `SystemPrompt.build` gained a `memory` parameter (default empty, old call sites unaffected). Version 0.13.0 (code 14) · APK 11.55 MB (release, signed).


## [0.12.1] — 2026-11-24 — Extra Plumage · ريش مكمّلة: image→theme, Material You, semantic haptics & the field kit

### Appearance · المظهر
- **Image → theme (الثيم من صورة):** the Theme Studio now accepts a photo — pick any image and a deterministic offline pipeline (RGB histogram quantization, most-frequent buckets for the neutrals, the most saturated *distinct* bucket for the accent, WCAG-contrast text guard) derives a full six-color pack in milliseconds, fully on-device; a live preview row then applies / saves / copies it like any other pack. الخاتمة الفعلية لبند «الثيم من صورة» الذي كان مؤجلًا من 0.12.
- **Material You dynamic theme («ديناميكي»):** a fifth theme mode on Android 12+ re-tints only the accent family (accent / accentText / accentSoft / onAccent) from the wallpaper seed via `dynamicColorScheme`, over the fixed Saqr neutrals — the identity stays, the personality follows the phone; below 12 it falls back to the Saqr gold.
- **Semantic haptics tokens (توكنات الاهتزاز الدلالي):** every acknowledged touch maps to one token — TICK / POP / CONFIRM / REJECT / HEAVY — resolved to `HapticFeedbackConstants` with API-level fallbacks (CONFIRM/REJECT only exist on 30+). Wired to theme application (studio apply), build success/failure in the Build tab, and the image picker; system haptics settings still govern everything.

### Build · البناء
- **Field kit (عدّة الميدان):** closes the 0.12 «field numbers» item the practical way — every bootstrap/build step (rootfs, apt JDK, cmdline-tools, sdkmanager, aapt2, gradle) is timed automatically on-device with the device identity (model, SoC family detection for Snapdragon / MediaTek / Exynos / Tensor / Unisoc / Kirin, total RAM, battery %, APK size) and persisted per run; the Build tab shows the last build time and exports the records as a README-ready **Markdown table** (copy) or **CSV** (share) in one tap, so real Snapdragon + MediaTek numbers land in the repo straight from the phone. Failed runs never enter the table.

### Infrastructure · البنية
- 273 unit tests (12 new in `V0121FeaturesTest`): image palette (dark/light extraction, accent saturation + distinctness, WCAG text guard, empty-image rejection, pack round-trip), DynamicAccent (neutrals untouched, tint family, soft alpha), haptic token mapping with fallbacks, and FieldKit (SoC families, time formatting, Markdown/CSV export with quote escaping, JSON round-trip and junk rejection). Bilingual strings for every new surface (13 pairs); versionCode 13 · APK 16.0 MB (debug).

## [0.12.0] — 2026-11-24 — New Plumage · ريش جديدة: design system 2.0, Theme Studio & the command palette

### Appearance · المظهر
- **Theme Studio (استوديو الثيمات):** a mood phrase becomes a complete color theme through the active model (one-shot JSON with tolerant parsing), with live swatch preview, apply / save / copy-JSON, **six built-in community packs** (نجد، تهامة، البحر، الوادي، عُمان، الأندلس) and JSON import. A custom pack derives a full `SaqrColors` palette — borders, states, code and terminal colors — from just six colors, so every screen stays token-driven. Image → theme lands later.
- **OLED theme «ليل»:** a pure-black palette with the gold tuned a step lighter, as a fourth theme mode beside dark / light / system; instant switching, no restart.
- **Editor polish:** font size (12–20sp) and line wrap — both in Settings and on the editor toolbar (A− / A+ / wrap), applied live and persisted.

### Productivity · الإنتاجية
- **Arabic command palette:** a search button in the IDE top bar opens one box that reaches files, slash commands (Arabic aliases included), settings destinations and the sessions sheet — Arabic-aware matching (alef forms unify, diacritics and tatweel dropped) with ranked results.
- **Live home cards:** each project card shows the last on-device build result (✓ / ✗ with when) and the current git branch read straight from `.git/HEAD`; deleting a project clears its record.

### Build
- **Screenshot tests are excluded from the suite unless `SAQR_SCREENS=1`** — their compose activity rule launched before the `@Before` assume gate and failed the whole suite on machines without Robolectric native graphics; the opt-in behavior now matches the README.
- Version 0.12.0 (code 12) · APK 10.98 MB · new tests in `V012FeaturesTest` (theme packs, studio parsing, palette search, build history, editor settings, branch helper).

## [0.11.0] — 2026-11-24 — Plans, payments & stdio MCP · الخطط والمدفوعات وMCP عبر stdio

### Monetization · الدفع والترخيص
- **Plans in SAR:** Free (everything shipped so far, capped at 3 projects / 1 MCP server / 1 git host, no signed release builds, no team agents), **Pro SAR 15/mo**, **Student SAR 5/mo** (same features as Pro, honors-system), **Lifetime SAR 149**. The paywall (Home ★ or Settings → الاشتراك والترخيص) shows plan cards with SAR prices and the current status (active until / lifetime).
  خطط بالريال: المجانية (كل ما شُحن سابقاً بحدود: 3 مشاريع، خادم MCP واحد، مضيف git واحد، بلا إصدارات موقّعة ولا فريق وكلاء)، برو 15 ر.س شهرياً، الطلاب 5 ر.س شهرياً، ومدى الحياة 149 ر.س — وصفحة خطط عربية أولاً تظهر الخطة الحالية وتاريخها.
- **Google Play Billing (PBL 8.0):** products `saqr_pro_monthly` / `saqr_student_monthly` / `saqr_lifetime`; purchases are acknowledged immediately, re-read from Play on every connect (re-installs keep their plan), and devices without Play services fall back to the Moyasar path. Billing logic (tier merge, 31-day monthly trust, 3-day grace before expiry) lives in pure, tested code.
  فواتير جوجل بلاي: الشراء يُعتمد فوراً ويُقرأ من بلاي عند كل تشغيل، والأجهزة بلا بلاي تنتقل لمسار مويسر. منطق الاستحقاق (الدمج، الثقة الشهرية، سماح 3 أيام قبل الانتهاء) كله مُختبر.
- **Moyasar invoices + offline license tokens:** the paywall offers the merchant's public Moyasar payment page (mada, Apple Pay, STC Pay, cards — hosted by Moyasar) and a license-token field. Tokens (`SQ1.<payload>.<hmac>`, HMAC-SHA256) are minted by the developer after payment — Settings → Billing → **merchant tools** (create invoice per plan, open the hosted page, poll status, mint the token) or `scripts/license_tool.py` in the repo. The HMAC key comes from `keystore.properties` → `licenseKey` (private repo); the public fallback is explicitly non-secret.
  فواتير مويسر + رموز ترخيص دون اتصال: الدفع عبر صفحة مويسر المستضافة (مدى / Apple Pay / STC Pay)، والرمز `SQ1.` يُصنع بعد الدفع من أدوات التاجر داخل الإعدادات أو سكربت license_tool.py، ومفتاح التوقيع من keystore.properties (المفتاح العام الافتراضي غير سري عمداً).
- **Honest gating:** free users hit the paywall dialog when creating a 4th project, adding a 2nd MCP server, linking a 2nd git host, enabling team mode, or switching the Build tab to signed release — every gate explains the limit instead of failing silently.
  بوابات صادقة: محاولة تجاوز أي حد (مشروع رابع، خادم MCP ثانٍ، مضيف git ثانٍ، فريق الوكلاء، الإصدار الموقّع) تفتح نافذة الخطط وتشرح الحد بدل الفشل الصامت.
- **SecureStore hardening:** if the device keystore is unavailable (broken OEM keystore), the encrypted store resets once and finally falls back to plain prefs with a warning — degraded but alive, and unit tests can now boot the real store.
  تحصين المخزن السري: عند غياب الكيستور (بعض الأجهزة) يعيد المحاولة ثم ينتقل لتخزين عادي مع تحذير — يعمل بدل أن ينهار.

### Agent reach · وصول أوسع للوكيل
- **MCP over stdio:** the long tail of local MCP servers (filesystem, git, sqlite…) now works — a Settings form takes a name + one-line command (+ `K=V` env lines) and the server runs **inside the app's terminal env** (proot Linux when installed, /system/bin/sh otherwise), speaking newline-delimited JSON-RPC over stdin/stdout with a proper initialize handshake, per-call timeouts (20s handshake / 120s call), EOF and flood detection. stdio tools join the same `mcp_<server>_<tool>` namespace as HTTP servers and share the same plan budget.
  MCP عبر stdio: خوادم MCP المحلية تعمل الآن داخل بيئة الطرفية نفسها (proot أو sh)، بند MCP سطر واحد مع متغيرات بيئة اختيارية، ومصافحة initialize كاملة ومهل زمنية واكتشاف انقطاع، وتُدمج أدواتها في نفس مساحة أسماء أدوات خوادم HTTP.

### Infrastructure · البنية
- 269 unit tests (26 new in `V011FeaturesTest`): plan catalog/limits, entitlement states (grace/expiry/renew/merge + JSON round-trip), Moyasar amounts (halalas), Basic auth, invoice body/status parsing/error extraction, license token sign/verify (round-trip, lifetime, tamper, wrong key, expiry, Python-tool interop), SettingsStore purchase/license application, stdio config validation/env parsing/JSON-RPC framing.
- Google Play Billing Library 8.0.0 (8.3.0 needs Kotlin 2.2 metadata; v7 is no longer accepted by Play); bilingual strings for every new surface (54 pairs); versionCode 11.


## [0.10.0] — 2026-11-24 — Trust the APK, control the tools, own the forge · التحقق من التوقيع والتحكم بالأدوات ودعم GitLab/Gitea

### Build flexibility · مرونة البناء
- **In-app APK signature verification:** the Build tab gains «تحقق من التوقيع» — a from-scratch implementation of APK Signature Scheme v2/v3 verification (no apksigner download): it locates the APK Signing Block, checks the signature over `signed-data` with the embedded key (RSA PSS/PKCS1, ECDSA, DSA), recomputes the chunked content digests (1 MB chunks, 0xa5/0x5a framing, EoCD Central-Directory offset patched to the block start) and shows the signer certificate SHA-256 fingerprint, subject and expiry — so the user confirms it is *their* key before tapping Install. A modified APK fails loudly («APK contents do NOT match the signed digest»).
  التحقق من توقيع الـ APK داخل التطبيق نفسه: تحقق v2/v3 كامل بلا أدوات خارجية مع بصمة شهادة الموقّع SHA-256 ومالكها وصلاحيتها، وأي تعديل على الملف بعد البناء يُكتشف ويُرفض.
- **Keep only the last APK:** a Settings switch (also surfaced per-project) that, after every successful build, deletes the previous APKs in `dist/` plus the heaviest build leftovers (`app/build/intermediates`, `app/build/tmp`, the project `.gradle` cache) and logs the reclaimed size — one-tap storage hygiene for phones.
  «احتفظ بآخر إصدار فقط»: بعد نجاح كل بناء تُحذف ملفات APK القديمة ومخلفات البناء الوسيطة ويسجل المقدار المستعاد.

### Agent tool control · التحكم بأدوات الوكيل
- **MCP per-server tool allow/deny:** the tools browser gains an eye toggle per tool — hidden tools never reach the agent's spec list, a hidden tool is refused even if the model calls it anyway (defense in depth), and the turn notes how many tools were hidden. Stored encrypted-free in prefs as `{server-slug: [tools]}`.
  سماح/منع لكل أداة MCP لكل خادم: عين تُخفي الأداة عن الوكيل، فلا تظهر في قائمة أدواته، ويُرفض استدعاؤها حتى لو حاولها النموذج.

### Forge parity · تكافؤ المنصات
- **GitLab & Gitea end-to-end:** the Accounts screen gains a «GitLab / Gitea» card — link any HTTPS host (GitLab.com, Codeberg, self-hosted Gitea/Forgejo) with a token and pick its API dialect (Auto / GitLab / Gitea, with hostname auto-detection). The Git tab's Publish dialog now offers GitHub **and** every linked host (create repository + first push), and the PR flow opens a real **GitLab merge request** or **Gitea pull request** on the origin's forge automatically.
  دعم GitLab وGitea من البداية للنهاية: ربط أي مضيف HTTPS برمز واختيار نوع الـ API، والنشر ينشئ مستودعاً على المنصة المختارة، وطلب الدمج/السحب يُفتح على منصة origin تلقائياً.

### Infrastructure · البنية
- 243 unit tests; the v0.10 suite builds synthetic v2/v3-signed APKs on the JVM (keytool RSA + EC keys) and exercises the verifier end-to-end: accept, v3-preferred, ECDSA, tampered contents, corrupted signature, wrong signing key, unsigned zip — plus forge detection/URL parsing, GitLab project-id encoding, MCP disabled-tools round-trip/filter, and the keep-last-APK helpers.
- Bilingual strings for every new surface (values + values-ar, 20 pairs); versionCode 10.


## [0.9.0] — 2026-11-24 — Signed release builds, cache control & effortless sign-in · بناء موقّع وإدارة مخزون ودخول بلا رموز

### Field-ready build · بناء جاهز للميدان
- **Signed release builds on-device:** the Build tab gains an «إصدار موقّع» switch — point it at a keystore inside the project (e.g. `keystore/release.jks`), type the passwords (**kept in memory only** — never persisted, never logged), and the build runs `assembleRelease` signed by AGP (v1+v2+v3) through a generated Groovy init script; the output lands in `dist/app-release.apk`. Release is built without shrinking (R8 on a phone is not realistic) and the keystore path is validated against traversal outside the project.
  بناء إصدار موقّع على الجهاز بمفتاحك: مفتاح داخل المشروع وكلمات مرور في الذاكرة فقط، والتوقيع بنسخ v1+v2+v3 عبر سكربت init مولّد، والناتج في dist/app-release.apk دون تصغير ليناسب الهاتف.
- **aapt2 mirror presets:** Settings → On-device build now offers curated sources — «تلقائي» (the v0.8 GitHub-API lookup of `Llzzy/aarch64-android-builds`), the verified **ReVanced/aapt2** stable `releases/latest/download/aapt2-arm64-v8a` URL, or a custom direct URL — every source is still **ELF-validated** before use.
  مرايا aapt2 جاهزة: تلقائي (بحث GitHub)، أو ReVanced/aapt2 (رابط ثابت لأحدث إصدار)، أو رابط مخصص — وكلها تُفحص ELF قبل الاستخدام.
- **Build-cache management:** the Build tab shows what each component occupies (Debian rootfs, Android SDK, Gradle, downloads) with one-tap reclaim; reclaiming an environment component drops the ready marker so the next «تجهيز» re-fetches exactly what is missing (finished steps stay skipped).
  إدارة مخزون البناء: حجم كل مكوّن واسترجاعه بنقرة، مع إعادة «تجهيز» ذكية تجلب الناقص فقط وتتخطى ما أُنجز.

### Effortless sign-in & team polish · دخول أسهل وتحسينات الفريق
- **GitHub Device Flow sign-in (RFC 8628):** the accounts screen gains «الدخول برمز جهاز» — paste your OAuth App's **public Client ID** once, get a short code, approve in any browser at github.com/login/device; the app polls and links the account (badge «متصل · رمز جهاز»). No more pasting long tokens on a phone.
  الدخول إلى GitHub برمز الجهاز: تُلصق الـ Client ID العام مرة واحدة، ويظهر رمز قصير تُوافق عليه من أي متصفح، والتطبيق يستكمل الربط تلقائياً — بلا لصق رموز طويلة.
- **Per-provider model pickers:** the reviewer and planner model fields now carry a suggestions menu from the active provider instead of typing model ids blind.
  منتقي نموذج المراجع/المخطط يعرض نماذج المزوّد النشط كقائمة مقترحة بدل كتابة الأسماء العمياء.
- **MCP tools browser:** tap any MCP server in Settings to list its tools live (handshake + `tools/list`), read each tool's schema summary, edit JSON arguments (prefilled from the schema with type-based values) and **run it manually** to see the raw result — a debugging window into what the agent sees.
  مستعرض أدوات MCP: سرد أدوات كل خادم مباشرة، ووصف وسائطها، وتشغيلها يدوياً بوسائط JSON قابلة للتعديل (معبأة مسبقاً من المخطط).

### Infrastructure · البنية
- 227 unit tests (200 run in-sandbox; the rest need a device/emulator), including the signing contract (path safety + init-script env-only passwords), aapt2 preset resolution, cache sizing/formatting, and the MCP sampleArgs helper.
- Bilingual strings for every new surface (values + values-ar); versionCode 9.


## [0.8.0] — 2026-11-23 — Build hardening, planner & MCP · تحصين البناء والمخطط ودعم MCP

### Hardened on-device build path · تحصين مسار البناء على الجهاز
- **Pinned + resumable downloads:** every build component (ubuntu-base rootfs, cmdline-tools, Gradle) now carries the publisher's official SHA-256 and is verified before use; interrupted downloads resume with HTTP Range (`206`) instead of restarting, and a corrupted download fails loudly with the measured hash. Artifact sizes are capped, mirrors are tried in order (Gradle has a second official host).
  كل مكوّنات البناء مثبّتة بـ SHA-256 رسمي وتُتحقق قبل الاستخدام، والتنزيلات تُستأنف من نقطة الانقطاع بدل الإعادة من الصفر، مع سقف للحجم وقائمة مرايا.
- **aapt2 mirror + ELF validation:** the arm64 aapt2 can come from a **direct mirror URL set in Settings → On-device build**, falling back to the built-in GitHub lookup; whatever the source, the binary is rejected early unless it really is an **arm64 ELF** (magic + machine check).
  مرآة مباشرة لملف aapt2 من الإعدادات مع تحقق صارم أن الملف فعلاً ELF arm64 قبل استخدامه.
- **Friendly failure hints:** bootstrap/build failures get an actionable hint line in the log (out-of-memory → try the Lite template; network → retry; storage; proot; sdkmanager; apt; compile errors; aapt2).
  كل فشل شائع يقابله سطر إرشادي واضح في سجل البناء.
- **Crash monitor:** after building, «فحص الأعطال» reads the device crash log (`logcat -b crash`), filters the built package's FATAL EXCEPTION blocks (package parsed from `applicationId`) and shows them as short friendly lines in the Build tab.
  فحص أعطال التطبيق المبني من سجل النظام داخل تبويب البناء نفسه.
- **Lite Web template:** a zero-dependency WebView app (plain `android.app.Activity`, `android.useAndroidX=false`, one Kotlin file + `assets/index.html` Arabic RTL) that builds in minutes even on weak phones — validated end-to-end in CI-like conditions (605 KB APK, no AndroidX on the classpath).
  قالب «تطبيق ويب خفيف» بلا أي اعتماديات يُبنى في دقائق على الهواتف الضعيفة، ومجرّب بالكامل.

### Deeper agent team · فريق وكلاء أعمق
- **Explicit planner stage:** in team mode, a cheap model turns each substantial request into a 2–6 task plan **before coding** (distinct from `update_plan`); the plan fills the progress pill, lands as a `[Planner]` message, and the coder follows it. The planner model is selectable (Settings → Agent team) and its spend is shown separately. Small talk and slash commands skip it.
  مرحلة «مخطط» صريحة: نموذج اقتصادي يحوّل الطلب إلى خطة مهام قبل كتابة الكود، بنموذج قابل للاختيار وتكلفة تظهر منفصلة.
- **Automatic checks for the reviewer:** after each applied batch, `lint_project` (and `run_tests` when a runner exists, 120 s budget) runs and its output joins the reviewer's context under «Automatic checks» — a check failure caused by the diff is a real issue. Toggle in Settings (on by default).
  فحوصات تلقائية تُمرَّر إلى المراجع بعد كل دفعة تعديلات، مع مفتاح في الإعدادات.
- **Review cost in the meter:** the reviewer's tokens/cost are tracked per model and shown separately in the step meter («review: …») and as their own rows in the usage sheet; the planner's spend likewise.
  تكلفة المراجع والمخطط تظهران منفصلتين في عدّاد التكلفة وورقة الاستخدام.
- **MCP support (Streamable HTTP):** user-configured MCP servers (Settings → MCP servers: name + HTTPS URL + optional encrypted auth header) expose their tools to the agent as `mcp_<server>_<tool>` tools, discovered once per turn (up to 4 servers, 32 tools each) with strict JSON-RPC handling, SSE responses parsed, tool results flattened to text, and unreachable servers reported as a notice instead of failing the turn.
  دعم خوادم MCP عبر Streamable HTTP: تظهر أدواتها للوكيل بأسماء mcp_…، مع اكتشاف أثناء كل جولة، وتحليل ردود SSE، وإشعار واضح عند فشل الاتصال بدل إفشال الجولة.

### Infrastructure · البنية
- 216 unit tests (189 run in-sandbox; the rest need a device/emulator), including the full MCP protocol parser, planner parser, downloader primitives (SHA-256, Content-Range), ELF validator, hint mapper, crash parser and the lite template contract.
- Bilingual strings for every new surface (values + values-ar); versionCode 8.


## [0.7.0] — 2026-11-23 — Agent team, web tools & on-device build · فريق الوكلاء وأدوات الويب والبناء على الجهاز

### Agent team (Planner → Coder → Reviewer) · فريق الوكلاء
- **Settings → Agent team:** after every applied file batch a **reviewer** model receives the user's request and a compact diff, and must answer with a strict JSON verdict. Issues go back to the coder as a `[Reviewer]` message and are **fixed in the same turn** (max 2 review rounds; the loop detector still applies). The reviewer model is selectable (blank = the coder's model — a cheaper, faster model works well) and a **verdict card** (ok / issues / failed) appears in the chat.
  في الإعدادات: «فريق الوكلاء» — بعد كل دفعة تعديلات يراجعها نموذج ثانٍ ويعيد الوكيل إصلاح ملاحظاته في الجولة نفسها (حتى جولتَي مراجعة)، بنموذج مراجع اختياري وبطاقة حكم في المحادثة.
- The reviewer shares the active provider and is billed per call (its usage lands in the same meter under the reviewer model).

### Context compaction & loop guard · ضغط السياق وكاشف الحلقات
- **Auto compaction:** when the estimated context passes ~150k chars, the oldest safe block of the model's message list is summarised into one `[COMPACTED EARLIER CONTEXT]` brief (goals, decisions, files, pending work) and the loop continues from it — instead of silently losing the far end. Cut points are always user-message boundaries, so no tool call/result pair is ever split. Settings toggle (on by default) and a notice pill in the chat.
  ضغط السياق تلقائياً عند تجاوز الحد: يُلخّص أقدم جزء آمن من السجل إلى موجز واحد ويكمل الوكيل منه، مع مفتاح في الإعدادات وتنبيه في المحادثة.
- **Loop detector:** the same tool fingerprint (name + canonical args) three steps in a row gets the model one nudge; a second detection **stops the turn** with a clear notice instead of burning tokens in circles.
  كاشف الحلقات: توجيه أول عند تكرار نفس الأدوات بنفس الوسائط، ثم إيقاف الجولة بتنبيه واضح.

### New agent tools · أدوات جديدة للوكيل
- **`web_search`:** DuckDuckGo HTML search (no key needed) returning titles, URLs and snippets; **`web_fetch`:** readable text of a page (HTML stripped, capped, JSON pretty-printed). Both are **public internet only** — localhost, LAN ranges (10/172.16–31/192.168/100.64–127/169.254), `.local`, embedded credentials and cloud metadata endpoints are blocked, and the resolved DNS is re-checked against private ranges.
  بحث ويب وقراءة صفحة بدون مفاتيح، مع حجب العناوين الخاصة/المحلية ونقاط البيانات السحابية والتحقق من DNS.
- **`lint_project`:** `node --check` for JS, `python3 -m py_compile` for Python inside the terminal, plus pure JSON validation and HTML tag-balance checks with `file: issue` output.
  فحص جودة سريع: تحقق صيغة جافاسكربت وبايثون في الطرفية، وتحقق JSON وتوازن وسوم HTML مباشرة.
- **`run_tests`:** detects `npm test` / `pytest` / `python -m unittest` from the project layout and runs the first match (300 s budget), telling the agent to offer writing tests when none exist.
  تشغيل الاختبارات: يكتشف المشغّل المناسب للمشروع ويشغّله، ويقترح كتابة اختبارات إن لم توجد.

### On-device APK build (experimental) · بناء APK على الجهاز
- New **Build** tab: one tap prepares a Debian build environment inside proot — ubuntu-base arm64 rootfs, OpenJDK 17 via apt, Android cmdline-tools + `platforms;android-35` via sdkmanager, an **arm64 aapt2** fetched from a community GitHub release (Google ships build-tools for x86_64 only), and Gradle 8.11.1. Every step streams into a live log and retries skip finished steps.
  تبويب «بناء» جديد: يجهّز ديبيان وJDK 17 وأدوات Android SDK داخل proot مع aapt2 لمعمارية arm64 وGradle، ويعرض السجل مباشرة، وإعادة المحاولة تتجاوز الخطوات المكتملة.
- **Build APK** runs `assembleDebug` with `android.aapt2FromMavenOverride` pointed at the arm64 binary and a phone-friendly 1.4 GB heap, copies the APK to `dist/` in the project, and **installs it** with one tap (unknown-sources confirm). First preparation downloads ~600 MB; a small Compose app builds in 5–15 minutes on a mid-range phone.
  زر «بناء APK» يشغّل Gradle بنقرة، ينسخ الناتج إلى `dist/` داخل المشروع، ويثبّته مباشرة. التجهيز الأول ينزّل نحو 600 ميغابايت وبناء تطبيق صغير يستغرق 5–15 دقيقة.

### New templates · قوالب جديدة
- **Kotlin + Jetpack Compose app** (Arabic/RTL-ready, strings in `values` + `values-ar`, Compose BOM) and an **Arabic IME keyboard** (InputMethodService, `arabic_keyboard.xml` layout, method.xml) — both compile straight from the new Build tab for a phone-only workflow.
  قالبا «تطبيق Compose» و«لوحة مفاتيح عربية» — كلاهما يُبنى مباشرة من تبويب البناء دون حاسوب.

## [0.6.0] — 2026-11-23 — Chat history, voice & background agent · سجل المحادثات والصوت والعمل في الخلفية

### Persistent chat history · حفظ سجل المحادثات
- Every project's conversation is **saved on the phone** (one JSON file per session; attached images stored as files), written ~1 s after each change and when the app pauses. The **last session reopens on app start** and the agent **really resumes**: the model's message list is rebuilt from the saved chat (tool calls and results included; still only the last 2 images keep their pixels).
  تُحفظ كل محادثة على الهاتف (ملف لكل جلسة وصورها كملفات)، وتُفتح آخر جلسة عند تشغيل التطبيق ويكمل الوكيل فعليًا من السجل المحفوظ.
- **Sessions** list (chat ⋮ menu and side drawer): new session, open, **rename**, **delete**, **search** (Arabic-aware: ignores diacritics and alef/yaa/taa-marbuta forms), automatic title from the first message. Up to 200 sessions per project, with a warning above 50 MB.
  قائمة «الجلسات»: جلسة جديدة، فتح، إعادة تسمية، حذف، بحث يفهم العربية، وعنوان تلقائي من أول رسالة. حتى 200 جلسة لكل مشروع مع تنبيه فوق 50 ميغابايت.
- `ask_user` after a restart now **continues the real turn**: your answer goes back to the model as the tool result instead of a new message. `/clear` and “New chat” keep the old chat in the list.
  الإجابة على سؤال الوكيل بعد إعادة التشغيل تكمل الجولة نفسها.

### Voice · الصوت
- **Mic button** in the composer (next to send while the box is empty): live dictation with partial results into the message box; tap again to stop. Language follows the app (Arabic **ar-SA** / English **en-US**); **long-press** switches. Clear messages when no speech service is installed or the microphone is blocked (permission asked with an explanation).
  زر ميكروفون للإملاء المباشر بالعربية أو الإنجليزية، والضغط المطوّل يبدّل اللغة.
- **Read aloud** on assistant replies (TextToSpeech, Arabic voice when available; code blocks and markdown are skipped).
  «اقرأ بصوت» لردود الوكيل مع تخطّي الشيفرة.
- Settings → Voice: **send after dictation** (off by default) and dictation language.
  إعداد «الإرسال بعد الإملاء» (مغلق افتراضيًا) ولغة الإملاء.

### Background agent · الوكيل في الخلفية
- Agent turns now run under a **foreground service** (`AgentService`) and **keep going when you leave the app, swipe it from recents, or lock the screen**. A partial wake lock (60-min safety timeout, re-armed every step) and a Wi-Fi lock are held only while a turn is actually running.
  تستمر المهام عند مغادرة التطبيق أو إزالته من التطبيقات الأخيرة أو قفل الشاشة، عبر خدمة أمامية وقفل استيقاظ وشبكة أثناء العمل فقط.
- **Notifications** with the gold Saqr mark: ongoing (project · step · current tool such as “Writing app.kt…”, elapsed time, **Stop** / **Open**); **“Saqr needs your answer”** for git push/commit approvals and `ask_user` (single-choice questions with up to 3 options get **inline answer buttons**); a summary when a task finishes or fails.
  إشعار مستمر بالخطوة والأداة والوقت مع «إيقاف» و«فتح»، وإشعار «صقر يحتاج ردّك» مع أزرار إجابة مباشرة، وملخص عند الانتهاء أو التعثّر.
- **Survives being killed**: the service is sticky and state is saved, so if Android kills the process mid-task it restarts and **resumes the interrupted turn from the saved history** (“Resumed after interruption”). After a **reboot**, a “Resume task?” notification offers to continue (nothing runs until you tap).
  إذا أوقف النظام التطبيق أثناء المهمة يُستأنف العمل من السجل المحفوظ، وبعد إعادة تشغيل الهاتف يظهر إشعار «استئناف المهمة؟».
- **Battery**: a one-time explanation of OEM battery killers (Infinix/XOS, Xiaomi, …) with shortcuts to allow unrestricted battery use; Settings toggle **Keep running in background** (on by default).
  شرح لمرة واحدة عن قيود البطارية في هواتف إنفينكس وشاومي وغيرها، وخيار «العمل في الخلفية» (مفعّل افتراضيًا).

## [0.5.0] — 2026-11-23 — Preview element picker & image → code · منتقي العناصر والصورة إلى كود

### Preview element picker · منتقي العناصر في المعاينة
- **Tap an element** in the web preview to select it, then **describe the change** in plain words (Arabic or English); the agent receives the element's tag, selector, text and attributes, is pointed at the **matched source line**, and edits it directly.
  انقر أي عنصر في المعاينة، صف التعديل المطلوب، والوكيل يجد السطر المطابق في الكود ويعدّله.
- With a vision-capable model, a **cropped screenshot** of the picked element is attached for extra context.
  مع النماذج التي تدعم الصور تُرفق لقطة مقصوصة للعنصر.
- **Secure, local-only bridge**: the picker runs only on local preview pages (the project's own files or localhost), and every message must carry a per-page random **token** that is regenerated on each navigation; remote pages cannot talk to the app.
  جسر آمن ومحلي فقط: يعمل على صفحات المشروع المحلية فقط، مع رمز عشوائي يتجدد مع كل تنقّل.

### Image → code & image attachments · الصورة إلى كود ومرفقات الصور
- **Attach images** to any message from the **camera** or the **photo picker**; send a screenshot or mockup and the agent rebuilds it for the project's stack (HTML/CSS/JS or Jetpack Compose, Arabic/RTL-aware; asks when the stack is unclear).
  أرفق صورًا من الكاميرا أو المعرض، وأرسل لقطة أو تصميمًا ليبنيه الوكيل.
- **Multimodal requests** for OpenAI-compatible providers (OpenAI, Gemini, OpenRouter, DeepSeek, custom) and the **Anthropic** Messages API.
  طلبات متعددة الوسائط لمزوّدي OpenAI المتوافقين وAnthropic.
- **Vision auto-detect** per model, with a **per-model override** (Auto / On / Off) in Settings; sending images to a non-vision model warns you and suggests a vision-capable model.
  اكتشاف تلقائي لدعم الصور لكل نموذج، مع تجاوز يدوي لكل نموذج من الإعدادات، وتنبيه واقتراح نموذج بديل.
- **Last-2-images pruning**: only the two most recent images stay in the conversation context; older ones are replaced by a short placeholder to save tokens and cost.
  الاحتفاظ بآخر صورتين فقط في السياق لتوفير الرموز والتكلفة.

## [0.4.0] — 2026-11-23 — Editor, interactive agent & cost control · المحرر والوكيل التفاعلي والتكلفة

### Editor · المحرر
- **Incremental syntax highlighting** for 12 languages (Kotlin, Java, JS/TS, Python, HTML/XML, CSS, JSON, Markdown, shell, Go, Rust, C-family, Dart), re-tokenising only the edited lines so large files stay smooth.
  تمييز صيغة تدريجي لـ 12 لغة يعيد تحليل الأسطر المعدّلة فقط.
- **Find & replace** with case sensitivity and **regex** (with capture groups), match counter, next/previous, replace one / replace all.
  بحث واستبدال مع دعم التعابير النمطية (Regex).
- **Undo / redo** with grouped typing history and keyboard shortcuts (Ctrl+Z undo, Ctrl+Y / Ctrl+Shift+Z redo, Ctrl+F find, Ctrl+H replace, F3 / Shift+F3 next/previous, Esc closes).
  تراجع وإعادة مع اختصارات لوحة المفاتيح.

### Interactive agent · الوكيل التفاعلي
- New **`ask_user`** tool: the agent asks a question as an **interactive choice card** (single or multiple choice, with a free-text "other" answer) and waits for your pick.
  أداة `ask_user`: يسألك الوكيل ببطاقة خيارات تفاعلية وينتظر اختيارك.
- **Quick-reply chips**: when a reply ends with a numbered or bulleted list of options (Western or Arabic-Indic digits), each option becomes a tappable chip above the composer.
  رقائق رد سريع تظهر تلقائيًا عند سرد خيارات في الرد.

### Checkpoints · نقاط الاستعادة
- **Checkpoints** replace the old restore points: every agent turn that changes files saves a checkpoint (files touched, created and deleted); browse them in a sheet and **restore** any one, which reverts it and every newer change.
  نقاط استعادة لكل جولة تعديل، واسترجاع أي نقطة بنقرة.

### Slash commands · أوامر الشرطة المائلة
- Built-in `/fix`, `/explain`, `/test`, `/review`, `/plan`, `/commit`, `/clear`, `/model`, with **Arabic aliases** (`/اصلح`, `/اشرح`, `/اختبر`, `/راجع`, `/خطة`) and an autocomplete popup in the composer.
  أوامر جاهزة مع أسماء عربية بديلة وإكمال تلقائي.
- **Custom commands**: define your own `/name` → prompt template in Settings.
  أوامر مخصصة تعرّفها بنفسك من الإعدادات.

### Cost control · التحكم بالتكلفة
- **Token / cost meter**: input and output tokens (including cached input) per turn and per session, with the estimated cost per model; today and this-month totals in Settings.
  عدّاد الرموز والتكلفة لكل جولة وجلسة، ومجموع اليوم والشهر.
- **Monthly budget**: warning at 80%, confirmation required before continuing at 100%.
  ميزانية شهرية: تنبيه عند 80% وتأكيد عند تجاوزها.

### Safety · الأمان
- **`git_commit` now requires approval**: the agent shows the commit message and waits for your yes before committing (like `git_push`).
  الإيداع (`git_commit`) أصبح يتطلب موافقتك.

## [0.3.0] — 2026-11-23 — Git & accounts · Git والحسابات

### Git engine · محرّك Git
- Real git on the device via **Eclipse JGit** (no git binary): init, clone, status with line counts, stage/unstage/discard, commit/amend, history, diffs, branches (create/switch/delete/merge, conflict state), remotes, fetch/pull/push with upstream tracking. Tokens are redacted from every message.
  Git حقيقي على الجهاز عبر JGit: إيداع، سجل، فروع، دمج، سحب ودفع، مع إخفاء الرموز من كل الرسائل.
- New **Source control** screen (Changes / History / Branches tabs, diff viewer, publish to GitHub, open pull request).

### GitHub accounts · حسابات GitHub
- New **Settings → Accounts** screen: paste a fine-grained token with an inline scope guide (Contents: Read & write, Metadata: Read, Pull requests optional), a button that opens GitHub's token page, **Verify & link** (checks the token with the GitHub API), avatar + @username when linked, and **Sign out**.
  شاشة الحسابات: لصق الرمز مع دليل الصلاحيات، التحقق والربط، صورة الحساب واسم المستخدم، وتسجيل الخروج.
- **Commit identity** (author name / email), defaulting to the linked account's name and noreply address.
- Tokens stored encrypted (Android Keystore); the v0.2 token migrates automatically. Other HTTPS hosts (GitLab, Gitea, …) supported in the account store.
- "Link GitHub" in the Git screen now opens the Accounts screen directly.

### Agent git tools · أدوات Git للوكيل
- `git_status`, `git_diff`, `git_log`, `git_commit`, `git_branch`, `git_pull`, `git_push`, backed by the JGit engine and the linked account.
- **`git_push` always asks for approval** with a card showing the remote, branch and URL, whatever the approval mode.
  الدفع (push) يتطلب موافقتك دائمًا.

### Terminal · الطرفية
- Full **Termux terminal emulator** (VT100/xterm) with multiple sessions, quick keys and adjustable font size, on the Alpine/proot environment.

### Brand · الهوية
- New **"Stoop"** falcon icon (adaptive + monochrome) and in-app logo mark.
- **Splash screen** (AndroidX SplashScreen, Android 12 style, backported to Android 8+) with the Stoop mark.

## [0.2.0] — 2026-11-22 — "Falcon" UI redesign · إعادة تصميم الواجهة

### Design system · نظام التصميم
- New "Falcon" design system (`ui/theme`): near-black graphite surfaces with layered elevations and hairline borders, one signature accent — **Saqr Gold `#E9B44C`** (falcon/falconry identity, premium on dark, ~10:1 contrast, distinct from the blue most coding tools use) — plus a full **light theme**; theme switch in settings (dark / light / system).
  نظام تصميم جديد: أسطح داكنة متدرجة وحدود رفيعة، ولون مميز واحد هو ذهبي الصقر، مع سمة فاتحة كاملة.
- Type scale on IBM Plex Sans Arabic (now incl. SemiBold) + **JetBrains Mono** for code, paths and numbers; spacing / radius / motion tokens.
- Motion: press-scale on every tappable surface, animated expand/collapse, streaming shimmer, animated progress, fade transitions between screens and panes.
- Numbers are shown with Latin digits everywhere for consistency.

### Icons · الأيقونات
- Coherent Lucide-style stroke icon set (auto-mirrored in RTL where directional).
- Original colored **file-type glyphs** (kt, java, py, js, ts, html, css, json, md, sh, go, rs, dart, …), folder glyph, terminal & tool icons, provider monograms.
- **New falcon app icon**: gold peregrine head whose malar stripe is a code slash, on a graphite gradient with a soft glow; adaptive icon with a proper **monochrome** layer for Android 13 themed icons.

### Agent chat · محادثة الوكيل
- Floating glass top bar: drawer button, **plan pill** ("الخطة 2/5", tap for the step list) with pause, preview & terminal buttons.
- New `update_plan` tool: the agent keeps a live step plan for multi-step tasks.
- Every tool call is a compact row: file-type icon, filename, action (إنشاء/تعديل/قراءة/حذف), **+N/−N diff chips**, duration, status (✓ / ! / spinner) and a chevron that expands to the diff or the command output.
- Plan-approval card redesigned; amber **system notice** cards and red error cards; "stopped" pill.
- **Step progress bar** and **context-token meter** (e.g. 55.4k / 200k, per-provider window).
- Rounded composer: **+ attach project file** (@mention), **model chip** with provider badge and quick model switch, gold send / animated stop button.
- Markdown renderer: headings, lists, quotes, links, inline code, **code blocks with language label, copy button and syntax highlighting**; each paragraph picks its own direction so Arabic/English mixed text reads correctly.

### Other screens · بقية الشاشات
- **Onboarding** (first run, 3 pages) → straight to provider setup.
- **Home**: hero card, setup banner when no key, recent-project cards (main-file icon, time, file count), **template gallery**.
- **Files & editor**: icon tree with folder glyphs and sizes, pill tabs with dirty dot, line-number gutter with current line, status bar, light/dark code palettes.
- **Terminal**: window chrome, colored prompt/echo, quick keys (history ↑↓, ls, pwd, clear, apk update, git status), real `clear`.
- **Preview** pane (new): renders the project's `index.html` in a WebView.
- **Git** pane as a card with connection status.
- **Settings**: grouped cards, segmented theme/language controls, provider rows with monogram badges and inline editors.
- Navigation drawer for the workspace (Agent, Files, Terminal, Git, Preview, restore points, settings).

### Engineering
- Screens split into stateless `*Content` composables; **JVM screenshot tests** (Robolectric native graphics) render Arabic dark/light screens to `dist/screens/` (`SAQR_SCREENS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'`).
- 23 JVM unit tests (+3: Markdown parser, digits/token formatting, plan tool).
- versionCode 2, signed with the same release key as 0.1.0 (in-place upgrade).

## [0.1.0] — 2026-11-22 — MVP

### Added · أُضيف
- Arabic/English UI with RTL, per-app language switch, IBM Plex Sans Arabic, dark Material 3 theme, falcon adaptive icon.
  واجهة عربية وإنجليزية مع RTL، وتبديل اللغة، وخط IBM Plex Sans Arabic، وثيم داكن، وأيقونة صقر.
- BYOK providers: Anthropic, OpenAI, Google Gemini, OpenRouter, DeepSeek, custom OpenAI-compatible; keys encrypted with Android Keystore; model list fetch.
  مزوّدون بمفتاحك الخاص، والمفاتيح مشفّرة بـ Keystore، وجلب قائمة النماذج.
- Projects with templates (Web, Python, Node, Empty) and `AGENTS.md` rules.
  مشاريع بقوالب وملف قواعد `AGENTS.md`.
- File tree, tabbed code editor with syntax highlighting and save.
  شجرة ملفات ومحرر بتبويبات وتمييز صيغة.
- Root-less Linux terminal: proot + Alpine 3.24.4 (downloaded on first run, SHA-256 verified); basic piped shell.
  طرفية لينكس بلا Root: ‏proot و‏Alpine.
- AI agent: streaming, tool loop (list/read/write/edit/delete/run/search), plan approval for multi-file changes, diff preview, restore points, command safety blocklist, language mirroring (Arabic dialects understood).
  وكيل ذكي ببث مباشر، وأدوات، وموافقة على الخطة، ومعاينة الفروقات، ونقاط استرجاع، وحظر الأوامر الخطرة.
- GitHub commit & push via JGit with a fine-grained token; optional private-repo creation.
  إيداع ودفع إلى GitHub عبر JGit.
- 20 JVM unit tests (safety policy, diff, SSE + provider wire formats, LLM client against a mock server, tools, snapshots, tar extraction) + opt-in Alpine integration test.
