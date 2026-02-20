package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ShapeItem
import com.example.urduphotodesigner.databinding.LayoutShapeItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class ShapesAdapter(
    private val shapesList: List<com.webscare.urducanvas.data.model.ShapeItem>,
    private val onShapeSelected: (com.webscare.urducanvas.common.canvas.enums.LabelShape) -> Unit
) : RecyclerView.Adapter<ShapesAdapter.ShapeViewHolder>() {

    var selectedShape: com.webscare.urducanvas.common.canvas.enums.LabelShape = _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LabelShape.RECTANGLE_FILL // Default selected shape

    inner class ShapeViewHolder(val binding: LayoutShapeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(shapeItem: com.webscare.urducanvas.data.model.ShapeItem) {
            // Set the shape icon
            binding.shape.setImageResource(shapeItem.iconResId)

            // Check if the current item is selected
            val isSelected = shapeItem.shape == selectedShape
            if (isSelected) {
                binding.root.strokeWidth = 4
                binding.root.setCardBackgroundColor(Color.WHITE)
                binding.root.strokeColor =
                    ContextCompat.getColor(binding.root.context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
                binding.root.setCardBackgroundColor(Color.WHITE)
            }

            // Set on click listener to select the shape
            binding.root.addPressEffect {
                selectedShape = shapeItem.shape
                onShapeSelected(shapeItem.shape)
                notifyDataSetChanged() // Update the UI to show the selected shape
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeViewHolder {
        val binding = LayoutShapeItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShapeViewHolder(binding)
    }

    override fun getItemCount(): Int = shapesList.size

    override fun onBindViewHolder(holder: ShapeViewHolder, position: Int) {
        holder.bind(shapesList[position])
    }
}
