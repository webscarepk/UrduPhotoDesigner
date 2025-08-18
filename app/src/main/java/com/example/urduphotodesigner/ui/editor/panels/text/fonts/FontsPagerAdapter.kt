package com.example.urduphotodesigner.ui.editor.panels.text.fonts

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.urduphotodesigner.data.model.FontLanguages

class FontsPagerAdapter(
    fragment: Fragment,
    private var categories: List<FontLanguages>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = categories.size

    override fun createFragment(position: Int): Fragment {
        return FontsListFragment.newInstance(categories[position].name)
    }

    fun updateCategories(newCategories: List<FontLanguages>) {
        categories = newCategories
        notifyDataSetChanged() // triggers a smooth update
    }

    override fun getItemId(position: Int): Long {
        return categories[position].id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return categories.any { it.id.toLong() == itemId }
    }
}

