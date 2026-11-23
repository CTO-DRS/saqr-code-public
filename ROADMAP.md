
- ✅ v0.5 — Preview element picker (tap element → describe change → agent edits the matched source line; secure local-only bridge with token).
- ✅ v0.5 — Image → code and image attachments (multimodal OpenAI-compatible/Anthropic, vision auto-detect + per-model override, camera/photo picker, last-2-images pruning).
- ✅ v0.6 — Persistent chat history + Sessions list (rename/delete/search/auto-title), real resume after restart.
- ✅ v0.6 — Voice input (Arabic ar-SA / English en-US, live partial results) and Read aloud.
- ✅ v0.6 — Background agent: foreground service, step/attention/finish notifications, resume after process kill, resume offer after reboot, battery-optimization guide.

## المتبقي · Remaining

### الأولوية 1 · Priority 1
1. **قوالب · Templates**: Kotlin + Jetpack Compose app (Arabic/RTL-ready) and Android **IME keyboard** template (InputMethodService + Arabic layout).
2. **بناء APK على الجهاز · On-device APK build**: Debian rootfs option, JDK 17 + Android SDK inside proot, "Lite build" path, install via `PackageInstaller`, stream `logcat`.

### الأولوية 2 · Priority 2
3. **وكيل متعدد العملاء · Multi-agent**: Planner → Coder → Reviewer/Tester, reviewer on a different model, loop detector, context compaction.
4. **GitLab / Gitea UI**: account linking and repository flows in the app UI (the account store already supports other HTTPS hosts).
5. **زر GitHub Device Flow · Device-flow sign-in button** instead of pasting tokens.

### الأولوية 3 · Priority 3 (business)
6. **الدفع · Payments**: Google Play Billing; **Moyasar** (mada, Apple Pay, STC Pay); SAR pricing and a student plan.
7. Store listings (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
