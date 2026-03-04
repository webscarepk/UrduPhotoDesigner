package com.webscare.urducanvas.ui.editor.panels.objects

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment

class ObjectsPagerAdapter(
    private val fm: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fm, lifecycle) {

    // current filter text
    private var currentQuery: String = ""

    /** Called by the host whenever the search text changes */
    fun filter(query: String) {
        currentQuery = query
        // find any existing fragments and tell them to re-filter
        fm.fragments
            .filterIsInstance<ObjectsListFragment>()
            .forEach { it.updateFilter(query) }
    }

    fun refreshData(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        fm.fragments
            .filterIsInstance<ObjectsListFragment>()
            .forEach { it.updateImages(images) }
    }

    fun setTabs(newTabs: List<String>) {
        this.tabs = newTabs
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        // pass the initial filter into the fragment’s arguments
        return ObjectsListFragment.newInstance(tabs[position], currentQuery)
    }
}
