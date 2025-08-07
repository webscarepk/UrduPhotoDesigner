package com.example.urduphotodesigner.ui.creation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutSizesItemBinding

class CanvasSizeAdapter(
    private var items: List<CanvasSize>,
    private val onClick: (CanvasSize) -> Unit
) : RecyclerView.Adapter<CanvasSizeAdapter.SizeViewHolder>() {

    inner class SizeViewHolder(private val binding: LayoutSizesItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CanvasSize){
            binding.apply {
                image.setImageResource(item.drawableResId)
                title.text = item.name
                size.text = "${item.width} x ${item.height}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SizeViewHolder {
        val binding = LayoutSizesItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SizeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SizeViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)

        holder.itemView.addPressEffect {
            onClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<CanvasSize>){
        items = newItems
        notifyDataSetChanged()
    }
}
