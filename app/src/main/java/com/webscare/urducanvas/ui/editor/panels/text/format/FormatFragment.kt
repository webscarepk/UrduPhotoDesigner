package com.webscare.urducanvas.ui.editor.panels.text.format

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentFormatBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FormatFragment : Fragment() {
    private var _binding: FragmentFormatBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: FormatPagerAdapter

    private val formatCategories = listOf(
        RailCategoryItem("spacing",    "Spacing",    R.drawable.ic_spacing),
        RailCategoryItem("casing",     "Casing",     R.drawable.ic_all_caps),
        RailCategoryItem("decoration", "Decoration", R.drawable.ic_under_line),
        RailCategoryItem("alignment",  "Alignment",  R.drawable.ic_center_align)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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

        binding.collapsibleRail.bindPanelId("text_format")
        binding.collapsibleRail.setCategories(formatCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = formatCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = FormatPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in formatCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(formatCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Spacing",    true))
                tabs.add(PanelTabs(1, "Casing",     false))
                tabs.add(PanelTabs(2, "Decoration", false))
                tabs.add(PanelTabs(3, "Alignment",  false))

                pagerAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FormatFragment {
            return FormatFragment()
        }
    }
}