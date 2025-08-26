package com.example.urduphotodesigner.ui.navigation.fonts

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentPopularFontsListBinding
import com.example.urduphotodesigner.ui.navigation.home.FontsAdapter
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PopularFontsListFragment : Fragment() {
    private var _binding: FragmentPopularFontsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: FontsAdapter
    private lateinit var sglm: StaggeredGridLayoutManager
    private var filterJob: Job? = null

    private var baseFonts: List<FontEntity> = emptyList()
    private var activeSubcategory: String = "All"
    private var activeQuery: String = ""
    private var suppressChipClicks = false
    private var filterPanelVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPopularFontsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        setupRecycler()
        observeData()
    }

    private fun setEvents() {
        binding.searchBar.doAfterTextChanged {
            activeQuery = it?.toString().orEmpty()
            applyFiltersList()
        }

        binding.filters.addPressEffect { toggleFilterPanel() }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.appColor, R.color.black, R.color.gray
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.fontsRV.stopScroll()
            binding.fontsRV.scrollToPosition(0)
            mainViewModel.fetchAndStoreFontsFromApi()
        }

        binding.back.addPressEffect { findNavController().navigateUp() }
    }

    private fun toggleFilterPanel() {
        val panel = binding.subCategoryChips
        val bar = binding.searchBar

        if (!filterPanelVisible) {
            binding.swipeRefresh.isEnabled = false
            panel.isVisible = true
            panel.doOnPreDraw {
                bar.bringToFront()
                bar.elevation = resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
                panel.elevation = resources.getDimension(com.intuit.sdp.R.dimen._1sdp)

                panel.translationY = -panel.height.toFloat()
                panel.alpha = 0f
                panel.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
        } else {
            panel.animate()
                .translationY(-panel.height.toFloat())
                .alpha(0f)
                .setDuration(180)
                .withEndAction { panel.isGone = true }
                .start()
        }
        binding.swipeRefresh.isEnabled = true
        filterPanelVisible = !filterPanelVisible
    }

    private fun setupRecycler() {
        binding.title.text = "Fonts"

//        adapter = FontsAdapter { font, isInstalled ->
//            if (isInstalled) {
//                // maybe preview or show detail
//            } else {
//                // trigger download
//                mainViewModel.downloadFont(font)
//            }
//        }

        sglm = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            isItemPrefetchEnabled = true
        }

        binding.fontsRV.apply {
            layoutManager = sglm
            adapter = this@PopularFontsListFragment.adapter
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            itemAnimator = null
            setItemViewCacheSize(24)
        }

        binding.fontsRV.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                binding.swipeRefresh.isEnabled = !rv.canScrollVertically(-1) && !filterPanelVisible
            }
        })
    }

    private fun filterFontsList(
        source: List<FontEntity>,
        subcategory: String,
        query: String
    ): List<FontEntity> {
        val bySub = if (subcategory.equals("All", true)) source
        else source.filter { it.font_category.equals(subcategory, true) }

        val q = query.trim().lowercase()
        return if (q.isBlank()) bySub else bySub.filter {
            it.font_name.lowercase().contains(q)
        }
    }

    private fun applyFiltersList() {
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val filtered = filterFontsList(baseFonts, activeSubcategory, activeQuery)
            val fresh = filtered.map { it.copy() }

            withContext(Dispatchers.Main) {
                adapter.submitList(fresh)
                sglm.invalidateSpanAssignments()
                if (!binding.fontsRV.canScrollVertically(-1)) {
                    binding.fontsRV.scrollToPosition(0)
                }
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.localFonts.collect { all ->
                baseFonts = all

                val subcats = buildList {
                    add("All")
                    addAll(
                        baseFonts
                            .map { it.font_category.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                    )
                }

                val cg = binding.subCategoryChips
                val current = (0 until cg.childCount)
                    .mapNotNull { (cg.getChildAt(it) as? com.google.android.material.chip.Chip)?.text?.toString() }
                if (current != subcats) renderSubcategoryChips(subcats)

                applyFiltersList()
            }
        }
    }

    private fun renderSubcategoryChips(subcats: List<String>) {
        val cg = binding.subCategoryChips
        cg.isSingleSelection = true
        cg.isSelectionRequired = false
        cg.removeAllViews()

        val selected = activeSubcategory
        subcats.forEach { label ->
            val chip = layoutInflater.inflate(
                R.layout.chip_filter_item, cg, false
            ) as com.google.android.material.chip.Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.isCheckable = true
            chip.isChecked = label.equals(selected, true)
            cg.addView(chip)

            chip.addPressEffect {
                if (suppressChipClicks) return@addPressEffect
                val clickedText = chip.text.toString()

                suppressChipClicks = true
                cg.clearCheck()
                chip.isChecked = true
                suppressChipClicks = false
                activeSubcategory = clickedText

                applyFiltersList()
                if (filterPanelVisible) toggleFilterPanel()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}