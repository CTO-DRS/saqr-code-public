# خطة التنفيذ · Execution plan

المصدر الوحيد للاتجاه: [ROADMAP.md](../ROADMAP.md) — هذه الوثيقة تحمل تفاصيل تنفيذ الإصدار الجاري فقط، وتُفتح من جديد عند كل شحن.

The single source of direction is [ROADMAP.md](../ROADMAP.md); this file holds the current release's execution details only.

---

## الإصدار الجاري · Current release: 0.13 «عقل الصقر · Falcon Mind» (يناير 2027)

### 1. بنك الذاكرة · Memory bank
- ذاكرة دائمة لكل مشروع (`.saqr/memory.json`): قرار، تفضيل، حقيقة — يسترجعها الوكيل تلقائيًا في كل دورة عبر موجه النظام، ويحفظها بنفسه بأدوات `memory_save` / `memory_forget`.
- Persistent per-project memory (`.saqr/memory.json`): decisions, preferences, facts — auto-recalled into the system prompt every turn; the agent saves them itself with `memory_save` / `memory_forget`.
- [x] مفاتيح عربية تُوحَّد (الهمزات، التاء المربوطة، التشكيل) فلا تُخزَّن الحقيقة نفسها مرتين — تحديث بالـ upsert وحدّ أقصى 60 مدخلة بإزاحة الأقدم.
- [x] Arabic keys are normalized (alef forms, taa marbouta, diacritics) so the same fact is never stored twice — upsert semantics with a 60-entry cap evicting the oldest.
- [x] استرجاع محدود الميزانية (~2000 حرف) مرتّب: التفضيلات ثم القرارات ثم الحقائق، والأحدث أولًا — لا يسرق سياق العمل.
- [x] Budgeted recall (~2000 chars), ordered preferences → decisions → facts, newest first — it never crowds out working context.
- [x] واجهة «بنك الذاكرة» من قائمة المحادثة: عرض، إضافة، تعديل، حذف، إفراغ — والمستخدم يملك الذاكرة كاملة.
- [x] «Memory bank» sheet from the chat menu: view, add, edit, delete, clear — the user fully owns the memory.
- [ ] لقطة ميدانية: نسبة المحادثات التي يسترجع فيها الوكيل قرارًا سابقًا دون إعادة سؤال المستخدم (قبل/بعد).
- [ ] Field note: share of turns where the agent recalls a prior decision without re-asking (before/after).

### 2. فهرس المشروع · Project index
- فهرس رموز خفيف (ملفات/دوال/أصناف) يتولد عند فتح المشروع ويغذي بحث الوكيل بدل قراءة ملفات كاملة.
- A lightweight symbol index (files/functions/classes) built when the project opens, feeding the agent's search instead of full-file reads.
- [ ] أول نتيجة أقل من 200ms على مشروع 500 ملف.
- [ ] First result under 200 ms on a 500-file project.

### 3. موجّه النماذج · Falcon Router
- توجيه المهمة للنموذج الأرخص المناسب (خطة/كود/مراجعة/تلخيص) بهدف توفير ≥25% من التكلفة، مع رقم معلن.
- Route each stage to the cheapest capable model (plan/code/review/summarise) targeting ≥25% cost savings, published as a number.
- [ ] توفير مقاس على حمل حقيقي مع بقاء جودة المراجعة.
- [ ] Measured savings on a real workload with review quality intact.

### 4. مدرّب صقر · Saqr Coach
- إرشاد أول استخدام ومسار تعلم مختصر داخل التطبيق (أمثلة أوامر بالعربية).
- In-app first-run guidance and a short learning path (Arabic command examples).
- [ ] مستخدم جديد يصل لأول بناء ناجح دون مساعدة خارجية.
- [ ] A new user reaches a first successful build without external help.

### 5. صوت بلا يدين · Hands-free voice
- إملاء مستمر مع إيقاظ «يا صقر» وتشغيل/إيقاف من الإشعار.
- Continuous dictation with a «Ya Saqr» wake phrase and notification toggle.
- [ ] دقة إملاء عربية مقبولة على ضوضاء خفيفة.
- [ ] Acceptable Arabic dictation accuracy in light noise.

### 6. وضع صحراوي · Desert mode
- وضع انقطاع الشبكة: قوائم أوامر محلية، إلغاء الميزات السحابية تلقائيًا، ومتابعة البناء المحلي.
- Offline-first mode: local command lists, cloud features gracefully off, on-device build keeps working.
- [ ] كل ميزة سحابية تتعامل مع الانقطاع برسالة واضحة بلا انهيار.
- [ ] Every cloud feature degrades gracefully with a clear message, no crash.

---

## معايير الجودة لكل إصدار · Quality bar for every release
- كل ميزة جديدة باختبارات وحدة (ملف `V0xFeaturesTest.kt` مكانه القائمة).
- Every feature ships with unit tests (keep the `V0xFeaturesTest.kt` pattern).
- السلاسل نصية ثنائية اللغة قبل الدمج (values + values-ar).
- Bilingual strings (values + values-ar) land in the same commit.
- تحديث README وCHANGELOG وROADMAP في نفس الـ commit الأخير.
- README / CHANGELOG / ROADMAP updated with the release commit.

## بعد 0.13 · After 0.13
- تُفتح هذه الوثيقة مجددًا لتعكس 0.14 «أجنحة مفتوحة»: MCP OAuth، حزم `.saqrpack`، بناء سحابي BYOC، مزامنة مشفرة، صقر الحارس، ويجت/Tile، معرض مجتمع، استوديو بصري.
- This file is reopened for 0.14 «Open Wings»: MCP OAuth, .saqrpack bundles, BYOC cloud build, encrypted sync, Saqr Guard, widget/Tile, community gallery, visual studio.
