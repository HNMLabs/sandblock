import Foundation

enum GameMode: String, CaseIterable, Identifiable, Codable {
    case classic = "classic"
    case timeRush = "time_rush"
    case sandtrix = "sandtrix"
    case adventure = "adventure"

    var id: String { rawValue }

    var titleTr: String {
        switch self {
        case .classic: return "Klasik Sonsuz"
        case .timeRush: return "Zamana Karşı"
        case .sandtrix: return "Sandtrix Akış"
        case .adventure: return "Macera Modu"
        }
    }

    var badgeEmoji: String {
        switch self {
        case .classic: return "♾️"
        case .timeRush: return "⏱️"
        case .sandtrix: return "🕹️"
        case .adventure: return "🗺️"
        }
    }
}

struct PlayerRankInfo: Equatable {
    let level: Int
    let titleTr: String
    let currentLevelXp: Int
    let neededLevelXp: Int
    let progress: Double
}

enum PlayerProgression {
    static func calculateRank(_ totalXp: Int) -> PlayerRankInfo {
        var remaining = max(0, totalXp)
        var lvl = 1
        while true {
            let needed = xpRequiredForNext(lvl)
            if remaining < needed {
                let ratio = Double(remaining) / Double(max(1, needed))
                return PlayerRankInfo(
                    level: lvl,
                    titleTr: rankTitle(lvl),
                    currentLevelXp: remaining,
                    neededLevelXp: needed,
                    progress: min(1.0, max(0.0, ratio))
                )
            }
            remaining -= needed
            lvl += 1
        }
    }

    static func xpRequiredForNext(_ level: Int) -> Int {
        120 + (level - 1) * 65
    }

    private static func rankTitle(_ level: Int) -> String {
        switch level {
        case ...2: return "Kum Çırağı"
        case 3...4: return "Çöl Gezgini"
        case 5...7: return "Vaha Mimarı"
        case 8...11: return "Fırtına Ustası"
        case 12...16: return "Magma Lordu"
        default: return "SandBlock Efsanesi"
        }
    }
}

enum DailyQuestType: String, CaseIterable, Identifiable {
    case clearLines = "dq_lines"
    case chainCombos = "dq_combos"
    case placeBlocks = "dq_blocks"

    var id: String { rawValue }

    var titleTr: String {
        switch self {
        case .clearLines: return "Kum Temizliği"
        case .chainCombos: return "Kombo Ustası"
        case .placeBlocks: return "Kum Dökücü"
        }
    }

    var descTr: String {
        switch self {
        case .clearLines: return "Bugün toplam 8 hat temizle"
        case .chainCombos: return "Bugün 3 kez 2x veya üzeri kombo yap"
        case .placeBlocks: return "Bugün oyun alanına 35 blok yerleştir"
        }
    }

    var emoji: String {
        switch self {
        case .clearLines: return "✨"
        case .chainCombos: return "🔥"
        case .placeBlocks: return "🧱"
        }
    }

    var target: Int {
        switch self {
        case .clearLines: return 8
        case .chainCombos: return 3
        case .placeBlocks: return 35
        }
    }

    var rewardCoins: Int {
        switch self {
        case .clearLines: return 60
        case .chainCombos: return 75
        case .placeBlocks: return 55
        }
    }

    var rewardXp: Int {
        switch self {
        case .clearLines: return 80
        case .chainCombos: return 100
        case .placeBlocks: return 70
        }
    }
}

enum AchievementDef: String, CaseIterable, Identifiable {
    case firstBridge = "ach_first"
    case combo3 = "ach_combo3"
    case combo5 = "ach_combo5"
    case totalLines50 = "ach_lines50"
    case breakObstacles15 = "ach_break15"
    case classic5000 = "ach_score5k"
    case timeRush2500 = "ach_time2500"
    case sandtrix2500 = "ach_sandtrix2500"
    case adventure10 = "ach_adv10"
    case stars30 = "ach_stars30"

    var id: String { rawValue }

    var titleTr: String {
        switch self {
        case .firstBridge: return "İlk Köprü"
        case .combo3: return "Zincirleme Akış"
        case .combo5: return "Kombo Efsanesi"
        case .totalLines50: return "Çöl Fırtınası"
        case .breakObstacles15: return "Kaya & Buz Kırıcı"
        case .classic5000: return "Altın Zirve"
        case .timeRush2500: return "Zaman Bükücü"
        case .sandtrix2500: return "Refleks Ustası"
        case .adventure10: return "Kıta Kaşifi"
        case .stars30: return "Yıldız Koleksiyoncusu"
        }
    }

    var descTr: String {
        switch self {
        case .firstBridge: return "İlk kum hattını soldan sağa temizle"
        case .combo3: return "Tek hamlede 3x Komboya ulaş"
        case .combo5: return "Tek hamlede 5x Komboya ulaş"
        case .totalLines50: return "Toplamda 50 kum hattı temizle"
        case .breakObstacles15: return "Toplam 15 Çatlak Taş veya Buz Bloğu kır"
        case .classic5000: return "Klasik modda 5.000 puana ulaş"
        case .timeRush2500: return "Zamana Karşı modda 2.500 puana ulaş"
        case .sandtrix2500: return "Sandtrix Akış modunda 2.500 puana ulaş"
        case .adventure10: return "Macera modunda 10. bölümü tamamla"
        case .stars30: return "Macera haritasında 30 yıldız topla"
        }
    }

    var emoji: String {
        switch self {
        case .firstBridge: return "🌉"
        case .combo3: return "⚡"
        case .combo5: return "🌋"
        case .totalLines50: return "🌪️"
        case .breakObstacles15: return "🧊"
        case .classic5000: return "🏆"
        case .timeRush2500: return "⏱️"
        case .sandtrix2500: return "🕹️"
        case .adventure10: return "🗺️"
        case .stars30: return "⭐"
        }
    }

    var target: Int {
        switch self {
        case .firstBridge: return 1
        case .combo3: return 3
        case .combo5: return 5
        case .totalLines50: return 50
        case .breakObstacles15: return 15
        case .classic5000: return 5000
        case .timeRush2500: return 2500
        case .sandtrix2500: return 2500
        case .adventure10: return 10
        case .stars30: return 30
        }
    }

    var rewardCoins: Int {
        switch self {
        case .firstBridge: return 40
        case .combo3: return 70
        case .combo5: return 130
        case .totalLines50: return 95
        case .breakObstacles15: return 90
        case .classic5000: return 110
        case .timeRush2500: return 100
        case .sandtrix2500: return 100
        case .adventure10: return 115
        case .stars30: return 160
        }
    }

    var rewardXp: Int {
        switch self {
        case .firstBridge: return 50
        case .combo3: return 90
        case .combo5: return 160
        case .totalLines50: return 120
        case .breakObstacles15: return 110
        case .classic5000: return 140
        case .timeRush2500: return 130
        case .sandtrix2500: return 130
        case .adventure10: return 150
        case .stars30: return 220
        }
    }
}
