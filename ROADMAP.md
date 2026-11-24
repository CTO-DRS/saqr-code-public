
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
- ✅ v0.9 — On-device **signed release builds** (project keystore, memory-only passwords, AGP v1+v2+v3 signing via generated init script), aapt2 mirror presets (Auto / ReVanced / custom), build-cache management with one-tap reclaim.
- ✅ v0.9 — **GitHub Device Flow** sign-in (RFC 8628), per-provider reviewer/planner model pickers, and the MCP tools browser (list, inspect, run tools manually).
- ✅ v0.10 — **In-app APK signature verification** (v2/v3, signer fingerprint), «keep only the last APK» auto-clean, MCP per-server tool allow/deny, and GitLab/Gitea account + create-repo + MR/PR flows.
- ✅ v0.11 — **Plans & payments**: SAR pricing (Free / Pro 15 / Student 5 / Lifetime 149) with honest gating, Google Play Billing (PBL 8) monthly + lifetime products with restore, Moyasar hosted invoices + merchant tools, offline `SQ1.` license tokens (`scripts/license_tool.py`), and **MCP over stdio** (local servers inside the terminal env).

## المتبقي · Remaining

Details and step-by-step acceptance criteria: [docs/PLAN.md](docs/PLAN.md).

### الأولوية 1 · Priority 1
1. **إثبات البناء على الجهاز · Field-test the on-device builder**: real-device matrix (Snapdragon + MediaTek) across every step — the code side is complete (lite template, pinned resumable downloads, aapt2 presets + ELF check, crash monitor, signed release, cache reclaim); field numbers pending.
2. **أدوات وحدة بناء أفضل · Publish the field numbers**: setup time, download size, peak memory and battery in the README, once the device matrix runs.

### الأولوية 2 · Priority 2
3. **تطوير فريق الوكلاء · Evolve the agent team**: v0.10 shipped per-server MCP tool allow/deny; v0.11 shipped stdio transport. Remaining: OAuth for gated MCP servers.
4. **GitLab / Gitea UI**: ✅ shipped in v0.10 (link, publish, MR/PR). Remaining polish: issue list & CI status views.

### الأولوية 3 · Priority 3 (business)
5. **الدفع · Payments**: ✅ shipped in v0.11 (Play Billing PBL 8, Moyasar invoices + merchant tools, `SQ1.` tokens, SAR pricing, student plan). Remaining: configure Play Console products + the public Moyasar page, then launch.
6. Store listings (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
