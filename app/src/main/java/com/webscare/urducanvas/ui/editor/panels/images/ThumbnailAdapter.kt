package com.webscare.urducanvas.ui.editor.panels.objects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.webscare.urducanvas.common.canvas.model.EmojiMeta
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.SvgLoader
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.databinding.LayoutThumbnailItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── SelectedItem sealed class ─────────────────────────────────────────────────

sealed class SelectedItem {
    data class Image(val entity: ImageEntity) : SelectedItem()
    data class Emoji(val meta: EmojiMeta, val cachedBitmap: Bitmap? = null) : SelectedItem()

    val uniqueId: String get() = when (this) {
        is Image -> "img_${entity.id}"
        is Emoji -> "emoji_${meta.char}"
    }
}

// ── ThumbnailAdapter ──────────────────────────────────────────────────────────

/**
 * Stable ListAdapter for the selection toolbar strip.
 * Uses its own dedicated layout_thumbnail_item.xml — a simple 52dp square
 * card with image preview + close icon. No shimmer, no premium badge.
 *
 * Created once in ObjectsFragment, updated via submitList().
 * DiffUtil handles smooth add/remove as user selects/deselects.
 */
class ThumbnailAdapter(
    private val onDeselect: (SelectedItem) -> Unit
) : ListAdapter<SelectedItem, ThumbnailAdapter.ThumbnailViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SelectedItem>() {
            override fun areItemsTheSame(old: SelectedItem, new: SelectedItem) =
                old.uniqueId == new.uniqueId
            override fun areContentsTheSame(old: SelectedItem, new: SelectedItem) =
                old.uniqueId == new.uniqueId
        }
        private const val EMOJI_THUMB_SIZE = 256
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ThumbnailViewHolder(
            LayoutThumbnailItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThumbnailViewHolder(
        private val binding: LayoutThumbnailItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SelectedItem) {
            binding.thumbImage.setImageDrawable(null)

            // Close icon — deselects this item
            binding.thumbClose.setOnClickListener { onDeselect(item) }

            when (item) {
                is SelectedItem.Image -> loadImage(item.entity)
                is SelectedItem.Emoji -> loadEmoji(item.meta, item.cachedBitmap)
            }
        }

        private fun loadImage(entity: ImageEntity) {
            val url   = Constants.BASE_URL_GLIDE + entity.file_url
            val isSvg = entity.file_name.endsWith(".svg", ignoreCase = true)

            if (isSvg) {
                SvgLoader.load(
                    url       = url,
                    imageView = binding.thumbImage,
                    scope     = scope,
                    cachedXml = entity.bitmapData
                ) { _, _ -> }
            } else {
                Glide.with(binding.thumbImage)
                    .load(entity.bitmapData ?: url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.thumbImage)
            }
        }

        private fun loadEmoji(meta: EmojiMeta, cachedBitmap: Bitmap?) {
            if (cachedBitmap != null) {
                binding.thumbImage.setImageBitmap(cachedBitmap)
                return
            }
            scope.launch {
                val bmp = withContext(Dispatchers.Default) { renderEmojiThumbnail(meta.char) }
                binding.thumbImage.setImageBitmap(bmp)
            }
        }

        private fun renderEmojiThumbnail(char: String): Bitmap {
            val size  = EMOJI_THUMB_SIZE
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = size * 0.75f
                textAlign = Paint.Align.CENTER
            }
            val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val fm     = paint.fontMetrics
            canvas.drawText(char, size / 2f, (size - fm.bottom - fm.top) / 2f, paint)
            return bmp
        }
    }
}