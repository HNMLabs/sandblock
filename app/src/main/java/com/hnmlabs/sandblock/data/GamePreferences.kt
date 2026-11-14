package com.hnmlabs.sandblock.data

import android.content.Context
import android.content.SharedPreferences
import com.hnmlabs.sandblock.model.AchievementDef
import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.DailyQuestType
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PlayerProgression
import java.time.LocalDate
import kotlin.math.roundToInt

class GamePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sandblock_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_INITIALIZED_V2, true)
                .putBoolean(KEY_INITIALIZED_V3, true)
                .putInt(KEY_COINS, 180)
                .putInt(boosterKey(BoosterType.BOMB), 2)
                .putInt(boosterKey(BoosterType.ROW_LASER), 1)
                .putInt(boosterKey(BoosterType.COLOR_CLEANER), 1)
                .putInt(boosterKey(BoosterType.RAINBOW_UPGRADE), 2)
                .putInt(boosterKey(BoosterType.REROLL_TRAY), 2)
                .putInt(KEY_GOLD_BONUS_LEVEL, 0)
                .putInt(KEY_UNLOCKED_LEVEL, 1)
                .putBoolean(KEY_SOUND, true)
                .putBoolean(KEY_MUSIC, true)
                .putBoolean(KEY_HAPTICS, true)
                .putString(KEY_THEME, GameTheme.SAHARA.id)
                .apply()
        } else {
            if (!prefs.getBoolean(KEY_INITIALIZED_V2, false)) {
                prefs.edit()
                    .putBoolean(KEY_INITIALIZED_V2, true)
                    .putInt(boosterKey(BoosterType.ROW_LASER), getBoosterCount(BoosterType.ROW_LASER) + 1)
                    .putInt(boosterKey(BoosterType.REROLL_TRAY), getBoosterCount(BoosterType.REROLL_TRAY) + 2)
                    .apply()
            }
            if (!prefs.getBoolean(KEY_INITIALIZED_V3, false)) {
                prefs.edit()
                    .putBoolean(KEY_INITIALIZED_V3, true)
                    .putBoolean(KEY_MUSIC, true)
                    .apply()
            }
        }
        ensureDailyQuestsCurrent()
    }

    // --- Scores per Mode ---
    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) {
            if (value > highScore) {
                prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()
            }
        }

    var timeRushHighScore: Int
        get() = prefs.getInt(KEY_TIME_RUSH_HIGH, 0)
        set(value) {
            if (value > timeRushHighScore) {
                prefs.edit().putInt(KEY_TIME_RUSH_HIGH, value).apply()
            }
        }

    var sandtrixHighScore: Int
        get() = prefs.getInt(KEY_SANDTRIX_HIGH, 0)
        set(value) {
            if (value > sandtrixHighScore) {
                prefs.edit().putInt(KEY_SANDTRIX_HIGH, value).apply()
            }
        }

    // --- Economy & XP ---
    var coins: Int
        get() = prefs.getInt(KEY_COINS, 180)
        set(value) {
            prefs.edit().putInt(KEY_COINS, value.coerceAtLeast(0)).apply()
        }

    var totalXp: Int
        get() = prefs.getInt(KEY_TOTAL_XP, 0)
        private set(value) {
            prefs.edit().putInt(KEY_TOTAL_XP, value.coerceAtLeast(0)).apply()
        }

    /**
     * Adds XP and awards +50 coins automatically whenever the player levels up!
     * Returns true if player leveled up.
     */
    fun addXp(amount: Int): Boolean {
        if (amount <= 0) return false
        val beforeLvl = PlayerProgression.calculateRank(totalXp).level
        totalXp += amount
        val afterLvl = PlayerProgression.calculateRank(totalXp).level
        if (afterLvl > beforeLvl) {
            val levelsGained = afterLvl - beforeLvl
            coins += levelsGained * 50
            return true
        }
        return false
    }

    var goldBonusLevel: Int
        get() = prefs.getInt(KEY_GOLD_BONUS_LEVEL, 0).coerceIn(0, MAX_GOLD_BONUS_LEVEL)
        private set(value) {
            prefs.edit().putInt(KEY_GOLD_BONUS_LEVEL, value.coerceIn(0, MAX_GOLD_BONUS_LEVEL)).apply()
        }

    fun getGoldUpgradeCost(): Int {
        val costs = intArrayOf(100, 180, 280, 400)
        val lvl = goldBonusLevel
        return if (lvl < costs.size) costs[lvl] else 0
    }

    fun upgradeGoldBonus(): Boolean {
        val lvl = goldBonusLevel
        if (lvl >= MAX_GOLD_BONUS_LEVEL) return false
        val cost = getGoldUpgradeCost()
        if (coins < cost) return false
        coins -= cost
        goldBonusLevel = lvl + 1
        return true
    }

    fun applyGoldBonus(baseCoins: Int): Int {
        if (baseCoins <= 0) return 0
        val multiplier = 1f + goldBonusLevel * 0.25f
        return (baseCoins * multiplier).roundToInt().coerceAtLeast(1)
    }

    fun awardLiveCoins(rawCoins: Int): Int {
        val finalCoins = applyGoldBonus(rawCoins)
        if (finalCoins > 0) {
            coins += finalCoins
        }
        return finalCoins
    }

    // --- Settings & Theme ---
    var unlockedLevel: Int
        get() = prefs.getInt(KEY_UNLOCKED_LEVEL, 1)
        set(value) {
            if (value > unlockedLevel) {
                prefs.edit().putInt(KEY_UNLOCKED_LEVEL, value).apply()
            }
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
        }

    var musicEnabled: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MUSIC, value).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    var selectedTheme: GameTheme
        get() = GameTheme.fromId(prefs.getString(KEY_THEME, GameTheme.SAHARA.id) ?: GameTheme.SAHARA.id)
        set(value) {
            prefs.edit().putString(KEY_THEME, value.id).apply()
        }

    // --- Boosters & Shop ---
    fun getBoosterCount(type: BoosterType): Int = prefs.getInt(boosterKey(type), 0)

    fun addBooster(type: BoosterType, delta: Int = 1) {
        val next = (getBoosterCount(type) + delta).coerceAtLeast(0)
        prefs.edit().putInt(boosterKey(type), next).apply()
    }

    fun buyBooster(type: BoosterType, quantity: Int = 1, customTotalCost: Int? = null): Boolean {
        val cost = customTotalCost ?: (type.coinCost * quantity)
        if (coins < cost) return false
        coins -= cost
        addBooster(type, quantity)
        return true
    }

    fun buyBombBundle(): Boolean = buyBooster(BoosterType.BOMB, quantity = 3, customTotalCost = BOMB_BUNDLE_COST)

    fun buyMegaBundle(): Boolean {
        if (coins < MEGA_BUNDLE_COST) return false
        coins -= MEGA_BUNDLE_COST
        addBooster(BoosterType.BOMB, 1)
        addBooster(BoosterType.ROW_LASER, 1)
        addBooster(BoosterType.COLOR_CLEANER, 1)
        addBooster(BoosterType.RAINBOW_UPGRADE, 1)
        return true
    }

    fun spendCoins(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        return true
    }

    fun consumeOrBuyBooster(type: BoosterType): Boolean {
        val count = getBoosterCount(type)
        if (count > 0) {
            addBooster(type, -1)
            return true
        }
        if (coins >= type.coinCost) {
            coins -= type.coinCost
            return true
        }
        return false
    }

    // --- Adventure Progress ---
    fun getLevelStars(levelNumber: Int): Int = prefs.getInt("stars_$levelNumber", 0)

    fun recordLevelWin(levelNumber: Int, stars: Int, rewardCoins: Int, rewardBooster: BoosterType?): Int {
        val prevStars = getLevelStars(levelNumber)
        val firstTimeClear = prevStars == 0
        if (stars > prevStars) {
            prefs.edit().putInt("stars_$levelNumber", stars).apply()
        }
        val highestCompleted = prefs.getInt(KEY_HIGHEST_COMPLETED_LEVEL, 0)
        if (levelNumber > highestCompleted) {
            prefs.edit().putInt(KEY_HIGHEST_COMPLETED_LEVEL, levelNumber).apply()
        }
        unlockedLevel = (levelNumber + 1).coerceAtMost(AdventureCatalog.MAX_LEVELS)
        val baseAward = if (firstTimeClear) rewardCoins else (rewardCoins / 3).coerceAtLeast(10)
        val totalAwarded = awardLiveCoins(baseAward)
        addXp(if (firstTimeClear) stars * 45 else 25)
        if (firstTimeClear && rewardBooster != null) {
            addBooster(rewardBooster, 1)
        }
        return totalAwarded
    }

    fun totalStarsEarned(): Int = (1..AdventureCatalog.MAX_LEVELS).sumOf { getLevelStars(it) }

    // --- Lifetime Stats, Daily Quests & Achievements ---
    var totalGamesPlayed: Int
        get() = prefs.getInt(KEY_STAT_GAMES, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_GAMES, value).apply()

    var totalBlocksPlaced: Int
        get() = prefs.getInt(KEY_STAT_BLOCKS, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_BLOCKS, value).apply()

    var totalLinesCleared: Int
        get() = prefs.getInt(KEY_STAT_LINES, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_LINES, value).apply()

    var bestComboEver: Int
        get() = prefs.getInt(KEY_STAT_BEST_COMBO, 0)
        private set(value) {
            if (value > bestComboEver) prefs.edit().putInt(KEY_STAT_BEST_COMBO, value).apply()
        }

    var totalObstaclesBroken: Int
        get() = prefs.getInt(KEY_STAT_OBSTACLES, 0)
        private set(value) = prefs.edit().putInt(KEY_STAT_OBSTACLES, value).apply()

    fun recordGameStarted() {
        totalGamesPlayed += 1
    }

    fun recordPiecePlaced() {
        ensureDailyQuestsCurrent()
        totalBlocksPlaced += 1
        incrementDailyQuest(DailyQuestType.PLACE_BLOCKS, 1)
        addXp(4)
    }

    fun recordLineClearStats(combo: Int, obstaclesBroken: Int) {
        ensureDailyQuestsCurrent()
        totalLinesCleared += 1
        bestComboEver = combo
        if (obstaclesBroken > 0) {
            totalObstaclesBroken += obstaclesBroken
        }
        incrementDailyQuest(DailyQuestType.CLEAR_LINES, 1)
        if (combo >= 2) {
            incrementDailyQuest(DailyQuestType.CHAIN_COMBOS, 1)
        }
        addXp(18 * combo.coerceAtLeast(1) + obstaclesBroken * 10)
    }

    private fun currentEpochDay(): Long = try {
        LocalDate.now().toEpochDay()
    } catch (_: Exception) {
        System.currentTimeMillis() / 86_400_000L
    }

    fun ensureDailyQuestsCurrent() {
        val today = currentEpochDay()
        val storedDay = prefs.getLong(KEY_DAILY_EPOCH_DAY, -1L)
        if (storedDay != today) {
            val ed = prefs.edit().putLong(KEY_DAILY_EPOCH_DAY, today)
                .putBoolean(KEY_DAILY_GIFT_CLAIMED, false)
            for (q in DailyQuestType.entries) {
                ed.putInt("dq_prog_${q.id}", 0)
                ed.putBoolean("dq_claim_${q.id}", false)
            }
            ed.apply()
        }
    }

    fun isDailyGiftAvailable(): Boolean {
        ensureDailyQuestsCurrent()
        return !prefs.getBoolean(KEY_DAILY_GIFT_CLAIMED, false)
    }

    fun claimDailyGift(): Boolean {
        ensureDailyQuestsCurrent()
        if (prefs.getBoolean(KEY_DAILY_GIFT_CLAIMED, false)) return false
        prefs.edit().putBoolean(KEY_DAILY_GIFT_CLAIMED, true).apply()
        coins += DAILY_GIFT_COINS
        addXp(40)
        return true
    }

    private fun incrementDailyQuest(type: DailyQuestType, delta: Int) {
        val key = "dq_prog_${type.id}"
        val next = (prefs.getInt(key, 0) + delta).coerceAtMost(type.target)
        prefs.edit().putInt(key, next).apply()
    }

    fun getDailyQuestProgress(type: DailyQuestType): Int {
        ensureDailyQuestsCurrent()
        return prefs.getInt("dq_prog_${type.id}", 0)
    }

    fun isDailyQuestClaimed(type: DailyQuestType): Boolean {
        ensureDailyQuestsCurrent()
        return prefs.getBoolean("dq_claim_${type.id}", false)
    }

    fun claimDailyQuest(type: DailyQuestType): Boolean {
        ensureDailyQuestsCurrent()
        if (isDailyQuestClaimed(type)) return false
        if (getDailyQuestProgress(type) < type.target) return false
        prefs.edit().putBoolean("dq_claim_${type.id}", true).apply()
        coins += type.rewardCoins
        addXp(type.rewardXp)
        return true
    }

    fun getAchievementProgress(def: AchievementDef): Int = when (def) {
        AchievementDef.FIRST_BRIDGE -> totalLinesCleared.coerceAtMost(def.target)
        AchievementDef.COMBO_3 -> bestComboEver.coerceAtMost(def.target)
        AchievementDef.COMBO_5 -> bestComboEver.coerceAtMost(def.target)
        AchievementDef.TOTAL_LINES_50 -> totalLinesCleared.coerceAtMost(def.target)
        AchievementDef.BREAK_OBSTACLES_15 -> totalObstaclesBroken.coerceAtMost(def.target)
        AchievementDef.CLASSIC_5000 -> highScore.coerceAtMost(def.target)
        AchievementDef.TIME_RUSH_2500 -> timeRushHighScore.coerceAtMost(def.target)
        AchievementDef.SANDTRIX_2500 -> sandtrixHighScore.coerceAtMost(def.target)
        AchievementDef.ADVENTURE_10 -> prefs.getInt(KEY_HIGHEST_COMPLETED_LEVEL, 0).coerceAtMost(def.target)
        AchievementDef.STARS_30 -> totalStarsEarned().coerceAtMost(def.target)
    }

    fun isAchievementClaimed(def: AchievementDef): Boolean =
        prefs.getBoolean("ach_claimed_${def.id}", false)

    fun claimAchievement(def: AchievementDef): Boolean {
        if (isAchievementClaimed(def)) return false
        if (getAchievementProgress(def) < def.target) return false
        prefs.edit().putBoolean("ach_claimed_${def.id}", true).apply()
        coins += def.rewardCoins
        addXp(def.rewardXp)
        return true
    }

    fun countUnclaimedRewards(): Int {
        var ready = 0
        if (isDailyGiftAvailable()) ready++
        for (q in DailyQuestType.entries) {
            if (!isDailyQuestClaimed(q) && getDailyQuestProgress(q) >= q.target) ready++
        }
        for (a in AchievementDef.entries) {
            if (!isAchievementClaimed(a) && getAchievementProgress(a) >= a.target) ready++
        }
        return ready
    }

    // --- Auto-Save & Resume for Classic Mode ---
    fun saveClassicSnapshot(serialized: String, currentScore: Int) {
        prefs.edit()
            .putString(KEY_SAVED_CLASSIC_DATA, serialized)
            .putInt(KEY_SAVED_CLASSIC_SCORE, currentScore)
            .apply()
    }

    fun getSavedClassicScore(): Int? {
        val data = prefs.getString(KEY_SAVED_CLASSIC_DATA, null)
        if (data.isNullOrBlank()) return null
        return prefs.getInt(KEY_SAVED_CLASSIC_SCORE, 0)
    }

    fun loadSavedClassicSnapshot(): String? = prefs.getString(KEY_SAVED_CLASSIC_DATA, null)

    fun clearSavedClassicSnapshot() {
        prefs.edit()
            .remove(KEY_SAVED_CLASSIC_DATA)
            .remove(KEY_SAVED_CLASSIC_SCORE)
            .apply()
    }

    private fun boosterKey(type: BoosterType): String = "booster_${type.id}"

    companion object {
        const val MAX_GOLD_BONUS_LEVEL = 4
        const val BOMB_BUNDLE_COST = 110
        const val MEGA_BUNDLE_COST = 150
        const val SAND_SWEEP_COST = 80
        const val LAVA_STORM_COST = 65
        const val DAILY_GIFT_COINS = 50
        const val REVIVE_BASE_COST = 75

        private const val KEY_INITIALIZED = "initialized_v1"
        private const val KEY_INITIALIZED_V2 = "initialized_v2_shop"
        private const val KEY_INITIALIZED_V3 = "initialized_v3_v2update"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_TIME_RUSH_HIGH = "time_rush_high_score"
        private const val KEY_SANDTRIX_HIGH = "sandtrix_high_score"
        private const val KEY_COINS = "coins"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_GOLD_BONUS_LEVEL = "gold_bonus_level"
        private const val KEY_UNLOCKED_LEVEL = "unlocked_level"
        private const val KEY_HIGHEST_COMPLETED_LEVEL = "highest_completed_level"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_MUSIC = "music_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_THEME = "selected_theme"

        private const val KEY_STAT_GAMES = "stat_games_played"
        private const val KEY_STAT_BLOCKS = "stat_blocks_placed"
        private const val KEY_STAT_LINES = "stat_lines_cleared"
        private const val KEY_STAT_BEST_COMBO = "stat_best_combo"
        private const val KEY_STAT_OBSTACLES = "stat_obstacles_broken"

        private const val KEY_DAILY_EPOCH_DAY = "daily_epoch_day"
        private const val KEY_DAILY_GIFT_CLAIMED = "daily_gift_claimed"

        private const val KEY_SAVED_CLASSIC_DATA = "saved_classic_snapshot_v2"
        private const val KEY_SAVED_CLASSIC_SCORE = "saved_classic_score_v2"
    }
}
