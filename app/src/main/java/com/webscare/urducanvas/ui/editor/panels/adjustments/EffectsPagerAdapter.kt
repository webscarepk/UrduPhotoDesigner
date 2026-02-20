package com.webscare.urducanvas.ui.editor.panels.adjustments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.adjustments.custom.AdjustmentsFragment
import com.example.urduphotodesigner.ui.editor.panels.adjustments.filters.FiltersFragment
import com.example.urduphotodesigner.ui.editor.panels.adjustments.mask.MaskFragment

class EffectsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private var tabs: List<String> = listOf("Adjust", "Filters"),
    private val elementId: String
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    /** Create appropriate fragment for each tab position */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.adjustments.custom.AdjustmentsFragment.Companion.newInstance()  // Brightness, contrast, etc.
            1 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.adjustments.filters.FiltersFragment.Companion.newInstance(elementId)      // Filters, presets, etc.
            2 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.adjustments.mask.MaskFragment.Companion.newInstance(elementId)      // Filters, presets, etc.
            else -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.adjustments.custom.AdjustmentsFragment.Companion.newInstance()
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