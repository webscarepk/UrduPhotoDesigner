package com.webscare.urducanvas.ui.editor.panels.text

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.AppearanceFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment
import com.webscare.urducanvas.ui.editor.panels.text.format.FormatFragment
import com.webscare.urducanvas.ui.editor.panels.text.styles.TextStylesFragment
import com.webscare.urducanvas.ui.editor.panels.text.threed.Text3DFragment

class TextAdjustmentsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Styles"     -> TextStylesFragment.newInstance()
            "Font"       -> FontsFragment.newInstance(standaloneMode = true)
            "Appearance" -> AppearanceFragment.newInstance()
            "3D"         -> Text3DFragment.newInstance()
            "Format"     -> FormatFragment.newInstance()
            else         -> TextStylesFragment.newInstance()
        }
    }
}