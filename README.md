# yeex — كل الملفات المعدّلة/الجديدة (تجميع كامل للجلسة)

فك ضغط هذا الملف واستبدل نفس المسارات داخل مشروعك (`yeex-main-fixed`). 25 ملفًا بالمجموع.

## ملفات جديدة كليًا (7)
- `app/src/main/java/com/yeex/dlof/ui/settings/SettingsScreen.kt` — شاشة "الإعدادات والخصوصية" الكاملة
- `app/src/main/java/com/yeex/dlof/data/repository/BlockRepository.kt` — حظر/إلغاء حظر الحسابات
- `app/src/main/java/com/yeex/dlof/data/repository/ReportRepository.kt` — إرسال البلاغات
- `app/src/main/java/com/yeex/dlof/util/SettingsPrefsStore.kt` — المظهر، تشغيل الفيديو تلقائيًا، حجم الخط، الإشعارات
- `app/src/main/java/com/yeex/dlof/util/MutedWordsStore.kt` — الكلمات المكتومة
- `app/src/main/java/com/yeex/dlof/util/CacheUtil.kt` — حجم/مسح ذاكرة التخزين المؤقت
- `app/src/main/java/com/yeex/dlof/util/DataExportUtil.kt` — تنزيل بياناتي + تشخيص "الإبلاغ عن مشكلة"

## ملفات معدّلة (18)
- `app/build.gradle.kts` — تفعيل `buildConfig = true`
- `firebase/database.rules.json` — تحقق للحقول الجديدة + عقدة `blocks/{uid}` الخاصة
- `MainActivity.kt` — تطبيق المظهر وحجم الخط
- `navigation/NavGraph.kt` — مسار `settings`
- `data/model/User.kt` — `isPrivateAccount`، `commentPrivacy`
- `data/repository/UserRepository.kt` — `updatePrivacy`، `updateCommentPrivacy`
- `data/repository/AuthRepository.kt` — `changePassword`، `deleteAccount`
- `util/BusinessCategory.kt` — **34 فئة حساب** بدل 6 الأصلية (28 فئة جديدة، أُضيفت على دفعتين)
- `ui/profile/ProfileScreen.kt` — زر "الإعدادات والخصوصية"
- `ui/theme/Theme.kt` — دعم `fontScale`
- `ui/components/ParagraphCard.kt` — إبلاغ حقيقي، حظر من قائمة الفقرة، احترام إعداد التشغيل التلقائي
- `ui/feed/FeedViewModel.kt` — إخفاء فقرات المحظورين + `blockAuthor()`
- `ui/feed/FeedScreen.kt` — ربط `onBlockAuthor`
- `ui/comments/CommentsSheet.kt` — إخفاء تعليقات المحظورين + الكلمات المكتومة
- `res/values/strings.xml`، `values-ar`، `values-en`، `values-es` — كل النصوص الجديدة

## ملخص المزايا
**صفحة الإعدادات والخصوصية:** تعديل الحساب، تغيير كلمة المرور، حساب خاص، من يمكنه
التعليق، الحسابات المحظورة، الكلمات المكتومة، إشعارات (إعجابات/تعليقات/متابعون/غرف)،
تشغيل الفيديو تلقائيًا، مسح ذاكرة التخزين المؤقت، تنزيل بياناتي، حجم الخط، المظهر
(فاتح/داكن/تلقائي)، اللغة، المساعدة والدعم (majdsaadi10096@gmail.com)، الإبلاغ عن
مشكلة، الشروط، سياسة الخصوصية، تسجيل الخروج، حذف الحساب نهائيًا.

**حظر وإبلاغ حقيقيان:** من قائمة أي فقرة مباشرة — يخفي المحتوى فورًا من الفيد
والتعليقات، والإبلاغ يكتب فعليًا إلى `/reports`.

**34 فئة حساب** بدل 6: شخصية عامة، شركة، متجر، قناة تلفاز، إعلام، فنان، موسيقي،
مصوّر، كاتب، رياضي، معلّم، أخصائي صحي، مطعم/مقهى، منظمة غير ربحية، لاعب ألعاب،
عقارات، تقنية، مجتمع، جهة حكومية، أزياء وجمال، سفر وسياحة، سيارات، مالية ومصرفية،
قانوني ومحاماة، زراعة، بناء ومقاولات، لياقة بدنية وعافية، حيوانات أليفة، تنظيم
فعاليات، منظمة دينية، صحفي/مراسل، كوميدي/مسلٍّ، تصميم وعمارة، أخرى.

## ⚠️ لا تنسَ
انشر `firebase/database.rules.json` المحدّث على Firebase (Realtime Database → Rules).
لا حاجة لأي تعديل على القواعد بخصوص الفئات الجديدة — حقل `businessCategory` يتحقق
فقط من طول النص، بدون قائمة ثابتة.
