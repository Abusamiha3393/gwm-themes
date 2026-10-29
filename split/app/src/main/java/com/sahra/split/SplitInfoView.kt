package com.sahra.split

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * لوحة المعلومات الجانبية كجسر لوحات: صف رموز الصفحات في الأعلى، وتحته الصفحة كلوحة طريق واحدة.
 * التنقل بلمس الرمز أو بالسحب أفقيًا.
 */
class SplitInfoView(context: Context, initialPage: Int, private val onPageChanged: (Int) -> Unit) : LinearLayout(context) {

    data class PageSpec(val name: String, val icon: Int, val color: Int)

    companion object {
        val PAGES = listOf(
            PageSpec("الساعة", R.drawable.ic_clock, Palette.green),
            PageSpec("الوقود", R.drawable.ic_fuel, Palette.blue),
            PageSpec("الرسوم", R.drawable.ic_chart, Palette.green),
            PageSpec("الطقس", R.drawable.ic_partly, Palette.brown),
            PageSpec("الموسيقى", R.drawable.ic_music, Palette.blue),
            PageSpec("وعرة", R.drawable.ic_terrain, Palette.brown),
        )
    }

    private val tabs = LinearLayout(context).apply { orientation = VERTICAL }
    private val tabViews = mutableListOf<LinearLayout>()
    private val host = SwipeFrame(context) { dir -> show(page + dir) }
    var page = initialPage.coerceIn(0, PAGES.size - 1)
        private set

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.gantry)
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        // صفّان من ثلاث لوحات صغيرة (رمز واسم)، كل واحدة 64dp على الأقل للمس أثناء القيادة
        val rows = listOf(LinearLayout(context), LinearLayout(context))
        PAGES.forEachIndexed { i, spec ->
            val t = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                contentDescription = spec.name
                setOnClickListener { show(i) }
            }.also { it.pressable(context.dp(10f)) }
            t.addView(ImageView(context).apply { setImageResource(spec.icon) }, LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(6) })
            t.addView(context.label(spec.name, 13f, Palette.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            tabViews.add(t)
            rows[i / 3].addView(t, LayoutParams(0, context.dp(64), 1f).apply { if (i % 3 > 0) marginStart = context.dp(6) })
        }
        tabs.addView(rows[0], LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        tabs.addView(rows[1], LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(host, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.dp(12) })
        show(page, notify = false)
    }

    fun show(i: Int, notify: Boolean = true) {
        page = (i + PAGES.size) % PAGES.size
        val spec = PAGES[page]
        host.background = context.sign(spec.color)
        host.removeAllViews()
        host.addView(create(page), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        tabViews.forEachIndexed { idx, t ->
            val on = idx == page
            val ink = if (on) Palette.white else Palette.muted
            t.background = if (on) context.smallSign(PAGES[idx].color) else rounded(0, context.dp(10f), context.dp(2), Palette.kerb)
            (t.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(ink)
            (t.getChildAt(1) as android.widget.TextView).apply {
                setTextColor(ink)
                typeface = if (on) Type.kufiBold else Type.kufi
            }
        }
        if (notify) onPageChanged(page)
    }

    private fun create(i: Int): View = when (i) {
        0 -> ClockPage(context)
        1 -> FuelPage(context)
        2 -> ChartsPage(context)
        3 -> WeatherPage(context)
        4 -> MusicPage(context)
        else -> OffroadPage(context)
    }
}

/** إطار يلتقط السحب الأفقي ويترك اللمسات العادية للأزرار داخله. */
class SwipeFrame(context: Context, private val onSwipe: (Int) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 3
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y; swiping = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX; val dy = ev.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) swiping = true
            }
        }
        return swiping
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // الصفحات المرسومة لا تستهلك اللمس، فيصل إلينا مباشرة دون المرور بالاعتراض
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_MOVE) onInterceptTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP && swiping) {
            val dx = ev.x - downX
            // في الواجهة العربية: السحب لليسار يعرض الصفحة التالية
            onSwipe(if (dx < 0) 1 else -1)
            swiping = false
        }
        return true
    }
}
