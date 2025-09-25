package com.example.urduphotodesigner.ui.navigation.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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


class BgRemovalFragment : Fragment() {

    private var _binding: FragmentBgRemovalBinding? = null
    private val binding get() = _binding!!

    private var preview = true
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                brushMaskBitmap = createBitmap(bitmap.width, bitmap.height)
                binding.imageCanvas.setImage(originalBitmap!!)
            }
        }

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


        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnBrush.setOnClickListener {
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.BRUSH)
        }

        binding.btnRect.setOnClickListener {
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.RECTANGLE)
        }

        binding.btnEllipse.setOnClickListener {
            binding.imageCanvas.setToolMode(BgRemovalCanvas.ToolMode.ELLIPSE)
        }

        binding.btnClear.setOnClickListener {
            binding.imageCanvas.clearSelection()
        }

        binding.btnAdd.setOnClickListener {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.ADD)
        }
        binding.btnRemove.setOnClickListener {
            binding.imageCanvas.setActionMode(BgRemovalCanvas.ActionMode.REMOVE)
        }

        binding.btnSelectSubject.setOnClickListener {
            originalBitmap?.let { bmp ->
                runSubjectSegmentation(bmp)
            }
        }

        binding.btnExport.setOnClickListener {
            if (!preview){
                originalBitmap?.let { bitmap -> binding.imageCanvas.setImage(bitmap) }
            }else{
                val result = binding.imageCanvas.exportMaskedImage()
                result?.let { maskedBitmap ->
                    binding.imageCanvas.setImage(maskedBitmap)
                }
            }
            preview = !preview
        }

        binding.btnPan.setOnClickListener {
            binding.imageCanvas.setToolMode(null)
        }

        binding.back.addPressEffect { findNavController().navigateUp() }

        binding.done.addPressEffect {
            val result = binding.imageCanvas.exportMaskedImage()
            result?.let { maskedBitmap ->
                binding.imageCanvas.setImage(maskedBitmap)
            }
        }

        binding.undo.addPressEffect { binding.imageCanvas.undo() }
        binding.redo.addPressEffect { binding.imageCanvas.redo() }

        return binding.root
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