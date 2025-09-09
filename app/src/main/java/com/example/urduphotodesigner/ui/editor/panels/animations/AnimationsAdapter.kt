package com.example.urduphotodesigner.ui.editor.panels.animations

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.AnimationItem
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutFilterItemBinding

class AnimationsAdapter(
    private val animations: List<AnimationItem>,
    private val onAnimationSelected: (AnimationItem) -> Unit
) : RecyclerView.Adapter<AnimationsAdapter.AnimationViewHolder>() {

    var selectedAnimation: Any? = null
        set(value) {
            val oldPos = animations.indexOfFirst { it.name == field }
            val newPos = animations.indexOfFirst { it.name == value }
            field = value
            if (oldPos != -1) notifyItemChanged(oldPos)
            if (newPos != -1) notifyItemChanged(newPos)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimationViewHolder {
        val binding = LayoutFilterItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AnimationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnimationViewHolder, position: Int) {
        val item = animations[position]
        holder.bind(item, item.name == selectedAnimation)

        holder.itemView.addPressEffect {
            onAnimationSelected(item)
            selectedAnimation = item.name
        }
    }

    override fun getItemCount(): Int = animations.size

    class AnimationViewHolder(private val binding: LayoutFilterItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AnimationItem, isSelected: Boolean) {
            binding.filterName.text = item.name

            // Apply selection styles
            binding.filterName.alpha = if (isSelected) 1.0f else 0.5f
            if (isSelected) {
                binding.card.strokeWidth = 4
                binding.card.setCardBackgroundColor(Color.WHITE)
                binding.card.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.white)
            } else {
                binding.card.strokeWidth = 0
                binding.card.setCardBackgroundColor(Color.WHITE)
            }
        }
    }
}
