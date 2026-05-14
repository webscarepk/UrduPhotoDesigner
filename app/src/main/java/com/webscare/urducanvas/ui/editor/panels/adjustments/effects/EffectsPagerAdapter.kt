package com.webscare.urducanvas.ui.editor.panels.adjustments.effects

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.AdjustmentPanelTabs
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.ShadowsFragment

class EffectsPagerAdapter(
    fragment: Fragment,
    private var tabs: List<AdjustmentPanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ImageShadowsFragment.newInstance()
            1 -> OverlayColorListFragment.newInstance()
            2 -> BlurFragment.newInstance()
            3 -> ImageStrokeFragment.newInstance()
            4 -> FeatherFragment.newInstance()
            else -> ShadowsFragment()
        }
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
