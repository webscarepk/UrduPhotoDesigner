package com.example.urduphotodesigner.ui.creation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.utils.Utils.getIconForSize
import com.example.urduphotodesigner.databinding.LayoutSizesFilterItemBinding
import com.example.urduphotodesigner.databinding.LayoutSizesItemBinding

class CanvasSizeAdapter(
    private var items: List<CanvasSize>,
    private val onClick: (CanvasSize) -> Unit,
    private var useNormalLayout: Boolean // true = LayoutSizesItem, false = LayoutSizesFilterItem
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_NORMAL = 0
        private const val TYPE_FILTER = 1
    }

    var selectedSizeName: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                old?.let { oldName ->
                    val oldPos = items.indexOfFirst { it.name == oldName }
                    if (oldPos != -1) notifyItemChanged(oldPos)
                }
                value?.let { newName ->
                    val newPos = items.indexOfFirst { it.name == newName }
                    if (newPos != -1) notifyItemChanged(newPos)
                }
            }
        }

    override fun getItemViewType(position: Int): Int {
        return if (useNormalLayout) TYPE_NORMAL else TYPE_FILTER
    }

    inner class NormalViewHolder(private val binding: LayoutSizesItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CanvasSize) {
            binding.apply {
                val icon = ContextCompat.getDrawable(itemView.context, getIconForSize(item.name))
                icon?.let { image.setIcon(it) }
                image.setCanvasSize(item.width, item.height)
                title.text = item.name
                size.text = "${item.width.toInt()} x ${item.height.toInt()}"
            }
        }
    }

    inner class FilterViewHolder(private val binding: LayoutSizesFilterItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CanvasSize) {
            binding.apply {
                val isSelected = item.name == selectedSizeName

                image.background = ContextCompat.getDrawable(
                    root.context,
                    if (isSelected) R.drawable.button_bg_stroke_fill_selected
                    else R.drawable.button_bg_stroke_fill
                )
                val icon = ContextCompat.getDrawable(itemView.context, getIconForSize(item.name))
                icon?.let { image.setIcon(it) }
                image.setCanvasSize(item.width, item.height)
                val displayName = item.name.substringAfterLast(" ")
                title.text = displayName
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_NORMAL -> NormalViewHolder(LayoutSizesItemBinding.inflate(inflater, parent, false))
            else -> FilterViewHolder(LayoutSizesFilterItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is NormalViewHolder -> holder.bind(item)
            is FilterViewHolder -> holder.bind(item)
        }
        holder.itemView.addPressEffect { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<CanvasSize>) {
        items = newItems
        notifyDataSetChanged()
    }
}