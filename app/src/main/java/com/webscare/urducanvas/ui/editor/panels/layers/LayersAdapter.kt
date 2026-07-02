package com.webscare.urducanvas.ui.editor.panels.layers

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.addPressEffectWithLongClick
import com.webscare.urducanvas.databinding.LayoutLayersGroupHeaderBinding
import com.webscare.urducanvas.databinding.LayoutLayersItemBinding
import com.webscare.urducanvas.databinding.LayoutLayersItemCollapsedBinding

// ── Display model ─────────────────────────────────────────────────────────────
sealed class DisplayItem {
    data class GroupHeader(val element: CanvasElement) : DisplayItem()
    data class Child(val element: CanvasElement) : DisplayItem()
    data class Standalone(val element: CanvasElement) : DisplayItem()
}

// Extension to get the CanvasElement from any DisplayItem
val DisplayItem.element: CanvasElement
    get() = when (this) {
        is DisplayItem.GroupHeader -> element
        is DisplayItem.Child       -> element
        is DisplayItem.Standalone  -> element
    }

class LayersAdapter(
    private val onLockToggle:       (element: CanvasElement) -> Unit,
    private val onMoreOptions:      (element: CanvasElement, anchorView: View) -> Unit,
    private val onItemClick:        (element: CanvasElement) -> Unit,
    private val onItemLongClick:    (element: CanvasElement) -> Unit,
    private val onStartDrag:        (RecyclerView.ViewHolder) -> Unit,
    private val onGroupHeaderClick: (element: CanvasElement) -> Unit,
    private val onToggleCollapse:   (element: CanvasElement) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_COLLAPSED             = 0
        const val TYPE_EXPANDED_GROUP_HEADER = 1
        const val TYPE_EXPANDED_ITEM         = 2
    }

    internal val items = mutableListOf<DisplayItem>()
    private var inSelectionMode = false

    var isExpanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    var slideOffset: Float = 0f
    var recyclerViewWidth: Int = 0
    var recyclerViewPadding: Int = 0

    fun setSelectionMode(enabled: Boolean) {
        if (inSelectionMode != enabled) {
            inSelectionMode = enabled
            notifyDataSetChanged()
        }
    }

    fun submitList(newItems: List<DisplayItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun currentList(): List<CanvasElement> = items.map { it.element }

    fun getDisplayItemAt(position: Int): DisplayItem? = items.getOrNull(position)

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun retypeItem(position: Int, asChild: Boolean, newGroupId: String?) {
        val current = items.getOrNull(position) ?: return
        val element = current.element
        val newItem = if (asChild) {
            element.groupId = newGroupId
            DisplayItem.Child(element)
        } else {
            element.groupId = null
            DisplayItem.Standalone(element)
        }
        items[position] = newItem
    }

    fun getItems(): List<CanvasElement> = items.map { it.element }

    fun getDisplayItems(): List<DisplayItem> = items.toList()

    override fun getItemViewType(position: Int): Int {
        if (!isExpanded) return TYPE_COLLAPSED
        return if (items[position] is DisplayItem.GroupHeader) TYPE_EXPANDED_GROUP_HEADER else TYPE_EXPANDED_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_COLLAPSED -> {
                Collapsed(
                    LayoutLayersItemCollapsedBinding.inflate(inflater, parent, false),
                    this, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse
                )
            }
            TYPE_EXPANDED_GROUP_HEADER -> {
                ExpandedGroupHeader(
                    LayoutLayersGroupHeaderBinding.inflate(inflater, parent, false),
                    this, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse
                )
            }
            else -> {
                ExpandedItem(
                    LayoutLayersItemBinding.inflate(inflater, parent, false),
                    this, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val displayItem = items[position]
        val layerHolder = holder as LayerViewHolder
        layerHolder.bind(
            displayItem,
            isChild = displayItem is DisplayItem.Child,
            isFirst = position == 0 || items[position - 1] is DisplayItem.GroupHeader,
            isLast  = position == items.size - 1 || items[position + 1] !is DisplayItem.Child,
            inSelectionMode = inSelectionMode,
            slideOffset = slideOffset,
            rvWidth = recyclerViewWidth,
            rvPadding = recyclerViewPadding
        )
    }

    override fun getItemCount(): Int = items.size

    // ── ViewHolders ───────────────────────────────────────────────────────────

    abstract class LayerViewHolder(
        itemView: View,
        protected val adapter: LayersAdapter,
        protected val onLockToggle:       (element: CanvasElement) -> Unit,
        protected val onMoreOptions:      (element: CanvasElement, anchorView: View) -> Unit,
        protected val onItemClick:        (element: CanvasElement) -> Unit,
        protected val onItemLongClick:    (element: CanvasElement) -> Unit,
        protected val onStartDrag:        (RecyclerView.ViewHolder) -> Unit,
        protected val onGroupHeaderClick: (element: CanvasElement) -> Unit,
        protected val onToggleCollapse:   (element: CanvasElement) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        abstract val cardRoot: com.google.android.material.card.MaterialCardView

        abstract fun bind(
            displayItem: DisplayItem,
            isChild: Boolean,
            isFirst: Boolean,
            isLast: Boolean,
            inSelectionMode: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        )

        fun updateSize(slideOffset: Float, rvWidth: Int, rvPadding: Int) {
            if (rvWidth <= 0) return
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val collapsedSize = (50 * density).toInt()

            val marginPx = 18 * density // spacing space (3 columns * 2 sides * 3dp = 18dp)
            val columnWidth = ((rvWidth - rvPadding - marginPx) / 3).toInt()

            val currentSize = (collapsedSize + (columnWidth - collapsedSize) * slideOffset).toInt()

            val lp = cardRoot.layoutParams
            if (lp.width != currentSize || lp.height != currentSize) {
                lp.width = currentSize
                lp.height = currentSize
                cardRoot.layoutParams = lp
            }
        }

        protected fun Float.dpToPx(context: android.content.Context): Float =
            this * context.resources.displayMetrics.density

        protected fun Int.dpToPx(context: android.content.Context): Int =
            (this * context.resources.displayMetrics.density).toInt()
    }

    class Collapsed(
        private val binding: LayoutLayersItemCollapsedBinding,
        adapter: LayersAdapter,
        onLockToggle:       (element: CanvasElement) -> Unit,
        onMoreOptions:      (element: CanvasElement, anchorView: View) -> Unit,
        onItemClick:        (element: CanvasElement) -> Unit,
        onItemLongClick:    (element: CanvasElement) -> Unit,
        onStartDrag:        (RecyclerView.ViewHolder) -> Unit,
        onGroupHeaderClick: (element: CanvasElement) -> Unit,
        onToggleCollapse:   (element: CanvasElement) -> Unit
    ) : LayerViewHolder(binding.root, adapter, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse) {

        override val cardRoot get() = binding.root

        override fun bind(
            displayItem: DisplayItem,
            isChild: Boolean,
            isFirst: Boolean,
            isLast: Boolean,
            inSelectionMode: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            val element = displayItem.element

            binding.image.setImageResource(
                if (displayItem is DisplayItem.GroupHeader) {
                    R.drawable.ic_stickers
                } else {
                    when (element.type) {
                        ElementType.TEXT    -> R.drawable.ic_text_layer
                        ElementType.IMAGE   -> R.drawable.ic_image_layer
                        ElementType.STICKER -> R.drawable.ic_sticker
                        ElementType.DRAW    -> R.drawable.ic_pen
                        ElementType.SHAPE   -> R.drawable.ic_shapes
                        else                -> R.drawable.ic_stickers
                    }
                }
            )

            if (element.isSelected) {
                binding.root.strokeWidth = 2
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
            }

            binding.root.addPressEffectWithLongClick(
                onClick = {
                    if (displayItem is DisplayItem.GroupHeader) onGroupHeaderClick(element)
                    else onItemClick(element)
                },
                onLongClick = { onItemLongClick(element) }
            )

            updateSize(slideOffset, rvWidth, rvPadding)
        }
    }

    class ExpandedGroupHeader(
        private val binding: LayoutLayersGroupHeaderBinding,
        adapter: LayersAdapter,
        onLockToggle:       (element: CanvasElement) -> Unit,
        onMoreOptions:      (element: CanvasElement, anchorView: View) -> Unit,
        onItemClick:        (element: CanvasElement) -> Unit,
        onItemLongClick:    (element: CanvasElement) -> Unit,
        onStartDrag:        (RecyclerView.ViewHolder) -> Unit,
        onGroupHeaderClick: (element: CanvasElement) -> Unit,
        onToggleCollapse:   (element: CanvasElement) -> Unit
    ) : LayerViewHolder(binding.root, adapter, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse) {

        override val cardRoot get() = binding.root

        override fun bind(
            displayItem: DisplayItem,
            isChild: Boolean,
            isFirst: Boolean,
            isLast: Boolean,
            inSelectionMode: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            val element = displayItem.element
            binding.apply {
                title.text = element.customName ?: "Group"

                val childCount = adapter.items.count {
                    it is DisplayItem.Child && it.element.groupId == element.id
                }
                badge.text       = childCount.toString()
                badge.visibility = if (childCount > 0) View.VISIBLE else View.GONE

                chevron.setImageResource(
                    if (element.isGroupCollapsed) R.drawable.ic_next else R.drawable.ic_down
                )

                lock.setImageResource(
                    if (element.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock
                )
                lock.addPressEffect { onLockToggle(element) }

                if (element.isSelected) {
                    root.setCardBackgroundColor(
                        ContextCompat.getColor(root.context, R.color.white)
                    )
                    root.strokeWidth = 2
                    root.strokeColor = ContextCompat.getColor(root.context, R.color.appColor)
                } else {
                    root.setCardBackgroundColor(Color.TRANSPARENT)
                    root.strokeWidth = 0
                }

                chevron.addPressEffect { onToggleCollapse(element) }
                options.setOnClickListener { v -> onMoreOptions(element, v) }
                drag.setOnTouchListener { _, _ -> onStartDrag(this@ExpandedGroupHeader); false }
                root.addPressEffectWithLongClick(
                    onClick     = { onGroupHeaderClick(element) },
                    onLongClick = { onItemLongClick(element) }
                )
            }
            updateSize(slideOffset, rvWidth, rvPadding)
        }
    }

    class ExpandedItem(
        private val binding: LayoutLayersItemBinding,
        adapter: LayersAdapter,
        onLockToggle:       (element: CanvasElement) -> Unit,
        onMoreOptions:      (element: CanvasElement, anchorView: View) -> Unit,
        onItemClick:        (element: CanvasElement) -> Unit,
        onItemLongClick:    (element: CanvasElement) -> Unit,
        onStartDrag:        (RecyclerView.ViewHolder) -> Unit,
        onGroupHeaderClick: (element: CanvasElement) -> Unit,
        onToggleCollapse:   (element: CanvasElement) -> Unit
    ) : LayerViewHolder(binding.root, adapter, onLockToggle, onMoreOptions, onItemClick, onItemLongClick, onStartDrag, onGroupHeaderClick, onToggleCollapse) {

        override val cardRoot get() = binding.root

        @SuppressLint("ClickableViewAccessibility")
        override fun bind(
            displayItem: DisplayItem,
            isChild: Boolean,
            isFirst: Boolean,
            isLast: Boolean,
            inSelectionMode: Boolean,
            slideOffset: Float,
            rvWidth: Int,
            rvPadding: Int
        ) {
            val element = displayItem.element
            binding.apply {
                val indentDp = if (isChild) 16 else 0
                val indentPx = (indentDp * root.context.resources.displayMetrics.density).toInt()
                (drag.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                    ?.let { lp -> lp.marginStart = indentPx; drag.layoutParams = lp }

                if (isChild) {
                    root.setCardBackgroundColor(
                        ContextCompat.getColor(root.context, R.color.contrast)
                    )
                    root.strokeWidth = 0
                    (root.layoutParams as? ViewGroup.MarginLayoutParams)
                        ?.let { lp ->
                            lp.bottomMargin = if (isLast) 3.dpToPx(root.context) else 0
                            root.layoutParams = lp
                        }
                    root.radius = when {
                        isFirst && isLast -> 6f.dpToPx(root.context)
                        isFirst           -> 0f
                        isLast            -> 6f.dpToPx(root.context)
                        else              -> 0f
                    }
                } else {
                    (root.layoutParams as? ViewGroup.MarginLayoutParams)
                        ?.let { lp -> lp.bottomMargin = 3.dpToPx(root.context); root.layoutParams = lp }
                    root.radius = 6f.dpToPx(root.context)
                }

                title.text = element.customName ?: when (element.type) {
                    ElementType.TEXT    -> element.text ?: "Text"
                    ElementType.IMAGE   -> "Image"
                    ElementType.STICKER -> "Sticker"
                    ElementType.DRAW    -> "Brush"
                    ElementType.SHAPE   -> element.shapeType?.displayName ?: "Shape"
                    else                -> "Background"
                }

                image.setImageResource(when (element.type) {
                    ElementType.TEXT    -> R.drawable.ic_text_layer
                    ElementType.IMAGE   -> R.drawable.ic_image_layer
                    ElementType.STICKER -> R.drawable.ic_sticker
                    ElementType.DRAW    -> R.drawable.ic_pen
                    ElementType.SHAPE   -> R.drawable.ic_shapes
                    else                -> R.drawable.ic_stickers
                })

                if (element.isSelected) {
                    if (inSelectionMode) {
                        root.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.contrast)
                        )
                        root.strokeWidth = 0
                    } else {
                        root.setCardBackgroundColor(
                            ContextCompat.getColor(root.context, R.color.white)
                        )
                        root.strokeWidth = 2
                        root.strokeColor = ContextCompat.getColor(root.context, R.color.appColor)
                    }
                } else if (!isChild) {
                    root.setCardBackgroundColor(Color.TRANSPARENT)
                    root.strokeWidth = 0
                }

                val hideDragOrOptions = element.isLocked
                if (inSelectionMode) {
                    lock.visibility    = View.GONE
                    options.visibility = View.GONE
                    drag.visibility    = if (element.isSelected) View.VISIBLE else View.GONE
                } else {
                    lock.visibility    = View.VISIBLE
                    options.visibility = if (hideDragOrOptions) View.GONE else View.VISIBLE
                    drag.visibility    = if (hideDragOrOptions) View.GONE else View.VISIBLE
                }

                lock.setImageResource(
                    if (element.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock
                )

                lock.addPressEffect {
                    element.isLocked = !element.isLocked
                    onLockToggle(element)
                }
                options.setOnClickListener { v -> onMoreOptions(element, v) }
                drag.setOnTouchListener { _, _ ->
                    onStartDrag(this@ExpandedItem)
                    false
                }
                root.addPressEffectWithLongClick(
                    onClick     = { onItemClick(element) },
                    onLongClick = { onItemLongClick(element) }
                )
            }
            updateSize(slideOffset, rvWidth, rvPadding)
        }
    }}