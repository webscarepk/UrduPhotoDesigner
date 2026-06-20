package com.webscare.urducanvas.ui.editor.panels.shape

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.utils.ShapeRenderUtils.drawShape
import com.webscare.urducanvas.databinding.LayoutImagesItemBinding
import com.webscare.urducanvas.databinding.LayoutImagesItemExpandedBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShapeAdapter(
    private val context: Context,
    shapes: List<ShapeType>,
    private val onShapeSelected: (ShapeType) -> Unit
) : RecyclerView.Adapter<ShapeAdapter.ShapeViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED = 0
        const val TYPE_EXPANDED  = 1
        private const val BITMAP_SIZE = 300   // single size — padding controls visual size

        // Shape drawn inside 30–70% of the bitmap (35% pad each side).
        // This leaves ~30% visual width for the stroke + breathing room.
        // Increase PAD to make shapes smaller, decrease to make them larger.
        private const val PAD = 0.32f
    }

    private val shapes = shapes.toMutableList()
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var selectedShape: ShapeType? = null

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, "expand_changed")
        }

    // ── Pre-rendered bitmap cache ─────────────────────────────────────────────
    // One shared bitmap per shape — collapsed and expanded use the same bitmap,
    // the card size changes but the image scales via scaleType="fitCenter".
    // This halves memory vs two separate caches.

    private val bitmaps = mutableMapOf<ShapeType, Bitmap>()
    private var bitmapsReady = false

    init {
        adapterScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                shapes.associateWith { renderShapeBitmap(it) }
            }
            bitmaps.putAll(rendered)
            bitmapsReady = true
            notifyDataSetChanged()
        }
    }

    private fun renderShapeBitmap(shape: ShapeType): Bitmap {
        val size   = BITMAP_SIZE
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = context.getColor(R.color.black)
            strokeWidth = size * 0.025f  // 2.5% — thinner, looks cleaner at thumbnail sizes
            style       = Paint.Style.STROKE
        }
        // PAD controls how much empty space surrounds the shape.
        // 0.32f = shape occupies the middle 36% of the bitmap side.
        val pad    = size * PAD
        val rect   = RectF(pad, pad, size - pad, size - pad)
        drawShape(canvas, paint, shape, rect, 0f)
        return bitmap
    }

    fun updateShapes(newShapes: List<ShapeType>) {
        shapes.clear()
        shapes.addAll(newShapes)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (isExpanded) TYPE_EXPANDED else TYPE_COLLAPSED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeViewHolder =
        if (viewType == TYPE_EXPANDED) {
            ShapeViewHolder.Expanded(
                LayoutImagesItemExpandedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                onShapeSelected = ::handleShapeClick
            )
        } else {
            ShapeViewHolder.Collapsed(
                LayoutImagesItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                onShapeSelected = ::handleShapeClick
            )
        }

    private fun handleShapeClick(shape: ShapeType) {
        val oldPos = shapes.indexOf(selectedShape)
        val newPos = shapes.indexOf(shape)
        selectedShape = shape
        if (oldPos >= 0) notifyItemChanged(oldPos, "selection_changed")
        if (newPos >= 0) notifyItemChanged(newPos, "selection_changed")
        onShapeSelected(shape)
    }

    override fun onBindViewHolder(holder: ShapeViewHolder, position: Int) {
        val shape  = shapes[position]
        holder.bind(shape, shape == selectedShape, bitmaps[shape])
    }

    override fun onBindViewHolder(
        holder: ShapeViewHolder, position: Int, payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) { onBindViewHolder(holder, position); return }
        val shape  = shapes[position]
        if (payloads.contains("selection_changed")) {
            holder.updateSelectionOnly(shape == selectedShape)
        }
        if (payloads.contains("expand_changed")) {
            bitmaps[shape]?.let { holder.setImage(it) }
        }
    }

    override fun getItemCount() = shapes.size

    /** Call from the host fragment's onDestroyView to cancel in-flight bitmap rendering. */
    fun cancelRender() = adapterScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()

    // ── ViewHolder ────────────────────────────────────────────────────────────

    sealed class ShapeViewHolder(
        itemView: android.view.View,
        private val onShapeSelected: (ShapeType) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        protected abstract val imageView: android.widget.ImageView
        protected abstract val cardRoot: com.google.android.material.card.MaterialCardView

        private var boundShape: ShapeType? = null

        fun bind(shape: ShapeType, isSelected: Boolean, bitmap: Bitmap?) {
            boundShape = shape
            // fitCenter keeps aspect ratio and adds natural padding within the card
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            bitmap?.let { imageView.setImageBitmap(it) }
            updateSelectionOnly(isSelected)
            itemView.setOnClickListener { boundShape?.let { onShapeSelected(it) } }
        }

        fun setImage(bitmap: Bitmap) {
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            imageView.setImageBitmap(bitmap)
        }

        fun updateSelectionOnly(isSelected: Boolean) {
            cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(itemView.context, R.color.contrast)
            )
            cardRoot.strokeWidth = if (isSelected) 4 else 0
            if (isSelected) {
                cardRoot.strokeColor = ContextCompat.getColor(itemView.context, R.color.appColor)
            }
        }

        class Collapsed(
            private val binding: LayoutImagesItemBinding,
            onShapeSelected: (ShapeType) -> Unit
        ) : ShapeViewHolder(binding.root, onShapeSelected) {
            override val imageView get() = binding.image
            override val cardRoot  get() = binding.root
            init {
                binding.isPremium.visibility = android.view.View.GONE
                binding.loading.visibility   = android.view.View.GONE
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.setShimmer(null)
            }
        }

        class Expanded(
            private val binding: LayoutImagesItemExpandedBinding,
            onShapeSelected: (ShapeType) -> Unit
        ) : ShapeViewHolder(binding.root, onShapeSelected) {
            override val imageView get() = binding.image
            override val cardRoot  get() = binding.root
            init {
                binding.isPremium.visibility = android.view.View.GONE
                binding.loading.visibility   = android.view.View.GONE
                binding.checkIcon.visibility = android.view.View.GONE
                binding.shimmerLayout.stopShimmer()
                binding.shimmerLayout.setShimmer(null)
            }
        }
    }
}