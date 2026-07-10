package com.webscare.urducanvas.ui.editor.export

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ExportViewType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutFormatsItemBinding
import com.webscare.urducanvas.databinding.LayoutQualityItemBinding
import com.webscare.urducanvas.databinding.LayoutResolutionsItemBinding
import com.webscare.urducanvas.databinding.LayoutResolutionsItemPrefsBinding

class ExportOptionAdapter<T>(
    private var items: List<T>,
    private val viewType: ExportViewType,
    private val displayMode: Boolean,
    private val onItemSelected: (T) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var isSubscribed: Boolean = false

    override fun getItemCount(): Int = items.size
    override fun getItemViewType(position: Int) = viewType.ordinal

    fun updateList(newItems: List<T>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (displayMode) {
            true -> when (ExportViewType.entries[this.viewType.ordinal]) {
                ExportViewType.RESOLUTION -> ResolutionViewHolder(
                    LayoutResolutionsItemBinding.inflate(inflater, parent, false),
                )

                ExportViewType.QUALITY -> QualityViewHolder(
                    LayoutQualityItemBinding.inflate(inflater, parent, false),
                )

                ExportViewType.FORMAT -> FormatViewHolder(
                    LayoutFormatsItemBinding.inflate(inflater, parent, false),
                )
            }

            false -> CompactViewHolder(
                LayoutResolutionsItemPrefsBinding.inflate(inflater, parent, false),
            )
        }
    }

    inner class CompactViewHolder(private val binding: LayoutResolutionsItemPrefsBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Any) {
            var isSelected = false
            when (item) {
                is com.webscare.urducanvas.common.canvas.model.ExportResolution -> {
                    binding.title.text = item.name
                    isSelected = item.isSelected
                }

                is com.webscare.urducanvas.common.canvas.model.ExportQuality -> {
                    binding.title.text = item.label
                    isSelected = item.isSelected
                }

                is com.webscare.urducanvas.common.canvas.model.ExportFormat -> {
                    binding.title.text = item.name
                    isSelected = item.isSelected
                }
            }

            // ✅ show drawableEnd checkmark if selected, else remove
            val checkDrawable = if (isSelected) {
                ContextCompat.getDrawable(
                    binding.root.context,
                    R.drawable.ic_done,
                )
            } else {
                null
            }
            binding.title.setCompoundDrawablesWithIntrinsicBounds(null, null, checkDrawable, null)

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ExportOptionAdapter<*>.ResolutionViewHolder -> holder.bind(
                items[position] as com.webscare.urducanvas.common.canvas.model.ExportResolution,
            )
            is ExportOptionAdapter<*>.QualityViewHolder -> holder.bind(
                items[position] as com.webscare.urducanvas.common.canvas.model.ExportQuality,
            )
            is ExportOptionAdapter<*>.FormatViewHolder -> holder.bind(
                items[position] as com.webscare.urducanvas.common.canvas.model.ExportFormat,
            )
            is ExportOptionAdapter<*>.CompactViewHolder -> holder.bind(items[position] as Any)
        }
    }

    inner class ResolutionViewHolder(private val binding: LayoutResolutionsItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: com.webscare.urducanvas.common.canvas.model.ExportResolution) {
            binding.resolutionTitle.text = item.name
            binding.resolutionValue.text = item.label
            binding.resolutionDesc.text = item.description
            binding.resolutionDiff.text = String.format("~%.1f MB", item.estimatedSizeKb / 1000f)

            binding.isPremium.isVisible = item.isPremium && !isSubscribed
            // Apply UI changes based on selection state
            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.card_bg_selected)
                binding.done.visibility = View.VISIBLE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.appColor)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, android.R.color.white),
                )
            } else {
                binding.root.setBackgroundResource(R.drawable.card_bg)
                binding.done.visibility = View.GONE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.contrast)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.gray),
                )
            }

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }

    inner class QualityViewHolder(private val binding: LayoutQualityItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: com.webscare.urducanvas.common.canvas.model.ExportQuality) {
            binding.resolutionTitle.text = item.label
            binding.resolutionValue.text = item.description
            binding.resolutionDesc.text = "${item.quality}%"
            binding.resolutionDiff.text = when {
                item.extraSizePercent > 0 -> "+${item.extraSizePercent}%"
                item.extraSizePercent < 0 -> "${item.extraSizePercent}%"
                else -> "Base"
            }

            when (adapterPosition) {
                0 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.quality_high_light),
                )

                1 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.quality_medium_light),
                )

                2 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.quality_low_light),
                )
            }
            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.card_bg_selected)
                binding.done.visibility = View.VISIBLE
            } else {
                binding.root.setBackgroundResource(R.drawable.card_bg)
                binding.done.visibility = View.GONE
            }

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }

    inner class FormatViewHolder(private val binding: LayoutFormatsItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: com.webscare.urducanvas.common.canvas.model.ExportFormat) {
            binding.resolutionTitle.text = item.name
            binding.resolutionDesc.text = item.description
            binding.resolutionDiff.text = when {
                item.format != null -> ".${item.format.name.lowercase()}"
                else -> ".pdf"
            }
            val tags = item.tags
            binding.resolutionValue.text = tags.getOrNull(0) ?: ""
            binding.resolutionValue2.text = tags.getOrNull(1) ?: ""
            binding.resolutionValue3.text = tags.getOrNull(2) ?: ""

            binding.resolutionValue.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            binding.resolutionValue2.visibility = if (tags.size > 1) View.VISIBLE else View.GONE
            binding.resolutionValue3.visibility = if (tags.size > 2) View.VISIBLE else View.GONE

            binding.isPremium.isVisible = item.isPremium && !isSubscribed
            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.card_bg_selected)
                binding.done.visibility = View.VISIBLE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.appColor)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, android.R.color.white),
                )
            } else {
                binding.root.setBackgroundResource(R.drawable.card_bg)
                binding.done.visibility = View.GONE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.contrast)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.gray),
                )
            }

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }
}
