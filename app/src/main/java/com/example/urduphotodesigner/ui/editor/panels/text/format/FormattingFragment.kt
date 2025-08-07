package com.example.urduphotodesigner.ui.editor.panels.text.format

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.ParagraphIndentation
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentFormattingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FormattingFragment : Fragment() {
    private var _binding: FragmentFormattingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
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
                caseCards.forEach { (otherCard, _) ->
                    otherCard.strokeWidth = if (otherCard == card) 4 else 0
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
                    decorationCards.forEach { (otherCard, _) ->
                        otherCard.strokeWidth = if (otherCard == card) 4 else 0
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
                        otherCard.strokeWidth = if (updatedDecorations.contains(otherDecorationType)) 4 else 0
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
                alignCards.forEach { (otherCard, _) ->
                    otherCard.strokeWidth = if (otherCard == card) 4 else 0
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
                val paraValue = viewModel.paragraphIndentation.value

                paraCards.forEach { (otherCard, otherIndent) ->
                    otherCard.strokeWidth = if (paraValue?.toInt() == 0 && otherIndent == ParagraphIndentation.NONE) 4 else 0
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
                binding.leftAlign to TextAlignment.LEFT,
                binding.centerAlignment to TextAlignment.CENTER,
                binding.rightAlign to TextAlignment.RIGHT,
                binding.justify to TextAlignment.JUSTIFY,
            )

            alignCards.forEach { (card, alignType) ->
                card.strokeWidth = if (alignType == alignment) 4 else 0
            }
        }

        viewModel.listStyle.observe(viewLifecycleOwner) { listStyle ->
            val listCards = listOf(
                binding.defaultList to ListStyle.NONE,
                binding.numberedList to ListStyle.NUMBERED,
                binding.bulletedList to ListStyle.BULLETED
            )

            listCards.forEach { (card, list) ->
                card.strokeWidth = if (list == listStyle) 4 else 0
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
                card.strokeWidth = if (letterCase == case) 4 else 0
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
                card.strokeWidth = if (currentDecorations.contains(decorationType)) 4 else 0
            }
        }

        viewModel.paragraphIndentation.observe(viewLifecycleOwner) { para ->
            val paraCards = listOf(
                binding.defaultIndent to ParagraphIndentation.NONE,
                binding.decreaseIndent to ParagraphIndentation.DECREASE_INDENT,
                binding.increaseIndent to ParagraphIndentation.INCREASE_INDENT
            )

            paraCards.forEach { (card, indent) ->
                card.strokeWidth = if (para?.toInt() == 0 && indent == ParagraphIndentation.NONE) 4 else 0
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