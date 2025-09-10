package com.example.urduphotodesigner.ui.editor.panels.animations

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.AnimationItem
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutAnimationItemBinding

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
        val binding = LayoutAnimationItemBinding.inflate(
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

    class AnimationViewHolder(private val binding: LayoutAnimationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AnimationItem, isSelected: Boolean) {
            binding.filterName.text = item.name
            binding.filterPreview.setImageResource(item.iconResId)
            // Apply selection styles
            binding.filterName.alpha = if (isSelected) 1.0f else 0.5f

            binding.card.strokeWidth = if (isSelected) 2 else 0
        }
    }
}
