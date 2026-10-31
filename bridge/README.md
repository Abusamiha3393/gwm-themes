# جسر الهافال — HAVAL Phone Bridge

تطبيق واحد على شاشة الهافال يستقبل المواقع والروابط والنصوص والصور والملفات من الجوال عبر الشبكة المحلية، دون ADB لكل عملية.

| الوحدة | الدور |
| --- | --- |
| `car` | التطبيق الأساسي، يُثبّت على الشاشة (`com.sahra.bridge.car`) |
| `phone` | تطبيق Android اختياري، يُبنى ويُضمَّن داخل `car` وتقدّمه الشاشة على `/app.apk` |

## التثبيت على الشاشة (مرة واحدة)

```
adb install HavalBridge-0.2.N.apk
adb shell appops set com.sahra.bridge.car SYSTEM_ALERT_WINDOW allow
adb shell appops set com.sahra.bridge.car REQUEST_INSTALL_PACKAGES allow
adb shell dumpsys deviceidle whitelist +com.sahra.bridge.car
adb shell am start -n com.sahra.bridge.car/.MainActivity
```

## الاستخدام

1. صِل الشاشة بنقطة اتصال الجوال.
2. امسح رمز QR الظاهر على الشاشة بكاميرا الجوال. تفتح صفحة مربوطة بالسيارة فورًا (المفتاح لمرة واحدة، صالح 10 دقائق).
3. من الصفحة: أرسل رابطًا أو موقعًا أو نصًا، أو صورًا وملفات. تعمل على iPhone وAndroid دون تثبيت.
4. اختياري على Android: «ثبّت تطبيق الجوال» ثم «افتح التطبيق واربطه» (`havalbridge://pair?…`)، فيظهر «أرسل إلى الهافال» في قائمة المشاركة.

طرق احتياطية في تطبيق الجوال: البحث في الشبكة مع «سماح» على الشاشة، أو الرمز من 6 أرقام.

## البروتوكول (الإصدار 1)

خادم HTTP على المنفذ 47800 في الشاشة، يُعلن عن نفسه بـ mDNS `_havalbridge._tcp`.

| الطلب | الغرض |
| --- | --- |
| `GET /` و `/p/{key}` | صفحة الويب (assets/web/index.html) |
| `POST /v1/web/claim` | `{key, name}` ← `{token}`؛ مفتاح QR لمرة واحدة |
| `GET /app.apk` | تطبيق الجوال المضمَّن |
| `GET /v1/info` | الاسم والمعرّف، بلا مصادقة |
| `POST /v1/pair/request` | `{phone}` ← `{request}`؛ تظهر على الشاشة «سماح / رفض» لمدة 60 ثانية |
| `GET /v1/pair/request/{id}` | `{status}`، ومع `approved` يأتي `token` مرة واحدة |
| `POST /v1/pair` | احتياطي: `{code, phone}` ← `{token}`؛ الرمز صالح 5 دقائق ولمرة واحدة، و5 أخطاء تقفل 10 دقائق |
| `GET /v1/ping` | فحص الرمز |
| `POST /v1/items` | `{type: geo\|link\|text\|share, payload}` مع `Authorization: Bearer`؛ `share` نص خام تحلّله الشاشة |
| `POST /v1/uploads` | `{name, size, mime, sha256}` ← `{id, received, chunk}`؛ المعرّف ثابت لنفس الملف فيُستأنف. الصفحة ترسل sha256 فارغًا |
| `PUT /v1/uploads/{id}` | جزء حتى 4 MB مع `X-Offset`؛ عند الاكتمال يُتحقق من SHA-256 |
| `GET /v1/uploads/{id}` | ما وصل حتى الآن |

الملفات المستلمة تُحفظ في مجلد التطبيق وتُفتح لتطبيقات الشاشة الأخرى عبر `content://com.sahra.bridge.car.files/…`.

بلا TLS حتى الآن: الاتصال على الشبكة المحلية فقط ومحمي برمز جلسة لكل جوال.
