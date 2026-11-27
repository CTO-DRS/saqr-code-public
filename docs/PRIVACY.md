# Privacy and security · الخصوصية والأمان

Draft for store listing. The owner should review it before publishing. · مسودة لصفحة المتجر، تحتاج مراجعة المالك قبل النشر.

## English

**Saqr Code collects no data about you.** There is no Saqr server, no account, no analytics and no crash-reporting SDK in the app.

### What stays on your phone
Projects, chat sessions, memory, settings and build output live in the app's private storage. API keys and Git tokens are stored in Android Keystore-backed encrypted storage and are excluded from backups. Secrets are redacted before anything is written to logs or audit files.

### What leaves your phone, and only when you use the feature
- **Model providers (bring your own key):** the prompt, the relevant project context and tool results are sent directly from your phone to the provider you configured (for example OpenAI, Anthropic, Google, or any compatible endpoint). Their privacy terms apply. Saqr never relays this through its own servers.
- **Git hosts:** clone, push, pull and pull-request actions talk to GitHub, GitLab or Gitea with your token.
- **Web tools:** when the agent searches or fetches a page, the request goes to that site.
- **MCP servers:** only the servers you add.
- **Payments:** subscriptions go through Google Play Billing or Moyasar. Saqr does not see or store card numbers.
- **Voice:** speech recognition uses the Android recognizer on your device or the service your phone is set to use. The microphone is used only while you dictate or hold wake mode on.
- **Downloads:** build toolchains are downloaded from public mirrors and verified by SHA-256.

### Permissions
Internet and network state (providers, Git, downloads); microphone (voice, optional); notifications and foreground service (long agent runs and build progress); boot receiver and battery-optimization exemption (resume interrupted runs, optional); wake lock (keep a run alive).

### Your control
Delete any session or memory entry in the app. Clearing app data or uninstalling removes everything. Safety settings let you require approval for file changes, commands and pushes, and set daily and monthly spend caps.

### Security model
Dangerous commands are blocked or need a confirmation card. Web and MCP output is treated as untrusted data, not instructions. Release builds are signed with a key you control.

### Contact
Open an issue at https://github.com/CTO-DRS/saqr-code-public/issues.

## العربية

**صقر كود لا يجمع أي بيانات عنك.** ما فيه خادم لصقر ولا حساب ولا تحليلات ولا مكتبة تقارير أعطال.

### وش يبقى على جوالك
المشاريع والمحادثات والذاكرة والإعدادات وملفات البناء في مساحة التطبيق الخاصة. مفاتيح الـAPI وتوكنات Git محفوظة بتخزين مشفّر مدعوم بـAndroid Keystore ومستثناة من النسخ الاحتياطي. الأسرار تُحجب قبل كتابة أي سجل.

### وش يطلع من جوالك، فقط حين تستخدم الميزة
- **مزوّدو النماذج (بمفتاحك):** النص وسياق المشروع المطلوب ونتائج الأدوات تروح مباشرة من جوالك للمزوّد اللي ضبطته. تطبق عليه سياسة المزوّد، وما نمرّرها على أي خادم تابع لنا.
- **مضيفو Git:** الاستنساخ والرفع والسحب وطلبات الدمج مع GitHub أو GitLab أو Gitea بتوكنك.
- **أدوات الويب:** البحث أو جلب صفحة يروح للموقع نفسه.
- **خوادم MCP:** فقط اللي تضيفها أنت.
- **المدفوعات:** الاشتراك عبر Google Play أو مويسر. صقر ما يشوف ولا يحفظ أرقام البطاقات.
- **الصوت:** التعرف على الكلام عبر مُعرّف أندرويد في جوالك أو الخدمة المضبوطة عندك. الميكروفون يشتغل فقط وقت الإملاء أو تفعيل وضع الاستدعاء.
- **التنزيلات:** أدوات البناء تنزل من مرايا عامة وتتحقق بـSHA-256.

### الصلاحيات
الإنترنت وحالة الشبكة (المزوّدون وGit والتنزيل)، الميكروفون (الصوت، اختياري)، الإشعارات والخدمة الأمامية (المهام الطويلة وتقدم البناء)، الاستقبال عند الإقلاع واستثناء توفير البطارية (استئناف المهام المقطوعة، اختياري)، قفل التنبيه (إبقاء المهمة شغالة).

### تحكمك
احذف أي جلسة أو عنصر ذاكرة من التطبيق. مسح بيانات التطبيق أو حذفه يمسح كل شيء. إعدادات الأمان تخليك تطلب موافقة على تعديل الملفات والأوامر والرفع، وتحدد سقف صرف يومي وشهري.

### نموذج الأمان
الأوامر الخطرة تُحجب أو تحتاج بطاقة تأكيد. مخرجات الويب وMCP تُعامل كبيانات غير موثوقة لا كتعليمات. إصدارات الإنتاج تُوقَّع بمفتاح تتحكم فيه أنت.

### التواصل
افتح بلاغًا على https://github.com/CTO-DRS/saqr-code-public/issues
