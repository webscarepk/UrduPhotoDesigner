package com.webscare.urducanvas.ui.navigation.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.SubscriptionPlan
import com.webscare.urducanvas.databinding.LayoutSubscriptionsItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class SubscriptionsAdapter(
    private val onSelect: (SubscriptionPlan) -> Unit
) : RecyclerView.Adapter<SubscriptionsAdapter.VH>() {

    private val items = mutableListOf<SubscriptionPlan>()

    fun submitList(list: List<SubscriptionPlan>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val binding: LayoutSubscriptionsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubscriptionPlan) = with(binding) {

            title.text = item.title
            price.text = item.price
            duration.text = item.duration

            // Badge
            saveBadge.apply {
                isVisible = item.badge == null
                text = item.badge
            }

            // Selection UI
            if (item.isSelected) {
                mainCard.strokeWidth = 1
                mainCard.strokeColor =
                    ContextCompat.getColor(root.context, R.color.appColor)
            } else {
                mainCard.strokeWidth = 0
                mainCard.strokeColor =
                    ContextCompat.getColor(root.context, R.color.appColor)
            }

            root.addPressEffect {
                items.forEach { it.isSelected = false }
                item.isSelected = true
                notifyDataSetChanged()
                onSelect(item)
            }
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
}
