package com.hnmlabs.sandblock.engine

import com.hnmlabs.sandblock.model.AdventureLevel
import com.hnmlabs.sandblock.model.BlockShape
import com.hnmlabs.sandblock.model.ColorMath
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PieceGenerator
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.TerrainPreset
import com.hnmlabs.sandblock.model.TrayPiece
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

sealed interface WorldEvent {
    data class PiecePlaced(val centerX: Float, val centerY: Float, val colorArgb: Int, val isRainbow: Boolean) : WorldEvent
    data class LineClearStarted(
        val combo: Int,
        val grainCount: Int,
        val crackedBlocksBroken: Int,
        val centerX: Float,
        val centerY: Float,
        val colorArgb: Int,
        val pointsAwarded: Int
    ) : WorldEvent
    data class BombExploded(
        val centerX: Float,
        val centerY: Float,
        val destroyedGrains: Int,
        val pointsAwarded: Int
    ) : WorldEvent
    data class ColorCleaned(
        val colorIndex: Int,
        val colorArgb: Int,
        val destroyedGrains: Int,
        val pointsAwarded: Int
    ) : WorldEvent
    data class LevelWon(val stars: Int, val finalScore: Int) : WorldEvent
    data object GameLost : WorldEvent
}

enum class GameStatus {
    PLAYING,
    WON,
    GAME_OVER
}

class SandWorld(
    val width: Int = 72,
    val height: Int = 112,
    val grainScale: Int = 8,
    val dangerRow: Int = 18,
    private val random: Random = Random.Default
) {
    companion object {
        const val CELL_EMPTY: Byte = 0
        const val CELL_SAND: Byte = 1
        const val CELL_RAINBOW: Byte = 2
        const val CELL_STONE: Byte = 3
        const val CELL_CRACKED_STONE: Byte = 4

        const val CLEAR_FLASH_DURATION = 14 // ~230ms at 60fps
        const val BOMB_RADIUS = 18
    }

    val size: Int = width * height
    val cellTypes = ByteArray(size)
    val colorIndices = ByteArray(size)
    val shades = ByteArray(size)
    val clearMask = BooleanArray(size)

    private val visited = BooleanArray(size)
    private val bfsQueue = IntArray(size)
    private val pendingEvents = ArrayList<WorldEvent>(8)

    private val pieceGenerator = PieceGenerator(random)
    val trayPieces = arrayOfNulls<TrayPiece>(3)

    var adventureLevel: AdventureLevel? = null
        private set
    var theme: GameTheme = GameTheme.SAHARA

    var score: Int = 0
        private set
    var linesCleared: Int = 0
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var movesUsed: Int = 0
        private set
    var status: GameStatus = GameStatus.PLAYING
        private set

    var tickCount: Int = 0
        private set
    var clearFlashTimer: Int = 0
        private set
    private var settleQuietTicks: Int = 0

    val activeColorCount: Int
        get() = adventureLevel?.activeColors ?: when {
            score >= 4500 -> 5
            score >= 1400 -> 4
            else -> 3
        }

    val movesRemaining: Int?
        get() = adventureLevel?.let { lvl ->
            if (lvl.maxMoves > 0) (lvl.maxMoves - movesUsed).coerceAtLeast(0) else null
        }

    fun drainEvents(): List<WorldEvent> {
        if (pendingEvents.isEmpty()) return emptyList()
        val copy = ArrayList(pendingEvents)
        pendingEvents.clear()
        return copy
    }

    fun startClassic(selectedTheme: GameTheme) {
        theme = selectedTheme
        adventureLevel = null
        resetBoardState()
        refillTray()
    }

    fun startAdventure(level: AdventureLevel, selectedThemeOverride: GameTheme? = null) {
        adventureLevel = level
        theme = selectedThemeOverride ?: level.theme
        resetBoardState()
        applyTerrainPreset(level.terrainPreset, level.activeColors)
        refillTray()
    }

    private fun resetBoardState() {
        cellTypes.fill(CELL_EMPTY)
        colorIndices.fill(0)
        shades.fill(0)
        clearMask.fill(false)
        pendingEvents.clear()
        pieceGenerator.reset()
        trayPieces.fill(null)

        score = 0
        linesCleared = 0
        combo = 0
        maxCombo = 0
        movesUsed = 0
        status = GameStatus.PLAYING
        tickCount = 0
        clearFlashTimer = 0
        settleQuietTicks = 0
    }

    private fun refillTray() {
        val next = pieceGenerator.nextTray(activeColorCount, allowSpecialPieces = true)
        for (i in 0 until 3) {
            trayPieces[i] = next[i]
        }
    }

    fun rotateTrayPiece(slot: Int): Boolean {
        if (slot !in 0..2 || status != GameStatus.PLAYING) return false
        val piece = trayPieces[slot] ?: return false
        if (piece.kind == PieceKind.BOMB) return false
        trayPieces[slot] = piece.rotated()
        return true
    }

    fun upgradeTrayPieceToRainbow(): Int {
        if (status != GameStatus.PLAYING) return -1
        val targetSlot = (0..2).firstOrNull { slot ->
            val p = trayPieces[slot]
            p != null && p.kind == PieceKind.NORMAL
        } ?: (0..2).firstOrNull { trayPieces[it] != null && trayPieces[it]?.kind != PieceKind.RAINBOW }
        ?: return -1

        val current = trayPieces[targetSlot] ?: return -1
        trayPieces[targetSlot] = pieceGenerator.upgradePieceToRainbow(current)
        return targetSlot
    }

    fun canPlaceShape(shape: BlockShape, originX: Int, originY: Int): Boolean {
        val pieceW = shape.cols * grainScale
        val pieceH = shape.rows * grainScale
        if (originX < 0 || originY < 0 || originX + pieceW > width || originY + pieceH > height) {
            return false
        }
        for (cell in shape.cells) {
            val startX = originX + cell.col * grainScale
            val startY = originY + cell.row * grainScale
            for (gy in startY until startY + grainScale) {
                val rowBase = gy * width
                for (gx in startX until startX + grainScale) {
                    if (cellTypes[rowBase + gx] != CELL_EMPTY) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun findValidPlacement(piece: TrayPiece, rawX: Int, rawY: Int): Pair<Int, Int>? {
        val pieceW = piece.shape.cols * grainScale
        val pieceH = piece.shape.rows * grainScale
        val clampedX = rawX.coerceIn(0, width - pieceW)
        val clampedY = rawY.coerceIn(0, height - pieceH)

        if (piece.kind == PieceKind.BOMB) {
            return clampedX to clampedY
        }

        if (canPlaceShape(piece.shape, clampedX, clampedY)) {
            return clampedX to clampedY
        }

        // Gentle snap assistance around finger so placing right above sand feels effortless
        for (radius in 1..10) {
            // Prefer snapping slightly upward above sand crest first
            val upY = clampedY - radius
            if (upY >= 0 && canPlaceShape(piece.shape, clampedX, upY)) {
                return clampedX to upY
            }
            for (dx in -radius..radius) {
                if (dx == 0) continue
                val nx = clampedX + dx
                if (nx in 0..(width - pieceW)) {
                    if (canPlaceShape(piece.shape, nx, clampedY)) return nx to clampedY
                    if (upY >= 0 && canPlaceShape(piece.shape, nx, upY)) return nx to upY
                }
            }
        }
        return null
    }

    fun placeTrayPiece(slot: Int, rawX: Int, rawY: Int): Boolean {
        if (slot !in 0..2 || status != GameStatus.PLAYING) return false
        val piece = trayPieces[slot] ?: return false
        val placement = findValidPlacement(piece, rawX, rawY) ?: return false
        val (originX, originY) = placement

        trayPieces[slot] = null
        movesUsed++
        combo = 0
        settleQuietTicks = 0

        if (piece.kind == PieceKind.BOMB) {
            val pieceW = piece.shape.cols * grainScale
            val pieceH = piece.shape.rows * grainScale
            val cx = (originX + pieceW / 2).coerceIn(0, width - 1)
            var cy = (originY + pieceH / 2).coerceIn(0, height - 1)
            // If dropped in mid-air above sand, drop bomb straight down onto first solid surface
            while (cy < height - 1 && cellTypes[(cy + 1) * width + cx] == CELL_EMPTY && cellTypes[cy * width + cx] == CELL_EMPTY) {
                cy++
            }
            detonateBomb(cx, cy)
        } else {
            spawnPieceGrains(piece, originX, originY)
            val cx = originX + (piece.shape.cols * grainScale) / 2f
            val cy = originY + (piece.shape.rows * grainScale) / 2f
            val baseColor = if (piece.kind == PieceKind.RAINBOW) {
                0xFFFFFFFF.toInt()
            } else {
                theme.sandPalette[piece.colorIndex % theme.sandPalette.size]
            }
            pendingEvents.add(
                WorldEvent.PiecePlaced(
                    centerX = cx,
                    centerY = cy,
                    colorArgb = baseColor,
                    isRainbow = piece.kind == PieceKind.RAINBOW
                )
            )
        }

        if (trayPieces.all { it == null }) {
            refillTray()
        }
        return true
    }

    fun spawnPieceGrains(piece: TrayPiece, originX: Int, originY: Int) {
        val targetType = if (piece.kind == PieceKind.RAINBOW) CELL_RAINBOW else CELL_SAND
        val cIdx = (piece.colorIndex.coerceIn(0, 4)).toByte()

        for (cell in piece.shape.cells) {
            val startX = originX + cell.col * grainScale
            val startY = originY + cell.row * grainScale
            for (gy in startY until startY + grainScale) {
                val rowBase = gy * width
                val isTopEdge = gy == startY
                val isBottomEdge = gy == startY + grainScale - 1
                for (gx in startX until startX + grainScale) {
                    val idx = rowBase + gx
                    if (cellTypes[idx] == CELL_EMPTY) {
                        cellTypes[idx] = targetType
                        colorIndices[idx] = cIdx
                        val edgeBias = when {
                            isTopEdge -> 10
                            isBottomEdge -> -10
                            else -> 0
                        }
                        shades[idx] = (random.nextInt(-14, 15) + edgeBias).coerceIn(-28, 28).toByte()
                    }
                }
            }
        }
    }

    fun detonateBomb(centerX: Int, centerY: Int, radius: Int = BOMB_RADIUS): Int {
        if (status != GameStatus.PLAYING) return 0
        var destroyed = 0
        val minX = (centerX - radius).coerceAtLeast(0)
        val maxX = (centerX + radius).coerceAtMost(width - 1)
        val minY = (centerY - radius).coerceAtLeast(0)
        val maxY = (centerY + radius).coerceAtMost(height - 1)
        val rSq = radius * radius

        val crackedMacrosHit = HashSet<Int>()

        for (y in minY..maxY) {
            val dy = y - centerY
            val rowBase = y * width
            for (x in minX..maxX) {
                val dx = x - centerX
                if (dx * dx + dy * dy <= rSq) {
                    val idx = rowBase + x
                    val type = cellTypes[idx]
                    if (type != CELL_EMPTY) {
                        if (type == CELL_CRACKED_STONE) {
                            crackedMacrosHit.add((y / grainScale) * 100 + (x / grainScale))
                        }
                        cellTypes[idx] = CELL_EMPTY
                        clearMask[idx] = false
                        destroyed++
                    }
                }
            }
        }

        // Shatter any cracked stone macro-blocks clipped by explosion
        for (macroKey in crackedMacrosHit) {
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            destroyed += destroyMacroStone(mCol, mRow, CELL_CRACKED_STONE)
        }

        val points = destroyed * 2 + 80
        score += points
        settleQuietTicks = 0
        pendingEvents.add(
            WorldEvent.BombExploded(
                centerX = centerX.toFloat(),
                centerY = centerY.toFloat(),
                destroyedGrains = destroyed,
                pointsAwarded = points
            )
        )
        return destroyed
    }

    fun clearAllSandOfColorAt(gridX: Int, gridY: Int): Boolean {
        if (status != GameStatus.PLAYING) return false
        if (gridX !in 0 until width || gridY !in 0 until height) return false

        // Search small neighborhood around tap for nearest colored sand grain
        var targetColor: Byte = -1
        outer@ for (r in 0..5) {
            for (dy in -r..r) {
                val ny = gridY + dy
                if (ny !in 0 until height) continue
                for (dx in -r..r) {
                    val nx = gridX + dx
                    if (nx !in 0 until width) continue
                    val idx = ny * width + nx
                    if (cellTypes[idx] == CELL_SAND) {
                        targetColor = colorIndices[idx]
                        break@outer
                    }
                }
            }
        }

        if (targetColor < 0) return false
        return clearAllOfColor(targetColor.toInt()) > 0
    }

    fun clearAllOfColor(colorIndex: Int): Int {
        if (status != GameStatus.PLAYING) return 0
        val target = colorIndex.toByte()
        var destroyed = 0
        for (i in 0 until size) {
            if (cellTypes[i] == CELL_SAND && colorIndices[i] == target) {
                cellTypes[i] = CELL_EMPTY
                clearMask[i] = false
                destroyed++
            }
        }
        if (destroyed > 0) {
            val pts = destroyed * 2 + 100
            score += pts
            settleQuietTicks = 0
            val argb = theme.sandPalette[colorIndex % theme.sandPalette.size]
            pendingEvents.add(
                WorldEvent.ColorCleaned(
                    colorIndex = colorIndex,
                    colorArgb = argb,
                    destroyedGrains = destroyed,
                    pointsAwarded = pts
                )
            )
        }
        return destroyed
    }

    /**
     * Advances simulation by one frame (typically called at 60Hz).
     */
    fun updateFrame(physicsSteps: Int = 3) {
        if (status != GameStatus.PLAYING) return
        tickCount++

        if (clearFlashTimer > 0) {
            clearFlashTimer--
            if (clearFlashTimer == 0) {
                finalizeMarkedClears()
            }
            return
        }

        var verticalMoves = 0
        var totalMoves = 0
        repeat(physicsSteps) {
            val stats = stepPhysicsOnce()
            totalMoves += stats.first
            verticalMoves += stats.second
        }

        // Check left-to-right bridges once vertical free-fall mostly settles
        if (verticalMoves <= 8) {
            if (detectAndBeginLineClear()) {
                settleQuietTicks = 0
                return
            }
        }

        if (totalMoves == 0) {
            settleQuietTicks++
            if (settleQuietTicks >= 6) {
                evaluateEndConditions()
            }
        } else {
            settleQuietTicks = 0
        }
    }

    /**
     * Runs a single cellular-automata step. Returns (totalMoved, verticalMoved).
     */
    fun stepPhysicsOnce(): Pair<Int, Int> {
        tickCount++
        var moved = 0
        var vertical = 0

        for (y in height - 2 downTo 0) {
            val rowBase = y * width
            val nextRowBase = (y + 1) * width
            val leftToRight = ((tickCount + y) and 1) == 0

            for (stepX in 0 until width) {
                val x = if (leftToRight) stepX else (width - 1 - stepX)
                val idx = rowBase + x
                val type = cellTypes[idx]
                if (type != CELL_SAND && type != CELL_RAINBOW) continue
                if (clearMask[idx]) continue

                val belowIdx = nextRowBase + x
                if (cellTypes[belowIdx] == CELL_EMPTY) {
                    moveCell(idx, belowIdx)
                    moved++
                    vertical++
                    continue
                }

                val dir1 = if (((x * 37 + y * 13 + tickCount) and 1) == 0) -1 else 1
                val dir2 = -dir1

                if (canSlideDiagonally(x, y, dir1)) {
                    moveCell(idx, nextRowBase + (x + dir1))
                    moved++
                } else if (canSlideDiagonally(x, y, dir2)) {
                    moveCell(idx, nextRowBase + (x + dir2))
                    moved++
                }
            }
        }
        return moved to vertical
    }

    private fun canSlideDiagonally(x: Int, y: Int, dx: Int): Boolean {
        val nx = x + dx
        if (nx !in 0 until width) return false
        val targetIdx = (y + 1) * width + nx
        if (cellTypes[targetIdx] != CELL_EMPTY) return false
        val sideType = cellTypes[y * width + nx]
        return sideType != CELL_STONE && sideType != CELL_CRACKED_STONE
    }

    private fun moveCell(fromIdx: Int, toIdx: Int) {
        cellTypes[toIdx] = cellTypes[fromIdx]
        colorIndices[toIdx] = colorIndices[fromIdx]
        shades[toIdx] = shades[fromIdx]
        cellTypes[fromIdx] = CELL_EMPTY
        colorIndices[fromIdx] = 0
        shades[fromIdx] = 0
    }

    /**
     * Performs 8-directional left-to-right BFS for each sand color (with RAINBOW acting as wildcard).
     * Returns true if at least one full bridge was found and marked into [clearMask].
     */
    fun detectAndBeginLineClear(): Boolean {
        clearMask.fill(false)
        var anyBridge = false
        var dominantColorArgb = theme.accentColor
        var largestComponent = 0

        for (color in 0 until 5) {
            visited.fill(false)
            val targetByte = color.toByte()

            for (startY in 0 until height) {
                val startIdx = startY * width // x = 0
                if (visited[startIdx]) continue
                if (!matchesColorOrRainbow(startIdx, targetByte)) continue

                var head = 0
                var tail = 0
                bfsQueue[tail++] = startIdx
                visited[startIdx] = true

                var reachesRight = false
                var realColorCount = 0

                while (head < tail) {
                    val cur = bfsQueue[head++]
                    val cx = cur % width
                    val cy = cur / width

                    if (cx == width - 1) reachesRight = true
                    if (cellTypes[cur] == CELL_SAND && colorIndices[cur] == targetByte) {
                        realColorCount++
                    }

                    val minNy = (cy - 1).coerceAtLeast(0)
                    val maxNy = (cy + 1).coerceAtMost(height - 1)
                    val minNx = (cx - 1).coerceAtLeast(0)
                    val maxNx = (cx + 1).coerceAtMost(width - 1)

                    for (ny in minNy..maxNy) {
                        val rowOffset = ny * width
                        for (nx in minNx..maxNx) {
                            val nIdx = rowOffset + nx
                            if (!visited[nIdx] && matchesColorOrRainbow(nIdx, targetByte)) {
                                visited[nIdx] = true
                                bfsQueue[tail++] = nIdx
                            }
                        }
                    }
                }

                // Pure-rainbow bridges are matched once when color == 0; mixed bridges require >=1 grain of `color`
                if (reachesRight && (realColorCount > 0 || color == 0)) {
                    anyBridge = true
                    for (i in 0 until tail) {
                        clearMask[bfsQueue[i]] = true
                    }
                    if (tail > largestComponent) {
                        largestComponent = tail
                        dominantColorArgb = if (realColorCount > 0) {
                            theme.sandPalette[color % theme.sandPalette.size]
                        } else {
                            0xFFFFFFFF.toInt()
                        }
                    }
                }
            }
        }

        if (!anyBridge) return false

        // Find adjacent cracked stone macro-blocks (within 2 grains of cleared sand) and include them!
        val crackedMacrosToBreak = HashSet<Int>()
        var sumX = 0L
        var sumY = 0L
        var sandClearCount = 0

        for (idx in 0 until size) {
            if (!clearMask[idx]) continue
            val cx = idx % width
            val cy = idx / width
            sumX += cx
            sumY += cy
            sandClearCount++

            val minNy = (cy - 2).coerceAtLeast(0)
            val maxNy = (cy + 2).coerceAtMost(height - 1)
            val minNx = (cx - 2).coerceAtLeast(0)
            val maxNx = (cx + 2).coerceAtMost(width - 1)
            for (ny in minNy..maxNy) {
                val rBase = ny * width
                for (nx in minNx..maxNx) {
                    if (cellTypes[rBase + nx] == CELL_CRACKED_STONE) {
                        crackedMacrosToBreak.add((ny / grainScale) * 100 + (nx / grainScale))
                    }
                }
            }
        }

        for (macroKey in crackedMacrosToBreak) {
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            val startX = mCol * grainScale
            val startY = mRow * grainScale
            for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
                val rBase = gy * width
                for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                    val mIdx = rBase + gx
                    if (cellTypes[mIdx] == CELL_CRACKED_STONE) {
                        clearMask[mIdx] = true
                    }
                }
            }
        }

        combo++
        if (combo > maxCombo) maxCombo = combo
        linesCleared++

        val comboMultiplier = 1f + (combo - 1) * 0.65f
        val basePoints = sandClearCount * 2 + crackedMacrosToBreak.size * 150
        val awarded = (basePoints * comboMultiplier).toInt().coerceAtLeast(100)
        score += awarded

        val avgX = if (sandClearCount > 0) (sumX.toFloat() / sandClearCount) else width / 2f
        val avgY = if (sandClearCount > 0) (sumY.toFloat() / sandClearCount) else height / 2f

        clearFlashTimer = CLEAR_FLASH_DURATION
        pendingEvents.add(
            WorldEvent.LineClearStarted(
                combo = combo,
                grainCount = sandClearCount,
                crackedBlocksBroken = crackedMacrosToBreak.size,
                centerX = avgX,
                centerY = avgY,
                colorArgb = dominantColorArgb,
                pointsAwarded = awarded
            )
        )
        return true
    }

    fun finalizeMarkedClears() {
        for (i in 0 until size) {
            if (clearMask[i]) {
                cellTypes[i] = CELL_EMPTY
                colorIndices[i] = 0
                shades[i] = 0
                clearMask[i] = false
            }
        }
        settleQuietTicks = 0
    }

    private fun matchesColorOrRainbow(idx: Int, colorByte: Byte): Boolean {
        val t = cellTypes[idx]
        return (t == CELL_SAND && colorIndices[idx] == colorByte) || t == CELL_RAINBOW
    }

    fun countRemainingCrackedStones(): Int {
        val macros = HashSet<Int>()
        for (i in 0 until size) {
            if (cellTypes[i] == CELL_CRACKED_STONE) {
                val x = i % width
                val y = i / width
                macros.add((y / grainScale) * 100 + (x / grainScale))
            }
        }
        return macros.size
    }

    fun highestSandRow(): Int {
        for (y in 0 until height) {
            val rBase = y * width
            for (x in 0 until width) {
                val t = cellTypes[rBase + x]
                if (t == CELL_SAND || t == CELL_RAINBOW) {
                    return y
                }
            }
        }
        return height
    }

    private fun evaluateEndConditions() {
        if (status != GameStatus.PLAYING || clearFlashTimer > 0) return

        // 1. Check Adventure Victory first
        adventureLevel?.let { lvl ->
            val scoreOk = score >= lvl.targetScore
            val clearsOk = linesCleared >= lvl.targetClears
            val stonesOk = !lvl.requireClearCrackedStones || countRemainingCrackedStones() == 0

            if (scoreOk && clearsOk && stonesOk) {
                val remainingBonus = ((lvl.maxMoves - movesUsed).coerceAtLeast(0)) * 60
                score += remainingBonus
                val stars = when {
                    score >= lvl.star3Score -> 3
                    score >= lvl.star2Score -> 2
                    else -> 1
                }
                status = GameStatus.WON
                pendingEvents.add(WorldEvent.LevelWon(stars = stars, finalScore = score))
                return
            }
        }

        // 2. Check Danger Line breach
        if (highestSandRow() < dangerRow) {
            status = GameStatus.GAME_OVER
            pendingEvents.add(WorldEvent.GameLost)
            return
        }

        // 3. Check Adventure Move Exhaustion
        adventureLevel?.let { lvl ->
            if (lvl.maxMoves > 0 && movesUsed >= lvl.maxMoves) {
                status = GameStatus.GAME_OVER
                pendingEvents.add(WorldEvent.GameLost)
                return
            }
        }
    }

    /**
     * Renders current cell state directly into a reusable [IntArray] ARGB pixel buffer of length [size].
     */
    fun renderPixels(outPixels: IntArray) {
        val bg = theme.boardBg
        val gridLine = theme.boardGridLine
        val palette = theme.sandPalette
        val stoneBase = theme.stoneBaseColor
        val crackedBase = theme.crackedStoneColor
        val flashProgress = if (clearFlashTimer > 0) {
            1f - (clearFlashTimer.toFloat() / CLEAR_FLASH_DURATION)
        } else {
            0f
        }

        for (y in 0 until height) {
            val rowBase = y * width
            val isMacroRowBorder = (y % grainScale) == 0
            for (x in 0 until width) {
                val idx = rowBase + x
                val type = cellTypes[idx]

                if (clearMask[idx] && clearFlashTimer > 0) {
                    val sparkle = if (((x + y + tickCount) and 1) == 0) 0xFFFFFFFF.toInt() else theme.accentColor
                    outPixels[idx] = ColorMath.blend(sparkle, 0xFFFFF3B0.toInt(), flashProgress)
                    continue
                }

                when (type) {
                    CELL_EMPTY -> {
                        val isGrid = isMacroRowBorder || (x % grainScale) == 0
                        outPixels[idx] = if (isGrid) {
                            ColorMath.blend(bg, gridLine or (0xFF shl 24), 0.08f)
                        } else {
                            bg
                        }
                    }
                    CELL_SAND -> {
                        val base = palette[colorIndices[idx].toInt().coerceIn(0, palette.size - 1)]
                        outPixels[idx] = ColorMath.shade(base, shades[idx].toInt())
                    }
                    CELL_RAINBOW -> {
                        val rb = ColorMath.rainbowColor(x * 3 + y * 5, tickCount)
                        outPixels[idx] = ColorMath.shade(rb, shades[idx].toInt() / 2)
                    }
                    CELL_STONE -> {
                        val localX = x % grainScale
                        val localY = y % grainScale
                        val bevel = when {
                            localX == 0 || localY == 0 -> 24
                            localX == grainScale - 1 || localY == grainScale - 1 -> -26
                            else -> shades[idx].toInt() / 2
                        }
                        outPixels[idx] = ColorMath.shade(stoneBase, bevel)
                    }
                    CELL_CRACKED_STONE -> {
                        val localX = x % grainScale
                        val localY = y % grainScale
                        val isCrackLine = (localX == localY && localX in 1..6) ||
                            (localX + localY == grainScale - 1 && localX in 2..5)
                        val bevel = when {
                            isCrackLine -> -58
                            localX == 0 || localY == 0 -> 28
                            localX == grainScale - 1 || localY == grainScale - 1 -> -22
                            else -> shades[idx].toInt() / 3
                        }
                        outPixels[idx] = ColorMath.shade(crackedBase, bevel)
                    }
                }
            }
        }
    }

    private fun destroyMacroStone(macroCol: Int, macroRow: Int, targetType: Byte): Int {
        var count = 0
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
            val rBase = gy * width
            for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                val idx = rBase + gx
                if (cellTypes[idx] == targetType) {
                    cellTypes[idx] = CELL_EMPTY
                    clearMask[idx] = false
                    count++
                }
            }
        }
        return count
    }

    fun placeMacroStone(macroCol: Int, macroRow: Int, cracked: Boolean = false) {
        val maxCols = width / grainScale
        val maxRows = height / grainScale
        if (macroCol !in 0 until maxCols || macroRow !in 0 until maxRows) return
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        val t = if (cracked) CELL_CRACKED_STONE else CELL_STONE
        for (gy in startY until startY + grainScale) {
            val rBase = gy * width
            for (gx in startX until startX + grainScale) {
                val idx = rBase + gx
                cellTypes[idx] = t
                shades[idx] = random.nextInt(-10, 11).toByte()
            }
        }
    }

    private fun pourPresetSand(macroCol: Int, macroRow: Int, colorIdx: Int) {
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        val c = colorIdx.coerceIn(0, 4).toByte()
        for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
            val rBase = gy * width
            for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                val idx = rBase + gx
                if (cellTypes[idx] == CELL_EMPTY) {
                    cellTypes[idx] = CELL_SAND
                    colorIndices[idx] = c
                    shades[idx] = random.nextInt(-14, 15).toByte()
                }
            }
        }
    }

    private fun applyTerrainPreset(preset: TerrainPreset, colors: Int) {
        when (preset) {
            TerrainPreset.NONE -> {
                pourPresetSand(1, 13, 0)
                pourPresetSand(7, 13, 1 % colors)
            }
            TerrainPreset.CENTER_STEP -> {
                placeMacroStone(4, 10)
                placeMacroStone(3, 11)
                placeMacroStone(4, 11)
                placeMacroStone(5, 11)
            }
            TerrainPreset.CRACKED_BRIDGE -> {
                placeMacroStone(2, 10, cracked = true)
                placeMacroStone(4, 10, cracked = true)
                placeMacroStone(6, 10, cracked = true)
            }
            TerrainPreset.SIDE_SHELVES -> {
                placeMacroStone(0, 8)
                placeMacroStone(1, 8)
                placeMacroStone(7, 8)
                placeMacroStone(8, 8)
            }
            TerrainPreset.OCEAN_FUNNEL -> {
                placeMacroStone(1, 7)
                placeMacroStone(2, 8)
                placeMacroStone(7, 7)
                placeMacroStone(6, 8)
            }
            TerrainPreset.CRACKED_PYRAMID -> {
                placeMacroStone(4, 9, cracked = true)
                placeMacroStone(3, 10, cracked = true)
                placeMacroStone(5, 10, cracked = true)
                placeMacroStone(2, 11, cracked = true)
                placeMacroStone(6, 11, cracked = true)
            }
            TerrainPreset.TWIN_PILLARS -> {
                placeMacroStone(2, 8)
                placeMacroStone(2, 9)
                placeMacroStone(6, 8)
                placeMacroStone(6, 9)
            }
            TerrainPreset.CYBER_HOURGLASS -> {
                placeMacroStone(0, 7)
                placeMacroStone(1, 8)
                placeMacroStone(8, 7)
                placeMacroStone(7, 8)
                placeMacroStone(4, 11, cracked = true)
            }
            TerrainPreset.CRACKED_FORTRESS -> {
                placeMacroStone(1, 9, cracked = true)
                placeMacroStone(3, 9, cracked = true)
                placeMacroStone(5, 9, cracked = true)
                placeMacroStone(7, 9, cracked = true)
                placeMacroStone(4, 7, cracked = true)
            }
            TerrainPreset.PASTEL_TERRACES -> {
                placeMacroStone(1, 7)
                placeMacroStone(4, 9)
                placeMacroStone(7, 7)
            }
            TerrainPreset.NEON_GATES -> {
                placeMacroStone(2, 7)
                placeMacroStone(4, 7, cracked = true)
                placeMacroStone(6, 7)
                placeMacroStone(3, 10)
                placeMacroStone(5, 10)
            }
            TerrainPreset.CROWN_LABYRINTH -> {
                placeMacroStone(1, 7, cracked = true)
                placeMacroStone(4, 6, cracked = true)
                placeMacroStone(7, 7, cracked = true)
                placeMacroStone(2, 10, cracked = true)
                placeMacroStone(4, 10)
                placeMacroStone(6, 10, cracked = true)
            }
        }
        // Let any preset sand settle naturally
        repeat(45) {
            stepPhysicsOnce()
        }
        tickCount = 0
    }
}
