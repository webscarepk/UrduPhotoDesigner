package com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.ColorItem
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.LayoutColorItemBinding
import com.example.urduphotodesigner.databinding.LayoutColorPickerItemBinding // Assuming you create this layout
import com.example.urduphotodesigner.databinding.LayoutEyeDropperItemBinding // New layout for eye dropper

class ColorsAdapter(
    private val colorList: List<ColorItem>,
    private val onColorSelected: (ColorItem) -> Unit,
    private val onNoneSelected: () -> Unit,
    private val onColorPickerClicked: () -> Unit,
    private val onEyeDropperClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Define view types
    private val VIEW_TYPE_EYEDROPPER    = 0
    private val VIEW_TYPE_NONE          = 1
    private val VIEW_TYPE_COLOR_PICKER  = 2
    private val VIEW_TYPE_COLOR_ITEM    = 3

    var selectedColor: Int = Color.BLACK
        set(value) {
            field = value
            colorList.forEach { it.isSelected = (it.colorCode.toColorInt() == value) }
            notifyDataSetChanged()
        }

    inner class EyeDropperViewHolder(val binding: LayoutEyeDropperItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.addPressEffect { onEyeDropperClicked.invoke() }
        }
    }

    inner class NoneViewHolder(val binding: LayoutColorPickerItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.colorView.setImageResource(R.drawable.ic_none)
            binding.root.addPressEffect { onNoneSelected.invoke() }
        }
    }

    inner class ColorPickerViewHolder(val binding: LayoutColorPickerItemBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.addPressEffect { onColorPickerClicked.invoke() }
        }
    }

    inner class ColorViewHolder(val binding: LayoutColorItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(colorItem: ColorItem) {
            binding.colorView.setBackgroundColor(colorItem.colorCode.toColorInt())
            val isSelected = colorItem.colorCode.toColorInt() == selectedColor
            if (isSelected) {
                binding.root.strokeWidth = 4
                binding.root.setCardBackgroundColor(Color.WHITE)
                binding.root.strokeColor = ContextCompat.getColor(binding.root.context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
                binding.root.setCardBackgroundColor(Color.WHITE)
            }
            binding.root.addPressEffect { onColorSelected.invoke(colorItem) }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_EYEDROPPER
            1 -> VIEW_TYPE_NONE
            2 -> VIEW_TYPE_COLOR_PICKER
            else -> VIEW_TYPE_COLOR_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_EYEDROPPER -> {
                val binding = LayoutEyeDropperItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                EyeDropperViewHolder(binding)
            }
            VIEW_TYPE_NONE -> {
                val binding = LayoutColorPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                NoneViewHolder(binding)
            }
            VIEW_TYPE_COLOR_PICKER -> {
                val binding = LayoutColorPickerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ColorPickerViewHolder(binding)
            }
            else -> {
                val binding = LayoutColorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ColorViewHolder(binding)
            }
        }
    }

    override fun getItemCount(): Int = colorList.size + 3

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ColorViewHolder) {
            holder.bind(colorList[position - 3])
        }
    }
}
