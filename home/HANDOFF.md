# منزلي (Haval Home) — ملف تسليم لاستكمال التطوير

> انسخ هذا الملف كاملًا لأي مساعد برمجي (مثل ChatGPT) مع ملفي `haval-home.html` و`MainActivity.kt`، ثم اطلب التعديل المطلوب.

## 1. ما هو المشروع
تطبيق أندرويد بملء الشاشة يعمل على شاشة سيارة **هافال (GWM)** للتحكم بالبيت عبر **Home Assistant**.
- يعرض الغرف والأجهزة (إضاءة، مفاتيح، مراوح، مكيفات، أقفال، باب كراج، حساسات، مشغلات وسائط، مشاهد وسكربتات).
- واجهة عربية RTL داكنة، أزرار كبيرة مناسبة للقيادة، ضغط مطوّل للتأكيد على الكراج والأقفال.
- خادم Home Assistant للمستخدم: `https://abusamiha.fun` (عبر Cloudflare Tunnel).
- اسم التطبيق: «منزلي»، الحزمة `com.sahra.home`.

## 2. المستودع
- GitHub: `Abusamiha3393/gwm-themes` (فرع `main`). المشروع داخل المجلد `home/`.
- المستودع يحتوي مشاريع أخرى لا علاقة لها (`app/` = Sahra Dash، `bridge/`، `split/`، `sahra/`) — **لا تعدّلها**.

```
home/
├─ settings.gradle.kts, build.gradle.kts, gradle.properties
├─ app/build.gradle.kts            # namespace/applicationId com.sahra.home, versionCode = GITHUB_RUN_NUMBER
└─ app/src/main/
   ├─ AndroidManifest.xml          # MainActivity + InstallResultReceiver
   ├─ assets/haval-home.html       # الواجهة كاملة (HTML+CSS+JS في ملف واحد)
   ├─ java/com/sahra/home/MainActivity.kt   # WebView بملء الشاشة + جسر JS
   ├─ java/com/sahra/home/Updater.kt        # التحديث التلقائي من GitHub Releases
   └─ res/drawable/ic_launcher.xml, res/values/strings.xml
.github/workflows/build-home.yml   # بناء APK ونشر إصدار home-<n>
```

## 3. البناء والإصدار
- لا يوجد Android SDK محليًا؛ **البناء فقط عبر GitHub Actions** (`build-home.yml`) عند أي push يمس `home/**`.
- الأدوات: Kotlin 1.9.24، AGP 8.5.2، Gradle 8.9 (بدون wrapper)، JDK 17، compileSdk/targetSdk 34، minSdk 26.
- **بدون AndroidX**: Activity و Views عادية فقط، الواجهة الأصلية مبنية بالكود. حافظ على ذلك.
- التوقيع: `release.jks` في جذر المستودع (PKCS12، alias `sahra`) وكلمة المرور في السر `KEYSTORE_PASSWORD`. لا تغيّر المفتاح وإلا لن تُثبَّت التحديثات فوق النسخة القديمة.
- كل بناء ينشر إصدارًا بوسم `home-<run_number>` وملف `HavalHome-1.0.<n>.apk` مع `make_latest: false` (حتى لا يلتبس مع تحديثات Sahra Dash).
- قبل الدفع راجع Kotlin بعناية: smart casts بعد `?:`، تعارض أسماء داخل `apply {}`، واجهات أعلى من API 26 بدون فحص `Build.VERSION.SDK_INT`، والـ imports.

## 4. كيف يعمل التطبيق الأصلي (MainActivity.kt)
- WebView بملء الشاشة (immersive)، الشاشة تبقى مضاءة، خلفية `#0A0C0F`.
- يحمّل الرابط `https://abusamiha.fun/local/haval-home.html`، لكن **يعترض الطلب** (`shouldInterceptRequest`) ويقدّم الملف من `assets`. النتيجة: الواجهة تفتح فورًا دون إنترنت، و**origin الصفحة هو نطاق الخادم** فيعمل تسجيل الدخول العادي و`fetch('/auth/token')` بلا CORS.
- عنوان الخادم محفوظ في SharedPreferences (`home` → `server`)، الافتراضي `https://abusamiha.fun`.
- جسر JavaScript باسم `window.HavalApp`:
  - `server()` → عنوان الخادم
  - `setServer(url)` → يحفظ ويعيد التحميل
  - `buzz()` → اهتزاز خفيف
  - `version()` → رقم الإصدار
  - `save(key, value)` / `load(key)` → تخزين دائم (SharedPreferences بمفاتيح `js_<key>`، يُكتب بـ commit)
  - `checkUpdate()` → فحص يدوي للتحديث
- زر الرجوع: يرجع داخل WebView (مثلًا من صفحة تسجيل الدخول) وإلا يخرج.

## 5. التحديث التلقائي (Updater.kt)
- يقرأ `https://api.github.com/repos/Abusamiha3393/gwm-themes/releases?per_page=40` ويختار أعلى رقم لوسم يبدأ بـ `home-`.
- يقارن بـ versionCode المثبت. الفحص عند `onResume` كل 3 ساعات على الأكثر، أو يدويًا من الإعدادات.
- عند التوفر: نافذة AlertDialog «يتوفر تحديث» → تنزيل مع نسبة تقدم → `PackageInstaller` → `InstallResultReceiver` يعرض تأكيد النظام.
- إذا لم يُسمح بتثبيت التطبيقات المجهولة يفتح `ACTION_MANAGE_UNKNOWN_APP_SOURCES` ويكمل عند الرجوع.

## 6. الواجهة (assets/haval-home.html)
ملف واحد بلا مكتبات خارجية. أهم الأجزاء:

**التخطيطات (CSS media queries، كلها RTL، المقاسات بوحدة `--u`):**
- كامل الشاشة 1920×720: شريط الغرف يمين + عمود رئيسي (ساعة، بطاقة الكراج، مشاهد، وسائط) + شبكة الأجهزة.
- نصف الشاشة (16/9 > النسبة ≥ 1/1، مثل 960×720): شريط أيقونات + شبكة.
- ثلث الشاشة (1/1 > النسبة ≥ 7/10، مثل 640×720): الأساسيات فقط (الكراج والمشاهد).
- جوال طولي (< 7/10): كل شيء عموديًا.

**الهوية البصرية:** خلفية `#0A0C0F`، لوحات `#12161B`/`#1B2129`، لون أساسي كهرماني `#F2A33A`، ثانوي فيروزي `#3FD0C9` (للمكيف والمراوح والمشاهد)، أيقونات خطية SVG أصلية. **لا تستخدم شعارات أو أسماء علامات تجارية للسيارات.**

**الاتصال بـ Home Assistant (WebSocket):**
- `HA` كائن الاتصال: `auth` → `get_states` + `config/area_registry/list` + `config/entity_registry/list` + `config/device_registry/list` → `subscribe_events state_changed`. إعادة اتصال بتأخير متزايد.
- تجميع الأجهزة حسب المنطقة (area للكيان أو لجهازه)، وتجاهل الكيانات المخفية/المعطلة/ذات entity_category.
- الخدمات: `toggle` للإضاءة والمفاتيح والمراوح و input_boolean؛ `open_cover/close_cover`؛ `lock/unlock`؛ `climate.turn_on/off`؛ `media_player.media_play_pause/media_next_track`؛ `scene/script.turn_on`؛ `homeassistant.turn_off` لزر «إطفاء الكل».
- إذا فُتحت الصفحة داخل iframe في واجهة Home Assistant (لوحة Webpage) تستخدم اتصال الواجهة الأم مباشرة (`parent.document.querySelector('home-assistant').hass.connection`).

**تسجيل الدخول (مهم):**
1. المستخدم يضغط «تسجيل الدخول» → توجيه إلى `/auth/authorize` بـ `client_id = origin + '/'` و`redirect_uri = نفس الصفحة?auth_callback=1`.
2. عند العودة بـ `code` → `POST /auth/token` (grant_type=authorization_code) → حفظ access/refresh في `LS 'auth'`.
3. بعد أول اتصال ناجح تُنشئ الصفحة **رمزًا طويل الأمد** عبر `auth/long_lived_access_token` (اسمه «منزلي — الهافال <تاريخ>»، مدة 3650 يومًا) وتحفظه في `LS 'token'`. بعدها لا يُطلب الدخول مجددًا.
4. `LS` يكتب في localStorage **وفي** `HavalApp.save` معًا، ويقرأ من localStorage ثم من التطبيق.
5. لا يُحذف تسجيل الدخول إلا إذا ردّ الخادم `400 invalid_grant`. أخطاء الشبكة → إعادة محاولة.
6. بدون أي تسجيل دخول تعمل الصفحة في «وضع العرض» ببيانات تجريبية.

**التفاعل:** نقرة = تشغيل/إطفاء؛ ضغط مطوّل 0.9 ث = فتح/إغلاق الكراج والأقفال (مع شريط تقدم واهتزاز)؛ ضغط مطوّل على إضاءة قابلة للتعتيم = نافذة السطوع.

**التخزين المحلي (مفاتيح LS):** `url`، `token`، `auth`، `garage` (كيان الكراج المختار)، `hideUnavail`، `view` (الغرفة المختارة).

## 7. أفكار مقترحة للخطوات القادمة
- فتح الكراج تلقائيًا عند الاقتراب من البيت (موقع GPS من التطبيق الأصلي + نطاق جغرافي).
- قراءة بيانات السيارة (السرعة) وقفل الأوامر الخطرة أثناء الحركة. Sahra Dash في نفس المستودع يقرأها عبر صلاحيات `com.gwm.permission.VEHICLE_DATA`، فراجع `app/src/main/java/com/sahra/dash/GwmAdapter.kt`.
- مفضلة يختارها المستخدم في الشاشة الرئيسية بدل «الكل».
- كاميرات Home Assistant (صور `camera_proxy`) ولوحة تنبيهات.
- ودجت أو زر عائم سريع للكراج من خارج التطبيق.
- تشغيل تلقائي عند تشغيل السيارة (BOOT_COMPLETED) — اختياري.
- دعم أزرار المقود عبر `onKeyDown`.

## 8. طريقة العمل المطلوبة من المساعد
- عدّل الملفات داخل `home/` فقط، وأعطني الملف كاملًا بعد التعديل (لا مقتطفات) لأرفعه على GitHub.
- حافظ على: ملف HTML واحد بلا مكتبات خارجية، العربية RTL، بدون AndroidX، نفس مفتاح التوقيع، `make_latest: false`.
- أي ميزة جديدة يجب أن تعمل في المقاسات الأربعة (كامل، نصف، ثلث، جوال).
- بعد الرفع، يبني GitHub Actions الإصدار وتظهر رسالة التحديث داخل التطبيق في السيارة تلقائيًا.
