package com.webscare.urducanvas.common.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.PictureDrawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.webscare.urducanvas.R
import com.webscare.urducanvas.common.canvas.enums.BlendType
import com.webscare.urducanvas.common.canvas.enums.BrushStyle
import com.webscare.urducanvas.common.canvas.enums.ElementType
import com.webscare.urducanvas.common.canvas.enums.FeatherDirection
import com.webscare.urducanvas.common.canvas.enums.GradientPickerTarget
import com.webscare.urducanvas.common.canvas.enums.GradientType
import com.webscare.urducanvas.common.canvas.enums.LabelShape
import com.webscare.urducanvas.common.canvas.enums.LetterCasing
import com.webscare.urducanvas.common.canvas.enums.ListStyle
import com.webscare.urducanvas.common.canvas.enums.PickerTarget
import com.webscare.urducanvas.common.canvas.enums.ShapeType
import com.webscare.urducanvas.common.canvas.enums.TextAlignment
import com.webscare.urducanvas.common.canvas.enums.TextDecoration
import com.webscare.urducanvas.common.canvas.enums.UnitType
import com.webscare.urducanvas.common.canvas.model.AdjustmentValues
import com.webscare.urducanvas.common.canvas.model.CanvasElement
import com.webscare.urducanvas.common.canvas.model.CanvasSize
import com.webscare.urducanvas.common.canvas.model.ExportFormat
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.canvas.model.ExportQuality
import com.webscare.urducanvas.common.canvas.model.ExportResolution
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.model.StrokeData
import com.webscare.urducanvas.common.canvas.sealed.BatchedCanvasAction
import com.webscare.urducanvas.common.canvas.sealed.CanvasAction
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import com.webscare.urducanvas.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.urducanvas.common.datastore.PreferencesDataStoreHelper
import com.webscare.urducanvas.common.utils.ImageProcessor
import com.webscare.urducanvas.common.utils.ImageProcessor.trimTransparentEdges
import com.webscare.urducanvas.common.views.CanvasView
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.FontPanelState
import com.webscare.urducanvas.data.model.PremiumAssetItem
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.domain.usecase.GetFontsUseCase
import com.webscare.urducanvas.viewmodels.FontGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Stack
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val getFontsUseCase: GetFontsUseCase,
    private val gson: Gson,
    private val dataStore: PreferencesDataStoreHelper,
    private val fontGate: FontGate,
    private val billingManager: BillingManager
) : ViewModel() {

    private val _fontPanelState = MutableLiveData(FontPanelState())
    val fontPanelState: LiveData<FontPanelState> = _fontPanelState
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

    private val _isDrawingMode = MutableLiveData(false)
    val isDrawingMode: LiveData<Boolean> get() = _isDrawingMode

    private val _isMaskingMode = MutableLiveData(false)
    val isMaskingMode: LiveData<Boolean> get() = _isMaskingMode

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
    private val _currentTextSize = MutableLiveData(50f)
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

    // UI-facing angle/distance — derived from dx/dy on load, converted back on save.
    // shadowDx and shadowDy remain the source of truth for canvas drawing and serialization.
    private val _shadowAngle = MutableLiveData(135f)   // degrees, 0–360
    val shadowAngle: LiveData<Float> = _shadowAngle

    private val _shadowDistance = MutableLiveData(21f) // pixels, 0–100
    val shadowDistance: LiveData<Float> = _shadowDistance

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

    private val _blendingType = MutableLiveData(BlendType.SRC) // Default blend type
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

    // 🎨 Image Adjustment LiveData
    private val _brightness = MutableLiveData(0f)
    val brightness: LiveData<Float> = _brightness

    private val _contrast = MutableLiveData(1f)
    val contrast: LiveData<Float> = _contrast

    private val _saturation = MutableLiveData(1f)
    val saturation: LiveData<Float> = _saturation

    private val _blur = MutableLiveData(0f)
    val blur: LiveData<Float> = _blur

    private val _featherRadius = MutableLiveData(0f)
    val featherRadius: LiveData<Float> = _featherRadius

    private val _featherDirection = MutableLiveData<FeatherDirection>(FeatherDirection.ALL)
    val featherDirection: LiveData<FeatherDirection> = _featherDirection
    private val _featherWidth = MutableLiveData(50f)
    val featherWidth: LiveData<Float> = _featherWidth

    private val _shadows = MutableLiveData(0f)
    val shadows: LiveData<Float> = _shadows

    private val _temperature = MutableLiveData(0f)
    val temperature: LiveData<Float> = _temperature

    private val _tint = MutableLiveData(0f)
    val tint: LiveData<Float> = _tint

    private val _vibrance = MutableLiveData(1f)
    val vibrance: LiveData<Float> = _vibrance

    private val _sharpness = MutableLiveData(0f)
    val sharpness: LiveData<Float> = _sharpness

    private val _highlights = MutableLiveData(0f)
    val highlights: LiveData<Float> = _highlights

    private val _clarity = MutableLiveData(0f)
    val clarity: LiveData<Float> = _clarity

    private val _fade = MutableLiveData(0f)
    val fade: LiveData<Float> = _fade

    private val _currentBrushStyle = MutableLiveData(BrushStyle.BRUSH)
    val currentBrushStyle: LiveData<BrushStyle> = _currentBrushStyle

    private val _brushHardness = MutableLiveData(1f)   // softness vs hardness
    val brushHardness: LiveData<Float> = _brushHardness

    private val _brushThickness = MutableLiveData(10f)
    val brushThickness: LiveData<Float> = _brushThickness

    private val _brushColor = MutableLiveData(Color.BLACK)
    val brushColor: LiveData<Int> = _brushColor

    private val _currentShapeType = MutableLiveData(ShapeType.RECTANGLE)
    val currentShapeType: LiveData<ShapeType> = _currentShapeType

    private val _shapeFillEnabled = MutableLiveData(false)
    val shapeFillEnabled: LiveData<Boolean> = _shapeFillEnabled

    private val _shapeStrokeEnabled = MutableLiveData(true)
    val shapeStrokeEnabled: LiveData<Boolean> = _shapeStrokeEnabled

    private val _shapeCornerEnabled = MutableLiveData(true)
    val shapeCornerEnabled: LiveData<Boolean> = _shapeCornerEnabled

    private val _shapeStrokeWidth = MutableLiveData(1f)
    val shapeStrokeWidth: LiveData<Float> = _shapeStrokeWidth

    private val _shapeCornerRadius = MutableLiveData(0f)
    val shapeCornerRadius: LiveData<Float> = _shapeCornerRadius

    private val _shapeFillColor = MutableLiveData(Color.BLACK)
    val shapeFillColor: LiveData<Int> = _shapeFillColor

    private val _shapeStrokeColor = MutableLiveData(Color.BLACK)
    val shapeStrokeColor: LiveData<Int> = _shapeStrokeColor

    private val _shapeFillGradient = MutableLiveData<GradientItem?>(null)
    val shapeFillGradient: LiveData<GradientItem?> = _shapeFillGradient

    private val _shapeStrokeGradient = MutableLiveData<GradientItem?>(null)
    val shapeStrokeGradient: LiveData<GradientItem?> = _shapeStrokeGradient

    private val _brushGradient = MutableLiveData<GradientItem?>(null)
    val brushGradient: LiveData<GradientItem?> = _brushGradient

    private val _imagePanX = MutableLiveData(0f)
    val imagePanX: LiveData<Float> = _imagePanX

    private val _imagePanY = MutableLiveData(0f)
    val imagePanY: LiveData<Float> = _imagePanY

    private val _imageScale = MutableLiveData(1f)
    val imageScale: LiveData<Float> = _imageScale

    private val _imageFitMode = MutableLiveData("cover")
    val imageFitMode: LiveData<String> = _imageFitMode

    fun setExportResult(result: ExportResult) {
        Log.d("CanvasVM", "Setting ExportResult: $result")
        _exportResult.value = result
        Log.d("CanvasVM", "Post ExportResult: ${_exportResult.value}")
    }

    private var selectedElement: CanvasElement? = null
    private var _activeDrawSession: CanvasElement? = null

    private var currentBatchAction: BatchedCanvasAction? = null
    private var autoBatchJob: kotlinx.coroutines.Job? = null

    private fun startAutoBatchIfNeeded(elementId: String) {
        autoBatchJob?.cancel()
        if (currentBatchAction == null) {
            startBatchUpdate(elementId, "adjustments")
        }
        autoBatchJob = viewModelScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(600)
            endBatchUpdate(elementId)
        }
    }
    private var _isExplicitChange = false

    // ── Canvas overlay toggles ───────────────────────────────────
    private val _isGridEnabled = MutableLiveData(false)
    val isGridEnabled: LiveData<Boolean> get() = _isGridEnabled

    private val _rulerState = MutableLiveData(com.webscare.urducanvas.common.canvas.enums.RulerState.OFF)
    val rulerState: LiveData<com.webscare.urducanvas.common.canvas.enums.RulerState> get() = _rulerState
    val isRulerEnabled: LiveData<Boolean> = _rulerState.map { it != com.webscare.urducanvas.common.canvas.enums.RulerState.OFF }

    private val _isPanMode = MutableLiveData(false)
    val isPanMode: LiveData<Boolean> get() = _isPanMode

    // ── Canvas pan lock ────────────────────────────────────────────────────────
    // When true, canvas pan and pinch-to-zoom gestures are suppressed.
    // Observed by EditorFragment → forwarded to CanvasView.setCanvasPanLocked().
    private val _isCanvasPanLocked = MutableLiveData(false)
    val isCanvasPanLocked: LiveData<Boolean> get() = _isCanvasPanLocked

    // ── Zoom level (1.0f = 100%) ─────────────────────────────────
    private val _zoomLevel = MutableLiveData(1.0f)
    val zoomLevel: LiveData<Float> get() = _zoomLevel

    val availableResolutions = listOf(
        ExportResolution("Regular", 0, 0, 0.5f, "1280 x 720", "Keep regular size", 2500),
        ExportResolution("High", 0, 0, 3f, "1920 x 1080", "High quality", 1200, isPremium = true),
        ExportResolution("Print", 0, 0, 4f, "3840 x 2160", "Print quality", 4800, isPremium = true)
    )

    val qualityOptions = listOf(
        ExportQuality("High", 100, "Maximum compression, larger file size", 40),
        ExportQuality("Medium", 75, "Balanced compression and size", 0),
        ExportQuality("Low", 50, "Faster export, smaller size", -30)
    )

    val formatOptions = listOf(
        ExportFormat(
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
            "PNG",
            Bitmap.CompressFormat.PNG,
            "Lossless format",
            listOf("Transparent", "High Quality", "Larger Size"),
            isPremium = true
        ), ExportFormat(
            "PDF",
            null,
            "Portable Document Format",
            listOf("Vector container", "Shareable", "Multi-page capable"),
            isPremium = true
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

    private val _canvasView = MutableLiveData<CanvasView?>()
    val canvasView: LiveData<CanvasView?> = _canvasView

    // Emits once when applyMaskToSelected finishes committing to LiveData.
    // BgRemovalFragment (or EditorFragment) observes this to know the safe
    // moment to call navigateUp() — after the data is committed, not before.
    private val _maskAppliedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val maskAppliedEvent = _maskAppliedEvent

    fun toggleGrid() {
        _isGridEnabled.value = !(_isGridEnabled.value ?: false)
    }

    fun toggleRuler() {
        val nextState = when (_rulerState.value) {
            com.webscare.urducanvas.common.canvas.enums.RulerState.OFF -> com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES
            com.webscare.urducanvas.common.canvas.enums.RulerState.TWO_SIDES -> com.webscare.urducanvas.common.canvas.enums.RulerState.FOUR_SIDES
            else -> com.webscare.urducanvas.common.canvas.enums.RulerState.OFF
        }
        _rulerState.value = nextState
    }

    fun togglePanMode() {
        _isPanMode.value = !(_isPanMode.value ?: false)
    }

    /** Toggle canvas pan/zoom lock. When locked, two-finger zoom and single-finger canvas pan are blocked. */
    fun toggleCanvasPanLock() {
        _isCanvasPanLocked.value = !(_isCanvasPanLocked.value ?: false)
    }

    private val zoomMin = 0.5f
    private val zoomMax = 3.0f
    private val zoomStep = 0.2f

    fun setZoomLevel(zoom: Float) {
        _zoomLevel.value = zoom.coerceIn(zoomMin, zoomMax)
    }

    fun zoomIn() {
        val current = _zoomLevel.value ?: 1f
        val next = (Math.round(current / zoomStep) * zoomStep + zoomStep)
            .coerceAtMost(zoomMax)
        _zoomLevel.value = Math.round(next / zoomStep) * zoomStep
    }

    fun zoomOut() {
        val current = _zoomLevel.value ?: 1f
        val next = (Math.round(current / zoomStep) * zoomStep - zoomStep)
            .coerceAtLeast(zoomMin)
        _zoomLevel.value = Math.round(next / zoomStep) * zoomStep
    }

    fun resetZoom() {
        _zoomLevel.value = 1.0f   // 100%
    }

    fun markChanged() {
        hasChanges.value = true
    }

    fun updateShapeType(shape: ShapeType) {
        _currentShapeType.value = shape
        updateSelectedShape { it.copy(shapeType = shape) }
    }

    fun updateCornerRadius(value: Float) {
        _shapeCornerRadius.value = value
        _shapeCornerEnabled.value = true
        updateSelectedShape { it.copy(shapeCornerRadius = value, shapeHasCorner = true) }
    }

    fun updateStrokeWidth(value: Float) {
        _shapeStrokeWidth.value = value
        _shapeStrokeEnabled.value = true
        updateSelectedShape { it.copy(shapeStrokeWidth = value, shapeHasStroke = true) }
    }

    fun toggleFillEnabled(enabled: Boolean) {
        _shapeFillEnabled.value = enabled
        updateSelectedShape { it.copy(shapeHasFill = enabled) }
    }

    fun toggleStrokeEnabled(enabled: Boolean) {
        _shapeStrokeEnabled.value = enabled
        updateSelectedShape { it.copy(shapeHasStroke = enabled) }
    }

    fun toggleCornerEnabled(enabled: Boolean) {
        _shapeCornerEnabled.value = enabled
        updateSelectedShape { it.copy(shapeHasCorner = enabled) }
    }

    fun setFillColor(color: Int) {
        _shapeFillColor.value = color
        _shapeFillEnabled.value = true
        updateSelectedShape { it.copy(shapeFillColor = color, shapeHasFill = true) }
    }

    fun setStrokeColor(color: Int) {
        _shapeStrokeColor.value = color
        _shapeStrokeEnabled.value = true
        updateSelectedShape { it.copy(shapeStrokeColor = color, shapeHasStroke = true) }
    }

    fun setFillGradient(grad: GradientItem?) {
        _shapeFillGradient.value = grad
        _shapeFillEnabled.value = true
        updateSelectedShape { it.copy(shapeFillGradient = grad, shapeHasFill = true) }
    }

    fun setStrokeGradient(grad: GradientItem?) {
        _shapeStrokeGradient.value = grad
        _shapeStrokeEnabled.value = true
        updateSelectedShape { it.copy(shapeStrokeGradient = grad, shapeHasStroke = true) }
    }

    fun setImagePanX(value: Float) {
        _imagePanX.value = value
        updateSelectedShape { it.copy(imagePanX = value) }
    }

    fun setImagePanY(value: Float) {
        _imagePanY.value = value
        updateSelectedShape { it.copy(imagePanY = value) }
    }

    fun setImageScale(value: Float) {
        _imageScale.value = value
        updateSelectedShape { it.copy(imageScale = value) }
    }

    fun setImageFitMode(mode: String) {
        _imageFitMode.value = mode
        updateSelectedShape { it.copy(imageFitMode = mode) }
    }

    fun addImageInsideShape(bitmap: Bitmap, context: Context, isPremium: Boolean = false) {
        updateSelectedShape { element ->
            if (element.type == ElementType.SHAPE) {
                // Only update bitmap — logicalContentWidth/Height and scale stay
                // as the user set them on the shape, preserving its visual size.
                element.copy(
                    context = context,
                    bitmap = bitmap,
                    isPremium = isPremium
                )
            } else {
                element
            }
        }
    }

    private fun updateSelectedShape(update: (CanvasElement) -> CanvasElement) {
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.SHAPE) {
                val oldElement = element.copy()
                val newElement = update(element)
                _canvasActions.push(CanvasAction.UpdateElement(element.id, newElement, oldElement))
                newElement
            } else element
        }
        _canvasElements.value = updatedList
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun updateCanvasElement(updatedElement: CanvasElement) {
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.id == updatedElement.id) {
                val oldElement = element.copy()
                _canvasActions.push(
                    CanvasAction.UpdateElement(
                        element.id, updatedElement, oldElement
                    )
                )
                updatedElement
            } else {
                element
            }
        }
        _canvasElements.value = updatedList
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun addShapeElement() {
        val currentList = _canvasElements.value.orEmpty().toMutableList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f
        val (logicalW, logicalH) = when (_currentShapeType.value) {
            ShapeType.RECTANGLE -> Pair(225f, 150f)
            else -> Pair(150f, 150f)
        }
        val element = CanvasElement(
            id = UUID.randomUUID().toString(),
            type = ElementType.SHAPE,
            shapeType = _currentShapeType.value,
            shapeHasStroke = _shapeStrokeEnabled.value ?: true,
            shapeHasFill = _shapeFillEnabled.value ?: true,
            shapeStrokeWidth = _shapeStrokeWidth.value ?: 1f,
            shapeCornerRadius = _shapeCornerRadius.value ?: 0f,
            shapeStrokeColor = _shapeStrokeColor.value ?: Color.BLACK,
            shapeFillColor = _shapeFillColor.value ?: Color.BLACK,
            shapeStrokeGradient = _shapeStrokeGradient.value,
            shapeFillGradient = _shapeFillGradient.value,
            x = canvasW / 2f,
            y = canvasH / 2f,
            scale = 1f,
            rotation = 0f,
            zIndex = newZIndex,
            isSelected = true,
            logicalContentWidth = logicalW,
            logicalContentHeight = logicalH
        )

        _canvasElements.value = _canvasElements.value?.plus(element)
        _canvasActions.push(CanvasAction.AddShape(element))
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun mergeImageToShape(
        imageElement: CanvasElement,
        shapeType: ShapeType,
        context: Context,
        isPremium: Boolean = false
    ) {
        // ── Why scale is reset to 1f ──────────────────────────────────────────
        // SHAPE draws at: logicalContentWidth * scale  (canvas units)
        // IMAGE draws at: bitmap.width * scale         (canvas units)
        //
        // The image had scale=0.26 (small fraction because bitmap.width=4000).
        // If we keep scale=0.26 with logicalContentWidth=300, the shape draws at
        // 300 * 0.26 = 78 canvas units — tiny.
        //
        // A fresh shape (addShapeElement) always starts at scale=1f, logicalContentWidth=150.
        // That draws at 150 * 1 = 150 canvas units — a sensible default size.
        //
        // By resetting scale=1f here, the masked shape appears at the same size
        // as a freshly-created shape — no surprise size change, no need to resize.
        val updatedElement = imageElement.copy(
            id = imageElement.id,
            context = context,
            type = ElementType.SHAPE,
            shapeType = shapeType,
            shapeHasStroke = _shapeStrokeEnabled.value ?: true,
            shapeHasFill = _shapeFillEnabled.value ?: true,
            shapeStrokeWidth = _shapeStrokeWidth.value ?: 1f,
            shapeCornerRadius = _shapeCornerRadius.value ?: 0f,
            shapeStrokeColor = _shapeStrokeColor.value ?: Color.BLACK,
            shapeFillColor = _shapeFillColor.value ?: Color.BLACK,
            shapeStrokeGradient = _shapeStrokeGradient.value,
            shapeFillGradient = _shapeFillGradient.value,
            x = imageElement.x,
            y = imageElement.y,
            scale = 1f,               // ← reset: shape uses logicalContentWidth as canvas units
            rotation = imageElement.rotation,
            zIndex = imageElement.zIndex,
            isSelected = true,
            logicalContentWidth = 300f,   // draws at 300 * 1f = 300 canvas units
            logicalContentHeight = 300f,
            isPremium = isPremium
        )

        updateCanvasElement(updatedElement)
        _isMaskingMode.value = false
    }

    fun startDrawSession(context: Context) {
        val currentList = _canvasElements.value.orEmpty()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f

        _activeDrawSession = CanvasElement(
            context = context,
            type = ElementType.DRAW,
            x = 0f,
            y = 0f,
            zIndex = newZIndex,
            isSelected = false,
            isVisible = true,
            drawStrokes = mutableListOf(),
            allowsStrokeEditing = true,
            backgroundColor = Color.TRANSPARENT
        )
    }

    fun commitDrawSession() {
        val session = _activeDrawSession ?: return
        if (session.drawStrokes.isNullOrEmpty()) {
            _activeDrawSession = null
            return
        }

        _canvasActions.removeAll { it is CanvasAction.DrawSessionStroke }

        viewModelScope.launch(Dispatchers.Default) {
            val canvasW = _canvasSize.value?.width?.toInt() ?: 0
            val canvasH = _canvasSize.value?.height?.toInt() ?: 0

            val rasterized: CanvasElement = if (canvasW > 0 && canvasH > 0) {

                // --- Step 1: Compute tight bounds across all strokes ---
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE

                session.drawStrokes?.forEach { stroke ->
                    val path = stroke.path ?: return@forEach
                    val pathBounds = android.graphics.RectF()
                    path.computeBounds(pathBounds, true)
                    val expand = (stroke.thickness.takeIf { it.isFinite() } ?: 0f) * 0.5f
                    pathBounds.inset(-expand, -expand)
                    minX = minOf(minX, pathBounds.left)
                    minY = minOf(minY, pathBounds.top)
                    maxX = maxOf(maxX, pathBounds.right)
                    maxY = maxOf(maxY, pathBounds.bottom)
                }

                // Clamp to canvas bounds
                minX = minX.coerceAtLeast(0f)
                minY = minY.coerceAtLeast(0f)
                maxX = maxX.coerceAtMost(canvasW.toFloat())
                maxY = maxY.coerceAtMost(canvasH.toFloat())

                val strokesWidth = (maxX - minX).coerceAtLeast(1f)
                val strokesHeight = (maxY - minY).coerceAtLeast(1f)

                // --- Step 2: Render strokes onto a full-canvas bitmap ---
                val fullBitmap = createBitmap(canvasW, canvasH)
                val fullCanvas = android.graphics.Canvas(fullBitmap)

                session.drawStrokes?.forEach { stroke ->
                    com.webscare.urducanvas.common.utils.BrushRenderUtils.drawSingleStroke(
                        fullCanvas, stroke, 255
                    )
                }

                // --- Step 3: Crop to tight bounds ---
                val cropX = minX.toInt().coerceIn(0, fullBitmap.width - 1)
                val cropY = minY.toInt().coerceIn(0, fullBitmap.height - 1)
                val cropW = strokesWidth.toInt().coerceIn(1, fullBitmap.width - cropX)
                val cropH = strokesHeight.toInt().coerceIn(1, fullBitmap.height - cropY)
                val croppedBitmap = Bitmap.createBitmap(
                    fullBitmap,
                    cropX,
                    cropY,
                    cropW,
                    cropH
                )
                fullBitmap.recycle()

                val bitmapData = ImageProcessor.bitmapToBase64Lossless(croppedBitmap)

                // --- Step 4: Position element at center of stroke bounds ---
                val centerX = minX + strokesWidth / 2f
                val centerY = minY + strokesHeight / 2f

                session.copy(
                    bitmap = croppedBitmap,
                    bitmapData = bitmapData,
                    drawStrokes = null,
                    x = centerX,
                    y = centerY,
                    logicalContentWidth = strokesWidth,
                    logicalContentHeight = strokesHeight,
                    isSelected = true
                )
            } else {
                session.copy(isSelected = false)
            }

            withContext(Dispatchers.Main) {
                val currentList = _canvasElements.value.orEmpty().toMutableList()
                currentList.add(rasterized)
                _canvasElements.postValue(currentList)
                _canvasActions.push(
                    CanvasAction.AddDrawStroke(rasterized.copy(context = null))
                )
                _redoStack.clear()
                notifyUndoRedoChanged()
                _activeDrawSession = null
            }
        }
    }

    fun discardDrawSession() {
        _canvasActions.removeAll { it is CanvasAction.DrawSessionStroke }
        _redoStack.removeAll { it is CanvasAction.DrawSessionStroke }
        _activeDrawSession = null
        notifyUndoRedoChanged()
    }

    fun getActiveDrawSession(): CanvasElement? = _activeDrawSession

    fun updateBrushProperties(
        color: Int? = null,
        thickness: Float? = null,
        hardness: Float? = null,
        style: BrushStyle? = null,
        gradient: GradientItem? = null
    ) {
        val currentList = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val selected = currentList.firstOrNull { it.isSelected && it.type == ElementType.DRAW }

        // --- Step 1: Always update LiveData ---
        color?.let { _brushColor.value = it }
        thickness?.let { _brushThickness.value = it }
        hardness?.let { _brushHardness.value = it }
        style?.let { _currentBrushStyle.value = it }
        gradient?.let { _brushGradient.value = it }

        // --- Step 2: If a draw element is selected, update its stroke data ---
        if (selected != null) {
            val updatedStrokes = selected.drawStrokes?.map { stroke ->
                stroke.copy(
                    color = color ?: stroke.color,
                    thickness = thickness ?: stroke.thickness,
                    hardness = hardness ?: stroke.hardness,
                    style = style ?: stroke.style,
                    gradient = gradient ?: stroke.gradient
                )
            }

            val updatedElement = selected.copy(drawStrokes = updatedStrokes?.toMutableList())

            // Replace in list
            val updatedList = currentList.map {
                if (it.id == selected.id) updatedElement else it
            }

            _canvasElements.value = updatedList

            // Push to undo/redo stack
            _canvasActions.push(
                CanvasAction.UpdateElement(
                    elementId = selected.id,
                    newElement = updatedElement.copy(context = null),
                    oldElement = selected.copy(context = null)
                )
            )

            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun resetBrushSettings() {
        // 🔹 Reset all brush-related LiveData values to default
        _brushColor.value = Color.BLACK
        _brushThickness.value = 10f
        _brushHardness.value = 1f
        _currentBrushStyle.value = BrushStyle.BRUSH
        _brushGradient.value = null
    }

    fun setBrushColor(color: Int) {
        _brushColor.value = color
        _brushGradient.value = null
        updateBrushProperties(color = color)
    }

    fun setBrushThickness(value: Float) {
        _brushThickness.value = value
        updateBrushProperties(thickness = value)
    }

    fun setBrushHardness(value: Float) {
        _brushHardness.value = value
        updateBrushProperties(hardness = value)
    }

    fun setBrushStyle(style: BrushStyle) {
        _currentBrushStyle.value = style
        updateBrushProperties(style = style)
    }

    fun setBrushGradient(gradient: GradientItem?) {
        _brushGradient.value = gradient
        updateBrushProperties(gradient = gradient)
    }

    // 🎨 ADJUSTMENT UPDATERS
    fun setBrightness(value: Float) {
        _brightness.value = value
        updateSelectedElementAdjustments { it.copy(brightness = value) }
    }

    fun setContrast(value: Float) {
        _contrast.value = value
        updateSelectedElementAdjustments { it.copy(contrast = value) }
    }

    fun setSaturation(value: Float) {
        _saturation.value = value
        updateSelectedElementAdjustments { it.copy(saturation = value) }
    }

    fun setBlur(value: Float) {
        _blur.value = value
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                startAutoBatchIfNeeded(element.id)
                val old = element.copy(context = null)
                element.blurValue = value
                // hasBlur must stay consistent with blurValue so blur doesn't vanish
                // when the element is redrawn after a move/resize gesture.
                element.hasBlur = value > 0f
                // Invalidate the adjustment cache so the new blur is applied on next draw.
                element.isAdjustmentDirty = true
                element.cachedAdjustedBitmap?.recycle()
                element.cachedAdjustedBitmap = null

                if (currentBatchAction == null) {
                    _canvasActions.push(
                        CanvasAction.UpdateElement(
                            elementId = element.id,
                            newElement = element.copy(context = null),
                            oldElement = old
                        )
                    )
                }
                element
            } else element
        }
        _canvasElements.value = updatedList
        if (currentBatchAction == null) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun setFeatherDirection(direction: FeatherDirection) {
        _featherDirection.value = direction
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                startAutoBatchIfNeeded(element.id)
                val old = element.copy(context = null)
                element.featherDirection = direction
                if (currentBatchAction == null) {
                    _canvasActions.push(
                        CanvasAction.UpdateElement(
                            elementId = element.id,
                            newElement = element.copy(context = null),
                            oldElement = old
                        )
                    )
                }
                element
            } else element
        }
        _canvasElements.value = updatedList
        if (currentBatchAction == null) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun setFeather(value: Float) {
        _featherRadius.value = value
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                startAutoBatchIfNeeded(element.id)
                val old = element.copy(context = null)
                element.featherRadius = value
                element.hasFeather = value > 0f
                // Feather is composited on the GPU in CanvasView — no pixel processing,
                // no adjustment cache to invalidate. Just push the value and redraw.
                if (currentBatchAction == null) {
                    _canvasActions.push(
                        CanvasAction.UpdateElement(
                            elementId = element.id,
                            newElement = element.copy(context = null),
                            oldElement = old
                        )
                    )
                }
                element
            } else element
        }
        _canvasElements.value = updatedList
        if (currentBatchAction == null) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun setFeatherWidth(value: Float) {
        _featherWidth.value = value
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected) {
                startAutoBatchIfNeeded(element.id)
                val old = element.copy(context = null)
                element.featherWidth = value
                if (element.featherRadius > 0f) element.hasFeather = true
                // Feather softness is GPU compositing — no cache to clear, just redraw.
                if (currentBatchAction == null) {
                    _canvasActions.push(
                        CanvasAction.UpdateElement(
                            elementId = element.id,
                            newElement = element.copy(context = null),
                            oldElement = old
                        )
                    )
                }
                element
            } else element
        }
        _canvasElements.value = updatedList
        if (currentBatchAction == null) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    private fun updateSelectedElementValue(updateBlock: (CanvasElement) -> Unit) {
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map {
            if (it.isSelected) {
                it.apply(updateBlock)
                it
            } else it
        }
        _canvasElements.value = updatedList
    }

    fun setShadows(value: Float) {
        _shadows.value = value
        updateSelectedElementAdjustments { it.copy(shadows = value) }
    }

    fun setTemperature(value: Float) {
        _temperature.value = value
        updateSelectedElementAdjustments { it.copy(temperature = value) }
    }

    fun setTint(value: Float) {
        _tint.value = value
        updateSelectedElementAdjustments { it.copy(tint = value) }
    }

    fun setVibrance(value: Float) {
        _vibrance.value = value
        updateSelectedElementAdjustments { it.copy(vibrance = value) }
    }

    fun setSharpness(value: Float) {
        _sharpness.value = value
        updateSelectedElementAdjustments { it.copy(sharpness = value) }
    }

    fun setHighlights(value: Float) {
        _highlights.value = value
        updateSelectedElementAdjustments { it.copy(highlights = value) }
    }

    fun setClarity(value: Float) {
        _clarity.value = value
        updateSelectedElementAdjustments { it.copy(clarity = value) }
    }

    fun setFade(value: Float) {
        _fade.value = value
        updateSelectedElementAdjustments { it.copy(fade = value) }
    }

    private fun updateSelectedElementAdjustments(update: (AdjustmentValues) -> AdjustmentValues) {
        val currentList = _canvasElements.value ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected && (element.type == ElementType.IMAGE || element.type == ElementType.STICKER || element.type == ElementType.SHAPE || (element.type == ElementType.BACKGROUND && element.bitmap != null))) {
                startAutoBatchIfNeeded(element.id)
                val oldElement = element.copy(context = null)

                val newAdjustments = update(element.adjustments)
                val updated = element.copy(adjustments = newAdjustments)
                updated.isAdjustmentDirty = true
                updated.cachedAdjustedBitmap?.recycle()
                updated.cachedAdjustedBitmap = null

                if (currentBatchAction == null) {
                    _canvasActions.push(
                        CanvasAction.UpdateElement(
                            elementId = element.id,
                            newElement = updated.copy(context = null),
                            oldElement = oldElement
                        )
                    )
                }
                updated
            } else element
        }

        _canvasElements.value = updatedList
        if (currentBatchAction == null) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun resetAdjustments() {
        _brightness.value = 0f
        _contrast.value = 1f
        _saturation.value = 1f
        _blur.value = 0f
        _shadows.value = 0f
        _temperature.value = 0f
        _tint.value = 0f
        _vibrance.value = 1f
        _sharpness.value = 0f
        _clarity.value = 0f
        _fade.value = 0f
        _featherRadius.value = 0f
        _featherWidth.value = 50f

        updateSelectedElementAdjustments { AdjustmentValues() }
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
        _brushThickness.value = 50f
        _brushHardness.value = 1f
        _currentBrushStyle.value = BrushStyle.BRUSH
        _brushColor.value = Color.BLACK
    }

    fun fetchExportOptionsFromDataStore() {
        viewModelScope.launch {
            val resName =
                dataStore.getFirstPreference(
                    PreferenceDataStoreKeysConstants.KEY_RESOLUTION,
                    "Regular"
                )
            val qualityLabel =
                dataStore.getFirstPreference(PreferenceDataStoreKeysConstants.KEY_QUALITY, "Medium")
            val formatName =
                dataStore.getFirstPreference(PreferenceDataStoreKeysConstants.KEY_FORMAT, "JPEG")

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
        val selected = _selectedElements.value ?: return
        if (selected.isEmpty()) return

        val oldList = _canvasElements.value?.map { it.copy(context = null) } ?: emptyList()

        val newGroupId = UUID.randomUUID().toString()
        _currentGroupId.value = newGroupId

        // ── 1. Build the GROUP sentinel element ───────────────────────────────
        val highestZ = selected.maxOf { it.zIndex }
        val groupSentinel = CanvasElement(
            type       = ElementType.GROUP,
            id         = newGroupId,          // sentinel id == groupId of children
            customName = "Group",
            zIndex     = highestZ,
            isSelected = false,
            groupId    = null,                // sentinels never belong to another group
            isGroupCollapsed = false
        )

        // ── 2. Tag every selected element with the new groupId & add sentinel ────────────────
        val updated = (_canvasElements.value ?: emptyList()).map { element ->
            if (element.isSelected && element.type != ElementType.GROUP) {
                element.copy(groupId = newGroupId)
            } else {
                element
            }
        }.toMutableList()
        updated.add(groupSentinel)

        _canvasElements.value = updated
        _canvasActions.push(CanvasAction.UpdateCanvasElementsOrder(oldList, updated.map { it.copy(context = null) }))
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun ungroupElements() {
        val selected = _selectedElements.value ?: return
        if (selected.isEmpty()) return

        val oldList = _canvasElements.value?.map { it.copy(context = null) } ?: emptyList()

        // Collect all groupIds that are being dissolved.
        // Works whether the user selected children or the sentinel itself.
        val groupIdsToDissolve = selected.mapNotNull { it.groupId }.toSet() +
                selected.filter { it.type == ElementType.GROUP }.map { it.id }.toSet()

        val updated = (_canvasElements.value ?: emptyList()).mapNotNull { element ->
            when {
                // Remove the GROUP sentinel(s)
                element.type == ElementType.GROUP && groupIdsToDissolve.contains(element.id) -> null
                // Clear groupId from children
                element.groupId != null && groupIdsToDissolve.contains(element.groupId) ->
                    element.copy(groupId = null)
                else -> element
            }
        }

        _canvasElements.value = updated
        _selectedElements.value = emptyList()
        _currentGroupId.value = null

        _canvasActions.push(CanvasAction.UpdateCanvasElementsOrder(oldList, updated.map { it.copy(context = null) }))
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    // ── Photoshop-style merge/group ──────────────────────────────────────────
    // Called when the selection toolbar group button is tapped and the selection
    // is NOT a single uniform group (i.e. we should merge, not ungroup).
    // Handles all cases:
    //   - Pure standalones → new group
    //   - Mix of standalones + existing group children → new group absorbs all
    //   - Two group sentinels selected → nested flat merge into one new group
    fun mergeIntoGroup() {
        val selected = _selectedElements.value ?: return
        if (selected.size < 2) return

        val current = _canvasElements.value?.toMutableList() ?: return
        val oldList = current.map { it.copy(context = null) }
        val newGroupId = UUID.randomUUID().toString()

        // Collect every real (non-sentinel) element that is selected or
        // belongs to a selected GROUP sentinel.
        val selectedGroupIds = selected
            .filter { it.type == ElementType.GROUP }
            .map { it.id }.toSet()

        val membersToMerge = current.filter { el ->
            el.type != ElementType.GROUP &&
                    (el.isSelected || (el.groupId != null && el.groupId in selectedGroupIds))
        }

        if (membersToMerge.isEmpty()) return

        // Remove old sentinels whose entire membership is being absorbed.
        // A sentinel is removed if all its children are moving into the new group.
        val oldSentinelIdsToRemove = selectedGroupIds.filter { gid ->
            val allChildren = current.filter { it.groupId == gid }
            allChildren.isNotEmpty() && allChildren.all { child ->
                membersToMerge.any { it.id == child.id }
            }
        }.toSet()

        val highestZ = membersToMerge.maxOf { it.zIndex }
        val newSentinel = CanvasElement(
            type     = ElementType.GROUP,
            id       = newGroupId,
            customName = "Group",
            zIndex   = highestZ,
            isSelected = false,
            groupId  = null,
            isGroupCollapsed = false
        )

        val updated = current.mapNotNull { el ->
            when {
                // Drop absorbed old sentinels
                el.type == ElementType.GROUP && el.id in oldSentinelIdsToRemove -> null
                // Re-assign members to new group
                membersToMerge.any { m -> m.id == el.id } -> el.copy(groupId = newGroupId)
                else -> el
            }
        }.toMutableList()
        updated.add(newSentinel)

        _canvasElements.value = updated
        _selectedElements.value = emptyList()
        _currentGroupId.value = newGroupId

        _canvasActions.push(CanvasAction.UpdateCanvasElementsOrder(oldList, updated.map { it.copy(context = null) }))
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    // ── Apply drag-reorder from the layers panel ──────────────────────────────
    // Receives the current DisplayItem list (top→bottom as shown in the RV).
    // Resolves groupId mutations caused by cross-boundary drags:
    //   - Child moved outside its group  → groupId = null (becomes standalone)
    //   - Standalone moved inside a group → groupId = that group's id
    //   - GroupHeader moved               → its children stay attached (no groupId change)
    // After resolving memberships, auto-removes any sentinel left with 0 children,
    // then assigns fresh z-indices (top of list = highest z).
    fun applyLayerReorder(displayItems: List<com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem>) {
        val oldList = _canvasElements.value ?: return

        // Resolve groupId from DisplayItem type — onMove already mutated types correctly:
        //   Child     → el.groupId is correct (set by retypeItem or unchanged within group)
        //   Standalone → el.groupId is null (cleared by retypeItem when exiting group)
        //   GroupHeader → always null (sentinel has no parent group)
        // No position scanning needed.

        // Local helper: extract CanvasElement from any DisplayItem type
        fun elementOf(di: com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem): CanvasElement = when (di) {
            is com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem.GroupHeader -> di.element
            is com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem.Child       -> di.element
            is com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem.Standalone  -> di.element
        }

        val resolvedGroupIdMap = mutableMapOf<String, String?>()
        for (item in displayItems) {
            val el = elementOf(item)
            resolvedGroupIdMap[el.id] = when (item) {
                is com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem.Child -> el.groupId
                else -> null
            }
        }

        // Step 3: Build full ordered list including collapsed children.
        // Collapsed children are absent from displayItems; re-insert them under their sentinel.
        val displayIds = displayItems.map { elementOf(it).id }.toSet()

        val collapsedByGroup = oldList
            .filter { it.groupId != null && it.id !in displayIds }
            .groupBy { it.groupId!! }

        val fullOrderedList = mutableListOf<CanvasElement>()
        for (item in displayItems) {
            val el = elementOf(item)
            // Apply resolved groupId
            val newGid = resolvedGroupIdMap[el.id]
            fullOrderedList.add(if (newGid != el.groupId) el.copy(groupId = newGid) else el)

            // Re-insert collapsed children right after their sentinel
            if (item is com.webscare.urducanvas.ui.editor.panels.layers.DisplayItem.GroupHeader) {
                collapsedByGroup[el.id]?.sortedByDescending { it.zIndex }?.forEach { child ->
                    fullOrderedList.add(child)
                }
            }
        }

        // Step 4: Auto-remove sentinels that now have 0 children.
        val memberCountBySentinel = fullOrderedList
            .filter { it.groupId != null }
            .groupingBy { it.groupId!! }
            .eachCount()

        val finalList = fullOrderedList.filter { el ->
            if (el.type == ElementType.GROUP) (memberCountBySentinel[el.id] ?: 0) > 0
            else true
        }

        // Step 5: Assign z-indices. Top of panel = highest z.
        val total = finalList.size
        val context = oldList.firstOrNull()?.context
        val updatedList = finalList.mapIndexed { index, el ->
            val newZ = total - 1 - index
            val copied = el.copy(zIndex = newZ, context = context ?: el.context)
            if (copied.type == ElementType.TEXT && copied.fontId != null) {
                val font = localFonts.value.find { it.id.toString() == copied.fontId }
                if (font?.file_path?.isNotBlank() == true) {
                    try { copied.paint.typeface = Typeface.createFromFile(font.file_path) }
                    catch (e: Exception) {
                        copied.paint.typeface = context?.let {
                            ResourcesCompat.getFont(it, R.font.default_canvas)
                        } ?: Typeface.DEFAULT
                    }
                } else {
                    copied.paint.typeface = context?.let {
                        ResourcesCompat.getFont(it, R.font.default_canvas)
                    } ?: Typeface.DEFAULT
                }
            } else {
                copied.paint.typeface = context?.let {
                    ResourcesCompat.getFont(it, R.font.default_canvas)
                } ?: Typeface.DEFAULT
            }
            copied
        }

        _canvasActions.push(
            CanvasAction.UpdateCanvasElementsOrder(
                oldList.map { it.copy(context = null) },
                updatedList.map { it.copy(context = null) }
            )
        )
        _redoStack.clear()
        _canvasElements.value = updatedList
        notifyUndoRedoChanged()
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
                                            it, R.font.default_canvas
                                        )
                                    } ?: Typeface.DEFAULT
                                }
                            } else {
                                updatedElement.paint.typeface = updatedElement.context?.let {
                                    ResourcesCompat.getFont(
                                        it, R.font.default_canvas
                                    )
                                } ?: Typeface.DEFAULT
                            }
                        } else {
                            // Ensure non-text elements or text elements without fontId also have a default typeface if applicable
                            updatedElement.paint.typeface = updatedElement.context?.let {
                                ResourcesCompat.getFont(
                                    it, R.font.default_canvas
                                )
                            } ?: Typeface.DEFAULT
                        }
                        updatedElement
                    }
                }
            }
        }
    }

    fun enterMaskMode() {
        _isMaskingMode.value = true
    }

    fun enterDrawingMode(context: Context) {
        startDrawSession(context)
        _isDrawingMode.value = true
    }

    fun exitDrawingMode(commit: Boolean = false) {
        if (commit) commitDrawSession() else discardDrawSession()
        _isDrawingMode.value = false
    }

    fun startPicking(slot: PickerTarget) {
        _activePicker.value = slot
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
            PickerTarget.EYE_DROPPER_OVERLAY -> setElementOverlay(color)
            PickerTarget.EYE_DROPPER_TEXT_FILL -> setTextColor(color)
            PickerTarget.EYE_DROPPER_TEXT_STROKE -> setTextBorder(
                true, color, _borderWidth.value!!
            )

            PickerTarget.EYE_DROPPER_SHADOW -> setTextShadow(
                true, color, _shadowDx.value!!, _shadowDy.value!!
            )

            PickerTarget.EYE_DROPPER_LABEL -> setTextLabel(true, color, _labelShape.value!!)
            PickerTarget.COLOR_PICKER_BACKGROUND -> setCanvasBackgroundColor(color)
            PickerTarget.COLOR_PICKER_OVERLAY -> setElementOverlay(color)
            PickerTarget.COLOR_PICKER_TEXT_FILL -> setTextColor(color)
            PickerTarget.COLOR_PICKER_TEXT_STROKE -> setTextBorder(
                true, color, _borderWidth.value!!
            )

            PickerTarget.COLOR_PICKER_SHADOW -> setTextShadow(
                true, color, _shadowDx.value!!, _shadowDy.value!!
            )

            PickerTarget.COLOR_PICKER_IMAGE_SHADOW -> setTextShadow(
                true, color, _shadowDx.value!!, _shadowDy.value!!
            )

            PickerTarget.COLOR_PICKER_LABEL -> setTextLabel(true, color, _labelShape.value!!)
            PickerTarget.COLOR_PICKER_GRADIENT -> {
                updateSelectedStopColor(color)
            }

            PickerTarget.EYE_DROPPER_GRADIENT -> {
                updateSelectedStopColor(color)   // same fix
            }

            PickerTarget.EYE_DROPPER_DRAW_STROKE -> {
                setBrushColor(color)
            }

            PickerTarget.EYE_DROPPER_DRAW_FILL -> {
                setBrushColor(color)
            }

            PickerTarget.COLOR_PICKER_DRAW_STROKE -> {
                setBrushColor(color)
            }

            PickerTarget.COLOR_PICKER_DRAW_FILL -> {
                setBrushColor(color)
            }

            PickerTarget.EYE_DROPPER_SHAPE_STROKE -> setStrokeColor(color)
            PickerTarget.EYE_DROPPER_SHAPE_FILL -> setFillColor(color)
            PickerTarget.COLOR_PICKER_SHAPE_STROKE -> setStrokeColor(color)
            PickerTarget.COLOR_PICKER_SHAPE_FILL -> setFillColor(color)

            PickerTarget.EYE_DROPPER_IMAGE_STROKE -> setImageBorder(
                true, color, _borderWidth.value ?: 1f
            )

            PickerTarget.COLOR_PICKER_IMAGE_STROKE -> setImageBorder(
                true, color, _borderWidth.value ?: 1f
            )

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

            GradientPickerTarget.DRAW_STROKE -> if (gradientItem != null) setBrushGradient(
                gradientItem
            ) else setBrushGradient(null)

            GradientPickerTarget.DRAW_FILL -> if (gradientItem != null) setBrushGradient(
                gradientItem
            ) else setBrushGradient(null)

            null -> {}
            GradientPickerTarget.SHAPE_STROKE -> if (gradientItem != null) setStrokeGradient(
                gradientItem
            ) else setStrokeGradient(null)

            GradientPickerTarget.SHAPE_FILL -> if (gradientItem != null) setFillGradient(
                gradientItem
            ) else setFillGradient(null)

            GradientPickerTarget.OVERLAY -> if (gradientItem != null) setElementOverlayGradient(
                gradientItem
            ) else setElementOverlayGradient(null)

            GradientPickerTarget.IMAGE_STROKE -> if (gradientItem != null) setImageStrokeGradient(
                gradientItem, _borderWidth.value ?: 1f
            ) else clearImageStrokeGradients()
        }
    }

    fun setElementOverlayGradient(gradient: GradientItem?) {
        val element = _selectedElements.value?.firstOrNull() ?: return

        val oldGradient = element.overlayGradient

        if (oldGradient != gradient) {

            val action = CanvasAction.SetOverlayGradient(
                element.id, oldGradient, gradient
            )

            _canvasActions.push(action)
            _redoStack.clear()

            element.overlayGradient = gradient

            if (gradient != null) {
                element.overlayColor =
                    Color.TRANSPARENT  // ✅ clear solid color when gradient applied
                // ✅ ensure opacity is non-zero so CanvasView actually draws it
                if (element.overlayOpacity == 0) {
                    element.overlayOpacity = 255
                }
                element.hasOverlay = true
            } else {
                element.hasOverlay =
                    element.overlayOpacity > 0 && element.overlayColor != Color.TRANSPARENT
            }

            applyAction(action, true)
            notifyUndoRedoChanged()
            notifyCanvasUpdated()
        }
    }

    // Snapshots of selected elements captured at the START of a seekbar drag.
    // Used by commit functions so the undo action has the true "before" value,
    // not the already-previewed intermediate state.
    private var lineSpacingDragSnapshot: Map<String, CanvasElement> = emptyMap()
    private var letterSpacingDragSnapshot: Map<String, CanvasElement> = emptyMap()

    fun beginLineSpacingDrag() {
        lineSpacingDragSnapshot = _canvasElements.value
            ?.filter { it.isSelected && it.type == ElementType.TEXT }
            ?.associate { it.id to it.copy(context = null) }
            ?: emptyMap()
    }

    fun setLineSpacing(spacing: Float) {
        _lineSpacing.value = spacing
        applyChangesToSelectedTextElementsPreview()
    }

    fun commitLineSpacing() {
        commitWithSnapshot(lineSpacingDragSnapshot)
        lineSpacingDragSnapshot = emptyMap()
    }

    fun beginLetterSpacingDrag() {
        letterSpacingDragSnapshot = _canvasElements.value
            ?.filter { it.isSelected && it.type == ElementType.TEXT }
            ?.associate { it.id to it.copy(context = null) }
            ?: emptyMap()
    }

    fun setLetterSpacing(spacing: Float) {
        _letterSpacing.value = spacing
        applyChangesToSelectedTextElementsPreview()
    }

    fun commitLetterSpacing() {
        commitWithSnapshot(letterSpacingDragSnapshot)
        letterSpacingDragSnapshot = emptyMap()
    }

    /**
     * Pushes a single [CanvasAction.UpdateElement] using [snapshot] as the "before" state
     * and the current canvas state as "after". Skips the push entirely if nothing changed.
     */
    private fun commitWithSnapshot(snapshot: Map<String, CanvasElement>) {
        if (snapshot.isEmpty()) return
        val currentList = _canvasElements.value ?: return
        var pushed = false
        currentList.forEach { element ->
            val before = snapshot[element.id] ?: return@forEach
            val after = element.copy(context = null)
            // Only push if something actually changed — avoids phantom undo entries
            if (before.lineSpacing != after.lineSpacing || before.letterSpacing != after.letterSpacing) {
                _canvasActions.push(CanvasAction.UpdateElement(element.id, after, before))
                pushed = true
            }
        }
        if (pushed) {
            _redoStack.clear()
            notifyUndoRedoChanged()
        }
    }

    fun setLetterCasing(casing: LetterCasing) {
        _letterCasing.value = casing
        applyChangesToSelectedTextElements()
    }

    fun setKasheeda(kasheeda: Int) {
        _kasheeda.value = kasheeda
        if (kasheeda > 1) {
            markSelectedTextElementAsPremium(true)
        } else {
            markSelectedTextElementAsPremium(false)
        }
        applyChangesToSelectedTextElements()
    }

    private fun markSelectedTextElementAsPremium(isPremium: Boolean) {
        val isSubscribed = billingManager.isSubscribed.value
        val updatedList = _canvasElements.value?.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                element.copy(
                    isPremium = isPremium,
                    isSubscribed = isSubscribed
                )
            } else element
        } ?: return
        _canvasElements.value = updatedList
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

        // Start auto-batching if needed
        selectedElements.firstOrNull()?.let { startAutoBatchIfNeeded(it.id) }

        // Prepare to store old sizes for undo/redo purposes
        val oldSizes = selectedElements.map { it.paintTextSize }

        val canvasW = _canvasSize.value?.width ?: 0f
        val maxW = if (canvasW > 0f) canvasW * 0.85f else 0f

        // Update the font size for each selected element
        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                element.copy().apply {
                    // Apply new font size
                    paintTextSize = size
                    paint.textSize = size
                    paint.typeface = applyTypefaceFromFontList() // Reapply the typeface if needed
                    if (boxWidth == null && maxW > 0f && paint.measureText(getTextWithKashida()) > maxW) {
                        boxWidth = maxW
                    }
                }
            } else {
                element
            }
        }

        // Update the canvas with the new list of elements
        _canvasElements.value = updatedList

        if (currentBatchAction == null) {
            // Push the undo action for all updated elements
            selectedElements.forEachIndexed { idx, oldElement ->
                val newElement = updatedList.find { it.id == oldElement.id }!!
                _canvasActions.push(
                    CanvasAction.UpdateElement(
                        elementId = newElement.id,
                        newElement = newElement.copy(context = null),
                        oldElement = oldElement.copy(paintTextSize = oldSizes[idx], context = null) // Revert back to old size on undo
                    )
                )
            }

            // Clear redo stack after applying changes
            _redoStack.clear()

            // Notify UI to update undo/redo status
            notifyUndoRedoChanged()
        }
    }

    fun setImageBorder(enabled: Boolean, color: Int, width: Float) {
        _borderColor.value = color
        _borderWidth.value = width
        _hasBorder.value = enabled
        applyChangesToSelectedImageElements()
    }

    fun setImageStrokeGradient(gradient: GradientItem, width: Float) {
        _strokeGradient.value = gradient
        _borderWidth.value = width
        _hasBorder.value = true
        applyChangesToSelectedImageElements()
    }

    fun clearImageStrokeGradients() {
        _strokeGradient.value = null
        applyChangesToSelectedImageElements()
    }

    private fun applyChangesToSelectedImageElements() {
        val currentList = _canvasElements.value?.toMutableList() ?: return
        var oldElement: CanvasElement? = null
        var newElement: CanvasElement? = null
        var targetId: String? = null

        val updatedList = currentList.map { element ->
            if (element.isSelected && (element.type == ElementType.IMAGE || element.type == ElementType.STICKER || element.type == ElementType.SHAPE)) {
                oldElement = element.copy(context = null)
                targetId = element.id

                val updated = element.copy(
                    hasStroke = _hasBorder.value ?: element.hasStroke,
                    strokeColor = _borderColor.value ?: element.strokeColor,
                    strokeWidth = _borderWidth.value ?: element.strokeWidth,
                    strokeGradient = _strokeGradient.value ?: element.strokeGradient
                )
                newElement = updated.copy(context = null)

                updated
            } else element
        }

        if (oldElement != null && newElement != null && targetId != null) {
            _canvasActions.push(CanvasAction.UpdateElement(targetId!!, newElement!!, oldElement!!))
            _redoStack.clear()
            notifyUndoRedoChanged()
            markChanged()
        }
        _canvasElements.value = updatedList
    }

    fun saveFontPanelState(
        language: String, category: String?, scrollIndex: Int = 0, scrollOffset: Int = 0
    ) {
        _fontPanelState.value = FontPanelState(language, category, scrollIndex, scrollOffset)
    }

    fun getFontPanelState(): FontPanelState = _fontPanelState.value ?: FontPanelState()

    /**
     * Applies the current LiveData values to selected text elements WITHOUT pushing to the
     * undo stack. Use this during continuous gestures (seekbar drag) so every intermediate
     * value doesn't pollute undo history. Call [applyChangesToSelectedTextElements] once on
     * finger-up to commit a single undoable action.
     */
    private fun applyChangesToSelectedTextElementsPreview() {
        val currentList = _canvasElements.value?.toMutableList() ?: return
        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                element.copy(
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
            } else element
        }
        // Update canvas visuals immediately, no undo entry
        _canvasElements.value = updatedList
    }

    private fun applyChangesToSelectedTextElements() {
        val currentList = _canvasElements.value?.toMutableList() ?: return
        var oldElement: CanvasElement? = null
        var newElement: CanvasElement? = null
        var targetId: String? = null

        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT) {
                oldElement = element.copy(context = null)
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

                newElement = updated.copy(context = null)
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

        val offsetX = 20f
        val offsetY = 20f

        // Collect GROUP sentinel ids among the selection so we can include their children.
        val selectedGroupIds = selected
            .filter { it.type == ElementType.GROUP }
            .map { it.id }.toSet()

        // Build the full set to copy: selected items + any children of selected groups
        // that are not already directly selected (avoids duplicates).
        val selectedIds = selected.map { it.id }.toSet()
        val groupChildren = if (selectedGroupIds.isNotEmpty()) {
            currentList.filter { el ->
                el.type != ElementType.GROUP &&
                        el.groupId != null &&
                        el.groupId in selectedGroupIds &&
                        el.id !in selectedIds
            }
        } else emptyList()

        val allToCopy = selected + groupChildren

        // Map old element id → new element id so children can reference the new sentinel id.
        val idRemapSentinels = selectedGroupIds.associateWith { UUID.randomUUID().toString() }

        val copiedElements = allToCopy.map { element ->
            val newId = if (element.type == ElementType.GROUP && element.id in idRemapSentinels)
                idRemapSentinels[element.id]!!
            else UUID.randomUUID().toString()

            // Remap groupId: if the element's groupId points to a copied sentinel, use the new id
            val newGroupId = element.groupId?.let { idRemapSentinels[it] } ?: run {
                // If a selected non-group element has no groupId (standalone), keep null
                if (element.type != ElementType.GROUP) null else null
            }

            val copied = element.copy(
                id = newId,
                isSelected = false,
                groupId = newGroupId,
                x = if (element.type != ElementType.GROUP) element.x + offsetX else element.x,
                y = if (element.type != ElementType.GROUP) element.y + offsetY else element.y
            )
            copied.paint.typeface = copied.applyTypefaceFromFontList()
            copied
        }

        _canvasElements.value = currentList + copiedElements

        copiedElements.forEach { copied ->
            when {
                copied.type == ElementType.GROUP -> { /* sentinel — no undo action needed separately */ }
                copied.type == ElementType.TEXT -> _canvasActions.push(CanvasAction.AddText(copied.text, copied))
                else -> _canvasActions.push(CanvasAction.AddSticker(copied))
            }
        }
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun setCanvasSize(newSize: CanvasSize) {
        val oldSize = _canvasSize.value
        if (oldSize != newSize) {
            _canvasActions.push(
                CanvasAction.SetCanvasSize(newSize, oldSize ?: newSize)
            )
            _redoStack.clear()
            _canvasSize.value = newSize
            syncBackgroundElementSize(newSize)   // ← ADD THIS
            notifyUndoRedoChanged()
        }
    }

    // ADD this private helper below setCanvasSize:
    private fun syncBackgroundElementSize(size: CanvasSize) {
        val current = _canvasElements.value?.toMutableList() ?: return
        val bgIndex = current.indexOfFirst { it.type == ElementType.BACKGROUND }
        if (bgIndex == -1) return
        val bg = current[bgIndex]
        current[bgIndex] = bg.copy(
            x = size.width / 2f,
            y = size.height / 2f,
            logicalContentWidth = size.width,
            logicalContentHeight = size.height
        ).also {
            it.isLocked = bg.isLocked
            it.paintColor = bg.paintColor
            it.fillGradient = bg.fillGradient
            it.bitmap = bg.bitmap
            it.bitmapData = bg.bitmapData
            it.cachedAdjustedBitmap = bg.cachedAdjustedBitmap
            it.context = bg.context
            it.updatePaintProperties()
        }
        _canvasElements.value = current
    }

    fun resizeCanvas(newSize: CanvasSize) {
        setCanvasSize(newSize)  // reuses undo stack, LiveData update, everything
        hasChanges.value = true
    }

    fun setCanvasUnit(newUnit: UnitType) {
        _canvasUnit.value = newUnit
        notifyUndoRedoChanged()
    }

    fun endBatchUpdate(elementId: String) {
        val currentList = _canvasElements.value ?: emptyList()
        val finalElement = currentList.find { it.id == elementId }
            ?.copy(context = null) // Capture final state for undo

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

                is BatchedCanvasAction.GenericBatch -> {
                    val initialElement =
                        (currentBatchAction as BatchedCanvasAction.GenericBatch).initialElement
                    if (initialElement != finalElement) { // Only push if any properties changed
                        _canvasActions.push(
                            CanvasAction.UpdateElement(
                                elementId = elementId,
                                newElement = finalElement,
                                oldElement = initialElement
                            )
                        )
                    }
                }
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
            ?.copy(context = null) // Capture initial state for undo

        if (initialElement != null) {
            currentBatchAction = when (actionType) {
                "drag" -> BatchedCanvasAction.DragBatch(elementId, initialElement)
                "rotate" -> BatchedCanvasAction.RotateBatch(elementId, initialElement)
                "resize" -> BatchedCanvasAction.ResizeBatch(elementId, initialElement)
                "adjustments" -> BatchedCanvasAction.GenericBatch(elementId, initialElement)
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
                            it, R.font.default_canvas
                        )
                    } ?: Typeface.DEFAULT
                }
            } else {
                // Ensure non-text elements or text elements without fontId also have a default typeface if applicable
                elementToUpdate.paint.typeface = elementToUpdate.context?.let {
                    ResourcesCompat.getFont(
                        it, R.font.default_canvas
                    )
                } ?: Typeface.DEFAULT
            }


            // Replace the entire element with the updated version (now with the correct typeface)
            _canvasElements.value = currentList.map {
                if (it.id == elementToUpdate.id) elementToUpdate else it // Use elementToUpdate here
            }

            // Only push to undo stack if no batch action is in progress.
            // Continuous actions (drag, rotate, resize) will be handled by endBatchUpdate.
            if (currentBatchAction == null) {
                val oldCopy = oldElement.copy(
                    context = null,
                    drawStrokes = oldElement.drawStrokes?.map { it.copy(path = Path(it.path)) }
                        ?.toMutableList())
                val newCopy = elementToUpdate.copy(
                    context = null,
                    drawStrokes = elementToUpdate.drawStrokes?.map { it.copy(path = Path(it.path)) }
                        ?.toMutableList())

                _canvasActions.push(
                    CanvasAction.UpdateElement(
                        elementId = elementToUpdate.id, newElement = newCopy, oldElement = oldCopy
                    )
                )
                _redoStack.clear()
                notifyUndoRedoChanged()
            }
        }
    }

    fun updateCanvasElementsOrderAndZIndex(reorderedList: List<CanvasElement>) {
        val oldList = _canvasElements.value ?: emptyList()

        // ── Expand reorderedList to include collapsed children ────────────────
        // reorderedList contains only the items visible in the RecyclerView.
        // Children of COLLAPSED groups are absent — we re-insert them beneath
        // their GROUP sentinel so their z-indices are always updated correctly.
        val reorderedIds = reorderedList.map { it.id }.toSet()

        // Collapsed children: present in canvasElements but absent from reorderedList
        val collapsedChildrenByGroup: Map<String, List<CanvasElement>> =
            oldList
                .filter { it.groupId != null && it.id !in reorderedIds }
                .groupBy { it.groupId!! }

        // Build the full ordered list: after each GROUP sentinel inject its
        // collapsed children (preserving their relative old z-index order so
        // internal ordering survives collapse/expand cycles).
        val fullOrderedList = mutableListOf<CanvasElement>()
        for (element in reorderedList) {
            fullOrderedList.add(element)
            if (element.type == ElementType.GROUP) {
                val collapsed = collapsedChildrenByGroup[element.id]
                    ?.sortedByDescending { it.zIndex }
                    ?: emptyList()
                fullOrderedList.addAll(collapsed)
            }
        }

        // ── Assign z-indices: position 0 (top of layers panel) = highest z ──
        val totalCount = fullOrderedList.size
        val updatedList = fullOrderedList.mapIndexed { index, element ->
            val newZ = totalCount - 1 - index
            val copiedElement = element.copy(zIndex = newZ, context = element.context)
            if (copiedElement.type == ElementType.TEXT && copiedElement.fontId != null) {
                val font = localFonts.value.find { it.id.toString() == copiedElement.fontId }
                if (font != null && font.file_path?.isNotBlank() == true) {
                    try {
                        copiedElement.paint.typeface = Typeface.createFromFile(font.file_path)
                    } catch (e: Exception) {
                        println("Error re-applying typeface in updateCanvasElementsOrderAndZIndex: ${font.file_path}. Error: ${e.message}")
                        copiedElement.paint.typeface = copiedElement.context?.let {
                            ResourcesCompat.getFont(it, R.font.default_canvas)
                        } ?: Typeface.DEFAULT
                    }
                } else {
                    copiedElement.paint.typeface = copiedElement.context?.let {
                        ResourcesCompat.getFont(it, R.font.default_canvas)
                    } ?: Typeface.DEFAULT
                }
            } else {
                copiedElement.paint.typeface = copiedElement.context?.let {
                    ResourcesCompat.getFont(it, R.font.default_canvas)
                } ?: Typeface.DEFAULT
            }
            copiedElement
        }

        _canvasActions.push(
            CanvasAction.UpdateCanvasElementsOrder(
                oldList.map { it.copy(context = null) },
                updatedList.map { it.copy(context = null) }
            )
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
                    context?.let { ResourcesCompat.getFont(it, R.font.default_canvas) }
                        ?: Typeface.DEFAULT
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
            elementsToSelect.firstOrNull { it.type == ElementType.IMAGE || it.type == ElementType.STICKER }
        _currentImageFilter.value = firstSelectedImageElement?.imageFilter
    }

    fun applyMaskToSelected(maskedBitmap: Bitmap) {
        // Encoding bitmapToBase64 is expensive — run on Default, update LiveData on Main.
        val currentList = canvasElements.value ?: return
        val selected = currentList.firstOrNull {
            it.isSelected && (it.type == ElementType.IMAGE || it.type == ElementType.STICKER
                    || it.type == ElementType.SHAPE || it.type == ElementType.BACKGROUND)
        } ?: return

        val context = selected.context ?: return

        viewModelScope.launch {
            // --- background thread: encode (can take 1-3s on large bitmaps) ---
            val newBitmapData = withContext(Dispatchers.Default) {
                ImageProcessor.bitmapToBase64(maskedBitmap)
            }

            // --- main thread: commit to LiveData atomically ---
            val oldCopy = selected.copy(context = null)

            val newElement = selected.copy(
                context = context,
                bitmap = maskedBitmap,
                bitmapData = newBitmapData
            ).apply { updatePaintProperties() }

            _canvasActions.push(
                CanvasAction.UpdateElement(
                    elementId = selected.id,
                    newElement = newElement.copy(context = null),
                    oldElement = oldCopy
                )
            )
            _redoStack.clear()

            // Update canvasElements — this triggers canvasManager.syncElements in EditorFragment.
            _canvasElements.value = currentList.map {
                if (it.id == selected.id) newElement else it
            }

            // Update selectedElements so the selectedElements observer re-fires with
            // the updated bitmapData (sameSelectionAs compares bitmapData, not just IDs).
            _selectedElements.value = _canvasElements.value?.filter { it.isSelected }
                ?: emptyList()

            notifyUndoRedoChanged()

            // Signal completion — EditorFragment (or BgRemovalFragment) observes this
            // and calls navigateUp() only AFTER the data is committed to LiveData.
            // Without this, navigateUp() was called synchronously in onMaskConfirmed
            // before this coroutine ran, so the fragment was gone before the canvas updated.
            _maskAppliedEvent.tryEmit(Unit)
        }
    }

    private fun getTypefaceForElement(element: CanvasElement, context: Context?): Typeface {
        return if (element.type == ElementType.TEXT && element.fontId != null) {
            val font = localFonts.value.find { it.id.toString() == element.fontId }
            font?.file_path?.takeIf { it.isNotBlank() }?.let { path ->
                try {
                    Typeface.createFromFile(path)
                } catch (e: Exception) {
                    ResourcesCompat.getFont(
                        context ?: return Typeface.DEFAULT, R.font.default_canvas
                    ) ?: Typeface.DEFAULT
                }
            } ?: ResourcesCompat.getFont(context ?: return Typeface.DEFAULT, R.font.default_canvas)
            ?: Typeface.DEFAULT
        } else {
            ResourcesCompat.getFont(context ?: return Typeface.DEFAULT, R.font.default_canvas)
                ?: Typeface.DEFAULT
        }
    }

    fun onCanvasSelectionChanged(selectedListFromCanvas: List<CanvasElement>) {
        val currentElements = _canvasElements.value?.toMutableList() ?: mutableListOf()
        val context = currentElements.firstOrNull()?.context

        // Decide whether to collapse a group selection to its sentinel.
        // Rules:
        //   - Single child selected alone (e.g. tapped from layers panel or individually
        //     selected) → keep the child selected, do NOT collapse to sentinel.
        //     This preserves individual drag/edit on that child.
        //   - All children of a group selected together → collapse to sentinel so the
        //     whole group moves as one unit.
        val commonGroupId = selectedListFromCanvas
            .mapNotNull { it.groupId }
            .distinct()
            .singleOrNull()
            ?.takeIf { gid -> selectedListFromCanvas.all { it.groupId == gid } }

        val collapseToSentinel = commonGroupId != null && selectedListFromCanvas.size > 1

        val selectedIds: Set<String> = if (collapseToSentinel) {
            // Whole group selected — mark sentinel selected, not children
            setOf(commonGroupId!!)
        } else {
            selectedListFromCanvas.map { it.id }.toSet()
        }

        val updatedList = currentElements.map { element ->
            val updated =
                element.copy(isSelected = selectedIds.contains(element.id), context = context)
            updated.paint.typeface = getTypefaceForElement(updated, context)
            updated
        }

        _canvasElements.value = updatedList
        refreshSelectedElements()

        val firstText = selectedListFromCanvas.firstOrNull { it.type == ElementType.TEXT }
        val firstImage =
            selectedListFromCanvas.firstOrNull { it.type == ElementType.IMAGE || it.type == ElementType.STICKER }
        val firstDraw = selectedListFromCanvas.firstOrNull { it.type == ElementType.DRAW }
        val firstShape = selectedListFromCanvas.firstOrNull { it.type == ElementType.SHAPE }

        when {
            firstText != null -> {
                syncUiFormattingWithSelectedTextElement(firstText)
                _currentImageFilter.value = null
            }

            firstImage != null -> {
                syncUiFormattingWithSelectedTextElement(firstImage)
                _currentImageFilter.value = firstImage.imageFilter
                val adj = firstImage.adjustments
                if (adj != null) {
                    _brightness.value = adj.brightness
                    _contrast.value = adj.contrast
                    _saturation.value = adj.saturation
                    _shadows.value = adj.shadows
                    _temperature.value = adj.temperature
                    _tint.value = adj.tint
                    _vibrance.value = adj.vibrance
                    _sharpness.value = adj.sharpness
                    _highlights.value = adj.highlights
                    _clarity.value = adj.clarity
                    _fade.value = adj.fade
                    _featherRadius.value = firstImage.featherRadius
                    _featherWidth.value = firstImage.featherWidth
                }
                // Always sync blur/opacity state so UI reflects the element's current values
                _blur.value = firstImage.blurValue
                _blurValue.value = firstImage.blurValue
                _hasBlur.value = firstImage.hasBlur
                _opacity.value = firstImage.paintAlpha
            }

            firstDraw != null -> {
                // 🖌️ Update brush LiveData to defaults or selected draw element values
                _brushColor.value = firstDraw.drawStrokes?.lastOrNull()?.color ?: Color.BLACK
                _brushThickness.value = firstDraw.drawStrokes?.lastOrNull()?.thickness ?: 10f
                _brushHardness.value = firstDraw.drawStrokes?.lastOrNull()?.hardness ?: 1f
                _currentBrushStyle.value =
                    firstDraw.drawStrokes?.lastOrNull()?.style ?: BrushStyle.BRUSH
                _brushGradient.value = firstDraw.drawStrokes?.lastOrNull()?.gradient
            }

            firstShape != null -> {
                if (firstShape.bitmap != null) {
                    _imagePanX.value = firstShape.imagePanX
                    _imagePanY.value = firstShape.imagePanY
                    _imageScale.value = firstShape.imageScale
                    _imageFitMode.value = firstShape.imageFitMode ?: "cover"
                }
            }

            else -> {
                // No text, image, or draw → reset brushes
                _brushColor.value = Color.BLACK
                _brushThickness.value = 10f
                _brushHardness.value = 1f
                _currentBrushStyle.value = BrushStyle.BRUSH
                _brushGradient.value = null
            }
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
            val (angle, dist) = dxDyToAngleDistance(textElement.shadowDx, textElement.shadowDy)
            _shadowAngle.value = angle
            _shadowDistance.value = dist

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
        _currentTextSize.value = 50f
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
        _blendingType.value = BlendType.SRC
    }

    private fun refreshSelectedElements() {
        val currentList = _canvasElements.value ?: emptyList()
        // Surface whatever is marked isSelected.
        // GROUP sentinels are included as-is -- they represent their group as 1 unit.
        // CanvasView.syncElements expands sentinel -> children for bounds/transforms.
        // ViewModel consumers (toolbar, layers panel) see the sentinel as 1 item.
        _selectedElements.value = currentList.filter { it.isSelected }
    }

    fun setCanvasBackgroundColor(color: Int) {
        val previousColor = _backgroundColor.value ?: Color.WHITE
        if (color != previousColor) {
            _backgroundColor.value = color
        }
    }

    fun syncShadowStateFromSelected() {
        val element = _selectedElements.value?.firstOrNull() ?: return

        _shadowColor.value = element.shadowColor
        _shadowDx.value = element.shadowDx
        _shadowDy.value = element.shadowDy
        _shadowRadius.value = element.shadowRadius
        _shadowOpacity.value = element.shadowOpacity

        // Derive UI angle/distance from the stored dx/dy so existing templates
        // show sensible values in the new seekbars without any data migration.
        val (angle, distance) = dxDyToAngleDistance(element.shadowDx, element.shadowDy)
        _shadowAngle.value = angle
        _shadowDistance.value = distance
    }

    // Convert UI angle (0–360°) + distance (0–100px) → shadowDx / shadowDy.
    // Angle 0° = right, 90° = down, 180° = left, 270° = up (standard CSS convention).
    private fun angleDistanceToDxDy(angleDeg: Float, distance: Float): Pair<Float, Float> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = (Math.cos(rad) * distance).toFloat()
        val dy = (Math.sin(rad) * distance).toFloat()
        return dx to dy
    }

    // Convert existing dx/dy back to angle + distance for display in the UI.
    private fun dxDyToAngleDistance(dx: Float, dy: Float): Pair<Float, Float> {
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val angleDeg = if (distance < 0.001f) 135f else {
            var deg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            deg
        }
        return angleDeg to distance
    }

    fun disableFeature(type: String) {
        val element = _selectedElements.value?.firstOrNull() ?: return

        val alreadyDisabled = when (type) {
            "Shadow"  -> !element.hasShadow
            "Stroke"  -> !element.hasStroke
            "Blur"    -> !element.hasBlur
            "Overlay" -> !element.hasOverlay
            "Light"   -> !element.hasLight
            "Color"   -> !element.hasColor
            "Detail"  -> !element.hasDetail
            "Feather" -> !element.hasFeather   // ← ADD
            else      -> false
        }

        if (!alreadyDisabled) {
            val updatedElement = element.copy().apply {
                when (type) {
                    "Shadow"  -> hasShadow  = false
                    "Stroke"  -> hasStroke  = false
                    "Blur"    -> hasBlur    = false
                    "Overlay" -> hasOverlay = false
                    "Light"   -> hasLight   = false
                    "Color"   -> hasColor   = false
                    "Detail"  -> hasDetail  = false
                    "Feather" -> hasFeather = false   // ← ADD
                }
            }
            updateCanvasElement(updatedElement)
        }
    }

    fun toggleFeature(type: String) {
        val element = _selectedElements.value?.firstOrNull() ?: return
        val updatedElement = element.copy().apply {
            when (type) {
                "Shadow" -> {
                    hasShadow = !hasShadow
                    if (hasShadow) applyShadowPresets(this)
                }
                "Stroke" -> {
                    hasStroke = !hasStroke
                    if (hasStroke) applyStrokePresets(this)
                }
                "Blur" -> {
                    hasBlur = !hasBlur
                    if (hasBlur) applyBlurPresets(this)
                }
                "Overlay" -> {
                    hasOverlay = !hasOverlay
                    if (hasOverlay) applyOverlayPresets(this)
                }
                "Feather" -> {                            // ← ADD
                    hasFeather = !hasFeather               // ← ADD
                    if (hasFeather) applyFeatherPresets(this)  // ← ADD
                }                                          // ← ADD
                "Light"  -> hasLight  = !hasLight
                "Color"  -> hasColor  = !hasColor
                "Detail" -> hasDetail = !hasDetail
            }
        }
        updateCanvasElement(updatedElement)
    }

    fun enableFeature(type: String) {
        val element = _selectedElements.value?.firstOrNull() ?: return

        val alreadyEnabled = when (type) {
            "Shadow"  -> element.hasShadow
            "Stroke"  -> element.hasStroke
            "Blur"    -> element.hasBlur
            "Overlay" -> element.hasOverlay
            "Light"   -> element.hasLight
            "Color"   -> element.hasColor
            "Detail"  -> element.hasDetail
            "Feather" -> element.hasFeather   // ← ADD
            else      -> true
        }

        if (!alreadyEnabled) {
            val updatedElement = element.copy().apply {
                when (type) {
                    "Shadow"  -> { hasShadow  = true; applyShadowPresets(this) }
                    "Stroke"  -> { hasStroke  = true; applyStrokePresets(this) }
                    "Blur"    -> { hasBlur    = true; applyBlurPresets(this) }
                    "Overlay" -> { hasOverlay = true; applyOverlayPresets(this) }
                    "Feather" -> { hasFeather = true; applyFeatherPresets(this) }  // ← ADD
                    "Light"   -> hasLight  = true
                    "Color"   -> hasColor  = true
                    "Detail"  -> hasDetail = true
                }
            }
            updateCanvasElement(updatedElement)
        }
    }

    private fun applyFeatherPresets(element: CanvasElement) {
        if (element.featherRadius == 0f) {
            element.featherRadius = 30f   // sensible default: 30% feather
            _featherRadius.value = element.featherRadius
        }
    }

    private fun applyShadowPresets(element: CanvasElement) {
        if (element.shadowRadius <= 1f && element.shadowDx <= 1f && element.shadowDy <= 1f && element.shadowOpacity <= 1) {
            element.shadowRadius = 10f
            element.shadowDx = 15f
            element.shadowDy = 15f
            element.shadowOpacity = 50
            element.shadowColor = Color.BLACK

            _shadowRadius.value = element.shadowRadius
            _shadowDx.value = element.shadowDx
            _shadowDy.value = element.shadowDy
            _shadowOpacity.value = element.shadowOpacity
            _shadowColor.value = element.shadowColor

            // Sync UI angle/distance to match preset dx/dy (135°, ~21px)
            val (angle, dist) = dxDyToAngleDistance(element.shadowDx, element.shadowDy)
            _shadowAngle.value = angle
            _shadowDistance.value = dist
        }
    }

    private fun applyStrokePresets(element: CanvasElement) {
        if (element.strokeWidth <= 1.1f) {
            element.strokeWidth = 5f
            element.strokeColor = Color.RED

            _borderWidth.value = element.strokeWidth
            _borderColor.value = element.strokeColor
        }
    }

    private fun applyBlurPresets(element: CanvasElement) {
        // Check if it's a valid type for blur
        val isValidType =
            element.type == ElementType.IMAGE || element.type == ElementType.STICKER || element.type == ElementType.SHAPE || (element.type == ElementType.BACKGROUND && element.bitmap != null)

        if (isValidType && element.blurValue == 0f) {
            element.blurValue = 10f // Default preset
            _blur.value = element.blurValue // SeekBar sync
        }
    }

    private fun applyOverlayPresets(element: CanvasElement) {
        if (element.overlayColor == Color.TRANSPARENT) {
            element.overlayColor = "#FF746C".toColorInt()
        }

        if (element.overlayOpacity == 0) {
            element.overlayOpacity = 255
        }
    }

    fun setImageShadow(
        enabled: Boolean,
        color: Int,
        dx: Float,
        dy: Float,
        radius: Float,
        opacity: Int,
        pushToUndo: Boolean = true
    ) {

        val element = _selectedElements.value?.firstOrNull() ?: return

        if (pushToUndo) {
            val action = CanvasAction.SetImageShadow(
                element.id,
                element.hasShadow,
                element.shadowColor,
                element.shadowDx,
                element.shadowDy,
                element.shadowRadius,
                element.shadowOpacity,
                enabled,
                color,
                dx,
                dy,
                radius,
                opacity
            )

            _canvasActions.push(action)
            _redoStack.clear()
            notifyUndoRedoChanged()
        }

        element.hasShadow = enabled
        element.shadowColor = color
        element.shadowDx = dx
        element.shadowDy = dy
        element.shadowRadius = radius.coerceAtLeast(0.1f)
        element.shadowOpacity = opacity.coerceIn(0, 255)

        syncShadowStateFromSelected()
        notifyCanvasUpdated()
    }

    private fun notifyCanvasUpdated() {
        _canvasElements.value = _canvasElements.value
    }

    fun setElementOverlay(color: Int) {
        val element = _selectedElements.value?.firstOrNull() ?: return

        val prevColor = element.overlayColor

        if (prevColor != color) {

            val action = CanvasAction.SetOverlay(
                element.id,
                element.hasOverlay,
                prevColor,
                element.overlayOpacity,
                element.overlayOpacity > 0,
                color,
                element.overlayOpacity
            )
            _canvasActions.push(action)
            _redoStack.clear()

            element.overlayColor = color
            element.overlayGradient = null  // ✅ clear gradient when solid color applied

            if (color == Color.TRANSPARENT) {
                element.hasOverlay = false
            } else {
                // ✅ ensure opacity is non-zero so CanvasView actually draws it
                if (element.overlayOpacity == 0) {
                    element.overlayOpacity = 255
                }
            }

            notifyUndoRedoChanged()
            applyAction(action, true)
            notifyCanvasUpdated()
        }
    }

    fun setElementOverlayOpacity(opacity: Int) {

        val element = _selectedElements.value?.firstOrNull() ?: return

        val prevOpacity = element.overlayOpacity
        val prevHasOverlay = element.hasOverlay

        if (prevOpacity != opacity) {

            val action = CanvasAction.SetOverlay(
                element.id,
                prevHasOverlay,
                element.overlayColor,
                prevOpacity,
                opacity > 0,
                element.overlayColor,
                opacity
            )
            _canvasActions.push(
                action
            )

            _redoStack.clear()

            element.overlayOpacity = opacity
            element.hasOverlay = opacity > 0

            notifyUndoRedoChanged()
            applyAction(action, true)
        }
    }

//    fun setCanvasBackgroundImage(bitmap: Bitmap?) {
//        val previousBitmap = _backgroundImage.value
//        // Only push action if there's a change
//        if (bitmap != previousBitmap) {
//            _canvasActions.push(CanvasAction.SetBackgroundImage(bitmap, previousBitmap))
//            _redoStack.clear()
//            _backgroundImage.value = bitmap
//            notifyUndoRedoChanged()
//        }
//    }

    fun setCanvasBackgroundImage(bitmap: Bitmap?, context: Context) {
        if (bitmap?.width!! <= 0 || bitmap.height!! <= 0) return

        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1

        val canvasW = _canvasSize.value?.width ?: return
        val canvasH = _canvasSize.value?.height ?: return

        // Place it right above the background element (zIndex 1) so it sits as a background-like layer
        val bgZIndex = currentList.firstOrNull { it.type == ElementType.BACKGROUND }?.zIndex ?: 0

        val element = CanvasElement(
            context = context,
            type = ElementType.IMAGE,
            bitmap = bitmap,
            bitmapData = ImageProcessor.bitmapToBase64(bitmap),
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintAlpha = 255,
            zIndex = newZIndex,
            // Full canvas logical size so the cover-scale math fills the canvas
            logicalContentWidth = canvasW,
            logicalContentHeight = canvasH,
            imageFitMode = "cover",
            scale = 1f
        )

        element.updatePaintProperties()
        _canvasActions.push(CanvasAction.AddSticker(element.copy(context = null, bitmap = null)))
        _redoStack.clear()
        _canvasElements.value = currentList + element
        notifyUndoRedoChanged()
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

    fun replaceSticker(bitmap: Bitmap?, context: Context, isPremium: Boolean = false) {
        if (bitmap == null) return

        val currentList = _canvasElements.value ?: emptyList()

        val selectedElement =
            currentList.find { it.isSelected && (it.type == ElementType.IMAGE || it.type == ElementType.STICKER) }

        val canvasW = _canvasSize.value?.width ?: return
        val canvasH = _canvasSize.value?.height ?: return

        val imageW = bitmap.width.toFloat()
        val imageH = bitmap.height.toFloat()
        val maxAllowedW = canvasW * 0.8f
        val maxAllowedH = canvasH * 0.8f

        var finalBitmap = bitmap
        if (imageW > maxAllowedW || imageH > maxAllowedH) {
            val scaleFactor = minOf(maxAllowedW / imageW, maxAllowedH / imageH)
            finalBitmap =
                bitmap.scale((imageW * scaleFactor).toInt(), (imageH * scaleFactor).toInt())
        }

        val updatedElement = selectedElement?.copy(
            bitmap = finalBitmap,
            bitmapData = ImageProcessor.bitmapToBase64(finalBitmap),
            isPremium = isPremium
        )

        updatedElement?.let { updateCanvasElement(it) }

        _canvasElements.value = currentList.map {
            if (it.id == selectedElement?.id) updatedElement!! else it
        }

        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun addSvgSticker(
        drawable: PictureDrawable,
        svgXml: String?,          // ✅ nullable — graceful fallback for legacy/import
        context: Context,
        isPremium: Boolean = false,
        applyWhiteTintInDarkMode: Boolean = false
    ) {
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1

        val canvasW = _canvasSize.value?.width ?: return
        val canvasH = _canvasSize.value?.height ?: return
        if (canvasW <= 0f || canvasH <= 0f) return

        val svgW = drawable.picture.width.takeIf { it > 0 }?.toFloat() ?: canvasW * 0.6f
        val svgH = drawable.picture.height.takeIf { it > 0 }?.toFloat() ?: canvasH * 0.6f
        val targetW = canvasW * 0.4f
        val targetH = canvasH * 0.4f
        val scaleFactor = minOf(targetW / svgW, targetH / svgH)

        val actualTint = applyWhiteTintInDarkMode && (svgXml == null || com.webscare.urducanvas.common.utils.SvgLoader.isSingleColorDarkSvg(svgXml))

        val element = CanvasElement(
            context = context,
            type = ElementType.STICKER,
            bitmap = null,
            bitmapData = null,
            svgData = svgXml,     // ✅ persisted — survives any scale, forever
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintAlpha = 255,
            zIndex = newZIndex,
            isPremium = isPremium,
            applyWhiteTintInDarkMode = actualTint
        ).apply {
            svgDrawable = drawable
            scale = scaleFactor
        }

        element.updatePaintProperties()
        _canvasActions.push(CanvasAction.AddSticker(element.copy(context = null)))
        _redoStack.clear()
        _canvasElements.value = currentList + element
        notifyUndoRedoChanged()
    }

    fun addSticker(
        bitmap: Bitmap?, context: Context, elementType: ElementType, isPremium: Boolean = false
    ) {
        if (bitmap == null) return
        _loadingStage.value = "Loading Image" to 50

        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1

        val canvasW = _canvasSize.value?.width ?: return
        val canvasH = _canvasSize.value?.height ?: return

        if (canvasW <= 0f || canvasH <= 0f) return  // ← GUARD: canvas not ready

        val imageW = bitmap.width.toFloat()
        val imageH = bitmap.height.toFloat()

        if (imageW <= 0f || imageH <= 0f) return  // ← GUARD: bad bitmap

        // Target: sticker should be at most 60% of canvas in either dimension
        val targetW = canvasW * 0.6f
        val targetH = canvasH * 0.6f

        // ✅ Never physically resize the bitmap — compute display scale instead.
        // This preserves full pixel resolution (critical for SVGs rasterized at 2× canvas size).
        val initialScale = when {
            imageW > targetW || imageH > targetH -> {
                // Bitmap is larger than canvas budget → shrink visually via scale
                minOf(targetW / imageW, targetH / imageH)
            }

            imageW < targetW * 0.2f || imageH < targetH * 0.2f -> {
                // Bitmap is very small → boost it up so it's visible
                minOf(targetW / imageW, targetH / imageH) * 0.5f
            }

            else -> {
                // Already in a good range → no scaling needed
                1f
            }
        }

        val element = CanvasElement(
            context = context,
            type = elementType,
            bitmap = bitmap,         // ← full-resolution bitmap, untouched
            bitmapData = ImageProcessor.bitmapToBase64(bitmap),
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintAlpha = 255,
            zIndex = newZIndex,
            isPremium = isPremium
        ).apply {
            scale = initialScale     // ← canvas matrix handles display size
        }

        element.updatePaintProperties()

        _canvasActions.push(
            CanvasAction.AddSticker(
                element.copy(context = null)
            )
        )

        _redoStack.clear()
        _canvasElements.value = currentList + element
        notifyUndoRedoChanged()
    }

    fun ensureBackgroundElement(context: Context) {
        // if we already have a background, do nothing
        if ((_canvasElements.value ?: emptyList()).any { it.type == ElementType.BACKGROUND }) return

        // _canvasSize is null when clearCanvas() resets the ViewModel (e.g. navigating back
        // from the editor). In that case there is no canvas to add a background to - bail out.
        val size = _canvasSize.value ?: return

        val isNightMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val defaultBgColor = if (isNightMode) Color.parseColor("#2B2B2B") else Color.WHITE

        // otherwise create and insert one
        val bg = CanvasElement(
            context = context,
            type = ElementType.BACKGROUND,
            x = size.width / 2f,
            y = size.height / 2f,
            paintColor = defaultBgColor,
            backgroundColor = defaultBgColor,
            fillGradient = null,
            bitmap = null
        ).apply {
            isLocked = true
            logicalContentWidth = size.width
            logicalContentHeight = size.height
            updatePaintProperties()
        }

        // prepend it so it’s always drawn first
        _canvasElements.value = listOf(bg) + (_canvasElements.value ?: emptyList())
        _backgroundColor.value = defaultBgColor
    }

    fun addText(text: String, context: Context) {
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f

        val scaledTextSize = (minOf(canvasW, canvasH) * 0.05f).coerceIn(20f, 200f)

        val element = CanvasElement(
            context = context,
            type = ElementType.TEXT,
            text = text,
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintColor = Color.BLACK,
            paintTextSize = scaledTextSize,
            alignment = TextAlignment.CENTER,
            paintAlpha = 255,
            fontId = null,
            zIndex = newZIndex
        )

        element.updatePaintProperties()
        element.originalTypeface = element.applyTypefaceFromFontList()
        element.paint.typeface = element.applyTypefaceFromFontList()

        val maxW = if (canvasW > 0f) canvasW * 0.85f else 0f
        if (maxW > 0f && element.paint.measureText(element.getTextWithKashida()) > maxW) {
            element.boxWidth = maxW
        }

        val action = CanvasAction.AddText(
            text, element.copy(context = null)
        ) // Push a copy for undo, without transient data
        _canvasActions.push(action)
        _redoStack.clear()
        _canvasElements.value = currentList + element
        selectedElement = element
        notifyUndoRedoChanged()
    }

    fun addTextWithFont(text: String, fontEntity: FontEntity?, context: Context) {
        _loadingStage.value = "Loading Font" to 50
        val currentList = _canvasElements.value ?: emptyList()
        val newZIndex = currentList.maxOfOrNull { it.zIndex }?.plus(1) ?: 1
        val canvasW = _canvasSize.value?.width ?: 0f
        val canvasH = _canvasSize.value?.height ?: 0f
        // Create base element
        val element = CanvasElement(
            context = context,
            type = ElementType.TEXT,
            text = text,
            x = canvasW / 2f,
            y = canvasH / 2f,
            paintColor = Color.BLACK,
            paintTextSize = 50f,
            alignment = TextAlignment.CENTER,
            paintAlpha = 255,
            fontId = fontEntity?.id.toString(),
            fontUrl = fontEntity?.file_url,
            zIndex = newZIndex,
            isPremium = fontEntity?.is_premium ?: false
        )

        // If a fontEntity was provided, try to apply it
        if (fontEntity != null) {
            element.fontId = fontEntity.id.toString()
            element.fontUrl = fontEntity.file_url
            element.paint.typeface = try {
                Typeface.createFromFile(fontEntity.file_path)
            } catch (e: Exception) {
                println("Error applying font: ${fontEntity.file_path}. Error: ${e.message}")
                element.fontId = null
                ResourcesCompat.getFont(context, R.font.default_canvas) ?: Typeface.DEFAULT
            }
        }

        // Update paint props after assigning typeface
        element.updatePaintProperties()

        val maxW = if (canvasW > 0f) canvasW * 0.85f else 0f
        if (maxW > 0f && element.paint.measureText(element.getTextWithKashida()) > maxW) {
            element.boxWidth = maxW
        }

        // Push action for undo/redo (store a copy without transient fields)
        val action = CanvasAction.AddText(
            text, element.copy(context = null)
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

        val hasSelectedText = currentList.any {
            it.isSelected && it.type == ElementType.TEXT
        }

        if (!hasSelectedText) {
            context?.let {
                addTextWithFont(
                    text = context.getString(R.string.dummyText),
                    fontEntity = fontEntity,
                    context = it
                )
            }
            return
        }

        val affectedElementsData = mutableListOf<Pair<String, String?>>()

        val updatedList = currentList.map { element ->
            if (element.isSelected && element.type == ElementType.TEXT && element.fontId != fontEntity.id.toString()) {
                affectedElementsData.add(element.id to element.fontId)
                element.copy(context = context).apply {
                    fontId = fontEntity.id.toString()
                    fontUrl = fontEntity.file_url
                    isPremium = fontEntity.is_premium
                    paint.typeface = try {
                        Typeface.createFromFile(fontEntity.file_path)
                    } catch (e: Exception) {
                        println("Error applying font: ${fontEntity.file_path}. Error: ${e.message}")
                        fontId = null
                        context?.let { ResourcesCompat.getFont(it, R.font.default_canvas) }
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

    // Called from the Angle seekbar (0–360°). Converts to dx/dy and applies.
    fun setShadowAngle(angleDeg: Float) {
        _shadowAngle.value = angleDeg
        val distance = _shadowDistance.value ?: 21f
        val (dx, dy) = angleDistanceToDxDy(angleDeg, distance)
        _shadowDx.value = dx
        _shadowDy.value = dy
        // Apply to whichever element type is selected
        val element = _selectedElements.value?.firstOrNull()
        if (element != null) {
            when (element.type) {
                ElementType.TEXT -> applyChangesToSelectedTextElements()
                else -> setImageShadow(
                    true, element.shadowColor, dx, dy,
                    element.shadowRadius, element.shadowOpacity, pushToUndo = false
                )
            }
        }
    }

    // Called from the Distance seekbar (0–100px). Converts to dx/dy and applies.
    fun setShadowDistance(distance: Float) {
        _shadowDistance.value = distance
        val angle = _shadowAngle.value ?: 135f
        val (dx, dy) = angleDistanceToDxDy(angle, distance)
        _shadowDx.value = dx
        _shadowDy.value = dy
        val element = _selectedElements.value?.firstOrNull()
        if (element != null) {
            when (element.type) {
                ElementType.TEXT -> applyChangesToSelectedTextElements()
                else -> setImageShadow(
                    true, element.shadowColor, dx, dy,
                    element.shadowRadius, element.shadowOpacity, pushToUndo = false
                )
            }
        }
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
        clearStrokeGradients()
        _borderColor.value = color
        _borderWidth.value = width
        _hasBorder.value = enabled
        applyChangesToSelectedTextElements()
    }

    private fun isFontFileValid(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() < 4) return false
            // Check for valid font magic bytes
            // TTF/OTF starts with 0x00010000 or 'OTTO' or 'true' or 'typ1'
            val bytes = ByteArray(4)
            file.inputStream().use { it.read(bytes) }
            val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
            magic == 0x00010000 ||           // TrueType
                    magic == 0x4F54544F ||           // 'OTTO' OpenType/CFF
                    magic == 0x74727565 ||           // 'true' Mac TrueType
                    magic == 0x74797031              // 'typ1'
        } catch (e: Exception) {
            false
        }
    }

    private fun CanvasElement.applyTypefaceFromFontList(): Typeface {
        return fontId?.let { id ->
            localFonts.value.firstOrNull { it.id.toString() == id }?.file_path
                ?.takeIf { it.isNotBlank() && File(it).exists() && isFontFileValid(it) }
                ?.let { path ->
                    try {
                        Typeface.createFromFile(path)
                    } catch (e: Exception) {
                        null
                    }
                }
        } ?: context?.let { ResourcesCompat.getFont(it, R.font.default_canvas) } ?: Typeface.DEFAULT
    }

    fun setTextColor(color: Int) {
        clearFillGradients()
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
            startAutoBatchIfNeeded(targetElementId!!)
            _opacity.value = opacity
            _canvasElements.value = updatedList
            if (currentBatchAction == null) {
                _canvasActions.push(
                    CanvasAction.SetOpacity(
                        opacity, oldOpacity ?: 255, targetElementId!!
                    )
                )
                _redoStack.clear()
                notifyUndoRedoChanged()
            }
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
                // Auto-constrain boxWidth when typed text overflows 85% canvas width
                val canvasW = _canvasSize.value?.width ?: 0f
                val maxW = if (canvasW > 0f) canvasW * 0.85f else 0f
                if (boxWidth == null && maxW > 0f) {
                    val rawWidth = paint.measureText(getTextWithKashida())
                    if (rawWidth > maxW) {
                        boxWidth = maxW
                    }
                }
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
        val targetElement = currentList.find { it.id == elementId } ?: return

        val oldFilter = targetElement.imageFilter
        if (oldFilter != newFilter) {
            val context = targetElement.context
            val updatedElement =
                targetElement.copy(imageFilter = newFilter!!, context = context).also {
                    // ✅ Stale cached bitmap must be discarded when the filter changes
                    it.isAdjustmentDirty = true
                    it.cachedAdjustedBitmap?.recycle()
                    it.cachedAdjustedBitmap = null
                }

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
        val oldCopies = selected.map { it.copy(context = null) }

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
                    newElement = newElem.copy(context = null),
                    oldElement = oldCopies[idx]
                )
            )
        }
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    // Locks or unlocks the GROUP sentinel and all its children together.
    // Called from GroupHeaderViewHolder lock button and the popup menu.
    fun toggleGroupLock(groupId: String) {
        val currentList = _canvasElements.value ?: return
        val sentinel = currentList.firstOrNull { it.id == groupId && it.type == ElementType.GROUP } ?: return
        val newLockState = !sentinel.isLocked

        val oldList = currentList.map { it.copy(context = null) }

        val updated = currentList.map { element ->
            if (element.id == groupId || element.groupId == groupId) {
                element.copy(isLocked = newLockState).apply {
                    if (type == ElementType.TEXT) paint.typeface = applyTypefaceFromFontList()
                }
            } else element
        }

        _canvasElements.value = updated
        refreshSelectedElements()

        _canvasActions.push(CanvasAction.UpdateCanvasElementsOrder(oldList, updated.map { it.copy(context = null) }))
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
            .map { it.copy(context = null) }

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
                    newElement = newElem.copy(context = null),
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
        val oldCopy = oldElem.copy(context = null)

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
                newElement = updatedElem.copy(context = null),
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

        // Also collect children of any selected GROUP sentinels
        val selectedGroupIds = toRemove.filter { it.type == ElementType.GROUP }.map { it.id }.toSet()
        val childrenOfGroups = if (selectedGroupIds.isNotEmpty())
            currentList.filter { it.groupId != null && it.groupId in selectedGroupIds }
        else emptyList()
        val allToRemove = (toRemove + childrenOfGroups).distinctBy { it.id }

        allToRemove.forEach { elem ->
            val element = elem.copy(context = null)
            element.paint.typeface = element.applyTypefaceFromFontList()
            if (element.bitmapData == null && element.svgData == null) {
                if (element.bitmap != null && !element.bitmap!!.isRecycled) {
                    element.bitmapData = ImageProcessor.bitmapToBase64Lossless(element.bitmap!!)
                } else if (element.svgDrawable != null) {
                    try {
                        val w = (element.logicalContentWidth.takeIf { it > 0 } ?: 512f).toInt()
                        val h = (element.logicalContentHeight.takeIf { it > 0 } ?: 512f).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bmp)
                        element.svgDrawable!!.setBounds(0, 0, w, h)
                        element.svgDrawable!!.draw(c)
                        element.bitmapData = ImageProcessor.bitmapToBase64Lossless(bmp)
                        bmp.recycle()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _canvasActions.push(CanvasAction.RemoveElement(element))
        }
        _redoStack.clear()

        val removeIds = allToRemove.map { it.id }.toSet()
        _canvasElements.value = currentList.filter { it.id !in removeIds }
        refreshSelectedElements()
        selectedElement = null
        notifyUndoRedoChanged()
    }

    fun removeElement(element: CanvasElement) {
        val currentList = _canvasElements.value ?: emptyList()
        if (currentList.any { it.id == element.id }) {
            val newElement = element.copy(context = null)
            newElement.paint.typeface = newElement.applyTypefaceFromFontList()
            if (newElement.bitmapData == null && newElement.svgData == null) {
                if (newElement.bitmap != null && !newElement.bitmap!!.isRecycled) {
                    newElement.bitmapData = ImageProcessor.bitmapToBase64Lossless(newElement.bitmap!!)
                } else if (newElement.svgDrawable != null) {
                    try {
                        val w = (newElement.logicalContentWidth.takeIf { it > 0 } ?: 512f).toInt()
                        val h = (newElement.logicalContentHeight.takeIf { it > 0 } ?: 512f).toInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = android.graphics.Canvas(bmp)
                        newElement.svgDrawable!!.setBounds(0, 0, w, h)
                        newElement.svgDrawable!!.draw(c)
                        newElement.bitmapData = ImageProcessor.bitmapToBase64Lossless(bmp)
                        bmp.recycle()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _canvasActions.push(CanvasAction.RemoveElement(newElement))
            _redoStack.clear()
            // If deleting a GROUP sentinel, also delete all its children.
            // 'Delete group' means delete everything in it, not ungroup.
            val idsToRemove = if (element.type == ElementType.GROUP) {
                setOf(element.id) + currentList.filter { it.groupId == element.id }.map { it.id }.toSet()
            } else {
                setOf(element.id)
            }
            _canvasElements.value = currentList.filter { it.id !in idsToRemove }
            if (element.type == ElementType.BACKGROUND) {
                _backgroundImage.value = null
            }
            refreshSelectedElements()
            selectedElement = null
            notifyUndoRedoChanged()
        }
    }

    /**
     * Called by CanvasView after each stroke is committed to the active draw session.
     * Records the stroke so it can be individually undone/redone during the session.
     */
    fun notifyDrawStrokeAdded(stroke: StrokeData) {
        // Deep-copy the path so the undo record is independent of future mutations
        val snapshot = stroke.copy(path = stroke.path?.let { Path(it) })
        _canvasActions.push(CanvasAction.DrawSessionStroke(snapshot))
        _redoStack.clear()
        notifyUndoRedoChanged()
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

                ElementType.IMAGE, ElementType.STICKER, ElementType.SHAPE, ElementType.BACKGROUND, ElementType.DRAW -> {
                    if (svgDrawable == null && (bitmap == null || bitmap?.isRecycled == true)) {
                        if (!svgData.isNullOrBlank()) {
                            try {
                                val svg = com.caverock.androidsvg.SVG.getFromString(svgData)
                                val vb = svg.documentViewBox
                                var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
                                var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
                                if (w <= 0f || h <= 0f) {
                                    w = 512f
                                    h = 512f
                                }
                                svg.documentWidth = w
                                svg.documentHeight = h

                                svgDrawable = PictureDrawable(svg.renderToPicture()).trimTransparentEdges()
                                bitmap = null
                            } catch (e: Exception) {
                                bitmapData?.let { data ->
                                    if (data.isNotBlank()) bitmap = ImageProcessor.base64ToBitmap(data)
                                }
                            }
                        } else if (type == ElementType.DRAW && !drawStrokes.isNullOrEmpty()) {
                            drawStrokes?.forEach { stroke -> stroke.restorePath() }
                        } else {
                            bitmapData?.let { data ->
                                if (data.isNotBlank()) bitmap = ImageProcessor.base64ToBitmap(data)
                            }
                        }
                    }
                }

                else -> { /* no extra work */
                }
            }
        }
        return restored
    }

    /**
     * Background-thread-safe version of restoreWithContext.
     * Called during loadTemplateFromJsonFile where ALL bitmap decoding must happen
     * on Dispatchers.Default — never on the main thread — to avoid ANR.
     *
     * Identical logic to restoreWithContext; kept separate so the intent is explicit
     * and safe to call from any coroutine context.
     */
    private fun CanvasElement.restoreWithContextBackground(context: Context?): CanvasElement {
        val restored = this.copy(context = context).apply {
            updatePaintProperties()
            when (type) {
                ElementType.TEXT -> {
                    paint.typeface = applyTypefaceFromFontList(context)
                }

                ElementType.IMAGE, ElementType.STICKER, ElementType.SHAPE, ElementType.BACKGROUND, ElementType.DRAW -> {
                    if (svgDrawable == null && (bitmap == null || bitmap?.isRecycled == true)) {
                        if (!svgData.isNullOrBlank()) {
                            try {
                                val svg = com.caverock.androidsvg.SVG.getFromString(svgData)
                                val vb = svg.documentViewBox
                                var w = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.width() else svg.documentWidth
                                var h = if (vb != null && vb.width() > 0f && vb.height() > 0f) vb.height() else svg.documentHeight
                                if (w <= 0f || h <= 0f) {
                                    w = 512f
                                    h = 512f
                                }
                                svg.documentWidth = w
                                svg.documentHeight = h

                                svgDrawable = PictureDrawable(svg.renderToPicture()).trimTransparentEdges()
                                bitmap = null
                            } catch (e: Exception) {
                                bitmapData?.let { data ->
                                    if (data.isNotBlank()) bitmap = ImageProcessor.base64ToBitmap(data)
                                }
                            }
                        } else if (type == ElementType.DRAW && !drawStrokes.isNullOrEmpty()) {
                            drawStrokes?.forEach { stroke -> stroke.restorePath() }
                        } else {
                            bitmapData?.let { data ->
                                if (data.isNotBlank()) bitmap = ImageProcessor.base64ToBitmap(data)
                            }
                        }
                    }
                }

                else -> { /* no extra work */ }
            }
        }
        return restored
    }

    // Adjust applyTypefaceFromFontList to accept context param:
    private fun CanvasElement.applyTypefaceFromFontList(context: Context?): Typeface {
        return fontId?.let { id ->
            localFonts.value.firstOrNull { it.id.toString() == id }?.file_path?.takeIf { it.isNotBlank() }
                ?.let { Typeface.createFromFile(it) }
        } ?: context?.let { ResourcesCompat.getFont(it, R.font.default_canvas) } ?: Typeface.DEFAULT
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

    fun findElementById(id: String): CanvasElement? {
        return _canvasElements.value?.firstOrNull { it.id == id }
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

            is CanvasAction.SetOverlayGradient -> {
                val targetGradient = if (isRedo) action.newGradient else action.oldGradient
                val currentList = _canvasElements.value.orEmpty()
                _canvasElements.value = currentList.map { element ->
                    if (element.id == action.elementId) {
                        element.overlayGradient = targetGradient
                        if (targetGradient != null) {
                            element.overlayColor = Color.TRANSPARENT
                            if (element.overlayOpacity == 0) element.overlayOpacity = 255
                            element.hasOverlay = true
                        } else {
                            element.hasOverlay =
                                element.overlayOpacity > 0 &&
                                        element.overlayColor != Color.TRANSPARENT
                        }
                        element
                    } else element
                }
            }

            is CanvasAction.SetImageShadow -> {
                val el = findElementById(action.elementId)
                if (isRedo) {
                    el?.hasShadow = action.newEnabled
                    el?.shadowColor = action.newColor
                    el?.shadowDx = action.newDx
                    el?.shadowDy = action.newDy
                    el?.shadowRadius = action.newRadius
                    el?.shadowOpacity = action.newOpacity
                } else {
                    el?.hasShadow = action.oldEnabled
                    el?.shadowColor = action.oldColor
                    el?.shadowDx = action.oldDx
                    el?.shadowDy = action.oldDy
                    el?.shadowRadius = action.oldRadius
                    el?.shadowOpacity = action.oldOpacity
                }
            }

            is CanvasAction.SetOverlay -> {
                val el = findElementById(action.elementId)
                if (isRedo) {
                    el?.hasOverlay = action.newHasOverlay
                    el?.overlayColor = action.newColor
                    el?.overlayOpacity = action.newOpacity
                } else {
                    el?.hasOverlay = action.oldHasOverlay
                    el?.overlayColor = action.oldColor
                    el?.overlayOpacity = action.oldOpacity
                }
            }

            is CanvasAction.AddDrawStroke -> {
                val currentList = _canvasElements.value.orEmpty()
                if (isRedo) {
                    // Reapply context & paint/typeface/bitmap, then add
                    val restored = action.element.restoreWithContext(context)
                    _canvasElements.value = currentList + restored
                } else {
                    // Undo: remove by ID
                    _canvasElements.value = currentList.filter { it.id != action.element.id }
                }
            }

            is CanvasAction.AddShape -> {
                val currentList = _canvasElements.value.orEmpty()
                if (isRedo) {
                    // Reapply context & paint/typeface/bitmap, then add
                    val restored = action.element.restoreWithContext(context)
                    _canvasElements.value = currentList + restored
                } else {
                    // Undo: remove by ID
                    _canvasElements.value = currentList.filter { it.id != action.element.id }
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
                                            it, R.font.default_canvas
                                        )
                                    } ?: Typeface.DEFAULT
                                    copied.fontId = null
                                }
                            } else {
                                copied.paint.typeface = context?.let {
                                    ResourcesCompat.getFont(
                                        it, R.font.default_canvas
                                    )
                                } ?: Typeface.DEFAULT
                                copied.fontId = null
                            }
                        } else {
                            copied.paint.typeface =
                                context?.let { ResourcesCompat.getFont(it, R.font.default_canvas) }
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
                val sizeToApply = if (isRedo) action.newSize else action.oldSize
                _canvasSize.value = sizeToApply
                syncBackgroundElementSize(sizeToApply)
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
                    _currentImageFilter.value = if (isRedo) action.newFilter else action.oldFilter
                }
            }

            is CanvasAction.DrawSessionStroke -> {
                val session = _activeDrawSession ?: return
                if (isRedo) {
                    // Re-append the stroke (deep copy so redo record stays clean)
                    val restored = action.strokeData.copy(
                        path = action.strokeData.path?.let { Path(it) }
                    )
                    session.drawStrokes?.add(restored)
                } else {
                    // Undo: remove the last stroke from the session
                    session.drawStrokes?.removeLastOrNull()
                }
                // Trigger a canvas redraw so the live preview updates immediately
                _canvasView.value?.invalidate()
            }

            is CanvasAction.TransformCanvas -> {
                val zoom = if (isRedo) action.newZoom else action.oldZoom
                val panX = if (isRedo) action.newPanX else action.oldPanX
                val panY = if (isRedo) action.newPanY else action.oldPanY

                _canvasView.value?.setZoomAndPan(zoom, panX, panY)
                _zoomLevel.value = zoom
            }
        }

        notifyUndoRedoChanged()
        // Force LiveData re-emit if needed
        _canvasElements.value = _canvasElements.value
    }

    fun recordCanvasTransform(
        oldZoom: Float, oldPanX: Float, oldPanY: Float,
        newZoom: Float, newPanX: Float, newPanY: Float
    ) {
        if (kotlin.math.abs(oldZoom - newZoom) < 0.01f && kotlin.math.abs(oldPanX - newPanX) < 2f && kotlin.math.abs(oldPanY - newPanY) < 2f) return
        val action = CanvasAction.TransformCanvas(oldZoom, oldPanX, oldPanY, newZoom, newPanX, newPanY)
        _canvasActions.push(action)
        _redoStack.clear()
        notifyUndoRedoChanged()
    }

    fun populateAdjustmentsFromElement(elementId: String) {
        val element = canvasElements.value?.find { it.id == elementId }
        if (element == null || element.type == ElementType.TEXT) return

        val adj = element.adjustments

        // Update LiveData values from element.adjustments
        _brightness.value = adj.brightness
        _contrast.value = adj.contrast
        _saturation.value = adj.saturation
        _vibrance.value = adj.vibrance
        _temperature.value = adj.temperature
        _tint.value = adj.tint
        _shadows.value = adj.shadows
        _highlights.value = adj.highlights
        _clarity.value = adj.clarity
        _fade.value = adj.fade
        _sharpness.value = adj.sharpness
        _featherRadius.value = element.featherRadius
        _featherWidth.value = element.featherWidth
    }

    fun clearCanvas() {
        _canvasElements.value = emptyList()
        _selectedElements.value = emptyList()
        _canvasActions.clear()
        _redoStack.clear()

        _canvasSize.value = null
        _canvasView.value = null
        _backgroundColor.value = Color.WHITE
        _backgroundImage.value = null
        _backgroundGradient.value = null

        resetTextFormattingToDefault()
        clearFillGradients()
        clearStrokeGradients()
        clearLabelGradients()
        clearGradient()
        resetAdjustments()

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

        projectSourceName = null
    }

    fun resetCanvasState() {
        _canvasSize.value = null
        _canvasElements.value = emptyList()
        _exportResult.value = null
        _backgroundImage.value = null
        _backgroundColor.value = Color.WHITE
        _backgroundGradient.value = null
        _canvasActions.clear()
        _redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _isExplicitChange = false
        _canvasView.value = null
    }

    fun loadTemplateFromJsonFile(
        exportResult: ExportResult,
        context: Context,
        titleHint: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        resetCanvasState()
        val defaultTitle = if (exportResult.isExported) "Loading Project" else "Loading Template"
        val initialTitle = titleHint ?: defaultTitle
        _isLoadingTemplate.value = true
        _loadingStage.value = initialTitle to 10
        viewModelScope.launch(Dispatchers.Default) {

            try {
                val jsonFilePath = exportResult.jsonPath
                val sourceFile = File(jsonFilePath)

                if (!sourceFile.exists()) {
                    Log.e("CanvasViewModel", "Template file not found: $jsonFilePath")
                    return@launch
                }

                _loadingStage.postValue("Parsing JSON" to 30)

                // Decode .urdc -> plain JSON temp file, OR pass an old plain-JSON file through
                // unchanged (auto-detected by magic bytes). Streams to disk so we never build a
                // giant String — preserves the OOM protection noted below.
                val tempJson = File(context.cacheDir, "open_${System.currentTimeMillis()}.json")
                val jsonFile = try {
                    com.webscare.urducanvas.common.canvas.io.ProjectCodec
                        .toPlainJsonFile(sourceFile, tempJson)
                } catch (e: com.webscare.urducanvas.common.canvas.io.ProjectCodec.BadProjectFileException) {
                    Log.e("CanvasViewModel", "Bad/foreign project file: ${e.message}")
                    tempJson.delete()
                    return@launch
                }

                // Stream the JSON file directly into Gson — never load it as a String.
                // readText() on a large project file (many images stored as base64) can
                // allocate 50–250 MB as a single String, causing the ANR/OOM on load.
                val elements: List<CanvasElement> = jsonFile.bufferedReader().use { reader ->
                    gson.fromJson(reader, Array<CanvasElement>::class.java).toList()
                }
                // Clean up the temp file if we created one (jsonFile == sourceFile for plain JSON).
                if (jsonFile.absolutePath == tempJson.absolutePath) tempJson.delete()

                val requiredFontIds =
                    elements.filter { it.type == ElementType.TEXT }.mapNotNull { it.fontId }
                        .distinct()

                _loadingStage.postValue("Preparing fonts" to 40)
                fontGate.ensureFonts(requiredFontIds) { stageMsg, pct ->
                    _loadingStage.postValue(stageMsg to pct)
                }

                _loadingStage.postValue("Fonts ready" to 55)

                // ── Hydrate elements — all heavy work stays on Dispatchers.Default ──────
                // base64ToBitmap is PNG decode and can take hundreds of ms per image.
                // Doing it here (background thread) instead of on the main thread is what
                // prevents the ANR.
                _loadingStage.postValue("Hydrating elements" to 60)

                val hydratedElements = elements.mapIndexed { index, raw ->
                    val fixed = if (raw.adjustments == null) raw.copy(adjustments = AdjustmentValues()) else raw
                    val element = fixed.copy(context = context).apply {
                        if (type == ElementType.DRAW && !drawStrokes.isNullOrEmpty()) {
                            drawStrokes?.forEach { stroke -> stroke.restorePath() }
                        }
                    }
                    // Decode bitmap on background thread — this is the expensive step
                    element.restoreWithContextBackground(context)
                }

                _loadingStage.postValue("Applying fonts" to 70)

                val currentFonts = _localFonts.value
                val hydratedWithFonts = hydratedElements.map { element ->
                    if (element.type == ElementType.TEXT && element.fontId != null) {
                        val font = currentFonts.find { it.id.toString() == element.fontId }
                        if (font?.file_path?.isNotBlank() == true) {
                            try {
                                element.paint.typeface = Typeface.createFromFile(font.file_path)
                            } catch (e: Exception) {
                                element.paint.typeface =
                                    ResourcesCompat.getFont(context, R.font.default_canvas)
                                        ?: Typeface.DEFAULT
                            }
                        }
                    }
                    element
                }

                // ── Decode background bitmap on background thread ──────────────────────
                _loadingStage.postValue("Loading background" to 80)

                val bgElement = if (elements.isNotEmpty() && elements[0].type == ElementType.BACKGROUND) {
                    elements[0]
                } else null

                val bgBitmap = bgElement?.bitmapData?.let { data ->
                    ImageProcessor.base64ToBitmap(data) // ← background thread, not main
                }

                // ── Only switch to Main for LiveData writes — zero heavy work here ────
                _loadingStage.postValue("Applying to canvas" to 90)
                withContext(Dispatchers.Main) {

                    if (bgElement != null) {
                        _backgroundColor.value = bgElement.backgroundColor
                        _backgroundGradient.value = bgElement.fillGradient

                        if (bgBitmap != null) {
                            _backgroundImage.value = bgBitmap
                        } else if (bgElement.bitmapData != null) {
                            Log.e("CanvasViewModel", "Bitmap decoding failed for background")
                        }

                        if (_backgroundGradient.value == null && _backgroundImage.value == null) {
                            _backgroundColor.value = bgElement.backgroundColor ?: Color.WHITE
                        }
                    }

                    _canvasSize.value = exportResult.canvasSize
                    val subscribed = billingManager.isSubscribed.value

                    _canvasElements.value = hydratedWithFonts.map { element ->
                        element.copy(isSubscribed = subscribed && element.isPremium)
                            .also { copied ->
                                if (copied.type == ElementType.TEXT) {
                                    copied.paint.typeface = copied.applyTypefaceFromFontList()
                                }
                            }
                    }

                    // Bitmaps are already decoded — just wire up adjustment LiveData for
                    // the selected element. No more base64ToBitmap calls on the main thread.
                    val selected = hydratedWithFonts.find { it.isSelected && it.type != ElementType.TEXT }
                    selected?.let {
                        _currentFont.postValue(_localFonts.value.find { font -> font.id.toString() == it.fontId })
                        _brightness.postValue(it.adjustments.brightness)
                        _contrast.postValue(it.adjustments.contrast)
                        _saturation.postValue(it.adjustments.saturation)
                        _shadows.postValue(it.adjustments.shadows)
                        _temperature.postValue(it.adjustments.temperature)
                        _tint.postValue(it.adjustments.tint)
                        _vibrance.postValue(it.adjustments.vibrance)
                        _sharpness.postValue(it.adjustments.sharpness)
                        _clarity.postValue(it.adjustments.clarity)
                        _fade.postValue(it.adjustments.fade)
                    }

                    val isTemplateOpening = exportResult.id == 0L && (exportResult.sourceTemplateId != null || jsonFilePath.contains("downloaded_templates", ignoreCase = true) || jsonFilePath.contains("template_", ignoreCase = true))

                    val activeExportResult = if (isTemplateOpening) {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                        val newJsonFileName = "project_$timestamp.json"
                        val newImageFileName = "project_img_$timestamp.png"
                        val newJsonPath = File(context.filesDir, newJsonFileName).absolutePath
                        val newImagePath = File(context.filesDir, newImageFileName).absolutePath

                        exportResult.copy(
                            id = 0L,
                            jsonPath = newJsonPath,
                            imagePath = newImagePath,
                            fileName = if (exportResult.fileName.isNotBlank() && !exportResult.fileName.startsWith("template_", ignoreCase = true)) exportResult.fileName else "Project_$timestamp"
                        )
                    } else {
                        exportResult
                    }

                    _exportResult.value = activeExportResult
                }
                Log.e("CanvasViewModel", "Successful")

            } catch (e: Exception) {
                Log.e("CanvasViewModel", "Error loading template: ${e.message}")
                Log.e("CanvasViewModel", "Error: ${e.cause}")
            } finally {
                withContext(Dispatchers.Main) {
                    _loadingStage.value = "Done" to 100
                    _isLoadingTemplate.value = false
                    val loaded = _canvasSize.value != null
                    onComplete?.invoke(loaded)
                    _isLoadingTemplate.value = null
                }
            }
        }
    }

    fun clearLoading() {
        _isLoadingTemplate.value = null
        _zoomLevel.value = 1.0f
    }

    fun isExplicitChange(): Boolean {
        return _isExplicitChange
    }

    private val _openAppearanceTab = MutableLiveData<Boolean>()
    val openAppearanceTab: LiveData<Boolean> = _openAppearanceTab

    fun openAppearanceTab() {
        _openAppearanceTab.value = true
    }

    fun closeAppearanceTab() {
        _openAppearanceTab.value = false
    }

    private var projectSourceName: String? = null

    /**
     * Call this before [loadTemplateFromJsonFile] with the template's category/subcategory.
     * The raw string is sanitized here: lowercased, spaces → underscores, non-alphanumeric stripped.
     * e.g. "Eid Mubarak" → "eid_mubarak"
     */
    fun setProjectSourceName(rawName: String?) {
        projectSourceName = rawName?.trim()?.lowercase()?.replace(Regex("\\s+"), "_")
            ?.replace(Regex("[^a-z0-9_]"), "")?.trimEnd('_')?.ifBlank { null }
    }

    /**
     * Returns a filename base for the current project, e.g.:
     *  - "eid_mubarak_1712345678901"  (template-sourced)
     *  - "project_1712345678901"      (blank / image canvas)
     * The caller should append the file extension.
     */
    fun buildProjectFileName(): String {
        val timestamp = System.currentTimeMillis()
        val prefix = projectSourceName ?: "project"
        return "${prefix}_${timestamp}"
    }


    val hasPremiumAsset: LiveData<Boolean> = canvasElements.map { list ->
        list?.any { !it.isSubscribed && it.isPremium } ?: false
    }

    fun getPremiumAssets(): List<PremiumAssetItem> {
        return canvasElements.value
            ?.filter { !it.isSubscribed && it.isPremium }
            ?.map { element ->
                PremiumAssetItem(
                    elementId = element.id,
                    type = element.type!!,
                    fontId = element.fontId,
                    bitmapData = element.bitmapData,
                    applyWhiteTintInDarkMode = element.applyWhiteTintInDarkMode
                )
            } ?: emptyList()
    }

    fun removeAllPremiumAssets() {
        val current = canvasElements.value?.toMutableList() ?: return
        current.removeAll { it.isPremium }
//        setCanvasElements(current)
    }
}