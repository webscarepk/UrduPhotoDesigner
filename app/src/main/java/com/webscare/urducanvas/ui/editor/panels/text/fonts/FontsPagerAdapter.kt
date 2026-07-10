package com.webscare.urducanvas.ui.editor.panels.text.fonts

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.FontLanguages

class FontsPagerAdapter(
    fragment: Fragment,
    categories: List<FontLanguages>,
    private val standaloneMode: Boolean = false,
) : FragmentStateAdapter(fragment) {

    var categories: List<FontLanguages> = categories
        private set

    override fun getItemCount() = categories.size

    override fun createFragment(position: Int): Fragment = FontsListFragment.newInstance(
        fontLanguage = categories[position].name,
        standaloneMode = standaloneMode,
    )

    /**
     * Update the category list with surgical notifications.
     * Only notifies when page structure actually changes — avoids unnecessary
     * ViewPager2 rebinds (flicker + scroll reset) on font download state changes.
     */
    fun updateCategories(newCategories: List<FontLanguages>) {
        val oldCategories = categories
        categories = newCategories

        val oldSize = oldCategories.size
        val newSize = newCategories.size

        when {
            newSize == oldSize -> {
                for (i in 0 until newSize) {
                    if (oldCategories[i].id != newCategories[i].id ||
                        oldCategories[i].name != newCategories[i].name
                    ) {
                        notifyItemChanged(i)
                    }
                }
            }
            newSize > oldSize -> notifyItemRangeInserted(oldSize, newSize - oldSize)
            else -> notifyItemRangeRemoved(newSize, oldSize - newSize)
        }
    }

    override fun getItemId(position: Int): Long = categories[position].id.toLong()

    override fun containsItem(itemId: Long): Boolean = categories.any { it.id.toLong() == itemId }
}
