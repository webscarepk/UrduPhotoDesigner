package com.webscare.urducanvas.ui.navigation.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFilterBottomSheetBinding
import com.webscare.urducanvas.ui.creation.CanvasSizeAdapter
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentFilterBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var sizeAdapter: CanvasSizeAdapter

    private var chipSectionTitle: String = "Categories"
    private var chipsList: List<String> = emptyList()
    private var selectedChip: String = "All"
    private var selectedSize: CanvasSize? = null
    private var availableSizes: List<CanvasSize> = emptyList()

    var onFilterApplied: ((size: CanvasSize?, chipSelection: String) -> Unit)? = null
    var onFilterCleared: (() -> Unit)? = null

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            chipSectionTitle = args.getString(ARG_CHIP_TITLE, "Categories")
            chipsList = args.getStringArrayList(ARG_CHIPS) ?: emptyList()
            selectedChip = args.getString(ARG_SELECTED_CHIP, "All")
            val sizeName = args.getString(ARG_SELECTED_SIZE_NAME)
            if (!sizeName.isNullOrBlank()) {
                selectedSize = CanvasSize(id = 0, name = sizeName, width = 0f, height = 0f)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSizesAdapter()
        setupChips()
        setEvents()
        observeSizes()
    }

    private fun setupSizesAdapter() {
        sizeAdapter = CanvasSizeAdapter(
            items = emptyList(),
            onClick = { clicked ->
                selectedSize = if (selectedSize?.name == clicked.name) {
                    null
                } else {
                    clicked
                }
                sizeAdapter.selectedSizeName = selectedSize?.name ?: ""
                sizeAdapter.notifyDataSetChanged()
            },
            useNormalLayout = false
        )
        selectedSize?.name?.let { sizeAdapter.selectedSizeName = it }
        binding.sizesRV.adapter = sizeAdapter
    }

    private fun setupChips() {
        binding.chipSectionTitle.text = chipSectionTitle
        val cg = binding.categoryChipGroup
        cg.removeAllViews()

        val fullList = if (chipsList.none { it.equals("All", true) }) {
            listOf("All") + chipsList
        } else {
            chipsList
        }

        fullList.forEach { label ->
            val chip = layoutInflater.inflate(R.layout.chip_filter_item, cg, false) as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = label.equals(selectedChip, true)
            cg.addView(chip)

            chip.addPressEffect {
                selectedChip = chip.text.toString()
                cg.clearCheck()
                chip.isChecked = true
            }
        }
    }

    private fun setEvents() {
        binding.closeBtn.addPressEffect {
            dismiss()
        }

        binding.resetBtn.addPressEffect {
            selectedSize = null
            selectedChip = "All"
            sizeAdapter.selectedSizeName = ""
            sizeAdapter.notifyDataSetChanged()

            val cg = binding.categoryChipGroup
            cg.clearCheck()
            for (i in 0 until cg.childCount) {
                val chip = cg.getChildAt(i) as? Chip
                if (chip?.text?.toString()?.equals("All", true) == true) {
                    chip.isChecked = true
                    break
                }
            }

            onFilterCleared?.invoke()
        }

        binding.applyBtn.addPressEffect {
            // Find actual matching CanvasSize object if available
            val finalSize = selectedSize?.name?.let { name ->
                availableSizes.firstOrNull { it.name.equals(name, true) } ?: selectedSize
            }
            onFilterApplied?.invoke(finalSize, selectedChip)
            dismiss()
        }
    }

    private fun observeSizes() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localCanvasSizes.collect { entities ->
                if (entities.isEmpty()) return@collect
                availableSizes = entities.map {
                    CanvasSize(id = it.id, name = it.name, width = it.width, height = it.height)
                }
                sizeAdapter.submitList(availableSizes)
                selectedSize?.name?.let { selectedName ->
                    val matching = availableSizes.firstOrNull { it.name.equals(selectedName, true) }
                    if (matching != null) {
                        selectedSize = matching
                        sizeAdapter.selectedSizeName = matching.name
                        sizeAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // The sheet's own root paints bottom_sheet_bg, so the container behind it
        // must stay transparent or its square corners show through the rounded top.
        bottomSheet.setBackgroundResource(android.R.color.transparent)

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.apply {
            isFitToContents = true
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FilterBottomSheet"
        private const val ARG_CHIP_TITLE = "chip_title"
        private const val ARG_CHIPS = "chips"
        private const val ARG_SELECTED_CHIP = "selected_chip"
        private const val ARG_SELECTED_SIZE_NAME = "selected_size_name"

        fun newInstance(
            chipTitle: String = "Categories",
            chips: List<String> = emptyList(),
            selectedChip: String = "All",
            selectedSizeName: String? = null
        ): FilterBottomSheetFragment {
            return FilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHIP_TITLE, chipTitle)
                    putStringArrayList(ARG_CHIPS, ArrayList(chips))
                    putString(ARG_SELECTED_CHIP, selectedChip)
                    putString(ARG_SELECTED_SIZE_NAME, selectedSizeName ?: "")
                }
            }
        }
    }
}
