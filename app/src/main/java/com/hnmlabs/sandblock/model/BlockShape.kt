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
    BOMB
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
    private var traysGenerated = 0

    fun reset() {
        traysGenerated = 0
    }

    fun nextTray(activeColors: Int, allowSpecialPieces: Boolean = true): List<TrayPiece> {
        traysGenerated++
        val safeColors = activeColors.coerceIn(2, 5)
        val pieces = ArrayList<TrayPiece>(3)

        // Ensure at least two pieces in a tray or consecutive trays give good color synergy
        val primaryColor = random.nextInt(safeColors)

        for (slot in 0 until 3) {
            val roll = random.nextFloat()
            val kind = when {
                allowSpecialPieces && traysGenerated >= 2 && slot == 2 && roll < 0.11f -> PieceKind.RAINBOW
                allowSpecialPieces && traysGenerated >= 3 && slot == 2 && roll in 0.11f..0.19f -> PieceKind.BOMB
                else -> PieceKind.NORMAL
            }

            if (kind == PieceKind.BOMB) {
                pieces.add(
                    TrayPiece(
                        uid = nextUid++,
                        shape = BlockShape.BOMB_SHAPE,
                        colorIndex = 0,
                        kind = PieceKind.BOMB
                    )
                )
            } else {
                var shape = BlockShape.STANDARD_SHAPES[random.nextInt(BlockShape.STANDARD_SHAPES.size)]
                val rotations = random.nextInt(4)
                repeat(rotations) {
                    shape = shape.rotatedClockwise()
                }
                val color = if (slot == 0 && random.nextFloat() < 0.45f) {
                    primaryColor
                } else {
                    random.nextInt(safeColors)
                }
                pieces.add(
                    TrayPiece(
                        uid = nextUid++,
                        shape = shape,
                        colorIndex = color,
                        kind = kind
                    )
                )
            }
        }
        return pieces
    }

    fun upgradePieceToRainbow(piece: TrayPiece): TrayPiece {
        val newShape = if (piece.kind == PieceKind.BOMB) {
            BlockShape.STANDARD_SHAPES[1] // I3
        } else {
            piece.shape
        }
        return piece.copy(shape = newShape, kind = PieceKind.RAINBOW)
    }
}
