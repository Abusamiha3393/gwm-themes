package com.sahra.bridge.phone

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

/**
 * يحوّل ما يشاركه المستخدم إلى عنصر جاهز للإرسال: موقع (geo) أو رابط (link) أو نص (text).
 * الروابط المختصرة مثل maps.app.goo.gl تُفك هنا على الجوال لأن لديه إنترنت غالبًا،
 * فيصل الهافال إحداثيات جاهزة حتى بلا اتصال.
 */
object MapLinks {
    private val URL_RE = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val NUM = """(-?\d{1,3}\.\d+)"""
    private val COORD_PATTERNS = listOf(
        Regex("""!3d$NUM!4d$NUM"""),
        Regex("""[?&](?:q|query|ll|daddr|destination|sll|center|saddr)=(?:loc:)?$NUM\s*,\s*\+?$NUM"""),
        Regex("""ll\.$NUM,$NUM"""),
        Regex("""/(?:search|place|dir)/(?:[^/]*/)*?$NUM,\s*\+?$NUM"""),
        Regex("""@$NUM,$NUM"""),
    )
    private val PLAIN_COORDS = Regex("""^\s*$NUM\s*[,،]\s*$NUM\s*$""")
    private val MAP_HOSTS = listOf(
        "maps.app.goo.gl", "goo.gl", "maps.google.", "google.", "maps.apple.com", "waze.com",
        "openstreetmap.org", "here.com", "bing.com",
    )

    /** يعيد {type, payload}. قد يتصل بالإنترنت لفك الروابط المختصرة، فيُستدعى من خيط خلفي. */
    fun build(text: String, subject: String? = null): JSONObject {
        val trimmed = text.trim()
        PLAIN_COORDS.matchEntire(trimmed)?.let { m ->
            return geo(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), "", "", "")
        }
        val url = URL_RE.find(trimmed)?.value?.trimEnd('.', ',', ')', '،')
        if (url == null) {
            return JSONObject().put("type", "text").put("payload", JSONObject().put("text", trimmed))
        }
        // النص المحيط بالرابط غالبًا هو اسم المكان (Google Maps وواتساب يضعانه قبل الرابط)
        val around = trimmed.replace(url, " ").lines().map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ").take(200).ifEmpty { subject?.trim().orEmpty() }

        if (isMapLink(url)) {
            val finalUrl = if (needsExpand(url)) expand(url) else url
            val decoded = decode(finalUrl)
            val coords = coords(decoded)
            val place = placeName(decoded)
            val label = around.ifEmpty { place }
            if (coords != null) return geo(coords.first, coords.second, label, place, url)
            val query = queryText(decoded).ifEmpty { place }.ifEmpty { around }
            if (query.isNotEmpty()) {
                return JSONObject().put("type", "geo").put(
                    "payload", JSONObject().put("query", query).put("label", label.ifEmpty { query }).put("sourceUrl", url),
                )
            }
        }
        return JSONObject().put("type", "link").put("payload", JSONObject().put("url", url).put("title", around))
    }

    private fun geo(lat: Double, lng: Double, label: String, query: String, src: String) =
        JSONObject().put("type", "geo").put(
            "payload",
            JSONObject().put("lat", lat).put("lng", lng).put("label", label).put("query", query).put("sourceUrl", src),
        )

    fun isMapLink(url: String): Boolean {
        val host = hostOf(url)
        val path = url.substringAfter(host, "").lowercase(Locale.US)
        if (host == "goo.gl") return path.startsWith("/maps")
        if (host.startsWith("google.") || host.contains(".google.")) {
            return host.startsWith("maps.") || path.startsWith("/maps")
        }
        return MAP_HOSTS.any { host == it || host.endsWith(".$it") || (it.endsWith(".") && host.startsWith(it)) }
    }

    private fun needsExpand(url: String): Boolean {
        val host = hostOf(url)
        return host == "maps.app.goo.gl" || host == "goo.gl" || host == "g.co" || host.endsWith("page.link")
    }

    private fun hostOf(url: String): String = try {
        URL(url).host.lowercase(Locale.US).removePrefix("www.")
    } catch (e: Exception) {
        ""
    }

    /** يتبع التحويلات يدويًا حتى 6 مرات ويعيد آخر رابط. عند الفشل يعيد الرابط الأصلي. */
    fun expand(start: String): String {
        var current = start
        repeat(6) {
            val c = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return current
            }
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) HavalBridge")
                val code = c.responseCode
                val loc = c.getHeaderField("Location")
                if (code !in 300..399 || loc.isNullOrEmpty()) return current
                current = URL(URL(current), loc).toString()
                // صفحة الموافقة من Google تحمل الرابط الحقيقي في continue
                if (hostOf(current).startsWith("consent.")) {
                    Regex("""[?&]continue=([^&]+)""").find(current)?.let { current = decode(it.groupValues[1]) }
                }
            } catch (e: Exception) {
                return current
            } finally {
                c.disconnect()
            }
        }
        return current
    }

    fun coords(decoded: String): Pair<Double, Double>? {
        for (re in COORD_PATTERNS) {
            val m = re.find(decoded) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lng = m.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) return lat to lng
        }
        return null
    }

    /** اسم المكان من روابط Google ‎/maps/place/اسم+المكان/ */
    private fun placeName(decoded: String): String {
        val m = Regex("""/maps/place/([^/@?]+)""").find(decoded) ?: return ""
        val name = m.groupValues[1].replace('+', ' ').trim()
        return if (coords("/search/$name") != null) "" else name.take(200)
    }

    private fun queryText(decoded: String): String {
        val m = Regex("""[?&](?:q|query|daddr|destination|address)=([^&#]+)""").find(decoded) ?: return ""
        val v = m.groupValues[1].replace('+', ' ').trim()
        return if (coords("?q=$v") != null) "" else v.take(300)
    }

    private fun decode(s: String): String = try {
        // نحافظ على + كما هي؛ تُعالج في كل حقل على حدة
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }
}
