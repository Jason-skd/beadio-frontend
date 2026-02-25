package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jasonskd.BeadioClient
import domain.SupportedSite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val requiredSites: Set<SupportedSite> = emptySet(),
    val allSessionsReady: Boolean = false
)

class LinkSessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LinkSessionUiState())
    val uiState: StateFlow<LinkSessionUiState> = _uiState.asStateFlow()

    private val siteJobs = mutableMapOf<SupportedSite, Job>()

    fun startSessionsFor(siteNames: List<String>) {
        _uiState.update { it.copy(allSessionsReady = false) }
        val sites = siteNames.mapNotNull { SupportedSite.fromName(it) }
        _uiState.update { it.copy(requiredSites = sites.toSet()) }
        sites.forEach { site -> connectSession(site) }
    }

    fun connectSession(site: SupportedSite) {
        siteJobs[site]?.cancel()
        siteJobs[site] = viewModelScope.launch {
            updateSiteStatus(site, SessionStatus.Connecting, "连接中...")
            try {
                val resp = BeadioClient.createSession(site.name)
                when (resp.exceptionType) {
                    null -> updateSiteStatus(site, SessionStatus.Connecting, resp.message ?: "连接中...")
                    "SESSION_ALREADY_EXISTS" -> updateSiteStatus(site, SessionStatus.Connecting, resp.message ?: "连接中...")
                    else -> {
                        updateSiteStatus(site, SessionStatus.Failed, resp.message ?: "连接失败")
                        return@launch
                    }
                }
                // Poll until Ready or Failed
                while (true) {
                    delay(1500)
                    val poll = BeadioClient.getSession(site.name)
                    when (poll.type) {
                        "Ready" -> {
                            updateSiteStatus(site, SessionStatus.Ready, poll.message ?: "已就绪")
                            checkAllReady()
                            return@launch
                        }
                        "Failed" -> {
                            updateSiteStatus(site, SessionStatus.Failed, poll.message ?: "连接失败")
                            return@launch
                        }
                        else -> updateSiteStatus(site, SessionStatus.Connecting, poll.message ?: "连接中...")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateSiteStatus(site, SessionStatus.Failed, e.message ?: "连接失败")
            }
        }
    }

    fun retrySession(site: SupportedSite) {
        connectSession(site)
    }

    fun disconnectAll() {
        viewModelScope.launch {
            try {
                BeadioClient.deleteSessions()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* ignore errors on disconnect */ }
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
        val required = _uiState.value.requiredSites
        if (required.isEmpty()) return
        val allReady = _uiState.value.sessions
            .filter { it.site in required }
            .all { it.status == SessionStatus.Ready }
        if (allReady) {
            _uiState.update { it.copy(allSessionsReady = true) }
        }
    }
}
