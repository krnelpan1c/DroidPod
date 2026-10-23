package io.github.krnelpan1c.droidpod.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class WheelSensitivity(val label: String, val ticksPerRevolution: Int) {
    Low("Low", 16),
    Medium("Medium", 24),
    High("High", 32),
}

enum class HapticStrength(val label: String) {
    Off("Off"),
    Light("Light"),
    Strong("Strong"),
}

/** How the screen is drawn; independent of the case color. */
enum class SystemStyle(val label: String) {
    Classic("Classic"),
    Retro("Retro"),
}

enum class PodThemeId(val label: String) {
    Classic("White"),
    Black("Black"),
    Red("Red"),
    Silver("Silver"),
}

data class Settings(
    val clickSound: Boolean = true,
    val haptics: HapticStrength = HapticStrength.Light,
    val sensitivity: WheelSensitivity = WheelSensitivity.Medium,
    val theme: PodThemeId = PodThemeId.Classic,
    val style: SystemStyle = SystemStyle.Classic,
    val fullScreen: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showOnLockScreen: Boolean = false,
    /** Players the user added beyond [io.github.krnelpan1c.droidpod.media.SupportedPlayers]. */
    val customPlayers: List<String> = emptyList(),
)

class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    /** Null until the first read from disk, so the UI never flashes default values. */
    val settings: StateFlow<Settings?> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun update(transform: (Settings) -> Settings) {
        scope.launch {
            store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        }
    }

    fun addCustomPlayer(packageName: String) = update { s ->
        if (packageName in s.customPlayers) s else s.copy(customPlayers = s.customPlayers + packageName)
    }

    fun removeCustomPlayer(packageName: String) = update { s ->
        s.copy(customPlayers = s.customPlayers - packageName)
    }

    private companion object {
        val CLICK_SOUND = booleanPreferencesKey("click_sound")
        /** Pre-0.2 on/off haptics; read once to migrate to [HAPTIC_STRENGTH]. */
        val LEGACY_HAPTICS = booleanPreferencesKey("haptics")
        val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val THEME = stringPreferencesKey("theme")
        val STYLE = stringPreferencesKey("system_style")
        val FULL_SCREEN = booleanPreferencesKey("full_screen")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_ON_LOCK_SCREEN = booleanPreferencesKey("show_on_lock_screen")
        /** Pre-0.3 list of recently played apps; no longer used. */
        val LEGACY_RECENT_PLAYERS = stringPreferencesKey("recent_players")
        val CUSTOM_PLAYERS = stringPreferencesKey("custom_players")

        fun Preferences.toSettings(): Settings {
            val defaults = Settings()
            return Settings(
                clickSound = this[CLICK_SOUND] ?: defaults.clickSound,
                haptics = enumOrNull<HapticStrength>(this[HAPTIC_STRENGTH])
                    ?: if (this[LEGACY_HAPTICS] == false) HapticStrength.Off else defaults.haptics,
                sensitivity = enumOrNull<WheelSensitivity>(this[SENSITIVITY]) ?: defaults.sensitivity,
                theme = enumOrNull<PodThemeId>(this[THEME]) ?: defaults.theme,
                style = enumOrNull<SystemStyle>(this[STYLE]) ?: defaults.style,
                fullScreen = this[FULL_SCREEN] ?: defaults.fullScreen,
                keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
                showOnLockScreen = this[SHOW_ON_LOCK_SCREEN] ?: defaults.showOnLockScreen,
                customPlayers = this[CUSTOM_PLAYERS]?.split(',')?.filter { it.isNotBlank() } ?: defaults.customPlayers,
            )
        }

        fun MutablePreferences.write(s: Settings) {
            this[CLICK_SOUND] = s.clickSound
            this[HAPTIC_STRENGTH] = s.haptics.name
            remove(LEGACY_HAPTICS)
            this[SENSITIVITY] = s.sensitivity.name
            this[THEME] = s.theme.name
            this[STYLE] = s.style.name
            this[FULL_SCREEN] = s.fullScreen
            this[KEEP_SCREEN_ON] = s.keepScreenOn
            this[SHOW_ON_LOCK_SCREEN] = s.showOnLockScreen
            this[CUSTOM_PLAYERS] = s.customPlayers.joinToString(",")
            remove(LEGACY_RECENT_PLAYERS)
        }

        inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
            enumValues<T>().firstOrNull { it.name == name }
    }
}
