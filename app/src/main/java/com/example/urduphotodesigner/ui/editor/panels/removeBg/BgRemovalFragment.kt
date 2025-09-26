package com.example.urduphotodesigner.ui.editor.panels.removeBg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.common.views.BgRemovalCanvas
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

    private fun setEvents() {
        binding.addIcon.setOnClickListener {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        }
        binding.removeIcon.setOnClickListener {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_lasso

        binding.bottomNavigation.setOnItemSelectedListener { item ->
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
            setIconSelected(binding.addIcon, mode == BgRemovalCanvas.ActionMode.ADD)
            setIconSelected(binding.removeIcon, mode == BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.imageCanvas.onPreviewChanged = { enabled ->
            setIconSelected(binding.previewIcon, enabled)
        }

        binding.imageCanvas.onMaskConfirmed = { maskedBitmap ->
            viewModel.applyMaskToSelected(maskedBitmap)
            findNavController().navigateUp()
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

    private fun runSubjectSegmentation(bitmap: Bitmap) {
        binding.progressBar.visibility = View.VISIBLE

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
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}