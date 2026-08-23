package pl.navilas.finder.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Horizontal swipe zone outside [androidx.viewpager2.widget.ViewPager2]
 * (toolbar / footer). Finger left → next page; finger right → previous page.
 * Child buttons still receive taps.
 */
class PageSwipeZone @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onSwipeToNext: (() -> Unit)? = null
    var onSwipeToPrevious: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) < MIN_DISTANCE_PX || abs(dx) < abs(dy) * HORIZONTAL_BIAS) {
                    return false
                }
                if (abs(velocityX) < MIN_VELOCITY) return false
                if (dx < 0f) {
                    onSwipeToNext?.invoke()
                } else {
                    onSwipeToPrevious?.invoke()
                }
                return true
            }
        },
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
                detector.onTouchEvent(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!intercepting) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > touchSlop && dx > dy * HORIZONTAL_BIAS) {
                        intercepting = true
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                intercepting = false
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> intercepting = false
        }
        return handled || intercepting || event.actionMasked == MotionEvent.ACTION_DOWN
    }

    companion object {
        private const val MIN_DISTANCE_PX = 80f
        private const val MIN_VELOCITY = 200f
        private const val HORIZONTAL_BIAS = 1.2f
    }
}
