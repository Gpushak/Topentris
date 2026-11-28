package com.topentris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    @Test
    fun revertingARuleRestoresEveryBuiltInPreset() {
        for (preset in Preset.builtIns) {
            val original = GameConfig.of(preset)
            val changed = original.copy(ghost = !original.ghost).detectPreset()
            assertEquals(Preset.CUSTOM, changed.preset)
            assertEquals(original, changed.copy(ghost = original.ghost).detectPreset())
        }
    }

    @Test
    fun equivalentRulesSwitchDirectlyToAnotherBuiltIn() {
        val guideline = GameConfig.author().copy(generator = Generator.BAG, queueSize = 5).detectPreset()
        assertEquals(Preset.GUIDELINE, SettingsState(Settings(rules = guideline)).activeBuiltIn)
    }

    @Test
    fun settingsRecognizeASavedPresetAfterRevertingAControlOrDisplayChange() {
        val custom = Settings(controls = ControlConfig(dasMs = 170), showButtons = false)
        val saved = SavedPreset("Мой набор", custom.configuration)

        assertNull(SettingsState(custom.copy(controls = ControlConfig()), listOf(saved)).activeSavedPreset)
        val changed = SettingsState(custom.copy(scorePopups = false), listOf(saved))
        assertNull(changed.activeSavedPreset)
        assertNull(changed.activeBuiltIn)
        assertEquals(saved, changed.copy(settings = custom).activeSavedPreset)
    }

    @Test
    fun audioAndPrikolModeStayIndependentOfPresets() {
        val settings = Settings(music = false, musicVolume = 24, sfxVolume = 62, prikolMode = true)
        val configuration = GameplaySettings(GameConfig.nes(), scorePopups = false)
        val saved = SavedPreset("NES без всплывающих очков", configuration)
        val applied = settings.applyConfiguration(configuration)

        assertEquals(Preset.AUTHOR, SettingsState(settings).activeBuiltIn)
        assertEquals(saved, SettingsState(applied, listOf(saved)).activeSavedPreset)
        assertEquals(settings.copy(rules = GameConfig.nes(), scorePopups = false), applied)
    }

    @Test
    fun visualStyleStaysIndependentOfBuiltInAndSavedPresets() {
        val settings = Settings(visualStyle = VisualStyle.LCD, splitRotate = false, showButtons = false)
        assertEquals(Preset.AUTHOR, SettingsState(settings).activeBuiltIn)

        val configuration = GameplaySettings(GameConfig.nes(), scorePopups = false)
        val saved = SavedPreset("NES без всплывающих очков", configuration)
        val applied = settings.applyConfiguration(configuration)
        assertEquals(false, applied.splitRotate)
        assertEquals(false, applied.showButtons)
        assertEquals(VisualStyle.LCD, applied.visualStyle)
        assertEquals(saved, SettingsState(applied.copy(visualStyle = VisualStyle.NEON, splitRotate = true, showButtons = true), listOf(saved)).activeSavedPreset)
    }

    @Test
    fun explicitSavedSelectionTakesPrecedenceOverAnIdenticalBuiltIn() {
        val settings = Settings()
        val saved = SavedPreset("Мой авторский", settings.configuration)
        val state = SettingsState(settings, listOf(saved))

        assertEquals(Preset.AUTHOR, state.activeBuiltIn)
        assertNull(state.activeSavedPreset)

        val selected = state.copy(preferredPresetName = saved.name)
        assertEquals(saved, selected.activeSavedPreset)
        assertNull(selected.activeBuiltIn)
    }
}
