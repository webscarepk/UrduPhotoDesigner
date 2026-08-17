package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.FragmentAdjustmentsBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AdjustmentsFragment : Fragment() {
    private var _binding: FragmentAdjustmentsBinding? = null
    private val binding get() = _binding!!
    private lateinit var tabs: ArrayList<AdjustmentPanelTabs>
    private lateinit var pagerAdapter: AdjustmentsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()

    private val adjustCategories = listOf(
        RailCategoryItem("light",  "Light",  R.drawable.ic_light,  isEnabled = false),
        RailCategoryItem("color",  "Color",  R.drawable.ic_color,  isEnabled = false),
        RailCategoryItem("detail", "Detail", R.drawable.ic_detail, isEnabled = false)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdjustmentsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRailAndPager()
        setupObservers()
    }

    private fun setupRailAndPager() {
        tabs = arrayListOf(
            AdjustmentPanelTabs(0, "Light", true),
            AdjustmentPanelTabs(1, "Color", false),
            AdjustmentPanelTabs(2, "Detail", false)
        )

        binding.collapsibleRail.bindPanelId("image_adjust")
        binding.collapsibleRail.setCategories(adjustCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = adjustCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0 && index < tabs.size) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.collapsibleRail.onCategoryToggleChangedListener = { catItem, isEnabled ->
            val featureName = when (catItem.id) {
                "light"  -> "Light"
                "color"  -> "Color"
                "detail" -> "Detail"
                else     -> catItem.label
            }
            if (isEnabled) {
                viewModel.enableFeature(featureName)
            } else {
                viewModel.disableFeature(featureName)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = AdjustmentsPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in adjustCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(adjustCategories[position].id)
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            val lightEnabled  = element.hasLight
            val colorEnabled  = element.hasColor
            val detailEnabled = element.hasDetail

            binding.collapsibleRail.setCategoryEnabled("light",  lightEnabled)
            binding.collapsibleRail.setCategoryEnabled("color",  colorEnabled)
            binding.collapsibleRail.setCategoryEnabled("detail", detailEnabled)
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): AdjustmentsFragment {
            return AdjustmentsFragment()
        }
    }
}