package com.webscare.urducanvas.ui.editor.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.ItemRailCategoryBinding
import com.webscare.urducanvas.databinding.ItemRailSubcategoryBinding
import com.webscare.urducanvas.databinding.ViewCollapsibleRailBinding
import java.util.Collections
import java.util.WeakHashMap

data class RailSubCategoryItem(
    val id: String,
    val label: String
)

data class RailCategoryItem(
    val id: String,
    val label: String,
    val iconRes: Int? = null,
    val isEnabled: Boolean? = null,
    val subItems: List<RailSubCategoryItem> = emptyList(),
    val isSubListExpanded: Boolean = false,
    val selectedSubItemId: String? = null,
    val hasSubList: Boolean = false,
    val isActionButton: Boolean = false
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
    var onSubCategorySelectedListener: ((parentCategory: RailCategoryItem, subCategory: RailSubCategoryItem) -> Unit)? = null
    var onCategoryToggleChangedListener: ((category: RailCategoryItem, isEnabled: Boolean) -> Unit)? = null
    var onCollapseStateChangedListener: ((isCollapsed: Boolean) -> Unit)? = null

    init {
        initGlobalState(context)
        binding.rvRailCategories.layoutManager = LinearLayoutManager(context)
        binding.rvRailCategories.adapter = adapter

        binding.clToggleContainer.addPressEffect {
            toggleCollapsed(animate = true)
        }

        isCollapsed = isGlobalCollapsed
        updateLayoutState(animate = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activeRailViews.add(this)
        val global = getGlobalCollapsedState()
        applyCollapsedState(global, animate = false)
    }

    override fun onDetachedFromWindow() {
        activeRailViews.remove(this)
        super.onDetachedFromWindow()
    }

    fun bindPanelId(id: String) {
        this.panelId = id
        val globalCollapsed = getGlobalCollapsedState()
        applyCollapsedState(globalCollapsed, animate = false)
    }

    fun setCategories(categoryItems: List<RailCategoryItem>, defaultSelectedId: String? = null) {
        this.items = categoryItems
        if (defaultSelectedId != null) {
            this.selectedCategoryId = defaultSelectedId
        } else if (items.isNotEmpty() && selectedCategoryId == null) {
            this.selectedCategoryId = items.first().id
        }
        val globalCollapsed = getGlobalCollapsedState()
        applyCollapsedState(globalCollapsed, animate = false)
    }

    fun setSelectedCategory(id: String) {
        if (selectedCategoryId != id) {
            selectedCategoryId = id
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0 && items[index].subItems.isNotEmpty()) {
                val mutable = items.toMutableList()
                mutable[index] = mutable[index].copy(isSubListExpanded = true)
                items = mutable
            }
            adapter.rebuildAndNotify()
        }
    }

    fun setCategoryEnabled(id: String, isEnabled: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = items[index].copy(isEnabled = isEnabled)
            val mutableList = items.toMutableList()
            mutableList[index] = updated
            items = mutableList
            adapter.rebuildAndNotify()
        }
    }

    fun setCollapsed(collapsed: Boolean, animate: Boolean = true) {
        applyCollapsedState(collapsed, animate = animate)
        setGlobalCollapsed(context, collapsed, sourceView = this)
    }

    private fun applyCollapsedState(collapsed: Boolean, animate: Boolean) {
        isCollapsed = collapsed
        updateLayoutState(animate = animate)
        onCollapseStateChangedListener?.invoke(isCollapsed)
    }

    fun toggleCollapsed(animate: Boolean = true) {
        setCollapsed(!isCollapsed, animate = animate)
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getGlobalCollapsedState(): Boolean {
        return getPrefs().getBoolean(PREF_KEY_GLOBAL_COLLAPSED, false)
    }

    private fun updateLayoutState(animate: Boolean) {
        val targetWidth = if (isCollapsed) collapsedWidthPx else expandedWidthPx
        val startWidth = layoutParams?.width ?: if (isCollapsed) expandedWidthPx else collapsedWidthPx

        binding.tvCollapseLabel.visibility = if (isCollapsed) View.GONE else View.VISIBLE

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

            binding.ivToggleChevron.animate()
                .rotation(if (isCollapsed) 180f else 0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            val lp = layoutParams
            if (lp != null) {
                lp.width = targetWidth
                layoutParams = lp
                requestLayout()
                (parent as? ViewGroup)?.requestLayout()
                (parent as? ViewGroup)?.invalidate()
            } else {
                post {
                    val postLp = layoutParams
                    if (postLp != null) {
                        postLp.width = targetWidth
                        layoutParams = postLp
                        requestLayout()
                        (parent as? ViewGroup)?.requestLayout()
                        (parent as? ViewGroup)?.invalidate()
                    }
                }
            }
            binding.ivToggleChevron.rotation = if (isCollapsed) 180f else 0f
        }

        binding.clToggleContainer.contentDescription = if (isCollapsed) "Expand rail" else "Collapse rail"
        adapter.rebuildAndNotify()
    }

    companion object {
        private const val PREFS_NAME = "urdu_canvas_rail_prefs"
        private const val PREF_KEY_GLOBAL_COLLAPSED = "pref_rail_collapsed_global"

        private val activeRailViews = Collections.newSetFromMap(
            WeakHashMap<CollapsibleRailView, Boolean>()
        )

        var isGlobalCollapsed: Boolean = false
            private set

        fun initGlobalState(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isGlobalCollapsed = prefs.getBoolean(PREF_KEY_GLOBAL_COLLAPSED, false)
        }

        fun setGlobalCollapsed(context: Context, collapsed: Boolean, sourceView: CollapsibleRailView? = null) {
            isGlobalCollapsed = collapsed
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_GLOBAL_COLLAPSED, collapsed)
                .apply()

            activeRailViews.toList().forEach { rail ->
                if (rail != sourceView) {
                    rail.applyCollapsedState(collapsed, animate = true)
                }
            }
        }

        fun monogram(name: String): String {
            val clean = name.trim().removePrefix("+").trim()
            return if (clean.isNotEmpty()) clean.take(1).uppercase() else "?"
        }
    }

    private sealed class FlattenedItem {
        data class Parent(val item: RailCategoryItem, val originalIndex: Int) : FlattenedItem()
        data class Sub(val parentItem: RailCategoryItem, val subItem: RailSubCategoryItem, val parentIndex: Int) : FlattenedItem()
    }

    private inner class RailAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_PARENT = 0
        private val TYPE_SUB = 1

        private var displayItems: List<FlattenedItem> = emptyList()

        fun rebuildAndNotify() {
            val list = mutableListOf<FlattenedItem>()
            items.forEachIndexed { index, parent ->
                list.add(FlattenedItem.Parent(parent, index))
                if (parent.isSubListExpanded && parent.subItems.isNotEmpty()) {
                    parent.subItems.forEach { sub ->
                        list.add(FlattenedItem.Sub(parent, sub, index))
                    }
                }
            }
            displayItems = list
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (displayItems[position]) {
                is FlattenedItem.Parent -> TYPE_PARENT
                is FlattenedItem.Sub -> TYPE_SUB
            }
        }

        override fun getItemCount(): Int = displayItems.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_PARENT) {
                val b = ItemRailCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ParentViewHolder(b)
            } else {
                val b = ItemRailSubcategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SubViewHolder(b)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val dItem = displayItems[position]) {
                is FlattenedItem.Parent -> (holder as ParentViewHolder).bind(dItem)
                is FlattenedItem.Sub -> (holder as SubViewHolder).bind(dItem)
            }
        }

        inner class ParentViewHolder(val binding: ItemRailCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(fItem: FlattenedItem.Parent) {
                val item = fItem.item
                val isSelected = item.id == selectedCategoryId
                val hasIcon = item.iconRes != null
                val hasSub = item.subItems.isNotEmpty() || item.hasSubList
                val isAction = item.isActionButton
                val b = binding

                val regFont = ResourcesCompat.getFont(context, R.font.regular)
                val medFont = ResourcesCompat.getFont(context, R.font.medium)

                b.clCategoryRow.contentDescription = item.label

                b.clCategoryRow.setOnLongClickListener {
                    Toast.makeText(context, item.label, Toast.LENGTH_SHORT).show()
                    true
                }

                b.clCategoryRow.addPressEffect {
                    if (isAction) {
                        onCategorySelectedListener?.invoke(item)
                    } else if (selectedCategoryId != item.id) {
                        // First tap: Navigate/Select
                        selectedCategoryId = item.id
                        if (item.subItems.isNotEmpty()) {
                            val mutable = items.toMutableList()
                            mutable[fItem.originalIndex] = item.copy(isSubListExpanded = true)
                            items = mutable
                        }
                        rebuildAndNotify()
                        onCategorySelectedListener?.invoke(item)
                    } else {
                        // Already selected: Toggle effect if toggleable, otherwise toggle sub-list
                        if (item.isEnabled != null) {
                            val newState = !item.isEnabled
                            setCategoryEnabled(item.id, newState)
                            onCategoryToggleChangedListener?.invoke(item, newState)
                        } else if (item.subItems.isNotEmpty()) {
                            val mutable = items.toMutableList()
                            mutable[fItem.originalIndex] = item.copy(isSubListExpanded = !item.isSubListExpanded)
                            items = mutable
                            rebuildAndNotify()
                        }
                    }
                }

                val colorSelectedBg = ContextCompat.getColor(context, R.color.rail_selected_bg)
                val colorSelectedText = ContextCompat.getColor(context, R.color.rail_selected_text)
                val colorUnselectedText = ContextCompat.getColor(context, R.color.rail_unselected_text)
                val colorMuted = ContextCompat.getColor(context, R.color.rail_muted)
                val colorTileUnselected = ContextCompat.getColor(context, R.color.rail_tile_unselected)
                val colorDotDisabled = ContextCompat.getColor(context, R.color.rail_dot_disabled)

                if (isAction) {
                    b.clCategoryRow.setBackgroundColor(Color.TRANSPARENT)
                    b.vSelectionIndicator.visibility = View.GONE
                } else {
                    b.clCategoryRow.setBackgroundColor(
                        if (isSelected) colorSelectedBg else Color.TRANSPARENT
                    )
                    b.vSelectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
                }

                // ── ACTION BUTTON (e.g. "+ Add Style") ──────────────────────────────
                if (isAction) {
                    b.llActionButton.visibility = View.VISIBLE
                    b.vSelectionIndicator.visibility = View.GONE
                    b.ivIcon.visibility = View.GONE
                    b.tvMonogram.visibility = View.GONE
                    b.ivDotIndicator.visibility = View.GONE
                    b.ivCaret.visibility = View.GONE
                    b.ivExpandChevron.visibility = View.GONE
                    b.tvLabel.visibility = View.GONE

                    val actionLp = b.llActionButton.layoutParams as LayoutParams
                    if (isCollapsed) {
                        actionLp.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, resources.displayMetrics).toInt()
                        actionLp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, resources.displayMetrics).toInt()
                        actionLp.marginStart = 0
                        actionLp.marginEnd = 0
                        b.llActionButton.setPadding(0, 0, 0, 0)
                        b.tvActionText.visibility = View.GONE

                        val plusLp = b.ivActionPlus.layoutParams as android.widget.LinearLayout.LayoutParams
                        plusLp.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 11f, resources.displayMetrics).toInt()
                        plusLp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 11f, resources.displayMetrics).toInt()
                        b.ivActionPlus.layoutParams = plusLp
                    } else {
                        actionLp.width = LayoutParams.MATCH_CONSTRAINT
                        actionLp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics).toInt()
                        actionLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                        actionLp.marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                        b.llActionButton.setPadding(
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt(),
                            0,
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt(),
                            0
                        )
                        b.tvActionText.visibility = View.VISIBLE
                        b.tvActionText.text = item.label.removePrefix("+").trim()

                        val plusLp = b.ivActionPlus.layoutParams as android.widget.LinearLayout.LayoutParams
                        plusLp.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                        plusLp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                        b.ivActionPlus.layoutParams = plusLp
                    }
                    b.llActionButton.layoutParams = actionLp
                    return
                } else {
                    b.llActionButton.visibility = View.GONE
                }

                if (isCollapsed) {
                    b.tvLabel.visibility = View.GONE
                    b.ivExpandChevron.visibility = View.GONE

                    if (hasIcon) {
                        b.ivIcon.visibility = View.VISIBLE
                        b.tvMonogram.visibility = View.GONE
                        b.ivIcon.setImageResource(item.iconRes!!)

                        val lp = b.ivIcon.layoutParams as LayoutParams
                        lp.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
                        lp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
                        lp.startToStart = LayoutParams.PARENT_ID
                        lp.endToEnd = LayoutParams.PARENT_ID
                        lp.marginStart = 0
                        b.ivIcon.layoutParams = lp

                        val tintColor = if (isSelected) colorSelectedText else colorMuted
                        b.ivIcon.setColorFilter(tintColor)
                    } else {
                        b.ivIcon.visibility = View.GONE
                        b.tvMonogram.visibility = View.VISIBLE
                        b.tvMonogram.text = monogram(item.label)
                        
                        b.tvMonogram.setBackgroundResource(R.drawable.bg_monogram_tile)
                        if (isSelected) {
                            b.tvMonogram.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                            b.tvMonogram.setTextColor(colorSelectedText)
                            b.tvMonogram.typeface = medFont
                        } else {
                            b.tvMonogram.backgroundTintList = ColorStateList.valueOf(colorTileUnselected)
                            b.tvMonogram.setTextColor(colorMuted)
                            b.tvMonogram.typeface = regFont
                        }

                        val lp = b.tvMonogram.layoutParams as LayoutParams
                        lp.startToStart = LayoutParams.PARENT_ID
                        lp.endToEnd = LayoutParams.PARENT_ID
                        lp.marginStart = 0
                        b.tvMonogram.layoutParams = lp
                    }

                    if (item.isEnabled != null) {
                        b.ivDotIndicator.visibility = View.VISIBLE
                        b.ivDotIndicator.setImageResource(R.drawable.bg_rail_dot)
                        val dotColor = if (item.isEnabled) colorSelectedText else colorDotDisabled
                        b.ivDotIndicator.imageTintList = ColorStateList.valueOf(dotColor)
                        b.ivDotIndicator.isClickable = false
                        b.ivDotIndicator.isFocusable = false
                    } else {
                        b.ivDotIndicator.visibility = View.GONE
                    }

                    if (hasSub) {
                        b.ivCaret.visibility = View.VISIBLE
                        b.ivCaret.rotation = if (item.isSubListExpanded) 180f else 0f
                        val caretTint = if (isSelected) colorSelectedText else colorMuted
                        b.ivCaret.setColorFilter(caretTint)
                    } else {
                        b.ivCaret.visibility = View.GONE
                    }

                } else {
                    b.tvLabel.visibility = View.VISIBLE
                    b.tvLabel.text = item.label
                    b.ivCaret.visibility = View.GONE

                    if (isSelected) {
                        b.tvLabel.setTextColor(colorSelectedText)
                        b.tvLabel.typeface = medFont
                    } else {
                        b.tvLabel.setTextColor(colorUnselectedText)
                        b.tvLabel.typeface = regFont
                    }

                    if (hasIcon) {
                        b.ivIcon.visibility = View.VISIBLE
                        b.tvMonogram.visibility = View.GONE
                        b.ivIcon.setImageResource(item.iconRes!!)

                        val iconLp = b.ivIcon.layoutParams as LayoutParams
                        iconLp.width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
                        iconLp.height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics).toInt()
                        iconLp.startToStart = LayoutParams.PARENT_ID
                        iconLp.endToStart = LayoutParams.UNSET
                        iconLp.endToEnd = LayoutParams.UNSET
                        iconLp.horizontalChainStyle = LayoutParams.CHAIN_SPREAD
                        iconLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                        b.ivIcon.layoutParams = iconLp

                        val labelLp = b.tvLabel.layoutParams as LayoutParams
                        labelLp.startToStart = LayoutParams.UNSET
                        labelLp.startToEnd = b.ivIcon.id
                        labelLp.endToEnd = LayoutParams.UNSET
                        labelLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics).toInt()
                        b.tvLabel.layoutParams = labelLp

                        val tintColor = if (isSelected) colorSelectedText else colorMuted
                        b.ivIcon.setColorFilter(tintColor)
                    } else {
                        b.ivIcon.visibility = View.GONE
                        b.tvMonogram.visibility = View.GONE

                        val labelLp = b.tvLabel.layoutParams as LayoutParams
                        labelLp.startToEnd = LayoutParams.UNSET
                        labelLp.startToStart = LayoutParams.PARENT_ID
                        labelLp.endToEnd = LayoutParams.UNSET
                        labelLp.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
                        b.tvLabel.layoutParams = labelLp
                    }

                    if (hasSub) {
                        b.ivExpandChevron.visibility = View.VISIBLE
                        b.ivExpandChevron.rotation = if (item.isSubListExpanded) 180f else 0f
                    } else {
                        b.ivExpandChevron.visibility = View.GONE
                    }

                    if (item.isEnabled != null) {
                        b.ivDotIndicator.visibility = View.VISIBLE
                        b.ivDotIndicator.setImageResource(R.drawable.bg_rail_dot)
                        val dotColor = if (item.isEnabled) colorSelectedText else colorDotDisabled
                        b.ivDotIndicator.imageTintList = ColorStateList.valueOf(dotColor)
                        b.ivDotIndicator.isClickable = false
                        b.ivDotIndicator.isFocusable = false
                    } else {
                        b.ivDotIndicator.visibility = View.GONE
                    }
                }
            }
        }

        inner class SubViewHolder(val binding: ItemRailSubcategoryBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(fItem: FlattenedItem.Sub) {
                val parent = fItem.parentItem
                val sub = fItem.subItem
                val b = binding

                val regFont = ResourcesCompat.getFont(context, R.font.regular)
                val medFont = ResourcesCompat.getFont(context, R.font.medium)

                val colorSelectedText = ContextCompat.getColor(context, R.color.rail_selected_text)
                val colorMuted = ContextCompat.getColor(context, R.color.rail_muted)

                val isSubSelected = (sub.id == parent.selectedSubItemId) ||
                        (parent.selectedSubItemId == null && sub.id.equals("All", ignoreCase = true))

                b.clSubCategoryRow.contentDescription = sub.label

                b.clSubCategoryRow.setOnLongClickListener {
                    Toast.makeText(context, sub.label, Toast.LENGTH_SHORT).show()
                    true
                }

                b.clSubCategoryRow.addPressEffect {
                    val mutable = items.toMutableList()
                    val curParent = mutable[fItem.parentIndex]
                    mutable[fItem.parentIndex] = curParent.copy(selectedSubItemId = sub.id)
                    items = mutable
                    if (selectedCategoryId != parent.id) {
                        selectedCategoryId = parent.id
                    }
                    rebuildAndNotify()
                    onSubCategorySelectedListener?.invoke(parent, sub)
                }

                if (isCollapsed) {
                    b.tvSubLabel.visibility = View.GONE
                    b.tvSubMonogram.visibility = View.VISIBLE
                    b.tvSubMonogram.text = monogram(sub.label)

                    b.tvSubMonogram.backgroundTintList = null
                    if (isSubSelected) {
                        b.tvSubMonogram.setBackgroundResource(R.drawable.bg_monogram_tile_stroked)
                        b.tvSubMonogram.setTextColor(colorSelectedText)
                        b.tvSubMonogram.typeface = medFont
                    } else {
                        b.tvSubMonogram.setBackgroundResource(R.drawable.bg_monogram_sub_unselected)
                        b.tvSubMonogram.setTextColor(colorMuted)
                        b.tvSubMonogram.typeface = regFont
                    }
                } else {
                    b.tvSubMonogram.visibility = View.GONE
                    b.tvSubLabel.visibility = View.VISIBLE
                    b.tvSubLabel.text = sub.label

                    if (isSubSelected) {
                        b.tvSubLabel.setTextColor(colorSelectedText)
                        b.tvSubLabel.typeface = medFont
                    } else {
                        b.tvSubLabel.setTextColor(colorMuted)
                        b.tvSubLabel.typeface = regFont
                    }
                }
            }
        }
    }
}
