package com.example.urduphotodesigner.ui.editor.panels.layers

import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.databinding.FragmentLayersBinding
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
    private var inSelectionMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLayersBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupToolbarInitial()
        observeViewModel()
    }

    private fun setupToolbarInitial() {
        val selected = viewModel.selectedElements.value ?: emptyList()
        if (selected.size > 1) {
            enterSelectionMode()
        } else {
            binding.toolbarLayers.title = getString(R.string.layers)
            binding.toolbarLayers.subtitle = getString(R.string.drag_to_rearrange)
        }
    }

    private fun setupRecyclerView() {
        adapter = LayersAdapter(
            onLockToggle = { element ->
                viewModel.updateElement(element)
            },
            onMoreOptions = { element, anchorView ->
                showItemPopupMenu(element, anchorView)
            },
            onItemClick = { element ->
                handleItemClick(element)
            },
            onItemLongClick = { element ->
                handleItemLongClick(element)
            },
            onStartDrag = { holder ->
                itemTouchHelper.startDrag(holder)
            }
        )
        binding.layers.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
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
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
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

        viewModel.selectedElements.observe(viewLifecycleOwner) { selectedList ->
            if (!isAdded) return@observe

            when {
                selectedList.isEmpty() && inSelectionMode -> {
                    exitSelectionMode()
                }

                selectedList.isNotEmpty() -> {
                    if (!inSelectionMode && selectedList.size > 1) {
                        enterSelectionMode()
                    }
                    if (inSelectionMode) {
                        updateSelectionToolbar()
                    }
                }
            }
        }
    }

    private fun enterSelectionMode() {
        inSelectionMode = true
        viewModel.enterSelectionMode()
        adapter.setSelectionMode(true)
        val count = viewModel.selectedElements.value?.size ?: 0
        binding.toolbarLayers.title = getString(R.string.selected_n_layers, count)
        binding.toolbarLayers.subtitle = ""  // or null
        binding.toolbarLayers.menu.clear()
        binding.toolbarLayers.inflateMenu(R.menu.menu_layers_action_mode)
        binding.toolbarLayers.setNavigationIcon(R.drawable.ic_close)
        binding.toolbarLayers.setNavigationOnClickListener {
            exitSelectionMode()
        }
        binding.toolbarLayers.setOnMenuItemClickListener { item ->
            // replicate onActionItemClicked logic:
            when (item.itemId) {
                R.id.action_lock_toggle_all -> {
                    viewModel.toggleLockOnSelected()
                    updateSelectionToolbar()
                    true
                }

                R.id.action_visibility_toggle_all -> {
                    viewModel.toggleVisibilityOnSelected()
                    updateSelectionToolbar()
                    true
                }

                R.id.action_group_toggle_all -> {
                    val selected = viewModel.selectedElements.value.orEmpty()
                    // if any of the selected already has a groupId → ungroup
                    if (selected.any { it.groupId != null }) {
                        viewModel.ungroupElements()
                    } else {
                        viewModel.selectElementForGrouping()
                    }
                    updateSelectionToolbar()
                    true
                }

                R.id.action_delete_all -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.confirm_delete)
                        .setMessage(
                            getString(
                                R.string.delete_n_layers,
                                viewModel.selectedElements.value?.size ?: 0
                            )
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            viewModel.removeSelectedElements()
                            exitSelectionMode()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    true
                }

                else -> false
            }
        }
    }

    private fun updateSelectionToolbar() {
        val count = viewModel.selectedElements.value?.size ?: 0
        binding.toolbarLayers.title = getString(R.string.selected_n_layers, count)
        // Update lock icon/title
        val menu = binding.toolbarLayers.menu
        val lockItem = menu.findItem(R.id.action_lock_toggle_all)
        if (lockItem != null) {
            val selected = viewModel.selectedElements.value ?: emptyList()
            val allLocked = selected.all { it.isLocked }
            lockItem.icon = ContextCompat.getDrawable(
                requireContext(),
                if (allLocked) R.drawable.ic_unlock else R.drawable.ic_lock
            )
            lockItem.title =
                if (allLocked) getString(R.string.unlock_all) else getString(R.string.lock_all)
        }

        val groupItem = menu.findItem(R.id.action_group_toggle_all)
        groupItem?.let { item ->
            val selected = viewModel.selectedElements.value.orEmpty()
            val anyGrouped = selected.any { it.groupId != null }

            item.icon = ContextCompat.getDrawable(
                requireContext(),
                if (anyGrouped) R.drawable.ic_un_group else R.drawable.ic_group
            )
            item.title = if (anyGrouped)
                getString(R.string.un_group_all)
            else
                getString(R.string.group_all)
        }

        // Update visibility icon/title
        val visItem = menu.findItem(R.id.action_visibility_toggle_all)
        if (visItem != null) {
            val selected = viewModel.selectedElements.value ?: emptyList()
            val allHidden = selected.isNotEmpty() && selected.all { it.isVisible }
            val allVisible = selected.isNotEmpty() && selected.all { !it.isVisible }
            when {
                allHidden -> {
                    visItem.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_show_pass)
                    visItem.title = getString(R.string.show_all)
                }

                allVisible -> {
                    visItem.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_hide_pass)
                    visItem.title = getString(R.string.hide_all)
                }

                else -> {
                    visItem.icon =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_hide_pass)
                    visItem.title = getString(R.string.hide_all)
                }
            }
        }
    }

    private fun handleItemLongClick(element: CanvasElement) {
        if (!inSelectionMode) {
            if (!element.isSelected) toggleSelection(element)
            enterSelectionMode()
        } else {
            if (!element.isSelected) toggleSelection(element)
            updateSelectionToolbar()
        }
    }

    private fun handleItemClick(element: CanvasElement) {
        if (inSelectionMode) {
            toggleSelection(element)
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) {
                exitSelectionMode()
            } else {
                updateSelectionToolbar()
            }
        } else {
            selectElement(element)
        }
    }

    private fun exitSelectionMode() {
        inSelectionMode = false
        viewModel.exitSelectionMode()
        adapter.setSelectionMode(false)
        clearSelection()
        if (isAdded) {
            binding.toolbarLayers.menu.clear()
            binding.toolbarLayers.title = getString(R.string.layers)
            binding.toolbarLayers.subtitle = getString(R.string.drag_to_rearrange)
            binding.toolbarLayers.setNavigationIcon(null)
        }
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
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_layer_item, popup.menu)

        val visibilityItem = popup.menu.findItem(R.id.action_visibility_toggle)
        visibilityItem.title =
            if (element.isVisible) getString(R.string.hide) else getString(R.string.show)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_visibility_toggle -> {
                    viewModel.toggleVisibility(element)
                    true
                }

                R.id.action_delete -> {
                    viewModel.removeElement(element)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}