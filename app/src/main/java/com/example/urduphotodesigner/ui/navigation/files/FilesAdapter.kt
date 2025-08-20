package com.example.urduphotodesigner.ui.navigation.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.Utils.addPressEffectWithLongClick
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.LayoutFilesGridBinding
import com.example.urduphotodesigner.databinding.LayoutFilesRowBinding

class FilesAdapter(
    private var items: List<Any>,
    private var isGrid: Boolean = true,
    private val onItemClick: (Any) -> Unit,
    private val onItemLongClick: (Any) -> Unit,
    private val onOptionsClick: (Any, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    var multiSelectMode = false
        private set

    private val selectedItems = mutableSetOf<Any>()

    fun toggleMultiSelectMode(enabled: Boolean) {
        multiSelectMode = enabled
        if (!enabled) selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<Any> = selectedItems.toList()

    override fun getItemViewType(position: Int): Int {
        return if (isGrid) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = LayoutFilesGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GridViewHolder(binding)
        } else {
            val binding = LayoutFilesRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is GridViewHolder -> holder.bind(item)
            is ListViewHolder -> holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<Any>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun toggleViewType(isGrid: Boolean) {
        this.isGrid = isGrid
        notifyDataSetChanged()
    }

    private fun handleSelection(item: Any) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
        if (selectedItems.isEmpty()) {
            toggleMultiSelectMode(false)
        } else {
            notifyDataSetChanged()
        }
    }

    inner class GridViewHolder(private val binding: LayoutFilesGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {

            bindCommon(item, binding.root, binding.moreOptions, binding)

            binding.root.addPressEffectWithLongClick(
                onClick = {
                    if (multiSelectMode) {
                        handleSelection(item)
                    } else {
                        onItemClick(item)
                    }
                },
                onLongClick = {
                    if (!multiSelectMode) {
                        toggleMultiSelectMode(true)
                    }
                    onItemLongClick(item)
                    handleSelection(item)
                }
            )

            binding.moreOptions.addPressEffect { onOptionsClick(item, binding.root) }
        }
    }

    inner class ListViewHolder(private val binding: LayoutFilesRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {
            bindCommon(item, binding.root, binding.moreOptions, binding)

            binding.root.addPressEffectWithLongClick(
                onClick = {
                    if (multiSelectMode) {
                        handleSelection(item)
                    } else {
                        onItemClick(item)
                    }
                },
                onLongClick = {
                    if (!multiSelectMode) {
                        toggleMultiSelectMode(true)
                    }
                    onItemLongClick(item)
                    handleSelection(item)
                }
            )

            binding.moreOptions.addPressEffect { onOptionsClick(item, binding.root) }
        }
    }

    private fun bindCommon(item: Any, root: View, optionsView: View, binding: Any) {
        when (binding) {
            is LayoutFilesGridBinding -> {
                when (item) {
                    is ImageEntity -> {
                        binding.assetName.text = item.file_name
                        binding.metaData.text =
                            "Image - ${formatSize(item.file_size)} - ${item.created_at}"

                        val isPng = item.file_name.endsWith(".png", ignoreCase = true)
                        binding.image.scaleType =
                            if (isPng) android.widget.ImageView.ScaleType.CENTER
                            else android.widget.ImageView.ScaleType.CENTER_CROP

                        Glide.with(binding.image).load(item.bitmapData).into(binding.image)

                    }

                    is FontEntity -> {
                        binding.assetName.text = item.font_name
                        binding.metaData.text =
                            "Font - ${formatSize(item.file_size)} - ${item.created_at}"
                        binding.image.scaleType = android.widget.ImageView.ScaleType.CENTER
                        binding.image.setImageResource(R.drawable.ic_font_thumbnail)
                    }

                    is ExportResult -> {
                        binding.assetName.text = item.fileName
                        binding.metaData.text =
                            "Project - ${formatSize(item.fileSizeMB)} - ${item.exportDate}"
                        binding.image.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        Glide.with(binding.image).load(item.imagePath).into(binding.image)
                    }
                }
            }

            is LayoutFilesRowBinding -> {
                when (item) {
                    is ImageEntity -> {
                        binding.assetName.text = item.file_name
                        binding.metaData.text =
                            "Image - ${formatSize(item.file_size)} - ${item.created_at}"
                        Glide.with(binding.image).load(item.bitmapData).into(binding.image)

                    }

                    is FontEntity -> {
                        binding.assetName.text = item.font_name
                        binding.metaData.text =
                            "Font - ${formatSize(item.file_size)} - ${item.created_at}"
                        Glide.with(binding.image).load(item.font_image).into(binding.image)
                    }

                    is ExportResult -> {
                        binding.assetName.text = item.fileName
                        binding.metaData.text =
                            "Project - ${formatSize(item.fileSizeMB)} - ${item.exportDate}"
                        Glide.with(binding.image).load(item.imagePath).into(binding.image)
                    }
                }
            }
        }

        // Selection handling
        val isSelected = selectedItems.contains(item)
        val context = root.context

        if (binding is LayoutFilesGridBinding) {
            binding.selection.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            binding.selection.setImageResource(
                if (isSelected) R.drawable.ic_selected_radio
                else R.drawable.ic_unselected_radio
            )
            binding.root.strokeWidth = if (isSelected) 2 else 0
            binding.root.strokeColor = context.getColor(R.color.appColor)

            val dp = if (multiSelectMode) 10 else 0
            val px = (dp * context.resources.displayMetrics.density).toInt()
            binding.parentForPadding.setPadding(px, 0, px, 0)

            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.setShimmer(null)
        }

        if (binding is LayoutFilesRowBinding) {
            binding.selection.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            binding.selection.setImageResource(
                if (isSelected) R.drawable.ic_selected_radio
                else R.drawable.ic_unselected_radio
            )
            binding.itemCard.strokeWidth = if (isSelected) 2 else 0
            binding.itemCard.strokeColor = context.getColor(R.color.appColor)

            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.setShimmer(null)
        }

        optionsView.addPressEffect { onOptionsClick(item, root) }
    }

    private fun formatSize(size: Any?): String {
        if (size == null) return ""

        val bytes = when (size) {
            is String -> size.toLongOrNull() ?: return size
            is Int -> size.toLong()
            is Long -> size
            is Float -> (size * 1024 * 1024).toLong()  // assuming float given in MB
            is Double -> (size * 1024 * 1024).toLong() // assuming double given in MB
            else -> return size.toString()
        }

        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }
}
