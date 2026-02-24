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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jasonskd.ui.screens.CreatePlanScreen
import com.github.jasonskd.ui.screens.ExecutePlanScreen
import com.github.jasonskd.ui.screens.LinkSessionScreen
import com.github.jasonskd.ui.screens.SettingsScreen
import com.github.jasonskd.ui.theme.BeadioTheme

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
            var selected by remember { mutableStateOf<AppDestination>(AppDestination.ExecutePlan) }

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
                        label = { Text("执行计划") }
                    )
                    NavigationRailItem(
                        selected = selected is AppDestination.CreatePlan,
                        onClick = { selected = AppDestination.CreatePlan },
                        icon = { Text("＋", fontSize = 18.sp) },
                        label = { Text("创建计划") }
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
                        label = { Text("配置") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selected) {
                        AppDestination.ExecutePlan -> ExecutePlanScreen()
                        AppDestination.CreatePlan -> CreatePlanScreen()
                        AppDestination.LinkSession -> LinkSessionScreen()
                        AppDestination.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}