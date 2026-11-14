import SwiftUI

struct SandProgressBar: View {
    let progress: Double
    let fill: Color
    var track: Color = Color.white.opacity(0.14)
    var height: CGFloat = 6

    var body: some View {
        GeometryReader { geo in
            let clamped = min(1.0, max(0.0, progress))
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(track)
                Capsule()
                    .fill(fill)
                    .frame(width: max(0, geo.size.width * clamped))
            }
        }
        .frame(height: height)
    }
}

struct ShopSheetView: View {
    let theme: GameTheme
    @ObservedObject var prefs: GamePreferences
    let soundEngine: AsmrSoundEngine
    let inActiveGame: Bool
    var canSweepSandNow: Bool = false
    var onExecuteSandSweep: (() -> Bool)?
    var onExecuteLavaStorm: (() -> Bool)?
    var onPurchaseChanged: () -> Void
    var onDismiss: () -> Void

    @State private var statusBanner: String?

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardBg: Color { Color(argb: theme.surfaceCard) }
    private var itemBg: Color { Color(argb: theme.bgBottom).opacity(0.92) }
    private let goldColor = Color(argb: 0xFFFFD166)

    var body: some View {
        ZStack {
            Color.black.opacity(0.68)
                .ignoresSafeArea()
                .onTapGesture {
                    soundEngine.playTap()
                    onDismiss()
                }

            VStack(spacing: 12) {
                // Header
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("🛒 ALTIN MAĞAZASI")
                            .font(.system(size: 19, weight: .black))
                            .foregroundColor(.white)
                        Text(inActiveGame ? "Aldığın ürünleri anında oyunda kullan!" : "Oyun öncesi çantanı güçlendir!")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundColor(.white.opacity(0.72))
                    }

                    Spacer()

                    Text("🪙 \(prefs.coins)")
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(goldColor)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Color.black.opacity(0.38))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(goldColor.opacity(0.75), lineWidth: 1.2)
                        )
                }

                if let msg = statusBanner {
                    Text(msg)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(accent.opacity(0.18))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .stroke(accent.opacity(0.5), lineWidth: 1)
                        )
                }

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 10) {
                        if inActiveGame {
                            shopSectionLabel("🆘 ACİL OYUN İÇİ MÜDAHALE", color: accent)

                            if let sweepAction = onExecuteSandSweep {
                                let canBuySweep = prefs.coins >= GamePreferences.sandSweepCost && canSweepSandNow
                                ShopRowCardView(
                                    emoji: "🌪️",
                                    title: "Kum Fırtınası (Üstü Süpür)",
                                    subtitle: canSweepSandNow
                                        ? "Anında en üstteki tehlikeli kum tepelerini temizler!"
                                        : "Alanda süpürülecek kum olduğunda kullanılır.",
                                    ownedBadge: "ANINDA",
                                    costText: "🪙 \(GamePreferences.sandSweepCost)",
                                    enabled: canBuySweep,
                                    itemBg: itemBg,
                                    accent: Color(argb: 0xFFFF6B6B)
                                ) {
                                    if prefs.coins >= GamePreferences.sandSweepCost && sweepAction() {
                                        prefs.spendCoins(GamePreferences.sandSweepCost)
                                        soundEngine.playCoinPurchase()
                                        onPurchaseChanged()
                                        onDismiss()
                                    }
                                }
                            }

                            if let lavaAction = onExecuteLavaStorm {
                                let canBuyLava = prefs.coins >= GamePreferences.lavaStormCost
                                ShopRowCardView(
                                    emoji: "🌋",
                                    title: "Lav Yağmuru (5 Kor Akışı)",
                                    subtitle: "Yukarıdan akan kızgın lav dalgası kum, taş ve buz engellerini eritir!",
                                    ownedBadge: "ANINDA",
                                    costText: "🪙 \(GamePreferences.lavaStormCost)",
                                    enabled: canBuyLava,
                                    itemBg: itemBg,
                                    accent: Color(argb: 0xFFFF6D00)
                                ) {
                                    if prefs.coins >= GamePreferences.lavaStormCost && lavaAction() {
                                        prefs.spendCoins(GamePreferences.lavaStormCost)
                                        soundEngine.playCoinPurchase()
                                        onPurchaseChanged()
                                        onDismiss()
                                    }
                                }
                            }
                        }

                        shopSectionLabel("⚡ GÜÇLENDİRİCİLER", color: accent)
                        ForEach(BoosterType.allCases) { booster in
                            let owned = prefs.getBoosterCount(booster)
                            let affordable = prefs.coins >= booster.coinCost
                            ShopRowCardView(
                                emoji: booster.emoji,
                                title: booster.titleTr,
                                subtitle: booster.descTr,
                                ownedBadge: "Çantada: x\(owned)",
                                costText: "🪙 \(booster.coinCost)",
                                enabled: affordable,
                                itemBg: itemBg,
                                accent: accent
                            ) {
                                if prefs.buyBooster(booster, quantity: 1) {
                                    soundEngine.playCoinPurchase()
                                    statusBanner = "✅ +1 \(booster.titleTr) satın alındı! (x\(owned + 1))"
                                    onPurchaseChanged()
                                }
                            }
                        }

                        shopSectionLabel("📦 AVANTAJ PAKETLERİ", color: goldColor)
                        ShopRowCardView(
                            emoji: "💥",
                            title: "3'lü TNT Bomba Paketi",
                            subtitle: "3 adet 💣 TNT Bomba birden ekler (%18 indirimli!).",
                            ownedBadge: "PAKET",
                            costText: "🪙 \(GamePreferences.bombBundleCost)",
                            enabled: prefs.coins >= GamePreferences.bombBundleCost,
                            itemBg: itemBg,
                            accent: goldColor
                        ) {
                            if prefs.buyBombBundle() {
                                soundEngine.playCoinPurchase()
                                statusBanner = "💥 +3 TNT Bomba çantana eklendi!"
                                onPurchaseChanged()
                            }
                        }

                        ShopRowCardView(
                            emoji: "🎁",
                            title: "Süper Kombo Paketi",
                            subtitle: "1x 💣 TNT + 1x ⚡ Lazer + 1x 🎨 Renk + 1x 🌈 Joker (%25 indirimli!).",
                            ownedBadge: "4 ÜRÜN",
                            costText: "🪙 \(GamePreferences.megaBundleCost)",
                            enabled: prefs.coins >= GamePreferences.megaBundleCost,
                            itemBg: itemBg,
                            accent: goldColor
                        ) {
                            if prefs.buyMegaBundle() {
                                soundEngine.playCoinPurchase()
                                statusBanner = "🎁 Süper Kombo Paketi (4 güçlendirici) eklendi!"
                                onPurchaseChanged()
                            }
                        }

                        shopSectionLabel("👑 KALICI GELİŞTİRME", color: goldColor)
                        let goldLevel = prefs.goldBonusLevel
                        let goldUpgradeCost = prefs.getGoldUpgradeCost()
                        let isMaxGold = goldLevel >= GamePreferences.maxGoldBonusLevel
                        let bonusPercent = goldLevel * 25
                        ShopRowCardView(
                            emoji: "👑",
                            title: "Altın Bereketi (Sv. \(goldLevel)/\(GamePreferences.maxGoldBonusLevel))",
                            subtitle: isMaxGold
                                ? "Maksimum seviye! Tüm altın kazançların +%100 zamlı!"
                                : "Oyun oynarken kazanılan altını kalıcı artırır (Şu an: +%\(bonusPercent) → +%\(bonusPercent + 25)).",
                            ownedBadge: "+%\(bonusPercent)",
                            costText: isMaxGold ? "MAKS" : "🪙 \(goldUpgradeCost)",
                            enabled: !isMaxGold && prefs.coins >= goldUpgradeCost,
                            itemBg: itemBg,
                            accent: goldColor
                        ) {
                            if prefs.upgradeGoldBonus() {
                                soundEngine.playCoinPurchase()
                                statusBanner = "👑 Altın Bereketi Sv.\(goldLevel + 1) oldu! (+%\((goldLevel + 1) * 25) altın)"
                                onPurchaseChanged()
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }

                Button {
                    soundEngine.playTap()
                    onDismiss()
                } label: {
                    Text(inActiveGame ? "▶ Oyuna Dön" : "Kapat")
                        .font(.system(size: 15, weight: .heavy))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(18)
            .frame(maxWidth: 440)
            .frame(maxHeight: 650)
            .background(cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .stroke(accent.opacity(0.7), lineWidth: 1.8)
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 24)
        }
    }

    private func shopSectionLabel(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .heavy))
            .tracking(1.0)
            .foregroundColor(color)
            .padding(.top, 4)
            .padding(.leading, 2)
    }
}

private struct ShopRowCardView: View {
    let emoji: String
    let title: String
    let subtitle: String
    let ownedBadge: String
    let costText: String
    let enabled: Bool
    let itemBg: Color
    let accent: Color
    let onBuy: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(accent.opacity(0.18))
                    .frame(width: 42, height: 42)
                Text(emoji)
                    .font(.system(size: 20))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.72))
                    .fixedSize(horizontal: false, vertical: true)
                Text(ownedBadge)
                    .font(.system(size: 10.5, weight: .heavy))
                    .foregroundColor(accent)
            }

            Spacer(minLength: 4)

            Button(action: onBuy) {
                Text(costText)
                    .font(.system(size: 12, weight: .heavy))
                    .foregroundColor(enabled ? Color(argb: 0xFF1A1208) : .white.opacity(0.38))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(enabled ? Color(argb: 0xFFFFD166) : Color.white.opacity(0.10))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .disabled(!enabled)
        }
        .padding(12)
        .background(itemBg)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.white.opacity(0.14), lineWidth: 1)
        )
    }
}
