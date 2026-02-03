package com.example.urduphotodesigner.viewmodels

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.canvas.sealed.FontDownloadState
import com.example.urduphotodesigner.common.canvas.sealed.HomeRow
import com.example.urduphotodesigner.common.canvas.sealed.TemplateDownloadState
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.GradientPresets
import com.example.urduphotodesigner.data.model.ExportResult
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.data.model.TemplateEntity
import com.example.urduphotodesigner.domain.repo.DownloadRepo
import com.example.urduphotodesigner.domain.usecase.DeleteFontsUseCase
import com.example.urduphotodesigner.domain.usecase.DeleteGradientUseCase
import com.example.urduphotodesigner.domain.usecase.DeleteImagesUseCase
import com.example.urduphotodesigner.domain.usecase.ExportResultsUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPIFontsUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPIImagesUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPITemplatesUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPITrendsUseCase
import com.example.urduphotodesigner.domain.usecase.GetAllGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.GetFontsUseCase
import com.example.urduphotodesigner.domain.usecase.GetImagesUseCase
import com.example.urduphotodesigner.domain.usecase.GetTemplatesUseCase
import com.example.urduphotodesigner.domain.usecase.GetTrendsUseCase
import com.example.urduphotodesigner.domain.usecase.InsertFontsUseCase
import com.example.urduphotodesigner.domain.usecase.InsertGradientUseCase
import com.example.urduphotodesigner.domain.usecase.InsertImagesUseCase
import com.example.urduphotodesigner.domain.usecase.InsertTemplatesUseCase
import com.example.urduphotodesigner.domain.usecase.InsertTrendsUseCase
import com.example.urduphotodesigner.domain.usecase.SeedGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateFontStatusUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateFontsUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateGradientUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateImagesUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateTemplatesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
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
    private val fontRepository: DownloadRepo,
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
    private val insertTrendsUseCase: InsertTrendsUseCase
) : ViewModel() {

    private val progressMutex = kotlinx.coroutines.sync.Mutex()

    private val _trendRows = MutableStateFlow<List<HomeRow>>(emptyList())
    val trendRows: StateFlow<List<HomeRow>> = _trendRows.asStateFlow()

    private val _downloadState = MutableStateFlow<FontDownloadState?>(null)
    val downloadState: StateFlow<FontDownloadState?> = _downloadState

    private val _localFonts = MutableStateFlow<List<FontEntity>>(emptyList())
    val localFonts: StateFlow<List<FontEntity>> = _localFonts.asStateFlow()

    private val _localImages = MutableStateFlow<List<ImageEntity>>(emptyList())
    val localImages: StateFlow<List<ImageEntity>> = _localImages.asStateFlow()

    private val _localTemplates = MutableStateFlow<List<TemplateEntity>>(emptyList())
    val localTemplates: StateFlow<List<TemplateEntity>> = _localTemplates.asStateFlow()

    private val _templateDownloadState = MutableStateFlow<TemplateDownloadState?>(null)
    val templateDownloadState: StateFlow<TemplateDownloadState?> = _templateDownloadState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _gradients = MutableLiveData<List<GradientItem>>()
    val gradients: LiveData<List<GradientItem>> = _gradients

    private val _exportResults = MutableLiveData<List<ExportResult>>()
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
        fetchAndStoreFontsFromApi()
        observeLocalFonts()
        fetchAndStoreImagesFromApi()
        observeLocalImages()
        getAllExportResults()
        fetchAndStoreTemplatesFromApi()
        observeLocalTemplates()
        observeLocalTrends()
        fetchAndStoreTrendsFromApi()
        viewModelScope.launch {
            seed(GradientPresets.defaultList)
        }

        viewModelScope.launch {
            getAll().collect { uiList ->
                _gradients.value = uiList
            }
        }
    }

    fun deleteGradient(id: Long) = viewModelScope.launch { delete(id) }
    fun updateGradient(g: GradientItem) = viewModelScope.launch { update(g) }
    fun insertGradient(g: GradientItem) = viewModelScope.launch { insert(g) }

    fun fetchAndStoreTemplatesFromApi() {
        viewModelScope.launch {
            fetchAPITemplatesUseCase().collect { response ->
                when (response) {
                    is Response.Loading -> _isLoading.value = true

                    is Response.Success -> {
                        _isLoading.value = false
                        insertTemplatesUseCase.invoke(response.data!!) // list<TemplateEntity>
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
                    is Response.Loading -> _isLoading.value = true

                    is Response.Success -> {
                        _isLoading.value = false
                        insertFontsUseCase.invoke(response.data!!)
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
                    is Response.Loading -> _isLoading.value = true
                    is Response.Success -> {
                        _isLoading.value = false
                        insertTrendsUseCase.invoke(response.data!!) // saves in Room
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
                    is Response.Loading -> _isLoading.value = true

                    is Response.Success -> {
                        _isLoading.value = false
                        insertImagesUseCase.invoke(response.data!!)
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
        viewModelScope.launch {
            _templateDownloadState.value = TemplateDownloadState.Progress(
                0, template.copy(is_downloading = true, download_progress = 0)
            )

            try {
                // 1️⃣ Mark as downloading in DB (0%)
                updateTemplatesUseCase.invoke(
                    template.id.toString(),
                    isDownloaded = false,
                    isDownloading = true,
                    progress = 0,
                    filePath = null
                )

                val downloadedFile = fontRepository.downloadAssets(
                    url = Constants.BASE_URL_DOWNLOAD + template.json_url,
                    fileName = "template_${template.id}.json",
                    onProgress = { progress ->

                        Log.d(TAG, "downloadTemplate: ${template.id} $progress")

                        viewModelScope.launch {
                            progressMutex.withLock {
                                updateTemplatesUseCase.invoke(
                                    template.id.toString(),
                                    isDownloaded = false,
                                    isDownloading = true,
                                    progress = progress,
                                    filePath = null
                                )
                            }
                        }
                        _templateDownloadState.value = TemplateDownloadState.Progress(
                            progress,
                            template.copy(is_downloading = true, download_progress = progress)
                        )

                    })

                updateTemplatesUseCase.invoke(
                    template.id.toString(),
                    isDownloaded = true,
                    isDownloading = false,
                    progress = 100,
                    filePath = downloadedFile.absolutePath
                )
                val updated = template.copy(
                    is_downloaded = true,
                    file_path = downloadedFile.absolutePath,
                    download_progress = 100
                )
                _templateDownloadState.value =
                    TemplateDownloadState.SuccessWithTemplate(downloadedFile, updated)

            } catch (e: Exception) {

                updateTemplatesUseCase.invoke(
                    template.id.toString(),
                    isDownloaded = false,
                    isDownloading = false,
                    progress = 0,
                    filePath = null
                )
                _templateDownloadState.value =
                    TemplateDownloadState.Error(e.message ?: "Download failed")

            }
        }
    }

    fun clearTemplateDownloadState() {
        _templateDownloadState.value = null
    }

    fun downloadFont(font: FontEntity) {
        viewModelScope.launch {
            _downloadState.value = FontDownloadState.Progress(0, font.copy(is_downloading = true))
            updateFontStatusUseCase.invoke(font.id.toString(), true)

            try {
                val downloadedFile = fontRepository.downloadAssets(
                    url = Constants.BASE_URL_GLIDE + font.file_url,
                    fileName = font.font_name + ".ttf",
                    onProgress = { progress ->
                        _downloadState.value = FontDownloadState.Progress(
                            progress, font.copy(is_downloading = true, download_progress = progress)
                        )
                    })

                updateFontsUseCase.invoke(
                    font.id.toString(),
                    isDownloaded = true,
                    isDownloading = false,
                    filePath = downloadedFile.absolutePath
                )

                // After successful download, get the typeface and update the canvas
                val updatedFont = font.copy(
                    is_downloaded = true,
                    is_downloading = false,
                    download_progress = 100,
                    file_path = downloadedFile.absolutePath
                )

                _downloadState.value =
                    FontDownloadState.SuccessWithTypeface(downloadedFile, updatedFont)
            } catch (e: Exception) {
                updateFontStatusUseCase.invoke(font.id.toString(), false)
                _downloadState.value = FontDownloadState.Error(e.message ?: "Download failed", font)
            }
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

    fun clearDownloadState() {
        _downloadState.value = null
    }
}
