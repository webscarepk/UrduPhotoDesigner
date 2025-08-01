package com.example.urduphotodesigner.ui.navigation.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var recentAdapter: RecentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun setEvents() {
        viewModel.clearCanvas()

        recentAdapter = RecentAdapter { exportResult ->
            viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
            val bundle = Bundle().apply {
                putSerializable("canvas_size", exportResult.canvasSize)
                putSerializable("unit_type", UnitType.PIXELS)
            }
            findNavController().navigate(R.id.editorFragment, bundle)
        }

        binding.recentsRV.adapter = recentAdapter

        binding.create.setOnClickListener {
            findNavController().navigate(R.id.createFragment)
        }

        binding.saved.setOnClickListener {
            findNavController().navigate(R.id.savedFragment)
        }
    }

    private fun initObservers(){
        mainViewModel.exportResults.observe(viewLifecycleOwner, Observer { results ->
            recentAdapter.submitList(results)
        })

        viewModel.backgroundColor.observe(viewLifecycleOwner) { color ->
            Log.d("BG Color", "initObservers: $color")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}