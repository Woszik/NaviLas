package pl.navilas.finder.ui

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/** NestedScrollView that can cap height so a wrap_content overlay still scrolls. */
class MaxHeightNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NestedScrollView(context, attrs) {
    var maxHeightPx: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = if (maxHeightPx < Int.MAX_VALUE) {
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val limit = if (mode == MeasureSpec.UNSPECIFIED) {
                maxHeightPx
            } else {
                minOf(size, maxHeightPx)
            }
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, capped)
    }
}
