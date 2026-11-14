package com.hnmlabs.sandblock.model

enum class GameMode(
    val id: String,
    val titleTr: String,
    val badgeEmoji: String
) {
    CLASSIC("classic", "Klasik Sonsuz", "♾️"),
    TIME_RUSH("time_rush", "Zamana Karşı", "⏱️"),
    SANDTRIX("sandtrix", "Sandtrix Akış", "🕹️"),
    ADVENTURE("adventure", "Macera Modu", "🗺️")
}

data class PlayerRankInfo(
    val level: Int,
    val titleTr: String,
    val currentLevelXp: Int,
    val neededLevelXp: Int,
    val progress: Float
)

object PlayerProgression {
    fun calculateRank(totalXp: Int): PlayerRankInfo {
        var remaining = totalXp.coerceAtLeast(0)
        var lvl = 1
        while (true) {
            val needed = xpRequiredForNext(lvl)
            if (remaining < needed) {
                val title = rankTitle(lvl)
                return PlayerRankInfo(
                    level = lvl,
                    titleTr = title,
                    currentLevelXp = remaining,
                    neededLevelXp = needed,
                    progress = (remaining.toFloat() / needed.coerceAtLeast(1)).coerceIn(0f, 1f)
                )
            }
            remaining -= needed
            lvl++
        }
    }

    fun xpRequiredForNext(level: Int): Int = 120 + (level - 1) * 65

    private fun rankTitle(level: Int): String = when {
        level <= 2 -> "Kum Çırağı"
        level <= 4 -> "Çöl Gezgini"
        level <= 7 -> "Vaha Mimarı"
        level <= 11 -> "Fırtına Ustası"
        level <= 16 -> "Magma Lordu"
        else -> "SandBlock Efsanesi"
    }
}

enum class DailyQuestType(
    val id: String,
    val titleTr: String,
    val descTr: String,
    val emoji: String,
    val target: Int,
    val rewardCoins: Int,
    val rewardXp: Int
) {
    CLEAR_LINES(
        id = "dq_lines",
        titleTr = "Kum Temizliği",
        descTr = "Bugün toplam 8 hat temizle",
        emoji = "✨",
        target = 8,
        rewardCoins = 60,
        rewardXp = 80
    ),
    CHAIN_COMBOS(
        id = "dq_combos",
        titleTr = "Kombo Ustası",
        descTr = "Bugün 3 kez 2x veya üzeri kombo yap",
        emoji = "🔥",
        target = 3,
        rewardCoins = 75,
        rewardXp = 100
    ),
    PLACE_BLOCKS(
        id = "dq_blocks",
        titleTr = "Kum Dökücü",
        descTr = "Bugün oyun alanına 35 blok yerleştir",
        emoji = "🧱",
        target = 35,
        rewardCoins = 55,
        rewardXp = 70
    )
}

enum class AchievementDef(
    val id: String,
    val titleTr: String,
    val descTr: String,
    val emoji: String,
    val target: Int,
    val rewardCoins: Int,
    val rewardXp: Int
) {
    FIRST_BRIDGE("ach_first", "İlk Köprü", "İlk kum hattını soldan sağa temizle", "🌉", 1, 40, 50),
    COMBO_3("ach_combo3", "Zincirleme Akış", "Tek hamlede 3x Komboya ulaş", "⚡", 3, 70, 90),
    COMBO_5("ach_combo5", "Kombo Efsanesi", "Tek hamlede 5x Komboya ulaş", "🌋", 5, 130, 160),
    TOTAL_LINES_50("ach_lines50", "Çöl Fırtınası", "Toplamda 50 kum hattı temizle", "🌪️", 50, 95, 120),
    BREAK_OBSTACLES_15("ach_break15", "Kaya & Buz Kırıcı", "Toplam 15 Çatlak Taş veya Buz Bloğu kır", "🧊", 15, 90, 110),
    CLASSIC_5000("ach_score5k", "Altın Zirve", "Klasik modda 5.000 puana ulaş", "🏆", 5000, 110, 140),
    TIME_RUSH_2500("ach_time2500", "Zaman Bükücü", "Zamana Karşı modda 2.500 puana ulaş", "⏱️", 2500, 100, 130),
    SANDTRIX_2500("ach_sandtrix2500", "Refleks Ustası", "Sandtrix Akış modunda 2.500 puana ulaş", "🕹️", 2500, 100, 130),
    ADVENTURE_10("ach_adv10", "Kıta Kaşifi", "Macera modunda 10. bölümü tamamla", "🗺️", 10, 115, 150),
    STARS_30("ach_stars30", "Yıldız Koleksiyoncusu", "Macera haritasında 30 yıldız topla", "⭐", 30, 160, 220)
}
