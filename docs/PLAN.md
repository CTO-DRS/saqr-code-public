# خطة التنفيذ · Execution plan

المصدر الوحيد للاتجاه: [ROADMAP.md](../ROADMAP.md) — هذه الوثيقة تحمل تفاصيل تنفيذ الإصدار الجاري فقط، وتُفتح من جديد عند كل شحن.

The single source of direction is [ROADMAP.md](../ROADMAP.md); this file holds the current release's execution details only.

---

## الإصدار الجاري · Current release: 0.12 «ريش جديدة · New Plumage» (ديسمبر 2026)

### 1. توكنات التصميم 2.0 · Design tokens 2.0
- إكمال منظومة `SaqrColors` بتوكنات حركة ونصف قطر وظلال وطباعة، وثيم «ليل» OLED أسود نقي.
- Complete the `SaqrColors` system with motion, radius, elevation and type tokens, plus a pure-black «Layl» OLED theme.
- [x] تبديل الثيمات فوري بلا إعادة تشغيل (داكن/فاتح/OLED/مخصص/نظام)، ولا لون جديد خارج التوكنات في مسار الثيمات.
- [x] Instant theme switching without restart; no hardcoded colors outside the tokens.

### 2. استوديو الثيمات v1 · Theme Studio v1
- توليد ثيم كامل من صورة أو كلمة مزاج عبر النموذج النشط، معاينة حية، حفظ وتصدير/استيراد JSON.
- Generate a full theme from an image or a mood phrase via the active model, with live preview and JSON export/import.
- [x] ثيم من كلمة المزاج خلال ثوانٍ، و6 حزم جاهزة (نجد، تهامة، البحر، الوادي، عُمان، الأندلس) — ومن صورة تبقى للإصدار التالي.
- [x] Mood → theme in seconds; six built-in packs — image → theme moves to the next release.

### 3. لوحة الأوامر العربية · Arabic command palette
- بحث موحد من أي تبويب: ملفات، أوامر شرطة، إعدادات، جلسات، أدوات MCP.
- Unified search from any tab: files, slash commands, settings, sessions, MCP tools.
- [x] أول نتيجة سريعة على مشاريع كبيرة، وتفهم المرادفات العربية وصيغ الهمزات.
- [x] Fast first result on large projects; understands Arabic synonyms and alef forms.

### 4. الشاشة الرئيسية الحية · Live home dashboard
- بطاقة لكل مشروع: جلسة نشطة، آخر بناء وحالته، فرع git وفروقاته، حجم مخزون البناء.
- Per-project card: active session, last build + status, git branch & diffs, build-cache size.
- [x] تحديث لحظي (آخر بناء + الفرع)، وحذف المشروع يزيل سجله — الجلسة النشطة عبر لوحة الأوامر وقائمة الجلسات.
- [x] Live updates (last build + branch); deleting clears the record — active session via the palette and sessions sheet.

### 5. لمسات المحرر والحركة · Editor & motion polish
- حجم خط المحرر (12–20sp)، التفاف أسطر، شريط أدوات سريع، توكنات الحركة وانتقالات التبويبات، ولمس اهتزازي دلالي.
- Editor font size (12–20sp), line wrap, quick toolbar, motion tokens, shared-axis tab transitions, haptic feedback.
- [x] تطبيق فوري وحفظ في الإعدادات — من الإعدادات وشريط أدوات المحرر معًا.
- [x] Applied instantly and persisted — from Settings and the editor toolbar both.

### 6. الأرقام الميدانية الأولى · First field numbers
- اختبار خطوات التجهيز والبناء (تنزيل مثبت، apt، sdkmanager، aapt2، قالب Lite، إصدار موقّع) على أجهزة Snapdragon وMediaTek.
- Run every build step on Snapdragon + MediaTek devices and publish numbers.
- [x] عدّة الميدان داخل التطبيق (0.12.1): توقيت تلقائي لكل خطوة مع هوية الجهاز والذاكرة والبطارية وحجم APK، وتصدير جدول Markdown/CSV بضغطة — الجدول الفعلي يُجمع من الأجهزة الحقيقية ويُلصق في README.
- [x] In-app field kit (0.12.1): automatic per-step timings with device identity, exported as a Markdown table / CSV in one tap — actual device numbers get pasted into the README from real phones.
- [ ] جدول أزمنة وأحجام وذاكرة وبطارية لجهازين على الأقل في README.
- [ ] A README table with time, size, memory and battery for at least two devices.

### المتبقي من 0.12 · Remaining from 0.12
- المتبقي عمليًا: الجدول الميداني نفسه (يُجمع من أجهزة Snapdragon وMediaTek حقيقية عبر عدّة الميدان الجاهزة) — وبقية بنود دفعة 0.12.x (الثيم من صورة، Material You، توكنات الاهتزاز) شُحنت في 0.12.1.
- Practically remaining: the field table itself (gathered from real Snapdragon + MediaTek phones via the shipped field kit) — the rest of the 0.12.x batch (image → theme, Material You, haptics tokens) shipped in 0.12.1.

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
