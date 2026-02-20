package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.FilterItem
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutFilterItemBinding // You'll need to create this layout
import androidx.core.graphics.scale
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class ImageFiltersAdapter(
    private val filterList: List<com.webscare.urducanvas.common.canvas.model.FilterItem>,
    private val baseBitmap: Bitmap?,
    private val onFilterSelected: (com.webscare.urducanvas.common.canvas.model.FilterItem) -> Unit
) : RecyclerView.Adapter<ImageFiltersAdapter.FilterViewHolder>() {

    // Keep track of the currently selected filter
    var selectedFilter: com.webscare.urducanvas.common.canvas.sealed.ImageFilter? = null
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
        fun bind(filterItem: com.webscare.urducanvas.common.canvas.model.FilterItem) {
            binding.filterName.text = filterItem.name

            baseBitmap?.let { bmp ->
                val maxSize = 150
                val aspectRatio = bmp.width.toFloat() / bmp.height.toFloat()
                val (targetWidth, targetHeight) = if (aspectRatio > 1) {
                    maxSize to (maxSize / aspectRatio).toInt()
                } else {
                    (maxSize * aspectRatio).toInt() to maxSize
                }
                val thumb = bmp.scale(targetWidth, targetHeight)
                val filteredThumb = _root_ide_package_.com.webscare.urducanvas.common.utils.ImageProcessor.applyFilter(thumb, filterItem.filter)
                binding.filterPreview.setImageBitmap(filteredThumb)
            }

            val isCurrentItemSelected = filterItem.filter == selectedFilter

            binding.card.strokeWidth = if (isCurrentItemSelected) 4 else 0

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
