package com.example.urduphotodesigner.ui.editor.panels.draw.eraser

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.data.model.PanelTabs
import com.example.urduphotodesigner.ui.editor.panels.draw.brush.BrushPanelFragment

class EraserPagerAdapter(
    fragment: Fragment,
    private var tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return EraserPanelFragment.newInstance(tabs[position].tab_name)
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
