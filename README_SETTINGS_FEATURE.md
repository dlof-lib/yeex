# صفحة "الإعدادات والخصوصية" — ملفات معدّلة/جديدة فقط

فك ضغط هذا الملف واستبدل نفس المسارات داخل مشروعك (`yeex-main-fixed`).

## ملفات جديدة (3)
- `app/src/main/java/com/yeex/dlof/ui/settings/SettingsScreen.kt` — الشاشة نفسها
- `app/src/main/java/com/yeex/dlof/data/repository/BlockRepository.kt` — الحسابات المحظورة
- `app/src/main/java/com/yeex/dlof/util/SettingsPrefsStore.kt` — تفضيلات محلية (المظهر + الإشعارات)

## ملفات معدّلة (11)
- `app/src/main/java/com/yeex/dlof/MainActivity.kt` — تطبيق اختيار المظهر (فاتح/داكن/تلقائي)
- `app/src/main/java/com/yeex/dlof/navigation/NavGraph.kt` — إضافة مسار `settings`
- `app/src/main/java/com/yeex/dlof/ui/profile/ProfileScreen.kt` — زر "الإعدادات والخصوصية" بقائمة الحساب
- `app/src/main/java/com/yeex/dlof/data/model/User.kt` — حقلا `isPrivateAccount` و`commentPrivacy`
- `app/src/main/java/com/yeex/dlof/data/repository/UserRepository.kt` — `updatePrivacy` / `updateCommentPrivacy`
- `app/src/main/java/com/yeex/dlof/data/repository/AuthRepository.kt` — `changePassword` / `deleteAccount`
- `app/src/main/res/values/strings.xml`, `values-ar`, `values-en`, `values-es` — كل نصوص الشاشة الجديدة
- `firebase/database.rules.json` — تحقّق للحقول الجديدة + عقدة خاصة `blocks/{uid}` (اقرأ/اكتب لصاحبها فقط)

## أقسام الشاشة
الحساب (تعديل الملف، تغيير كلمة المرور) · الخصوصية (حساب خاص، من يعلّق، الحسابات المحظورة)
· الإشعارات (إعجابات/تعليقات/متابعون/غرف) · المظهر واللغة · حول ودعم (نسخة التطبيق، الشروط، سياسة الخصوصية)
· إدارة الحساب (تسجيل الخروج، حذف الحساب نهائيًا).

## ⚠️ لا تنسَ
انشر قواعد `firebase/database.rules.json` المحدّثة على مشروع Firebase (Realtime Database → Rules)
حتى يعمل تبديل الخصوصية وحظر الحسابات — بدون النشر ستفشل هذه الكتابات بصلاحية مرفوضة.
