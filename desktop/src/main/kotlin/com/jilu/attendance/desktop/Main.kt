package com.jilu.attendance.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.time.LocalDate
import java.time.YearMonth

fun main() = application {
    val state = remember { AppState().also { it.load() } }
    val windowState = rememberWindowState(width = 940.dp, height = 640.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "考勤记录",
        state = windowState
    ) {
        window.minimumSize = Dimension(880, 600)

        AppTheme {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (state.settingsOpen && event.key != Key.Escape) return@onPreviewKeyEvent false

                        when (event.key) {
                            Key.Escape -> {
                                state.settingsOpen = false
                                true
                            }

                            Key.DirectionLeft -> {
                                moveSelection(state, -1)
                                true
                            }

                            Key.DirectionRight -> {
                                moveSelection(state, 1)
                                true
                            }

                            Key.DirectionUp -> {
                                moveSelection(state, -7)
                                true
                            }

                            Key.DirectionDown -> {
                                moveSelection(state, 7)
                                true
                            }

                            Key.Spacebar -> {
                                if (!state.settingsOpen) {
                                    val first = state.outcomesFor(state.selected).first()
                                    state.mark(state.selected, first)
                                }
                                true
                            }

                            else -> false
                        }
                    }
            ) {
                BackgroundLayer(state = state, modifier = Modifier.fillMaxSize())
                AppContent(state)
            }
        }
    }
}

private fun moveSelection(state: AppState, days: Long) {
    val next: LocalDate = state.selected.plusDays(days)
    state.selected = next
    state.month = YearMonth.from(next)
}
