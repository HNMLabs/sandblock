package com.hnmlabs.sandblock.model

import kotlin.random.Random

data class CellOffset(val col: Int, val row: Int)

data class BlockShape(
    val id: String,
    val cells: List<CellOffset>,
    val cols: Int,
    val rows: Int
) {
    fun rotatedClockwise(): BlockShape {
        val rotatedRaw = cells.map { CellOffset(col = rows - 1 - it.row, row = it.col) }
        return normalize(id, rotatedRaw)
    }

    companion object {
        fun byId(id: String): BlockShape =
            if (id == BOMB_SHAPE.id) BOMB_SHAPE
            else STANDARD_SHAPES.firstOrNull { it.id == id } ?: STANDARD_SHAPES.first()

        fun normalize(id: String, rawCells: List<CellOffset>): BlockShape {
            val minCol = rawCells.minOf { it.col }
            val minRow = rawCells.minOf { it.row }
            val norm = rawCells.map { CellOffset(it.col - minCol, it.row - minRow) }
                .distinct()
            val cols = (norm.maxOfOrNull { it.col } ?: 0) + 1
            val rows = (norm.maxOfOrNull { it.row } ?: 0) + 1
            return BlockShape(id = id, cells = norm, cols = cols, rows = rows)
        }

        private fun fromAscii(id: String, vararg lines: String): BlockShape {
            val cells = mutableListOf<CellOffset>()
            lines.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    if (ch == '#') cells.add(CellOffset(c, r))
                }
            }
            return normalize(id, cells)
        }

        val BOMB_SHAPE: BlockShape = fromAscii(
            "BOMB",
            "##",
            "##"
        )

        val STANDARD_SHAPES: List<BlockShape> = listOf(
            fromAscii("O2", "##", "##"),
            fromAscii("I3", "###"),
            fromAscii("I4", "####"),
            fromAscii("T3", "###", ".#."),
            fromAscii("L3", "#..", "###"),
            fromAscii("J3", "..#", "###"),
            fromAscii("S3", ".##", "##."),
            fromAscii("Z3", "##.", ".##"),
            fromAscii("CORNER3", "##", "#."),
            fromAscii("PLUS", ".#.", "###", ".#."),
            fromAscii("U_CUP", "#.#", "###"),
            fromAscii("O3", "###", "###"),
            fromAscii("BAR2", "##"),
            fromAscii("STEP3", "#..", "##.", ".##")
        )
    }
}

enum class PieceKind {
    NORMAL,
    RAINBOW,
    BOMB,
    LAVA
}

data class TrayPiece(
    val uid: Long,
    val shape: BlockShape,
    val colorIndex: Int,
    val kind: PieceKind = PieceKind.NORMAL
) {
    fun rotated(): TrayPiece {
        if (kind == PieceKind.BOMB) return this
        return copy(shape = shape.rotatedClockwise())
    }
}

class PieceGenerator(private val random: Random = Random.Default) {
    private var nextUid = 1L
    private var piecesGenerated = 0

    fun reset() {
        piecesGenerated = 0
    }

    fun nextSinglePiece(activeColors: Int, allowSpecialPieces: Boolean = true): TrayPiece {
        piecesGenerated++
        val safeColors = activeColors.coerceIn(2, 5)
        val roll = random.nextFloat()

        val kind = when {
            allowSpecialPieces && piecesGenerated >= 5 && roll < 0.055f -> PieceKind.RAINBOW
            allowSpecialPieces && piecesGenerated >= 7 && roll in 0.055f..0.095f -> PieceKind.BOMB
            allowSpecialPieces && piecesGenerated >= 8 && roll in 0.095f..0.135f -> PieceKind.LAVA
            else -> PieceKind.NORMAL
        }

        if (kind == PieceKind.BOMB) {
            return TrayPiece(
                uid = nextUid++,
                shape = BlockShape.BOMB_SHAPE,
                colorIndex = 0,
                kind = PieceKind.BOMB
            )
        }

        val shapePool = if (kind == PieceKind.LAVA) {
            // Compact shapes for molten lava blocks
            BlockShape.STANDARD_SHAPES.take(5)
        } else {
            BlockShape.STANDARD_SHAPES
        }
        var shape = shapePool[random.nextInt(shapePool.size)]
        repeat(random.nextInt(4)) {
            shape = shape.rotatedClockwise()
        }

        return TrayPiece(
            uid = nextUid++,
            shape = shape,
            colorIndex = random.nextInt(safeColors),
            kind = kind
        )
    }

    fun nextTray(activeColors: Int, allowSpecialPieces: Boolean = true): List<TrayPiece> {
        val safeColors = activeColors.coerceIn(2, 5)
        val pieces = ArrayList<TrayPiece>(3)
        val primaryColor = random.nextInt(safeColors)

        for (slot in 0 until 3) {
            val base = nextSinglePiece(
                activeColors = safeColors,
                allowSpecialPieces = allowSpecialPieces && slot == 2
            )
            val piece = if (slot == 0 && base.kind == PieceKind.NORMAL && random.nextFloat() < 0.48f) {
                base.copy(colorIndex = primaryColor)
            } else {
                base
            }
            pieces.add(piece)
        }
        return pieces
    }

    fun upgradePieceToRainbow(piece: TrayPiece): TrayPiece {
        val newShape = if (piece.kind == PieceKind.BOMB) {
            BlockShape.STANDARD_SHAPES[1]
        } else {
            piece.shape
        }
        return piece.copy(shape = newShape, kind = PieceKind.RAINBOW)
    }

    fun upgradePieceToLava(piece: TrayPiece): TrayPiece {
        val newShape = if (piece.kind == PieceKind.BOMB) {
            BlockShape.STANDARD_SHAPES[0]
        } else {
            piece.shape
        }
        return piece.copy(shape = newShape, kind = PieceKind.LAVA)
    }
}
