package com.webscare.urducanvas.ui.navigation.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentPopularFontsBinding
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.text.equals
import kotlin.text.isNotEmpty

@AndroidEntryPoint
class PopularFontsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentPopularFontsBinding? = null
    private val binding get() = _binding!!
    private val handledFontIds = mutableSetOf<Int>()
    private var tabs = emptyList<String>()
    private val viewModel: com.webscare.urducanvas.viewmodels.FiltersViewModel by activityViewModels()
    private val mainViewModel: com.webscare.urducanvas.viewmodels.MainViewModel by activityViewModels()
    private val canvasViewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()
    private lateinit var pagerAdapter: PopularFontsPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPopularFontsBinding.inflate(inflater, container, false)
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

        lifecycleScope.launch {
            mainViewModel.localFonts.collect { allFonts ->
                val categories = buildList {
                    add("All")
                    addAll(
                        allFonts.map { it.font_category.trim() }
                            .filter { it.isNotEmpty() && !it.equals("Imported", true) }
                            .distinct()
                            .sorted()
                    )
                }

                tabs = categories
                if (!::pagerAdapter.isInitialized) {
                    setupTabsAndPager()
                } else {
                    pagerAdapter.updateTabs(categories)
                }
            }
        }
    }

    private fun setupTabsAndPager() {
        pagerAdapter = PopularFontsPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context)
                .inflate(R.layout.layout_custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        updateTabStyles(binding.tabLayout.selectedTabPosition)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabStyles(position)
            }
        })
    }

    private fun setEvents() {
        binding.listStyle.addPressEffect { viewModel.toggleGrid() }
        binding.searchBar.addTextChangedListener { text ->
            viewModel.setSearchQuery(text.toString())
        }
        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    fun updateTabStyles(selectedPosition: Int) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val tabView = binding.tabLayout.getTabAt(i)?.customView
            val root =
                tabView?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.tabRoot)
            val text = tabView?.findViewById<TextView>(R.id.tabTitle)

            if (i == selectedPosition) {
                root?.setCardBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.appColor
                    )
                )
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whiteText))
            } else {
                root?.setCardBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.contrast
                    )
                )
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.clearFilters()
        _binding = null
    }
}