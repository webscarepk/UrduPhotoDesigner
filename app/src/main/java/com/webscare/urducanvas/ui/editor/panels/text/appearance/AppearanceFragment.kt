package com.webscare.urducanvas.ui.editor.panels.text.appearance

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
import com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters.AppearancePagerAdapter
import com.webscare.urducanvas.ui.editor.views.CollapsibleRailView
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppearanceFragment : Fragment() {
    private var _binding: FragmentAppearanceBinding? = null
    private val binding get() = _binding!!

    private lateinit var tabs: ArrayList<PanelTabs>
    private lateinit var pagerAdapter: AppearancePagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val appearanceCategories = listOf(
        RailCategoryItem("kasheeda", "Kasheeda", R.drawable.ic_kasheeda),
        RailCategoryItem("fill",     "Fill",     R.drawable.ic_fill),
        RailCategoryItem("stroke",   "Stroke",   R.drawable.ic_stroke),
        RailCategoryItem("shadow",   "Shadow",   R.drawable.ic_shadow),
        RailCategoryItem("label",    "Label",    R.drawable.ic_label),
        RailCategoryItem("effect",   "Effect",   R.drawable.ic_effect)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
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
        
        binding.collapsibleRail.bindPanelId("text_appearance")
        binding.collapsibleRail.setCategories(appearanceCategories)
        
        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = appearanceCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 1

        pagerAdapter = AppearancePagerAdapter(this, tabs)
        binding.viewPager.adapter = pagerAdapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in appearanceCategories.indices) {
                    binding.collapsibleRail.setSelectedCategory(appearanceCategories[position].id)
                }
            }
        })
    }

    private fun initObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tabs.clear()
                tabs.add(PanelTabs(0, "Kasheeda", true))
                tabs.add(PanelTabs(1, "Fill",     false))
                tabs.add(PanelTabs(2, "Stroke",   false))
                tabs.add(PanelTabs(3, "Shadow",   false))
                tabs.add(PanelTabs(4, "Label",    false))
                tabs.add(PanelTabs(5, "Effect",   false))

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
        fun newInstance(): AppearanceFragment {
            return AppearanceFragment()
        }
    }
}