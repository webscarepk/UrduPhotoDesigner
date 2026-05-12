package com.webscare.urducanvas.data.model

import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.ui.editor.panels.objects.ObjectsFragment

/**
 * Snapshot of everything ObjectsFragment needs in a single shot.
 *
 * Pre-computed in MainViewModel on a background thread whenever [localImages]
 * changes. Each fragment reads its category's slice in O(1) instead of filtering
 * the full 2000-item list independently.
 *
 * @property tabs               Ordered tab names ready for the ViewPager.
 * @property imagesByCategory   category -> images, pre-filtered (excluded categories removed).
 * @property recents            Already-filtered list for the "Recents" tab.
 */
data class ObjectsData(
    val tabs: List<String>,
    val imagesByCategory: Map<String, List<ImageEntity>>,
    val recents: List<ImageEntity>
) {
    companion object {
        /**
         * Initial value emitted by the StateFlow before the database has loaded.
         * Contains only the static emoji tabs — image tabs (Recents + extras)
         * are added once [MainViewModel.localImages] emits.
         *
         * This is what makes ObjectsFragment open instantly with emoji tabs visible.
         */
        val Initial = ObjectsData(
            tabs = ObjectsFragment.BASE_TABS,
            imagesByCategory = emptyMap(),
            recents = emptyList()
        )
    }
}