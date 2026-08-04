package com.webscare.urducanvas.common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * A FrameLayout that always measures itself as a square.
 * Height is forced to equal measured width — works as a direct child
 * of MaterialCardView, LinearLayout, RecyclerView item, anything.
 *
 * No XML attributes needed. Just use it in your layout.
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (heightMode == MeasureSpec.EXACTLY) {
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val size = minOf(widthSize, heightSize)
            val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            super.onMeasure(spec, spec)
        } else {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }
}