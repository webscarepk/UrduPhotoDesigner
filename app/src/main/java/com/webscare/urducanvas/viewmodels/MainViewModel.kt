package com.webscare.urducanvas.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webscare.urducanvas.common.canvas.model.GradientItem
import com.webscare.urducanvas.common.canvas.sealed.FontDownloadState
import com.webscare.urducanvas.common.canvas.sealed.HomeRow
import com.webscare.urducanvas.common.canvas.sealed.TemplateDownloadState
import com.webscare.urducanvas.common.sealed.Response
import com.webscare.urducanvas.common.utils.Constants
import com.webscare.urducanvas.common.utils.GradientPresets
import com.webscare.urducanvas.data.model.ExportResult
import com.webscare.urducanvas.data.model.FontEntity
import com.webscare.urducanvas.data.model.FontsResponse
import com.webscare.urducanvas.data.model.ImageEntity
import com.webscare.urducanvas.data.model.ImageResponse
import com.webscare.urducanvas.data.model.TemplateEntity
import com.webscare.urducanvas.data.model.TemplatesResponse
import com.webscare.urducanvas.data.model.TrendResponse
import com.webscare.urducanvas.di.BillingManager
import com.webscare.urducanvas.domain.repo.DownloadRepo
import com.webscare.urducanvas.domain.usecase.DeleteFontsUseCase
import com.webscare.urducanvas.domain.usecase.DeleteGradientUseCase
import com.webscare.urducanvas.domain.usecase.DeleteImagesUseCase
import com.webscare.urducanvas.domain.usecase.ExportResultsUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPIFontsUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPIImagesUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPITemplatesUseCase
import com.webscare.urducanvas.domain.usecase.FetchAPITrendsUseCase
import com.webscare.urducanvas.domain.usecase.GetAllGradientsUseCase
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    private val billingManager: BillingManager
) : ViewModel() {

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

    private val _rawQuery = MutableStateFlow("")
    val rawQuery: StateFlow<String> = _rawQuery.asStateFlow()

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
        }
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

    fun fetchAndStoreTemplatesFromApi() {
        viewModelScope.launch {
            fetchAPITemplatesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _isLoading.value =
                        true

                    is Response.Success -> {
                        _isLoading.value = false
                        val subscribed = billingManager.isSubscribed.value

                        val templatesToSave = response.data!!.templates.map { template ->
                            if (subscribed) template.copy(is_subscribed = true)
                            else template
                        }

                        insertTemplatesUseCase.invoke(
                            TemplatesResponse(templates = templatesToSave)
                        )
                    }

                    is Response.Error -> {
                        _isLoading.value = false
                        _error.value = response.message
                    }

                    else -> {}
                }
            }
        }
    }

    private fun observeLocalTemplates() {
        viewModelScope.launch {
            getTemplatesUseCase().collect { templates ->
                _localTemplates.value = templates
            }
        }
    }

    fun insertTemplate(template: TemplateEntity) {
        viewModelScope.launch {
            insertTemplatesUseCase.insertSingleTemplate(template)
        }
    }

    fun fetchAndStoreFontsFromApi() {
        viewModelScope.launch {
            fetchAPIFontsUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _isLoading.value =
                        true

                    is Response.Success -> {
                        _isLoading.value = false
                        val subscribed = billingManager.isSubscribed.value  // ← current status

                        val fontsToSave = response.data!!.fonts.map { font ->
                            if (subscribed) {
                                font.copy(is_subscribed = true)  // subscribed → sab unlock
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
                    }

                    is Response.Error -> {
                        _isLoading.value = false
                        _error.value = response.message
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
                    is Response.Loading -> _isLoading.value =
                        true

                    is Response.Success -> {
                        _isLoading.value = false
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
                    }

                    is Response.Error -> {
                        _isLoading.value = false
                        _error.value = response.message
                    }

                    else -> {}
                }
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
            }
        }
    }

    private fun fetchAndStoreImagesFromApi() {
        viewModelScope.launch {
            fetchAPIImagesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _isLoading.value =
                        true

                    is Response.Success -> {
                        _isLoading.value = false
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
                        _isLoading.value = false
                        _error.value = response.message
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

    private fun observeLocalFonts() {
        viewModelScope.launch {
            getFontsUseCase().collect { fonts ->
                _localFonts.value = fonts
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

    private fun getAllExportResults() {
        viewModelScope.launch {
            exportResultsUseCase.getAllExportResults().collect {
                _exportResults.value = it
            }
        }
    }

    fun clearFontDownloadState() {
        _fontDownloadStates.value = _fontDownloadStates.value.filterValues { state ->
            state is FontDownloadState.Progress
        }
    }

}
