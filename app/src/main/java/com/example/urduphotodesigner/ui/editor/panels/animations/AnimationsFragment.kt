package com.example.urduphotodesigner.ui.editor.panels.animations

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.AnimationItem
import com.example.urduphotodesigner.common.canvas.sealed.ElementAnimation
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentAnimationsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnimationsFragment : Fragment() {
    private var _binding: FragmentAnimationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var animationsAdapter: AnimationsAdapter
    private val viewModel: CanvasViewModel by activityViewModels()

    private val availableAnimations = listOf(
        AnimationItem("None", R.drawable.ic_none),
        AnimationItem(ElementAnimation.Rise.name, R.drawable.ic_rise),
        AnimationItem(ElementAnimation.Pan.name, R.drawable.ic_pan),
        AnimationItem(ElementAnimation.Fade.name, R.drawable.ic_fade),
        AnimationItem(ElementAnimation.Pop.name, R.drawable.ic_pop),
        AnimationItem(ElementAnimation.Wipe.name, R.drawable.ic_wipe),
        AnimationItem(ElementAnimation.Blur.name, R.drawable.ic_blur),
        AnimationItem(ElementAnimation.Succession.name, R.drawable.ic_succession),
        AnimationItem(ElementAnimation.Breathe.name, R.drawable.ic_breathe),
        AnimationItem(ElementAnimation.Baseline.name, R.drawable.ic_baseline),
        AnimationItem(ElementAnimation.Drift.name, R.drawable.ic_drift),
        AnimationItem(ElementAnimation.Tectonic.name, R.drawable.ic_tectonic),
        AnimationItem(ElementAnimation.Tumble.name, R.drawable.ic_tumble),
        AnimationItem(ElementAnimation.Neon.name, R.drawable.ic_neon),
        AnimationItem(ElementAnimation.Scrapbook.name, R.drawable.ic_scrap_book),
        AnimationItem(ElementAnimation.Stomp.name, R.drawable.ic_stomp),

        // Add-on effects
        AnimationItem(ElementAnimation.Rotate.name, R.drawable.ic_rotate_anim),
        AnimationItem(ElementAnimation.Flicker.name, R.drawable.ic_flicker),
        AnimationItem(ElementAnimation.Pulse.name, R.drawable.ic_pulse),
        AnimationItem(ElementAnimation.Wiggle.name, R.drawable.ic_wiggle)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnimationsBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        initObservers()
    }

    private fun setupRecyclerView() {
        animationsAdapter = AnimationsAdapter(availableAnimations) { animationItem ->
            viewModel.applyElementAnimation( animationItem.name)
        }
        binding.animationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = animationsAdapter
        }

        binding.done.addPressEffect {
            findNavController().navigateUp()
        }
    }

    private fun initObservers() {
        viewModel.currentElementAnimation.observe(viewLifecycleOwner) { currentAnimation ->
            animationsAdapter.selectedAnimation = currentAnimation
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}