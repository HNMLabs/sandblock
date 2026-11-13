package com.hnmlabs.sandblock.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.AdventureLevel
import com.hnmlabs.sandblock.model.GameTheme

@Composable
fun AdventureMapScreen(
    theme: GameTheme,
    unlockedLevel: Int,
    totalStars: Int,
    getStarsForLevel: (Int) -> Int,
    onSelectLevel: (AdventureLevel) -> Unit,
    onBack: () -> Unit
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(theme.bgTop), Color(theme.bgBottom))
    )
    val accent = Color(theme.accentColor)
    val cardBg = Color(theme.surfaceCard)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .systemBarsPadding()
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                color = cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "← Menü",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Text(
                text = "MACERA HARİTASI",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Surface(
                color = cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "⭐ $totalStars / 36",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(AdventureCatalog.levels, key = { it.number }) { level ->
                val isUnlocked = level.number <= unlockedLevel
                val stars = getStarsForLevel(level.number)

                if ((level.number - 1) % 3 == 0) {
                    Text(
                        text = "${level.theme.emoji} ${level.worldName.uppercase()}",
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(level.theme.accentColor),
                        letterSpacing = 1.2.sp
                    )
                }

                LevelCard(
                    level = level,
                    isUnlocked = isUnlocked,
                    stars = stars,
                    onClick = { if (isUnlocked) onSelectLevel(level) }
                )
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: AdventureLevel,
    isUnlocked: Boolean,
    stars: Int,
    onClick: () -> Unit
) {
    val worldAccent = Color(level.theme.accentColor)
    val containerColor = if (isUnlocked) {
        Color(level.theme.surfaceCard)
    } else {
        Color(0xFF1A1A22).copy(alpha = 0.72f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isUnlocked, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (isUnlocked && stars == 0) 1.8.dp else 1.dp,
            color = if (isUnlocked) worldAccent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level Number Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) worldAccent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isUnlocked) "${level.number}" else "🔒",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isUnlocked) worldAccent else Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = level.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) Color.White else Color.White.copy(alpha = 0.45f)
                    )
                    if (level.requireClearCrackedStones) {
                        Text(text = "🪨", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = level.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = if (isUnlocked) 0.75f else 0.38f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🎯 ${level.targetScore} Puan",
                        fontSize = 11.sp,
                        color = worldAccent.copy(alpha = if (isUnlocked) 0.9f else 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "🪙 +${level.rewardCoins}",
                        fontSize = 11.sp,
                        color = Color(0xFFFFD166).copy(alpha = if (isUnlocked) 0.9f else 0.4f),
                        fontWeight = FontWeight.SemiBold
                    )
                    level.rewardBooster?.let { b ->
                        Text(
                            text = "+1 ${b.emoji}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = if (isUnlocked) 0.9f else 0.4f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stars indicator
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (s in 1..3) {
                        Text(
                            text = "★",
                            fontSize = 16.sp,
                            color = if (s <= stars) Color(0xFFFFD166) else Color.White.copy(alpha = 0.22f)
                        )
                    }
                }
                if (isUnlocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "OYNA ▶",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = worldAccent
                    )
                }
            }
        }
    }
}
