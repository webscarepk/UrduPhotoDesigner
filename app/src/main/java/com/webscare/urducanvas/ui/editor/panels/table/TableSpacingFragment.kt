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

        binding.seekLetterSpacing.max = 100
        binding.seekLetterSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mappedLetterSpacing = -0.5f + (progress / 100.0f) * 2.0f
                    binding.tvLetterSpacingValue.text = "%.2f".format(mappedLetterSpacing)
                    viewModel.setTableLetterSpacing(mappedLetterSpacing)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekLineSpacing.max = 100
        binding.seekLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val mappedLineSpacing = -0.5f + (progress / 100.0f) * (3.0f + 0.5f)
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
