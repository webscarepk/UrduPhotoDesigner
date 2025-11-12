package com.example.urduphotodesigner.ui.navigation.fonts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.sealed.FontDownloadState
import com.example.urduphotodesigner.common.utils.showGlobalSuccessSnack
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.databinding.FragmentPopularFontsListBinding
import com.example.urduphotodesigner.viewmodels.FiltersViewModel
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.snackbar.Snackbar
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
    private var shuffleAfterRefresh = false

    private val mainViewModel: MainViewModel by activityViewModels()
    private val filtersViewModel: FiltersViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()

    private lateinit var adapter: PopularFontsAdapter
    private lateinit var sglm: StaggeredGridLayoutManager
    private var filterJob: Job? = null

    private var baseFonts: List<FontEntity> = emptyList()
    private var category: String = "All"
    private var bundle: Bundle = Bundle()
    val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()

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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        adapter = PopularFontsAdapter({ font, isInstalled ->
            if (!isInstalled) {
                mainViewModel.downloadFont(font)
            } else {
                viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
                viewModel.addTextWithFont(
                    requireActivity().getString(R.string.dummyText), font, requireActivity()
                )
                view?.post { findNavController().navigate(R.id.editorFragment, bundle, navOptions) }
            }
        }, onDownload = {
            mainViewModel.downloadFont(it)
        })

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
            shuffleAfterRefresh = true
            binding.fontsRV.stopScroll()
            binding.fontsRV.scrollToPosition(0)

            binding.fontsRV.suppressLayout(true)
            mainViewModel.fetchAndStoreFontsFromApi()
        }
    }

    private fun filterFonts(
        source: List<FontEntity>, category: String, query: String
    ): List<FontEntity> {
        val withoutImported = source.filter { !it.font_category.equals("Imported", true) }
        val byCategory = if (category.equals("All", true)) withoutImported
        else withoutImported.filter { it.font_category.equals(category, true) }

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
        lifecycleScope.launch {
            filtersViewModel.isGrid.collect { isGrid ->
                if (isGrid) {
                    binding.fontsRV.layoutManager = GridLayoutManager(requireContext(), 2)
                } else {
                    binding.fontsRV.layoutManager = LinearLayoutManager(requireContext())
                }
                adapter.toggleViewType(isGrid)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.isLoading.collect { loading ->
                if (!loading && binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.isRefreshing = false
                    binding.fontsRV.suppressLayout(false)

                    if (shuffleAfterRefresh) {
                        shuffleAfterRefresh = false
                        applyFilters(filtersViewModel.searchQuery.value)
                    } else {
                        sglm.invalidateSpanAssignments()
                    }
                }
            }
        }

        // observe localFonts
        lifecycleScope.launch {
            mainViewModel.localFonts.collectLatest { fonts ->
                baseFonts = fonts
                applyFilters(filtersViewModel.searchQuery.value)
            }
        }

        lifecycleScope.launch {
            mainViewModel.downloadState.collect { downloadState ->
                when (downloadState) {
                    is FontDownloadState.Progress -> {
                        val font = downloadState.fontEntity
                        adapter.updateProgress(
                            font.id, ProgressUi(
                                progress = downloadState.progress,
                                isDownloading = true,
                                isDownloaded = false
                            )
                        )
                    }

                    is FontDownloadState.SuccessWithTypeface -> {
                        val font = downloadState.fontEntity

                        adapter.updateProgress(
                            font.id, ProgressUi(100, isDownloading = false, isDownloaded = true)
                        )

                        showGlobalSuccessSnack("Font downloaded") {
                            lifecycleScope.launch {
                                viewModel.setCanvasSize(CanvasSize("", 2000f, 2000f))
                                viewModel.addTextWithFont(
                                    requireActivity().getString(R.string.dummyText),
                                    font,
                                    requireActivity()
                                )

                                if (isAdded && findNavController().currentDestination?.id != R.id.editorFragment) {
                                    view?.post {
                                        findNavController().navigate(
                                            R.id.editorFragment, bundle, navOptions
                                        )
                                    }
                                }
                            }
                        }
                        mainViewModel.clearDownloadState()
                    }

                    is FontDownloadState.Error -> {
                        val font = downloadState.fontEntity
                        adapter.updateProgress(
                            font.id, ProgressUi(
                                progress = 0, isDownloading = false, isDownloaded = false
                            )
                        )

                        mainViewModel.clearDownloadState()
                        Snackbar.make(requireView(), "Download failed!", Snackbar.LENGTH_SHORT)
                            .show()
                    }

                    else -> {}
                }
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