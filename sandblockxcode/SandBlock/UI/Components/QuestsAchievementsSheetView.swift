import SwiftUI

struct QuestsAchievementsSheetView: View {
    let theme: GameTheme
    @ObservedObject var prefs: GamePreferences
    let soundEngine: AsmrSoundEngine
    var onRewardsChanged: () -> Void
    var onDismiss: () -> Void

    @State private var selectedTab: Int = 0
    @State private var bannerText: String?

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardBg: Color { Color(argb: theme.surfaceCard) }
    private var itemBg: Color { Color(argb: theme.bgBottom).opacity(0.90) }
    private let goldColor = Color(argb: 0xFFFFD166)

    var body: some View {
        let rank = PlayerProgression.calculateRank(prefs.totalXp)

        ZStack {
            Color.black.opacity(0.68)
                .ignoresSafeArea()
                .onTapGesture {
                    soundEngine.playTap()
                    onDismiss()
                }

            VStack(spacing: 11) {
                // Header: Rank + Gold
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("🎖️ Sv.\(rank.level) • \(rank.titleTr)")
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                        Text("XP: \(rank.currentLevelXp) / \(rank.neededLevelXp)  (Her seviye +50 🪙)")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.white.opacity(0.72))
                    }

                    Spacer()

                    Text("🪙 \(prefs.coins)")
                        .font(.system(size: 14, weight: .heavy))
                        .foregroundColor(goldColor)
                        .padding(.horizontal, 13)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.35))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(goldColor.opacity(0.75), lineWidth: 1.2)
                        )
                }

                SandProgressBar(progress: rank.progress, fill: accent, height: 6)

                if let msg = bannerText {
                    Text(msg)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(goldColor.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(goldColor.opacity(0.55), lineWidth: 1)
                        )
                }

                // 3-Tab Selector
                HStack(spacing: 8) {
                    let tabs = ["📋 Günlük", "🏅 Başarımlar", "📊 İstatistik"]
                    ForEach(0..<tabs.count, id: \.self) { idx in
                        let active = (selectedTab == idx)
                        Button {
                            soundEngine.playTap()
                            selectedTab = idx
                        } label: {
                            Text(tabs[idx])
                                .font(.system(size: 12, weight: .heavy))
                                .foregroundColor(active ? Color(argb: 0xFF140E0A) : .white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 9)
                                .background(active ? accent : itemBg)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .stroke(active ? accent : Color.white.opacity(0.16), lineWidth: 1)
                                )
                        }
                    }
                }

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        if selectedTab == 0 {
                            let giftAvailable = prefs.isDailyGiftAvailable()
                            QuestRewardCardView(
                                emoji: "🎁",
                                title: "Günlük Giriş Hediyesi",
                                description: "Her gün oyuna girerek ücretsiz altın ve XP kazan!",
                                progress: 1,
                                target: 1,
                                rewardLabel: "🪙 +\(GamePreferences.dailyGiftCoins)  •  +40 XP",
                                claimed: !giftAvailable,
                                canClaim: giftAvailable,
                                itemBg: itemBg,
                                accent: goldColor
                            ) {
                                if prefs.claimDailyGift() {
                                    soundEngine.playCoinPurchase()
                                    bannerText = "🎁 Günlük Hediye alındı: +\(GamePreferences.dailyGiftCoins) 🪙!"
                                    onRewardsChanged()
                                }
                            }

                            ForEach(DailyQuestType.allCases) { q in
                                let prog = prefs.getDailyQuestProgress(q)
                                let claimed = prefs.isDailyQuestClaimed(q)
                                QuestRewardCardView(
                                    emoji: q.emoji,
                                    title: q.titleTr,
                                    description: q.descTr,
                                    progress: prog,
                                    target: q.target,
                                    rewardLabel: "🪙 +\(q.rewardCoins)  •  +\(q.rewardXp) XP",
                                    claimed: claimed,
                                    canClaim: !claimed && prog >= q.target,
                                    itemBg: itemBg,
                                    accent: accent
                                ) {
                                    if prefs.claimDailyQuest(q) {
                                        soundEngine.playCoinPurchase()
                                        bannerText = "✅ \(q.titleTr) ödülü alındı: +\(q.rewardCoins) 🪙!"
                                        onRewardsChanged()
                                    }
                                }
                            }
                        } else if selectedTab == 1 {
                            ForEach(AchievementDef.allCases) { ach in
                                let prog = prefs.getAchievementProgress(ach)
                                let claimed = prefs.isAchievementClaimed(ach)
                                QuestRewardCardView(
                                    emoji: ach.emoji,
                                    title: ach.titleTr,
                                    description: ach.descTr,
                                    progress: prog,
                                    target: ach.target,
                                    rewardLabel: "🪙 +\(ach.rewardCoins)  •  +\(ach.rewardXp) XP",
                                    claimed: claimed,
                                    canClaim: !claimed && prog >= ach.target,
                                    itemBg: itemBg,
                                    accent: accent
                                ) {
                                    if prefs.claimAchievement(ach) {
                                        soundEngine.playCoinPurchase()
                                        bannerText = "🏅 Başarım açıldı: \(ach.titleTr) (+\(ach.rewardCoins) 🪙)!"
                                        onRewardsChanged()
                                    }
                                }
                            }
                        } else {
                            let totalStars = prefs.totalStarsEarned()
                            let maxStars = AdventureCatalog.maxLevels * 3
                            StatSummaryRowView(label: "♾️ Klasik Sonsuz Rekoru", value: "\(prefs.highScore)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "⏱️ Zamana Karşı Rekoru", value: "\(prefs.timeRushHighScore)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "🕹️ Sandtrix Akış Rekoru", value: "\(prefs.sandtrixHighScore)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "⭐ Macera Yıldızları", value: "\(totalStars) / \(maxStars)", itemBg: itemBg, valueColor: goldColor)
                            StatSummaryRowView(label: "🎮 Toplam Oynanan Oyun", value: "\(prefs.totalGamesPlayed)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "🧱 Yerleştirilen Blok", value: "\(prefs.totalBlocksPlaced)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "✨ Temizlenen Kum Hattı", value: "\(prefs.totalLinesCleared)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "🔥 En Yüksek Kombo", value: "x\(prefs.bestComboEver)", itemBg: itemBg, valueColor: goldColor)
                            StatSummaryRowView(label: "🧊 Kırılan Kaya & Buz", value: "\(prefs.totalObstaclesBroken)", itemBg: itemBg, valueColor: accent)
                            StatSummaryRowView(label: "👑 Altın Bereketi Çarpanı", value: "+%\(prefs.goldBonusLevel * 25)", itemBg: itemBg, valueColor: goldColor)
                        }
                    }
                    .padding(.vertical, 2)
                }

                Button {
                    soundEngine.playTap()
                    onDismiss()
                } label: {
                    Text("Kapat")
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(Color(argb: 0xFF140E0A))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(18)
            .frame(maxWidth: 440)
            .frame(maxHeight: 660)
            .background(cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .stroke(accent.opacity(0.7), lineWidth: 1.8)
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 20)
        }
    }
}

private struct QuestRewardCardView: View {
    let emoji: String
    let title: String
    let description: String
    let progress: Int
    let target: Int
    let rewardLabel: String
    let claimed: Bool
    let canClaim: Bool
    let itemBg: Color
    let accent: Color
    let onClaim: () -> Void

    var body: some View {
        let ratio = Double(progress) / Double(max(1, target))

        HStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(accent.opacity(0.18))
                    .frame(width: 42, height: 42)
                Text(emoji)
                    .font(.system(size: 20))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(.white)
                Text(description)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.72))
                    .fixedSize(horizontal: false, vertical: true)
                SandProgressBar(
                    progress: ratio,
                    fill: (canClaim || claimed) ? Color(argb: 0xFF06D6A0) : accent,
                    height: 5
                )
                HStack {
                    Text("\(progress) / \(target)")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.white.opacity(0.7))
                    Spacer()
                    Text(rewardLabel)
                        .font(.system(size: 10.5, weight: .heavy))
                        .foregroundColor(Color(argb: 0xFFFFD166))
                }
            }

            Button(action: onClaim) {
                Text(claimed ? "✓ Alındı" : (canClaim ? "Ödülü Al" : "Devam"))
                    .font(.system(size: 11.5, weight: .heavy))
                    .foregroundColor(canClaim ? Color(argb: 0xFF1A1208) : .white.opacity(0.45))
                    .padding(.horizontal, 11)
                    .padding(.vertical, 8)
                    .background(canClaim ? Color(argb: 0xFFFFD166) : Color.white.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .disabled(!canClaim)
        }
        .padding(12)
        .background(itemBg)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(
                    canClaim ? Color(argb: 0xFFFFD166) : Color.white.opacity(0.14),
                    lineWidth: canClaim ? 1.6 : 1.0
                )
        )
    }
}

private struct StatSummaryRowView: View {
    let label: String
    let value: String
    let itemBg: Color
    let valueColor: Color

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.white.opacity(0.9))
            Spacer()
            Text(value)
                .font(.system(size: 15, weight: .heavy))
                .foregroundColor(valueColor)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(itemBg)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color.white.opacity(0.12), lineWidth: 1)
        )
    }
}
