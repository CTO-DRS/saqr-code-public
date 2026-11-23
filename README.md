# صقر كود · Saqr Code 🦅

**وكيل برمجة ذكي على أندرويد، عربي أولاً.** اكتب طلبك بالعربي (حتى باللهجة الخليجية) أو بالإنجليزي، والوكيل يقرأ مشروعك ويعدّل الملفات ويشغّل الأوامر في طرفية لينكس حقيقية على جوالك، بمفتاحك الخاص (BYOK).

**An Arabic-first AI coding agent for Android.** Ask in Arabic (Gulf dialect included) or English; the agent reads your project, edits files and runs commands in a real root-less Linux terminal on your phone, using your own API key.

> الإصدار / Version: **0.4.0** · Package: `com.ctodrs.saqrcode` · minSdk 26 · targetSdk 35 · arm64-v8a

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
1. ثبّت ملف `SaqrCode-v0.4.0-arm64-release.apk` على جهاز arm64 (أندرويد 8 أو أحدث).
2. افتح **الإعدادات** ← اختر مزوّدًا ← الصق مفتاح API ← **حفظ** ← **استخدم هذا المزوّد**.
3. أنشئ مشروعًا، ثم من تبويب **الطرفية** اضغط **تثبيت Alpine Linux**.
4. من تبويب **الوكيل** اكتب مثلًا: «سوّ لي صفحة هبوط لمقهى باسم سراج، عربية RTL، فيها قائمة أسعار».
5. للدفع إلى GitHub: أضف اسم المستخدم والرمز في الإعدادات، ثم تبويب **Git** ← **إيداع ودفع**.

صلاحيات الرمز المطلوبة: `Contents: Read and write`، وأضف `Administration: Read and write` إن أردت إنشاء المستودع من التطبيق.

---

## English

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
./gradlew testDebugUnitTest      # 23 JVM unit tests (+13 opt-in screenshot renders)
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
ui/chat    Agent chat: tool rows, approval/notice cards, composer, Markdown
agent/     Providers, LlmClient (OpenAI-compatible + Anthropic SSE streaming), Tools,
           AgentSession (loop, approvals, restore points), SafetyPolicy, Diff, Snapshots
terminal/  LinuxEnv (Alpine install + proot command line), ShellSession, TarExtractor
git/       GitService (JGit + GitHub REST)
data/      SecureStore (Keystore), SettingsStore, ProjectRepository + templates
editor/    SyntaxHighlighter
```

### Third-party
See [NOTICE.md](NOTICE.md). proot (GPL-2.0) and talloc (LGPL-3.0) binaries come from the Termux packages repository; source: <https://github.com/termux/proot>, <https://www.samba.org/ftp/talloc/>.
