package com.github.jasonskd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import api.ProgressPair
import api.VideoProgressData
import com.github.jasonskd.ui.viewmodels.ExecutePhase
import com.github.jasonskd.ui.viewmodels.ExecutePlanViewModel

private val ProgressPair.fraction: Float
    get() = if (duration == 0) 0f else watched.toFloat() / duration

private val VideoProgressData.fraction: Float
    get() = if (duration == 0) 0f else watched.toFloat() / duration

@Composable
fun ExecutePlanScreen(
    onSessionsNotReady: (List<String>) -> Unit,
    vm: ExecutePlanViewModel
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val currentCallback by rememberUpdatedState(onSessionsNotReady)

    LaunchedEffect(vm) {
        vm.sessionNotReadyEvent.collect { currentCallback(it) }
    }

    var planToDelete by remember { mutableStateOf<String?>(null) }

    Row(modifier = Modifier.fillMaxSize()) {
        PlanListPanel(
            plans = uiState.plans,
            selectedPlan = uiState.selectedPlan,
            isExecuting = uiState.isExecuting,
            onSelectPlan = { vm.selectPlan(it) },
            onDeletePlan = { planToDelete = it },
            onRefresh = { vm.refreshPlans() },
            modifier = Modifier.width(220.dp).fillMaxHeight()
        )

        VerticalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            when (val phase = uiState.phase) {
                is ExecutePhase.LoadingPlans -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ExecutePhase.Idle -> {
                    if (uiState.selectedPlan == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "请从左侧选择一个计划",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = uiState.selectedPlan!!,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { vm.startExecution() },
                                modifier = Modifier.width(120.dp).height(48.dp)
                            ) {
                                Text("执行", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                is ExecutePhase.Investigating -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(phase.statusMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ExecutePhase.Executing -> {
                    ExecutingContent(phase = phase, onCancel = { vm.cancelExecution() })
                }
                is ExecutePhase.Done -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(phase.message, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { vm.reset() }) { Text("返回") }
                    }
                }
                is ExecutePhase.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("错误: ${phase.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { vm.startExecution() }) { Text("重试") }
                    }
                }
            }
        }
    }

    planToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("删除计划") },
            text = { Text("确定要删除计划 \"$name\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deletePlan(name); planToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PlanListPanel(
    plans: List<String>,
    selectedPlan: String?,
    isExecuting: Boolean,
    onSelectPlan: (String) -> Unit,
    onDeletePlan: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "计划列表",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(plans) { plan ->
                val isSelected = plan == selectedPlan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable(enabled = !isExecuting) { onSelectPlan(plan) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = plan,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                            isExecuting -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    IconButton(
                        onClick = { onDeletePlan(plan) },
                        enabled = !isExecuting,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(
                            "✕",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExecuting) MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Text("刷新")
        }
    }
}

@Composable
private fun ExecutingContent(
    phase: ExecutePhase.Executing,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!phase.investigationDone) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(phase.investigationMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (phase.executionProgress != null) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("总体进度", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { phase.executionProgress.planProgress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${phase.executionProgress.planProgress.watched}s / ${phase.executionProgress.planProgress.duration}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                phase.executionProgress.sites.forEach { (site, siteData) ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(site.displayName, style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(
                                progress = { siteData.siteProgress.fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${siteData.siteProgress.watched}s / ${siteData.siteProgress.duration}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            siteData.currentVideo?.let { video ->
                                HorizontalDivider()
                                Text("当前: ${video.name}", style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress = { video.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    "${video.watched}s / ${video.duration}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(phase.executionMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("取消执行")
        }
    }
}
