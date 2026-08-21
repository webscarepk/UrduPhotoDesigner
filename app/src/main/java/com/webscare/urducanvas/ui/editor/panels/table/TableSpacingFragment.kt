package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.databinding.LayoutTableSpacingOptionsBinding

class TableSpacingFragment : Fragment() {

    private var _binding: LayoutTableSpacingOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableSpacingOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentStyle = viewModel.getTableScopeStyle()
        val initLetter = currentStyle?.letterSpacing ?: 0f
        val initLine = currentStyle?.lineSpacing ?: 1.0f

        val initLetterProgress = (((initLetter - (-0.5f)) / 2.5f) * 100f).toInt().coerceIn(0, 100)
        binding.seekLetterSpacing.max = 100
        binding.seekLetterSpacing.progress = initLetterProgress
        binding.tvLetterSpacingValue.text = "%.2f".format(initLetter)

        binding.seekLetterSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mappedLetterSpacing = -0.5f + (progress / 100.0f) * 2.5f
                    binding.tvLetterSpacingValue.text = "%.2f".format(mappedLetterSpacing)
                    viewModel.setTableLetterSpacing(mappedLetterSpacing)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val initLineProgress = (((initLine - 0.5f) / 2.5f) * 100f).toInt().coerceIn(0, 100)
        binding.seekLineSpacing.max = 100
        binding.seekLineSpacing.progress = initLineProgress
        binding.tvLineSpacingValue.text = "%.2f".format(initLine)

        binding.seekLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mappedLineSpacing = 0.5f + (progress / 100.0f) * 2.5f
                    binding.tvLineSpacingValue.text = "%.2f".format(mappedLineSpacing)
                    viewModel.setTableLineSpacing(mappedLineSpacing)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableSpacingFragment()
    }
}
