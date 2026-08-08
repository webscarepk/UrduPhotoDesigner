package com.webscare.urducanvas.ui.editor.panels.table

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableHeadingOptionsBinding
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.ColorsAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient.GradientsAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableHeadingFragment : Fragment() {

    private var _binding: LayoutTableHeadingOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var colorsAdapter: ColorsAdapter
    private lateinit var gradientsAdapter: GradientsAdapter

    private var isBold = false
    private var isItalic = false
    private var isUnderline = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableHeadingOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFontSizeSeekbar()
        setupFormattingCards()
        setupColorAdapters()
        setupToggle()
        observeViewModel()
    }

    private fun setupFontSizeSeekbar() {
        binding.seekFontSize.max = 72
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val validSize = progress.coerceAtLeast(8)
                    binding.tvFontSizeValue.text = "${validSize}sp"
                    viewModel.setTableTextSize(validSize.toFloat())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupFormattingCards() {
        binding.cardBold.addPressEffect {
            isBold = !isBold
            viewModel.setTableBold(isBold)
            updateCardStroke(binding.cardBold, isBold)
        }

        binding.cardItalic.addPressEffect {
            isItalic = !isItalic
            viewModel.setTableItalic(isItalic)
            updateCardStroke(binding.cardItalic, isItalic)
        }

        binding.cardUnderline.addPressEffect {
            isUnderline = !isUnderline
            viewModel.setTableUnderline(isUnderline)
            updateCardStroke(binding.cardUnderline, isUnderline)
        }
    }

    private fun updateCardStroke(card: MaterialCardView, selected: Boolean) {
        val context = context ?: return
        val appColor = ContextCompat.getColor(context, R.color.appColor)
        card.strokeColor = appColor
        card.strokeWidth = if (selected) 4 else 0
    }

    private fun setupColorAdapters() {
        colorsAdapter = ColorsAdapter(
            Constants.colorList,
            onColorSelected = { color ->
                val colorInt = color.colorCode.toColorInt()
                viewModel.setTableTextColor(colorInt)
            },
            onNoneSelected = {
                viewModel.setTableTextColor(Color.TRANSPARENT)
            },
            onColorPickerClicked = {
                viewModel.startPicking(com.webscare.urducanvas.common.canvas.enums.PickerTarget.COLOR_PICKER_TABLE_TEXT_COLOR)
            },
            onEyeDropperClicked = {
                viewModel.startPicking(com.webscare.urducanvas.common.canvas.enums.PickerTarget.EYE_DROPPER_TABLE_TEXT_COLOR)
            }
        )
        binding.colors.adapter = colorsAdapter

        gradientsAdapter = GradientsAdapter(
            gradientList = emptyList(),
            onGradientSelected = { _, item ->
                viewModel.updateSelectedTableData { data ->
                    val scope = viewModel.currentTableScope.value
                    val r = viewModel.selectedTableRow.value
                    val c = viewModel.selectedTableCol.value
                    when (scope) {
                        com.webscare.urducanvas.common.canvas.enums.TableScope.WHOLE_TABLE -> data.base.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.HEADER_ROW -> data.headerStyle.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.FOOTER_ROW -> data.footerStyle.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.HEADER_COL -> data.headerColStyle.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.ROW -> data.rowStyles.getOrPut(r) { com.webscare.urducanvas.common.canvas.model.TableTextStyle() }.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.COLUMN -> data.colStyles.getOrPut(c) { com.webscare.urducanvas.common.canvas.model.TableTextStyle() }.textGradient = item
                        com.webscare.urducanvas.common.canvas.enums.TableScope.CELL -> {
                            if (r in 0 until data.rows && c in 0 until data.cols) {
                                val cell = data.cells[r][c]
                                val cellOverride = cell.override ?: com.webscare.urducanvas.common.canvas.model.TableTextStyle().also { cell.override = it }
                                cellOverride.textGradient = item
                            }
                        }
                    }
                }
            },
            onGradientEditSelected = { _, _ -> },
            onNoneSelected = {
                viewModel.updateSelectedTableData { data ->
                    data.base.textGradient = null
                }
            },
            onGradientPickerClicked = {}
        )
        binding.gradients.adapter = gradientsAdapter
    }

    private fun setupToggle() {
        binding.btnSolidText.addPressEffect {
            binding.colors.isVisible = true
            binding.gradients.isVisible = false
            binding.btnSolidText.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
            binding.btnGradientText.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
        }

        binding.btnGradientText.addPressEffect {
            binding.colors.isVisible = false
            binding.gradients.isVisible = true
            binding.btnSolidText.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrast))
            binding.btnGradientText.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white))
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
        fun newInstance() = TableHeadingFragment()
    }
}
