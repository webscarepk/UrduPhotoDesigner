package com.webscare.urducanvas.ui.editor.panels.objects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.EmojiMeta
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.ItemEmojiBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmojiAdapter(
    private val context: Context,
    initialEmojis: List<EmojiMeta>,
    private val emojiSizePx: Int = 512,
    private val onEmojiClicked: (Bitmap) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private val emojis = initialEmojis.toMutableList()

    // Paint created once, reused across all renders
    private val paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            textSize  = emojiSizePx * 0.82f
            textAlign = Paint.Align.CENTER
            typeface  = ResourcesCompat.getFont(context, R.font.symbols) ?: Typeface.DEFAULT
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun updateData(newList: List<EmojiMeta>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = emojis.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                emojis[oldPos].char == newList[newPos].char
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                emojis[oldPos] == newList[newPos]
        })
        emojis.clear()
        emojis.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class EmojiViewHolder(private val binding: ItemEmojiBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(emoji: EmojiMeta) {
            binding.emojiText.text = emoji.char
            binding.root.addPressEffect {
                // Render bitmap off the main thread so the tap feels instant
                CoroutineScope(Dispatchers.Main).launch {
                    val bmp = withContext(Dispatchers.Default) { emojiToBitmap(emoji.char) }
                    onEmojiClicked(bmp)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val binding = ItemEmojiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EmojiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) =
        holder.bind(emojis[position])

    override fun getItemCount(): Int = emojis.size

    // ── Bitmap rendering ──────────────────────────────────────────────────────

    /**
     * Renders a single emoji into a high-resolution square Bitmap.
     * Safe to call from a background thread — Paint is read-only here.
     *
     * Strategy:
     *  1. Draw at [emojiSizePx] (512 px) for crisp detail.
     *  2. Tight-crop to the actual glyph bounds with a small padding.
     *  3. Recycle the full bitmap immediately after cropping.
     */
    private fun emojiToBitmap(emoji: String): Bitmap {
        val size = emojiSizePx

        val fm       = paint.fontMetrics
        val baseline = (size - fm.bottom - fm.top) / 2f
        val cx       = size / 2f

        val textWidth = paint.measureText(emoji).coerceAtMost(size.toFloat())
        val glyphH    = (fm.descent - fm.ascent).coerceAtMost(size.toFloat())

        val pad   = (size * 0.04f).toInt()
        val cropW = (textWidth + pad * 2).toInt().coerceIn(1, size)
        val cropH = (glyphH   + pad * 2).toInt().coerceIn(1, size)

        val full   = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        canvas.drawText(emoji, cx, baseline, paint)

        val left = ((size - cropW) / 2).coerceAtLeast(0)
        val top  = (baseline + fm.ascent - pad).toInt().coerceAtLeast(0)

        return try {
            val cropped = Bitmap.createBitmap(full, left, top, cropW, cropH)
            full.recycle()
            cropped
        } catch (e: IllegalArgumentException) {
            full  // geometry edge-case fallback
        }
    }
}