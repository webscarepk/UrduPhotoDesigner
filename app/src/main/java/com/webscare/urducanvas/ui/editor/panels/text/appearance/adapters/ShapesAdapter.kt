package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.ShapeItem
import com.webscare.urducanvas.databinding.LayoutShapeItemBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect

class ShapesAdapter(
    private val shapesList: List<com.webscare.urducanvas.data.model.ShapeItem>,
    private val onShapeSelected: (LabelShape) -> Unit
) : RecyclerView.Adapter<ShapesAdapter.ShapeViewHolder>() {

    var selectedShape: LabelShape = LabelShape.RECTANGLE_FILL // Default selected shape

    inner class ShapeViewHolder(val binding: LayoutShapeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(shapeItem: com.webscare.urducanvas.data.model.ShapeItem) {
            // Set the shape icon
            binding.shape.setImageResource(shapeItem.iconResId)

            // Check if the current item is selected
            val isSelected = shapeItem.shape == selectedShape
            val context = binding.root.context
            val contrastColor = ContextCompat.getColor(context, R.color.contrast)
            val strokePx = (1.5f * context.resources.displayMetrics.density + 0.5f).toInt()
            if (isSelected) {
                binding.root.strokeWidth = strokePx
                binding.root.setCardBackgroundColor(Color.WHITE)
                binding.root.strokeColor =
                    ContextCompat.getColor(context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
                binding.root.setCardBackgroundColor(contrastColor)
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
