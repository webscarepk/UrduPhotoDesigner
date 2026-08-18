package com.webscare.urducanvas.ui.editor.panels.text.styles

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.PanelTabs
import com.webscare.urducanvas.data.model.PresetCategory

class TextStylesPagerAdapter(
    fragment: Fragment,
    private var tabs: List<PanelTabs>,
    private val isAddMode: Boolean = false
) : FragmentStateAdapter(fragment) {

    fun updateTabs(newTabs: List<PanelTabs>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    override fun getItemCount() = tabs.size

    override fun createFragment(position: Int): Fragment {
        val tab = tabs[position]
        val cat = PresetCategory.values().getOrNull(tab.id) ?: PresetCategory.THREE_D
        return TextStyleGridFragment.newInstance(cat, isAddMode)
    }

    override fun getItemId(position: Int): Long {
        return tabs[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it.id.toLong() == itemId }
    }
}
