package com.webscare.urducanvas.ui.editor.panels.adjustments

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.adjustments.custom.AdjustmentsFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.effects.EffectsFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.filters.FiltersFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.mask.MaskFragment

class EffectsPagerAdapter(
    fragment: Fragment,
    private var tabs: List<String> = listOf("Adjust", "Filters"),
    private val elementId: String
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = tabs.size

    /** Create appropriate fragment for each tab position */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> EffectsFragment.Companion.newInstance()  // Brightness, contrast, etc.
            1 -> AdjustmentsFragment.Companion.newInstance()  // Brightness, contrast, etc.
            2 -> FiltersFragment.Companion.newInstance(elementId)      // Filters, presets, etc.
            3 -> MaskFragment.Companion.newInstance(elementId)      // Filters, presets, etc.
            else -> AdjustmentsFragment.Companion.newInstance()
        }
    }

    override fun getItemId(position: Int): Long {
        return (tabs[position] + elementId).hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { (it + elementId).hashCode().toLong() == itemId }
    }
}