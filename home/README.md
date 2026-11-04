# منزلي — Haval Home

تطبيق أندرويد بملء الشاشة لشاشة الهافال للتحكم بالبيت عبر Home Assistant.

- الواجهة في `app/src/main/assets/haval-home.html` (ملف HTML واحد، يعمل أيضًا من مجلد `www` في Home Assistant).
- يعرضها التطبيق على عنوان الخادم (الافتراضي `https://abusamiha.fun`) مع تقديم الملف من داخل التطبيق، فيعمل تسجيل الدخول العادي لـ Home Assistant.
- يُغيَّر عنوان الخادم من الترس داخل التطبيق.
- البناء: `.github/workflows/build-home.yml` ينشر إصدار `home-<n>` بالملف `HavalHome-1.0.<n>.apk`.
