package com.example.urduphotodesigner.ui.editor.panels.draw.shape

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.databinding.FragmentEraserPanelBinding
import com.example.urduphotodesigner.databinding.FragmentShapePanelBinding
import com.example.urduphotodesigner.ui.editor.panels.draw.eraser.EraserPanelFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShapePanelFragment : Fragment() {
    private var _binding: FragmentShapePanelBinding? = null
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
        _binding = FragmentShapePanelBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        fun newInstance(tabName: String): ShapePanelFragment {
            val fragment = ShapePanelFragment()
            val args = Bundle()
            args.putString("tabName", tabName)
            fragment.arguments = args
            return fragment
        }
    }
}