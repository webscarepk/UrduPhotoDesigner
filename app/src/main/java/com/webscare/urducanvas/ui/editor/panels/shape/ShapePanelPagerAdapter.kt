package com.webscare.urducanvas.ui.editor.panels.shape

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.ui.editor.panels.adjustments.mask.MaskFragment

class ShapePanelPagerAdapter(fragment: Fragment, private var tabs: List<AdjustmentPanelTabs>) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment = when (tabs[position].tab_name) {
        "Mask" -> MaskFragment()
        else -> ShapePanelFragment.newInstance(tabs[position].tab_name)
    }

    override fun getItemId(position: Int): Long = tabs[position].id.toLong()
    override fun containsItem(itemId: Long): Boolean = tabs.any { it.id.toLong() == itemId }
}
