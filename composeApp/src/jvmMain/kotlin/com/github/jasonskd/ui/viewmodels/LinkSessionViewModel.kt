package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import domain.SupportedSite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionStatus { Idle, Connecting, Ready, Failed }

data class SessionSiteUiState(
    val site: SupportedSite,
    val status: SessionStatus = SessionStatus.Idle,
    val statusMessage: String = "未连接"
)

data class LinkSessionUiState(
    val sessions: List<SessionSiteUiState> = SupportedSite.entries().map { SessionSiteUiState(it) },
    val allSessionsReady: Boolean = false
)

class LinkSessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LinkSessionUiState())
    val uiState: StateFlow<LinkSessionUiState> = _uiState.asStateFlow()

    fun startSessionsFor(siteNames: List<String>) {
        _uiState.update { it.copy(allSessionsReady = false) }
        val sites = siteNames.mapNotNull { SupportedSite.fromName(it) }
        sites.forEach { site -> connectSession(site) }
    }

    fun connectSession(site: SupportedSite) {
        viewModelScope.launch {
            updateSiteStatus(site, SessionStatus.Connecting, "连接中...")
            try {
                // stub: POST /sessions/{site.name} + poll
                delay(1500)
                delay(1000)
                updateSiteStatus(site, SessionStatus.Ready, "已就绪")
                checkAllReady()
            } catch (e: Exception) {
                updateSiteStatus(site, SessionStatus.Failed, "连接失败")
            }
        }
    }

    fun retrySession(site: SupportedSite) {
        connectSession(site)
    }

    fun disconnectAll() {
        viewModelScope.launch {
            // stub: DELETE /sessions
            delay(500)
            _uiState.update { state ->
                state.copy(
                    sessions = state.sessions.map {
                        it.copy(status = SessionStatus.Idle, statusMessage = "未连接")
                    },
                    allSessionsReady = false
                )
            }
        }
    }

    private fun updateSiteStatus(site: SupportedSite, status: SessionStatus, message: String) {
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.map { s ->
                    if (s.site == site) s.copy(status = status, statusMessage = message) else s
                }
            )
        }
    }

    private fun checkAllReady() {
        val allReady = _uiState.value.sessions.all { it.status == SessionStatus.Ready }
        if (allReady) {
            _uiState.update { it.copy(allSessionsReady = true) }
        }
    }
}
