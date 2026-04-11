package com.webscare.urducanvas.ui.auth

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.webscare.urducanvas.databinding.FragmentVerificationBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VerificationFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentVerificationBinding?= null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerificationBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}