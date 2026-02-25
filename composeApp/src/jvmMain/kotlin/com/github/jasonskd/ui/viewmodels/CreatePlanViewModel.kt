package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jasonskd.BeadioClient
import domain.Course
import domain.SupportedSite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class CreatePlanUiState(
    val step: Int = 1,
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val errorMessage: String? = null,
    val availableSites: List<SupportedSite> = SupportedSite.entries(),
    val selectedSites: Set<SupportedSite> = emptySet(),
    val coursesBySite: Map<SupportedSite, List<Course>> = emptyMap(),
    val selectedCourseIndices: Map<SupportedSite, Set<Int>> = emptyMap(),
    val planName: String = "",
    val savedPlanName: String? = null
)

class CreatePlanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CreatePlanUiState())
    val uiState: StateFlow<CreatePlanUiState> = _uiState.asStateFlow()

    val sessionNotReadyEvent = MutableSharedFlow<List<String>>()

    fun toggleSite(site: SupportedSite) {
        _uiState.update { state ->
            val newSelected = if (site in state.selectedSites) {
                state.selectedSites - site
            } else {
                state.selectedSites + site
            }
            state.copy(selectedSites = newSelected)
        }
    }

    fun startCourseCollection() {
        val sites = _uiState.value.selectedSites.toList()
        if (sites.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Step 1: POST /plans/new
                val createResp = BeadioClient.createPlanManager(sites.map { it.name })
                when (createResp.exceptionType) {
                    null -> _uiState.update { it.copy(loadingMessage = createResp.message ?: "") }
                    "MANAGER_ALREADY_EXISTS" -> _uiState.update { it.copy(loadingMessage = createResp.message ?: "") }
                    "SESSIONS_NOT_READY" -> {
                        val notReadySites = createResp.data?.let {
                            BeadioClient.json.decodeFromJsonElement<List<String>>(it)
                        } ?: sites.map { it.name }
                        _uiState.update { it.copy(isLoading = false) }
                        sessionNotReadyEvent.emit(notReadySites)
                        return@launch
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = createResp.message) }
                        return@launch
                    }
                }

                // Step 2: POST /plans/new/courses
                val coursesResp = BeadioClient.startCourseCollection()
                if (coursesResp.exceptionType != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = coursesResp.message) }
                    return@launch
                }
                _uiState.update { it.copy(loadingMessage = coursesResp.message ?: "") }

                // Step 3: Poll until AwaitingCourseSelection (type == "Ready")
                while (true) {
                    delay(1500)
                    val pollResp = BeadioClient.getPlanCreationState()
                    when {
                        pollResp.type == "Ready" && pollResp.data != null -> {
                            val raw = BeadioClient.json.decodeFromJsonElement<Map<String, List<Course>>>(pollResp.data)
                            val coursesBySite = raw.entries.mapNotNull { (key, value) ->
                                SupportedSite.fromName(key)?.let { site -> site to value }
                            }.toMap()
                            _uiState.update { it.copy(isLoading = false, step = 2, coursesBySite = coursesBySite) }
                            return@launch
                        }
                        pollResp.exceptionType != null || pollResp.type == "Failed" -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = pollResp.message) }
                            return@launch
                        }
                        else -> _uiState.update { it.copy(loadingMessage = pollResp.message ?: "") }
                        // Processing → keep polling
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "网络错误") }
            }
        }
    }

    fun toggleCourse(site: SupportedSite, index: Int) {
        _uiState.update { state ->
            val current = state.selectedCourseIndices[site] ?: emptySet()
            val newSet = if (index in current) current - index else current + index
            state.copy(selectedCourseIndices = state.selectedCourseIndices + (site to newSet))
        }
    }

    fun fetchVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val selection = _uiState.value.selectedCourseIndices
                    .mapKeys { (site, _) -> site.name }
                    .mapValues { (_, indices) -> indices.toList() }

                // Step 1: POST /plans/new/videos
                val fetchResp = BeadioClient.fetchVideos(selection)
                if (fetchResp.exceptionType != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = fetchResp.message) }
                    return@launch
                }
                _uiState.update { it.copy(loadingMessage = fetchResp.message ?: "") }

                // Step 2: Poll until AwaitingPlanSave (Processing + message == "等待保存计划")
                while (true) {
                    delay(1500)
                    val pollResp = BeadioClient.getPlanCreationState()
                    when {
                        pollResp.type == "Processing" && pollResp.message == "等待保存计划" -> {
                            _uiState.update { it.copy(isLoading = false, step = 3) }
                            return@launch
                        }
                        pollResp.exceptionType != null || pollResp.type == "Failed" -> {
                            _uiState.update { it.copy(isLoading = false, errorMessage = pollResp.message) }
                            return@launch
                        }
                        else -> _uiState.update { it.copy(loadingMessage = pollResp.message ?: "") }
                        // Still fetching videos → keep polling
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "网络错误") }
            }
        }
    }

    fun updatePlanName(name: String) {
        _uiState.update { it.copy(planName = name) }
    }

    fun savePlan() {
        val name = _uiState.value.planName
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val resp = BeadioClient.savePlan(name)
                if (resp.exceptionType == null) {
                    _uiState.update { it.copy(isLoading = false, savedPlanName = name) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = resp.message) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "网络错误") }
            }
        }
    }

    fun reset() {
        _uiState.update { CreatePlanUiState() }
    }
}
