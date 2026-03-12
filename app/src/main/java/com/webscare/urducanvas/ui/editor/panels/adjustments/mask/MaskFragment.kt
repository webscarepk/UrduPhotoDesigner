package com.webscare.urducanvas.ui.editor.panels.adjustments.mask

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentMaskBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MaskFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentMaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private var elementId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            elementId = arguments?.getString("elementId")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMaskBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {
        updateAddMaskButtonText()
        binding.cover.addPressEffect { viewModel.setImageFitMode("cover") }
        binding.contain.addPressEffect { viewModel.setImageFitMode("contain") }
        binding.stretch.addPressEffect { viewModel.setImageFitMode("stretch") }

        binding.zoomBar.apply {
            
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val scale = 0.5f + (progress / 100f) * 2.5f
                    binding.zoom.text = String.format("%.2fx", scale)
                    if (fromUser) {
                        viewModel.setImageScale(scale)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.panXBar.apply {
            
            max = 200
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val offset = (progress - 100) * 1.5f
                    binding.panX.text = offset.toInt().toString()
                    if (fromUser) {
                        viewModel.setImagePanX(offset)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.panYBar.apply {
            
            max = 200
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val offset = (progress - 100) * 1.5f
                    binding.panY.text = offset.toInt().toString()
                    if (fromUser) {
                        viewModel.setImagePanY(offset)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.editShape.addPressEffect { goToShapePanel() }

    }

    private fun updateAddMaskButtonText() {
        val selectedElement = viewModel.canvasElements.value?.find { it.isSelected }
        if (selectedElement != null && (selectedElement.type == ElementType.IMAGE || selectedElement.type == ElementType.STICKER)) {
            binding.editShape.text = getString(R.string.mask_image_as_shape)
        } else {
            binding.editShape.text = getString(R.string.edit_shape)
        }
    }


    private fun updateFitModeButtonState(mode: String) {
        val contrastColor = ContextCompat.getColor(requireContext(), R.color.contrast)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)

        // Reset all buttons first
        binding.cover.backgroundTintList = ColorStateList.valueOf(contrastColor)
        binding.contain.backgroundTintList = ColorStateList.valueOf(contrastColor)
        binding.stretch.backgroundTintList = ColorStateList.valueOf(contrastColor)

        // Highlight the selected one
        when (mode.lowercase()) {
            "cover" -> binding.cover.backgroundTintList = ColorStateList.valueOf(whiteColor)
            "contain" -> binding.contain.backgroundTintList = ColorStateList.valueOf(whiteColor)
            "stretch" -> binding.stretch.backgroundTintList = ColorStateList.valueOf(whiteColor)
        }
    }

    private fun goToShapePanel() {
        if (binding.editShape.text != getString(R.string.edit_shape)) {
            viewModel.enterMaskMode()
        }
        val args = Bundle().apply { putInt("startPage", 1) }
        val nav = requireActivity().findNavController(R.id.panelNavHost)

        nav.popBackStack(R.id.adjustmentsParentFragment, true)

        nav.navigate(R.id.drawFragment, args, NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    private fun initObservers() {
        viewModel.imageScale.observe(viewLifecycleOwner) { scale ->
            val progress = ((scale - 0.5f) / 2.5f * 100f).toInt().coerceIn(0, 100)
            binding.zoomBar.progress = progress
            binding.zoom.text = String.format("%.2fx", scale)
        }

        viewModel.imagePanX.observe(viewLifecycleOwner) { panX ->
            val progress = ((panX / 1.5f) + 100f).toInt().coerceIn(0, 200)
            binding.panXBar.progress = progress
            binding.panX.text = String.format("%.1f", panX)
        }

        viewModel.imagePanY.observe(viewLifecycleOwner) { panY ->
            val progress = ((panY / 1.5f) + 100f).toInt().coerceIn(0, 200)
            binding.panYBar.progress = progress
            binding.panY.text = String.format("%.1f", panY)
        }

        viewModel.imageFitMode.observe(viewLifecycleOwner) { mode ->
            updateFitModeButtonState(mode)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(key: String): MaskFragment {
            val bundle = Bundle().apply { putString("elementId", key) }
            return MaskFragment().apply { arguments = bundle }
        }
    }
}
