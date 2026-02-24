package com.github.jasonskd.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jasonskd.ui.viewmodels.LinkSessionViewModel
import com.github.jasonskd.ui.viewmodels.SessionSiteUiState
import com.github.jasonskd.ui.viewmodels.SessionStatus

@Composable
fun LinkSessionScreen(viewModel: LinkSessionViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "链接会话",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "管理各网站的浏览器会话。在执行计划前，请确保所需网站会话已就绪。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        uiState.sessions.forEach { sessionState ->
            SessionCard(
                sessionState = sessionState,
                onConnect = { viewModel.connectSession(sessionState.site) },
                onRetry = { viewModel.retrySession(sessionState.site) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val anyActiveSession = uiState.sessions.any {
            it.status == SessionStatus.Ready || it.status == SessionStatus.Connecting
        }
        OutlinedButton(
            onClick = { viewModel.disconnectAll() },
            enabled = anyActiveSession,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("断开所有会话")
        }
    }
}

@Composable
private fun SessionCard(
    sessionState: SessionSiteUiState,
    onConnect: () -> Unit,
    onRetry: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionState.site.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = sessionState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (sessionState.status) {
                SessionStatus.Idle -> {
                    Button(onClick = onConnect) {
                        Text("连接")
                    }
                }
                SessionStatus.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("连接中...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                SessionStatus.Ready -> {
                    Text(
                        text = "✓ 已就绪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SessionStatus.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✕ 失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRetry) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}
