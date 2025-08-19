package com.example.urduphotodesigner.ui.navigation.home

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.common.utils.Utils.addPressEffectWithLongClick
import com.example.urduphotodesigner.databinding.LayoutRecentsItemBinding
import java.io.File

class RecentAdapter(private val onClick: (ExportResult) -> Unit,
                    private val onLongClick: (View, ExportResult) -> Unit) :
    RecyclerView.Adapter<RecentAdapter.RecentViewHolder>() {

    private var exportResults: List<ExportResult> = listOf()

    fun submitList(items: List<ExportResult>) {
        exportResults = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding =
            LayoutRecentsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = exportResults[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = exportResults.size

    inner class RecentViewHolder(private val binding: LayoutRecentsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportResult) {
            Log.d("ImagePath", "bind: ${item.imagePath}")
            val file = File(item.imagePath)
            Glide.with(binding.thumbnail)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(false)
                .signature(ObjectKey("${file.length()}_${file.lastModified()}"))
                .into(binding.thumbnail)

            binding.title.text = item.fileName

            binding.root.addPressEffectWithLongClick(
                onClick = {onClick(item)},
                onLongClick = {onLongClick(binding.root, item)}
            )
        }
    }
}
