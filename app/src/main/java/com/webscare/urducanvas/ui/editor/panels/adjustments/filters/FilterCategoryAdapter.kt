package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.R

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
        val cardView: MaterialCardView? = itemView as? MaterialCardView ?: itemView.findViewById(R.id.categoryCard)
        val textView: TextView = itemView.findViewById(R.id.categoryName) ?: TextView(itemView.context)
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
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val blackColor = ContextCompat.getColor(context, R.color.black)
        val strokeColor = ContextCompat.getColor(context, R.color.light_gray)

        holder.cardView?.setCardBackgroundColor(whiteColor)

        if (isSelected) {
            holder.cardView?.strokeColor = appColor
            holder.cardView?.strokeWidth = (1.5f * context.resources.displayMetrics.density + 0.5f).toInt()
            holder.textView.setTextColor(appColor)
            holder.textView.setTypeface(null, Typeface.BOLD)
        } else {
            holder.cardView?.strokeColor = strokeColor
            holder.cardView?.strokeWidth = (1f * context.resources.displayMetrics.density + 0.5f).toInt()
            holder.textView.setTextColor(blackColor)
            holder.textView.setTypeface(null, Typeface.NORMAL)
        }

        holder.itemView.setOnClickListener {
            selectedCategory = category
            onCategorySelected(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}
