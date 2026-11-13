package com.hnmlabs.sandblock.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.data.GamePreferences
import com.hnmlabs.sandblock.engine.GameStatus
import com.hnmlabs.sandblock.engine.SandWorld
import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.AdventureLevel
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.ui.stage.HudSnapshot
import com.hnmlabs.sandblock.ui.stage.SandStageView

@Composable
fun GameScreen(
    initialLevel: AdventureLevel?,
    selectedTheme: GameTheme,
    prefs: GamePreferences,
    soundEngine: AsmrSoundEngine,
    onExitToMenu: () -> Unit,
    onExitToAdventureMap: () -> Unit
) {
    var currentLevel by remember { mutableStateOf(initialLevel) }
    var sessionKey by remember { mutableIntStateOf(1) }

    val world = remember {
        SandWorld().also { w ->
            if (initialLevel != null) {
                w.startAdventure(initialLevel)
            } else {
                w.startClassic(selectedTheme)
            }
        }
    }

    var stageRef by remember { mutableStateOf<SandStageView?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    var activeTargetBooster by remember { mutableStateOf<BoosterType?>(null) }
    var inventoryTick by remember { mutableIntStateOf(0) }

    var hud by remember {
        mutableStateOf(
            HudSnapshot(
                score = 0,
                linesCleared = 0,
                combo = 0,
                movesRemaining = initialLevel?.maxMoves,
                crackedRemaining = if (initialLevel?.requireClearCrackedStones == true) {
                    world.countRemainingCrackedStones()
                } else 0,
                status = GameStatus.PLAYING,
                wonStars = 0
            )
        )
    }

    var sessionFinalized by remember(sessionKey) { mutableStateOf(false) }
    var wonStarsDisplay by remember(sessionKey) { mutableIntStateOf(0) }
    var earnedSessionCoins by remember(sessionKey) { mutableIntStateOf(0) }
    var isNewHighScore by remember(sessionKey) { mutableStateOf(false) }

    fun restartGame(nextLevel: AdventureLevel? = currentLevel) {
        currentLevel = nextLevel
        activeTargetBooster = null
        isPaused = false
        if (nextLevel != null) {
            world.startAdventure(nextLevel)
        } else {
            world.startClassic(selectedTheme)
        }
        stageRef?.activeTargetBooster = null
        stageRef?.isGamePaused = false
        stageRef?.resetCachedHud()
        sessionKey++
        hud = HudSnapshot(
            score = 0,
            linesCleared = 0,
            combo = 0,
            movesRemaining = nextLevel?.maxMoves,
            crackedRemaining = if (nextLevel?.requireClearCrackedStones == true) {
                world.countRemainingCrackedStones()
            } else 0,
            status = GameStatus.PLAYING,
            wonStars = 0
        )
    }

    LaunchedEffect(hud.status, sessionKey) {
        if (!sessionFinalized && hud.status != GameStatus.PLAYING) {
            sessionFinalized = true
            activeTargetBooster = null
            stageRef?.activeTargetBooster = null

            val lvl = currentLevel
            if (hud.status == GameStatus.WON && lvl != null) {
                val stars = hud.wonStars.coerceIn(1, 3)
                wonStarsDisplay = stars
                val firstClear = prefs.recordLevelWin(
                    levelNumber = lvl.number,
                    stars = stars,
                    rewardCoins = lvl.rewardCoins,
                    rewardBooster = lvl.rewardBooster
                )
                earnedSessionCoins = if (firstClear) lvl.rewardCoins else (lvl.rewardCoins / 3).coerceAtLeast(10)
                inventoryTick++
            } else if (hud.status == GameStatus.GAME_OVER) {
                if (lvl == null) {
                    if (hud.score > prefs.highScore) {
                        isNewHighScore = true
                        prefs.highScore = hud.score
                    }
                    val bonusCoins = (hud.score / 55).coerceAtLeast(if (hud.score >= 150) 5 else 0)
                    if (bonusCoins > 0) {
                        prefs.coins += bonusCoins
                        earnedSessionCoins = bonusCoins
                        inventoryTick++
                    }
                }
            }
        }
    }

    BackHandler {
        if (activeTargetBooster != null) {
            activeTargetBooster = null
            stageRef?.activeTargetBooster = null
        } else if (!isPaused && hud.status == GameStatus.PLAYING) {
            isPaused = true
            stageRef?.isGamePaused = true
        } else {
            if (currentLevel != null) onExitToAdventureMap() else onExitToMenu()
        }
    }

    val activeTheme = world.theme
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(activeTheme.bgTop), Color(activeTheme.bgBottom))
    )
    val accent = Color(activeTheme.accentColor)
    val cardColor = Color(activeTheme.surfaceCard)

    // Read current inventory reactive to inventoryTick
    val currentCoins = remember(inventoryTick, hud.score) { prefs.coins }
    val currentBest = remember(hud.score, sessionKey) {
        maxOf(prefs.highScore, if (currentLevel == null) hud.score else 0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .systemBarsPadding()
    ) {
        // 1. Top Game HUD Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause button
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        soundEngine.playTap()
                        isPaused = true
                        stageRef?.isGamePaused = true
                    },
                color = cardColor,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "⏸", fontSize = 16.sp, color = Color.White)
                }
            }

            // Score & Mode Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val lvl = currentLevel
                if (lvl == null) {
                    Text(
                        text = "${hud.score}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "🏆 REKOR: $currentBest  •  HAT: ${hud.linesCleared}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                } else {
                    Text(
                        text = "BÖLÜM ${lvl.number}: ${lvl.title.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "${hud.score} / ${lvl.targetScore}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            // Coins & Combo Pill
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = cardColor,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "🪙 $currentCoins",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                }
                if (hud.combo >= 2) {
                    Text(
                        text = "🔥 x${hud.combo}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFEA00)
                    )
                }
            }
        }

        // 2. Adventure Progress Strip (only in Adventure Mode)
        currentLevel?.let { lvl ->
            val progress = (hud.score.toFloat() / lvl.targetScore.coerceAtLeast(1)).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hat: ${hud.linesCleared}/${lvl.targetClears}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (lvl.requireClearCrackedStones) {
                        Text(
                            text = "🪨 Çatlak Taş: ${hud.crackedRemaining}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB703)
                        )
                    }
                    hud.movesRemaining?.let { rem ->
                        Text(
                            text = "Hamle: $rem",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rem <= 5) Color(0xFFFF6B6B) else accent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }
        }

        // 3. Interactive Booster Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoosterType.entries.forEach { booster ->
                val count = remember(inventoryTick) { prefs.getBoosterCount(booster) }
                val isSelected = activeTargetBooster == booster
                val canAfford = count > 0 || currentCoins >= booster.coinCost

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = hud.status == GameStatus.PLAYING) {
                            if (isSelected) {
                                activeTargetBooster = null
                                stageRef?.activeTargetBooster = null
                                soundEngine.playTap()
                                return@clickable
                            }
                            if (!canAfford) return@clickable

                            if (booster == BoosterType.RAINBOW_UPGRADE) {
                                val upgradedSlot = world.upgradeTrayPieceToRainbow()
                                if (upgradedSlot >= 0 && prefs.consumeOrBuyBooster(booster)) {
                                    activeTargetBooster = null
                                    stageRef?.activeTargetBooster = null
                                    soundEngine.playMagic()
                                    inventoryTick++
                                    stageRef?.invalidate()
                                }
                            } else {
                                activeTargetBooster = booster
                                stageRef?.activeTargetBooster = booster
                                soundEngine.playTap()
                            }
                        },
                    color = if (isSelected) accent.copy(alpha = 0.30f) else cardColor.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else Color.White.copy(alpha = if (canAfford) 0.22f else 0.08f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${booster.emoji} ${booster.titleTr}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = if (canAfford) 1f else 0.4f),
                            maxLines = 1
                        )
                        val badgeText = if (count > 0) "x$count" else "🪙${booster.coinCost}"
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (count > 0) accent else Color(0xFFFFD166).copy(alpha = if (canAfford) 0.9f else 0.4f)
                        )
                    }
                }
            }
        }

        // 4. Custom 60FPS Sand Board + 3-Piece Tray View
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                SandStageView(
                    context = ctx,
                    world = world,
                    soundEngine = soundEngine,
                    onHudUpdated = { snapshot ->
                        hud = snapshot
                    },
                    onTargetBoosterExecuted = { usedBooster ->
                        prefs.consumeOrBuyBooster(usedBooster)
                        activeTargetBooster = null
                        inventoryTick++
                    }
                ).also { view ->
                    stageRef = view
                }
            },
            update = { view ->
                view.isGamePaused = isPaused
                view.activeTargetBooster = activeTargetBooster
            }
        )
    }

    // Pause Overlay Dialog
    if (isPaused && hud.status == GameStatus.PLAYING) {
        PauseDialog(
            theme = activeTheme,
            soundEnabled = prefs.soundEnabled,
            hapticsEnabled = prefs.hapticsEnabled,
            onToggleSound = {
                val next = !prefs.soundEnabled
                prefs.soundEnabled = next
                soundEngine.soundEnabled = next
                inventoryTick++
            },
            onToggleHaptics = {
                val next = !prefs.hapticsEnabled
                prefs.hapticsEnabled = next
                soundEngine.hapticsEnabled = next
                inventoryTick++
            },
            onResume = {
                isPaused = false
                stageRef?.isGamePaused = false
            },
            onRestart = { restartGame(currentLevel) },
            onQuit = {
                if (currentLevel != null) onExitToAdventureMap() else onExitToMenu()
            }
        )
    }

    // Level Victory Dialog
    if (hud.status == GameStatus.WON && currentLevel != null) {
        val lvl = currentLevel!!
        VictoryDialog(
            level = lvl,
            score = hud.score,
            stars = wonStarsDisplay.coerceAtLeast(1),
            earnedCoins = earnedSessionCoins,
            onNextLevel = if (lvl.number < AdventureCatalog.levels.size) {
                { restartGame(AdventureCatalog.getLevel(lvl.number + 1)) }
            } else null,
            onReplay = { restartGame(lvl) },
            onMap = onExitToAdventureMap
        )
    }

    // Game Over Dialog
    if (hud.status == GameStatus.GAME_OVER) {
        GameOverDialog(
            theme = activeTheme,
            score = hud.score,
            bestScore = prefs.highScore,
            isNewBest = isNewHighScore,
            earnedCoins = earnedSessionCoins,
            isAdventure = currentLevel != null,
            onRetry = { restartGame(currentLevel) },
            onExit = {
                if (currentLevel != null) onExitToAdventureMap() else onExitToMenu()
            }
        )
    }
}

@Composable
private fun PauseDialog(
    theme: GameTheme,
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleHaptics: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit
) {
    val accent = Color(theme.accentColor)
    Dialog(onDismissRequest = onResume) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(theme.surfaceCard)),
            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⏸ OYUN DURAKLATILDI",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleSound,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (soundEnabled) "🔊 Ses Açık" else "🔇 Ses Kapalı",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleHaptics,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (hapticsEnabled) "📳 Titreşim Açık" else "📴 Kapalı",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("▶ Devam Et", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🔄 Yeniden Başlat", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onQuit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("🏠 Çıkış Yap", color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun VictoryDialog(
    level: AdventureLevel,
    score: Int,
    stars: Int,
    earnedCoins: Int,
    onNextLevel: (() -> Unit)?,
    onReplay: () -> Unit,
    onMap: () -> Unit
) {
    val accent = Color(level.theme.accentColor)
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(level.theme.surfaceCard)),
            border = BorderStroke(2.dp, accent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎉 BÖLÜM TAMAMLANDI!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    textAlign = TextAlign.Center
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 1..3) {
                        Text(
                            text = "★",
                            fontSize = 38.sp,
                            color = if (i <= stars) Color(0xFFFFD166) else Color.White.copy(alpha = 0.2f)
                        )
                    }
                }

                Text(
                    text = "Toplam Skor: $score",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = buildString {
                            append("Ödül: 🪙 +$earnedCoins")
                            level.rewardBooster?.let { append("  •  ${it.emoji} ${it.titleTr}") }
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                }

                if (onNextLevel != null) {
                    Button(
                        onClick = onNextLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Sonraki Bölüm ➔", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🔄 Tekrar", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onMap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🗺️ Harita", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameOverDialog(
    theme: GameTheme,
    score: Int,
    bestScore: Int,
    isNewBest: Boolean,
    earnedCoins: Int,
    isAdventure: Boolean,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val accent = Color(theme.accentColor)
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(theme.surfaceCard)),
            border = BorderStroke(1.8.dp, Color(theme.dangerLineColor).copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isAdventure) "⏳ BÖLÜM BAŞARISIZ" else "💥 OYUN BİTTİ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(theme.dangerLineColor)
                )

                if (isNewBest) {
                    Surface(
                        color = Color(0xFFFFD166).copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFFFFD166))
                    ) {
                        Text(
                            text = "🏆 YENİ REKOR!",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            color = Color(0xFFFFD166),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Text(
                    text = "$score",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                if (!isAdventure) {
                    Text(
                        text = "En Yüksek Skor: $bestScore",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }

                if (earnedCoins > 0) {
                    Text(
                        text = "Kazanılan Altın: 🪙 +$earnedCoins",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD166)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("🔄 Tekrar Oyna", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isAdventure) "🗺️ Macera Haritası" else "🏠 Ana Menü",
                        color = Color.White
                    )
                }
            }
        }
    }
}
