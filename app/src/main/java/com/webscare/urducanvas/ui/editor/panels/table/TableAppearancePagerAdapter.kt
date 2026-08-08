package com.webscare.urducanvas.ui.editor.panels.table

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.FillStrokeFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.ShadowsFragment

class TableAppearancePagerAdapter(
    fragment: Fragment,
    private val tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position].tab_name) {
            "Fill"          -> FillStrokeFragment.newInstance("fill")
            "Stroke"        -> TableStrokeFragment.newInstance()
            "Shadow"        -> ShadowsFragment.newInstance()
            "Corner Radius" -> TableRadiusFragment.newInstance()
            else            -> FillStrokeFragment.newInstance("fill")
        }
    }
}
