package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.databinding.FragmentBrushStyleGridBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrushStyleFragment : Fragment() {

    private var _binding: FragmentBrushStyleGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var styleAdapter: BrushStyleAdapter

    private val allBrushStyles = listOf(
        BrushStyle.ROUND_BRUSH,
        BrushStyle.PENCIL,
        BrushStyle.MARKER,
        BrushStyle.CALLIGRAPHY,
        BrushStyle.INK_PEN,
        BrushStyle.AIRBRUSH,
        BrushStyle.CHALK,
        BrushStyle.CHARCOAL,
        BrushStyle.WATERCOLOR,
        BrushStyle.TEXTURE,
        BrushStyle.FINE_LINER,
        BrushStyle.BRUSH_PEN,
        BrushStyle.FLAT_BRUSH,
        BrushStyle.SPLATTER,
        BrushStyle.GLITTER
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushStyleGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        styleAdapter = BrushStyleAdapter(allBrushStyles) { selectedStyle ->
            viewModel.setBrushStyle(selectedStyle)
            viewModel.enterDrawingMode(requireActivity())
        }

        binding.brushStylesRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.HORIZONTAL, false)
            adapter = styleAdapter
            post {
                styleAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentBrushStyle.observe(viewLifecycleOwner) { currentStyle ->
            currentStyle?.let {
                styleAdapter.selectedStyle = it
            }
        }
    }

    override fun onDestroyView() {
        _binding?.brushStylesRV?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BrushStyleFragment = BrushStyleFragment()
    }
}
