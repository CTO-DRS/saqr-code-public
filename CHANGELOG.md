# Changelog · سجل التغييرات

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
