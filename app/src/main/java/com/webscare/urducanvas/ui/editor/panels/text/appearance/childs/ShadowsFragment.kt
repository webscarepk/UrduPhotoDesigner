package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.databinding.FragmentShadowsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShadowsFragment : Fragment() {
    private var _binding: FragmentShadowsBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShadowsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initSeekBars()
        setupRecyclerView()
        initObservers()
    }

    private fun setupRecyclerView() {
        colorsAdapter =
            ColorsAdapter(
                Constants.shadowColorList,
                { color ->
                    val selectedColor = color.colorCode.toColorInt()
                    val dx = viewModel.shadowDx.value ?: 0f
                    val dy = viewModel.shadowDy.value ?: 0f
                    viewModel.setTextShadow(true, selectedColor, dx, dy)
                },
                {
                    val dx = viewModel.shadowDx.value ?: 0f
                    val dy = viewModel.shadowDy.value ?: 0f
                    viewModel.setTextShadow(false, android.R.color.transparent, dx, dy)
                },
                {
                    viewModel.startPicking(PickerTarget.COLOR_PICKER_SHADOW)
                    childFragmentManager
                        .beginTransaction()
                        .replace(
                            R.id.shadowsFragment,
                            ColorPickerFragment()
                        )
                        .addToBackStack(null)
                        .commit()
                },
                {
                    viewModel.startPicking(PickerTarget.EYE_DROPPER_SHADOW)
                })

        binding.colors.apply {
            adapter = colorsAdapter
        }
    }

    private fun initSeekBars() {
        // Shadow DX SeekBar
        binding.shadowX.apply {
            min = 0
            max = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.shadowXSize.text = "$progress"
                        val color = viewModel.shadowColor.value ?: Color.BLACK
                        val dy = viewModel.shadowDy.value ?: 0f
                        viewModel.setTextShadow(true, color, progress.toFloat(), dy)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        // Shadow DY SeekBar
        binding.shadowY.apply {
            min = 0
            max = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.shadowYSize.text = "$progress"
                        val color = viewModel.shadowColor.value ?: Color.BLACK
                        val dx = viewModel.shadowDx.value ?: 0f
                        viewModel.setTextShadow(true, color, dx, progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.opacity.apply {
            min = 1
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.opacitySize.text = "$progress"
                        val color = viewModel.shadowColor.value ?: Color.BLACK
                        val dx = viewModel.shadowDx.value ?: 0f
                        val dy = viewModel.shadowDy.value ?: 0f
                        viewModel.setShadowOpacity(progress)
                        viewModel.setTextShadow(true, color, dx, dy)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.radius.apply {
            min = 1
            max = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.radiusSize.text = "$progress"
                        val color = viewModel.shadowColor.value ?: Color.BLACK
                        val dx = viewModel.shadowDx.value ?: 0f
                        val dy = viewModel.shadowDy.value ?: 0f
                        viewModel.setShadowRadius(progress.toFloat())
                        viewModel.setTextShadow(true, color, dx, dy)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun initObservers() {
        viewModel.shadowColor.observe(viewLifecycleOwner) { color ->
            colorsAdapter.selectedColor = color ?: Color.BLACK
        }

        viewModel.shadowDx.observe(viewLifecycleOwner) { dx ->
            binding.shadowXSize.text = "${dx?.toInt() ?: 0}"
            binding.shadowX.progress = dx?.toInt() ?: 0
        }

        viewModel.shadowDy.observe(viewLifecycleOwner) { dy ->
            binding.shadowYSize.text = "${dy?.toInt() ?: 0}"
            binding.shadowY.progress = dy?.toInt() ?: 0
        }

        viewModel.shadowOpacity.observe(viewLifecycleOwner) { opacity ->
            binding.opacitySize.text = "${opacity?.toInt() ?: 0}"
            binding.opacity.progress = opacity?.toInt() ?: 0
        }

        viewModel.shadowRadius.observe(viewLifecycleOwner) { radius ->
            binding.radiusSize.text = "${radius?.toInt() ?: 0}"
            binding.radius.progress = radius?.toInt() ?: 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPicking()
    }

    companion object {

        fun newInstance(): ShadowsFragment {
            val fragment = ShadowsFragment()
            return fragment
        }
    }
}