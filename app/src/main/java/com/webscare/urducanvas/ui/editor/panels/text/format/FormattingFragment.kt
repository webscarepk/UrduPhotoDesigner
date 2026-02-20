package com.webscare.urducanvas.ui.editor.panels.text.format

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFormattingBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlin.collections.minus

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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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

    private fun setEvents() {

        val caseCards = listOf(
            binding.defaultCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.NONE,
            binding.allCaps to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.ALL_CAPS,
            binding.lowerCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.LOWER_CASE,
            binding.titleCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.TITLE_CASE
        )

        caseCards.forEach { (card, caseType) ->
            card.addPressEffect {
                val currentCase = viewModel.letterCasing.value ?: _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.NONE
                // Set letter casing only if the card is different
                if (currentCase != caseType) {
                    viewModel.setLetterCasing(caseType)
                }
                // Update stroke for the selected card
                caseCards.forEach { (otherCard, _) ->
                    otherCard.strokeWidth = if (otherCard == card) 4 else 0
                }
            }
        }

        val decorationCards = listOf(
            binding.bold to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.BOLD,
            binding.italic to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.ITALIC,
            binding.underLine to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.UNDERLINE,
            binding.defaultStyle to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.NONE
        )

        decorationCards.forEach { (card, decorationType) ->
            card.addPressEffect {
                val currentDecorations = viewModel.textDecoration.value ?: emptySet()

                if (decorationType == _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.NONE) {
                    if (currentDecorations.isEmpty()) return@addPressEffect
                    viewModel.setTextDecoration(emptySet())
                    decorationCards.forEach { (otherCard, _) ->
                        otherCard.strokeWidth = if (otherCard == card) 4 else 0
                    }
                } else {
                    if (currentDecorations.contains(_root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.NONE)) {
                        viewModel.setTextDecoration(currentDecorations - _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.NONE)
                    }
                    val updatedDecorations = if (currentDecorations.contains(decorationType)) {
                        currentDecorations - decorationType
                    } else {
                        currentDecorations + decorationType
                    }
                    viewModel.setTextDecoration(updatedDecorations)
                    decorationCards.forEach { (otherCard, otherDecorationType) ->
                        otherCard.strokeWidth = if (updatedDecorations.contains(otherDecorationType)) 4 else 0
                    }
                }
            }
        }

        val alignCards = listOf(
            binding.leftAlign to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.LEFT,
            binding.centerAlignment to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.CENTER,
            binding.rightAlign to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.RIGHT,
            binding.justify to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.JUSTIFY
        )

        alignCards.forEach { (card, alignType) ->
            card.addPressEffect {
                val currentAlign = viewModel.textAlignment.value ?: _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.LEFT
                if (currentAlign != alignType) {
                    viewModel.setTextAlignment(alignType)
                }
                alignCards.forEach { (otherCard, _) ->
                    otherCard.strokeWidth = if (otherCard == card) 4 else 0
                }
            }
        }

        val paraCards = listOf(
            binding.defaultIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.NONE,
            binding.decreaseIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.DECREASE_INDENT,
            binding.increaseIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.INCREASE_INDENT
        )

        paraCards.forEach { (card, indent) ->
            card.addPressEffect {
                when (indent) {
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.NONE -> {
                        viewModel.setIndentNone()
                    }
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.INCREASE_INDENT -> {
                        viewModel.increaseIndent()
                    }
                    _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.DECREASE_INDENT -> {
                        viewModel.decreaseIndent()
                    }
                }
                val paraValue = viewModel.paragraphIndentation.value

                paraCards.forEach { (otherCard, otherIndent) ->
                    otherCard.strokeWidth = if (paraValue?.toInt() == 0 && otherIndent == _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.NONE) 4 else 0
                }
            }
        }

        val listCards = listOf(
            binding.defaultList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.NONE,
            binding.numberedList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.NUMBERED,
            binding.bulletedList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.BULLETED
        )

        listCards.forEach { (card, listType) ->
            card.addPressEffect {
                val currentList = viewModel.listStyle.value ?: _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.NONE
                // Set list style only if it's a different selection
                if (currentList != listType) {
                    viewModel.setListStyle(listType)
                }
                // Update stroke for selected list style
                listCards.forEach { (otherCard, _) ->
                    otherCard.strokeWidth = if (otherCard == card) 4 else 0
                }
            }
        }

        binding.lineSpace.apply {
            min = 0
            max = 100
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser){
                        val mappedLineSpacing = -0.5f + (progress / 100.0f) * (3.0f + 0.5f)
                        binding.lineSpacing.text = "%.2f".format(mappedLineSpacing)

                        viewModel.setLineSpacing(mappedLineSpacing)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.letterSpace.apply {
            min = 0
            max = 100
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser){
                        val mappedLetterSpacing = -0.5f + (progress / 100.0f) * 2.0f // Letter spacing range from -0.5 to 1.5
                        binding.letterSpacing.text = "%.2f".format(mappedLetterSpacing) // Display with 2 decimal places

                        viewModel.setLetterSpacing(mappedLetterSpacing)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
    }

    private fun initObservers(){
        viewModel.lineSpacing.observe(viewLifecycleOwner) { lineSpace ->
            val mappedLineProgress = (((lineSpace + 0.5f) / 3.5f) * 100).toInt().coerceIn(0, 100)
            binding.lineSpace.progress = mappedLineProgress
            binding.lineSpacing.text = "$mappedLineProgress"
        }

        viewModel.letterSpacing.observe(viewLifecycleOwner) { letterSpace ->
            val mappedLetterProgress = (((letterSpace + 0.5f) / 2.0f) * 100).toInt().coerceIn(0, 100)
            binding.letterSpace.progress = mappedLetterProgress
            binding.letterSpacing.text = "$mappedLetterProgress"
        }

        viewModel.currentTextAlignment.observe(viewLifecycleOwner) { alignment ->
            val alignCards = listOf(
                binding.leftAlign to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.LEFT,
                binding.centerAlignment to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.CENTER,
                binding.rightAlign to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.RIGHT,
                binding.justify to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextAlignment.JUSTIFY,
            )

            alignCards.forEach { (card, alignType) ->
                card.strokeWidth = if (alignType == alignment) 4 else 0
            }
        }

        viewModel.listStyle.observe(viewLifecycleOwner) { listStyle ->
            val listCards = listOf(
                binding.defaultList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.NONE,
                binding.numberedList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.NUMBERED,
                binding.bulletedList to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ListStyle.BULLETED
            )

            listCards.forEach { (card, list) ->
                card.strokeWidth = if (list == listStyle) 4 else 0
            }
        }

        viewModel.letterCasing.observe(viewLifecycleOwner) { case ->
            val caseCards = listOf(
                binding.defaultCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.NONE,
                binding.allCaps to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.ALL_CAPS,
                binding.lowerCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.LOWER_CASE,
                binding.titleCase to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.LetterCasing.TITLE_CASE
            )

            caseCards.forEach { (card, letterCase) ->
                card.strokeWidth = if (letterCase == case) 4 else 0
            }
        }


        viewModel.textDecoration.observe(viewLifecycleOwner) { currentDecorations ->
            val decorationCards = listOf(
                binding.bold to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.BOLD,
                binding.italic to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.ITALIC,
                binding.underLine to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.UNDERLINE,
                binding.defaultStyle to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.TextDecoration.NONE
            )

            decorationCards.forEach { (card, decorationType) ->
                card.strokeWidth = if (currentDecorations.contains(decorationType)) 4 else 0
            }
        }

        viewModel.paragraphIndentation.observe(viewLifecycleOwner) { para ->
            val paraCards = listOf(
                binding.defaultIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.NONE,
                binding.decreaseIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.DECREASE_INDENT,
                binding.increaseIndent to _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.INCREASE_INDENT
            )

            paraCards.forEach { (card, indent) ->
                card.strokeWidth = if (para?.toInt() == 0 && indent == _root_ide_package_.com.webscare.urducanvas.common.canvas.enums.ParagraphIndentation.NONE) 4 else 0
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

    override fun onDestroy() {
        super.onDestroy()
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