package app.async.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.async.data.model.Curve
import app.async.data.model.TransitionMode
import app.async.data.model.TransitionRule
import app.async.data.model.TransitionSettings
import app.async.data.repository.TransitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransitionUiState(
    val rule: TransitionRule? = null,
    val globalSettings: TransitionSettings = TransitionSettings(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
)

@HiltViewModel
class TransitionViewModel @Inject constructor(
    private val transitionRepository: TransitionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String? = savedStateHandle["playlistId"]

    private val _uiState = MutableStateFlow(TransitionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val playlistRule = playlistId?.let { transitionRepository.getPlaylistDefaultRule(it).first() }
            val globalSettings = transitionRepository.getGlobalSettings().first()

            _uiState.update {
                it.copy(
                    rule = playlistRule,
                    globalSettings = globalSettings,
                    isLoading = false
                )
            }
        }
    }

    private fun getCurrentSettings(): TransitionSettings {
        return _uiState.value.rule?.settings ?: _uiState.value.globalSettings
    }

    fun updateDuration(durationMs: Int) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(durationMs = durationMs)
        updateRuleWithNewSettings(newSettings)
    }

    fun updateMode(mode: TransitionMode) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(mode = mode)
        updateRuleWithNewSettings(newSettings)
    }

    fun updateCurve(curve: Curve) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(curveIn = curve, curveOut = curve)
        updateRuleWithNewSettings(newSettings)
    }

    private fun updateRuleWithNewSettings(newSettings: TransitionSettings) {
        val ruleToUpdate = _uiState.value.rule ?: TransitionRule(
            playlistId = playlistId ?: "",
            settings = TransitionSettings()
        )
        _uiState.update {
            it.copy(rule = ruleToUpdate.copy(settings = newSettings), isSaved = false)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val ruleToSave = _uiState.value.rule

            if (playlistId != null) {
                if (ruleToSave != null) {
                    if (ruleToSave.settings.mode == TransitionMode.NONE) {
                        transitionRepository.deletePlaylistDefaultRule(playlistId)
                    } else {
                        transitionRepository.saveRule(ruleToSave)
                    }
                }
            } else {
                transitionRepository.saveGlobalSettings(getCurrentSettings())
            }
            loadSettings()
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
