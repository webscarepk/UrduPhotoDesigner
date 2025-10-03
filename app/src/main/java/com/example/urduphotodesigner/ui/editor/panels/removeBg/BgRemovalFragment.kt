package com.example.urduphotodesigner.ui.editor.panels.removeBg

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
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.views.BgRemovalCanvas
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentBgRemovalBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.getValue

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
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = Dialog(requireContext()).apply {
            setContentView(dialogBinding!!.root)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window?.attributes
            params?.width = (resources.displayMetrics.widthPixels * 0.8).toInt() // 80% width
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.attributes = params

            window?.setGravity(Gravity.CENTER)
            show()
        }

        // start animated progress loop
        startProgressLoop()
        startIconRotation()

        // start rotating messages
        lifecycleScope.launch {
            while (loadingDialog?.isShowing == true) {
                dialogBinding?.subtitle?.text = loadingMessages[messageIndex]
                dialogBinding?.title?.text = getString(R.string.processing_your_image)
                dialogBinding?.tvProgressPercent?.text = getString(R.string.please_wait)
                dialogBinding?.cancel?.isVisible = true
                dialogBinding?.cancel?.addPressEffect { binding.imageCanvas.cancelProcessing() }
                messageIndex = (messageIndex + 1) % loadingMessages.size
                kotlinx.coroutines.delay(2000) // change text every 2s
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
        binding.addIcon.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        }
        binding.removeIcon.addPressEffect {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            toggleMiniToolbar(true)

            when (item.itemId) {
                R.id.nav_lasso -> {
                    binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.BRUSH)
                    true
                }

                R.id.nav_rect -> {
                    binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.RECTANGLE)
                    true
                }

                R.id.nav_circle -> {
                    binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.ELLIPSE)
                    true
                }

                R.id.nav_magic_wand -> {
                    binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.MAGIC_WAND)
                    true
                }

                R.id.nav_subject -> {
                    originalBitmap?.let { bmp ->
                        runSubjectSegmentation(bmp)
                    }
                    true
                }

                else -> false
            }
        }

        binding.invertIcon.addPressEffect {
            binding.imageCanvas.invertSelection()
        }

        binding.clearIcon.addPressEffect {
            binding.imageCanvas.clearSelection()
        }

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
            binding.imageCanvas.confirmMask()
        }

        binding.undo.addPressEffect { binding.imageCanvas.undo() }
        binding.redo.addPressEffect { binding.imageCanvas.redo() }
    }

    private fun imageCallbacks() {
        binding.imageCanvas.onProcessingChanged = { isProcessing ->
            if (isProcessing) {
                showLoadingDialog()
            } else {
                dismissLoadingDialog()
            }
        }


        binding.imageCanvas.onToolModeChanged = { mode ->
            // sync bottom nav / icons
            when (mode) {
                BgRemovalCanvas.ToolMode.BRUSH -> setIconSelected(binding.handIcon, false)
                null -> setIconSelected(binding.handIcon, true)
                else -> { /* other modes */
                }
            }
        }

        binding.imageCanvas.onActionModeChanged = { mode ->
            // update add/remove buttons tint
            setActionIconSelected(binding.addIcon, mode == BgRemovalCanvas.ActionMode.ADD)
            setActionIconSelected(binding.removeIcon, mode == BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.imageCanvas.onPreviewChanged = { enabled ->
            setIconSelected(binding.previewIcon, enabled)
        }

        binding.imageCanvas.onMaskConfirmed = { maskedBitmap ->
            viewModel.applyMaskToSelected(maskedBitmap)
            findNavController().navigateUp()
        }
    }

    private fun toggleMiniToolbar(show: Boolean) {
        if (show && !binding.miniToolbar.isVisible) {
            binding.miniToolbar.apply {
                visibility = View.VISIBLE
                translationY = height.toFloat()
                animate()
                    .translationY(0f)
                    .setDuration(250)
                    .start()
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
        val selected = viewModel.selectedElements.value?.firstOrNull()
        val bitmap = selected?.bitmap
        originalBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)
        brushMaskBitmap = bitmap?.let { createBitmap(it.width, bitmap.height) }
        binding.imageCanvas.setImage(originalBitmap!!)
    }

    private fun setIconSelected(view: ImageView, selected: Boolean) {
        if (selected) {
            view.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            view.imageTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.white)
        } else {
            view.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.contrast)
            view.imageTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black)
        }
    }

    private fun setActionIconSelected(view: ImageView, selected: Boolean) {
        if (selected) {
            view.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.appColor)
            view.imageTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.appColor)
        } else {
            view.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.light_gray)
            view.imageTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.light_gray)
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
                            binding.imageCanvas.applyGeneratedMask(maskBuffer, width, height)
                            showLoadingDialog()
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
        _binding = null
    }
}