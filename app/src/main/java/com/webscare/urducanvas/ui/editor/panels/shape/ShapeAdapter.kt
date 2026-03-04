package com.webscare.urducanvas.ui.editor.panels.shape

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.utils.ShapeRenderUtils.drawShape
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutColorItemBinding

class ShapeAdapter(
    private val context: Context,
    private val shapes: List<ShapeType>,
    private val onShapeSelected: (ShapeType) -> Unit
) : RecyclerView.Adapter<ShapeAdapter.ShapeViewHolder>() {

    var selectedShape: ShapeType? = null

    inner class ShapeViewHolder(val binding: LayoutColorItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(shape: ShapeType, isSelected: Boolean) {
            // 🎨 Draw shape into bitmap
            val size = 220
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.black)
                strokeWidth = 10f
                style = Paint.Style.STROKE
            }

            val radius = size * 0.15f
            val rect = RectF(size * 0.2f, size * 0.2f, size * 0.8f, size * 0.8f)

            if (shape == ShapeType.RECTANGLE) {
                drawShape(
                    canvas,
                    paint,
                    shape,
                    rect,
                    0f
                )
            } else {
                drawShape(
                    canvas,
                    paint,
                    shape,
                    rect,
                    radius
                )
            }

            // 🟢 Apply shape bitmap
            binding.colorView.setImageBitmap(bitmap)

            // 🎯 Selection style (minimal, modern)
            binding.root.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.contrast)
            )

            if (isSelected) {
                binding.root.strokeWidth = 4
                binding.root.strokeColor = ContextCompat.getColor(context, R.color.appColor)
            } else {
                binding.root.strokeWidth = 0
            }

            // 🩵 Add click animation + callback
            binding.root.addPressEffect {
                selectedShape = shape
                notifyDataSetChanged()
                onShapeSelected.invoke(shape)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeViewHolder {
        val binding = LayoutColorItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ShapeViewHolder(binding)
    }

    override fun getItemCount(): Int = shapes.size

    override fun onBindViewHolder(holder: ShapeViewHolder, position: Int) {
        val shape = shapes[position]
        holder.bind(shape, shape == selectedShape)
    }
}