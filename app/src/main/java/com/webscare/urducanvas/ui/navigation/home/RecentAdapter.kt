package com.webscare.urducanvas.ui.navigation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.databinding.LayoutRecentsItemBinding
import java.io.File

class RecentAdapter(
    private val onClick: (com.webscare.urducanvas.data.model.ExportResult) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<com.webscare.urducanvas.data.model.ExportResult, RecentAdapter.RecentViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<com.webscare.urducanvas.data.model.ExportResult>() {
            override fun areItemsTheSame(oldItem: com.webscare.urducanvas.data.model.ExportResult, newItem: com.webscare.urducanvas.data.model.ExportResult): Boolean {
                // Use database id if available, otherwise fall back to file path
                return oldItem.id == newItem.id || oldItem.imagePath == newItem.imagePath
            }

            override fun areContentsTheSame(oldItem: com.webscare.urducanvas.data.model.ExportResult, newItem: com.webscare.urducanvas.data.model.ExportResult): Boolean {
                // Compare all relevant fields
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = LayoutRecentsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class RecentViewHolder(private val binding: LayoutRecentsItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: com.webscare.urducanvas.data.model.ExportResult) {
            val file = File(item.imagePath)
            Glide.with(binding.thumbnail)
                .load(file)
                // Use file modification time as cache key — thumbnail updates when project is
                // re-saved, but is served from disk cache on every other bind. This eliminates
                // the per-bind decode that was causing HeapTaskDaemon thrash in the ANR log.
                .signature(ObjectKey(file.lastModified()))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .thumbnail(0.2f)
                .into(binding.thumbnail)

            binding.title.text = item.fileName

            binding.root.addPressEffect { onClick(item) }
        }
    }
}
