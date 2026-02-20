package com.webscare.urducanvas.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class FiltersViewModel @Inject constructor(
    private val saved: SavedStateHandle
) : ViewModel() {

//    For Files Filtering
    private val _isGrid = MutableStateFlow(false)
    val isGrid = _isGrid.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun toggleGrid() {
        _isGrid.value = !_isGrid.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    //For Templates Filtering
    data class Filters(
        val category: String = "All",
        val query: String = "",
        val size: com.webscare.urducanvas.common.canvas.model.CanvasSize? = null
    ) : Serializable

    private val KEY = "templates_filters"

    private val _filters =
        MutableStateFlow(saved.get<Filters>(KEY) ?: Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private fun commit(new: Filters) {
        if (new == _filters.value) return
        _filters.value = new
        saved[KEY] = new
    }

    fun setCategory(cat: String) = commit(_filters.value.copy(category = cat))
    fun setQuery(q: String)      = commit(_filters.value.copy(query = q))
    fun setSize(size: com.webscare.urducanvas.common.canvas.model.CanvasSize?) = commit(_filters.value.copy(size = size))

    fun clearFilters() = commit(Filters())

}