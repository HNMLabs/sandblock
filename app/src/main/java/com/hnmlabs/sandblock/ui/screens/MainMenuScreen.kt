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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.data.GamePreferences
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.ui.components.ShopDialog

@Composable
fun MainMenuScreen(
    theme: GameTheme,
    prefs: GamePreferences,
    soundEngine: AsmrSoundEngine,
    highScore: Int,
    coins: Int,
    totalStars: Int,
    unlockedLevel: Int,
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleHaptics: () -> Unit,
    onSelectTheme: (GameTheme) -> Unit,
    onCoinsChanged: () -> Unit,
    onStartClassic: () -> Unit,
    onOpenAdventure: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }
    var showShopDialog by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        // Ambient falling sand grains backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val palette = theme.sandPalette
            val grainCount = 46
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
                .padding(horizontal = 22.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top stats & quick toggles bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(
                        text = "🛒 🪙 $coins +",
                        bgColor = cardColor,
                        borderColor = Color(0xFFFFD166).copy(alpha = 0.7f),
                        onClick = {
                            soundEngine.playTap()
                            showShopDialog = true
                        }
                    )
                    StatPill(text = "⭐ $totalStars/36", bgColor = cardColor, borderColor = accent.copy(alpha = 0.45f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Spacer(modifier = Modifier.height(34.dp))

            // Colorful SandBlock Emblem
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                theme.sandPalette.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(c))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SANDBLOCK",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = Color.White
            )
            Text(
                text = "KUM FİZİĞİ BLOK BULMACASI",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.8.sp,
                color = accent
            )

            Spacer(modifier = Modifier.height(28.dp))

            // High Score Showcase Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.92f)),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🏆 EN YÜKSEK SKOR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.72f),
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$highScore",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktif Tema: ${theme.emoji} ${theme.titleTr}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Classic Mode Button
            Button(
                onClick = onStartClassic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF140E0A)
                )
            ) {
                Text(
                    text = "▶  KLASİK SONSUZ MOD",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Adventure Mode Button
            Button(
                onClick = onOpenAdventure,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Bölüm $unlockedLevel / 12",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gold Shop Button
            OutlinedButton(
                onClick = {
                    soundEngine.playTap()
                    showShopDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, Color(0xFFFFD166).copy(alpha = 0.75f))
            ) {
                Text(
                    text = "🛒  ALTIN MAĞAZASI (BOMBA, LAZER & PAKETLER)",
                    color = Color(0xFFFFD166),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Themes & How To Play
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = "${theme.emoji} Temalar",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { showHowToPlay = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
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

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "ASMR Kum Fiziği • Çevrimdışı Oynanabilir",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 13.sp,
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
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = if (active) accent.copy(alpha = 0.22f) else cardColor.copy(alpha = 0.7f),
        shape = CircleShape,
        border = BorderStroke(1.2.dp, if (active) accent else Color.White.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 16.sp)
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
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(currentTheme.surfaceCard)),
            border = BorderStroke(1.5.dp, Color(currentTheme.accentColor).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🎨 Görsel Tema Seç",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

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
                                .padding(14.dp),
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
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(theme.surfaceCard)),
            border = BorderStroke(1.5.dp, Color(theme.accentColor).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📖 Nasıl Oynanır?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                RuleItem("1️⃣", "Sürükle ve Bırak", "Alttaki 3 bloktan istediğini oyun alanına sürükle. Üzerine dokunarak 90° döndürebilirsin.")
                RuleItem("2️⃣", "Kum Fiziği", "Bıraktığın her blok yüzlerce kum tanesine dönüşür ve boşluklara doğru doğal şekilde akar.")
                RuleItem("3️⃣", "Soldan Sağa Birleştir", "Aynı renkteki kum sol duvardan sağ duvara kesintisiz bir hat kurduğunda patlayarak silinir ve kombo başlatır!")
                RuleItem("🌈", "Özel Güçlendiriciler", "TNT Bomba alanı patlatır, Renk Silici tek bir rengi yok eder, Joker Kum ise her renkle birleşir!")
                RuleItem("⚠️", "Sınır Çizgisi", "Kum yığını üstteki kırmızı SINIR çizgisini aşarsa oyun biter.")

                Spacer(modifier = Modifier.height(4.dp))
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
