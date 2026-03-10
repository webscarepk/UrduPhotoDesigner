package com.webscare.urducanvas.ui.editor.panels.shape

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.databinding.FragmentShapesParentBinding
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
        initObservers()
    }

    private fun initObservers() {
        viewModel.openAppearanceTab.observe(viewLifecycleOwner) {

            binding.viewPager.post {
                binding.viewPager.currentItem = 1
            }

        }
    }

    private fun setEvents() {
        tabs.clear()
        tabs.add(PanelTabs(0, "Shape", is_selected = true))
        tabs.add(PanelTabs(1, "Style", is_selected = false))

        adapter = ShapePagerAdapter(this, tabs)
        binding.viewPager.adapter = adapter

        binding.addImage.addPressEffect { pickImage.launch("image/*") }
        binding.editImage.addPressEffect {
            val selectedElement = viewModel.selectedElements.value?.firstOrNull()

            if (selectedElement != null) {
                // 2. Prepare Bundle with elementId
                val bundle = Bundle().apply {
                    putString("elementId", selectedElement.id)
                }

                // 3. Put bitmap in cache
                selectedElement.bitmap?.let { bmp ->
                    com.webscare.urducanvas.common.utils.BitmapCache.put(selectedElement.id, bmp)
                }

                // 4. Navigate with NavOptions
                val navOptions =
                    androidx.navigation.NavOptions.Builder().setLaunchSingleTop(true).build()

                findNavController().navigate(
                    R.id.adjustmentsParentFragment, bundle, navOptions
                )
            } else {
                Snackbar.make(requireView(), "Please select a shape first", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
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
                val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }

                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        viewModel.addImageInsideShape(bitmap, requireActivity())
                    }
                } else {
                    Toast.makeText(
                        requireContext(), "Please select a shape first", Toast.LENGTH_SHORT
                    ).show()
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
        viewModel.closeAppearanceTab()
        _binding = null
    }
}