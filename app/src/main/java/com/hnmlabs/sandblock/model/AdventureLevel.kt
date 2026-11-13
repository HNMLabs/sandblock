package com.hnmlabs.sandblock.model

enum class BoosterType(
    val id: String,
    val titleTr: String,
    val hintTr: String,
    val emoji: String,
    val coinCost: Int
) {
    BOMB(
        id = "bomb",
        titleTr = "TNT Bomba",
        hintTr = "Patlatmak istediğin alana dokun!",
        emoji = "💣",
        coinCost = 50
    ),
    COLOR_CLEANER(
        id = "color_cleaner",
        titleTr = "Renk Silici",
        hintTr = "Tümünü silmek istediğin kum rengine dokun!",
        emoji = "🎨",
        coinCost = 70
    ),
    RAINBOW_UPGRADE(
        id = "rainbow",
        titleTr = "Joker Kum",
        hintTr = "Tepsideki bir bloğu gökkuşağına dönüştürür",
        emoji = "🌈",
        coinCost = 45
    )
}

enum class TerrainPreset {
    NONE,
    CENTER_STEP,
    SIDE_SHELVES,
    CRACKED_BRIDGE,
    OCEAN_FUNNEL,
    TWIN_PILLARS,
    CRACKED_PYRAMID,
    NEON_GATES,
    CYBER_HOURGLASS,
    CRACKED_FORTRESS,
    PASTEL_TERRACES,
    CROWN_LABYRINTH
}

data class AdventureLevel(
    val number: Int,
    val worldName: String,
    val title: String,
    val description: String,
    val theme: GameTheme,
    val activeColors: Int,
    val targetScore: Int,
    val targetClears: Int,
    val requireClearCrackedStones: Boolean,
    val maxMoves: Int,
    val star2Score: Int,
    val star3Score: Int,
    val rewardCoins: Int,
    val rewardBooster: BoosterType?,
    val terrainPreset: TerrainPreset
)

object AdventureCatalog {
    val levels: List<AdventureLevel> = listOf(
        // Bölge 1: Sahra Çölü
        AdventureLevel(
            number = 1,
            worldName = "Sahra Çölü",
            title = "Altın Kumullar",
            description = "Soldan sağa aynı renk kumu birleştirerek 2 hat temizle.",
            theme = GameTheme.SAHARA,
            activeColors = 3,
            targetScore = 600,
            targetClears = 2,
            requireClearCrackedStones = false,
            maxMoves = 22,
            star2Score = 950,
            star3Score = 1400,
            rewardCoins = 35,
            rewardBooster = BoosterType.RAINBOW_UPGRADE,
            terrainPreset = TerrainPreset.NONE
        ),
        AdventureLevel(
            number = 2,
            worldName = "Sahra Çölü",
            title = "Kum Basamağı",
            description = "Ortadaki taş basamağın çevresinden kum akıtarak 3 hat temizle.",
            theme = GameTheme.SAHARA,
            activeColors = 3,
            targetScore = 1000,
            targetClears = 3,
            requireClearCrackedStones = false,
            maxMoves = 24,
            star2Score = 1500,
            star3Score = 2100,
            rewardCoins = 45,
            rewardBooster = null,
            terrainPreset = TerrainPreset.CENTER_STEP
        ),
        AdventureLevel(
            number = 3,
            worldName = "Sahra Çölü",
            title = "Çatlak Kumtaşı",
            description = "Çatlak kayaların yanında hat temizleyerek tüm çatlak taşları kır!",
            theme = GameTheme.SAHARA,
            activeColors = 3,
            targetScore = 1200,
            targetClears = 3,
            requireClearCrackedStones = true,
            maxMoves = 26,
            star2Score = 1800,
            star3Score = 2500,
            rewardCoins = 60,
            rewardBooster = BoosterType.BOMB,
            terrainPreset = TerrainPreset.CRACKED_BRIDGE
        ),

        // Bölge 2: Derin Okyanus
        AdventureLevel(
            number = 4,
            worldName = "Derin Okyanus",
            title = "Mercan Rafları",
            description = "Yan mercan raflarının altını doldur ve 4 hat temizle.",
            theme = GameTheme.OCEAN,
            activeColors = 4,
            targetScore = 1500,
            targetClears = 4,
            requireClearCrackedStones = false,
            maxMoves = 28,
            star2Score = 2200,
            star3Score = 3000,
            rewardCoins = 55,
            rewardBooster = null,
            terrainPreset = TerrainPreset.SIDE_SHELVES
        ),
        AdventureLevel(
            number = 5,
            worldName = "Derin Okyanus",
            title = "Girdap Hunisi",
            description = "Daralan okyanus hunisinden kumları süzerek 1.900 puana ulaş.",
            theme = GameTheme.OCEAN,
            activeColors = 4,
            targetScore = 1900,
            targetClears = 4,
            requireClearCrackedStones = false,
            maxMoves = 28,
            star2Score = 2600,
            star3Score = 3500,
            rewardCoins = 65,
            rewardBooster = BoosterType.COLOR_CLEANER,
            terrainPreset = TerrainPreset.OCEAN_FUNNEL
        ),
        AdventureLevel(
            number = 6,
            worldName = "Derin Okyanus",
            title = "Batık Piramit",
            description = "Batık çatlak piramidi tamamen parçala ve 4 hat temizle!",
            theme = GameTheme.OCEAN,
            activeColors = 4,
            targetScore = 2200,
            targetClears = 4,
            requireClearCrackedStones = true,
            maxMoves = 30,
            star2Score = 3000,
            star3Score = 4000,
            rewardCoins = 75,
            rewardBooster = BoosterType.RAINBOW_UPGRADE,
            terrainPreset = TerrainPreset.CRACKED_PYRAMID
        ),

        // Bölge 3: Neon Gece
        AdventureLevel(
            number = 7,
            worldName = "Neon Gece",
            title = "İkiz Sütunlar",
            description = "İkiz neon sütunların arasından köprü kurarak 5 hat temizle.",
            theme = GameTheme.NEON,
            activeColors = 4,
            targetScore = 2500,
            targetClears = 5,
            requireClearCrackedStones = false,
            maxMoves = 30,
            star2Score = 3400,
            star3Score = 4400,
            rewardCoins = 75,
            rewardBooster = null,
            terrainPreset = TerrainPreset.TWIN_PILLARS
        ),
        AdventureLevel(
            number = 8,
            worldName = "Neon Gece",
            title = "Siber Kum Saati",
            description = "Kum saati geçidinde zincirleme kombolarla 3.000 puana ulaş!",
            theme = GameTheme.NEON,
            activeColors = 4,
            targetScore = 3000,
            targetClears = 5,
            requireClearCrackedStones = false,
            maxMoves = 32,
            star2Score = 4000,
            star3Score = 5200,
            rewardCoins = 85,
            rewardBooster = BoosterType.BOMB,
            terrainPreset = TerrainPreset.CYBER_HOURGLASS
        ),
        AdventureLevel(
            number = 9,
            worldName = "Neon Gece",
            title = "Lazer Kalesi",
            description = "Tüm çatlak siber blokları patlat ve 5 hat temizle!",
            theme = GameTheme.NEON,
            activeColors = 5,
            targetScore = 3200,
            targetClears = 5,
            requireClearCrackedStones = true,
            maxMoves = 34,
            star2Score = 4300,
            star3Score = 5600,
            rewardCoins = 95,
            rewardBooster = BoosterType.COLOR_CLEANER,
            terrainPreset = TerrainPreset.CRACKED_FORTRESS
        ),

        // Bölge 4: Pastel Rüya
        AdventureLevel(
            number = 10,
            worldName = "Pastel Rüya",
            title = "Şeker Terasları",
            description = "5 farklı kum rengiyle terasları aş ve 6 hat temizle.",
            theme = GameTheme.PASTEL,
            activeColors = 5,
            targetScore = 3600,
            targetClears = 6,
            requireClearCrackedStones = false,
            maxMoves = 34,
            star2Score = 4800,
            star3Score = 6200,
            rewardCoins = 100,
            rewardBooster = null,
            terrainPreset = TerrainPreset.PASTEL_TERRACES
        ),
        AdventureLevel(
            number = 11,
            worldName = "Pastel Rüya",
            title = "Lavanta Geçitleri",
            description = "Dar geçitlerde taşmadan 4.200 puana ve 6 hatta ulaş.",
            theme = GameTheme.PASTEL,
            activeColors = 5,
            targetScore = 4200,
            targetClears = 6,
            requireClearCrackedStones = false,
            maxMoves = 35,
            star2Score = 5500,
            star3Score = 7000,
            rewardCoins = 110,
            rewardBooster = BoosterType.RAINBOW_UPGRADE,
            terrainPreset = TerrainPreset.NEON_GATES
        ),
        AdventureLevel(
            number = 12,
            worldName = "Pastel Rüya",
            title = "SandBlock Tacı",
            description = "Büyük final! Tüm çatlak taşları kır, 6 hat temizle ve 4.800 puana ulaş!",
            theme = GameTheme.PASTEL,
            activeColors = 5,
            targetScore = 4800,
            targetClears = 6,
            requireClearCrackedStones = true,
            maxMoves = 36,
            star2Score = 6400,
            star3Score = 8200,
            rewardCoins = 150,
            rewardBooster = BoosterType.BOMB,
            terrainPreset = TerrainPreset.CROWN_LABYRINTH
        )
    )

    fun getLevel(number: Int): AdventureLevel =
        levels.firstOrNull { it.number == number } ?: levels.first()
}
