package com.example.urduphotodesigner.ui.navigation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.data.model.ProgressUi
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.databinding.LayoutCategoryRowBinding
import com.example.urduphotodesigner.ui.navigation.templates.TemplatesMiniAdapter

class TrendsAdapter(
    private val onSeeAll: (String) -> Unit,
    private val onTemplateClick: (TemplateEntity, Boolean) -> Unit
) : ListAdapter<HomeRow, TrendsAdapter.TrendVH>(Diff()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val row = currentList[position] as HomeRow.TrendRow
        return row.title.hashCode().toLong()
    }

    private var hostRv: RecyclerView? = null
    private val progressById = mutableMapOf<Int, ProgressUi>()

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        hostRv = rv
    }
    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        super.onDetachedFromRecyclerView(rv)
        hostRv = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendVH {
        val binding = LayoutCategoryRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrendVH(binding)
    }

    override fun onBindViewHolder(holder: TrendVH, position: Int) {
        holder.bind(getItem(position) as HomeRow.TrendRow)
    }

    inner class TrendVH(private val b: LayoutCategoryRowBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val miniAdapter = TemplatesMiniAdapter(
            onClick = onTemplateClick,
            progressProvider = { id -> progressById[id] }
        )

        init {
            b.listRV.adapter = miniAdapter
            b.listRV.setHasFixedSize(true)
        }

        fun bind(row: HomeRow.TrendRow) {
            b.title.text = row.title
            b.seeAll.addPressEffect { onSeeAll(row.title) }

            val current = miniAdapter.currentList
            val same = current.size == row.templates.size &&
                    current.zip(row.templates).all { (a, b) -> a.id == b.id }
            if (!same) {
                miniAdapter.submitList(row.templates)
            }
        }

        fun updateChildProgress(templateId: Int, state: ProgressUi) {
            miniAdapter.updateProgress(templateId, state)
        }
    }

    fun updateTemplateProgress(
        templateId: Int,
        progress: ProgressUi,
    ) {
        progressById[templateId] = progress

        val rowIndex = currentList.indexOfFirst { row ->
            row is HomeRow.TrendRow && row.templates.any { it.id == templateId }
        }
        if (rowIndex == -1) return
        val holder = hostRv?.findViewHolderForAdapterPosition(rowIndex) as? TrendVH
        holder?.updateChildProgress(templateId, progress)
    }

    class Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(o: HomeRow, n: HomeRow): Boolean {
            return when {
                o is HomeRow.TrendRow && n is HomeRow.TrendRow -> o.title == n.title
                else -> false
            }
        }
        override fun areContentsTheSame(o: HomeRow, n: HomeRow) = o == n
    }
}
