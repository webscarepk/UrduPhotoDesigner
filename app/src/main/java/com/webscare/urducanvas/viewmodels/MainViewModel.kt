package com.webscare.urducanvas.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webscare.urducanvas.common.canvas.enums.ErrorType
import com.webscare.urducanvas.common.canvas.enums.SectionStatus
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.HomeUiState
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.GradientPresets
import com.webscare.urducanvas.data.model.CanvasSizeEntity
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImageResponse
import com.webscare.urducanvas.data.model.ImagesData
import com.webscare.urducanvas.data.model.ObjectsData
import com.webscare.urducanvas.data.model.ShapesData
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.TemplatesResponse
import com.webscare.urducanvas.data.model.TrendResponse
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.domain.repo.DownloadRepo
import com.webscare.urducanvas.domain.usecase.DeleteFontsUseCase
import com.webscare.urducanvas.domain.usecase.DeleteGradientUseCase
import com.webscare.urducanvas.domain.usecase.DeleteImagesUseCase
import com.webscare.urducanvas.domain.usecase.ExportResultsUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPICanvasSizesUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPIFontsUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPIImagesUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPITemplatesUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPITrendsUseCase
import com.webscare.urducanvas.domain.usecase.GetAllGradientsUseCase
import com.webscare.urducanvas.domain.usecase.GetCanvasSizesUseCase
import com.webscare.urducanvas.domain.usecase.GetFontsUseCase
import com.webscare.urducanvas.domain.usecase.GetImagesUseCase
import com.webscare.urducanvas.domain.usecase.GetTemplatesUseCase
import com.webscare.urducanvas.domain.usecase.GetTrendsUseCase
import com.webscare.urducanvas.domain.usecase.InsertFontsUseCase
import com.webscare.urducanvas.domain.usecase.InsertGradientUseCase
import com.webscare.urducanvas.domain.usecase.InsertImagesUseCase
import com.webscare.urducanvas.domain.usecase.InsertTemplatesUseCase
import com.webscare.urducanvas.domain.usecase.InsertTrendsUseCase
import com.webscare.urducanvas.domain.usecase.SeedGradientsUseCase
import com.webscare.urducanvas.domain.usecase.UpdateFontStatusUseCase
import com.webscare.urducanvas.domain.usecase.UpdateFontsUseCase
import com.webscare.urducanvas.domain.usecase.UpdateGradientUseCase
import com.webscare.urducanvas.domain.usecase.UpdateImagesUseCase
import com.webscare.urducanvas.domain.usecase.UpdateTemplatesUseCase
import com.webscare.urducanvas.ui.editor.panels.objects.ObjectsFragment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val fetchAPITemplatesUseCase: FetchAPITemplatesUseCase,
    private val getTemplatesUseCase: GetTemplatesUseCase,
    private val insertTemplatesUseCase: InsertTemplatesUseCase,
    private val updateTemplatesUseCase: UpdateTemplatesUseCase,
    private val fetchAPIFontsUseCase: FetchAPIFontsUseCase,
    private val deleteImagesUseCase: DeleteImagesUseCase,
    private val updateImagesUseCase: UpdateImagesUseCase,
    private val deleteFontsUseCase: DeleteFontsUseCase,
    private val insertFontsUseCase: InsertFontsUseCase,
    private val getFontsUseCase: GetFontsUseCase,
    private val fetchAPIImagesUseCase: FetchAPIImagesUseCase,
    private val insertImagesUseCase: InsertImagesUseCase,
    private val getImagesUseCase: GetImagesUseCase,
    private val downloadRepository: DownloadRepo,
    private val updateFontsUseCase: UpdateFontsUseCase,
    private val updateFontStatusUseCase: UpdateFontStatusUseCase,
    private val getAll: GetAllGradientsUseCase,
    private val seed: SeedGradientsUseCase,
    private val delete: DeleteGradientUseCase,
    private val insert: InsertGradientUseCase,
    private val update: UpdateGradientUseCase,
    private val exportResultsUseCase: ExportResultsUseCase,
    private val fetchAPITrendsUseCase: FetchAPITrendsUseCase,
    private val getTrendsUseCase: GetTrendsUseCase,
    private val insertTrendsUseCase: InsertTrendsUseCase,
    private val billingManager: BillingManager,
    private val fetchAPICanvasSizesUseCase: FetchAPICanvasSizesUseCase,
    private val getCanvasSizesUseCase: GetCanvasSizesUseCase,
) : ViewModel() {

    private val _selectedImageIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedImageIds: StateFlow<Set<Int>> = _selectedImageIds.asStateFlow()

    private val _selectedEmojiChars = MutableStateFlow<Set<String>>(emptySet())
    val selectedEmojiChars: StateFlow<Set<String>> = _selectedEmojiChars.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()

    // Per-section status (so we can show inline retry per section)
    private val _templatesStatus = MutableStateFlow(SectionStatus.Loading)
    val templatesStatus: StateFlow<SectionStatus> = _templatesStatus.asStateFlow()

    private val _fontsStatus = MutableStateFlow(SectionStatus.Loading)
    val fontsStatus: StateFlow<SectionStatus> = _fontsStatus.asStateFlow()

    private val _trendsStatus = MutableStateFlow(SectionStatus.Loading)
    val trendsStatus: StateFlow<SectionStatus> = _trendsStatus.asStateFlow()

    // Unified home state
    private val _homeUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()
    private val _trendRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val trendRows: StateFlow<List<HomeRow>> = _trendRows.asStateFlow()

    private val fontJobs = mutableMapOf<String, Job>()
    private val templateJobs = mutableMapOf<String, Job>()

    private val _fontDownloadStates =
        MutableStateFlow<Map<String, FontDownloadState>>(emptyMap())

    val fontDownloadStates: StateFlow<Map<String, FontDownloadState>> =
        _fontDownloadStates

    private val _templateDownloadStates =
        MutableStateFlow<Map<String, TemplateDownloadState>>(emptyMap())

    val templateDownloadStates: StateFlow<Map<String, TemplateDownloadState>> =
        _templateDownloadStates

    private val _localFonts =
        MutableStateFlow<List<FontEntity>>(emptyList())
    val localFonts: StateFlow<List<FontEntity>> =
        _localFonts.asStateFlow()

    private val _localImages =
        MutableStateFlow<List<ImageEntity>>(emptyList())
    val localImages: StateFlow<List<ImageEntity>> =
        _localImages.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Pre-computed Objects fragment data.
    //
    // Filtering 2000 items × 20 fragments = 40k+ allocations per emit. Doing
    // it ONCE here on Dispatchers.Default — every fragment then reads its
    // category's slice in O(1) from imagesByCategory.
    //
    // SharingStarted.Eagerly = build the map as soon as MainViewModel is
    // created so by the time the user navigates to Objects, .value is already
    // populated. initialValue gives the UI emoji tabs IMMEDIATELY, even
    // before the DB has loaded.
    // ─────────────────────────────────────────────────────────────────────────
    val objectsData: StateFlow<ObjectsData> = localImages
        .map { images ->
            withContext(Dispatchers.Default) { buildObjectsData(images) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ObjectsData.Initial
        )

    private fun buildObjectsData(images: List<ImageEntity>): ObjectsData {
        Log.d("IMAGES_DEBUG", "buildObjectsData called: ${images.size}")
        val recents = ArrayList<ImageEntity>()
        val byCategory = HashMap<String, MutableList<ImageEntity>>()

        for (img in images) {
            if (!img.parent_category.equals("Vectors", ignoreCase = true)) continue

            byCategory.getOrPut(img.category) { ArrayList() }.add(img)
            if (img.is_recent) recents.add(img)
        }

        val baseLower = ObjectsFragment.BASE_TABS.map { it.lowercase() }.toHashSet()
        val extraTabs = byCategory.keys
            .map { it.trim() }
            .filter { it.lowercase() !in baseLower }
            .distinct()
            .sorted()

        val tabs = buildList {
            if (recents.isNotEmpty()) add("Recents")
            addAll(extraTabs)                  // Vectors subcategories tabs
            addAll(ObjectsFragment.BASE_TABS)  // Emoji tabs at end
        }

        return ObjectsData(tabs, byCategory, recents)
    }

    // ─── ImagesData — same Eagerly pattern as objectsData ───────────────────────
    val imagesData: StateFlow<ImagesData> = localImages
        .map { images ->
            withContext(Dispatchers.Default) { buildImagesData(images) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ImagesData.Initial
        )

    private fun buildImagesData(images: List<ImageEntity>): ImagesData {
        val recents = ArrayList<ImageEntity>()
        val byCategory = HashMap<String, MutableList<ImageEntity>>()

        for (img in images) {
            val parent = img.parent_category ?: continue
            if (!parent.equals("Images", ignoreCase = true) &&
                !parent.equals("Backgrounds", ignoreCase = true)
            ) continue

            val tabName = when {
                img.category.equals("Images Imported", ignoreCase = true)      -> "My Images"
                img.category.equals("Backgrounds Imported", ignoreCase = true) -> "My Backgrounds"
                else -> img.category.trim()
            }

            byCategory.getOrPut(tabName) { ArrayList() }.add(img)
            if (img.is_recent) recents.add(img)
        }

        val specialTabs = setOf("My Images", "My Backgrounds")

        val imageTabs = byCategory.keys
            .filter { it !in specialTabs }
            .filter { tab -> byCategory[tab]?.any { it.parent_category.equals("Images", ignoreCase = true) } == true }
            .sorted()

        val backgroundTabs = byCategory.keys
            .filter { it !in specialTabs }
            .filter { tab -> byCategory[tab]?.any { it.parent_category.equals("Backgrounds", ignoreCase = true) } == true }
            .sorted()

        val tabs = buildList {
            if (recents.isNotEmpty()) add("Recents")
            addAll(imageTabs)
            if (byCategory.containsKey("My Images")) add("My Images")
            addAll(backgroundTabs)
            if (byCategory.containsKey("My Backgrounds")) add("My Backgrounds")
        }

        return ImagesData(tabs, byCategory, recents)
    }

    // ─── ShapesData ───────────────────────────────────────────────────────────
    val shapesData: StateFlow<ShapesData> = localImages
        .map { images ->
            withContext(Dispatchers.Default) { buildShapesData(images) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ShapesData.initial()
        )

    private fun buildShapesData(images: List<ImageEntity>): ShapesData {
        val recents = ArrayList<ImageEntity>()
        val byCategory = HashMap<String, MutableList<ImageEntity>>()

        for (img in images) {
            if (!img.parent_category.equals("Shapes", ignoreCase = true)) continue
            byCategory.getOrPut(img.category.trim()) { ArrayList() }.add(img)
            if (img.is_recent) recents.add(img)
        }

        val apiTabs = byCategory.keys.sorted()

        val tabs = buildList {
            if (recents.isNotEmpty()) add("Recents")
            addAll(apiTabs)
        }

        return ShapesData(tabs, byCategory, recents)
    }
    private val _localTemplates =
        MutableStateFlow<List<TemplateEntity>>(emptyList())
    val localTemplates: StateFlow<List<TemplateEntity>> =
        _localTemplates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _gradients =
        MutableLiveData<List<GradientItem>>()
    val gradients: LiveData<List<GradientItem>> =
        _gradients

    private val _exportResults =
        MutableLiveData<List<ExportResult>>()
    val exportResults: LiveData<List<ExportResult>> get() = _exportResults

    private val _localCanvasSizes = MutableStateFlow<List<CanvasSizeEntity>>(emptyList())
    val localCanvasSizes: StateFlow<List<CanvasSizeEntity>> = _localCanvasSizes.asStateFlow()

    private val _rawQuery = MutableStateFlow("")
    val rawQuery: StateFlow<String> = _rawQuery.asStateFlow()

    // Persists which Objects tab the user last had open.
    // Stored here (in ViewModel) so it survives ObjectsFragment recreation.
    var lastObjectsTabCategory: String? = null
    var lastShapesTabCategory: String? = null
    var lastImagesTabCategory: String? = null

    // Debounced, distinct stream for UI filtering
    val queryDebounced = rawQuery.map { it.trim() }.distinctUntilChanged()

    fun setQuery(q: String) {
        _rawQuery.value = q
    }

    fun clearQuery() {
        _rawQuery.value = ""
    }


    init {
        viewModelScope.launch {
            billingManager.loadSavedSubscriptionStatus()
            billingManager.checkSubscriptionOnLaunch()
            fetchAndStoreFontsFromApi()
            fetchAndStoreImagesFromApi()
            fetchAndStoreTemplatesFromApi()
            fetchAndStoreTrendsFromApi()
            fetchAndStoreCanvasSizesFromApi()
        }
        observeLocalCanvasSizes()
        observeLocalFonts()
        observeLocalImages()
        getAllExportResults()
        observeLocalTemplates()
        observeLocalTrends()

        viewModelScope.launch {
            seed(GradientPresets.defaultList)
        }
        viewModelScope.launch {
            getAll().collect { uiList ->
                _gradients.value = uiList
            }
        }

        viewModelScope.launch {
            billingManager.isSubscribed.collect { subscribed ->
                _localFonts.value.forEach { font ->
                    if (subscribed && font.is_premium) {
                        updateFontsUseCase.invoke(font.copy(is_subscribed = true))
                    }else if (!subscribed && font.is_premium){
                        updateFontsUseCase.invoke(font.copy(is_subscribed = false))
                    }
                }
                _localImages.value.forEach { image ->
                    if (subscribed && image.is_premium) {
                        updateImagesUseCase.invoke(image.copy(is_subscribed = true))
                    }else if (!subscribed && image.is_premium){
                        updateImagesUseCase.invoke(image.copy(is_subscribed = false))
                    }
                }

                _localTemplates.value.forEach { template ->
                    if (subscribed && template.is_premium) {
                        updateTemplatesUseCase.updatePremiumStatus(
                            template.id, true
                        )
                    }else if (!subscribed && template.is_premium){
                        updateTemplatesUseCase.updatePremiumStatus(
                            template.id, false
                        )
                    }
                }
            }
        }
    }

    fun enableSubscription(){
        billingManager.debugSetSubscription(true, planId = 1)
    }

    fun disableSubscription(){
        billingManager.debugSetSubscription(false)
    }

    fun deleteGradient(id: Long) = viewModelScope.launch { delete(id) }
    fun updateGradient(g: GradientItem) =
        viewModelScope.launch { update(g) }

    fun insertGradient(g: GradientItem) =
        viewModelScope.launch { insert(g) }

    private fun recomputeHomeState() {
        val templates = _localTemplates.value
        val fonts = _localFonts.value
        val trends = _trendRows.value
        val recents = _exportResults.value.orEmpty()

        val hasAnyData = templates.isNotEmpty() || fonts.isNotEmpty() ||
                trends.isNotEmpty() || recents.isNotEmpty()

        val tStatus = _templatesStatus.value
        val fStatus = _fontsStatus.value
        val trStatus = _trendsStatus.value

        val anyLoading = tStatus == SectionStatus.Loading ||
                fStatus == SectionStatus.Loading ||
                trStatus == SectionStatus.Loading

        val allFailed = tStatus == SectionStatus.Failed &&
                fStatus == SectionStatus.Failed &&
                trStatus == SectionStatus.Failed

        val newState = when {
            hasAnyData -> HomeUiState.Content
            anyLoading -> HomeUiState.Loading
            allFailed -> HomeUiState.Error(ErrorType.NO_INTERNET, "Couldn't load content")
            else -> HomeUiState.Empty
        }

        Log.d("HomeState", "templates=$tStatus fonts=$fStatus trends=$trStatus | hasData=$hasAnyData | newState=$newState")

        _homeUiState.value = newState
    }

    fun insertTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            insertTemplatesUseCase.insertSingleTemplate(template)
        }
    }

    private fun observeLocalTemplates() {
        viewModelScope.launch {
            getTemplatesUseCase().collect { templates ->
                _localTemplates.value = templates
                recomputeHomeState()
            }
        }
    }

    private fun observeLocalFonts() {
        viewModelScope.launch {
            getFontsUseCase().collect { fonts ->
                _localFonts.value = fonts
                recomputeHomeState()
            }
        }
    }

    private fun observeLocalTrends() {
        viewModelScope.launch {
            getTrendsUseCase().map { trendsWithTemplates ->
                trendsWithTemplates.map { twt ->
                    HomeRow.TrendRow(
                        title = twt.trend.name, templates = twt.templates
                    )
                }
            }.collect { rows ->
                _trendRows.value = rows
                recomputeHomeState()
            }
        }
    }

    private fun getAllExportResults() {
        viewModelScope.launch {
            exportResultsUseCase.getAllExportResults().collect {
                _exportResults.value = it
                recomputeHomeState()
            }
        }
    }

    fun fetchAndStoreTemplatesFromApi() {
        viewModelScope.launch {
            fetchAPITemplatesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _templatesStatus.value = SectionStatus.Loading

                    is Response.Success -> {
                        _templatesStatus.value = SectionStatus.Loaded
                        val subscribed = billingManager.isSubscribed.value
                        val templatesToSave = response.data!!.templates.map { template ->
                            if (subscribed) template.copy(is_subscribed = true) else template
                        }
                        insertTemplatesUseCase.invoke(
                            TemplatesResponse(templates = templatesToSave)
                        )
                        recomputeHomeState()
                    }

                    is Response.Error -> {
                        _templatesStatus.value = SectionStatus.Failed
                        recomputeHomeState()
                    }

                    else -> {}
                }
            }
        }
    }

    fun fetchAndStoreFontsFromApi() {
        viewModelScope.launch {
            fetchAPIFontsUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _fontsStatus.value = SectionStatus.Loading

                    is Response.Success -> {
                        _fontsStatus.value = SectionStatus.Loaded
                        val subscribed = billingManager.isSubscribed.value

                        val fontsToSave = response.data!!.fonts.map { font ->
                            if (subscribed) {
                                font.copy(is_subscribed = true)
                            } else {
                                font
                            }
                        }
                        insertFontsUseCase.invoke(
                            FontsResponse(
                                message = response.data.message,
                                fonts = fontsToSave
                            )
                        )
                        recomputeHomeState()
                    }

                    is Response.Error -> {
                        _fontsStatus.value = SectionStatus.Failed
                        recomputeHomeState()
                    }

                    else -> {}
                }
            }
        }
    }

    fun fetchAndStoreTrendsFromApi() {
        viewModelScope.launch {
            fetchAPITrendsUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _trendsStatus.value = SectionStatus.Loading

                    is Response.Success -> {
                        _trendsStatus.value = SectionStatus.Loaded
                        val subscribed = billingManager.isSubscribed.value

                        val trendsToSave = response.data!!.trends.map { trend ->
                            trend.copy(
                                templates = trend.templates.map { template ->
                                    if (subscribed) template.copy(is_subscribed = true)
                                    else template
                                }
                            )
                        }

                        insertTrendsUseCase.invoke(
                            TrendResponse(trends = trendsToSave)
                        )
                        recomputeHomeState()
                    }

                    is Response.Error -> {
                        _trendsStatus.value = SectionStatus.Failed
                        recomputeHomeState()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun fetchAndStoreImagesFromApi() {
        viewModelScope.launch {
            fetchAPIImagesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> { /* not part of home UI */ }

                    is Response.Success -> {
                        val subscribed = billingManager.isSubscribed.value

                        val imagesToSave = response.data!!.image.map { image ->
                            if (subscribed) {
                                image.copy(is_subscribed = true)
                            } else {
                                image
                            }
                        }

                        insertImagesUseCase.invoke(
                            ImageResponse(
                                message = response.data.message,
                                image = imagesToSave
                            )
                        )
                    }

                    is Response.Error -> {
                        Log.w("MainViewModel", "Images fetch failed: ${response.message}")
                    }

                    else -> {}
                }
            }
        }
    }

    fun fetchAndStoreCanvasSizesFromApi() {
        viewModelScope.launch {
            fetchAPICanvasSizesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> { /* not part of home UI */ }

                    is Response.Success -> { /* Room observer picks it up */ }

                    is Response.Error -> {
                        // silently fail — Room already has data from last successful fetch
                        Log.w("MainViewModel", "Canvas sizes fetch failed: ${response.message}")
                    }

                    else -> {}
                }
            }
        }
    }

    fun insertImage(imageEntity: ImageEntity) {
        viewModelScope.launch {
            insertImagesUseCase.insertSingleImage(imageEntity)
        }
    }

    fun insertFont(fontEntity: FontEntity) {
        viewModelScope.launch {
            try {
                // Call InsertFontsUseCase to insert the font
                insertFontsUseCase.insertSingleFont(fontEntity)
                Log.d("MainViewModel", "Font inserted successfully: ${fontEntity.file_name}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error inserting font: ${e.message}")
            }
        }
    }

    private fun observeLocalImages() {
        viewModelScope.launch {
            getImagesUseCase().collect { images ->
                _localImages.value = images
            }
        }
    }

    private fun observeLocalCanvasSizes() {
        viewModelScope.launch {
            getCanvasSizesUseCase().collect { sizes ->
                _localCanvasSizes.value = sizes
            }
        }
    }

    fun downloadTemplate(template: TemplateEntity) {

        val id = template.id.toString()
        templateJobs[id]?.cancel()

        val job = viewModelScope.launch {

            // DB: start
            updateTemplatesUseCase.invoke(
                id, isDownloaded = false, isDownloading = true, progress = 0, filePath = null
            )

            updateTemplateState(
                id, TemplateDownloadState.Progress(0, template)
            )

            try {
                val file = downloadRepository.downloadTemplateById(
                    templateId = id,
                    fileName = "template_${id}.json",
                    totalSizeFromApi = template.json_size,
                    onProgress = { progress ->
                        updateTemplateState(
                            id, TemplateDownloadState.Progress(
                                progress, template.copy(
                                    is_downloading = true, download_progress = progress
                                )
                            )
                        )
                    })


                updateTemplatesUseCase.invoke(
                    id,
                    isDownloaded = true,
                    isDownloading = false,
                    progress = 100,
                    filePath = file.absolutePath
                )

                updateTemplateState(
                    id, TemplateDownloadState.SuccessWithTemplate(
                        file, template.copy(
                            is_downloaded = true,
                            is_downloading = false,
                            download_progress = 100,
                            file_path = file.absolutePath
                        )
                    )
                )

            } catch (e: Exception) {

                updateTemplatesUseCase.invoke(
                    id, isDownloaded = false, isDownloading = false, progress = 0, filePath = null
                )

                updateTemplateState(
                    id, TemplateDownloadState.Error(e.message ?: "Failed")
                )
            }
        }

        templateJobs[id] = job
    }

    fun clearTemplateDownloadState() {
        _templateDownloadStates.value = _templateDownloadStates.value.filterValues { state ->
            state is TemplateDownloadState.Progress
        }
    }

    fun downloadFont(font: FontEntity) {

        val fontId = font.id.toString()
        fontJobs[fontId]?.cancel()
        val job = viewModelScope.launch {
            updateFontStatusUseCase.invoke(fontId, true)
            updateFontState(
                fontId,
                FontDownloadState.Progress(0, font.copy(is_downloading = true))
            )

            try {
                val downloadedFile = downloadRepository.downloadAssets(
                    url = Constants.BASE_URL_GLIDE + font.file_url,
                    fileName = font.font_name + ".ttf",
                    onProgress = { progress ->

                        val currentState = _fontDownloadStates.value[fontId]

                        if (currentState is FontDownloadState.SuccessWithTypeface) {
                            return@downloadAssets
                        }

                        updateFontState(
                            fontId, FontDownloadState.Progress(
                                progress, font.copy(
                                    is_downloading = true, download_progress = progress
                                )
                            )
                        )
                    })

                updateFontsUseCase.invoke(
                    fontId,
                    isDownloaded = true,
                    isDownloading = false,
                    filePath = downloadedFile.absolutePath
                )

                val updatedFont = font.copy(
                    is_downloaded = true,
                    is_downloading = false,
                    download_progress = 100,
                    file_path = downloadedFile.absolutePath
                )

                Log.d("FONT_DEBUG", "Before emitting SUCCESS for $fontId")

                updateFontState(
                    fontId,
                    FontDownloadState.SuccessWithTypeface(downloadedFile, updatedFont)
                )
                Log.d("FONT_DEBUG", "After emitting SUCCESS for $fontId")

            } catch (e: Exception) {
                updateFontStatusUseCase.invoke(fontId, false)
                updateFontState(
                    fontId,
                    FontDownloadState.Error(e.message ?: "Download failed", font)
                )
            }
        }

        fontJobs[fontId] = job
    }

    private fun updateFontState(id: String, state: FontDownloadState) {
        val currentMap = _fontDownloadStates.value.toMutableMap()
        currentMap[id] = state
        _fontDownloadStates.value = currentMap
    }

    private fun updateTemplateState(id: String, state: TemplateDownloadState) {
        _templateDownloadStates.value = _templateDownloadStates.value.toMutableMap().apply {
            this[id] = state
        }
    }

    suspend fun insertExportResult(exportResult: ExportResult): Long {
        return exportResultsUseCase.insertExportResult(exportResult)
    }

    fun deleteExportResult(exportResult: ExportResult) {
        viewModelScope.launch {
            exportResultsUseCase.deleteExportResult(exportResult)
        }
    }

    fun deleteFont(font: FontEntity) {
        viewModelScope.launch {
            deleteFontsUseCase.invoke(font)
        }
    }

    fun deleteImage(image: ImageEntity) {
        viewModelScope.launch {
            deleteImagesUseCase.invoke(image)
        }
    }

    fun updateImage(image: ImageEntity) {
        viewModelScope.launch {
            updateImagesUseCase.invoke(image)
        }
    }

    fun updateFont(font: FontEntity) {
        viewModelScope.launch {
            updateFontsUseCase.invoke(font)
        }
    }

    fun clearFontDownloadState() {
        _fontDownloadStates.value = _fontDownloadStates.value.filterValues { state ->
            state is FontDownloadState.Progress
        }
    }

    fun retryHomeData() {
        _templatesStatus.value = SectionStatus.Loading
        _fontsStatus.value = SectionStatus.Loading
        _trendsStatus.value = SectionStatus.Loading
        recomputeHomeState()

        fetchAndStoreTemplatesFromApi()
        fetchAndStoreFontsFromApi()
        fetchAndStoreTrendsFromApi()
    }

    fun retryTemplates() { _templatesStatus.value = SectionStatus.Loading; fetchAndStoreTemplatesFromApi() }
    fun retryFonts() { _fontsStatus.value = SectionStatus.Loading; fetchAndStoreFontsFromApi() }
    fun retryTrends() { _trendsStatus.value = SectionStatus.Loading; fetchAndStoreTrendsFromApi() }

    val isInMultiSelectMode: StateFlow<Boolean> =
        combine(_selectedImageIds, _selectedEmojiChars) { imageIds, emojiChars ->
            imageIds.isNotEmpty() || emojiChars.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun clearAllSelection() {
        clearImageSelection()
        clearEmojiSelection()
    }

    fun toggleImageSelection(id: Int) {
        _selectedImageIds.value = _selectedImageIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    fun clearImageSelection() {
        _selectedImageIds.value = emptySet()
    }

    fun isImageSelected(id: Int): Boolean = id in _selectedImageIds.value

    fun toggleEmojiSelection(char: String) {
        _selectedEmojiChars.value = _selectedEmojiChars.value.toMutableSet().apply {
            if (contains(char)) remove(char) else add(char)
        }
    }

    fun clearEmojiSelection() {
        _selectedEmojiChars.value = emptySet()
    }

    fun isEmojiSelected(char: String): Boolean = char in _selectedEmojiChars.value

    fun togglePanelExpanded() {
        _isPanelExpanded.value = !_isPanelExpanded.value
    }

    fun collapsePanelIfExpanded() {
        if (_isPanelExpanded.value) {
            _isPanelExpanded.value = false
        }
    }
}