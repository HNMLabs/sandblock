package com.hnmlabs.sandblock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.data.GamePreferences
import com.hnmlabs.sandblock.model.AchievementDef
import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.DailyQuestType
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PlayerProgression

@Composable
fun QuestsAchievementsDialog(
    theme: GameTheme,
    prefs: GamePreferences,
    soundEngine: AsmrSoundEngine,
    onRewardsChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var bannerText by remember { mutableStateOf<String?>(null) }

    val coins = remember(refreshTick) { prefs.coins }
    val rank = remember(refreshTick) { PlayerProgression.calculateRank(prefs.totalXp) }

    val accent = Color(theme.accentColor)
    val cardBg = Color(theme.surfaceCard)
    val itemBg = Color(theme.bgBottom).copy(alpha = 0.88f)
    val goldColor = Color(0xFFFFD166)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .heightIn(max = 660.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.8.dp, accent.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header: Player Rank + Gold
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎖️ Sv.${rank.level} • ${rank.titleTr}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "XP: ${rank.currentLevelXp} / ${rank.neededLevelXp}  (Her seviye +50 🪙)",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.2.dp, goldColor.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = "🪙 $coins",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = goldColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { rank.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )

                bannerText?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = goldColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, goldColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3-Tab Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("📋 Günlük", "🏅 Başarımlar", "📊 İstatistik")
                    tabs.forEachIndexed { idx, title ->
                        val active = selectedTab == idx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    soundEngine.playTap()
                                    selectedTab = idx
                                },
                            color = if (active) accent else itemBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (active) accent else Color.White.copy(alpha = 0.16f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (active) Color(0xFF140E0A) else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Daily Gift Card
                            val giftAvailable = remember(refreshTick) { prefs.isDailyGiftAvailable() }
                            QuestRewardCard(
                                emoji = "🎁",
                                title = "Günlük Giriş Hediyesi",
                                description = "Her gün oyuna girerek ücretsiz altın ve XP kazan!",
                                progress = if (giftAvailable) 1 else 1,
                                target = 1,
                                rewardLabel = "🪙 +${GamePreferences.DAILY_GIFT_COINS}  •  +40 XP",
                                claimed = !giftAvailable,
                                canClaim = giftAvailable,
                                itemBg = itemBg,
                                accent = goldColor,
                                onClaim = {
                                    if (prefs.claimDailyGift()) {
                                        soundEngine.playCoinPurchase()
                                        bannerText = "🎁 Günlük Hediye alındı: +${GamePreferences.DAILY_GIFT_COINS} 🪙!"
                                        refreshTick++
                                        onRewardsChanged()
                                    }
                                }
                            )

                            // 3 Daily Quests
                            DailyQuestType.entries.forEach { q ->
                                val prog = remember(refreshTick) { prefs.getDailyQuestProgress(q) }
                                val claimed = remember(refreshTick) { prefs.isDailyQuestClaimed(q) }
                                QuestRewardCard(
                                    emoji = q.emoji,
                                    title = q.titleTr,
                                    description = q.descTr,
                                    progress = prog,
                                    target = q.target,
                                    rewardLabel = "🪙 +${q.rewardCoins}  •  +${q.rewardXp} XP",
                                    claimed = claimed,
                                    canClaim = !claimed && prog >= q.target,
                                    itemBg = itemBg,
                                    accent = accent,
                                    onClaim = {
                                        if (prefs.claimDailyQuest(q)) {
                                            soundEngine.playCoinPurchase()
                                            bannerText = "✅ ${q.titleTr} ödülü alındı: +${q.rewardCoins} 🪙!"
                                            refreshTick++
                                            onRewardsChanged()
                                        }
                                    }
                                )
                            }
                        }

                        1 -> {
                            AchievementDef.entries.forEach { ach ->
                                val prog = remember(refreshTick) { prefs.getAchievementProgress(ach) }
                                val claimed = remember(refreshTick) { prefs.isAchievementClaimed(ach) }
                                QuestRewardCard(
                                    emoji = ach.emoji,
                                    title = ach.titleTr,
                                    description = ach.descTr,
                                    progress = prog,
                                    target = ach.target,
                                    rewardLabel = "🪙 +${ach.rewardCoins}  •  +${ach.rewardXp} XP",
                                    claimed = claimed,
                                    canClaim = !claimed && prog >= ach.target,
                                    itemBg = itemBg,
                                    accent = accent,
                                    onClaim = {
                                        if (prefs.claimAchievement(ach)) {
                                            soundEngine.playCoinPurchase()
                                            bannerText = "🏅 Başarım açıldı: ${ach.titleTr} (+${ach.rewardCoins} 🪙)!"
                                            refreshTick++
                                            onRewardsChanged()
                                        }
                                    }
                                )
                            }
                        }

                        2 -> {
                            val totalStars = remember(refreshTick) { prefs.totalStarsEarned() }
                            val maxStars = AdventureCatalog.MAX_LEVELS * 3
                            StatSummaryRow("♾️ Klasik Sonsuz Rekoru", "${prefs.highScore}", itemBg, accent)
                            StatSummaryRow("⏱️ Zamana Karşı Rekoru", "${prefs.timeRushHighScore}", itemBg, accent)
                            StatSummaryRow("🕹️ Sandtrix Akış Rekoru", "${prefs.sandtrixHighScore}", itemBg, accent)
                            StatSummaryRow("⭐ Macera Yıldızları", "$totalStars / $maxStars", itemBg, goldColor)
                            StatSummaryRow("🎮 Toplam Oynanan Oyun", "${prefs.totalGamesPlayed}", itemBg, accent)
                            StatSummaryRow("🧱 Yerleştirilen Blok", "${prefs.totalBlocksPlaced}", itemBg, accent)
                            StatSummaryRow("✨ Temizlenen Kum Hattı", "${prefs.totalLinesCleared}", itemBg, accent)
                            StatSummaryRow("🔥 En Yüksek Kombo", "x${prefs.bestComboEver}", itemBg, goldColor)
                            StatSummaryRow("🧊 Kırılan Kaya & Buz", "${prefs.totalObstaclesBroken}", itemBg, accent)
                            StatSummaryRow(
                                "👑 Altın Bereketi Çarpanı",
                                "+%${prefs.goldBonusLevel * 25}",
                                itemBg,
                                goldColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        soundEngine.playTap()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF140E0A)
                    )
                ) {
                    Text("Kapat", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun QuestRewardCard(
    emoji: String,
    title: String,
    description: String,
    progress: Int,
    target: Int,
    rewardLabel: String,
    claimed: Boolean,
    canClaim: Boolean,
    itemBg: Color,
    accent: Color,
    onClaim: () -> Unit
) {
    val ratio = (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = itemBg),
        border = BorderStroke(
            width = if (canClaim) 1.6.dp else 1.dp,
            color = if (canClaim) Color(0xFFFFD166) else Color.White.copy(alpha = 0.14f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (canClaim || claimed) Color(0xFF06D6A0) else accent,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$progress / $target",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = rewardLabel,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD166)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onClaim,
                enabled = canClaim,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD166),
                    contentColor = Color(0xFF1A1208),
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    disabledContentColor = Color.White.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    text = when {
                        claimed -> "✓ Alındı"
                        canClaim -> "Ödülü Al"
                        else -> "Devam"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun StatSummaryRow(
    label: String,
    value: String,
    itemBg: Color,
    valueColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = itemBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
        }
    }
}
