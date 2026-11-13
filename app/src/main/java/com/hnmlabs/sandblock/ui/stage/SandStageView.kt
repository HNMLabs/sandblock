package com.hnmlabs.sandblock.ui.stage

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.hnmlabs.sandblock.audio.AsmrSoundEngine
import com.hnmlabs.sandblock.engine.GameStatus
import com.hnmlabs.sandblock.engine.SandWorld
import com.hnmlabs.sandblock.engine.WorldEvent
import com.hnmlabs.sandblock.model.BoosterType
import com.hnmlabs.sandblock.model.ColorMath
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.TrayPiece
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class HudSnapshot(
    val score: Int,
    val linesCleared: Int,
    val combo: Int,
    val movesRemaining: Int?,
    val crackedRemaining: Int,
    val status: GameStatus,
    val wonStars: Int = 0
)

class SandStageView(
    context: Context,
    val world: SandWorld,
    private val soundEngine: AsmrSoundEngine,
    private val onHudUpdated: (HudSnapshot) -> Unit,
    private val onLiveCoinsEarned: (Int) -> Int,
    private val onTargetBoosterExecuted: (BoosterType) -> Unit
) : View(context), Choreographer.FrameCallback {

    private class Particle {
        var active = false
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var size = 4f
        var color = 0
        var life = 0f
        var maxLife = 1f
    }

    private class FloatingPopup(
        var text: String = "",
        var x: Float = 0f,
        var y: Float = 0f,
        var color: Int = 0xFFFFFFFF.toInt(),
        var life: Float = 0f,
        var maxLife: Float = 1f,
        var active: Boolean = false
    )

    private class ShockwaveRing(
        var x: Float = 0f,
        var y: Float = 0f,
        var maxRadius: Float = 100f,
        var color: Int = 0xFFFFB703.toInt(),
        var life: Float = 0f,
        var active: Boolean = false
    )

    private class LaserBeamFx(
        var y: Float = 0f,
        var life: Float = 0f,
        var active: Boolean = false
    )

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val pixelBuffer = IntArray(world.size)
    private val sandBitmap: Bitmap =
        Bitmap.createBitmap(world.width, world.height, Bitmap.Config.ARGB_8888)
    private val srcRect = Rect(0, 0, world.width, world.height)
    private val boardRect = RectF()
    private val boardFrameRect = RectF()
    private val trayCardRects = Array(3) { RectF() }
    private val tempRect = RectF()

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dangerDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val particles = Array(280) { Particle() }
    private val popups = Array(16) { FloatingPopup() }
    private val shockwaves = Array(6) { ShockwaveRing() }
    private val laserBeams = Array(4) { LaserBeamFx() }
    private val fxRandom = Random.Default

    var isGamePaused: Boolean = false
    var activeTargetBooster: BoosterType? = null
        set(value) {
            field = value
            invalidate()
        }

    private var running = false
    private var lastFrameNanos = 0L
    private var animPhase = 0f

    // Drag & Drop state
    private var draggingSlot = -1
    private var downX = 0f
    private var downY = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var isDraggingMoved = false
    private var downTimeMs = 0L

    // Cached HUD values to avoid redundant callbacks
    private var lastScore = -1
    private var lastClears = -1
    private var lastCombo = -1
    private var lastMovesRem: Int? = -2
    private var lastCracked = -1
    private var lastStatus = GameStatus.PLAYING

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    fun startLoop() {
        if (!running) {
            running = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopLoop() {
        if (running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    fun resetCachedHud() {
        lastScore = -1
        lastStatus = GameStatus.PLAYING
        draggingSlot = -1
        isDraggingMoved = false
        emitHudIfChanged(0)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w.toFloat(), h.toFloat())
    }

    private fun computeLayout(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val sidePad = 14f * density
        val topPad = 6f * density
        val bottomPad = 10f * density
        val trayHeight = (108f * density).coerceAtMost(h * 0.21f)
        val hintGap = 24f * density

        val availBoardH = (h - topPad - bottomPad - trayHeight - hintGap).coerceAtLeast(120f)
        val availBoardW = (w - sidePad * 2f).coerceAtLeast(100f)

        val aspect = world.width.toFloat() / world.height.toFloat()
        var bW = availBoardW
        var bH = bW / aspect
        if (bH > availBoardH) {
            bH = availBoardH
            bW = bH * aspect
        }

        val bLeft = (w - bW) * 0.5f
        val bTop = topPad + (availBoardH - bH) * 0.5f
        boardRect.set(bLeft, bTop, bLeft + bW, bTop + bH)

        val frameInset = 4f * density
        boardFrameRect.set(
            boardRect.left - frameInset,
            boardRect.top - frameInset,
            boardRect.right + frameInset,
            boardRect.bottom + frameInset
        )

        val trayTop = boardRect.bottom + hintGap
        val cardGap = 10f * density
        val totalTrayW = (w - sidePad * 2f).coerceAtMost(bW + 28f * density)
        val trayStartX = (w - totalTrayW) * 0.5f
        val cardW = (totalTrayW - cardGap * 2f) / 3f
        for (i in 0 until 3) {
            val left = trayStartX + i * (cardW + cardGap)
            trayCardRects[i].set(left, trayTop, left + cardW, trayTop + trayHeight)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
        }
        lastFrameNanos = frameTimeNanos
        animPhase += dt

        if (!isGamePaused) {
            world.updateFrame(physicsSteps = 3)
            processWorldEvents()
        }

        updateVisualEffects(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun processWorldEvents() {
        val events = world.drainEvents()
        var wonStars = 0
        for (ev in events) {
            when (ev) {
                is WorldEvent.PiecePlaced -> {
                    soundEngine.playSandDrop(ev.isRainbow)
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    spawnBurst(sx, sy, ev.colorArgb, count = 18, speed = 110f * density)
                }
                is WorldEvent.LineClearStarted -> {
                    soundEngine.playLineClear(ev.combo)
                    val awardedGold = onLiveCoinsEarned(ev.coinsAwarded)
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    spawnBurst(sx, sy, ev.colorArgb, count = 48, speed = 210f * density)
                    val label = if (ev.combo >= 2) {
                        "KOMBO x${ev.combo}! +${ev.pointsAwarded}  🪙+$awardedGold"
                    } else {
                        "+${ev.pointsAwarded}  🪙+$awardedGold"
                    }
                    spawnPopup(label, sx, sy, if (ev.combo >= 2) 0xFFFFEA00.toInt() else 0xFFFFFFFF.toInt())
                    if (ev.crackedBlocksBroken > 0) {
                        spawnPopup(
                            "TAŞ KIRILDI! x${ev.crackedBlocksBroken}",
                            sx,
                            sy - 26f * density,
                            0xFFFFB703.toInt()
                        )
                    }
                }
                is WorldEvent.BombExploded -> {
                    soundEngine.playBomb()
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    val maxR = (SandWorld.BOMB_RADIUS.toFloat() / world.width) * boardRect.width() * 1.25f
                    spawnShockwave(sx, sy, maxR, 0xFFFF6B35.toInt())
                    spawnBurst(sx, sy, 0xFFFF9F1C.toInt(), count = 64, speed = 280f * density)
                    spawnPopup("BOOM! +${ev.pointsAwarded}", sx, sy, 0xFFFF9F1C.toInt())
                }
                is WorldEvent.LaserFired -> {
                    soundEngine.playLaser()
                    val sy = gridToScreenY(ev.centerY)
                    val beam = laserBeams.firstOrNull { !it.active } ?: laserBeams[0]
                    beam.y = sy
                    beam.life = 0.34f
                    beam.active = true
                    for (step in 1..5) {
                        val bx = boardRect.left + boardRect.width() * (step / 6f)
                        spawnBurst(bx, sy, 0xFF00F5D4.toInt(), count = 14, speed = 230f * density)
                    }
                    spawnPopup("⚡ LAZER! +${ev.pointsAwarded}", boardRect.centerX(), sy, 0xFF00F5D4.toInt())
                }
                is WorldEvent.ColorCleaned -> {
                    soundEngine.playMagic()
                    val cx = boardRect.centerX()
                    val cy = boardRect.centerY()
                    spawnShockwave(cx, cy, boardRect.width() * 0.55f, ev.colorArgb)
                    spawnBurst(cx, cy, ev.colorArgb, count = 60, speed = 250f * density)
                    spawnPopup("RENK SİLİNDİ! +${ev.pointsAwarded}", cx, cy, ev.colorArgb)
                }
                is WorldEvent.SandStormCleared -> {
                    soundEngine.playMagic()
                    val sy = gridToScreenY(ev.topY)
                    spawnShockwave(boardRect.centerX(), sy, boardRect.width() * 0.6f, 0xFFFFD166.toInt())
                    spawnBurst(boardRect.centerX(), sy, 0xFFFFD166.toInt(), count = 70, speed = 260f * density)
                    spawnPopup("🌪️ KUM FIRTINASI! +${ev.pointsAwarded}", boardRect.centerX(), sy, 0xFFFFD166.toInt())
                }
                is WorldEvent.LevelWon -> {
                    wonStars = ev.stars
                    soundEngine.playWin()
                    spawnBurst(
                        boardRect.centerX(),
                        boardRect.centerY(),
                        0xFFFFD166.toInt(),
                        count = 85,
                        speed = 300f * density
                    )
                }
                WorldEvent.GameLost -> {
                    soundEngine.playGameOver()
                }
            }
        }
        emitHudIfChanged(wonStars)
    }

    private fun emitHudIfChanged(wonStars: Int) {
        val cracked = if (world.adventureLevel?.requireClearCrackedStones == true) {
            world.countRemainingCrackedStones()
        } else {
            0
        }
        val movesRem = world.movesRemaining
        if (world.score != lastScore ||
            world.linesCleared != lastClears ||
            world.combo != lastCombo ||
            movesRem != lastMovesRem ||
            cracked != lastCracked ||
            world.status != lastStatus ||
            wonStars > 0
        ) {
            lastScore = world.score
            lastClears = world.linesCleared
            lastCombo = world.combo
            lastMovesRem = movesRem
            lastCracked = cracked
            lastStatus = world.status
            onHudUpdated(
                HudSnapshot(
                    score = world.score,
                    linesCleared = world.linesCleared,
                    combo = world.combo,
                    movesRemaining = movesRem,
                    crackedRemaining = cracked,
                    status = world.status,
                    wonStars = wonStars
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boardRect.width() <= 0f) return

        val theme = world.theme

        // 1. Draw Board Frame & Well Shadow
        fillPaint.color = theme.boardBg
        val cornerR = 14f * density
        canvas.drawRoundRect(boardFrameRect, cornerR, cornerR, fillPaint)

        // 2. Render SandWorld pixel buffer into Bitmap & draw crisp onto boardRect
        world.renderPixels(pixelBuffer)
        sandBitmap.setPixels(pixelBuffer, 0, world.width, 0, 0, world.width, world.height)

        val saveBoard = canvas.save()
        canvas.clipRect(boardRect)
        canvas.drawBitmap(sandBitmap, srcRect, boardRect, bitmapPaint)

        // 3. Draw Danger Line (Limit Line) with proximity warning pulse
        val dangerY = gridToScreenY(world.dangerRow.toFloat())
        val highestRow = world.highestSandRow()
        val isDangerNear = highestRow < world.dangerRow + 16
        val pulseAlpha = if (isDangerNear) {
            (170 + (sin(animPhase * 9f) * 80f).toInt()).coerceIn(90, 255)
        } else {
            135
        }
        if (isDangerNear) {
            fillPaint.color = ColorMath.withAlpha(theme.dangerLineColor, 34)
            canvas.drawRect(boardRect.left, boardRect.top, boardRect.right, dangerY, fillPaint)
        }
        dangerDashPaint.color = ColorMath.withAlpha(theme.dangerLineColor, pulseAlpha)
        canvas.drawLine(boardRect.left, dangerY, boardRect.right, dangerY, dangerDashPaint)

        textPaint.textSize = 9.5f * density
        textPaint.color = ColorMath.withAlpha(theme.dangerLineColor, pulseAlpha)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SINIR", boardRect.right - 6f * density, dangerY - 4f * density, textPaint)
        textPaint.textAlign = Paint.Align.CENTER

        // 4. Draw Ghost Preview on Board if dragging a piece
        if (draggingSlot in 0..2 && isDraggingMoved) {
            val piece = world.trayPieces[draggingSlot]
            if (piece != null) {
                drawDragBoardPreview(canvas, piece)
            }
        }

        // 5. Target Booster highlight overlay on board
        activeTargetBooster?.let { booster ->
            val glowAlpha = (45 + (sin(animPhase * 6f) * 25f).toInt()).coerceIn(20, 80)
            strokePaint.strokeWidth = 3f * density
            strokePaint.color = ColorMath.withAlpha(theme.accentColor, 200)
            fillPaint.color = ColorMath.withAlpha(theme.accentColor, glowAlpha / 2)
            canvas.drawRect(boardRect, fillPaint)
            textPaint.textSize = 13f * density
            textPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText(booster.hintTr, boardRect.centerX(), boardRect.top + 32f * density, textPaint)
        }

        canvas.restoreToCount(saveBoard)

        // Outer neon/gold border around board
        strokePaint.strokeWidth = 2.6f * density
        strokePaint.color = if (activeTargetBooster != null) {
            theme.accentColor
        } else {
            ColorMath.withAlpha(theme.boardBorder, 215)
        }
        canvas.drawRoundRect(boardFrameRect, cornerR, cornerR, strokePaint)

        // 6. Hint text between board & tray
        textPaint.textSize = 11f * density
        textPaint.color = 0xAAFFFFFF.toInt()
        val hintY = boardFrameRect.bottom + 15f * density
        val hintStr = activeTargetBooster?.hintTr ?: "Bloğu döndürmek için dokun • Alana sürükle & bırak"
        canvas.drawText(hintStr, width * 0.5f, hintY, textPaint)

        // 7. Draw 3-Piece Tray
        drawTray(canvas)

        // 8. Draw Floating Dragged Piece under/above finger
        if (draggingSlot in 0..2 && isDraggingMoved) {
            val piece = world.trayPieces[draggingSlot]
            if (piece != null) {
                val grainPx = boardRect.width() / world.width.toFloat()
                val cellPx = grainPx * world.grainScale
                val liftY = touchY - 76f * density - (piece.shape.rows * cellPx * 0.5f)
                val leftX = touchX - (piece.shape.cols * cellPx * 0.5f)
                drawPieceCells(canvas, piece, leftX, liftY, cellPx, alpha = 240)
            }
        }

        // 9. Shockwaves, Particles & Popups
        drawEffects(canvas)
    }

    private fun drawDragBoardPreview(canvas: Canvas, piece: TrayPiece) {
        val grainPx = boardRect.width() / world.width.toFloat()
        val cellPx = grainPx * world.grainScale
        val liftCenterY = touchY - 76f * density
        val liftLeftX = touchX - (piece.shape.cols * cellPx * 0.5f)
        val liftTopY = liftCenterY - (piece.shape.rows * cellPx * 0.5f)

        if (liftCenterY > boardRect.bottom + 24f * density) return

        val rawGridX = ((liftLeftX - boardRect.left) / grainPx).toInt()
        val rawGridY = ((liftTopY - boardRect.top) / grainPx).toInt()
        val valid = world.findValidPlacement(piece, rawGridX, rawGridY)

        if (valid != null) {
            val (gx, gy) = valid
            val snapLeft = boardRect.left + gx * grainPx
            val snapTop = boardRect.top + gy * grainPx
            val pieceScreenW = piece.shape.cols * cellPx

            // Vertical guide beam downward
            fillPaint.color = ColorMath.withAlpha(world.theme.accentColor, 22)
            canvas.drawRect(
                snapLeft,
                snapTop,
                snapLeft + pieceScreenW,
                boardRect.bottom,
                fillPaint
            )

            if (piece.kind == PieceKind.BOMB) {
                // Show circular blast radius preview!
                val cx = snapLeft + pieceScreenW * 0.5f
                val cy = snapTop + (piece.shape.rows * cellPx) * 0.5f
                val blastR = SandWorld.BOMB_RADIUS * grainPx
                fillPaint.color = 0x33FF595E
                canvas.drawCircle(cx, cy, blastR, fillPaint)
                strokePaint.strokeWidth = 2f * density
                strokePaint.color = 0xCCFF595E.toInt()
                canvas.drawCircle(cx, cy, blastR, strokePaint)
            }

            // Ghost cells
            for (cell in piece.shape.cells) {
                val cl = snapLeft + cell.col * cellPx
                val ct = snapTop + cell.row * cellPx
                tempRect.set(cl + 1f, ct + 1f, cl + cellPx - 1f, ct + cellPx - 1f)
                fillPaint.color = 0x44FFFFFF
                canvas.drawRoundRect(tempRect, 3f * density, 3f * density, fillPaint)
                strokePaint.strokeWidth = 1.6f * density
                strokePaint.color = 0xCCFFFFFF.toInt()
                canvas.drawRoundRect(tempRect, 3f * density, 3f * density, strokePaint)
            }
        } else {
            // Invalid red outline
            for (cell in piece.shape.cells) {
                val cl = liftLeftX + cell.col * cellPx
                val ct = liftTopY + cell.row * cellPx
                tempRect.set(cl, ct, cl + cellPx, ct + cellPx)
                fillPaint.color = 0x44FF3B30
                canvas.drawRoundRect(tempRect, 3f * density, 3f * density, fillPaint)
            }
        }
    }

    private fun drawTray(canvas: Canvas) {
        val theme = world.theme
        for (i in 0 until 3) {
            val rect = trayCardRects[i]
            fillPaint.color = ColorMath.withAlpha(theme.surfaceCard, 235)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, fillPaint)

            strokePaint.strokeWidth = 1.5f * density
            strokePaint.color = ColorMath.withAlpha(theme.boardBorder, 95)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, strokePaint)

            val piece = world.trayPieces[i]
            if (piece != null && !(draggingSlot == i && isDraggingMoved)) {
                val pad = 12f * density
                val maxCellW = (rect.width() - pad * 2f) / piece.shape.cols.coerceAtLeast(1)
                val maxCellH = (rect.height() - pad * 2f) / piece.shape.rows.coerceAtLeast(1)
                val cellPx = min(maxCellW, maxCellH).coerceAtMost(24f * density)

                val totalW = piece.shape.cols * cellPx
                val totalH = piece.shape.rows * cellPx
                val startX = rect.centerX() - totalW * 0.5f
                val startY = rect.centerY() - totalH * 0.5f

                drawPieceCells(canvas, piece, startX, startY, cellPx, alpha = 255)

                // Small badge for special pieces
                if (piece.kind == PieceKind.RAINBOW) {
                    textPaint.textSize = 10f * density
                    textPaint.color = 0xFFFFEA00.toInt()
                    canvas.drawText("🌈 JOKER", rect.centerX(), rect.bottom - 5f * density, textPaint)
                } else if (piece.kind == PieceKind.BOMB) {
                    textPaint.textSize = 10f * density
                    textPaint.color = 0xFFFF6B6B.toInt()
                    canvas.drawText("💣 TNT", rect.centerX(), rect.bottom - 5f * density, textPaint)
                }
            }
        }
    }

    private fun drawPieceCells(
        canvas: Canvas,
        piece: TrayPiece,
        startX: Float,
        startY: Float,
        cellPx: Float,
        alpha: Int
    ) {
        val palette = world.theme.sandPalette
        val corner = (cellPx * 0.18f).coerceAtLeast(2f)

        for (cell in piece.shape.cells) {
            val cl = startX + cell.col * cellPx
            val ct = startY + cell.row * cellPx
            tempRect.set(cl + 0.8f, ct + 0.8f, cl + cellPx - 0.8f, ct + cellPx - 0.8f)

            val baseColor = when (piece.kind) {
                PieceKind.NORMAL -> palette[piece.colorIndex % palette.size]
                PieceKind.RAINBOW -> ColorMath.rainbowColor(
                    (cell.col * 7 + cell.row * 11),
                    (animPhase * 40f).toInt()
                )
                PieceKind.BOMB -> 0xFFD62828.toInt()
            }

            fillPaint.shader = LinearGradient(
                cl,
                ct,
                cl + cellPx,
                ct + cellPx,
                ColorMath.withAlpha(ColorMath.shade(baseColor, 26), alpha),
                ColorMath.withAlpha(ColorMath.shade(baseColor, -24), alpha),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(tempRect, corner, corner, fillPaint)
            fillPaint.shader = null

            // Granular inner speckles so blocks visibly look made of compressed sand!
            fillPaint.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), (alpha * 0.28f).toInt())
            canvas.drawCircle(cl + cellPx * 0.30f, ct + cellPx * 0.32f, cellPx * 0.07f, fillPaint)
            canvas.drawCircle(cl + cellPx * 0.68f, ct + cellPx * 0.58f, cellPx * 0.06f, fillPaint)
            fillPaint.color = ColorMath.withAlpha(0xFF000000.toInt(), (alpha * 0.22f).toInt())
            canvas.drawCircle(cl + cellPx * 0.55f, ct + cellPx * 0.28f, cellPx * 0.06f, fillPaint)
            canvas.drawCircle(cl + cellPx * 0.36f, ct + cellPx * 0.72f, cellPx * 0.07f, fillPaint)
        }

        if (piece.kind == PieceKind.BOMB) {
            val cx = startX + (piece.shape.cols * cellPx) * 0.5f
            val cy = startY + (piece.shape.rows * cellPx) * 0.55f
            textPaint.textSize = cellPx * 0.68f
            textPaint.color = ColorMath.withAlpha(0xFFFFEA00.toInt(), alpha)
            canvas.drawText("💣", cx, cy + cellPx * 0.2f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGamePaused || world.status != GameStatus.PLAYING) return false

        val x = event.x
        val y = event.y

        // Handle Active Target Booster tap on board!
        val currentBooster = activeTargetBooster
        if (currentBooster != null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (boardRect.contains(x, y)) {
                    val gx = ((x - boardRect.left) / boardRect.width() * world.width).toInt()
                        .coerceIn(0, world.width - 1)
                    val gy = ((y - boardRect.top) / boardRect.height() * world.height).toInt()
                        .coerceIn(0, world.height - 1)

                    val success = when (currentBooster) {
                        BoosterType.BOMB -> {
                            world.detonateBomb(gx, gy)
                            true
                        }
                        BoosterType.ROW_LASER -> {
                            world.fireHorizontalLaser(gy)
                            true
                        }
                        BoosterType.COLOR_CLEANER -> {
                            world.clearAllSandOfColorAt(gx, gy)
                        }
                        BoosterType.RAINBOW_UPGRADE,
                        BoosterType.REROLL_TRAY -> false
                    }
                    if (success) {
                        activeTargetBooster = null
                        onTargetBoosterExecuted(currentBooster)
                    } else {
                        spawnPopup("Burada renkli kum yok!", x, y - 20f * density, 0xFFFF6B6B.toInt())
                    }
                    return true
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slot = (0..2).firstOrNull { idx ->
                    trayCardRects[idx].contains(x, y) && world.trayPieces[idx] != null
                } ?: -1
                if (slot in 0..2 && world.trayPieces[slot] != null) {
                    draggingSlot = slot
                    downX = x
                    downY = y
                    touchX = x
                    touchY = y
                    isDraggingMoved = false
                    downTimeMs = event.eventTime
                    soundEngine.playTap()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingSlot in 0..2) {
                    touchX = x
                    touchY = y
                    if (!isDraggingMoved && hypot(x - downX, y - downY) > touchSlop * 0.75f) {
                        isDraggingMoved = true
                    }
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (draggingSlot in 0..2) {
                    val slot = draggingSlot
                    val piece = world.trayPieces[slot]
                    val elapsed = event.eventTime - downTimeMs

                    if (!isDraggingMoved && elapsed < 320L) {
                        // Tap to rotate!
                        if (world.rotateTrayPiece(slot)) {
                            soundEngine.playTap()
                        }
                    } else if (piece != null && isDraggingMoved) {
                        val grainPx = boardRect.width() / world.width.toFloat()
                        val cellPx = grainPx * world.grainScale
                        val liftCenterY = y - 76f * density
                        val liftLeftX = x - (piece.shape.cols * cellPx * 0.5f)
                        val liftTopY = liftCenterY - (piece.shape.rows * cellPx * 0.5f)

                        if (liftCenterY <= boardRect.bottom + 20f * density &&
                            x in (boardRect.left - 24f * density)..(boardRect.right + 24f * density)
                        ) {
                            val rawGridX = ((liftLeftX - boardRect.left) / grainPx).toInt()
                            val rawGridY = ((liftTopY - boardRect.top) / grainPx).toInt()
                            world.placeTrayPiece(slot, rawGridX, rawGridY)
                        }
                    }

                    draggingSlot = -1
                    isDraggingMoved = false
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                draggingSlot = -1
                isDraggingMoved = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun gridToScreenX(gx: Float): Float =
        boardRect.left + (gx / world.width.toFloat()) * boardRect.width()

    private fun gridToScreenY(gy: Float): Float =
        boardRect.top + (gy / world.height.toFloat()) * boardRect.height()

    private fun spawnBurst(cx: Float, cy: Float, baseColor: Int, count: Int, speed: Float) {
        var spawned = 0
        for (p in particles) {
            if (!p.active) {
                val angle = fxRandom.nextFloat() * 6.28318f
                val spd = speed * (0.25f + fxRandom.nextFloat() * 0.85f)
                p.active = true
                p.x = cx + fxRandom.nextFloat() * 14f - 7f
                p.y = cy + fxRandom.nextFloat() * 14f - 7f
                p.vx = cos(angle) * spd
                p.vy = sin(angle) * spd - 55f * density
                p.size = (2.2f + fxRandom.nextFloat() * 3.4f) * density
                p.color = ColorMath.shade(baseColor, fxRandom.nextInt(-25, 35))
                p.maxLife = 0.42f + fxRandom.nextFloat() * 0.38f
                p.life = p.maxLife
                spawned++
                if (spawned >= count) break
            }
        }
    }

    private fun spawnPopup(text: String, x: Float, y: Float, color: Int) {
        val slot = popups.firstOrNull { !it.active } ?: popups[0]
        slot.text = text
        slot.x = x.coerceIn(boardRect.left + 56f * density, boardRect.right - 56f * density)
        slot.y = y.coerceIn(boardRect.top + 36f * density, boardRect.bottom - 24f * density)
        slot.color = color
        slot.maxLife = 1.05f
        slot.life = slot.maxLife
        slot.active = true
    }

    private fun spawnShockwave(x: Float, y: Float, maxR: Float, color: Int) {
        val ring = shockwaves.firstOrNull { !it.active } ?: shockwaves[0]
        ring.x = x
        ring.y = y
        ring.maxRadius = maxR
        ring.color = color
        ring.life = 0.38f
        ring.active = true
    }

    private fun updateVisualEffects(dt: Float) {
        val gravity = 460f * density
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
            } else {
                p.vy += gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
            }
        }

        for (pop in popups) {
            if (!pop.active) continue
            pop.life -= dt
            if (pop.life <= 0f) {
                pop.active = false
            } else {
                pop.y -= 36f * density * dt
            }
        }

        for (ring in shockwaves) {
            if (!ring.active) continue
            ring.life -= dt
            if (ring.life <= 0f) ring.active = false
        }

        for (beam in laserBeams) {
            if (!beam.active) continue
            beam.life -= dt
            if (beam.life <= 0f) beam.active = false
        }
    }

    private fun drawEffects(canvas: Canvas) {
        for (beam in laserBeams) {
            if (!beam.active) continue
            val ratio = (beam.life / 0.34f).coerceIn(0f, 1f)
            val outerHalf = 14f * density * ratio
            val innerHalf = 4.5f * density * ratio
            fillPaint.color = ColorMath.withAlpha(0xFF00F5D4.toInt(), (ratio * 150f).toInt())
            canvas.drawRect(boardRect.left, beam.y - outerHalf, boardRect.right, beam.y + outerHalf, fillPaint)
            fillPaint.color = ColorMath.withAlpha(0xFFFFFFFF.toInt(), (ratio * 245f).toInt())
            canvas.drawRect(boardRect.left, beam.y - innerHalf, boardRect.right, beam.y + innerHalf, fillPaint)
        }

        for (ring in shockwaves) {
            if (!ring.active) continue
            val progress = 1f - (ring.life / 0.38f).coerceIn(0f, 1f)
            val r = ring.maxRadius * progress
            val a = ((1f - progress) * 210f).toInt().coerceIn(0, 255)
            strokePaint.strokeWidth = (5f * (1f - progress) + 1.5f) * density
            strokePaint.color = ColorMath.withAlpha(ring.color, a)
            canvas.drawCircle(ring.x, ring.y, r, strokePaint)
        }

        for (p in particles) {
            if (!p.active) continue
            val alpha = ((p.life / p.maxLife) * 255f).toInt().coerceIn(0, 255)
            fillPaint.color = ColorMath.withAlpha(p.color, alpha)
            val half = p.size * 0.5f
            canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, fillPaint)
        }

        for (pop in popups) {
            if (!pop.active) continue
            val ratio = (pop.life / pop.maxLife).coerceIn(0f, 1f)
            val alpha = (ratio * 255f).toInt().coerceIn(0, 255)
            textPaint.textSize = 15.5f * density
            // Text shadow
            textPaint.color = ColorMath.withAlpha(0xFF000000.toInt(), (alpha * 0.75f).toInt())
            canvas.drawText(pop.text, pop.x + 1.5f * density, pop.y + 1.5f * density, textPaint)
            textPaint.color = ColorMath.withAlpha(pop.color, alpha)
            canvas.drawText(pop.text, pop.x, pop.y, textPaint)
        }
    }
}
