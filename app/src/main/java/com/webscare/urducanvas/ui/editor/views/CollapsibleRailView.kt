package com.webscare.urducanvas.ui.editor.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.databinding.ItemRailCategoryBinding
import com.webscare.urducanvas.databinding.ViewCollapsibleRailBinding

data class RailCategoryItem(
    val id: String,
    val label: String,
    val iconRes: Int? = null,
    val isEnabled: Boolean? = null,
    val hasSubList: Boolean = false
)

class CollapsibleRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewCollapsibleRailBinding =
        ViewCollapsibleRailBinding.inflate(LayoutInflater.from(context), this, true)

    private val expandedWidthPx: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 118f, resources.displayMetrics
    ).toInt()

    private val collapsedWidthPx: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 44f, resources.displayMetrics
    ).toInt()

    var isCollapsed: Boolean = false
        private set

    private var panelId: String? = null
    private var selectedCategoryId: String? = null

    private var items: List<RailCategoryItem> = emptyList()
    private val adapter = RailAdapter()

    var onCategorySelectedListener: ((category: RailCategoryItem) -> Unit)? = null
    var onCategoryToggleChangedListener: ((category: RailCategoryItem, isEnabled: Boolean) -> Unit)? = null
    var onCollapseStateChangedListener: ((isCollapsed: Boolean) -> Unit)? = null

    init {
        binding.rvRailCategories.layoutManager = LinearLayoutManager(context)
        binding.rvRailCategories.adapter = adapter

        val toggleClickListener = OnClickListener {
            toggleCollapsed(animate = true)
        }
        binding.clToggleContainer.setOnClickListener(toggleClickListener)
        binding.llToggleContent.setOnClickListener(toggleClickListener)
        binding.ivToggleChevron.setOnClickListener(toggleClickListener)
        binding.tvToggleLabel.setOnClickListener(toggleClickListener)

        isCollapsed = getGlobalCollapsedState()
        updateLayoutState(animate = false)
    }

    fun bindPanelId(id: String) {
        this.panelId = id
        val globalCollapsed = getGlobalCollapsedState()
        if (isCollapsed != globalCollapsed) {
            setCollapsed(globalCollapsed, animate = false)
        }
    }

    fun setCategories(categoryItems: List<RailCategoryItem>, defaultSelectedId: String? = null) {
        this.items = categoryItems
        if (defaultSelectedId != null) {
            this.selectedCategoryId = defaultSelectedId
        } else if (items.isNotEmpty() && selectedCategoryId == null) {
            this.selectedCategoryId = items.first().id
        }
        adapter.notifyDataSetChanged()
    }

    fun setSelectedCategory(id: String) {
        if (selectedCategoryId != id) {
            selectedCategoryId = id
            adapter.notifyDataSetChanged()
        }
    }

    fun setCategoryEnabled(id: String, isEnabled: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = items[index].copy(isEnabled = isEnabled)
            val mutableList = items.toMutableList()
            mutableList[index] = updated
            items = mutableList
            adapter.notifyItemChanged(index)
        }
    }

    fun setCollapsed(collapsed: Boolean, animate: Boolean = true) {
        if (isCollapsed == collapsed) return
        isCollapsed = collapsed
        isGlobalCollapsed = collapsed

        getPrefs().edit().putBoolean(PREF_KEY_GLOBAL_COLLAPSED, collapsed).apply()

        updateLayoutState(animate = animate)
        onCollapseStateChangedListener?.invoke(isCollapsed)
    }

    fun toggleCollapsed(animate: Boolean = true) {
        setCollapsed(!isCollapsed, animate = animate)
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences("urdu_canvas_rail_prefs", Context.MODE_PRIVATE)
    }

    private fun getGlobalCollapsedState(): Boolean {
        return getPrefs().getBoolean(PREF_KEY_GLOBAL_COLLAPSED, false)
    }

    private fun updateLayoutState(animate: Boolean) {
        val targetWidth = if (isCollapsed) collapsedWidthPx else expandedWidthPx
        val startWidth = layoutParams?.width ?: if (isCollapsed) expandedWidthPx else collapsedWidthPx

        if (animate && startWidth != targetWidth) {
            val animator = ValueAnimator.ofInt(startWidth, targetWidth)
            animator.duration = 220
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { anim ->
                val currentWidth = anim.animatedValue as Int
                val lp = layoutParams
                if (lp != null) {
                    lp.width = currentWidth
                    layoutParams = lp
                    requestLayout()
                    (parent as? ViewGroup)?.requestLayout()
                    (parent as? ViewGroup)?.invalidate()
                }
            }
            animator.start()
        } else {
            val lp = layoutParams
            if (lp != null) {
                lp.width = targetWidth
                layoutParams = lp
                requestLayout()
                (parent as? ViewGroup)?.requestLayout()
                (parent as? ViewGroup)?.invalidate()
            }
        }

        binding.ivToggleChevron.setImageResource(
            if (isCollapsed) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left
        )
        binding.ivToggleChevron.clearColorFilter()

        binding.tvToggleLabel.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        binding.clToggleContainer.contentDescription = if (isCollapsed) "Expand categories" else "Collapse categories"

        val toggleContentLp = binding.llToggleContent.layoutParams as ConstraintLayout.LayoutParams
        if (isCollapsed) {
            binding.llToggleContent.gravity = Gravity.CENTER
            binding.llToggleContent.setPadding(0, 0, 0, 0)
        } else {
            binding.llToggleContent.gravity = Gravity.CENTER_VERTICAL
            binding.llToggleContent.setPadding(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt(),
                0,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt(),
                0
            )
        }
        binding.llToggleContent.layoutParams = toggleContentLp

        adapter.notifyDataSetChanged()
    }

    companion object {
        private const val PREF_KEY_GLOBAL_COLLAPSED = "pref_rail_collapsed_global"

        var isGlobalCollapsed: Boolean = false
            private set

        fun monogram(name: String): String {
            val t = name.trim().take(2)
            return t.replaceFirstChar { it.uppercase() }
        }
    }

    private inner class RailAdapter : RecyclerView.Adapter<RailAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemRailCategoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemRailCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = item.id == selectedCategoryId
            val b = holder.binding

            b.clCategoryRow.contentDescription = item.label

            b.clCategoryRow.setOnLongClickListener {
                Toast.makeText(context, item.label, Toast.LENGTH_SHORT).show()
                true
            }

            b.clCategoryRow.setOnClickListener {
                if (selectedCategoryId != item.id) {
                    selectedCategoryId = item.id
                    notifyDataSetChanged()
                    onCategorySelectedListener?.invoke(item)
                }
            }

            b.clCategoryRow.setBackgroundColor(
                if (isSelected) Color.parseColor("#E4F3E9") else Color.TRANSPARENT
            )
            b.vSelectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE

            val hasIcon = item.iconRes != null

            if (isCollapsed) {
                // COLLAPSED STATE: Hide text label, center Icon or Monogram tile in 44dp strip
                b.tvLabel.visibility = View.GONE

                if (hasIcon) {
                    b.ivIcon.visibility = View.VISIBLE
                    b.tvMonogram.visibility = View.GONE
                    b.ivIcon.setImageResource(item.iconRes!!)

                    val lp = b.ivIcon.layoutParams as ConstraintLayout.LayoutParams
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.marginStart = 0
                    b.ivIcon.layoutParams = lp

                    val tintColor = if (isSelected) Color.parseColor("#005D28") else Color.parseColor("#8E94A2")
                    b.ivIcon.setColorFilter(tintColor)
                } else {
                    b.ivIcon.visibility = View.GONE
                    b.tvMonogram.visibility = View.VISIBLE
                    b.tvMonogram.text = monogram(item.label)
                    b.tvMonogram.setBackgroundResource(R.drawable.bg_monogram_tile)

                    val lp = b.tvMonogram.layoutParams as ConstraintLayout.LayoutParams
                    lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    lp.marginStart = 0
                    b.tvMonogram.layoutParams = lp

                    if (isSelected) {
                        b.tvMonogram.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                        b.tvMonogram.setTextColor(Color.parseColor("#005D28"))
                    } else {
                        b.tvMonogram.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F3F3F3"))
                        b.tvMonogram.setTextColor(Color.parseColor("#8E94A2"))
                    }
                }

                if (item.isEnabled != null) {
                    b.ivDotIndicator.visibility = View.VISIBLE
                    b.ivDotIndicator.setImageResource(R.drawable.bg_rail_dot)
                    val dotColor = if (item.isEnabled) Color.parseColor("#005D28") else Color.parseColor("#C9CFCB")
                    b.ivDotIndicator.imageTintList = android.content.res.ColorStateList.valueOf(dotColor)
                    b.ivDotIndicator.setOnClickListener {
                        val newState = !item.isEnabled
                        setCategoryEnabled(item.id, newState)
                        onCategoryToggleChangedListener?.invoke(item, newState)
                    }
                } else {
                    b.ivDotIndicator.visibility = View.GONE
                }

                b.ivCaret.visibility = if (item.hasSubList) View.VISIBLE else View.GONE

            } else {
                // EXPANDED STATE:
                b.tvLabel.visibility = View.VISIBLE
                b.tvLabel.text = item.label
                b.ivCaret.visibility = View.GONE

                if (isSelected) {
                    b.tvLabel.setTextColor(Color.parseColor("#005D28"))
                    b.tvLabel.setTypeface(null, Typeface.BOLD)
                } else {
                    b.tvLabel.setTextColor(Color.parseColor("#1E1E1E"))
                    b.tvLabel.setTypeface(null, Typeface.NORMAL)
                }

                if (hasIcon) {
                    // Icon Panel in Expanded State: Show Icon + Label side-by-side
                    b.ivIcon.visibility = View.VISIBLE
                    b.tvMonogram.visibility = View.GONE
                    b.ivIcon.setImageResource(item.iconRes!!)

                    val iconLp = b.ivIcon.layoutParams as ConstraintLayout.LayoutParams
                    iconLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    iconLp.endToEnd = ConstraintLayout.LayoutParams.UNSET
                    iconLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                    b.ivIcon.layoutParams = iconLp

                    val labelLp = b.tvLabel.layoutParams as ConstraintLayout.LayoutParams
                    labelLp.startToStart = ConstraintLayout.LayoutParams.UNSET
                    labelLp.startToEnd = b.ivIcon.id
                    labelLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()
                    b.tvLabel.layoutParams = labelLp

                    val tintColor = if (isSelected) Color.parseColor("#005D28") else Color.parseColor("#8E94A2")
                    b.ivIcon.setColorFilter(tintColor)
                } else {
                    // Text Panel (Styles, Fonts) in Expanded State: Show Label ONLY starting at 12dp padding
                    b.ivIcon.visibility = View.GONE
                    b.tvMonogram.visibility = View.GONE

                    val labelLp = b.tvLabel.layoutParams as ConstraintLayout.LayoutParams
                    labelLp.startToEnd = ConstraintLayout.LayoutParams.UNSET
                    labelLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    labelLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                    b.tvLabel.layoutParams = labelLp
                }

                if (item.isEnabled != null) {
                    b.ivDotIndicator.visibility = View.VISIBLE
                    b.ivDotIndicator.setImageResource(R.drawable.bg_rail_dot)
                    val dotColor = if (item.isEnabled) Color.parseColor("#005D28") else Color.parseColor("#C9CFCB")
                    b.ivDotIndicator.imageTintList = android.content.res.ColorStateList.valueOf(dotColor)
                    b.ivDotIndicator.setOnClickListener {
                        val newState = !item.isEnabled
                        setCategoryEnabled(item.id, newState)
                        onCategoryToggleChangedListener?.invoke(item, newState)
                    }
                } else {
                    b.ivDotIndicator.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
