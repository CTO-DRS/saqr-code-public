# صقر كود · Saqr Code 🦅

**وكيل برمجة ذكي على أندرويد، عربي أولاً.** اكتب طلبك بالعربي (حتى باللهجة الخليجية) أو بالإنجليزي، والوكيل يقرأ مشروعك ويعدّل الملفات ويشغّل الأوامر في طرفية لينكس حقيقية على جوالك، بمفتاحك الخاص (BYOK).

**An Arabic-first AI coding agent for Android.** Ask in Arabic (Gulf dialect included) or English; the agent reads your project, edits files and runs commands in a real root-less Linux terminal on your phone, using your own API key.

> الإصدار / Version: **0.19.3** · Package: `com.ctodrs.saqrcode` · minSdk 26 · targetSdk 35 · arm64-v8a
> الخارطة / Roadmap: [ROADMAP.md](ROADMAP.md) · خطة الإصدار الجاري / Current plan: [docs/PLAN.md](docs/PLAN.md)

---

## العربية

### ماذا يقدّم الإصدار 0.1
| الميزة | الحالة |
|---|---|
| واجهة عربية وإنجليزية كاملة مع RTL، وتبديل اللغة من الإعدادات، وخط IBM Plex Sans Arabic، وثيم داكن، وأيقونة صقر | ✅ |
| مزوّدو ذكاء اصطناعي بمفتاحك: Anthropic وOpenAI وGoogle Gemini وOpenRouter وDeepSeek ومزوّد مخصص متوافق مع OpenAI (APIs رسمية فقط، بلا كشط جلسات ويب) | ✅ |
| حفظ المفاتيح مشفّرة (EncryptedSharedPreferences + Android Keystore)، والنسخ الاحتياطي معطّل | ✅ |
| اختيار النموذج يدويًا أو جلب القائمة من واجهة النماذج الرسمية للمزوّد | ✅ |
| مشاريع داخل التطبيق بقوالب: HTML/CSS/JS وبايثون وNode.js وفارغ | ✅ |
| شجرة ملفات، ومحرر كود بتبويبات وتمييز صيغة وأرقام أسطر وحفظ | ✅ |
| طرفية لينكس بلا Root: ‏proot + ‏Alpine 3.24 يُنزَّل عند أول تشغيل (~4 ميغابايت، مع تحقق SHA-256) | ✅ (طرفية أساسية بلا PTY) |
| الوكيل: محادثة ببث مباشر، وحلقة أدوات (list_files وread_file وwrite_file وedit_file وdelete_file وrun_command وsearch) | ✅ |
| موافقة على الخطة عند تعديل ملفين أو أكثر، ومعاينة الفروقات (diff) | ✅ |
| نقاط استرجاع تلقائية قبل كل دفعة تعديلات، واسترجاع بنقرة | ✅ |
| قائمة حظر للأوامر الخطرة (rm -rf /، ‏curl \| sh، ‏eval، ‏sudo، ‏mkfs …) | ✅ |
| GitHub: إيداع ودفع حقيقي عبر JGit برمز Fine-grained، وإنشاء مستودع خاص اختياريًا | ✅ |

### الجديد في 0.18.0
| الميزة | الحالة |
|---|---|
| «مهارات صقر»: تعليمات قصيرة قابلة للمراجعة (قائمة مراجعة، وصفة اختبارات…) تُرفق بتشغيل الوكيل — داخل صيغة ‎.saqrpack نفسها في قسم `skills` اختياري؛ الحزم القديمة وفهرس المعرض القديم تُقرأ وتُثبّت كما هي تماماً | ✅ |
| استخدام يدوي: اذكر المهارة في رسالتك بالشكل ‎`$skill-id` (مثل ‎`$ar-code-review`) — والإرفاق التلقائي لأنسب مهارتين (BM25 على الجهاز يفهم العربية) خلف مفتاح مُطفأ افتراضياً، وكل إرفاق وسببه في سجل التدقيق | ✅ |
| صلاحيات محدودة: المهارة تعلن أدواتها وسقف خطورة (آمن/متوسط)؛ الأدوات عالية الخطورة لا تُمنح لحزمة أبداً، والأدوات الفعلية = المعلنة ∩ أدوات الدور؛ سياسة الأوامر وبطاقات الموافقة تبقى كما هي، ونص المهارة يصل داخل غلاف «بيانات لا تتجاوز قواعد النظام» | ✅ |
| فحص أمان قبل التثبيت: علامات حقن الأوامر (عربي وإنجليزي)، طلب الأسرار، روابط لعناوين داخلية، المحارف المخفية واتجاه النص، الحجم — مع رفض صادق يذكر السبب | ✅ |
| بصمة SHA-256 للمحتوى تُسجّل عند التثبيت وتُتحقق عند التحديث، ومن المعرض عند وجودها (عدم التطابق = رفض)؛ تحديث بمقارنة الإصدارات وعرض الفرق في سجل التغييرات قبل التطبيق | ✅ |
| مدير المهارات في الإعدادات «المهارات · Skills»: بحث عربي/إنجليزي، فئات، تفعيل/إيقاف، تفاصيل (الصلاحيات، الإصدار، السجل، البصمة)، تقييمك 1–5 وعدد الاستخدام، تحديث، مشاركة وحفظ ملف، حذف، استيراد ملف، ومهارات خاصة محلية لا تُصدَّر إلا بتأكيدك | ✅ |
| المعرض: حقول اختيارية جديدة (الإصدار، البصمة، ملخص الصلاحيات، تقييم إجمالي للعرض فقط، رابط سجل التغييرات، عدد المهارات) و«تحديث متاح» — وأول مهارة حقيقية: «مراجعة الكود بالعربية». التفاصيل: [docs/SKILLS.md](docs/SKILLS.md) | ✅ |

### الجديد في 0.17.0
| الميزة | الحالة |
|---|---|
| «محرك الذاكرة» بثلاث طبقات: الجلسة (مع ملخّص متجدّد للأدوار القديمة يُحفظ مع الجلسة — اختياري)، والمشروع (‎.saqr/memory.json بأنواع: قرار، معمارية، اعتمادية، عُرف، درس، تفضيل، ملاحظة — مع المصدر والوقت والملفات المرتبطة)، وطويلة المدى (تفضيلاتك عبر المشاريع، خاصة بالتطبيق، مُطفأة افتراضياً) | ✅ |
| ترحيل تلقائي لملف الذاكرة القديم: كل الإدخالات تبقى كما هي بنوع «ملاحظة»، والعرض للنموذج لم يتغير؛ والسقف صار ٢٠٠ إدخال | ✅ |
| «دروس من الإصلاحات»: حين يحوّل التشغيل فحوصاً فاشلة إلى ناجحة يُحفظ درس «خطأ ← حل» تلقائياً دون تكرار، ويُعرض الدرس المطابق على المصحِّح ورسالة المُحقِّق في المرة القادمة | ✅ |
| استرجاع بالصلة على الجهاز (BM25 يفهم العربية: الألف والتاء المربوطة والتشكيل و«ال» والجموع) — يُرسل أهم K ذكريات (8 افتراضياً) بدل البنك كاملاً، وتفضيلاتك تُرسل دائماً — خلف مفتاح، مُطفأ افتراضياً | ✅ |
| الذكريات المرتبطة بملفات حُذفت تُعلَّم «قديمة» عند تغيّر ملفات المشروع؛ وورقة الذاكرة صارت بتبويبات (المشروع · عامة · الجلسة) وبحث وفلتر أنواع وحذف ومسح و«اجعلها عامة» | ✅ |
| حارس الأسرار على كل طبقة: يرفض أنماط الأسرار ومفاتيحك الفعلية المُعدّة في التطبيق، وينظّف مخرجات الاختبارات والملخّص قبل الحفظ | ✅ |
| «الموجّه الذكي» (امتداد لموجّه الصقر، مُطفأ افتراضياً): جدول قدرات للنماذج المعروفة (رؤية، أدوات، نافذة السياق، سرعة، سعر)، رخيص للتخطيط وقوي للبرمجة ومراجِع مستقل مختلف عن المبرمج، الصور إلى نموذج رؤية، والسياق الكبير إلى نافذة تتسع له — ومن مزوّدات أضفت مفاتيحها فقط | ✅ |
| سلسلة بدائل عند تعطّل المزوّد: بديلك المختار أولاً ثم نماذج قادرة على مزوّدات أخرى، مع إشعار وسطر في سجل التدقيق لكل قرار وسببه | ✅ |
| سقف إنفاق يومي وشهري يوقف التشغيل بهدوء مع إشعار واضح، وتقدير تكلفة قبل البدء بأسعار كل نموذج الفعلية كنطاق (أدنى–أعلى) | ✅ |

### الجديد في 0.16.0
| الميزة | الحالة |
|---|---|
| «فريق الوكلاء» — وضع القائد (اختياري، مُطفأ افتراضياً): يقرأ القائد طلبك ويصنّفه (سؤال، تعديل، مشروع جديد، إصلاح خلل، بحث، عمل متعدد المراحل) بالقواعد أولاً، والنموذج الرخيص فقط عند الالتباس | ✅ |
| ١٢ دوراً لكلٍّ اسم عربي/إنجليزي وتعليمات مركّزة وأدوات مسموحة فقط (المراجع والأمن والباحث للقراءة فقط؛ النشر يستخدم git لكن الدفع يطلب موافقتك دائماً) ونموذج حسب الفئة عبر موجّه فالكون | ✅ |
| أقل فريق يكفي: السؤال بلا فريق، والتعديل الصغير مطوّر + مراجع، والخلل مصحّح + مختبر + مراجع، والمشروع الجديد مخطّط + معماري + مطوّر + مختبر + مراجع — ويُضاف الأمن عند لمس الدخول/الأسرار/الشبكة/الأوامر والباحث عند الحاجة لمعلومة خارجية | ✅ |
| مخطط مهام بأدوار واعتماديات (كشف الحلقات، الترتيب، المهام الجاهزة، نسبة التقدم) يظهر في شريط الخطة ويُحفظ في ملف المهمة فيُستأنف بعد إغلاق التطبيق | ✅ |
| كل دور يرى فقط ما يحتاجه: الهدف ومهمته وتقارير ما يعتمد عليه (مختصرة) وقائمة الملفات — لا المحادثة كاملة | ✅ |
| تعاون حقيقي: المراجع (والأمن) يراجعان عمل المطوّر؛ الرفض الأول يعيده لصاحبه، والثاني للمصحّح، ثم القرار لك على بطاقة سؤال — ومهمة تفشل مرتين تُسند للمصحّح مرة ثم تُعلَن فاشلة بصدق | ✅ |
| تكلفة كل دور (رموز ودولار) في سطر مختصر بعد التشغيل، وتقدير تقريبي قبل البدء — وبوابة التحقق والميزانيات وسجل التدقيق (مع اسم الدور) تعمل على الفريق كله | ✅ |

### الجديد في 0.15.0
| الميزة | الحالة |
|---|---|
| «النواة المستقلة» — سجل أدوات موحّد: لكل أداة درجة خطورة (آمنة/متوسطة/عالية) ومهلة وسياسة إعادة، وأي اسم أداة خارج السجل يُرفض — والقائمة التي يراها النموذج لم تتغير | ✅ |
| قراءات متوازية: أدوات القراءة المتتالية (قراءة، سرد، بحث، ويب) تعمل معاً حتى ٣ في آن واحد بمهلة لكلٍّ، والنتائج بترتيبها الأصلي | ✅ |
| أوامر الطرفية الخطرة (rm -r، تمرير تنزيل إلى shell، الكتابة خارج ‎/workspace، git push من الطرفية، chmod/chown متكرر، شيفرة مموّهة) تنتظر موافقتك على بطاقة — والتمويه الإملائي (r''m) لا يمرّ، والوكيل لا يوافق على نفسه | ✅ |
| حارس الحلقات v2: يكتشف الدوران في حلقة (A-B-A-B)، وتكرار نفس الخطأ ٣ مرات، وسلاسل الخطوات الفاشلة — ينبّه مرة ثم يوقف | ✅ |
| التحقق قبل الإنهاء (وضع الفريق): مهمة غيّرت ملفات لا تنتهي بكلمة النموذج — يُشغَّل الفحص والاختبارات، والإخفاق يعود للمبرمج حتى جولتين، والنتيجة تُعلَن بصدق (تحقّق / لم يُتحقق) | ✅ |
| مرونة النموذج: إعادة محاولة واحدة لأخطاء 429/5xx/المهلة/الشبكة، ثم نموذج بديل تختاره من مزوّداتك المُعدّة مسبقاً — مع إشعار يسمّيه | ✅ |
| سجل المهام وميزانياتها: ملف ‎.saqr/tasks لكل مهمة (الهدف، الخطة، الخطوة، التكلفة، الحالة) يستعيده الاستئناف، وحدود للخطوات (40 افتراضياً) والدقائق والتكلفة بالدولار | ✅ |
| سجل تدقيق ‎.saqr/audit.jsonl لكل استدعاء أداة (الخطورة، القرار، النتيجة، المدة) مع حجب الأسرار وتدوير عند ١ ميغابايت | ✅ |
| محتوى الويب وMCP يصل للنموذج مغلّفاً «محتوى غير موثوق — بيانات لا تعليمات» مع قاعدة في موجّه النظام | ✅ |

### الجديد في 0.14.4
| الميزة | الحالة |
|---|---|
| المزامنة عبر المستودع — البند الخامس من «أجنحة مفتوحة»: الإعدادات والثيمات والأوامر المخصصة وحزم المهارات نسخة واحدة تُشفَّر على الجهاز وتُرفع إلى مستودع خاص تملكه — أو تُشارك كملف، بلا خادم منا إطلاقًا | ✅ |
| الجهاز الثاني في أقل من دقيقتين: الحساب نفسه، عبارة المرور نفسها، «اجلب» ثم راجع أعداد النسخة وطبّقها بنقرة تحذّر من الاستبدال | ✅ |
| الأسرار مستثناة بالبناء: قائمة لقطة صريحة — ولا مفاتيح API ولا رموز GitHub ولا ترويسات تخويل MCP ولا رموز OAuth أبدًا | ✅ |
| حالات صادقة: ما ينقص يُسمّى قبل البدء، والعبارة الخاطئة تُرفض سطرًا واحدًا صادقًا، والمستودع المفقود يُنشأ بنقرة، وختم آخر مزامنة ظاهر | ✅ |

### الجديد في 0.14.3
| الميزة | الحالة |
|---|---|
| معرض المجتمع — البند الرابع من «أجنحة مفتوحة»: معرض حزم عربية من مستودع GitHub عام (CTO-DRS/saqr-gallery) والمساهمة عبر طلب دمج — بلا خادم منا | ✅ |
| تصفح وبحث عربي (الهمزات والتشكيل لا تعيق المطابقة) وتثبيت مدخل بنقرة عبر خط أنابيب الحزم نفسه — القالب على الشاشة الرئيسية والأوامر بجوار المدمجة | ✅ |
| حالات صادقة: جلب تلقائي أول مرة، وآخر نسخة محفوظة تعمل بلا شبكة مع ملاحظة صريحة، ووسم «مثبّت» للمدخلات الحالية، ورفض مقروء لكل فشل شبكة | ✅ |

### الجديد في 0.14.2
| الميزة | الحالة |
|---|---|
| البناء السحابي (BYOC) — البند الثالث من «أجنحة مفتوحة»: البناء يعمل على GitHub Actions داخل مستودع المستخدم نفسه — رمزه ومستودعه، بلا سحابة منا | ✅ |
| نقرة واحدة: تثبيت سير البناء في المستودع، وإرساله على الفرع الافتراضي، ومتابعة حية بآخر أسطر سجل المهمة، ثم تنزيل ناتج APK وتثبيته داخل التطبيق | ✅ |
| حالات صادقة بلون لكل مرحلة، ورسائل تسمّي ما ينقص (المستودع أو الرمز)، وإيقاف نظيف، وحد زمني 20 دقيقة، وأسطر الخطأ من سجل المهمة عند الفشل | ✅ |

### الجديد في 0.14.1
| الميزة | الحالة |
|---|---|
| MCP عبر OAuth (PKCE) — البند الثاني من «أجنحة مفتوحة»: الخوادم المحمية تسجّل الدخول من الإعدادات ← خوادم MCP ← متصفح الخادم | ✅ |
| اكتشاف تلقائي لنقاط النهاية (بيانات المورد المحمي + RFC 8414) مع تجاوزات يدوية، وتدفق OAuth 2.1 كامل بـ PKCE بطريقة S256 حصرًا داخل متصفح النظام | ✅ |
| الرمز قرب الانتهاء يُجدَّد تلقائيًا قبل اتصال الوكيل، والمنتهي يرتد إلى ترويسة التخويل اليدوية بدل الفشل الصامت | ✅ |
| دورة المتصفح آمنة الحالة: state مانع للتزوير يُتحقق منه في نشاط مخصص، والرموز في SecureStore المشفر والمُثبِت لا يغادر الجهاز، والخطأ 401 يسمّي المخرج | ✅ |

### الجديد في 0.14.0
| الميزة | الحالة |
|---|---|
| حزم المهارات (.saqrpack) — البند الأول من «أجنحة مفتوحة»: حزمة JSON واحدة تنصب بنقرة قالب مشروع وأوامر عربية جاهزة وقواعد AGENTS.md وأدوات مفضلة | ✅ |
| التثبيت من الإعدادات بلصق محتوى الحزمة أو بنقرة على عينة Flask الجاهزة، فتظهر الحزمة بطاقة قالب في الشاشة الرئيسية وينشئ مشروعًا كامل الملفات | ✅ |
| القواعد والأدوات المفضلة تُحقن في AGENTS.md فيقرأها الوكيل تلقائيًا في كل دورة، والأوامر تُدمج بالتوحيد العربي دون تغطية المدمجة | ✅ |
| حماية كاملة: مسارات الحزمة محصورة بلا مطلق ولا قفز .. والأحجام مقيّدة، وإزالة الحزمة تحذف قالبها فقط وتُبقي الأوامر | ✅ |

### الجديد في 0.13.5
| الميزة | الحالة |
|---|---|
| وضع صحراوي (البند الأخير من «عقل الصقر»): ثلاث حالات — إيقاف، «عند الانقطاع» يُفعَّل تلقائيًا عبر مراقب اتصال حيّ (إنترنت مُتحقَّق منه فقط)، و«دائمًا» لترشيد البيانات والبطارية حتى والشبكة موجودة | ✅ |
| كل ميزة سحابية تُجاب برسالة واضحة واحدة بلا انهيار ولا تعليق: الإرسال بإشعار واحد غير متكدس، وأدوات الويب وMCP البعيدة ودفع/سحب Git برفض مقروء يسمّي ما يبقى شغّالًا، وخوادم MCP البعيدة تُتخطى بإشعار بدل المهلة | ✅ |
| كل ما هو محلي يستمر: الملفات والطرفية وبنك الذاكرة وفهرس الرموز وgit المحلي والفحص والاختبارات وخوادم stdio المحلية | ✅ |
| البناء على الجهاز بلا شبكة: بيئة مخزّنة بالكامل تُبنى عليها كما هي، ولا يتوقف التجهيز إلا عند الجزء الناقص فعلًا بسطر يسمّيه ويبيّن المخرج | ✅ |

### الجديد في 0.13.4
| الميزة | الحالة |
|---|---|
| صوت بلا يدين: إملاء مستمر يبقى في الإنصات ما دام المشروع مفتوحًا، يعيد الإنصات تلقائيًا بعد كل جملة مع تراجع تدريجي عند الأخطاء العابرة | ✅ |
| إيقاظ «يا صقر»: تحوّت الإيقاظ بمرونة (التشكيل والهمزات والدمج «ياصقر» وباللاتينية «Ya Saqr») ثم يُرسل الأمر الذي يليه تلقائيًا — ولا يُرسل شيئًا قبل الإيقاظ | ✅ |
| تشغيل/إيقاف من الإشعار: إشعار صامت دائم أثناء الإنصات بزر إيقاف يقلب الوضع من الإعدادات فورًا — والتطبيق لا يسمع من الخلفية أبدًا | ✅ |
| حماية من الحلقات المغلقة: الأخطاء العابرة (صمت/شبكة/انشغال الخدمة) تعيد الإنصات، والقاتلة (صلاحيات/عميل/صوت) تُوقف الوضع برسالة واضحة، وبعد 4 أخطاء متتالية يستسلم بأدب | ✅ |

### الجديد في 0.13.3
| الميزة | الحالة |
|---|---|
| مدرّب صقر: مسار تعلّم من خمس خطوات على الشاشة الرئيسية من أول تثبيت إلى أول بناء ناجح على الجهاز، مع بطاقة تقدّم حيّة (n/5) ولوحة كاملة بالخطوات المرتبة | ✅ |
| لا اكتمال بلا فعل حقيقي: كل خطوة تكتمل فقط بمُشيرها الفعلي (مفتاح محفوظ، مشروع قائم، أول أمر للوكيل، محاولة بناء، بناء ناجح) — والطريق لا يقفز للأمام أبدًا | ✅ |
| أمثلة أوامر عربية جاهزة بزر نسخ: عائلة لكل قالب (ويب/بايثون/Node/Compose/لوحة مفاتيح) وأمثلة عامة — أول أمر على بعد لصقة واحدة | ✅ |
| المدرّب يتذكر حكايته: وقت اكتمال كل خطوة يُختم عند أول رصد ويظهر بتاريخ نسبي، والطريق المكتمل يحوّل البطاقة إلى احتفال «أول بناء ناجح» | ✅ |

### الجديد في 0.13.2
| الميزة | الحالة |
|---|---|
| موجّه النماذج «صقر»: التخطيط والمراجعة وضغط السياق تُوجَّه تلقائياً إلى أرخص نموذج يكفي لدى مزوّدك — والمبرمج يبقى على نموذجك، والاختيار اليدوي لكل مرحلة يتقدم على الموجّه | ✅ |
| حفاظ على الجودة: تصنيف القدرة بعائلة النموذج (خفيف/متوسط/كامل)، والمراجع يشترط متوسطاً فلا تُسند مراجعة الفروقات لنموذج ضعيف | ✅ |
| لا توجيه بلا إثبات: يوجّه فقط حين يثبت جدول الأسعار توفيراً، والنماذج غير المُسعّرة تُتخطى ولا تُخمَّن أبداً | ✅ |
| الرقم المعلن: كل نداء موجَّه يُسعَّر عند نموذجه وعند سعر نموذج المبرمج (خط الأس) ويُخزَّن يومياً — و«وفّره الموجّه» يظهر في الاستهلاك والميزانية | ✅ |

### الجديد في 0.13.1
| الميزة | الحالة |
|---|---|
| فهرس المشروع: فهرس رموز خفيف (ملفات/أصناف/دوال) يتولد عند فتح المشروع على خيوط خلفية، وأداة `project_search` تُعيد «ملف:سطر» فوراً بدل قراءة ملفات كاملة | ✅ |
| بحث عربي-واعٍ: «الشّبكه» تجد الشبكة.kt (توحيد الهمزات والتاء المربوطة والتشكيل)، والترتيب: التطابق التام ثم البادئة ثم الاحتواء | ✅ |
| أول نتيجة < 200ms على مشروع 500 ملف (~3000 رمز)، مع تخطي `.git` و`build` و`node_modules` والملفات > 512KB | ✅ |

### الجديد في 0.13
| الميزة | الحالة |
|---|---|
| بنك الذاكرة: ما يتذكره صقر عن مشروعك وقراراتك يُحفظ في `.saqr/memory.json` (مستثنى من git افتراضيًا) ويُسترجع تلقائيًا في موجه كل جلسة — بلا إعادة سؤال | ✅ |
| مفاتيح عربية ذكية: توحيد الهمزات (أ إ آ ← ا) والتاء المربوطة والألف المقصورة والتشكيل والمدّ فلا تُخزَّن الحقيقة نفسها مرتين، والتحديث بالـ upsert بحدّ 60 مدخلة | ✅ |
| الوكيل يدير ذاكرته: `memory_save` لحفظ قرار أو تفضيل أو حقيقة، و`memory_forget` للتصحيح، مع رفض صريح للأسرار (كلمات السر والرموز بالعربي والإنجليزي) | ✅ |
| استرجاع محدود الميزانية (~2000 حرف) مرتّب: التفضيلات فالقرارات فالحقائق، الأحدث أولًا — لا يسرق سياق العمل | ✅ |
| واجهة «بنك الذاكرة» من قائمة المحادثة: عرض وإضافة وتعديل وحذف وإفراغ — الذاكرة ملكك وقراءةً وتحريرًا | ✅ |

### الجديد في 0.12.1
| الميزة | الحالة |
|---|---|
| الثيم من صورة: أي صورة تتحول محليًا (بدون شبكة) إلى ثيم كامل — أعمدة الألوان الأكثر تكرارًا للنيوترال وأعلى تشبع مميّز للّون المميز وحارس تباين WCAG للنص | ✅ |
| ثيم «ديناميكي» Material You على أندرويد 12+: عائلة اللون المميز وحدها تتلوّن من بذرة الخلفية فوق الحياديات الصقرية الثابتة | ✅ |
| توكنات الاهتزاز الدلالي: TICK / POP / CONFIRM / REJECT / HEAVY مع بدائل للأجهزة الأقدم — على تطبيق الثيم ونجاح/فشل البناء | ✅ |
| عدّة الميدان: توقيت تلقائي لكل خطوة بناء (rootfs، apt، cmdline-tools، sdkmanager، aapt2، gradle) مع هوية الجهاز والذاكرة والبطارية وحجم APK، وتصدير جدول Markdown أو CSV بضغطة | ✅ |

### الجديد في 0.12
| الميزة | الحالة |
|---|---|
| استوديو الثيمات: كلمة المزاج تتحول عبر النموذج النشط إلى ثيم كامل بمعاينة حية، حفظ ونسخ JSON واستيراده، وستّ حزم مجتمعية جاهزة (نجد، تهامة، البحر، الوادي، عُمان، الأندلس) | ✅ |
| ثيم «ليل» OLED أسود نقي بذهب أفتح — وضع رابع بجانب الداكن والفاتح والنظام، بتبديل فوري | ✅ |
| لوحة الأوامر العربية من أي تبويب: ملفات، أوامر شرطة بأسمائها العربية، وجهات الإعدادات، والجلسات — بمطابقة تفهم صيغ الهمزات والحركات | ✅ |
| البطاقات الحية في الرئيسية: نتيجة آخر بناء على الجهاز وفرع git الحالي لكل مشروع | ✅ |
| المحرر: حجم خط (12–20sp) والتفاف أسطر — من الإعدادات وشريط أدوات المحرر، وتطبيق فوري | ✅ |

### الجديد في 0.11
| الميزة | الحالة |
|---|---|
| خطط بالريال: المجانية (3 مشاريع، خادم MCP واحد، مضيف git واحد) · برو 15 ر.س/شهر · الطلاب 5 ر.س/شهر · مدى الحياة 149 ر.س | ✅ |
| فواتير جوجل بلاي (PBL 8) بمنتجات شهرية ومدى الحياة، وتُستعاد تلقائياً عند كل تشغيل | ✅ |
| مويسر: صفحة دفع مستضافة (مدى / Apple Pay / STC Pay) + أدوات تاجر لإنشاء الفواتير ومتابعتها | ✅ |
| رموز ترخيص دون اتصال (SQ1. HMAC-SHA256) تُصنع من التطبيق أو `scripts/license_tool.py` | ✅ |
| بوابات صادقة للحدود المجانية: مشروع رابع، خادم MCP ثانٍ، مضيف ثانٍ، فريق الوكلاء، الإصدار الموقّع | ✅ |
| MCP عبر stdio: خوادم محلية تعمل داخل طرفية التطبيق بـ JSON-RPC على stdin/stdout | ✅ |

### الجديد في 0.10
| الميزة | الحالة |
|---|---|
| التحقق من توقيع الـ APK داخل التطبيق (v2/v3 كامل، ببصمة شهادة الموقّع SHA-256 ومالكها وصلاحيتها) قبل زر التثبيت | ✅ |
| «احتفظ بآخر إصدار فقط»: بعد نجاح البناء تُحذف ملفات APK القديمة ومخلفات البناء الوسيطة تلقائياً | ✅ |
| سماح/منع أدوات MCP لكل خادم: عين تُخفي الأداة عن الوكيل نهائياً | ✅ |
| GitLab وGitea بالكامل: ربط المضيفين وإنشاء المستودعات وفتح طلبات الدمج/السحب من تبويب Git | ✅ |

### الجديد في 0.9
| الميزة | الحالة |
|---|---|
| بناء إصدار موقّع على الجهاز: مفتاح داخل المشروع وكلمات مرور في الذاكرة فقط، وتوقيع AGP بنسخ v1+v2+v3 عبر سكربت init مولّد، والناتج في `dist/app-release.apk` | ✅ |
| مرايا aapt2 جاهزة: «تلقائي» أو **ReVanced/aapt2** (رابط ثابت لأحدث إصدار) أو رابط مخصص — وكلها تُفحص ELF arm64 قبل الاستخدام | ✅ |
| إدارة مخزون البناء في تبويب «بناء»: حجم كل مكوّن (rootfs/SDK/Gradle/التنزيلات) واسترجاعه بنقرة، وإعادة تجهيز تجلب الناقص فقط | ✅ |
| الدخول إلى GitHub برمز الجهاز (Device Flow): Client ID عام + رمز قصير تُوافق عليه من المتصفح — بلا لصق رموز طويلة | ✅ |
| منتقي نموذج المراجع/المخطط بقائمة مقترحة من المزوّد النشط بدل كتابة الأسماء يدوياً | ✅ |
| مستعرض أدوات MCP في الإعدادات: سرد أدوات كل خادم مباشرة وتشغيل أي أداة يدوياً بوسائط JSON معبأة من المخطط | ✅ |

### الجديد في 0.8
| الميزة | الحالة |
|---|---|
| تحصين البناء: تنزيلات مثبّتة بـ SHA-256 رسمي وقابلة للاستئناف (HTTP Range)، وقائمة مرايا مع مرآة aapt2 من الإعدادات وتحقق ELF arm64 صارم | ✅ |
| إرشادات فشل ودّية في سجل البناء (نفاد الذاكرة، الشبكة، التخزين، proot، أخطاء الكود…) ومراقب أعطال التطبيق المبني داخل تبويب «بناء» | ✅ |
| قالب «تطبيق ويب خفيف»: WebView بلا أي اعتماديات — يُبنى في دقائق على الهواتف الضعيفة (مُجرّب بالكامل) | ✅ |
| مرحلة «مخطط» صريحة في وضع الفريق: نموذج اقتصادي يحوّل الطلب إلى خطة مهام قبل الكود، بنموذج قابل للاختيار | ✅ |
| فحوصات تلقائية (فحص + اختبارات) تُمرَّر إلى المراجع مع مفتاح في الإعدادات | ✅ |
| تكلفة المراجع والمخطط تظهران منفصلتين في عدّاد التكلفة وورقة الاستخدام | ✅ |
| دعم خوادم **MCP** عبر Streamable HTTP: أدوات الخادم تظهر للوكيل بأسماء `mcp_…` مع تحليل SSE وترويسة مصادقة مشفّرة | ✅ |

### الجديد في 0.7
| الميزة | الحالة |
|---|---|
| فريق الوكلاء (مخطط ← مبرمج ← مراجع): بعد كل دفعة تعديلات يراجعها نموذج ثانٍ ويتصحّح الوكيل تلقائياً، بنموذج مراجع قابل للاختيار وبطاقة حكم في المحادثة | ✅ |
| ضغط السياق تلقائياً: تلخيص الجولات الأقدم عند تجاوز الحد بدل فقدانها (مفتاح في الإعدادات) | ✅ |
| كاشف الحلقات المفرغة: توجيه عند التكرار ثم إيقاف الجولة لتوفير الرموز | ✅ |
| أدوات جديدة: `web_search` و`web_fetch` (الإنترنت العام فقط مع حجب العناوين الداخلية)، و`lint_project`، و`run_tests` | ✅ |
| بناء APK على الجهاز (تجريبي): ديبيان + JDK 17 + Android SDK داخل proot مع aapt2 arm64 وسجل مباشر وتثبيت بنقرة | ✅ |
| قوالب جديدة: تطبيق Kotlin + Compose عربي RTL، ولوحة مفاتيح IME عربية — تُبنى مباشرة من تبويب «بناء» | ✅ |

### الجديد في 0.6
| الميزة | الحالة |
|---|---|
| حفظ سجل المحادثات على الهاتف واستعادة آخر جلسة عند التشغيل، مع استئناف فعلي للوكيل | ✅ |
| قائمة الجلسات: جديدة، فتح، إعادة تسمية، حذف، بحث يفهم العربية، عنوان تلقائي | ✅ |
| إدخال صوتي بالعربية (ar-SA) والإنجليزية (en-US) مع نتائج مباشرة، و«اقرأ بصوت» للردود | ✅ |
| الوكيل يعمل في الخلفية (خدمة أمامية) مع إشعارات الخطوة و«صقر يحتاج ردّك» وأزرار إجابة مباشرة | ✅ |
| استئناف المهمة بعد أن يوقف النظام التطبيق، وعرض «استئناف المهمة؟» بعد إعادة تشغيل الهاتف | ✅ |

### الجديد في 0.5
| الميزة | الحالة |
|---|---|
| منتقي العناصر في المعاينة: انقر عنصرًا، صف التعديل، والوكيل يعدّل السطر المطابق في الكود | ✅ |
| جسر آمن ومحلي فقط بين المعاينة والتطبيق برمز عشوائي لكل صفحة | ✅ |
| الصورة إلى كود: إرفاق صور من الكاميرا أو المعرض، وطلبات متعددة الوسائط (OpenAI المتوافق وAnthropic) | ✅ |
| اكتشاف تلقائي لدعم الصور لكل نموذج مع تجاوز يدوي من الإعدادات | ✅ |
| الاحتفاظ بآخر صورتين فقط في السياق لتوفير التكلفة | ✅ |

### الجديد في 0.4
| الميزة | الحالة |
|---|---|
| محرر: تمييز صيغة تدريجي لـ 12 لغة، بحث واستبدال مع Regex، تراجع/إعادة واختصارات لوحة المفاتيح | ✅ |
| أداة `ask_user`: بطاقات خيارات تفاعلية، ورقائق رد سريع عند سرد الخيارات | ✅ |
| نقاط استعادة لكل جولة تعديل مع استرجاع بنقرة | ✅ |
| أوامر الشرطة المائلة (`/fix` و`/explain` و`/test` و`/review` و`/plan` و`/commit`…) بأسماء عربية بديلة (`/اصلح` `/اشرح` `/اختبر` `/راجع` `/خطة`) وأوامر مخصصة | ✅ |
| عدّاد الرموز والتكلفة وميزانية شهرية (تنبيه 80%، تأكيد 100%) | ✅ |
| الإيداع (`git_commit`) والدفع (`git_push`) يتطلبان موافقتك | ✅ |

### الجديد في 0.3
محرّك Git كامل عبر JGit (فروع، دمج، سحب ودفع، فروقات)، شاشة التحكم بالمصدر، شاشة حسابات GitHub مع التحقق من الرمز، أدوات Git للوكيل، طرفية Termux حقيقية بجلسات متعددة، وأيقونة "Stoop" وشاشة بداية جديدة.

### الجديد في 0.2
واجهة مُعاد تصميمها بالكامل: نظام تصميم "Falcon" (داكن وفاتح، لون ذهبي مميز)، محادثة وكيل بشريط علوي عائم وحبة الخطة وصفوف أدوات مدمجة مع شارات الفروقات والمدة والحالة، عدّاد الخطوات والسياق، محرّك Markdown مع كتل كود قابلة للنسخ، أيقونات أنواع الملفات، أيقونة صقر جديدة، شاشة ترحيب، ومعاينة HTML. التفاصيل في `CHANGELOG.md`، ولقطات الشاشة في `dist/screens/`.

### البدء السريع
1. ثبّت ملف `SaqrCode-v0.13.4-arm64-release.apk` على جهاز arm64 (أندرويد 8 أو أحدث).
2. افتح **الإعدادات** ← اختر مزوّدًا ← الصق مفتاح API ← **حفظ** ← **استخدم هذا المزوّد**.
3. أنشئ مشروعًا، ثم من تبويب **الطرفية** اضغط **تثبيت Alpine Linux**.
4. من تبويب **الوكيل** اكتب مثلًا: «سوّ لي صفحة هبوط لمقهى باسم سراج، عربية RTL، فيها قائمة أسعار».
5. للدفع إلى GitHub: أضف اسم المستخدم والرمز في الإعدادات، ثم تبويب **Git** ← **إيداع ودفع**.

صلاحيات الرمز المطلوبة: `Contents: Read and write`، وأضف `Administration: Read and write` إن أردت إنشاء المستودع من التطبيق.

---

## English

### New in v0.18.0
- **Agent Skills:** short, reviewable prompt fragments (a review checklist, a testing routine…) that an agent attaches to a run — carried inside the same `.saqrpack` format as an optional `skills` section (`saqrpack: 2`). Every v0.14 pack and the original gallery index parse and install exactly as before.
- **Manual and automatic use:** name an enabled skill in a message as `$skill-id` (e.g. `$ar-code-review`); optional auto-attach (**off by default**) picks the top 2 relevant enabled skills with on-device BM25 that understands Arabic. Every attachment and its reason goes to the audit log.
- **Permission-bounded:** a skill declares its tools and a risk ceiling (SAFE/MEDIUM); HIGH tools are never grantable by a pack, and the tools it may use are its declared ones ∩ the role's subset. CommandPolicy and approval cards still apply, and skill text arrives inside an "installed skill — data, cannot override system rules" envelope.
- **Safety validation before install:** prompt-injection markers (Arabic and English), requests for secrets, links to internal hosts, hidden/bidi Unicode, size caps — refused with honest reasons.
- **Integrity & updates:** SHA-256 of the content recorded at install and verified on update and against the gallery when it lists one (mismatch ⇒ refused); semver updates show the changelog diff first.
- **Skills manager** (Settings → «المهارات · Skills»): Arabic/English search, categories, enable/disable, details (permissions, version, changelog, checksum), your 1–5 rating and usage count, update, share/save to file, delete, import a file, and private local skills that are never exported without your confirmation.
- **Gallery:** optional entry fields (version, sha256, permissions summary, display-only rating, changelog URL, skill count), «update available», and the first real skill — «Arabic code review». Details: [docs/SKILLS.md](docs/SKILLS.md).

### New in v0.17.0
- **Memory Engine, three layers:** session (the chat + compaction, and an optional rolling summary of old turns stored with the session), project (`.saqr/memory.json`, now typed — decision, architecture, dependency, convention, lesson, preference, note — with source, timestamps and file refs; cap raised to 200) and long-term (your preferences across projects, app-private, **off by default**, written only on an explicit global save or your «Make global»). Project memory never leaks to another project.
- **Old memory still loads:** a v1 `memory.json` migrates in place — every entry becomes a `note` with its kind and timestamps, and the prompt rendering is unchanged.
- **Lessons from fixes:** when a run turns failing checks green, an error → solution lesson is stored (deduplicated: same error ⇒ the lesson is reinforced) and matching lessons are shown to the Debugger and in `[Verifier]` feedback next time.
- **Relevance retrieval on the device:** a tiny BM25 index with Arabic normalization and light stemming injects only the top-K memories (default 8) plus your preferences instead of the whole bank — behind a switch, off by default (the old inject-all stays).
- **Stale refs & Memory sheet:** memories whose files disappeared are flagged when project files change; the sheet gained Project · Global · Session tabs, search, a type filter, delete, clear and «Make global».
- **Secret guard on every layer:** secret patterns and your real configured keys/tokens are refused; captured test output and summaries are scrubbed first.
- **Smart Router** (extends Falcon Router; **off by default** — Falcon Router unchanged when off): a capability table (vision, tools, context, speed, price) for the models the app knows; cheap for planning, strong for coding, an **independent reviewer** different from the coder, images to a vision model, big prompts to a window that fits — only providers you added a key for (or a local endpoint).
- **Failover chain:** on a provider failure, your fallback first, then capable models on other providers, each switch with a notice; every router decision and its reason in the audit log.
- **Spend caps & estimates:** daily/monthly caps (USD) stop a run cleanly with a clear notice; the Chief's pre-run estimate uses real per-model prices as a low–high range. Details: [docs/MEMORY.md](docs/MEMORY.md), [docs/ROUTER.md](docs/ROUTER.md).

### New in v0.16.0
- **Agent Team — Chief mode** (optional, off by default; team mode): the Chief classifies each request — question, edit, new project, bug fix, research, multi-stage — with rules first and the cheap model only when they are unsure, then staffs it with the **minimum** set of roles.
- **12 roles** (Chief, Planner, Research, Architect, Developer, Debugger, Security, Tester, Reviewer, Optimizer, Documentation, Deployment), each with an Arabic + English name, a focused prompt, only its allowed tools (Reviewer/Security/Research/Architect read-only; Deployment may build and use git, but `git_push` still always asks you) and a model tier routed through Falcon Router.
- **Minimal teams:** a question gets no team, a small edit Developer + Reviewer, a bug Debugger + Tester + Reviewer, a new project Planner + Architect + Developer + Tester + Reviewer; Security joins when auth/secrets/network/commands are touched, Research when external information is needed.
- **Task graph:** subtasks with roles and dependencies (cycle detection, topological order, ready set, progress %), shown in the plan pill, saved in the task file and resumed after the app is killed. The planner JSON gained optional `role`/`dependsOn` — old plans parse as before.
- **Scoped context & collaboration:** each role sees only the goal, its subtask, its dependencies' capped reports and the relevant files. Reviews: author fix → Debugger → your decision on an ask card; a subtask failing twice is reassigned once to the Debugger, then failed honestly; conflicting edits between roles are detected and reconciled.
- **Cost per role:** tokens and priced spend per role in a compact line after each team run (`RunOutcome.roles`), and a rough estimate before start. The completion gate, budgets, audit log (now with the role) and loop guard (per role) apply to the whole team. Details: [docs/AGENTS.md](docs/AGENTS.md).

### New in v0.15.0
- **Autonomous Core — tool registry:** every tool now carries a risk tier (SAFE / MEDIUM / HIGH), a read-only flag, a timeout and a retry policy; a tool name outside the registry (or this turn's MCP list) is refused. The tool list the model sees is unchanged.
- **Parallel reads:** consecutive read-only SAFE calls (read, list, search, project search, git status/diff/log, web) run side by side — at most 3, each under its own timeout — and results keep their original order.
- **Tiered command policy:** on top of the existing blocklist, HIGH-risk terminal commands (rm -r, downloads piped into an interpreter, writes outside /workspace, git push/force/reset --hard from the shell, recursive chmod/chown, obfuscated inline scripts, commands assembled from variables) wait for the user's approval card; quoting tricks like `r''m` are de-obfuscated first, and the agent can never approve itself.
- **Loop guard v2:** besides exact triple repeats, it catches A-B-A-B / A-B-C-A-B-C cycles, the same failing call with the same error three times, and six-step streaks of only failing tools — nudge once, then stop.
- **Completion gate (team mode):** a run that changed files cannot finish on the model's word alone — lint and the project's tests run, failures go back to the coder as a `[Verifier]` message (max 2 rounds), and the final status (verified / not verified / no checks could run) is reported in the chat and the finished notification. No fake success.
- **Model resilience:** a 429/5xx/timeout/network failure is retried once with backoff; if it still fails and the user picked a fallback model among providers they already configured, the step runs there with a notice naming it. No key is ever assumed.
- **Task state & budgets:** each run writes `.saqr/tasks/<id>.json` (goal, plan, step, active time, tokens, cost, status) on every step and resume restores it; per-task budgets for steps (default 40, as before), active minutes and priced cost stop cleanly with a notice.
- **Audit log & untrusted content:** every tool call lands in `.saqr/audit.jsonl` (risk, decision, ok, duration, redacted summary; rotated at 1 MB); web and MCP output reaches the model inside an UNTRUSTED CONTENT envelope with a matching system-prompt rule.
- Every feature has its own switch in Settings → Autonomous core (Arabic + English).

### New in v0.14.4
- **Repository sync** — the fifth «أجنحة مفتوحة» item: settings, themes, custom commands and skill packs become one snapshot, **encrypted on the device** (AES-GCM + PBKDF2 passphrase) and pushed to a private repository the user owns — their token, their repo, no Saqr server — or shared as a file offline.
- **A second device in under two minutes:** link the same GitHub account, type the same passphrase, pull, review the snapshot's device/date/counts, then apply with a button that explicitly warns it replaces themes, commands and packs.
- **Secrets excluded by construction:** the snapshot whitelist is an explicit auditable table — API keys, GitHub tokens, MCP auth headers and OAuth tokens never enter the payload; a wrong passphrase or a damaged copy refuses with one honest line; a missing repository becomes a one-tap private-repo creation.

### New in v0.14.3
- **Community gallery** — the fourth «أجنحة مفتوحة» item: Arabic packs live in a public GitHub repository (`CTO-DRS/saqr-gallery`) and contribution is a pull request — no Saqr-side server, the community owns the shelf.
- **Browse, search, one-tap install:** the Arabic-aware search matches through hamza forms and diacritics; installing an entry runs the exact same pack pipeline as a pasted `.saqrpack` — the template lands on the home screen and the commands merge beside the built-ins.
- **Honest, offline-first UX:** the first visit fetches automatically, the last good copy survives offline with an explicit note, installed entries carry a «مثبّت» tag, a 404 names the missing index, and every failure is a readable refusal.

### New in v0.14.2
- **Cloud build (BYOC)** — the third «أجنحة مفتوحة» item: the build runs on GitHub Actions inside the user's own repository — their token, their repo, never a Saqr cloud.
- **One tap end-to-end:** Saqr installs the build workflow into the repo, dispatches it on the default branch, streams a live log tail of the running job, then downloads the APK artifact for in-app install (debug APK — no signing setup needed).
- **Honest UX:** colored state tag (queued / running / downloading / ready / failed), hints naming exactly what is missing, «open the run on GitHub», clean stop, a 20-minute time box, and the job's real error lines on failure.

### New in v0.14.1
- **MCP over OAuth (PKCE)** — the second «أجنحة مفتوحة» item: protected MCP servers sign in from Settings → MCP servers → the server's browser, with automatic endpoint discovery (protected-resource metadata + RFC 8414) and manual overrides.
- **Full OAuth 2.1 authorization-code flow with PKCE (S256)** inside the system browser; tokens live in the encrypted SecureStore and never touch plain prefs.
- **Tokens stay fresh:** an expiring token refreshes right before the agent connects, an expired one falls back to the manual auth header, and a 401 names the way out (sign in again from Settings) instead of a bare HTTP error.
- **State-safe browser round-trip:** the anti-forgery state is validated in a dedicated no-UI callback activity; mismatches are refused outright.

### New in v0.14.0
- **Skill packs (.saqrpack)** — the first «أجنحة مفتوحة» item: one JSON bundle installs, in one tap, a project template, ready Arabic slash commands, AGENTS.md rules and preferred tools.
- **Install from Settings** by pasting the pack JSON or one tap on the built-in Flask sample; the pack then appears as a template card on the home screen and creates a full project (template base + the pack's files).
- **Rules and preferred tools land in AGENTS.md**, so the agent reads them in every turn automatically; commands merge by the Arabic-normalized name and can never shadow a builtin.
- **Hardened by design:** pack paths are sandboxed (no absolute paths, no `..` jumps), sizes are capped, and uninstalling a pack removes its template but keeps the installed commands.

### New in v0.13.5
- **Desert mode (وضع صحراوي):** the final «عقل الصقر» item — an offline-first mode with three settings: off, **«عند الانقطاع»** (activates automatically while the device has no validated internet) and **«دائمًا»** (forced rationing of data / battery / heat even online).
- **Every cloud feature degrades gracefully:** a fresh send lands as one deduped desert notice; web tools, remote MCP servers and git push/pull answer the model mid-turn with a readable refusal naming what still works; remote MCP servers are skipped with a notice instead of timing out. No crash, no hang.
- **Everything local keeps working:** files, terminal, memory bank, symbol index, local git, lint/tests, local stdio MCP servers — untouched.
- **On-device build offline:** a fully cached environment builds as-is; only a genuinely missing download stops, with a log line naming it and the way out.

### New in v0.13.4
- **Hands-free voice (صوت بلا يدين):** the fifth «عقل الصقر» item. Continuous dictation keeps re-listening while a project is open — each utterance chains into the next with a gentle back-off (350ms → 4s) on transient errors (silence, busy service, network), and a hard stop after 4 consecutive failures so a broken recognizer never spins forever.
- **«Ya Saqr» wake phrase:** flexible matching through the Arabic-aware normalizer (diacritics, alef forms, fused «ياصقر») plus latin spellings («Ya Saqr», «hey saqer»…). In wake mode only a wake phrase starts a command, and the command that follows **sends itself**; speech before the wake is never typed or sent.
- **Notification toggle:** a silent ongoing notification mirrors the listening state with a Stop action that flips the persisted mode instantly — the project screen tears the recognizer down reactively. Starting mic capture from the notification is deliberately not supported: Saqr never listens while backgrounded.
- **Two modes:** «إملاء مستمر» types into the composer (auto-send follows the existing setting), «إيقاظ يا صقر» hands the agent voice commands hands-free; the mic button stops hands-free with one tap while it is on.

### New in v0.13.3
- **Saqr Coach (مدرّب صقر):** a five-step learning path on the home screen — connect a key, create a project, send the first command, try the on-device build, reach the first successful build — with a live n/5 progress card and a full coach sheet listing the ordered steps.
- **Real signals only:** a step completes only when the underlying thing actually happened (stored key, existing project, first command received, build attempt, successful build); the path never skips ahead, and a failed attempt still completes «try the build» — the final gate is a successful one.
- **Arabic starter commands:** a curated library of concrete Arabic prompts (one family per template + universal ones) with a copy button, following the newest project's template family, so the first command is one paste away.
- **The coach remembers its story:** completion times are stamped on first observation and shown as relative dates; the finished path turns the card into a small celebration instead of vanishing. Hide it from the sheet, re-open it from Settings.

### New in v0.13.2
- **Falcon Router (موجّه النماذج):** the light agent stages — planning, review, context compaction — route automatically to the **cheapest capable model** on your provider while the coder keeps yours. Capability is tiered by model family (haiku/mini/flash → light, opus/pro → full, rest → mid); the reviewer requires mid so diff review never lands on a weak model. An explicit per-stage pick always wins over the router.
- **Provable savings only:** routing happens only when the price table proves it (a priced candidate cheaper than the coder); unpriced models are skipped, never guessed — and the original vendor-prefixed id (OpenRouter etc.) is what reaches the API.
- **The published number:** every routed call is priced at its model and at the coder's price (baseline), persisted daily in `usage/falcon.json`, and shown as «Saved by the router (this month)» in Usage & budget — measured on your real workload, on your own price table.

### New in v0.13.1
- **Project index (فهرس المشروع):** a lightweight symbol index (files, classes, objects, interfaces, enums, functions) built on a small worker pool when the project opens, refreshed when stale. The new `project_search(query)` tool answers «file:line · kind · name» instantly, so the agent locates code instead of listing directories or reading whole files.
- **Arabic-aware symbol search:** queries and names match through the same normalization as memory keys — «الشّبكه» finds `الشبكة.kt` (alef forms, taa marbouta, diacritics); ranking is exact → prefix → Arabic-normalized → contains → path. First result under 200 ms on a 500-file / ~3000-symbol project; `.git`, `build`, `node_modules` and files over 512 KB are skipped.

### New in v0.13
- **Memory bank (the first «عقل الصقر» item):** every project gets a persistent memory the agent recalls automatically at the start of each turn — durable decisions (stack, architecture), user preferences (language, style, workflow) and project facts. The bank lives in the project at `.saqr/memory.json` (git-ignored by the default Saqr `.gitignore`, hidden from the project map) and is rendered into the system prompt inside a ~2000-char budget, ordered preferences → decisions → facts, newest first.
- **Arabic-aware memory keys:** alef forms, taa marbouta, alef maqsura, diacritics and tatweel all collapse to one normalized key — «إدارة الحالة» and «اداره الحاله» are the same memory; saving over a key updates it (upsert) and the bank holds at most 60 entries, evicting the oldest update.
- **The agent maintains its own memory:** `memory_save(key, value, kind)` (preference / decision / fact) and `memory_forget(key)` are first-class tools; secrets are refused by a heuristic that catches English assignment patterns (password/token/API key), Arabic secret words (كلمة السر، كلمة المرور…) and known token shapes (ghp_…, sk-…, AKIA…). The system prompt tells the model to honor memories instead of re-asking.
- **Memory sheet:** the chat overflow menu opens the bank — view, add, edit, delete, clear — bilingual (AR/EN), so the user owns every remembered line.

### New in v0.12
- **Theme Studio:** a mood phrase becomes a complete color theme via the active model (one-shot JSON, tolerant parsing) with a live swatch preview, apply / save / copy-JSON and JSON import, plus **six built-in community packs** (Najd, Tihama, Bahar, Wadi, Oman, Andalus). A pack is six colors; the rest of the palette (borders, states, code/terminal) is derived, so the app stays token-driven. A pure-black **OLED «ليل» theme** joins dark / light / system, and the editor gains a live font-size (12–20sp) and line-wrap control (Settings + toolbar).
- **Image → theme (0.12.1):** pick any photo and a deterministic offline pipeline (RGB histogram, saturated-distinct accent, WCAG text guard) derives the full pack on-device — the Theme Studio's deferred item, closed without a network call.
- **Material You «Dynamic» (0.12.1):** a fifth theme mode on Android 12+ re-tints only the accent family from the wallpaper seed over the fixed Saqr neutrals; gold fallback below 12.
- **Semantic haptics (0.12.1):** one token per acknowledged touch (TICK / POP / CONFIRM / REJECT / HEAVY) with API-level fallbacks, wired to theme application and build outcomes.
- **Field kit (0.12.1):** every build step is timed automatically with the device identity (SoC family, RAM, battery, APK size); the Build tab exports a README-ready Markdown table or CSV in one tap — real Snapdragon/MediaTek field numbers straight from the phone.
- **Arabic command palette:** the IDE top bar gains a search button — one box that reaches files, slash commands (Arabic aliases), settings destinations and the sessions sheet, with Arabic-aware ranked matching (alef forms, diacritics, tatweel).
- **Live home cards:** each project shows its last on-device build result (✓/✗ + when) and the current git branch (read from `.git/HEAD`).

### New in v0.11
- **Plans in SAR with honest gating:** Free keeps everything (3 projects, 1 MCP server, 1 git host); **Pro SAR 15/mo**, **Student SAR 5/mo** (honors-system), **Lifetime SAR 149** lift the caps and unlock team agents + signed release builds. Every limit attempt opens the paywall with an explanation instead of failing silently.
- **Google Play Billing (PBL 8.0):** `saqr_pro_monthly` / `saqr_student_monthly` / `saqr_lifetime` — purchases acknowledged immediately, re-read from Play on every connect (re-installs keep their plan); devices without Play services fall back to Moyasar. Tier merge / 31-day monthly trust / 3-day grace live in pure, tested code.
- **Moyasar + offline license tokens:** the paywall opens the merchant's hosted Moyasar page (mada, Apple Pay, STC Pay, cards) and accepts `SQ1.` license tokens (HMAC-SHA256) minted via Settings → Billing → merchant tools (create invoice, poll status, mint token) or `scripts/license_tool.py`. The HMAC key ships from `keystore.properties` (private repo); the public fallback is explicitly non-secret.
- **MCP over stdio:** local MCP servers (filesystem, git, sqlite…) run inside the app's terminal env (proot Linux or /system/bin/sh) — newline-delimited JSON-RPC over stdin/stdout, full initialize handshake, per-call timeouts, EOF/flood detection, same `mcp_<server>_<tool>` namespace and plan budget as HTTP servers.
- **SecureStore hardening:** a broken device keystore now degrades to plain prefs with a warning instead of crashing, and the real store can boot in JVM tests.

### New in v0.10
- **In-app APK signature verification:** a from-scratch implementation of APK Signature Scheme v2/v3 verification (no apksigner download) — the Build tab's «تحقق من التوقيع» checks the signature with the embedded key (RSA PSS/PKCS1, ECDSA, DSA), recomputes the chunked content digests, and shows the signer certificate's SHA-256 fingerprint, subject and expiry before you tap Install; any modification after the build is rejected loudly.
- **Keep only the last APK:** an on-device-build setting that cleans previous APKs in `dist/` and the heaviest build leftovers (intermediates, tmp, project `.gradle`) after every successful build, logging the reclaimed size.
- **MCP per-server tool allow/deny:** an eye toggle in the tools browser hides tools from the agent — hidden tools are removed from its spec list and refused even if called anyway.
- **GitLab & Gitea end-to-end:** link any HTTPS host with a token (Auto/GitLab/Gitea dialect pick with hostname auto-detection), publish projects to GitLab/Codeberg/self-hosted Gitea from the Git tab, and open merge requests / pull requests on the origin's forge automatically.

### New in v0.9
- **Signed release builds on-device:** the Build tab has an «إصدار موقّع» switch — point it at a keystore inside the project, type the passwords (memory only, never persisted), and the build runs `assembleRelease` signed by AGP (v1+v2+v3) through a generated init script; output lands in `dist/app-release.apk` (release is built without shrinking to fit the phone).
- **aapt2 mirror presets:** Auto (GitHub-API lookup of `Llzzy/aarch64-android-builds`), the verified **ReVanced/aapt2** stable URL, or a custom direct URL — all ELF-validated before use.
- **Build-cache management:** the Build tab shows each component's footprint (Debian rootfs / SDK / Gradle / downloads) with one-tap reclaim; the next Prepare re-fetches only what is missing.
- **GitHub Device Flow sign-in (RFC 8628):** paste your OAuth App's public Client ID once, get a short code, approve at github.com/login/device in any browser; the app polls and links the account — no long tokens to copy on a phone.
- **Per-provider model pickers:** the reviewer/planner model fields get a suggestions menu from the active provider.
- **MCP tools browser:** tap a server in Settings to list its tools live, inspect the schema summary, edit prefilled JSON arguments and run the tool manually to see the raw result.

### New in v0.8
- **Hardened build path:** every build component (ubuntu-base, cmdline-tools, Gradle) is pinned to the publisher's SHA-256 and verified before use; downloads **resume** with HTTP Range instead of restarting; sizes are capped and mirrors tried in order. The arm64 **aapt2** accepts a direct mirror URL from Settings → On-device build and is always **ELF-validated**.
- **Friendly failure hints + crash monitor:** known failures (memory, network, storage, proot, apt, compile errors…) get an actionable hint line in the build log; a «Check crashes» button parses the device crash buffer and shows the built app's FATAL EXCEPTIONs in the Build tab.
- **Lite Web template:** a zero-dependency WebView app (plain Activity, one Kotlin file + Arabic RTL `index.html`) that builds in minutes even on weak phones — validated end-to-end (605 KB APK).
- **Explicit planner stage:** in team mode a cheap model turns each substantial request into a 2–6 task plan before coding; the plan fills the progress pill and is handed to the coder as a `[Planner]` message. Planner model selectable; small talk and slash commands skip it.
- **Automatic checks for the reviewer:** after each applied batch, lint (and tests, when a runner exists) run and their output joins the reviewer's context; a diff-caused check failure is a real issue. Settings toggle, on by default.
- **Separate review/planner cost:** the reviewer's and planner's token spend show separately in the step meter and the usage sheet.
- **MCP support (Streamable HTTP):** add servers in Settings → MCP servers (name + HTTPS URL + optional encrypted auth header); their tools appear to the agent as `mcp_<server>_<tool>`, discovered once per turn, SSE responses parsed, unreachable servers reported as a notice.

### New in v0.7
- **Agent team (Planner → Coder → Reviewer):** after every applied file batch, a second model reviews the diff and the coder fixes its notes in the same turn (max 2 review rounds). The reviewer model is selectable in Settings (blank = the coder's model); a verdict card shows the result in chat.
- **Auto context compaction:** when the estimated context passes the threshold, older turns are summarised into one brief the loop continues from (Settings toggle; cut points are always safe user-message boundaries).
- **Loop detector:** identical repeated tool calls get one nudge, then the turn stops instead of burning tokens in circles.
- **New agent tools:** `web_search` + `web_fetch` (public internet only — private/link-local/cloud-metadata addresses are blocked), `lint_project` (node --check, py_compile, JSON and HTML checks), `run_tests` (npm test / pytest / unittest detection and run).
- **On-device APK build (experimental):** a **Build** tab prepares a Debian rootfs (ubuntu-base) + OpenJDK 17 + Android cmdline-tools + android-35 + an arm64 aapt2 inside proot, streams the Gradle log live, and installs the produced debug APK with one tap. Pairs with the new Compose template for a full phone-only workflow.
- **New templates:** Kotlin + Jetpack Compose app (Arabic/RTL-ready) and an Arabic IME keyboard (InputMethodService + Arabic layout).

### New in v0.6
- **Persistent chat history:** each project's sessions are saved on the phone (JSON per session, images as files); the last session reopens on start and the agent really resumes from the rebuilt history. **Sessions** list with new / rename / delete / Arabic-aware search / auto-titles (200 per project, warning above 50 MB). `ask_user` answered after a restart continues the real turn.
- **Voice:** mic button with live Arabic (ar-SA) / English (en-US) dictation (long-press to switch), optional send-after-dictation, and **Read aloud** for replies (TextToSpeech, code skipped).
- **Background agent:** turns run under a foreground service and keep going when you leave the app, swipe it away or lock the screen (wake + Wi-Fi locks only while running). Ongoing notification with step/tool, Stop and Open; “Saqr needs your answer” with inline buttons for small single-choice questions; finish/error summaries. Interrupted turns resume after the process is killed; a “Resume task?” notification after reboot. One-time battery-optimization guide (Infinix/XOS, Xiaomi…) and a “Keep running in background” toggle.

### New in v0.5
- **Preview element picker:** tap an element in the web preview, describe the change, and the agent edits the matched source line. Secure local-only bridge (project preview pages only, per-page random token regenerated on navigation).
- **Image → code & image attachments:** attach images from the camera or photo picker; multimodal requests for OpenAI-compatible providers and Anthropic.
- **Vision auto-detect** per model with a per-model override (Auto / On / Off) in Settings, plus a warning and model suggestion when the current model can't see images.
- **Last-2-images pruning:** only the two most recent images stay in context, to keep token cost down.

### New in v0.4
- **Editor:** incremental syntax highlighting for 12 languages, find & replace with regex, undo/redo with keyboard shortcuts (Ctrl+Z/Y/F/H, F3).
- **`ask_user` tool:** interactive single/multiple-choice cards; quick-reply chips when a reply lists options.
- **Checkpoints:** one per file-changing agent turn; restore any checkpoint with one tap.
- **Slash commands:** `/fix`, `/explain`, `/test`, `/review`, `/plan`, `/commit`, `/clear`, `/model`, with Arabic aliases (`/اصلح`, `/اشرح`, `/اختبر`, `/راجع`, `/خطة`) and user-defined custom commands.
- **Token / cost meter** per turn and session, today/month totals, and a **monthly budget** (warn at 80%, confirm at 100%).
- **`git_commit` now requires approval**, like `git_push`.

### New in v0.3
Full JGit engine (branches, merge, pull/push, diffs), Source control screen, GitHub Accounts screen with token verification, agent git tools, real Termux terminal with multiple sessions, "Stoop" icon and splash screen.

### What v0.1 includes
- **Arabic + English UI** with full RTL, in-app language switch (per-app locales), IBM Plex Sans Arabic, dark Material 3 theme, falcon adaptive icon.
- **BYOK providers (official APIs only):** Anthropic Messages API, OpenAI, Google Gemini (official OpenAI-compatible endpoint), OpenRouter, DeepSeek, and any custom OpenAI-compatible base URL. Model picker with live model list.
- **Secure key storage:** EncryptedSharedPreferences backed by an Android Keystore AES-256-GCM master key; `allowBackup=false` and backup exclusion rules.
- **Projects:** create/open/delete in app storage, templates (Web, Python, Node, Empty), each with an `AGENTS.md` rules file the agent reads.
- **Editor:** file tree (create/delete via long-press), tabs, dirty markers, save, regex syntax highlighting (Kotlin, Java, JS/TS, Python, shell, HTML/CSS, Go, Rust, C, Dart), line numbers, always-LTR code.
- **Terminal:** root-less Linux using Termux's `proot` (bundled as `libproot.so` so it is executable from `nativeLibraryDir` on Android 10+) + Alpine minirootfs 3.24.4 downloaded on first run and verified with SHA-256. Project mounted at `/workspace`. Falls back to Android `/system/bin/sh` if Linux is not installed.
- **Agent:** streaming chat; tool-calling loop (up to 40 steps) with `list_files`, `read_file`, `write_file`, `edit_file`, `delete_file`, `run_command`, `search`; per-turn context injection (project map, files touched this session, `AGENTS.md`); replies in the user's language with Gulf/Saudi dialect understanding.
- **Plan approval & diffs:** any turn that changes 2+ files becomes one approval card with per-file diffs (or every change, if "approve all" is on). Single-file changes are applied and shown as a diff.
- **Restore points:** before applying any batch the previous content of every touched file is snapshotted; restoring a point reverts it and all newer points.
- **Safety:** command blocklist (recursive deletes outside the project, `curl|sh`, `eval`, `$IFS` tricks, `sudo/su`, `mkfs`, raw `dd`, fork bombs, cloud-metadata SSRF, reverse shells…). Paths are confined to the project root.
- **GitHub:** real git via JGit 5.13 — init, add (incl. deletions), commit, push over HTTPS with a fine-grained token; optional creation of a private repo via the REST API.

### Screenshots
`dist/screens/*.png` are rendered on the JVM (Robolectric native graphics, Arabic, dark + light):
`SAQR_SCREENS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'`.

### Build
Requirements: JDK 17+ (tested with JDK 21), Android SDK platform 35 + build-tools 35.0.0, Gradle wrapper (8.11.1), AGP 8.7.3, Kotlin 2.0.21.

```bash
# On a normal x86_64 machine remove the aapt2 override line from gradle.properties first.
./gradlew testDebugUnitTest      # JVM unit tests (V0x feature suites; + opt-in screenshot renders)
./gradlew lintDebug              # 0 errors
./gradlew assembleRelease        # signed with keystore.properties if present, otherwise debug key
```

Release signing: create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
Optional integration test (downloads Alpine and checks our tar extractor against GNU tar):
`SAQR_INTEGRATION=1 SAQR_REF_ROOTFS=/path/to/gnu-tar-extracted ./gradlew testDebugUnitTest`.

### Architecture
```
ui/        Compose screens: Onboarding, Home, Settings, IDE (Agent · Files · Terminal · Git · Preview)
ui/theme   "Falcon" design system (colors, type, spacing, radius, motion; dark + light)
ui/icons   Lucide-style icon set, file-type glyphs, provider monograms
ui/chat    Agent chat: tool rows, approval/notice/review cards, composer, Markdown
agent/     Providers, LlmClient (OpenAI-compatible + Anthropic SSE streaming), Tools,
           AgentSession (loop, approvals, team review, compaction, loop guard), SafetyPolicy, Diff
ondevice/  OnDeviceBuild (proot Debian + JDK 17 + Android SDK, on-device assembleDebug)
terminal/  LinuxEnv (Alpine install + proot command line), ShellSession, TarExtractor
git/       GitService (JGit + GitHub REST)
data/      SecureStore (Keystore), SettingsStore, ProjectRepository + templates
editor/    SyntaxHighlighter
```

### Third-party
See [NOTICE.md](NOTICE.md). proot (GPL-2.0) and talloc (LGPL-3.0) binaries come from the Termux packages repository; source: <https://github.com/termux/proot>, <https://www.samba.org/ftp/talloc/>.
