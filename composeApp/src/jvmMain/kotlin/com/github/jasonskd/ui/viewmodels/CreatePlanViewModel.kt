package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.Course
import domain.SupportedSite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePlanUiState(
    val step: Int = 1,
    val isLoading: Boolean = false,
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
            _uiState.update { it.copy(isLoading = true) }
            // stub: POST /plans/new + POST /plans/new/courses + poll
            delay(2000)
            val mockCourses = sites.associateWith { site ->
                listOf(
                    Course(name = "${site.displayName} 课程一", url = "https://example.com/1"),
                    Course(name = "${site.displayName} 课程二", url = "https://example.com/2"),
                    Course(name = "${site.displayName} 课程三", url = "https://example.com/3")
                )
            }
            _uiState.update { it.copy(isLoading = false, step = 2, coursesBySite = mockCourses) }
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
            _uiState.update { it.copy(isLoading = true) }
            // stub: POST /plans/new/videos + poll
            delay(2000)
            _uiState.update { it.copy(isLoading = false, step = 3) }
        }
    }

    fun updatePlanName(name: String) {
        _uiState.update { it.copy(planName = name) }
    }

    fun savePlan() {
        val name = _uiState.value.planName
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // stub: PUT /plans/new
            delay(1000)
            _uiState.update { it.copy(isLoading = false, savedPlanName = name) }
        }
    }

    fun reset() {
        _uiState.update { CreatePlanUiState() }
    }
}
