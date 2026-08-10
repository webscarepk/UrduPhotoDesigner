package com.webscare.urducanvas.ui.editor.panels.table

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment

class TableAdjustmentsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Font"       -> FontsFragment.newInstance(standaloneMode = true)
            "Appearance" -> TableAppearanceTabFragment.newInstance()
            "Format"     -> TableFormatTabFragment.newInstance()
            "Structure"  -> TableStructureTabFragment.newInstance()
            "Styles"     -> TableStylesFragment.newInstance()
            else         -> FontsFragment.newInstance(standaloneMode = true)
        }
    }
}
