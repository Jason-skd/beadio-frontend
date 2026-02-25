package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.ExecutionProgressData
import com.github.jasonskd.BeadioClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

sealed class ExecutePhase {
    object Idle : ExecutePhase()
    object LoadingPlans : ExecutePhase()
    data class Investigating(val statusMessage: String) : ExecutePhase()
    data class Executing(
        val investigationDone: Boolean,
        val investigationMessage: String,
        val executionProgress: ExecutionProgressData?,
        val executionMessage: String
    ) : ExecutePhase()
    data class Done(val message: String) : ExecutePhase()
    data class Error(val message: String) : ExecutePhase()
}

data class ExecutePlanUiState(
    val plans: List<String> = emptyList(),
    val selectedPlan: String? = null,
    val phase: ExecutePhase = ExecutePhase.LoadingPlans
) {
    val isExecuting: Boolean
        get() = phase is ExecutePhase.Investigating || phase is ExecutePhase.Executing
}

class ExecutePlanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ExecutePlanUiState())
    val uiState: StateFlow<ExecutePlanUiState> = _uiState.asStateFlow()

    val sessionNotReadyEvent = MutableSharedFlow<List<String>>()

    private var executionJob: Job? = null

    init {
        probeAndLoad()
    }

    // Starts (or restarts) the investigation + execution polling loops.
    // Uses save-then-cancel so it can safely be called from inside executionJob.
    private fun resumePolling(skipInvestigation: Boolean) {
        val oldJob = executionJob
        executionJob = viewModelScope.launch {
            val invJob: Job? = if (!skipInvestigation) launch {
                while (isActive) {
                    delay(1500)
                    val resp = BeadioClient.getInvestigation()
                    _uiState.update { s ->
                        val p = s.phase as? ExecutePhase.Executing ?: return@update s
                        when (resp.type) {
                            "Ready", "Failed" -> s.copy(phase = p.copy(investigationDone = true, investigationMessage = resp.message ?: ""))
                            else -> s.copy(phase = p.copy(investigationMessage = resp.message ?: p.investigationMessage))
                        }
                    }
                    if (resp.type == "Ready" || resp.type == "Failed") return@launch
                }
            } else null

            while (isActive) {
                delay(1500)
                val resp = BeadioClient.getExecution()
                when (resp.type) {
                    "Ready" -> {
                        invJob?.cancel()
                        _uiState.update { it.copy(phase = ExecutePhase.Done(resp.message ?: "")) }
                        return@launch
                    }
                    "Failed" -> {
                        invJob?.cancel()
                        _uiState.update { it.copy(phase = ExecutePhase.Error(resp.message ?: "")) }
                        return@launch
                    }
                    else -> {
                        val progress = resp.data?.let {
                            BeadioClient.json.decodeFromJsonElement<ExecutionProgressData>(it)
                        }
                        _uiState.update { s ->
                            val p = s.phase as? ExecutePhase.Executing ?: return@update s
                            s.copy(phase = p.copy(
                                executionProgress = progress ?: p.executionProgress,
                                executionMessage = resp.message ?: p.executionMessage
                            ))
                        }
                    }
                }
            }
        }
        oldJob?.cancel()
    }

    // Loads plans and probes backend execution/investigation state to determine the correct phase.
    private fun probeAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ExecutePhase.LoadingPlans) }
            try {
                val plans = BeadioClient.getPlans()
                _uiState.update { it.copy(plans = plans) }

                val execResp = BeadioClient.getExecution()
                val execInProgress = execResp.exceptionType == null && execResp.type == "Processing"
                val execDone = execResp.exceptionType == null && execResp.type == "Ready"
                val execFailed = execResp.exceptionType == null && execResp.type == "Failed"

                when {
                    execInProgress -> {
                        val invResp = BeadioClient.getInvestigation()
                        val invInProgress = invResp.exceptionType == null && invResp.type == "Processing"
                        val progress = execResp.data?.let {
                            BeadioClient.json.decodeFromJsonElement<ExecutionProgressData>(it)
                        }
                        _uiState.update {
                            it.copy(phase = ExecutePhase.Executing(
                                investigationDone = !invInProgress,
                                investigationMessage = invResp.message ?: "",
                                executionProgress = progress,
                                executionMessage = execResp.message ?: ""
                            ))
                        }
                        resumePolling(skipInvestigation = !invInProgress)
                    }
                    execDone -> _uiState.update { it.copy(phase = ExecutePhase.Done(execResp.message ?: "")) }
                    execFailed -> _uiState.update { it.copy(phase = ExecutePhase.Error(execResp.message ?: "")) }
                    else -> _uiState.update { it.copy(phase = ExecutePhase.Idle) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(plans = emptyList(), phase = ExecutePhase.Idle) }
            }
        }
    }

    fun selectPlan(planName: String) {
        if (_uiState.value.isExecuting) return
        _uiState.update { it.copy(selectedPlan = planName, phase = ExecutePhase.Idle) }
    }

    fun deletePlan(planName: String) {
        if (_uiState.value.isExecuting) return
        viewModelScope.launch {
            try {
                BeadioClient.deletePlan(planName)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* ignore errors on delete */ }
            _uiState.update { state ->
                val newPlans = state.plans.filter { it != planName }
                state.copy(
                    plans = newPlans,
                    selectedPlan = if (state.selectedPlan == planName) null else state.selectedPlan
                )
            }
        }
    }

    fun refreshPlans() {
        if (_uiState.value.isExecuting) {
            // Execution in progress: only refresh the plan list, preserve phase
            viewModelScope.launch {
                try {
                    val plans = BeadioClient.getPlans()
                    _uiState.update { it.copy(plans = plans) }
                } catch (_: Exception) {}
            }
            return
        }
        probeAndLoad()
    }

    fun startExecution() {
        val planName = _uiState.value.selectedPlan ?: return
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            try {
                // Phase 1: POST /investigation
                _uiState.update { it.copy(phase = ExecutePhase.Investigating("正在启动调查...")) }
                var skipInvestigation = false
                val invResp = BeadioClient.createInvestigation(planName)
                when (invResp.exceptionType) {
                    null -> _uiState.update { it.copy(phase = ExecutePhase.Investigating(invResp.message ?: "正在启动调查...")) }
                    "ALL_VIDEOS_COLLECTED" -> { skipInvestigation = true }
                    "SESSIONS_NOT_READY" -> {
                        val notReadySites = invResp.data?.let {
                            BeadioClient.json.decodeFromJsonElement<List<String>>(it)
                        } ?: emptyList()
                        _uiState.update { it.copy(phase = ExecutePhase.Idle) }
                        sessionNotReadyEvent.emit(notReadySites)
                        return@launch
                    }
                    else -> {
                        _uiState.update { it.copy(phase = ExecutePhase.Error(invResp.message ?: "")) }
                        return@launch
                    }
                }

                // Phase 2: POST /execution
                val execResp = BeadioClient.createExecution(planName)
                when (execResp.exceptionType) {
                    null -> { /* started */ }
                    "SESSIONS_NOT_READY" -> {
                        val notReadySites = execResp.data?.let {
                            BeadioClient.json.decodeFromJsonElement<List<String>>(it)
                        } ?: emptyList()
                        _uiState.update { it.copy(phase = ExecutePhase.Idle) }
                        sessionNotReadyEvent.emit(notReadySites)
                        return@launch
                    }
                    else -> {
                        _uiState.update { it.copy(phase = ExecutePhase.Error(execResp.message ?: "")) }
                        return@launch
                    }
                }

                _uiState.update {
                    it.copy(
                        phase = ExecutePhase.Executing(
                            investigationDone = skipInvestigation,
                            investigationMessage = invResp.message ?: "",
                            executionProgress = null,
                            executionMessage = execResp.message ?: ""
                        )
                    )
                }

                // Phase 3 & 4: hand off to resumePolling (cancels this coroutine via oldJob.cancel())
                resumePolling(skipInvestigation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = ExecutePhase.Error(e.message ?: "网络错误")) }
            }
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
        executionJob = null
        _uiState.update { it.copy(phase = ExecutePhase.Idle) }
        viewModelScope.launch {
            try { BeadioClient.deleteExecution() } catch (_: Exception) {}
            try { BeadioClient.deleteInvestigation() } catch (_: Exception) {}
        }
    }

    fun reset() {
        executionJob?.cancel()
        executionJob = null
        _uiState.update { it.copy(phase = ExecutePhase.Idle, selectedPlan = null) }
        viewModelScope.launch {
            try { BeadioClient.deleteExecution() } catch (_: Exception) {}
            try { BeadioClient.deleteInvestigation() } catch (_: Exception) {}
        }
    }
}
