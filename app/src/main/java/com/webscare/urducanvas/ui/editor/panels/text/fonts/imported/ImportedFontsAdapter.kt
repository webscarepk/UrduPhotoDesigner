package com.webscare.urducanvas.ui.editor.panels.text.fonts.imported

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.databinding.LayoutImportedFontsGridBinding
import java.io.File

/**
 * Standalone adapter for [ImportedFontsBottomSheet].
 * Displays imported fonts only — no selection mode, no rename, no options menu.
 * Reuses item_file.xml layout (same as FilesAdapter) for visual consistency.
 */
class ImportedFontsAdapter(
    private val onFontClick: (FontEntity) -> Unit
) : ListAdapter<FontEntity, ImportedFontsAdapter.FontViewHolder>(DIFF) {

    var selectedFontId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                // Refresh old + new selection rows only
                currentList.indexOfFirst { it.id.toString() == old }
                    .takeIf { it >= 0 }?.let { notifyItemChanged(it) }
                currentList.indexOfFirst { it.id.toString() == value }
                    .takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val binding = LayoutImportedFontsGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FontViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FontViewHolder(private val binding: LayoutImportedFontsGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(font: FontEntity) {
            binding.assetName.text = font.font_name
            binding.metaData.text = "Font - ${formatSize(font.file_size)}"

            binding.root.addPressEffect { onFontClick(font) }
        }
    }

    private fun formatSize(size: Any?): String {
        if (size == null) return ""
        val bytes = when (size) {
            is String -> size.toLongOrNull() ?: return size
            is Int -> size.toLong()
            is Long -> size
            is Float -> (size * 1024 * 1024).toLong()
            is Double -> (size * 1024 * 1024).toLong()
            else -> return size.toString()
        }
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024f * 1024f))
        }
    }
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FontEntity>() {
            override fun areItemsTheSame(a: FontEntity, b: FontEntity) = a.id == b.id
            override fun areContentsTheSame(a: FontEntity, b: FontEntity) = a == b
        }
    }
}