package com.github.jasonskd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.jasonskd.ui.viewmodels.CreatePlanViewModel

@Composable
fun CreatePlanScreen(
    onSessionsNotReady: (List<String>) -> Unit,
    vm: CreatePlanViewModel = viewModel { CreatePlanViewModel() }
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val currentCallback by rememberUpdatedState(onSessionsNotReady)

    LaunchedEffect(vm) {
        vm.sessionNotReadyEvent.collect { currentCallback(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WizardStepBar(
            currentStep = uiState.step,
            steps = listOf("选择站点", "选择课程", "命名保存"),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        HorizontalDivider()

        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            when {
                uiState.savedPlanName != null -> {
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
                        Text(
                            "计划 \"${uiState.savedPlanName}\" 已创建！",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { vm.reset() }) {
                            Text("再创建一个")
                        }
                    }
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("错误: ${uiState.errorMessage}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { vm.reset() }) { Text("重置") }
                    }
                }
                uiState.step == 1 && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("选择要包含的网站", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.availableSites.forEach { site ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = site in uiState.selectedSites,
                                    onCheckedChange = { vm.toggleSite(site) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(site.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        site.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { vm.startCourseCollection() },
                            enabled = uiState.selectedSites.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("开始获取课程 →")
                        }
                    }
                }
                uiState.step == 1 && uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在获取课程列表...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.step == 2 && !uiState.isLoading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text("选择要包含的课程", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            uiState.coursesBySite.forEach { (site, courses) ->
                                stickyHeader(key = "header_${site.name}") {
                                    Surface(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = site.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = courses,
                                    key = { index, _ -> "${site.name}_$index" }
                                ) { index, course ->
                                    val selectedIndices = uiState.selectedCourseIndices[site] ?: emptySet()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = index in selectedIndices,
                                            onCheckedChange = { vm.toggleCourse(site, index) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(course.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { vm.fetchVideos() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("获取视频列表 →")
                        }
                    }
                }
                uiState.step == 2 && uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在获取视频列表...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                uiState.step == 3 && !uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("为计划命名", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = uiState.planName,
                            onValueChange = { vm.updatePlanName(it) },
                            label = { Text("计划名称") },
                            placeholder = { Text("例如: 砺儒春季课程") },
                            modifier = Modifier.fillMaxWidth(0.6f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { vm.savePlan() },
                            enabled = uiState.planName.isNotBlank()
                        ) {
                            Text("保存计划")
                        }
                    }
                }
                uiState.step == 3 && uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在保存...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepBar(
    currentStep: Int,
    steps: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val isCompleted = stepNumber < currentStep
            val isCurrent = stepNumber == currentStep
            val isActive = isCompleted || isCurrent

            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isCompleted) "✓" else stepNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (index < steps.size - 1) {
                Spacer(modifier = Modifier.width(6.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = if (isCompleted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}
