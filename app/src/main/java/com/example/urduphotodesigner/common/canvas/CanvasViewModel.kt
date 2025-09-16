package com.example.urduphotodesigner.common.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.urduphotodesigner.R
import com.example.urduphotodesigner.common.canvas.enums.BlendType
import com.example.urduphotodesigner.common.canvas.enums.ElementType
import com.example.urduphotodesigner.common.canvas.enums.GradientPickerTarget
import com.example.urduphotodesigner.common.canvas.enums.GradientType
import com.example.urduphotodesigner.common.canvas.enums.LabelShape
import com.example.urduphotodesigner.common.canvas.enums.LetterCasing
import com.example.urduphotodesigner.common.canvas.enums.ListStyle
import com.example.urduphotodesigner.common.canvas.enums.PickerTarget
import com.example.urduphotodesigner.common.canvas.enums.TextAlignment
import com.example.urduphotodesigner.common.canvas.enums.TextDecoration
import com.example.urduphotodesigner.common.canvas.enums.UnitType
import com.example.urduphotodesigner.common.canvas.model.CanvasElement
import com.example.urduphotodesigner.common.canvas.model.CanvasSize
import com.example.urduphotodesigner.common.canvas.model.ExportFormat
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import com.example.urduphotodesigner.common.canvas.model.ExportQuality
import com.example.urduphotodesigner.common.canvas.model.ExportResolution
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.sealed.BatchedCanvasAction
import com.example.urduphotodesigner.common.canvas.sealed.CanvasAction
import com.example.urduphotodesigner.common.canvas.sealed.ImageFilter
import com.example.urduphotodesigner.common.datastore.PreferenceDataStoreKeysConstants
import com.example.urduphotodesigner.common.datastore.PreferencesDataStoreHelper
import com.example.urduphotodesigner.common.utils.ImageProcessor
import com.example.urduphotodesigner.common.views.CanvasView
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.domain.usecase.GetFontsUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Stack
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val getFontsUseCase: GetFontsUseCase,
    private val gson: Gson,
    private val dataStore: PreferencesDataStoreHelper
) : ViewModel() {
    private val _pagingLocked = MutableLiveData(false)
    val pagingLocked: LiveData<Boolean> = _pagingLocked
    private val _isLoadingTemplate = MutableLiveData<Boolean?>()
    val isLoadingTemplate: LiveData<Boolean?> = _isLoadingTemplate
    private val _loadingStage = MutableLiveData<Pair<String, Int>>()
    val loadingStage: LiveData<Pair<String, Int>> = _loadingStage
    private val _canvasActions = Stack<CanvasAction>()
    private val _redoStack = Stack<CanvasAction>()
    private val _canvasElements = MutableLiveData<List<CanvasElement>>(emptyList())
    val canvasElements: MutableLiveData<List<CanvasElement>> = _canvasElements
    private val _selectedElements = MutableLiveData<List<CanvasElement>>(emptyList())
    val selectedElements: LiveData<List<CanvasElement>> = _selectedElements
    private val _exportOptions = MutableLiveData<ExportOptions>()
    val exportOptions: LiveData<ExportOptions> = _exportOptions

    private val _activePicker = MutableLiveData<PickerTarget?>(null)
    val activePicker: LiveData<PickerTarget?> = _activePicker
    private val _activeGradientPicker = MutableLiveData<GradientPickerTarget?>(null)
    private val _localFonts = MutableStateFlow<List<FontEntity>>(emptyList())
    private val localFonts: StateFlow<List<FontEntity>> = _localFonts.asStateFlow()
    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> get() = _canUndo
    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> get() = _canRedo
    private val _backgroundColor = MutableLiveData(Color.WHITE) // Initialize with a default color
    val backgroundColor: LiveData<Int> = _backgroundColor
    private val _backgroundImage = MutableLiveData<Bitmap?>()
    val backgroundImage: LiveData<Bitmap?> = _backgroundImage
    private val _backgroundGradient = MutableLiveData<GradientItem?>()
    val backgroundGradient: MutableLiveData<GradientItem?> = _backgroundGradient
    private val _currentFont = MutableLiveData<FontEntity?>()
    val currentFont: LiveData<FontEntity?> = _currentFont
    private val _currentTextColor = MutableLiveData(Color.BLACK)
    val currentTextColor: LiveData<Int> = _currentTextColor
    private val _currentTextSize = MutableLiveData(40f)  // Initialize with a default size
    val currentTextSize: LiveData<Float> = _currentTextSize

    private val _currentTextAlignment = MutableLiveData(TextAlignment.CENTER)
    val currentTextAlignment: LiveData<TextAlignment> = _currentTextAlignment

    private val _canvasSize = MutableLiveData<CanvasSize?>()
    val canvasSize: LiveData<CanvasSize?> = _canvasSize

    private val _canvasUnit = MutableLiveData<UnitType?>(UnitType.PIXELS)
    val canvasUnit: LiveData<UnitType?> = _canvasUnit

    private val _currentImageFilter = MutableLiveData<ImageFilter?>(null)
    val currentImageFilter: LiveData<ImageFilter?> = _currentImageFilter

    private val _fillGradient = MutableLiveData<GradientItem?>()
    val fillGradient: LiveData<GradientItem?> = _fillGradient

    // Stroke gradient
    private val _strokeGradient = MutableLiveData<GradientItem?>()

    // Stroke gradient
    private val _labelGradient = MutableLiveData<GradientItem?>()
    val labelGradient: LiveData<GradientItem?> = _labelGradient

    // 🔷 Shadow
    private val _hasShadow = MutableLiveData(false)

    private val _shadowColor = MutableLiveData(Color.GRAY)
    val shadowColor: LiveData<Int> = _shadowColor

    private val _shadowDx = MutableLiveData(1f)
    val shadowDx: LiveData<Float> = _shadowDx

    private val _shadowDy = MutableLiveData(1f)
    val shadowDy: LiveData<Float> = _shadowDy

    private val _shadowRadius = MutableLiveData(8f)
    val shadowRadius: LiveData<Float> = _shadowRadius

    private val _shadowOpacity = MutableLiveData(64)
    val shadowOpacity: LiveData<Int> = _shadowOpacity

    private val _blurValue = MutableLiveData(10f) // Default blur value
    val blurValue: LiveData<Float> = _blurValue

    private val _opacity = MutableLiveData(255) // Default opacity
    val opacity: LiveData<Int> = _opacity

    private val _hasBlur = MutableLiveData(false)
    val hasChanges = MutableLiveData(false)

    private val _blendingType = MutableLiveData(BlendType.SRC_OVER) // Default blend type
    val blendingType: LiveData<BlendType> = _blendingType

    // 🔷 Border
    private val _hasBorder = MutableLiveData(false)

    private val _borderColor = MutableLiveData(Color.BLACK)
    val borderColor: LiveData<Int> = _borderColor

    private val _borderWidth = MutableLiveData(1f)
    val borderWidth: LiveData<Float> = _borderWidth

    // 🔷 Label
    private val _hasLabel = MutableLiveData(false)

    private val _labelColor = MutableLiveData(Color.YELLOW)
    val labelColor: LiveData<Int> = _labelColor

    private val _labelShape = MutableLiveData(LabelShape.RECTANGLE_FILL)
    val labelShape: LiveData<LabelShape> = _labelShape

    private val _lineSpacing = MutableLiveData(1.0f)
    val lineSpacing: LiveData<Float> = _lineSpacing

    private val _letterSpacing = MutableLiveData(0f)
    val letterSpacing: LiveData<Float> = _letterSpacing

    private val _letterCasing = MutableLiveData(LetterCasing.NONE)
    val letterCasing: LiveData<LetterCasing> = _letterCasing

    private val _kasheeda = MutableLiveData(0)
    val kasheeda: LiveData<Int> = _kasheeda

    private val _textDecoration = MutableLiveData(setOf(TextDecoration.NONE))
    val textDecoration: LiveData<Set<TextDecoration>> = _textDecoration

    private val _textAlignment = MutableLiveData(TextAlignment.CENTER)
    val textAlignment: LiveData<TextAlignment> = _textAlignment

    private val _paragraphIndentation = MutableLiveData(0f)
    val paragraphIndentation: LiveData<Float> = _paragraphIndentation

    private val _listStyle = MutableLiveData(ListStyle.NONE)
    val listStyle: LiveData<ListStyle> = _listStyle

    private val _groupId = MutableLiveData<String?>()

    // Track the selected group ID for grouping operations
    private val _currentGroupId = MutableLiveData<String?>()

    private val _exportResult = MutableLiveData<ExportResult?>()
    val exportResult: LiveData<ExportResult?> = _exportResult

    private val _inSelectionMode = MutableLiveData(false)
    val inSelectionMode: LiveData<Boolean> get() = _inSelectionMode

    fun setExportResult(result: ExportResult) {
        Log.d("CanvasVM", "Setting ExportResult: $result")
        _exportResult.value = result
        Log.d("CanvasVM", "Post ExportResult: ${_exportResult.value}")
    }

    private var selectedElement: CanvasElement? = null
    private var currentBatchAction: BatchedCanvasAction? = null
    private var _isExplicitChange = false

    val availableResolutions = listOf(
        ExportResolution("Original", 0, 0, 1f, "Native", "Keep original size", 2500),
        ExportResolution("HD", 0, 0, 2f, "1280 x 720", "Standard quality", 800),
        ExportResolution("Full HD", 0, 0, 3f, "1920 x 1080", "High quality", 1200),
        ExportResolution("4k", 0, 0, 4f, "3840 x 2160", "Ultra quality", 4800)
    )

    val qualityOptions = listOf(
        ExportQuality("High Quality", 100, "Maximum quality, larger file size", 40),
        ExportQuality("Medium Quality", 75, "Balanced quality and size", 0),
        ExportQuality("Low Quality", 50, "Faster export, smaller size", -30)
    )

    val formatOptions = listOf(
        ExportFormat(
            "PNG",
            Bitmap.CompressFormat.PNG,
            "Lossless format",
            listOf("Transparent", "High Quality", "Larger Size")
        ), ExportFormat(
            "JPEG",
            Bitmap.CompressFormat.JPEG,
            "Compressed, smaller size",
            listOf("Small size", "Good for photos", "No transparency")
        ), ExportFormat(
            "WEBP",
            Bitmap.CompressFormat.WEBP,
            "Modern format with balance",
            listOf("Efficient", "Web Friendly", "Small & sharp")
        ), ExportFormat(
            "PDF",
            null,
            "Portable Document Format",
            listOf("Vector container", "Shareable", "Multi-page capable")
        )
    )

    private val _gradient = MutableLiveData(
        GradientItem(
            colors = listOf(Color.BLACK, Color.GRAY),
            positions = listOf(0f, 1f),
            angle = 0f,
            scale = 1f,
            type = GradientType.LINEAR
        )
    )
    val gradient: LiveData<GradientItem> = _gradient

    private val _gradientStopColor = MediatorLiveData(Color.BLACK)
    val gradientStopColor: LiveData<Int> = _gradientStopColor

    private val _selectedStopIndex = MutableLiveData<Int?>(null)

    private val _canvasView = MutableLiveData<CanvasView>()
    val canvasView: LiveData<CanvasView> = _canvasView

    fun markChanged() {
        hasChanges.value = true
    }

    fun setCanvasView(view: CanvasView) {
        _canvasView.value = view
    }

    fun getCanvasView(): CanvasView? {
        return _canvasView.value
    }

    init {
        observeLocalFonts()

        // Gradient color observation (unchanged)
        _gradientStopColor.addSource(_gradient) { gradient ->
            _selectedStopIndex.value?.let { idx ->
                if (idx in gradient.colors.indices) {
                    _gradientStopColor.value = gradient.colors[idx]
                }
            }
        }
        _gradientStopColor.addSource(_selectedStopIndex) { idx ->
            _gradient.value?.let { gradient ->
                if (idx != null && idx in gradient.colors.indices) {
                    _gradientStopColor.value = gradient.colors[idx]
                }
            }
        }
    }

    fun fetchExportOptionsFromDataStore() {
        viewModelScope.launch {
            val resName =
                dataStore.getFirstPreference(PreferenceDataStoreKeysConstants.KEY_RESOLUTION, "")
            val qualityLabel =
                dataStore.getFirstPreference(PreferenceDataStoreKeysConstants.KEY_QUALITY, "")
            val formatName =
                dataStore.getFirstPreference(PreferenceDataStoreKeysConstants.KEY_FORMAT, "")

            val res =
                availableResolutions.find { it.name == resName } ?: availableResolutions.first()
            val quality = qualityOptions.find { it.label == qualityLabel } ?: qualityOptions.first()
            val format = formatOptions.find { it.name == formatName } ?: formatOptions.first()

            val newOptions = ExportOptions(res, quality, format)
            updateExportOptionsInMemory(newOptions)
        }
    }

    fun enterSelectionMode() {
        _inSelectionMode.value = true
    }

    fun exitSelectionMode() {
        _inSelectionMode.value = false
    }

    // 2. Update only ViewModel (temporary, no save)
    fun updateExportOptionsInMemory(newOptions: ExportOptions) {
        markSelections(newOptions)
        _exportOptions.value = newOptions
    }

    // 3. Update ViewModel + Save into DataStore
    fun updateExportOptionsAndSave(newOptions: ExportOptions) {
        markSelections(newOptions)
        _exportOptions.value = newOptions

        viewModelScope.launch {
            dataStore.putPreference(
                PreferenceDataStoreKeysConstants.KEY_RESOLUTION, newOptions.resolution.name
            )
            dataStore.putPreference(
                PreferenceDataStoreKeysConstants.KEY_QUALITY, newOptions.quality.label
            )
            dataStore.putPreference(
                PreferenceDataStoreKeysConstants.KEY_FORMAT, newOptions.format.name
            )
        }
    }

    // 4. Reset everything to defaults (no save)
    fun resetExportOptions() {
        val defaults = ExportOptions(
            availableResolutions.first(), qualityOptions.first(), formatOptions.first()
        )
        updateExportOptionsInMemory(defaults)
    }

    // helper to mark selected
    private fun markSelections(newOptions: ExportOptions) {
        availableResolutions.forEach { it.isSelected = it == newOptions.resolution }
        qualityOptions.forEach { it.isSelected = it == newOptions.quality }
        formatOptions.forEach { it.isSelected = it == newOptions.format }
    }

    fun setPagingLocked(locked: Boolean) {
        _pagingLocked.value = locked
    }

    /**
     * Remove the stop at [index], if valid.
     * Clears the selection if it was the removed stop.
     */
    fun removeStop(index: Int) {
        val item = _gradient.value ?: return
        val c = item.colors.toMutableList()
        val p = item.positions.toMutableList()

        // only remove if we have more than two stops (to keep a valid gradient)
        if (c.size <= 2 || index !in c.indices) return

        c.removeAt(index)
        p.removeAt(index)
        _gradient.value = item.copy(colors = c, positions = p)

        // if the removed stop was selected, clear selection
        if (_selectedStopIndex.value == index) {
            _selectedStopIndex.value = null
        } else if (_selectedStopIndex.value != null && _selectedStopIndex.value!! > index) {
            // shift selection down if it was after the removed index
            _selectedStopIndex.value = _selectedStopIndex.value!! - 1
        }
    }

    /**
     * Remove whichever stop is currently selected (if any).
     */
    fun removeSelectedStop() {
        val idx = _selectedStopIndex.value ?: return
        removeStop(idx)
        // clear selection once removed
        _selectedStopIndex.value = null
    }

    fun swapGradientStops() {
        _gradient.value = _gradient.value?.swapped()
    }

    fun setGradient(gradientItem: GradientItem) {
        _gradient.value = gradientItem
    }

    fun clearGradient() {
        _gradient.value = GradientItem(
            colors = listOf(Color.BLACK, Color.GRAY),
            positions = listOf(0f, 1f),
            angle = 0f,
            scale = 1f,
            type = GradientType.LINEAR
        )
    }

    /** Call when the user taps on an empty spot and you want to add a stop */
    fun addStop(position: Float, sampledColor: Int) {
        val item = _gradient.value ?: return
        val (c, p) = insertAt(item, position to sampledColor)
        _gradient.value = item.copy(colors = c, positions = p)
        // auto-select new stop
        _selectedStopIndex.value = c.indexOf(sampledColor)
    }

    /** Call when the user drags a handle to a new position */
    fun moveStop(index: Int, newPosition: Float) {
        val item = _gradient.value ?: return
        val c = item.colors.toMutableList()
        val p = item.positions.toMutableList()
        if (index in p.indices) {
            p[index] = newPosition.coerceIn(0f, 1f)
            _gradient.value = item.copy(colors = c, positions = p)
        }
    }

    /** Call when the user taps an existing handle */
    fun selectStop(index: Int) {
        _selectedStopIndex.value = index
    }

    /** Call after the color‐picker fragment returns a new color */
    fun updateSelectedStopColor(newColor: Int) {
        val idx = _selectedStopIndex.value ?: return
        val item = _gradient.value ?: return
        val c = item.colors.toMutableList()
        if (idx in c.indices) {
            c[idx] = newColor
            _gradient.value = item.copy(colors = c)
        }
    }


    /** Switch between LINEAR / RADIAL / SWEEP */
    fun setType(type: GradientType) {
        val item = _gradient.value ?: return
        _gradient.value = item.copy(type = type)
    }

    fun updateGradient(
        scale: Float,
        angle: Float,
        sweepStartAngle: Float,
        radialRadiusFactor: Float,
        centerX: Float,
        centerY: Float,
    ) {
        _gradient.value = _gradient.value?.copy(
            scale = scale,
            angle = angle,
            sweepStartAngle = sweepStartAngle,
            radialRadiusFactor = radialRadiusFactor,
            centerX = centerX,
            centerY = centerY,
        )
    }

    fun selectElementForGrouping() {
        if (_selectedElements.value?.isNotEmpty() == true) {
            // Create a new group ID
            val newGroupId = UUID.randomUUID().toString()
            _currentGroupId.value = newGroupId

            // Update the groupId of selected elements
            _selectedElements.value?.forEach { element ->
                element.groupId = newGroupId
            }

            // Notify observer to update the canvas
            _canvasElements.value = _canvasElements.value
            // After updating the groupId, refresh the canvas view with the updated list of elements.
        }
    }

    fun ungroupElements() {
        // Remove the group ID and reset selected elements
        _canvasElements.value = _canvasElements.value?.map { element ->
            if (element.groupId == _currentGroupId.value) {
                element.copy(groupId = null)
            } else {
                element
            }
        }
        // Reset selection and clear the group ID
        _currentGroupId.value = null
        _selectedElements.value = emptyList()
        // Notify observer to update the canvas
    }

    private fun insertAt(
        item: GradientItem, newEntry: Pair<Float, Int>
    ): Pair<List<Int>, List<Float>> {
        val (pos, color) = newEntry
        val c = item.colors.toMutableList()
        val p = item.positions.toMutableList()
        val idx = p.indexOfFirst { it > pos }.takeIf { it >= 0 } ?: p.size
        c.add(idx, color)
        p.add(idx, pos.coerceIn(0f, 1f))
        return c to p
    }

    private fun observeLocalFonts() {
        viewModelScope.launch {
            getFontsUseCase().collect { fonts ->
                _localFonts.value = fonts
                // After fonts are loaded, re-apply typeface to existing elements if any
                _canvasElements.value?.let { currentElements ->
                    _canvasElements.value = currentElements.map { element ->
                        // Create a copy to ensure its paint is re-initialized with context
                        val updatedElement = element.copy(context = element.context)
                        if (updatedElement.type == ElementType.TEXT && updatedElement.fontId != null) {
                            val font = fonts.find { it.id.toString() == updatedElement.fontId }
                            if (font != null && font.file_path?.isNotBlank() == true) {
                                try {
                                    updatedElement.paint.typeface =
                                        Typeface.createFromFile(font.file_path)
                                } catch (e: Exception) {
                                    println("Error re-applying typeface in observeLocalFonts: ${font.file_path}. Error: ${e.message}")
                                    updatedElement.paint.typeface = updatedElement.context?.let {
                                        ResourcesCompat.getFont(
                                            it, R.font.regular
                                        )
                                    } ?: Typeface.DEFAULT
                                }
                            } else {
                                updatedElement.paint.typeface = updatedElement.context?.let {
                                    ResourcesCompat.getFont(
                                        it, R.font.regular
                                    )
                                } ?: Typeface.DEFAULT
                            }
                        } else {
                            // Ensure non-text elements or text elements without fontId also have a default typeface if applicable
                            updatedElement.paint.typeface = updatedElement.context?.let {
                                ResourcesCompat.getFont(
                                    it, R.font.regular
                                )
                            } ?: Typeface.DEFAULT
                        }
                        updatedElement
                    }
                }
            }
        }
    }

    fun startPicking(slot: PickerTarget) {
        if (_activePicker.value == null) {
            _activePicker.value = slot
        } else {
            _activePicker.value = null
        }
    }

    fun stopPicking() {
        _activePicker.value = null
    }

    fun startPickingGradient(slot: GradientPickerTarget) {
        if (_activeGradientPicker.value == null) {
            _activeGradientPicker.value = slot
        } else {
            _activeGradientPicker.value = null
        }
    }

    fun stopPickingGradient() {
        _activeGradientPicker.value = null
    }

    /** Call this when the CanvasView fires “I just picked this color: 0xAARRGGBB” */
    fun finishPicking(color: Int) {
        when (_activePicker.value) {
            PickerTarget.EYE_DROPPER_BACKGROUND -> setCanvasBackgroundColor(color)
            PickerTarget.EYE_DROPPER_TEXT_FILL -> setTextColor(color)
            PickerTarget.EYE_DROPPER_TEXT_STROKE -> setTextBorder(
                true, color, _borderWidth.value!!
            )

            PickerTarget.EYE_DROPPER_SHADOW -> setTextShadow(
                true, color, _shadowDx.value!!, _shadowDy.value!!
            )

            PickerTarget.EYE_DROPPER_LABEL -> setTextLabel(true, color, _labelShape.value!!)
            PickerTarget.COLOR_PICKER_BACKGROUND -> setCanvasBackgroundColor(color)
            PickerTarget.COLOR_PICKER_TEXT_FILL -> setTextColor(color)
            PickerTarget.COLOR_PICKER_TEXT_STROKE -> setTextBorder(
                true, color, _borderWidth.value!!
            )

            PickerTarget.COLOR_PICKER_SHADOW -> setTextShadow(
                true, color, _shadowDx.value!!, _shadowDy.value!!
            )

            PickerTarget.COLOR_PICKER_LABEL -> setTextLabel(true, color, _labelShape.value!!)
            PickerTarget.COLOR_PICKER_GRADIENT -> {
                _gradientStopColor.value = color
            }

            PickerTarget.EYE_DROPPER_GRADIENT -> {
                _gradientStopColor.value = color
            }

            null -> { /* nothing to do */
            }
        }
    }

    fun finishPickingGradient(gradientItem: GradientItem?) {
        when (_activeGradientPicker.value) {
            GradientPickerTarget.TEXT_FILL -> if (gradientItem != null) setTextFillGradient(
                gradientItem
            ) else clearFillGradients()

            GradientPickerTarget.TEXT_STROKE -> if (gradientItem != null) setTextStrokeGradient(
                gradientItem, _borderWidth.value ?: 1f
            ) else clearStrokeGradients()

            GradientPickerTarget.TEXT_LABEL -> if (gradientItem != null) setTextLabelGradient(
                true, _labelShape.value ?: LabelShape.RECTANGLE_FILL, gradientItem
            ) else clearLabelGradients()

            GradientPickerTarget.BACKGROUND -> if (gradientItem != null) setCanvasGradient(
                gradientItem
            ) else removeCanvasGradient()

            null -> {}
        }
    }

    fun setLineSpacing(spacing: Float) {
        _lineSpacing.value = spacing
        applyChangesToSelectedTextElements()
    }

    fun setLetterSpacing(spacing: Float) {
        _letterSpacing.value = spacing
        applyChangesToSelectedTextElements()
    }

    fun setLetterCasing(casing: LetterCasing) {
        _letterCasing.value = casing
        applyChangesToSelectedTextElements()
    }

    fun setKasheeda(kasheeda: Int) {
        _kasheeda.value = kasheeda
        applyChangesToSelectedTextElements()
    }

    fun setTextDecoration(decorations: Set<TextDecoration>) {
        _textDecoration.value = decorations
        applyChangesToSelectedTextElements()
    }

    fun setTextAlignment(alignment: TextAlignment) {
        _textAlignment.value = alignment
        applyChangesToSelectedTextElements()
    }

    fun setIndentNone() {
        _paragraphIndentation.value = 0f
        applyChangesToSelectedTextElements()
    }

    fun increaseIndent() {
        val currentIndent = _paragraphIndentation.value ?: 0f
        _paragraphIndentation.value = currentIndent + 5f
        applyChangesToSelectedTextElements()
    }

    fun decreaseIndent() {
        val currentIndent = _paragraphIndentation.value ?: 0f
        _paragraphIndentation.value = currentIndent - 5f
        applyChangesToSelectedTextElements()
    }

    fun setListStyle(style: ListStyle) {
        _listStyle.value = style
        applyChangesToSelectedTextElements()
    }

    fun setTextFillGradient(gradientItem: GradientItem) {
        _fillGradient.value = gradientItem
        applyChangesToSelectedTextElements()
    }

    fun setBlurValue(value: Float) {
        _hasBlur.value = value > 0
        _blurValue.value = value
        applyChangesToSelectedTextElements()
    }

    fun setOpacityValue(value: Int) {
        _opacity.value = value
        applyChangesToSelectedTextElements()
    }

    fun setBlendingType(type: BlendType) {
        _blendingType.value = type
        applyChangesToSelectedTextElements()
    }

    /** Call this when the user selects a new text‐stroke gradient */
    fun setTextStrokeGradient(gradientItem: GradientItem, width: Float) {
        _borderWidth.value = width
        _hasBorder.value = true
        _strokeGradient.value = gradientItem
        applyChangesToSelectedTextElements()
    }

    fun clearFillGradients() {
        _fillGradient.value = null
        applyChangesToSelectedTextElements()
    }

    fun clearStrokeGradients() {
        _borderWidth.value = 0f
        _hasBorder.value = false
        _strokeGradient.value = null
        applyChangesToSelectedTextElements()
    }

    fun setTextLabelGradient(
        enabled: Boolean, shape: LabelShape, gradientItem: GradientItem
    ) {
        _labelGradient.value = gradientItem
        _labelShape.value = shape
        _hasLabel.value = enabled
        applyChangesToSelectedTextElements()
    }

    fun setTextLabel(enabled: Boolean, color: Int, shape: LabelShape) {
        _labelColor.value = color
        _labelShape.value = shape
        _hasLabel.value = enabled
        applyChangesToSelectedTextElements()
    }

    fun clearLabelGradients() {
        _hasLabel.value = false
        _labelGradient.value = null
        applyChangesToSelectedTextElements()
    }

    fun setTextSizeForAllSelected(size: Float) {
        val currentList = _canvasElements.value ?: return
        val selectedElements = currentList.filter { it.isSelected }

        // If there are no selected elements, do nothing.
        if (selectedElements.isEmpty()) return

        // Prepare to store old sizes for undo/redo purposes
        val oldSizes = selectedElements.map { it.paintTextSize }

        // Update the font size for each selected element
        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                element.copy().apply {
                    // Apply new font size
                    paintTextSize = size
                    paint.textSize = size
                    paint.typeface = applyTypefaceFromFontList() // Reapply the typeface if needed
                }
            } else {
                element
            }
        }

        // Update the canvas with the new list of elements
        _canvasElements.value = updatedList

        // Push the undo action for all updated elements
        selectedElements.forEachIndexed { idx, oldElement ->
            val newElement = updatedList.find { it.id == oldElement.id }!!
            _canvasActions.push(
                CanvasAction.UpdateElement(
                    elementId = newElement.id,
                    newElement = newElement.copy(context = null, bitmap = null),
                    oldElement = oldElement.copy(paintTextSize = oldSizes[idx]) // Revert back to old size on undo
                )
            )
        }

        // Clear redo stack after applying changes
        _redoStack.clear()

        // Notify UI to update undo/redo status
        notifyUndoRedoChanged()
    }

    private fun applyChangesToSelectedTextElements() {
        val currentList = _canvasElements.value?.toMutableList() ?: return

        var oldElement: CanvasElement? = null
        var newElement: CanvasElement? = null
        var targetId: String? = null

        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                oldElement = element.copy(context = null, bitmap = null)
                targetId = element.id

                val updated = element.copy(
                    lineSpacing = _lineSpacing.value ?: element.lineSpacing,
                    letterSpacing = _letterSpacing.value ?: element.letterSpacing,
                    letterCasing = _letterCasing.value ?: element.letterCasing,
                    textDecoration = _textDecoration.value ?: element.textDecoration,
                    alignment = _textAlignment.value ?: element.alignment,
                    currentIndent = _paragraphIndentation.value ?: element.currentIndent,
                    listStyle = _listStyle.value ?: element.listStyle,

                    hasShadow = _hasShadow.value ?: element.hasShadow,
                    shadowColor = _shadowColor.value ?: element.shadowColor,
                    shadowDx = _shadowDx.value ?: element.shadowDx,
                    shadowDy = _shadowDy.value ?: element.shadowDy,
                    shadowRadius = _shadowRadius.value ?: element.shadowRadius,
                    shadowOpacity = _shadowOpacity.value ?: element.shadowOpacity,

                    hasStroke = _hasBorder.value ?: element.hasStroke,
                    strokeColor = _borderColor.value ?: element.strokeColor,
                    strokeWidth = _borderWidth.value ?: element.strokeWidth,

                    hasLabel = _hasLabel.value ?: element.hasLabel,
                    labelColor = _labelColor.value ?: element.labelColor,
                    labelShape = _labelShape.value ?: element.labelShape,

                    fillGradient = if (_fillGradient.value == null) null else _fillGradient.value
                        ?: element.fillGradient,

                    strokeGradient = if (_strokeGradient.value == null) null else _strokeGradient.value
                        ?: element.strokeGradient,

                    labelGradient = if (_labelGradient.value == null) null else _labelGradient.value
                        ?: element.labelGradient,

                    blurValue = _blurValue.value ?: element.blurValue,
                    hasBlur = _hasBlur.value ?: element.hasBlur,
                    paintAlpha = _opacity.value ?: element.paintAlpha,
                    blendType = _blendingType.value ?: element.blendType,
                    kashidaSize = _kasheeda.value ?: element.kashidaSize
                ).apply {
                    paint.typeface = element.applyTypefaceFromFontList()
                }

                newElement = updated.copy(context = null, bitmap = null)
                updated
            } else element
        }

        if (oldElement != null && newElement != null && targetId != null) {
            _canvasActions.push(
                CanvasAction.UpdateElement(
                    targetId!!, newElement!!, oldElement!!
                )
            )
            _redoStack.clear()
            notifyUndoRedoChanged()
        }

        _canvasElements.value = updatedList
    }

    /**
     * Copies all currently selected elements as a group, offset by a fixed delta.
     */
    fun copySelectedElementsGroup() {
        val currentList = _canvasElements.value ?: return
        val selected = currentList.filter { it.isSelected }
        if (selected.isEmpty()) return

        // Compute a group offset. For simplicity, offset all copies by +20f in x and y.
        val offsetX = 20f
        val offsetY = 20f

        // Prepare list of copied elements
        val copiedElements = selected.map { element ->
            val newId = UUID.randomUUID().toString()
            val copied = element.copy(
                id = newId,
                // Deselect copies by default:
                isSelected = false, groupId = null,
                // Offset position:
                x = element.x + offsetX, y = element.y + offsetY
            )
            // Re-apply paint/typeface if needed:
            copied.paint.typeface = copied.applyTypefaceFromFontList()
            copied
        }

        // Add all copies to the canvas
        _canvasElements.value = currentList + copiedElements

        // Push undo actions: you can push individually, or if you have a grouped action type, push once.
        // Here, pushing individually:
        copiedElements.forEach { copied ->
            if (copied.type == ElementType.TEXT) {
                _canvasActions.push(CanvasAction.AddText(copied.text, copied))
            } else {
                _canvasActions.push(CanvasAction.AddSticker(copied))
            }
        }
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun setCanvasSize(newSize: CanvasSize) {
        val oldSize = _canvasSize.value
        if (oldSize != newSize) {
            _canvasActions.push(
                CanvasAction.SetCanvasSize(
                    newSize, oldSize ?: newSize
                )
            ) // Push old size for undo
            _redoStack.clear()
            _canvasSize.value = newSize
            notifyUndoRedoChanged()
        }
    }

    fun setCanvasUnit(newUnit: UnitType) {
        _canvasUnit.value = newUnit
        notifyUndoRedoChanged()
    }

    fun endBatchUpdate(elementId: String) {
        val currentList = _canvasElements.value ?: emptyList()
        val finalElement = currentList.find { it.id == elementId }
            ?.copy(context = null, bitmap = null) // Capture final state for undo

        if (finalElement != null && currentBatchAction != null) {
            when (currentBatchAction) {
                is BatchedCanvasAction.DragBatch -> {
                    val initialElement =
                        (currentBatchAction as BatchedCanvasAction.DragBatch).initialElement
                    if (initialElement.x != finalElement.x || initialElement.y != finalElement.y) { // Only push if position changed
                        _canvasActions.push(
                            CanvasAction.UpdateElement(
                                elementId = elementId,
                                newElement = finalElement,
                                oldElement = initialElement
                            )
                        )
                    }
                }

                is BatchedCanvasAction.RotateBatch -> {
                    val initialElement =
                        (currentBatchAction as BatchedCanvasAction.RotateBatch).initialElement
                    if (initialElement.rotation != finalElement.rotation) { // Only push if rotation changed
                        _canvasActions.push(
                            CanvasAction.UpdateElement(
                                elementId = elementId,
                                newElement = finalElement,
                                oldElement = initialElement
                            )
                        )
                    }
                }

                is BatchedCanvasAction.ResizeBatch -> {
                    val initialElement =
                        (currentBatchAction as BatchedCanvasAction.ResizeBatch).initialElement
                    if (initialElement.scale != finalElement.scale) { // Only push if scale changed
                        _canvasActions.push(
                            CanvasAction.UpdateElement(
                                elementId = elementId,
                                newElement = finalElement,
                                oldElement = initialElement
                            )
                        )
                    }
                }
                // Add cases for other batch actions
                else -> { /* No specific batch action in progress */
                }
            }
            _redoStack.clear() // Clear redo stack on new action
            notifyUndoRedoChanged()
        }
        currentBatchAction = null // Clear the batch action
    }

    fun startBatchUpdate(elementId: String, actionType: String) {
        val currentList = _canvasElements.value ?: emptyList()
        val initialElement = currentList.find { it.id == elementId }
            ?.copy(context = null, bitmap = null) // Capture initial state for undo

        if (initialElement != null) {
            currentBatchAction = when (actionType) {
                "drag" -> BatchedCanvasAction.DragBatch(elementId, initialElement)
                "rotate" -> BatchedCanvasAction.RotateBatch(elementId, initialElement)
                "resize" -> BatchedCanvasAction.ResizeBatch(elementId, initialElement)
                else -> null
            }
        }
    }

    fun updateElement(updated: CanvasElement) {
        val currentList = _canvasElements.value ?: emptyList()
        val oldElement = currentList.find { it.id == updated.id }

        if (oldElement != null) {
            // Create a mutable copy to work with.
            // Pass the original context to ensure the paint's init block has it.
            val elementToUpdate = updated.copy(context = oldElement.context)

            // Explicitly re-apply the typeface if it's a TEXT element with a fontId
            if (elementToUpdate.type == ElementType.TEXT && elementToUpdate.fontId != null) {
                val font = localFonts.value.find { it.id.toString() == elementToUpdate.fontId }
                if (font != null && font.file_path?.isNotBlank() == true) {
                    elementToUpdate.paint.typeface = elementToUpdate.applyTypefaceFromFontList()
                } else {
                    // If font not found or path is blank, revert to default system font
                    elementToUpdate.paint.typeface = elementToUpdate.context?.let {
                        ResourcesCompat.getFont(
                            it, R.font.regular
                        )
                    } ?: Typeface.DEFAULT
                }
            } else {
                // Ensure non-text elements or text elements without fontId also have a default typeface if applicable
                elementToUpdate.paint.typeface =
                    elementToUpdate.context?.let { ResourcesCompat.getFont(it, R.font.regular) }
                        ?: Typeface.DEFAULT
            }


            // Replace the entire element with the updated version (now with the correct typeface)
            _canvasElements.value = currentList.map {
                if (it.id == elementToUpdate.id) elementToUpdate else it // Use elementToUpdate here
            }

            // Only push to undo stack if no batch action is in progress.
            // Continuous actions (drag, rotate, resize) will be handled by endBatchUpdate.
            if (currentBatchAction == null) {
                _canvasActions.push(
                    CanvasAction.UpdateElement(
                        elementId = elementToUpdate.id,
                        newElement = elementToUpdate.copy( // Copy for serialization, without transient data
                            context = null, bitmap = null
                        ),
                        oldElement = oldElement.copy( // Full copy without transient data
                            context = null, bitmap = null
                        )
                    )
                )
                _redoStack.clear()
                notifyUndoRedoChanged()
            }
        }
    }

    fun updateCanvasElementsOrderAndZIndex(reorderedList: List<CanvasElement>) {
        val oldList = _canvasElements.value ?: emptyList()

        // Create a new list with updated zIndex based on their position in the reorderedList
        val updatedList = reorderedList.mapIndexed { index, element ->
            // Pass context to ensure paint re-initialization can use it
            val copiedElement = element.copy(zIndex = index, context = element.context)
            // Re-apply font for text elements
            if (copiedElement.type == ElementType.TEXT && copiedElement.fontId != null) {
                val font = localFonts.value.find { it.id.toString() == copiedElement.fontId }
                if (font != null && font.file_path?.isNotBlank() == true) {
                    try {
                        copiedElement.paint.typeface = Typeface.createFromFile(font.file_path)
                    } catch (e: Exception) {
                        println("Error re-applying typeface in updateCanvasElementsOrderAndZIndex: ${font.file_path}. Error: ${e.message}")
                        copiedElement.paint.typeface = copiedElement.context?.let {
                            ResourcesCompat.getFont(
                                it, R.font.regular
                            )
                        } ?: Typeface.DEFAULT
                    }
                } else {
                    copiedElement.paint.typeface = copiedElement.context?.let {
                        ResourcesCompat.getFont(
                            it, R.font.regular
                        )
                    } ?: Typeface.DEFAULT
                }
            } else {
                copiedElement.paint.typeface =
                    copiedElement.context?.let { ResourcesCompat.getFont(it, R.font.regular) }
                        ?: Typeface.DEFAULT
            }
            copiedElement
        }

        _canvasActions.push(
            CanvasAction.UpdateCanvasElementsOrder(
                oldList.map {
                    it.copy(
                        context = null, bitmap = null
                    )
                }, // Store copies without transient data
                updatedList.map { it.copy(context = null, bitmap = null) })
        )
        _redoStack.clear()
        _canvasElements.value = updatedList
        notifyUndoRedoChanged()
    }

    fun setSelectedElements(elementsToSelect: List<CanvasElement>) {
        val currentElements = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val context = currentElements.firstOrNull()?.context
        val idsToSelect = elementsToSelect.map { it.id }.toSet()

        // Create updated list with new selections
        val updatedList = currentElements.map { element ->
            val copiedElement = element.copy(
                isSelected = idsToSelect.contains(element.id), context = context
            ).apply {
                // Set the appropriate font
                paint.typeface = if (type == ElementType.TEXT && fontId != null) {
                    applyTypefaceFromFontList()
                } else {
                    context?.let { ResourcesCompat.getFont(it, R.font.regular) } ?: Typeface.DEFAULT
                }
            }
            copiedElement
        }

        // Update the canvas elements LiveData once
        _canvasElements.value = updatedList

        // Set the selected elements
        _selectedElements.value = updatedList.filter { it.isSelected }

        refreshSelectedElements()

        // UI handling: Only sync formatting if one text element is selected, otherwise reset
        val selectedTextElements = elementsToSelect.filter { it.type == ElementType.TEXT }
        if (selectedTextElements.size == 1) {
            syncUiFormattingWithSelectedTextElement(selectedTextElements.first())
        } else {
            resetTextFormattingToDefault()
        }

        // Handle image filter for first selected image
        val firstSelectedImageElement =
            elementsToSelect.firstOrNull { it.type == ElementType.IMAGE }
        _currentImageFilter.value = firstSelectedImageElement?.imageFilter
    }

    private fun getTypefaceForElement(element: CanvasElement, context: Context?): Typeface {
        return if (element.type == ElementType.TEXT && element.fontId != null) {
            val font = localFonts.value.find { it.id.toString() == element.fontId }
            font?.file_path?.takeIf { it.isNotBlank() }?.let { path ->
                try {
                    Typeface.createFromFile(path)
                } catch (e: Exception) {
                    ResourcesCompat.getFont(context ?: return Typeface.DEFAULT, R.font.regular)
                        ?: Typeface.DEFAULT
                }
            } ?: ResourcesCompat.getFont(context ?: return Typeface.DEFAULT, R.font.regular)
            ?: Typeface.DEFAULT
        } else {
            ResourcesCompat.getFont(context ?: return Typeface.DEFAULT, R.font.regular)
                ?: Typeface.DEFAULT
        }
    }

    fun onCanvasSelectionChanged(selectedListFromCanvas: List<CanvasElement>) {
        val currentElements = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val context = currentElements.firstOrNull()?.context

        val selectedIds = selectedListFromCanvas.map { it.id }.toSet()

        val updatedList = currentElements.map { element ->
            val updated =
                element.copy(isSelected = selectedIds.contains(element.id), context = context)
            updated.paint.typeface = getTypefaceForElement(updated, context)
            updated
        }

        _canvasElements.value = updatedList
        refreshSelectedElements()

        val firstText = selectedListFromCanvas.firstOrNull { it.type == ElementType.TEXT }
        val firstImage = selectedListFromCanvas.firstOrNull { it.type == ElementType.IMAGE }

        if (firstText != null) {
            syncUiFormattingWithSelectedTextElement(firstText)
            _currentImageFilter.value = null
        } else {
            syncUiFormattingWithSelectedTextElement(firstImage)
            _currentImageFilter.value = firstImage?.imageFilter
        }
    }

    private fun syncUiFormattingWithSelectedTextElement(textElement: CanvasElement?) {
        if (textElement != null) {
            _currentFont.value = localFonts.value.find { font ->
                textElement.fontId != null && font.id.toString() == textElement.fontId
            }
            _currentTextColor.value = textElement.paintColor
            _currentTextSize.value = textElement.paintTextSize
            _currentTextAlignment.value = textElement.alignment

            _lineSpacing.value = textElement.lineSpacing
            _letterSpacing.value = textElement.letterSpacing
            _letterCasing.value = textElement.letterCasing
            _textDecoration.value = textElement.textDecoration
            _textAlignment.value = textElement.alignment
            _paragraphIndentation.value = textElement.currentIndent
            _listStyle.value = textElement.listStyle

            // 🟡 Shadow
            _hasShadow.value = textElement.hasShadow
            _shadowColor.value = textElement.shadowColor
            _shadowDx.value = textElement.shadowDx
            _shadowDy.value = textElement.shadowDy
            _shadowRadius.value = textElement.shadowRadius
            _shadowOpacity.value = textElement.shadowOpacity

            // 🟡 Border
            _hasBorder.value = textElement.hasStroke
            _borderColor.value = textElement.strokeColor
            _borderWidth.value = textElement.strokeWidth

            // 🟡 Label
            _hasLabel.value = textElement.hasLabel
            _labelColor.value = textElement.labelColor
            _labelShape.value = textElement.labelShape

            // 🟡 Gradients
            _fillGradient.value = textElement.fillGradient
            _strokeGradient.value = textElement.strokeGradient
            _labelGradient.value = textElement.labelGradient

            // 🟡 Blur and opacity settings
            _blurValue.value = textElement.blurValue
            _hasBlur.value = textElement.hasBlur
            _opacity.value = textElement.paintAlpha
            _blendingType.value = textElement.blendType
        } else {
            resetTextFormattingToDefault()
        }
    }

    private fun resetTextFormattingToDefault() {
        _currentFont.value = null
        _currentTextColor.value = Color.BLACK
        _currentTextSize.value = 40f
        _currentTextAlignment.value = TextAlignment.CENTER

        _lineSpacing.value = 1.0f
        _letterSpacing.value = 0f
        _letterCasing.value = LetterCasing.NONE
        _textDecoration.value = setOf(TextDecoration.NONE)
        _textAlignment.value = TextAlignment.CENTER
        _paragraphIndentation.value = 0f
        _listStyle.value = ListStyle.NONE

        // Reset Shadow
        _hasShadow.value = false
        _shadowColor.value = Color.GRAY
        _shadowDx.value = 1f
        _shadowDy.value = 1f

        // Reset Border
        _hasBorder.value = false
        _borderColor.value = Color.BLACK
        _borderWidth.value = 1f

        // Reset Label
        _hasLabel.value = false
        _labelColor.value = Color.YELLOW
        _labelShape.value = LabelShape.RECTANGLE_FILL

        // Reset Gradients
        _fillGradient.value = null
        _strokeGradient.value = null
        _labelGradient.value = null

        // Reset Blur
        _blurValue.value = 0f
        _hasBlur.value = false
        _opacity.value = 255
        _blendingType.value = BlendType.SRC_OVER
    }

    private fun refreshSelectedElements() {
        val currentList = _canvasElements.value ?: emptyList()
        // Filter the elements currently marked isSelected == true
        _selectedElements.value = currentList.filter { it.isSelected }
    }

    fun setCanvasBackgroundColor(color: Int) {
        val previousColor = _backgroundColor.value ?: Color.WHITE
        if (color != previousColor) {
            _canvasActions.push(CanvasAction.SetBackgroundColor(color, previousColor))
            _redoStack.clear()
            _backgroundColor.value = color
            _backgroundImage.value = null
            notifyUndoRedoChanged()
        }
    }

    fun setCanvasBackgroundImage(bitmap: Bitmap?) {
        val previousBitmap = _backgroundImage.value
        // Only push action if there's a change
        if (bitmap != previousBitmap) {
            _canvasActions.push(CanvasAction.SetBackgroundImage(bitmap, previousBitmap))
            _redoStack.clear()
            _backgroundImage.value = bitmap
            notifyUndoRedoChanged()
        }
    }

    fun setCanvasGradient(newGradient: GradientItem) {
        val previous = _backgroundGradient.value
        // Only push if actually changed
        if (newGradient != previous) {
            // record the change (new, old)
            _canvasActions.push(CanvasAction.SetBackgroundGradient(newGradient, previous))
            _redoStack.clear()
            _backgroundGradient.value = newGradient
            notifyUndoRedoChanged()
        }
    }

    fun removeCanvasGradient() {
        val previous = _backgroundGradient.value
        if (previous != null) {
            val defaultGradient = GradientItem()
            _canvasActions.push(CanvasAction.SetBackgroundGradient(defaultGradient, previous))
            _redoStack.clear()
            _backgroundGradient.value = defaultGradient
            notifyUndoRedoChanged()
        }
    }


    fun addSticker(bitmap: Bitmap?, context: Context) {
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f
        val element = CanvasElement(
            context = context,
            type = ElementType.IMAGE,
            bitmap = bitmap,
//            bitmapData = ImageProcessor.bitmapToFilePath(context, bitmap!!),
            bitmapData = ImageProcessor.bitmapToBase64(bitmap!!),
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintAlpha = 255,
            zIndex = newZIndex
        )
        // Ensure paint properties are set correctly after construction (including context)
        element.updatePaintProperties()

        _canvasActions.push(
            CanvasAction.AddSticker(
                element.copy(
                    context = null, bitmap = null
                )
            )
        ) // Push a copy for undo, without transient data
        _redoStack.clear()
        _canvasElements.value = currentList + element
        notifyUndoRedoChanged()
    }

    fun ensureBackgroundElement(context: Context) {
        // if we already have a background, do nothing
        if ((_canvasElements.value ?: emptyList()).any { it.type == ElementType.BACKGROUND }) return

        // otherwise create and insert one
        val bg = CanvasElement(
            context = context,
            type = ElementType.BACKGROUND,
            x = _canvasSize.value?.width!! / 2f,
            y = _canvasSize.value?.height!! / 2f,
            paintColor = Color.WHITE,
            fillGradient = null,
            bitmap = null
        ).apply {
            isLocked = true
            logicalContentWidth = _canvasSize.value?.width!!
            logicalContentHeight = _canvasSize.value?.height!!
            updatePaintProperties()
        }

        // prepend it so it’s always drawn first
        _canvasElements.value = listOf(bg) + (_canvasElements.value ?: emptyList())
    }

    fun addText(text: String, context: Context) {
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f
        val element = CanvasElement(
            context = context,
            type = ElementType.TEXT,
            text = text,
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintColor = Color.BLACK,
            paintTextSize = 40f,
            alignment = TextAlignment.CENTER,
            paintAlpha = 255,
            fontId = null,
            zIndex = newZIndex
        )

        element.updatePaintProperties()

        val action = CanvasAction.AddText(
            text, element.copy(context = null, bitmap = null)
        ) // Push a copy for undo, without transient data
        _canvasActions.push(action)
        _redoStack.clear()
        _canvasElements.value = currentList + element
        selectedElement = element
        notifyUndoRedoChanged()
    }

    fun addTextWithFont(text: String, fontEntity: FontEntity?, context: Context) {
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1

        // Create base element
        val element = CanvasElement(
            context = context,
            type = ElementType.TEXT,
            text = text,
            x = 1000f,
            y = 1000f,
            paintColor = Color.BLACK,
            paintTextSize = 150f,
            alignment = TextAlignment.CENTER,
            paintAlpha = 255,
            fontId = null,
            zIndex = newZIndex
        )

        // If a fontEntity was provided, try to apply it
        if (fontEntity != null) {
            element.fontId = fontEntity.id.toString()
            element.paint.typeface = try {
                Typeface.createFromFile(fontEntity.file_path)
            } catch (e: Exception) {
                println("Error applying font: ${fontEntity.file_path}. Error: ${e.message}")
                element.fontId = null
                ResourcesCompat.getFont(context, R.font.regular) ?: Typeface.DEFAULT
            }
        }

        // Update paint props after assigning typeface
        element.updatePaintProperties()

        // Push action for undo/redo (store a copy without transient fields)
        val action = CanvasAction.AddText(
            text, element.copy(context = null, bitmap = null)
        )
        _canvasActions.push(action)
        _redoStack.clear()

        // Add to list + mark selected
        _canvasElements.value = currentList + element
        selectedElement = element

        // If we added with a font, update _currentFont accordingly
        if (fontEntity != null) {
            _currentFont.value = fontEntity
        }

        notifyUndoRedoChanged()
    }

    fun setFont(fontEntity: FontEntity, isExplicit: Boolean = true) {
        _isExplicitChange = isExplicit
        val currentList = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val context = currentList.firstOrNull()?.context
        val affectedElementsData = mutableListOf<Pair<String, String?>>()

        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT && element.fontId != fontEntity.id.toString()) {
                affectedElementsData.add(element.id to element.fontId)
                element.copy(context = context).apply {
                    fontId = fontEntity.id.toString()
                    paint.typeface = try {
                        Typeface.createFromFile(fontEntity.file_path)
                    } catch (e: Exception) {
                        println("Error applying font: ${fontEntity.file_path}. Error: ${e.message}")
                        fontId = null
                        context?.let { ResourcesCompat.getFont(it, R.font.regular) }
                            ?: Typeface.DEFAULT
                    }
                }
            } else element
        }

        if (affectedElementsData.isNotEmpty()) {
            val selectedTextElements =
                updatedList.filter { it.isSelected && it.type == ElementType.TEXT }
            _currentFont.value = when {
                selectedTextElements.isEmpty() -> null
                selectedTextElements.all { it.fontId == fontEntity.id.toString() } -> fontEntity
                selectedTextElements.any { it.fontId == fontEntity.id.toString() } -> null // mixed
                else -> fontEntity
            }

            _canvasElements.value = updatedList
            _canvasActions.push(CanvasAction.SetFont(fontEntity, affectedElementsData))
            _redoStack.clear()
            _isExplicitChange = false
            notifyUndoRedoChanged()
        }
    }

    fun setTextShadow(enabled: Boolean, color: Int, dx: Float, dy: Float) {
        _shadowColor.value = color
        _shadowDx.value = dx
        _shadowDy.value = dy
        _hasShadow.value = enabled
        applyChangesToSelectedTextElements()
    }

    fun setShadowRadius(radius: Float) {
        _shadowRadius.value = radius
        applyChangesToSelectedTextElements()
    }

    fun setShadowOpacity(opacity: Int) {
        _shadowOpacity.value = opacity
        applyChangesToSelectedTextElements()
    }

    fun setTextBorder(enabled: Boolean, color: Int, width: Float) {
        _borderColor.value = color
        _borderWidth.value = width
        _hasBorder.value = enabled
        applyChangesToSelectedTextElements()
    }

    private fun CanvasElement.applyTypefaceFromFontList(): Typeface {
        return fontId?.let { id ->
            localFonts.value.firstOrNull { it.id.toString() == id }?.file_path?.takeIf { it.isNotBlank() }
                ?.let { Typeface.createFromFile(it) }
        } ?: context?.let { ResourcesCompat.getFont(it, R.font.regular) } ?: Typeface.DEFAULT
    }

    fun setTextColor(color: Int) {
        val currentList = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val context = currentList.firstOrNull()?.context
        var oldColor: Int? = null
        var targetElementId: String? = null

        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                oldColor = oldColor ?: element.paintColor
                targetElementId = targetElementId ?: element.id

                element.copy(context = context).apply {
                    paintColor = color
                    paint.color = color

                    // Apply correct typeface
                    paint.typeface = element.applyTypefaceFromFontList()
                }
            } else element
        }

        if (targetElementId != null) {
            _currentTextColor.value = color
            _canvasElements.value = updatedList
            _canvasActions.push(
                CanvasAction.SetTextColor(color, oldColor ?: Color.BLACK, targetElementId!!)
            )
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    /**
     * Applies opacity to all currently selected elements.
     */
    fun setOpacity(opacity: Int) {
        val currentList = _canvasElements.value ?: return
        val context = currentList.firstOrNull()?.context
        var oldOpacity: Int? = null
        var targetElementId: String? = null

        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                oldOpacity = oldOpacity ?: element.paintAlpha
                targetElementId = targetElementId ?: element.id

                element.copy(context = context).apply {
                    paintAlpha = opacity
                    paint.alpha = opacity
                    if (type == ElementType.TEXT) {
                        paint.typeface = element.applyTypefaceFromFontList()
                    }
                }
            } else element
        }

        if (targetElementId != null) {
            _opacity.value = opacity
            _canvasElements.value = updatedList
            _canvasActions.push(
                CanvasAction.SetOpacity(
                    opacity, oldOpacity ?: 255, targetElementId!!
                )
            )
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun updateText(element: CanvasElement) {
        val currentList = _canvasElements.value ?: return
        val textElement = currentList.find { it.id == element.id } ?: return
        val context = textElement.context
        val oldText = textElement.text

        val updatedElement = textElement.copy(text = element.text, context = context).apply {
            if (type == ElementType.TEXT) {
                paint.typeface = element.applyTypefaceFromFontList()
            }
        }

        _canvasElements.value = currentList.map { if (it.id == element.id) updatedElement else it }
        _canvasActions.push(
            CanvasAction.UpdateText(
                elementId = element.id, text = updatedElement.text, previousText = oldText
            )
        )
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    /**
     * Applies an image filter to the specified image element.
     * @param elementId The ID of the CanvasElement to apply the filter to.
     * @param newFilter The ImageFilter to apply.
     */
    fun applyImageFilter(
        elementId: String, newFilter: ImageFilter?, isExplicit: Boolean = true
    ) {
        _isExplicitChange = isExplicit
        val currentList = _canvasElements.value ?: return
        val targetElement =
            currentList.find { it.id == elementId && it.type == ElementType.IMAGE } ?: return

        val oldFilter = targetElement.imageFilter
        if (oldFilter != newFilter) {
            val context = targetElement.context
            val updatedElement = targetElement.copy(imageFilter = newFilter!!, context = context)

            _canvasElements.value =
                currentList.map { if (it.id == updatedElement.id) updatedElement else it }
            if (updatedElement.isSelected) _currentImageFilter.value = newFilter
            _canvasActions.push(CanvasAction.ApplyImageFilter(elementId, newFilter, oldFilter))
            _redoStack.clear()
            _isExplicitChange = false
            notifyUndoRedoChanged()
        }
    }

    /**
     * Toggle lock status on all selected elements.
     * If all selected are locked, this unlocks them; otherwise locks all.
     */
    fun toggleLockOnSelected() {
        val currentList = _canvasElements.value ?: return
        // Filter currently selected elements
        val selected = currentList.filter { it.isSelected }
        if (selected.isEmpty()) return

        // Determine target: if all locked, then unlock; else lock all
        val allLocked = selected.all { it.isLocked }
        val newLockState = !allLocked

        // Prepare old copies for undo
        val oldCopies = selected.map { it.copy(context = null, bitmap = null) }

        // Build updated list
        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                element.copy(isLocked = newLockState).apply {
                    if (type == ElementType.TEXT) {
                        paint.typeface = element.applyTypefaceFromFontList()
                    }
                }
            } else element
        }

        // Update LiveData
        _canvasElements.value = updatedList
        refreshSelectedElements()

        // Push undo actions for each element changed
        selected.forEachIndexed { idx, oldElem ->
            val newElem = updatedList.find { it.id == oldElem.id }!!
            _canvasActions.push(
                CanvasAction.UpdateElement(
                    elementId = newElem.id,
                    newElement = newElem.copy(context = null, bitmap = null),
                    oldElement = oldCopies[idx]
                )
            )
        }
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun toggleVisibilityOnSelected() {
        val currentList = _canvasElements.value ?: return
        // Gather selected IDs
        val selectedIds = currentList.filter { it.isSelected }.map { it.id }
        if (selectedIds.isEmpty()) return

        // Determine new visibility: if all selected are currently hidden, we’ll show; else hide.
        val allHidden = currentList.filter { it.id in selectedIds }.all { !it.isVisible }

        // Prepare old copies for undo
        val oldCopies = currentList.filter { it.id in selectedIds }
            .map { it.copy(context = null, bitmap = null) }

        // Build updated list: toggle only selected
        val updatedList = currentList.map { element ->
            if (element.id in selectedIds) {
                element.copy(isVisible = allHidden).also { toggled ->
                    toggled.updatePaintProperties()
                    if (toggled.type == ElementType.TEXT) {
                        toggled.paint.typeface = toggled.applyTypefaceFromFontList()
                    }
                }
            } else element
        }

        // Update LiveData
        _canvasElements.value = updatedList
        refreshSelectedElements()

        // Push undo actions
        oldCopies.forEachIndexed { _, oldElem ->
            val newElem = updatedList.first { it.id == oldElem.id }
            _canvasActions.push(
                CanvasAction.UpdateElement(
                    elementId = newElem.id,
                    newElement = newElem.copy(context = null, bitmap = null),
                    oldElement = oldElem
                )
            )
        }
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun toggleVisibility(element: CanvasElement) {
        val currentList = _canvasElements.value ?: return
        // Find the element in the list
        val idx = currentList.indexOfFirst { it.id == element.id }
        if (idx == -1) return

        // Prepare old copy for undo
        val oldElem = currentList[idx]
        val oldCopy = oldElem.copy(context = null, bitmap = null)

        // Toggle the isVisible flag
        val newVisible = !oldElem.isVisible
        val updatedElem = oldElem.copy(isVisible = newVisible).apply {
            // Update paint properties so rendering reflects visibility
            updatePaintProperties()
            if (type == ElementType.TEXT) {
                paint.typeface = applyTypefaceFromFontList()
            }
        }

        // Build updated list
        val updatedList = currentList.toMutableList().also {
            it[idx] = updatedElem
        }

        // Update LiveData
        _canvasElements.value = updatedList

        // If you track selectedElements or other LiveData, refresh if needed:
        refreshSelectedElements()

        // Push undo action
        _canvasActions.push(
            CanvasAction.UpdateElement(
                elementId = updatedElem.id,
                newElement = updatedElem.copy(context = null, bitmap = null),
                oldElement = oldCopy
            )
        )
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun removeSelectedElements() {
        val currentList = _canvasElements.value ?: return
        val toRemove = currentList.filter { it.isSelected }
        if (toRemove.isEmpty()) return

        // Push a grouped action for undo if desired; here we push each individually:
        toRemove.forEach { elem ->
            val element = elem.copy(context = null, bitmap = null)
            element.paint.typeface = element.applyTypefaceFromFontList()
            _canvasActions.push(CanvasAction.RemoveElement(element))
        }
        _redoStack.clear()

        // Remove them all in one filter:
        _canvasElements.value = currentList.filter { !it.isSelected }
        refreshSelectedElements()
        selectedElement = null
        notifyUndoRedoChanged()
    }

    fun removeElement(element: CanvasElement) {
        val currentList = _canvasElements.value ?: emptyList()
        if (currentList.any { it.id == element.id }) { // Check by ID in case it's a copy
            val newElement = element.copy(context = null, bitmap = null)
            newElement.paint.typeface = newElement.applyTypefaceFromFontList()
            _canvasActions.push(
                CanvasAction.RemoveElement(
                    newElement
                )
            ) // Push a copy for undo, without transient data
            _redoStack.clear()
            _canvasElements.value = currentList.filter { it.id != element.id }
            refreshSelectedElements()
            selectedElement = null
            notifyUndoRedoChanged()
        }
    }

    fun undo() {
        if (_canvasActions.isEmpty()) return
        val action = _canvasActions.pop()
        _redoStack.push(action)
        applyAction(action, isRedo = false)
        notifyUndoRedoChanged()
    }

    fun redo() {
        if (_redoStack.isEmpty()) return
        val action = _redoStack.pop()
        _canvasActions.push(action)
        applyAction(action, isRedo = true)
        notifyUndoRedoChanged()
    }

    private fun notifyUndoRedoChanged() {
        _canUndo.value = _canvasActions.isNotEmpty()
        _canRedo.value = _redoStack.isNotEmpty()
        refreshSelectedElements()
    }

    private fun CanvasElement.restoreWithContext(context: Context?): CanvasElement {
        // Copy and set context
        val restored = this.copy(context = context).apply {
            updatePaintProperties()
            when (type) {
                ElementType.TEXT -> {
                    paint.typeface = applyTypefaceFromFontList(context)
                }

                ElementType.IMAGE -> {
                    bitmapData?.let { data ->
//                        bitmap = ImageProcessor.filePathToBitmap(data)
                        bitmap = ImageProcessor.base64ToBitmap(data)
                    }
                }

                else -> { /* no extra work */
                }
            }
        }
        return restored
    }

    // Adjust applyTypefaceFromFontList to accept context param:
    private fun CanvasElement.applyTypefaceFromFontList(context: Context?): Typeface {
        return fontId?.let { id ->
            localFonts.value.firstOrNull { it.id.toString() == id }?.file_path?.takeIf { it.isNotBlank() }
                ?.let { Typeface.createFromFile(it) }
        } ?: context?.let { ResourcesCompat.getFont(it, R.font.regular) } ?: Typeface.DEFAULT
    }

    private fun updateSingleElement(
        elementId: String,
        getNewValue: (CanvasElement) -> Any?,
        applyValue: (CanvasElement, Any?) -> CanvasElement
    ) {
        val currentList = _canvasElements.value.orEmpty()
        val context = currentList.firstOrNull()?.context
        val updatedList = currentList.map { element ->
            if (element.id == elementId) {
                // Determine value to apply via getNewValue, which inside can pick from action.new vs action.old
                val rawValue = getNewValue(element)
                // Copy and set the relevant field, then restore paint/bitmap
                applyValue(
                    element.copy(context = context), rawValue
                ).restoreWithContext(context)
            } else element
        }
        _canvasElements.value = updatedList
    }

    private fun applyAction(action: CanvasAction, isRedo: Boolean) {
        // Always try to get context from an existing element for re-applying paint properties
        val context = _canvasElements.value?.firstOrNull()?.context

        when (action) {
            is CanvasAction.UpdateElement -> {
                val list = _canvasElements.value.orEmpty()
                val updated = list.map { element ->
                    if (element.id == action.elementId) {
                        // Choose old or new element data
                        val chosen = if (isRedo) action.newElement else action.oldElement
                        // Ensure the id remains the same, then restore properties
                        chosen.copy(id = element.id).restoreWithContext(context)
                    } else element
                }
                _canvasElements.value = updated
            }

            is CanvasAction.SetBackgroundColor -> {
                _backgroundColor.value = if (isRedo) action.color else action.previousColor
            }

            is CanvasAction.SetBackgroundImage -> {
                _backgroundImage.value = if (isRedo) action.bitmap else action.previousBitmap
            }

            is CanvasAction.SetBackgroundGradient -> {
                _backgroundGradient.value = if (isRedo) action.gradientItem
                else action.prevGradientItem
            }

            is CanvasAction.AddSticker -> {
                val currentList = _canvasElements.value.orEmpty()
                if (isRedo) {
                    // Reapply context & paint/typeface/bitmap, then add
                    val restored = action.sticker.restoreWithContext(context)
                    _canvasElements.value = currentList + restored
                } else {
                    // Undo: remove by ID
                    _canvasElements.value = currentList.filter { it.id != action.sticker.id }
                }
            }

            is CanvasAction.AddText -> {
                val currentList = _canvasElements.value.orEmpty()
                if (isRedo) {
                    // Reapply context, paint, typeface, then add
                    val restored = action.element.copy(context = context).apply {
                        updatePaintProperties()
                        paint.typeface = applyTypefaceFromFontList(context)
                        originalTypeface = paint.typeface
                    }
                    _canvasElements.value = currentList + restored
                } else {
                    _canvasElements.value = currentList.filter { it.id != action.element.id }
                }
            }

            is CanvasAction.SetFont -> {
                val currentList = _canvasElements.value.orEmpty()
                val updated = currentList.map { element ->
                    // Look for affected element in action.affectedElements: Pair(id, previousFontId)
                    val affectedData = action.affectedElements.find { it.first == element.id }
                    if (affectedData != null && element.type == ElementType.TEXT) {
                        val fontIdToApply = if (isRedo) {
                            action.newFontEntity.id.toString()
                        } else {
                            affectedData.second
                        }
                        val copied = element.copy(context = context)
                        if (fontIdToApply != null) {
                            val fontEntity =
                                localFonts.value.firstOrNull { it.id.toString() == fontIdToApply }
                            if (fontEntity?.file_path?.isNotBlank() == true) {
                                try {
                                    val tf = Typeface.createFromFile(fontEntity.file_path)
                                    copied.originalTypeface = tf
                                    copied.paint.typeface = tf
                                    copied.fontId = fontEntity.id.toString()
                                } catch (e: Exception) {
                                    copied.paint.typeface = context?.let {
                                        ResourcesCompat.getFont(
                                            it, R.font.regular
                                        )
                                    } ?: Typeface.DEFAULT
                                    copied.fontId = null
                                }
                            } else {
                                copied.paint.typeface =
                                    context?.let { ResourcesCompat.getFont(it, R.font.regular) }
                                        ?: Typeface.DEFAULT
                                copied.fontId = null
                            }
                        } else {
                            copied.paint.typeface =
                                context?.let { ResourcesCompat.getFont(it, R.font.regular) }
                                    ?: Typeface.DEFAULT
                            copied.fontId = null
                        }
                        copied
                    } else element
                }
                _canvasElements.value = updated
                // Update currentFont LiveData
                _currentFont.value = if (isRedo) {
                    action.newFontEntity
                } else {
                    // Find the font by previous ID if any
                    action.affectedElements.firstOrNull()?.second?.let { prevId ->
                        localFonts.value.firstOrNull { it.id.toString() == prevId }
                    }
                }
            }

            is CanvasAction.SetTextColor -> {
                updateSingleElement(
                    elementId = action.elementId,
                    getNewValue = { if (isRedo) action.color else action.previousColor },
                    applyValue = { elem, raw ->
                        (raw as? Int)?.let {
                            elem.apply {
                                paint.color = it
                                paintColor = it
                            }
                        } ?: elem
                    })
                _currentTextColor.value = if (isRedo) action.color else action.previousColor
            }

            is CanvasAction.SetTextSize -> {
                updateSingleElement(
                    elementId = action.elementId,
                    getNewValue = { if (isRedo) action.size else action.previousSize },
                    applyValue = { elem, raw ->
                        (raw as? Float)?.let {
                            elem.apply {
                                paint.textSize = it
                                paintTextSize = it
                            }
                        } ?: elem
                    })
                _currentTextSize.value = if (isRedo) action.size else action.previousSize
            }

            is CanvasAction.SetTextAlignment -> {
                updateSingleElement(
                    elementId = action.elementId,
                    getNewValue = { if (isRedo) action.alignment else action.previousAlignment },
                    applyValue = { elem, raw ->
                        (raw as? TextAlignment)?.let {
                            elem.apply { alignment = it }
                        } ?: elem
                    })
                _currentTextAlignment.value =
                    if (isRedo) action.alignment else action.previousAlignment
            }

            is CanvasAction.SetOpacity -> {
                updateSingleElement(
                    elementId = action.elementId,
                    getNewValue = { if (isRedo) action.opacity else action.previousOpacity },
                    applyValue = { elem, raw ->
                        (raw as? Int)?.let {
                            elem.apply {
                                paint.alpha = it
                                paintAlpha = it
                            }
                        } ?: elem
                    })
                _opacity.value = if (isRedo) action.opacity else action.previousOpacity
            }

            is CanvasAction.UpdateText -> {
                updateSingleElement(
                    elementId = action.elementId,
                    getNewValue = { if (isRedo) action.text else action.previousText },
                    applyValue = { elem, raw ->
                        (raw as? String)?.let {
                            elem.apply { text = it }
                        } ?: elem
                    })
            }

            is CanvasAction.RemoveElement -> {
                val currentList = _canvasElements.value.orEmpty()
                if (isRedo) {
                    // Remove by ID
                    _canvasElements.value = currentList.filter { it.id != action.element.id }
                } else {
                    // Undo: add back, reapply context & paint/typeface/bitmap
                    val restored = action.element.restoreWithContext(context)
                    if (currentList.none { it.id == restored.id }) {
                        _canvasElements.value = currentList + restored
                    }
                }
            }

            is CanvasAction.UpdateCanvasElementsOrder -> {
                // Apply either newList or oldList
                val listToApply = if (isRedo) action.newList else action.oldList
                // Reapply context & paint/typeface/bitmap for each
                val restoredList = listToApply.map { it.restoreWithContext(context) }
                _canvasElements.value = restoredList
            }

            is CanvasAction.SetCanvasSize -> {
                _canvasSize.value = if (isRedo) action.newSize else action.oldSize
            }

            is CanvasAction.ApplyImageFilter -> {
                updateSingleElement(elementId = action.elementId, getNewValue = {
                    // Note: in original code, imageFilter swap seemed inverted; ensure correct:
                    if (isRedo) action.newFilter else action.oldFilter
                }, applyValue = { elem, raw ->
                    (raw as? ImageFilter)?.let {
                        elem.copy(context = elem.context, imageFilter = it)
                    } ?: elem
                })
                // If selected element, update LiveData
                _canvasElements.value?.find { it.id == action.elementId && it.isSelected }?.let {
                        _currentImageFilter.value =
                            if (isRedo) action.newFilter else action.oldFilter
                    }
            }
        }

        notifyUndoRedoChanged()
        // Force LiveData re-emit if needed
        _canvasElements.value = _canvasElements.value
    }

    fun clearCanvas() {
        _canvasElements.value = emptyList()
        _selectedElements.value = emptyList()
        _canvasActions.clear()
        _redoStack.clear()

        _canvasSize.value = null
        _backgroundColor.value = Color.WHITE
        _backgroundImage.value = null
        _backgroundGradient.value = null

        resetTextFormattingToDefault()
        clearFillGradients()
        clearStrokeGradients()
        clearLabelGradients()
        clearGradient()

        _currentFont.value = null
        _currentImageFilter.value = null
        _groupId.value = null
        _currentGroupId.value = null
        _exportResult.value = null
        resetExportOptions()

        _canUndo.value = false
        _canRedo.value = false

        selectedElement = null
        currentBatchAction = null
        hasChanges.value = false
    }

    fun loadTemplateFromJsonFile(exportResult: ExportResult, context: Context) {
        viewModelScope.launch(Dispatchers.Default) {

            _isLoadingTemplate.postValue(true)

            try {
                _loadingStage.postValue("Reading file" to 10)
                val jsonFilePath = exportResult.jsonPath
                val jsonFile = File(jsonFilePath)

                if (!jsonFile.exists()) {
                    Log.e("CanvasViewModel", "Template JSON file not found: $jsonFilePath")
                    return@launch
                }

                _loadingStage.postValue("Parsing JSON" to 30)
                val jsonContent = jsonFile.readText()
                val elements = gson.fromJson(jsonContent, Array<CanvasElement>::class.java).toList()

                _loadingStage.postValue("Hydrating elements" to 60)
                val hydratedElements = elements.map { raw ->
                    raw.copy(context = context).restoreWithContext(context)
                }

                _loadingStage.postValue("Applying to canvas" to 90)
                withContext(Dispatchers.Main) {

                    if (elements.isNotEmpty()) {
                        val bg = elements[0]

                        _backgroundColor.value = bg.backgroundColor
                        _backgroundGradient.value = bg.fillGradient

                        if (bg.bitmapData != null) {
                            val bitmap = ImageProcessor.base64ToBitmap(bg.bitmapData!!)
                            if (bitmap != null) {
                                _backgroundImage.value = bitmap
                            } else {
                                Log.e(
                                    "CanvasViewModel",
                                    "Bitmap decoding failed for path: ${bg.bitmapData}"
                                )
                            }
                        }

                        if (_backgroundGradient.value == null && _backgroundImage.value == null) {
                            _backgroundColor.value = bg.backgroundColor ?: Color.WHITE
                        }
                    }

                    _canvasSize.value = exportResult.canvasSize
                    _canvasElements.value = hydratedElements
                    _exportResult.value = exportResult
                }
            } catch (e: Exception) {
                Log.e("CanvasViewModel", "Error loading template: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    _loadingStage.value = "Done" to 100
                    _isLoadingTemplate.value = false
                }
            }
        }
    }

    fun clearLoading() {
        _isLoadingTemplate.value = null
    }

    fun isExplicitChange(): Boolean {
        return _isExplicitChange
    }
}