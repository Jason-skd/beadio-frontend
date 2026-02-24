package com.github.jasonskd.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.ExecutionProgressData
import api.ProgressPair
import api.SiteProgressData
import api.VideoProgressData
import domain.SupportedSite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class ExecutePlanViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ExecutePlanUiState())
    val uiState: StateFlow<ExecutePlanUiState> = _uiState.asStateFlow()

    val sessionNotReadyEvent = MutableSharedFlow<List<String>>()

    init {
        loadPlans()
    }

    private fun loadPlans() {
        viewModelScope.launch {
            // stub: GET /plans
            delay(500)
            _uiState.update {
                it.copy(
                    plans = listOf("我的计划", "砺儒课程计划"),
                    phase = ExecutePhase.Idle
                )
            }
        }
    }

    fun selectPlan(planName: String) {
        _uiState.update { it.copy(selectedPlan = planName, phase = ExecutePhase.Idle) }
    }

    fun deletePlan(planName: String) {
        viewModelScope.launch {
            // stub: DELETE /plans
            delay(300)
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
        _uiState.update { it.copy(phase = ExecutePhase.LoadingPlans) }
        loadPlans()
    }

    fun startExecution() {
        val planName = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            // Step 1: Start Investigation
            _uiState.update { it.copy(phase = ExecutePhase.Investigating("正在收集视频元数据...")) }
            // stub: POST /investigation
            delay(1000)

            // stub: POST /execution
            _uiState.update {
                it.copy(
                    phase = ExecutePhase.Executing(
                        investigationDone = false,
                        investigationMessage = "正在调查视频...",
                        executionProgress = null,
                        executionMessage = "正在启动执行..."
                    )
                )
            }

            // stub: simulate concurrent polling
            var investigationRound = 0
            var executionRound = 0
            while (true) {
                delay(800)
                investigationRound++
                executionRound++

                val investigationDone = investigationRound >= 4
                val executionDone = executionRound >= 6

                val watched = (executionRound * 200).coerceAtMost(1200)
                val duration = 1200
                val videoWatched = (executionRound * 50).coerceAtMost(300)
                val videoDuration = 300

                val mockProgress = if (executionRound >= 2) {
                    ExecutionProgressData(
                        planProgress = ProgressPair(watched, duration),
                        sites = mapOf(
                            SupportedSite.Liru to SiteProgressData(
                                siteProgress = ProgressPair(watched, duration),
                                currentVideo = if (!executionDone) VideoProgressData(
                                    name = "第${executionRound}节 示例视频",
                                    watched = videoWatched,
                                    duration = videoDuration
                                ) else null
                            )
                        )
                    )
                } else null

                if (executionDone) {
                    _uiState.update {
                        it.copy(phase = ExecutePhase.Done("执行完成！计划 \"$planName\" 已全部播放完毕。"))
                    }
                    break
                }

                _uiState.update {
                    it.copy(
                        phase = ExecutePhase.Executing(
                            investigationDone = investigationDone,
                            investigationMessage = if (investigationDone) "调查完成" else "正在调查第 $investigationRound 个视频...",
                            executionProgress = mockProgress,
                            executionMessage = "正在执行第 $executionRound 轮..."
                        )
                    )
                }
            }
        }
    }

    fun cancelExecution() {
        _uiState.update { it.copy(phase = ExecutePhase.Idle) }
    }

    fun reset() {
        _uiState.update { it.copy(phase = ExecutePhase.Idle, selectedPlan = null) }
    }
}
