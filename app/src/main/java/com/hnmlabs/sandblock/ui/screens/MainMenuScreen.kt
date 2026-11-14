package com.hnmlabs.sandblock.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.data.GamePreferences
import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.GameMode
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PlayerProgression
import com.hnmlabs.sandblock.ui.components.QuestsAchievementsDialog
import com.hnmlabs.sandblock.ui.components.ShopDialog

@Composable
fun MainMenuScreen(
    theme: GameTheme,
    prefs: GamePreferences,
    soundEngine: AsmrSoundEngine,
    highScore: Int,
    timeRushHighScore: Int,
    sandtrixHighScore: Int,
    savedClassicScore: Int?,
    coins: Int,
    totalStars: Int,
    unlockedLevel: Int,
    unclaimedCount: Int,
    soundEnabled: Boolean,
    musicEnabled: Boolean,
    hapticsEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleHaptics: () -> Unit,
    onSelectTheme: (GameTheme) -> Unit,
    onCoinsChanged: () -> Unit,
    onResumeClassic: () -> Unit,
    onStartMode: (GameMode) -> Unit,
    onOpenAdventure: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }
    var showShopDialog by remember { mutableStateOf(false) }
    var showQuestsDialog by remember { mutableStateOf(false) }

    val rank = remember(coins, unclaimedCount, highScore) {
        PlayerProgression.calculateRank(prefs.totalXp)
    }

    val infinite = rememberInfiniteTransition(label = "ambient_sand")
    val fallProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sand_fall"
    )

    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(theme.bgTop), Color(theme.bgBottom))
    )
    val accent = Color(theme.accentColor)
    val cardColor = Color(theme.surfaceCard)
    val maxStars = AdventureCatalog.MAX_LEVELS * 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        // Ambient falling sand grains backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val palette = theme.sandPalette
            val grainCount = 48
            for (i in 0 until grainCount) {
                val xFrac = ((i * 37) % 100) / 100f
                val speed = 1f + (i % 3) * 0.5f
                val yFrac = ((fallProgress * speed + (i * 0.137f)) % 1f)
                val sizePx = (4f + (i % 4) * 2f) * density
                val col = Color(palette[i % palette.size]).copy(alpha = 0.24f)
                drawRect(
                    color = col,
                    topLeft = Offset(xFrac * size.width, yFrac * size.height),
                    size = Size(sizePx, sizePx)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top stats & quick toggles bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatPill(
                        text = "🛒 🪙 $coins +",
                        bgColor = cardColor,
                        borderColor = Color(0xFFFFD166).copy(alpha = 0.7f),
                        onClick = {
                            soundEngine.playTap()
                            showShopDialog = true
                        }
                    )
                    StatPill(
                        text = "⭐ $totalStars/$maxStars",
                        bgColor = cardColor,
                        borderColor = accent.copy(alpha = 0.45f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconTogglePill(
                        label = if (musicEnabled) "🎵" else "🔕",
                        active = musicEnabled,
                        accent = accent,
                        cardColor = cardColor,
                        onClick = onToggleMusic
                    )
                    IconTogglePill(
                        label = if (soundEnabled) "🔊" else "🔇",
                        active = soundEnabled,
                        accent = accent,
                        cardColor = cardColor,
                        onClick = onToggleSound
                    )
                    IconTogglePill(
                        label = if (hapticsEnabled) "📳" else "📴",
                        active = hapticsEnabled,
                        accent = accent,
                        cardColor = cardColor,
                        onClick = onToggleHaptics
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Colorful SandBlock Emblem
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                theme.sandPalette.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(c))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SANDBLOCK",
                fontSize = 39.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = Color.White
            )
            Text(
                text = "KUM FİZİĞİ BLOK BULMACASI v2.0",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
                color = accent
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Player Rank & High Scores Showcase Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        soundEngine.playTap()
                        showQuestsDialog = true
                    },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.92f)),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.48f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎖️ Sv.${rank.level} • ${rank.titleTr}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${rank.currentLevelXp}/${rank.neededLevelXp} XP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { rank.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ScoreMiniCol("♾️ KLASİK", "$highScore", accent)
                        ScoreMiniCol("⏱️ ZAMAN", "$timeRushHighScore", Color(0xFF00F5D4))
                        ScoreMiniCol("🕹️ SANDTRIX", "$sandtrixHighScore", Color(0xFFFFD166))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resume Saved Classic Game Button (if available)
            if (savedClassicScore != null) {
                Button(
                    onClick = onResumeClassic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF06D6A0),
                        contentColor = Color(0xFF081C15)
                    )
                ) {
                    Text(
                        text = "💾  DEVAM ET (KLASİK SKOR: $savedClassicScore)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Classic Endless Mode Button
            Button(
                onClick = { onStartMode(GameMode.CLASSIC) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF140E0A)
                )
            ) {
                Text(
                    text = "▶  KLASİK SONSUZ MOD",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2 New Modes Row: Time Rush & Sandtrix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onStartMode(GameMode.TIME_RUSH) },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cardColor,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.6.dp, Color(0xFF00F5D4).copy(alpha = 0.75f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⏱️ ZAMANA KARŞI",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00F5D4)
                        )
                        Text(
                            text = "90sn + Kombo Süre",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }

                Button(
                    onClick = { onStartMode(GameMode.SANDTRIX) },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cardColor,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.6.dp, Color(0xFFFF9E00).copy(alpha = 0.8f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🕹️ SANDTRIX AKIŞ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF9E00)
                        )
                        Text(
                            text = "Düşen Kum Blokları",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Adventure Mode Button (20 Levels across 6 Worlds)
            Button(
                onClick = onOpenAdventure,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cardColor,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.8.dp, accent.copy(alpha = 0.75f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🗺️  MACERA MODU",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Bölüm $unlockedLevel / ${AdventureCatalog.MAX_LEVELS}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quests, Achievements & Statistics Button
            OutlinedButton(
                onClick = {
                    soundEngine.playTap()
                    showQuestsDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (unclaimedCount > 0) Color(0xFF06D6A0) else accent.copy(alpha = 0.65f)
                )
            ) {
                Text(
                    text = if (unclaimedCount > 0) {
                        "🎁  GÖREVLER & BAŞARIMLAR ($unclaimedCount ÖDÜL HAZIR!)"
                    } else {
                        "🏅  GÜNLÜK GÖREVLER, BAŞARIMLAR & İSTATİSTİK"
                    },
                    color = if (unclaimedCount > 0) Color(0xFF06D6A0) else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Gold Shop Button
            OutlinedButton(
                onClick = {
                    soundEngine.playTap()
                    showShopDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFFD166).copy(alpha = 0.75f))
            ) {
                Text(
                    text = "🛒  ALTIN MAĞAZASI (BOMBA, LAV, LAZER & PAKETLER)",
                    color = Color(0xFFFFD166),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Themes & How To Play
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = "${theme.emoji} 6 Tema",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { showHowToPlay = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = "❓ Nasıl Oynanır",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ASMR Kum Fiziği & Zen Müzik • Çevrimdışı Oynanabilir",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }

    if (showQuestsDialog) {
        QuestsAchievementsDialog(
            theme = theme,
            prefs = prefs,
            soundEngine = soundEngine,
            onRewardsChanged = onCoinsChanged,
            onDismiss = { showQuestsDialog = false }
        )
    }

    if (showShopDialog) {
        ShopDialog(
            theme = theme,
            prefs = prefs,
            soundEngine = soundEngine,
            inActiveGame = false,
            onPurchaseChanged = onCoinsChanged,
            onDismiss = { showShopDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectorDialog(
            currentTheme = theme,
            onSelect = {
                onSelectTheme(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showHowToPlay) {
        HowToPlayDialog(
            theme = theme,
            onDismiss = { showHowToPlay = false }
        )
    }
}

@Composable
private fun ScoreMiniCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.65f)
        )
        Text(
            text = value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
private fun StatPill(
    text: String,
    bgColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
        } else {
            Modifier
        },
        color = bgColor.copy(alpha = 0.9f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IconTogglePill(
    label: String,
    active: Boolean,
    accent: Color,
    cardColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = if (active) accent.copy(alpha = 0.22f) else cardColor.copy(alpha = 0.7f),
        shape = CircleShape,
        border = BorderStroke(1.2.dp, if (active) accent else Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ThemeSelectorDialog(
    currentTheme: GameTheme,
    onSelect: (GameTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(currentTheme.surfaceCard)),
            border = BorderStroke(1.5.dp, Color(currentTheme.accentColor).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "🎨 Görsel Tema Seç (6 Dünya)",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameTheme.entries.forEach { candidate ->
                        val isSelected = candidate == currentTheme
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(candidate) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(candidate.bgBottom)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.2.dp else 1.dp,
                                color = if (isSelected) Color(candidate.accentColor) else Color.White.copy(alpha = 0.18f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${candidate.emoji} ${candidate.titleTr}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = candidate.subtitleTr,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    candidate.sandPalette.take(4).forEach { col ->
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(col))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(currentTheme.accentColor),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Kapat", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HowToPlayDialog(
    theme: GameTheme,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(theme.surfaceCard)),
            border = BorderStroke(1.5.dp, Color(theme.accentColor).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "📖 Nasıl Oynanır? (v2.0)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    RuleItem("1️⃣", "Sürükle, Döndür & Sakla", "Blokları alana sürükle, dokunarak 90° döndür veya sol alttaki 📦 SAKLA yuvasına yedekle.")
                    RuleItem("2️⃣", "Soldan Sağa Birleştir", "Aynı renkteki kum sol duvardan sağ duvara kesintisiz köprü kurduğunda patlar, puan ve 🪙 altın kazandırır!")
                    RuleItem("🔥", "Kombo Ateşi (Fever 2X)", "Peş peşe hat temizleyerek barı doldur; 10 saniye boyunca tüm skor ve altınlar 2X katlanır!")
                    RuleItem("🌋", "Lav Kumu & Buz Bloklar", "🌋 Lav blokları kum, taş ve 2 vuruşluk 🧊 Buz Bloklarını eriterek aşağı süzülür.")
                    RuleItem("🕹️", "4 Farklı Oyun Modu", "Klasik Sonsuz, 90 saniyelik Zamana Karşı, yukarıdan düşen bloklarla Sandtrix Akış ve 20 bölümlük Macera!")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.accentColor),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Anladım, Başlayalım!", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RuleItem(icon: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.78f),
                lineHeight = 16.sp
            )
        }
    }
}
