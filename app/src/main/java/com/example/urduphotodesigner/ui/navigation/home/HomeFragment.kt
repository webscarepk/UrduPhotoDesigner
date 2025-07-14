package com.example.urduphotodesigner.ui.navigation.home

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.utils.LayerImportEngine
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding?= null
    private val binding get() = _binding!!

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
        val assetStream = requireActivity().assets.open("template.pdf")
        val document = PDDocument.load(assetStream).apply { assetStream.close() }

        // Grab the OCProperties (layers) from the catalog
        val ocProps = document.documentCatalog.ocProperties
            ?: throw IllegalStateException("This PDF has no layers")

        // Build a lookup map by the COSName key (what BDC/EMC will reference)
        val groupsByRef: Map<COSBase, PDOptionalContentGroup> =
            ocProps.optionalContentGroups
                .associateBy { it.cosObject }

        // Collector for all found elements
        val elements = mutableListOf<CanvasElement>()
        val engine = LayerImportEngine(groupsByRef) { elem ->
            elements.add(elem)
            Log.d(TAG, "loadTemplate: ${elem.type}")
        }

        // Process every page
        document.pages.forEach { page ->
            engine.processPage(page as PDPage)
        }

        document.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}