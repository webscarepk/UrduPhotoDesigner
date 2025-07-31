package com.example.urduphotodesigner.ui.navigation.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.databinding.LayoutRecentsItemBinding

class RecentAdapter (private val onClick: (ExportResult) -> Unit) : RecyclerView.Adapter<RecentAdapter.RecentViewHolder>() {

    private var exportResults: List<ExportResult> = listOf()

    fun submitList(items: List<ExportResult>) {
        exportResults = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val binding = LayoutRecentsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        val item = exportResults[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = exportResults.size

    inner class RecentViewHolder(private val binding: LayoutRecentsItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExportResult) {
            // Bind ExportResult data to UI elements
            binding.thumbnail.setImageURI(Uri.parse(item.imagePath)) // Use an appropriate resource
            binding.title.text = item.fileName
            binding.date.text = item.exportDate
            binding.size.text = "%.1f MB".format(item.fileSizeMB)

            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }
}
