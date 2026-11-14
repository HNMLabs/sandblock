import Combine
import Foundation

final class GamePreferences: ObservableObject {
    static let maxGoldBonusLevel = 4
    static let bombBundleCost = 110
    static let megaBundleCost = 150
    static let sandSweepCost = 80
    static let lavaStormCost = 65
    static let dailyGiftCoins = 50
    static let reviveBaseCost = 75

    private static let keyInitialized = "sb_initialized_v1"
    private static let keyHighScore = "sb_high_score"
    private static let keyTimeRushHigh = "sb_time_rush_high_score"
    private static let keySandtrixHigh = "sb_sandtrix_high_score"
    private static let keyCoins = "sb_coins"
    private static let keyTotalXp = "sb_total_xp"
    private static let keyGoldBonusLevel = "sb_gold_bonus_level"
    private static let keyUnlockedLevel = "sb_unlocked_level"
    private static let keyHighestCompletedLevel = "sb_highest_completed_level"
    private static let keySound = "sb_sound_enabled"
    private static let keyMusic = "sb_music_enabled"
    private static let keyHaptics = "sb_haptics_enabled"
    private static let keyTheme = "sb_selected_theme"

    private static let keyStatGames = "sb_stat_games_played"
    private static let keyStatBlocks = "sb_stat_blocks_placed"
    private static let keyStatLines = "sb_stat_lines_cleared"
    private static let keyStatBestCombo = "sb_stat_best_combo"
    private static let keyStatObstacles = "sb_stat_obstacles_broken"

    private static let keyDailyEpochDay = "sb_daily_epoch_day"
    private static let keyDailyGiftClaimed = "sb_daily_gift_claimed"

    private static let keySavedClassicData = "sb_saved_classic_snapshot_v2"
    private static let keySavedClassicScore = "sb_saved_classic_score_v2"

    private let defaults: UserDefaults

    @Published private(set) var changeToken: Int = 0

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        if !defaults.bool(forKey: Self.keyInitialized) {
            defaults.set(true, forKey: Self.keyInitialized)
            defaults.set(180, forKey: Self.keyCoins)
            defaults.set(2, forKey: boosterKey(.bomb))
            defaults.set(1, forKey: boosterKey(.rowLaser))
            defaults.set(1, forKey: boosterKey(.colorCleaner))
            defaults.set(2, forKey: boosterKey(.rainbowUpgrade))
            defaults.set(2, forKey: boosterKey(.rerollTray))
            defaults.set(0, forKey: Self.keyGoldBonusLevel)
            defaults.set(1, forKey: Self.keyUnlockedLevel)
            defaults.set(true, forKey: Self.keySound)
            defaults.set(true, forKey: Self.keyMusic)
            defaults.set(true, forKey: Self.keyHaptics)
            defaults.set(GameTheme.sahara.id, forKey: Self.keyTheme)
        }
        ensureDailyQuestsCurrent()
    }

    private func notify() {
        changeToken &+= 1
    }

    // MARK: - Scores per Mode

    var highScore: Int {
        get { defaults.integer(forKey: Self.keyHighScore) }
        set {
            if newValue > highScore {
                defaults.set(newValue, forKey: Self.keyHighScore)
                notify()
            }
        }
    }

    var timeRushHighScore: Int {
        get { defaults.integer(forKey: Self.keyTimeRushHigh) }
        set {
            if newValue > timeRushHighScore {
                defaults.set(newValue, forKey: Self.keyTimeRushHigh)
                notify()
            }
        }
    }

    var sandtrixHighScore: Int {
        get { defaults.integer(forKey: Self.keySandtrixHigh) }
        set {
            if newValue > sandtrixHighScore {
                defaults.set(newValue, forKey: Self.keySandtrixHigh)
                notify()
            }
        }
    }

    // MARK: - Economy & XP

    var coins: Int {
        get { defaults.object(forKey: Self.keyCoins) != nil ? defaults.integer(forKey: Self.keyCoins) : 180 }
        set {
            defaults.set(max(0, newValue), forKey: Self.keyCoins)
            notify()
        }
    }

    private(set) var totalXp: Int {
        get { defaults.integer(forKey: Self.keyTotalXp) }
        set {
            defaults.set(max(0, newValue), forKey: Self.keyTotalXp)
            notify()
        }
    }

    @discardableResult
    func addXp(_ amount: Int) -> Bool {
        guard amount > 0 else { return false }
        let beforeLvl = PlayerProgression.calculateRank(totalXp).level
        totalXp += amount
        let afterLvl = PlayerProgression.calculateRank(totalXp).level
        if afterLvl > beforeLvl {
            coins += (afterLvl - beforeLvl) * 50
            return true
        }
        return false
    }

    private(set) var goldBonusLevel: Int {
        get { min(Self.maxGoldBonusLevel, max(0, defaults.integer(forKey: Self.keyGoldBonusLevel))) }
        set {
            defaults.set(min(Self.maxGoldBonusLevel, max(0, newValue)), forKey: Self.keyGoldBonusLevel)
            notify()
        }
    }

    func getGoldUpgradeCost() -> Int {
        let costs = [100, 180, 280, 400]
        let lvl = goldBonusLevel
        return lvl < costs.count ? costs[lvl] : 0
    }

    @discardableResult
    func upgradeGoldBonus() -> Bool {
        let lvl = goldBonusLevel
        guard lvl < Self.maxGoldBonusLevel else { return false }
        let cost = getGoldUpgradeCost()
        guard coins >= cost else { return false }
        coins -= cost
        goldBonusLevel = lvl + 1
        return true
    }

    func applyGoldBonus(_ baseCoins: Int) -> Int {
        guard baseCoins > 0 else { return 0 }
        let multiplier = 1.0 + Double(goldBonusLevel) * 0.25
        return max(1, Int((Double(baseCoins) * multiplier).rounded()))
    }

    @discardableResult
    func awardLiveCoins(_ rawCoins: Int) -> Int {
        let finalCoins = applyGoldBonus(rawCoins)
        if finalCoins > 0 {
            coins += finalCoins
        }
        return finalCoins
    }

    // MARK: - Settings & Theme

    var unlockedLevel: Int {
        get { max(1, defaults.integer(forKey: Self.keyUnlockedLevel)) }
        set {
            if newValue > unlockedLevel {
                defaults.set(newValue, forKey: Self.keyUnlockedLevel)
                notify()
            }
        }
    }

    var soundEnabled: Bool {
        get { defaults.object(forKey: Self.keySound) != nil ? defaults.bool(forKey: Self.keySound) : true }
        set {
            defaults.set(newValue, forKey: Self.keySound)
            notify()
        }
    }

    var musicEnabled: Bool {
        get { defaults.object(forKey: Self.keyMusic) != nil ? defaults.bool(forKey: Self.keyMusic) : true }
        set {
            defaults.set(newValue, forKey: Self.keyMusic)
            notify()
        }
    }

    var hapticsEnabled: Bool {
        get { defaults.object(forKey: Self.keyHaptics) != nil ? defaults.bool(forKey: Self.keyHaptics) : true }
        set {
            defaults.set(newValue, forKey: Self.keyHaptics)
            notify()
        }
    }

    var selectedTheme: GameTheme {
        get { GameTheme.fromId(defaults.string(forKey: Self.keyTheme) ?? GameTheme.sahara.id) }
        set {
            defaults.set(newValue.id, forKey: Self.keyTheme)
            notify()
        }
    }

    // MARK: - Boosters & Shop

    func getBoosterCount(_ type: BoosterType) -> Int {
        defaults.integer(forKey: boosterKey(type))
    }

    func addBooster(_ type: BoosterType, delta: Int = 1) {
        let next = max(0, getBoosterCount(type) + delta)
        defaults.set(next, forKey: boosterKey(type))
        notify()
    }

    @discardableResult
    func buyBooster(_ type: BoosterType, quantity: Int = 1, customTotalCost: Int? = nil) -> Bool {
        let cost = customTotalCost ?? (type.coinCost * quantity)
        guard coins >= cost else { return false }
        coins -= cost
        addBooster(type, delta: quantity)
        return true
    }

    @discardableResult
    func buyBombBundle() -> Bool {
        buyBooster(.bomb, quantity: 3, customTotalCost: Self.bombBundleCost)
    }

    @discardableResult
    func buyMegaBundle() -> Bool {
        guard coins >= Self.megaBundleCost else { return false }
        coins -= Self.megaBundleCost
        addBooster(.bomb, delta: 1)
        addBooster(.rowLaser, delta: 1)
        addBooster(.colorCleaner, delta: 1)
        addBooster(.rainbowUpgrade, delta: 1)
        return true
    }

    @discardableResult
    func spendCoins(_ amount: Int) -> Bool {
        guard coins >= amount else { return false }
        coins -= amount
        return true
    }

    @discardableResult
    func consumeOrBuyBooster(_ type: BoosterType) -> Bool {
        let count = getBoosterCount(type)
        if count > 0 {
            addBooster(type, delta: -1)
            return true
        }
        if coins >= type.coinCost {
            coins -= type.coinCost
            return true
        }
        return false
    }

    // MARK: - Adventure Progress

    func getLevelStars(_ levelNumber: Int) -> Int {
        defaults.integer(forKey: "sb_stars_\(levelNumber)")
    }

    @discardableResult
    func recordLevelWin(
        levelNumber: Int,
        stars: Int,
        rewardCoins: Int,
        rewardBooster: BoosterType?
    ) -> Int {
        let prevStars = getLevelStars(levelNumber)
        let firstTimeClear = (prevStars == 0)
        if stars > prevStars {
            defaults.set(stars, forKey: "sb_stars_\(levelNumber)")
        }
        let highestCompleted = defaults.integer(forKey: Self.keyHighestCompletedLevel)
        if levelNumber > highestCompleted {
            defaults.set(levelNumber, forKey: Self.keyHighestCompletedLevel)
        }
        unlockedLevel = min(AdventureCatalog.maxLevels, levelNumber + 1)
        let baseAward = firstTimeClear ? rewardCoins : max(10, rewardCoins / 3)
        let totalAwarded = awardLiveCoins(baseAward)
        addXp(firstTimeClear ? stars * 45 : 25)
        if firstTimeClear, let booster = rewardBooster {
            addBooster(booster, delta: 1)
        }
        notify()
        return totalAwarded
    }

    func totalStarsEarned() -> Int {
        (1...AdventureCatalog.maxLevels).reduce(0) { $0 + getLevelStars($1) }
    }

    // MARK: - Lifetime Stats, Daily Quests & Achievements

    private(set) var totalGamesPlayed: Int {
        get { defaults.integer(forKey: Self.keyStatGames) }
        set {
            defaults.set(newValue, forKey: Self.keyStatGames)
            notify()
        }
    }

    private(set) var totalBlocksPlaced: Int {
        get { defaults.integer(forKey: Self.keyStatBlocks) }
        set {
            defaults.set(newValue, forKey: Self.keyStatBlocks)
            notify()
        }
    }

    private(set) var totalLinesCleared: Int {
        get { defaults.integer(forKey: Self.keyStatLines) }
        set {
            defaults.set(newValue, forKey: Self.keyStatLines)
            notify()
        }
    }

    private(set) var bestComboEver: Int {
        get { defaults.integer(forKey: Self.keyStatBestCombo) }
        set {
            if newValue > bestComboEver {
                defaults.set(newValue, forKey: Self.keyStatBestCombo)
                notify()
            }
        }
    }

    private(set) var totalObstaclesBroken: Int {
        get { defaults.integer(forKey: Self.keyStatObstacles) }
        set {
            defaults.set(newValue, forKey: Self.keyStatObstacles)
            notify()
        }
    }

    func recordGameStarted() {
        totalGamesPlayed += 1
    }

    func recordPiecePlaced() {
        ensureDailyQuestsCurrent()
        totalBlocksPlaced += 1
        incrementDailyQuest(.placeBlocks, delta: 1)
        addXp(4)
    }

    func recordLineClearStats(combo: Int, obstaclesBroken: Int) {
        ensureDailyQuestsCurrent()
        totalLinesCleared += 1
        bestComboEver = combo
        if obstaclesBroken > 0 {
            totalObstaclesBroken += obstaclesBroken
        }
        incrementDailyQuest(.clearLines, delta: 1)
        if combo >= 2 {
            incrementDailyQuest(.chainCombos, delta: 1)
        }
        addXp(18 * max(1, combo) + obstaclesBroken * 10)
    }

    private func currentEpochDay() -> Int {
        let start = Calendar.current.startOfDay(for: Date())
        return Int(start.timeIntervalSince1970 / 86_400)
    }

    func ensureDailyQuestsCurrent() {
        let today = currentEpochDay()
        let storedDay = defaults.object(forKey: Self.keyDailyEpochDay) != nil
            ? defaults.integer(forKey: Self.keyDailyEpochDay)
            : -1
        if storedDay != today {
            defaults.set(today, forKey: Self.keyDailyEpochDay)
            defaults.set(false, forKey: Self.keyDailyGiftClaimed)
            for q in DailyQuestType.allCases {
                defaults.set(0, forKey: "sb_dq_prog_\(q.id)")
                defaults.set(false, forKey: "sb_dq_claim_\(q.id)")
            }
        }
    }

    func isDailyGiftAvailable() -> Bool {
        ensureDailyQuestsCurrent()
        return !defaults.bool(forKey: Self.keyDailyGiftClaimed)
    }

    @discardableResult
    func claimDailyGift() -> Bool {
        ensureDailyQuestsCurrent()
        if defaults.bool(forKey: Self.keyDailyGiftClaimed) { return false }
        defaults.set(true, forKey: Self.keyDailyGiftClaimed)
        coins += Self.dailyGiftCoins
        addXp(40)
        notify()
        return true
    }

    private func incrementDailyQuest(_ type: DailyQuestType, delta: Int) {
        let key = "sb_dq_prog_\(type.id)"
        let next = min(type.target, defaults.integer(forKey: key) + delta)
        defaults.set(next, forKey: key)
    }

    func getDailyQuestProgress(_ type: DailyQuestType) -> Int {
        ensureDailyQuestsCurrent()
        return defaults.integer(forKey: "sb_dq_prog_\(type.id)")
    }

    func isDailyQuestClaimed(_ type: DailyQuestType) -> Bool {
        ensureDailyQuestsCurrent()
        return defaults.bool(forKey: "sb_dq_claim_\(type.id)")
    }

    @discardableResult
    func claimDailyQuest(_ type: DailyQuestType) -> Bool {
        ensureDailyQuestsCurrent()
        if isDailyQuestClaimed(type) { return false }
        if getDailyQuestProgress(type) < type.target { return false }
        defaults.set(true, forKey: "sb_dq_claim_\(type.id)")
        coins += type.rewardCoins
        addXp(type.rewardXp)
        notify()
        return true
    }

    func getAchievementProgress(_ def: AchievementDef) -> Int {
        switch def {
        case .firstBridge: return min(def.target, totalLinesCleared)
        case .combo3: return min(def.target, bestComboEver)
        case .combo5: return min(def.target, bestComboEver)
        case .totalLines50: return min(def.target, totalLinesCleared)
        case .breakObstacles15: return min(def.target, totalObstaclesBroken)
        case .classic5000: return min(def.target, highScore)
        case .timeRush2500: return min(def.target, timeRushHighScore)
        case .sandtrix2500: return min(def.target, sandtrixHighScore)
        case .adventure10: return min(def.target, defaults.integer(forKey: Self.keyHighestCompletedLevel))
        case .stars30: return min(def.target, totalStarsEarned())
        }
    }

    func isAchievementClaimed(_ def: AchievementDef) -> Bool {
        defaults.bool(forKey: "sb_ach_claimed_\(def.id)")
    }

    @discardableResult
    func claimAchievement(_ def: AchievementDef) -> Bool {
        if isAchievementClaimed(def) { return false }
        if getAchievementProgress(def) < def.target { return false }
        defaults.set(true, forKey: "sb_ach_claimed_\(def.id)")
        coins += def.rewardCoins
        addXp(def.rewardXp)
        notify()
        return true
    }

    func countUnclaimedRewards() -> Int {
        var ready = 0
        if isDailyGiftAvailable() { ready += 1 }
        for q in DailyQuestType.allCases {
            if !isDailyQuestClaimed(q) && getDailyQuestProgress(q) >= q.target {
                ready += 1
            }
        }
        for a in AchievementDef.allCases {
            if !isAchievementClaimed(a) && getAchievementProgress(a) >= a.target {
                ready += 1
            }
        }
        return ready
    }

    // MARK: - Auto-Save & Resume for Classic Mode

    func saveClassicSnapshot(_ serialized: String, currentScore: Int) {
        defaults.set(serialized, forKey: Self.keySavedClassicData)
        defaults.set(currentScore, forKey: Self.keySavedClassicScore)
        notify()
    }

    func getSavedClassicScore() -> Int? {
        guard let data = defaults.string(forKey: Self.keySavedClassicData), !data.isEmpty else {
            return nil
        }
        return defaults.integer(forKey: Self.keySavedClassicScore)
    }

    func loadSavedClassicSnapshot() -> String? {
        defaults.string(forKey: Self.keySavedClassicData)
    }

    func clearSavedClassicSnapshot() {
        defaults.removeObject(forKey: Self.keySavedClassicData)
        defaults.removeObject(forKey: Self.keySavedClassicScore)
        notify()
    }

    private func boosterKey(_ type: BoosterType) -> String {
        "sb_booster_\(type.id)"
    }
}
