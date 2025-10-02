package com.example.urduphotodesigner.ui.editor.panels.background

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.ui.editor.panels.background.backgrounds.BackgroundsListFragment
import com.example.urduphotodesigner.ui.editor.panels.background.colors.ColorsListFragment

class BackgroundPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val tabs: List<String>
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        return when (tabs[position]) {
            "Images" -> BackgroundsListFragment.newInstance(tabs[position])
            "Colors" -> ColorsListFragment.newInstance()
            else -> BackgroundsListFragment.newInstance(tabs[position])
        }
    }

}