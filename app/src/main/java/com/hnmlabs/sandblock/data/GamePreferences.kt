package com.hnmlabs.sandblock.data

import android.content.Context
import android.content.SharedPreferences
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.GameTheme

class GamePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sandblock_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putInt(KEY_COINS, 120)
                .putInt(boosterKey(BoosterType.BOMB), 3)
                .putInt(boosterKey(BoosterType.COLOR_CLEANER), 2)
                .putInt(boosterKey(BoosterType.RAINBOW_UPGRADE), 3)
                .putInt(KEY_UNLOCKED_LEVEL, 1)
                .putBoolean(KEY_SOUND, true)
                .putBoolean(KEY_HAPTICS, true)
                .putString(KEY_THEME, GameTheme.SAHARA.id)
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
        get() = prefs.getInt(KEY_COINS, 120)
        set(value) {
            prefs.edit().putInt(KEY_COINS, value.coerceAtLeast(0)).apply()
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

    fun recordLevelWin(levelNumber: Int, stars: Int, rewardCoins: Int, rewardBooster: BoosterType?): Boolean {
        val prevStars = getLevelStars(levelNumber)
        val firstTimeClear = prevStars == 0
        if (stars > prevStars) {
            prefs.edit().putInt("stars_$levelNumber", stars).apply()
        }
        unlockedLevel = (levelNumber + 1).coerceAtMost(12)
        if (firstTimeClear) {
            coins += rewardCoins
            if (rewardBooster != null) {
                addBooster(rewardBooster, 1)
            }
        } else {
            coins += (rewardCoins / 3).coerceAtLeast(10)
        }
        return firstTimeClear
    }

    fun totalStarsEarned(): Int = (1..12).sumOf { getLevelStars(it) }

    private fun boosterKey(type: BoosterType): String = "booster_${type.id}"

    companion object {
        private const val KEY_INITIALIZED = "initialized_v1"
        private const val KEY_HIGH_SCORE = "high_score"
        private const val KEY_COINS = "coins"
        private const val KEY_UNLOCKED_LEVEL = "unlocked_level"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_THEME = "selected_theme"
    }
}
