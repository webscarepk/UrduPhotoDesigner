package com.webscare.urducanvas.ui.editor.panels.adjustments

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.BitmapCache
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentAdjustmentsParentBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AdjustmentsParentFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentAdjustmentsParentBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private var tabs = mutableListOf<String>()
    private lateinit var adapter: EffectsPagerAdapter
    private var previewBitmap: Bitmap? = null
    private var elementId: String? = null
    private val viewModel: CanvasViewModel by activityViewModels()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            elementId = arguments?.getString("elementId")
            previewBitmap = BitmapCache.get(elementId ?: "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdjustmentsParentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = null

        val isMixedGroup = arguments?.getBoolean("isMixedGroup") ?: false
        val groupId = arguments?.getString("groupId")
        if (isMixedGroup) {
            binding.groupToggleContainer.visibility = View.VISIBLE
            val toggleAction = {
                val bundle = Bundle().apply {
                    putString("elementId", elementId)
                    putBoolean("isMixedGroup", true)
                    putString("groupId", groupId)
                }
                val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
                findNavController().navigate(R.id.textAdjustmentsFragment, bundle, navOptions)
            }
            binding.btnPrevGroupTab.addPressEffect { toggleAction() }
            binding.btnNextGroupTab.addPressEffect { toggleAction() }
        }

        viewModel.isProcessingAdjustments.observe(viewLifecycleOwner) { isProcessing ->
            binding.processingProgress.visibility = if (isProcessing == true) View.VISIBLE else View.GONE
        }

        setEvents()
    }

    private fun setEvents() {
        val selectedElement = elementId?.let { id ->
            viewModel.canvasElements.value?.find { it.id == id }
        }

        val isSvgElement = selectedElement?.svgDrawable != null
        val isDrawElement = selectedElement?.type == ElementType.DRAW
        val isGroupElement = selectedElement?.type == ElementType.GROUP

        // DRAW layers show Effects & Mask tabs only (no Adjust or Filters)
        // SVG elements and GROUP layers hide Mask tab (requires individual raster bitmap)
        tabs = when {
            isGroupElement -> mutableListOf("Effects", "Adjust", "Filters")
            isDrawElement -> mutableListOf("Effects", "Mask")
            isSvgElement -> mutableListOf("Effects", "Adjust", "Filters")
            else -> mutableListOf("Effects", "Adjust", "Filters", "Mask")
        }

        elementId?.let {
            adapter = EffectsPagerAdapter(
                // childFragmentManager — scoped to this Fragment's view, NOT the Activity.
                // Using requireActivity().supportFragmentManager causes FragmentMaxLifecycleEnforcer
                // to call commitNow() on the Activity FM while Activity.onStart is already
                // dispatching its own transaction → "FragmentManager already executing transactions".
                // viewLifecycleOwner.lifecycle — tied to the view lifetime, not the Fragment
                // instance lifetime, so the adapter is torn down with the view on back-stack.
                childFragmentManager,
                viewLifecycleOwner.lifecycle,
                tabs, it
            )
            viewModel.populateAdjustmentsFromElement(it)
            binding.viewPager.adapter = adapter
            adapter.stateRestorationPolicy =
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT

            setupTabLayout()
        }

        binding.viewPager.isUserInputEnabled = false
        binding.replaceImage.addPressEffect { pickImage.launch("image/*") }
        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun setupTabLayout() {
        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }
        mediator?.attach()

        binding.tabLayout.doOnLayout {
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
                    val selectedElement = elementId?.let { id ->
                        viewModel.canvasElements.value?.find { it.id == id }
                    }

                    if (selectedElement?.type == ElementType.SHAPE) {
                        viewModel.addImageInsideShape(ImageProcessor.filePathToBitmap(filePath!!)!!, requireActivity())
                    } else {
                        val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                        viewModel.replaceSticker(
                            ImageProcessor.filePathToBitmap(filePath!!),
                            requireActivity()
                        )
                    }
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