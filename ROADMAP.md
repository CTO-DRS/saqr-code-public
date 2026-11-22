# خارطة الطريق · Roadmap — v0.2

## الأولوية 1 · Priority 1
1. **بناء APK على الجهاز · On-device Gradle APK build**
   - Debian (glibc) rootfs option next to Alpine; JDK 17 + Android SDK cmdline-tools + build-tools (arm64 aapt2) installed on demand inside proot.
   - Gradle daemon capped (`-Xmx1g`), incremental builds; "Lite build" path (aapt2 + d8 + kotlinc) for small projects.
   - Install via `PackageInstaller` session, launch, and stream `logcat` into the app.
2. **قوالب · Templates**
   - Kotlin + Jetpack Compose app (Arabic/RTL-ready, i18n strings).
   - Android **IME keyboard** template (InputMethodService + Arabic layout).
   - React/Vite, Node/Express, Python/FastAPI.
   - **Arabic templates:** RTL landing page, Arabic e-commerce storefront, Hijri date utils, prayer-times widget, Saudi VAT invoice.
3. **طرفية حقيقية · Real terminal**: PTY via JNI + Termux `terminal-view` (colours, Ctrl-C, vim/htop), background foreground-service, multiple sessions.

## الأولوية 2 · Priority 2
4. **وكيل متعدد العملاء · Multi-agent**: Planner → Coder → Reviewer/Tester; reviewer on a different model; cost/step budgets and loop detector; context compaction; text tool-call parser for weaker models; `ask_user` and `propose_plan` tools.
5. **المحرر · Editor**: Sora-Editor + TextMate grammars, find/replace, LSP (tsserver, pyright) inside proot.
6. **Git**: pull/clone/branches/diff view, GitHub OAuth Device Flow (instead of pasted tokens), GitLab/Gitea.
7. **معاينة الويب · Web preview** with console.

## الأولوية 3 · Priority 3 (business)
8. **الدفع · Payments**: Google Play Billing for the Play build; **Moyasar** (mada, Apple Pay, STC Pay) for the web/enterprise plan; SAR pricing and a student plan.
9. Optional hosted credits through a Saqr proxy (KSA region), audit log, secret scanner before sending context to models.
10. Store listing (Play, Huawei AppGallery, Samsung Galaxy Store), Arabic docs and video tutorials.
