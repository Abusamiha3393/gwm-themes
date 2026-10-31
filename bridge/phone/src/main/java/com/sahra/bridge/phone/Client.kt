package com.sahra.bridge.phone

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** سيارة وُجدت على الشبكة. */
data class Car(val id: String, val name: String, val host: String, val port: Int)

class HttpError(val code: Int) : IOException("HTTP $code")

/** عميل بروتوكول الجسر (الإصدار 1). كل الدوال متزامنة، تُستدعى من خيط خلفي. */
object Client {
    const val PORT = 47800

    @Throws(IOException::class)
    fun info(host: String, port: Int = PORT, timeoutMs: Int = 2500): Car {
        val o = request("GET", host, port, "/v1/info", null, null, timeoutMs)
        if (o.optString("app") != "havalbridge") throw IOException("not a HAVAL Bridge")
        return Car(o.optString("id"), o.optString("name", "HAVAL"), host, port)
    }

    /** يعيد رمز الجلسة. يرمي HttpError(403) إن كان الرمز خاطئًا و429 عند القفل. */
    @Throws(IOException::class)
    fun pair(car: Car, code: String, phoneName: String): String {
        val body = JSONObject().put("code", code).put("phone", phoneName)
        val o = request("POST", car.host, car.port, "/v1/pair", null, body, 5000)
        return o.optString("token").ifEmpty { throw IOException("no token") }
    }

    @Throws(IOException::class)
    fun send(host: String, port: Int, token: String, item: JSONObject) {
        request("POST", host, port, "/v1/items", token, item, 6000)
    }

    @Throws(IOException::class)
    fun ping(host: String, port: Int, token: String) {
        request("GET", host, port, "/v1/ping", token, null, 2500)
    }

    private fun request(
        method: String, host: String, port: Int, path: String,
        token: String?, body: JSONObject?, timeoutMs: Int,
    ): JSONObject {
        val url = URL("http", host, port, path)
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.useCaches = false
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            if (code != 200) throw HttpError(code)
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
