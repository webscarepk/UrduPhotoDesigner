package com.webscare.urducanvas.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.databinding.FragmentEditorBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

object EditorObserverBinder {

    fun observeViewModel(
        fragment: EditorFragment,
        viewModel: CanvasViewModel,
        mainViewModel: MainViewModel,
        onCanvasReady: () -> Unit
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel.collect { panel ->
                    val expanded = panel != null
                    fragment.expandPanel(expanded)
                    fragment.setPanelTouchBlocked(expanded)
                }
            }
        }

        viewModel.canvasSize.observe(fragment.viewLifecycleOwner) { size ->
            if (size != null) {
                fragment.canvasSize = size

                if (!fragment.uiFullyInitialized) {
                    fragment.uiFullyInitialized = true
                    fragment.initBottomNavigation()
                    fragment.initCanvas(size.width.toInt(), size.height.toInt())
                    fragment.initUIControls()
                    fragment.initBackHandling()
                    onCanvasReady()
                    if (fragment.exportModel == null) fragment.autoSaveSilent()
                } else {
                    fragment.sizedCanvasView.resizeCanvas(size.width.toInt(), size.height.toInt())
                    fragment.autoSaveSilent()
                }
            }
        }
    }

    fun observeAfterCanvasReady(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel,
        mainViewModel: MainViewModel
    ) {
        val viewLifecycleOwner = fragment.viewLifecycleOwner

        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (fragment.uiFullyInitialized) fragment.sizedCanvasView.setSelectionMode(enabled)
        }

        bindCanvasBackgroundObservers(fragment, binding, viewModel, viewLifecycleOwner)
        bindHistoryAndFontObservers(fragment, binding, viewModel, viewLifecycleOwner)
        bindBrushObservers(fragment, viewModel, viewLifecycleOwner)
        bindViewSettingsObservers(fragment, binding, viewModel, viewLifecycleOwner)
        bindGridRulerPanObservers(fragment, binding, viewModel, viewLifecycleOwner)
        bindSelectedElementsObserver(fragment, viewModel, mainViewModel, viewLifecycleOwner)
    }

    private fun bindCanvasBackgroundObservers(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        // Set default background color based on app mode for new designs/projects
        if (fragment.exportModel == null && viewModel.backgroundColor.value == Color.WHITE) {
            val isNightMode = (fragment.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val defaultColor = if (isNightMode) Color.parseColor("#2B2B2B") else Color.WHITE
            viewModel.setCanvasBackgroundColor(defaultColor)
        }

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            if (fragment.isAdded) {
                color?.let {
                    binding.editorRoot.setBackgroundColor(it)
                    fragment.scheduleJsonSave()
                }
            }
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { exportResult ->
            if (exportResult == null) {
                viewModel.ensureBackgroundElement(fragment.requireActivity())
                fragment.autoSaveSilent()
            } else {
                fragment.exportModel = exportResult
                fragment.jsonPath = exportResult.jsonPath
                if (exportResult.imagePath.startsWith("/storage")) {
                    fragment.exportModel!!.imagePath = fragment.imagePath
                } else {
                    fragment.imagePath = exportResult.imagePath
                }
            }
        }

        viewModel.backgroundImage.observe(viewLifecycleOwner) { bitmap ->
            if (fragment.isAdded) {
                bitmap?.let {
                    fragment.canvasManager.setCanvasBackgroundImage(it)
                    fragment.scheduleJsonSave()
                }
            }
        }

        viewModel.backgroundGradient.observe(viewLifecycleOwner) { gradient ->
            if (fragment.isAdded) {
                gradient?.let {
                    fragment.canvasManager.setCanvasBackgroundGradient(it)
                    fragment.scheduleJsonSave()
                }
            }
        }
    }

    private fun bindHistoryAndFontObservers(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        viewModel.canvasUnit.observe(viewLifecycleOwner) { unit ->
            if (unit != null) {
                fragment.currentUnit = unit
                binding.canvasContainer.invalidate()
            }
        }

        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            if (fragment.isAdded) {
                if (!elements.isNullOrEmpty()) {
                    fragment.canvasManager.syncElements(elements)
                    binding.canvasContainer.invalidate()
                    fragment.scheduleJsonSave()
                }
                val panelDestinations = listOf(
                    R.id.adjustmentsParentFragment,
                    R.id.shapeFragment,
                    R.id.textAdjustmentsFragment,
                )
                val currentDest = fragment.navController.currentDestination?.id
                if (currentDest != null && currentDest in panelDestinations) {
                    val hasSelection = elements?.any { it.isSelected } == true
                    if (!hasSelection) fragment.navController.popBackStack(currentDest, true)
                }
            }
        }

        viewModel.canUndo.observe(viewLifecycleOwner) { canUndo ->
            binding.undo.isEnabled = canUndo
        }

        viewModel.canRedo.observe(viewLifecycleOwner) { canRedo ->
            binding.redo.isEnabled = canRedo
        }

        viewModel.currentFont.observe(viewLifecycleOwner) { font ->
            if (font != null && viewModel.isExplicitChange()) {
                fragment.canvasManager.setFont(font)
            }
        }

        viewModel.currentImageFilter.observe(viewLifecycleOwner) { filter ->
            if (filter != null && viewModel.isExplicitChange()) {
                fragment.canvasManager.applyImageFilter(filter)
            }
        }
    }

    private fun bindBrushObservers(
        fragment: EditorFragment,
        viewModel: CanvasViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        viewModel.isDrawingMode.observe(viewLifecycleOwner) { isDrawing ->
            if (fragment.uiFullyInitialized) {
                fragment.sizedCanvasView.setDrawingMode(isDrawing)
            }
        }

        viewModel.brushColor.observe(viewLifecycleOwner) {
            fragment.sizedCanvasView.updateBrushSettings(color = it)
        }

        viewModel.brushThickness.observe(viewLifecycleOwner) {
            fragment.sizedCanvasView.updateBrushSettings(thickness = it)
        }

        viewModel.brushHardness.observe(viewLifecycleOwner) {
            fragment.sizedCanvasView.updateBrushSettings(hardness = it)
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) {
            fragment.sizedCanvasView.updateBrushSettings(style = it)
        }

        viewModel.brushGradient.observe(viewLifecycleOwner) {
            fragment.sizedCanvasView.updateBrushSettings(gradient = it)
        }
    }

    private fun bindViewSettingsObservers(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        viewModel.opacity.observe(viewLifecycleOwner) { opacity ->
            binding.seekBarOpacity.progress = opacity
            binding.opacityValue.text = "${opacity ?: 255}"
        }

        viewModel.currentTextSize.observe(viewLifecycleOwner) { size ->
            binding.fontSize.text = "${size?.toInt() ?: 40}"
            binding.seekBarFontSize.progress = size?.toInt() ?: 40
        }

        viewModel.blendingType.observe(viewLifecycleOwner) { type ->
            binding.blendSpinner.text = type.name
        }

        viewModel.activePicker.observe(viewLifecycleOwner) { slot ->
            if (fragment.uiFullyInitialized) {
                val isDropper = slot in listOf(
                    PickerTarget.EYE_DROPPER_LABEL, PickerTarget.EYE_DROPPER_OVERLAY, PickerTarget.EYE_DROPPER_SHADOW,
                    PickerTarget.EYE_DROPPER_BACKGROUND, PickerTarget.EYE_DROPPER_TEXT_FILL, PickerTarget.EYE_DROPPER_TEXT_STROKE,
                    PickerTarget.EYE_DROPPER_GRADIENT, PickerTarget.EYE_DROPPER_DRAW_STROKE, PickerTarget.EYE_DROPPER_DRAW_FILL,
                    PickerTarget.EYE_DROPPER_IMAGE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_STROKE, PickerTarget.EYE_DROPPER_SHAPE_FILL
                )
                if (isDropper) {
                    fragment.sizedCanvasView.enableColorPicker()
                } else {
                    fragment.sizedCanvasView.disableColorPicker()
                }
            }
        }
    }

    private fun bindGridRulerPanObservers(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        viewModel.isGridEnabled.observe(viewLifecycleOwner) { enabled ->
            fragment.updateToggleButton(binding.grid, enabled)
            fragment.sizedCanvasView.setGridEnabled(enabled)
        }

        viewModel.isRulerEnabled.observe(viewLifecycleOwner) { enabled ->
            fragment.updateToggleButton(binding.ruler, enabled)
            fragment.sizedCanvasView.setRulerEnabled(enabled)
        }

        viewModel.isPanMode.observe(viewLifecycleOwner) { enabled ->
            fragment.updateToggleButton(binding.pan, enabled)
            fragment.sizedCanvasView.setPanMode(enabled)
        }

        viewModel.isCanvasPanLocked.observe(viewLifecycleOwner) { locked ->
            if (fragment.uiFullyInitialized) {
                fragment.sizedCanvasView.setCanvasPanLocked(locked)
            }
        }

        viewModel.zoomLevel.observe(viewLifecycleOwner) { zoom ->
            fragment.sizedCanvasView.setZoomLevel(zoom)
        }
    }

    private fun bindSelectedElementsObserver(
        fragment: EditorFragment,
        viewModel: CanvasViewModel,
        mainViewModel: MainViewModel,
        viewLifecycleOwner: androidx.lifecycle.LifecycleOwner
    ) {
        viewModel.selectedElements.observe(viewLifecycleOwner) { newSelection ->
            if (!fragment.isAdded) return@observe

            val selectionChanged = !newSelection.sameSelectionAs(fragment.lastSelection)
            if (!selectionChanged) return@observe

            fragment.lastSelection = newSelection.toList()

            if (mainViewModel.expandedPanel.value != null) {
                fragment.resetPanelsOnSelectionChange()
                fragment.selectionFromUserInteraction = false
                return@observe
            }

            fragment.resetPanelsOnSelectionChange()
            fragment.updateToolbarVisibility(newSelection)

            if (viewModel.inSelectionMode.value == true) {
                fragment.selectionFromUserInteraction = false
                return@observe
            }

            handleSelectionNavigation(fragment, viewModel, newSelection)
        }
    }

    private fun handleSelectionNavigation(
        fragment: EditorFragment,
        viewModel: CanvasViewModel,
        newSelection: List<CanvasElement>
    ) {
        val first = newSelection.firstOrNull()
        val currentDest = fragment.navController.currentDestination?.id

        if (currentDest == R.id.layersFragment) {
            fragment.selectionFromUserInteraction = false
            return
        }

        if (!fragment.selectionFromUserInteraction) return
        fragment.selectionFromUserInteraction = false

        val targetDestination = when {
            newSelection.size == 1 && first != null -> {
                when (first.type) {
                    ElementType.TEXT -> R.id.textAdjustmentsFragment
                    ElementType.IMAGE, ElementType.STICKER, ElementType.BACKGROUND -> R.id.adjustmentsParentFragment
                    ElementType.SHAPE -> if (fragment.shapeJustAdded) {
                        fragment.shapeJustAdded = false
                        null
                    } else {
                        R.id.shapeFragment
                    }
                    else -> null
                }
            }
            else -> null
        }

        performNavigationForSelection(fragment, viewModel, targetDestination, first, currentDest)
    }

    private fun performNavigationForSelection(
        fragment: EditorFragment,
        viewModel: CanvasViewModel,
        targetDestination: Int?,
        first: CanvasElement?,
        currentDest: Int?
    ) {
        val panelDestinations = listOf(
            R.id.adjustmentsParentFragment,
            R.id.shapeFragment,
            R.id.textAdjustmentsFragment,
        )

        if (targetDestination == null) {
            viewModel.closeAppearanceTab()
            if (currentDest != null && currentDest in panelDestinations) {
                fragment.navController.popBackStack(currentDest, true)
            }
            return
        }

        if (currentDest == targetDestination) return

        first?.let { element ->
            val bundle = Bundle().apply { putString("elementId", element.id) }

            if (targetDestination == R.id.adjustmentsParentFragment) {
                setupAdjustmentCache(element)
            } else if (targetDestination == R.id.textAdjustmentsFragment || targetDestination == R.id.shapesParentFragment) {
                if (!(targetDestination == R.id.shapesParentFragment && fragment.shapeJustAdded)) {
                    viewModel.openAppearanceTab()
                }
            }

            if (currentDest != null && currentDest in panelDestinations && currentDest != targetDestination) {
                fragment.navController.popBackStack(currentDest, true)
            }

            val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
            if (targetDestination == R.id.shapesParentFragment) {
                fragment.shapeJustAdded = false
            }
            fragment.navController.navigate(targetDestination, bundle, navOptions)
        }
    }

    private fun setupAdjustmentCache(element: CanvasElement) {
        if (element.bitmap != null) {
            BitmapCache.put(element.id, element.bitmap!!)
        } else if (element.svgDrawable != null) {
            val svg = element.svgDrawable!!
            val w = svg.intrinsicWidth.takeIf { it > 0 } ?: 512
            val h = svg.intrinsicHeight.takeIf { it > 0 } ?: 512
            svg.setBounds(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bmp).also { svg.draw(it) }
            BitmapCache.put(element.id, bmp)
        }
    }

    private fun List<CanvasElement>.sameSelectionAs(other: List<CanvasElement>): Boolean {
        if (size != other.size) return false
        return this.zip(other).all { (a, b) ->
            a.id == b.id && a.bitmapData == b.bitmapData
        }
    }
}
