package com.sahra.bridge.car

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class Request(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val bytes: ByteArray,
    val remote: String,
) {
    val body: String get() = String(bytes, Charsets.UTF_8)
}

/** رد JSON افتراضيًا، أو أي محتوى (صفحة الويب، ملف APK) عبر raw. */
class Response(
    val code: Int,
    val json: String = "",
    val raw: ByteArray? = null,
    val type: String = "application/json; charset=utf-8",
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun raw(code: Int, bytes: ByteArray, type: String, headers: Map<String, String> = emptyMap()) =
            Response(code, "", bytes, type, headers)
    }
}

/**
 * خادم HTTP صغير بلا مكتبات: طلب واحد لكل اتصال. جسم JSON لا يتجاوز 64 KB،
 * وأجزاء الملفات (PUT /v1/uploads/…) حتى 5 MB لكل طلب.
 */
class HttpServer(private val port: Int, private val handler: (Request) -> Response) {
    @Volatile private var socket: ServerSocket? = null
    private var pool: ExecutorService? = null

    @Throws(IOException::class)
    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(port))
        socket = s
        val workers = Executors.newFixedThreadPool(4)
        pool = workers
        thread(name = "bridge-accept", isDaemon = true) {
            while (!s.isClosed) {
                val client = try {
                    s.accept()
                } catch (e: IOException) {
                    break
                }
                workers.execute { serve(client) }
            }
        }
    }

    fun stop() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun serve(client: Socket) {
        try {
            client.use { c ->
                c.soTimeout = 20_000
                val input = BufferedInputStream(c.getInputStream())
                val out = c.getOutputStream()
                val first = readLine(input) ?: return
                val parts = first.split(" ")
                if (parts.size < 2) return
                val headers = HashMap<String, String>()
                while (true) {
                    val h = readLine(input) ?: return
                    if (h.isEmpty()) break
                    val i = h.indexOf(':')
                    if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                val limit = if (method == "PUT" && path.startsWith("/v1/uploads/")) MAX_CHUNK else MAX_BODY
                if (len < 0 || len > limit) {
                    write(out, Response(413, """{"error":"too_large"}"""))
                    return
                }
                val body = ByteArray(len)
                var off = 0
                while (off < len) {
                    val n = input.read(body, off, len - off)
                    if (n < 0) break
                    off += n
                }
                if (off < len) return
                val req = Request(method, path, headers, body, c.inetAddress?.hostAddress ?: "")
                val resp = try {
                    handler(req)
                } catch (e: Exception) {
                    Log.w(TAG, "handler failed", e)
                    Response(500, """{"error":"internal"}""")
                }
                write(out, resp)
            }
        } catch (e: IOException) {
            Log.d(TAG, "connection dropped: ${e.message}")
        }
    }

    private fun write(out: OutputStream, r: Response) {
        val bytes = r.raw ?: r.json.toByteArray(Charsets.UTF_8)
        val extra = r.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
        val head = "HTTP/1.1 ${r.code} ${reason(r.code)}\r\n" +
            "Content-Type: ${r.type}\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            extra +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    /** يقرأ سطرًا حتى \n ويحذف \r. يعيد null عند انتهاء الاتصال. */
    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > MAX_LINE) throw IOException("line too long")
        }
        return buf.toString("UTF-8")
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        409 -> "Conflict"
        422 -> "Unprocessable Entity"
        429 -> "Too Many Requests"
        507 -> "Insufficient Storage"
        else -> "Error"
    }

    companion object {
        private const val TAG = "HavalBridge"
        const val MAX_BODY = 64 * 1024
        const val MAX_CHUNK = 5 * 1024 * 1024
        const val MAX_LINE = 8 * 1024
    }
}
