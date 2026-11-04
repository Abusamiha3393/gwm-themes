package com.sahra.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * منزلي — تطبيق شاشة كامل للتحكم بالبيت عبر Home Assistant.
 *
 * الواجهة نفسها مضمّنة في التطبيق (assets/haval-home.html)، لكنها تُعرض على عنوان
 * خادم Home Assistant (مثل https://abusamiha.fun/local/haval-home.html) عبر اعتراض الطلب.
 * بهذا تعمل الواجهة فورًا حتى بدون إنترنت، وتكون من نفس نطاق الخادم فيعمل تسجيل الدخول
 * العادي باسم المستخدم وكلمة المرور دون الحاجة لرمز طويل الأمد.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("home", Context.MODE_PRIVATE) }

    private fun serverUrl(): String =
        (prefs.getString(KEY_SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER).trimEnd('/')

    private fun pageUrl(): String = serverUrl() + PAGE_PATH

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.setBackgroundColor(BG)

        web = WebView(this)
        web.setBackgroundColor(BG)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.textZoom = 100
        s.useWideViewPort = true
        s.loadWithOverviewMode = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(web, true)

        web.addJavascriptInterface(Bridge(), "HavalApp")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url
                if (request.method == "GET" && url.path == PAGE_PATH && isServer(url)) {
                    return try {
                        val res = WebResourceResponse("text/html", "utf-8", assets.open(ASSET_PAGE))
                        res.responseHeaders = mapOf("Cache-Control" to "no-store")
                        res
                    } catch (e: Exception) {
                        null
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }

        setContentView(web)
        hideBars()

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(pageUrl())
        }
    }

    private fun isServer(url: Uri): Boolean {
        val base = Uri.parse(serverUrl())
        return url.scheme.equals(base.scheme, ignoreCase = true) &&
            url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideBars()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // داخل صفحة تسجيل الدخول نرجع للخلف، وإلا نخرج للشاشة الرئيسية للسيارة
        if (web.canGoBack()) {
            web.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun normalize(raw: String): String? {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return null
        if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
            u = "https://$u"
        }
        val host = Uri.parse(u).host
        return if (host.isNullOrEmpty()) null else u
    }

    /** واجهة صغيرة تستدعيها صفحة الويب عبر window.HavalApp */
    inner class Bridge {
        @JavascriptInterface
        fun server(): String = this@MainActivity.serverUrl()

        @JavascriptInterface
        fun setServer(url: String) {
            val clean = normalize(url) ?: return
            runOnUiThread {
                prefs.edit().putString(KEY_SERVER, clean).apply()
                web.loadUrl(clean + PAGE_PATH)
            }
        }

        @JavascriptInterface
        fun buzz() {
            try {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) {
                // بعض الشاشات بلا محرك اهتزاز
            }
        }

        @JavascriptInterface
        fun version(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        const val DEFAULT_SERVER = "https://abusamiha.fun"
        const val PAGE_PATH = "/local/haval-home.html"
        const val ASSET_PAGE = "haval-home.html"
        const val KEY_SERVER = "server"
        val BG: Int = Color.parseColor("#0A0C0F")
    }
}
