package com.webscare.urducanvas.ui.editor.panels.removeBg

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.BgRemovalCanvas
import com.webscare.urducanvas.databinding.DialogLoadingProgressBinding
import com.webscare.urducanvas.databinding.FragmentBgRemovalBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BgRemovalFragment : androidx.fragment.app.Fragment() {

    companion object {
        private const val TAG = "BgRemovalFragment"
    }

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
    private var preview = true

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
        setupImage()
        imageCallbacks()
        setEvents()
        binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)

        // Observe maskAppliedEvent HERE (not in EditorFragment) so we control
        // dialog dismiss + navigation in one place, in the right order.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.maskAppliedEvent.collect {
                    // Data is committed — dismiss dialog then navigate back immediately.
                    dismissLoadingDialog()
                    if (isAdded) findNavController().navigateUp()
                }
            }
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
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

        lifecycleScope.launch {
            while (loadingDialog?.isShowing == true) {
                dialogBinding?.subtitle?.text = loadingMessages[messageIndex]
                dialogBinding?.title?.text = getString(R.string.processing_your_image)
                dialogBinding?.tvProgressPercent?.text = getString(R.string.please_wait)
                dialogBinding?.cancel?.isVisible = true
                dialogBinding?.cancel?.addPressEffect { binding.imageCanvas.cancelProcessing() }
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

    private fun stopProgressLoop() { progressAnimator?.cancel(); progressAnimator = null }
    private fun stopIconRotation() { rotationAnimator?.cancel(); rotationAnimator = null }

    private fun dismissLoadingDialog() {
        stopProgressLoop()
        stopIconRotation()
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun setEvents() {
        binding.addIcon.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        }
        binding.removeIcon.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            toggleMiniToolbar(true)
            when (item.itemId) {
                R.id.nav_lasso -> { binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.BRUSH); true }
                R.id.nav_rect -> { binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.RECTANGLE); true }
                R.id.nav_circle -> { binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.ELLIPSE); true }
                R.id.nav_magic_wand -> { binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.MAGIC_WAND); true }
                R.id.nav_subject -> {
                    val bmp = originalBitmap
                    if (bmp != null) runSubjectSegmentation(bmp)
                    else Log.e(TAG, "nav_subject: originalBitmap is null")
                    true
                }
                else -> false
            }
        }

        binding.invertIcon.addPressEffect { binding.imageCanvas.invertSelection() }
        binding.clearIcon.addPressEffect { binding.imageCanvas.clearSelection() }

        binding.previewIcon.addPressEffect {
            if (!preview) {
                originalBitmap?.let { bitmap -> binding.imageCanvas.setImage(bitmap) }
            } else {
                binding.imageCanvas.previewMaskedImage()?.let { maskedBitmap ->
                    binding.imageCanvas.setImage(maskedBitmap)
                }
            }
            binding.imageCanvas.setPreviewMode(preview)
            setIconSelected(binding.previewIcon, preview)
            preview = !preview
        }

        binding.handIcon.addPressEffect {
            if (binding.imageCanvas.getToolMode() == null) {
                binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.BRUSH)
                setIconSelected(binding.handIcon, false)
            } else {
                binding.imageCanvas.setToolMode(null)
                setIconSelected(binding.handIcon, true)
            }
        }

        binding.back.addPressEffect { findNavController().navigateUp() }

        binding.done.addPressEffect {
            // Show a saving indicator while we encode the bitmap off-thread.
            // Navigation back to EditorFragment happens via EditorFragment observing
            // viewModel.maskAppliedEvent — AFTER the data is committed to LiveData.
            // DO NOT call navigateUp() here — that was the race condition bug.
            binding.imageCanvas.confirmMask()
        }

        binding.undo.addPressEffect { binding.imageCanvas.undo() }
        binding.redo.addPressEffect { binding.imageCanvas.redo() }
    }

    private fun imageCallbacks() {
        binding.imageCanvas.onProcessingChanged = { isProcessing ->
            if (isProcessing) showLoadingDialog() else dismissLoadingDialog()
        }

        binding.imageCanvas.onToolModeChanged = { mode ->
            when (mode) {
                BgRemovalCanvas.ToolMode.BRUSH -> setIconSelected(binding.handIcon, false)
                null -> setIconSelected(binding.handIcon, true)
                else -> {}
            }
        }

        binding.imageCanvas.onActionModeChanged = { mode ->
            setActionIconSelected(binding.addIcon, mode == BgRemovalCanvas.ActionMode.ADD)
            setActionIconSelected(binding.removeIcon, mode == BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.imageCanvas.onPreviewChanged = { enabled ->
            setIconSelected(binding.previewIcon, enabled)
        }

        // onMaskConfirmed: hand bitmap to ViewModel, show a brief loading state.
        // Navigation happens in the maskAppliedEvent collector above (in onViewCreated),
        // which fires AFTER the encode coroutine commits data — dismiss dialog → navigateUp().
        binding.imageCanvas.onMaskConfirmed = { maskedBitmap ->
            Log.d(TAG, "onMaskConfirmed: ${maskedBitmap.width}x${maskedBitmap.height}")
            showLoadingDialog()           // brief spinner while encoding on background thread
            viewModel.applyMaskToSelected(maskedBitmap)
            // navigateUp() is NOT called here — the maskAppliedEvent collector handles it
            // AFTER the data is committed, so EditorFragment always sees the new bitmap.
        }
    }

    private fun toggleMiniToolbar(show: Boolean) {
        if (show && !binding.miniToolbar.isVisible) {
            binding.miniToolbar.apply {
                visibility = View.VISIBLE
                translationY = height.toFloat()
                animate().translationY(0f).setDuration(250).start()
            }
        } else if (!show && binding.miniToolbar.isVisible) {
            binding.miniToolbar.animate()
                .translationY(binding.miniToolbar.height.toFloat())
                .setDuration(250)
                .withEndAction { binding.miniToolbar.visibility = View.GONE }
                .start()
        }
    }

    private fun setupImage() {
        binding.bottomNavigation.selectedItemId = R.id.nav_lasso

        val selected = viewModel.selectedElements.value?.firstOrNull()
        val bitmap = selected?.bitmap
        if (bitmap == null) {
            Log.e(TAG, "setupImage: no bitmap available from selected element")
            return
        }
        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        brushMaskBitmap = createBitmap(bitmap.width, bitmap.height)
        binding.imageCanvas.setImage(originalBitmap!!)
        Log.d(TAG, "setupImage: loaded ${bitmap.width}x${bitmap.height} bitmap")
    }

    private fun setIconSelected(view: ImageView, selected: Boolean) {
        if (selected) {
            view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            view.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.white)
        } else {
            view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.contrast)
            view.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.black)
        }
    }

    private fun setActionIconSelected(view: ImageView, selected: Boolean) {
        if (selected) {
            view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            view.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.appColor)
        } else {
            view.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.light_gray)
            view.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.light_gray)
        }
    }

    private fun runSubjectSegmentation(bitmap: Bitmap) {
        Log.d(TAG, "runSubjectSegmentation: ${bitmap.width}x${bitmap.height}")
        showLoadingDialog()
        val image = InputImage.fromBitmap(bitmap, 0)
        subjectSegmenter.process(image)
            .addOnSuccessListener { result ->
                val maskBuffer = result.foregroundConfidenceMask
                val maskBitmap = result.foregroundBitmap
                Log.d(TAG, "MLKit success — maskBuffer=${maskBuffer != null} maskBitmap=${maskBitmap?.width}x${maskBitmap?.height}")
                if (maskBuffer != null) {
                    val width = maskBitmap?.width ?: bitmap.width
                    val height = maskBitmap?.height ?: bitmap.height
                    dismissLoadingDialog() // hand off to canvas processing flow
                    binding.imageCanvas.applyGeneratedMask(maskBuffer, width, height)
                } else {
                    Log.w(TAG, "maskBuffer is null — no subject detected")
                    dismissLoadingDialog()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "MLKit failed: ${e.javaClass.simpleName}: ${e.message}", e)
                dismissLoadingDialog()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}