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
import com.hnmlabs.sandblock.model.GameMode
import com.hnmlabs.sandblock.ui.screens.AdventureMapScreen
import com.hnmlabs.sandblock.ui.screens.GameScreen
import com.hnmlabs.sandblock.ui.screens.MainMenuScreen

private sealed interface AppRoute {
    data object Menu : AppRoute
    data object AdventureMap : AppRoute
    data class PlayMode(val mode: GameMode, val resumeClassic: Boolean = false) : AppRoute
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
            it.updateMusicPreference(prefs.musicEnabled)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Menu) }
                var themeState by remember { mutableStateOf(prefs.selectedTheme) }
                var soundState by remember { mutableStateOf(prefs.soundEnabled) }
                var musicState by remember { mutableStateOf(prefs.musicEnabled) }
                var hapticsState by remember { mutableStateOf(prefs.hapticsEnabled) }
                var refreshKey by remember { mutableIntStateOf(0) }

                when (val current = route) {
                    AppRoute.Menu -> {
                        val best = remember(refreshKey) { prefs.highScore }
                        val timeRushBest = remember(refreshKey) { prefs.timeRushHighScore }
                        val sandtrixBest = remember(refreshKey) { prefs.sandtrixHighScore }
                        val savedScore = remember(refreshKey) { prefs.getSavedClassicScore() }
                        val coins = remember(refreshKey) { prefs.coins }
                        val stars = remember(refreshKey) { prefs.totalStarsEarned() }
                        val unlocked = remember(refreshKey) { prefs.unlockedLevel }
                        val unclaimed = remember(refreshKey) { prefs.countUnclaimedRewards() }

                        MainMenuScreen(
                            theme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            highScore = best,
                            timeRushHighScore = timeRushBest,
                            sandtrixHighScore = sandtrixBest,
                            savedClassicScore = savedScore,
                            coins = coins,
                            totalStars = stars,
                            unlockedLevel = unlocked,
                            unclaimedCount = unclaimed,
                            soundEnabled = soundState,
                            musicEnabled = musicState,
                            hapticsEnabled = hapticsState,
                            onToggleSound = {
                                val next = !soundState
                                soundState = next
                                prefs.soundEnabled = next
                                soundEngine.soundEnabled = next
                                if (next) soundEngine.playTap()
                            },
                            onToggleMusic = {
                                val next = !musicState
                                musicState = next
                                prefs.musicEnabled = next
                                soundEngine.updateMusicPreference(next)
                                soundEngine.playTap()
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
                            onResumeClassic = {
                                soundEngine.playTap()
                                route = AppRoute.PlayMode(GameMode.CLASSIC, resumeClassic = true)
                            },
                            onStartMode = { chosenMode ->
                                soundEngine.playTap()
                                route = AppRoute.PlayMode(chosenMode, resumeClassic = false)
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

                    is AppRoute.PlayMode -> {
                        GameScreen(
                            initialMode = current.mode,
                            initialLevel = null,
                            resumeClassic = current.resumeClassic,
                            selectedTheme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            onExitToMenu = {
                                soundState = prefs.soundEnabled
                                musicState = prefs.musicEnabled
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
                            initialMode = GameMode.ADVENTURE,
                            initialLevel = current.level,
                            resumeClassic = false,
                            selectedTheme = themeState,
                            prefs = prefs,
                            soundEngine = soundEngine,
                            onExitToMenu = {
                                soundState = prefs.soundEnabled
                                musicState = prefs.musicEnabled
                                hapticsState = prefs.hapticsEnabled
                                refreshKey++
                                route = AppRoute.Menu
                            },
                            onExitToAdventureMap = {
                                soundState = prefs.soundEnabled
                                musicState = prefs.musicEnabled
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

    override fun onResume() {
        super.onResume()
        if (::soundEngine.isInitialized) {
            soundEngine.onAppResumed()
        }
    }

    override fun onPause() {
        if (::soundEngine.isInitialized) {
            soundEngine.onAppPaused()
        }
        super.onPause()
    }

    override fun onDestroy() {
        soundEngine.release()
        super.onDestroy()
    }
}
