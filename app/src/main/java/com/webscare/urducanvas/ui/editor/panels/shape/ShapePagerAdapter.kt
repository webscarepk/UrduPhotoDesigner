package com.webscare.urducanvas.ui.editor.panels.shape

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs

class ShapePagerAdapter(
    fragment: Fragment,
    private var tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return ShapeFragment.newInstance(tabs[position].tab_name)
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
