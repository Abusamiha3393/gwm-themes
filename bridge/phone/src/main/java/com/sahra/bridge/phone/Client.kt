package com.sahra.bridge.phone

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** سيارة وُجدت على الشبكة. */
data class Car(val id: String, val name: String, val host: String, val port: Int)

/** خطأ HTTP مع جسم الرد (مثل {received} عند 409). */
class HttpError(val code: Int, val body: JSONObject? = null) : IOException("HTTP $code")

/** عميل بروتوكول الجسر. كل الدوال متزامنة، تُستدعى من خيط خلفي. */
object Client {
    const val PORT = 47800

    @Throws(IOException::class)
    fun info(host: String, port: Int = PORT, timeoutMs: Int = 2500): Car {
        val o = request("GET", host, port, "/v1/info", null, null, timeoutMs)
        if (o.optString("app") != "havalbridge") throw IOException("not a HAVAL Bridge")
        return Car(o.optString("id"), o.optString("name", "HAVAL"), host, port)
    }

    /** الاقتران بالرمز (احتياطي). يرمي HttpError(403) إن كان الرمز خاطئًا و429 عند القفل. */
    @Throws(IOException::class)
    fun pair(car: Car, code: String, phoneName: String): String {
        val body = JSONObject().put("code", code).put("phone", phoneName)
        val o = request("POST", car.host, car.port, "/v1/pair", null, json(body), 5000)
        return o.optString("token").ifEmpty { throw IOException("no token") }
    }

    /** يطلب الاقتران بالموافقة على الشاشة ويعيد معرّف الطلب. 404 = تطبيق شاشة قديم. */
    @Throws(IOException::class)
    fun pairRequest(car: Car, phoneName: String): String {
        val o = request("POST", car.host, car.port, "/v1/pair/request", null, json(JSONObject().put("phone", phoneName)), 5000)
        return o.optString("request").ifEmpty { throw IOException("no request") }
    }

    /** {status: pending|approved|denied|expired, token?} */
    @Throws(IOException::class)
    fun pairStatus(car: Car, id: String): JSONObject =
        request("GET", car.host, car.port, "/v1/pair/request/$id", null, null, 3000)

    @Throws(IOException::class)
    fun send(host: String, port: Int, token: String, item: JSONObject) {
        request("POST", host, port, "/v1/items", token, json(item), 6000)
    }

    @Throws(IOException::class)
    fun ping(host: String, port: Int, token: String) {
        request("GET", host, port, "/v1/ping", token, null, 2500)
    }

    /** {id, received, done, chunk} */
    @Throws(IOException::class)
    fun uploadStart(host: String, port: Int, token: String, meta: JSONObject): JSONObject =
        request("POST", host, port, "/v1/uploads", token, json(meta), 15000)

    @Throws(IOException::class)
    fun uploadChunk(host: String, port: Int, token: String, id: String, offset: Long, data: ByteArray, len: Int): JSONObject =
        request(
            "PUT", host, port, "/v1/uploads/$id", token,
            Body(data, len, "application/octet-stream"), 60000, mapOf("X-Offset" to offset.toString()),
        )

    private class Body(val bytes: ByteArray, val len: Int, val type: String)

    private fun json(o: JSONObject): Body {
        val b = o.toString().toByteArray(Charsets.UTF_8)
        return Body(b, b.size, "application/json; charset=utf-8")
    }

    private fun request(
        method: String, host: String, port: Int, path: String,
        token: String?, body: Body?, timeoutMs: Int, headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val url = URL("http", host, port, path)
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = minOf(timeoutMs, 5000)
            c.readTimeout = timeoutMs
            c.useCaches = false
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", body.type)
                c.setFixedLengthStreamingMode(body.len)
                c.outputStream.use { it.write(body.bytes, 0, body.len) }
            }
            val code = c.responseCode
            if (code != 200) {
                val err = try {
                    c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { JSONObject(it.readText()) }
                } catch (e: Exception) {
                    null
                }
                throw HttpError(code, err)
            }
            val text = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IOException("bad response")
            }
        } finally {
            c.disconnect()
        }
    }
}
