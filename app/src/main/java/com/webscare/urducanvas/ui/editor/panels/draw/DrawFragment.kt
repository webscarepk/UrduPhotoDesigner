package com.webscare.urducanvas.ui.editor.panels.draw

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentDrawBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawFragment : Fragment() {
    private var _binding: FragmentDrawBinding? = null
    private val binding get() = _binding!!
    private var tabs = mutableListOf<String>()
    private lateinit var adapter: DrawPagerAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDrawBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ Setup adapter once
        tabs = mutableListOf("Brush")

        adapter = DrawPagerAdapter(
            requireActivity().supportFragmentManager, lifecycle, tabs
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false
        binding.done.addPressEffect {
            viewModel.exitDrawingMode()
        }

        binding.reset.addPressEffect { viewModel.resetBrushSettings() }
        initObservers()
        setupTabLayout()

        val startPage = arguments?.getInt("startPage", 0) ?: 0

        if (startPage in 0 until tabs.size) {
            binding.viewPager.setCurrentItem(startPage, false)
        }

        binding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == 1) {
                    viewModel.exitDrawingMode()
                }
            }
        })
    }

    private fun initObservers() {
        lifecycleScope.launch {
            viewModel.isDrawingMode.observe(viewLifecycleOwner) { isDrawingMode ->
                binding.done.isVisible = isDrawingMode
            }
        }
    }

    private fun setupTabLayout() {
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        binding.tabLayout.viewTreeObserver.addOnGlobalLayoutListener {
            if (isAdded) {
                for (i in 0 until binding.tabLayout.tabCount) {
                    val tabView = (binding.tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(i)
                    tabView?.scaleX = 0.9f
                    tabView?.scaleY = 0.9f
                }

                binding.tabLayout.getTabAt(binding.tabLayout.selectedTabPosition)?.view?.apply {
                    scaleX = 1.0f
                    scaleY = 1.0f
                }
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(150)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator())?.start()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.view?.animate()?.scaleX(0.9f)?.scaleY(0.9f)?.setDuration(150)?.start()
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        viewModel.exitDrawingMode()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            viewModel.exitDrawingMode()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.exitDrawingMode()
    }

    override fun onStop() {
        super.onStop()
        viewModel.exitDrawingMode()
    }
}