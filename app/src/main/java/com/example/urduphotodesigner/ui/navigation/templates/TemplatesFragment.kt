package com.example.urduphotodesigner.ui.navigation.templates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentTemplatesBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplatesFragment : Fragment() {
    private var _binding: FragmentTemplatesBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    private var tabs: List<String> = emptyList()
    private var mediator: TabLayoutMediator? = null
    private var pagerAdapter: TemplatesPagerAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTemplatesBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPager(emptyList())   // init with no tabs; will update after first collect
        observeTemplateCategories()
        setEvents()
    }

    private fun setEvents() {
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabStyles(position)
            }
        })

        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun observeTemplateCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localTemplates.collect { templates ->
                // Build distinct categories from DB items
                val distinct = templates.map { it.category }.filter { !it.isNullOrBlank() }.distinct()
                val newTabs = listOf("All") + distinct

                if (newTabs != tabs) {
                    tabs = newTabs
                    setupPager(tabs)
                    updateTabStyles(binding.tabLayout.selectedTabPosition)
                }
            }
        }
    }

    private fun setupPager(tabTitles: List<String>) {
        // Clean up existing mediator to avoid duplicates
        mediator?.detach()

        // (Re)create adapter with latest tabs
        pagerAdapter = TemplatesPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabTitles
        )
        binding.viewPager.adapter = pagerAdapter

        mediator = TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.layout_tabs_bar_item, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabTitles.getOrNull(position) ?: ""
            tab.customView = tabView
        }.also { it.attach() }
    }

    fun updateTabStyles(selectedPosition: Int) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val tabView = binding.tabLayout.getTabAt(i)?.customView
            val root = tabView?.findViewById<ConstraintLayout>(R.id.tabRoot)
            val text = tabView?.findViewById<TextView>(R.id.tabTitle)

            if (i == selectedPosition) {
                root?.background = ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.button_bg_stroke_fill_dark
                )
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whiteText))
            } else {
                root?.background = ContextCompat.getDrawable(
                    requireActivity(),
                    R.drawable.button_bg_round_stroke_fill
                )
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}