package com.webscare.urducanvas.ui.editor.panels.images

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.ui.editor.panels.objects.ObjectsListFragment

class ImagesPagerAdapter(
    private val fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    private var currentQuery: String = ""

    fun filter(query: String) {
        currentQuery = query
        // find any existing fragments and tell them to re-filter
        fragmentManager.fragments
            .filterIsInstance<com.webscare.urducanvas.ui.editor.panels.objects.ObjectsListFragment>()
            .forEach { it.updateFilter(query) }
    }
    fun refreshData(images: List<com.webscare.urducanvas.data.model.ImageEntity>) {
        fragmentManager.fragments
            .filterIsInstance<ImagesListFragment>() // or whatever your per-tab fragment is
            .forEach { it.updateImages(images) }
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