package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jasonskd.BeadioClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement

data class SettingsUiState(
    val availableBrowsers: List<String> = emptyList(),
    val selectedChannel: String = "chrome",
    val savedChannel: String = "chrome",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    val configSavedEvent = MutableSharedFlow<Unit>()

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val browsers = BeadioClient.getAvailableBrowsers()
                val configResp = BeadioClient.getConfig()
                val currentChannel = if (configResp.data != null)
                    BeadioClient.json.decodeFromJsonElement<LocalConfig>(configResp.data).browserChannel
                else browsers.firstOrNull() ?: "chrome"
                val configAlreadyExists = configResp.data != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        availableBrowsers = browsers,
                        selectedChannel = currentChannel,
                        savedChannel = if (configAlreadyExists) currentChannel else ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun selectChannel(channel: String) {
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val resp = BeadioClient.updateConfig(_uiState.value.selectedChannel)
                if (resp.exceptionType == null) {
                    _uiState.update {
                        it.copy(isSaving = false, savedChannel = _uiState.value.selectedChannel)
                    }
                    configSavedEvent.emit(Unit)
                } else {
                    _uiState.update { it.copy(isSaving = false, errorMessage = resp.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }
}

@Serializable
private data class LocalConfig(val browserChannel: String)
