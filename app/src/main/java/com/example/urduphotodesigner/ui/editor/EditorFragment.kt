package com.example.urduphotodesigner.ui.editor

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasManager
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.HAlign
import com.example.urduphotodesigner.common.canvas.enums.MultiAlignMode
import com.example.urduphotodesigner.common.canvas.enums.PickerTarget
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.enums.VAlign
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.Converter.cmToPx
import com.example.urduphotodesigner.common.utils.Converter.inchesToPx
import com.example.urduphotodesigner.common.utils.displayName
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.databinding.BottomSheetExportSettingsBinding
import com.example.urduphotodesigner.databinding.FragmentEditorBinding
import com.example.urduphotodesigner.ui.editor.export.ExportFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class EditorFragment : Fragment() {
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var canvasManager: CanvasManager
    private var _navController: NavController? = null
    private val navController get() = _navController!!

    private lateinit var canvasSize: CanvasSize
    private var currentUnit = UnitType.PIXELS

    private val viewModel: CanvasViewModel by activityViewModels()
    private var currentPanelItemId: Int? = null

    private lateinit var sizedCanvasView: CanvasView
    private var currentMode: MultiAlignMode = MultiAlignMode.CANVAS

    private val blendingOptions = listOf(
        BlendType.SRC,
        BlendType.DST,
        BlendType.SRC_OVER,
        BlendType.DST_OVER,
        BlendType.SRC_IN,
        BlendType.DST_IN,
        BlendType.SRC_OUT,
        BlendType.DST_OUT,
        BlendType.SRC_ATOP,
        BlendType.DST_ATOP,
        BlendType.XOR,
        BlendType.DARKEN,
        BlendType.LIGHTEN,
        BlendType.ADD,
        BlendType.MULTIPLY,
        BlendType.SCREEN
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            if (Build.MANUFACTURER.equals("realme", ignoreCase = true)) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
            }
            insets
        }

        canvasSize = arguments?.getSerializable("canvas_size") as CanvasSize
        currentUnit = (arguments?.getSerializable("unit_type") as? UnitType)!!
        viewModel.setCanvasSize(canvasSize)

        setEvents()
        observeViewModel()

        if (Constants.TEMPLATE.isNotEmpty()) {
            viewModel.loadTemplate(Constants.TEMPLATE, requireContext())
            Toast.makeText(requireContext(), "Template loaded!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTextEditDialog(element: CanvasElement) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_text)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        val done = dialog.findViewById<ImageView>(R.id.done)
        editText.setText(element.text)
        editText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        done.setOnClickListener {
            val newText = editText.text.toString()
            if (newText.isNotBlank()) {
                element.text = newText
                viewModel.updateText(element)
            }
            dialog.dismiss()
        }

        // Set dialog window attributes for no dim background
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent) // Make background transparent
            setDimAmount(0f) // No dim
            setGravity(Gravity.BOTTOM)
            // You might want to adjust width/height if the layout doesn't fill as expected
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        // Show the dialog
        dialog.show()
    }

    private fun observeViewModel() {
        viewModel.canvasSize.observe(viewLifecycleOwner) { size ->
            canvasSize = size
            binding.canvasContainer.invalidate()
        }

        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            canvasManager.syncElements(elements)
            binding.canvasContainer.invalidate()
        }

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            canvasManager.setCanvasBackgroundColor(color)
        }

        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            binding.undo.isEnabled = canUndo
        }

        viewModel.canRedo.observe(viewLifecycleOwner) { canRedo ->
            binding.redo.isEnabled = canRedo
        }

        viewModel.backgroundImage.observe(viewLifecycleOwner) { bitmap ->
            bitmap?.let { canvasManager.setCanvasBackgroundImage(it) }
        }

        viewModel.backgroundGradient.observe(viewLifecycleOwner) { gradient ->
            gradient?.let {
                canvasManager.setCanvasBackgroundGradient(it)
            }
        }

        viewModel.currentFont.observe(viewLifecycleOwner) { font ->
            if (font != null && viewModel.isExplicitChange()) {
                font.let { canvasManager.setFont(it) }
            }
        }

        viewModel.currentImageFilter.observe(viewLifecycleOwner) { filter ->
            if (filter != null && viewModel.isExplicitChange()) {
                canvasManager.applyImageFilter(filter)
            }
        }

        viewModel.opacity.observe(viewLifecycleOwner) { opacity ->
            binding.seekBar.progress = opacity
            binding.opacityValue.text = "${opacity?.toInt() ?: 255}"
        }

        viewModel.currentTextSize.observe(viewLifecycleOwner) { size ->
            binding.fontSize.text = "${size?.toInt() ?: 40}"
            binding.seekBarFontSize.progress = size?.toInt() ?: 40
        }

        viewModel.blendingType.observe(viewLifecycleOwner) { type ->
            binding.blendSpinner.text = type.name
        }

        viewModel.activePicker.observe(viewLifecycleOwner) { slot ->
            when (slot) {
                PickerTarget.EYE_DROPPER_LABEL,
                PickerTarget.EYE_DROPPER_SHADOW,
                PickerTarget.EYE_DROPPER_BACKGROUND,
                PickerTarget.EYE_DROPPER_TEXT_FILL,
                PickerTarget.EYE_DROPPER_TEXT_STROKE,
                PickerTarget.EYE_DROPPER_GRADIENT -> {
                    sizedCanvasView.enableColorPicker()
                }

                else -> {
                    sizedCanvasView.disableColorPicker()
                }
            }
        }

        viewModel.selectedElements.distinctUntilChanged()
            .observe(viewLifecycleOwner) { selectedList ->
                updateToolbarVisibility(selectedList)
            }
    }

    private fun updateToolbarVisibility(selected: List<CanvasElement>) {
        val hasText = selected.any { it.type == ElementType.TEXT }
        val hasImage = selected.any { it.type == ElementType.IMAGE }
        val hasBackground = selected.any { it.type == ElementType.BACKGROUND }
        val isMulti = selected.size > 1
        val anySelected = selected.isNotEmpty()

        val showFont = anySelected && hasText && !isMulti && !hasImage && !hasBackground
        val showCopy = anySelected && !hasBackground && !isMulti

        updateIconVisibility(binding.opacityIcon, anySelected)
        updateIconVisibility(binding.blendIcon, anySelected)
        updateIconVisibility(binding.fontSizeIcon, showFont)
        updateIconVisibility(binding.copyIcon, showCopy)
        updateIconVisibility(
            binding.alignmentKit,
            anySelected,
            animShow = R.anim.slide_in,
            animHide = R.anim.slide_out
        )
    }

    private fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2
    ) {
        val isVisible = view.visibility == View.VISIBLE

        if (shouldBeVisible && !isVisible) {
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
        } else if (!shouldBeVisible && isVisible) {
            if (view == binding.fontSizeIcon) {
                binding.seekBarFontSize.isVisible = false
                binding.fontSize.isVisible = false
            }
            val anim = AnimationUtils.loadAnimation(view.context, animHide)
            view.startAnimation(anim)
            val duration = anim.duration
            view.postDelayed({ view.visibility = View.GONE }, duration)
        }
    }

    private fun setEvents() {
        binding.back.setOnClickListener { findNavController().navigateUp() }

        val widthPx = when (currentUnit) {
            UnitType.INCHES -> inchesToPx(canvasSize.width)
            UnitType.CENTIMETERS -> cmToPx(canvasSize.width)
            UnitType.PIXELS -> canvasSize.width.toInt()
        }

        val heightPx = when (currentUnit) {
            UnitType.INCHES -> inchesToPx(canvasSize.height)
            UnitType.CENTIMETERS -> cmToPx(canvasSize.height)
            UnitType.PIXELS -> canvasSize.height.toInt()
        }

        // Setup navigation
        val navHostFragment =
            childFragmentManager.findFragmentById(R.id.panelNavHost) as NavHostFragment
        _navController = navHostFragment.navController

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (currentPanelItemId == menuItem.itemId) {
                // Reselected the same item, hide the panel
                binding.panelNavHost.visibility = View.GONE
                currentPanelItemId = null // Reset current item
            } else {
                // New item selected, show the panel and navigate
                binding.panelNavHost.visibility = View.VISIBLE
                currentPanelItemId = menuItem.itemId

                when (menuItem.itemId) {
                    R.id.nav_background -> navController.navigate(R.id.backgroundsFragment)
                    R.id.nav_objects -> navController.navigate(R.id.objectsFragment)
                    R.id.nav_text -> navController.navigate(R.id.textFragment)
                    R.id.nav_images -> navController.navigate(R.id.imagesFragment)
                    R.id.nav_layers -> navController.navigate(R.id.layersFragment)
                    else -> false // Should not happen with defined menu items
                }
            }
            true
        }

        sizedCanvasView = CanvasView(
            requireContext(),
            canvasWidth = widthPx,
            canvasHeight = heightPx,
            onEditTextRequested = { element ->
                if (element.type == ElementType.IMAGE) {
                    viewModel.canvasElements.value?.find { it.id == element.id }?.let {
                        navController.navigate(R.id.filtersFragment)
                    }
                } else {
                    showTextEditDialog(element)
                }
            },
            onElementChanged = { canvasElement ->
                viewModel.canvasElements.value?.find { it.id == canvasElement.id }?.let {
                    viewModel.updateElement(canvasElement)
                }
            },
            onElementRemoved = { canvasElement ->
                viewModel.canvasElements.value?.find { it.id == canvasElement.id }?.let {
                    viewModel.removeElement(it)
                }
            }, onElementSelected = { elements ->
                viewModel.onCanvasSelectionChanged(elements)
            },
            onEndBatchUpdate = { elementId ->
                viewModel.endBatchUpdate(elementId)
            },
            onStartBatchUpdate = { elementId, actionType ->
                viewModel.startBatchUpdate(elementId, actionType)
            },
            onColorPicked = { colorInt ->
                val opaque = (colorInt and 0x00FFFFFF) or (0xFF shl 24)
                viewModel.finishPicking(opaque)
                viewModel.stopPicking()
            }
        ).apply {
            binding.canvasContainer.addView(this)
        }

        canvasManager = CanvasManager(sizedCanvasView)

        viewModel.ensureBackgroundElement(requireActivity(), canvasSize.width, canvasSize.height)

        binding.undo.setOnClickListener { viewModel.undo() }
        binding.redo.setOnClickListener { viewModel.redo() }

        binding.opacityIcon.setOnClickListener {
            togglePanel(showOpacityPanel = true)
        }
        binding.fontSizeIcon.setOnClickListener {
            togglePanel(showOpacityPanel = false)
        }

        binding.blendIcon.setOnClickListener {
            toggleBlendPanel()
        }

        binding.artBoard.setOnClickListener {
            if (currentMode != MultiAlignMode.CANVAS) {
                currentMode = MultiAlignMode.CANVAS
                updateModeDrawables()
            }
        }
        binding.selection.setOnClickListener {
            if (currentMode != MultiAlignMode.SELECTION) {
                currentMode = MultiAlignMode.SELECTION
                updateModeDrawables()
            }
        }

        binding.blendSpinner.setOnClickListener {
            val popupMenu = PopupMenu(requireActivity(), binding.blendSpinner)
            blendingOptions.forEachIndexed { index, blendType ->
                popupMenu.menu.add(0, index, index, blendType.displayName())
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                val selectedBlendType = blendingOptions[menuItem.itemId]
                viewModel.setBlendingType(selectedBlendType)
                true
            }
            popupMenu.show()
        }

        binding.leftAlign.setOnClickListener {
            sizedCanvasView.alignHorizontal(HAlign.LEFT, currentMode)
        }
        binding.centerHorizontal.setOnClickListener {
            sizedCanvasView.alignHorizontal(HAlign.CENTER, currentMode)
        }
        binding.rightAlign.setOnClickListener {
            sizedCanvasView.alignHorizontal(HAlign.RIGHT, currentMode)
        }

        binding.topAlign.setOnClickListener {
            sizedCanvasView.alignVertical(VAlign.TOP, currentMode)
        }
        binding.centerVertical.setOnClickListener {
            sizedCanvasView.alignVertical(VAlign.MIDDLE, currentMode)
        }
        binding.bottomAlign.setOnClickListener {
            sizedCanvasView.alignVertical(VAlign.BOTTOM, currentMode)
        }

        binding.seekBar.apply {
            min = 1
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewModel.setOpacity(progress)
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.seekBarFontSize.apply {
            min = 0
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.fontSize.text = "$progress"
                        viewModel.setTextSizeForAllSelected(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }


        binding.copyIcon.setOnClickListener {
            viewModel.copySelectedElementsGroup()
        }

        binding.done.setOnClickListener {
            viewModel.setCanvasView(sizedCanvasView)
            sizedCanvasView.clearSelection()
            findNavController().navigate(R.id.exportFragment)
        }
    }

    private fun toggleBlendPanel() {
        val isCurrentlyVisible = binding.blendSpinner.isVisible
        if (isCurrentlyVisible) {
            // hide blend panel
            binding.blendSpinner.isVisible = false
        } else {
            // show blendSpinner, hide other panels
            binding.blendSpinner.isVisible = true
            binding.seekBar.isVisible = false
            binding.opacityValue.isVisible = false
            binding.seekBarFontSize.isVisible = false
            binding.fontSize.isVisible = false
        }
    }

    private fun togglePanel(showOpacityPanel: Boolean) {
        if (showOpacityPanel) {
            val isCurrentlyVisible = binding.seekBar.isVisible
            if (isCurrentlyVisible) {
                binding.seekBar.isVisible = false
                binding.opacityValue.isVisible = false
            } else {
                binding.seekBar.isVisible = true
                binding.opacityValue.isVisible = true
                // hide other panels
                binding.seekBarFontSize.isVisible = false
                binding.fontSize.isVisible = false
                binding.blendSpinner.isVisible = false
            }
        } else {
            val isCurrentlyVisible = binding.seekBarFontSize.isVisible
            if (isCurrentlyVisible) {
                binding.seekBarFontSize.isVisible = false
                binding.fontSize.isVisible = false
            } else {
                binding.seekBarFontSize.isVisible = true
                binding.fontSize.isVisible = true
                // hide other panels
                binding.seekBar.isVisible = false
                binding.opacityValue.isVisible = false
                binding.blendSpinner.isVisible = false
            }
        }
    }

    private fun updateModeDrawables() {
        when (currentMode) {
            MultiAlignMode.CANVAS -> {
                binding.artBoard.setImageResource(R.drawable.ic_align_art_board_filled)
                binding.selection.setImageResource(R.drawable.ic_align_selection_stroke)
            }

            MultiAlignMode.SELECTION -> {
                binding.artBoard.setImageResource(R.drawable.ic_align_art_board_stroke)
                binding.selection.setImageResource(R.drawable.ic_align_selection_filled)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _navController = null
        _binding = null
    }
}