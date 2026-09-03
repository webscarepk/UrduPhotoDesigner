package com.webscare.urducanvas.ui.editor.panels.text.threed.adapters

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Extrusion3DFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Lighting3DFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Material3DFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Presets3DFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Shadow3DFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.childs.Transform3DFragment

class Text3DPagerAdapter(
    fragment: Fragment,
    private val tabs: List<PanelTabs>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Presets3DFragment.newInstance()
            1 -> Transform3DFragment.newInstance()
            2 -> Extrusion3DFragment.newInstance()
            3 -> Material3DFragment.newInstance()
            4 -> Lighting3DFragment.newInstance()
            5 -> Shadow3DFragment.newInstance()
            else -> Presets3DFragment.newInstance()
        }
    }
}
