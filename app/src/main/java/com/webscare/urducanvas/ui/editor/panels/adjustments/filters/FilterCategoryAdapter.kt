package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class FilterCategoryAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<FilterCategoryAdapter.ViewHolder>() {

    private var recyclerView: RecyclerView? = null

    var selectedCategory: String = categories.firstOrNull() ?: "All"
        set(value) {
            if (field != value) {
                val oldIndex = categories.indexOf(field)
                field = value
                val newIndex = categories.indexOf(value)

                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
                if (newIndex != -1) {
                    smoothScrollToPosition(newIndex)
                }
            }
        }

    private fun smoothScrollToPosition(position: Int) {
        val rv = recyclerView ?: return
        val scroller = object : LinearSmoothScroller(rv.context) {
            override fun getHorizontalSnapPreference(): Int = SNAP_TO_ANY
        }
        scroller.targetPosition = position
        rv.layoutManager?.startSmoothScroll(scroller)
    }

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        recyclerView = null
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.categoryName)
        val indicator: View = itemView.findViewById(R.id.selectionIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_filter_category_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.textView.text = category

        val isSelected = (category == selectedCategory)
        val context = holder.itemView.context

        val appColor = ContextCompat.getColor(context, R.color.appColor)
        val mutedColor = ContextCompat.getColor(context, R.color.rail_muted)

        val fontRes = if (isSelected) R.font.bold else R.font.medium
        holder.textView.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, fontRes)

        if (isSelected) {
            holder.textView.setTextColor(appColor)
            holder.indicator.visibility = View.VISIBLE
        } else {
            holder.textView.setTextColor(mutedColor)
            holder.indicator.visibility = View.INVISIBLE
        }

        holder.itemView.addPressEffect {
            selectedCategory = category
            onCategorySelected(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}
