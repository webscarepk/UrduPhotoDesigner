package com.example.urduphotodesigner.ui.editor.panels.draw.eraser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.urduphotodesigner.databinding.FragmentEraserPanelBinding
import com.example.urduphotodesigner.ui.editor.panels.draw.brush.BrushPanelFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EraserPanelFragment : Fragment() {
    private var _binding: FragmentEraserPanelBinding? = null
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
        _binding = FragmentEraserPanelBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): EraserPanelFragment {
            val fragment = EraserPanelFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}