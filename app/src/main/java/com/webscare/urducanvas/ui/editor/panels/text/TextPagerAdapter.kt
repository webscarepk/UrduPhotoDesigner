package com.webscare.urducanvas.ui.editor.panels.text

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.ui.editor.panels.text.appearance.AppearanceFragment
import com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment
import com.webscare.urducanvas.ui.editor.panels.text.format.FormatFragment

class TextPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Font" -> FontsFragment.Companion.newInstance()
            "Appearance" -> AppearanceFragment.Companion.newInstance()
            "Format" -> FormatFragment.Companion.newInstance()
            "Style" -> FormatFragment.Companion.newInstance()
            else -> FontsFragment.Companion.newInstance()
        }
    }

}