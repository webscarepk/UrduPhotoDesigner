package com.webscare.urducanvas.ui.editor.panels.text.format

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFormattingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormattingFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFormattingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private var currentTab: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTab = arguments?.getString("tab_name")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormattingBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupControlsVisibility()
        setEvents()
        initObservers()
    }

    private fun updateCardStroke(
        card: com.google.android.material.card.MaterialCardView,
        isSelected: Boolean
    ) {
        val context = card.context
        card.strokeColor = androidx.core.content.ContextCompat.getColor(context, com.webscare.urducanvas.R.color.appColor)
        card.strokeWidth = if (isSelected) 4 else 0
        card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, com.webscare.urducanvas.R.color.contrast))
    }

    private fun setEvents() {

        val caseCards = listOf(
            binding.defaultCase to LetterCasing.NONE,
            binding.allCaps to LetterCasing.ALL_CAPS,
            binding.lowerCase to LetterCasing.LOWER_CASE,
            binding.titleCase to LetterCasing.TITLE_CASE
        )

        caseCards.forEach { (card, caseType) ->
            card.addPressEffect {
                val currentCase = viewModel.letterCasing.value ?: LetterCasing.NONE
                // Set letter casing only if the card is different
                if (currentCase != caseType) {
                    viewModel.setLetterCasing(caseType)
                }
                // Update stroke for the selected card
                caseCards.forEach { (otherCard, otherCaseType) ->
                    updateCardStroke(otherCard, otherCaseType == caseType)
                }
            }
        }

        val decorationCards = listOf(
            binding.bold to TextDecoration.BOLD,
            binding.italic to TextDecoration.ITALIC,
            binding.underLine to TextDecoration.UNDERLINE,
            binding.defaultStyle to TextDecoration.NONE
        )

        decorationCards.forEach { (card, decorationType) ->
            card.addPressEffect {
                val currentDecorations = viewModel.textDecoration.value ?: emptySet()

                if (decorationType == TextDecoration.NONE) {
                    if (currentDecorations.isEmpty()) return@addPressEffect
                    viewModel.setTextDecoration(emptySet())
                    decorationCards.forEach { (otherCard, otherDecorationType) ->
                        updateCardStroke(otherCard, otherDecorationType == TextDecoration.NONE)
                    }
                } else {
                    if (currentDecorations.contains(TextDecoration.NONE)) {
                        viewModel.setTextDecoration(currentDecorations - TextDecoration.NONE)
                    }
                    val updatedDecorations = if (currentDecorations.contains(decorationType)) {
                        currentDecorations - decorationType
                    } else {
                        currentDecorations + decorationType
                    }
                    viewModel.setTextDecoration(updatedDecorations)
                    decorationCards.forEach { (otherCard, otherDecorationType) ->
                        val isSel = if (otherDecorationType == TextDecoration.NONE) {
                            updatedDecorations.isEmpty()
                        } else {
                            updatedDecorations.contains(otherDecorationType)
                        }
                        updateCardStroke(otherCard, isSel)
                    }
                }
            }
        }

        val alignCards = listOf(
            binding.leftAlign to TextAlignment.LEFT,
            binding.centerAlignment to TextAlignment.CENTER,
            binding.rightAlign to TextAlignment.RIGHT,
            binding.justify to TextAlignment.JUSTIFY
        )

        alignCards.forEach { (card, alignType) ->
            card.addPressEffect {
                val currentAlign = viewModel.textAlignment.value ?: TextAlignment.LEFT
                if (currentAlign != alignType) {
                    viewModel.setTextAlignment(alignType)
                }
                alignCards.forEach { (otherCard, otherAlignType) ->
                    updateCardStroke(otherCard, otherAlignType == alignType)
                }
            }
        }

        val paraCards = listOf(
            binding.defaultIndent to ParagraphIndentation.NONE,
            binding.decreaseIndent to ParagraphIndentation.DECREASE_INDENT,
            binding.increaseIndent to ParagraphIndentation.INCREASE_INDENT
        )

        paraCards.forEach { (card, indent) ->
            card.addPressEffect {
                when (indent) {
                    ParagraphIndentation.NONE -> {
                        viewModel.setIndentNone()
                    }

                    ParagraphIndentation.INCREASE_INDENT -> {
                        viewModel.increaseIndent()
                    }

                    ParagraphIndentation.DECREASE_INDENT -> {
                        viewModel.decreaseIndent()
                    }
                }
                val paraValue = viewModel.paragraphIndentation.value ?: 0f

                paraCards.forEach { (otherCard, otherIndent) ->
                    val isSel = when (otherIndent) {
                        ParagraphIndentation.NONE -> paraValue == 0f
                        ParagraphIndentation.INCREASE_INDENT -> paraValue > 0f
                        ParagraphIndentation.DECREASE_INDENT -> paraValue < 0f
                    }
                    updateCardStroke(otherCard, isSel)
                }
            }
        }

        val listCards = listOf(
            binding.defaultList to ListStyle.NONE,
            binding.numberedList to ListStyle.NUMBERED,
            binding.bulletedList to ListStyle.BULLETED
        )

        listCards.forEach { (card, listType) ->
            card.addPressEffect {
                val currentList = viewModel.listStyle.value ?: ListStyle.NONE
                // Set list style only if it's a different selection
                if (currentList != listType) {
                    viewModel.setListStyle(listType)
                }
                // Update stroke for selected list style
                listCards.forEach { (otherCard, otherListType) ->
                    updateCardStroke(otherCard, otherListType == listType)
                }
            }
        }

        binding.lineSpace.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val mappedLineSpacing = -0.5f + (progress / 100.0f) * (3.0f + 0.5f)
                        binding.lineSpacing.text = "%.2f".format(mappedLineSpacing)
                        // Live preview — no undo entry yet
                        viewModel.setLineSpacing(mappedLineSpacing)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    // Snapshot the before-state before any preview changes
                    viewModel.beginLineSpacingDrag()
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    // Finger lifted — commit one undoable action
                    viewModel.commitLineSpacing()
                }
            })
        }

        binding.letterSpace.apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val mappedLetterSpacing =
                            -0.5f + (progress / 100.0f) * 2.0f
                        binding.letterSpacing.text = "%.2f".format(mappedLetterSpacing)
                        // Live preview — no undo entry yet
                        viewModel.setLetterSpacing(mappedLetterSpacing)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {
                    // Snapshot the before-state before any preview changes
                    viewModel.beginLetterSpacingDrag()
                }

                override fun onStopTrackingTouch(sb: SeekBar) {
                    // Finger lifted — commit one undoable action
                    viewModel.commitLetterSpacing()
                }
            })
        }
    }

    private fun initObservers() {
        viewModel.lineSpacing.observe(viewLifecycleOwner) { lineSpace ->
            val mappedLineProgress = (((lineSpace + 0.5f) / 3.5f) * 100).toInt().coerceIn(0, 100)
            binding.lineSpace.progress = mappedLineProgress
            binding.lineSpacing.text = "$mappedLineProgress"
        }

        viewModel.letterSpacing.observe(viewLifecycleOwner) { letterSpace ->
            val mappedLetterProgress =
                (((letterSpace + 0.5f) / 2.0f) * 100).toInt().coerceIn(0, 100)
            binding.letterSpace.progress = mappedLetterProgress
            binding.letterSpacing.text = "$mappedLetterProgress"
        }

        viewModel.currentTextAlignment.observe(viewLifecycleOwner) { alignment ->
            val alignCards = listOf(
                binding.leftAlign to TextAlignment.LEFT,
                binding.centerAlignment to TextAlignment.CENTER,
                binding.rightAlign to TextAlignment.RIGHT,
                binding.justify to TextAlignment.JUSTIFY,
            )

            alignCards.forEach { (card, alignType) ->
                updateCardStroke(card, alignType == alignment)
            }
        }

        viewModel.listStyle.observe(viewLifecycleOwner) { listStyle ->
            val listCards = listOf(
                binding.defaultList to ListStyle.NONE,
                binding.numberedList to ListStyle.NUMBERED,
                binding.bulletedList to ListStyle.BULLETED
            )

            listCards.forEach { (card, list) ->
                updateCardStroke(card, list == listStyle)
            }
        }

        viewModel.letterCasing.observe(viewLifecycleOwner) { case ->
            val caseCards = listOf(
                binding.defaultCase to LetterCasing.NONE,
                binding.allCaps to LetterCasing.ALL_CAPS,
                binding.lowerCase to LetterCasing.LOWER_CASE,
                binding.titleCase to LetterCasing.TITLE_CASE
            )

            caseCards.forEach { (card, letterCase) ->
                updateCardStroke(card, letterCase == case)
            }
        }

        viewModel.textDecoration.observe(viewLifecycleOwner) { currentDecorations ->
            val decorationCards = listOf(
                binding.bold to TextDecoration.BOLD,
                binding.italic to TextDecoration.ITALIC,
                binding.underLine to TextDecoration.UNDERLINE,
                binding.defaultStyle to TextDecoration.NONE
            )

            decorationCards.forEach { (card, decorationType) ->
                val isSel = if (decorationType == TextDecoration.NONE) {
                    currentDecorations.isEmpty()
                } else {
                    currentDecorations.contains(decorationType)
                }
                updateCardStroke(card, isSel)
            }
        }

        viewModel.paragraphIndentation.observe(viewLifecycleOwner) { para ->
            val paraCards = listOf(
                binding.defaultIndent to ParagraphIndentation.NONE,
                binding.decreaseIndent to ParagraphIndentation.DECREASE_INDENT,
                binding.increaseIndent to ParagraphIndentation.INCREASE_INDENT
            )

            val indentVal = para ?: 0f
            paraCards.forEach { (card, indent) ->
                val isSel = when (indent) {
                    ParagraphIndentation.NONE -> indentVal == 0f
                    ParagraphIndentation.INCREASE_INDENT -> indentVal > 0f
                    ParagraphIndentation.DECREASE_INDENT -> indentVal < 0f
                }
                updateCardStroke(card, isSel)
            }
        }
    }

    private fun setupControlsVisibility() {
        // only show the relevant controls panel
        when (currentTab?.lowercase()) {
            "spacing" -> {
                // preserve existing width
                binding.lineSpacingCard.visibility = View.VISIBLE
                binding.letterSpacingCard.visibility = View.VISIBLE
            }

            "casing" -> {
                binding.casingCard.visibility = View.VISIBLE
            }

            "decoration" -> {
                binding.decorationCard.visibility = View.VISIBLE
            }

            else -> {
                binding.alignmentKit.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TAB_NAME = "tab_name"

        fun newInstance(tabName: String): FormattingFragment {
            val fragment = FormattingFragment()
            val args = Bundle()
            args.putString(ARG_TAB_NAME, tabName)
            fragment.arguments = args
            return fragment
        }
    }
}