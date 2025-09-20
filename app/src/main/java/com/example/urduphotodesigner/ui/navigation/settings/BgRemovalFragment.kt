package com.example.urduphotodesigner.ui.navigation.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.createBitmap
import androidx.fragment.app.Fragment
import com.example.urduphotodesigner.common.views.BgRemovalCanvas
import com.example.urduphotodesigner.databinding.FragmentBgRemovalBinding

class BgRemovalFragment : Fragment() {

    private var _binding: FragmentBgRemovalBinding? = null
    private val binding get() = _binding!!

    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private var originalBitmap: Bitmap? = null
    private var brushMaskBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBgRemovalBinding.inflate(inflater, container, false)

        setupImagePicker()

        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            imagePickerLauncher.launch(intent)
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

        binding.btnExport.setOnClickListener {
            val result = binding.imageCanvas.exportMaskedImage()
            result?.let { maskedBitmap ->
                // Preview result
                binding.imageCanvas.setImage(maskedBitmap)

                // (Optional) Save to gallery
                // saveBitmapToGallery(maskedBitmap)
            }
        }

        return binding.root
    }

    // 🔹 Image picker launcher
    private fun setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    val inputStream = requireContext().contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    brushMaskBitmap = createBitmap(bitmap.width, bitmap.height)
                    binding.imageCanvas.setImage(originalBitmap!!)
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}