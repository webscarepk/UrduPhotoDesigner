package com.webscare.urducanvas.ui.editor.panels.adjustments.filters

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.FilterItem
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutFilterItemBinding

class ImageFiltersAdapter(
    private val filterList: List<FilterItem>,
    private var baseBitmap: Bitmap?,
    private val onFilterSelected: (FilterItem) -> Unit,
    private val onFilterReSelected: ((FilterItem) -> Unit)? = null
) : RecyclerView.Adapter<ImageFiltersAdapter.FilterViewHolder>() {

    // Keep track of the currently selected filter
    var selectedFilter: ImageFilter? = null
        set(value) {
            if (field != value) {
                val oldSelectedPosition = filterList.indexOfFirst { it.filter == field }
                val newSelectedPosition = filterList.indexOfFirst { it.filter == value }

                field = value

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
        fun bind(filterItem: FilterItem, position: Int) {
            binding.filterView.scaleX = 1f
            binding.filterView.scaleY = 1f

            binding.filterName.text = filterItem.name

            val isFirstOfCategory = position > 0 &&
                    filterItem.filter.category != filterList[position - 1].filter.category
            binding.categoryDivider.visibility = if (isFirstOfCategory) View.VISIBLE else View.GONE

            baseBitmap?.let { bmp ->
                if (bmp.isRecycled) return@let

                val filteredThumb = try {
                    val minDim = minOf(bmp.width, bmp.height)
                    val x = (bmp.width - minDim) / 2
                    val y = (bmp.height - minDim) / 2
                    val cropped = Bitmap.createBitmap(bmp, x, y, minDim, minDim)
                    val thumb = cropped.scale(150, 150)
                    if (cropped !== bmp && !cropped.isRecycled) cropped.recycle()
                    ImageProcessor.applyFilter(thumb, filterItem.filter)
                } catch (e: Throwable) {
                    if (e is OutOfMemoryError) System.gc()
                    null
                }

                filteredThumb?.let { binding.filterPreview.setImageBitmap(it) }
            }

            val isCurrentItemSelected = filterItem.filter == selectedFilter
            val context = binding.root.context

            binding.card.strokeWidth = if (isCurrentItemSelected) 4 else 0

            // Font & Text Color
            val fontRes = if (isCurrentItemSelected) R.font.bold else R.font.regular
            binding.filterName.typeface = ResourcesCompat.getFont(context, fontRes)
            val textColorRes = if (isCurrentItemSelected) R.color.appColor else R.color.black
            binding.filterName.setTextColor(ContextCompat.getColor(context, textColorRes))

            // Dim Overlay + Adjust Icon for Selected non-None Filters
            val isNonNoneSelected = isCurrentItemSelected && filterItem.filter !is ImageFilter.None
            if (isNonNoneSelected) {
                val isNightMode = (context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

                binding.selectedOverlay.visibility = View.VISIBLE
                binding.selectedOverlay.setBackgroundColor(
                    if (isNightMode) Color.parseColor("#66FFFFFF") else Color.parseColor("#66000000")
                )

                binding.adjustIcon.visibility = View.VISIBLE
                binding.adjustIcon.setColorFilter(if (isNightMode) Color.BLACK else Color.WHITE)
            } else {
                binding.selectedOverlay.visibility = View.GONE
                binding.adjustIcon.visibility = View.GONE
            }

            binding.filterView.addPressEffect {
                if (isCurrentItemSelected && filterItem.filter !is ImageFilter.None) {
                    onFilterReSelected?.invoke(filterItem)
                } else {
                    onFilterSelected.invoke(filterItem)
                }
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
        holder.bind(filterList[position], position)
    }

    fun updatePreviewBitmap(newBitmap: Bitmap?) {
        val old = this.baseBitmap
        this.baseBitmap = newBitmap
        old?.recycle()
        notifyDataSetChanged()
    }
}
