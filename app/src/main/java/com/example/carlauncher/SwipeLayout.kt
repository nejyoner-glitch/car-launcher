package com.example.carlauncher

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/** Yatay kaydırmayı yakalayan LinearLayout. onSwipe(+1) sola, onSwipe(-1) sağa kaydırma. */
class SwipeLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    var onSwipe: ((Int) -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    private fun isHorizontal(ev: MotionEvent): Boolean {
        val dx = ev.x - downX
        val dy = ev.y - downY
        return abs(dx) > slop * 3 && abs(dx) > abs(dy) * 1.5f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> if (isHorizontal(ev)) {
                swiping = true
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (!swiping && isHorizontal(ev)) swiping = true
            MotionEvent.ACTION_UP -> if (swiping) {
                val dx = ev.x - downX
                if (abs(dx) > slop * 6) onSwipe?.invoke(if (dx < 0) 1 else -1)
                swiping = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return if (swiping) true else super.onTouchEvent(ev)
    }
}
