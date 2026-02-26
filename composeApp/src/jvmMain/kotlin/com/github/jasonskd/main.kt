package com.github.jasonskd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.system.exitProcess

fun main() {
    Thread({
        val engineMain = Class.forName("io.ktor.server.netty.EngineMain")
        engineMain.getMethod("main", Array<String>::class.java).invoke(null, emptyArray<String>())
    }, "backend-server").apply { isDaemon = true }.start()

    val shutdownScope = CoroutineScope(Dispatchers.IO)

    application {
        var isVisible by remember { mutableStateOf(true) }

        Window(
            onCloseRequest = {
                isVisible = false  // 立即隐藏窗口
                shutdownScope.launch {
                    try {
                        withTimeout(5000L) { BeadioClient.breakBackend() }
                    } catch (_: Exception) {}
                    exitProcess(0)  // 兜底退出（/break 成功时后端已 exitProcess）
                }
            },
            visible = isVisible,
            title = "Beadio",
            state = rememberWindowState(width = 960.dp, height = 680.dp)
        ) {
            App()
        }
    }
}
