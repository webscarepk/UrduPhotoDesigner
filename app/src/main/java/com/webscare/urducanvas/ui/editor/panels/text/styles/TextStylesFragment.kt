package com.webscare.urducanvas.ui.editor.panels.text.styles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PresetCategory
import com.webscare.urducanvas.data.model.TextStylePreset
import com.webscare.urducanvas.data.repository.TextStylesRepository
import com.webscare.urducanvas.databinding.FragmentTextStylesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TextStylesFragment : Fragment() {

    private var _binding: FragmentTextStylesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    private var selectedCategory: PresetCategory = PresetCategory.BADGES_SALE
    private lateinit var categoriesAdapter: CategoriesAdapter
    private lateinit var presetsAdapter: PresetsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextStylesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCategoriesRecyclerView()
        setupPresetsRecyclerView()
        loadPresetsForCategory(selectedCategory)
    }

    private fun setupCategoriesRecyclerView() {
        val categoriesList = PresetCategory.values().toList()
        categoriesAdapter = CategoriesAdapter(categoriesList, selectedCategory) { cat ->
            selectedCategory = cat
            categoriesAdapter.setSelected(cat)
            loadPresetsForCategory(cat)
        }
        binding.categoriesRecyclerView.adapter = categoriesAdapter
    }

    private fun setupPresetsRecyclerView() {
        presetsAdapter = PresetsAdapter(emptyList(), onPresetClick = { preset ->
            viewModel.applyTextStylePreset(preset)
        }, onSaveCurrentStyleClick = {
            saveCurrentSelectedElementStyle()
        })
        binding.presetsRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.presetsRecyclerView.adapter = presetsAdapter
    }

    private fun loadPresetsForCategory(category: PresetCategory) {
        val presets = TextStylesRepository.getPresetsByCategory(category, requireContext())
        presetsAdapter.setItems(presets, showSaveCard = (category == PresetCategory.MY_STYLES))
    }

    private fun saveCurrentSelectedElementStyle() {
        val currentList = viewModel.canvasElements.value ?: return
        val selectedEl = currentList.firstOrNull { it.isSelected && it.type == com.webscare.urducanvas.common.canvas.enums.ElementType.TEXT }
            ?: return

        val customPreset = TextStylePreset(
            id = "custom_${System.currentTimeMillis()}",
            name = "Custom Style",
            category = PresetCategory.MY_STYLES,
            textColor = selectedEl.paintColor,
            textGradient = selectedEl.fillGradient,
            strokeColor = selectedEl.strokeColor,
            strokeWidth = selectedEl.strokeWidth,
            shadowColor = selectedEl.shadowColor,
            shadowRadius = selectedEl.shadowRadius,
            shadowDx = selectedEl.shadowDx,
            shadowDy = selectedEl.shadowDy,
            hasLabel = selectedEl.hasLabel,
            labelShape = selectedEl.labelShape,
            labelColor = selectedEl.labelColor,
            labelGradient = selectedEl.labelGradient,
            labelSecondaryColor = selectedEl.labelSecondaryColor,
            labelStrokeColor = selectedEl.labelStrokeColor,
            labelStrokeWidth = selectedEl.labelStrokeWidth,
            hasGlossHighlight = selectedEl.hasGlossHighlight,
            hasFoldedRibbonFlaps = selectedEl.hasFoldedRibbonFlaps,
            isCustomUserSaved = true
        )

        TextStylesRepository.saveCustomUserStyle(requireContext(), customPreset)
        loadPresetsForCategory(PresetCategory.MY_STYLES)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TextStylesFragment()
    }

    // -----------------------------------------------------------------
    // Categories Sidebar Adapter (Text highlight + Green Indicator Bar)
    // -----------------------------------------------------------------
    private class CategoriesAdapter(
        private val categories: List<PresetCategory>,
        private var selectedCategory: PresetCategory,
        private val onCategoryClick: (PresetCategory) -> Unit
    ) : RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.categoryTitle)
            val indicator: View = view.findViewById(R.id.activeIndicator)
            val container: View = view.findViewById(R.id.categoryItemContainer)
        }

        fun setSelected(cat: PresetCategory) {
            selectedCategory = cat
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_style_category_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cat = categories[position]
            holder.title.text = cat.displayName

            val isSelected = (cat == selectedCategory)
            if (isSelected) {
                holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.appColor))
                holder.title.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                holder.indicator.visibility = View.VISIBLE
            } else {
                holder.title.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.black))
                holder.title.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                holder.indicator.visibility = View.GONE
            }

            holder.container.addPressEffect {
                onCategoryClick(cat)
            }
        }

        override fun getItemCount(): Int = categories.size
    }

    // -----------------------------------------------------------------
    // Presets Grid Adapter
    // -----------------------------------------------------------------
    private class PresetsAdapter(
        private var presets: List<TextStylePreset>,
        private val onPresetClick: (TextStylePreset) -> Unit,
        private val onSaveCurrentStyleClick: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var showSaveCard = false

        companion object {
            private const val TYPE_SAVE_CARD = 0
            private const val TYPE_PRESET_CARD = 1
        }

        fun setItems(newPresets: List<TextStylePreset>, showSaveCard: Boolean) {
            this.presets = newPresets
            this.showSaveCard = showSaveCard
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return if (showSaveCard && position == 0) TYPE_SAVE_CARD else TYPE_PRESET_CARD
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_text_style_preset_item, parent, false)
            return if (viewType == TYPE_SAVE_CARD) SaveViewHolder(view) else PresetViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is SaveViewHolder) {
                holder.previewImg.setImageResource(R.drawable.ic_add)
                holder.previewImg.visibility = View.VISIBLE
                holder.titleTxt.text = "+ Save"
                holder.titleTxt.visibility = View.VISIBLE
                holder.itemView.addPressEffect { onSaveCurrentStyleClick() }
            } else if (holder is PresetViewHolder) {
                val realPos = if (showSaveCard) position - 1 else position
                val preset = presets[realPos]
                
                holder.titleTxt.visibility = View.GONE
                holder.previewImg.visibility = View.VISIBLE
                
                val bmp = generatePresetThumbnail(holder.itemView.context, preset)
                holder.previewImg.setImageBitmap(bmp)

                holder.itemView.addPressEffect { onPresetClick(preset) }
            }
        }

        override fun getItemCount(): Int {
            return if (showSaveCard) presets.size + 1 else presets.size
        }

        class SaveViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val previewImg: ImageView = view.findViewById(R.id.presetPreviewImage)
            val titleTxt: TextView = view.findViewById(R.id.presetTitleText)
        }

        class PresetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val previewImg: ImageView = view.findViewById(R.id.presetPreviewImage)
            val titleTxt: TextView = view.findViewById(R.id.presetTitleText)
        }

        // Generate dynamic mini preview bitmap of styled text
        private fun generatePresetThumbnail(context: android.content.Context, preset: TextStylePreset): Bitmap {
            val width = 160
            val height = 100
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val text = "Urdu"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            val textWidth = textPaint.measureText(text)
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.bottom - fontMetrics.top

            val cx = width / 2f
            val cy = height / 2f + (textHeight / 4f)

            // Draw Label Background if present
            if (preset.hasLabel) {
                val padX = 24f
                val padY = 12f
                val rect = RectF(cx - textWidth / 2f - padX, height / 2f - textHeight / 2f - padY, cx + textWidth / 2f + padX, height / 2f + textHeight / 2f + padY)

                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }

                if (preset.labelGradient != null) {
                    val colors = preset.labelGradient.colors.toIntArray()
                    labelPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP)
                } else {
                    labelPaint.color = preset.labelColor
                }

                // Folded Ribbon Flaps
                if (preset.hasFoldedRibbonFlaps && preset.labelSecondaryColor != null) {
                    val flapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = preset.labelSecondaryColor; style = Paint.Style.FILL }
                    val flapPath = Path().apply {
                        moveTo(rect.left, rect.bottom)
                        lineTo(rect.left - 10f, rect.bottom + 6f)
                        lineTo(rect.left + 8f, rect.bottom)
                        close()
                        moveTo(rect.right, rect.top)
                        lineTo(rect.right + 10f, rect.top - 6f)
                        lineTo(rect.right - 8f, rect.top)
                        close()
                    }
                    canvas.drawPath(flapPath, flapPaint)
                }

                // Main Shape
                when (preset.labelShape) {
                    LabelShape.CAPSULE_FILL -> canvas.drawRoundRect(rect, 20f, 20f, labelPaint)
                    LabelShape.SLANTED_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left + 12f, rect.top)
                            lineTo(rect.right, rect.top)
                            lineTo(rect.right - 12f, rect.bottom)
                            lineTo(rect.left, rect.bottom)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    LabelShape.TAG_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left, rect.top)
                            lineTo(rect.right - 12f, rect.top)
                            lineTo(rect.right, rect.centerY())
                            lineTo(rect.right - 12f, rect.bottom)
                            lineTo(rect.left, rect.bottom)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    LabelShape.RIBBON_FILL -> {
                        val path = Path().apply {
                            moveTo(rect.left, rect.top)
                            lineTo(rect.left + 10f, rect.centerY())
                            lineTo(rect.left, rect.bottom)
                            lineTo(rect.right - 10f, rect.bottom)
                            lineTo(rect.right, rect.centerY())
                            lineTo(rect.right - 10f, rect.top)
                            close()
                        }
                        canvas.drawPath(path, labelPaint)
                    }
                    else -> canvas.drawRoundRect(rect, 10f, 10f, labelPaint)
                }

                // Inner Border Line
                if (preset.labelStrokeColor != null && preset.labelStrokeWidth > 0f) {
                    val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = preset.labelStrokeColor
                        style = Paint.Style.STROKE
                        strokeWidth = preset.labelStrokeWidth
                    }
                    canvas.drawRoundRect(RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f), 8f, 8f, strokeP)
                }

                // Glossy Highlight
                if (preset.hasGlossHighlight) {
                    val glossP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(rect.left, rect.top, rect.left, rect.centerY(), Color.argb(100, 255, 255, 255), Color.argb(10, 255, 255, 255), Shader.TileMode.CLAMP)
                    }
                    canvas.drawRoundRect(RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.centerY()), 8f, 8f, glossP)
                }
            }

            // Draw Text
            if (preset.textGradient != null) {
                val colors = preset.textGradient.colors.toIntArray()
                textPaint.shader = LinearGradient(cx - 30f, cy, cx + 30f, cy, colors, null, Shader.TileMode.CLAMP)
            } else {
                textPaint.color = preset.textColor ?: Color.BLACK
            }

            if (preset.shadowRadius > 0f && preset.shadowColor != null) {
                textPaint.setShadowLayer(preset.shadowRadius, preset.shadowDx, preset.shadowDy, preset.shadowColor)
            }

            canvas.drawText(text, cx, cy, textPaint)

            if (preset.strokeWidth > 0f && preset.strokeColor != null) {
                val strokeP = Paint(textPaint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = preset.strokeWidth
                    color = preset.strokeColor
                    shader = null
                }
                canvas.drawText(text, cx, cy, strokeP)
            }

            return bitmap
        }
    }
}
