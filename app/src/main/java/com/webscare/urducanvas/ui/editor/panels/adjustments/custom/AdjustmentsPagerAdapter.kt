package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs

class AdjustmentsPagerAdapter(fragment: Fragment, private var tabs: List<AdjustmentPanelTabs>) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> ToneAdjustmentsFragment()
        1 -> ColorAdjustmentsFragment()
        2 -> AdvancedAdjustmentsFragment()
        else -> ToneAdjustmentsFragment()
    }

    override fun getItemId(position: Int): Long = tabs[position].id.toLong()

    override fun containsItem(itemId: Long): Boolean = tabs.any { it.id.toLong() == itemId }
}
