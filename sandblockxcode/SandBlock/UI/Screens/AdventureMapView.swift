import SwiftUI

struct AdventureMapView: View {
    let theme: GameTheme
    @ObservedObject var prefs: GamePreferences
    var onSelectLevel: (AdventureLevel) -> Void
    var onBack: () -> Void

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardBg: Color { Color(argb: theme.surfaceCard) }
    private let levels = AdventureCatalog.levels
    private let maxStars = AdventureCatalog.maxLevels * 3

    var body: some View {
        let totalStars = prefs.totalStarsEarned()
        let unlockedLevel = prefs.unlockedLevel

        ZStack {
            LinearGradient(
                colors: [Color(argb: theme.bgTop), Color(argb: theme.bgBottom)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header Bar
                HStack {
                    Button(action: onBack) {
                        Text("← Menü")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(cardBg)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
                            )
                    }

                    Spacer()

                    Text("MACERA HARİTASI")
                        .font(.system(size: 17, weight: .heavy))
                        .tracking(1.0)
                        .foregroundColor(.white)

                    Spacer()

                    Text("⭐ \(totalStars) / \(maxStars)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(accent)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(cardBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(accent.opacity(0.5), lineWidth: 1)
                        )
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                ScrollView(showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(levels.enumerated()), id: \.element.id) { index, level in
                            let isUnlocked = level.number <= unlockedLevel
                            let stars = prefs.getLevelStars(level.number)
                            let isNewWorld = (index == 0 || levels[index - 1].worldName != level.worldName)

                            if isNewWorld {
                                Text("\(level.theme.emoji) \(level.worldName.uppercased())")
                                    .font(.system(size: 13, weight: .heavy))
                                    .tracking(1.2)
                                    .foregroundColor(Color(argb: level.theme.accentColor))
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                            }

                            AdventureLevelCardView(
                                level: level,
                                isUnlocked: isUnlocked,
                                stars: stars
                            ) {
                                if isUnlocked {
                                    onSelectLevel(level)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
    }
}

private struct AdventureLevelCardView: View {
    let level: AdventureLevel
    let isUnlocked: Bool
    let stars: Int
    let onTap: () -> Void

    var body: some View {
        let worldAccent = Color(argb: level.theme.accentColor)
        let containerColor = isUnlocked
            ? Color(argb: level.theme.surfaceCard)
            : Color(argb: 0xFF1A1A22).opacity(0.72)

        Button(action: onTap) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(isUnlocked ? worldAccent.opacity(0.22) : Color.white.opacity(0.08))
                        .frame(width: 48, height: 48)
                    Text(isUnlocked ? "\(level.number)" : "🔒")
                        .font(.system(size: 18, weight: .heavy))
                        .foregroundColor(isUnlocked ? worldAccent : .white.opacity(0.5))
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(level.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(isUnlocked ? .white : .white.opacity(0.45))
                        if level.requireClearCrackedStones {
                            Text(level.number >= 16 ? "🧊" : "🪨")
                                .font(.system(size: 13))
                        }
                    }

                    Text(level.description)
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(isUnlocked ? 0.75 : 0.38))
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)

                    HStack(spacing: 10) {
                        Text("🎯 \(level.targetScore) Puan")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(worldAccent.opacity(isUnlocked ? 0.92 : 0.4))
                        Text("🪙 +\(level.rewardCoins)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color(argb: 0xFFFFD166).opacity(isUnlocked ? 0.92 : 0.4))
                        if let booster = level.rewardBooster {
                            Text("+1 \(booster.emoji)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.white.opacity(isUnlocked ? 0.92 : 0.4))
                        }
                    }
                    .padding(.top, 2)
                }

                Spacer(minLength: 4)

                VStack(alignment: .trailing, spacing: 6) {
                    HStack(spacing: 2) {
                        ForEach(1...3, id: \.self) { s in
                            Text("★")
                                .font(.system(size: 16))
                                .foregroundColor(s <= stars ? Color(argb: 0xFFFFD166) : .white.opacity(0.22))
                        }
                    }
                    if isUnlocked {
                        Text("OYNA ▶")
                            .font(.system(size: 11, weight: .heavy))
                            .foregroundColor(worldAccent)
                    }
                }
            }
            .padding(16)
            .background(containerColor)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(
                        isUnlocked ? worldAccent.opacity(0.65) : Color.white.opacity(0.12),
                        lineWidth: (isUnlocked && stars == 0) ? 1.8 : 1.0
                    )
            )
        }
        .buttonStyle(.plain)
        .disabled(!isUnlocked)
    }
}
