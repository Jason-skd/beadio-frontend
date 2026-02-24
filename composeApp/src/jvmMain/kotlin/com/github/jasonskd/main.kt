import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.jasonskd.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Beadio",
        state = rememberWindowState(width = 960.dp, height = 680.dp)
    ) {
        App()
    }
}
