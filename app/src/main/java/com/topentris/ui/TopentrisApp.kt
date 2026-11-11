package com.topentris.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.topentris.audio.Effects
import com.topentris.audio.Soundtrack
import com.topentris.data.SettingsRepository
import com.topentris.game.ControlConfig
import com.topentris.game.GameConfig
import com.topentris.game.Settings

private enum class Page { MENU, GAME, SETTINGS, RECORDS }

private data class Session(
    val rules: GameConfig,
    val controls: ControlConfig,
    val scorePopups: Boolean,
    val id: Int,
)

@Composable
fun TopentrisApp(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState(initial = Settings())
    var page by remember { mutableStateOf(Page.MENU) }
    var session by remember { mutableStateOf<Session?>(null) }
    var nextId by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val soundtrack = remember { Soundtrack(context.applicationContext) }
    val effects = remember { Effects(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(soundtrack, effects) {
        onDispose {
            soundtrack.release()
            effects.release()
        }
    }
    DisposableEffect(lifecycleOwner, soundtrack) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> soundtrack.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> soundtrack.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        soundtrack.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        repository.applyAuthorDefaultOnce()
    }
    LaunchedEffect(settings.music, settings.volume) {
        soundtrack.setEnabled(settings.music)
        soundtrack.setVolume(settings.volume / 100f)
        effects.setVolume(settings.volume / 100f)
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (page) {
            Page.MENU -> MenuScreen(
                onPlay = {
                    session = Session(settings.rules, settings.controls, settings.scorePopups, nextId)
                    nextId += 1
                    page = Page.GAME
                },
                onSettings = { page = Page.SETTINGS },
                onRecords = { page = Page.RECORDS },
            )
            Page.GAME -> {
                val current = session
                if (current == null) {
                    MenuScreen(
                        onPlay = { page = Page.MENU },
                        onSettings = { page = Page.SETTINGS },
                        onRecords = { page = Page.RECORDS },
                    )
                } else {
                    GameScreen(
                        rules = current.rules,
                        controls = current.controls,
                        sessionId = current.id,
                        repository = repository,
                        soundtrack = soundtrack,
                        effects = effects,
                        scorePopups = current.scorePopups,
                        onExit = { page = Page.MENU },
                        onRestart = {
                            session = current.copy(id = nextId)
                            nextId += 1
                        },
                    )
                }
            }
            Page.SETTINGS -> SettingsScreen(
                repository = repository,
                onBack = { page = Page.MENU },
            )
            Page.RECORDS -> RecordsScreen(
                repository = repository,
                onBack = { page = Page.MENU },
            )
        }
    }
}
