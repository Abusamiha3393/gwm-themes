package com.sahra.split

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * لوحة المعلومات الجانبية: شريط صفحات في الأعلى، وصفحة واحدة ظاهرة.
 * التنقل باللمس على اسم الصفحة أو بالسحب أفقيًا.
 */
class SplitInfoView(context: Context, initialPage: Int, private val onPageChanged: (Int) -> Unit) : LinearLayout(context) {

    companion object {
        val PAGES = listOf("الساعة", "الوقود", "الرسوم", "الطقس", "الموسيقى", "الطرق الوعرة")
    }

    private val tabs = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val tabViews = mutableListOf<TextView>()
    private val host = SwipeFrame(context) { dir -> show(page + dir) }
    var page = initialPage.coerceIn(0, PAGES.size - 1)
        private set

    init {
        orientation = VERTICAL
        background = rounded(Palette.bg, 0f)
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        PAGES.forEachIndexed { i, name ->
            val t = TextView(context).apply {
                text = name
                textSize = 15f
                setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
                setOnClickListener { show(i) }
            }
            tabViews.add(t)
            tabs.addView(t, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = context.dp(4) })
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(context.dp(6), context.dp(6), context.dp(6), 0)
        })
        addView(host, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        show(page, notify = false)
    }

    fun show(i: Int, notify: Boolean = true) {
        page = (i + PAGES.size) % PAGES.size
        host.removeAllViews()
        host.addView(create(page), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        tabViews.forEachIndexed { idx, t ->
            val on = idx == page
            t.setTextColor(if (on) Palette.bg else Palette.muted)
            t.background = if (on) rounded(Palette.accent, context.dp(14f)) else null
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
