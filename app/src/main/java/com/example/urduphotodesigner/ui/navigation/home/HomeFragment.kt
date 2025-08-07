package com.example.urduphotodesigner.ui.navigation.home

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.CanvasViewModel
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.common.utils.Utils.addPressEffect
import com.example.urduphotodesigner.databinding.DialogLoadingProgressBinding
import com.example.urduphotodesigner.databinding.FragmentHomeBinding
import com.example.urduphotodesigner.databinding.LayoutProjectPopupBinding
import com.example.urduphotodesigner.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CanvasViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var recentAdapter: RecentAdapter

    private var bundle: Bundle = Bundle()
    private var loadingDialog: AlertDialog? = null
    private var dialogBinding: DialogLoadingProgressBinding? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                val widthVal = bitmap.width.toFloat()
                val heightVal = bitmap.height.toFloat()

                val canvasSize = CanvasSize("From Image", 0, widthVal, heightVal)
                val bundle = Bundle().apply {
                    putSerializable("canvas_size", canvasSize)
                    putSerializable("unit_type", UnitType.PIXELS)
                }

                viewModel.clearCanvas()
                viewModel.setCanvasSize(canvasSize)
                viewModel.setCanvasBackgroundImage(bitmap)
                findNavController().navigate(R.id.editorFragment, bundle)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setEvents()
        initObservers()
    }

    private fun showLoadingDialog() {
        dialogBinding = DialogLoadingProgressBinding.inflate(LayoutInflater.from(requireActivity()))

        loadingDialog = AlertDialog.Builder(requireActivity())
            .setView(dialogBinding!!.root)
            .setCancelable(false)
            .create()

        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
        dialogBinding = null
    }

    private fun setEvents() {

        recentAdapter = RecentAdapter(onClick = { exportResult ->
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    viewModel.loadTemplateFromJsonFile(exportResult, requireContext())
                    bundle = Bundle().apply {
                        putSerializable("canvas_size", exportResult.canvasSize)
                        putSerializable("unit_type", UnitType.PIXELS)
                    }
                }
            }
        }, onLongClick = { view, exportResult ->
            showPopupMenu(view, exportResult)
        })

        binding.recentsRV.adapter = recentAdapter

        binding.create.addPressEffect {
            pickImageLauncher.launch("image/*")
        }

        binding.blankCanvas.addPressEffect {
            findNavController().navigate(R.id.createFragment)
        }

        binding.templates.addPressEffect {
            findNavController().navigate(R.id.templatesFragment)
        }
    }

    private fun initObservers(){
        mainViewModel.exportResults.observe(viewLifecycleOwner) { results ->
            recentAdapter.submitList(results)
        }

        viewModel.loadingStage.observe(viewLifecycleOwner) { (message, percent) ->
            dialogBinding?.apply {
                progressBar.progress = percent
                subtitle.text = "$message... $percent%"
            }
        }

        viewModel.isLoadingTemplate.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading == true){
                showLoadingDialog()
            }else if (isLoading == false){
                dismissLoadingDialog()
                findNavController().navigate(R.id.editorFragment, bundle)
            }
        }
    }

    private fun showPopupMenu(
        anchorView: View,
        item: ExportResult,
    ) {
        val binding = LayoutProjectPopupBinding.inflate(LayoutInflater.from(context))
        val popupWindow = PopupWindow(
            binding.root,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.isOutsideTouchable = true
        popupWindow.animationStyle = R.style.PopupFadeAnimation

        // -- Handle actions
        binding.actionOpen.setOnClickListener {
            popupWindow.dismiss()
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    viewModel.loadTemplateFromJsonFile(item, requireContext())
                    bundle = Bundle().apply {
                        putSerializable("canvas_size", item.canvasSize)
                        putSerializable("unit_type", UnitType.PIXELS)
                    }
                }
            }
        }

        binding.actionDuplicate.setOnClickListener {
            popupWindow.dismiss()
            lifecycleScope.launch { mainViewModel.insertExportResult(item.copy()) }
        }

        binding.actionShare.setOnClickListener {
            popupWindow.dismiss()
            // Share logic
        }

        binding.actionRename.setOnClickListener {
            popupWindow.dismiss()
            // Rename logic
        }

        binding.actionDelete.setOnClickListener {
            popupWindow.dismiss()
            mainViewModel.deleteExportResult(item)
        }

        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)
    }

    override fun onResume() {
        super.onResume()
        if (findNavController().currentDestination?.id!! != R.id.editorFragment) {
            viewModel.clearCanvas()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}