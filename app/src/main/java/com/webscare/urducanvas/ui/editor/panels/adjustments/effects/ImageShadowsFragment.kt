package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

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
import com.webscare.urducanvas.databinding.FragmentImagesShadowBinding
import com.webscare.urducanvas.databinding.FragmentShadowsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.ColorPickerFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImageShadowsFragment : Fragment() {
    private var _binding: FragmentImagesShadowBinding? = null
    private val binding get() = _binding!!

    private lateinit var colorsAdapter: ColorsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesShadowBinding.inflate(layoutInflater, container, false)
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
                    val element = viewModel.selectedElements.value?.firstOrNull() ?: return@ColorsAdapter

                    viewModel.setImageShadow(
                        true,
                        color.colorCode.toColorInt(),
                        element.shadowDx,
                        element.shadowDy,
                        element.shadowRadius,
                        element.shadowOpacity,
                        pushToUndo = true
                    )
                },
                {
                    val element = viewModel.selectedElements.value?.firstOrNull() ?: return@ColorsAdapter

                    viewModel.setImageShadow(
                        false,
                        element.shadowColor,
                        element.shadowDx,
                        element.shadowDy,
                        element.shadowRadius,
                        element.shadowOpacity,
                        pushToUndo = true
                    )
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
        // X OFFSET
        binding.shadowX.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener

            viewModel.setImageShadow(
                true,
                element.shadowColor,
                progress.toFloat(),
                element.shadowDy,
                element.shadowRadius,
                element.shadowOpacity,
                pushToUndo = push
            )
        })

        // Y OFFSET
        binding.shadowY.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener

            viewModel.setImageShadow(
                true,
                element.shadowColor,
                element.shadowDx,
                progress.toFloat(),
                element.shadowRadius,
                element.shadowOpacity,
                pushToUndo = push
            )
        })

        // OPACITY
        binding.opacity.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener

            viewModel.setImageShadow(
                true,
                element.shadowColor,
                element.shadowDx,
                element.shadowDy,
                element.shadowRadius,
                progress,
                pushToUndo = push
            )
        })

        // RADIUS
        binding.radius.setOnSeekBarChangeListener(createSeekListener { progress, push ->
            val element = viewModel.selectedElements.value?.firstOrNull() ?: return@createSeekListener

            viewModel.setImageShadow(
                true,
                element.shadowColor,
                element.shadowDx,
                element.shadowDy,
                progress.toFloat(),
                element.shadowOpacity,
                pushToUndo = push
            )
        })
    }

    // --------------------------------------------------
    // GENERIC SEEK LISTENER
    // --------------------------------------------------

    private fun createSeekListener(
        onChange: (progress: Int, pushToUndo: Boolean) -> Unit
    ): SeekBar.OnSeekBarChangeListener {

        return object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!fromUser) return
                onChange(progress, false)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onChange(seekBar.progress, true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
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

    override fun onResume() {
        super.onResume()
        viewModel.syncShadowStateFromSelected()
    }

    companion object {

        fun newInstance(): ImageShadowsFragment {
            val fragment = ImageShadowsFragment()
            return fragment
        }
    }
}