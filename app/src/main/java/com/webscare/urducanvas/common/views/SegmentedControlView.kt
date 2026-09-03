package com.webscare.urducanvas.common.views

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class SegmentedControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var items: List<String> = emptyList()
    var selectedIndex: Int = 0
        private set

    var onSegmentSelected: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_segmented_track)
        val p = dp(3f).toInt()
        setPadding(p, p, p, p)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun setItems(titles: List<String>, defaultIndex: Int = 0) {
        this.items = titles
        this.selectedIndex = defaultIndex.coerceIn(0, (titles.size - 1).coerceAtLeast(0))
        removeAllViews()

        for ((index, title) in titles.withIndex()) {
            val tv = TextView(context).apply {
                layoutParams = LayoutParams(0, dp(26f).toInt(), 1f)
                gravity = Gravity.CENTER
                text = title
                textSize = 11f
                setSingleLine(true)
                addPressEffect {
                    selectIndex(index, notify = true)
                }
            }
            addView(tv)
        }
        updateItemsUi()
    }

    fun selectIndex(index: Int, notify: Boolean = false) {
        if (index in items.indices && index != selectedIndex) {
            selectedIndex = index
            updateItemsUi()
            if (notify) {
                onSegmentSelected?.invoke(index)
            }
        }
    }

    private fun updateItemsUi() {
        for (i in 0 until childCount) {
            val tv = getChildAt(i) as? TextView ?: continue
            val isSelected = i == selectedIndex
            if (isSelected) {
                tv.background = ContextCompat.getDrawable(context, R.drawable.bg_segmented_item_selected)
                tv.setTextColor("#2B2B2B".toColorInt())
                try {
                    tv.typeface = ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD
                } catch (e: Exception) {
                    tv.typeface = Typeface.DEFAULT_BOLD
                }
            } else {
                tv.background = null
                tv.setTextColor("#5F6368".toColorInt())
                try {
                    tv.typeface = ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT
                } catch (e: Exception) {
                    tv.typeface = Typeface.DEFAULT
                }
            }
        }
    }
}
