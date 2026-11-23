# Development plan · خطة التطوير

## الوضع بعد 0.8.0 · Where we are after 0.8.0

شُحنت في 0.8.0: تحصين مسار البناء (تنزيلات مثبّتة بـ SHA-256 قابلة للاستئناف، مرآة aapt2 مع تحقق ELF، إرشادات فشل ودّية، مراقب أعطال، قالب ويب خفيف بلا اعتماديات)، ومرحلة «مخطط» صريحة في وضع الفريق، وفحوصات تلقائية تُمرَّر للمراجع، وتكلفة المراجع/المخطط منفصلة في العدّاد، ودعم خوادم **MCP** عبر Streamable HTTP كأدوات `mcp_*`.

Shipped in 0.8.0: hardened build path (SHA-256-pinned resumable downloads, aapt2 mirror with ELF validation, friendly failure hints, crash monitor, zero-dependency Lite Web template), the explicit planner stage in team mode, automatic checks fed to the reviewer, separate review/planner cost in the meter, and **MCP** servers over Streamable HTTP exposed as `mcp_*` tools.

---

## الأولوية 1 · Priority 1 — إثبات البناء ميدانياً · Field-prove the on-device build

### 1. اختبار ميداني وأرقام · Field-test and publish numbers
- اختبار على هواتف حقيقية (Snapdragon وMediaTek، 4–8 غيغابايت) لكل خطوة: التنزيل المثبّت، apt داخل proot، sdkmanager، جلب aapt2، وبناء قالب Lite كاملاً ثم قالب Compose.
- Test on real phones (Snapdragon + MediaTek, 4–8 GB) through every step: pinned downloads, apt in proot, sdkmanager, aapt2 fetch, then a full Lite-template and Compose-template build.
- قياس زمن التجهيز، حجم التنزيل، الذاكرة القصوى والبطارية، ونشر الأرقام في README.
- Track setup time, download size, peak memory, battery — publish in the README.
- خيار «بناء الإصدار الموقّع»: توقيع الـ APK بمفتاح المشروع (إدخال كلمة المرور) بدل debug فقط.
- «Signed release build» option: sign the on-device APK with the project keystore (password prompt) instead of debug-only.

### 2. مرونة البناء · Build flexibility
- قائمة مرايا aapt2 كاملة (عدة مستودعات جاهزة) بدلاً من مرآة واحدة + القائمة المدمجة.
- Full aapt2 mirror presets (several curated sources) beyond the single user URL + built-in lookup.
- تنظيف تلقائي للمخزونات القديمة (rootfs/SDK/Gradle) وحساب المساحة في التبويب.
- Cache management in the Build tab (old rootfs/SDK/Gradle, sizes, one-tap reclaim).

## الأولوية 2 · Priority 2 — ذكاء أعمق ودمج أوسع · Deeper agent, wider integrations

### 3. تطوير فريق الوكلاء · Evolve the agent team
- اختيار نموذج المراجع/المخطط لكل مزوّد (قوائم مقترحة بدل كتابة الاسم يدوياً).
- Per-provider reviewer/planner pickers with suggested-model menus instead of free-text ids.
- MCP: نقل stdio عبر الطرفية للمستخدمين المتقدمين، وOAuth لخوادم تتطلب دخولاً.
- MCP: stdio transport bridged through the terminal for advanced users; OAuth for servers requiring sign-in.
- لوحة أدوات MCP في الإعدادات (استعراض أدوات كل خادم واختبارها يدوياً).
- An MCP tools browser in Settings (list and test each server's tools manually).

### 4. GitLab / Gitea + تسجيل دخول أسهل · GitLab / Gitea + easier sign-in
- شاشة حسابات تدعم GitLab وGitea بشكل كامل (المخزن يدعمها فعلاً؛ يتبقى واجهة إنشاء المستودعات والدفع).
- Full GitLab/Gitea account UI (the account store already accepts custom HTTPS hosts; missing: create-repo and PR flows).
- زر **GitHub Device Flow** بدل لصق الرموز (Client ID اختياري موجود بالفعل).
- **GitHub Device Flow** button instead of pasting tokens (the optional Client ID setting already exists).

## الأولوية 3 · Priority 3 — العمل · Business

### 5. النشر والدفع · Distribution & payments
- Google Play Billing + **Moyasar** (mada، Apple Pay، STC Pay) بأسعار بالريال وخطة للطلاب.
- Google Play Billing + **Moyasar** (mada, Apple Pay, STC Pay), SAR pricing, student plan.
- صفحات المتاجر بالعربية والإنجليزية مع لقطات التبويبات الجديدة (البناء، المراجعة، MCP).
- Store listings in Arabic and English with screenshots of the new tabs (Build, Review, MCP).
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
