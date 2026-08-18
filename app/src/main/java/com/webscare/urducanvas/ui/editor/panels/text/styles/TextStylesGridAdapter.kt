package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.TextStylePreset

class TextStylesGridAdapter(
    private var presets: List<TextStylePreset>,
    private val onPresetClick: (TextStylePreset) -> Unit
) : RecyclerView.Adapter<TextStylesGridAdapter.PresetViewHolder>() {

    var selectedPresetId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_text_style_preset_item, parent, false)
        return PresetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        val preset = presets[position]
        holder.titleTxt.visibility = View.GONE
        holder.previewImg.visibility = View.VISIBLE

        val isSelected = preset.id == selectedPresetId
        val cardView = holder.itemView as? com.google.android.material.card.MaterialCardView
        if (cardView != null) {
            val strokePx = (1.5f * cardView.context.resources.displayMetrics.density + 0.5f).toInt()
            cardView.strokeWidth = if (isSelected) strokePx else 0
            cardView.strokeColor = androidx.core.content.ContextCompat.getColor(cardView.context, R.color.appColor)
        }

        val bmp = TextStyleThumbnailRenderer.getCachedOrGenerateThumbnail(holder.itemView.context, preset)
        holder.previewImg.setImageBitmap(bmp)

        holder.itemView.addPressEffect {
            selectedPresetId = preset.id
            onPresetClick(preset)
        }
    }

    override fun getItemCount(): Int = presets.size

    class PresetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewImg: ImageView = view.findViewById(R.id.presetPreviewImage)
        val titleTxt: TextView = view.findViewById(R.id.presetTitleText)
    }
}
