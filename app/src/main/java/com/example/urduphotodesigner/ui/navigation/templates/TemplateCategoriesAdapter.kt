package com.example.urduphotodesigner.ui.navigation.templates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutCategoryRowBinding

class TemplateCategoriesAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<HomeRow, TemplateCategoriesAdapter.CategoryVH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryVH {
        val binding = LayoutCategoryRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryVH(binding)
    }

    override fun onBindViewHolder(holder: CategoryVH, position: Int) {
        holder.bind(getItem(position) as HomeRow.CategoryRow)
    }

    inner class CategoryVH(private val b: LayoutCategoryRowBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val miniAdapter = TemplatesMiniAdapter(onTemplateClick)

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun bind(row: HomeRow.CategoryRow) {
            b.title.text = row.title
            b.seeAll.setOnClickListener { onSeeAll(row.title) }
            miniAdapter.submitList(row.templates)
        }
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(o: HomeRow, n: HomeRow) =
            (o as? HomeRow.CategoryRow)?.title == (n as? HomeRow.CategoryRow)?.title
        override fun areContentsTheSame(o: HomeRow, n: HomeRow) = o == n
    }
}
