import Foundation

enum BoosterType: String, CaseIterable, Identifiable, Codable {
    case bomb = "bomb"
    case rowLaser = "row_laser"
    case colorCleaner = "color_cleaner"
    case rainbowUpgrade = "rainbow"
    case rerollTray = "reroll_tray"

    var id: String { rawValue }

    var shortTitleTr: String {
        switch self {
        case .bomb: return "TNT"
        case .rowLaser: return "Lazer"
        case .colorCleaner: return "Renk"
        case .rainbowUpgrade: return "Joker"
        case .rerollTray: return "Yenile"
        }
    }

    var titleTr: String {
        switch self {
        case .bomb: return "TNT Bomba"
        case .rowLaser: return "Yatay Lazer"
        case .colorCleaner: return "Renk Silici"
        case .rainbowUpgrade: return "Joker Kum"
        case .rerollTray: return "Tepsi Yenile"
        }
    }

    var descTr: String {
        switch self {
        case .bomb: return "Dokunduğun noktada dev bir krater patlatır, taş ve buzları kırar."
        case .rowLaser: return "Seçtiğin satırı boydan boya lazerle kesip üstteki kumları çökertir."
        case .colorCleaner: return "Dokunduğun renkteki tüm kum tanelerini haritadan siler."
        case .rainbowUpgrade: return "Tepsideki bir bloğu her renkle birleşen Gökkuşağı kumuna çevirir."
        case .rerollTray: return "Alttaki blokları anında yeni şekil ve renklerle değiştirir."
        }
    }

    var hintTr: String {
        switch self {
        case .bomb: return "💣 Patlatmak istediğin alana dokun!"
        case .rowLaser: return "⚡ Lazerle kesmek istediğin satıra dokun!"
        case .colorCleaner: return "🎨 Tümünü silmek istediğin kum rengine dokun!"
        case .rainbowUpgrade: return "🌈 Tepsideki bloğu gökkuşağına dönüştürür"
        case .rerollTray: return "🔄 Blokları yeniler"
        }
    }

    var emoji: String {
        switch self {
        case .bomb: return "💣"
        case .rowLaser: return "⚡"
        case .colorCleaner: return "🎨"
        case .rainbowUpgrade: return "🌈"
        case .rerollTray: return "🔄"
        }
    }

    var coinCost: Int {
        switch self {
        case .bomb: return 45
        case .rowLaser: return 55
        case .colorCleaner: return 65
        case .rainbowUpgrade: return 40
        case .rerollTray: return 30
        }
    }
}

enum TerrainPreset: CaseIterable, Equatable {
    case none
    case centerStep
    case sideShelves
    case crackedBridge
    case oceanFunnel
    case twinPillars
    case crackedPyramid
    case neonGates
    case cyberHourglass
    case crackedFortress
    case pastelTerraces
    case crownLabyrinth
    case volcanoCaldera
    case magmaChambers
    case obsidianArch
    case eruptionCore
    case frostWall
    case glacierSteps
    case auroraSanctuary
    case eternalThrone
}

struct AdventureLevel: Identifiable, Equatable {
    let number: Int
    let worldName: String
    let title: String
    let description: String
    let theme: GameTheme
    let activeColors: Int
    let targetScore: Int
    let targetClears: Int
    let requireClearCrackedStones: Bool
    let maxMoves: Int
    let star2Score: Int
    let star3Score: Int
    let rewardCoins: Int
    let rewardBooster: BoosterType?
    let terrainPreset: TerrainPreset

    var id: Int { number }
}

enum AdventureCatalog {
    static let maxLevels = 20

    static let levels: [AdventureLevel] = [
        // Bölge 1: Sahra Çölü (1–3)
        AdventureLevel(
            number: 1,
            worldName: "Sahra Çölü",
            title: "Altın Kumullar",
            description: "Soldan sağa aynı renk kumu birleştirerek 2 hat temizle.",
            theme: .sahara,
            activeColors: 3,
            targetScore: 600,
            targetClears: 2,
            requireClearCrackedStones: false,
            maxMoves: 22,
            star2Score: 950,
            star3Score: 1400,
            rewardCoins: 40,
            rewardBooster: .rainbowUpgrade,
            terrainPreset: .none
        ),
        AdventureLevel(
            number: 2,
            worldName: "Sahra Çölü",
            title: "Kum Basamağı",
            description: "Ortadaki taş basamağın çevresinden kum akıtarak 3 hat temizle.",
            theme: .sahara,
            activeColors: 3,
            targetScore: 1000,
            targetClears: 3,
            requireClearCrackedStones: false,
            maxMoves: 24,
            star2Score: 1500,
            star3Score: 2100,
            rewardCoins: 45,
            rewardBooster: nil,
            terrainPreset: .centerStep
        ),
        AdventureLevel(
            number: 3,
            worldName: "Sahra Çölü",
            title: "Çatlak Kumtaşı",
            description: "Çatlak kayaların yanında hat temizleyerek tüm çatlak taşları kır!",
            theme: .sahara,
            activeColors: 3,
            targetScore: 1200,
            targetClears: 3,
            requireClearCrackedStones: true,
            maxMoves: 26,
            star2Score: 1800,
            star3Score: 2500,
            rewardCoins: 60,
            rewardBooster: .bomb,
            terrainPreset: .crackedBridge
        ),

        // Bölge 2: Derin Okyanus (4–6)
        AdventureLevel(
            number: 4,
            worldName: "Derin Okyanus",
            title: "Mercan Rafları",
            description: "Yan mercan raflarının altını doldur ve 4 hat temizle.",
            theme: .ocean,
            activeColors: 4,
            targetScore: 1500,
            targetClears: 4,
            requireClearCrackedStones: false,
            maxMoves: 28,
            star2Score: 2200,
            star3Score: 3000,
            rewardCoins: 55,
            rewardBooster: nil,
            terrainPreset: .sideShelves
        ),
        AdventureLevel(
            number: 5,
            worldName: "Derin Okyanus",
            title: "Girdap Hunisi",
            description: "Daralan okyanus hunisinden kumları süzerek 1.900 puana ulaş.",
            theme: .ocean,
            activeColors: 4,
            targetScore: 1900,
            targetClears: 4,
            requireClearCrackedStones: false,
            maxMoves: 28,
            star2Score: 2600,
            star3Score: 3500,
            rewardCoins: 65,
            rewardBooster: .colorCleaner,
            terrainPreset: .oceanFunnel
        ),
        AdventureLevel(
            number: 6,
            worldName: "Derin Okyanus",
            title: "Batık Piramit",
            description: "Batık çatlak piramidi tamamen parçala ve 4 hat temizle!",
            theme: .ocean,
            activeColors: 4,
            targetScore: 2200,
            targetClears: 4,
            requireClearCrackedStones: true,
            maxMoves: 30,
            star2Score: 3000,
            star3Score: 4000,
            rewardCoins: 75,
            rewardBooster: .rainbowUpgrade,
            terrainPreset: .crackedPyramid
        ),

        // Bölge 3: Neon Gece (7–9)
        AdventureLevel(
            number: 7,
            worldName: "Neon Gece",
            title: "İkiz Sütunlar",
            description: "İkiz neon sütunların arasından köprü kurarak 5 hat temizle.",
            theme: .neon,
            activeColors: 4,
            targetScore: 2500,
            targetClears: 5,
            requireClearCrackedStones: false,
            maxMoves: 30,
            star2Score: 3400,
            star3Score: 4400,
            rewardCoins: 75,
            rewardBooster: nil,
            terrainPreset: .twinPillars
        ),
        AdventureLevel(
            number: 8,
            worldName: "Neon Gece",
            title: "Siber Kum Saati",
            description: "Kum saati geçidinde zincirleme kombolarla 3.000 puana ulaş!",
            theme: .neon,
            activeColors: 4,
            targetScore: 3000,
            targetClears: 5,
            requireClearCrackedStones: false,
            maxMoves: 32,
            star2Score: 4000,
            star3Score: 5200,
            rewardCoins: 85,
            rewardBooster: .rowLaser,
            terrainPreset: .cyberHourglass
        ),
        AdventureLevel(
            number: 9,
            worldName: "Neon Gece",
            title: "Lazer Kalesi",
            description: "Tüm çatlak siber blokları patlat ve 5 hat temizle!",
            theme: .neon,
            activeColors: 5,
            targetScore: 3200,
            targetClears: 5,
            requireClearCrackedStones: true,
            maxMoves: 34,
            star2Score: 4300,
            star3Score: 5600,
            rewardCoins: 95,
            rewardBooster: .colorCleaner,
            terrainPreset: .crackedFortress
        ),

        // Bölge 4: Pastel Rüya (10–12)
        AdventureLevel(
            number: 10,
            worldName: "Pastel Rüya",
            title: "Şeker Terasları",
            description: "5 farklı kum rengiyle terasları aş ve 6 hat temizle.",
            theme: .pastel,
            activeColors: 5,
            targetScore: 3600,
            targetClears: 6,
            requireClearCrackedStones: false,
            maxMoves: 34,
            star2Score: 4800,
            star3Score: 6200,
            rewardCoins: 100,
            rewardBooster: nil,
            terrainPreset: .pastelTerraces
        ),
        AdventureLevel(
            number: 11,
            worldName: "Pastel Rüya",
            title: "Lavanta Geçitleri",
            description: "Dar geçitlerde taşmadan 4.200 puana ve 6 hatta ulaş.",
            theme: .pastel,
            activeColors: 5,
            targetScore: 4200,
            targetClears: 6,
            requireClearCrackedStones: false,
            maxMoves: 35,
            star2Score: 5500,
            star3Score: 7000,
            rewardCoins: 110,
            rewardBooster: .rainbowUpgrade,
            terrainPreset: .neonGates
        ),
        AdventureLevel(
            number: 12,
            worldName: "Pastel Rüya",
            title: "Rüya Labirenti",
            description: "Tüm çatlak blokları kır ve 6 hat temizle!",
            theme: .pastel,
            activeColors: 5,
            targetScore: 4600,
            targetClears: 6,
            requireClearCrackedStones: true,
            maxMoves: 36,
            star2Score: 6200,
            star3Score: 7800,
            rewardCoins: 125,
            rewardBooster: .bomb,
            terrainPreset: .crownLabyrinth
        ),

        // Bölge 5: Volkanik Magma (13–16)
        AdventureLevel(
            number: 13,
            worldName: "Volkanik Magma",
            title: "Krater Ağzı",
            description: "Volkanik kaldera çukurunda kumları birleştirerek 6 hat temizle.",
            theme: .volcano,
            activeColors: 5,
            targetScore: 5000,
            targetClears: 6,
            requireClearCrackedStones: false,
            maxMoves: 36,
            star2Score: 6600,
            star3Score: 8400,
            rewardCoins: 130,
            rewardBooster: .rerollTray,
            terrainPreset: .volcanoCaldera
        ),
        AdventureLevel(
            number: 14,
            worldName: "Volkanik Magma",
            title: "Magma Odaları",
            description: "Obsidyen bölmelerdeki tüm çatlak kayaları parçala!",
            theme: .volcano,
            activeColors: 5,
            targetScore: 5400,
            targetClears: 6,
            requireClearCrackedStones: true,
            maxMoves: 36,
            star2Score: 7000,
            star3Score: 9000,
            rewardCoins: 140,
            rewardBooster: .rowLaser,
            terrainPreset: .magmaChambers
        ),
        AdventureLevel(
            number: 15,
            worldName: "Volkanik Magma",
            title: "Obsidyen Kemer",
            description: "Dev obsidyen kemerin altından kum akıtarak 7 hat temizle.",
            theme: .volcano,
            activeColors: 5,
            targetScore: 5800,
            targetClears: 7,
            requireClearCrackedStones: false,
            maxMoves: 38,
            star2Score: 7600,
            star3Score: 9600,
            rewardCoins: 150,
            rewardBooster: nil,
            terrainPreset: .obsidianArch
        ),
        AdventureLevel(
            number: 16,
            worldName: "Volkanik Magma",
            title: "Yanardağ Çekirdeği",
            description: "Magma çekirdeğindeki tüm çatlak taş ve buz engelleri yok et!",
            theme: .volcano,
            activeColors: 5,
            targetScore: 6200,
            targetClears: 7,
            requireClearCrackedStones: true,
            maxMoves: 38,
            star2Score: 8200,
            star3Score: 10400,
            rewardCoins: 165,
            rewardBooster: .bomb,
            terrainPreset: .eruptionCore
        ),

        // Bölge 6: Kuzey Işıkları — Buzul Zirvesi (17–20)
        AdventureLevel(
            number: 17,
            worldName: "Kuzey Işıkları",
            title: "Buzul Duvarı",
            description: "2 katmanlı 🧊 Buz Bloklarının yanında hat temizleyerek buzları erit!",
            theme: .aurora,
            activeColors: 5,
            targetScore: 6600,
            targetClears: 7,
            requireClearCrackedStones: true,
            maxMoves: 38,
            star2Score: 8600,
            star3Score: 11000,
            rewardCoins: 175,
            rewardBooster: .rainbowUpgrade,
            terrainPreset: .frostWall
        ),
        AdventureLevel(
            number: 18,
            worldName: "Kuzey Işıkları",
            title: "Kristal Merdiven",
            description: "Buzul basamaklarını eriterek 7 hat ve 7.200 puana ulaş!",
            theme: .aurora,
            activeColors: 5,
            targetScore: 7200,
            targetClears: 7,
            requireClearCrackedStones: true,
            maxMoves: 40,
            star2Score: 9400,
            star3Score: 11800,
            rewardCoins: 185,
            rewardBooster: .colorCleaner,
            terrainPreset: .glacierSteps
        ),
        AdventureLevel(
            number: 19,
            worldName: "Kuzey Işıkları",
            title: "Aurora Tapınağı",
            description: "Buz ve taş tapınakta 8 hat temizleyerek tüm buzulları kır!",
            theme: .aurora,
            activeColors: 5,
            targetScore: 7800,
            targetClears: 8,
            requireClearCrackedStones: true,
            maxMoves: 40,
            star2Score: 10200,
            star3Score: 12800,
            rewardCoins: 200,
            rewardBooster: .rowLaser,
            terrainPreset: .auroraSanctuary
        ),
        AdventureLevel(
            number: 20,
            worldName: "Kuzey Işıkları",
            title: "Sonsuzluk Tacı",
            description: "Büyük Final! Tüm buzul ve çatlak kayaları yok et, 8.500 puana ulaş!",
            theme: .aurora,
            activeColors: 5,
            targetScore: 8500,
            targetClears: 8,
            requireClearCrackedStones: true,
            maxMoves: 42,
            star2Score: 11200,
            star3Score: 14000,
            rewardCoins: 250,
            rewardBooster: .bomb,
            terrainPreset: .eternalThrone
        )
    ]

    static func getLevel(_ number: Int) -> AdventureLevel {
        levels.first(where: { $0.number == number }) ?? levels[0]
    }
}
