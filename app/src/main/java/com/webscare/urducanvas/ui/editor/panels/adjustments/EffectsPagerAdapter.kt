package com.webscare.urducanvas.ui.editor.panels.adjustments

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.adjustments.custom.AdjustmentsFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.effects.EffectsFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.filters.FiltersFragment
import com.webscare.urducanvas.ui.editor.panels.adjustments.mask.MaskFragment

class EffectsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>,
    private val elementId: String
) : FragmentStateAdapter(
    fragmentManager,
    lifecycle
) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs.getOrNull(position)) {
            "Effects" -> EffectsFragment.newInstance()
            "Adjust" -> AdjustmentsFragment.newInstance()
            "Filters" -> FiltersFragment.newInstance(elementId)
            "Mask" -> MaskFragment.newInstance(elementId)
            else -> EffectsFragment.newInstance()
        }
    }

    override fun getItemId(position: Int): Long {
        return (elementId.hashCode().toLong() shl 32) or position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        val position = (itemId and 0xFFFFFFFFL).toInt()
        return position < itemCount
    }
}