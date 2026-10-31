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
adb shell dumpsys deviceidle whitelist +com.sahra.bridge.car
adb shell am start -n com.sahra.bridge.car/.MainActivity
```

## الاستخدام

1. صِل الشاشة بنقطة اتصال الجوال (أو العكس).
2. في «رفيق الهافال» على الجوال: ابحث عن السيارة وأدخل الرمز الظاهر على الشاشة.
3. من أي تطبيق: مشاركة ← «أرسل إلى الهافال».

## البروتوكول (الإصدار 1)

خادم HTTP على المنفذ 47800 في الشاشة، يُعلن عن نفسه بـ mDNS `_havalbridge._tcp`.

| الطلب | الغرض |
| --- | --- |
| `GET /v1/info` | الاسم والمعرّف، بلا مصادقة |
| `POST /v1/pair` | `{code, phone}` ← `{token}`؛ الرمز صالح 5 دقائق ولمرة واحدة، و5 أخطاء تقفل 10 دقائق |
| `GET /v1/ping` | فحص الرمز |
| `POST /v1/items` | `{type: geo\|link\|text, payload}` مع `Authorization: Bearer` |

المرحلة الأولى بلا TLS: الاتصال على الشبكة المحلية فقط ومحمي برمز جلسة. التشفير ونقل الملفات والصور في المرحلة الثانية.
