package com.webscare.urducanvas.ui.editor.panels.removeBg

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.webscare.ads.WebsCareAds
import com.webscare.urducanvas.BuildConfig
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.BgRemovalCanvas
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentBgRemovalBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BgRemovalFragment : Fragment() {

    private var _binding: FragmentBgRemovalBinding? = null
    private val binding get() = _binding!!
    private var loadingDialog: Dialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null
    private var rotationAnimator: ObjectAnimator? = null
    private var progressAnimator: ValueAnimator? = null

    private val loadingMessages = listOf(
        "Analyzing image details…",
        "Tracing fine edges…",
        "Refining selection for accuracy…",
        "Final touches in progress…",
        "Enhancing sharp areas…",
        "Softening rough edges…",
        "Balancing light and dark regions…",
        "Improving mask precision…",
        "Cleaning background smoothly…",
        "Almost ready, preparing final result…"
    )
    private var messageIndex = 0
    private val viewModel: CanvasViewModel by activityViewModels()
    private var preview = false

    private var originalBitmap: Bitmap? = null
    private var brushMaskBitmap: Bitmap? = null

    private val subjectSegmenter: SubjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        SubjectSegmentation.getClient(options)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBgRemovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Preload background removal rewarded ad to make sure it's ready when requested
        WebsCareAds.preloadRewarded(requireContext(), BuildConfig.AD_REWARDED_BG_REMOVAL)

        setupImage()
        imageCallbacks()
        setEvents()

        binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        updateActionModeUI(BgRemovalCanvas.ActionMode.ADD)
        updateSelectionToolUI(R.id.btnBrush)
        updatePreviewUI(false)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.maskAppliedEvent.collect {
                    dismissLoadingDialog()
                    if (isAdded) findNavController().navigateUp()
                }
            }
        }
    }

    private fun showLoadingDialog() {
        if (!isAdded || loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt()
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params
            window?.setGravity(Gravity.CENTER)
            show()
        }

        startProgressLoop()
        startIconRotation()

        viewLifecycleOwner.lifecycleScope.launch {
            while (isAdded && loadingDialog?.isShowing == true) {
                dialogBinding?.subtitle?.text = loadingMessages[messageIndex]
                dialogBinding?.title?.text = getString(R.string.processing_your_image)
                dialogBinding?.tvProgressPercent?.text = getString(R.string.please_wait)
                dialogBinding?.cancel?.isVisible = true
                dialogBinding?.cancel?.addPressEffect {
                    if (_binding != null) binding.imageCanvas.cancelProcessing()
                }
                messageIndex = (messageIndex + 1) % loadingMessages.size
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun startProgressLoop() {
        progressAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = 1000L
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                dialogBinding?.progressBar?.progress = animator.animatedValue as Int
            }
            start()
        }
    }

    private fun startIconRotation() {
        dialogBinding?.view4?.let { icon ->
            rotationAnimator = ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, 360f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun stopProgressLoop() {
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private fun stopIconRotation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    private fun dismissLoadingDialog() {
        stopProgressLoop()
        stopIconRotation()
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun setEvents() {
        binding.back.addPressEffect {
            if (isAdded) findNavController().navigateUp()
        }

        binding.undo.addPressEffect { binding.imageCanvas.undo() }
        binding.redo.addPressEffect { binding.imageCanvas.redo() }
        binding.done.addPressEffect { binding.imageCanvas.confirmMask() }

        // Quick actions card
        binding.btnMove.addPressEffect {
            binding.imageCanvas.setToolMode(null)
            updateSelectionToolUI(-1)
            binding.handIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
        }

        binding.btnPreview.addPressEffect {
            togglePreview()
        }

        binding.btnFloatingPreview.addPressEffect {
            togglePreview()
        }

        binding.btnInvert.addPressEffect {
            binding.imageCanvas.invertSelection()
        }

        binding.btnClear.addPressEffect {
            binding.imageCanvas.clearSelection()
        }

        binding.btnAdd.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        }

        binding.btnRemove.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.REMOVE)
        }

        // Selection tools
        binding.btnAiSubject.addPressEffect {
            updateSelectionToolUI(R.id.btnAiSubject)
            originalBitmap?.let { bmp ->
                var rewardEarned = false
                WebsCareAds.showRewarded(
                    activity = requireActivity(),
                    adUnitId = BuildConfig.AD_REWARDED_BG_REMOVAL,
                    onRewarded = { _, _ ->
                        rewardEarned = true
                    },
                    onDismissed = {
                        if (rewardEarned) {
                            binding.imageCanvas.postDelayed({
                                if (isAdded) {
                                    runSubjectSegmentation(bmp)
                                }
                            }, 400)
                        } else {
                            Snackbar.make(binding.root, "Watch ad to auto-remove background.", Snackbar.LENGTH_SHORT).show()
                        }
                    },
                    onNotReady = {
                        // Fast non-blocking fallback if ad fails to load or premium bypass is active
                        runSubjectSegmentation(bmp)
                    }
                )
            }
        }

        binding.btnBrush.addPressEffect {
            updateSelectionToolUI(R.id.btnBrush)
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.BRUSH)
        }

        binding.btnRect.addPressEffect {
            updateSelectionToolUI(R.id.btnRect)
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.RECTANGLE)
        }

        binding.btnEllipse.addPressEffect {
            updateSelectionToolUI(R.id.btnEllipse)
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.ELLIPSE)
        }

        binding.btnMagicWand.addPressEffect {
            updateSelectionToolUI(R.id.btnMagicWand)
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.MAGIC_WAND)
        }

        // Zoom Controls
        binding.btnZoomIn.addPressEffect { binding.imageCanvas.zoomIn() }
        binding.btnZoomOut.addPressEffect { binding.imageCanvas.zoomOut() }
    }

    private fun togglePreview() {
        val nextState = !preview
        binding.imageCanvas.setPreviewMode(nextState)
        updatePreviewUI(nextState)
    }

    private fun imageCallbacks() {
        binding.imageCanvas.onProcessingChanged = { isProcessing ->
            if (isProcessing) showLoadingDialog() else dismissLoadingDialog()
        }

        binding.imageCanvas.onActionModeChanged = { mode ->
            updateActionModeUI(mode)
        }

        binding.imageCanvas.onPreviewChanged = { enabled ->
            updatePreviewUI(enabled)
        }

        binding.imageCanvas.onToolModeChanged = { mode ->
            when (mode) {
                BgRemovalCanvas.ToolMode.BRUSH -> updateSelectionToolUI(R.id.btnBrush)
                BgRemovalCanvas.ToolMode.RECTANGLE -> updateSelectionToolUI(R.id.btnRect)
                BgRemovalCanvas.ToolMode.ELLIPSE -> updateSelectionToolUI(R.id.btnEllipse)
                BgRemovalCanvas.ToolMode.MAGIC_WAND -> updateSelectionToolUI(R.id.btnMagicWand)
                null -> {
                    updateSelectionToolUI(-1)
                    binding.handIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
                }
            }
        }

        binding.imageCanvas.onMaskConfirmed = { maskedBitmap ->
            viewModel.applyMaskToSelected(maskedBitmap)
        }

        binding.imageCanvas.onZoomChanged = { scale ->
            if (_binding != null) {
                val percentText = "${(scale * 100).toInt()}%"
                binding.tvZoomPercent.text = percentText
                binding.tvZoomControlPercent.text = percentText
                binding.miniNavigatorCard.isVisible = scale > 1.05f
                if (scale > 1.05f) {
                    originalBitmap?.let { bmp ->
                        binding.miniNavImage.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun updateActionModeUI(mode: BgRemovalCanvas.ActionMode) {
        if (_binding == null) return
        if (mode == BgRemovalCanvas.ActionMode.ADD) {
            binding.btnAdd.background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle)
            binding.btnAdd.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            binding.addIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)

            binding.removeIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            binding.btnRemove.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)

        } else {
            binding.btnRemove.background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle)
            binding.btnRemove.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            binding.removeIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)

            binding.btnAdd.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
            binding.addIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
        }
    }

    private fun updatePreviewUI(isPreviewActive: Boolean) {
        if (_binding == null) return
        preview = isPreviewActive
        if (isPreviewActive) {
            binding.tvPreviewLabel.text = "Exit Preview"
            binding.previewEyeIcon.setImageResource(R.drawable.ic_hide_pass)
            binding.previewEyeIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            binding.btnPreview.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.contrast)
            binding.previewIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
        } else {
            binding.tvPreviewLabel.text = "Preview Cutout"
            binding.previewEyeIcon.setImageResource(R.drawable.ic_show_pass)
            binding.previewEyeIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
            binding.btnPreview.backgroundTintList = null
            binding.previewIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
        }
    }

    private fun updateSelectionToolUI(selectedId: Int) {
        if (_binding == null) return
        val tools = listOf(
            Triple(binding.btnAiSubject, binding.icAiSubject, binding.tvAiSubject),
            Triple(binding.btnBrush, binding.icBrush, binding.tvBrush),
            Triple(binding.btnRect, binding.icRect, binding.tvRect),
            Triple(binding.btnEllipse, binding.icEllipse, binding.tvEllipse),
            Triple(binding.btnMagicWand, binding.icMagicWand, binding.tvMagicWand)
        )
        val toolIds = listOf(
            R.id.btnAiSubject, R.id.btnBrush, R.id.btnRect, R.id.btnEllipse, R.id.btnMagicWand
        )

        tools.forEachIndexed { index, (container, icon, label) ->
            if (toolIds[index] == selectedId) {
                container.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.contrast)
                icon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
                label.setTextColor(ContextCompat.getColor(requireContext(), R.color.appColor))
            } else {
                container.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white)
                icon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.gray)
                label.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
            }
        }

        if (selectedId != -1) {
            binding.handIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
        }
    }

    private fun setupImage() {
        val selected = viewModel.selectedElements.value?.firstOrNull()
        val bitmap = selected?.bitmap
        if (bitmap != null) {
            originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            brushMaskBitmap = createBitmap(bitmap.width, bitmap.height)
            originalBitmap?.let { binding.imageCanvas.setImage(it) }
        }
    }

    private fun runSubjectSegmentation(bitmap: Bitmap) {
        showLoadingDialog()

        val image = InputImage.fromBitmap(bitmap, 0)

        subjectSegmenter.process(image)
            .addOnSuccessListener { result ->
                val maskBuffer = result.foregroundConfidenceMask
                val maskBitmap = result.foregroundBitmap

                if (maskBuffer != null) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        val width = maskBitmap?.width ?: bitmap.width
                        val height = maskBitmap?.height ?: bitmap.height

                        withContext(Dispatchers.Main) {
                            if (_binding != null) {
                                binding.imageCanvas.applyGeneratedMask(maskBuffer, width, height)
                            }
                        }
                    }
                } else {
                    dismissLoadingDialog()
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                dismissLoadingDialog()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissLoadingDialog()
        _binding = null
    }
}