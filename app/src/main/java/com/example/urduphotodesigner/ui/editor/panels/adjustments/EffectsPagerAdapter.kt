package com.example.urduphotodesigner.ui.editor.panels.adjustments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.adjustments.custom.AdjustmentsFragment
import com.example.urduphotodesigner.ui.editor.panels.adjustments.filters.FiltersFragment

class EffectsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String> = listOf("Adjust", "Filters"),
    private val elementId: String
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    /** Create appropriate fragment for each tab position */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AdjustmentsFragment.newInstance()  // Brightness, contrast, etc.
            1 -> FiltersFragment.newInstance(elementId)      // Filters, presets, etc.
            else -> AdjustmentsFragment.newInstance()
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