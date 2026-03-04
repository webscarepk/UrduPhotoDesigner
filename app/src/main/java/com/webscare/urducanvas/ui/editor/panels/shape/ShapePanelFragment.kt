package com.webscare.urducanvas.ui.editor.panels.shape

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentShapePanelBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientEditorFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class ShapePanelFragment : Fragment() {
    private var _binding: FragmentShapePanelBinding? = null
    private val binding get() = _binding!!
    private var tabName: String = ""
    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter
    private lateinit var shapesAdapter: ShapeAdapter
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isStrokeEnabled = false
    private var isFillEnabled = true
    private var selectColorFor = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabName = it.getString("tabName")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapePanelBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    private fun setEvents() {
        binding.shapePane.isVisible = tabName == "Shape"
        binding.stylePane.isVisible = tabName == "Style"
        binding.colorsPane.isVisible = tabName == "Color"

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

        binding.strokeWidthBar.apply {
            min = 1
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val width = progress.toFloat()
                    binding.strokeWidth.text = progress.toString()
                    if (fromUser) {
                        viewModel.updateStrokeWidth(width)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.cornerRadiusBar.apply {
            min = 0
            max = 300
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    binding.cornerRadius.text = "$progress"  // Show the progress directly as the value
                    if (fromUser) {
                        val radius = progress.toFloat()  // Get the radius directly in the range 0-300
                        viewModel.updateCornerRadius(radius)  // Pass the radius to the viewModel
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        selectColorFor = (tabName == "Stroke")

        // 2. Visibility Logic: Decide which pane to show
        // We now check if the tabName belongs to a Style or Color category
        binding.shapePane.isVisible = (tabName == "Basic" || tabName == "Geometry")
        binding.stylePane.isVisible = (tabName == "Fill" || tabName == "Stroke") && !isColorTab()
        binding.colorsPane.isVisible = (tabName == "Fill" || tabName == "Stroke") && isColorTab()

        setupRecyclerView()
        initObserver()
    }

    // Helper to check if the grandparent is the "Color" tab
    private fun isColorTab(): Boolean {
        return parentFragment is ShapeFragment &&
                (parentFragment as ShapeFragment).arguments?.getString("tabName") == "Color"
    }

    private fun initObserver() {
        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                gradientsAdapter.updateList(gradients)
            }

            viewModel.currentShapeType.observe(viewLifecycleOwner) { type ->
                type?.let {
                    shapesAdapter.selectedShape = it
                }
            }

            // 🟢 Stroke width
            viewModel.shapeStrokeWidth.observe(viewLifecycleOwner) { width ->
                val safeWidth = width ?: 1f
                binding.strokeWidthBar.progress = safeWidth.toInt().coerceIn(1, 100)
                binding.strokeWidth.text = safeWidth.toInt().toString()
            }

            // 🟣 Corner radius
            viewModel.shapeCornerRadius.observe(viewLifecycleOwner) { radius ->
                Log.d("TAG", "initObserver: $radius")
                val safeRadius = radius ?: 0f
                val progress = (safeRadius).roundToInt().coerceIn(0, 300)
                binding.cornerRadiusBar.progress = progress
                binding.cornerRadius.text = "$progress"
            }

            // 🎨 Fill color
            viewModel.shapeFillColor.observe(viewLifecycleOwner) { color ->
                color?.let {
                    colorsAdapter.selectedColor = it
                }
            }

            // 🖌 Stroke color
            viewModel.shapeStrokeColor.observe(viewLifecycleOwner) { color ->
                color?.let {
                    colorsAdapter.selectedColor = it
                }
            }

            // 🌈 Fill gradient
            viewModel.shapeFillGradient.observe(viewLifecycleOwner) { gradient ->
                gradientsAdapter.selectedItem = gradient
            }

            // 🌈 Stroke gradient
            viewModel.shapeStrokeGradient.observe(viewLifecycleOwner) { gradient ->
                gradientsAdapter.selectedItem = gradient
            }
        }
    }

    private fun setupRecyclerView() {
        colorsAdapter =
            ColorsAdapter(
                Constants.colorList,
                onColorSelected = { color ->
                    val selectedColor = color.colorCode.toColorInt()
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(null)
                        viewModel.setStrokeColor(selectedColor)
                    } else {
                        viewModel.setFillGradient(null)
                        viewModel.setFillColor(selectedColor)
                    }
                },
                onNoneSelected = {
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(null)
                        viewModel.setStrokeColor(android.R.color.transparent)
                    } else {
                        viewModel.setFillGradient(null)
                        viewModel.setFillColor(android.R.color.transparent)
                    }
                },
                onColorPickerClicked = {
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(null)
                        viewModel.startPicking(PickerTarget.COLOR_PICKER_SHAPE_STROKE)
                    } else {
                        viewModel.setFillGradient(null)
                        viewModel.startPicking(PickerTarget.COLOR_PICKER_SHAPE_FILL)
                    }
                    childFragmentManager.beginTransaction().replace(
                        R.id.shapePanel,
                        ColorPickerFragment()
                    )
                        .addToBackStack(null).commit()
                },
                onEyeDropperClicked = {
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(null)
                        viewModel.startPicking(PickerTarget.EYE_DROPPER_SHAPE_STROKE)
                    } else {
                        viewModel.setFillGradient(null)
                        viewModel.startPicking(PickerTarget.EYE_DROPPER_SHAPE_FILL)
                    }
                })

        gradientsAdapter =
            GradientsAdapter(
                gradientList = emptyList(),
                onGradientSelected = { _, item ->
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(item)
                    } else {
                        viewModel.setFillGradient(item)
                    }
                },
                onGradientEditSelected = { _, item ->
                    if (selectColorFor) {
                        viewModel.startPickingGradient(GradientPickerTarget.SHAPE_STROKE)
                    } else {
                        viewModel.startPickingGradient(GradientPickerTarget.SHAPE_FILL)
                    }
                    viewModel.setGradient(item)
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction()
                        .replace(
                            R.id.shapePanel,
                            GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", true)
                                    }
                                }).addToBackStack(null).commit()
                },
                onNoneSelected = {
                    if (selectColorFor) {
                        viewModel.setStrokeGradient(null)
                    } else {
                        viewModel.setFillGradient(null)
                    }
                },
                onGradientPickerClicked = {
                    if (selectColorFor) {
                        viewModel.startPickingGradient(GradientPickerTarget.SHAPE_STROKE)
                    } else {
                        viewModel.startPickingGradient(GradientPickerTarget.SHAPE_FILL)
                    }
                    viewModel.setPagingLocked(true)
                    childFragmentManager.beginTransaction()
                        .replace(
                            R.id.shapePanel,
                            GradientEditorFragment()
                                .apply {
                                    arguments = Bundle().apply {
                                        putBoolean("IS_EDIT", false)
                                    }
                                }).addToBackStack(null).commit()
                })

        shapesAdapter = ShapeAdapter(requireContext(), ShapeType.entries) { shape ->
            val elements = viewModel.canvasElements.value
            val isMask = viewModel.isMaskingMode.value
            val isShapeSelected = elements?.any { it.isSelected && it.type == ElementType.SHAPE } == true
            if (isMask == true) {
                val selectedElement = elements?.find { it.isSelected && it.type == ElementType.IMAGE }
                selectedElement?.let {
                    viewModel.mergeImageToShape(it, shape, requireActivity())
                }
            } else {
                if (isShapeSelected) {
                    viewModel.updateShapeType(shape)
                } else {
                    viewModel.updateShapeType(shape)
                    viewModel.addShapeElement()
                }
            }
        }

        binding.shapes.apply {
            setHasFixedSize(true)
            adapter = shapesAdapter
        }

        binding.colors.apply {
            setHasFixedSize(true)
            adapter = colorsAdapter
        }

        binding.gradients.apply {
            setHasFixedSize(true)
            adapter = gradientsAdapter
        }
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

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): ShapePanelFragment {
            val fragment = ShapePanelFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}