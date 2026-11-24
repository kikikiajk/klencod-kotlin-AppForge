# AppForge IDE

تطبيق أندرويد (Kotlin + Compose) يبني تطبيقات Kotlin / Java / Flutter / HTML عبر GitHub Actions.

## كيف تبنيه (من الموبايل فقط)
1. ارفع هذا المجلد كمستودع على GitHub (أو استورد الـ ZIP في Klencod IDE).
2. الـ workflow جاهز في `.github/workflows/build.yml` وراح يطلع APK بالـ Artifacts.
3. نزّل الـ APK وثبّته.

## ربط GitHub
- الطريقة 1 (تلقائية): GitHub > Settings > Developer settings > OAuth Apps > New، فعّل Enable Device Flow،
  انسخ Client ID وحطه بالإعدادات واضغط "ربط GitHub".
- الطريقة 2 (بديلة): "فتح صفحة إنشاء التوكن" والصق التوكن.

## ملاحظات
- مشاريع HTML: تتحول لتطبيق WebView وتنبني عبر GitHub أيضاً (المعاينة محلية).
- مافي gradle wrapper داخل الـ ZIP؛ الـ workflow يستخدم Gradle 8.9 مباشرة.