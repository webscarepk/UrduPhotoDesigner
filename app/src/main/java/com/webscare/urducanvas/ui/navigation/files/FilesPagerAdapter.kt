package com.webscare.urducanvas.ui.navigation.files

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

class FilesPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle, private val tabs: List<String>) : androidx.viewpager2.adapter.FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = FilesListFragment.Companion.newInstance(tabs[position])
}
