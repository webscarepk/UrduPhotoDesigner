package com.example.urduphotodesigner.ui.editor.panels.objects

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AnimRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.databinding.FragmentObjectsBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.getValue

@AndroidEntryPoint
class ObjectsFragment : Fragment() {
    private var _binding: FragmentObjectsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ObjectsPagerAdapter
    private var tabs = mutableListOf<String>()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: CanvasViewModel by activityViewModels()
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handlePickedUri(it) }

        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentObjectsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setEvents() {
        tabs.addAll(
            listOf(
                "Stickers",
                "Emoticons",
                "Animals",
                "Nature",
                "Food",
                "Sports",
                "Transport",
                "Objects",
                "Alchemy",
                "Shapes",
                "Arrows",
                "Letters",
                "Flags"
            )
        )

        adapter = ObjectsPagerAdapter(
            requireActivity().supportFragmentManager,
            lifecycle,
            tabs
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            val tabView = LayoutInflater.from(context).inflate(R.layout.custom_tab, null)
            tabView.findViewById<TextView>(R.id.tabTitle).text = tabs[position]
            tab.customView = tabView
        }.attach()

        binding.searchIcon.addPressEffect {
            binding.searchIcon.isVisible = false
            binding.searchBar.isVisible = true

            binding.searchBar.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchBar, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.searchBar.text.isNullOrEmpty()) {
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
                adapter.filter(query)
                hideKeyboard()

                true
            } else {
                false
            }
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val hasText = charSequence?.isNotEmpty() == true
                binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    if (hasText) {
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_close)
                    } else {
                        null
                    },
                    null
                )
            }

            override fun afterTextChanged(charSequence: Editable?) {}
        })

        binding.searchBar.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableRight = binding.searchBar.compoundDrawables[2]
                if (drawableRight != null && event.x >= binding.searchBar.width - binding.searchBar.paddingRight - drawableRight.bounds.width()) {
                    binding.searchBar.text.clear()
                    adapter.filter("")
                    binding.searchBar.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(requireActivity(), R.drawable.ic_search),
                        null,
                        null,
                        null
                    )
                    hideKeyboard()
                    return@setOnTouchListener true
                }
            }
            false
        }

        binding.addImage.addPressEffect {
            pickImage.launch("image/*")
        }
    }

    private fun bitmapCompress(image: Bitmap): Bitmap {
        val canvasWidth = 300
        val canvasHeight = 300

        val widthRatio = canvasWidth.toFloat() / image.width
        val heightRatio = canvasHeight.toFloat() / image.height
        val minScale = minOf(1f, widthRatio, heightRatio)

        val newWidth = (image.width * minScale).toInt()
        val newHeight = (image.height * minScale).toInt()

        val resized = Bitmap.createScaledBitmap(image, newWidth, newHeight, true)
        return resized
    }

    private fun handlePickedUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = ImageProcessor.copyUriToTempFile(requireActivity(), uri)?.absolutePath
                val exportDate =
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                mainViewModel.insertImage(
                    ImageEntity(
                        id = System.currentTimeMillis().toInt(),
                        file_name = File(filePath).name,
                        file_url = "",
                        file_size = File(filePath).length().toString(),
                        alt_text = "",
                        category = "Images Imported",
                        user_id = 0,
                        is_selected = false,
                        bitmapData = filePath,
                        created_at = exportDate
                    )
                )

                withContext(Dispatchers.Main) {
                    viewModel.addSticker(ImageProcessor.filePathToBitmap(filePath!!)
                        ?.let { bitmapCompress(it) }, requireActivity())
                }
            } catch (e: Exception) {
                Log.e("PhotoPicker", "Failed compressing image", e)
            }
        }
    }

    private fun updateIconVisibility(
        view: View,
        shouldBeVisible: Boolean,
        @AnimRes animShow: Int = R.anim.slide_up_2,
        @AnimRes animHide: Int = R.anim.slide_down_2
    ) {
        val isVisible = view.visibility == View.VISIBLE

        if (shouldBeVisible && !isVisible) {
            view.visibility = View.VISIBLE
            view.startAnimation(AnimationUtils.loadAnimation(view.context, animShow))
        } else if (!shouldBeVisible && isVisible) {
            if (view == binding.searchIcon) {
                binding.searchBar.isVisible = false
            }
            val anim = AnimationUtils.loadAnimation(view.context, animHide)
            view.startAnimation(anim)
            val duration = anim.duration
            view.postDelayed({ view.visibility = View.GONE }, duration)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.searchBar.windowToken, 0)
        binding.searchBar.clearFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}