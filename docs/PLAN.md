# خطة التنفيذ · Execution plan

المصدر الوحيد للاتجاه: [ROADMAP.md](../ROADMAP.md) — هذه الوثيقة تحمل تفاصيل تنفيذ الإصدار الجاري فقط، وتُفتح من جديد عند كل شحن.

The single source of direction is [ROADMAP.md](../ROADMAP.md); this file holds the current release's execution details only.

---

## الإصدار الجاري · Current release: 0.12 «ريش جديدة · New Plumage» (ديسمبر 2026)

### 1. توكنات التصميم 2.0 · Design tokens 2.0
- إكمال منظومة `SaqrColors` بتوكنات حركة ونصف قطر وظلال وطباعة، وثيم «ليل» OLED أسود نقي.
- Complete the `SaqrColors` system with motion, radius, elevation and type tokens, plus a pure-black «Layl» OLED theme.
- [ ] تبديل الثيمات الثلاثة فوري بلا إعادة تشغيل، ولا لون مكتوب يدويًا خارج التوكنات.
- [ ] Instant three-theme switching without restart; no hardcoded colors outside the tokens.

### 2. استوديو الثيمات v1 · Theme Studio v1
- توليد ثيم كامل من صورة أو كلمة مزاج عبر النموذج النشط، معاينة حية، حفظ وتصدير/استيراد JSON.
- Generate a full theme from an image or a mood phrase via the active model, with live preview and JSON export/import.
- [ ] ثيم من صورة في ≤ 10 ثوانٍ، و6 ثيمات جاهزة (نجد، تهامة، البحر، الوادي، عُمان، الأندلس).
- [ ] Image → theme in ≤ 10s; six built-in packs (Najd, Tihama, Bahar, Wadi, Oman, Andalus).

### 3. لوحة الأوامر العربية · Arabic command palette
- بحث موحد من أي تبويب: ملفات، أوامر شرطة، إعدادات، جلسات، أدوات MCP.
- Unified search from any tab: files, slash commands, settings, sessions, MCP tools.
- [ ] أول نتيجة في ≤ 100ms على 500 ملف، وتفهم المرادفات العربية.
- [ ] First result ≤ 100ms on a 500-file project; understands Arabic synonyms.

### 4. الشاشة الرئيسية الحية · Live home dashboard
- بطاقة لكل مشروع: جلسة نشطة، آخر بناء وحالته، فرع git وفروقاته، حجم مخزون البناء.
- Per-project card: active session, last build + status, git branch & diffs, build-cache size.
- [ ] تحديث لحظي والانتقال للبناء أو الجلسة بنقرة.
- [ ] Live updates; one-tap jump to build or session.

### 5. لمسات المحرر والحركة · Editor & motion polish
- حجم خط المحرر (12–20sp)، التفاف أسطر، شريط أدوات سريع، توكنات الحركة وانتقالات التبويبات، ولمس اهتزازي دلالي.
- Editor font size (12–20sp), line wrap, quick toolbar, motion tokens, shared-axis tab transitions, haptic feedback.
- [ ] تطبيق فوري وحفظ في الإعدادات، سلاسة 60fps، واحترام «تقليل الحركة».
- [ ] Applied instantly and persisted; 60fps; honors reduced-motion.

### 6. الأرقام الميدانية الأولى · First field numbers
- اختبار خطوات التجهيز والبناء (تنزيل مثبت، apt، sdkmanager، aapt2، قالب Lite، إصدار موقّع) على أجهزة Snapdragon وMediaTek.
- Run every build step on Snapdragon + MediaTek devices and publish numbers.
- [ ] جدول أزمنة وأحجام وذاكرة وبطارية لجهازين على الأقل في README.
- [ ] A README table with time, size, memory and battery for at least two devices.

---

## معايير الجودة لكل إصدار · Quality bar for every release
- كل ميزة جديدة باختبارات وحدة (ملف `V0xFeaturesTest.kt` مكانه القائمة).
- Every feature ships with unit tests (keep the `V0xFeaturesTest.kt` pattern).
- السلاسل نصية ثنائية اللغة قبل الدمج (values + values-ar).
- Bilingual strings (values + values-ar) land in the same commit.
- تحديث README وCHANGELOG وROADMAP في نفس الـ commit الأخير.
- README / CHANGELOG / ROADMAP updated with the release commit.

## بعد 0.12 · After 0.12
- تُفتح هذه الوثيقة مجددًا لتعكس 0.13 «عقل الصقر»: بنك الذاكرة، فهرس المشروع، موجّه النماذج، مدرّب صقر، صوت بلا يدين، وضع صحراوي.
- This file is reopened for 0.13 «Falcon Mind»: memory bank, project index, model router, Saqr Coach, hands-free voice, desert mode.
