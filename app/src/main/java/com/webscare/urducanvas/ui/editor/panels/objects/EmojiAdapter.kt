package com.webscare.urducanvas.ui.editor.panels.objects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.EmojiMeta
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.ItemEmojiBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class EmojiAdapter(
    private val context: Context,
    private val emojis: List<com.webscare.urducanvas.common.canvas.model.EmojiMeta>,
    private val emojiSizePx: Int = 150,          // adjust to your desired size
    private val onEmojiClicked: (Bitmap) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    inner class EmojiViewHolder(private val binding: ItemEmojiBinding)
        : RecyclerView.ViewHolder(binding.root) {
        fun bind(emoji: com.webscare.urducanvas.common.canvas.model.EmojiMeta) {
            binding.emojiText.text = emoji.char
            binding.root.addPressEffect {
                val bmp = emojiToBitmap(emoji.char, emojiSizePx)
                onEmojiClicked(bmp)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val binding = ItemEmojiBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EmojiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.bind(emojis[position])
    }

    override fun getItemCount(): Int = emojis.size

    /**
     * Renders a single emoji string into a square Bitmap.
     */
    private fun emojiToBitmap(emoji: String, sizePx: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = sizePx * 0.8f
            textAlign = Paint.Align.CENTER
            _root_ide_package_.android.graphics.Paint.setTypeface = ResourcesCompat.getFont(context,
                R.font.symbols)
                ?: Typeface.DEFAULT
        }
        // Use font metrics to vertically center
        val fm = paint.fontMetrics
        val baseline = (sizePx - fm.bottom - fm.top) / 2

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(emoji, sizePx / 2f, baseline, paint)
        return bitmap
    }
}
