package com.example.urduphotodesigner.ui.editor.panels.draw.brush

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.urduphotodesigner.databinding.FragmentBrushPanelBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrushPanelFragment : Fragment() {
    private var _binding: FragmentBrushPanelBinding? = null
    private val binding get() = _binding!!
    private var tabName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            tabName = it.getString("tabName")!!
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrushPanelBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): BrushPanelFragment {
            val fragment = BrushPanelFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}