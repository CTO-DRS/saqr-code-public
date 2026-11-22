# Changelog · سجل التغييرات

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
