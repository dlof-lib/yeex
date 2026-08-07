# yeex

منصة نشر "فقرات" (نصوص/صور/فيديو قصير) مبنية بـ **Kotlin + Jetpack Compose + Firebase**
(المشروع مربوط فعلياً بمشروعك على Firebase: `yeex-90774`).

---

## ⚠️ اقرأ هذا أولاً — حدود ما تم إنجازه

هذا الكود **لم يُبنى (compile) أو يُختبر فعلياً** — بيئة التنفيذ هنا لا تملك Android SDK ولا
اتصال إنترنت لتحميل مكتبات Gradle. الكود مكتوب بعناية وبأسلوب قياسي (idiomatic Kotlin/Compose)
لكن من المتوقع وجود أخطاء بسيطة عند أول بناء فعلي في Android Studio — راجع قسم "خطوات التشغيل".

**ميزات مبنية بالكامل ووظيفية:**
- تسجيل حساب / دخول بمعرف وكلمة سر (بدون بريد إلكتروني ظاهر للمستخدم)
- التحقق من صيغة المعرف (بدون `_ - ~` أو أحرف كبيرة، يدعم نقطة وأرقام عربية/إنجليزية)
- نشر فقرة (نص/صورة/فيديو) بترميز Base64 مباشرة في Realtime Database
- حذف تلقائي بعد 24 ساعة (فلترة عند القراءة + قاعدة تحقق عند الكتابة)
- فيد بالتمرير يمين/يسار (HorizontalPager) لفقرات مربعة الشكل
- إعجاب / لم يعجبني / تعليقات / إعادة نشر داخل غرفة مع تعليق
- نظام المتابعة tek / teker / teking
- غرف عامة/خاصة بسيرة وروابط تواصل ورقم هاتف اختياري
- حاويات تخصيص (TEX-W) وبحث بصيغة `@container.name[].me`
- طلب توثيق (العلامة القرمزية) بإدخال أعداد متابعين من منصات أخرى
- 3 لغات (عربي افتراضي، إنجليزي، إسباني) عبر `strings.xml`
- علامة مائية "yeex" على الصور
- قواعد أمان Firebase كاملة (`firebase/database.rules.json`)
- سكربت لإنشاء حساب **yeex.open** الرسمي الموثّق

**تحديثات هذه الجولة:**
- تعليقات وإعادة النشر أصبحتا شاشتين حقيقيتين ومربوطتين فعلياً في `NavGraph.kt`
  (`CommentsScreen`, `RepostScreen`)، وحالة "أعجبني/لم يعجبني" لكل مستخدم مبنية بالكامل
  عبر `Reaction` في `ParagraphRepository` + `FeedViewModel`.
- **قسم النشر أصبح نافذة منبثقة (ModalBottomSheet)**: الضغط على زر "+" في الفيد يفتح
  `CreateParagraphScreen` كـ bottom sheet فوق نفس الشاشة بدل الانتقال لصفحة كاملة.
- **قسم الحساب احترافي + منبثق**: `ProfileScreen` أصبح يعرض الأيقونة/الاسم/السيرة/الإحصائيات
  بتصميم أوضح، وزر تعديل (✎) يفتح `EditAccountSheet` (bottom sheet) لتغيير **أيقونة الحساب**
  (صورة يتم ترميزها Base64 وتخزينها في `/users/{uid}/profileIconUrl`)، الاسم الظاهر، والسيرة.
- **صفحة "لا يوجد إنترنت"**: `NoInternetScreen` + `NetworkUtil` (مراقبة حية عبر
  `ConnectivityManager`) — `MainActivity` يعرضها تلقائياً بدل كل التطبيق عند فقدان الاتصال،
  وتعود الشاشة الطبيعية فوراً عند عودة الإنترنت.

**ميزات مبنية جزئياً (Scaffolded) — تحتاج عمل إضافي:**
- **علامة مائية على الفيديو**: `WatermarkUtil` يعمل فقط على الصور/الغلاف. دمج علامة على كل
  فريمات الفيديو يحتاج مكتبة إعادة ترميز مثل `ffmpeg-kit` (ثقيلة الحجم، غير مضافة افتراضياً).
- **تصدير PDF بعلامة مائية**: لم يُبنَ بعد؛ الفكرة موثّقة في `WatermarkUtil`.
- **البحث عن المعرفات/الغرف** (غير `@container...`): الشاشة تدعم صيغة الحاوية فقط حالياً.
- **إعادة التحقق التلقائي من 20 ألف متابع خارجي**: لا يمكن التحقق برمجياً من أرقام منصات
  أخرى بدون استخدام APIs تلك المنصات (تتطلب مفاتيح واتفاقيات منفصلة). النظام الحالي:
  المستخدم يُدخل الرقم بنفسه ← يُعلَّم الطلب "مؤهل تلقائياً" إذا تجاوز 20 ألف ← **مراجعة بشرية**
  من فريق yeex.open تمنح العلامة فعلياً.

⚠️ ملاحظة تُخصّ هذه الجولة تحديدًا: لم تُشغَّل هذه التعديلات فعلياً عبر مُصرّف Kotlin/Gradle
(نفس القيد الوارد أعلاه — لا Android SDK هنا)، فراجعها في Android Studio قبل الرفع النهائي.

---

## البنية

```
yeex-android/
├── app/                          # التطبيق (Kotlin/Compose)
│   ├── google-services.json      # نسخة من ملفك، مربوط بمشروع yeex-90774
│   └── src/main/java/com/yeex/dlof/
│       ├── data/model/           # User, Paragraph, Room, Comment, Container
│       ├── data/repository/      # كل التعامل مع Firebase
│       ├── util/                 # التحقق من المعرف، الترتيب، الترميز، العلامة المائية
│       ├── ui/                   # الشاشات (auth, feed, create, room, profile, search, verify)
│       └── navigation/NavGraph.kt
├── firebase/database.rules.json  # قواعد أمان Realtime Database — ارفعها لمشروعك
├── admin/                        # سكربت إنشاء حساب yeex.open (Node, يُشغَّل مرة واحدة يدوياً)
└── README.md
```

---

## لماذا Firebase مجاني بالكامل؟

- **Authentication** (بريد/كلمة سر): مجاني بلا حدود عملياً على Spark plan.
- **Realtime Database**: مجاني حتى 1GB تخزين و10GB/شهر نقل بيانات — كافٍ للبداية.
  لهذا خُزّنت الصور/الفيديو كـ Base64 داخل RTDB مباشرة بدل **Cloud Storage** (الذي يتطلب
  أحياناً ترقية Blaze لبعض الاستخدامات)، تنفيذاً لطلبك بالتحديد.
- **لا يوجد Cloud Functions** في هذا الإصدار: الحذف بعد 24 ساعة نُفِّذ بفلترة على العميل
  (client-side) بدل مهمة مجدولة على السيرفر، لأن Cloud Functions المجدولة (Scheduled)
  تتطلب خطة Blaze (حتى لو الاستخدام ضمن الحد المجاني). **البديل المجاني الكامل يعني أن
  الفقرة المنتهية تختفي من كل الأجهزة فور القراءة، لكنها تبقى فعلياً في القاعدة إلى أن
  يحذفها أحد** — إذا احتجت حذفاً فعلياً من التخزين لاحقاً، الحل: ترقية لـ Blaze وإضافة
  Cloud Function مجدولة (سأجهزها متى احتجتها، تكلفتها عملياً صفر ضمن الحد المجاني للتنفيذ،
  فقط يتطلب بطاقة مسجلة على الحساب).

---

## خطوات التشغيل الفعلي

1. افتح المجلد في **Android Studio** (Koala أو أحدث).
2. تأكد من وجود `app/google-services.json` (موجود مسبقاً في هذه الحزمة).
3. من Firebase Console → مشروعك `yeex-90774` → Realtime Database → Rules:
   الصق محتوى `firebase/database.rules.json` وانشره.
4. من Firebase Console → Authentication → Sign-in method: فعّل **Email/Password**
   (نستخدمه داخلياً خلف "المعرف" — راجع `UsernameValidator.toPseudoEmail`).
5. Sync Gradle، ثم Run على جهاز/محاكي.
6. لإنشاء حساب **yeex.open** الرسمي: اتبع التعليمات في أعلى `admin/seedAdmin.js`.

---

## رفعه على GitHub

```bash
cd yeex-android
git init
git add .
git commit -m "yeex: initial scaffold"
git branch -M main
git remote add origin https://github.com/<اسمك>/yeex.git
git push -u origin main
```

`.gitignore` يستثني تلقائياً `local.properties`، مجلدات البناء، و
`admin/serviceAccountKey.json` (لا تُرفع أبداً — تمنح صلاحية كاملة على مشروعك).

---

## عن ملف APK

لا يمكن إنتاج ملف APK قابل للتثبيت من هذه البيئة (لا تتوفر Android SDK/Gradle حقيقية هنا).
لكن المشروع فيه **GitHub Actions** يبنيه لك تلقائياً بمجرد الرفع لـ GitHub — لا تحتاج
Android Studio أصلاً لهذه الخطوة:

### 1) بناء تلقائي عند كل رفع (`.github/workflows/build-apk.yml`)
كل `git push` لفرع `main` يشغّل بناء تلقائي على سيرفرات GitHub. لتحميل الـ APK الناتج:
**GitHub → تبويب Actions → افتح آخر تشغيل ناجح → Artifacts → `yeex-debug-apk`**.

### 2) إصدار رسمي بضغطة وسم (`.github/workflows/release-apk.yml`)
```bash
git tag v0.1.0
git push origin v0.1.0
```
هذا يبني الـ APK ويرفعه تلقائياً لصفحة **Releases** في المستودع — رابط تحميل مباشر
لأي شخص بدون فتح تبويب Actions.

### ملاحظة مهمة
لا يوجد ملف `gradle/wrapper/gradle-wrapper.jar` في هذه الحزمة (ملف ثنائي/binary، تعذّر
توليده في بيئة بلا اتصال إنترنت). لذلك الـ workflows تستخدم Gradle مُثبَّت مباشرة على
GitHub Actions بدل `./gradlew`. عند أول فتح للمشروع في Android Studio، شغّل مرة واحدة:
```bash
gradle wrapper --gradle-version 8.7
```
ثم ارفع مجلد `gradle/wrapper/` الناتج لـ GitHub — بعدها تقدر تستخدم `./gradlew` محلياً أيضاً.

