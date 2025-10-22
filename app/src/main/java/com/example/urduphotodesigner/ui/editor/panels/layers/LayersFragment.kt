package com.example.urduphotodesigner.ui.editor.panels.layers

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.utils.DialogUtils
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.FragmentLayersBinding
import com.example.urduphotodesigner.databinding.LayoutLayerItemPopupBinding
import com.example.urduphotodesigner.databinding.LayoutToolbarLayersNormalBinding
import com.example.urduphotodesigner.databinding.LayoutToolbarLayersSelectionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LayersFragment : Fragment() {
    private var _binding: FragmentLayersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private lateinit var adapter: LayersAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var normalToolbar: LayoutToolbarLayersNormalBinding
    private lateinit var selectionToolbar: LayoutToolbarLayersSelectionBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayersBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        normalToolbar = LayoutToolbarLayersNormalBinding.bind(binding.toolbarNormalInclude.root)
        selectionToolbar =
            LayoutToolbarLayersSelectionBinding.bind(binding.toolbarSelectionInclude.root)
        setupRecyclerView()
        setupToolbarInitial()
        observeViewModel()
    }

    private fun showSelectionToolbar() {
        normalToolbar.root.visibility = View.GONE
        selectionToolbar.root.visibility = View.VISIBLE

        updateSelectionToolbar()

        val count = viewModel.selectedElements.value?.size ?: 0
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        // Close button
        selectionToolbar.close.addPressEffect {
            exitSelectionMode()
        }

        // Example action buttons
        selectionToolbar.lock.addPressEffect {
            viewModel.toggleLockOnSelected()
            updateSelectionToolbar()
        }
        selectionToolbar.group.addPressEffect {
            val selected = viewModel.selectedElements.value.orEmpty()
            // if any of the selected already has a groupId → ungroup
            if (selected.any { it.groupId != null }) {
                viewModel.ungroupElements()
            } else {
                viewModel.selectElementForGrouping()
            }
            updateSelectionToolbar()
        }

        selectionToolbar.visibility.addPressEffect {
            viewModel.toggleVisibilityOnSelected()
            updateSelectionToolbar()
        }

        selectionToolbar.delete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context = requireContext(),
                titleText = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_n_layers, count)
            ) {
                viewModel.removeSelectedElements()
                exitSelectionMode()
            }
        }
    }

    private fun setupToolbarInitial() {
        val selected = viewModel.selectedElements.value ?: emptyList()
        if (selected.size > 1) {
            enterSelectionMode()
        } else {
            showNormalToolbar()
        }
    }

    private fun showNormalToolbar() {
        normalToolbar.root.visibility = View.VISIBLE
        selectionToolbar.root.visibility = View.GONE

        normalToolbar.title.text = getString(R.string.layers)
        normalToolbar.subTitle.text = getString(R.string.drag_to_rearrange)
    }

    private fun setupRecyclerView() {
        adapter = LayersAdapter(onLockToggle = { element ->
            viewModel.updateElement(element)
        }, onMoreOptions = { element, anchorView ->
            showItemPopupMenu(element, anchorView)
        }, onItemClick = { element ->
            handleItemClick(element)
        }, onItemLongClick = { element ->
            handleItemLongClick(element)
        }, onStartDrag = { holder ->
            itemTouchHelper.startDrag(holder)
        })
        binding.layers.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewModel.updateCanvasElementsOrderAndZIndex(adapter.getItems().reversed())
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.layers)
    }

    private fun observeViewModel() {
        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            CoroutineScope(Dispatchers.IO).launch {
                val sortedElements = elements.sortedBy { it.zIndex }.reversed()
                withContext(Dispatchers.Main) {
                    adapter.submitList(sortedElements)
                }
            }
        }

        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (enabled) {
                adapter.setSelectionMode(true)
                showSelectionToolbar()
            } else {
                adapter.setSelectionMode(false)
                clearSelection()
                showNormalToolbar()
            }
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { selectedList ->
            if (!isAdded) return@observe

            updateSelectionToolbar()

            if (selectedList.isNotEmpty()) {
                val last = selectedList.last()
                val pos = adapter.currentList().indexOfFirst { it.id == last.id }
                if (pos != -1) {
                    binding.layers.smoothScrollToPosition(pos)
                }
            }
        }
    }

    private fun enterSelectionMode() {
        viewModel.enterSelectionMode()
    }

    private fun updateSelectionToolbar() {
        val count = viewModel.selectedElements.value?.size ?: 0
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        val selected = viewModel.selectedElements.value.orEmpty()

        // 🔒 Lock / Unlock
        val allLocked = selected.isNotEmpty() && selected.all { it.isLocked }
        selectionToolbar.lock.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (allLocked) R.drawable.ic_lock else R.drawable.ic_unlock
            )
        )
        selectionToolbar.lock.contentDescription =
            if (allLocked) getString(R.string.unlock_all) else getString(R.string.lock_all)

        // 👁 Visibility
        val allVisible = selected.isNotEmpty() && selected.all { it.isVisible }
        val allHidden = selected.isNotEmpty() && selected.all { !it.isVisible }

        when {
            allVisible -> {
                selectionToolbar.visibility.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_show_pass)
                )
                selectionToolbar.visibility.contentDescription = getString(R.string.hide_all)
            }

            allHidden -> {
                selectionToolbar.visibility.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_hide_pass)
                )
                selectionToolbar.visibility.contentDescription = getString(R.string.show_all)
            }

            else -> {
                // Mixed state → default hide
                selectionToolbar.visibility.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_show_pass)
                )
                selectionToolbar.visibility.contentDescription = getString(R.string.hide_all)
            }
        }

        // 👥 Group / Ungroup
        val anyGrouped = selected.any { it.groupId != null }
        selectionToolbar.group.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (anyGrouped) R.drawable.ic_group else R.drawable.ic_un_group
            )
        )
        selectionToolbar.group.contentDescription =
            if (anyGrouped) getString(R.string.un_group_all) else getString(R.string.group_all)
    }

    private fun handleItemLongClick(element: CanvasElement) {
        if (!element.isSelected) toggleSelection(element)
        viewModel.enterSelectionMode()
    }

    private fun handleItemClick(element: CanvasElement) {
        if (viewModel.inSelectionMode.value == true) {
            toggleSelection(element)
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) {
                viewModel.exitSelectionMode()
            }
        } else {
            selectElement(element)
        }
    }

    private fun exitSelectionMode() {
        viewModel.exitSelectionMode()
    }

    private fun selectElement(element: CanvasElement) {
        viewModel.setSelectedElements(listOf(element))
    }

    private fun toggleSelection(element: CanvasElement) {
        val currentElements = viewModel.canvasElements.value?.toMutableList() ?: return
        val target = currentElements.find { it.id == element.id } ?: return
        target.isSelected = !target.isSelected
        val newSelected = currentElements.filter { it.isSelected }
        viewModel.setSelectedElements(newSelected)
    }

    private fun clearSelection() {
        viewModel.setSelectedElements(emptyList())
    }

    // Show per-item popup menu anchored at the overflow icon
    private fun showItemPopupMenu(element: CanvasElement, anchorView: View) {
        val popupBinding =
            LayoutLayerItemPopupBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(), // ~200dp width
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 2f
        popupWindow.isOutsideTouchable = true


        // ---- item logic ----
        popupBinding.visibility.findViewById<TextView>(R.id.visibility).text =
            if (element.isVisible) getString(R.string.hide) else getString(R.string.show)

        popupBinding.visibility.addPressEffect {
            viewModel.toggleVisibility(element)
            popupWindow.dismiss()
        }

        popupBinding.actionDelete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context = requireContext(),
                titleText = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_layer)
            ) {
                viewModel.removeElement(element)

            }
            popupWindow.dismiss()
        }

        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop = location[1]
            val anchorBottom = anchorTop + anchorView.height

            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight

            val spaceBelow = screenHeight - anchorBottom
            val spaceAbove = anchorTop

            if (spaceBelow >= popupHeight) {
                popupWindow.showAsDropDown(anchorView)
            } else if (spaceAbove >= popupHeight) {
                popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY, location[0], // x
                    anchorTop - popupHeight // y (above anchor)
                )
            } else {
                popupWindow.showAsDropDown(anchorView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}