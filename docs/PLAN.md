# Development plan · خطة التطوير

## الوضع بعد 0.10.0 · Where we are after 0.10.0

شُحنت في 0.10.0: **التحقق من توقيع الـ APK داخل التطبيق** (مخطط v2/v3 كامل مع بصمة شهادة الموقّع قبل زر التثبيت)، وخيار «احتفظ بآخر إصدار فقط» الذي ينظف APKs القديمة ومخلفات البناء بعد كل نجاح، وسماح/منع أدوات MCP لكل خادم (أدوات مخفية لا يراها الوكيل ولا يستدعيها)، ودعم GitLab وGitea من البداية للنهاية (ربط المضيفين بنوع API، وإنشاء المستودعات، وفتح طلبات الدمج/السحب من تبويب Git).

Shipped in 0.10.0: **in-app APK signature verification** (full v2/v3 with the signer's SHA-256 fingerprint before Install), the «keep only the last APK» auto-clean after successful builds, per-server MCP tool allow/deny (hidden tools never reach the agent), and end-to-end GitLab/Gitea support (host linking with an API dialect, create-repository publish, MR/PR from the Git tab).

---

## الأولوية 1 · Priority 1 — إثبات البناء ميدانياً · Field-prove the on-device build

### 1. اختبار ميداني وأرقام · Field-test and publish numbers
- اختبار على هواتف حقيقية (Snapdragon وMediaTek، 4–8 غيغابايت) لكل خطوة: التنزيل المثبّت، apt داخل proot، sdkmanager، جلب aapt2 من المرايا، بناء قالب Lite ثم قالب Compose، ثم **إصدار موقّع** بمفتاح حقيقي.
- Test on real phones (Snapdragon + MediaTek, 4–8 GB) through every step: pinned downloads, apt in proot, sdkmanager, aapt2 presets, then a full Lite-template and Compose-template build, and finally a **signed release** with a real keystore.
- قياس زمن التجهيز، حجم التنزيل، الذاكرة القصوى والبطارية، ونشر الأرقام في README.
- Track setup time, download size, peak memory, battery — publish in the README.

### 2. مرونة البناء · Build flexibility
- تنظيف تلقائي للمخزونات القديمة بعد نجاح البناء (خيار «احتفظ بآخر إصدار فقط»).
- Optional auto-clean of old caches after a successful build («keep only the last APK»).
- تحقق إضافي: فحص توقيع الـ APK المبني داخل التطبيق (apksigner verify عبر مصدر موثوق) قبل عرض زر التثبيت.
- Verify the built APK's signature in-app before showing Install.

## الأولوية 2 · Priority 2 — ذكاء أعمق ودمج أوسع · Deeper agent, wider integrations

### 3. تطوير فريق الوكلاء · Evolve the agent team
- MCP: نقل stdio عبر الطرفية للمستخدمين المتقدمين، وOAuth لخوادم تتطلب دخولاً.
- MCP: stdio transport bridged through the terminal for advanced users; OAuth for servers requiring sign-in.
- ~~واجهة اختيار أدوات MCP~~ — شُحنت في 0.10 (منع/سماح لكل خادم من مستعرض الأدوات).
- ~~Per-server MCP tool allow/deny lists~~ — shipped in 0.10 (eye toggle in the tools browser).

### 4. GitLab / Gitea · قوائم المستودعات والطلبات
- ~~شاشة حسابات تدعم GitLab وGitea~~ — شُحنت في 0.10: ربط المضيفين وإنشاء المستودعات وطلبات الدمج.
- ~~Full GitLab/Gitea account UI~~ — shipped in 0.10: host linking with API dialect, create-repo publish, MR/PR flows.

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
