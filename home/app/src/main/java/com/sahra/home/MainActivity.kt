package com.sahra.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.widget.FrameLayout
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
    private lateinit var diag: TextView
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var pageReady = false
    private val consoleErrors = ArrayList<String>()
    private var dialog: AlertDialog? = null
    private var checking = false
    private var pendingUpdate: Updater.Release? = null
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
        if (prefs.getBoolean(KEY_SOFTWARE, false)) web.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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

            // محرك العرض في الشاشة توقف: أعد التشغيل بدل الإغلاق
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                handler.post { recreate() }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    synchronized(consoleErrors) {
                        consoleErrors.add("${m.message()} (${m.lineNumber()})")
                        if (consoleErrors.size > 5) consoleErrors.removeAt(0)
                    }
                }
                return false
            }
        }

        // رسالة تشخيص أصلية تظهر إذا لم تكتمل الواجهة، لأنها لا تعتمد على WebView
        diag = TextView(this)
        diag.setBackgroundColor(Color.parseColor("#E6141A21"))
        diag.setTextColor(Color.WHITE)
        diag.textSize = 18f
        diag.gravity = Gravity.CENTER
        val pad = (32 * resources.displayMetrics.density).toInt()
        diag.setPadding(pad, pad, pad, pad)
        diag.visibility = View.GONE
        diag.setOnClickListener { toggleSoftwareAndReload() }

        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(diag, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        handler.postDelayed({ checkShown() }, 9000)
        hideBars()

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl(pageUrl())
        }
    }

    private fun webViewVersion(): String = try {
        WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "غير معروف"
    } catch (e: Exception) {
        "غير معروف"
    }

    private fun checkShown() {
        if (pageReady || isFinishing) return
        val errors = synchronized(consoleErrors) { consoleErrors.joinToString("\n") }
        val mode = if (prefs.getBoolean(KEY_SOFTWARE, false)) "العرض البرمجي مفعّل" else "العرض بكرت الشاشة"
        diag.text = "الواجهة لم تظهر بشكل صحيح\n\n" +
            "نسخة متصفح النظام: ${webViewVersion()}\n" +
            "الإصدار: ${Updater.currentName(this)} — $mode\n" +
            (if (errors.isNotEmpty()) "\nالأخطاء:\n$errors\n" else "") +
            "\nصوّر هذه الشاشة، ثم اضغط عليها لإعادة المحاولة بطريقة عرض أخرى"
        diag.visibility = View.VISIBLE
    }

    private fun toggleSoftwareAndReload() {
        val now = !prefs.getBoolean(KEY_SOFTWARE, false)
        prefs.edit().putBoolean(KEY_SOFTWARE, now).commit()
        diag.visibility = View.GONE
        pageReady = false
        recreate()
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
        val waiting = pendingUpdate
        if (waiting != null && Updater.canInstall(this)) {
            pendingUpdate = null
            startUpdate(waiting)
        } else {
            maybeAutoCheck()
        }
    }

    // ---------- التحديث ----------

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun maybeAutoCheck() {
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_EVERY_MS) return
        checkUpdate(false)
    }

    private fun checkUpdate(userAsked: Boolean) {
        if (checking) return
        checking = true
        Thread {
            val result = Updater.check(this)
            if (result !is Updater.Result.Failed) {
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            }
            runOnUiThread {
                checking = false
                if (!isFinishing) {
                    when (result) {
                        is Updater.Result.Available -> offerUpdate(result.release)
                        is Updater.Result.UpToDate -> if (userAsked) toast("لديك أحدث نسخة ✓")
                        is Updater.Result.Failed -> if (userAsked) toast(result.message)
                    }
                }
            }
        }.start()
    }

    private fun offerUpdate(r: Updater.Release) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("يتوفر تحديث لمنزلي")
            .setMessage("الإصدار الجديد 1.0.${r.number}\nالإصدار الحالي ${Updater.currentName(this)}")
            .setPositiveButton("تحديث الآن") { _, _ -> startUpdate(r) }
            .setNegativeButton("لاحقًا", null)
            .show()
    }

    private fun startUpdate(r: Updater.Release) {
        if (!Updater.canInstall(this)) {
            pendingUpdate = r
            toast("فعّل السماح لمنزلي بتثبيت التحديثات ثم ارجع للتطبيق")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                pendingUpdate = null
                toast("لم أستطع فتح إعدادات التثبيت")
            }
            return
        }
        val pad = (24 * resources.displayMetrics.density).toInt()
        val status = TextView(this)
        status.setPadding(pad, pad, pad, pad)
        status.textSize = 18f
        status.text = "جارٍ التنزيل…"
        val progress = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("تحديث منزلي")
            .setView(status)
            .setCancelable(false)
            .show()
        Thread {
            try {
                val apk = Updater.download(this, r) { p -> runOnUiThread { status.text = "جارٍ التنزيل $p%" } }
                runOnUiThread {
                    progress.dismiss()
                    try {
                        Updater.install(this, apk)
                    } catch (e: Exception) {
                        toast("فشل التثبيت")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    toast("تعذّر تنزيل التحديث")
                }
            }
        }.start()
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
        dialog?.dismiss()
        handler.removeCallbacksAndMessages(null)
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

        /** تستدعيها الصفحة بعد أول رسم ناجح */
        @JavascriptInterface
        fun ready() {
            pageReady = true
            runOnUiThread { diag.visibility = View.GONE }
        }

        @JavascriptInterface
        fun webview(): String = webViewVersion()

        @JavascriptInterface
        fun version(): String = Updater.currentName(this@MainActivity)

        /** حفظ دائم لإعدادات الصفحة وتسجيل الدخول، يبقى حتى بعد إطفاء السيارة */
        @JavascriptInterface
        fun save(key: String, value: String) {
            prefs.edit().putString("js_$key", value).commit()
        }

        @JavascriptInterface
        fun load(key: String): String? = prefs.getString("js_$key", null)

        @JavascriptInterface
        fun checkUpdate() {
            runOnUiThread { this@MainActivity.checkUpdate(true) }
        }
    }

    companion object {
        const val DEFAULT_SERVER = "https://abusamiha.fun"
        const val PAGE_PATH = "/local/haval-home.html"
        const val ASSET_PAGE = "haval-home.html"
        const val KEY_SERVER = "server"
        const val KEY_SOFTWARE = "software_render"
        const val KEY_LAST_CHECK = "last_update_check"
        const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L
        val BG: Int = Color.parseColor("#0A0C0F")
    }
}
