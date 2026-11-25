# خطة التنفيذ · Execution plan

المصدر الوحيد للاتجاه: [ROADMAP.md](../ROADMAP.md) — هذه الوثيقة تحمل تفاصيل تنفيذ الإصدار الجاري فقط، وتُفتح من جديد عند كل شحن.

The single source of direction is [ROADMAP.md](../ROADMAP.md); this file holds the current release's execution details only.

---

## الإصدار الجاري · Current release: 0.14 «أجنحة مفتوحة · Open Wings» (فبراير 2027)

الترتيب حسب الأولوية: P0 أولًا ثم P1 ثم P2، وكل بند يُشحن إصدارًا فرعيًا كاملًا (0.14.0، 0.14.1 …) ببنود قبوله.

Order follows priority: P0 first, then P1, then P2; each item ships as its own complete minor release (0.14.0, 0.14.1 …).

### 1. حزم المهارات · Skill packs (P0)
- حزمة `.saqrpack` تنصب قالبًا وأوامر مخصصة وقواعد AGENTS.md وأدوات مفضلة بنقرة.
- A `.saqrpack` bundle installs a template, custom commands, AGENTS.md rules and preferred tools in one tap.
- [x] تثبيت حزمة «Flask» يضف قالبًا وأوامرًا جاهزة في ≤ 5 ثوانٍ. — شُحن في 0.14.0
- [x] Installing a «Flask» pack adds a template and ready commands in ≤ 5 seconds. — shipped in 0.14.0
- [x] القالب يظهر في الشاشة الرئيسية ويُنشئ مشروعًا كامل الملفات، والقواعد تُحقن في AGENTS.md ويقرأها الوكيل تلقائيًا. — شُحن في 0.14.0
- [x] The template shows on the home screen and creates a full project; rules land in AGENTS.md and the agent picks them up automatically. — shipped in 0.14.0
- [x] الأوامر تُدمج دون كسر المدمجة أو تكرار الاسم (توحيد عربي)، والاستيراد بالصق أو بعينة جاهزة، والإزالة تحذف القالب لا الأوامر المثبتة. — شُحن في 0.14.0
- [x] Commands merge without breaking builtins or duplicating names (Arabic-aware); import by paste or a ready sample; removal deletes the template only, not the installed commands. — shipped in 0.14.0

### 2. MCP عبر OAuth · MCP over OAuth (P1)
- دخول PKCE لخوادم MCP المحمية من الإعدادات.
- PKCE sign-in for protected MCP servers from Settings.
- [x] ربط خادم يتطلب OAuth واكتشاف أدواته كاملة. — شُحن في 0.14.1
- [x] Connect a server that requires OAuth and discover its full toolset. — shipped in 0.14.1

### 3. بناء سحابي اختياري (BYOC) · Bring-your-own-compute cloud build (P1)
- للهواتف الضعيفة: تشغيل البناء على GitHub Actions في مستودع المستخدم نفسه، وإعادة APK للمعاينة والتثبيت داخل التطبيق.
- For weak phones: run the build on GitHub Actions inside the user's own repository, and return the APK for in-app preview and install.
- [ ] بناء قالب Compose سحابيًا في ≤ 15 دقيقة مع سجل مباشر.
- [ ] Build a Compose template in the cloud in ≤ 15 minutes with a live log.

### 4. معرض المجتمع · Community gallery (P1)
- معرض قوالب ومشاريع عربية يُسحب من مستودع GitHub عام، والمساهمة عبر PR.
- A gallery of Arabic templates and projects pulled from a public GitHub repository, with PR-based contribution.
- [ ] تصفح وتثبيت قالب من المعرض بنقرة.
- [ ] Browse and install a template from the gallery in one tap.

### 5. مزامنة عبر المستودع · Repository-based sync (P2)
- تصدير/استيراد الإعدادات والثيمات والأوامر المخصصة عبر ملف أو مستودع خاص مشفر — بلا خادم مركزي.
- Export/import settings, themes and custom commands via a file or an encrypted private repository — no central server.
- [ ] إعداد جهاز ثانٍ في ≤ دقيقتين.
- [ ] Set up a second device in ≤ 2 minutes.

### 6. صقر الحارس · Saqr Guard (P2)
- مهام مجدولة محلية: اختبارات ليلية، تقرير صباحي بإشعار، مراقبة فروقات مستودع المستخدم.
- Local scheduled tasks: nightly tests, a morning report notification, watching the user's repository for changes.
- [ ] مهمة مجدولة تعمل في وقتها ويصل تقريرها.
- [ ] A scheduled task runs on time and its report arrives.

### 7. ويجت وبلاطة · Widget and Quick Tile (P2)
- ويجت حالة المهمة الجارية على الشاشة الرئيسية + Quick Tile لتشغيل/إيقاف الوكيل.
- A home-screen widget for the running task + a Quick Tile to start/stop the agent.
- [ ] تحديث لحظي وإيقاف بنقرة.
- [ ] Live updates and one-tap stop.

### 8. استوديو بصري v1 · Visual studio v1 (P2)
- توسيع انتقاء العناصر إلى تحرير مباشر (لون، مسافة، نص) من المعاينة يُترجم بدقة إلى الكود.
- Extend element picking into direct editing (color, spacing, text) from the preview, translated faithfully to code.
- [ ] تعديل لون/مسافة من المعاينة يعدّل السطر الصحيح.
- [ ] A color/spacing edit from the preview lands on the right line.

---

## معايير الجودة لكل إصدار · Quality bar for every release
- كل ميزة جديدة باختبارات وحدة (ملف `V0xFeaturesTest.kt` مكانه القائمة).
- Every feature ships with unit tests (keep the `V0xFeaturesTest.kt` pattern).
- السلاسل نصية ثنائية اللغة قبل الدمج (values + values-ar).
- Bilingual strings (values + values-ar) land in the same commit.
- تحديث README وCHANGELOG وROADMAP في نفس الـ commit الأخير.
- README / CHANGELOG / ROADMAP updated with the release commit.

## بعد 0.14 · After 0.14
- تُفتح هذه الوثيقة مجددًا لـ 1.0 «الانقضض»: إثبات ميداني نهائي، إطلاق Google Play، أداء واستقرار، تدقيق أمني، توثيق وإطلاق، ترحيب جديد.
- This file reopens for 1.0 «The Strike»: final field proof, Google Play launch, performance & stability, security audit, docs & launch, a fresh onboarding.
