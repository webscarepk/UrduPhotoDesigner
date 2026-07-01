package com.webscare.urducanvas.ui.navigation.settings.subscriptions

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.LayoutSubscriptionsItemBinding

class SubscriptionsAdapter(
    private val onSelect: (SubscriptionPlan) -> Unit
) : RecyclerView.Adapter<SubscriptionsAdapter.VH>() {

    private val items = mutableListOf<SubscriptionPlan>()

    fun submitList(list: List<SubscriptionPlan>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged() // ✅ fine here — only called on entry
    }

    /**
     * Programmatic selection — used when the carousel snaps to a new page
     * from a swipe. Does NOT invoke [onSelect]; the carousel's own
     * scroll-settle handler is the source of truth for that, same way a
     * click here is the source of truth for a tap.
     */
    fun selectPosition(position: Int) = selectAt(position, notifyCaller = false)

    private fun selectAt(position: Int, notifyCaller: Boolean) {
        if (position !in items.indices) return
        val previousIndex = items.indexOfFirst { it.isSelected }
        if (previousIndex == position) return

        // ✅ Only update 2 items, no full rebind = no animation retrigger
        if (previousIndex != -1) {
            items[previousIndex].isSelected = false
            notifyItemChanged(previousIndex, "selection") // payload = no flicker
        }
        items[position].isSelected = true
        notifyItemChanged(position, "selection")

        if (notifyCaller) onSelect(items[position])
    }

    /** Index of the plan with the highest discount — that one gets the "BEST VALUE" ribbon. */
    private fun bestValueIndex(): Int =
        items.indices.filter { items[it].hasDiscount }
            .maxByOrNull { items[it].discountPercent } ?: -1

    inner class VH(val binding: LayoutSubscriptionsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubscriptionPlan) = with(binding) {
            title.text = item.title
            price.text = item.price
            duration.text = item.duration

            bindSaveLine(item)
            bestValueBadge.isVisible = adapterPosition == bestValueIndex()

            updateSelection(item.isSelected)

            root.addPressEffect {
                val position = items.indexOf(item)
                selectAt(position, notifyCaller = true)
            }
        }

        private fun bindSaveLine(item: SubscriptionPlan) = with(binding) {
            val ctx = root.context
            if (item.hasDiscount) {
                // Solid pill — much more prominent than plain colored text.
                saveBadge.text = ctx.getString(R.string.sub_save_percent, item.discountPercent)
                saveBadge.background = ContextCompat.getDrawable(ctx, R.drawable.sub_discount_pill)
                saveBadge.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.contrast)
                )
                saveBadge.setTextColor(ContextCompat.getColor(ctx, R.color.appColor))
                saveBadge.setPadding(dp(9f), dp(3f), dp(9f), dp(3f))
                saveBadge.alpha = 1f
            } else {
                // Em-dash placeholder, no pill — keeps all three cards the
                // same height even when this plan has no discount to show.
                saveBadge.text = ctx.getString(R.string.sub_save_placeholder)
                saveBadge.background = null
                saveBadge.setPadding(0, 0, 0, 0)
                saveBadge.setTextColor(ContextCompat.getColor(ctx, R.color.gray))
                saveBadge.alpha = 0.6f
            }
        }

        fun updateSelection(isSelected: Boolean) = with(binding) {
            // Border highlight only — no radio circle in the compact card design.
            mainCard.strokeWidth = if (isSelected) dp(1.5f) else dp(1f)
            mainCard.strokeColor = ContextCompat.getColor(
                root.context,
                if (isSelected) R.color.appColor else R.color.sub_divider
            )
        }

        private fun dp(v: Float) =
            (v * itemView.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = LayoutSubscriptionsItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    // ✅ Partial rebind — only updates stroke, never triggers layout animation
    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "selection") {
            holder.updateSelection(items[position].isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}