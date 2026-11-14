package com.hnmlabs.sandblock.engine

import com.hnmlabs.sandblock.model.AdventureLevel
import com.hnmlabs.sandblock.model.BlockShape
import com.hnmlabs.sandblock.model.CellOffset
import com.hnmlabs.sandblock.model.ColorMath
import com.hnmlabs.sandblock.model.GameMode
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PieceGenerator
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.TerrainPreset
import com.hnmlabs.sandblock.model.TrayPiece
import java.util.Base64
import kotlin.random.Random

sealed interface WorldEvent {
    data class PiecePlaced(
        val centerX: Float,
        val centerY: Float,
        val colorArgb: Int,
        val isRainbow: Boolean,
        val isLava: Boolean = false
    ) : WorldEvent

    data class LineClearStarted(
        val combo: Int,
        val grainCount: Int,
        val crackedBlocksBroken: Int,
        val centerX: Float,
        val centerY: Float,
        val colorArgb: Int,
        val pointsAwarded: Int,
        val coinsAwarded: Int,
        val feverBonusActive: Boolean = false
    ) : WorldEvent

    data class BombExploded(
        val centerX: Float,
        val centerY: Float,
        val destroyedGrains: Int,
        val pointsAwarded: Int
    ) : WorldEvent

    data class LaserFired(
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

    data class SandStormCleared(
        val topY: Float,
        val destroyedGrains: Int,
        val pointsAwarded: Int
    ) : WorldEvent

    data class LavaMelted(
        val centerX: Float,
        val centerY: Float,
        val meltedCount: Int,
        val pointsAwarded: Int
    ) : WorldEvent

    data object FeverStarted : WorldEvent
    data class TimeBonusAdded(val bonusSeconds: Float) : WorldEvent
    data object Revived : WorldEvent
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
        const val CELL_ICE: Byte = 5
        const val CELL_THIN_ICE: Byte = 6
        const val CELL_LAVA: Byte = 7

        const val CLEAR_FLASH_DURATION = 13 // ~215ms at 60fps
        const val BOMB_RADIUS = 18
        const val FEVER_DURATION_SEC = 10f
        const val TIME_RUSH_INITIAL_SEC = 90f
        private const val LAVA_INITIAL_FUEL: Byte = 3
    }

    val size: Int = width * height
    val cellTypes = ByteArray(size)
    val colorIndices = ByteArray(size)
    val shades = ByteArray(size)
    val lavaFuel = ByteArray(size)
    private val lavaRestTicks = ByteArray(size)
    val clearMask = BooleanArray(size)

    private val visited = BooleanArray(size)
    private val bfsQueue = IntArray(size)
    private val pendingEvents = ArrayList<WorldEvent>(10)

    private val pieceGenerator = PieceGenerator(random)
    val trayPieces = arrayOfNulls<TrayPiece>(3)
    var holdPiece: TrayPiece? = null
        private set

    var mode: GameMode = GameMode.CLASSIC
        private set
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
    private var bonusMovesGranted: Int = 0
    var status: GameStatus = GameStatus.PLAYING
        private set

    // Fever state
    var feverCharge: Float = 0f
        private set
    var feverRemainingSec: Float = 0f
        private set
    val isFeverActive: Boolean
        get() = feverRemainingSec > 0f

    // Time Rush state
    var timeRemainingSec: Float = TIME_RUSH_INITIAL_SEC
        private set

    // Sandtrix Real-Time Falling Block state
    var fallingPiece: TrayPiece? = null
        private set
    var nextFallingPiece: TrayPiece? = null
        private set
    var fallingX: Int = 0
        private set
    var fallingY: Float = 0f
        private set
    var canSwapHoldThisDrop: Boolean = true
        private set
    private var fallingLockTimer: Float = 0f

    var tickCount: Int = 0
        private set
    var clearFlashTimer: Int = 0
        private set
    private var settleQuietTicks: Int = 0

    private var lavaMeltedAccumCount = 0
    private var lavaMeltedAccumPoints = 0
    private var lavaLastMeltX = width / 2f
    private var lavaLastMeltY = height / 2f

    val isBoardQuiet: Boolean
        get() = settleQuietTicks >= 4 && clearFlashTimer == 0

    val activeColorCount: Int
        get() = adventureLevel?.activeColors ?: when {
            score >= 4800 -> 5
            score >= 1500 -> 4
            else -> 3
        }

    val movesRemaining: Int?
        get() = adventureLevel?.let { lvl ->
            if (lvl.maxMoves > 0) (lvl.maxMoves + bonusMovesGranted - movesUsed).coerceAtLeast(0) else null
        }

    fun drainEvents(): List<WorldEvent> {
        if (pendingEvents.isEmpty()) return emptyList()
        val copy = ArrayList(pendingEvents)
        pendingEvents.clear()
        return copy
    }

    fun startClassic(selectedTheme: GameTheme) {
        startMode(GameMode.CLASSIC, selectedTheme)
    }

    fun startMode(gameMode: GameMode, selectedTheme: GameTheme) {
        mode = if (gameMode == GameMode.ADVENTURE) GameMode.CLASSIC else gameMode
        theme = selectedTheme
        adventureLevel = null
        resetBoardState()
        if (mode == GameMode.TIME_RUSH) {
            timeRemainingSec = TIME_RUSH_INITIAL_SEC
        }
        if (mode == GameMode.SANDTRIX) {
            nextFallingPiece = pieceGenerator.nextSinglePiece(activeColorCount, allowSpecialPieces = false)
            spawnNextFallingPiece()
        } else {
            refillTray()
        }
    }

    fun startAdventure(level: AdventureLevel, selectedThemeOverride: GameTheme? = null) {
        mode = GameMode.ADVENTURE
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
        lavaFuel.fill(0)
        lavaRestTicks.fill(0)
        clearMask.fill(false)
        pendingEvents.clear()
        pieceGenerator.reset()
        trayPieces.fill(null)
        holdPiece = null

        score = 0
        linesCleared = 0
        combo = 0
        maxCombo = 0
        movesUsed = 0
        bonusMovesGranted = 0
        status = GameStatus.PLAYING

        feverCharge = 0f
        feverRemainingSec = 0f
        timeRemainingSec = TIME_RUSH_INITIAL_SEC

        fallingPiece = null
        nextFallingPiece = null
        fallingX = 0
        fallingY = 0f
        canSwapHoldThisDrop = true
        fallingLockTimer = 0f

        tickCount = 0
        clearFlashTimer = 0
        settleQuietTicks = 0
        lavaMeltedAccumCount = 0
        lavaMeltedAccumPoints = 0
    }

    private fun refillTray() {
        val next = pieceGenerator.nextTray(activeColorCount, allowSpecialPieces = true)
        for (i in 0 until 3) {
            trayPieces[i] = next[i]
        }
    }

    // --- Tray & Hold Controls ---

    fun rotateTrayPiece(slot: Int): Boolean {
        if (slot !in 0..2 || status != GameStatus.PLAYING) return false
        val piece = trayPieces[slot] ?: return false
        if (piece.kind == PieceKind.BOMB) return false
        trayPieces[slot] = piece.rotated()
        return true
    }

    fun rotateHoldPiece(): Boolean {
        if (status != GameStatus.PLAYING) return false
        val piece = holdPiece ?: return false
        if (piece.kind == PieceKind.BOMB) return false
        holdPiece = piece.rotated()
        return true
    }

    fun holdOrSwapTrayPiece(slot: Int): Boolean {
        if (slot !in 0..2 || status != GameStatus.PLAYING || mode == GameMode.SANDTRIX) return false
        val incoming = trayPieces[slot] ?: return false
        val previousHold = holdPiece
        holdPiece = incoming
        trayPieces[slot] = previousHold
        if (trayPieces.all { it == null }) {
            refillTray()
        }
        return true
    }

    fun placeHoldPiece(rawX: Int, rawY: Int): Boolean {
        if (status != GameStatus.PLAYING || mode == GameMode.SANDTRIX) return false
        val piece = holdPiece ?: return false
        val placement = findValidPlacement(piece, rawX, rawY) ?: return false
        holdPiece = null
        commitPieceToBoard(piece, placement.first, placement.second)
        return true
    }

    fun upgradeTrayPieceToRainbow(): Int {
        if (status != GameStatus.PLAYING) return -1
        if (mode == GameMode.SANDTRIX) {
            val fp = fallingPiece
            if (fp != null && fp.kind != PieceKind.RAINBOW) {
                fallingPiece = pieceGenerator.upgradePieceToRainbow(fp)
                return 0
            }
            val np = nextFallingPiece
            if (np != null && np.kind != PieceKind.RAINBOW) {
                nextFallingPiece = pieceGenerator.upgradePieceToRainbow(np)
                return 1
            }
            return -1
        }

        val targetSlot = (0..2).firstOrNull { slot ->
            val p = trayPieces[slot]
            p != null && p.kind == PieceKind.NORMAL
        } ?: (0..2).firstOrNull { trayPieces[it] != null && trayPieces[it]?.kind != PieceKind.RAINBOW }
            ?: return -1

        val current = trayPieces[targetSlot] ?: return -1
        trayPieces[targetSlot] = pieceGenerator.upgradePieceToRainbow(current)
        return targetSlot
    }

    fun rerollTray(): Boolean {
        if (status != GameStatus.PLAYING) return false
        if (mode == GameMode.SANDTRIX) {
            nextFallingPiece = pieceGenerator.nextSinglePiece(activeColorCount, allowSpecialPieces = true)
            spawnNextFallingPiece()
            return true
        }
        refillTray()
        return true
    }

    // --- Sandtrix Real-Time Falling Block Controls ---

    private fun spawnNextFallingPiece() {
        val next = nextFallingPiece ?: pieceGenerator.nextSinglePiece(activeColorCount, allowSpecialPieces = true)
        nextFallingPiece = pieceGenerator.nextSinglePiece(activeColorCount, allowSpecialPieces = true)
        val pieceW = next.shape.cols * grainScale
        val spawnX = ((width - pieceW) / 2 / (grainScale / 2)) * (grainScale / 2)
        val spawnY = 2
        fallingPiece = next
        fallingX = spawnX.coerceIn(0, (width - pieceW).coerceAtLeast(0))
        fallingY = spawnY.toFloat()
        fallingLockTimer = 0f
        canSwapHoldThisDrop = true

        if (next.kind != PieceKind.BOMB && !canPlaceShape(next.shape, fallingX, spawnY)) {
            status = GameStatus.GAME_OVER
            pendingEvents.add(WorldEvent.GameLost)
        }
    }

    fun swapSandtrixHold(): Boolean {
        if (mode != GameMode.SANDTRIX || status != GameStatus.PLAYING || !canSwapHoldThisDrop) return false
        val current = fallingPiece ?: return false
        val prevHold = holdPiece
        holdPiece = current
        if (prevHold != null) {
            val pieceW = prevHold.shape.cols * grainScale
            fallingPiece = prevHold
            fallingX = ((width - pieceW) / 2).coerceIn(0, (width - pieceW).coerceAtLeast(0))
            fallingY = 2f
            fallingLockTimer = 0f
        } else {
            spawnNextFallingPiece()
        }
        canSwapHoldThisDrop = false
        return true
    }

    fun moveFallingHorizontal(deltaGrains: Int): Boolean {
        if (mode != GameMode.SANDTRIX || status != GameStatus.PLAYING) return false
        val piece = fallingPiece ?: return false
        val pieceW = piece.shape.cols * grainScale
        val targetX = (fallingX + deltaGrains).coerceIn(0, (width - pieceW).coerceAtLeast(0))
        if (targetX == fallingX) return false

        val intY = fallingY.toInt()
        if (piece.kind == PieceKind.BOMB || canPlaceShape(piece.shape, targetX, intY)) {
            fallingX = targetX
            return true
        }
        // Allow slight 2-grain upward step over small sand slopes
        if (intY >= 2 && canPlaceShape(piece.shape, targetX, intY - 2)) {
            fallingX = targetX
            fallingY = (intY - 2).toFloat()
            return true
        }
        return false
    }

    fun rotateFallingPiece(): Boolean {
        if (mode != GameMode.SANDTRIX || status != GameStatus.PLAYING) return false
        val piece = fallingPiece ?: return false
        if (piece.kind == PieceKind.BOMB) return false
        val rotated = piece.rotated()
        val rotW = rotated.shape.cols * grainScale
        val intY = fallingY.toInt()
        val kicks = intArrayOf(0, -4, 4, -grainScale, grainScale)
        for (dx in kicks) {
            val testX = (fallingX + dx).coerceIn(0, (width - rotW).coerceAtLeast(0))
            if (canPlaceShape(rotated.shape, testX, intY)) {
                fallingPiece = rotated
                fallingX = testX
                return true
            }
        }
        return false
    }

    fun computeFallingGhostY(): Int {
        val piece = fallingPiece ?: return 0
        val pieceH = piece.shape.rows * grainScale
        var testY = fallingY.toInt().coerceAtLeast(0)
        if (piece.kind == PieceKind.BOMB) {
            val cx = (fallingX + (piece.shape.cols * grainScale) / 2).coerceIn(0, width - 1)
            while (testY + pieceH < height && cellTypes[(testY + pieceH) * width + cx] == CELL_EMPTY) {
                testY++
            }
            return testY
        }
        while (testY + 1 <= height - pieceH && canPlaceShape(piece.shape, fallingX, testY + 1)) {
            testY++
        }
        return testY
    }

    fun hardDropFallingPiece(): Boolean {
        if (mode != GameMode.SANDTRIX || status != GameStatus.PLAYING) return false
        val piece = fallingPiece ?: return false
        val dropY = computeFallingGhostY()
        val droppedDist = (dropY - fallingY.toInt()).coerceAtLeast(0)
        score += (droppedDist / 2).coerceAtLeast(6)
        fallingPiece = null
        commitPieceToBoard(piece, fallingX, dropY)
        if (status == GameStatus.PLAYING) {
            spawnNextFallingPiece()
        }
        return true
    }

    private fun updateSandtrixFalling(dt: Float) {
        if (mode != GameMode.SANDTRIX || status != GameStatus.PLAYING || clearFlashTimer > 0) return
        val piece = fallingPiece
        if (piece == null) {
            spawnNextFallingPiece()
            return
        }

        val pieceH = piece.shape.rows * grainScale
        val speedGrainsPerSec = 15f + (score / 700f).coerceAtMost(24f)
        val desiredY = fallingY + speedGrainsPerSec * dt
        var curIntY = fallingY.toInt()
        val targetIntY = desiredY.toInt()

        var blocked = false
        while (curIntY < targetIntY) {
            val nextY = curIntY + 1
            val canMove = if (piece.kind == PieceKind.BOMB) {
                val cx = (fallingX + (piece.shape.cols * grainScale) / 2).coerceIn(0, width - 1)
                nextY + pieceH <= height && cellTypes[(nextY + pieceH - 1) * width + cx] == CELL_EMPTY
            } else {
                nextY + pieceH <= height && canPlaceShape(piece.shape, fallingX, nextY)
            }
            if (canMove) {
                curIntY = nextY
            } else {
                blocked = true
                break
            }
        }

        if (!blocked) {
            // Also peek 1 grain below curIntY to know if resting on surface
            val resting = if (piece.kind == PieceKind.BOMB) {
                val cx = (fallingX + (piece.shape.cols * grainScale) / 2).coerceIn(0, width - 1)
                curIntY + pieceH >= height || cellTypes[(curIntY + pieceH) * width + cx] != CELL_EMPTY
            } else {
                curIntY + pieceH >= height || !canPlaceShape(piece.shape, fallingX, curIntY + 1)
            }
            fallingY = if (resting) curIntY.toFloat() else desiredY
            if (resting) {
                fallingLockTimer += dt
            } else {
                fallingLockTimer = 0f
            }
        } else {
            fallingY = curIntY.toFloat()
            fallingLockTimer += dt
        }

        if (fallingLockTimer >= 0.22f) {
            fallingPiece = null
            fallingLockTimer = 0f
            commitPieceToBoard(piece, fallingX, curIntY)
            if (status == GameStatus.PLAYING) {
                spawnNextFallingPiece()
            }
        }
    }

    // --- Board Placement & Spawning ---

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
        val clampedX = rawX.coerceIn(0, (width - pieceW).coerceAtLeast(0))
        val clampedY = rawY.coerceIn(0, (height - pieceH).coerceAtLeast(0))

        if (piece.kind == PieceKind.BOMB) {
            return clampedX to clampedY
        }

        if (canPlaceShape(piece.shape, clampedX, clampedY)) {
            return clampedX to clampedY
        }

        // Responsive snap search above sand crests & near left/right walls
        for (radius in 1..12) {
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
        if (slot !in 0..2 || status != GameStatus.PLAYING || mode == GameMode.SANDTRIX) return false
        val piece = trayPieces[slot] ?: return false
        val placement = findValidPlacement(piece, rawX, rawY) ?: return false
        trayPieces[slot] = null

        commitPieceToBoard(piece, placement.first, placement.second)

        if (trayPieces.all { it == null }) {
            refillTray()
        }
        return true
    }

    private fun commitPieceToBoard(piece: TrayPiece, originX: Int, originY: Int) {
        movesUsed++
        combo = 0
        settleQuietTicks = 0
        addFeverCharge(0.07f)

        if (piece.kind == PieceKind.BOMB) {
            val pieceW = piece.shape.cols * grainScale
            val pieceH = piece.shape.rows * grainScale
            val cx = (originX + pieceW / 2).coerceIn(0, width - 1)
            var cy = (originY + pieceH / 2).coerceIn(0, height - 1)
            while (cy < height - 1 &&
                cellTypes[(cy + 1) * width + cx] == CELL_EMPTY &&
                cellTypes[cy * width + cx] == CELL_EMPTY
            ) {
                cy++
            }
            detonateBomb(cx, cy)
        } else {
            spawnPieceGrains(piece, originX, originY)
            val cx = originX + (piece.shape.cols * grainScale) / 2f
            val cy = originY + (piece.shape.rows * grainScale) / 2f
            val baseColor = when (piece.kind) {
                PieceKind.RAINBOW -> 0xFFFFFFFF.toInt()
                PieceKind.LAVA -> 0xFFFF5400.toInt()
                else -> theme.sandPalette[piece.colorIndex % theme.sandPalette.size]
            }
            pendingEvents.add(
                WorldEvent.PiecePlaced(
                    centerX = cx,
                    centerY = cy,
                    colorArgb = baseColor,
                    isRainbow = piece.kind == PieceKind.RAINBOW,
                    isLava = piece.kind == PieceKind.LAVA
                )
            )
        }
    }

    private fun addFeverCharge(delta: Float) {
        if (isFeverActive) return
        feverCharge = (feverCharge + delta).coerceIn(0f, 1f)
        if (feverCharge >= 1f) {
            feverCharge = 0f
            feverRemainingSec = FEVER_DURATION_SEC
            pendingEvents.add(WorldEvent.FeverStarted)
        }
    }

    fun triggerFeverForTest() {
        feverCharge = 0f
        feverRemainingSec = FEVER_DURATION_SEC
    }

    fun spawnPieceGrains(piece: TrayPiece, originX: Int, originY: Int) {
        val targetType = when (piece.kind) {
            PieceKind.RAINBOW -> CELL_RAINBOW
            PieceKind.LAVA -> CELL_LAVA
            else -> CELL_SAND
        }
        val cIdx = (piece.colorIndex.coerceIn(0, 4)).toByte()

        for (cell in piece.shape.cells) {
            val startX = originX + cell.col * grainScale
            val startY = originY + cell.row * grainScale
            for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
                val rowBase = gy * width
                val isTopEdge = gy == startY
                val isBottomEdge = gy == startY + grainScale - 1
                for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                    val idx = rowBase + gx
                    if (cellTypes[idx] == CELL_EMPTY) {
                        cellTypes[idx] = targetType
                        colorIndices[idx] = cIdx
                        if (targetType == CELL_LAVA) {
                            lavaFuel[idx] = LAVA_INITIAL_FUEL
                            lavaRestTicks[idx] = 0
                        }
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

    // --- Power-Ups & Special Interventions ---

    fun pourLavaShower(): Int {
        if (status != GameStatus.PLAYING) return 0
        var spawned = 0
        val streamCols = intArrayOf(8, 22, 36, 50, 63)
        for (centerCol in streamCols) {
            for (gy in 2..7) {
                for (gx in (centerCol - 2)..(centerCol + 2)) {
                    if (gx in 0 until width && gy in 0 until height) {
                        val idx = gy * width + gx
                        if (cellTypes[idx] == CELL_EMPTY) {
                            cellTypes[idx] = CELL_LAVA
                            lavaFuel[idx] = 4
                            lavaRestTicks[idx] = 0
                            spawned++
                        }
                    }
                }
            }
        }
        if (spawned > 0) {
            score += 120
            settleQuietTicks = 0
            pendingEvents.add(
                WorldEvent.PiecePlaced(
                    centerX = width / 2f,
                    centerY = 6f,
                    colorArgb = 0xFFFF5400.toInt(),
                    isRainbow = false,
                    isLava = true
                )
            )
        }
        return spawned
    }

    fun detonateBomb(centerX: Int, centerY: Int, radius: Int = BOMB_RADIUS): Int {
        if (status != GameStatus.PLAYING) return 0
        var destroyed = 0
        val minX = (centerX - radius).coerceAtLeast(0)
        val maxX = (centerX + radius).coerceAtMost(width - 1)
        val minY = (centerY - radius).coerceAtLeast(0)
        val maxY = (centerY + radius).coerceAtMost(height - 1)
        val rSq = radius * radius

        val breakableMacrosHit = HashSet<Int>()

        for (y in minY..maxY) {
            val dy = y - centerY
            val rowBase = y * width
            for (x in minX..maxX) {
                val dx = x - centerX
                if (dx * dx + dy * dy <= rSq) {
                    val idx = rowBase + x
                    val type = cellTypes[idx]
                    if (type != CELL_EMPTY) {
                        if (isBreakableObstacle(type)) {
                            breakableMacrosHit.add((y / grainScale) * 100 + (x / grainScale))
                        }
                        clearSingleCell(idx)
                        destroyed++
                    }
                }
            }
        }

        for (macroKey in breakableMacrosHit) {
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            destroyed += destroyBreakableMacroAt(mCol, mRow)
        }

        val points = (destroyed * 2 + 80) * (if (isFeverActive) 2 else 1)
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
                clearSingleCell(i)
                destroyed++
            }
        }
        if (destroyed > 0) {
            val pts = (destroyed * 2 + 100) * (if (isFeverActive) 2 else 1)
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

    fun fireHorizontalLaser(centerY: Int, halfBand: Int = 5): Int {
        if (status != GameStatus.PLAYING) return 0
        val minY = (centerY - halfBand).coerceAtLeast(0)
        val maxY = (centerY + halfBand).coerceAtMost(height - 1)
        var destroyed = 0
        val breakableMacrosHit = HashSet<Int>()

        for (y in minY..maxY) {
            val rowBase = y * width
            for (x in 0 until width) {
                val idx = rowBase + x
                val t = cellTypes[idx]
                if (t != CELL_EMPTY) {
                    if (isBreakableObstacle(t)) {
                        breakableMacrosHit.add((y / grainScale) * 100 + (x / grainScale))
                    }
                    clearSingleCell(idx)
                    destroyed++
                }
            }
        }

        for (macroKey in breakableMacrosHit) {
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            destroyed += destroyBreakableMacroAt(mCol, mRow)
        }

        val pts = (destroyed * 2 + 95) * (if (isFeverActive) 2 else 1)
        score += pts
        settleQuietTicks = 0
        pendingEvents.add(
            WorldEvent.LaserFired(
                centerY = centerY.coerceIn(0, height - 1).toFloat(),
                destroyedGrains = destroyed,
                pointsAwarded = pts
            )
        )
        return destroyed
    }

    fun clearTopDangerSand(rowsToClear: Int = 28): Int {
        if (status != GameStatus.PLAYING) return 0
        val topRow = highestSandRow()
        if (topRow >= height) return 0
        val endRow = (topRow + rowsToClear).coerceAtMost(height)
        var destroyed = 0

        for (y in topRow until endRow) {
            val rowBase = y * width
            for (x in 0 until width) {
                val idx = rowBase + x
                val t = cellTypes[idx]
                if (t == CELL_SAND || t == CELL_RAINBOW || t == CELL_LAVA) {
                    clearSingleCell(idx)
                    destroyed++
                }
            }
        }

        if (destroyed > 0) {
            val pts = destroyed * 2 + 120
            score += pts
            settleQuietTicks = 0
            pendingEvents.add(
                WorldEvent.SandStormCleared(
                    topY = ((topRow + endRow) * 0.5f),
                    destroyedGrains = destroyed,
                    pointsAwarded = pts
                )
            )
        }
        return destroyed
    }

    fun reviveContinue(): Boolean {
        if (status != GameStatus.GAME_OVER) return false
        val clearUntilRow = maxOf(dangerRow + 6, (height * 0.58f).toInt()).coerceAtMost(height)
        for (y in 0 until clearUntilRow) {
            val rBase = y * width
            for (x in 0 until width) {
                val idx = rBase + x
                val t = cellTypes[idx]
                if (t == CELL_SAND || t == CELL_RAINBOW || t == CELL_LAVA) {
                    clearSingleCell(idx)
                }
            }
        }
        if (mode == GameMode.TIME_RUSH && timeRemainingSec < 35f) {
            timeRemainingSec = 35f
        }
        if (mode == GameMode.ADVENTURE) {
            bonusMovesGranted += 6
        }
        status = GameStatus.PLAYING
        settleQuietTicks = 0
        if (mode == GameMode.SANDTRIX) {
            fallingPiece = null
            spawnNextFallingPiece()
        }
        pendingEvents.add(WorldEvent.Revived)
        return true
    }

    // --- Frame Update & Cellular Automata Physics ---

    fun updateFrame(physicsSteps: Int = 4, dt: Float = 1f / 60f) {
        if (status != GameStatus.PLAYING) return
        tickCount++

        if (feverRemainingSec > 0f) {
            feverRemainingSec = (feverRemainingSec - dt).coerceAtLeast(0f)
        }

        if (clearFlashTimer > 0) {
            clearFlashTimer--
            if (clearFlashTimer == 0) {
                finalizeMarkedClears()
            }
            return
        }

        if (mode == GameMode.TIME_RUSH) {
            timeRemainingSec = (timeRemainingSec - dt).coerceAtLeast(0f)
            if (timeRemainingSec <= 0f) {
                status = GameStatus.GAME_OVER
                pendingEvents.add(WorldEvent.GameLost)
                return
            }
        }

        if (mode == GameMode.SANDTRIX) {
            updateSandtrixFalling(dt)
            if (status != GameStatus.PLAYING) return
        }

        var verticalMoves = 0
        var totalMoves = 0
        repeat(physicsSteps) {
            val stats = stepPhysicsOnce()
            totalMoves += stats.first
            verticalMoves += stats.second
        }

        flushLavaEventIfReady(force = totalMoves == 0)

        if (verticalMoves <= 10) {
            if (detectAndBeginLineClear()) {
                settleQuietTicks = 0
                return
            }
        }

        if (totalMoves == 0) {
            settleQuietTicks++
            if (settleQuietTicks >= 5) {
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
                if (type != CELL_SAND && type != CELL_RAINBOW && type != CELL_LAVA) continue
                if (clearMask[idx]) continue

                if (type == CELL_LAVA) {
                    if (stepLavaCell(x, y, idx, nextRowBase)) {
                        moved++
                        vertical++
                    }
                    continue
                }

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

        // Evaporate bottom-row resting lava grains smoothly as well
        val bottomBase = (height - 1) * width
        for (x in 0 until width) {
            val bIdx = bottomBase + x
            if (cellTypes[bIdx] == CELL_LAVA) {
                if (!tryLavaMeltSides(x, height - 1, bIdx)) {
                    val nextRest = (lavaRestTicks[bIdx].toInt() + 1)
                    if (nextRest >= 28) {
                        clearSingleCell(bIdx)
                    } else {
                        lavaRestTicks[bIdx] = nextRest.toByte()
                    }
                }
            }
        }

        return moved to vertical
    }

    private fun stepLavaCell(x: Int, y: Int, idx: Int, nextRowBase: Int): Boolean {
        val belowIdx = nextRowBase + x
        val belowType = cellTypes[belowIdx]

        // 1. Fall into empty space below
        if (belowType == CELL_EMPTY) {
            moveCell(idx, belowIdx)
            return true
        }

        // 2. Melt solid/sand directly below!
        if (belowType != CELL_LAVA) {
            meltTargetWithLava(x, y + 1, belowIdx, lavaIdx = idx, moveIntoTarget = true)
            return true
        }

        // 3. Diagonal fall or diagonal melt
        val dir1 = if (((x * 31 + y * 17 + tickCount) and 1) == 0) -1 else 1
        val dir2 = -dir1
        for (dx in intArrayOf(dir1, dir2)) {
            val nx = x + dx
            if (nx !in 0 until width) continue
            val diagIdx = nextRowBase + nx
            val diagType = cellTypes[diagIdx]
            if (diagType == CELL_EMPTY) {
                moveCell(idx, diagIdx)
                return true
            } else if (diagType != CELL_LAVA) {
                meltTargetWithLava(nx, y + 1, diagIdx, lavaIdx = idx, moveIntoTarget = true)
                return true
            }
        }

        // 4. Melt sideways neighbors
        if (tryLavaMeltSides(x, y, idx)) {
            return true
        }

        // 5. Rest counter before cooling/evaporating
        val rest = lavaRestTicks[idx].toInt() + 1
        if (rest >= 28) {
            clearSingleCell(idx)
        } else {
            lavaRestTicks[idx] = rest.toByte()
        }
        return false
    }

    private fun tryLavaMeltSides(x: Int, y: Int, lavaIdx: Int): Boolean {
        for (dx in intArrayOf(-1, 1)) {
            val nx = x + dx
            if (nx in 0 until width) {
                val nIdx = y * width + nx
                val t = cellTypes[nIdx]
                if (t != CELL_EMPTY && t != CELL_LAVA) {
                    meltTargetWithLava(nx, y, nIdx, lavaIdx = lavaIdx, moveIntoTarget = false)
                    return true
                }
            }
        }
        return false
    }

    private fun meltTargetWithLava(
        targetX: Int,
        targetY: Int,
        targetIdx: Int,
        lavaIdx: Int,
        moveIntoTarget: Boolean
    ) {
        val targetType = cellTypes[targetIdx]
        var meltedGrains = 1
        var pts = 3

        if (isBreakableObstacle(targetType)) {
            meltedGrains = destroyBreakableMacroAt(targetX / grainScale, targetY / grainScale).coerceAtLeast(1)
            pts = 95
        } else {
            clearSingleCell(targetIdx)
        }

        score += pts
        lavaMeltedAccumCount += meltedGrains
        lavaMeltedAccumPoints += pts
        lavaLastMeltX = targetX.toFloat()
        lavaLastMeltY = targetY.toFloat()

        val remainingFuel = lavaFuel[lavaIdx].toInt() - (if (meltedGrains > 1) 2 else 1)
        if (remainingFuel <= 0) {
            clearSingleCell(lavaIdx)
        } else {
            lavaFuel[lavaIdx] = remainingFuel.toByte()
            lavaRestTicks[lavaIdx] = 0
            if (moveIntoTarget && cellTypes[targetIdx] == CELL_EMPTY) {
                moveCell(lavaIdx, targetIdx)
            }
        }
    }

    private fun flushLavaEventIfReady(force: Boolean) {
        if (lavaMeltedAccumCount >= 22 || (force && lavaMeltedAccumCount > 0)) {
            pendingEvents.add(
                WorldEvent.LavaMelted(
                    centerX = lavaLastMeltX,
                    centerY = lavaLastMeltY,
                    meltedCount = lavaMeltedAccumCount,
                    pointsAwarded = lavaMeltedAccumPoints
                )
            )
            lavaMeltedAccumCount = 0
            lavaMeltedAccumPoints = 0
        }
    }

    private fun isSolidObstacle(type: Byte): Boolean =
        type == CELL_STONE || type == CELL_CRACKED_STONE || type == CELL_ICE || type == CELL_THIN_ICE

    private fun isBreakableObstacle(type: Byte): Boolean =
        type == CELL_CRACKED_STONE || type == CELL_ICE || type == CELL_THIN_ICE

    private fun canSlideDiagonally(x: Int, y: Int, dx: Int): Boolean {
        val nx = x + dx
        if (nx !in 0 until width) return false
        val targetIdx = (y + 1) * width + nx
        if (cellTypes[targetIdx] != CELL_EMPTY) return false
        val sideType = cellTypes[y * width + nx]
        return !isSolidObstacle(sideType)
    }

    private fun clearSingleCell(idx: Int) {
        cellTypes[idx] = CELL_EMPTY
        colorIndices[idx] = 0
        shades[idx] = 0
        lavaFuel[idx] = 0
        lavaRestTicks[idx] = 0
        clearMask[idx] = false
    }

    private fun moveCell(fromIdx: Int, toIdx: Int) {
        cellTypes[toIdx] = cellTypes[fromIdx]
        colorIndices[toIdx] = colorIndices[fromIdx]
        shades[toIdx] = shades[fromIdx]
        lavaFuel[toIdx] = lavaFuel[fromIdx]
        lavaRestTicks[toIdx] = 0
        clearSingleCell(fromIdx)
    }

    // --- Line Clear BFS & Obstacle Thawing/Shattering ---

    fun detectAndBeginLineClear(): Boolean {
        clearMask.fill(false)
        var anyBridge = false
        var dominantColorArgb = theme.accentColor
        var largestComponent = 0

        for (color in 0 until 5) {
            visited.fill(false)
            val targetByte = color.toByte()

            for (startY in 0 until height) {
                val startIdx = startY * width
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

        val macrosToShatter = HashSet<Int>()
        val solidIceMacrosToThaw = HashSet<Int>()
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
                    val neighborType = cellTypes[rBase + nx]
                    val macroKey = (ny / grainScale) * 100 + (nx / grainScale)
                    when (neighborType) {
                        CELL_CRACKED_STONE, CELL_THIN_ICE -> macrosToShatter.add(macroKey)
                        CELL_ICE -> solidIceMacrosToThaw.add(macroKey)
                    }
                }
            }
        }

        // Shatter 1-hit cracked stone and thin ice
        for (macroKey in macrosToShatter) {
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            val startX = mCol * grainScale
            val startY = mRow * grainScale
            for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
                val rBase = gy * width
                for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                    val mIdx = rBase + gx
                    if (cellTypes[mIdx] == CELL_CRACKED_STONE || cellTypes[mIdx] == CELL_THIN_ICE) {
                        clearMask[mIdx] = true
                    }
                }
            }
        }

        // Thaw 2-hit solid ice into cracked thin ice (1st hit!)
        for (macroKey in solidIceMacrosToThaw) {
            if (macroKey in macrosToShatter) continue
            val mRow = macroKey / 100
            val mCol = macroKey % 100
            thawMacroIce(mCol, mRow)
        }

        combo++
        if (combo > maxCombo) maxCombo = combo
        linesCleared++

        val feverActiveBefore = isFeverActive
        addFeverCharge(0.34f + (combo - 1) * 0.18f)
        val feverBonusNow = feverActiveBefore || isFeverActive
        val feverMult = if (feverBonusNow) 2 else 1

        val comboMultiplier = 1f + (combo - 1) * 0.65f
        val basePoints = sandClearCount * 2 + macrosToShatter.size * 150 + solidIceMacrosToThaw.size * 70
        val awarded = ((basePoints * comboMultiplier).toInt().coerceAtLeast(100)) * feverMult
        score += awarded

        val rawCoins = (6 + (combo - 1) * 5 + macrosToShatter.size * 4 + (sandClearCount / 90)) * feverMult

        if (mode == GameMode.TIME_RUSH) {
            val bonusSec = 5f + (combo - 1) * 2.5f
            timeRemainingSec = (timeRemainingSec + bonusSec).coerceAtMost(120f)
            pendingEvents.add(WorldEvent.TimeBonusAdded(bonusSec))
        }

        val avgX = if (sandClearCount > 0) (sumX.toFloat() / sandClearCount) else width / 2f
        val avgY = if (sandClearCount > 0) (sumY.toFloat() / sandClearCount) else height / 2f

        clearFlashTimer = CLEAR_FLASH_DURATION
        pendingEvents.add(
            WorldEvent.LineClearStarted(
                combo = combo,
                grainCount = sandClearCount,
                crackedBlocksBroken = macrosToShatter.size,
                centerX = avgX,
                centerY = avgY,
                colorArgb = dominantColorArgb,
                pointsAwarded = awarded,
                coinsAwarded = rawCoins,
                feverBonusActive = feverBonusNow
            )
        )
        return true
    }

    fun finalizeMarkedClears() {
        for (i in 0 until size) {
            if (clearMask[i]) {
                clearSingleCell(i)
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
            if (isBreakableObstacle(cellTypes[i])) {
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
                val totalAllowed = lvl.maxMoves + bonusMovesGranted
                val remainingBonus = ((totalAllowed - movesUsed).coerceAtLeast(0)) * 60
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
            val totalAllowed = lvl.maxMoves + bonusMovesGranted
            if (lvl.maxMoves > 0 && movesUsed >= totalAllowed) {
                status = GameStatus.GAME_OVER
                pendingEvents.add(WorldEvent.GameLost)
                return
            }
        }
    }

    // --- Rendering ---

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
                        // 3D sunlit top crest highlight on sand dunes
                        val crestBoost = if (y > 0 && cellTypes[idx - width] == CELL_EMPTY) 18 else 0
                        outPixels[idx] = ColorMath.shade(base, shades[idx].toInt() + crestBoost)
                    }
                    CELL_RAINBOW -> {
                        val rb = ColorMath.rainbowColor(x * 3 + y * 5, tickCount)
                        outPixels[idx] = ColorMath.shade(rb, shades[idx].toInt() / 2)
                    }
                    CELL_LAVA -> {
                        val lv = ColorMath.lavaColor(x * 5 + y * 7, tickCount)
                        outPixels[idx] = lv
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
                    CELL_ICE -> {
                        val localX = x % grainScale
                        val localY = y % grainScale
                        val iceBase = 0xFF7BDFF2.toInt()
                        val isGlint = (localX + localY == 3) || (localX == 2 && localY == 2)
                        val bevel = when {
                            isGlint -> 68
                            localX == 0 || localY == 0 -> 44
                            localX == grainScale - 1 || localY == grainScale - 1 -> -28
                            else -> shades[idx].toInt() / 3
                        }
                        outPixels[idx] = ColorMath.shade(iceBase, bevel)
                    }
                    CELL_THIN_ICE -> {
                        val localX = x % grainScale
                        val localY = y % grainScale
                        val thinIceBase = 0xFFB2F7EF.toInt()
                        val isFrostCrack = (localX == localY && localX in 1..6) ||
                            (localX + localY == grainScale - 1 && localX in 1..6)
                        val bevel = when {
                            isFrostCrack -> -65
                            localX == 0 || localY == 0 -> 34
                            localX == grainScale - 1 || localY == grainScale - 1 -> -24
                            else -> shades[idx].toInt() / 3
                        }
                        outPixels[idx] = ColorMath.shade(thinIceBase, bevel)
                    }
                }
            }
        }
    }

    // --- Obstacles & Terrain Presets ---

    private fun destroyBreakableMacroAt(macroCol: Int, macroRow: Int): Int {
        var count = 0
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
            val rBase = gy * width
            for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                val idx = rBase + gx
                if (isBreakableObstacle(cellTypes[idx])) {
                    clearSingleCell(idx)
                    count++
                }
            }
        }
        return count
    }

    private fun thawMacroIce(macroCol: Int, macroRow: Int) {
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        for (gy in startY until (startY + grainScale).coerceAtMost(height)) {
            val rBase = gy * width
            for (gx in startX until (startX + grainScale).coerceAtMost(width)) {
                val idx = rBase + gx
                if (cellTypes[idx] == CELL_ICE) {
                    cellTypes[idx] = CELL_THIN_ICE
                }
            }
        }
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

    fun placeMacroIce(macroCol: Int, macroRow: Int, thin: Boolean = false) {
        val maxCols = width / grainScale
        val maxRows = height / grainScale
        if (macroCol !in 0 until maxCols || macroRow !in 0 until maxRows) return
        val startX = macroCol * grainScale
        val startY = macroRow * grainScale
        val t = if (thin) CELL_THIN_ICE else CELL_ICE
        for (gy in startY until startY + grainScale) {
            val rBase = gy * width
            for (gx in startX until startX + grainScale) {
                val idx = rBase + gx
                cellTypes[idx] = t
                shades[idx] = random.nextInt(-8, 9).toByte()
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
            TerrainPreset.VOLCANO_CALDERA -> {
                placeMacroStone(0, 8)
                placeMacroStone(1, 9)
                placeMacroStone(7, 9)
                placeMacroStone(8, 8)
                pourPresetSand(4, 12, 0)
            }
            TerrainPreset.MAGMA_CHAMBERS -> {
                placeMacroStone(4, 8)
                placeMacroStone(2, 8, cracked = true)
                placeMacroStone(6, 8, cracked = true)
                placeMacroStone(3, 11, cracked = true)
                placeMacroStone(5, 11, cracked = true)
            }
            TerrainPreset.OBSIDIAN_ARCH -> {
                placeMacroStone(2, 8)
                placeMacroStone(3, 7)
                placeMacroStone(4, 7)
                placeMacroStone(5, 7)
                placeMacroStone(6, 8)
                placeMacroStone(2, 11, cracked = true)
                placeMacroStone(6, 11, cracked = true)
            }
            TerrainPreset.ERUPTION_CORE -> {
                placeMacroStone(1, 8)
                placeMacroStone(7, 8)
                placeMacroStone(3, 8, cracked = true)
                placeMacroStone(5, 8, cracked = true)
                placeMacroIce(4, 8)
                placeMacroStone(4, 11, cracked = true)
            }
            TerrainPreset.FROST_WALL -> {
                placeMacroIce(2, 9)
                placeMacroIce(4, 9)
                placeMacroIce(6, 9)
            }
            TerrainPreset.GLACIER_STEPS -> {
                placeMacroIce(1, 8)
                placeMacroIce(3, 9)
                placeMacroIce(5, 10)
                placeMacroIce(7, 11)
                placeMacroStone(4, 12)
            }
            TerrainPreset.AURORA_SANCTUARY -> {
                placeMacroStone(1, 8)
                placeMacroStone(7, 8)
                placeMacroIce(3, 7)
                placeMacroIce(5, 7)
                placeMacroIce(2, 10)
                placeMacroStone(4, 10, cracked = true)
                placeMacroIce(6, 10)
            }
            TerrainPreset.ETERNAL_THRONE -> {
                placeMacroIce(2, 7)
                placeMacroIce(4, 6)
                placeMacroIce(6, 7)
                placeMacroStone(1, 10, cracked = true)
                placeMacroIce(4, 9)
                placeMacroStone(7, 10, cracked = true)
                placeMacroStone(3, 11)
                placeMacroStone(5, 11)
            }
        }
        repeat(45) {
            stepPhysicsOnce()
        }
        tickCount = 0
    }

    // --- Auto-Save & Resume Serialization (Classic Mode) ---

    private fun encodePiece(piece: TrayPiece?): String {
        if (piece == null) return "none"
        val cellsStr = piece.shape.cells.joinToString(",") { "${it.col}.${it.row}" }
        return "${piece.uid}:${piece.shape.id}:$cellsStr:${piece.colorIndex}:${piece.kind.name}"
    }

    private fun decodePiece(token: String): TrayPiece? {
        if (token == "none" || token.isBlank()) return null
        val parts = token.split(":")
        if (parts.size < 5) return null
        val uid = parts[0].toLongOrNull() ?: 1L
        val id = parts[1]
        val cells = parts[2].split(",").mapNotNull { pair ->
            val cr = pair.split(".")
            if (cr.size == 2) {
                val c = cr[0].toIntOrNull()
                val r = cr[1].toIntOrNull()
                if (c != null && r != null) CellOffset(c, r) else null
            } else null
        }
        if (cells.isEmpty()) return null
        val shape = BlockShape.normalize(id, cells)
        val colorIdx = parts[3].toIntOrNull() ?: 0
        val kind = runCatching { PieceKind.valueOf(parts[4]) }.getOrDefault(PieceKind.NORMAL)
        return TrayPiece(uid = uid, shape = shape, colorIndex = colorIdx, kind = kind)
    }

    fun serializeClassicState(): String {
        val packed = ByteArray(size * 4)
        System.arraycopy(cellTypes, 0, packed, 0, size)
        System.arraycopy(colorIndices, 0, packed, size, size)
        System.arraycopy(shades, 0, packed, size * 2, size)
        System.arraycopy(lavaFuel, 0, packed, size * 3, size)
        val b64 = Base64.getEncoder().encodeToString(packed)

        val piecesEncoded = listOf(
            encodePiece(trayPieces[0]),
            encodePiece(trayPieces[1]),
            encodePiece(trayPieces[2]),
            encodePiece(holdPiece)
        ).joinToString(";")

        return listOf(
            "SBV2",
            "$width,$height",
            theme.id,
            score.toString(),
            linesCleared.toString(),
            combo.toString(),
            maxCombo.toString(),
            movesUsed.toString(),
            feverCharge.toString(),
            feverRemainingSec.toString(),
            piecesEncoded,
            b64
        ).joinToString("|")
    }

    fun restoreClassicState(serialized: String): Boolean {
        return try {
            val fields = serialized.split("|")
            if (fields.size < 12 || fields[0] != "SBV2") return false
            val dims = fields[1].split(",")
            if (dims.size != 2 || dims[0].toIntOrNull() != width || dims[1].toIntOrNull() != height) {
                return false
            }

            val packed = Base64.getDecoder().decode(fields[11])
            if (packed.size != size * 4) return false

            resetBoardState()
            mode = GameMode.CLASSIC
            adventureLevel = null
            theme = GameTheme.fromId(fields[2])
            score = fields[3].toIntOrNull() ?: 0
            linesCleared = fields[4].toIntOrNull() ?: 0
            combo = fields[5].toIntOrNull() ?: 0
            maxCombo = fields[6].toIntOrNull() ?: 0
            movesUsed = fields[7].toIntOrNull() ?: 0
            feverCharge = (fields[8].toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
            feverRemainingSec = (fields[9].toFloatOrNull() ?: 0f).coerceAtLeast(0f)

            val pieceTokens = fields[10].split(";")
            if (pieceTokens.size >= 4) {
                trayPieces[0] = decodePiece(pieceTokens[0])
                trayPieces[1] = decodePiece(pieceTokens[1])
                trayPieces[2] = decodePiece(pieceTokens[2])
                holdPiece = decodePiece(pieceTokens[3])
            }
            if (trayPieces.all { it == null }) {
                refillTray()
            }

            System.arraycopy(packed, 0, cellTypes, 0, size)
            System.arraycopy(packed, size, colorIndices, 0, size)
            System.arraycopy(packed, size * 2, shades, 0, size)
            System.arraycopy(packed, size * 3, lavaFuel, 0, size)
            status = GameStatus.PLAYING
            true
        } catch (_: Exception) {
            false
        }
    }
}
