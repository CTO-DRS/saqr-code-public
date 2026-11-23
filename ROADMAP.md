
- ✅ v0.5 — Preview element picker (tap element → describe change → agent edits the matched source line; secure local-only bridge with token).
- ✅ v0.5 — Image → code and image attachments (multimodal OpenAI-compatible/Anthropic, vision auto-detect + per-model override, camera/photo picker, last-2-images pruning).
- ✅ v0.6 — Persistent chat history + Sessions list (rename/delete/search/auto-title), real resume after restart.
- ✅ v0.6 — Voice input (Arabic ar-SA / English en-US, live partial results) and Read aloud.
- ✅ v0.6 — Background agent: foreground service, step/attention/finish notifications, resume after process kill, resume offer after reboot, battery-optimization guide.
- ✅ v0.7 — Agent team (Planner → Coder → Reviewer on a second model), verdict card in chat, max 2 review rounds per turn.
- ✅ v0.7 — Auto context compaction (safe cut boundaries, Settings toggle) and the loop detector (nudge once, then stop).
- ✅ v0.7 — Web tools (`web_search`, `web_fetch`, public-only with private-net guard) and quality tools (`lint_project`, `run_tests`).
- ✅ v0.7 — On-device APK build v1 (experimental Build tab: Debian + JDK 17 + SDK in proot, arm64 aapt2, streamed log, one-tap install).
- ✅ v0.7 — Templates: Kotlin + Jetpack Compose app (Arabic/RTL) and Arabic IME keyboard.
- ✅ v0.8 — Hardened build path: SHA-256-pinned resumable downloads, aapt2 mirror + ELF validation, friendly failure hints, crash monitor, Lite Web template (zero dependencies).
- ✅ v0.8 — Planner stage in team mode, automatic checks fed to the reviewer, separate review/planner cost, MCP servers (Streamable HTTP) as `mcp_*` tools.

## المتبقي · Remaining

Details and step-by-step acceptance criteria: [docs/PLAN.md](docs/PLAN.md).

### الأولوية 1 · Priority 1
1. **إثبات البناء على الجهاز · Field-test the on-device builder**: real-device matrix (Snapdragon + MediaTek) across every step — code side shipped in v0.8 (lite template, pinned resumable downloads, aapt2 mirror + ELF check, crash monitor); field numbers pending.
2. **بناء الإصدار الموقّع على الجهاز · On-device signed release build**: use the project keystore (password prompt) instead of debug-only output.

### الأولوية 2 · Priority 2
3. **تطوير فريق الوكلاء · Evolve the agent team**: v0.8 shipped the explicit planner stage, reviewer-consumed lint/test output, separate review/planner cost, and MCP over Streamable HTTP. Remaining: per-provider model pickers and richer MCP transports (stdio via terminal, OAuth).
4. **GitLab / Gitea UI**: account linking and repository flows in the app UI (the account store already supports other HTTPS hosts).
5. **زر GitHub Device Flow · Device-flow sign-in button** instead of pasting tokens.

### الأولوية 3 · Priority 3 (business)
6. **الدفع · Payments**: Google Play Billing; **Moyasar** (mada, Apple Pay, STC Pay); SAR pricing and a student plan.
7. Store listings (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
