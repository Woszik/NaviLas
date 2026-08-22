package pl.navilas.finder.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.absoluteValue

/**
 * Allows a vertical child (e.g. RecyclerView) to scroll inside horizontal [ViewPager2]
 * without stealing page swipes.
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f
    private val parentViewPager: ViewPager2?
        get() {
            var v: View? = parent as? View
            while (v != null && v !is ViewPager2) {
                v = v.parent as? View
            }
            return v as? ViewPager2
        }

    init {
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        handleIntercept(e)
        return super.onInterceptTouchEvent(e)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        handleIntercept(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun handleIntercept(e: MotionEvent) {
        val pager = parentViewPager ?: return
        val orientation = pager.orientation
        if (e.action == MotionEvent.ACTION_DOWN) {
            initialX = e.x
            initialY = e.y
            parent.requestDisallowInterceptTouchEvent(true)
        } else if (e.action == MotionEvent.ACTION_MOVE) {
            val dx = e.x - initialX
            val dy = e.y - initialY
            val absDx = dx.absoluteValue
            val absDy = dy.absoluteValue
            if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                if (absDx > touchSlop && absDx * 0.5f > absDy) {
                    // Horizontal page swipe — let ViewPager2 handle it.
                    parent.requestDisallowInterceptTouchEvent(false)
                } else if (absDy > touchSlop) {
                    // Vertical list scroll.
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
    }
}
