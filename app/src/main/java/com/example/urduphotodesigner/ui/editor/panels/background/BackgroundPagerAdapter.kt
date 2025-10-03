package com.example.urduphotodesigner.ui.editor.panels.background

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.BackgroundsListFragment
import com.example.urduphotodesigner.ui.editor.panels.background.colors.ColorsListFragment

class BackgroundPagerAdapter(
    private val fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    fun setTabs(newTabs: List<String>) {
        this.tabs = newTabs
        notifyDataSetChanged()
    }

    fun refreshData(images: List<ImageEntity>) {
        fragmentManager.fragments
            .filterIsInstance<BackgroundsListFragment>()
            .forEach { it.updateImages(images) }
    }

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Images" -> BackgroundsListFragment.newInstance(tabs[position])
            "Colors" -> ColorsListFragment.newInstance()
            else -> BackgroundsListFragment.newInstance(tabs[position])
        }
    }

    /** 🔑 Force fragment recreation when tab list changes */
    override fun getItemId(position: Int): Long {
        return tabs[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.hashCode().toLong() == itemId }
    }
}
