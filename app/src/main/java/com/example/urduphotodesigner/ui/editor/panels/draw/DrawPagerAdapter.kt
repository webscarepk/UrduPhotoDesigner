package com.example.urduphotodesigner.ui.editor.panels.draw

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.draw.brush.BrushFragment
import com.example.urduphotodesigner.ui.editor.panels.draw.eraser.EraserFragment
import com.example.urduphotodesigner.ui.editor.panels.draw.shape.ShapeFragment

class DrawPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String>,
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    /** Create appropriate fragment for each tab position */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> BrushFragment.newInstance()
            1 -> ShapeFragment.newInstance()
            2 -> EraserFragment.newInstance()
            else -> BrushFragment.newInstance()
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