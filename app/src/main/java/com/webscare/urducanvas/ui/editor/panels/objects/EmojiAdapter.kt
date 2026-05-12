package com.webscare.urducanvas.ui.editor.panels.objects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.EmojiMeta
import com.webscare.urducanvas.databinding.ItemEmojiBinding
import com.webscare.urducanvas.databinding.ItemEmojiExpandedBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmojiAdapter(
    private val context: Context,
    initialEmojis: List<EmojiMeta>,
    private val onEmojiClicked: (Bitmap) -> Unit,
    private val onEmojiLongPress: ((EmojiMeta) -> Unit)? = null
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED    = 0
        const val TYPE_EXPANDED     = 1
        const val PAYLOAD_SELECTION = "emoji_selection_changed"

        // High-res render size for canvas placement
        private const val RENDER_SIZE_PX = 2048

        /**
         * System emoji font — this is what renders the full-color emoji you see
         * in the TextView on screen. R.font.symbols is a custom symbol font, NOT
         * a color emoji font, which is why bitmaps rendered with it look flat/pixelated.
         *
         * Using Typeface.DEFAULT here lets Android select the system font stack
         * which includes NotoColorEmoji, producing crisp full-color bitmaps.
         *
         * We create one instance and reuse it across all render calls.
         */
        private val EMOJI_TYPEFACE: Typeface = Typeface.DEFAULT
    }

    private val emojis = initialEmojis.toMutableList()

    private val selectionShadow = mutableMapOf<String, Boolean>()
    fun isEmojiSelected(char: String): Boolean = selectionShadow[char] == true

    var isInMultiSelectMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    fun updateSelectionForChar(char: String, isSelected: Boolean) {
        selectionShadow[char] = isSelected
        val position = emojis.indexOfFirst { it.char == char }
        if (position >= 0) notifyItemChanged(position, PAYLOAD_SELECTION)
    }

    fun clearSelectionShadow() {
        if (selectionShadow.isEmpty()) return
        selectionShadow.clear()
        notifyDataSetChanged()
    }

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    // ── Paint for display (TextView shows emoji natively — paint only used for canvas render) ──

    /**
     * ThreadLocal paint configured for HIGH-QUALITY emoji rendering.
     *
     * Key points:
     * - Typeface.DEFAULT → system font stack → NotoColorEmoji → full color
     * - textAlign = LEFT  → predictable getTextBounds() origin
     * - isLinearText = true → disables hinting for large sizes → sharper outlines
     * - RENDER_SIZE_PX * 0.85f → fills ~85% of the canvas, leaving room for crop padding
     */
    private val renderPaintLocal = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            textSize     = RENDER_SIZE_PX * 0.85f
            textAlign    = Paint.Align.LEFT
            typeface     = EMOJI_TYPEFACE
            isLinearText = true
        }
    }

    /**
     * Separate smaller paint for the display TextView — uses the symbols font
     * so the custom symbol glyphs still render correctly in the grid.
     * (The grid TextViews use the symbols font for display only — we never
     * render those to bitmap. Canvas bitmaps use renderPaintLocal above.)
     */
    private val displayPaintLocal = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.symbols) ?: Typeface.DEFAULT
        }
    }

    fun getPaint(): Paint? = renderPaintLocal.get()

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

    fun getCurrentEmojis(): List<EmojiMeta> = emojis.toList()

    override fun getItemViewType(position: Int): Int =
        if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder =
        if (viewType == TYPE_EXPANDED) {
            EmojiViewHolder.Expanded(
                ItemEmojiExpandedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                adapter        = this,
                onEmojiClicked = onEmojiClicked,
                onLongPress    = onEmojiLongPress
            )
        } else {
            EmojiViewHolder.Collapsed(
                ItemEmojiBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                adapter        = this,
                onEmojiClicked = onEmojiClicked,
                onLongPress    = null   // no selection in collapsed
            )
        }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        holder.bind(emoji, isEmojiSelected(emoji.char), isInMultiSelectMode, renderPaintLocal)
    }

    override fun onBindViewHolder(
        holder: EmojiViewHolder, position: Int, payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelectionOnly(
                isEmojiSelected(emojis[position].char), isInMultiSelectMode
            )
        }
    }

    override fun getItemCount(): Int = emojis.size

    // ── ViewHolder ────────────────────────────────────────────────────────────

    sealed class EmojiViewHolder(
        itemView: View,
        private val adapter: EmojiAdapter,
        private val onEmojiClicked: (Bitmap) -> Unit,
        private val onLongPress: ((EmojiMeta) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        protected abstract val emojiText: android.widget.TextView
        protected open val selectionIcon: android.widget.ImageView? get() = null
        protected open val cardRoot: com.google.android.material.card.MaterialCardView? get() = null
        // Loading indicator — shown while bitmap renders, same UX as SVG loading
        protected open val loadingAnim: com.airbnb.lottie.LottieAnimationView? get() = null

        private var renderJob: Job? = null
        private var boundEmoji: EmojiMeta? = null

        fun bind(
            emoji: EmojiMeta,
            isSelected: Boolean,
            inMultiSelectMode: Boolean,
            paintLocal: ThreadLocal<Paint>
        ) {
            boundEmoji = emoji
            emojiText.text = emoji.char
            loadingAnim?.isVisible = false
            updateSelectionOnly(isSelected, inMultiSelectMode)
            wireClicks(emoji, paintLocal)
        }

        fun updateSelectionOnly(isSelected: Boolean, inMultiSelectMode: Boolean) {
            selectionIcon?.apply {
                visibility = if (inMultiSelectMode) View.VISIBLE else View.GONE
                setImageResource(
                    if (isSelected) R.drawable.ic_selected_radio
                    else R.drawable.ic_unselected_radio
                )
            }
            cardRoot?.strokeWidth = if (isSelected) 2 else 0
            if (isSelected) {
                cardRoot?.strokeColor =
                    ContextCompat.getColor(itemView.context, R.color.appColor)
            }
        }

        private fun wireClicks(emoji: EmojiMeta, paintLocal: ThreadLocal<Paint>) {
            // Plain listeners — no addPressEffect/addPressEffectWithLongClick.
            // Those reduce alpha and can leave the view stuck in a dim state
            // when the touch sequence is interrupted by a long press.
            itemView.setOnClickListener {
                val current = boundEmoji ?: emoji
                if (adapter.isInMultiSelectMode) {
                    onLongPress?.invoke(current)
                } else {
                    renderJob?.cancel()
                    renderJob = itemView.findViewTreeLifecycleOwner()
                        ?.lifecycleScope?.launch {
                            renderAndEmit(current, paintLocal)
                        }
                }
            }

            itemView.setOnLongClickListener {
                val current = boundEmoji ?: emoji
                onLongPress?.invoke(current)
                true
            }
        }

        /**
         * Shows loading indicator, renders emoji bitmap at 2048px on IO dispatcher,
         * hides indicator, then delivers bitmap — same pattern as SVG loading.
         */
        private suspend fun renderAndEmit(emoji: EmojiMeta, paintLocal: ThreadLocal<Paint>) {
            // Show loading spinner — gives user feedback during the ~50–100ms render
            loadingAnim?.isVisible = true

            val bmp = withContext(Dispatchers.Default) {
                emojiToBitmap(emoji.char, paintLocal.get()!!)
            }

            // Hide spinner and deliver result
            loadingAnim?.isVisible = false
            onEmojiClicked(bmp)
        }

        /**
         * Renders emoji to a high-res bitmap using the system emoji font.
         *
         * WHY THIS PRODUCES HIGH QUALITY:
         * - Paint.typeface = Typeface.DEFAULT → system resolves to NotoColorEmoji
         *   which is a COLR/CBLC color font containing pre-rendered PNG strikes
         *   at multiple resolutions. At textSize=2048*0.85, Android picks the
         *   largest available strike and scales it up — still far crisper than
         *   rendering with a non-color font.
         * - getTextBounds() → tight crop → no wasted transparent border making
         *   the emoji look small when placed on canvas.
         * - Oversized intermediate canvas (1.5×) → no edge clipping.
         */
        private fun emojiToBitmap(emojiChar: String, paint: Paint): Bitmap {
            val size   = RENDER_SIZE_PX
            val bounds = Rect()
            paint.getTextBounds(emojiChar, 0, emojiChar.length, bounds)

            val glyphW = bounds.width().coerceAtLeast(1)
            val glyphH = bounds.height().coerceAtLeast(1)
            val pad    = ((maxOf(glyphW, glyphH)) * 0.06f).toInt().coerceAtLeast(8)
            val outW   = glyphW + pad * 2
            val outH   = glyphH + pad * 2

            // 1.5× canvas so any slight measurement underestimate doesn't clip
            val canvasSize = (maxOf(outW, outH) * 1.5f).toInt().coerceAtLeast(size)
            val full       = Bitmap.createBitmap(
                canvasSize, canvasSize, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(full)

            // Draw at (pad - bounds.left, pad - bounds.top) so glyph top-left
            // lands exactly at (pad, pad) in the output bitmap
            val drawX = (-bounds.left + pad).toFloat()
            val drawY = (-bounds.top  + pad).toFloat()
            canvas.drawText(emojiChar, drawX, drawY, paint)

            val cropW = outW.coerceAtMost(canvasSize)
            val cropH = outH.coerceAtMost(canvasSize)

            return try {
                val cropped = Bitmap.createBitmap(full, 0, 0, cropW, cropH)
                full.recycle()
                cropped
            } catch (e: IllegalArgumentException) {
                full
            }
        }

        // ── Subtypes ──────────────────────────────────────────────────────────

        class Collapsed(
            private val binding: ItemEmojiBinding,
            adapter: EmojiAdapter,
            onEmojiClicked: (Bitmap) -> Unit,
            onLongPress: ((EmojiMeta) -> Unit)?
        ) : EmojiViewHolder(binding.root, adapter, onEmojiClicked, onLongPress) {
            override val emojiText  get() = binding.emojiText
            // No loading anim in collapsed — it's a small 38dp item
        }

        class Expanded(
            private val binding: ItemEmojiExpandedBinding,
            adapter: EmojiAdapter,
            onEmojiClicked: (Bitmap) -> Unit,
            onLongPress: ((EmojiMeta) -> Unit)?
        ) : EmojiViewHolder(binding.root, adapter, onEmojiClicked, onLongPress) {
            override val emojiText     get() = binding.emojiText
            override val selectionIcon get() = binding.selection
            override val cardRoot      get() = binding.root
            override val loadingAnim   get() = binding.loading
        }
    }
}