package com.webscare.urducanvas.ui.editor.panels.text

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.AppearanceFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment
import com.webscare.urducanvas.ui.editor.panels.text.format.FormatFragment

class TextAdjustmentsPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>,
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = when (tabs[position]) {
        "Font" -> FontsFragment.newInstance(standaloneMode = true)
        "Appearance" -> AppearanceFragment.newInstance()
        "Format" -> FormatFragment.newInstance()
        else -> FontsFragment.newInstance(standaloneMode = true)
    }
}
