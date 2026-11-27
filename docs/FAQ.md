# Saqr Code FAQ · الأسئلة الشائعة

## Where do my API keys go? · وين تروح مفاتيحي؟
Keys stay on your phone in encrypted storage. Saqr sends them only to the model provider you chose. See docs/PRIVACY.md.
المفاتيح تبقى على جوالك مشفّرة، وتُرسل فقط لمزوّد النموذج الذي اخترته.

## Does it work offline? · هل يعمل بلا إنترنت؟
The editor, files, git and terminal work offline. The agent needs a connection to reach your model.
المحرر والملفات وgit والطرفية تعمل بلا إنترنت. الوكيل يحتاج اتصالًا بالنموذج.

## Can the agent break my project? · هل يخرّب الوكيل مشروعي؟
Risky steps (deleting, pushing, running unknown commands) ask first. Each turn is saved so you can stop it; work so far is kept. Use git to roll back.
الخطوات الحساسة تطلب موافقتك أولًا، ويمكنك إيقاف أي دورة، وgit يرجعك لأي نسخة.

## What is the terminal? · ما الطرفية؟
A root-less Alpine Linux on your phone. Install Node, Python or Git with `apk add`.
لينكس Alpine بلا Root؛ ثبّت Node وPython وGit بالأمر `apk add`.

## How do I sync between devices? · كيف أزامن بين أجهزتي؟
Settings, Sync: point it to your own private Git repo. Data is encrypted before upload.
الإعدادات ثم المزامنة: اربطها بمستودعك الخاص، والبيانات مشفّرة قبل الرفع.

## The agent keeps failing a step. What now? · الوكيل يفشل في خطوة
Error cards explain the cause in plain words. Try a smaller request, switch the model, or open the diagnostics digest from the card.
بطاقات الخطأ تشرح السبب بكلمات بسيطة. جرّب طلبًا أصغر أو غيّر النموذج.

## Which build do I install? · أي نسخة أثبّت؟
Releases on GitHub are debug-signed test builds, versions 0.1.x. Install over the previous one; your data is kept.
