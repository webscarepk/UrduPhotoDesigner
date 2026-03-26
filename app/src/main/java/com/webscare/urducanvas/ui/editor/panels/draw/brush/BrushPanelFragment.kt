package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.BrushRenderUtils
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentBrushPanelBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    private fun updateBrushPreview() {
        val preview = binding.brushPreview
        val width = preview.width.takeIf { it > 0 } ?: return
        val height = preview.height.takeIf { it > 0 } ?: return

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 🔹 Create same type of StrokeData used in CanvasView
        val path = Path().apply {
            moveTo(width * 0.1f, height * 0.5f)
            cubicTo(
                width * 0.3f,
                height * 0.3f,
                width * 0.7f,
                height * 0.7f,
                width * 0.9f,
                height * 0.5f
            )
        }

        val stroke = _root_ide_package_.com.webscare.urducanvas.common.canvas.model.StrokeData(
            path = path,
            color = viewModel.brushColor.value ?: Color.BLACK,
            thickness = viewModel.brushThickness.value ?: 20f,
            hardness = viewModel.brushHardness.value ?: 1f,
            style = viewModel.currentBrushStyle.value ?: BrushStyle.PEN,
            gradient = viewModel.brushGradient.value
        )

        // 🪄 Use the same rendering pipeline as CanvasView
        BrushRenderUtils.drawStrokePreview(
            canvas = canvas,
            stroke = stroke,
            paintAlpha = 255,
            width = width,
            height = height,
            makePaint = BrushRenderUtils::makeStrokePaint,
            drawBrush = BrushRenderUtils::drawBrushStroke,
            drawPen = BrushRenderUtils::drawTaperedPenStroke
        )

        preview.setImageBitmap(bitmap)
    }

    private fun togglePanels() {
        val fadeDuration = 300L

        if (binding.colors.isVisible && binding.gradients.isVisible) return

        val showGradients = binding.gradients.isVisible

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
            .setInterpolator(android.view.animation.LinearInterpolator()).withStartAction {
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
                viewModel.enterDrawingMode(requireActivity())

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
        colorsAdapter =
            _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter(
                Constants.colorList,
                onColorSelected = { color ->
                    val selectedColor = color.colorCode.toColorInt()
                    viewModel.setBrushColor(selectedColor)
                    viewModel.setBrushGradient(null)
                },
                onNoneSelected = {
                    viewModel.setBrushColor(android.R.color.transparent)
                    viewModel.setBrushGradient(null)
                },
                onColorPickerClicked = {
                    viewModel.startPicking(PickerTarget.COLOR_PICKER_DRAW_STROKE)
                    viewModel.setBrushGradient(null)
                    childFragmentManager.beginTransaction().replace(
                        R.id.brushPanel, ColorPickerFragment()
                    ).addToBackStack(null).commit()
                },
                onEyeDropperClicked = {
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
                childFragmentManager.beginTransaction().replace(
                        R.id.brushPanel, GradientEditorFragment().apply {
                                arguments = Bundle().apply {
                                    putBoolean("IS_EDIT", true)
                                }
                            }).addToBackStack(null).commit()
            }, onNoneSelected = {
                viewModel.setBrushGradient(null)
            }, onGradientPickerClicked = {
                viewModel.startPickingGradient(GradientPickerTarget.DRAW_STROKE)
                viewModel.setPagingLocked(true)
                childFragmentManager.beginTransaction().replace(
                        R.id.brushPanel, GradientEditorFragment().apply {
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        viewModel.exitDrawingMode()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            viewModel.exitDrawingMode()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    override fun onStop() {
        super.onStop()
        viewModel.exitDrawingMode()
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