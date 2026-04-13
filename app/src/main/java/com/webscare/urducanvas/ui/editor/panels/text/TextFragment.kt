package com.webscare.urducanvas.ui.editor.panels.text

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentTextBinding
import com.webscare.urducanvas.ui.editor.panels.text.fonts.imported.ImportedFontsBottomSheet
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TextFragment : Fragment() {
    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!
    private var tabs = emptyList<String>()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {
        viewModel.openAppearanceTab.observe(viewLifecycleOwner) { openTab ->
            if (isAdded){
                binding.viewPager.post {
                    binding.viewPager.currentItem = if (openTab == true) 1 else 0
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        tabs = listOf("Font", "Appearance", "Format")

        val adapter = TextPagerAdapter(
            requireActivity().supportFragmentManager, lifecycle, tabs
        )
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.searchIcon.isVisible = (position == 0)
                if (position != 0) {
                    binding.searchIcon.isVisible = false
                    binding.searchBar.isVisible = false
                    binding.searchBar.text?.clear()
                    mainViewModel.setQuery("")
                } else {
                    binding.searchIcon.isVisible = true
                    binding.searchBar.isVisible = false
                    binding.searchBar.text?.clear()
                    mainViewModel.setQuery("")
                }
            }
        })

        binding.addText.addPressEffect {
            viewModel.addText(
                requireActivity().getString(R.string.dummyText), requireActivity()
            )
        }
        binding.addFont.addPressEffect {
            ImportedFontsBottomSheet.newInstance()
                .show(childFragmentManager, ImportedFontsBottomSheet.TAG)
        }

        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true
            binding.searchBar.requestFocus()
            binding.searchBar.setSelection(binding.searchBar.text?.length ?: 0)
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.searchIcon.isVisible = true
                binding.searchBar.isVisible = false
            }
        }

        binding.searchBar.imeOptions = EditorInfo.IME_ACTION_SEARCH
        binding.searchBar.setRawInputType(InputType.TYPE_CLASS_TEXT)

        binding.searchBar.setImeActionLabel("🔍", EditorInfo.IME_ACTION_SEARCH)

        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchBar.text.toString()
                mainViewModel.setQuery(query)
                hideKeyboard()
                binding.searchBar.isVisible = false
                binding.searchIcon.isVisible = true
                true
            } else {
                false
            }
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence?, start: Int, count: Int, after: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence?, start: Int, before: Int, count: Int
            ) {
                val hasText = charSequence?.isNotEmpty() == true
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, if (hasText) {
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
                    } else {
                        null
                    }, null
                )
            }

            override fun afterTextChanged(charSequence: Editable?) {}
        })

        binding.searchBar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableRight = binding.searchBar.compoundDrawables[2]
                if (drawableRight != null && event.x >= binding.searchBar.width - binding.searchBar.paddingRight - drawableRight.bounds.width()) {
                    binding.searchBar.text.clear()
                    mainViewModel.setQuery("")
                    binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_search),
                        null,
                        null,
                        null
                    )
                    hideKeyboard()
                    binding.searchBar.clearFocus()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}