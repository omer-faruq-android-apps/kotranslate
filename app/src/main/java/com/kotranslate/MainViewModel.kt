package com.kotranslate

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val serverRunning: Boolean = false,
    val ipAddress: String = "...",
    val port: Int = TranslationServer.DEFAULT_PORT,
    val models: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = true,
    val downloadingLanguages: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val modelManager = ModelManager()

    init {
        refreshIp()
        refreshModels()
    }

    fun refreshIp() {
        val ip = NetworkUtils.getLocalIpAddress(app)
        _uiState.value = _uiState.value.copy(ipAddress = ip)
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true)
            try {
                val models = modelManager.getAllModelsWithStatus()
                _uiState.value = _uiState.value.copy(
                    models = models,
                    isLoadingModels = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    errorMessage = "Failed to load models: ${e.message}"
                )
            }
        }
    }

    fun startServer() {
        val intent = Intent(app, TranslationService::class.java).apply {
            action = TranslationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        _uiState.value = _uiState.value.copy(serverRunning = true)
    }

    fun stopServer() {
        val intent = Intent(app, TranslationService::class.java).apply {
            action = TranslationService.ACTION_STOP
        }
        app.startService(intent)
        _uiState.value = _uiState.value.copy(serverRunning = false)
    }

    fun downloadModel(languageCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadingLanguages = _uiState.value.downloadingLanguages + languageCode
            )
            try {
                modelManager.downloadModel(languageCode)
                refreshModels()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to download ${LanguageUtils.getNameByCode(languageCode)}: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    downloadingLanguages = _uiState.value.downloadingLanguages - languageCode
                )
            }
        }
    }

    fun deleteModel(languageCode: String) {
        viewModelScope.launch {
            try {
                modelManager.deleteModel(languageCode)
                refreshModels()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete ${LanguageUtils.getNameByCode(languageCode)}: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
