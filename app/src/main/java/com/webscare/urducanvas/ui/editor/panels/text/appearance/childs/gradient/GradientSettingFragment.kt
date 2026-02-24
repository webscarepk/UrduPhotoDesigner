package com.webscare.urducanvas.ui.editor.panels.text.appearance.childs.gradient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.activityViewModels
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentGradientSettingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class GradientSettingFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentGradientSettingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: com.webscare.urducanvas.common.canvas.CanvasViewModel by activityViewModels()

    private var pendingScale: Float = 1f
    private var pendingAngle: Float = 0f
    private var pendingX: Float = 0f
    private var pendingY: Float = 0f
    private var pendingSweepAngle: Float = 0f
    private var pendingRadius: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGradientSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun initObservers() {
        viewModel.gradient.observe(viewLifecycleOwner) { gradient ->
            pendingScale = gradient.scale.coerceIn(0.1f, 3f)
            binding.scale.progress = (pendingScale * 100).toInt()
            binding.scaleSize.text = String.format("%.2f", pendingScale)

            pendingRadius = gradient.radialRadiusFactor.coerceIn(0.1f, 1f)
            binding.radius.progress = (pendingRadius * 100).toInt()
            binding.radiusSize.text = String.format("%.2f°", pendingRadius)

            pendingSweepAngle = gradient.sweepStartAngle.mod(360f)
            binding.sweepAngle.progress = pendingSweepAngle.roundToInt()
            binding.sweepAngleSize.text = String.format("%d°", pendingSweepAngle.toInt())

            pendingX = gradient.centerX.coerceIn(0.1f, 1f)
            binding.shadowX.progress = (pendingX * 100).toInt()
            binding.shadowXSize.text = String.format("%.2f°", pendingX)

            pendingY = gradient.centerY.coerceIn(0.1f, 1f)
            binding.shadowY.progress = (pendingY * 100).toInt()
            binding.shadowYSize.text = String.format("%.2f°", pendingY)

            pendingAngle = gradient.angle.mod(360f)
            binding.angle.progress = pendingAngle.roundToInt()
            binding.angleSize.text = String.format("%d°", pendingAngle.toInt())

            val allCards = listOf(
                binding.scaleCard,
                binding.angleCard,
                binding.sweepAngleCard,
                binding.radiusCard,
                binding.shadowXCard,
                binding.shadowYCard
            )

            val visibleCardsByType = mapOf(
                GradientType.LINEAR to listOf(
                    binding.scaleCard,
                    binding.angleCard,
                ), GradientType.RADIAL to listOf(
                    binding.scaleCard, binding.radiusCard, binding.shadowXCard, binding.shadowYCard
                ), GradientType.SWEEP to listOf(
                    binding.scaleCard,
                    binding.sweepAngleCard,
                    binding.shadowXCard,
                    binding.shadowYCard
                )
            )

            val shouldShow = visibleCardsByType[gradient.type] ?: emptyList()
            allCards.forEach { card ->
                card.visibility = if (card in shouldShow) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setEvents() {
        binding.done.addPressEffect {
            parentFragment?.childFragmentManager?.popBackStack()
        }

        binding.scale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingScale = progress / 100f
                    binding.scaleSize.text = String.format("%.2f", pendingScale)
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.angle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingAngle = progress.toFloat()
                    binding.angleSize.text = "${progress}°"
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sweepAngle.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingSweepAngle = progress.toFloat()
                    binding.sweepAngleSize.text = "${progress}°"
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.radius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingRadius = progress / 100f
                    binding.radiusSize.text = String.format("%.2f", pendingRadius)
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.shadowX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingX = progress / 100f
                    binding.shadowXSize.text = String.format("%.2f", pendingX)
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.shadowY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    pendingY = progress / 100f
                    binding.shadowYSize.text = String.format("%.2f", pendingY)
                    updateGradient()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

    }

    private fun updateGradient() {
        viewModel.updateGradient(
            pendingScale,
            pendingAngle,
            pendingSweepAngle,
            pendingRadius,
            pendingX,
            pendingY,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}