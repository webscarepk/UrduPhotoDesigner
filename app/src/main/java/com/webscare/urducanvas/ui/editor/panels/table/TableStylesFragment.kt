package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.repository.TablePresetRepository
import com.webscare.urducanvas.databinding.FragmentTableStylesBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem

class TableStylesFragment : Fragment() {

    private var _binding: FragmentTableStylesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val categories = mutableListOf<String>()
    private lateinit var pagerAdapter: TableStylesPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTableStylesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        categories.clear()
        categories.addAll(TablePresetRepository.categories)

        binding.collapsibleRail.bindPanelId("table_styles")

        val railItems = categories.map { catName ->
            RailCategoryItem(
                id = catName,
                label = catName,
                iconRes = null
            )
        }
        binding.collapsibleRail.setCategories(railItems)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = categories.indexOfFirst { it.equals(catItem.id, ignoreCase = true) }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TableStylesPagerAdapter(this, categories)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selectedCategory = categories.getOrNull(position) ?: return
                binding.collapsibleRail.setSelectedCategory(selectedCategory)
            }
        })

        if (categories.isNotEmpty()) {
            binding.collapsibleRail.setSelectedCategory(categories.first())
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableStylesFragment()
    }
}
