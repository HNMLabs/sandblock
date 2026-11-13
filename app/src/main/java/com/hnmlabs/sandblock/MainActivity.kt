package com.hnmlabs.sandblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.data.GamePreferences
import com.hnmlabs.sandblock.model.AdventureLevel
import com.hnmlabs.sandblock.ui.screens.AdventureMapScreen
import com.hnmlabs.sandblock.ui.screens.GameScreen
import com.hnmlabs.sandblock.ui.screens.MainMenuScreen

private sealed interface AppRoute {
    data object Menu : AppRoute
    data object AdventureMap : AppRoute
    data object PlayClassic : AppRoute
    data class PlayAdventure(val level: AdventureLevel) : AppRoute
}

class MainActivity : ComponentActivity() {

    private lateinit var prefs: GamePreferences
    private lateinit var soundEngine: AsmrSoundEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        prefs = GamePreferences(applicationContext)
        soundEngine = AsmrSoundEngine(applicationContext).also {
            it.soundEnabled = prefs.soundEnabled
            it.hapticsEnabled = prefs.hapticsEnabled
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Menu) }
                var themeState by remember { mutableStateOf(prefs.selectedTheme) }
                var soundState by remember { mutableStateOf(prefs.soundEnabled) }
                var hapticsState by remember { mutableStateOf(prefs.hapticsEnabled) }
                var refreshKey by remember { mutableIntStateOf(0) }

                when (val current = route) {
                    AppRoute.Menu -> {
                        val best = remember(refreshKey) { prefs.highScore }
                        val coins = remember(refreshKey) { prefs.coins }
                        val stars = remember(refreshKey) { prefs.totalStarsEarned() }
                        val unlocked = remember(refreshKey) { prefs.unlockedLevel }

                        MainMenuScreen(
                            theme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            highScore = best,
                            coins = coins,
                            totalStars = stars,
                            unlockedLevel = unlocked,
                            soundEnabled = soundState,
                            hapticsEnabled = hapticsState,
                            onToggleSound = {
                                val next = !soundState
                                soundState = next
                                prefs.soundEnabled = next
                                soundEngine.soundEnabled = next
                                if (next) soundEngine.playTap()
                            },
                            onToggleHaptics = {
                                val next = !hapticsState
                                hapticsState = next
                                prefs.hapticsEnabled = next
                                soundEngine.hapticsEnabled = next
                                soundEngine.playTap()
                            },
                            onSelectTheme = { chosen ->
                                themeState = chosen
                                prefs.selectedTheme = chosen
                                soundEngine.playTap()
                            },
                            onCoinsChanged = {
                                refreshKey++
                            },
                            onStartClassic = {
                                soundEngine.playTap()
                                route = AppRoute.PlayClassic
                            },
                            onOpenAdventure = {
                                soundEngine.playTap()
                                route = AppRoute.AdventureMap
                            }
                        )
                    }

                    AppRoute.AdventureMap -> {
                        val unlocked = remember(refreshKey) { prefs.unlockedLevel }
                        val totalStars = remember(refreshKey) { prefs.totalStarsEarned() }

                        AdventureMapScreen(
                            theme = themeState,
                            unlockedLevel = unlocked,
                            totalStars = totalStars,
                            getStarsForLevel = { lvlNum -> prefs.getLevelStars(lvlNum) },
                            onSelectLevel = { chosenLevel ->
                                soundEngine.playTap()
                                route = AppRoute.PlayAdventure(chosenLevel)
                            },
                            onBack = {
                                soundEngine.playTap()
                                refreshKey++
                                route = AppRoute.Menu
                            }
                        )
                    }

                    AppRoute.PlayClassic -> {
                        GameScreen(
                            initialLevel = null,
                            selectedTheme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            onExitToMenu = {
                                soundState = prefs.soundEnabled
                                hapticsState = prefs.hapticsEnabled
                                refreshKey++
                                route = AppRoute.Menu
                            },
                            onExitToAdventureMap = {
                                refreshKey++
                                route = AppRoute.AdventureMap
                            }
                        )
                    }

                    is AppRoute.PlayAdventure -> {
                        GameScreen(
                            initialLevel = current.level,
                            selectedTheme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            onExitToMenu = {
                                soundState = prefs.soundEnabled
                                hapticsState = prefs.hapticsEnabled
                                refreshKey++
                                route = AppRoute.Menu
                            },
                            onExitToAdventureMap = {
                                soundState = prefs.soundEnabled
                                hapticsState = prefs.hapticsEnabled
                                refreshKey++
                                route = AppRoute.AdventureMap
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        soundEngine.release()
        super.onDestroy()
    }
}
