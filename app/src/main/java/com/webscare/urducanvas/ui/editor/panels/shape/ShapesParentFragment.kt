package com.webscare.urducanvas.ui.editor.panels.shape

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentAdjustmentsParentBinding
import com.webscare.urducanvas.databinding.FragmentShapesParentBinding
import com.webscare.urducanvas.ui.editor.panels.adjustments.EffectsPagerAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ShapesParentFragment : Fragment() {
    private var _binding: FragmentShapesParentBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private var tabs = mutableListOf<PanelTabs>()
    private lateinit var adapter: ShapePagerAdapter

    private val viewModel: CanvasViewModel by activityViewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShapesParentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = null

        setEvents()
        setupTabLayout()
    }

    private fun setEvents() {
        tabs.clear()
        tabs.add(PanelTabs(0, "Shape", is_selected = true))
        tabs.add(PanelTabs(1, "Style", is_selected = false))
        tabs.add(PanelTabs(2, "Color", is_selected = false))

        adapter = ShapePagerAdapter(this, tabs)
        binding.viewPager.adapter = adapter

        binding.editImage.addPressEffect { findNavController().navigate(R.id.adjustmentsParentFragment) }
    }

    private fun setupTabLayout() {
        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position].tab_name
            tab.customView = tabView
        }
        mediator?.attach()

        binding.tabLayout.viewTreeObserver.addOnGlobalLayoutListener {
            if (isAdded && _binding != null) {
                for (i in 0 until binding.tabLayout.tabCount) {
                    val tabView = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)
                    tabView?.scaleX = 0.9f
                    tabView?.scaleY = 0.9f
                }

                // Make the first tab look selected initially
                binding.tabLayout.getTabAt(binding.tabLayout.selectedTabPosition)?.view?.apply {
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator())?.start()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(150)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath =
                    ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath

                withContext(Dispatchers.Main) {
                    viewModel.replaceSticker(
                        ImageProcessor.filePathToBitmap(filePath!!), requireActivity()
                    )
                }
            } catch (e: Exception) {
                Log.e("ImagesFragment", "Failed to import image", e)
            }
        }
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        binding.viewPager.adapter = null
        super.onDestroyView()
        _binding = null
    }
}