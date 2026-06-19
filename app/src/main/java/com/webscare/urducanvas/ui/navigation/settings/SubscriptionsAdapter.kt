package com.webscare.urducanvas.ui.navigation.settings

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

    inner class VH(val binding: LayoutSubscriptionsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubscriptionPlan) = with(binding) {
            title.text = item.title
            price.text = item.price
            duration.text = item.duration

            saveBadge.apply {
                isVisible = item.badge != null
                text = item.badge
            }

            updateSelection(item.isSelected)

            root.addPressEffect {
                val position = items.indexOf(item)
                selectAt(position, notifyCaller = true)
            }
        }

        fun updateSelection(isSelected: Boolean) = with(binding) {
            // Border highlight…
            mainCard.strokeWidth = if (isSelected) 1 else 0
            mainCard.strokeColor = ContextCompat.getColor(root.context, R.color.appColor)
            // …and radio circle, moved together.
            radio.setImageResource(
                if (isSelected) R.drawable.ic_radio_selected
                else R.drawable.ic_radio_unselected
            )
        }
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

    // ✅ Partial rebind — only updates stroke + radio, never triggers layout animation
    override fun onBindViewHolder(holder: VH, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "selection") {
            holder.updateSelection(items[position].isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}