package com.webscare.urducanvas.ui.editor.panels.draw.brush

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.databinding.FragmentBrushStyleGridBinding
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * The Styles tab of the brush panel — one grid of brushes.
 *
 * Which shelf it shows comes from the panel's tab row: tapping the selected Style tab
 * drills into `[← Style] All Basic Ink …`, the same way the text panel drills into its
 * preset categories. A search query overrides the shelf, because a brush name does not
 * tell you which shelf it sits on.
 */
@AndroidEntryPoint
class BrushStyleFragment : Fragment() {

    private var _binding: FragmentBrushStyleGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var styleAdapter: BrushStyleAdapter

    private var query: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushStyleGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Selecting a brush is one of only two gestures that arm draw mode — the other is
        // the panel's Add brush button. Browsing the catalog leaves the canvas alone.
        styleAdapter = BrushStyleAdapter(BrushStyle.selectable) { selectedStyle ->
            viewModel.setBrushStyle(selectedStyle)
            viewModel.enterDrawingMode(requireActivity())
        }

        binding.brushStylesRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 3, GridLayoutManager.HORIZONTAL, false)
            adapter = styleAdapter
        }

        viewModel.currentBrushStyle.observe(viewLifecycleOwner) { styleAdapter.selectedStyle = it }
        viewModel.brushCategoryFilter.observe(viewLifecycleOwner) { rebind() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.searchQuery.collect {
                    query = it
                    rebind()
                }
            }
        }

        rebind()
    }

    private fun rebind() {
        val binding = _binding ?: return
        val searching = query.isNotBlank()
        val list = when {
            searching -> BrushStyle.search(query)
            else -> viewModel.brushCategoryFilter.value
                ?.let { BrushStyle.inCategory(it) }
                ?: BrushStyle.selectable
        }

        styleAdapter.updateStyles(list)
        binding.brushStylesRV.isVisible = list.isNotEmpty()
        binding.emptyLabel.isVisible = list.isEmpty()
        if (list.isEmpty()) {
            binding.emptyLabel.text = getString(R.string.no_brushes_match, query.trim())
        }
        binding.brushStylesRV.scrollToPosition(0)
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
