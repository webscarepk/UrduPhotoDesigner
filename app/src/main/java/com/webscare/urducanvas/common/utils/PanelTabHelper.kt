package com.webscare.urducanvas.common.utils

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R

/**
 * Shared helper utility for setting up restyled custom panel tabs with:
 *  - 13sp labels, bold & green (#005D28) when active, regular & gray (#8E94A2) when inactive
 *  - Full width rounded indicator
 *  - Edited state dot indicator (5dp circle at top 8dp, end 10dp)
 *  - Accessibility contentDescription updates
 */
object PanelTabHelper {

    fun setupCustomPanelTabs(
        tabLayout: TabLayout,
        viewPager: ViewPager2,
        titles: List<String>,
        onTabSelected: ((position: Int) -> Unit)? = null
    ) {
        val context = tabLayout.context
        val boldFont = ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD
        val regularFont = ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val customView = LayoutInflater.from(context)
                .inflate(R.layout.view_panel_tab, tabLayout, false)
            val titleView = customView.findViewById<TextView>(R.id.tabTitle)
            val title = titles.getOrNull(position) ?: ""
            titleView.text = title
            tab.customView = customView
            tab.contentDescription = title
        }.attach()

        // Apply initial selection state
        tabLayout.post {
            val selectedPos = tabLayout.selectedTabPosition.coerceAtLeast(0)
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                updateTabStyle(tab, i == selectedPos, boldFont, regularFont)
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabStyle(tab, true, boldFont, regularFont)
                onTabSelected?.invoke(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyle(tab, false, boldFont, regularFont)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                updateTabStyle(tab, true, boldFont, regularFont)
            }
        })
    }

    fun setTabEdited(tabLayout: TabLayout, position: Int, edited: Boolean) {
        val tab = tabLayout.getTabAt(position) ?: return
        val customView = tab.customView ?: return
        val dotView = customView.findViewById<View>(R.id.tabDot) ?: return
        val titleView = customView.findViewById<TextView>(R.id.tabTitle) ?: return

        dotView.visibility = if (edited) View.VISIBLE else View.GONE

        val baseTitle = titleView.text.toString()
        tab.contentDescription = if (edited) "$baseTitle, has changes" else baseTitle
    }

    fun updateTabStyle(
        tab: TabLayout.Tab?,
        isSelected: Boolean,
        boldFont: Typeface? = null,
        regularFont: Typeface? = null
    ) {
        val customView = tab?.customView ?: return
        val titleView = customView.findViewById<TextView>(R.id.tabTitle) ?: return
        val indicatorView = customView.findViewById<View>(R.id.tabIndicator)
        val context = customView.context
        val bold = boldFont ?: (ResourcesCompat.getFont(context, R.font.bold) ?: Typeface.DEFAULT_BOLD)
        val regular = regularFont ?: (ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT)

        if (isSelected) {
            titleView.setTextColor(ContextCompat.getColor(context, R.color.tab_selected_text))
            titleView.typeface = bold
            indicatorView?.visibility = View.VISIBLE
        } else {
            titleView.setTextColor(ContextCompat.getColor(context, R.color.tab_unselected_text))
            titleView.typeface = regular
            indicatorView?.visibility = View.GONE
        }
    }

    fun scrollToTabIfOverflows(tabLayout: TabLayout, position: Int) {
        tabLayout.post {
            if (tabLayout.tabCount <= 0) return@post
            val lastTab = tabLayout.getTabAt(tabLayout.tabCount - 1)?.view ?: return@post
            val totalTabWidth = lastTab.right
            val layoutWidth = tabLayout.width
            if (layoutWidth <= 0) return@post

            if (totalTabWidth <= layoutWidth) {
                tabLayout.scrollTo(0, 0)
            } else {
                val targetTab = tabLayout.getTabAt(position)?.view ?: return@post
                val maxScroll = (totalTabWidth - layoutWidth).coerceAtLeast(0)
                val targetScroll = (targetTab.left - layoutWidth / 2 + targetTab.width / 2).coerceIn(0, maxScroll)
                tabLayout.scrollTo(targetScroll, 0)
            }
        }
    }
}

/**
 * Convenience Extension functions
 */
fun TabLayout.setupPanelTabs(
    viewPager: ViewPager2,
    titles: List<String>,
    onTabSelected: ((position: Int) -> Unit)? = null
) {
    PanelTabHelper.setupCustomPanelTabs(this, viewPager, titles, onTabSelected)
}

fun TabLayout.setTabEdited(position: Int, edited: Boolean) {
    PanelTabHelper.setTabEdited(this, position, edited)
}

fun TabLayout.scrollToTabIfOverflows(position: Int) {
    PanelTabHelper.scrollToTabIfOverflows(this, position)
}
