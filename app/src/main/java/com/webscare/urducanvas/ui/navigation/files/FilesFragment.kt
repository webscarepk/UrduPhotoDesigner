package com.webscare.urducanvas.ui.navigation.files

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentFilesBinding
import com.webscare.urducanvas.viewmodels.FiltersViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilesFragment : Fragment() {
    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!
    private var tabs = emptyList<String>()
    private val viewModel: FiltersViewModel by activityViewModels()
    private val canvasViewModel: CanvasViewModel by activityViewModels()

    // Track whether we are currently in multi-select mode so we can
    // restore the right button when the tab changes mid-selection.
    private var isSelectionActive = false

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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isGrid.collect { isGrid ->
                    if (isGrid) {
                        binding.listStyle.setImageResource(R.drawable.ic_list_view)
                    } else {
                        binding.listStyle.setImageResource(R.drawable.ic_grid_view)
                    }
                }
            }
        }
    }

    private fun setEvents() {
        tabs = listOf("All", "Projects", "Fonts", "Stickers", "Backgrounds")

        val adapter = FilesPagerAdapter(
            childFragmentManager,
            viewLifecycleOwner.lifecycle,
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

        // Initial state
        val initialTab = binding.tabLayout.selectedTabPosition
        updateTabStyles(initialTab)
        refreshToolbarButtons(initialTab)

        // Update on swipe
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabStyles(position)
                refreshToolbarButtons(position)
            }
        })

        binding.back.addPressEffect {
            findNavController().navigateUp()
        }

        binding.listStyle.addPressEffect {
            viewModel.toggleGrid()
        }

        // Import button → delegate to the current child fragment's launcher
        binding.importBtn.addPressEffect {
            getCurrentFilesListFragment()?.triggerImport()
        }

        // Delete All button → delegate to the current child fragment's delete logic
        binding.deleteAllBtn.addPressEffect {
            getCurrentFilesListFragment()?.triggerDeleteSelected()
        }

        binding.searchBar.addTextChangedListener { text ->
            viewModel.setSearchQuery(text.toString())
        }
    }

    // ─── Toolbar button orchestration ─────────────────────────────────────────

    /**
     * Called by [FilesListFragment] whenever multi-select mode changes.
     * Swaps Import ↔ Delete All in the toolbar.
     */
    fun onSelectionModeChanged(active: Boolean) {
        isSelectionActive = active
        refreshToolbarButtons(binding.viewPager.currentItem)
    }

    /**
     * Decide which toolbar button to show based on the current tab and
     * whether multi-select is active.
     *
     * Rules:
     *  • Selection active  → show Delete All, hide Import (regardless of tab)
     *  • "All" tab, no selection → hide both
     *  • Any other tab, no selection → show Import with contextual label
     */
    private fun refreshToolbarButtons(position: Int) {
        val tabName = tabs.getOrNull(position) ?: return

        if (isSelectionActive) {
            binding.importBtn.visibility    = View.GONE
            binding.deleteAllBtn.visibility = View.VISIBLE
        } else {
            binding.deleteAllBtn.visibility = View.GONE
            if (tabName == "All") {
                binding.importBtn.visibility = View.GONE
            } else {
                binding.importBtn.visibility = View.VISIBLE
                binding.importBtn.text = when (tabName) {
                    "Projects"    -> "Import Project"
                    "Fonts"       -> "Import Font"
                    "Stickers"    -> "Import Sticker"
                    "Backgrounds" -> "Import Background"
                    else          -> "Import"
                }
            }
        }
    }

    // ─── Tab styling ──────────────────────────────────────────────────────────

    fun updateTabStyles(selectedPosition: Int) {
        for (i in 0 until binding.tabLayout.tabCount) {
            val tabView = binding.tabLayout.getTabAt(i)?.customView
            val root    = tabView?.findViewById<MaterialCardView>(R.id.tabRoot)
            val text    = tabView?.findViewById<TextView>(R.id.tabTitle)

            if (i == selectedPosition) {
                root?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.appColor))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.whiteText))
            } else {
                root?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.contrast))
                text?.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
            }
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /** Returns the [FilesListFragment] currently visible in the ViewPager2. */
    private fun getCurrentFilesListFragment(): FilesListFragment? {
        val currentItem = binding.viewPager.currentItem
        return childFragmentManager.fragments
            .filterIsInstance<FilesListFragment>()
            .firstOrNull { it.arguments?.getString("TAB_NAME") == tabs.getOrNull(currentItem) }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            canvasViewModel.clearCanvas()
        }
    }

    override fun onDestroyView() {
        _binding?.viewPager?.adapter = null
        super.onDestroyView()
        _binding = null
        viewModel.clearFilters()
    }
}