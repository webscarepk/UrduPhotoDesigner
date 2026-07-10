package com.webscare.urducanvas.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.common.utils.PexelsCategories
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.domain.repo.PexelsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PexelsViewModel"

@HiltViewModel
class PexelsViewModel @Inject constructor(private val repo: PexelsRepo) : ViewModel() {

    // ── Pagination for category tabs ──────────────────────────────────────────

    private val _paginatingTabs = MutableStateFlow<Set<String>>(emptySet())
    val paginatingTabs: StateFlow<Set<String>> = _paginatingTabs.asStateFlow()
    private val pageJobs = mutableMapOf<String, Job>()

    // Emits newly fetched items for a specific tab so the fragment can
    // appendItems() directly — bypassing Room→buildImagesData→submitList chain.
    // This is the key fix for the jumping issue.
    // Pair<tabName, newItems>
    private val _paginationResult = MutableStateFlow<Pair<String, List<ImageEntity>>?>(null)
    val paginationResult: StateFlow<Pair<String, List<ImageEntity>>?> = _paginationResult.asStateFlow()

    // ── Search state ──────────────────────────────────────────────────────────

    // What the user is currently typing — used for live local search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Local Room results shown while typing (instant, no API)
    private val _localSearchResults = MutableStateFlow<List<ImageEntity>>(emptyList())
    val localSearchResults: StateFlow<List<ImageEntity>> = _localSearchResults.asStateFlow()

    // Full results (local + API) shown after IME submit
    private val _searchResults = MutableStateFlow<List<ImageEntity>>(emptyList())
    val searchResults: StateFlow<List<ImageEntity>> = _searchResults.asStateFlow()

    // True once user pressed IME search — shows API results
    private val _isApiSearchActive = MutableStateFlow(false)
    val isApiSearchActive: StateFlow<Boolean> = _isApiSearchActive.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Which tab triggered the search — only that fragment shows results
    private val _activeSearchTab = MutableStateFlow("")
    val activeSearchTab: StateFlow<String> = _activeSearchTab.asStateFlow()

    private var searchHasMore = true
    private var currentSearchPage = 1
    private var searchJob: Job? = null
    private var localSearchJob: Job? = null

    // ── Error ─────────────────────────────────────────────────────────────────

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Category pagination ───────────────────────────────────────────────────

    /**
     * Called when user scrolls near bottom of a Pexels category tab.
     * Fetches next page, saves to Room. Room observer updates adapter automatically.
     */
    fun loadMore(tabName: String) {
        val superQuery = PexelsCategories.superQueryForTab(tabName)?.query ?: return
        if (pageJobs[superQuery]?.isActive == true) return

        pageJobs[superQuery] = viewModelScope.launch {
            _paginatingTabs.value = _paginatingTabs.value + tabName
            repo.loadNextPage(superQuery).collect { response ->
                when (response) {
                    is Response.Success -> {
                        val newItems = response.data ?: emptyList()
                        Log.d(TAG, "loadMore: +${newItems.size} for '$tabName'")
                        _paginatingTabs.value = _paginatingTabs.value - tabName
                        // Emit for direct append — fragment uses appendItems() not submitList()
                        // This bypasses Room observer chain and prevents jumping
                        if (newItems.isNotEmpty()) {
                            _paginationResult.value = Pair(tabName, newItems)
                        }
                    }
                    is Response.Error -> {
                        _error.value = response.message
                        _paginatingTabs.value = _paginatingTabs.value - tabName
                    }
                    else -> {}
                }
            }
        }
    }

    /** Lazy-load a tab group that wasn't seeded on launch. */
    fun loadTabGroupIfNeeded(tabName: String) {
        val sq = PexelsCategories.superQueryForTab(tabName) ?: return
        if (!sq.lazyLoad) return
        if (pageJobs[sq.query]?.isActive == true) return

        pageJobs[sq.query] = viewModelScope.launch {
            if (repo.getMeta(sq.query) != null) return@launch
            _paginatingTabs.value = _paginatingTabs.value + tabName
            repo.loadNextPage(sq.query).collect { response ->
                when (response) {
                    is Response.Success -> {
                        _paginatingTabs.value = _paginatingTabs.value - tabName
                        val newItems = response.data ?: emptyList()
                        if (newItems.isNotEmpty()) {
                            _paginationResult.value = Pair(tabName, newItems)
                        }
                    }
                    is Response.Error -> {
                        _error.value = response.message
                        _paginatingTabs.value = _paginatingTabs.value - tabName
                    }
                    else -> {}
                }
            }
        }
    }

    // ── Search: two-phase ─────────────────────────────────────────────────────
    //
    // Phase 1 — TYPING: user types → searchLocal(query) → show in adapter instantly
    //           No API call. Updates as user types (debounced by TextWatcher rate).
    //
    // Phase 2 — SUBMIT: user presses IME search button → hit API if needed,
    //           save results to Room under query as category name, show merged results.
    //           After this, the search tab appears in the tab layout automatically
    //           because Room observer in MainViewModel picks up the new category.

    /**
     * Called on every TextWatcher change — local Room search only, zero API cost.
     * [fromTab] is the category tab currently visible.
     */
    fun searchLocal(query: String, fromTab: String) {
        _searchQuery.value = query
        _activeSearchTab.value = fromTab
        _isApiSearchActive.value = false

        if (query.isBlank()) {
            clearSearch()
            return
        }

        localSearchJob?.cancel()
        localSearchJob = viewModelScope.launch {
            val results = repo.searchLocal(query)
            _localSearchResults.value = results
            // While typing, show local results in the adapter
            _searchResults.value = results
            Log.d(TAG, "searchLocal '$query': ${results.size} local results")
        }
    }

    /**
     * Called when user presses IME search button — hits API if local results < 5,
     * saves results to Room, results appear as a new tab automatically.
     */
    fun submitSearch(query: String, fromTab: String) {
        if (query.isBlank()) return
        _searchQuery.value = query
        _activeSearchTab.value = fromTab
        _isApiSearchActive.value = true
        currentSearchPage = 1
        searchHasMore = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true

            val localResults = repo.searchLocal(query)
            Log.d(TAG, "submitSearch '$query': ${localResults.size} local")

            if (localResults.size >= 5) {
                // Enough cached — no API call needed
                _searchResults.value = localResults
                _isSearching.value = false
                Log.d(TAG, "submitSearch: sufficient local results, skipping API")
                return@launch
            }

            // Hit API — results saved to Room under query as category
            Log.d(TAG, "submitSearch: hitting API for '$query'")
            repo.search(query, page = 1).collect { response ->
                when (response) {
                    is Response.Success -> {
                        val apiResults = response.data ?: emptyList()
                        searchHasMore = apiResults.size == 30
                        val localIds = localResults.map { it.id }.toSet()
                        val merged = localResults + apiResults.filter { it.id !in localIds }
                        _searchResults.value = merged
                        _isSearching.value = false
                        // Room observer will pick up new category tab automatically
                        Log.d(TAG, "submitSearch: ${merged.size} merged results saved to Room")
                    }
                    is Response.Error -> {
                        _searchResults.value = localResults // fallback to local
                        _error.value = response.message
                        _isSearching.value = false
                    }
                    else -> {}
                }
            }
        }
    }

    /** Paginate current search (scroll to bottom while API search results showing). */
    fun loadMoreSearch() {
        if (!searchHasMore || _isSearching.value || !_isApiSearchActive.value) return
        val query = _searchQuery.value.ifBlank { return }
        currentSearchPage++

        viewModelScope.launch {
            _isSearching.value = true
            repo.search(query, currentSearchPage).collect { response ->
                when (response) {
                    is Response.Success -> {
                        val more = response.data ?: emptyList()
                        searchHasMore = more.size == 30
                        val existing = _searchResults.value.map { it.id }.toSet()
                        _searchResults.value = _searchResults.value + more.filter { it.id !in existing }
                        _isSearching.value = false
                    }
                    is Response.Error -> {
                        _error.value = response.message
                        _isSearching.value = false
                    }
                    else -> {}
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        localSearchJob?.cancel()
        _searchQuery.value = ""
        _activeSearchTab.value = ""
        _localSearchResults.value = emptyList()
        _searchResults.value = emptyList()
        _isSearching.value = false
        _isApiSearchActive.value = false
        currentSearchPage = 1
        searchHasMore = true
    }

    fun isPexelsTab(tabName: String): Boolean = PexelsCategories.isPexelsTab(tabName)
    fun clearError() {
        _error.value = null
    }
}
