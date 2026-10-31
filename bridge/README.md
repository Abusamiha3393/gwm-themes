# جسر الهافال — HAVAL Phone Bridge

تطبيقان يرسلان المواقع والروابط والنصوص من الجوال إلى شاشة الهافال عبر الشبكة المحلية، دون ADB لكل عملية.

| الوحدة | يُثبّت على | الحزمة |
| --- | --- | --- |
| `car` | شاشة الهافال (مرة واحدة عبر ADB) | `com.sahra.bridge.car` |
| `phone` | جوال Android | `com.sahra.bridge.phone` |

## التثبيت على الشاشة (مرة واحدة)

```
adb install HavalBridge-Car-0.1.N.apk
adb shell appops set com.sahra.bridge.car SYSTEM_ALERT_WINDOW allow
adb shell appops set com.sahra.bridge.car REQUEST_INSTALL_PACKAGES allow
adb shell dumpsys deviceidle whitelist +com.sahra.bridge.car
adb shell am start -n com.sahra.bridge.car/.MainActivity
```

## الاستخدام

1. صِل الشاشة بنقطة اتصال الجوال (أو العكس).
2. في «رفيق الهافال» على الجوال: ابحث عن السيارة، ثم اضغط «سماح» على الشاشة. مرة واحدة فقط؛ بعدها يتصل تلقائيًا.
   (الرمز من 6 أرقام يبقى طريقة احتياطية.)
3. من أي تطبيق: مشاركة ← «أرسل إلى الهافال» — مواقع، روابط، نصوص، صور، فيديو، ملفات، وAPK.

## البروتوكول (الإصدار 1)

خادم HTTP على المنفذ 47800 في الشاشة، يُعلن عن نفسه بـ mDNS `_havalbridge._tcp`.

| الطلب | الغرض |
| --- | --- |
| `GET /v1/info` | الاسم والمعرّف، بلا مصادقة |
| `POST /v1/pair/request` | `{phone}` ← `{request}`؛ تظهر على الشاشة «سماح / رفض» لمدة 60 ثانية |
| `GET /v1/pair/request/{id}` | `{status}`، ومع `approved` يأتي `token` مرة واحدة |
| `POST /v1/pair` | احتياطي: `{code, phone}` ← `{token}`؛ الرمز صالح 5 دقائق ولمرة واحدة، و5 أخطاء تقفل 10 دقائق |
| `GET /v1/ping` | فحص الرمز |
| `POST /v1/items` | `{type: geo\|link\|text, payload}` مع `Authorization: Bearer` |
| `POST /v1/uploads` | `{name, size, mime, sha256}` ← `{id, received, chunk}`؛ المعرّف ثابت لنفس الملف فيُستأنف |
| `PUT /v1/uploads/{id}` | جزء حتى 4 MB مع `X-Offset`؛ عند الاكتمال يُتحقق من SHA-256 |
| `GET /v1/uploads/{id}` | ما وصل حتى الآن |

الملفات المستلمة تُحفظ في مجلد التطبيق وتُفتح لتطبيقات الشاشة الأخرى عبر `content://com.sahra.bridge.car.files/…`.

بلا TLS حتى الآن: الاتصال على الشبكة المحلية فقط ومحمي برمز جلسة لكل جوال.
