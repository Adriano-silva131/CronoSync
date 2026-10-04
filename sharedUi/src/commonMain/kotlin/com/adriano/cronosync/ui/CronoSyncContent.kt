package com.adriano.cronosync.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adriano.cronosync.ui.pomodoro.PomodoroRoute
import com.adriano.cronosync.ui.resources.Res
import com.adriano.cronosync.ui.resources.tab_pomodoro
import com.adriano.cronosync.ui.resources.tab_stopwatch
import com.adriano.cronosync.ui.resources.tab_timer
import com.adriano.cronosync.ui.stopwatch.StopwatchRoute
import com.adriano.cronosync.ui.sync.RoomBarRoute
import com.adriano.cronosync.ui.timer.TimerRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class Mode(val label: StringResource) {
    Stopwatch(Res.string.tab_stopwatch),
    Timer(Res.string.tab_timer),
    Pomodoro(Res.string.tab_pomodoro),
}

private val SideBySideMinWidth = 1200.dp

@Composable
fun CronoSyncContent(
    modifier: Modifier = Modifier,
    timer: @Composable (Modifier) -> Unit = { TimerRoute(modifier = it) },
    pomodoro: @Composable (Modifier) -> Unit = { PomodoroRoute(modifier = it) },
) {
    var selected by rememberSaveable { mutableStateOf(Mode.Stopwatch) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= SideBySideMinWidth

        Scaffold(
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    if (!sideBySide) {
                        PrimaryTabRow(selectedTabIndex = selected.ordinal) {
                            Mode.entries.forEach { mode ->
                                Tab(
                                    selected = mode == selected,
                                    onClick = { selected = mode },
                                    text = { Text(stringResource(mode.label)) },
                                )
                            }
                        }
                    }
                    RoomBarRoute()
                }
            },
        ) { innerPadding ->
            val content = Modifier.padding(innerPadding)
            if (sideBySide) {
                Row(modifier = content.fillMaxSize()) {
                    StopwatchRoute(modifier = Modifier.weight(1f))
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    timer(Modifier.weight(1f))
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    pomodoro(Modifier.weight(1f))
                }
            } else {
                when (selected) {
                    Mode.Stopwatch -> StopwatchRoute(modifier = content)
                    Mode.Timer -> timer(content)
                    Mode.Pomodoro -> pomodoro(content)
                }
            }
        }
    }
}
