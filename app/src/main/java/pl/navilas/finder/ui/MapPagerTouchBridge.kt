package pl.navilas.finder.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Resolves ViewPager2 ↔ MapLibre touch conflict.
 *
 * - Single-finger mostly-horizontal swipe → allow parent [ViewPager2] to change page.
 * - Vertical pan, diagonal map move, or multi-touch (pinch) → keep gestures on the map.
 */
class MapPagerTouchBridge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var pagingGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                pagingGesture = false
                // Start optimistic: map may pan; we may release to pager later.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Pinch / multi-touch always belongs to the map.
                pagingGesture = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    pagingGesture = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (!pagingGesture) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    if (absDx > touchSlop || absDy > touchSlop) {
                        val horizontalDominant = absDx > absDy * HORIZONTAL_BIAS
                        if (horizontalDominant) {
                            pagingGesture = true
                            // Let ViewPager2 intercept the horizontal page swipe.
                            parent?.requestDisallowInterceptTouchEvent(false)
                        } else {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pagingGesture = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        /** Horizontal must exceed vertical by this factor to count as a page swipe. */
        const val HORIZONTAL_BIAS = 1.15f
    }
}
