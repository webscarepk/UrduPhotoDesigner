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
        private const val TYPE_GROUP_HEADER = 0
        private const val TYPE_ITEM         = 1
    }

    private val items = mutableListOf<DisplayItem>()
    private var inSelectionMode = false

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

    // Returns CanvasElement list mapped from current display items (for external callers).
    fun currentList(): List<CanvasElement> = items.map { it.element }

    // Returns the raw DisplayItem at a position — used by drag callback for type-safe checks.
    fun getDisplayItemAt(position: Int): DisplayItem? = items.getOrNull(position)

    fun moveItem(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    /**
     * Replaces the DisplayItem at [position] with a re-typed version.
     * Used during drag to convert a Standalone→Child (join group) or Child→Standalone (leave group).
     * [newGroupId] is the groupId to assign when joining; null when leaving.
     */
    fun retypeItem(position: Int, asChild: Boolean, newGroupId: String?) {
        val current = items.getOrNull(position) ?: return
        val element = current.element
        // Mutate the element's groupId so applyLayerReorder reads the correct value at clearView.
        // Do NOT call notifyItemChanged here — calling any notify during an active drag
        // causes ItemTouchHelper to lose its ViewHolder reference, which leaves a ghost
        // copy of the item stuck on screen until the panel is reopened.
        // The visual rebind happens naturally after clearView fires and the ViewModel
        // pushes a new canvasElements list → buildDisplayList → submitList.
        val newItem = if (asChild) {
            element.groupId = newGroupId
            DisplayItem.Child(element)
        } else {
            element.groupId = null
            DisplayItem.Standalone(element)
        }
        items[position] = newItem
        // intentionally no notifyItemChanged — see comment above
    }

    // Returns all CanvasElements in current display order (collapsed children excluded).
    fun getItems(): List<CanvasElement> = items.map { it.element }

    // Returns the raw DisplayItem list — used by clearView to resolve groupId mutations.
    fun getDisplayItems(): List<DisplayItem> = items.toList()

    override fun getItemViewType(position: Int): Int =
        if (items[position] is DisplayItem.GroupHeader) TYPE_GROUP_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_GROUP_HEADER) {
            val binding = LayoutLayersGroupHeaderBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            GroupHeaderViewHolder(binding)
        } else {
            val binding = LayoutLayersItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val displayItem = items[position]) {
            is DisplayItem.GroupHeader -> (holder as GroupHeaderViewHolder).bind(displayItem.element)
            is DisplayItem.Child       -> (holder as ItemViewHolder).bind(
                displayItem.element,
                isChild = true,
                isFirst = position == 0 || items[position - 1] is DisplayItem.GroupHeader,
                isLast  = position == items.size - 1 || items[position + 1] !is DisplayItem.Child
            )
            is DisplayItem.Standalone  -> (holder as ItemViewHolder).bind(
                displayItem.element, isChild = false, isFirst = false, isLast = false
            )
        }
    }

    override fun getItemCount(): Int = items.size

    // ── Group header ViewHolder ───────────────────────────────────────────────

    inner class GroupHeaderViewHolder(
        private val binding: LayoutLayersGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(element: CanvasElement) {
            binding.apply {
                title.text = element.customName ?: "Group"

                val childCount = items.count {
                    it is DisplayItem.Child && it.element.groupId == element.id
                }
                badge.text       = childCount.toString()
                badge.visibility = if (childCount > 0) View.VISIBLE else View.GONE

                chevron.setImageResource(
                    if (element.isGroupCollapsed) R.drawable.ic_next else R.drawable.ic_down
                )

                // ── Lock icon ─────────────────────────────────────────────────
                lock.setImageResource(
                    if (element.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock
                )
                lock.addPressEffect { onLockToggle(element) }

                // ── Selection highlight ───────────────────────────────────────
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
                drag.setOnTouchListener { _, _ -> onStartDrag(this@GroupHeaderViewHolder); false }
                root.addPressEffectWithLongClick(
                    onClick     = { onGroupHeaderClick(element) },
                    onLongClick = { onItemLongClick(element) }
                )
            }
        }
    }

    // ── Item ViewHolder ───────────────────────────────────────────────────────

    inner class ItemViewHolder(
        private val binding: LayoutLayersItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(element: CanvasElement, isChild: Boolean, isFirst: Boolean, isLast: Boolean) {
            binding.apply {

                // ── Child indent & container styling ──────────────────────────
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

                // ── Title ─────────────────────────────────────────────────────
                title.text = element.customName ?: when (element.type) {
                    ElementType.TEXT    -> element.text ?: "Text"
                    ElementType.IMAGE   -> "Image"
                    ElementType.STICKER -> "Sticker"
                    ElementType.DRAW    -> "Brush"
                    ElementType.SHAPE   -> element.shapeType?.displayName ?: "Shape"
                    else                -> "Background"
                }

                // ── Icon ──────────────────────────────────────────────────────
                image.setImageResource(when (element.type) {
                    ElementType.TEXT    -> R.drawable.ic_text_layer
                    ElementType.IMAGE   -> R.drawable.ic_image_layer
                    ElementType.STICKER -> R.drawable.ic_sticker
                    ElementType.DRAW    -> R.drawable.ic_pen
                    ElementType.SHAPE   -> R.drawable.ic_shapes
                    else                -> R.drawable.ic_stickers
                })

                // ── Selection highlight (override child bg when selected) ──────
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
                // Children keep their contrast bg set above unless selected

                // ── Icon visibility ───────────────────────────────────────────
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

                // ── Lock icon ─────────────────────────────────────────────────
                lock.setImageResource(
                    if (element.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock
                )

                // ── Click listeners ───────────────────────────────────────────
                lock.addPressEffect {
                    element.isLocked = !element.isLocked
                    onLockToggle(element)
                }
                options.setOnClickListener { v -> onMoreOptions(element, v) }
                drag.setOnTouchListener { _, _ ->
                    onStartDrag(this@ItemViewHolder)
                    false
                }
                root.addPressEffectWithLongClick(
                    onClick     = { onItemClick(element) },
                    onLongClick = { onItemLongClick(element) }
                )
            }
        }

        private fun Float.dpToPx(context: android.content.Context): Float =
            this * context.resources.displayMetrics.density

        private fun Int.dpToPx(context: android.content.Context): Int =
            (this * context.resources.displayMetrics.density).toInt()
    }
}