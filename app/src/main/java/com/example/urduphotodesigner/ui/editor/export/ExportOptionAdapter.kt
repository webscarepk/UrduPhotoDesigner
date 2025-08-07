package com.example.urduphotodesigner.ui.editor.export

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.enums.ExportViewType
import com.example.urduphotodesigner.common.canvas.model.ExportFormat
import com.example.urduphotodesigner.common.canvas.model.ExportQuality
import com.example.urduphotodesigner.common.canvas.model.ExportResolution
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutFormatsItemBinding
import com.example.urduphotodesigner.databinding.LayoutQualityItemBinding
import com.example.urduphotodesigner.databinding.LayoutResolutionsItemBinding

class ExportOptionAdapter<T>(
    private val items: List<T>,
    private val viewType: ExportViewType,
    private val onItemSelected: (T) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = items.size
    override fun getItemViewType(position: Int) = viewType.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (ExportViewType.entries[viewType]) {
            ExportViewType.RESOLUTION -> ResolutionViewHolder(
                LayoutResolutionsItemBinding.inflate(inflater, parent, false)
            )
            ExportViewType.QUALITY -> QualityViewHolder(
                LayoutQualityItemBinding.inflate(inflater, parent, false)
            )
            ExportViewType.FORMAT -> FormatViewHolder(
                LayoutFormatsItemBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ExportOptionAdapter<*>.ResolutionViewHolder -> holder.bind(items[position] as ExportResolution)
            is ExportOptionAdapter<*>.QualityViewHolder -> holder.bind(items[position] as ExportQuality)
            is ExportOptionAdapter<*>.FormatViewHolder -> holder.bind(items[position] as ExportFormat)
        }
    }

    inner class ResolutionViewHolder(
        private val binding: LayoutResolutionsItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportResolution) {
            binding.resolutionTitle.text = item.name
            binding.resolutionValue.text = item.label
            binding.resolutionDesc.text = item.description
            binding.resolutionDiff.text = String.format("~%.1f MB", item.estimatedSizeKb / 1000f)

            // Apply UI changes based on selection state
            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.card_bg_selected)
                binding.done.visibility = View.VISIBLE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.appColor)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            } else {
                binding.root.setBackgroundResource(R.drawable.card_bg)
                binding.done.visibility = View.GONE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.contrast)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.gray)
                )
            }

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }

    inner class QualityViewHolder(
        private val binding: LayoutQualityItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportQuality) {
            binding.resolutionTitle.text = item.label
            binding.resolutionValue.text = item.description
            binding.resolutionDesc.text = "${item.quality}%"
            binding.resolutionDiff.text =
                when {
                    item.extraSizePercent > 0 -> "+${item.extraSizePercent}%"
                    item.extraSizePercent < 0 -> "${item.extraSizePercent}%"
                    else -> "Base"
                }

            when(adapterPosition){
                0 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.gray)
                )
                1 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.yellow )
                )
                2 -> binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.appColor)
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

    inner class FormatViewHolder(
        private val binding: LayoutFormatsItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportFormat) {
            binding.resolutionTitle.text = item.name
            binding.resolutionDesc.text = item.description
            binding.resolutionDiff.text = ".${item.format.name.lowercase()}"

            val tags = item.tags
            binding.resolutionValue.text = tags.getOrNull(0) ?: ""
            binding.resolutionValue2.text = tags.getOrNull(1) ?: ""
            binding.resolutionValue3.text = tags.getOrNull(2) ?: ""

            binding.resolutionValue.visibility = if (tags.isNotEmpty()) View.VISIBLE else View.GONE
            binding.resolutionValue2.visibility = if (tags.size > 1) View.VISIBLE else View.GONE
            binding.resolutionValue3.visibility = if (tags.size > 2) View.VISIBLE else View.GONE

            if (item.isSelected) {
                binding.root.setBackgroundResource(R.drawable.card_bg_selected)
                binding.done.visibility = View.VISIBLE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.appColor)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, android.R.color.white)
                )
            } else {
                binding.root.setBackgroundResource(R.drawable.card_bg)
                binding.done.visibility = View.GONE
                binding.view1.backgroundTintList =
                    ContextCompat.getColorStateList(binding.root.context, R.color.contrast)
                binding.view1.setColorFilter(
                    ContextCompat.getColor(binding.root.context, R.color.gray)
                )
            }

            binding.root.addPressEffect {
                onItemSelected(item as T)
            }
        }
    }

}
