# Development plan · خطة التطوير

## الوضع بعد 0.7.0 · Where we are after 0.7.0

شُحنت في 0.7.0: فريق الوكلاء (مخطط ← مبرمج ← مراجع بنموذج مختلف)، ضغط السياق التلقائي، كاشف الحلقات، أدوات الويب (`web_search` / `web_fetch`) وفحوصات الجودة (`lint_project` / `run_tests`)، تبويب «بناء» على الجهاز (نسخة أولية تجريبية)، وقالبا Compose وIME.

Shipped in 0.7.0: agent team review (second model), auto context compaction, loop detector, web tools, quality checks, the experimental on-device Build tab, and the Compose + IME templates.

---

## الأولوية 1 · Priority 1 — إثبات البناء على الجهاز · Prove on-device build

### 1. تثبيت بناء الجهاز واختباره ميدانياً · Field-test the on-device builder
- اختبار على هواتف حقيقية (Snapdragon وMediaTek، 4–8 غيغابايت) لكل خطوة: تنزيل ubuntu-base، apt داخل proot، sdkmanager، تحميل aapt2، وبناء قالب Compose كاملاً.
- Test on real phones (Snapdragon + MediaTek, 4–8 GB) through every step: ubuntu-base download, apt in proot, sdkmanager, aapt2 fetch, and a full Compose-template build.
- سجّل الأخطاء الشائعة في الشاشة (نفاد الذاكرة، فشل الشبكة، تعارض proot) مع حلول مقترحة لكل خطأ.
- Surface the common failures (OOM, network, proot edge cases) as friendly hints in the build log.
- قياس: زمن التجهيز، حجم التنزيل، الذاكرة القصوى، وبطارية الهاتف — انشر الأرقام في README.
- Track: setup time, download size, peak memory, battery drain — publish numbers in the README.

### 2. تحصين مسار البناء · Harden the build path
- خيار «البناء الخفيف»: قالب لا يحتاج Compose (WebView بسيط) ليبني في دقائق على الهواتف الضعيفة.
- «Lite build» option: a WebView-only template that builds in minutes on low-end devices.
- تخزين مؤقت للتوزيعات: استئناف التنزيلات المجزّأة، والتحقق بـ SHA-256 لكل مكون (ubuntu-base وcmdline-tools وGradle).
- Resumable downloads + SHA-256 pinning for every component (ubuntu-base, cmdline-tools, Gradle, aapt2).
- بديل aapt2: قائمة مرايا قابلة للاختيار من الإعدادات بدل اعتماد مستودع واحد.
- aapt2 mirror list in Settings instead of a single source.
- بث `logcat` بعد التثبيت لإظهار أعطال التطبيق المبني داخل التبويب نفسه.
- Stream `logcat` of the built app after install, inside the same tab.

## الأولوية 2 · Priority 2 — ذكاء أعمق ودمج أوسع · Deeper agent, wider integrations

### 3. تطوير فريق الوكلاء · Evolve the agent team
- مرحلة «مخطط» صريحة: نموذج اقتصادي يحوّل الطلب إلى خطة مهام قبل كتابة الكود (مفصولة عن `update_plan`).
- Explicit planner stage: a cheap model turns the request into a task plan before coding (distinct from `update_plan`).
- اختبار تلقائي بعد المراجعة: تشغيل `run_tests`/`lint_project` وجمع نتائجها في حكم المراجع.
- Auto-test pass: feed `run_tests`/`lint_project` output into the reviewer's context.
- انتخاب نموذج المراجع لكل مزوّد، وعرض تكلفة المراجعة كحدها في عدّاد التكلفة.
- Per-provider reviewer model + show review cost separately in the meter.
- دعم **MCP** عبر HTTP/SSE: إعدادات خوادم المستخدم وأدواتها كأدوات وكيل باسم مسبوق بـ `mcp_`.
- **MCP** over HTTP/SSE: user-configured servers exposed as `mcp_*` agent tools.

### 4. GitLab / Gitea + تسجيل دخول أسهل · GitLab / Gitea + easier sign-in
- شاشة حسابات تدعم GitLab وGitea (حساب + رمز + مضيف HTTPS مخصص) مع نفس تدفقات الدفع والسحب.
- Accounts screen for GitLab and Gitea (host + token) reusing the same push/pull flows.
- زر **GitHub Device Flow** بدل لصق الرموز (Client ID اختياري من المستخدم كما هو موجود الآن).
- **GitHub Device Flow** button instead of pasting tokens (the optional Client ID setting already exists).

## الأولوية 3 · Priority 3 — العمل · Business

### 5. النشر والدفع · Distribution & payments
- Google Play Billing + **Moyasar** (mada، Apple Pay، STC Pay) بأسعار بالريال وخطة للطلاب.
- Google Play Billing + **Moyasar** (mada, Apple Pay, STC Pay), SAR pricing, student plan.
- صفحات المتاجر (Play، AppGallery، Galaxy Store) بالعربية والإنجليزية، ولقطات من الشاشات الجديدة (البناء، المراجعة).
- Store listings (Play, AppGallery, Galaxy Store) in Arabic and English with screenshots of the new tabs.
- مستندات ودروس فيديو قصيرة: «ابنِ أول تطبيق من هاتفك في 10 دقائق».
- Docs + short videos: «Build your first app from your phone in 10 minutes».

---

## معايير الجودة لكل إصدار · Quality bar for every release
- كل ميزة جديدة باختبارات وحدة (ملف `V0xFeaturesTest.kt` مكانه القائمة).
- Every feature ships with unit tests (keep the `V0xFeaturesTest.kt` pattern).
- السلاسل نصية ثنائية اللغة قبل الدمج (values + values-ar).
- Bilingual strings (values + values-ar) land in the same commit.
- تحديث README وCHANGELOG وROADMAP في نفس الـ commit الأخير.
- README / CHANGELOG / ROADMAP updated with the release commit.
