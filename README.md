# صقر كود · Saqr Code 🦅

**وكيل برمجة ذكي على أندرويد، عربي أولاً.** اكتب طلبك بالعربي (حتى باللهجة الخليجية) أو بالإنجليزي، والوكيل يقرأ مشروعك ويعدّل الملفات ويشغّل الأوامر في طرفية لينكس حقيقية على جوالك، بمفتاحك الخاص (BYOK).

**An Arabic-first AI coding agent for Android.** Ask in Arabic (Gulf dialect included) or English; the agent reads your project, edits files and runs commands in a real root-less Linux terminal on your phone, using your own API key.

> الإصدار / Version: **0.10.0** · Package: `com.ctodrs.saqrcode` · minSdk 26 · targetSdk 35 · arm64-v8a

---

## العربية

### ماذا يقدّم الإصدار 0.1
| الميزة | الحالة |
|---|---|
| واجهة عربية وإنجليزية كاملة مع RTL، وتبديل اللغة من الإعدادات، وخط IBM Plex Sans Arabic، وثيم داكن، وأيقونة صقر | ✅ |
| مزوّدو ذكاء اصطناعي بمفتاحك: Anthropic وOpenAI وGoogle Gemini وOpenRouter وDeepSeek ومزوّد مخصص متوافق مع OpenAI (APIs رسمية فقط، بلا كشط جلسات ويب) | ✅ |
| حفظ المفاتيح مشفّرة (EncryptedSharedPreferences + Android Keystore)، والنسخ الاحتياطي معطّل | ✅ |
| اختيار النموذج يدويًا أو جلب القائمة من واجهة النماذج الرسمية للمزوّد | ✅ |
| مشاريع داخل التطبيق بقوالب: HTML/CSS/JS وبايثون وNode.js وفارغ | ✅ |
| شجرة ملفات، ومحرر كود بتبويبات وتمييز صيغة وأرقام أسطر وحفظ | ✅ |
| طرفية لينكس بلا Root: ‏proot + ‏Alpine 3.24 يُنزَّل عند أول تشغيل (~4 ميغابايت، مع تحقق SHA-256) | ✅ (طرفية أساسية بلا PTY) |
| الوكيل: محادثة ببث مباشر، وحلقة أدوات (list_files وread_file وwrite_file وedit_file وdelete_file وrun_command وsearch) | ✅ |
| موافقة على الخطة عند تعديل ملفين أو أكثر، ومعاينة الفروقات (diff) | ✅ |
| نقاط استرجاع تلقائية قبل كل دفعة تعديلات، واسترجاع بنقرة | ✅ |
| قائمة حظر للأوامر الخطرة (rm -rf /، ‏curl \| sh، ‏eval، ‏sudo، ‏mkfs …) | ✅ |
| GitHub: إيداع ودفع حقيقي عبر JGit برمز Fine-grained، وإنشاء مستودع خاص اختياريًا | ✅ |

### الجديد في 0.10
| الميزة | الحالة |
|---|---|
| التحقق من توقيع الـ APK داخل التطبيق (v2/v3 كامل، ببصمة شهادة الموقّع SHA-256 ومالكها وصلاحيتها) قبل زر التثبيت | ✅ |
| «احتفظ بآخر إصدار فقط»: بعد نجاح البناء تُحذف ملفات APK القديمة ومخلفات البناء الوسيطة تلقائياً | ✅ |
| سماح/منع أدوات MCP لكل خادم: عين تُخفي الأداة عن الوكيل نهائياً | ✅ |
| GitLab وGitea بالكامل: ربط المضيفين وإنشاء المستودعات وفتح طلبات الدمج/السحب من تبويب Git | ✅ |

### الجديد في 0.9
| الميزة | الحالة |
|---|---|
| بناء إصدار موقّع على الجهاز: مفتاح داخل المشروع وكلمات مرور في الذاكرة فقط، وتوقيع AGP بنسخ v1+v2+v3 عبر سكربت init مولّد، والناتج في `dist/app-release.apk` | ✅ |
| مرايا aapt2 جاهزة: «تلقائي» أو **ReVanced/aapt2** (رابط ثابت لأحدث إصدار) أو رابط مخصص — وكلها تُفحص ELF arm64 قبل الاستخدام | ✅ |
| إدارة مخزون البناء في تبويب «بناء»: حجم كل مكوّن (rootfs/SDK/Gradle/التنزيلات) واسترجاعه بنقرة، وإعادة تجهيز تجلب الناقص فقط | ✅ |
| الدخول إلى GitHub برمز الجهاز (Device Flow): Client ID عام + رمز قصير تُوافق عليه من المتصفح — بلا لصق رموز طويلة | ✅ |
| منتقي نموذج المراجع/المخطط بقائمة مقترحة من المزوّد النشط بدل كتابة الأسماء يدوياً | ✅ |
| مستعرض أدوات MCP في الإعدادات: سرد أدوات كل خادم مباشرة وتشغيل أي أداة يدوياً بوسائط JSON معبأة من المخطط | ✅ |

### الجديد في 0.8
| الميزة | الحالة |
|---|---|
| تحصين البناء: تنزيلات مثبّتة بـ SHA-256 رسمي وقابلة للاستئناف (HTTP Range)، وقائمة مرايا مع مرآة aapt2 من الإعدادات وتحقق ELF arm64 صارم | ✅ |
| إرشادات فشل ودّية في سجل البناء (نفاد الذاكرة، الشبكة، التخزين، proot، أخطاء الكود…) ومراقب أعطال التطبيق المبني داخل تبويب «بناء» | ✅ |
| قالب «تطبيق ويب خفيف»: WebView بلا أي اعتماديات — يُبنى في دقائق على الهواتف الضعيفة (مُجرّب بالكامل) | ✅ |
| مرحلة «مخطط» صريحة في وضع الفريق: نموذج اقتصادي يحوّل الطلب إلى خطة مهام قبل الكود، بنموذج قابل للاختيار | ✅ |
| فحوصات تلقائية (فحص + اختبارات) تُمرَّر إلى المراجع مع مفتاح في الإعدادات | ✅ |
| تكلفة المراجع والمخطط تظهران منفصلتين في عدّاد التكلفة وورقة الاستخدام | ✅ |
| دعم خوادم **MCP** عبر Streamable HTTP: أدوات الخادم تظهر للوكيل بأسماء `mcp_…` مع تحليل SSE وترويسة مصادقة مشفّرة | ✅ |

### الجديد في 0.7
| الميزة | الحالة |
|---|---|
| فريق الوكلاء (مخطط ← مبرمج ← مراجع): بعد كل دفعة تعديلات يراجعها نموذج ثانٍ ويتصحّح الوكيل تلقائياً، بنموذج مراجع قابل للاختيار وبطاقة حكم في المحادثة | ✅ |
| ضغط السياق تلقائياً: تلخيص الجولات الأقدم عند تجاوز الحد بدل فقدانها (مفتاح في الإعدادات) | ✅ |
| كاشف الحلقات المفرغة: توجيه عند التكرار ثم إيقاف الجولة لتوفير الرموز | ✅ |
| أدوات جديدة: `web_search` و`web_fetch` (الإنترنت العام فقط مع حجب العناوين الداخلية)، و`lint_project`، و`run_tests` | ✅ |
| بناء APK على الجهاز (تجريبي): ديبيان + JDK 17 + Android SDK داخل proot مع aapt2 arm64 وسجل مباشر وتثبيت بنقرة | ✅ |
| قوالب جديدة: تطبيق Kotlin + Compose عربي RTL، ولوحة مفاتيح IME عربية — تُبنى مباشرة من تبويب «بناء» | ✅ |

### الجديد في 0.6
| الميزة | الحالة |
|---|---|
| حفظ سجل المحادثات على الهاتف واستعادة آخر جلسة عند التشغيل، مع استئناف فعلي للوكيل | ✅ |
| قائمة الجلسات: جديدة، فتح، إعادة تسمية، حذف، بحث يفهم العربية، عنوان تلقائي | ✅ |
| إدخال صوتي بالعربية (ar-SA) والإنجليزية (en-US) مع نتائج مباشرة، و«اقرأ بصوت» للردود | ✅ |
| الوكيل يعمل في الخلفية (خدمة أمامية) مع إشعارات الخطوة و«صقر يحتاج ردّك» وأزرار إجابة مباشرة | ✅ |
| استئناف المهمة بعد أن يوقف النظام التطبيق، وعرض «استئناف المهمة؟» بعد إعادة تشغيل الهاتف | ✅ |

### الجديد في 0.5
| الميزة | الحالة |
|---|---|
| منتقي العناصر في المعاينة: انقر عنصرًا، صف التعديل، والوكيل يعدّل السطر المطابق في الكود | ✅ |
| جسر آمن ومحلي فقط بين المعاينة والتطبيق برمز عشوائي لكل صفحة | ✅ |
| الصورة إلى كود: إرفاق صور من الكاميرا أو المعرض، وطلبات متعددة الوسائط (OpenAI المتوافق وAnthropic) | ✅ |
| اكتشاف تلقائي لدعم الصور لكل نموذج مع تجاوز يدوي من الإعدادات | ✅ |
| الاحتفاظ بآخر صورتين فقط في السياق لتوفير التكلفة | ✅ |

### الجديد في 0.4
| الميزة | الحالة |
|---|---|
| محرر: تمييز صيغة تدريجي لـ 12 لغة، بحث واستبدال مع Regex، تراجع/إعادة واختصارات لوحة المفاتيح | ✅ |
| أداة `ask_user`: بطاقات خيارات تفاعلية، ورقائق رد سريع عند سرد الخيارات | ✅ |
| نقاط استعادة لكل جولة تعديل مع استرجاع بنقرة | ✅ |
| أوامر الشرطة المائلة (`/fix` و`/explain` و`/test` و`/review` و`/plan` و`/commit`…) بأسماء عربية بديلة (`/اصلح` `/اشرح` `/اختبر` `/راجع` `/خطة`) وأوامر مخصصة | ✅ |
| عدّاد الرموز والتكلفة وميزانية شهرية (تنبيه 80%، تأكيد 100%) | ✅ |
| الإيداع (`git_commit`) والدفع (`git_push`) يتطلبان موافقتك | ✅ |

### الجديد في 0.3
محرّك Git كامل عبر JGit (فروع، دمج، سحب ودفع، فروقات)، شاشة التحكم بالمصدر، شاشة حسابات GitHub مع التحقق من الرمز، أدوات Git للوكيل، طرفية Termux حقيقية بجلسات متعددة، وأيقونة "Stoop" وشاشة بداية جديدة.

### الجديد في 0.2
واجهة مُعاد تصميمها بالكامل: نظام تصميم "Falcon" (داكن وفاتح، لون ذهبي مميز)، محادثة وكيل بشريط علوي عائم وحبة الخطة وصفوف أدوات مدمجة مع شارات الفروقات والمدة والحالة، عدّاد الخطوات والسياق، محرّك Markdown مع كتل كود قابلة للنسخ، أيقونات أنواع الملفات، أيقونة صقر جديدة، شاشة ترحيب، ومعاينة HTML. التفاصيل في `CHANGELOG.md`، ولقطات الشاشة في `dist/screens/`.

### البدء السريع
1. ثبّت ملف `SaqrCode-v0.10.0-arm64-release.apk` على جهاز arm64 (أندرويد 8 أو أحدث).
2. افتح **الإعدادات** ← اختر مزوّدًا ← الصق مفتاح API ← **حفظ** ← **استخدم هذا المزوّد**.
3. أنشئ مشروعًا، ثم من تبويب **الطرفية** اضغط **تثبيت Alpine Linux**.
4. من تبويب **الوكيل** اكتب مثلًا: «سوّ لي صفحة هبوط لمقهى باسم سراج، عربية RTL، فيها قائمة أسعار».
5. للدفع إلى GitHub: أضف اسم المستخدم والرمز في الإعدادات، ثم تبويب **Git** ← **إيداع ودفع**.

صلاحيات الرمز المطلوبة: `Contents: Read and write`، وأضف `Administration: Read and write` إن أردت إنشاء المستودع من التطبيق.

---

## English

### New in v0.10
- **In-app APK signature verification:** a from-scratch implementation of APK Signature Scheme v2/v3 verification (no apksigner download) — the Build tab's «تحقق من التوقيع» checks the signature with the embedded key (RSA PSS/PKCS1, ECDSA, DSA), recomputes the chunked content digests, and shows the signer certificate's SHA-256 fingerprint, subject and expiry before you tap Install; any modification after the build is rejected loudly.
- **Keep only the last APK:** an on-device-build setting that cleans previous APKs in `dist/` and the heaviest build leftovers (intermediates, tmp, project `.gradle`) after every successful build, logging the reclaimed size.
- **MCP per-server tool allow/deny:** an eye toggle in the tools browser hides tools from the agent — hidden tools are removed from its spec list and refused even if called anyway.
- **GitLab & Gitea end-to-end:** link any HTTPS host with a token (Auto/GitLab/Gitea dialect pick with hostname auto-detection), publish projects to GitLab/Codeberg/self-hosted Gitea from the Git tab, and open merge requests / pull requests on the origin's forge automatically.

### New in v0.9
- **Signed release builds on-device:** the Build tab has an «إصدار موقّع» switch — point it at a keystore inside the project, type the passwords (memory only, never persisted), and the build runs `assembleRelease` signed by AGP (v1+v2+v3) through a generated init script; output lands in `dist/app-release.apk` (release is built without shrinking to fit the phone).
- **aapt2 mirror presets:** Auto (GitHub-API lookup of `Llzzy/aarch64-android-builds`), the verified **ReVanced/aapt2** stable URL, or a custom direct URL — all ELF-validated before use.
- **Build-cache management:** the Build tab shows each component's footprint (Debian rootfs / SDK / Gradle / downloads) with one-tap reclaim; the next Prepare re-fetches only what is missing.
- **GitHub Device Flow sign-in (RFC 8628):** paste your OAuth App's public Client ID once, get a short code, approve at github.com/login/device in any browser; the app polls and links the account — no long tokens to copy on a phone.
- **Per-provider model pickers:** the reviewer/planner model fields get a suggestions menu from the active provider.
- **MCP tools browser:** tap a server in Settings to list its tools live, inspect the schema summary, edit prefilled JSON arguments and run the tool manually to see the raw result.

### New in v0.8
- **Hardened build path:** every build component (ubuntu-base, cmdline-tools, Gradle) is pinned to the publisher's SHA-256 and verified before use; downloads **resume** with HTTP Range instead of restarting; sizes are capped and mirrors tried in order. The arm64 **aapt2** accepts a direct mirror URL from Settings → On-device build and is always **ELF-validated**.
- **Friendly failure hints + crash monitor:** known failures (memory, network, storage, proot, apt, compile errors…) get an actionable hint line in the build log; a «Check crashes» button parses the device crash buffer and shows the built app's FATAL EXCEPTIONs in the Build tab.
- **Lite Web template:** a zero-dependency WebView app (plain Activity, one Kotlin file + Arabic RTL `index.html`) that builds in minutes even on weak phones — validated end-to-end (605 KB APK).
- **Explicit planner stage:** in team mode a cheap model turns each substantial request into a 2–6 task plan before coding; the plan fills the progress pill and is handed to the coder as a `[Planner]` message. Planner model selectable; small talk and slash commands skip it.
- **Automatic checks for the reviewer:** after each applied batch, lint (and tests, when a runner exists) run and their output joins the reviewer's context; a diff-caused check failure is a real issue. Settings toggle, on by default.
- **Separate review/planner cost:** the reviewer's and planner's token spend show separately in the step meter and the usage sheet.
- **MCP support (Streamable HTTP):** add servers in Settings → MCP servers (name + HTTPS URL + optional encrypted auth header); their tools appear to the agent as `mcp_<server>_<tool>`, discovered once per turn, SSE responses parsed, unreachable servers reported as a notice.

### New in v0.7
- **Agent team (Planner → Coder → Reviewer):** after every applied file batch, a second model reviews the diff and the coder fixes its notes in the same turn (max 2 review rounds). The reviewer model is selectable in Settings (blank = the coder's model); a verdict card shows the result in chat.
- **Auto context compaction:** when the estimated context passes the threshold, older turns are summarised into one brief the loop continues from (Settings toggle; cut points are always safe user-message boundaries).
- **Loop detector:** identical repeated tool calls get one nudge, then the turn stops instead of burning tokens in circles.
- **New agent tools:** `web_search` + `web_fetch` (public internet only — private/link-local/cloud-metadata addresses are blocked), `lint_project` (node --check, py_compile, JSON and HTML checks), `run_tests` (npm test / pytest / unittest detection and run).
- **On-device APK build (experimental):** a **Build** tab prepares a Debian rootfs (ubuntu-base) + OpenJDK 17 + Android cmdline-tools + android-35 + an arm64 aapt2 inside proot, streams the Gradle log live, and installs the produced debug APK with one tap. Pairs with the new Compose template for a full phone-only workflow.
- **New templates:** Kotlin + Jetpack Compose app (Arabic/RTL-ready) and an Arabic IME keyboard (InputMethodService + Arabic layout).

### New in v0.6
- **Persistent chat history:** each project's sessions are saved on the phone (JSON per session, images as files); the last session reopens on start and the agent really resumes from the rebuilt history. **Sessions** list with new / rename / delete / Arabic-aware search / auto-titles (200 per project, warning above 50 MB). `ask_user` answered after a restart continues the real turn.
- **Voice:** mic button with live Arabic (ar-SA) / English (en-US) dictation (long-press to switch), optional send-after-dictation, and **Read aloud** for replies (TextToSpeech, code skipped).
- **Background agent:** turns run under a foreground service and keep going when you leave the app, swipe it away or lock the screen (wake + Wi-Fi locks only while running). Ongoing notification with step/tool, Stop and Open; “Saqr needs your answer” with inline buttons for small single-choice questions; finish/error summaries. Interrupted turns resume after the process is killed; a “Resume task?” notification after reboot. One-time battery-optimization guide (Infinix/XOS, Xiaomi…) and a “Keep running in background” toggle.

### New in v0.5
- **Preview element picker:** tap an element in the web preview, describe the change, and the agent edits the matched source line. Secure local-only bridge (project preview pages only, per-page random token regenerated on navigation).
- **Image → code & image attachments:** attach images from the camera or photo picker; multimodal requests for OpenAI-compatible providers and Anthropic.
- **Vision auto-detect** per model with a per-model override (Auto / On / Off) in Settings, plus a warning and model suggestion when the current model can't see images.
- **Last-2-images pruning:** only the two most recent images stay in context, to keep token cost down.

### New in v0.4
- **Editor:** incremental syntax highlighting for 12 languages, find & replace with regex, undo/redo with keyboard shortcuts (Ctrl+Z/Y/F/H, F3).
- **`ask_user` tool:** interactive single/multiple-choice cards; quick-reply chips when a reply lists options.
- **Checkpoints:** one per file-changing agent turn; restore any checkpoint with one tap.
- **Slash commands:** `/fix`, `/explain`, `/test`, `/review`, `/plan`, `/commit`, `/clear`, `/model`, with Arabic aliases (`/اصلح`, `/اشرح`, `/اختبر`, `/راجع`, `/خطة`) and user-defined custom commands.
- **Token / cost meter** per turn and session, today/month totals, and a **monthly budget** (warn at 80%, confirm at 100%).
- **`git_commit` now requires approval**, like `git_push`.

### New in v0.3
Full JGit engine (branches, merge, pull/push, diffs), Source control screen, GitHub Accounts screen with token verification, agent git tools, real Termux terminal with multiple sessions, "Stoop" icon and splash screen.

### What v0.1 includes
- **Arabic + English UI** with full RTL, in-app language switch (per-app locales), IBM Plex Sans Arabic, dark Material 3 theme, falcon adaptive icon.
- **BYOK providers (official APIs only):** Anthropic Messages API, OpenAI, Google Gemini (official OpenAI-compatible endpoint), OpenRouter, DeepSeek, and any custom OpenAI-compatible base URL. Model picker with live model list.
- **Secure key storage:** EncryptedSharedPreferences backed by an Android Keystore AES-256-GCM master key; `allowBackup=false` and backup exclusion rules.
- **Projects:** create/open/delete in app storage, templates (Web, Python, Node, Empty), each with an `AGENTS.md` rules file the agent reads.
- **Editor:** file tree (create/delete via long-press), tabs, dirty markers, save, regex syntax highlighting (Kotlin, Java, JS/TS, Python, shell, HTML/CSS, Go, Rust, C, Dart), line numbers, always-LTR code.
- **Terminal:** root-less Linux using Termux's `proot` (bundled as `libproot.so` so it is executable from `nativeLibraryDir` on Android 10+) + Alpine minirootfs 3.24.4 downloaded on first run and verified with SHA-256. Project mounted at `/workspace`. Falls back to Android `/system/bin/sh` if Linux is not installed.
- **Agent:** streaming chat; tool-calling loop (up to 40 steps) with `list_files`, `read_file`, `write_file`, `edit_file`, `delete_file`, `run_command`, `search`; per-turn context injection (project map, files touched this session, `AGENTS.md`); replies in the user's language with Gulf/Saudi dialect understanding.
- **Plan approval & diffs:** any turn that changes 2+ files becomes one approval card with per-file diffs (or every change, if "approve all" is on). Single-file changes are applied and shown as a diff.
- **Restore points:** before applying any batch the previous content of every touched file is snapshotted; restoring a point reverts it and all newer points.
- **Safety:** command blocklist (recursive deletes outside the project, `curl|sh`, `eval`, `$IFS` tricks, `sudo/su`, `mkfs`, raw `dd`, fork bombs, cloud-metadata SSRF, reverse shells…). Paths are confined to the project root.
- **GitHub:** real git via JGit 5.13 — init, add (incl. deletions), commit, push over HTTPS with a fine-grained token; optional creation of a private repo via the REST API.

### Screenshots
`dist/screens/*.png` are rendered on the JVM (Robolectric native graphics, Arabic, dark + light):
`SAQR_SCREENS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'`.

### Build
Requirements: JDK 17+ (tested with JDK 21), Android SDK platform 35 + build-tools 35.0.0, Gradle wrapper (8.11.1), AGP 8.7.3, Kotlin 2.0.21.

```bash
# On a normal x86_64 machine remove the aapt2 override line from gradle.properties first.
./gradlew testDebugUnitTest      # JVM unit tests (V0x feature suites; + opt-in screenshot renders)
./gradlew lintDebug              # 0 errors
./gradlew assembleRelease        # signed with keystore.properties if present, otherwise debug key
```

Release signing: create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Optional integration test (downloads Alpine and checks our tar extractor against GNU tar):
`SAQR_INTEGRATION=1 SAQR_REF_ROOTFS=/path/to/gnu-tar-extracted ./gradlew testDebugUnitTest`.

### Architecture
```
ui/        Compose screens: Onboarding, Home, Settings, IDE (Agent · Files · Terminal · Git · Preview)
ui/theme   "Falcon" design system (colors, type, spacing, radius, motion; dark + light)
ui/icons   Lucide-style icon set, file-type glyphs, provider monograms
ui/chat    Agent chat: tool rows, approval/notice/review cards, composer, Markdown
agent/     Providers, LlmClient (OpenAI-compatible + Anthropic SSE streaming), Tools,
           AgentSession (loop, approvals, team review, compaction, loop guard), SafetyPolicy, Diff
ondevice/  OnDeviceBuild (proot Debian + JDK 17 + Android SDK, on-device assembleDebug)
terminal/  LinuxEnv (Alpine install + proot command line), ShellSession, TarExtractor
git/       GitService (JGit + GitHub REST)
data/      SecureStore (Keystore), SettingsStore, ProjectRepository + templates
editor/    SyntaxHighlighter
```

### Third-party
See [NOTICE.md](NOTICE.md). proot (GPL-2.0) and talloc (LGPL-3.0) binaries come from the Termux packages repository; source: <https://github.com/termux/proot>, <https://www.samba.org/ftp/talloc/>.
