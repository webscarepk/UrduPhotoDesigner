package com.example.urduphotodesigner.ui.editor.panels.animations

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
        AnimationItem( ElementAnimation.Rise.name),
        AnimationItem(ElementAnimation.Pan.name),
        AnimationItem( ElementAnimation.Fade.name),
        AnimationItem(ElementAnimation.Pop.name),
        AnimationItem( ElementAnimation.Wipe.name),
        AnimationItem( ElementAnimation.Blur.name),
        AnimationItem( ElementAnimation.Succession.name),
        AnimationItem(ElementAnimation.Breathe.name),
        AnimationItem(ElementAnimation.Baseline.name),
        AnimationItem(ElementAnimation.Drift.name),
        AnimationItem(ElementAnimation.Tectonic.name),
        AnimationItem(ElementAnimation.Tumble.name),
        AnimationItem( ElementAnimation.Neon.name),
        AnimationItem(ElementAnimation.Scrapbook.name),
        AnimationItem(ElementAnimation.Stomp.name),

        // Add-on effects
        AnimationItem(ElementAnimation.Rotate.name),
        AnimationItem(ElementAnimation.Flicker.name),
        AnimationItem(ElementAnimation.Pulse.name),
        AnimationItem(ElementAnimation.Wiggle.name)
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