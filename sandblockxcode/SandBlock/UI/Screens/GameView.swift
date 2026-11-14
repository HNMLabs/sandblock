import SwiftUI
import UIKit

struct GameView: View {
    let initialMode: GameMode
    let initialLevel: AdventureLevel?
    let resumeClassic: Bool
    let selectedTheme: GameTheme
    @ObservedObject var prefs: GamePreferences
    let soundEngine: AsmrSoundEngine
    var onExitToMenu: () -> Void
    var onExitToAdventureMap: () -> Void

    @StateObject private var stageController: SandStageController

    @State private var currentLevel: AdventureLevel?
    @State private var currentMode: GameMode
    @State private var isPaused: Bool = false
    @State private var isShopOpen: Bool = false
    @State private var activeTargetBooster: BoosterType?

    @State private var hud: HudSnapshot
    @State private var didAppearOnce: Bool = false
    @State private var sessionFinalized: Bool = false
    @State private var wonStarsDisplay: Int = 0
    @State private var earnedSessionCoins: Int = 0
    @State private var liveEarnedCoinsInRun: Int = 0
    @State private var isNewHighScore: Bool = false
    @State private var revivesUsed: Int = 0

    init(
        initialMode: GameMode,
        initialLevel: AdventureLevel?,
        resumeClassic: Bool = false,
        selectedTheme: GameTheme,
        prefs: GamePreferences,
        soundEngine: AsmrSoundEngine,
        onExitToMenu: @escaping () -> Void,
        onExitToAdventureMap: @escaping () -> Void
    ) {
        self.initialMode = initialMode
        self.initialLevel = initialLevel
        self.resumeClassic = resumeClassic
        self.selectedTheme = selectedTheme
        self.prefs = prefs
        self.soundEngine = soundEngine
        self.onExitToMenu = onExitToMenu
        self.onExitToAdventureMap = onExitToAdventureMap

        let resolvedMode: GameMode = initialLevel != nil ? .adventure : initialMode
        let snapshotForResume: String? = (initialLevel == nil && resumeClassic && initialMode == .classic)
            ? prefs.loadSavedClassicSnapshot()
            : nil

        let initialWorld = SandWorld()
        if let lvl = initialLevel {
            initialWorld.startAdventure(lvl)
        } else if let snap = snapshotForResume, initialWorld.restoreClassicState(snap) {
            // Restored saved Classic session
        } else {
            initialWorld.startMode(initialMode, selectedTheme: selectedTheme)
        }

        _stageController = StateObject(wrappedValue: SandStageController(world: initialWorld))
        _currentLevel = State(initialValue: initialLevel)
        _currentMode = State(initialValue: resolvedMode)
        _hud = State(
            initialValue: HudSnapshot(
                score: initialWorld.score,
                linesCleared: initialWorld.linesCleared,
                combo: initialWorld.combo,
                movesRemaining: initialWorld.movesRemaining,
                crackedRemaining: (initialLevel?.requireClearCrackedStones == true)
                    ? initialWorld.countRemainingCrackedStones()
                    : 0,
                timeRemainingDeciSec: Int(initialWorld.timeRemainingSec * 10.0),
                feverStep: Int(initialWorld.feverCharge * 20.0),
                isFeverActive: initialWorld.isFeverActive,
                status: initialWorld.status,
                wonStars: 0
            )
        )
    }

    private var world: SandWorld {
        stageController.world
    }

    var body: some View {
        let activeTheme = world.theme
        let accent = Color(argb: activeTheme.accentColor)
        let cardColor = Color(argb: activeTheme.surfaceCard)
        let goldColor = Color(argb: 0xFFFFD166)
        let currentCoins = prefs.coins
        let currentBest = max(modeSavedHighScore(currentMode), currentLevel == nil ? hud.score : 0)

        ZStack {
            LinearGradient(
                colors: [Color(argb: activeTheme.bgTop), Color(argb: activeTheme.bgBottom)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                // 1. Top Game HUD Bar
                HStack(alignment: .center) {
                    Button {
                        soundEngine.playTap()
                        saveClassicIfActive()
                        isPaused = true
                    } label: {
                        Text("⏸")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(cardColor)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Color.white.opacity(0.2), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    VStack(spacing: 1) {
                        if let lvl = currentLevel {
                            Text("BÖLÜM \(lvl.number): \(lvl.title.uppercased())")
                                .font(.system(size: 11, weight: .heavy))
                                .tracking(0.6)
                                .foregroundColor(accent)
                                .lineLimit(1)
                            Text("\(hud.score) / \(lvl.targetScore)")
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(.white)
                        } else {
                            Text("\(hud.score)")
                                .font(.system(size: 24, weight: .black))
                                .foregroundColor(.white)
                            Text("\(currentMode.badgeEmoji) \(currentBest)  •  HAT: \(hud.linesCleared)")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(accent)
                        }
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 2) {
                        Button {
                            guard hud.status == .playing else { return }
                            soundEngine.playTap()
                            activeTargetBooster = nil
                            isShopOpen = true
                        } label: {
                            HStack(spacing: 4) {
                                Text("🛒")
                                    .font(.system(size: 12.5))
                                Text("🪙 \(currentCoins)")
                                    .font(.system(size: 12.5, weight: .heavy))
                                    .foregroundColor(goldColor)
                                Text("+")
                                    .font(.system(size: 13, weight: .black))
                                    .foregroundColor(accent)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(cardColor)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(goldColor.opacity(0.75), lineWidth: 1.4)
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(hud.status != .playing)

                        if hud.combo >= 2 {
                            Text("🔥 x\(hud.combo)")
                                .font(.system(size: 11.5, weight: .heavy))
                                .foregroundColor(Color(argb: 0xFFFFEA00))
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 5)

                // 2. Time Rush Countdown OR Adventure Progress + Fever Meter Strip
                VStack(spacing: 3) {
                    if currentMode == .timeRush {
                        let secs = Double(hud.timeRemainingDeciSec) / 10.0
                        let timeRatio = min(1.0, max(0.0, secs / Double(SandWorld.timeRushInitialSec)))
                        let urgent = secs <= 15.0
                        let barColor = urgent ? Color(argb: 0xFFFF4D4D) : Color(argb: 0xFF00F5D4)

                        HStack {
                            Text("⏱️ ZAMANA KARŞI (Her Hat +Saniye)")
                                .font(.system(size: 10.5, weight: .bold))
                                .foregroundColor(.white.opacity(0.85))
                            Spacer()
                            Text(String(format: "%.1f sn", secs))
                                .font(.system(size: 11.5, weight: .black))
                                .foregroundColor(barColor)
                        }

                        SandProgressBar(
                            progress: timeRatio,
                            fill: barColor,
                            track: Color.white.opacity(0.14),
                            height: 5
                        )
                    }

                    if let lvl = currentLevel {
                        let progress = min(1.0, max(0.0, Double(hud.score) / Double(max(1, lvl.targetScore))))

                        HStack {
                            Text("Hat: \(hud.linesCleared)/\(lvl.targetClears)")
                                .font(.system(size: 10.5, weight: .bold))
                                .foregroundColor(.white.opacity(0.85))

                            Spacer()

                            if lvl.requireClearCrackedStones {
                                Text("🧊 Engel: \(hud.crackedRemaining)")
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundColor(Color(argb: 0xFFFFB703))
                                Spacer()
                            }

                            if let rem = hud.movesRemaining {
                                Text("Hamle: \(rem)")
                                    .font(.system(size: 10.5, weight: .bold))
                                    .foregroundColor(rem <= 5 ? Color(argb: 0xFFFF6B6B) : accent)
                            }
                        }

                        SandProgressBar(
                            progress: progress,
                            fill: accent,
                            track: Color.white.opacity(0.14),
                            height: 5
                        )
                    }

                    // Fever Charge / Active 2X Bar
                    let feverProgress = min(1.0, max(0.0, Double(hud.feverStep) / 20.0))
                    HStack(spacing: 8) {
                        Text(hud.isFeverActive ? "🔥 2X FEVER!" : "🔥 Ateş")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(hud.isFeverActive ? Color(argb: 0xFFFFEA00) : .white.opacity(0.72))

                        SandProgressBar(
                            progress: feverProgress,
                            fill: hud.isFeverActive ? Color(argb: 0xFFFF6D00) : Color(argb: 0xFFFFB703),
                            track: Color.white.opacity(0.12),
                            height: 4
                        )
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 2)

                // 3. 5-Slot Interactive Power-Up & Quick-Buy Bar
                HStack(spacing: 6) {
                    ForEach(BoosterType.allCases) { booster in
                        let count = prefs.getBoosterCount(booster)
                        let isSelected = (activeTargetBooster == booster)
                        let canAfford = count > 0 || currentCoins >= booster.coinCost

                        Button {
                            handleBoosterTapped(booster, canAfford: canAfford)
                        } label: {
                            VStack(spacing: 2) {
                                HStack(spacing: 2) {
                                    Text(booster.emoji)
                                        .font(.system(size: 12.5))
                                    Text(booster.shortTitleTr)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white.opacity(canAfford ? 1.0 : 0.48))
                                        .lineLimit(1)
                                }
                                let badgeText = count > 0 ? "x\(count)" : "🪙\(booster.coinCost)"
                                Text(badgeText)
                                    .font(.system(size: 9.5, weight: .heavy))
                                    .foregroundColor(
                                        count > 0
                                            ? accent
                                            : goldColor.opacity(canAfford ? 0.95 : 0.45)
                                    )
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 5)
                            .padding(.horizontal, 3)
                            .background(isSelected ? accent.opacity(0.30) : cardColor.opacity(0.90))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(
                                        isSelected ? accent : Color.white.opacity(canAfford ? 0.22 : 0.10),
                                        lineWidth: isSelected ? 2 : 1
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(hud.status != .playing)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 3)

                // 4. Custom 60FPS Sand Board + Hold/Tray/Sandtrix Stage View
                SandStageRepresentable(
                    controller: stageController,
                    soundEngine: soundEngine,
                    isPaused: isPaused || isShopOpen,
                    activeTargetBooster: activeTargetBooster,
                    onHudUpdated: { snapshot in
                        handleHudUpdated(snapshot)
                    },
                    onLiveCoinsEarned: { rawCoins in
                        let awarded = prefs.awardLiveCoins(rawCoins)
                        liveEarnedCoinsInRun += awarded
                        return awarded
                    },
                    onTargetBoosterExecuted: { usedBooster in
                        prefs.consumeOrBuyBooster(usedBooster)
                        activeTargetBooster = nil
                    },
                    onPiecePlacedStat: {
                        prefs.recordPiecePlaced()
                    },
                    onLineClearStat: { comboCount, brokenCount in
                        prefs.recordLineClearStats(combo: comboCount, obstaclesBroken: brokenCount)
                    },
                    onAutoSaveCheckpoint: {
                        saveClassicIfActive()
                    }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            // In-Game Gold Shop Modal
            if isShopOpen && hud.status == .playing {
                ShopSheetView(
                    theme: activeTheme,
                    prefs: prefs,
                    soundEngine: soundEngine,
                    inActiveGame: true,
                    canSweepSandNow: world.highestSandRow() < world.height - 6,
                    onExecuteSandSweep: {
                        let cleared = world.clearTopDangerSand()
                        stageController.invalidate()
                        return cleared > 0
                    },
                    onExecuteLavaStorm: {
                        let spawned = world.pourLavaShower()
                        stageController.invalidate()
                        return spawned > 0
                    },
                    onPurchaseChanged: {
                        stageController.invalidate()
                    },
                    onDismiss: {
                        isShopOpen = false
                    }
                )
            }

            // Pause Overlay Modal
            if isPaused && !isShopOpen && hud.status == .playing {
                PauseModalView(
                    theme: activeTheme,
                    soundEnabled: prefs.soundEnabled,
                    musicEnabled: prefs.musicEnabled,
                    hapticsEnabled: prefs.hapticsEnabled,
                    onToggleSound: {
                        let next = !prefs.soundEnabled
                        prefs.soundEnabled = next
                        soundEngine.soundEnabled = next
                        if next { soundEngine.playTap() }
                    },
                    onToggleMusic: {
                        let next = !prefs.musicEnabled
                        prefs.musicEnabled = next
                        soundEngine.updateMusicPreference(next)
                        soundEngine.playTap()
                    },
                    onToggleHaptics: {
                        let next = !prefs.hapticsEnabled
                        prefs.hapticsEnabled = next
                        soundEngine.hapticsEnabled = next
                        soundEngine.playTap()
                    },
                    onOpenShop: {
                        soundEngine.playTap()
                        isPaused = false
                        isShopOpen = true
                    },
                    onResume: {
                        soundEngine.playTap()
                        isPaused = false
                    },
                    onRestart: {
                        soundEngine.playTap()
                        restartGame(nextLevel: currentLevel)
                    },
                    onQuit: {
                        soundEngine.playTap()
                        saveClassicIfActive()
                        if currentLevel != nil {
                            onExitToAdventureMap()
                        } else {
                            onExitToMenu()
                        }
                    }
                )
            }

            // Level Victory Modal
            if hud.status == .won, let lvl = currentLevel {
                VictoryModalView(
                    level: lvl,
                    score: hud.score,
                    stars: max(1, wonStarsDisplay),
                    earnedCoins: earnedSessionCoins,
                    onNextLevel: lvl.number < AdventureCatalog.levels.count
                        ? {
                            soundEngine.playTap()
                            restartGame(nextLevel: AdventureCatalog.getLevel(lvl.number + 1))
                        }
                        : nil,
                    onReplay: {
                        soundEngine.playTap()
                        restartGame(nextLevel: lvl)
                    },
                    onMap: {
                        soundEngine.playTap()
                        onExitToAdventureMap()
                    }
                )
            }

            // Game Over Modal (with Coin Revive!)
            if hud.status == .gameOver {
                let reviveCost = GamePreferences.reviveBaseCost * (revivesUsed + 1)
                let canRevive = revivesUsed < 2 && currentCoins >= reviveCost

                GameOverModalView(
                    theme: activeTheme,
                    modeTitle: currentLevel.map { "Bölüm \($0.number)" } ?? currentMode.titleTr,
                    score: hud.score,
                    bestScore: modeSavedHighScore(currentMode),
                    isNewBest: isNewHighScore,
                    earnedCoins: earnedSessionCoins,
                    isAdventure: currentLevel != nil,
                    showReviveOption: revivesUsed < 2,
                    reviveCost: reviveCost,
                    canAffordRevive: canRevive,
                    onRevive: {
                        if prefs.spendCoins(reviveCost) && world.reviveContinue() {
                            revivesUsed += 1
                            sessionFinalized = false
                            soundEngine.playCoinPurchase()
                            stageController.resetCachedHud()
                            stageController.invalidate()
                        }
                    },
                    onRetry: {
                        soundEngine.playTap()
                        restartGame(nextLevel: currentLevel)
                    },
                    onExit: {
                        soundEngine.playTap()
                        if currentLevel != nil {
                            onExitToAdventureMap()
                        } else {
                            onExitToMenu()
                        }
                    }
                )
            }
        }
        .onAppear {
            guard !didAppearOnce else { return }
            didAppearOnce = true
            if initialLevel == nil && !resumeClassic && initialMode == .classic {
                prefs.clearSavedClassicSnapshot()
            }
            if !resumeClassic {
                prefs.recordGameStarted()
            }
        }
        .onDisappear {
            saveClassicIfActive()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            saveClassicIfActive()
            if hud.status == .playing && !isShopOpen {
                isPaused = true
            }
        }
    }

    // MARK: - Helpers & Session Lifecycle

    private func saveClassicIfActive() {
        if world.mode == .classic && world.status == .playing && world.score > 0 {
            prefs.saveClassicSnapshot(world.serializeClassicState(), currentScore: world.score)
        }
    }

    private func modeSavedHighScore(_ mode: GameMode) -> Int {
        switch mode {
        case .classic: return prefs.highScore
        case .timeRush: return prefs.timeRushHighScore
        case .sandtrix: return prefs.sandtrixHighScore
        case .adventure: return 0
        }
    }

    @discardableResult
    private func updateModeHighScore(_ mode: GameMode, newScore: Int) -> Bool {
        let prev = modeSavedHighScore(mode)
        guard newScore > prev else { return false }
        switch mode {
        case .classic: prefs.highScore = newScore
        case .timeRush: prefs.timeRushHighScore = newScore
        case .sandtrix: prefs.sandtrixHighScore = newScore
        case .adventure: break
        }
        return true
    }

    private func handleBoosterTapped(_ booster: BoosterType, canAfford: Bool) {
        guard hud.status == .playing else { return }

        if activeTargetBooster == booster {
            activeTargetBooster = nil
            soundEngine.playTap()
            return
        }

        if !canAfford {
            soundEngine.playTap()
            isShopOpen = true
            return
        }

        switch booster {
        case .rainbowUpgrade:
            let upgradedSlot = world.upgradeTrayPieceToRainbow()
            if upgradedSlot >= 0 && prefs.consumeOrBuyBooster(booster) {
                activeTargetBooster = nil
                soundEngine.playMagic()
                stageController.invalidate()
            }
        case .rerollTray:
            if world.rerollTray() && prefs.consumeOrBuyBooster(booster) {
                activeTargetBooster = nil
                soundEngine.playCoinPurchase()
                stageController.invalidate()
            }
        case .bomb, .rowLaser, .colorCleaner:
            activeTargetBooster = booster
            soundEngine.playTap()
        }
    }

    private func handleHudUpdated(_ snapshot: HudSnapshot) {
        hud = snapshot
        guard !sessionFinalized, snapshot.status != .playing else { return }

        sessionFinalized = true
        activeTargetBooster = nil
        isShopOpen = false

        if snapshot.status == .won, let lvl = currentLevel {
            let stars = min(3, max(1, snapshot.wonStars))
            wonStarsDisplay = stars
            let bonusAwarded = prefs.recordLevelWin(
                levelNumber: lvl.number,
                stars: stars,
                rewardCoins: lvl.rewardCoins,
                rewardBooster: lvl.rewardBooster
            )
            earnedSessionCoins = liveEarnedCoinsInRun + bonusAwarded
        } else if snapshot.status == .gameOver {
            if currentMode == .classic {
                prefs.clearSavedClassicSnapshot()
            }
            if currentLevel == nil {
                if updateModeHighScore(currentMode, newScore: snapshot.score) {
                    isNewHighScore = true
                }
                let endBonusRaw = max(snapshot.score >= 150 ? 4 : 0, snapshot.score / 80)
                let endBonus = prefs.awardLiveCoins(endBonusRaw)
                prefs.addXp(min(90, max(5, snapshot.score / 55)))
                earnedSessionCoins = liveEarnedCoinsInRun + endBonus
            } else {
                earnedSessionCoins = liveEarnedCoinsInRun
            }
        }
    }

    private func restartGame(nextLevel: AdventureLevel?) {
        currentLevel = nextLevel
        activeTargetBooster = nil
        isPaused = false
        isShopOpen = false
        sessionFinalized = false
        wonStarsDisplay = 0
        earnedSessionCoins = 0
        liveEarnedCoinsInRun = 0
        isNewHighScore = false
        revivesUsed = 0

        if let lvl = nextLevel {
            currentMode = .adventure
            world.startAdventure(lvl)
        } else {
            if currentMode == .classic {
                prefs.clearSavedClassicSnapshot()
            }
            world.startMode(currentMode, selectedTheme: selectedTheme)
        }

        prefs.recordGameStarted()
        stageController.resetCachedHud()
        stageController.invalidate()

        hud = HudSnapshot(
            score: 0,
            linesCleared: 0,
            combo: 0,
            movesRemaining: nextLevel?.maxMoves,
            crackedRemaining: (nextLevel?.requireClearCrackedStones == true)
                ? world.countRemainingCrackedStones()
                : 0,
            timeRemainingDeciSec: Int(world.timeRemainingSec * 10.0),
            feverStep: 0,
            isFeverActive: false,
            status: .playing,
            wonStars: 0
        )
    }
}

// MARK: - Pause, Victory & Game Over Modals

private struct PauseModalView: View {
    let theme: GameTheme
    let soundEnabled: Bool
    let musicEnabled: Bool
    let hapticsEnabled: Bool
    var onToggleSound: () -> Void
    var onToggleMusic: () -> Void
    var onToggleHaptics: () -> Void
    var onOpenShop: () -> Void
    var onResume: () -> Void
    var onRestart: () -> Void
    var onQuit: () -> Void

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardBg: Color { Color(argb: theme.surfaceCard) }
    private let goldColor = Color(argb: 0xFFFFD166)

    var body: some View {
        ZStack {
            Color.black.opacity(0.68)
                .ignoresSafeArea()
                .onTapGesture(perform: onResume)

            VStack(spacing: 10) {
                Text("⏸ OYUN DURAKLATILDI")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(.white)
                    .padding(.bottom, 4)

                HStack(spacing: 6) {
                    toggleChip(title: musicEnabled ? "🎵 Müzik" : "🔕 Kapalı", action: onToggleMusic)
                    toggleChip(title: soundEnabled ? "🔊 Ses" : "🔇 Kapalı", action: onToggleSound)
                    toggleChip(title: hapticsEnabled ? "📳 Titreşim" : "📴 Kapalı", action: onToggleHaptics)
                }

                Button(action: onResume) {
                    Text("▶ Devam Et")
                        .font(.system(size: 16, weight: .heavy))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)

                Button(action: onOpenShop) {
                    Text("🛒 Altın Mağazası")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(goldColor)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.white.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(goldColor.opacity(0.75), lineWidth: 1.2)
                        )
                }
                .buttonStyle(.plain)

                Button(action: onRestart) {
                    Text("🔄 Yeniden Başlat")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.white.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.white.opacity(0.25), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)

                Button(action: onQuit) {
                    Text("🏠 Kaydet & Çık")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white.opacity(0.85))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.white.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(22)
            .background(cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(accent.opacity(0.6), lineWidth: 1.5)
            )
            .padding(.horizontal, 24)
        }
    }

    private func toggleChip(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(Color.white.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(Color.white.opacity(0.24), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

private struct VictoryModalView: View {
    let level: AdventureLevel
    let score: Int
    let stars: Int
    let earnedCoins: Int
    var onNextLevel: (() -> Void)?
    var onReplay: () -> Void
    var onMap: () -> Void

    private var accent: Color { Color(argb: level.theme.accentColor) }
    private var cardBg: Color { Color(argb: level.theme.surfaceCard) }

    var body: some View {
        let rewardText: String = {
            var base = "Kazanılan Altın: 🪙 +\(earnedCoins)"
            if let booster = level.rewardBooster {
                base += "  •  \(booster.emoji) \(booster.titleTr)"
            }
            return base
        }()

        ZStack {
            Color.black.opacity(0.72)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                Text("🎉 BÖLÜM TAMAMLANDI!")
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(accent)
                    .multilineTextAlignment(.center)

                HStack(spacing: 8) {
                    ForEach(1...3, id: \.self) { idx in
                        Text("★")
                            .font(.system(size: 38, weight: .black))
                            .foregroundColor(idx <= stars ? Color(argb: 0xFFFFD166) : Color.white.opacity(0.2))
                    }
                }

                Text("Toplam Skor: \(score)")
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundColor(.white)

                Text(rewardText)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(argb: 0xFFFFD166))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.black.opacity(0.28))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                if let nextAction = onNextLevel {
                    Button(action: nextAction) {
                        Text("Sonraki Bölüm ➔")
                            .font(.system(size: 16, weight: .heavy))
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(accent)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 10) {
                    Button(action: onReplay) {
                        Text("🔄 Tekrar")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Color.white.opacity(0.24), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)

                    Button(action: onMap) {
                        Text("🗺️ Harita")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(Color.white.opacity(0.24), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(24)
            .background(cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .stroke(accent, lineWidth: 2)
            )
            .padding(.horizontal, 24)
        }
    }
}

private struct GameOverModalView: View {
    let theme: GameTheme
    let modeTitle: String
    let score: Int
    let bestScore: Int
    let isNewBest: Bool
    let earnedCoins: Int
    let isAdventure: Bool
    let showReviveOption: Bool
    let reviveCost: Int
    let canAffordRevive: Bool
    var onRevive: () -> Void
    var onRetry: () -> Void
    var onExit: () -> Void

    private var accent: Color { Color(argb: theme.accentColor) }
    private var cardBg: Color { Color(argb: theme.surfaceCard) }
    private var dangerColor: Color { Color(argb: theme.dangerLineColor) }
    private let goldColor = Color(argb: 0xFFFFD166)

    var body: some View {
        ZStack {
            Color.black.opacity(0.72)
                .ignoresSafeArea()

            VStack(spacing: 11) {
                Text(isAdventure ? "⏳ BÖLÜM BAŞARISIZ" : "💥 OYUN BİTTİ")
                    .font(.system(size: 21, weight: .black))
                    .foregroundColor(dangerColor)

                Text(modeTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white.opacity(0.7))

                if isNewBest {
                    Text("🏆 YENİ REKOR!")
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundColor(goldColor)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 4)
                        .background(goldColor.opacity(0.22))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(goldColor, lineWidth: 1)
                        )
                }

                Text("\(score)")
                    .font(.system(size: 36, weight: .black))
                    .foregroundColor(.white)

                if !isAdventure {
                    Text("En Yüksek Skor: \(bestScore)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.white.opacity(0.72))
                }

                if earnedCoins > 0 {
                    Text("Kazanılan Altın: 🪙 +\(earnedCoins)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(goldColor)
                }

                if showReviveOption {
                    Button(action: onRevive) {
                        Text("💖 İkinci Şansla Devam Et (🪙 \(reviveCost))")
                            .font(.system(size: 14, weight: .heavy))
                            .foregroundColor(canAffordRevive ? Color(argb: 0xFF081C15) : .white.opacity(0.42))
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(canAffordRevive ? Color(argb: 0xFF06D6A0) : Color.white.opacity(0.10))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(!canAffordRevive)
                }

                Button(action: onRetry) {
                    Text("🔄 Tekrar Oyna")
                        .font(.system(size: 15.5, weight: .heavy))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(accent)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)

                Button(action: onExit) {
                    Text(isAdventure ? "🗺️ Macera Haritası" : "🏠 Ana Menü")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(Color.white.opacity(0.05))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.white.opacity(0.24), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(24)
            .background(cardBg)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .stroke(dangerColor.opacity(0.7), lineWidth: 1.8)
            )
            .padding(.horizontal, 24)
        }
    }
}
