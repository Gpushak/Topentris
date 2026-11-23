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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.topentris.game.MatchSave
import com.topentris.game.SettingsState
import kotlinx.coroutines.launch

private enum class Page { MENU, GAME, SETTINGS, RECORDS }

private data class Session(
    val rules: GameConfig,
    val controls: ControlConfig,
    val scorePopups: Boolean,
    val splitRotate: Boolean,
    val showButtons: Boolean,
    val id: Int,
    val resume: MatchSave? = null,
)

@Composable
fun TopentrisApp(repository: SettingsRepository) {
    val settingsState by repository.settings.collectAsState(initial = SettingsState())
    val settings = settingsState.settings
    val saved by repository.savedMatch.collectAsState(initial = null)
    var page by remember { mutableStateOf(Page.MENU) }
    var session by remember { mutableStateOf<Session?>(null) }
    var nextId by remember { mutableIntStateOf(1) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(settings.music, settings.musicVolume) {
        soundtrack.setVolume(settings.musicVolume / 100f)
        soundtrack.setEnabled(settings.music)
    }
    LaunchedEffect(settings.sfxVolume) {
        effects.setVolume(settings.sfxVolume / 100f)
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (page) {
            Page.MENU -> MenuScreen(
                onPlay = {
                    scope.launch { repository.clearMatch() }
                    session = Session(
                        rules = settings.rules,
                        controls = settings.controls,
                        scorePopups = settings.scorePopups,
                        splitRotate = settings.splitRotate,
                        showButtons = settings.showButtons,
                        id = nextId,
                    )
                    nextId += 1
                    page = Page.GAME
                },
                onContinue = saved?.let { save ->
                    {
                        session = Session(
                            rules = save.rules,
                            controls = save.controls,
                            scorePopups = settings.scorePopups,
                            splitRotate = settings.splitRotate,
                            showButtons = settings.showButtons,
                            id = nextId,
                            resume = save,
                        )
                        nextId += 1
                        page = Page.GAME
                    }
                },
                onSettings = { page = Page.SETTINGS },
                onRecords = { page = Page.RECORDS },
            )
            Page.GAME -> {
                val current = session
                if (current == null) {
                    MenuScreen(
                        onPlay = { page = Page.MENU },
                        onContinue = null,
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
                        splitRotate = current.splitRotate,
                        showButtons = current.showButtons,
                        resume = current.resume,
                        onExit = { page = Page.MENU },
                        onRestart = {
                            scope.launch { repository.clearMatch() }
                            session = current.copy(id = nextId, resume = null)
                            nextId += 1
                        },
                    )
                }
            }
            Page.SETTINGS -> SettingsScreen(
                state = settingsState,
                edit = { transform -> scope.launch { repository.update(transform) } },
                onSelectBuiltIn = { preset -> scope.launch { repository.selectBuiltIn(preset) } },
                onSelectSaved = { name -> scope.launch { repository.selectSavedPreset(name) } },
                onSave = { name -> scope.launch { repository.savePreset(name) } },
                onDelete = { name -> scope.launch { repository.deletePreset(name) } },
                onBack = { page = Page.MENU },
            )
            Page.RECORDS -> RecordsScreen(
                repository = repository,
                onBack = { page = Page.MENU },
            )
        }
    }
}
