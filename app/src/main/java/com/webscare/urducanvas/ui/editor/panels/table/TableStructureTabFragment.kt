package com.webscare.urducanvas.ui.editor.panels.table

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentFormatBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableStructureTabFragment : Fragment() {

    private var _binding: FragmentFormatBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: TableStructurePagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val tableStructureCategories = listOf(
        RailCategoryItem("grid",      "Grid",      R.drawable.ic_stroke),
        RailCategoryItem("header",    "Header",    R.drawable.ic_fill),
        RailCategoryItem("direction", "Direction", R.drawable.ic_spacing),
        RailCategoryItem("wrap",      "Wrap",      R.drawable.ic_all_caps)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormatBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRailAndPager()
        initObservers()
    }

    private fun setupRailAndPager() {
        tabs = ArrayList()

        binding.collapsibleRail.bindPanelId("table_structure")
        binding.collapsibleRail.setCategories(tableStructureCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = tableStructureCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TableStructurePagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in tableStructureCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(tableStructureCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Grid", true))
                tabs.add(PanelTabs(1, "Header", false))
                tabs.add(PanelTabs(2, "Direction", false))
                tabs.add(PanelTabs(3, "Wrap", false))

                pagerAdapter.notifyDataSetChanged()
            }
        }

        viewModel.pagingLocked.observe(viewLifecycleOwner) { lock ->
            binding.viewPager.isUserInputEnabled = !lock
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = TableStructureTabFragment()
    }
}
