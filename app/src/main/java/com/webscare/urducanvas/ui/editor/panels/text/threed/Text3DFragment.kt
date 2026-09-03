package com.webscare.urducanvas.ui.editor.panels.text.threed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentText3dBinding
import com.webscare.urducanvas.ui.editor.panels.text.threed.adapters.Text3DPagerAdapter
import com.webscare.urducanvas.ui.editor.views.RailCategoryItem
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class Text3DFragment : Fragment() {

    private var _binding: FragmentText3dBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()

    // isEnabled is non-null on every row so the rail renders its on/off dot and a tap on the
    // already-selected row flips it — the same enable/disable affordance the image adjustment
    // panel uses. This replaces the per-subpanel toggle switches.
    private val threedCategories = listOf(
        RailCategoryItem("presets", "Presets", R.drawable.ic_magic_wand, isEnabled = true),
        RailCategoryItem("transform", "Basic 3D", R.drawable.ic_3d_cube, isEnabled = true),
        RailCategoryItem("extrusion", "Extrusion", R.drawable.ic_layer, isEnabled = true),
        RailCategoryItem("material", "Material", R.drawable.ic_fill, isEnabled = true),
        RailCategoryItem("lighting", "Lighting", R.drawable.ic_sun, isEnabled = true)
    )

    private lateinit var pagerAdapter: Text3DPagerAdapter
    private lateinit var tabs: ArrayList<PanelTabs>

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
        tabs = arrayListOf(
            PanelTabs(0, "Presets", true),
            PanelTabs(1, "Basic 3D", false),
            PanelTabs(2, "Extrusion", false),
            PanelTabs(3, "Material", false),
            PanelTabs(4, "Lighting", false)
        )

        binding.collapsibleRail.bindPanelId("text_3d")
        binding.collapsibleRail.setCategories(threedCategories)

        binding.collapsibleRail.onCategorySelectedListener = { catItem ->
            val index = threedCategories.indexOfFirst { it.id == catItem.id }
            if (index >= 0) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }

        binding.collapsibleRail.onCategoryToggleChangedListener = { catItem, isEnabled ->
            viewModel.updateText3D(pushToUndo = true) { data ->
                when (catItem.id) {
                    "presets" -> data.enabled = isEnabled
                    "transform" -> data.rotation.enabled = isEnabled
                    "extrusion" -> data.extrusion.enabled = isEnabled
                    "material" -> data.material.enabled = isEnabled
                    "lighting" -> data.lighting.enabled = isEnabled
                }
            }
        }

        binding.viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        binding.viewPager.offscreenPageLimit = 4

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
        viewModel.pagingLocked.observe(viewLifecycleOwner) { lock ->
            binding.viewPager.isUserInputEnabled = !lock
        }

        // Keep the rail dots in sync with the model — presets, undo/redo and element
        // reselection all change these flags without going through the rail.
        viewModel.text3dData.observe(viewLifecycleOwner) { data ->
            val rail = _binding?.collapsibleRail ?: return@observe
            rail.setCategoryEnabled("presets", data?.enabled ?: false)
            rail.setCategoryEnabled("transform", data?.rotation?.enabled ?: false)
            rail.setCategoryEnabled("extrusion", data?.extrusion?.enabled ?: false)
            rail.setCategoryEnabled("material", data?.material?.enabled ?: false)
            rail.setCategoryEnabled("lighting", data?.lighting?.enabled ?: false)
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance() = Text3DFragment()
    }
}
