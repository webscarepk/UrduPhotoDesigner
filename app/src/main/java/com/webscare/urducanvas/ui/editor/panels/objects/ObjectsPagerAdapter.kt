package com.webscare.urducanvas.ui.editor.panels.objects

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class ObjectsPagerAdapter(
    private val fm: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : FragmentStateAdapter(fm, lifecycle) {

    private var currentQuery: String = ""
    var onTabVisibilityChanged: ((category: String, hasResults: Boolean) -> Unit)? = null

    fun filter(query: String) {
        if (currentQuery == query) return
        currentQuery = query
        forEachLiveFragment { it.updateFilter(query) }
    }

    fun refreshData(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        forEachLiveFragment { it.updateImages(images) }
    }

    override fun getItemCount(): Int = tabs.size
    override fun getItemId(position: Int): Long = tabs[position].hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean =
        tabs.any { it.hashCode().toLong() == itemId }

    override fun createFragment(position: Int): Fragment {
        val fragment = ObjectsListFragment.newInstance(tabs[position], currentQuery)
        fragment.onFilterResult = { category: String, count: Int ->
            onTabVisibilityChanged?.invoke(category, count > 0)
        }
        return fragment
    }

    private inline fun forEachLiveFragment(action: (ObjectsListFragment) -> Unit) {
        fm.fragments.filterIsInstance<ObjectsListFragment>().forEach(action)
    }
}