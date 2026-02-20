package com.webscare.urducanvas.ui.navigation.fonts

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class PopularFontsPagerAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fm, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return PopularFontsListFragment.newInstance(tabs[position])
    }

    fun updateTabs(newTabs: List<String>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = tabs[position].hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean = tabs.any { it.hashCode().toLong() == itemId }

}
