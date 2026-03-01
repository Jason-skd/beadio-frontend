package com.github.jasonskd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.jasonskd.ui.screens.CreatePlanScreen
import com.github.jasonskd.ui.screens.ExecutePlanScreen
import com.github.jasonskd.ui.screens.LinkSessionScreen
import com.github.jasonskd.BeadioClient
import com.github.jasonskd.ui.screens.SettingsScreen
import com.github.jasonskd.ui.theme.BeadioTheme
import com.github.jasonskd.ui.viewmodels.ExecutePlanViewModel
import com.github.jasonskd.ui.viewmodels.LinkSessionViewModel
import com.github.jasonskd.ui.viewmodels.SettingsViewModel

sealed class AppDestination {
    object ExecutePlan : AppDestination()
    object CreatePlan : AppDestination()
    object LinkSession : AppDestination()
    object Settings : AppDestination()
}

@Composable
fun App() {
    BeadioTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val executePlanVm = viewModel { ExecutePlanViewModel() }
            val linkVm = viewModel { LinkSessionViewModel() }
            val settingsVm = viewModel { SettingsViewModel() }
            val linkSessionState by linkVm.uiState.collectAsStateWithLifecycle()
            var selected by remember { mutableStateOf<AppDestination>(AppDestination.ExecutePlan) }
            var sessionsLocked by remember { mutableStateOf(false) }
            var configLocked by remember { mutableStateOf(false) }
            var startupReady by remember { mutableStateOf(false) }
            var startupError by remember { mutableStateOf<String?>(null) }
            var previousDestination by remember { mutableStateOf<AppDestination>(AppDestination.ExecutePlan) }

            LaunchedEffect(linkSessionState.allSessionsReady) {
                if (linkSessionState.allSessionsReady && sessionsLocked) {
                    sessionsLocked = false
                    selected = previousDestination
                }
            }

            LaunchedEffect(Unit) {
                val deadline = System.currentTimeMillis() + 30_000L
                while (true) {
                    backendError.get()?.let { error ->
                        startupError = "后端启动失败: ${error.message}"
                        return@LaunchedEffect
                    }
                    if (System.currentTimeMillis() > deadline) {
                        startupError = "后端启动超时"
                        return@LaunchedEffect
                    }
                    try {
                        val exists = BeadioClient.configExists()
                        if (!exists) {
                            configLocked = true
                            selected = AppDestination.Settings
                        }
                        startupReady = true
                        break
                    } catch (_: Exception) {
                        delay(1500)
                    }
                }
            }

            LaunchedEffect(settingsVm) {
                settingsVm.configSavedEvent.collect {
                    if (configLocked) {
                        configLocked = false
                        selected = AppDestination.ExecutePlan
                    }
                }
            }

            val onSessionsNotReady: (List<String>) -> Unit = { sites ->
                previousDestination = selected
                sessionsLocked = true
                selected = AppDestination.LinkSession
                linkVm.startSessionsFor(sites)
            }

            val onNavigateToExecute: () -> Unit = {
                executePlanVm.refreshPlans()
                selected = AppDestination.ExecutePlan
            }

            if (!startupReady) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (startupError != null) {
                        Text(
                            text = startupError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        header = {
                            Text(
                                text = "beadio",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        NavigationRailItem(
                            selected = selected is AppDestination.ExecutePlan,
                            onClick = { selected = AppDestination.ExecutePlan },
                            icon = { Text("▶", fontSize = 18.sp) },
                            label = { Text("执行计划") },
                            enabled = !sessionsLocked && !configLocked
                        )
                        NavigationRailItem(
                            selected = selected is AppDestination.CreatePlan,
                            onClick = { selected = AppDestination.CreatePlan },
                            icon = { Text("＋", fontSize = 18.sp) },
                            label = { Text("创建计划") },
                            enabled = !sessionsLocked && !configLocked
                        )
                        NavigationRailItem(
                            selected = selected is AppDestination.LinkSession,
                            onClick = { selected = AppDestination.LinkSession },
                            icon = { Text("◎", fontSize = 18.sp) },
                            label = { Text("链接会话") },
                            enabled = !sessionsLocked && !configLocked
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        NavigationRailItem(
                            selected = selected is AppDestination.Settings,
                            onClick = { selected = AppDestination.Settings },
                            icon = { Text("⚙", fontSize = 18.sp) },
                            label = { Text("配置") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (selected) {
                            AppDestination.ExecutePlan -> ExecutePlanScreen(onSessionsNotReady = onSessionsNotReady, vm = executePlanVm)
                            AppDestination.CreatePlan -> CreatePlanScreen(
                                onSessionsNotReady = onSessionsNotReady,
                                onNavigateToExecute = onNavigateToExecute
                            )
                            AppDestination.LinkSession -> LinkSessionScreen(viewModel = linkVm)
                            AppDestination.Settings -> SettingsScreen(vm = settingsVm)
                        }
                    }
                }
            }
        }
    }
}
