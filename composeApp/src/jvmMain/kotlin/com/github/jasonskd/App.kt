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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.jasonskd.ui.screens.CreatePlanScreen
import com.github.jasonskd.ui.screens.ExecutePlanScreen
import com.github.jasonskd.ui.screens.LinkSessionScreen
import com.github.jasonskd.ui.screens.SettingsScreen
import com.github.jasonskd.ui.theme.BeadioTheme
import com.github.jasonskd.ui.viewmodels.ExecutePlanViewModel
import com.github.jasonskd.ui.viewmodels.LinkSessionViewModel

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
            val linkSessionState by linkVm.uiState.collectAsStateWithLifecycle()
            var selected by remember { mutableStateOf<AppDestination>(AppDestination.ExecutePlan) }
            var sessionsLocked by remember { mutableStateOf(false) }
            var previousDestination by remember { mutableStateOf<AppDestination>(AppDestination.ExecutePlan) }

            LaunchedEffect(linkSessionState.allSessionsReady) {
                if (linkSessionState.allSessionsReady && sessionsLocked) {
                    sessionsLocked = false
                    selected = previousDestination
                }
            }

            val onSessionsNotReady: (List<String>) -> Unit = { sites ->
                previousDestination = selected
                sessionsLocked = true
                selected = AppDestination.LinkSession
                linkVm.startSessionsFor(sites)
            }

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
                        enabled = !sessionsLocked
                    )
                    NavigationRailItem(
                        selected = selected is AppDestination.CreatePlan,
                        onClick = { selected = AppDestination.CreatePlan },
                        icon = { Text("＋", fontSize = 18.sp) },
                        label = { Text("创建计划") },
                        enabled = !sessionsLocked
                    )
                    NavigationRailItem(
                        selected = selected is AppDestination.LinkSession,
                        onClick = { selected = AppDestination.LinkSession },
                        icon = { Text("◎", fontSize = 18.sp) },
                        label = { Text("链接会话") }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    NavigationRailItem(
                        selected = selected is AppDestination.Settings,
                        onClick = { selected = AppDestination.Settings },
                        icon = { Text("⚙", fontSize = 18.sp) },
                        label = { Text("配置") },
                        enabled = !sessionsLocked
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selected) {
                        AppDestination.ExecutePlan -> ExecutePlanScreen(onSessionsNotReady = onSessionsNotReady, vm = executePlanVm)
                        AppDestination.CreatePlan -> CreatePlanScreen(onSessionsNotReady = onSessionsNotReady)
                        AppDestination.LinkSession -> LinkSessionScreen(viewModel = linkVm)
                        AppDestination.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}
