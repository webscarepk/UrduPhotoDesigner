package com.webscare.urducanvas.ui.editor.panels.layers

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.CanvasViewModel
import com.webscare.urducanvas.common.canvas.enums.PanelType
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.utils.DialogUtils
import com.webscare.urducanvas.common.utils.Utils.addPressEffect
import com.webscare.urducanvas.databinding.FragmentLayersBinding
import com.webscare.urducanvas.databinding.LayoutLayerItemPopupBinding
import com.webscare.urducanvas.databinding.LayoutToolbarLayersNormalBinding
import com.webscare.urducanvas.databinding.LayoutToolbarLayersSelectionBinding
import com.webscare.urducanvas.ui.creation.CreateFragment
import com.webscare.urducanvas.ui.editor.EditorFragment
import com.webscare.urducanvas.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class LayersFragment : Fragment() {

    private var _binding: FragmentLayersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

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

        normalToolbar   = LayoutToolbarLayersNormalBinding.bind(binding.toolbarNormalInclude.root)
        selectionToolbar = LayoutToolbarLayersSelectionBinding.bind(binding.toolbarSelectionInclude.root)

        setupRecyclerView()
        setupToolbarInitial()
        attachDragHandleSwipe()
        observeViewModel()
        observePanelExpanded()
    }

    // ── Drag handle ───────────────────────────────────────────────────────────
    //
    // Same pattern as every other panel. Swipe up → expand, swipe down → collapse.
    // Layers panel is different: no tabs, no search — just drag handle + toolbar.

    private fun attachDragHandleSwipe() {
        // Walk up the fragment hierarchy to find EditorFragment and hand it our
        // drag handle so PanelSheetBehavior drives the guideline directly.
        var f: Fragment? = this
        while (f != null) {
            if (f is EditorFragment) {
                f.attachDragHandle(binding.dragHandle)
                return
            }
            f = f.parentFragment
        }
    }

    // ── Panel expansion observer ──────────────────────────────────────────────
    //
    // Layers doesn't need to change any internal UI when expanded/collapsed —
    // the EditorFragment handles the centerGuide animation which resizes
    // panelNavHost. The layers list naturally fills whatever height it gets.
    //
    // We observe just to keep state in sync (e.g. another panel expanding
    // collapses layers automatically via the single expandedPanel StateFlow).

    private fun observePanelExpanded() {
        // ── 1. Final settled state ──────────────────────────────────────────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.expandedPanel
                    .map { it == PanelType.LAYERS }
                    .collect { expanded ->
                        normalToolbar.closePanel.isVisible = expanded
                    }
            }
        }

        // ── 2. Live slide offset: drives smooth crossfade every frame ───────────
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.panelSlideOffset.collect { offset ->
                    applySlideOffset(offset)
                }
            }
        }
    }

    /**
     * Driven every frame by PanelSheetBehavior during drag + spring settle.
     * Layers has no collapsed/expanded header pair to crossfade — the close
     * button is handled by the settled-state observer above. If a header pair
     * is ever added to the layers layout, apply the same alpha crossfade
     * pattern used in all other panels here.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun applySlideOffset(offset: Float) {
        // No per-frame visual work needed for Layers currently.
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun setupToolbarInitial() {
        if ((viewModel.selectedElements.value?.size ?: 0) > 1) {
            enterSelectionMode()
        } else {
            showNormalToolbar()
        }
    }

    private fun showNormalToolbar() {
        normalToolbar.root.visibility    = View.VISIBLE
        selectionToolbar.root.visibility = View.GONE

        normalToolbar.title.text = getString(R.string.layers)

        normalToolbar.closePanel.addPressEffect {
            mainViewModel.collapsePanel()
        }

        normalToolbar.canvasSizeBtn.addPressEffect {
            CreateFragment.newResizeInstance().show(parentFragmentManager, "resize_canvas")
        }
    }

    private fun showSelectionToolbar() {
        normalToolbar.root.visibility    = View.GONE
        selectionToolbar.root.visibility = View.VISIBLE

        updateSelectionToolbar()

        val count = viewModel.selectedElements.value?.size ?: 0
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        selectionToolbar.close.addPressEffect { exitSelectionMode() }

        selectionToolbar.lock.addPressEffect {
            viewModel.toggleLockOnSelected(); updateSelectionToolbar()
        }

        selectionToolbar.group.addPressEffect {
            val selected = viewModel.selectedElements.value.orEmpty()
            if (selected.any { it.groupId != null }) viewModel.ungroupElements()
            else viewModel.selectElementForGrouping()
            updateSelectionToolbar()
        }

        selectionToolbar.visibility.addPressEffect {
            viewModel.toggleVisibilityOnSelected(); updateSelectionToolbar()
        }

        selectionToolbar.delete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context      = requireContext(),
                titleText    = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_n_layers, count)
            ) {
                viewModel.removeSelectedElements()
                exitSelectionMode()
            }
        }
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = LayersAdapter(
            onLockToggle   = { element -> viewModel.updateElement(element) },
            onMoreOptions  = { element, anchor -> showItemPopupMenu(element, anchor) },
            onItemClick    = { element -> handleItemClick(element) },
            onItemLongClick = { element -> handleItemLongClick(element) },
            onStartDrag    = { holder -> itemTouchHelper.startDrag(holder) }
        )
        binding.layers.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
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

    // ── ViewModel observation ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.canvasElements.observe(viewLifecycleOwner) { elements ->
            CoroutineScope(Dispatchers.IO).launch {
                val sorted = elements.sortedBy { it.zIndex }.reversed()
                withContext(Dispatchers.Main) { adapter.submitList(sorted) }
            }
        }

        viewModel.inSelectionMode.observe(viewLifecycleOwner) { enabled ->
            if (enabled) { adapter.setSelectionMode(true); showSelectionToolbar() }
            else { adapter.setSelectionMode(false); clearSelection(); showNormalToolbar() }
        }

        viewModel.selectedElements.observe(viewLifecycleOwner) { selectedList ->
            if (!isAdded) return@observe
            updateSelectionToolbar()
            if (selectedList.isNotEmpty()) {
                val pos = adapter.currentList().indexOfFirst { it.id == selectedList.last().id }
                if (pos != -1) binding.layers.smoothScrollToPosition(pos)
            }
        }

        viewModel.canvasSize.observe(viewLifecycleOwner) { size ->
            size ?: return@observe
            normalToolbar.canvasSizeBtn.text =
                "${size.width.toInt()} × ${size.height.toInt()}"
        }
    }

    // ── Selection helpers ─────────────────────────────────────────────────────

    private fun enterSelectionMode()  { viewModel.enterSelectionMode() }
    private fun exitSelectionMode()   { viewModel.exitSelectionMode() }
    private fun clearSelection()      { viewModel.setSelectedElements(emptyList()) }
    private fun selectElement(el: CanvasElement) { viewModel.setSelectedElements(listOf(el)) }

    private fun toggleSelection(element: CanvasElement) {
        val current = viewModel.canvasElements.value?.toMutableList() ?: return
        val target  = current.find { it.id == element.id } ?: return
        target.isSelected = !target.isSelected
        viewModel.setSelectedElements(current.filter { it.isSelected })
    }

    private fun handleItemClick(element: CanvasElement) {
        if (viewModel.inSelectionMode.value == true) {
            toggleSelection(element)
            if ((viewModel.selectedElements.value?.size ?: 0) == 0) viewModel.exitSelectionMode()
        } else {
            selectElement(element)
        }
    }

    private fun handleItemLongClick(element: CanvasElement) {
        if (!element.isSelected) toggleSelection(element)
        viewModel.enterSelectionMode()
    }

    private fun updateSelectionToolbar() {
        if (!::selectionToolbar.isInitialized) return
        val selected = viewModel.selectedElements.value.orEmpty()
        val count    = selected.size
        selectionToolbar.title.text = getString(R.string.selected_n_layers, count)

        val allLocked  = selected.isNotEmpty() && selected.all { it.isLocked }
        val allVisible = selected.isNotEmpty() && selected.all { it.isVisible }
        val allHidden  = selected.isNotEmpty() && selected.all { !it.isVisible }
        val anyGrouped = selected.any { it.groupId != null }

        selectionToolbar.lock.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (allLocked) R.drawable.ic_lock else R.drawable.ic_unlock
            )
        )
        selectionToolbar.lock.contentDescription =
            if (allLocked) getString(R.string.unlock_all) else getString(R.string.lock_all)

        selectionToolbar.visibility.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                if (allHidden) R.drawable.ic_hide_pass else R.drawable.ic_show_pass
            )
        )
        selectionToolbar.visibility.contentDescription =
            if (allHidden) getString(R.string.show_all) else getString(R.string.hide_all)

        selectionToolbar.group.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(), if (anyGrouped) R.drawable.ic_group else R.drawable.ic_un_group
            )
        )
        selectionToolbar.group.contentDescription =
            if (anyGrouped) getString(R.string.un_group_all) else getString(R.string.group_all)
    }

    // ── Popup menu ────────────────────────────────────────────────────────────

    private fun showItemPopupMenu(element: CanvasElement, anchorView: View) {
        val popupBinding = LayoutLayerItemPopupBinding.inflate(
            LayoutInflater.from(requireActivity())
        )
        val popupWindow = PopupWindow(
            popupBinding.root,
            (180 * requireActivity().resources.displayMetrics.density).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation       = 2f
            isOutsideTouchable = true
        }

        // ── Visibility toggle ─────────────────────────────────────────────
        popupBinding.visibility.apply {
            text = if (element.isVisible) getString(R.string.hide) else getString(R.string.show)
            addPressEffect {
                viewModel.toggleVisibility(element)
                popupWindow.dismiss()
            }
        }

        // ── Rename ────────────────────────────────────────────────────────
        // Shows a simple AlertDialog with an EditText.
        // Writes the new name into element.customName (add this field to
        // CanvasElement if not present) and calls updateElement().
        popupBinding.actionRename.addPressEffect {
            popupWindow.dismiss()
            showRenameDialog(element)
        }

        // ── Delete ────────────────────────────────────────────────────────
        popupBinding.actionDelete.addPressEffect {
            DialogUtils.showDeleteDialog(
                context      = requireContext(),
                titleText    = getString(R.string.confirm_delete),
                subtitleText = getString(R.string.delete_layer)
            ) {
                viewModel.removeElement(element)
            }
            popupWindow.dismiss()
        }

        // ── Smart positioning ─────────────────────────────────────────────
        anchorView.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val location     = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorTop    = location[1]
            val anchorBottom = anchorTop + anchorView.height

            popupBinding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupHeight = popupBinding.root.measuredHeight
            val spaceBelow  = screenHeight - anchorBottom

            if (spaceBelow >= popupHeight) {
                popupWindow.showAsDropDown(anchorView)
            } else {
                popupWindow.showAtLocation(
                    anchorView, Gravity.NO_GRAVITY, location[0], anchorTop - popupHeight
                )
            }
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    //
    // A minimal AlertDialog with an EditText pre-filled with the current name.
    // On confirm: sets element.customName = newName, calls updateElement().
    //
    // If CanvasElement doesn't yet have a `customName` field, add:
    //   var customName: String? = null
    // to your CanvasElement data class. The LayersAdapter reads it via
    // element.customName ?: <default name by type>.

    private fun showRenameDialog(element: CanvasElement) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_text)

        val editText = dialog.findViewById<EditText>(R.id.edit_text_input)
        editText.apply {
            setText(element.customName ?: defaultNameFor(element))
            selectAll()
            hint = getString(R.string.layer_name_hint)
            requestFocus()
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    element.customName = newName
                    viewModel.updateElement(element)
                    // Force adapter to refresh this item
                    val pos = adapter.currentList().indexOfFirst { it.id == element.id }
                    if (pos != -1) adapter.notifyItemChanged(pos)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        // Set dialog window attributes for no dim background
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent) // Make background transparent
            setDimAmount(0f) // No dim
            setGravity(Gravity.BOTTOM)
            // You might want to adjust width/height if the layout doesn't fill as expected
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        // Show the dialog
        dialog.show()
    }

    private fun defaultNameFor(element: CanvasElement): String = when (element.type) {
        com.webscare.urducanvas.common.canvas.enums.ElementType.TEXT       -> element.text ?: "Text"
        com.webscare.urducanvas.common.canvas.enums.ElementType.IMAGE      -> "Image"
        com.webscare.urducanvas.common.canvas.enums.ElementType.STICKER    -> "Sticker"
        com.webscare.urducanvas.common.canvas.enums.ElementType.DRAW       -> "Brush"
        com.webscare.urducanvas.common.canvas.enums.ElementType.SHAPE      ->
            element.shapeType?.displayName ?: "Shape"
        else                                                                -> "Background"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}