package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.databinding.FragmentEffectsBinding
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EffectsFragment : Fragment() {
    private var _binding: FragmentEffectsBinding? = null
    private val binding get() = _binding!!
    private var tabs = ArrayList<AdjustmentPanelTabs>()
    private lateinit var pagerAdapter: EffectsPagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()

    private val effectsCategories = listOf(
        RailCategoryItem("shadow",  "Shadow",  R.drawable.ic_shadow,  isEnabled = false),
        RailCategoryItem("color",   "Color",   R.drawable.ic_color,   isEnabled = false),
        RailCategoryItem("blur",    "Blur",    R.drawable.ic_blur,    isEnabled = false),
        RailCategoryItem("stroke",  "Stroke",  R.drawable.ic_stroke,  isEnabled = false),
        RailCategoryItem("feather", "Feather", R.drawable.ic_feather, isEnabled = false)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEffectsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupData()
        setupRailAndPager()
        initObservers()
    }

    private fun setupData() {
        tabs.clear()
        tabs.add(AdjustmentPanelTabs(0, "Shadow", is_selected = true))
        tabs.add(AdjustmentPanelTabs(1, "Color", is_selected = false))
        tabs.add(AdjustmentPanelTabs(2, "Blur", is_selected = false))
        tabs.add(AdjustmentPanelTabs(3, "Stroke", is_selected = false))
        tabs.add(AdjustmentPanelTabs(4, "Feather", is_selected = false))
    }

    private fun setupRailAndPager() {
        binding.collapsibleRail.bindPanelId("image_effects")
        binding.collapsibleRail.setCategories(effectsCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = effectsCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0 && positionInTabs(index)) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.collapsibleRail.onCategoryToggleChangedListener = { catItem, isEnabled ->
            val featureName = when (catItem.id) {
                "shadow"  -> "Shadow"
                "color"   -> "Overlay"
                "blur"    -> "Blur"
                "stroke"  -> "Stroke"
                "feather" -> "Feather"
                else      -> catItem.label
            }
            if (isEnabled) {
                viewModel.enableFeature(featureName)
            } else {
                viewModel.disableFeature(featureName)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = EffectsPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false
    }

    private fun positionInTabs(index: Int): Boolean {
        return index in 0 until tabs.size
    }

    private fun initObservers() {
        viewModel.selectedElements.observe(viewLifecycleOwner) { elements ->
            val element = elements.firstOrNull() ?: return@observe

            val shadowEnabled  = element.hasShadow
            val colorEnabled   = element.hasOverlay
            val blurEnabled    = element.hasBlur
            val strokeEnabled  = element.hasStroke
            val featherEnabled = element.hasFeather

            binding.collapsibleRail.setCategoryEnabled("shadow",  shadowEnabled)
            binding.collapsibleRail.setCategoryEnabled("color",   colorEnabled)
            binding.collapsibleRail.setCategoryEnabled("blur",    blurEnabled)
            binding.collapsibleRail.setCategoryEnabled("stroke",  strokeEnabled)
            binding.collapsibleRail.setCategoryEnabled("feather", featherEnabled)
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): EffectsFragment {
            return EffectsFragment()
        }
    }
}