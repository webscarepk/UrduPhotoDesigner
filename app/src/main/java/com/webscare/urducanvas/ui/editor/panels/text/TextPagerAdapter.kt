package com.webscare.urducanvas.ui.editor.panels.text

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.text.appearance.AppearanceFragment
import com.example.urduphotodesigner.ui.editor.panels.text.fonts.FontsFragment
import com.example.urduphotodesigner.ui.editor.panels.text.format.FormatFragment

class TextPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Font" -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment.Companion.newInstance()
            "Appearance" -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.appearance.AppearanceFragment.Companion.newInstance()
            "Format" -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.format.FormatFragment.Companion.newInstance()
            "Style" -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.format.FormatFragment.Companion.newInstance()
            else -> _root_ide_package_.com.webscare.urducanvas.ui.editor.panels.text.fonts.FontsFragment.Companion.newInstance()
        }
    }

}