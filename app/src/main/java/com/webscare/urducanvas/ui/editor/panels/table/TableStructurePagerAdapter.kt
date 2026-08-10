package com.webscare.urducanvas.ui.editor.panels.table

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs

class TableStructurePagerAdapter(
    fragment: Fragment,
    private val tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position].tab_name) {
            "Grid"      -> TableGridFragment.newInstance()
            "Header"    -> TableHeaderFooterFragment.newInstance()
            "Direction" -> TableDirectionFragment.newInstance()
            "Wrap"      -> TableWrapFragment.newInstance()
            else        -> TableGridFragment.newInstance()
        }
    }
}
