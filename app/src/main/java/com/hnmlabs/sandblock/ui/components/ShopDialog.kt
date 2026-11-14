package com.hnmlabs.sandblock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.GameTheme

@Composable
fun ShopDialog(
    theme: GameTheme,
    prefs: GamePreferences,
    soundEngine: AsmrSoundEngine,
    inActiveGame: Boolean,
    canSweepSandNow: Boolean = false,
    onExecuteSandSweep: (() -> Boolean)? = null,
    onExecuteLavaStorm: (() -> Boolean)? = null,
    onPurchaseChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var localRefresh by remember { mutableIntStateOf(0) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val coins = remember(localRefresh) { prefs.coins }
    val goldLevel = remember(localRefresh) { prefs.goldBonusLevel }
    val goldUpgradeCost = remember(localRefresh) { prefs.getGoldUpgradeCost() }

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
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.8.dp, accent.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header with live coin balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🛒 ALTIN MAĞAZASI",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = if (inActiveGame) "Aldığın ürünleri anında oyunda kullan!" else "Oyun öncesi çantanı güçlendir!",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.2.dp, goldColor.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = "🪙 $coins",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = goldColor
                        )
                    }
                }

                statusBanner?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
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

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Shop Catalog
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Instant Emergency In-Game Interventions
                    if (inActiveGame) {
                        SectionLabel("🆘 ACİL OYUN İÇİ MÜDAHALE", accent)

                        if (onExecuteSandSweep != null) {
                            val canBuySweep = coins >= GamePreferences.SAND_SWEEP_COST && canSweepSandNow
                            ShopRowCard(
                                emoji = "🌪️",
                                title = "Kum Fırtınası (Üstü Süpür)",
                                subtitle = if (canSweepSandNow) {
                                    "Anında en üstteki tehlikeli kum tepelerini temizler!"
                                } else {
                                    "Alanda süpürülecek kum olduğunda kullanılır."
                                },
                                ownedBadge = "ANINDA",
                                costText = "🪙 ${GamePreferences.SAND_SWEEP_COST}",
                                enabled = canBuySweep,
                                itemBg = itemBg,
                                accent = Color(0xFFFF6B6B),
                                onBuy = {
                                    if (prefs.coins >= GamePreferences.SAND_SWEEP_COST && onExecuteSandSweep()) {
                                        prefs.spendCoins(GamePreferences.SAND_SWEEP_COST)
                                        soundEngine.playCoinPurchase()
                                        localRefresh++
                                        onPurchaseChanged()
                                        onDismiss()
                                    }
                                }
                            )
                        }

                        if (onExecuteLavaStorm != null) {
                            val canBuyLava = coins >= GamePreferences.LAVA_STORM_COST
                            ShopRowCard(
                                emoji = "🌋",
                                title = "Lav Yağmuru (5 Kor Akışı)",
                                subtitle = "Yukarıdan akan kızgın lav dalgası kum, taş ve buz engellerini eritir!",
                                ownedBadge = "ANINDA",
                                costText = "🪙 ${GamePreferences.LAVA_STORM_COST}",
                                enabled = canBuyLava,
                                itemBg = itemBg,
                                accent = Color(0xFFFF6D00),
                                onBuy = {
                                    if (prefs.coins >= GamePreferences.LAVA_STORM_COST && onExecuteLavaStorm()) {
                                        prefs.spendCoins(GamePreferences.LAVA_STORM_COST)
                                        soundEngine.playCoinPurchase()
                                        localRefresh++
                                        onPurchaseChanged()
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }

                    // Individual Boosters
                    SectionLabel("⚡ GÜÇLENDİRİCİLER", accent)
                    BoosterType.entries.forEach { booster ->
                        val owned = remember(localRefresh) { prefs.getBoosterCount(booster) }
                        val affordable = coins >= booster.coinCost
                        ShopRowCard(
                            emoji = booster.emoji,
                            title = booster.titleTr,
                            subtitle = booster.descTr,
                            ownedBadge = "Çantada: x$owned",
                            costText = "🪙 ${booster.coinCost}",
                            enabled = affordable,
                            itemBg = itemBg,
                            accent = accent,
                            onBuy = {
                                if (prefs.buyBooster(booster, 1)) {
                                    soundEngine.playCoinPurchase()
                                    statusBanner = "✅ +1 ${booster.titleTr} satın alındı! (x${owned + 1})"
                                    localRefresh++
                                    onPurchaseChanged()
                                }
                            }
                        )
                    }

                    // Value Bundles
                    SectionLabel("📦 AVANTAJ PAKETLERİ", goldColor)
                    ShopRowCard(
                        emoji = "💥",
                        title = "3'lü TNT Bomba Paketi",
                        subtitle = "3 adet 💣 TNT Bomba birden ekler (%18 indirimli!).",
                        ownedBadge = "PAKET",
                        costText = "🪙 ${GamePreferences.BOMB_BUNDLE_COST}",
                        enabled = coins >= GamePreferences.BOMB_BUNDLE_COST,
                        itemBg = itemBg,
                        accent = goldColor,
                        onBuy = {
                            if (prefs.buyBombBundle()) {
                                soundEngine.playCoinPurchase()
                                statusBanner = "💥 +3 TNT Bomba çantana eklendi!"
                                localRefresh++
                                onPurchaseChanged()
                            }
                        }
                    )

                    ShopRowCard(
                        emoji = "🎁",
                        title = "Süper Kombo Paketi",
                        subtitle = "1x 💣 TNT + 1x ⚡ Lazer + 1x 🎨 Renk + 1x 🌈 Joker (%25 indirimli!).",
                        ownedBadge = "4 ÜRÜN",
                        costText = "🪙 ${GamePreferences.MEGA_BUNDLE_COST}",
                        enabled = coins >= GamePreferences.MEGA_BUNDLE_COST,
                        itemBg = itemBg,
                        accent = goldColor,
                        onBuy = {
                            if (prefs.buyMegaBundle()) {
                                soundEngine.playCoinPurchase()
                                statusBanner = "🎁 Süper Kombo Paketi (4 güçlendirici) eklendi!"
                                localRefresh++
                                onPurchaseChanged()
                            }
                        }
                    )

                    // Permanent Gold Multiplier Upgrade
                    SectionLabel("👑 KALICI GELİŞTİRME", goldColor)
                    val isMaxGold = goldLevel >= GamePreferences.MAX_GOLD_BONUS_LEVEL
                    val bonusPercent = goldLevel * 25
                    ShopRowCard(
                        emoji = "👑",
                        title = "Altın Bereketi (Sv. $goldLevel/${GamePreferences.MAX_GOLD_BONUS_LEVEL})",
                        subtitle = if (isMaxGold) {
                            "Maksimum seviye! Tüm altın kazançların +%100 zamlı!"
                        } else {
                            "Oyun oynarken kazanılan altını kalıcı artırır (Şu an: +%$bonusPercent → +%${bonusPercent + 25})."
                        },
                        ownedBadge = "+%$bonusPercent",
                        costText = if (isMaxGold) "MAKS" else "🪙 $goldUpgradeCost",
                        enabled = !isMaxGold && coins >= goldUpgradeCost,
                        itemBg = itemBg,
                        accent = goldColor,
                        onBuy = {
                            if (prefs.upgradeGoldBonus()) {
                                soundEngine.playCoinPurchase()
                                statusBanner = "👑 Altın Bereketi Sv.${goldLevel + 1} oldu! (+%${(goldLevel + 1) * 25} altın)"
                                localRefresh++
                                onPurchaseChanged()
                            }
                        }
                    )
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
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (inActiveGame) "▶ Oyuna Dön" else "Kapat",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
    )
}

@Composable
private fun ShopRowCard(
    emoji: String,
    title: String,
    subtitle: String,
    ownedBadge: String,
    costText: String,
    enabled: Boolean,
    itemBg: Color,
    accent: Color,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = itemBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = ownedBadge,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onBuy,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD166),
                    contentColor = Color(0xFF1A1208),
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                )
            ) {
                Text(
                    text = costText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
