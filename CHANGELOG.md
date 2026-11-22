# Changelog · سجل التغييرات

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
