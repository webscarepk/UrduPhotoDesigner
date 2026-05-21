package com.webscare.urducanvas.ui.editor.panels.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.canvas.model.EmojiMeta
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.ShapeRenderUtils.drawShape
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
    data class Shape(val shape: ShapeType) : SelectedItem()

    val uniqueId: String get() = when (this) {
        is Image -> "img_${entity.id}"
        is Emoji -> "emoji_${meta.char}"
        is Shape -> "shape_${shape.name}"
    }
}

// ── ThumbnailAdapter ──────────────────────────────────────────────────────────

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
        private const val SHAPE_THUMB_SIZE = 200
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ThumbnailViewHolder(
            LayoutThumbnailItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ThumbnailViewHolder(
        private val binding: LayoutThumbnailItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SelectedItem) {
            binding.thumbImage.setImageDrawable(null)
            binding.thumbClose.setOnClickListener { onDeselect(item) }

            when (item) {
                is SelectedItem.Image -> loadImage(item.entity)
                is SelectedItem.Emoji -> loadEmoji(item.meta, item.cachedBitmap)
                is SelectedItem.Shape -> loadShape(item.shape)
            }
        }

        private fun loadImage(entity: ImageEntity) {
            // resolveUrl() handles both Pexels (full URL) and own images (needs base prefix)
            val url   = resolveUrl(entity)
            val isSvg = entity.file_name.endsWith(".svg", ignoreCase = true)
            if (isSvg) {
                SvgLoader.load(url, binding.thumbImage, scope, entity.bitmapData) { _, _ -> }
            } else {
                Glide.with(binding.thumbImage)
                    .load(entity.bitmapData ?: url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.thumbImage)
            }
        }

        private fun loadEmoji(meta: EmojiMeta, cachedBitmap: Bitmap?) {
            if (cachedBitmap != null) { binding.thumbImage.setImageBitmap(cachedBitmap); return }
            scope.launch {
                val bmp = withContext(Dispatchers.Default) { renderEmojiThumbnail(meta.char) }
                binding.thumbImage.setImageBitmap(bmp)
            }
        }

        private fun loadShape(shape: ShapeType) {
            scope.launch {
                val bmp = withContext(Dispatchers.Default) { renderShapeThumbnail(shape) }
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

        private fun renderShapeThumbnail(shape: ShapeType): Bitmap {
            val size   = SHAPE_THUMB_SIZE
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            val paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = android.graphics.Color.parseColor("#333333")
                strokeWidth = size * 0.045f
                style       = Paint.Style.STROKE
            }
            val pad    = size * 0.2f
            val radius = size * 0.15f
            val rect   = RectF(pad, pad, size - pad, size - pad)
            drawShape(canvas, paint, shape, rect, if (shape == ShapeType.RECTANGLE) 0f else radius)
            return bitmap
        }
    }
}