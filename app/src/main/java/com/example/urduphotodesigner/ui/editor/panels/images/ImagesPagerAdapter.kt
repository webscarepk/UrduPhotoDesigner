package com.example.urduphotodesigner.ui.editor.panels.images

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.objects.ObjectsListFragment

class ImagesPagerAdapter(
    private val fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private var currentQuery: String = ""

    fun filter(query: String) {
        currentQuery = query
        // find any existing fragments and tell them to re-filter
        fragmentManager.fragments
            .filterIsInstance<ObjectsListFragment>()
            .forEach { it.updateFilter(query) }
    }
    fun setTabs(newTabs: List<String>) {
        this.tabs = newTabs
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return ImagesListFragment.newInstance(tabs[position])
    }

}