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
import com.webscare.urducanvas.databinding.FragmentAppearanceBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableAppearanceTabFragment : Fragment() {

    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: TableAppearancePagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val tableCategories = listOf(
        RailCategoryItem("fill",          "Fill",          R.drawable.ic_fill),
        RailCategoryItem("stroke",        "Stroke",        R.drawable.ic_stroke),
        RailCategoryItem("shadow",        "Shadow",        R.drawable.ic_shadow),
        RailCategoryItem("corner_radius", "Corner Radius", R.drawable.ic_corner)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppearanceBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRailAndPager()
        initObservers()
    }

    private fun setupRailAndPager() {
        tabs = ArrayList()

        binding.collapsibleRail.bindPanelId("table_appearance")
        binding.collapsibleRail.setCategories(tableCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = tableCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = TableAppearancePagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in tableCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(tableCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Fill", true))
                tabs.add(PanelTabs(1, "Stroke", false))
                tabs.add(PanelTabs(2, "Shadow", false))
                tabs.add(PanelTabs(3, "Corner Radius", false))

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
        fun newInstance() = TableAppearanceTabFragment()
    }
}
