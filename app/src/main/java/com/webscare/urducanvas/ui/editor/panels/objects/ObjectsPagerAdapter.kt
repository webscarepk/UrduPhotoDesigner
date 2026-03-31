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

    /**
     * Push a new filter query to every currently-attached ObjectsListFragment.
     * FragmentStateAdapter keeps live fragments in the FragmentManager under
     * the tag "f{itemId}", so we filter by instance type which is safe and
     * avoids touching fragments that haven't been created yet.
     */
    fun filter(query: String) {
        if (currentQuery == query) return          // no-op if unchanged
        currentQuery = query
        forEachLiveFragment { it.updateFilter(query) }
    }

    /**
     * Push fresh image data to every currently-attached ObjectsListFragment.
     * Only non-base-tab fragments actually use this data, so each fragment
     * guards against unnecessary work internally.
     */
    fun refreshData(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        forEachLiveFragment { it.updateImages(images) }
    }

    // ── FragmentStateAdapter contract ─────────────────────────────────────────

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return ObjectsListFragment.newInstance(tabs[position], currentQuery)
    }

    /**
     * Stable IDs prevent ViewPager2 from destroying and recreating fragments
     * when notifyDataSetChanged() is called. We use the tab name's hash code
     * so a tab always maps to the same fragment regardless of its position.
     */
    override fun getItemId(position: Int): Long = tabs[position].hashCode().toLong()

    override fun containsItem(itemId: Long): Boolean =
        tabs.any { it.hashCode().toLong() == itemId }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private inline fun forEachLiveFragment(action: (ObjectsListFragment) -> Unit) {
        fm.fragments
            .filterIsInstance<ObjectsListFragment>()
            .forEach(action)
    }
}