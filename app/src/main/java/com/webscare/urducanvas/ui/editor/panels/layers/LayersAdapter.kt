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
import com.webscare.urducanvas.databinding.LayoutLayersItemBinding

class LayersAdapter(
    private val onLockToggle:   (element: CanvasElement) -> Unit,
    private val onMoreOptions:  (element: CanvasElement, anchorView: View) -> Unit,
    private val onItemClick:    (element: CanvasElement) -> Unit,
    private val onItemLongClick:(element: CanvasElement) -> Unit,
    private val onStartDrag:    (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<LayersAdapter.CanvasElementViewHolder>() {

    private val elements      = mutableListOf<CanvasElement>()
    private var inSelectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        if (inSelectionMode != enabled) {
            inSelectionMode = enabled
            notifyDataSetChanged()
        }
    }

    fun submitList(newElements: List<CanvasElement>) {
        elements.clear()
        elements.addAll(newElements)
        notifyDataSetChanged()
    }

    fun currentList(): List<CanvasElement> = elements

    fun moveItem(from: Int, to: Int) {
        if (from !in elements.indices || to !in elements.indices) return
        val item = elements.removeAt(from)
        elements.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getItems(): List<CanvasElement> = elements

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CanvasElementViewHolder {
        val binding = LayoutLayersItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CanvasElementViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CanvasElementViewHolder, position: Int) {
        holder.bind(elements[position])
    }

    override fun getItemCount(): Int = elements.size

    inner class CanvasElementViewHolder(
        private val binding: LayoutLayersItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(element: CanvasElement) {
            binding.apply {

                // ── Title ─────────────────────────────────────────────────
                // customName takes priority — set via the Rename popup.
                // Falls back to type-based default name.
                title.text = element.customName ?: when (element.type) {
                    ElementType.TEXT       -> element.text ?: "Text"
                    ElementType.IMAGE      -> "Image"
                    ElementType.STICKER    -> "Sticker"
                    ElementType.DRAW       -> "Brush"
                    ElementType.SHAPE      -> element.shapeType?.displayName ?: "Shape"
                    else                   -> "Background"
                }

                // ── Icon ──────────────────────────────────────────────────
                image.setImageResource(when (element.type) {
                    ElementType.TEXT    -> R.drawable.ic_text_layer
                    ElementType.IMAGE   -> R.drawable.ic_image_layer
                    ElementType.STICKER -> R.drawable.ic_sticker
                    ElementType.DRAW    -> R.drawable.ic_pen
                    ElementType.SHAPE   -> R.drawable.ic_shapes
                    else                -> R.drawable.ic_stickers
                })

                // ── Selection highlight ───────────────────────────────────
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
                } else {
                    root.setCardBackgroundColor(Color.TRANSPARENT)
                    root.strokeWidth = 0
                }

                // ── Icon visibility ───────────────────────────────────────
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

                // ── Lock icon ─────────────────────────────────────────────
                lock.setImageResource(
                    if (element.isLocked) R.drawable.ic_lock else R.drawable.ic_unlock
                )

                // ── Click listeners ───────────────────────────────────────
                lock.addPressEffect {
                    element.isLocked = !element.isLocked
                    onLockToggle(element)
                }
                options.setOnClickListener { v -> onMoreOptions(element, v) }
                drag.setOnTouchListener { _, _ ->
                    onStartDrag(this@CanvasElementViewHolder)
                    false
                }
                root.addPressEffectWithLongClick(
                    onClick    = { onItemClick(element) },
                    onLongClick = { onItemLongClick(element) }
                )
            }
        }
    }
}