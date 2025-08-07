package com.example.urduphotodesigner.ui.editor.panels.filters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.FilterItem
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutFilterItemBinding // You'll need to create this layout

class ImageFiltersAdapter(
    private val filterList: List<FilterItem>,
    private val onFilterSelected: (FilterItem) -> Unit
) : RecyclerView.Adapter<ImageFiltersAdapter.FilterViewHolder>() {

    // Keep track of the currently selected filter
    var selectedFilter: ImageFilter? = null
        set(value) {
            // Only update if the filter actually changed
            if (field != value) {
                val oldSelectedPosition = filterList.indexOfFirst { it.filter == field }
                val newSelectedPosition = filterList.indexOfFirst { it.filter == value }

                field = value // Update the backing field

                // Notify only the items whose selection state has changed
                if (oldSelectedPosition != -1) {
                    notifyItemChanged(oldSelectedPosition)
                }
                if (newSelectedPosition != -1 && newSelectedPosition != oldSelectedPosition) {
                    notifyItemChanged(newSelectedPosition)
                }
            }
        }

    inner class FilterViewHolder(val binding: LayoutFilterItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(filterItem: FilterItem) {
            binding.filterName.text = filterItem.name

            // Determine if the current item should be selected based on the adapter's selectedFilter
            val isCurrentItemSelected = filterItem.filter == selectedFilter

            if (isCurrentItemSelected) {
                binding.card.strokeWidth = 4
                binding.card.setCardBackgroundColor(Color.WHITE)
                binding.card.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.white)
            } else {
                binding.card.strokeWidth = 0
                binding.card.setCardBackgroundColor(Color.WHITE)
            }

            binding.card.addPressEffect {
                onFilterSelected.invoke(filterItem)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding =
            LayoutFilterItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FilterViewHolder(binding)
    }

    override fun getItemCount() = filterList.size

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(filterList[position])
    }
}
