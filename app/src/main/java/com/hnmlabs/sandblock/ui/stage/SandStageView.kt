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
import com.hnmlabs.sandblock.model.GameMode
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.TrayPiece
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
    val timeRemainingDeciSec: Int = 900,
    val feverStep: Int = 0, // 0..20
    val isFeverActive: Boolean = false,
    val status: GameStatus,
    val wonStars: Int = 0
)

class SandStageView(
    context: Context,
    val world: SandWorld,
    private val soundEngine: AsmrSoundEngine,
    private val onHudUpdated: (HudSnapshot) -> Unit,
    private val onLiveCoinsEarned: (Int) -> Int,
    private val onTargetBoosterExecuted: (BoosterType) -> Unit,
    private val onPiecePlacedStat: () -> Unit = {},
    private val onLineClearStat: (Int, Int) -> Unit = { _, _ -> },
    private val onAutoSaveCheckpoint: () -> Unit = {}
) : View(context), Choreographer.FrameCallback {

    companion object {
        private const val HOLD_DRAG_SLOT = 3
    }

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
        var isRisingEmber = false
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

    // Drag & Drop mode rects: Hold + 3 Tray slots
    private val holdCardRect = RectF()
    private val trayCardRects = Array(3) { RectF() }

    // Sandtrix mode control rects: Hold, Left, Rotate, HardDrop, Right, NextPreview
    private val stxHoldRect = RectF()
    private val stxLeftRect = RectF()
    private val stxRotRect = RectF()
    private val stxDropRect = RectF()
    private val stxRightRect = RectF()
    private val stxNextRect = RectF()

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

    private val particles = Array(320) { Particle() }
    private val popups = Array(18) { FloatingPopup() }
    private val shockwaves = Array(8) { ShockwaveRing() }
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
    private var shakeTrauma = 0f
    private var emberSpawnTimer = 0f
    private var dirtyForAutoSave = false

    // Drag & Drop state (-1 = none, 0..2 = tray, 3 = hold)
    private var draggingSlot = -1
    private var downX = 0f
    private var downY = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var isDraggingMoved = false
    private var downTimeMs = 0L

    // Sandtrix board swipe gesture state
    private var stxBoardTracking = false
    private var stxLastStepX = 0f
    private var stxSwipeConsumedDrop = false

    // Cached HUD values
    private var lastScore = -1
    private var lastClears = -1
    private var lastCombo = -1
    private var lastMovesRem: Int? = -2
    private var lastCracked = -1
    private var lastTimeDeci = -1
    private var lastFeverStep = -1
    private var lastFeverActive = false
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
        lastTimeDeci = -1
        lastFeverStep = -1
        lastStatus = GameStatus.PLAYING
        draggingSlot = -1
        isDraggingMoved = false
        stxBoardTracking = false
        computeLayout(width.toFloat(), height.toFloat())
        emitHudIfChanged(0)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout(w.toFloat(), h.toFloat())
    }

    private fun computeLayout(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val sidePad = 12f * density
        val topPad = 5f * density
        val bottomPad = 8f * density
        val trayHeight = (104f * density).coerceAtMost(h * 0.205f)
        val hintGap = 22f * density

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
        val totalTrayW = (w - sidePad * 2f).coerceAtMost(bW + 36f * density)
        val trayStartX = (w - totalTrayW) * 0.5f

        // 4-column Drag & Drop layout: [Hold (0.86)] + 3x [Tray (1.0)]
        val cardGap = 7f * density
        val unitW = (totalTrayW - cardGap * 3f) / 3.86f
        val holdW = unitW * 0.86f
        holdCardRect.set(trayStartX, trayTop, trayStartX + holdW, trayTop + trayHeight)
        var curLeft = holdCardRect.right + cardGap
        for (i in 0 until 3) {
            trayCardRects[i].set(curLeft, trayTop, curLeft + unitW, trayTop + trayHeight)
            curLeft += unitW + cardGap
        }

        // Sandtrix bottom deck: [SAKLA] [◀] [↻] [▼] [▶] [SIRADAKİ]
        val stxGap = 5f * density
        val sideBoxW = totalTrayW * 0.20f
        val midTotalW = totalTrayW - sideBoxW * 2f - stxGap * 5f
        val btnW = midTotalW / 4f
        var sx = trayStartX
        stxHoldRect.set(sx, trayTop, sx + sideBoxW, trayTop + trayHeight)
        sx += sideBoxW + stxGap
        stxLeftRect.set(sx, trayTop, sx + btnW, trayTop + trayHeight)
        sx += btnW + stxGap
        stxRotRect.set(sx, trayTop, sx + btnW, trayTop + trayHeight)
        sx += btnW + stxGap
        stxDropRect.set(sx, trayTop, sx + btnW, trayTop + trayHeight)
        sx += btnW + stxGap
        stxRightRect.set(sx, trayTop, sx + btnW, trayTop + trayHeight)
        sx += btnW + stxGap
        stxNextRect.set(sx, trayTop, sx + sideBoxW, trayTop + trayHeight)
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
            // Faster, snappier 4-step cellular sand flow per 60Hz frame!
            world.updateFrame(physicsSteps = 4, dt = dt)
            processWorldEvents()

            if (dirtyForAutoSave && world.mode == GameMode.CLASSIC && world.isBoardQuiet && world.status == GameStatus.PLAYING) {
                dirtyForAutoSave = false
                onAutoSaveCheckpoint()
            }
        }

        updateVisualEffects(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun triggerShake(amount: Float) {
        shakeTrauma = (shakeTrauma + amount).coerceIn(0f, 1f)
    }

    private fun processWorldEvents() {
        val events = world.drainEvents()
        var wonStars = 0
        for (ev in events) {
            when (ev) {
                is WorldEvent.PiecePlaced -> {
                    soundEngine.playSandDrop(isRainbow = ev.isRainbow, isLava = ev.isLava)
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    spawnBurst(sx, sy, ev.colorArgb, count = if (ev.isLava) 26 else 18, speed = 115f * density)
                    if (ev.isLava) {
                        spawnPopup("🌋 KOR LAV!", sx, sy - 14f * density, 0xFFFF6D00.toInt())
                    }
                    onPiecePlacedStat()
                    dirtyForAutoSave = true
                }
                is WorldEvent.LineClearStarted -> {
                    soundEngine.playLineClear(ev.combo)
                    val awardedGold = onLiveCoinsEarned(ev.coinsAwarded)
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    spawnBurst(sx, sy, ev.colorArgb, count = 52, speed = 220f * density)
                    if (ev.combo >= 2) {
                        triggerShake((0.28f + ev.combo * 0.12f).coerceAtMost(0.85f))
                    } else {
                        triggerShake(0.14f)
                    }
                    val feverTag = if (ev.feverBonusActive) " 🔥2X" else ""
                    val label = if (ev.combo >= 2) {
                        "KOMBO x${ev.combo}$feverTag! +${ev.pointsAwarded}  🪙+$awardedGold"
                    } else {
                        "+${ev.pointsAwarded}$feverTag  🪙+$awardedGold"
                    }
                    spawnPopup(label, sx, sy, if (ev.combo >= 2 || ev.feverBonusActive) 0xFFFFEA00.toInt() else 0xFFFFFFFF.toInt())
                    if (ev.crackedBlocksBroken > 0) {
                        spawnPopup(
                            "ENGEL KIRILDI! x${ev.crackedBlocksBroken}",
                            sx,
                            sy - 26f * density,
                            0xFF72EFDD.toInt()
                        )
                    }
                    onLineClearStat(ev.combo, ev.crackedBlocksBroken)
                    dirtyForAutoSave = true
                }
                is WorldEvent.BombExploded -> {
                    soundEngine.playBomb()
                    triggerShake(0.68f)
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    val maxR = (SandWorld.BOMB_RADIUS.toFloat() / world.width) * boardRect.width() * 1.25f
                    spawnShockwave(sx, sy, maxR, 0xFFFF6B35.toInt())
                    spawnBurst(sx, sy, 0xFFFF9F1C.toInt(), count = 68, speed = 285f * density)
                    spawnPopup("BOOM! +${ev.pointsAwarded}", sx, sy, 0xFFFF9F1C.toInt())
                    dirtyForAutoSave = true
                }
                is WorldEvent.LaserFired -> {
                    soundEngine.playLaser()
                    triggerShake(0.50f)
                    val sy = gridToScreenY(ev.centerY)
                    val beam = laserBeams.firstOrNull { !it.active } ?: laserBeams[0]
                    beam.y = sy
                    beam.life = 0.36f
                    beam.active = true
                    for (step in 1..5) {
                        val bx = boardRect.left + boardRect.width() * (step / 6f)
                        spawnBurst(bx, sy, 0xFF00F5D4.toInt(), count = 14, speed = 230f * density)
                    }
                    spawnPopup("⚡ LAZER! +${ev.pointsAwarded}", boardRect.centerX(), sy, 0xFF00F5D4.toInt())
                    dirtyForAutoSave = true
                }
                is WorldEvent.ColorCleaned -> {
                    soundEngine.playMagic()
                    triggerShake(0.35f)
                    val cx = boardRect.centerX()
                    val cy = boardRect.centerY()
                    spawnShockwave(cx, cy, boardRect.width() * 0.55f, ev.colorArgb)
                    spawnBurst(cx, cy, ev.colorArgb, count = 60, speed = 250f * density)
                    spawnPopup("RENK SİLİNDİ! +${ev.pointsAwarded}", cx, cy, ev.colorArgb)
                    dirtyForAutoSave = true
                }
                is WorldEvent.SandStormCleared -> {
                    soundEngine.playMagic()
                    triggerShake(0.42f)
                    val sy = gridToScreenY(ev.topY)
                    spawnShockwave(boardRect.centerX(), sy, boardRect.width() * 0.6f, 0xFFFFD166.toInt())
                    spawnBurst(boardRect.centerX(), sy, 0xFFFFD166.toInt(), count = 70, speed = 260f * density)
                    spawnPopup("🌪️ KUM FIRTINASI! +${ev.pointsAwarded}", boardRect.centerX(), sy, 0xFFFFD166.toInt())
                    dirtyForAutoSave = true
                }
                is WorldEvent.LavaMelted -> {
                    soundEngine.playLava()
                    val sx = gridToScreenX(ev.centerX)
                    val sy = gridToScreenY(ev.centerY)
                    spawnBurst(sx, sy, 0xFFFF5400.toInt(), count = 22, speed = 155f * density)
                    spawnPopup("🌋 ERİDİ +${ev.pointsAwarded}", sx, sy, 0xFFFF9E00.toInt())
                }
                WorldEvent.FeverStarted -> {
                    soundEngine.playFever()
                    triggerShake(0.55f)
                    val cx = boardRect.centerX()
                    val cy = boardRect.centerY()
                    spawnShockwave(cx, cy, boardRect.width() * 0.7f, 0xFFFF6D00.toInt())
                    spawnBurst(cx, cy, 0xFFFFB703.toInt(), count = 75, speed = 270f * density)
                    spawnPopup("🔥 KOMBO ATEŞİ! 10sn 2X PUAN & ALTIN!", cx, cy - 20f * density, 0xFFFFEA00.toInt())
                }
                is WorldEvent.TimeBonusAdded -> {
                    spawnPopup(
                        "⏱️ +${ev.bonusSeconds.toInt()}sn",
                        boardRect.right - 64f * density,
                        boardRect.top + 42f * density,
                        0xFF00F5D4.toInt()
                    )
                }
                WorldEvent.Revived -> {
                    soundEngine.playMagic()
                    triggerShake(0.45f)
                    val cx = boardRect.centerX()
                    val cy = boardRect.top + boardRect.height() * 0.3f
                    spawnShockwave(cx, cy, boardRect.width() * 0.65f, 0xFF06D6A0.toInt())
                    spawnBurst(cx, cy, 0xFF06D6A0.toInt(), count = 65, speed = 250f * density)
                    spawnPopup("💖 İKİNCİ ŞANS! DEVAM!", cx, cy, 0xFF72EFDD.toInt())
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
                    triggerShake(0.45f)
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
        val timeDeci = if (world.mode == GameMode.TIME_RUSH) {
            (world.timeRemainingSec * 10f).toInt()
        } else {
            900
        }
        val feverRatio = if (world.isFeverActive) {
            (world.feverRemainingSec / SandWorld.FEVER_DURATION_SEC).coerceIn(0f, 1f)
        } else {
            world.feverCharge.coerceIn(0f, 1f)
        }
        val feverStep = (feverRatio * 20f).toInt().coerceIn(0, 20)
        val feverAct = world.isFeverActive

        if (world.score != lastScore ||
            world.linesCleared != lastClears ||
            world.combo != lastCombo ||
            movesRem != lastMovesRem ||
            cracked != lastCracked ||
            timeDeci != lastTimeDeci ||
            feverStep != lastFeverStep ||
            feverAct != lastFeverActive ||
            world.status != lastStatus ||
            wonStars > 0
        ) {
            lastScore = world.score
            lastClears = world.linesCleared
            lastCombo = world.combo
            lastMovesRem = movesRem
            lastCracked = cracked
            lastTimeDeci = timeDeci
            lastFeverStep = feverStep
            lastFeverActive = feverAct
            lastStatus = world.status
            onHudUpdated(
                HudSnapshot(
                    score = world.score,
                    linesCleared = world.linesCleared,
                    combo = world.combo,
                    movesRemaining = movesRem,
                    crackedRemaining = cracked,
                    timeRemainingDeciSec = timeDeci,
                    feverStep = feverStep,
                    isFeverActive = feverAct,
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
        val cornerR = 14f * density

        val shakeSave = canvas.save()
        if (shakeTrauma > 0.01f) {
            val mag = shakeTrauma * shakeTrauma * 9f * density
            val sx = (fxRandom.nextFloat() * 2f - 1f) * mag
            val sy = (fxRandom.nextFloat() * 2f - 1f) * mag
            canvas.translate(sx, sy)
        }

        // 1. Board Background
        fillPaint.color = theme.boardBg
        canvas.drawRoundRect(boardFrameRect, cornerR, cornerR, fillPaint)

        // 2. Render SandWorld pixel buffer into Bitmap
        world.renderPixels(pixelBuffer)
        sandBitmap.setPixels(pixelBuffer, 0, world.width, 0, 0, world.width, world.height)

        val saveBoard = canvas.save()
        canvas.clipRect(boardRect)
        canvas.drawBitmap(sandBitmap, srcRect, boardRect, bitmapPaint)

        // 3. Danger Line
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

        // 4. Mode-specific Board Overlays
        if (world.mode == GameMode.SANDTRIX) {
            drawSandtrixFallingPiece(canvas)
        } else if (draggingSlot != -1 && isDraggingMoved) {
            val piece = activeDraggedPiece()
            if (piece != null) {
                drawDragBoardPreview(canvas, piece)
            }
        }

        // 5. Fever Mode active banner inside top-left of board
        if (world.isFeverActive) {
            val pillW = 94f * density
            val pillH = 20f * density
            tempRect.set(
                boardRect.left + 6f * density,
                boardRect.top + 5f * density,
                boardRect.left + 6f * density + pillW,
                boardRect.top + 5f * density + pillH
            )
            fillPaint.color = 0xCCFF5400.toInt()
            canvas.drawRoundRect(tempRect, 10f * density, 10f * density, fillPaint)
            textPaint.textSize = 10f * density
            textPaint.color = 0xFFFFF3B0.toInt()
            canvas.drawText(
                "🔥 2X FEVER (${world.feverRemainingSec.toInt() + 1}s)",
                tempRect.centerX(),
                tempRect.centerY() + 3.5f * density,
                textPaint
            )
        }

        // 6. Target Booster highlight overlay on board
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

        // 7. Outer Border (glowing flame border during Fever!)
        if (world.isFeverActive) {
            val pulse = (185 + (sin(animPhase * 12f) * 65f).toInt()).coerceIn(120, 255)
            strokePaint.strokeWidth = 4.2f * density
            strokePaint.color = ColorMath.withAlpha(0xFFFF6D00.toInt(), pulse)
            canvas.drawRoundRect(boardFrameRect, cornerR, cornerR, strokePaint)
        } else {
            strokePaint.strokeWidth = 2.6f * density
            strokePaint.color = if (activeTargetBooster != null) {
                theme.accentColor
            } else {
                ColorMath.withAlpha(theme.boardBorder, 215)
            }
            canvas.drawRoundRect(boardFrameRect, cornerR, cornerR, strokePaint)
        }

        // 8. Effects (inside shake space)
        drawEffects(canvas)
        canvas.restoreToCount(shakeSave)

        // 9. Hint strip between board & tray/controls
        textPaint.textSize = 10.5f * density
        textPaint.color = 0xAAFFFFFF.toInt()
        val hintY = boardFrameRect.bottom + 14.5f * density
        val hintStr = activeTargetBooster?.hintTr ?: if (world.mode == GameMode.SANDTRIX) {
            "Alanda kaydır/dokun veya alttaki tuşlarla yönlendir & indir"
        } else {
            "Döndürmek için dokun • Sürükle & bırak • Sol yuvada SAKLA"
        }
        canvas.drawText(hintStr, width * 0.5f, hintY, textPaint)

        // 10. Bottom Deck (Drag & Drop Tray + Hold OR Sandtrix Controls)
        if (world.mode == GameMode.SANDTRIX) {
            drawSandtrixControls(canvas)
        } else {
            drawHoldAndTray(canvas)
        }

        // 11. Floating Dragged Piece above finger
        if (world.mode != GameMode.SANDTRIX && draggingSlot != -1 && isDraggingMoved) {
            val piece = activeDraggedPiece()
            if (piece != null) {
                val grainPx = boardRect.width() / world.width.toFloat()
                val cellPx = grainPx * world.grainScale
                val liftY = touchY - 76f * density - (piece.shape.rows * cellPx * 0.5f)
                val leftX = touchX - (piece.shape.cols * cellPx * 0.5f)
                drawPieceCells(canvas, piece, leftX, liftY, cellPx, alpha = 242)
            }
        }
    }

    private fun activeDraggedPiece(): TrayPiece? = when (draggingSlot) {
        in 0..2 -> world.trayPieces[draggingSlot]
        HOLD_DRAG_SLOT -> world.holdPiece
        else -> null
    }

    private fun drawSandtrixFallingPiece(canvas: Canvas) {
        val piece = world.fallingPiece ?: return
        val grainPx = boardRect.width() / world.width.toFloat()
        val cellPx = grainPx * world.grainScale
        val startX = boardRect.left + world.fallingX * grainPx
        val startY = boardRect.top + world.fallingY * grainPx
        val ghostY = boardRect.top + world.computeFallingGhostY() * grainPx

        // Subtle vertical column guide + ghost landing outline
        val pieceW = piece.shape.cols * cellPx
        fillPaint.color = ColorMath.withAlpha(world.theme.accentColor, 18)
        canvas.drawRect(startX, startY, startX + pieceW, ghostY + piece.shape.rows * cellPx, fillPaint)

        for (cell in piece.shape.cells) {
            val gl = startX + cell.col * cellPx
            val gt = ghostY + cell.row * cellPx
            tempRect.set(gl + 1f, gt + 1f, gl + cellPx - 1f, gt + cellPx - 1f)
            strokePaint.strokeWidth = 1.4f * density
            strokePaint.color = 0x88FFFFFF.toInt()
            canvas.drawRoundRect(tempRect, 3f * density, 3f * density, strokePaint)
        }

        drawPieceCells(canvas, piece, startX, startY, cellPx, alpha = 250)
    }

    private fun drawDragBoardPreview(canvas: Canvas, piece: TrayPiece) {
        // Highlight Hold slot if hovering over Hold card
        if (draggingSlot in 0..2 && holdCardRect.contains(touchX, touchY)) {
            return
        }

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

            fillPaint.color = ColorMath.withAlpha(world.theme.accentColor, 22)
            canvas.drawRect(
                snapLeft,
                snapTop,
                snapLeft + pieceScreenW,
                boardRect.bottom,
                fillPaint
            )

            if (piece.kind == PieceKind.BOMB) {
                val cx = snapLeft + pieceScreenW * 0.5f
                val cy = snapTop + (piece.shape.rows * cellPx) * 0.5f
                val blastR = SandWorld.BOMB_RADIUS * grainPx
                fillPaint.color = 0x33FF595E
                canvas.drawCircle(cx, cy, blastR, fillPaint)
                strokePaint.strokeWidth = 2f * density
                strokePaint.color = 0xCCFF595E.toInt()
                canvas.drawCircle(cx, cy, blastR, strokePaint)
            }

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
            for (cell in piece.shape.cells) {
                val cl = liftLeftX + cell.col * cellPx
                val ct = liftTopY + cell.row * cellPx
                tempRect.set(cl, ct, cl + cellPx, ct + cellPx)
                fillPaint.color = 0x44FF3B30
                canvas.drawRoundRect(tempRect, 3f * density, 3f * density, fillPaint)
            }
        }
    }

    private fun drawHoldAndTray(canvas: Canvas) {
        val theme = world.theme
        val hoveringHold = draggingSlot in 0..2 && isDraggingMoved && holdCardRect.contains(touchX, touchY)

        // 1. Hold Card on the left
        fillPaint.color = if (hoveringHold) {
            ColorMath.withAlpha(theme.accentColor, 75)
        } else {
            ColorMath.withAlpha(theme.surfaceCard, 240)
        }
        canvas.drawRoundRect(holdCardRect, 14f * density, 14f * density, fillPaint)
        strokePaint.strokeWidth = if (hoveringHold) 2.4f * density else 1.5f * density
        strokePaint.color = if (hoveringHold) theme.accentColor else ColorMath.withAlpha(theme.accentColor, 140)
        canvas.drawRoundRect(holdCardRect, 14f * density, 14f * density, strokePaint)

        textPaint.textSize = 9.5f * density
        textPaint.color = theme.accentColor
        canvas.drawText("📦 SAKLA", holdCardRect.centerX(), holdCardRect.top + 13f * density, textPaint)

        val held = world.holdPiece
        if (held != null && !(draggingSlot == HOLD_DRAG_SLOT && isDraggingMoved)) {
            drawPieceInsideCard(canvas, held, holdCardRect, topReserved = 14f * density)
        } else if (held == null) {
            textPaint.textSize = 9f * density
            textPaint.color = 0x66FFFFFF
            canvas.drawText("Buraya", holdCardRect.centerX(), holdCardRect.centerY() + 2f * density, textPaint)
            canvas.drawText("bırak", holdCardRect.centerX(), holdCardRect.centerY() + 13f * density, textPaint)
        }

        // 2. 3 Tray Cards
        for (i in 0 until 3) {
            val rect = trayCardRects[i]
            fillPaint.color = ColorMath.withAlpha(theme.surfaceCard, 235)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, fillPaint)

            strokePaint.strokeWidth = 1.5f * density
            strokePaint.color = ColorMath.withAlpha(theme.boardBorder, 95)
            canvas.drawRoundRect(rect, 14f * density, 14f * density, strokePaint)

            val piece = world.trayPieces[i]
            if (piece != null && !(draggingSlot == i && isDraggingMoved)) {
                drawPieceInsideCard(canvas, piece, rect, topReserved = 2f * density)
            }
        }
    }

    private fun drawSandtrixControls(canvas: Canvas) {
        val theme = world.theme

        // Left: Hold box
        fillPaint.color = ColorMath.withAlpha(theme.surfaceCard, 240)
        canvas.drawRoundRect(stxHoldRect, 13f * density, 13f * density, fillPaint)
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.color = ColorMath.withAlpha(theme.accentColor, if (world.canSwapHoldThisDrop) 160 else 65)
        canvas.drawRoundRect(stxHoldRect, 13f * density, 13f * density, strokePaint)
        textPaint.textSize = 9f * density
        textPaint.color = theme.accentColor
        canvas.drawText("📦 SAKLA", stxHoldRect.centerX(), stxHoldRect.top + 12f * density, textPaint)
        world.holdPiece?.let {
            drawPieceInsideCard(canvas, it, stxHoldRect, topReserved = 14f * density, showBadge = false)
        }

        // Middle 4 Arcade Control Pads
        drawArcadePad(canvas, stxLeftRect, "◀", "SOL", theme.surfaceCard, theme.boardBorder)
        drawArcadePad(canvas, stxRotRect, "↻", "DÖNDÜR", theme.surfaceCard, theme.accentColor)
        drawArcadePad(canvas, stxDropRect, "⏬", "İNDİR", theme.surfaceCard, 0xFFFFB703.toInt())
        drawArcadePad(canvas, stxRightRect, "▶", "SAĞ", theme.surfaceCard, theme.boardBorder)

        // Right: Next Piece Preview box
        fillPaint.color = ColorMath.withAlpha(theme.surfaceCard, 240)
        canvas.drawRoundRect(stxNextRect, 13f * density, 13f * density, fillPaint)
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.color = ColorMath.withAlpha(theme.boardBorder, 120)
        canvas.drawRoundRect(stxNextRect, 13f * density, 13f * density, strokePaint)
        textPaint.textSize = 9f * density
        textPaint.color = 0xCCFFFFFF.toInt()
        canvas.drawText("SIRADAKİ", stxNextRect.centerX(), stxNextRect.top + 12f * density, textPaint)
        world.nextFallingPiece?.let {
            drawPieceInsideCard(canvas, it, stxNextRect, topReserved = 14f * density, showBadge = false)
        }
    }

    private fun drawArcadePad(
        canvas: Canvas,
        rect: RectF,
        icon: String,
        subLabel: String,
        bgColor: Int,
        borderColor: Int
    ) {
        fillPaint.color = ColorMath.withAlpha(bgColor, 242)
        canvas.drawRoundRect(rect, 13f * density, 13f * density, fillPaint)
        strokePaint.strokeWidth = 1.6f * density
        strokePaint.color = ColorMath.withAlpha(borderColor, 150)
        canvas.drawRoundRect(rect, 13f * density, 13f * density, strokePaint)

        textPaint.textSize = 20f * density
        textPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawText(icon, rect.centerX(), rect.centerY() + 2f * density, textPaint)

        textPaint.textSize = 8.8f * density
        textPaint.color = 0xBBFFFFFF.toInt()
        canvas.drawText(subLabel, rect.centerX(), rect.bottom - 8f * density, textPaint)
    }

    private fun drawPieceInsideCard(
        canvas: Canvas,
        piece: TrayPiece,
        rect: RectF,
        topReserved: Float,
        showBadge: Boolean = true
    ) {
        val padX = 9f * density
        val padY = 9f * density
        val availW = (rect.width() - padX * 2f).coerceAtLeast(16f)
        val availH = (rect.height() - padY * 2f - topReserved).coerceAtLeast(16f)
        val maxCellW = availW / piece.shape.cols.coerceAtLeast(1)
        val maxCellH = availH / piece.shape.rows.coerceAtLeast(1)
        val cellPx = min(maxCellW, maxCellH).coerceAtMost(22f * density)

        val totalW = piece.shape.cols * cellPx
        val totalH = piece.shape.rows * cellPx
        val startX = rect.centerX() - totalW * 0.5f
        val centerAreaY = rect.top + topReserved + (rect.height() - topReserved) * 0.48f
        val startY = centerAreaY - totalH * 0.5f

        drawPieceCells(canvas, piece, startX, startY, cellPx, alpha = 255)

        if (showBadge) {
            when (piece.kind) {
                PieceKind.RAINBOW -> {
                    textPaint.textSize = 9.5f * density
                    textPaint.color = 0xFFFFEA00.toInt()
                    canvas.drawText("🌈 JOKER", rect.centerX(), rect.bottom - 5f * density, textPaint)
                }
                PieceKind.BOMB -> {
                    textPaint.textSize = 9.5f * density
                    textPaint.color = 0xFFFF6B6B.toInt()
                    canvas.drawText("💣 TNT", rect.centerX(), rect.bottom - 5f * density, textPaint)
                }
                PieceKind.LAVA -> {
                    textPaint.textSize = 9.5f * density
                    textPaint.color = 0xFFFF6D00.toInt()
                    canvas.drawText("🌋 LAV", rect.centerX(), rect.bottom - 5f * density, textPaint)
                }
                PieceKind.NORMAL -> Unit
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
                PieceKind.LAVA -> ColorMath.lavaColor(
                    (cell.col * 5 + cell.row * 9),
                    (animPhase * 45f).toInt()
                )
                PieceKind.BOMB -> 0xFFD62828.toInt()
            }

            fillPaint.shader = LinearGradient(
                cl,
                ct,
                cl + cellPx,
                ct + cellPx,
                ColorMath.withAlpha(ColorMath.shade(baseColor, 28), alpha),
                ColorMath.withAlpha(ColorMath.shade(baseColor, -24), alpha),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(tempRect, corner, corner, fillPaint)
            fillPaint.shader = null

            // Granular inner speckles
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

        // 1. Target Booster tap on board
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
                        BoosterType.COLOR_CLEANER -> world.clearAllSandOfColorAt(gx, gy)
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

        // 2. Sandtrix Touch & Button Controls
        if (world.mode == GameMode.SANDTRIX) {
            return handleSandtrixTouch(event, x, y)
        }

        // 3. Drag & Drop + Hold Slot Touch Controls
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val slot = when {
                    holdCardRect.contains(x, y) && world.holdPiece != null -> HOLD_DRAG_SLOT
                    else -> (0..2).firstOrNull { idx ->
                        trayCardRects[idx].contains(x, y) && world.trayPieces[idx] != null
                    } ?: -1
                }
                if (slot != -1) {
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
                if (draggingSlot != -1) {
                    touchX = x
                    touchY = y
                    if (!isDraggingMoved && hypot(x - downX, y - downY) > touchSlop * 0.7f) {
                        isDraggingMoved = true
                    }
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (draggingSlot != -1) {
                    val slot = draggingSlot
                    val piece = activeDraggedPiece()
                    val elapsed = event.eventTime - downTimeMs

                    if (!isDraggingMoved && elapsed < 320L) {
                        // Tap to rotate tray or held piece!
                        val rotated = if (slot == HOLD_DRAG_SLOT) {
                            world.rotateHoldPiece()
                        } else {
                            world.rotateTrayPiece(slot)
                        }
                        if (rotated) soundEngine.playTap()
                    } else if (slot in 0..2 && isDraggingMoved && holdCardRect.contains(x, y)) {
                        // Dropped tray piece onto Hold slot!
                        if (world.holdOrSwapTrayPiece(slot)) {
                            soundEngine.playTap()
                            dirtyForAutoSave = true
                        }
                    } else if (piece != null && isDraggingMoved) {
                        val grainPx = boardRect.width() / world.width.toFloat()
                        val cellPx = grainPx * world.grainScale
                        val liftCenterY = y - 76f * density
                        val liftLeftX = x - (piece.shape.cols * cellPx * 0.5f)
                        val liftTopY = liftCenterY - (piece.shape.rows * cellPx * 0.5f)

                        if (liftCenterY <= boardRect.bottom + 22f * density &&
                            x in (boardRect.left - 28f * density)..(boardRect.right + 28f * density)
                        ) {
                            val rawGridX = ((liftLeftX - boardRect.left) / grainPx).toInt()
                            val rawGridY = ((liftTopY - boardRect.top) / grainPx).toInt()
                            if (slot == HOLD_DRAG_SLOT) {
                                world.placeHoldPiece(rawGridX, rawGridY)
                            } else {
                                world.placeTrayPiece(slot, rawGridX, rawGridY)
                            }
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

    private fun handleSandtrixTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when {
                    stxHoldRect.contains(x, y) -> {
                        if (world.swapSandtrixHold()) soundEngine.playTap()
                        invalidate()
                        return true
                    }
                    stxLeftRect.contains(x, y) -> {
                        if (world.moveFallingHorizontal(-4)) soundEngine.playTap()
                        invalidate()
                        return true
                    }
                    stxRightRect.contains(x, y) -> {
                        if (world.moveFallingHorizontal(4)) soundEngine.playTap()
                        invalidate()
                        return true
                    }
                    stxRotRect.contains(x, y) -> {
                        if (world.rotateFallingPiece()) soundEngine.playTap()
                        invalidate()
                        return true
                    }
                    stxDropRect.contains(x, y) -> {
                        world.hardDropFallingPiece()
                        invalidate()
                        return true
                    }
                    boardRect.contains(x, y) -> {
                        stxBoardTracking = true
                        downX = x
                        downY = y
                        stxLastStepX = x
                        stxSwipeConsumedDrop = false
                        downTimeMs = event.eventTime
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (stxBoardTracking && !stxSwipeConsumedDrop) {
                    val stepPx = (boardRect.width() / world.width.toFloat()) * 4f
                    val dx = x - stxLastStepX
                    if (kotlin.math.abs(dx) >= stepPx) {
                        val steps = (dx / stepPx).toInt()
                        if (steps != 0) {
                            world.moveFallingHorizontal(steps * 4)
                            stxLastStepX += steps * stepPx
                            invalidate()
                        }
                    }
                    val dy = y - downY
                    if (dy > 58f * density && dy > kotlin.math.abs(x - downX) * 1.5f) {
                        stxSwipeConsumedDrop = true
                        world.hardDropFallingPiece()
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (stxBoardTracking) {
                    val elapsed = event.eventTime - downTimeMs
                    if (!stxSwipeConsumedDrop && hypot(x - downX, y - downY) < touchSlop && elapsed < 280L) {
                        if (world.rotateFallingPiece()) soundEngine.playTap()
                        invalidate()
                    }
                    stxBoardTracking = false
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                stxBoardTracking = false
            }
        }
        return true
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
                p.isRisingEmber = false
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

    private fun spawnFeverEmber() {
        if (boardRect.width() <= 0f) return
        val p = particles.firstOrNull { !it.active } ?: return
        p.active = true
        p.isRisingEmber = true
        p.x = boardRect.left + fxRandom.nextFloat() * boardRect.width()
        p.y = boardRect.bottom - 4f * density
        p.vx = (fxRandom.nextFloat() - 0.5f) * 34f * density
        p.vy = -(75f + fxRandom.nextFloat() * 90f) * density
        p.size = (2.5f + fxRandom.nextFloat() * 3f) * density
        p.color = if (fxRandom.nextBoolean()) 0xFFFFB703.toInt() else 0xFFFF5400.toInt()
        p.maxLife = 0.7f + fxRandom.nextFloat() * 0.45f
        p.life = p.maxLife
    }

    private fun spawnPopup(text: String, x: Float, y: Float, color: Int) {
        val slot = popups.firstOrNull { !it.active } ?: popups[0]
        slot.text = text
        slot.x = x.coerceIn(boardRect.left + 58f * density, boardRect.right - 58f * density)
        slot.y = y.coerceIn(boardRect.top + 36f * density, boardRect.bottom - 24f * density)
        slot.color = color
        slot.maxLife = 1.10f
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
        if (shakeTrauma > 0f) {
            shakeTrauma = (shakeTrauma - 2.6f * dt).coerceAtLeast(0f)
        }

        if (world.isFeverActive && !isGamePaused) {
            emberSpawnTimer += dt
            while (emberSpawnTimer >= 0.045f) {
                emberSpawnTimer -= 0.045f
                spawnFeverEmber()
            }
        }

        val gravity = 460f * density
        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
            } else {
                if (!p.isRisingEmber) {
                    p.vy += gravity * dt
                }
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
            val ratio = (beam.life / 0.36f).coerceIn(0f, 1f)
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
            textPaint.textSize = 14.5f * density
            textPaint.color = ColorMath.withAlpha(0xFF000000.toInt(), (alpha * 0.78f).toInt())
            canvas.drawText(pop.text, pop.x + 1.5f * density, pop.y + 1.5f * density, textPaint)
            textPaint.color = ColorMath.withAlpha(pop.color, alpha)
            canvas.drawText(pop.text, pop.x, pop.y, textPaint)
        }
    }
}
