package com.example.urduphotodesigner.ui.navigation.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.databinding.FragmentPopularFontsListBinding
import com.example.urduphotodesigner.ui.navigation.home.FontsAdapter
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class PopularFontsListFragment : Fragment() {
    private var _binding: FragmentPopularFontsListBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val filtersViewModel: FiltersViewModel by activityViewModels()

    private lateinit var adapter: FontsAdapter
    private lateinit var sglm: StaggeredGridLayoutManager
    private var filterJob: Job? = null

    private var baseFonts: List<FontEntity> = emptyList()
    private var category: String = "All"

    companion object {
        private const val ARG_CATEGORY = "arg_category"

        fun newInstance(category: String): PopularFontsListFragment {
            return PopularFontsListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = arguments?.getString(ARG_CATEGORY) ?: "All"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPopularFontsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        observeData()
    }

    private fun setupRecycler() {
        adapter = FontsAdapter ({ font, isInstalled ->
            if (!isInstalled) {
                mainViewModel.downloadFont(font)
            }
        }, onDownload = {})

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

        binding.swipeRefresh.setColorSchemeResources(
            R.color.appColor, R.color.black, R.color.gray
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.fontsRV.stopScroll()
            binding.fontsRV.scrollToPosition(0)
            mainViewModel.fetchAndStoreFontsFromApi()
        }
    }

    private fun filterFonts(
        source: List<FontEntity>,
        category: String,
        query: String
    ): List<FontEntity> {
        val byCategory = if (category.equals("All", true)) source
        else source.filter { it.font_category.equals(category, true) }

        val q = query.trim().lowercase()
        return if (q.isBlank()) byCategory else byCategory.filter {
            it.font_name.lowercase().contains(q)
        }
    }

    private fun applyFilters(query: String) {
        filterJob?.cancel()
        filterJob = lifecycleScope.launch(Dispatchers.Default) {
            val filtered = filterFonts(baseFonts, category, query)
            val fresh = filtered.map { it.copy() } // defensive copy for DiffUtil

            withContext(Dispatchers.Main) {
                adapter.submitList(fresh)
                sglm.invalidateSpanAssignments()
                if (!binding.fontsRV.canScrollVertically(-1)) {
                    binding.fontsRV.scrollToPosition(0)
                }
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun observeData() {
        // observe localFonts
        lifecycleScope.launch {
            mainViewModel.localFonts.collectLatest { fonts ->
                baseFonts = fonts
                applyFilters(filtersViewModel.searchQuery.value)
            }
        }

        // observe search query from FiltersViewModel
        lifecycleScope.launch {
            filtersViewModel.searchQuery.collectLatest { query ->
                applyFilters(query)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}