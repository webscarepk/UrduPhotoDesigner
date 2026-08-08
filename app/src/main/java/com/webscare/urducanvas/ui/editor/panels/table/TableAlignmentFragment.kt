package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.HAlign
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.LayoutTableAlignmentOptionsBinding

class TableAlignmentFragment : Fragment() {

    private var _binding: LayoutTableAlignmentOptionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = LayoutTableAlignmentOptionsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCanvasAlignmentKit()
        setupHAlignCards()
        setupVAlignCards()
    }

    private fun setupCanvasAlignmentKit() {
        binding.btnCanvasLeft.addPressEffect {
            viewModel.alignHorizontal(HAlign.LEFT)
        }
        binding.btnCanvasCenterHoriz.addPressEffect {
            viewModel.alignHorizontal(HAlign.CENTER)
        }
        binding.btnCanvasRight.addPressEffect {
            viewModel.alignHorizontal(HAlign.RIGHT)
        }
        binding.btnCanvasTop.addPressEffect {
            viewModel.alignVertical(VAlign.TOP)
        }
        binding.btnCanvasCenterVert.addPressEffect {
            viewModel.alignVertical(VAlign.MIDDLE)
        }
        binding.btnCanvasBottom.addPressEffect {
            viewModel.alignVertical(VAlign.BOTTOM)
        }
    }

    private fun setupHAlignCards() {
        val hAlignCards = listOf(
            binding.btnAlignLeft to TextAlignment.LEFT,
            binding.btnAlignCenter to TextAlignment.CENTER,
            binding.btnAlignRight to TextAlignment.RIGHT,
            binding.btnAlignJustify to TextAlignment.JUSTIFY
        )

        hAlignCards.forEach { (card, align) ->
            card.addPressEffect {
                viewModel.setTableHAlign(align)
                updateCardSelection(card, hAlignCards.map { it.first })
            }
        }
    }

    private fun setupVAlignCards() {
        val vAlignCards = listOf(
            binding.btnVAlignTop to VAlign.TOP,
            binding.btnVAlignCenter to VAlign.MIDDLE,
            binding.btnVAlignBottom to VAlign.BOTTOM
        )

        vAlignCards.forEach { (card, align) ->
            card.addPressEffect {
                viewModel.setTableVAlign(align)
                updateCardSelection(card, vAlignCards.map { it.first })
            }
        }
    }

    private fun updateCardSelection(selectedCard: MaterialCardView, allCards: List<MaterialCardView>) {
        val context = context ?: return
        val appColor = ContextCompat.getColor(context, R.color.appColor)
        val contrast = ContextCompat.getColor(context, R.color.contrast)

        allCards.forEach { card ->
            if (card == selectedCard) {
                card.strokeColor = appColor
                card.strokeWidth = 4
                card.setCardBackgroundColor(contrast)
            } else {
                card.strokeWidth = 0
                card.setCardBackgroundColor(contrast)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableAlignmentFragment()
    }
}
