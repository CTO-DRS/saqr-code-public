
- ✅ v0.5 — Preview element picker (tap element → describe change → agent edits the matched source line; secure local-only bridge with token).
- ✅ v0.5 — Image → code and image attachments (multimodal OpenAI-compatible/Anthropic, vision auto-detect + per-model override, camera/photo picker, last-2-images pruning).

## المتبقي · Remaining

### الأولوية 1 · Priority 1
1. **إدخال صوتي · Voice input** in the composer (Arabic + English).
2. **قوالب · Templates**: Kotlin + Jetpack Compose app (Arabic/RTL-ready) and Android **IME keyboard** template (InputMethodService + Arabic layout).
3. **بناء APK على الجهاز · On-device APK build**: Debian rootfs option, JDK 17 + Android SDK inside proot, "Lite build" path, install via `PackageInstaller`, stream `logcat`.
4. **حفظ سجل المحادثة · Persist chat history** across app restarts.

### الأولوية 2 · Priority 2
5. **وكيل متعدد العملاء · Multi-agent**: Planner → Coder → Reviewer/Tester, reviewer on a different model, loop detector, context compaction.
6. **GitLab / Gitea UI**: account linking and repository flows in the app UI (the account store already supports other HTTPS hosts).
7. **زر GitHub Device Flow · Device-flow sign-in button** instead of pasting tokens.

### الأولوية 3 · Priority 3 (business)
8. **الدفع · Payments**: Google Play Billing; **Moyasar** (mada, Apple Pay, STC Pay); SAR pricing and a student plan.
9. Store listings (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
