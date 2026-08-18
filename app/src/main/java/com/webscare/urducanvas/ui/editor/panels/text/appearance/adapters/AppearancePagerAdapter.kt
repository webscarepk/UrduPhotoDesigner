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
    private var tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> KasheedaFragment.Companion.newInstance()
            1, 2 -> FillStrokeFragment.Companion.newInstance(tabs[position].tab_name)
            3 -> ShadowsFragment.Companion.newInstance()
            4 -> com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.GlowFragment.newInstance()
            5 -> LabelsFragment.Companion.newInstance()
            6 -> BlendFragment.Companion.newInstance()
            else -> BlendFragment.Companion.newInstance()
        }
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
