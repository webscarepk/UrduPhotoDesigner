package com.example.urduphotodesigner.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.urduphotodesigner.common.canvas.model.ExportResult
import com.example.urduphotodesigner.common.canvas.model.GradientItem
import com.example.urduphotodesigner.common.utils.Constants
import com.example.urduphotodesigner.common.utils.DownloadState
import com.example.urduphotodesigner.common.sealed.Response
import com.example.urduphotodesigner.common.utils.GradientPresets
import com.example.urduphotodesigner.data.mapper.toEntity
import com.example.urduphotodesigner.data.model.FontEntity
import com.example.urduphotodesigner.data.model.FontsResponse
import com.example.urduphotodesigner.data.model.ImageEntity
import com.example.urduphotodesigner.domain.repo.FontRepository
import com.example.urduphotodesigner.domain.usecase.DeleteGradientUseCase
import com.example.urduphotodesigner.domain.usecase.ExportResultsUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPIFontsUseCase
import com.example.urduphotodesigner.domain.usecase.FetchAPIImagesUseCase
import com.example.urduphotodesigner.domain.usecase.GetAllGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.GetFontsUseCase
import com.example.urduphotodesigner.domain.usecase.GetImagesUseCase
import com.example.urduphotodesigner.domain.usecase.InsertFontsUseCase
import com.example.urduphotodesigner.domain.usecase.InsertGradientUseCase
import com.example.urduphotodesigner.domain.usecase.InsertImagesUseCase
import com.example.urduphotodesigner.domain.usecase.SeedGradientsUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateFontStatusUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateFontsUseCase
import com.example.urduphotodesigner.domain.usecase.UpdateGradientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val fetchAPIFontsUseCase: FetchAPIFontsUseCase,
    private val insertFontsUseCase: InsertFontsUseCase,
    private val getFontsUseCase: GetFontsUseCase,
    private val fetchAPIImagesUseCase: FetchAPIImagesUseCase,
    private val insertImagesUseCase: InsertImagesUseCase,
    private val getImagesUseCase: GetImagesUseCase,
    private val fontRepository: FontRepository,
    private val updateFontsUseCase: UpdateFontsUseCase,
    private val updateFontStatusUseCase: UpdateFontStatusUseCase,
    private val getAll: GetAllGradientsUseCase,
    private val seed: SeedGradientsUseCase,
    private val delete: DeleteGradientUseCase,
    private val insert: InsertGradientUseCase,
    private val update: UpdateGradientUseCase,
    private val exportResultsUseCase: ExportResultsUseCase
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState

    private val _localFonts = MutableStateFlow<List<FontEntity>>(emptyList())
    val localFonts: StateFlow<List<FontEntity>> = _localFonts.asStateFlow()

    private val _localImages = MutableStateFlow<List<ImageEntity>>(emptyList())
    val localImages: StateFlow<List<ImageEntity>> = _localImages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _gradients = MutableLiveData<List<GradientItem>>()
    val gradients: LiveData<List<GradientItem>> = _gradients

    private val _exportResults = MutableLiveData<List<ExportResult>>()
    val exportResults: LiveData<List<ExportResult>> get() = _exportResults

    init {
        Log.d("FontsViewModel", "ViewModel initialized")
        fetchAndStoreFontsFromApi()
        observeLocalFonts()
        fetchAndStoreImagesFromApi()
        observeLocalImages()
        getAllExportResults()
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
    fun updateGradient(g: GradientItem)   = viewModelScope.launch { update(g) }
    fun insertGradient(g: GradientItem)   = viewModelScope.launch { insert(g) }

    private fun fetchAndStoreFontsFromApi() {
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

    // In MainViewModel.kt
    fun downloadFont(font: FontEntity) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Progress(0)
            updateFontStatusUseCase.invoke(font.id.toString(), true)

            try {
                val downloadedFile = fontRepository.downloadFont(
                    fontUrl = Constants.BASE_URL_GLIDE+font.file_url,
                    fileName = font.file_name,
                    onProgress = { progress ->
                        _downloadState.value = DownloadState.Progress(progress)
                    }
                )

                updateFontsUseCase.invoke(font.id.toString(),
                    isDownloaded = true,
                    isDownloading = false,
                    filePath = downloadedFile.absolutePath
                )

                // After successful download, get the typeface and update the canvas
                font.copy(
                    is_downloaded = true,
                    file_path = downloadedFile.absolutePath
                ).let {
                    _downloadState.value = DownloadState.SuccessWithTypeface(downloadedFile, it)
                }
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun insertExportResult(exportResult: ExportResult) {
        viewModelScope.launch {
            exportResultsUseCase.insertExportResult(exportResult)
        }
    }

    fun updateExportResult(exportResult: ExportResult) {
        viewModelScope.launch {
            exportResultsUseCase.updateExportResult(exportResult)
        }
    }

    fun deleteExportResult(exportResult: ExportResult) {
        viewModelScope.launch {
            exportResultsUseCase.deleteExportResult(exportResult)
        }
    }

    fun getAllExportResults() {
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
