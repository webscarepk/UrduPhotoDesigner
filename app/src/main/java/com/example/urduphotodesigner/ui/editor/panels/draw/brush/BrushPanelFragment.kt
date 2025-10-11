package com.example.urduphotodesigner.ui.editor.panels.draw.brush

import android.content.res.ColorStateList
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.BrushStyle
import com.example.urduphotodesigner.common.canvas.enums.GradientPickerTarget
import com.example.urduphotodesigner.common.canvas.enums.PickerTarget
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentBrushPanelBinding
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue
import androidx.core.graphics.createBitmap
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.model.BrushSettings
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@AndroidEntryPoint
class BrushPanelFragment : Fragment() {
    private var _binding: FragmentBrushPanelBinding? = null
    private val binding get() = _binding!!
    private var tabName: String = ""
    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabName = it.getString("tabName")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushPanelBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    private fun setEvents() {
        binding.brushPreview.post {
            updateBrushPreview()
            viewModel.enterDrawingMode()
        }

        binding.sizePane.isVisible = tabName == "Size"
        binding.stylePane.isVisible = tabName == "Style"
        binding.colorPane.isVisible = tabName == "Color"

        binding.solid.addPressEffect {
            if (!binding.colors.isVisible) {
                togglePanels()
            }
        }

        binding.gradient.addPressEffect {
            if (!binding.gradients.isVisible) {
                togglePanels()
            }
        }

        binding.thicknessBar.apply {
            min = 1
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val thickness = progress.toFloat()
                    binding.thickness.text = progress.toString()
                    if (fromUser) {
                        viewModel.setBrushThickness(thickness)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.hardnessBar.apply {
            min = 0
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.hardness.text = "$progress%"
                    if (fromUser) {
                        val hardness = progress.toFloat() / 100f   // normalized 0.0–1.0
                        viewModel.setBrushHardness(hardness)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        setupRecyclerView()
        initObservers()
    }

    private fun createBackgroundGradientShader(
        gradientItem: GradientItem, width: Float, height: Float
    ): Shader {
        val colors = gradientItem.colors.toIntArray()
        val positions = gradientItem.positions.toFloatArray()

        // compute actual center from relative values
        val cx = width * gradientItem.centerX
        val cy = height * gradientItem.centerY

        val baseShader = when (gradientItem.type) {
            GradientType.LINEAR -> {
                // angle in radians
                val theta = Math.toRadians(gradientItem.angle.toDouble())
                // full hypotenuse scaled, half on each side
                val halfLen = (hypot(width, height) * gradientItem.scale / 2f)
                val dx = (cos(theta) * halfLen).toFloat()
                val dy = (sin(theta) * halfLen).toFloat()

                LinearGradient(
                    cx - dx, cy - dy, cx + dx, cy + dy, colors, positions, Shader.TileMode.CLAMP
                )
            }

            GradientType.RADIAL -> {
                // radius based on the smaller dimension
                val radius =
                    min(width, height) / 2f * gradientItem.radialRadiusFactor * gradientItem.scale
                RadialGradient(
                    cx, cy, radius, colors, positions, Shader.TileMode.CLAMP
                )
            }

            GradientType.SWEEP -> {
                SweepGradient(cx, cy, colors, positions).apply {
                    // rotate start angle around the chosen center
                    val m = Matrix().apply {
                        postRotate(gradientItem.sweepStartAngle, cx, cy)
                    }
                    setLocalMatrix(m)
                }
            }
        }

        return baseShader
    }

    private fun updateBrushPreview() {
        val preview = binding.brushPreview
        val width = preview.width.takeIf { it > 0 } ?: return
        val height = preview.height.takeIf { it > 0 } ?: return

        val userThickness = viewModel.brushThickness.value ?: 20f
        val hardness = viewModel.brushHardness.value ?: 1f
        val currentStyle = viewModel.currentBrushStyle.value ?: BrushStyle.PEN

        val maxVisibleStroke = height * 0.8f
        val brushStrokeWidth = (userThickness / 100f * maxVisibleStroke)
            .coerceIn(height * 0.05f, maxVisibleStroke)
        val blurPadding = (1f - hardness) * 15f
        val safePadding = blurPadding + brushStrokeWidth * 0.4f

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = brushStrokeWidth
        }

        // 🌈 Apply color or gradient
        val gradient = viewModel.brushGradient.value
        if (gradient != null) {
            paint.shader = createBackgroundGradientShader(gradient, width.toFloat(), height.toFloat())
        } else {
            paint.color = viewModel.brushColor.value ?: Color.BLACK
        }

        // ☁️ Edge softness
        paint.maskFilter = if (hardness < 0.9f)
            BlurMaskFilter((1f - hardness) * 25f, BlurMaskFilter.Blur.NORMAL)
        else null

        // ✏️ Base coordinates
        val centerY = height / 2f
        val left = safePadding
        val right = width - safePadding

        // 🖌️ Render per style
        when (currentStyle) {
            BrushStyle.MARKER -> {
                paint.strokeCap = Paint.Cap.BUTT
                paint.alpha = 240
                val rect = RectF(left, centerY - brushStrokeWidth / 2, right, centerY + brushStrokeWidth / 2)
                canvas.drawRoundRect(rect, 5f, 5f, paint)
            }

            BrushStyle.HIGHLIGHTER -> {
                paint.strokeCap = Paint.Cap.BUTT
                paint.alpha = 130
                val path = Path().apply {
                    val tilt = brushStrokeWidth * 0.3f
                    moveTo(left, centerY + tilt)
                    lineTo(right, centerY - tilt)
                }
                paint.strokeWidth = brushStrokeWidth * 1.4f
                canvas.drawPath(path, paint)
            }

            BrushStyle.PENCIL -> {
                paint.alpha = 190
                paint.pathEffect = DashPathEffect(floatArrayOf(4f, 5f, 1f, 3f), 0f)
                canvas.drawLine(left, centerY, right, centerY, paint)
            }

            BrushStyle.BRUSH -> {
                val random = java.util.Random()
                val hardness = viewModel.brushHardness.value ?: 1f
                val softness = (1f - hardness).coerceIn(0f, 1f)
                val baseColor = viewModel.brushColor.value ?: Color.BLACK
                val gradient = viewModel.brushGradient.value

                val path = Path().apply {
                    moveTo(left, centerY)
                    cubicTo(
                        width * 0.25f, centerY - brushStrokeWidth * 0.2f,
                        width * 0.75f, centerY + brushStrokeWidth * 0.2f,
                        right, centerY
                    )
                }

                val mainShader: Shader? = gradient?.let {
                    createBackgroundGradientShader(it, width.toFloat(), height.toFloat())
                } ?: LinearGradient(
                    left, centerY, right, centerY,
                    intArrayOf(
                        ColorUtils.setAlphaComponent(baseColor, 255),
                        ColorUtils.setAlphaComponent(baseColor, 240),
                        ColorUtils.setAlphaComponent(baseColor, 120),
                        ColorUtils.setAlphaComponent(baseColor, 0)
                    ),
                    floatArrayOf(0f, 0.4f, 0.8f, 1f),
                    Shader.TileMode.CLAMP
                )

                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = brushStrokeWidth
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    shader = mainShader
                    alpha = (230 - softness * 70).toInt()
                }

                canvas.drawPath(path, bodyPaint)

                val bristlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = brushStrokeWidth * 0.06f
                    shader = mainShader
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                }

                val totalStreaks = (10 + softness * 25).toInt()
                for (i in 0 until totalStreaks) {
                    val factor = (i.toFloat() / totalStreaks)
                    val startX = left + factor * width * 0.2f
                    val endX = left + factor * width + random.nextFloat() * (width * 0.2f)
                    val yOffset = (random.nextFloat() - 0.5f) * brushStrokeWidth * (0.8f + softness)
                    val lineY = centerY + yOffset
                    val fadeAlpha = (180 * (1f - factor * 0.9f)).toInt()

                    bristlePaint.alpha = fadeAlpha
                    canvas.drawLine(startX, lineY, endX, lineY, bristlePaint)
                }

                if (softness > 0.05f) {
                    val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = brushStrokeWidth * 1.1f
                        shader = mainShader
                        alpha = (softness * 90).toInt()
                        val blurRadius = (brushStrokeWidth * 0.25f * softness).coerceAtLeast(0.5f)
                        maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawPath(path, edgePaint)
                }
            }

            BrushStyle.PEN -> {
                paint.alpha = 255
                paint.pathEffect = null
                val path = Path().apply {
                    moveTo(left, centerY)
                    cubicTo(
                        width * 0.3f, centerY - brushStrokeWidth,
                        width * 0.7f, centerY + brushStrokeWidth,
                        right, centerY
                    )
                }
                canvas.drawPath(path, paint)
            }

            BrushStyle.ERASER -> {
                // Clean cut (invisible stroke)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                canvas.drawLine(left, centerY, right, centerY, paint)
            }
        }

        preview.setImageBitmap(bitmap)
    }

    private fun togglePanels() {
        val fadeDuration = 300L

        // Check if clicked panel is already visible; if so, do nothing.
        if (binding.colors.isVisible && binding.gradients.isVisible) return

        // Check which panel is visible and apply transition
        val showGradients = binding.gradients.isVisible

        // If gradient is visible, hide it and show solid; otherwise, do the opposite
        if (showGradients) {
            // Fade out gradient and hide it
            binding.gradients.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                    binding.gradients.visibility = View.GONE
                    // Now fade in solid after gradient is hidden
                    binding.colors.alpha = 0f
                    binding.colors.visibility = View.VISIBLE
                    binding.colors.animate().alpha(1f).setDuration(fadeDuration).start()
                }.start()

            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            // Fade out solid and hide it
            binding.colors.animate().alpha(0f).setDuration(fadeDuration).withEndAction {
                    binding.colors.visibility = View.GONE
                    // Now fade in gradient after solid is hidden
                    binding.gradients.alpha = 0f
                    binding.gradients.visibility = View.VISIBLE
                    binding.gradients.animate().alpha(1f).setDuration(fadeDuration).start()
                }.start()
            binding.gradient.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.solid.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
        }
    }

    private fun animatePreview() {
        val targetAlpha = 1f
        val animDuration = 120L
        binding.brushPreview.animate().setDuration(animDuration)
            .setInterpolator(android.view.animation.LinearInterpolator())
            .withStartAction {
                updateBrushPreview()
            }.alpha(targetAlpha).start()
    }

    private fun initObservers() {
        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                gradientsAdapter.updateList(gradients)
            }
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) { style ->
            updateBrushStyleUI(style)
            animatePreview()
        }

        viewModel.brushHardness.observe(viewLifecycleOwner) { hardness ->
            val progressValue = (hardness * 100).toInt()
            binding.hardnessBar.progress = progressValue
            binding.hardness.text = "$progressValue%"
            animatePreview()
        }

        viewModel.brushThickness.observe(viewLifecycleOwner) { thickness ->
            val progressValue = thickness.toInt()
            binding.thicknessBar.progress = progressValue
            binding.thickness.text = progressValue.toString()
            animatePreview()
        }

        viewModel.brushColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
            animatePreview()
        }

        viewModel.brushGradient.observe(viewLifecycleOwner) { gradient ->
            gradientsAdapter.selectedItem = gradient
            animatePreview()
        }

    }

    private fun updateBrushStyleUI(style: BrushStyle) {
        val appColor = ContextCompat.getColor(requireContext(), R.color.appColor)
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        val whiteTint = ContextCompat.getColor(requireContext(), R.color.white)
        val grayTint = ContextCompat.getColor(requireContext(), R.color.gray)

        val styleCards = listOf(
            binding.pen to BrushStyle.PEN,
            binding.marker to BrushStyle.MARKER,
            binding.brush to BrushStyle.BRUSH,
            binding.highlighter to BrushStyle.HIGHLIGHTER,
            binding.pencil to BrushStyle.PENCIL
        )

        styleCards.forEach { (card, brushType) ->
            // Apply click logic
            card.addPressEffect {
                val currentStyle = viewModel.currentBrushStyle.value ?: BrushStyle.PEN
                if (currentStyle != brushType) {
                    viewModel.setBrushStyle(brushType)
                }

                // Update UI selection after click
                styleCards.forEach { (otherCard, otherType) ->
                    val isSelected = otherType == viewModel.currentBrushStyle.value
                    otherCard.backgroundTintList = ColorStateList.valueOf(
                        if (isSelected) appColor else contrastColor
                    )
                    otherCard.imageTintList = ColorStateList.valueOf(
                        if (isSelected) whiteTint else grayTint
                    )
                }
            }

            // Also handle visual update when LiveData triggers externally
            val isSelected = brushType == style
            card.backgroundTintList = ColorStateList.valueOf(
                if (isSelected) appColor else contrastColor
            )
            card.imageTintList = ColorStateList.valueOf(
                if (isSelected) whiteTint else grayTint
            )
        }
    }

    private fun setupRecyclerView() {
        colorsAdapter = ColorsAdapter(Constants.colorList, onColorSelected = { color ->
            val selectedColor = color.colorCode.toColorInt()
            viewModel.setBrushColor(selectedColor)
            viewModel.setBrushGradient(null)
        }, onNoneSelected = {
            viewModel.setBrushColor(android.R.color.transparent)
            viewModel.setBrushGradient(null)
        }, onColorPickerClicked = {
            viewModel.startPicking(PickerTarget.COLOR_PICKER_DRAW_STROKE)
            viewModel.setBrushGradient(null)
            childFragmentManager.beginTransaction().replace(R.id.brushPanel, ColorPickerFragment())
                .addToBackStack(null).commit()
        }, onEyeDropperClicked = {
            viewModel.setBrushGradient(null)
            viewModel.startPicking(PickerTarget.EYE_DROPPER_DRAW_STROKE)
        })

        gradientsAdapter =
            GradientsAdapter(gradientList = emptyList(), onGradientSelected = { _, item ->
                viewModel.setBrushGradient(item)
            }, onGradientEditSelected = { _, item ->
                viewModel.startPickingGradient(GradientPickerTarget.DRAW_STROKE)
                viewModel.setGradient(item)
                viewModel.setPagingLocked(true)
                childFragmentManager.beginTransaction()
                    .replace(R.id.brushPanel, GradientEditorFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("IS_EDIT", true)
                        }
                    }).addToBackStack(null).commit()
            }, onNoneSelected = {
                viewModel.setBrushGradient(null)
            }, onGradientPickerClicked = {
                viewModel.startPickingGradient(GradientPickerTarget.DRAW_STROKE)
                viewModel.setPagingLocked(true)
                childFragmentManager.beginTransaction()
                    .replace(R.id.brushPanel, GradientEditorFragment().apply {
                        arguments = Bundle().apply {
                            putBoolean("IS_EDIT", false)
                        }
                    }).addToBackStack(null).commit()
            })

        binding.colors.apply {
            setHasFixedSize(true)
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            setHasFixedSize(true)
            adapter = gradientsAdapter
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): BrushPanelFragment {
            val fragment = BrushPanelFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}