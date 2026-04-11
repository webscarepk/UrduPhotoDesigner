package com.webscare.urducanvas.ui.navigation.files

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.common.views.CanvasView
import com.webscare.urducanvas.databinding.FragmentFilesBinding
import com.webscare.urducanvas.viewmodels.FiltersViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.getValue

@AndroidEntryPoint
class FilesFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private var tabs = emptyList<String>()
    private val viewModel: com.webscare.urducanvas.viewmodels.FiltersViewModel by activityViewModels()
    private val canvasViewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {
        lifecycleScope.launch {
            viewModel.isGrid.collect { isGrid ->
                if (isGrid) {
                    binding.listStyle.setImageResource(R.drawable.ic_list_view)
                } else {
                    binding.listStyle.setImageResource(R.drawable.ic_grid_view)
                }
            }
        }
    }

    private fun setEvents() {
        tabs = listOf("All", "Projects", "Fonts", "Stickers", "Backgrounds")

        val adapter = FilesPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )
        binding.viewPager.adapter = adapter

        val targetPage = arguments?.getInt("targetPage", 0) ?: 0
        binding.viewPager.setCurrentItem(targetPage, false)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.layout_custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        // Initial style
        updateTabStyles(binding.tabLayout.selectedTabPosition)

        // Apply styles on swipe
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabStyles(position)
            }
        })

        binding.listStyle.addPressEffect {
            viewModel.toggleGrid()
        }

        // search
        binding.searchBar.addTextChangedListener { text ->
            viewModel.setSearchQuery(text.toString())
        }
    }

    fun updateTabStyles(selectedPosition: Int) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val tabView = binding.tabLayout.getTabAt(i)?.customView
            val root = tabView?.findViewById<MaterialCardView>(R.id.tabRoot)
            val text = tabView?.findViewById<TextView>(R.id.tabTitle)

            if (i == selectedPosition) {
                root?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.appColor))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whiteText))
            } else {
                root?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.contrast))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            canvasViewModel.clearCanvas()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearFilters()
    }
}