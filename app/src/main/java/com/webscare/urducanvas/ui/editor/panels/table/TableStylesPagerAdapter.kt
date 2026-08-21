package com.webscare.urducanvas.ui.editor.panels.table

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class TableStylesPagerAdapter(
    fragment: Fragment,
    private var categories: List<String>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        val category = categories.getOrNull(position) ?: ""
        return TableStyleGridFragment.newInstance(category)
    }

    override fun getItemId(position: Int): Long {
        return categories.getOrNull(position)?.hashCode()?.toLong() ?: position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return categories.any { it.hashCode().toLong() == itemId }
    }

    fun updateCategories(newCategories: List<String>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }
}
