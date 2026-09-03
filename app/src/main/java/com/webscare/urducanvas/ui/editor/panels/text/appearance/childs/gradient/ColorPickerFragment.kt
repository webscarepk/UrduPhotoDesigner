package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient

import android.content.ContentValues.TAG
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.ColorPickerDialog
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentColorPickerBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class ColorPickerFragment : Fragment() {
    private var _binding: FragmentColorPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CanvasViewModel by activityViewModels()

    private var currentHue = 0f          // 0–360
    private var currentBrightness = 0.5f // 0–1 (0=black, 0.5=pure, 1=white)
    private var tempColor: Int = Color.RED
    private var hasInteracted = false

    private val rainbow = intArrayOf(
        Color.RED, Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve current active color from ViewModel
        val initialColor = getInitialColor()
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        currentHue = hsv[0]
        currentBrightness = when {
            hsv[2] < 1f -> hsv[2] * 0.5f
            else        -> 0.5f + (1f - hsv[1]) * 0.5f
        }
        tempColor = initialColor

        setupHueBar()
        setupBrightnessBar()
        updateColor()

        // ── tap colorCode to open dialog ───────────────────────────────────
        binding.colorCode.addPressEffect {
            ColorPickerDialog(requireContext()) { color ->
                applyColorFromInt(color)
            }.show()
        }

        binding.done.addPressEffect {
            viewModel.finishPicking(tempColor)
            viewModel.stopPicking()
            parentFragment?.childFragmentManager?.popBackStack()
        }
    }

    private fun getInitialColor(): Int {
        val target = viewModel.activePicker.value ?: return Color.RED
        val element = viewModel.selectedElements.value?.firstOrNull()

        return when (target) {
            PickerTarget.COLOR_PICKER_BACKGROUND, PickerTarget.EYE_DROPPER_BACKGROUND -> {
                element?.backgroundColor ?: Color.WHITE
            }
            PickerTarget.COLOR_PICKER_OVERLAY, PickerTarget.EYE_DROPPER_OVERLAY -> {
                val overlay = element?.overlayColor ?: Color.TRANSPARENT
                if (overlay == Color.TRANSPARENT) Color.parseColor("#FF746C") else overlay
            }
            PickerTarget.COLOR_PICKER_TEXT_FILL, PickerTarget.EYE_DROPPER_TEXT_FILL -> {
                viewModel.currentTextColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_TEXT_STROKE, PickerTarget.EYE_DROPPER_TEXT_STROKE -> {
                viewModel.borderColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_SHADOW, PickerTarget.EYE_DROPPER_SHADOW, PickerTarget.COLOR_PICKER_IMAGE_SHADOW -> {
                viewModel.shadowColor.value ?: Color.GRAY
            }
            PickerTarget.COLOR_PICKER_GLOW, PickerTarget.EYE_DROPPER_GLOW -> {
                viewModel.outerGlowColor.value ?: Color.CYAN
            }
            PickerTarget.COLOR_PICKER_LABEL, PickerTarget.EYE_DROPPER_LABEL -> {
                viewModel.labelColor.value ?: Color.YELLOW
            }
            PickerTarget.COLOR_PICKER_GRADIENT, PickerTarget.EYE_DROPPER_GRADIENT -> {
                viewModel.gradientStopColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_DRAW_STROKE, PickerTarget.EYE_DROPPER_DRAW_STROKE,
            PickerTarget.COLOR_PICKER_DRAW_FILL, PickerTarget.EYE_DROPPER_DRAW_FILL -> {
                viewModel.brushColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_SHAPE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_STROKE -> {
                viewModel.shapeStrokeColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_SHAPE_FILL, PickerTarget.EYE_DROPPER_SHAPE_FILL -> {
                viewModel.shapeFillColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_IMAGE_STROKE, PickerTarget.EYE_DROPPER_IMAGE_STROKE -> {
                viewModel.borderColor.value ?: Color.BLACK
            }
            PickerTarget.COLOR_PICKER_TABLE_FILL, PickerTarget.EYE_DROPPER_TABLE_FILL -> {
                viewModel.getTableScopeStyle()?.bgColor ?: Color.WHITE
            }
            PickerTarget.COLOR_PICKER_TABLE_STROKE, PickerTarget.EYE_DROPPER_TABLE_STROKE -> {
                viewModel.getSelectedTableData()?.borderColor ?: Color.parseColor("#005D28")
            }
            PickerTarget.COLOR_PICKER_TABLE_TEXT_COLOR, PickerTarget.EYE_DROPPER_TABLE_TEXT_COLOR -> {
                viewModel.getTableScopeStyle()?.textColor ?: Color.BLACK
            }
        }
    }

    // ── Hue Bar: full rainbow ──────────────────────────────────────────────────
    private fun setupHueBar() {
        binding.seekbarHue.apply {
            max = 360
            setGradient(rainbow)
            progress = currentHue / 360f

            onProgressChanged = { hueDeg ->
                hasInteracted = true
                currentHue = hueDeg.toFloat()
                rebuildBrightnessGradient()
                updateColor()
            }

            onColorPicked = { _ ->
                binding.colorCode.text = colorToHex(tempColor)
            }
        }
    }

    // ── Brightness Bar: black → pure hue → white ──────────────────────────────
    private fun setupBrightnessBar() {
        binding.seekbarAlpha.apply {
            max = 100
            progress = currentBrightness
            rebuildBrightnessGradient()

            onProgressChanged = { value ->
                hasInteracted = true
                currentBrightness = value / 100f
                updateColor()
            }

            onColorPicked = { _ ->
                binding.colorCode.text = colorToHex(tempColor)
            }
        }
    }

    // rebuilds brightness bar gradient: black → pure hue color → white
    private fun rebuildBrightnessGradient() {
        val pureHue = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
        binding.seekbarAlpha.setGradient(
            intArrayOf(Color.BLACK, pureHue, Color.WHITE),
            floatArrayOf(0f, 0.5f, 1f)
        )
    }

    // ── Compute final color from hue + brightness ──────────────────────────────
    private fun updateColor() {
        tempColor = when {
            currentBrightness <= 0.5f -> {
                // black → pure hue  (brightness 0→0.5 maps to value 0→1, saturation stays 1)
                val t = currentBrightness * 2f  // 0→1
                val pureHue = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
                blendColors(Color.BLACK, pureHue, t)
            }
            else -> {
                // pure hue → white  (brightness 0.5→1 maps saturation 1→0 at full value)
                val t = (currentBrightness - 0.5f) * 2f  // 0→1
                val pureHue = Color.HSVToColor(floatArrayOf(currentHue, 1f, 1f))
                blendColors(pureHue, Color.WHITE, t)
            }
        }

        binding.colorCode.text = colorToHex(tempColor)
        if (hasInteracted) {
            viewModel.finishPicking(tempColor)
        }
    }

    // ── Apply color from dialog or external source ─────────────────────────────
    private fun applyColorFromInt(color: Int) {
        // decompose to HSV to find hue
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        currentHue = hsv[0]   // 0–360

        // figure out brightness position from saturation + value
        // value=1, sat=1 → pure (0.5), value<1 → dark side, sat<1 at value=1 → light side
        currentBrightness = when {
            hsv[2] < 1f -> hsv[2] * 0.5f               // dark side: 0→0.5
            else        -> 0.5f + (1f - hsv[1]) * 0.5f // light side: 0.5→1
        }

        // snap both bar handles
        binding.seekbarHue.progress = currentHue / 360f
        binding.seekbarAlpha.progress = currentBrightness

        rebuildBrightnessGradient()

        tempColor = color
        binding.colorCode.text = colorToHex(color)
        viewModel.finishPicking(color)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun blendColors(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from)   + t * (Color.red(to)   - Color.red(from))).toInt()
        val g = (Color.green(from) + t * (Color.green(to) - Color.green(from))).toInt()
        val b = (Color.blue(from)  + t * (Color.blue(to)  - Color.blue(from))).toInt()
        return Color.rgb(r, g, b)
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}