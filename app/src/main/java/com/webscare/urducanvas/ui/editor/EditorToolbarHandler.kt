package com.webscare.urducanvas.ui.editor

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.SeekBar
import androidx.annotation.AnimRes
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.HAlign
import com.webscare.urducanvas.common.canvas.enums.MultiAlignMode
import com.webscare.urducanvas.common.canvas.enums.VAlign
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.utils.Utils.vibrateSoft
import com.webscare.urducanvas.databinding.FragmentEditorBinding

object EditorToolbarHandler {

    fun initUIControls(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel
    ) {
        binding.fabContainer.post {
            val fab = binding.fabAdd
            val container = binding.fabContainer

            if (container.width > 0 && container.height > 0) {
                fab.x = container.width - fab.width - fragment.fabMargin.toFloat()
                fab.y = container.height - fab.height - fragment.fabMargin.toFloat()
            }

            fragment.initFab()
        }

        setupUndoRedoShowHide(fragment, binding, viewModel)
        setupActivePanels(fragment, binding)
        setupAlignments(fragment, binding)
        setupSeekbarsAndIcons(fragment, binding, viewModel)
        setupBottomControls(fragment, binding, viewModel)

        fragment.initPanelSheet()
    }

    private fun setupUndoRedoShowHide(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel
    ) {
        binding.undo.addPressEffect {
            binding.undo.vibrateSoft()
            viewModel.undo()
        }
        binding.redo.addPressEffect {
            binding.redo.vibrateSoft()
            viewModel.redo()
        }
        binding.showHide.addPressEffect {
            fragment.panelsLocked = !fragment.panelsLocked
            if (fragment.panelsLocked) {
                fragment.resetPanelsOnSelectionChange()
                binding.showHide.animate().rotation(180f).setDuration(300).start()
            } else {
                binding.showHide.animate().rotation(0f).setDuration(300).start()
            }
            fragment.updateToolbarVisibility(viewModel.selectedElements.value ?: emptyList())
        }
    }

    private fun setupActivePanels(
        fragment: EditorFragment,
        binding: FragmentEditorBinding
    ) {
        binding.opacityIcon.addPressEffect {
            fragment.togglePanel(showOpacityPanel = true)
            binding.opacityValue.setTextColor(ColorStateList.valueOf(fragment.colorOf(R.color.white)))
            binding.opacityValue.backgroundTintList =
                ColorStateList.valueOf(fragment.colorOf(R.color.appColor))
            fragment.resetFontSizeState()
            fragment.resetBlendState()
            fragment.activePanel = binding.opacityValue
        }

        binding.opacityValue.addPressEffect {
            fragment.togglePanel(showOpacityPanel = true)
            binding.opacityValue.setTextColor(ColorStateList.valueOf(fragment.colorOf(R.color.black)))
            binding.opacityValue.backgroundTintList = ColorStateList.valueOf(fragment.colorOf(R.color.white))
            fragment.resetFontSizeState()
            fragment.resetBlendState()
            fragment.activePanel = null
        }

        binding.fontSize.addPressEffect {
            if (fragment.activePanel == binding.fontSize) {
                fragment.resetFontSizeState()
                fragment.resetOpacityState()
                fragment.resetBlendState()
                fragment.activePanel = null
            } else {
                binding.fontSize.setTextColor(ColorStateList.valueOf(fragment.colorOf(R.color.white)))
                binding.fontSize.backgroundTintList =
                    ColorStateList.valueOf(fragment.colorOf(R.color.appColor))
                fragment.resetOpacityState()
                fragment.resetBlendState()
                fragment.activePanel = binding.fontSize
            }
            fragment.togglePanel(showOpacityPanel = false)
        }

        binding.blendIcon.addPressEffect {
            if (fragment.activePanel == binding.blendIcon) {
                fragment.resetBlendState()
                fragment.resetOpacityState()
                fragment.resetFontSizeState()
                fragment.activePanel = null
            } else {
                binding.blendIcon.imageTintList = ColorStateList.valueOf(fragment.colorOf(R.color.white))
                binding.blendIcon.backgroundTintList =
                    ColorStateList.valueOf(fragment.colorOf(R.color.appColor))
                fragment.resetOpacityState()
                fragment.resetFontSizeState()
                fragment.activePanel = binding.blendIcon
            }
            fragment.toggleBlendPanel()
        }
    }

    private fun setupAlignments(
        fragment: EditorFragment,
        binding: FragmentEditorBinding
    ) {
        binding.artBoard.addPressEffect {
            if (fragment.currentMode != MultiAlignMode.CANVAS) {
                fragment.currentMode = MultiAlignMode.CANVAS
                fragment.updateModeDrawables()
            }
        }
        binding.selection.addPressEffect {
            if (fragment.currentMode != MultiAlignMode.SELECTION) {
                fragment.currentMode = MultiAlignMode.SELECTION
                fragment.updateModeDrawables()
            }
        }

        binding.blendSpinner.addPressEffect {
            fragment.showItemPopupMenu(binding.blendSpinner)
        }

        binding.leftAlign.addPressEffect {
            fragment.sizedCanvasView.alignHorizontal(HAlign.LEFT, fragment.currentMode)
        }
        binding.centerHorizontal.addPressEffect {
            fragment.sizedCanvasView.alignHorizontal(HAlign.CENTER, fragment.currentMode)
        }
        binding.rightAlign.addPressEffect {
            fragment.sizedCanvasView.alignHorizontal(HAlign.RIGHT, fragment.currentMode)
        }

        binding.topAlign.addPressEffect { fragment.sizedCanvasView.alignVertical(VAlign.TOP, fragment.currentMode) }
        binding.centerVertical.addPressEffect {
            fragment.sizedCanvasView.alignVertical(VAlign.MIDDLE, fragment.currentMode)
        }
        binding.bottomAlign.addPressEffect {
            fragment.sizedCanvasView.alignVertical(VAlign.BOTTOM, fragment.currentMode)
        }
    }

    private fun setupSeekbarsAndIcons(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel
    ) {
        binding.seekBarOpacity.apply {
            max = 255
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) viewModel.setOpacity(progress)
                }

                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }

        binding.seekBarFontSize.apply {
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

        binding.copyIcon.addPressEffect { viewModel.copySelectedElementsGroup() }

        binding.cutOutIcon.addPressEffect {
            fragment.view?.post {
                val selected = viewModel.selectedElements.value?.firstOrNull()
                if (selected?.bitmap != null && selected.bitmapData != null) {
                    fragment.findNavController().navigate(R.id.bgRemovalFragment)
                }
            }
        }
    }

    private fun setupBottomControls(
        fragment: EditorFragment,
        binding: FragmentEditorBinding,
        viewModel: CanvasViewModel
    ) {
        binding.zoom.addPressEffect {
            fragment.showZoomPopup(binding.zoom)
        }

        binding.grid.addPressEffect {
            binding.grid.vibrateSoft()
            viewModel.toggleGrid()
        }

        binding.ruler.addPressEffect {
            binding.ruler.vibrateSoft()
            viewModel.toggleRuler()
        }

        binding.pan.addPressEffect {
            viewModel.togglePanMode()
        }

        binding.done.addPressEffect {
            viewModel.setCanvasView(fragment.sizedCanvasView)
            fragment.sizedCanvasView.clearSelection()
            fragment.view?.post {
                fragment.findNavController().navigate(R.id.exportFragment)
            }
        }

        fragment.initPanelSheet()
    }

    fun toggleBlendPanel(fragment: EditorFragment, binding: FragmentEditorBinding) {
        val isCurrentlyVisible = binding.blendSpinner.isVisible
        if (isCurrentlyVisible) {
            binding.blendSpinner.isVisible = false
        } else {
            fragment.activePanel = binding.blendIcon
            binding.blendSpinner.isVisible = true
            binding.seekBarOpacity.isVisible = false
            binding.opacityValue.isVisible = false
            binding.opacityIcon.isVisible = true
            binding.seekBarFontSize.isVisible = false
        }
    }

    fun togglePanel(fragment: EditorFragment, binding: FragmentEditorBinding, showOpacityPanel: Boolean) {
        if (showOpacityPanel) {
            val isCurrentlyVisible = binding.seekBarOpacity.isVisible
            if (isCurrentlyVisible) {
                binding.seekBarOpacity.isVisible = false
                binding.opacityValue.isVisible = false
                binding.opacityIcon.isVisible = true
            } else {
                fragment.activePanel = binding.opacityValue
                binding.seekBarOpacity.isVisible = true
                binding.opacityIcon.isVisible = false
                binding.opacityValue.isVisible = true
                binding.seekBarFontSize.isVisible = false
                binding.blendSpinner.isVisible = false
            }
        } else {
            val isCurrentlyVisible = binding.seekBarFontSize.isVisible
            if (isCurrentlyVisible) {
                binding.seekBarFontSize.isVisible = false
            } else {
                fragment.activePanel = binding.fontSize
                binding.seekBarFontSize.isVisible = true
                binding.seekBarOpacity.isVisible = false
                binding.opacityValue.isVisible = false
                binding.opacityIcon.isVisible = true
                binding.blendSpinner.isVisible = false
            }
        }
    }

    fun updateModeDrawables(fragment: EditorFragment, binding: FragmentEditorBinding) {
        when (fragment.currentMode) {
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

    fun updateToggleButton(fragment: EditorFragment, view: ImageView, isActive: Boolean) {
        if (isActive) {
            view.backgroundTintList = ColorStateList.valueOf(fragment.colorOf(R.color.appColor))
            view.imageTintList = ColorStateList.valueOf(fragment.colorOf(R.color.white))
        } else {
            view.backgroundTintList = ColorStateList.valueOf(fragment.colorOf(R.color.contrast))
            view.imageTintList = ColorStateList.valueOf(fragment.colorOf(R.color.gray))
        }
    }

    fun updateToolbarVisibility(fragment: EditorFragment, binding: FragmentEditorBinding, selected: List<CanvasElement>) {
        updateIconVisibility(binding, binding.showHideContainer, selected.isNotEmpty())
        if (fragment.panelsLocked) {
            fragment.resetPanelsOnSelectionChange()
            updateIconVisibility(binding, binding.opacityPane, false)
            updateIconVisibility(binding, binding.blendPane, false)
            updateIconVisibility(binding, binding.fontSizePane, false)
            updateIconVisibility(binding, binding.copyIcon, false)
            updateIconVisibility(binding, binding.cutOutIcon, false)
            updateIconVisibility(binding, binding.alignmentKit, false)
            updateIconVisibility(binding, binding.selection, false)
            return
        }

        val hasText = selected.any { it.type == ElementType.TEXT }
        val hasImage = selected.any { it.type == ElementType.IMAGE || it.type == ElementType.STICKER }
        val hasBackground = selected.any { it.type == ElementType.BACKGROUND }
        val hasShapeMask = selected.any { it.type == ElementType.SHAPE && it.bitmap != null }
        val isMulti = selected.size > 1
        val isSvg = selected.any { it.svgData != null }
        val anySelected = selected.isNotEmpty()

        val showFont = anySelected && hasText && !isMulti && !hasImage && !hasBackground
        val showCopy = anySelected && !hasBackground && !isMulti
        val showAlignWithSelection = isMulti
        val showRemoveBg = (hasImage || hasBackground || hasShapeMask) && !isMulti && !isSvg

        updateIconVisibility(binding, binding.opacityPane, anySelected)
        updateIconVisibility(binding, binding.blendPane, anySelected)
        updateIconVisibility(binding, binding.fontSizePane, showFont)
        updateIconVisibility(binding, binding.copyIcon, showCopy)
        updateIconVisibility(binding, binding.cutOutIcon, showRemoveBg)
        updateIconVisibility(
            binding,
            binding.alignmentKit,
            anySelected,
            animShow = R.anim.slide_in,
            animHide = R.anim.slide_out,
        )
        updateIconVisibility(binding, binding.selection, showAlignWithSelection)
    }

    fun updateIconVisibility(
        binding: FragmentEditorBinding,
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2,
    ) {
        val isVisible = view.isVisible

        if (shouldBeVisible && !isVisible) {
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
        } else if (!shouldBeVisible && isVisible) {
            if (view == binding.fontSizePane) {
                binding.seekBarFontSize.isVisible = false
            }
            val anim = AnimationUtils.loadAnimation(view.context, animHide)
            view.startAnimation(anim)
            val duration = anim.duration
            view.postDelayed({ view.visibility = View.GONE }, duration)
        }
    }
}
