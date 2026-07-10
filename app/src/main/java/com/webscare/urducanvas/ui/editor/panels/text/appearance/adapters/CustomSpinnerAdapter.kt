package com.webscare.urducanvas.ui.editor.panels.text.appearance.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.databinding.SpinnerItemBinding.inflate

class CustomSpinnerAdapter(private val items: List<BlendType>) : android.widget.BaseAdapter() {

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = inflate(LayoutInflater.from(parent.context), parent, false)
        val blendType = items[position]
        binding.spinnerText.text = blendType.name
        return binding.root
    }
}
