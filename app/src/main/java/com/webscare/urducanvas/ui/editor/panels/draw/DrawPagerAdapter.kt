package com.webscare.urducanvas.ui.editor.panels.draw

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.webscare.urducanvas.ui.editor.panels.draw.brush.BrushFragment

class DrawPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>,
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    /** Create appropriate fragment for each tab position */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BrushFragment.Companion.newInstance()
            else -> BrushFragment.Companion.newInstance()
        }
    }

    /** ✅ Force recreation when tab list changes */
    override fun getItemId(position: Int): Long {
        return tabs[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.hashCode().toLong() == itemId }
    }
}