package com.webscare.urducanvas.ui.editor.panels.adjustments.custom

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.data.model.PanelTabs

class AdjustmentsPagerAdapter(
    fragment: Fragment,
    private var tabs: List<com.webscare.urducanvas.data.model.PanelTabs>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ToneAdjustmentsFragment()
            1 -> ColorAdjustmentsFragment()
            2 -> AdvancedAdjustmentsFragment()
            else -> ToneAdjustmentsFragment()
        }
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
