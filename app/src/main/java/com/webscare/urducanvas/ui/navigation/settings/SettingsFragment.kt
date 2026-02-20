package com.webscare.urducanvas.ui.navigation.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentSettingsBinding
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : androidx.fragment.app.Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
    }

    private fun setEvents() {
        binding.upgradeNow.addPressEffect { view?.post { findNavController().navigate(R.id.subscriptionsFragment) } }
        binding.preferences.addPressEffect { view?.post {  findNavController().navigate(R.id.preferencesFragment) } }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}