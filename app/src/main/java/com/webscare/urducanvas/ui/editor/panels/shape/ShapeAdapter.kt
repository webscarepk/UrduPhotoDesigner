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
        private const val BITMAP_SIZE = 300
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

    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

    // ── Pre-rendered bitmap cache ─────────────────────────────────────────────

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
            strokeWidth = size * 0.025f  // 2.5%
            style       = Paint.Style.STROKE
        }
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_EXPANDED) {
            ShapeViewHolder.Expanded(
                LayoutImagesItemExpandedBinding.inflate(inflater, parent, false),
                ::handleShapeClick
            )
        } else {
            ShapeViewHolder.Collapsed(
                LayoutImagesItemBinding.inflate(inflater, parent, false),
                ::handleShapeClick
            )
        }
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
        holder.bind(shape, shape == selectedShape, bitmaps[shape], slideOffset, recyclerViewWidth, recyclerViewPadding)
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

    fun cancelRender() = adapterScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()

    // ── ViewHolder ────────────────────────────────────────────────────────────

    sealed class ShapeViewHolder(
        itemView: android.view.View,
        private val onShapeSelected: (ShapeType) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        abstract val imageView: android.widget.ImageView
        abstract val cardRoot: com.google.android.material.card.MaterialCardView

        private var boundShape: ShapeType? = null

        fun bind(
            shape: ShapeType,
            isSelected: Boolean,
            bitmap: Bitmap?,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            boundShape = shape
            imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            bitmap?.let { imageView.setImageBitmap(it) }
            updateSelectionOnly(isSelected)
            updateSize(slideOffset, rvWidth, rvPadding)
            itemView.setOnClickListener { boundShape?.let { onShapeSelected(it) } }
        }

        fun updateSize(slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            if (rvWidth <= 0) return
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val collapsedSize = (44 * density).toInt()

            val marginPx = 18 * density // spacing space (3 columns * 2 sides * 3dp = 18dp)
            val columnWidth = ((rvWidth - rvPadding - marginPx) / 3).toInt()

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()

            val lp = cardRoot.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (lp != null) {
                val marginEndPx = (6 * density).toInt()
                val marginBottomPx = (6 * density).toInt()

                // Calculate vertical clamping when horizontal orientation to prevent overlapping rows
                val recyclerView = itemView.parent as? androidx.recyclerview.widget.RecyclerView
                val lm = recyclerView?.layoutManager as? androidx.recyclerview.widget.GridLayoutManager
                val finalSize = if (lm != null && lm.orientation == androidx.recyclerview.widget.GridLayoutManager.HORIZONTAL) {
                    val rvHeight = recyclerView.height
                    val rvPaddingY = recyclerView.paddingTop + recyclerView.paddingBottom
                    val availHeight = rvHeight - rvPaddingY
                    val spanCount = lm.spanCount.coerceAtLeast(1)
                    val rowHeight = availHeight / spanCount
                    val maxAllowedHeight = rowHeight - marginBottomPx
                    minOf(currentSize, maxAllowedHeight).coerceAtLeast((28 * density).toInt())
                } else {
                    currentSize
                }

                if (lp.width != finalSize || lp.height != finalSize || lp.rightMargin != marginEndPx || lp.bottomMargin != marginBottomPx) {
                    lp.width = finalSize
                    lp.height = finalSize
                    lp.rightMargin = marginEndPx
                    lp.bottomMargin = marginBottomPx
                    cardRoot.layoutParams = lp
                }
            }
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