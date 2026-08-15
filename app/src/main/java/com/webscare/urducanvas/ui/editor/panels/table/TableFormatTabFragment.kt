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
class TableFormatTabFragment : Fragment() {

    private var _binding: FragmentFormatBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: TableFormatPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val tableFormatCategories = listOf(
        RailCategoryItem("alignment", "Alignment", R.drawable.ic_center_align),
        RailCategoryItem("padding",   "Padding",   R.drawable.ic_spacing),
        RailCategoryItem("text",      "Text",      R.drawable.ic_all_caps),
        RailCategoryItem("spacing",   "Spacing",   R.drawable.ic_spacing)
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

        binding.collapsibleRail.bindPanelId("table_format")
        binding.collapsibleRail.setCategories(tableFormatCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = tableFormatCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TableFormatPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in tableFormatCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(tableFormatCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Alignment", true))
                tabs.add(PanelTabs(1, "Padding", false))
                tabs.add(PanelTabs(2, "Text", false))
                tabs.add(PanelTabs(3, "Spacing", false))

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
        fun newInstance() = TableFormatTabFragment()
    }
}
