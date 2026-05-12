package com.webscare.urducanvas.ui.editor.panels.shape

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.databinding.FragmentObjectsListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class VectorsTabFragment : Fragment() {

    private var _binding: FragmentObjectsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private var shapesAdapter: ShapeAdapter? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = requireContext().contentResolver
                        .openInputStream(uri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)
                        }
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            viewModel.addImageInsideShape(bitmap, requireActivity())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VectorsTabFragment", "Image pick failed", e)
                }
            }
        }

    // Called by ShapesParentFragment when addImage button pressed
    fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (shapesAdapter == null) {
            shapesAdapter = ShapeAdapter(requireContext(), ShapeType.entries) { shape ->
                val elements = viewModel.canvasElements.value
                val isMask = viewModel.isMaskingMode.value
                val isShapeSelected = elements?.any {
                    it.isSelected && it.type == ElementType.SHAPE
                } == true
                val isImageSelected = elements?.any {
                    it.isSelected && it.type == ElementType.IMAGE
                } == true

                if (isMask == true && isImageSelected) {
                    val selectedElement = elements.find {
                        it.isSelected && it.type == ElementType.IMAGE
                    }
                    selectedElement?.let {
                        viewModel.mergeImageToShape(it, shape, requireActivity())
                    }
                } else {
                    viewModel.updateShapeType(shape)
                    if (!isShapeSelected) {
                        viewModel.addShapeElement()
                    }
                }
            }
        }

        binding.objects.apply {
            setHasFixedSize(true)
            adapter = shapesAdapter
        }

        binding.noEmojis.visibility = View.GONE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}