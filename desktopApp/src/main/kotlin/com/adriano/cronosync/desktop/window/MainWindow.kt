package com.adriano.cronosync.desktop.window

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import com.adriano.cronosync.ui.CronoSyncContent
import kotlinx.coroutines.flow.StateFlow
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun MainWindow(
    title: String,
    icon: Painter,
    state: WindowState,
    visible: Boolean,
    systemIsDark: StateFlow<Boolean?>,
    onFocused: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    val detected by systemIsDark.collectAsState()
    Window(
        onCloseRequest = onCloseRequest,
        title = title,
        icon = icon,
        state = state,
        visible = visible,
    ) {
        window.minimumSize = Dimension(380, 560)
        LaunchedEffect(visible) {
            if (visible) window.toFront()
        }
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) = onFocused()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        DesktopTheme(dark = detected ?: isSystemInDarkTheme()) {
            CronoSyncContent()
        }
    }
}

@Composable
private fun DesktopTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
