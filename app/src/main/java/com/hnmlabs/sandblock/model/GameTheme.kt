package com.hnmlabs.sandblock.model

enum class GameTheme(
    val id: String,
    val titleTr: String,
    val subtitleTr: String,
    val emoji: String,
    val bgTop: Int,
    val bgBottom: Int,
    val boardBg: Int,
    val boardGridLine: Int,
    val boardBorder: Int,
    val surfaceCard: Int,
    val accentColor: Int,
    val dangerLineColor: Int,
    val sandPalette: IntArray,
    val stoneBaseColor: Int,
    val crackedStoneColor: Int
) {
    SAHARA(
        id = "sahara",
        titleTr = "Sahra Çölü",
        subtitleTr = "Altın kumullar ve sıcak vaha tonları",
        emoji = "🏜️",
        bgTop = 0xFF1E130C.toInt(),
        bgBottom = 0xFF362013.toInt(),
        boardBg = 0xFF140C08.toInt(),
        boardGridLine = 0x18FFD6A5,
        boardBorder = 0xFFE5A93C.toInt(),
        surfaceCard = 0xFF2B1A10.toInt(),
        accentColor = 0xFFFFB703.toInt(),
        dangerLineColor = 0xFFFF4D4D.toInt(),
        sandPalette = intArrayOf(
            0xFFFFB703.toInt(), // Kehribar Altın
            0xFFFB5607.toInt(), // Terakota Mercan
            0xFF00B4D8.toInt(), // Vaha Turkuazı
            0xFF52B788.toInt(), // Kaktüs Zümrüt
            0xFF9D4EDD.toInt()  // Çöl Ametisti
        ),
        stoneBaseColor = 0xFF6C584C.toInt(),
        crackedStoneColor = 0xFFA98467.toInt()
    ),
    NEON(
        id = "neon",
        titleTr = "Neon Gece",
        subtitleTr = "Siberpunk parlak lazer akışları",
        emoji = "🌃",
        bgTop = 0xFF090717.toInt(),
        bgBottom = 0xFF19123B.toInt(),
        boardBg = 0xFF060510.toInt(),
        boardGridLine = 0x1A00F5D4,
        boardBorder = 0xFF00F5D4.toInt(),
        surfaceCard = 0xFF161132.toInt(),
        accentColor = 0xFF00F5D4.toInt(),
        dangerLineColor = 0xFFFF2A85.toInt(),
        sandPalette = intArrayOf(
            0xFFFF2A85.toInt(), // Lazer Macenta
            0xFF00F5D4.toInt(), // Elektrik Camgöbeği
            0xFFFEE440.toInt(), // Siber Sarı
            0xFF72EFDD.toInt(), // Buzul Neon (ya da Asit Yeşili: aşağıda net ayrım için parlak yeşil)
            0xFF9B5DE5.toInt()  // Ultra Mor
        ).also { it[3] = 0xFF57CC99.toInt() }, // Parlak Neon Yeşil
        stoneBaseColor = 0xFF3C3758.toInt(),
        crackedStoneColor = 0xFF6C639B.toInt()
    ),
    OCEAN(
        id = "ocean",
        titleTr = "Derin Okyanus",
        subtitleTr = "Mercan resifleri ve turkuaz akıntılar",
        emoji = "🌊",
        bgTop = 0xFF061924.toInt(),
        bgBottom = 0xFF0E344A.toInt(),
        boardBg = 0xFF041119.toInt(),
        boardGridLine = 0x1A90E0EF,
        boardBorder = 0xFF48CAE4.toInt(),
        surfaceCard = 0xFF0B2738.toInt(),
        accentColor = 0xFF48CAE4.toInt(),
        dangerLineColor = 0xFFFF6B6B.toInt(),
        sandPalette = intArrayOf(
            0xFFFF6B6B.toInt(), // Mercan Kırmızısı
            0xFFFFD166.toInt(), // Kumsal Güneşi
            0xFF06D6A0.toInt(), // Deniz Köpüğü Yeşili
            0xFF3A86FF.toInt(), // Lagün Mavisi
            0xFFC77DFF.toInt()  // İnci Orkide
        ),
        stoneBaseColor = 0xFF3D5A6C.toInt(),
        crackedStoneColor = 0xFF779CAB.toInt()
    ),
    PASTEL(
        id = "pastel",
        titleTr = "Pastel Rüya",
        subtitleTr = "Yumuşak ASMR şeker ve lavanta tonları",
        emoji = "🍬",
        bgTop = 0xFF211B2B.toInt(),
        bgBottom = 0xFF342A45.toInt(),
        boardBg = 0xFF171320.toInt(),
        boardGridLine = 0x1AFFC6FF,
        boardBorder = 0xFFFFAFCC.toInt(),
        surfaceCard = 0xFF2B233A.toInt(),
        accentColor = 0xFFFFAFCC.toInt(),
        dangerLineColor = 0xFFFF758F.toInt(),
        sandPalette = intArrayOf(
            0xFFFF8FA3.toInt(), // Şeftali Pembe
            0xFFFFEA85.toInt(), // Limon Krema
            0xFF80ED99.toInt(), // Nane Yeşili
            0xFF72DDF7.toInt(), // Bebek Mavisi
            0xFFD8BBFF.toInt()  // Lavanta Lazeri
        ),
        stoneBaseColor = 0xFF5E546E.toInt(),
        crackedStoneColor = 0xFF9D8CA7.toInt()
    );

    companion object {
        fun fromId(id: String): GameTheme = entries.firstOrNull { it.id == id } ?: SAHARA
    }
}

object ColorMath {
    private val rainbowLut = intArrayOf(
        0xFFFF4D6D.toInt(),
        0xFFFF9E00.toInt(),
        0xFFFFEA00.toInt(),
        0xFF38B000.toInt(),
        0xFF00B4D8.toInt(),
        0xFF7209B7.toInt(),
        0xFFF72585.toInt()
    )

    fun shade(argb: Int, delta: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((argb and 0xFF) + delta).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun blend(c1: Int, c2: Int, t: Float): Int {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        val a = (((c1 ushr 24) and 0xFF) * inv + ((c2 ushr 24) and 0xFF) * clamped).toInt()
        val r = (((c1 ushr 16) and 0xFF) * inv + ((c2 ushr 16) and 0xFF) * clamped).toInt()
        val g = (((c1 ushr 8) and 0xFF) * inv + ((c2 ushr 8) and 0xFF) * clamped).toInt()
        val b = ((c1 and 0xFF) * inv + (c2 and 0xFF) * clamped).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun withAlpha(argb: Int, alpha: Int): Int {
        return ((alpha.coerceIn(0, 255)) shl 24) or (argb and 0x00FFFFFF)
    }

    fun rainbowColor(seed: Int, tick: Int): Int {
        val pos = ((seed * 17 + tick * 2) and 0x7FFFFFFF) % (rainbowLut.size * 16)
        val idx = pos / 16
        val next = (idx + 1) % rainbowLut.size
        val frac = (pos % 16) / 16f
        return blend(rainbowLut[idx], rainbowLut[next], frac)
    }
}
