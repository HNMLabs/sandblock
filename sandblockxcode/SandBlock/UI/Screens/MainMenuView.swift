import SwiftUI

struct MainMenuView: View {
    let theme: GameTheme
    @ObservedObject var prefs: GamePreferences
    let soundEngine: AsmrSoundEngine
    var onToggleSound: () -> Void
    var onToggleMusic: () -> Void
    var onToggleHaptics: () -> Void
    var onSelectTheme: (GameTheme) -> Void
    var onResumeClassic: () -> Void
    var onStartMode: (GameMode) -> Void
    var onOpenAdventure: () -> Void

    @State private var showThemeModal = false
    @State private var showHowToPlayModal = false
    @State private var showShopModal = false
    @State private var showQuestsModal = false

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardColor: Color { Color(argb: theme.surfaceCard) }
    private let maxStars = AdventureCatalog.maxLevels * 3

    var body: some View {
        let rank = PlayerProgression.calculateRank(prefs.totalXp)
        let unclaimedCount = prefs.countUnclaimedRewards()
        let savedClassicScore = prefs.getSavedClassicScore()
        let totalStars = prefs.totalStarsEarned()

        ZStack {
            LinearGradient(
                colors: [Color(argb: theme.bgTop), Color(argb: theme.bgBottom)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Ambient Falling Sand Grains Backdrop
            TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                Canvas { context, size in
                    let t = timeline.date.timeIntervalSinceReferenceDate
                    let progress = (t / 9.0).truncatingRemainder(dividingBy: 1.0)
                    let palette = theme.sandPalette
                    for i in 0..<48 {
                        let xFrac = Double((i * 37) % 100) / 100.0
                        let speed = 1.0 + Double(i % 3) * 0.5
                        let yFrac = (progress * speed + Double(i) * 0.137).truncatingRemainder(dividingBy: 1.0)
                        let sz = CGFloat(4 + (i % 4) * 2)
                        let rect = CGRect(x: xFrac * size.width, y: yFrac * size.height, width: sz, height: sz)
                        context.fill(
                            Path(rect),
                            with: .color(Color(argb: palette[i % palette.count]).opacity(0.24))
                        )
                    }
                }
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    // Top Stats & Audio Toggles Bar
                    HStack {
                        HStack(spacing: 6) {
                            Button {
                                soundEngine.playTap()
                                showShopModal = true
                            } label: {
                                Text("🛒 🪙 \(prefs.coins) +")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(cardColor.opacity(0.9))
                                    .clipShape(Capsule())
                                    .overlay(
                                        Capsule().stroke(Color(argb: 0xFFFFD166).opacity(0.7), lineWidth: 1)
                                    )
                            }

                            Text("⭐ \(totalStars)/\(maxStars)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(cardColor.opacity(0.9))
                                .clipShape(Capsule())
                                .overlay(
                                    Capsule().stroke(accent.opacity(0.45), lineWidth: 1)
                                )
                        }

                        Spacer()

                        HStack(spacing: 6) {
                            MenuIconTogglePill(
                                label: prefs.musicEnabled ? "🎵" : "🔕",
                                active: prefs.musicEnabled,
                                accent: accent,
                                cardColor: cardColor,
                                action: onToggleMusic
                            )
                            MenuIconTogglePill(
                                label: prefs.soundEnabled ? "🔊" : "🔇",
                                active: prefs.soundEnabled,
                                accent: accent,
                                cardColor: cardColor,
                                action: onToggleSound
                            )
                            MenuIconTogglePill(
                                label: prefs.hapticsEnabled ? "📳" : "📴",
                                active: prefs.hapticsEnabled,
                                accent: accent,
                                cardColor: cardColor,
                                action: onToggleHaptics
                            )
                        }
                    }

                    Spacer().frame(height: 22)

                    // Colorful SandBlock Emblem
                    HStack(spacing: 6) {
                        ForEach(Array(theme.sandPalette.enumerated()), id: \.offset) { _, col in
                            RoundedRectangle(cornerRadius: 5, style: .continuous)
                                .fill(Color(argb: col))
                                .frame(width: 16, height: 16)
                        }
                    }

                    Spacer().frame(height: 10)

                    Text("SANDBLOCK")
                        .font(.system(size: 38, weight: .black))
                        .tracking(3.0)
                        .foregroundColor(.white)

                    Text("KUM FİZİĞİ BLOK BULMACASI v2.0")
                        .font(.system(size: 12, weight: .semibold))
                        .tracking(1.6)
                        .foregroundColor(accent)
                        .padding(.top, 2)

                    Spacer().frame(height: 18)

                    // Player Rank & High Scores Card
                    Button {
                        soundEngine.playTap()
                        showQuestsModal = true
                    } label: {
                        VStack(spacing: 10) {
                            HStack {
                                Text("🎖️ Sv.\(rank.level) • \(rank.titleTr)")
                                    .font(.system(size: 13, weight: .heavy))
                                    .foregroundColor(.white)
                                Spacer()
                                Text("\(rank.currentLevelXp)/\(rank.neededLevelXp) XP")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(accent)
                            }

                            SandProgressBar(progress: rank.progress, fill: accent, height: 5)

                            HStack {
                                Spacer()
                                MenuScoreMiniCol(label: "♾️ KLASİK", value: "\(prefs.highScore)", color: accent)
                                Spacer()
                                MenuScoreMiniCol(label: "⏱️ ZAMAN", value: "\(prefs.timeRushHighScore)", color: Color(argb: 0xFF00F5D4))
                                Spacer()
                                MenuScoreMiniCol(label: "🕹️ SANDTRIX", value: "\(prefs.sandtrixHighScore)", color: Color(argb: 0xFFFFD166))
                                Spacer()
                            }
                            .padding(.top, 2)
                        }
                        .padding(.vertical, 14)
                        .padding(.horizontal, 18)
                        .background(cardColor.opacity(0.92))
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .stroke(accent.opacity(0.48), lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)

                    Spacer().frame(height: 16)

                    // Resume Saved Classic Game Button
                    if let savedScore = savedClassicScore {
                        Button(action: onResumeClassic) {
                            Text("💾  DEVAM ET (KLASİK SKOR: \(savedScore))")
                                .font(.system(size: 15, weight: .black))
                                .tracking(0.5)
                                .foregroundColor(Color(argb: 0xFF081C15))
                                .frame(maxWidth: .infinity)
                                .frame(height: 54)
                                .background(Color(argb: 0xFF06D6A0))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        Spacer().frame(height: 10)
                    }

                    // Classic Endless Mode Button
                    Button {
                        onStartMode(.classic)
                    } label: {
                        Text("▶  KLASİK SONSUZ MOD")
                            .font(.system(size: 17, weight: .heavy))
                            .tracking(0.7)
                            .foregroundColor(Color(argb: 0xFF140E0A))
                            .frame(maxWidth: .infinity)
                            .frame(height: 58)
                            .background(accent)
                            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }

                    Spacer().frame(height: 10)

                    // Time Rush & Sandtrix Row
                    HStack(spacing: 10) {
                        Button {
                            onStartMode(.timeRush)
                        } label: {
                            VStack(spacing: 2) {
                                Text("⏱️ ZAMANA KARŞI")
                                    .font(.system(size: 13, weight: .heavy))
                                    .foregroundColor(Color(argb: 0xFF00F5D4))
                                Text("90sn + Kombo Süre")
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.72))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 58)
                            .background(cardColor)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color(argb: 0xFF00F5D4).opacity(0.75), lineWidth: 1.6)
                            )
                        }

                        Button {
                            onStartMode(.sandtrix)
                        } label: {
                            VStack(spacing: 2) {
                                Text("🕹️ SANDTRIX AKIŞ")
                                    .font(.system(size: 13, weight: .heavy))
                                    .foregroundColor(Color(argb: 0xFFFF9E00))
                                Text("Düşen Kum Blokları")
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.72))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 58)
                            .background(cardColor)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color(argb: 0xFFFF9E00).opacity(0.8), lineWidth: 1.6)
                            )
                        }
                    }

                    Spacer().frame(height: 10)

                    // Adventure Mode Button
                    Button(action: onOpenAdventure) {
                        HStack {
                            Text("🗺️  MACERA MODU")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                            Text("Bölüm \(prefs.unlockedLevel) / \(AdventureCatalog.maxLevels)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(accent)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(accent.opacity(0.2))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        .padding(.horizontal, 18)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(cardColor)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .stroke(accent.opacity(0.75), lineWidth: 1.8)
                        )
                    }

                    Spacer().frame(height: 12)

                    // Quests, Achievements & Stats Button
                    Button {
                        soundEngine.playTap()
                        showQuestsModal = true
                    } label: {
                        let highlight = unclaimedCount > 0
                        Text(
                            highlight
                                ? "🎁  GÖREVLER & BAŞARIMLAR (\(unclaimedCount) ÖDÜL HAZIR!)"
                                : "🏅  GÜNLÜK GÖREVLER, BAŞARIMLAR & İSTATİSTİK"
                        )
                        .font(.system(size: 12.5, weight: .heavy))
                        .foregroundColor(highlight ? Color(argb: 0xFF06D6A0) : .white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(cardColor.opacity(0.55))
                        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 15, style: .continuous)
                                .stroke(highlight ? Color(argb: 0xFF06D6A0) : accent.opacity(0.65), lineWidth: 1.5)
                        )
                    }

                    Spacer().frame(height: 10)

                    // Gold Shop Button
                    Button {
                        soundEngine.playTap()
                        showShopModal = true
                    } label: {
                        Text("🛒  ALTIN MAĞAZASI (BOMBA, LAV, LAZER & PAKETLER)")
                            .font(.system(size: 12.5, weight: .heavy))
                            .foregroundColor(Color(argb: 0xFFFFD166))
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(cardColor.opacity(0.55))
                            .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 15, style: .continuous)
                                    .stroke(Color(argb: 0xFFFFD166).opacity(0.75), lineWidth: 1.5)
                            )
                    }

                    Spacer().frame(height: 10)

                    // Themes & How To Play Row
                    HStack(spacing: 10) {
                        Button {
                            soundEngine.playTap()
                            showThemeModal = true
                        } label: {
                            Text("\(theme.emoji) 6 Tema")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(cardColor.opacity(0.45))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color.white.opacity(0.28), lineWidth: 1.2)
                                )
                        }

                        Button {
                            soundEngine.playTap()
                            showHowToPlayModal = true
                        } label: {
                            Text("❓ Nasıl Oynanır")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 48)
                                .background(cardColor.opacity(0.45))
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .stroke(Color.white.opacity(0.28), lineWidth: 1.2)
                                )
                        }
                    }

                    Spacer().frame(height: 18)

                    Text("ASMR Kum Fiziği & Zen Müzik • Çevrimdışı Oynanabilir")
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.45))
                        .padding(.bottom, 16)
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
            }

            if showQuestsModal {
                QuestsAchievementsSheetView(
                    theme: theme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    onRewardsChanged: {},
                    onDismiss: { showQuestsModal = false }
                )
            }

            if showShopModal {
                ShopSheetView(
                    theme: theme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    inActiveGame: false,
                    onPurchaseChanged: {},
                    onDismiss: { showShopModal = false }
                )
            }

            if showThemeModal {
                ThemeSelectorModalView(
                    currentTheme: theme,
                    onSelect: { chosen in
                        onSelectTheme(chosen)
                        showThemeModal = false
                    },
                    onDismiss: {
                        soundEngine.playTap()
                        showThemeModal = false
                    }
                )
            }

            if showHowToPlayModal {
                HowToPlayModalView(
                    theme: theme,
                    onDismiss: {
                        soundEngine.playTap()
                        showHowToPlayModal = false
                    }
                )
            }
        }
    }
}

private struct MenuScoreMiniCol: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.white.opacity(0.65))
            Text(value)
                .font(.system(size: 19, weight: .black))
                .foregroundColor(color)
        }
    }
}

private struct MenuIconTogglePill: View {
    let label: String
    let active: Bool
    let accent: Color
    let cardColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 15))
                .frame(width: 36, height: 36)
                .background(active ? accent.opacity(0.22) : cardColor.opacity(0.7))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(active ? accent : Color.white.opacity(0.2), lineWidth: 1.2)
                )
        }
    }
}

private struct ThemeSelectorModalView: View {
    let currentTheme: GameTheme
    let onSelect: (GameTheme) -> Void
    let onDismiss: () -> Void

    var body: some View {
        let accent = Color(argb: currentTheme.accentColor)
        ZStack {
            Color.black.opacity(0.68)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            VStack(alignment: .leading, spacing: 12) {
                Text("🎨 Görsel Tema Seç (6 Dünya)")
                    .font(.system(size: 19, weight: .heavy))
                    .foregroundColor(.white)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        ForEach(GameTheme.allCases) { candidate in
                            let isSelected = (candidate == currentTheme)
                            Button {
                                onSelect(candidate)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text("\(candidate.emoji) \(candidate.titleTr)")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.white)
                                        Text(candidate.subtitleTr)
                                            .font(.system(size: 11))
                                            .foregroundColor(.white.opacity(0.7))
                                    }
                                    Spacer()
                                    HStack(spacing: 4) {
                                        ForEach(0..<min(4, candidate.sandPalette.count), id: \.self) { idx in
                                            Circle()
                                                .fill(Color(argb: candidate.sandPalette[idx]))
                                                .frame(width: 12, height: 12)
                                        }
                                    }
                                }
                                .padding(13)
                                .background(Color(argb: candidate.bgBottom))
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .stroke(
                                            isSelected ? Color(argb: candidate.accentColor) : Color.white.opacity(0.18),
                                            lineWidth: isSelected ? 2.2 : 1.0
                                        )
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }

                Button(action: onDismiss) {
                    Text("Kapat")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(20)
            .frame(maxWidth: 420)
            .frame(maxHeight: 600)
            .background(Color(argb: currentTheme.surfaceCard))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(accent.opacity(0.6), lineWidth: 1.5)
            )
            .padding(.horizontal, 18)
        }
    }
}

private struct HowToPlayModalView: View {
    let theme: GameTheme
    let onDismiss: () -> Void

    var body: some View {
        let accent = Color(argb: theme.accentColor)
        ZStack {
            Color.black.opacity(0.68)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            VStack(alignment: .leading, spacing: 12) {
                Text("📖 Nasıl Oynanır? (v2.0)")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(.white)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 12) {
                        HowToRuleRow(badge: "1️⃣", title: "Sürükle, Döndür & Sakla", bodyText: "Blokları alana sürükle, dokunarak 90° döndür veya sol alttaki 📦 SAKLA yuvasına yedekle.", accent: accent)
                        HowToRuleRow(badge: "2️⃣", title: "Soldan Sağa Birleştir", bodyText: "Aynı renkteki kum sol duvardan sağ duvara kesintisiz köprü kurduğunda patlar, puan ve 🪙 altın kazandırır!", accent: accent)
                        HowToRuleRow(badge: "🔥", title: "Kombo Ateşi (Fever 2X)", bodyText: "Peş peşe hat temizleyerek barı doldur; 10 saniye boyunca tüm skor ve altınlar 2X katlanır!", accent: accent)
                        HowToRuleRow(badge: "🌋", title: "Lav Kumu & Buz Bloklar", bodyText: "🌋 Lav blokları kum, taş ve 2 vuruşluk 🧊 Buz Bloklarını eriterek aşağı süzülür.", accent: accent)
                        HowToRuleRow(badge: "🕹️", title: "4 Farklı Oyun Modu", bodyText: "Klasik Sonsuz, 90 saniyelik Zamana Karşı, yukarıdan düşen bloklarla Sandtrix Akış ve 20 bölümlük Macera!", accent: accent)
                    }
                }

                Button(action: onDismiss) {
                    Text("Anladım, Başlayalım!")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(20)
            .frame(maxWidth: 420)
            .frame(maxHeight: 600)
            .background(Color(argb: theme.surfaceCard))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(accent.opacity(0.6), lineWidth: 1.5)
            )
            .padding(.horizontal, 18)
        }
    }
}

private struct HowToRuleRow: View {
    let badge: String
    let title: String
    let bodyText: String
    let accent: Color

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text(badge)
                .font(.system(size: 20))
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(accent)
                Text(bodyText)
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.82))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
