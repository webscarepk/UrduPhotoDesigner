package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.BlendFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.FillStrokeFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.KasheedaFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.LabelsFragment
import com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.ShadowsFragment

class AppearancePagerAdapter(
    fragment: Fragment,
    private var tabs: List<com.webscare.urducanvas.data.model.PanelTabs>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.KasheedaFragment.Companion.newInstance()
            1, 2 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.FillStrokeFragment.Companion.newInstance(tabs[position].tab_name)
            3 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.ShadowsFragment.Companion.newInstance()
            4 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.LabelsFragment.Companion.newInstance()
            5 -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.BlendFragment.Companion.newInstance()
            else -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.BlendFragment.Companion.newInstance()
        }
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
