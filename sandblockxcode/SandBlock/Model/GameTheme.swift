import SwiftUI
import UIKit

enum GameTheme: String, CaseIterable, Identifiable, Codable {
    case sahara = "sahara"
    case ocean = "ocean"
    case neon = "neon"
    case pastel = "pastel"
    case volcano = "volcano"
    case aurora = "aurora"

    var id: String { rawValue }

    var titleTr: String {
        switch self {
        case .sahara: return "Sahra Çölü"
        case .ocean: return "Derin Okyanus"
        case .neon: return "Neon Gece"
        case .pastel: return "Pastel Rüya"
        case .volcano: return "Volkanik Magma"
        case .aurora: return "Kuzey Işıkları"
        }
    }

    var subtitleTr: String {
        switch self {
        case .sahara: return "Altın kumullar ve sıcak vaha tonları"
        case .ocean: return "Mercan resifleri ve turkuaz akıntılar"
        case .neon: return "Siberpunk parlak lazer akışları"
        case .pastel: return "Yumuşak ASMR şeker ve lavanta tonları"
        case .volcano: return "Obsidyen kayalar ve kor ateş yansımaları"
        case .aurora: return "Kutup buzulları ve kutup ışığı parıltıları"
        }
    }

    var emoji: String {
        switch self {
        case .sahara: return "🏜️"
        case .ocean: return "🌊"
        case .neon: return "🌃"
        case .pastel: return "🍬"
        case .volcano: return "🌋"
        case .aurora: return "🌌"
        }
    }

    var bgTop: UInt32 {
        switch self {
        case .sahara: return 0xFF1E130C
        case .ocean: return 0xFF061924
        case .neon: return 0xFF090717
        case .pastel: return 0xFF211B2B
        case .volcano: return 0xFF1A0808
        case .aurora: return 0xFF07131E
        }
    }

    var bgBottom: UInt32 {
        switch self {
        case .sahara: return 0xFF362013
        case .ocean: return 0xFF0E344A
        case .neon: return 0xFF19123B
        case .pastel: return 0xFF342A45
        case .volcano: return 0xFF33110C
        case .aurora: return 0xFF102A3A
        }
    }

    var boardBg: UInt32 {
        switch self {
        case .sahara: return 0xFF140C08
        case .ocean: return 0xFF041119
        case .neon: return 0xFF060510
        case .pastel: return 0xFF171320
        case .volcano: return 0xFF110505
        case .aurora: return 0xFF050E17
        }
    }

    var boardGridLine: UInt32 {
        switch self {
        case .sahara: return 0x18FFD6A5
        case .ocean: return 0x1A90E0EF
        case .neon: return 0x1A00F5D4
        case .pastel: return 0x1AFFC6FF
        case .volcano: return 0x1AFF6D00
        case .aurora: return 0x1A64DFDF
        }
    }

    var boardBorder: UInt32 {
        switch self {
        case .sahara: return 0xFFE5A93C
        case .ocean: return 0xFF48CAE4
        case .neon: return 0xFF00F5D4
        case .pastel: return 0xFFFFAFCC
        case .volcano: return 0xFFFF6D00
        case .aurora: return 0xFF64DFDF
        }
    }

    var surfaceCard: UInt32 {
        switch self {
        case .sahara: return 0xFF2B1A10
        case .ocean: return 0xFF0B2738
        case .neon: return 0xFF161132
        case .pastel: return 0xFF2B233A
        case .volcano: return 0xFF290E0B
        case .aurora: return 0xFF0D2230
        }
    }

    var accentColor: UInt32 {
        switch self {
        case .sahara: return 0xFFFFB703
        case .ocean: return 0xFF48CAE4
        case .neon: return 0xFF00F5D4
        case .pastel: return 0xFFFFAFCC
        case .volcano: return 0xFFFF9E00
        case .aurora: return 0xFF72EFDD
        }
    }

    var dangerLineColor: UInt32 {
        switch self {
        case .sahara: return 0xFFFF4D4D
        case .ocean: return 0xFFFF6B6B
        case .neon: return 0xFFFF2A85
        case .pastel: return 0xFFFF758F
        case .volcano: return 0xFFFF2A2A
        case .aurora: return 0xFFFF5D8F
        }
    }

    var sandPalette: [UInt32] {
        switch self {
        case .sahara:
            return [
                0xFFFFB703, // Kehribar Altın
                0xFFFB5607, // Terakota Mercan
                0xFF00B4D8, // Vaha Turkuazı
                0xFF52B788, // Kaktüs Zümrüt
                0xFF9D4EDD  // Çöl Ametisti
            ]
        case .ocean:
            return [
                0xFFFF6B6B, // Mercan Kırmızısı
                0xFFFFD166, // Kumsal Güneşi
                0xFF06D6A0, // Deniz Köpüğü Yeşili
                0xFF3A86FF, // Lagün Mavisi
                0xFFC77DFF  // İnci Orkide
            ]
        case .neon:
            return [
                0xFFFF2A85, // Lazer Macenta
                0xFF00F5D4, // Elektrik Camgöbeği
                0xFFFEE440, // Siber Sarı
                0xFF57CC99, // Parlak Neon Yeşil
                0xFF9B5DE5  // Ultra Mor
            ]
        case .pastel:
            return [
                0xFFFF8FA3, // Şeftali Pembe
                0xFFFFEA85, // Limon Krema
                0xFF80ED99, // Nane Yeşili
                0xFF72DDF7, // Bebek Mavisi
                0xFFD8BBFF  // Lavanta
            ]
        case .volcano:
            return [
                0xFFFF5400, // Magma Turuncusu
                0xFFFFBD00, // Kor Sarısı
                0xFF00BBF9, // Gayzer Mavisi
                0xFF38B000, // Zümrüt Kristal
                0xFFC77DFF  // Obsidyen Moru
            ]
        case .aurora:
            return [
                0xFF72EFDD, // Aurora Turkuazı
                0xFFFF758F, // Kutup Gülü
                0xFFFFE66D, // Kutup Yıldızı
                0xFF4EA8DE, // Buzul Mavisi
                0xFFB892FF  // Kuzey Moru
            ]
        }
    }

    var stoneBaseColor: UInt32 {
        switch self {
        case .sahara: return 0xFF6C584C
        case .ocean: return 0xFF3D5A6C
        case .neon: return 0xFF3C3758
        case .pastel: return 0xFF5E546E
        case .volcano: return 0xFF4A3535
        case .aurora: return 0xFF3D5A73
        }
    }

    var crackedStoneColor: UInt32 {
        switch self {
        case .sahara: return 0xFFA98467
        case .ocean: return 0xFF779CAB
        case .neon: return 0xFF6C639B
        case .pastel: return 0xFF9D8CA7
        case .volcano: return 0xFF8C5346
        case .aurora: return 0xFF7699B5
        }
    }

    static func fromId(_ id: String) -> GameTheme {
        GameTheme(rawValue: id) ?? .sahara
    }
}

enum ColorMath {
    private static let rainbowLut: [UInt32] = [
        0xFFFF4D6D,
        0xFFFF9E00,
        0xFFFFEA00,
        0xFF38B000,
        0xFF00B4D8,
        0xFF7209B7,
        0xFFF72585
    ]

    private static let lavaLut: [UInt32] = [
        0xFFFF2A00,
        0xFFFF6D00,
        0xFFFFB703,
        0xFFFFF3B0,
        0xFFFF5400
    ]

    static func shade(_ argb: UInt32, _ delta: Int) -> UInt32 {
        let a = Int((argb >> 24) & 0xFF)
        let r = min(255, max(0, Int((argb >> 16) & 0xFF) + delta))
        let g = min(255, max(0, Int((argb >> 8) & 0xFF) + delta))
        let b = min(255, max(0, Int(argb & 0xFF) + delta))
        return (UInt32(a) << 24) | (UInt32(r) << 16) | (UInt32(g) << 8) | UInt32(b)
    }

    static func blend(_ c1: UInt32, _ c2: UInt32, _ t: Float) -> UInt32 {
        let clamped = min(1.0, max(0.0, t))
        let inv = 1.0 - clamped
        let a = Int(Float((c1 >> 24) & 0xFF) * inv + Float((c2 >> 24) & 0xFF) * clamped)
        let r = Int(Float((c1 >> 16) & 0xFF) * inv + Float((c2 >> 16) & 0xFF) * clamped)
        let g = Int(Float((c1 >> 8) & 0xFF) * inv + Float((c2 >> 8) & 0xFF) * clamped)
        let b = Int(Float(c1 & 0xFF) * inv + Float(c2 & 0xFF) * clamped)
        return (UInt32(a & 0xFF) << 24) | (UInt32(r & 0xFF) << 16) | (UInt32(g & 0xFF) << 8) | UInt32(b & 0xFF)
    }

    static func withAlpha(_ argb: UInt32, _ alpha: Int) -> UInt32 {
        let a = UInt32(min(255, max(0, alpha)))
        return (a << 24) | (argb & 0x00FFFFFF)
    }

    static func rainbowColor(seed: Int, tick: Int) -> UInt32 {
        let span = rainbowLut.count * 16
        let raw = (seed &* 17 &+ tick &* 2) & 0x7FFFFFFF
        let pos = raw % span
        let idx = pos / 16
        let next = (idx + 1) % rainbowLut.count
        let frac = Float(pos % 16) / 16.0
        return blend(rainbowLut[idx], rainbowLut[next], frac)
    }

    static func lavaColor(seed: Int, tick: Int) -> UInt32 {
        let span = lavaLut.count * 16
        let raw = (seed &* 13 &+ tick &* 3) & 0x7FFFFFFF
        let pos = raw % span
        let idx = pos / 16
        let next = (idx + 1) % lavaLut.count
        let frac = Float(pos % 16) / 16.0
        return blend(lavaLut[idx], lavaLut[next], frac)
    }
}

extension Color {
    init(argb: UInt32) {
        let a = Double((argb >> 24) & 0xFF) / 255.0
        let r = Double((argb >> 16) & 0xFF) / 255.0
        let g = Double((argb >> 8) & 0xFF) / 255.0
        let b = Double(argb & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}

extension UIColor {
    convenience init(argb: UInt32) {
        let a = CGFloat((argb >> 24) & 0xFF) / 255.0
        let r = CGFloat((argb >> 16) & 0xFF) / 255.0
        let g = CGFloat((argb >> 8) & 0xFF) / 255.0
        let b = CGFloat(argb & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
