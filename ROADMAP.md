# خارطة الطريق · Roadmap — after v0.4

## تم · Done
- ✅ v0.2 — "Falcon" UI redesign, `update_plan`, Markdown, preview pane.
- ✅ v0.3 — Real terminal (Termux emulator, multiple sessions); Git: pull/clone/branches/merge/diff view, GitHub accounts; agent git tools.
- ✅ v0.4 — Editor: incremental highlighting (12 languages), find/replace with regex, undo/redo + shortcuts.
- ✅ v0.4 — `ask_user` interactive choice cards + quick-reply chips.
- ✅ v0.4 — Checkpoints with restore.
- ✅ v0.4 — Slash commands with Arabic aliases + custom commands.
- ✅ v0.4 — Token/cost meter + monthly budget (cost budgets); `git_commit` requires approval.

## المتبقي · Remaining

### الأولوية 1 · Priority 1
1. **منتقي العناصر في المعاينة · Preview element picker**: tap an element in the web preview to reference it in the chat.
2. **لقطة شاشة إلى كود · Screenshot-to-code**: attach a screenshot/mockup and have the agent build it.
3. **إدخال صوتي · Voice input** in the composer (Arabic + English).
4. **قوالب · Templates**: Kotlin + Jetpack Compose app (Arabic/RTL-ready) and Android **IME keyboard** template (InputMethodService + Arabic layout).
5. **بناء APK على الجهاز · On-device APK build**: Debian rootfs option, JDK 17 + Android SDK inside proot, "Lite build" path, install via `PackageInstaller`, stream `logcat`.

### الأولوية 2 · Priority 2
6. **وكيل متعدد العملاء · Multi-agent**: Planner → Coder → Reviewer/Tester, reviewer on a different model, loop detector, context compaction.
7. **GitLab / Gitea UI**: account linking and repository flows in the app UI (the account store already supports other HTTPS hosts).
8. **زر GitHub Device Flow · Device-flow sign-in button** instead of pasting tokens.

### الأولوية 3 · Priority 3 (business)
9. **الدفع · Payments**: Google Play Billing; **Moyasar** (mada, Apple Pay, STC Pay); SAR pricing and a student plan.
10. Store listings (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
