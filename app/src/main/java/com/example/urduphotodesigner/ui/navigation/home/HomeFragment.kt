package com.example.urduphotodesigner.ui.navigation.home

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.utils.LayerImportEngine
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding?= null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    private fun setEvents() {
        binding.create.setOnClickListener {
            findNavController().navigate(R.id.createFragment)
        }

        binding.template.setOnClickListener {
//            findNavController().navigate(R.id.templatesFragment)
            loadTemplate()
        }

        binding.saved.setOnClickListener {
            findNavController().navigate(R.id.savedFragment)
        }
    }

    private fun loadTemplate() {
        val assetStream = requireActivity().assets.open("1.pdf")
        val document = PDDocument.load(assetStream).apply { assetStream.close() }

        // Extract layer references (if any)
        val ocProps = document.documentCatalog.ocProperties
        val groupsByRef: Map<COSBase, PDOptionalContentGroup> =
            ocProps?.optionalContentGroups?.associateBy { it.cosObject } ?: emptyMap()

        val elements = mutableListOf<CanvasElement>()
        var canvasSize: CanvasSize? = null

        val engine = LayerImportEngine(
            groupsByRef,
            onElement = { elem ->
                elements.add(elem)
                Log.d("PDFParser", "Element: ${elem.type} @ x=${elem.x} y=${elem.y}")
            },
            onCanvasSize = { size ->
                canvasSize = size
                Log.d("PDFParser", "Canvas size: $size")
            }
        )

        engine.processDocument(document)
        document.close()
        Log.d("PDFParser", "${elements.size}")
        val bundle = Bundle().apply {
            putSerializable("canvas_size", canvasSize)
            putSerializable("unit_type", UnitType.PIXELS)
        }

        viewModel.setCanvasSize(canvasSize!!)
        viewModel.canvasElements.value = elements
        findNavController().navigate(R.id.editorFragment, bundle)
        // Use 'elements' and 'canvasSize' for rendering
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}