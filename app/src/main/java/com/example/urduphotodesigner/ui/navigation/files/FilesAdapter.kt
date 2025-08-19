package com.example.urduphotodesigner.ui.navigation.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.LayoutFilesGridBinding
import com.example.urduphotodesigner.databinding.LayoutFilesRowBinding

class FilesAdapter(
    private var items: List<Any>,
    private var isGrid: Boolean = true,
    private val onOptionsClick: (Any, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

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

    inner class GridViewHolder(private val binding: LayoutFilesGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {
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

            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.setShimmer(null)

            binding.moreOptions.addPressEffect { onOptionsClick(item, binding.root) }
        }
    }

    inner class ListViewHolder(private val binding: LayoutFilesRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {
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

            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.setShimmer(null)

            binding.moreOptions.addPressEffect { onOptionsClick(item, binding.root) }
        }
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
