package com.example.urduphotodesigner.ui.navigation.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.common.utils.Utils.addPressEffectWithLongClick
import com.example.urduphotodesigner.databinding.LayoutRecentsItemBinding
import java.io.File

class RecentAdapter(
    private val onClick: (ExportResult) -> Unit,
    private val onLongClick: (View, ExportResult) -> Unit
) : ListAdapter<ExportResult, RecentAdapter.RecentViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ExportResult>() {
            override fun areItemsTheSame(oldItem: ExportResult, newItem: ExportResult): Boolean {
                // Use database id if available, otherwise fall back to file path
                return oldItem.id == newItem.id || oldItem.imagePath == newItem.imagePath
            }

            override fun areContentsTheSame(oldItem: ExportResult, newItem: ExportResult): Boolean {
                // Compare all relevant fields
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = LayoutRecentsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class RecentViewHolder(private val binding: LayoutRecentsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportResult) {
            val file = File(item.imagePath)
            val bitmap = ImageProcessor.filePathToBitmap(file.absolutePath)
            Glide.with(binding.thumbnail.context).clear(binding.thumbnail)

            // Wait until ImageView has dimensions
            binding.thumbnail.post {
                Glide.with(binding.thumbnail.context)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .skipMemoryCache(false)
                    .centerCrop()
                    .thumbnail(0.1f)
                    .into(binding.thumbnail)
            }

            binding.title.text = item.fileName

            binding.root.addPressEffectWithLongClick(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(binding.root, item) }
            )
        }
    }
}