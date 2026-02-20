package com.webscare.urducanvas.ui.splash

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.webscare.urducanvas.R
import com.webscare.urducanvas.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : androidx.fragment.app.Fragment() {

    private var _binding: FragmentSplashBinding?= null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireActivity().window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }
    }
    override fun onResume() {
        super.onResume()
        setEvents()
    }

    private fun setEvents() {
        lifecycleScope.launch {
            delay(3000)
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.splashFragment, true)
                .build()
            view?.post { findNavController().navigate(R.id.homeFragment, null, navOptions) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}