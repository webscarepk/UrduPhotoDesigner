package com.webscare.urducanvas.ui.editor

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.DialogAutoSavingLayoutBinding
import com.webscare.urducanvas.databinding.LayoutCanvasPopupBinding
import com.webscare.urducanvas.databinding.LayoutBlendPopupBinding
import com.webscare.urducanvas.databinding.LayoutZoomPopupBinding
import com.webscare.urducanvas.ui.creation.CreateFragment
import kotlin.math.roundToInt

object EditorDialogManager {

    fun showTextEditDialog(context: Context, element: CanvasElement, viewModel: CanvasViewModel) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_edit_text)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        editText.setText(element.text)
        editText.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString() ?: ""
                element.text = newText
                viewModel.updateText(element)
                viewModel.markChanged()
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0f)
            setGravity(Gravity.BOTTOM)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        dialog.show()
    }

    fun showExportProgressDialog(fragment: EditorFragment) {
        if (fragment.exportDialog?.isShowing == true) return

        val binding = DialogAutoSavingLayoutBinding.inflate(fragment.layoutInflater)
        fragment.exportDialogBinding = binding

        fragment.exportDialog = Dialog(fragment.requireContext()).apply {
            setContentView(binding.root)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (fragment.resources.displayMetrics.widthPixels * 0.8).toInt()
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params

            window?.setGravity(Gravity.CENTER)
            show()
        }
        startIconRotation(fragment)
    }

    fun updateExportDialog(fragment: EditorFragment, percent: Int, stage: String) {
        fragment.exportDialogBinding?.apply {
            progressBar.progress = percent
            tvProgressPercent.text = fragment.getString(R.string.complete, percent)
            exportValue.text = stage
        }
    }

    fun dismissExportDialog(fragment: EditorFragment) {
        stopIconRotation(fragment)
        fragment.exportDialog?.dismiss()
        fragment.exportDialog = null
        fragment.exportDialogBinding = null
    }

    private fun startIconRotation(fragment: EditorFragment) {
        fragment.exportDialogBinding?.view4?.let { icon ->
            fragment.rotationAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopIconRotation(fragment: EditorFragment) {
        fragment.rotationAnimator?.cancel()
        fragment.rotationAnimator = null
    }

    fun showZoomPopup(fragment: EditorFragment, anchorView: View, viewModel: CanvasViewModel) {
        val popupBinding = LayoutZoomPopupBinding.inflate(LayoutInflater.from(fragment.requireActivity()))

        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * fragment.requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        )
        popupWindow.elevation = 8f
        popupWindow.isOutsideTouchable = true

        setupZoomSeekbar(fragment, popupBinding, popupWindow, viewModel)
        showPopupAtAnchor(fragment, anchorView, popupWindow, popupBinding.root)
    }

    private fun setupZoomSeekbar(
        fragment: EditorFragment,
        popupBinding: LayoutZoomPopupBinding,
        popupWindow: PopupWindow,
        viewModel: CanvasViewModel
    ) {
        fun zoomPercentFromProgress(progress: Int): Int = 50 + progress
        fun progressFromZoomLevel(zoomLevel: Float): Int = ((zoomLevel * 100f).roundToInt() - 50).coerceIn(0, 250)
        fun refreshLabel(progress: Int) {
            popupBinding.zoomValue.text = "${zoomPercentFromProgress(progress)}%"
        }

        val initialProgress = progressFromZoomLevel(viewModel.zoomLevel.value ?: 1f)
        popupBinding.zoomSeekbar.progress = initialProgress
        refreshLabel(initialProgress)

        popupBinding.zoomSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomFraction = zoomPercentFromProgress(progress) / 100f
                    viewModel.setZoomLevel(zoomFraction)
                    val actualZoom = fragment.sizedCanvasView.getCurrentZoom()
                    val actualProgress = progressFromZoomLevel(actualZoom)
                    seekBar.progress = actualProgress
                    refreshLabel(actualProgress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        popupBinding.reset.addPressEffect {
            viewModel.resetZoom()
            val resetProgress = progressFromZoomLevel(viewModel.zoomLevel.value ?: 1f)
            popupBinding.zoomSeekbar.progress = resetProgress
            refreshLabel(resetProgress)
            popupWindow.dismiss()
        }
    }

    private fun showPopupAtAnchor(
        fragment: EditorFragment,
        anchorView: View,
        popupWindow: PopupWindow,
        popupBindingRoot: View
    ) {
        anchorView.post {
            val screenHeight = fragment.resources.displayMetrics.heightPixels
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            popupBindingRoot.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val popupHeight = popupBindingRoot.measuredHeight
            val spaceBelow = screenHeight - anchorBottom
            val spaceAbove = anchorTop

            when {
                spaceBelow >= popupHeight -> popupWindow.showAsDropDown(anchorView)
                spaceAbove >= popupHeight -> popupWindow.showAtLocation(
                    anchorView,
                    Gravity.NO_GRAVITY,
                    location[0],
                    anchorTop - popupHeight,
                )
                else -> popupWindow.showAsDropDown(anchorView)
            }
        }
    }

    fun showItemPopupMenu(fragment: EditorFragment, anchorView: View, viewModel: CanvasViewModel) {
        val popupBinding = LayoutBlendPopupBinding.inflate(LayoutInflater.from(fragment.requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (150 * fragment.requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        )

        popupWindow.elevation = 2f
        popupWindow.isOutsideTouchable = true

        setupBlendActions(popupBinding, popupWindow, viewModel)
        showPopupAtAnchor(fragment, anchorView, popupWindow, popupBinding.root)
    }

    private fun setupBlendActions(
        popupBinding: LayoutBlendPopupBinding,
        popupWindow: PopupWindow,
        viewModel: CanvasViewModel
    ) {
        popupBinding.source.addPressEffect {
            viewModel.setBlendingType(BlendType.SRC)
            popupWindow.dismiss()
        }

        popupBinding.normal.addPressEffect {
            viewModel.setBlendingType(BlendType.NORMAL)
            popupWindow.dismiss()
        }

        popupBinding.darken.addPressEffect {
            viewModel.setBlendingType(BlendType.DARKEN)
            popupWindow.dismiss()
        }

        popupBinding.lighten.addPressEffect {
            viewModel.setBlendingType(BlendType.LIGHTEN)
            popupWindow.dismiss()
        }

        popupBinding.multiply.addPressEffect {
            viewModel.setBlendingType(BlendType.MULTIPLY)
            popupWindow.dismiss()
        }

        popupBinding.screen.addPressEffect {
            viewModel.setBlendingType(BlendType.SCREEN)
            popupWindow.dismiss()
        }
    }

    fun showCanvasPopupMenu(fragment: EditorFragment, touchRawX: Float, touchRawY: Float, viewModel: CanvasViewModel) {
        val popupBinding = LayoutCanvasPopupBinding.inflate(LayoutInflater.from(fragment.requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (210 * fragment.resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            elevation = 2f
            isOutsideTouchable = true
        }

        val size = viewModel.canvasSize.value
        popupBinding.canvasSizeValue.text = if (size != null) {
            fragment.getString(R.string.canvas_size_value, size.width.toInt(), size.height.toInt())
        } else {
            ""
        }

        popupBinding.actionCanvasSize.addPressEffect {
            popupWindow.dismiss()
            CreateFragment.newResizeInstance().show(fragment.parentFragmentManager, "resize_canvas")
        }

        setupCanvasPopupBgControls(fragment, popupBinding, popupWindow, viewModel)
        setupCanvasPopupLockControl(fragment, popupBinding, popupWindow, viewModel)
        positionCanvasPopup(fragment, popupBinding, popupWindow, touchRawX, touchRawY)
    }

    private fun setupCanvasPopupBgControls(
        fragment: EditorFragment,
        popupBinding: LayoutCanvasPopupBinding,
        popupWindow: PopupWindow,
        viewModel: CanvasViewModel
    ) {
        var lightColor = ContextCompat.getColor(fragment.requireContext(), R.color.contrast)
        var darkColor = ContextCompat.getColor(fragment.requireContext(), R.color.black)

        if (darkColor == Color.WHITE || darkColor == -1) {
            lightColor = Color.parseColor("#F7F7F7")
            darkColor = Color.parseColor("#2B2B2B")
        }

        val currentBgColor = viewModel.backgroundColor.value ?: Color.WHITE
        val isLightSelected = currentBgColor == lightColor || currentBgColor == Color.WHITE
        val isDarkSelected = currentBgColor == darkColor || currentBgColor == Color.BLACK

        popupBinding.bgLight.backgroundTintList = null
        popupBinding.bgDark.backgroundTintList = null

        with(fragment) {
            popupBinding.bgLight.applySelectionRing(isLightSelected, lightColor)
            popupBinding.bgDark.applySelectionRing(isDarkSelected, darkColor)
        }

        popupBinding.bgLight.addPressEffect {
            viewModel.setCanvasBackgroundColor(lightColor)
            popupWindow.dismiss()
        }
        popupBinding.bgDark.addPressEffect {
            viewModel.setCanvasBackgroundColor(darkColor)
            popupWindow.dismiss()
        }
    }

    private fun setupCanvasPopupLockControl(
        fragment: EditorFragment,
        popupBinding: LayoutCanvasPopupBinding,
        popupWindow: PopupWindow,
        viewModel: CanvasViewModel
    ) {
        val locked = viewModel.isCanvasPanLocked.value ?: false
        popupBinding.actionLock.text =
            fragment.getString(if (locked) R.string.unlock_canvas else R.string.lock_canvas)
        popupBinding.actionLock.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0,
            0,
            if (locked) R.drawable.ic_unlock else R.drawable.ic_lock,
            0,
        )
        popupBinding.actionLock.addPressEffect {
            viewModel.toggleCanvasPanLock()
            popupWindow.dismiss()
        }
    }

    private fun positionCanvasPopup(
        fragment: EditorFragment,
        popupBinding: LayoutCanvasPopupBinding,
        popupWindow: PopupWindow,
        touchRawX: Float,
        touchRawY: Float
    ) {
        fragment.binding.canvasContainer.post {
            val screenHeight = fragment.resources.displayMetrics.heightPixels
            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val popupHeight = popupBinding.root.measuredHeight
            val x = touchRawX.toInt()
            val y = if (screenHeight - touchRawY >= popupHeight) {
                touchRawY.toInt()
            } else {
                (touchRawY - popupHeight).toInt()
            }
            popupWindow.showAtLocation(fragment.binding.root, Gravity.NO_GRAVITY, x, y)
        }
    }
}
