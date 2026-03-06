package com.webscare.urducanvas.ui.editor.panels.text.fonts

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.webscare.urducanvas.data.model.FontLanguages

class FontsPagerAdapter(
    fragment: Fragment,
    private var categories: List<FontLanguages>
) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = categories.size

    override fun createFragment(position: Int): Fragment {
        return FontsListFragment.newInstance(categories[position].name)
    }

    /**
     * Update the category list with surgical notifications.
     *
     * Key insight: a font download only changes `is_downloaded`/`is_downloading` on a
     * FontEntity inside Room — it does NOT change the language names or their count.
     * In that case the page structure is identical and we must NOT call any notify*,
     * because even notifyItemRangeChanged causes ViewPager2 to rebind/recreate the
     * visible page (flicker + scroll reset).
     *
     * We only notify when the actual page structure changes:
     *   - a new language appeared  → notifyItemRangeInserted
     *   - a language was removed   → notifyItemRangeRemoved
     *   - a language was renamed   → notifyItemRangeChanged for that slot only
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
            newSize > oldSize -> {
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }
            else -> {
                notifyItemRangeRemoved(newSize, oldSize - newSize)
            }
        }
    }

    override fun getItemId(position: Int): Long = categories[position].id.toLong()

    override fun containsItem(itemId: Long): Boolean =
        categories.any { it.id.toLong() == itemId }
}