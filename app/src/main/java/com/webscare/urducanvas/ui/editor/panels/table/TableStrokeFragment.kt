package com.webscare.urducanvas.ui.editor.panels.table

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.TableBorderMode
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableStrokeOptionsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableStrokeFragment : Fragment() {

    private var _binding: LayoutTableStrokeOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableStrokeOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBorderModeTiles()
        setupSeekBar()
        setupAdapters()
        setupToggle()
        observeViewModel()
        syncInitialState()
    }

    private fun syncInitialState() {
        val data = viewModel.getSelectedTableData() ?: return
        val tiles = listOf(
            binding.tileAll to TableBorderMode.ALL,
            binding.tileOuter to TableBorderMode.OUTER,
            binding.tileInner to TableBorderMode.INNER,
            binding.tileHoriz to TableBorderMode.HORIZONTAL,
            binding.tileVert to TableBorderMode.VERTICAL,
            binding.tileNone to TableBorderMode.NONE
        )
        val selectedTile = tiles.firstOrNull { it.second == data.borderMode }?.first ?: binding.tileAll
        updateTileSelection(selectedTile, tiles.map { it.first })

        val width = data.borderWidth.toInt()
        binding.seekBorderWidth.progress = width.coerceIn(0, 10)
        binding.tvBorderWidth.text = "${width}dp"
        colorsAdapter.selectedColor = data.borderColor
    }

    private fun setupBorderModeTiles() {
        val tiles = listOf(
            binding.tileAll to TableBorderMode.ALL,
            binding.tileOuter to TableBorderMode.OUTER,
            binding.tileInner to TableBorderMode.INNER,
            binding.tileHoriz to TableBorderMode.HORIZONTAL,
            binding.tileVert to TableBorderMode.VERTICAL,
            binding.tileNone to TableBorderMode.NONE
        )

        tiles.forEach { (tile, mode) ->
            tile.addPressEffect {
                viewModel.updateSelectedTableData { data ->
                    data.borderMode = mode
                }
                updateTileSelection(tile, tiles.map { it.first })
            }
        }
    }

    private fun updateTileSelection(selectedTile: TextView, allTiles: List<TextView>) {
        val context = context ?: return
        val contrastColor = ContextCompat.getColor(context, R.color.contrast)

        allTiles.forEach { tile ->
            if (tile == selectedTile) {
                tile.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                tile.setTextColor(Color.BLACK)
                tile.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.bold)
            } else {
                tile.backgroundTintList = ColorStateList.valueOf(contrastColor)
                tile.setTextColor(Color.BLACK)
                tile.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.medium)
            }
        }
    }

    private fun setupSeekBar() {
        binding.seekBorderWidth.max = 10
        binding.seekBorderWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvBorderWidth.text = "${progress}dp"
                    viewModel.updateSelectedTableData { data ->
                        data.borderWidth = progress.toFloat()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupAdapters() {
        colorsAdapter = ColorsAdapter(
            Constants.colorList,
            onColorSelected = { color ->
                val colorInt = color.colorCode.toColorInt()
                colorsAdapter.selectedColor = colorInt
                viewModel.updateSelectedTableData { data ->
                    data.borderColor = colorInt
                    data.borderGradient = null
                }
            },
            onNoneSelected = {
                viewModel.updateSelectedTableData { data ->
                    data.borderColor = Color.TRANSPARENT
                }
            },
            onColorPickerClicked = {
                viewModel.startPicking(com.webscare.urducanvas.common.canvas.enums.PickerTarget.COLOR_PICKER_TABLE_STROKE)
            },
            onEyeDropperClicked = {
                viewModel.startPicking(com.webscare.urducanvas.common.canvas.enums.PickerTarget.EYE_DROPPER_TABLE_STROKE)
            }
        )
        binding.colors.adapter = colorsAdapter

        gradientsAdapter = GradientsAdapter(
            gradientList = emptyList(),
            onGradientSelected = { _, item ->
                viewModel.updateSelectedTableData { data ->
                    data.borderGradient = item
                }
            },
            onGradientEditSelected = { _, item -> },
            onNoneSelected = {
                viewModel.updateSelectedTableData { data ->
                    data.borderGradient = null
                }
            },
            onGradientPickerClicked = {}
        )
        binding.gradients.adapter = gradientsAdapter
    }

    private fun setupToggle() {
        binding.btnSolid.addPressEffect {
            binding.colors.isVisible = true
            binding.gradients.isVisible = false
            binding.btnSolid.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.btnSolid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
            binding.btnGradient.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.btnGradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
        }

        binding.btnGradient.addPressEffect {
            binding.colors.isVisible = false
            binding.gradients.isVisible = true
            binding.btnSolid.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.btnSolid.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
            binding.btnGradient.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.btnGradient.typeface = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.medium)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            mainViewModel.gradients.observe(viewLifecycleOwner) { gradients ->
                gradientsAdapter.updateList(gradients)
            }
        }
    }

    override fun onDestroyView() {
        _binding?.colors?.adapter = null
        _binding?.gradients?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableStrokeFragment()
    }
}
