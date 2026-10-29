package com.sahra.split

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * هوية لوحات الطرق: أرضية بلون الإسفلت، ولوحات خضراء (الطرق) وزرقاء (الخدمات والتطبيقات)
 * وبنية (السياحة والطقس والطرق الوعرة)، وكهرماني للتحذير، وأحمر للحدود. الكتابة بيضاء داخل إطار أبيض.
 */
object Palette {
    val asphalt = Color.parseColor("#18191B")
    val gantry = Color.parseColor("#111214")
    val kerb = Color.parseColor("#26282B")
    val shoulder = Color.parseColor("#1F2023")
    val laneDash = Color.parseColor("#3B3D41")
    val green = Color.parseColor("#00673F")
    val blue = Color.parseColor("#1B4C8C")
    val brown = Color.parseColor("#6E3B1E")
    val amber = Color.parseColor("#F3B300")
    val red = Color.parseColor("#C8102E")
    val white = Color.parseColor("#F4F5F0")
    val legendDim = Color.parseColor("#CCF4F5F0")
    val muted = Color.parseColor("#9FA3A8")
    val ink = Color.parseColor("#16130A")
}

/** الخطوط: كوفي عريض للكلمات، وأرقام بأسلوب لوحات الطرق السريعة. */
object Type {
    lateinit var kufi: Typeface; private set
    lateinit var kufiBold: Typeface; private set
    lateinit var digits: Typeface; private set
    lateinit var digitsBlack: Typeface; private set
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        val r = context.applicationContext.resources
        kufi = runCatching { r.getFont(R.font.kufi_medium) }.getOrDefault(Typeface.DEFAULT)
        kufiBold = runCatching { r.getFont(R.font.kufi_bold) }.getOrDefault(Typeface.DEFAULT_BOLD)
        digits = runCatching { r.getFont(R.font.digits_bold) }.getOrDefault(Typeface.DEFAULT_BOLD)
        digitsBlack = runCatching { r.getFont(R.font.digits_black) }.getOrDefault(Typeface.DEFAULT_BOLD)
        ready = true
    }
}

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dp(v: Int): Int = dp(v.toFloat()).toInt()

/**
 * لوحة طريق: تعبئة بلون اللوحة، وإطار أبيض داخلي بزوايا أصغر، كما في اللوحات الحقيقية.
 * topTab = true يجعل الزاوية العلوية القريبة من لسان المخرج شبه حادة.
 */
class SignDrawable(
    private val color: Int,
    private val radius: Float,
    private val inset: Float,
    private val border: Float,
    private val borderColor: Int = Palette.white,
    private val sharpTopEnd: Boolean = false,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@SignDrawable.color }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = border; this.color = borderColor
    }
    private val path = android.graphics.Path()

    override fun draw(c: Canvas) {
        val b = RectF(bounds)
        if (sharpTopEnd) {
            val small = radius * 0.3f
            path.reset()
            path.addRoundRect(b, floatArrayOf(radius, radius, small, small, radius, radius, radius, radius), android.graphics.Path.Direction.CW)
            c.drawPath(path, fill)
        } else c.drawRoundRect(b, radius, radius, fill)
        if (border > 0f) {
            val i = inset + border / 2
            val r = (radius - inset).coerceAtLeast(2f)
            c.drawRoundRect(RectF(b.left + i, b.top + i, b.right - i, b.bottom - i), r, r, line)
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; line.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf; line.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getPadding(padding: Rect): Boolean {
        val p = (inset + border).toInt()
        padding.set(p, p, p, p)
        return true
    }
}

/** لوحة بالمقاس الكبير (صفحات ولوحات المخارج). */
fun Context.sign(color: Int, sharpTopEnd: Boolean = false) = SignDrawable(color, dp(14f), dp(5f), dp(2.5f), Palette.white, sharpTopEnd)
/** لوحة صغيرة (أزرار وشارات). */
fun Context.smallSign(color: Int, border: Int = Palette.white) = SignDrawable(color, dp(10f), dp(3.5f), dp(2f), border)

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

/** نص بخط الهوية. digitsFace لأرقام اللوحات. */
fun Context.label(text: String, size: Float = 16f, color: Int = Palette.white, bold: Boolean = false, digitsFace: Boolean = false) = TextView(this).apply {
    Type.init(this@label)
    this.text = text
    textSize = size
    setTextColor(color)
    includeFontPadding = false
    typeface = when {
        digitsFace -> if (bold) Type.digitsBlack else Type.digits
        bold -> Type.kufiBold
        else -> Type.kufi
    }
}

/** رمز من مجموعة الرموز بلون محدد. */
fun Context.icon(res: Int, sizeDp: Int, color: Int = Palette.white) = ImageView(this).apply {
    setImageResource(res)
    imageTintList = ColorStateList.valueOf(color)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** زر على هيئة لوحة: رمز وكلمة. */
fun Context.signButton(res: Int?, text: String, color: Int, onClick: () -> Unit): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = smallSign(color)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        minimumHeight = dp(64)
        isClickable = true
        setOnClickListener { onClick() }
        foreground = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, rounded(Color.WHITE, dp(10f)))
    }
    if (res != null) row.addView(icon(res, 28), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = if (text.isEmpty()) 0 else dp(10) })
    if (text.isNotEmpty()) row.addView(label(text, 17f, Palette.white, true))
    return row
}

/** للتوافق مع الشاشات القديمة: زر لوحة زرقاء بلا رمز. */
fun Context.pill(text: String, onClick: () -> Unit) = signButton(null, text, Palette.blue, onClick)

/** زر الرجوع: لوحة «دوران للخلف». */
fun Context.backSign(onClick: () -> Unit) = signButton(R.drawable.ic_back, "رجوع", Palette.blue, onClick)

/** شريط عنوان الشاشات الفرعية: لوحة خضراء عريضة برمز واسم، وزر رجوع. */
fun Context.header(res: Int, title: String, color: Int = Palette.green, onBack: () -> Unit): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val title_ = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = sign(color)
        setPadding(dp(22), dp(12), dp(22), dp(12))
    }
    title_.addView(icon(res, 34), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(14) })
    title_.addView(label(title, 24f, Palette.white, true))
    // زر الرجوع في بداية السطر (اليمين في العربية) كما في أندرويد
    row.addView(backSign(onBack), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(68)).apply { marginEnd = dp(12) })
    row.addView(title_, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    return row
}

fun View.pad(all: Int) { setPadding(all, all, all, all) }

/** رقم بعدد خانات عشرية، أو «—» إن لم يكن معروفًا. الآلاف بفاصلة كما في لوحات المسافات. */
fun num(v: Float, decimals: Int = 0): String =
    if (v.isNaN() || v.isInfinite()) "—" else String.format(Locale.US, "%,.${decimals}f", v)

/** يعزل رقمًا مع رمزه (مثل 36°) باتجاه يسار-يمين داخل جملة عربية، حتى لا تنقلب العلامة إلى الجهة الأخرى. */
/** العربية بأرقام لاتينية، لتوحيد الأرقام مع أرقام اللوحات. */
val ArabicLatn: Locale = Locale.forLanguageTag("ar-u-nu-latn")

fun ltr(s: String) = "\u2066$s\u2069"
