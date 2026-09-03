package com.webscare.urducanvas.ui.editor.panels.text.threed

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
import com.webscare.urducanvas.databinding.FragmentText3dBinding
import com.webscare.urducanvas.ui.editor.panels.text.threed.adapters.Text3DPagerAdapter
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Text3DFragment : Fragment() {

    private var _binding: FragmentText3dBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: Text3DPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val threedCategories = listOf(
        RailCategoryItem("presets", "Presets", R.drawable.ic_magic_wand),
        RailCategoryItem("transform", "Transform", R.drawable.ic_transform),
        RailCategoryItem("extrusion", "Extrusion", R.drawable.ic_layer),
        RailCategoryItem("material", "Material", R.drawable.ic_fill),
        RailCategoryItem("lighting", "Lighting", R.drawable.ic_sun),
        RailCategoryItem("shadow", "Shadow", R.drawable.ic_shadow)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentText3dBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRailAndPager()
        initObservers()
    }

    private fun setupRailAndPager() {
        tabs = ArrayList()

        binding.collapsibleRail.bindPanelId("text_3d")
        binding.collapsibleRail.setCategories(threedCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = threedCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = Text3DPagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in threedCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(threedCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Presets", true))
                tabs.add(PanelTabs(1, "Transform", false))
                tabs.add(PanelTabs(2, "Extrusion", false))
                tabs.add(PanelTabs(3, "Material", false))
                tabs.add(PanelTabs(4, "Lighting", false))
                tabs.add(PanelTabs(5, "Shadow", false))

                pagerAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Text3DFragment()
    }
}
