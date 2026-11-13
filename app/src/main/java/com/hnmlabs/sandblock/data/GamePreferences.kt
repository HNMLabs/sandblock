package com.hnmlabs.sandblock.data

import android.content.Context
import android.content.SharedPreferences
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.GameTheme
import kotlin.math.roundToInt

class GamePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sandblock_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_INITIALIZED_V2, true)
                .putInt(KEY_COINS, 160)
                .putInt(boosterKey(BoosterType.BOMB), 2)
                .putInt(boosterKey(BoosterType.ROW_LASER), 1)
                .putInt(boosterKey(BoosterType.COLOR_CLEANER), 1)
                .putInt(boosterKey(BoosterType.RAINBOW_UPGRADE), 2)
                .putInt(boosterKey(BoosterType.REROLL_TRAY), 2)
                .putInt(KEY_GOLD_BONUS_LEVEL, 0)
                .putInt(KEY_UNLOCKED_LEVEL, 1)
                .putBoolean(KEY_SOUND, true)
                .putBoolean(KEY_HAPTICS, true)
                .putString(KEY_THEME, GameTheme.SAHARA.id)
                .apply()
        } else if (!prefs.getBoolean(KEY_INITIALIZED_V2, false)) {
            // Migrate v1 installs cleanly with starter charges for new shop items
            prefs.edit()
                .putBoolean(KEY_INITIALIZED_V2, true)
                .putInt(boosterKey(BoosterType.ROW_LASER), getBoosterCount(BoosterType.ROW_LASER) + 1)
                .putInt(boosterKey(BoosterType.REROLL_TRAY), getBoosterCount(BoosterType.REROLL_TRAY) + 2)
                .apply()
        }
    }

    var highScore: Int
        get() = prefs.getInt(KEY_HIGH_SCORE, 0)
        set(value) {
            if (value > highScore) {
                prefs.edit().putInt(KEY_HIGH_SCORE, value).apply()
            }
        }

    var coins: Int
        get() = prefs.getInt(KEY_COINS, 160)
        set(value) {
            prefs.edit().putInt(KEY_COINS, value.coerceAtLeast(0)).apply()
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

    fun getLevelStars(levelNumber: Int): Int = prefs.getInt("stars_$levelNumber", 0)

    fun recordLevelWin(levelNumber: Int, stars: Int, rewardCoins: Int, rewardBooster: BoosterType?): Int {
        val prevStars = getLevelStars(levelNumber)
        val firstTimeClear = prevStars == 0
        if (stars > prevStars) {
            prefs.edit().putInt("stars_$levelNumber", stars).apply()
        }
        unlockedLevel = (levelNumber + 1).coerceAtMost(12)
        val baseAward = if (firstTimeClear) rewardCoins else (rewardCoins / 3).coerceAtLeast(10)
        val totalAwarded = awardLiveCoins(baseAward)
        if (firstTimeClear && rewardBooster != null) {
            addBooster(rewardBooster, 1)
        }
        return totalAwarded
    }

    fun totalStarsEarned(): Int = (1..12).sumOf { getLevelStars(it) }

    private fun boosterKey(type: BoosterType): String = "booster_${type.id}"

    companion object {
        const val MAX_GOLD_BONUS_LEVEL = 4
        const val BOMB_BUNDLE_COST = 110
        const val MEGA_BUNDLE_COST = 150
        const val SAND_SWEEP_COST = 80

        private const val KEY_INITIALIZED = "initialized_v1"
        private const val KEY_INITIALIZED_V2 = "initialized_v2_shop"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_COINS = "coins"
        private const val KEY_GOLD_BONUS_LEVEL = "gold_bonus_level"
        private const val KEY_UNLOCKED_LEVEL = "unlocked_level"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_THEME = "selected_theme"
    }
}
