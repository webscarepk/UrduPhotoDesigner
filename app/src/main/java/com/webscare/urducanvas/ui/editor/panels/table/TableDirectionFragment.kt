package com.webscare.urducanvas.ui.editor.panels.table

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableDirectionOptionsBinding

class TableDirectionFragment : Fragment() {

    private var _binding: LayoutTableDirectionOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableDirectionOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRTL.addPressEffect {
            viewModel.setTableRTL(true)
            updatePillSelection(binding.btnRTL, binding.btnLTR)
        }

        binding.btnLTR.addPressEffect {
            viewModel.setTableRTL(false)
            updatePillSelection(binding.btnLTR, binding.btnRTL)
        }
    }

    private fun updatePillSelection(selected: TextView, unselected: TextView) {
        val context = context ?: return
        val appColor = ContextCompat.getColor(context, R.color.appColor)
        val contrastColor = ContextCompat.getColor(context, R.color.contrast)

        selected.backgroundTintList = ColorStateList.valueOf(appColor)
        selected.setTextColor(Color.WHITE)

        unselected.backgroundTintList = ColorStateList.valueOf(contrastColor)
        unselected.setTextColor(Color.BLACK)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableDirectionFragment()
    }
}
