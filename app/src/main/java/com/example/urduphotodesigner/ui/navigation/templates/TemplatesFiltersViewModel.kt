package com.example.urduphotodesigner.ui.navigation.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import java.io.Serializable

@HiltViewModel
class TemplatesFiltersViewModel @Inject constructor(
    private val saved: SavedStateHandle
) : ViewModel() {

    data class Filters(
        val category: String = "All",
        val query: String = "",
        val size: CanvasSize? = null
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
    fun setSize(size: CanvasSize?) = commit(_filters.value.copy(size = size))
}
