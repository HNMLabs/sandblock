import QuartzCore
import SwiftUI
import UIKit

struct HudSnapshot: Equatable {
    var score: Int
    var linesCleared: Int
    var combo: Int
    var movesRemaining: Int?
    var crackedRemaining: Int
    var timeRemainingDeciSec: Int = 900
    var feverStep: Int = 0 // 0..20
    var isFeverActive: Bool = false
    var status: GameStatus
    var wonStars: Int = 0
}

final class SandStageController: ObservableObject {
    let world: SandWorld
    weak var stageView: SandStageUIView?

    init(world: SandWorld = SandWorld()) {
        self.world = world
    }

    func invalidate() {
        stageView?.setNeedsDisplay()
    }

    func resetCachedHud() {
        stageView?.resetCachedHud()
    }
}

struct SandStageRepresentable: UIViewRepresentable {
    let controller: SandStageController
    let soundEngine: AsmrSoundEngine
    var isPaused: Bool
    var activeTargetBooster: BoosterType?
    var onHudUpdated: (HudSnapshot) -> Void
    var onLiveCoinsEarned: (Int) -> Int
    var onTargetBoosterExecuted: (BoosterType) -> Void
    var onPiecePlacedStat: () -> Void
    var onLineClearStat: (Int, Int) -> Void
    var onAutoSaveCheckpoint: () -> Void

    func makeUIView(context: Context) -> SandStageUIView {
        let view = SandStageUIView(
            world: controller.world,
            soundEngine: soundEngine
        )
        view.isOpaque = false
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = false
        applyCallbacks(to: view)
        view.isGamePaused = isPaused
        view.activeTargetBooster = activeTargetBooster
        controller.stageView = view
        return view
    }

    func updateUIView(_ uiView: SandStageUIView, context: Context) {
        controller.stageView = uiView
        applyCallbacks(to: uiView)
        uiView.isGamePaused = isPaused
        if uiView.activeTargetBooster != activeTargetBooster {
            uiView.activeTargetBooster = activeTargetBooster
        }
    }

    private func applyCallbacks(to view: SandStageUIView) {
        view.onHudUpdated = onHudUpdated
        view.onLiveCoinsEarned = onLiveCoinsEarned
        view.onTargetBoosterExecuted = onTargetBoosterExecuted
        view.onPiecePlacedStat = onPiecePlacedStat
        view.onLineClearStat = onLineClearStat
        view.onAutoSaveCheckpoint = onAutoSaveCheckpoint
    }
}

private final class DisplayLinkProxy {
    weak var target: SandStageUIView?
    init(target: SandStageUIView) { self.target = target }
    @objc func tick(_ link: CADisplayLink) {
        target?.handleDisplayLink(link)
    }
}

final class SandStageUIView: UIView {
    private static let holdDragSlot = 3

    private struct Particle {
        var active = false
        var x: CGFloat = 0
        var y: CGFloat = 0
        var vx: CGFloat = 0
        var vy: CGFloat = 0
        var size: CGFloat = 4
        var color: UInt32 = 0xFFFFFFFF
        var life: Float = 0
        var maxLife: Float = 1
        var isRisingEmber = false
    }

    private struct FloatingPopup {
        var text: String = ""
        var x: CGFloat = 0
        var y: CGFloat = 0
        var color: UInt32 = 0xFFFFFFFF
        var life: Float = 0
        var maxLife: Float = 1
        var active: Bool = false
    }

    private struct ShockwaveRing {
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxRadius: CGFloat = 100
        var color: UInt32 = 0xFFFFB703
        var life: Float = 0
        var active: Bool = false
    }

    private struct LaserBeamFx {
        var y: CGFloat = 0
        var life: Float = 0
        var active: Bool = false
    }

    let world: SandWorld
    private let soundEngine: AsmrSoundEngine

    var onHudUpdated: ((HudSnapshot) -> Void)?
    var onLiveCoinsEarned: ((Int) -> Int)?
    var onTargetBoosterExecuted: ((BoosterType) -> Void)?
    var onPiecePlacedStat: (() -> Void)?
    var onLineClearStat: ((Int, Int) -> Void)?
    var onAutoSaveCheckpoint: (() -> Void)?

    private var pixelBuffer: [UInt32]
    private let colorSpace = CGColorSpaceCreateDeviceRGB()
    private let bitmapInfo = CGBitmapInfo.byteOrder32Little.rawValue | CGImageAlphaInfo.noneSkipFirst.rawValue

    private var boardRect: CGRect = .zero
    private var boardFrameRect: CGRect = .zero
    private var holdCardRect: CGRect = .zero
    private var trayCardRects: [CGRect] = [.zero, .zero, .zero]

    private var stxHoldRect: CGRect = .zero
    private var stxLeftRect: CGRect = .zero
    private var stxRotRect: CGRect = .zero
    private var stxDropRect: CGRect = .zero
    private var stxRightRect: CGRect = .zero
    private var stxNextRect: CGRect = .zero

    private var particles = [Particle](repeating: Particle(), count: 320)
    private var popups = [FloatingPopup](repeating: FloatingPopup(), count: 18)
    private var shockwaves = [ShockwaveRing](repeating: ShockwaveRing(), count: 8)
    private var laserBeams = [LaserBeamFx](repeating: LaserBeamFx(), count: 4)
    private var fxRng = SeededRNG()

    var isGamePaused: Bool = false
    var activeTargetBooster: BoosterType? {
        didSet { setNeedsDisplay() }
    }

    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private var physicsAccumulator: Float = 0
    private var animPhase: Float = 0
    private var shakeTrauma: Float = 0
    private var emberSpawnTimer: Float = 0
    private var dirtyForAutoSave: Bool = false

    // Drag & Drop touch state (-1 = none, 0..2 = tray, 3 = hold)
    private var draggingSlot: Int = -1
    private var downPoint: CGPoint = .zero
    private var touchPoint: CGPoint = .zero
    private var isDraggingMoved: Bool = false
    private var downTimestamp: TimeInterval = 0
    private let touchSlop: CGFloat = 8.0

    // Sandtrix swipe state
    private var stxBoardTracking: Bool = false
    private var stxLastStepX: CGFloat = 0
    private var stxSwipeConsumedDrop: Bool = false

    // Cached HUD state
    private var lastScore: Int = -1
    private var lastClears: Int = -1
    private var lastCombo: Int = -1
    private var lastMovesRem: Int? = -2
    private var lastCracked: Int = -1
    private var lastTimeDeci: Int = -1
    private var lastFeverStep: Int = -1
    private var lastFeverActive: Bool = false
    private var lastStatus: GameStatus = .playing

    init(world: SandWorld, soundEngine: AsmrSoundEngine) {
        self.world = world
        self.soundEngine = soundEngine
        self.pixelBuffer = [UInt32](repeating: 0xFF000000, count: world.size)
        super.init(frame: .zero)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        stopLoop()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            startLoop()
        } else {
            stopLoop()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        computeLayout(w: bounds.width, h: bounds.height)
    }

    func startLoop() {
        guard displayLink == nil else { return }
        lastTimestamp = 0
        physicsAccumulator = 0
        let proxy = DisplayLinkProxy(target: self)
        let link = CADisplayLink(target: proxy, selector: #selector(DisplayLinkProxy.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stopLoop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    func resetCachedHud() {
        lastScore = -1
        lastTimeDeci = -1
        lastFeverStep = -1
        lastStatus = .playing
        draggingSlot = -1
        isDraggingMoved = false
        stxBoardTracking = false
        computeLayout(w: bounds.width, h: bounds.height)
        emitHudIfChanged(wonStars: 0)
        setNeedsDisplay()
    }

    private func computeLayout(w: CGFloat, h: CGFloat) {
        guard w > 0, h > 0 else { return }
        let sidePad: CGFloat = 12
        let topPad: CGFloat = 4
        let bottomPad: CGFloat = 8
        let trayHeight: CGFloat = min(104, h * 0.205)
        let hintGap: CGFloat = 22

        let availBoardH = max(120, h - topPad - bottomPad - trayHeight - hintGap)
        let availBoardW = max(100, w - sidePad * 2)

        let aspect = CGFloat(world.width) / CGFloat(world.height)
        var bW = availBoardW
        var bH = bW / aspect
        if bH > availBoardH {
            bH = availBoardH
            bW = bH * aspect
        }

        let bLeft = (w - bW) * 0.5
        let bTop = topPad + (availBoardH - bH) * 0.5
        boardRect = CGRect(x: bLeft, y: bTop, width: bW, height: bH)
        boardFrameRect = boardRect.insetBy(dx: -4, dy: -4)

        let trayTop = boardRect.maxY + hintGap
        let totalTrayW = min(w - sidePad * 2, bW + 36)
        let trayStartX = (w - totalTrayW) * 0.5

        // 4-column Drag & Drop layout: [Hold (0.86)] + 3x [Tray (1.0)]
        let cardGap: CGFloat = 7
        let unitW = (totalTrayW - cardGap * 3) / 3.86
        let holdW = unitW * 0.86
        holdCardRect = CGRect(x: trayStartX, y: trayTop, width: holdW, height: trayHeight)
        var curLeft = holdCardRect.maxX + cardGap
        for i in 0..<3 {
            trayCardRects[i] = CGRect(x: curLeft, y: trayTop, width: unitW, height: trayHeight)
            curLeft += unitW + cardGap
        }

        // Sandtrix bottom deck: [SAKLA] [◀] [↻] [⏬] [▶] [SIRADAKİ]
        let stxGap: CGFloat = 5
        let sideBoxW = totalTrayW * 0.20
        let midTotalW = totalTrayW - sideBoxW * 2 - stxGap * 5
        let btnW = midTotalW / 4
        var sx = trayStartX
        stxHoldRect = CGRect(x: sx, y: trayTop, width: sideBoxW, height: trayHeight)
        sx += sideBoxW + stxGap
        stxLeftRect = CGRect(x: sx, y: trayTop, width: btnW, height: trayHeight)
        sx += btnW + stxGap
        stxRotRect = CGRect(x: sx, y: trayTop, width: btnW, height: trayHeight)
        sx += btnW + stxGap
        stxDropRect = CGRect(x: sx, y: trayTop, width: btnW, height: trayHeight)
        sx += btnW + stxGap
        stxRightRect = CGRect(x: sx, y: trayTop, width: btnW, height: trayHeight)
        sx += btnW + stxGap
        stxNextRect = CGRect(x: sx, y: trayTop, width: sideBoxW, height: trayHeight)
    }

    fileprivate func handleDisplayLink(_ link: CADisplayLink) {
        let dt: Float
        if lastTimestamp == 0 {
            dt = 1.0 / 60.0
        } else {
            dt = min(0.05, max(0.004, Float(link.timestamp - lastTimestamp)))
        }
        lastTimestamp = link.timestamp
        animPhase += dt

        if !isGamePaused {
            physicsAccumulator = min(physicsAccumulator + dt, (1.0 / 60.0) * 2.5)
            let fixedStep: Float = 1.0 / 60.0
            while physicsAccumulator >= fixedStep * 0.92 {
                world.updateFrame(physicsSteps: 4, dt: fixedStep)
                processWorldEvents()
                physicsAccumulator = max(0, physicsAccumulator - fixedStep)
            }

            if dirtyForAutoSave && world.mode == .classic && world.isBoardQuiet && world.status == .playing {
                dirtyForAutoSave = false
                onAutoSaveCheckpoint?()
            }
        }

        updateVisualEffects(dt: dt)
        setNeedsDisplay()
    }

    private func triggerShake(_ amount: Float) {
        shakeTrauma = min(1.0, max(0.0, shakeTrauma + amount))
    }

    private func processWorldEvents() {
        let events = world.drainEvents()
        var wonStars = 0
        for ev in events {
            switch ev {
            case let .piecePlaced(centerX, centerY, colorArgb, isRainbow, isLava):
                soundEngine.playSandDrop(isRainbow: isRainbow, isLava: isLava)
                let sx = gridToScreenX(centerX)
                let sy = gridToScreenY(centerY)
                spawnBurst(cx: sx, cy: sy, baseColor: colorArgb, count: isLava ? 26 : 18, speed: 115)
                if isLava {
                    spawnPopup("🌋 KOR LAV!", x: sx, y: sy - 14, color: 0xFFFF6D00)
                }
                onPiecePlacedStat?()
                dirtyForAutoSave = true

            case let .lineClearStarted(combo, _, crackedBlocksBroken, centerX, centerY, colorArgb, pointsAwarded, coinsAwarded, feverBonusActive):
                soundEngine.playLineClear(combo: combo)
                let awardedGold = onLiveCoinsEarned?(coinsAwarded) ?? coinsAwarded
                let sx = gridToScreenX(centerX)
                let sy = gridToScreenY(centerY)
                spawnBurst(cx: sx, cy: sy, baseColor: colorArgb, count: 52, speed: 220)
                if combo >= 2 {
                    triggerShake(min(0.85, 0.28 + Float(combo) * 0.12))
                } else {
                    triggerShake(0.14)
                }
                let feverTag = feverBonusActive ? " 🔥2X" : ""
                let label = combo >= 2
                    ? "KOMBO x\(combo)\(feverTag)! +\(pointsAwarded)  🪙+\(awardedGold)"
                    : "+\(pointsAwarded)\(feverTag)  🪙+\(awardedGold)"
                let popColor: UInt32 = (combo >= 2 || feverBonusActive) ? 0xFFFFEA00 : 0xFFFFFFFF
                spawnPopup(label, x: sx, y: sy, color: popColor)
                if crackedBlocksBroken > 0 {
                    spawnPopup("ENGEL KIRILDI! x\(crackedBlocksBroken)", x: sx, y: sy - 26, color: 0xFF72EFDD)
                }
                onLineClearStat?(combo, crackedBlocksBroken)
                dirtyForAutoSave = true

            case let .bombExploded(centerX, centerY, _, pointsAwarded):
                soundEngine.playBomb()
                triggerShake(0.68)
                let sx = gridToScreenX(centerX)
                let sy = gridToScreenY(centerY)
                let maxR = (CGFloat(SandWorld.bombRadius) / CGFloat(world.width)) * boardRect.width * 1.25
                spawnShockwave(x: sx, y: sy, maxR: maxR, color: 0xFFFF6B35)
                spawnBurst(cx: sx, cy: sy, baseColor: 0xFFFF9F1C, count: 68, speed: 285)
                spawnPopup("BOOM! +\(pointsAwarded)", x: sx, y: sy, color: 0xFFFF9F1C)
                dirtyForAutoSave = true

            case let .laserFired(centerY, _, pointsAwarded):
                soundEngine.playLaser()
                triggerShake(0.50)
                let sy = gridToScreenY(centerY)
                let idx = laserBeams.firstIndex(where: { !$0.active }) ?? 0
                laserBeams[idx] = LaserBeamFx(y: sy, life: 0.36, active: true)
                for step in 1...5 {
                    let bx = boardRect.minX + boardRect.width * (CGFloat(step) / 6.0)
                    spawnBurst(cx: bx, cy: sy, baseColor: 0xFF00F5D4, count: 14, speed: 230)
                }
                spawnPopup("⚡ LAZER! +\(pointsAwarded)", x: boardRect.midX, y: sy, color: 0xFF00F5D4)
                dirtyForAutoSave = true

            case let .colorCleaned(_, colorArgb, _, pointsAwarded):
                soundEngine.playMagic()
                triggerShake(0.35)
                let cx = boardRect.midX
                let cy = boardRect.midY
                spawnShockwave(x: cx, y: cy, maxR: boardRect.width * 0.55, color: colorArgb)
                spawnBurst(cx: cx, cy: cy, baseColor: colorArgb, count: 60, speed: 250)
                spawnPopup("RENK SİLİNDİ! +\(pointsAwarded)", x: cx, y: cy, color: colorArgb)
                dirtyForAutoSave = true

            case let .sandStormCleared(topY, _, pointsAwarded):
                soundEngine.playMagic()
                triggerShake(0.42)
                let sy = gridToScreenY(topY)
                spawnShockwave(x: boardRect.midX, y: sy, maxR: boardRect.width * 0.6, color: 0xFFFFD166)
                spawnBurst(cx: boardRect.midX, cy: sy, baseColor: 0xFFFFD166, count: 70, speed: 260)
                spawnPopup("🌪️ KUM FIRTINASI! +\(pointsAwarded)", x: boardRect.midX, y: sy, color: 0xFFFFD166)
                dirtyForAutoSave = true

            case let .lavaMelted(centerX, centerY, _, pointsAwarded):
                soundEngine.playLava()
                let sx = gridToScreenX(centerX)
                let sy = gridToScreenY(centerY)
                spawnBurst(cx: sx, cy: sy, baseColor: 0xFFFF5400, count: 22, speed: 155)
                spawnPopup("🌋 ERİDİ +\(pointsAwarded)", x: sx, y: sy, color: 0xFFFF9E00)

            case .feverStarted:
                soundEngine.playFever()
                triggerShake(0.55)
                let cx = boardRect.midX
                let cy = boardRect.midY
                spawnShockwave(x: cx, y: cy, maxR: boardRect.width * 0.7, color: 0xFFFF6D00)
                spawnBurst(cx: cx, cy: cy, baseColor: 0xFFFFB703, count: 75, speed: 270)
                spawnPopup("🔥 KOMBO ATEŞİ! 10sn 2X PUAN & ALTIN!", x: cx, y: cy - 20, color: 0xFFFFEA00)

            case let .timeBonusAdded(bonusSeconds):
                spawnPopup("⏱️ +\(Int(bonusSeconds))sn", x: boardRect.maxX - 64, y: boardRect.minY + 42, color: 0xFF00F5D4)

            case .revived:
                soundEngine.playMagic()
                triggerShake(0.45)
                let cx = boardRect.midX
                let cy = boardRect.minY + boardRect.height * 0.3
                spawnShockwave(x: cx, y: cy, maxR: boardRect.width * 0.65, color: 0xFF06D6A0)
                spawnBurst(cx: cx, cy: cy, baseColor: 0xFF06D6A0, count: 65, speed: 250)
                spawnPopup("💖 İKİNCİ ŞANS! DEVAM!", x: cx, y: cy, color: 0xFF72EFDD)

            case let .levelWon(stars, _):
                wonStars = stars
                soundEngine.playWin()
                spawnBurst(cx: boardRect.midX, cy: boardRect.midY, baseColor: 0xFFFFD166, count: 85, speed: 300)

            case .gameLost:
                soundEngine.playGameOver()
                triggerShake(0.45)
            }
        }
        emitHudIfChanged(wonStars: wonStars)
    }

    private func emitHudIfChanged(wonStars: Int) {
        let cracked = (world.adventureLevel?.requireClearCrackedStones == true)
            ? world.countRemainingCrackedStones()
            : 0
        let movesRem = world.movesRemaining
        let timeDeci = (world.mode == .timeRush) ? Int(world.timeRemainingSec * 10.0) : 900
        let feverRatio: Float = world.isFeverActive
            ? min(1.0, max(0.0, world.feverRemainingSec / SandWorld.feverDurationSec))
            : min(1.0, max(0.0, world.feverCharge))
        let feverStep = min(20, max(0, Int(feverRatio * 20.0)))
        let feverAct = world.isFeverActive

        if world.score != lastScore ||
            world.linesCleared != lastClears ||
            world.combo != lastCombo ||
            movesRem != lastMovesRem ||
            cracked != lastCracked ||
            timeDeci != lastTimeDeci ||
            feverStep != lastFeverStep ||
            feverAct != lastFeverActive ||
            world.status != lastStatus ||
            wonStars > 0 {
            lastScore = world.score
            lastClears = world.linesCleared
            lastCombo = world.combo
            lastMovesRem = movesRem
            lastCracked = cracked
            lastTimeDeci = timeDeci
            lastFeverStep = feverStep
            lastFeverActive = feverAct
            lastStatus = world.status

            onHudUpdated?(
                HudSnapshot(
                    score: world.score,
                    linesCleared: world.linesCleared,
                    combo: world.combo,
                    movesRemaining: movesRem,
                    crackedRemaining: cracked,
                    timeRemainingDeciSec: timeDeci,
                    feverStep: feverStep,
                    isFeverActive: feverAct,
                    status: world.status,
                    wonStars: wonStars
                )
            )
        }
    }

    // MARK: - CoreGraphics 60FPS Drawing

    override func draw(_ rect: CGRect) {
        guard boardRect.width > 0, let ctx = UIGraphicsGetCurrentContext() else { return }

        let theme = world.theme
        let cornerR: CGFloat = 14

        ctx.saveGState()
        if shakeTrauma > 0.01 {
            let mag = CGFloat(shakeTrauma * shakeTrauma) * 9.0
            let sx = CGFloat(fxRng.nextFloat() * 2.0 - 1.0) * mag
            let sy = CGFloat(fxRng.nextFloat() * 2.0 - 1.0) * mag
            ctx.translateBy(x: sx, y: sy)
        }

        // 1. Board Outer Frame Fill
        UIColor(argb: theme.boardBg).setFill()
        UIBezierPath(roundedRect: boardFrameRect, cornerRadius: cornerR).fill()

        // 2. Render SandWorld pixel buffer & blit crisp pixel-art sand
        world.renderPixels(&pixelBuffer)
        let cgImage: CGImage? = pixelBuffer.withUnsafeMutableBytes { rawBuf in
            guard let base = rawBuf.baseAddress,
                  let bmp = CGContext(
                      data: base,
                      width: world.width,
                      height: world.height,
                      bitsPerComponent: 8,
                      bytesPerRow: world.width * 4,
                      space: colorSpace,
                      bitmapInfo: bitmapInfo
                  ) else { return nil }
            return bmp.makeImage()
        }

        ctx.saveGState()
        UIBezierPath(roundedRect: boardRect, cornerRadius: cornerR - 3).addClip()
        if let cgImage = cgImage {
            ctx.interpolationQuality = .none
            UIImage(cgImage: cgImage).draw(in: boardRect)
        }

        // 3. Danger Line
        let dangerY = gridToScreenY(Float(world.dangerRow))
        let highestRow = world.highestSandRow()
        let isDangerNear = highestRow < world.dangerRow + 16
        let pulseAlpha: Int = isDangerNear
            ? min(255, max(90, 170 + Int(sin(animPhase * 9.0) * 80.0)))
            : 135

        if isDangerNear {
            UIColor(argb: ColorMath.withAlpha(theme.dangerLineColor, 34)).setFill()
            ctx.fill(CGRect(x: boardRect.minX, y: boardRect.minY, width: boardRect.width, height: dangerY - boardRect.minY))
        }

        let dashPath = UIBezierPath()
        dashPath.move(to: CGPoint(x: boardRect.minX, y: dangerY))
        dashPath.addLine(to: CGPoint(x: boardRect.maxX, y: dangerY))
        dashPath.lineWidth = 2.2
        let dashes: [CGFloat] = [8, 6]
        dashPath.setLineDash(dashes, count: 2, phase: 0)
        UIColor(argb: ColorMath.withAlpha(theme.dangerLineColor, pulseAlpha)).setStroke()
        dashPath.stroke()

        drawRightText(
            "SINIR",
            rightX: boardRect.maxX - 6,
            centerY: dangerY - 8,
            fontSize: 9.5,
            color: UIColor(argb: ColorMath.withAlpha(theme.dangerLineColor, pulseAlpha))
        )

        // 4. Mode-specific Board Overlays
        if world.mode == .sandtrix {
            drawSandtrixFallingPiece(ctx: ctx)
        } else if draggingSlot != -1 && isDraggingMoved, let piece = activeDraggedPiece() {
            drawDragBoardPreview(ctx: ctx, piece: piece)
        }

        // 5. Fever Mode active pill inside top-left of board
        if world.isFeverActive {
            let pillRect = CGRect(x: boardRect.minX + 6, y: boardRect.minY + 5, width: 102, height: 20)
            UIColor(argb: 0xCCFF5400).setFill()
            UIBezierPath(roundedRect: pillRect, cornerRadius: 10).fill()
            drawCenteredText(
                "🔥 2X FEVER (\(Int(world.feverRemainingSec) + 1)s)",
                centerX: pillRect.midX,
                centerY: pillRect.midY,
                fontSize: 10,
                color: UIColor(argb: 0xFFFFF3B0)
            )
        }

        // 6. Target Booster highlight overlay on board
        if let booster = activeTargetBooster {
            let glowAlpha = min(80, max(20, 45 + Int(sin(animPhase * 6.0) * 25.0)))
            UIColor(argb: ColorMath.withAlpha(theme.accentColor, glowAlpha / 2)).setFill()
            ctx.fill(boardRect)
            drawCenteredText(
                booster.hintTr,
                centerX: boardRect.midX,
                centerY: boardRect.minY + 26,
                fontSize: 13,
                color: .white,
                shadow: true
            )
        }

        ctx.restoreGState() // End board clip

        // 7. Outer Border
        let borderPath = UIBezierPath(roundedRect: boardFrameRect, cornerRadius: cornerR)
        if world.isFeverActive {
            let pulse = min(255, max(120, 185 + Int(sin(animPhase * 12.0) * 65.0)))
            borderPath.lineWidth = 4.0
            UIColor(argb: ColorMath.withAlpha(0xFFFF6D00, pulse)).setStroke()
            borderPath.stroke()
        } else {
            borderPath.lineWidth = 2.5
            let borderCol = activeTargetBooster != nil
                ? theme.accentColor
                : ColorMath.withAlpha(theme.boardBorder, 215)
            UIColor(argb: borderCol).setStroke()
            borderPath.stroke()
        }

        // 8. Visual FX (inside shake space)
        drawEffects(ctx: ctx)
        ctx.restoreGState() // End shake space

        // 9. Hint strip between board & tray/controls
        let hintY = boardFrameRect.maxY + 11
        let hintStr: String
        if let booster = activeTargetBooster {
            hintStr = booster.hintTr
        } else if world.mode == .sandtrix {
            hintStr = "Alanda kaydır/dokun veya alttaki tuşlarla yönlendir & indir"
        } else {
            hintStr = "Döndürmek için dokun • Sürükle & bırak • Sol yuvada SAKLA"
        }
        drawCenteredText(hintStr, centerX: bounds.midX, centerY: hintY, fontSize: 10.5, weight: .semibold, color: UIColor(white: 1, alpha: 0.68))

        // 10. Bottom Deck
        if world.mode == .sandtrix {
            drawSandtrixControls(ctx: ctx)
        } else {
            drawHoldAndTray(ctx: ctx)
        }

        // 11. Floating Dragged Piece above finger
        if world.mode != .sandtrix && draggingSlot != -1 && isDraggingMoved, let piece = activeDraggedPiece() {
            let grainPx = boardRect.width / CGFloat(world.width)
            let cellPx = grainPx * CGFloat(world.grainScale)
            let liftY = touchPoint.y - 76 - (CGFloat(piece.shape.rows) * cellPx * 0.5)
            let leftX = touchPoint.x - (CGFloat(piece.shape.cols) * cellPx * 0.5)
            drawPieceCells(ctx: ctx, piece: piece, startX: leftX, startY: liftY, cellPx: cellPx, alpha: 242)
        }
    }

    private func activeDraggedPiece() -> TrayPiece? {
        if (0...2).contains(draggingSlot) {
            return world.trayPieces[draggingSlot]
        }
        if draggingSlot == Self.holdDragSlot {
            return world.holdPiece
        }
        return nil
    }

    private func drawSandtrixFallingPiece(ctx: CGContext) {
        guard let piece = world.fallingPiece else { return }
        let grainPx = boardRect.width / CGFloat(world.width)
        let cellPx = grainPx * CGFloat(world.grainScale)
        let startX = boardRect.minX + CGFloat(world.fallingX) * grainPx
        let startY = boardRect.minY + CGFloat(world.fallingY) * grainPx
        let ghostY = boardRect.minY + CGFloat(world.computeFallingGhostY()) * grainPx

        let pieceW = CGFloat(piece.shape.cols) * cellPx
        let colGuideH = (ghostY + CGFloat(piece.shape.rows) * cellPx) - startY
        if colGuideH > 0 {
            UIColor(argb: ColorMath.withAlpha(world.theme.accentColor, 18)).setFill()
            ctx.fill(CGRect(x: startX, y: startY, width: pieceW, height: colGuideH))
        }

        UIColor(white: 1.0, alpha: 0.55).setStroke()
        for cell in piece.shape.cells {
            let gl = startX + CGFloat(cell.col) * cellPx
            let gt = ghostY + CGFloat(cell.row) * cellPx
            let r = CGRect(x: gl + 1, y: gt + 1, width: cellPx - 2, height: cellPx - 2)
            let bp = UIBezierPath(roundedRect: r, cornerRadius: 3)
            bp.lineWidth = 1.4
            bp.stroke()
        }

        drawPieceCells(ctx: ctx, piece: piece, startX: startX, startY: startY, cellPx: cellPx, alpha: 250)
    }

    private func drawDragBoardPreview(ctx: CGContext, piece: TrayPiece) {
        if (0...2).contains(draggingSlot) && holdCardRect.contains(touchPoint) {
            return
        }

        let grainPx = boardRect.width / CGFloat(world.width)
        let cellPx = grainPx * CGFloat(world.grainScale)
        let liftCenterY = touchPoint.y - 76
        let liftLeftX = touchPoint.x - (CGFloat(piece.shape.cols) * cellPx * 0.5)
        let liftTopY = liftCenterY - (CGFloat(piece.shape.rows) * cellPx * 0.5)

        if liftCenterY > boardRect.maxY + 24 { return }

        let rawGridX = Int(((liftLeftX - boardRect.minX) / grainPx).rounded(.towardZero))
        let rawGridY = Int(((liftTopY - boardRect.minY) / grainPx).rounded(.towardZero))

        if let (gx, gy) = world.findValidPlacement(piece: piece, rawX: rawGridX, rawY: rawGridY) {
            let snapLeft = boardRect.minX + CGFloat(gx) * grainPx
            let snapTop = boardRect.minY + CGFloat(gy) * grainPx
            let pieceScreenW = CGFloat(piece.shape.cols) * cellPx

            UIColor(argb: ColorMath.withAlpha(world.theme.accentColor, 22)).setFill()
            ctx.fill(CGRect(x: snapLeft, y: snapTop, width: pieceScreenW, height: boardRect.maxY - snapTop))

            if piece.kind == .BOMB {
                let cx = snapLeft + pieceScreenW * 0.5
                let cy = snapTop + (CGFloat(piece.shape.rows) * cellPx) * 0.5
                let blastR = CGFloat(SandWorld.bombRadius) * grainPx
                let circle = UIBezierPath(arcCenter: CGPoint(x: cx, y: cy), radius: blastR, startAngle: 0, endAngle: .pi * 2, clockwise: true)
                UIColor(argb: 0x33FF595E).setFill()
                circle.fill()
                circle.lineWidth = 2.0
                UIColor(argb: 0xCCFF595E).setStroke()
                circle.stroke()
            }

            for cell in piece.shape.cells {
                let cl = snapLeft + CGFloat(cell.col) * cellPx
                let ct = snapTop + CGFloat(cell.row) * cellPx
                let r = CGRect(x: cl + 1, y: ct + 1, width: cellPx - 2, height: cellPx - 2)
                let bp = UIBezierPath(roundedRect: r, cornerRadius: 3)
                UIColor(white: 1.0, alpha: 0.26).setFill()
                bp.fill()
                bp.lineWidth = 1.6
                UIColor(white: 1.0, alpha: 0.80).setStroke()
                bp.stroke()
            }
        } else {
            UIColor(argb: 0x44FF3B30).setFill()
            for cell in piece.shape.cells {
                let cl = liftLeftX + CGFloat(cell.col) * cellPx
                let ct = liftTopY + CGFloat(cell.row) * cellPx
                UIBezierPath(roundedRect: CGRect(x: cl, y: ct, width: cellPx, height: cellPx), cornerRadius: 3).fill()
            }
        }
    }

    private func drawHoldAndTray(ctx: CGContext) {
        let theme = world.theme
        let hoveringHold = (0...2).contains(draggingSlot) && isDraggingMoved && holdCardRect.contains(touchPoint)

        let holdBg = hoveringHold
            ? ColorMath.withAlpha(theme.accentColor, 75)
            : ColorMath.withAlpha(theme.surfaceCard, 240)
        let holdPath = UIBezierPath(roundedRect: holdCardRect, cornerRadius: 14)
        UIColor(argb: holdBg).setFill()
        holdPath.fill()
        holdPath.lineWidth = hoveringHold ? 2.4 : 1.5
        UIColor(argb: hoveringHold ? theme.accentColor : ColorMath.withAlpha(theme.accentColor, 140)).setStroke()
        holdPath.stroke()

        drawCenteredText(
            "📦 SAKLA",
            centerX: holdCardRect.midX,
            centerY: holdCardRect.minY + 11,
            fontSize: 9.5,
            color: UIColor(argb: theme.accentColor)
        )

        if let held = world.holdPiece, !(draggingSlot == Self.holdDragSlot && isDraggingMoved) {
            drawPieceInsideCard(ctx: ctx, piece: held, rect: holdCardRect, topReserved: 15)
        } else if world.holdPiece == nil {
            drawCenteredText("Buraya", centerX: holdCardRect.midX, centerY: holdCardRect.midY - 2, fontSize: 9, weight: .semibold, color: UIColor(white: 1, alpha: 0.42))
            drawCenteredText("bırak", centerX: holdCardRect.midX, centerY: holdCardRect.midY + 9, fontSize: 9, weight: .semibold, color: UIColor(white: 1, alpha: 0.42))
        }

        for i in 0..<3 {
            let rect = trayCardRects[i]
            let bp = UIBezierPath(roundedRect: rect, cornerRadius: 14)
            UIColor(argb: ColorMath.withAlpha(theme.surfaceCard, 235)).setFill()
            bp.fill()
            bp.lineWidth = 1.5
            UIColor(argb: ColorMath.withAlpha(theme.boardBorder, 95)).setStroke()
            bp.stroke()

            if let piece = world.trayPieces[i], !(draggingSlot == i && isDraggingMoved) {
                drawPieceInsideCard(ctx: ctx, piece: piece, rect: rect, topReserved: 2)
            }
        }
    }

    private func drawSandtrixControls(ctx: CGContext) {
        let theme = world.theme

        let holdBp = UIBezierPath(roundedRect: stxHoldRect, cornerRadius: 13)
        UIColor(argb: ColorMath.withAlpha(theme.surfaceCard, 240)).setFill()
        holdBp.fill()
        holdBp.lineWidth = 1.5
        UIColor(argb: ColorMath.withAlpha(theme.accentColor, world.canSwapHoldThisDrop ? 160 : 65)).setStroke()
        holdBp.stroke()
        drawCenteredText("📦 SAKLA", centerX: stxHoldRect.midX, centerY: stxHoldRect.minY + 10, fontSize: 9, color: UIColor(argb: theme.accentColor))
        if let hp = world.holdPiece {
            drawPieceInsideCard(ctx: ctx, piece: hp, rect: stxHoldRect, topReserved: 14, showBadge: false)
        }

        drawArcadePad(rect: stxLeftRect, icon: "◀", subLabel: "SOL", bgColor: theme.surfaceCard, borderColor: theme.boardBorder)
        drawArcadePad(rect: stxRotRect, icon: "↻", subLabel: "DÖNDÜR", bgColor: theme.surfaceCard, borderColor: theme.accentColor)
        drawArcadePad(rect: stxDropRect, icon: "⏬", subLabel: "İNDİR", bgColor: theme.surfaceCard, borderColor: 0xFFFFB703)
        drawArcadePad(rect: stxRightRect, icon: "▶", subLabel: "SAĞ", bgColor: theme.surfaceCard, borderColor: theme.boardBorder)

        let nextBp = UIBezierPath(roundedRect: stxNextRect, cornerRadius: 13)
        UIColor(argb: ColorMath.withAlpha(theme.surfaceCard, 240)).setFill()
        nextBp.fill()
        nextBp.lineWidth = 1.5
        UIColor(argb: ColorMath.withAlpha(theme.boardBorder, 120)).setStroke()
        nextBp.stroke()
        drawCenteredText("SIRADAKİ", centerX: stxNextRect.midX, centerY: stxNextRect.minY + 10, fontSize: 9, color: UIColor(white: 1, alpha: 0.8))
        if let np = world.nextFallingPiece {
            drawPieceInsideCard(ctx: ctx, piece: np, rect: stxNextRect, topReserved: 14, showBadge: false)
        }
    }

    private func drawArcadePad(rect: CGRect, icon: String, subLabel: String, bgColor: UInt32, borderColor: UInt32) {
        let bp = UIBezierPath(roundedRect: rect, cornerRadius: 13)
        UIColor(argb: ColorMath.withAlpha(bgColor, 242)).setFill()
        bp.fill()
        bp.lineWidth = 1.6
        UIColor(argb: ColorMath.withAlpha(borderColor, 150)).setStroke()
        bp.stroke()

        drawCenteredText(icon, centerX: rect.midX, centerY: rect.midY - 5, fontSize: 20, color: .white)
        drawCenteredText(subLabel, centerX: rect.midX, centerY: rect.maxY - 12, fontSize: 8.8, color: UIColor(white: 1, alpha: 0.75))
    }

    private func drawPieceInsideCard(
        ctx: CGContext,
        piece: TrayPiece,
        rect: CGRect,
        topReserved: CGFloat,
        showBadge: Bool = true
    ) {
        let padX: CGFloat = 9
        let padY: CGFloat = 9
        let availW = max(16, rect.width - padX * 2)
        let availH = max(16, rect.height - padY * 2 - topReserved)
        let maxCellW = availW / CGFloat(max(1, piece.shape.cols))
        let maxCellH = availH / CGFloat(max(1, piece.shape.rows))
        let cellPx = min(22, min(maxCellW, maxCellH))

        let totalW = CGFloat(piece.shape.cols) * cellPx
        let totalH = CGFloat(piece.shape.rows) * cellPx
        let startX = rect.midX - totalW * 0.5
        let centerAreaY = rect.minY + topReserved + (rect.height - topReserved) * 0.46
        let startY = centerAreaY - totalH * 0.5

        drawPieceCells(ctx: ctx, piece: piece, startX: startX, startY: startY, cellPx: cellPx, alpha: 255)

        if showBadge {
            switch piece.kind {
            case .RAINBOW:
                drawCenteredText("🌈 JOKER", centerX: rect.midX, centerY: rect.maxY - 9, fontSize: 9.2, color: UIColor(argb: 0xFFFFEA00))
            case .BOMB:
                drawCenteredText("💣 TNT", centerX: rect.midX, centerY: rect.maxY - 9, fontSize: 9.2, color: UIColor(argb: 0xFFFF6B6B))
            case .LAVA:
                drawCenteredText("🌋 LAV", centerX: rect.midX, centerY: rect.maxY - 9, fontSize: 9.2, color: UIColor(argb: 0xFFFF6D00))
            case .NORMAL:
                break
            }
        }
    }

    private func drawPieceCells(
        ctx: CGContext,
        piece: TrayPiece,
        startX: CGFloat,
        startY: CGFloat,
        cellPx: CGFloat,
        alpha: Int
    ) {
        let palette = world.theme.sandPalette
        let corner = max(2.0, cellPx * 0.18)

        for cell in piece.shape.cells {
            let cl = startX + CGFloat(cell.col) * cellPx
            let ct = startY + CGFloat(cell.row) * cellPx
            let cellRect = CGRect(x: cl + 0.8, y: ct + 0.8, width: cellPx - 1.6, height: cellPx - 1.6)

            let baseColor: UInt32
            switch piece.kind {
            case .NORMAL:
                baseColor = palette[piece.colorIndex % palette.count]
            case .RAINBOW:
                baseColor = ColorMath.rainbowColor(seed: cell.col * 7 + cell.row * 11, tick: Int(animPhase * 40.0))
            case .LAVA:
                baseColor = ColorMath.lavaColor(seed: cell.col * 5 + cell.row * 9, tick: Int(animPhase * 45.0))
            case .BOMB:
                baseColor = 0xFFD62828
            }

            let topCol = UIColor(argb: ColorMath.withAlpha(ColorMath.shade(baseColor, 28), alpha)).cgColor
            let botCol = UIColor(argb: ColorMath.withAlpha(ColorMath.shade(baseColor, -24), alpha)).cgColor

            ctx.saveGState()
            let bp = UIBezierPath(roundedRect: cellRect, cornerRadius: corner)
            bp.addClip()
            if let grad = CGGradient(colorsSpace: colorSpace, colors: [topCol, botCol] as CFArray, locations: [0, 1]) {
                ctx.drawLinearGradient(
                    grad,
                    start: CGPoint(x: cl, y: ct),
                    end: CGPoint(x: cl + cellPx, y: ct + cellPx),
                    options: []
                )
            } else {
                UIColor(argb: ColorMath.withAlpha(baseColor, alpha)).setFill()
                bp.fill()
            }
            ctx.restoreGState()

            // Granular inner speckles
            UIColor(white: 1.0, alpha: CGFloat(alpha) / 255.0 * 0.28).setFill()
            UIBezierPath(arcCenter: CGPoint(x: cl + cellPx * 0.30, y: ct + cellPx * 0.32), radius: cellPx * 0.07, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
            UIBezierPath(arcCenter: CGPoint(x: cl + cellPx * 0.68, y: ct + cellPx * 0.58), radius: cellPx * 0.06, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()

            UIColor(white: 0.0, alpha: CGFloat(alpha) / 255.0 * 0.22).setFill()
            UIBezierPath(arcCenter: CGPoint(x: cl + cellPx * 0.55, y: ct + cellPx * 0.28), radius: cellPx * 0.06, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
            UIBezierPath(arcCenter: CGPoint(x: cl + cellPx * 0.36, y: ct + cellPx * 0.72), radius: cellPx * 0.07, startAngle: 0, endAngle: .pi * 2, clockwise: true).fill()
        }

        if piece.kind == .BOMB {
            let cx = startX + CGFloat(piece.shape.cols) * cellPx * 0.5
            let cy = startY + CGFloat(piece.shape.rows) * cellPx * 0.5
            drawCenteredText("💣", centerX: cx, centerY: cy, fontSize: cellPx * 0.68, color: UIColor(white: 1, alpha: CGFloat(alpha) / 255.0))
        }
    }

    // MARK: - Touch Handling (Zero-Latency Multi-Mode Input)

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !isGamePaused, world.status == .playing, let touch = touches.first else { return }
        let pt = touch.location(in: self)

        // 1. Target Booster tap on board
        if let currentBooster = activeTargetBooster {
            if boardRect.contains(pt) {
                let gx = min(world.width - 1, max(0, Int((pt.x - boardRect.minX) / boardRect.width * CGFloat(world.width))))
                let gy = min(world.height - 1, max(0, Int((pt.y - boardRect.minY) / boardRect.height * CGFloat(world.height))))

                let success: Bool
                switch currentBooster {
                case .bomb:
                    world.detonateBomb(centerX: gx, centerY: gy)
                    success = true
                case .rowLaser:
                    world.fireHorizontalLaser(centerY: gy)
                    success = true
                case .colorCleaner:
                    success = world.clearAllSandOfColorAt(gridX: gx, gridY: gy)
                case .rainbowUpgrade, .rerollTray:
                    success = false
                }

                if success {
                    activeTargetBooster = nil
                    onTargetBoosterExecuted?(currentBooster)
                } else {
                    spawnPopup("Burada renkli kum yok!", x: pt.x, y: pt.y - 20, color: 0xFFFF6B6B)
                }
                setNeedsDisplay()
            }
            return
        }

        // 2. Sandtrix Touch & Button Controls
        if world.mode == .sandtrix {
            handleSandtrixBegan(pt: pt, timestamp: touch.timestamp)
            return
        }

        // 3. Drag & Drop + Hold Slot
        let slot: Int
        if holdCardRect.contains(pt) && world.holdPiece != nil {
            slot = Self.holdDragSlot
        } else if let trayIdx = (0...2).first(where: { trayCardRects[$0].contains(pt) && world.trayPieces[$0] != nil }) {
            slot = trayIdx
        } else {
            slot = -1
        }

        if slot != -1 {
            draggingSlot = slot
            downPoint = pt
            touchPoint = pt
            isDraggingMoved = false
            downTimestamp = touch.timestamp
            soundEngine.playTap()
            setNeedsDisplay()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard !isGamePaused, world.status == .playing, let touch = touches.first else { return }
        let pt = touch.location(in: self)

        if activeTargetBooster != nil { return }

        if world.mode == .sandtrix {
            handleSandtrixMoved(pt: pt)
            return
        }

        if draggingSlot != -1 {
            touchPoint = pt
            if !isDraggingMoved && hypot(pt.x - downPoint.x, pt.y - downPoint.y) > touchSlop {
                isDraggingMoved = true
            }
            setNeedsDisplay()
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let pt = touch.location(in: self)

        if world.mode == .sandtrix {
            handleSandtrixEnded(pt: pt, timestamp: touch.timestamp)
            return
        }

        guard draggingSlot != -1 else { return }
        let slot = draggingSlot
        let piece = activeDraggedPiece()
        let elapsed = touch.timestamp - downTimestamp

        if !isDraggingMoved && elapsed < 0.32 {
            let rotated = (slot == Self.holdDragSlot)
                ? world.rotateHoldPiece()
                : world.rotateTrayPiece(slot)
            if rotated { soundEngine.playTap() }
        } else if (0...2).contains(slot) && isDraggingMoved && holdCardRect.contains(pt) {
            if world.holdOrSwapTrayPiece(slot) {
                soundEngine.playTap()
                dirtyForAutoSave = true
            }
        } else if let piece = piece, isDraggingMoved {
            let grainPx = boardRect.width / CGFloat(world.width)
            let cellPx = grainPx * CGFloat(world.grainScale)
            let liftCenterY = pt.y - 76
            let liftLeftX = pt.x - (CGFloat(piece.shape.cols) * cellPx * 0.5)
            let liftTopY = liftCenterY - (CGFloat(piece.shape.rows) * cellPx * 0.5)

            if liftCenterY <= boardRect.maxY + 22 &&
                pt.x >= boardRect.minX - 28 &&
                pt.x <= boardRect.maxX + 28 {
                let rawGridX = Int(((liftLeftX - boardRect.minX) / grainPx).rounded(.towardZero))
                let rawGridY = Int(((liftTopY - boardRect.minY) / grainPx).rounded(.towardZero))
                if slot == Self.holdDragSlot {
                    world.placeHoldPiece(rawX: rawGridX, rawY: rawGridY)
                } else {
                    world.placeTrayPiece(slot: slot, rawX: rawGridX, rawY: rawGridY)
                }
            }
        }

        draggingSlot = -1
        isDraggingMoved = false
        setNeedsDisplay()
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        draggingSlot = -1
        isDraggingMoved = false
        stxBoardTracking = false
        setNeedsDisplay()
    }

    private func handleSandtrixBegan(pt: CGPoint, timestamp: TimeInterval) {
        if stxHoldRect.contains(pt) {
            if world.swapSandtrixHold() { soundEngine.playTap() }
            setNeedsDisplay()
        } else if stxLeftRect.contains(pt) {
            if world.moveFallingHorizontal(-4) { soundEngine.playTap() }
            setNeedsDisplay()
        } else if stxRightRect.contains(pt) {
            if world.moveFallingHorizontal(4) { soundEngine.playTap() }
            setNeedsDisplay()
        } else if stxRotRect.contains(pt) {
            if world.rotateFallingPiece() { soundEngine.playTap() }
            setNeedsDisplay()
        } else if stxDropRect.contains(pt) {
            world.hardDropFallingPiece()
            setNeedsDisplay()
        } else if boardRect.contains(pt) {
            stxBoardTracking = true
            downPoint = pt
            stxLastStepX = pt.x
            stxSwipeConsumedDrop = false
            downTimestamp = timestamp
        }
    }

    private func handleSandtrixMoved(pt: CGPoint) {
        guard stxBoardTracking, !stxSwipeConsumedDrop else { return }
        let stepPx = (boardRect.width / CGFloat(world.width)) * 4.0
        let dx = pt.x - stxLastStepX
        if abs(dx) >= stepPx {
            let steps = Int((dx / stepPx).rounded(.towardZero))
            if steps != 0 {
                world.moveFallingHorizontal(steps * 4)
                stxLastStepX += CGFloat(steps) * stepPx
                setNeedsDisplay()
            }
        }
        let dy = pt.y - downPoint.y
        if dy > 58 && dy > abs(pt.x - downPoint.x) * 1.5 {
            stxSwipeConsumedDrop = true
            world.hardDropFallingPiece()
            setNeedsDisplay()
        }
    }

    private func handleSandtrixEnded(pt: CGPoint, timestamp: TimeInterval) {
        guard stxBoardTracking else { return }
        let elapsed = timestamp - downTimestamp
        if !stxSwipeConsumedDrop && hypot(pt.x - downPoint.x, pt.y - downPoint.y) < touchSlop && elapsed < 0.28 {
            if world.rotateFallingPiece() { soundEngine.playTap() }
            setNeedsDisplay()
        }
        stxBoardTracking = false
    }

    // MARK: - Particles, Shockwaves, Lasers & Text Helpers

    private func gridToScreenX(_ gx: Float) -> CGFloat {
        boardRect.minX + (CGFloat(gx) / CGFloat(world.width)) * boardRect.width
    }

    private func gridToScreenY(_ gy: Float) -> CGFloat {
        boardRect.minY + (CGFloat(gy) / CGFloat(world.height)) * boardRect.height
    }

    private func spawnBurst(cx: CGFloat, cy: CGFloat, baseColor: UInt32, count: Int, speed: CGFloat) {
        var spawned = 0
        for i in 0..<particles.count where !particles[i].active {
            let angle = CGFloat(fxRng.nextFloat()) * .pi * 2.0
            let spd = speed * CGFloat(0.25 + fxRng.nextFloat() * 0.85)
            particles[i].active = true
            particles[i].isRisingEmber = false
            particles[i].x = cx + CGFloat(fxRng.nextFloat() * 14.0 - 7.0)
            particles[i].y = cy + CGFloat(fxRng.nextFloat() * 14.0 - 7.0)
            particles[i].vx = cos(angle) * spd
            particles[i].vy = sin(angle) * spd - 55.0
            particles[i].size = CGFloat(2.2 + fxRng.nextFloat() * 3.4)
            particles[i].color = ColorMath.shade(baseColor, fxRng.nextInt(in: -25..<35))
            particles[i].maxLife = 0.42 + fxRng.nextFloat() * 0.38
            particles[i].life = particles[i].maxLife
            spawned += 1
            if spawned >= count { break }
        }
    }

    private func spawnFeverEmber() {
        guard boardRect.width > 0, let idx = particles.firstIndex(where: { !$0.active }) else { return }
        particles[idx].active = true
        particles[idx].isRisingEmber = true
        particles[idx].x = boardRect.minX + CGFloat(fxRng.nextFloat()) * boardRect.width
        particles[idx].y = boardRect.maxY - 4
        particles[idx].vx = CGFloat(fxRng.nextFloat() - 0.5) * 34.0
        particles[idx].vy = -CGFloat(75.0 + fxRng.nextFloat() * 90.0)
        particles[idx].size = CGFloat(2.5 + fxRng.nextFloat() * 3.0)
        particles[idx].color = fxRng.nextBool() ? 0xFFFFB703 : 0xFFFF5400
        particles[idx].maxLife = 0.70 + fxRng.nextFloat() * 0.45
        particles[idx].life = particles[idx].maxLife
    }

    private func spawnPopup(_ text: String, x: CGFloat, y: CGFloat, color: UInt32) {
        let idx = popups.firstIndex(where: { !$0.active }) ?? 0
        popups[idx].text = text
        popups[idx].x = min(boardRect.maxX - 58, max(boardRect.minX + 58, x))
        popups[idx].y = min(boardRect.maxY - 24, max(boardRect.minY + 36, y))
        popups[idx].color = color
        popups[idx].maxLife = 1.10
        popups[idx].life = 1.10
        popups[idx].active = true
    }

    private func spawnShockwave(x: CGFloat, y: CGFloat, maxR: CGFloat, color: UInt32) {
        let idx = shockwaves.firstIndex(where: { !$0.active }) ?? 0
        shockwaves[idx] = ShockwaveRing(x: x, y: y, maxRadius: maxR, color: color, life: 0.38, active: true)
    }

    private func updateVisualEffects(dt: Float) {
        if shakeTrauma > 0 {
            shakeTrauma = max(0, shakeTrauma - 2.6 * dt)
        }

        if world.isFeverActive && !isGamePaused {
            emberSpawnTimer += dt
            while emberSpawnTimer >= 0.045 {
                emberSpawnTimer -= 0.045
                spawnFeverEmber()
            }
        }

        let gravity: CGFloat = 460.0
        let cgDt = CGFloat(dt)
        for i in 0..<particles.count where particles[i].active {
            particles[i].life -= dt
            if particles[i].life <= 0 {
                particles[i].active = false
            } else {
                if !particles[i].isRisingEmber {
                    particles[i].vy += gravity * cgDt
                }
                particles[i].x += particles[i].vx * cgDt
                particles[i].y += particles[i].vy * cgDt
            }
        }

        for i in 0..<popups.count where popups[i].active {
            popups[i].life -= dt
            if popups[i].life <= 0 {
                popups[i].active = false
            } else {
                popups[i].y -= 36.0 * cgDt
            }
        }

        for i in 0..<shockwaves.count where shockwaves[i].active {
            shockwaves[i].life -= dt
            if shockwaves[i].life <= 0 { shockwaves[i].active = false }
        }

        for i in 0..<laserBeams.count where laserBeams[i].active {
            laserBeams[i].life -= dt
            if laserBeams[i].life <= 0 { laserBeams[i].active = false }
        }
    }

    private func drawEffects(ctx: CGContext) {
        for beam in laserBeams where beam.active {
            let ratio = CGFloat(min(1.0, max(0.0, beam.life / 0.36)))
            let outerHalf = 14.0 * ratio
            let innerHalf = 4.5 * ratio
            UIColor(argb: ColorMath.withAlpha(0xFF00F5D4, Int(ratio * 150))).setFill()
            ctx.fill(CGRect(x: boardRect.minX, y: beam.y - outerHalf, width: boardRect.width, height: outerHalf * 2))
            UIColor(white: 1.0, alpha: ratio * 0.96).setFill()
            ctx.fill(CGRect(x: boardRect.minX, y: beam.y - innerHalf, width: boardRect.width, height: innerHalf * 2))
        }

        for ring in shockwaves where ring.active {
            let progress = CGFloat(1.0 - min(1.0, max(0.0, ring.life / 0.38)))
            let r = ring.maxRadius * progress
            let a = min(255, max(0, Int((1.0 - progress) * 210.0)))
            let bp = UIBezierPath(arcCenter: CGPoint(x: ring.x, y: ring.y), radius: r, startAngle: 0, endAngle: .pi * 2, clockwise: true)
            bp.lineWidth = 5.0 * (1.0 - progress) + 1.5
            UIColor(argb: ColorMath.withAlpha(ring.color, a)).setStroke()
            bp.stroke()
        }

        for p in particles where p.active {
            let alpha = min(255, max(0, Int((p.life / p.maxLife) * 255.0)))
            UIColor(argb: ColorMath.withAlpha(p.color, alpha)).setFill()
            let half = p.size * 0.5
            ctx.fill(CGRect(x: p.x - half, y: p.y - half, width: p.size, height: p.size))
        }

        for pop in popups where pop.active {
            let ratio = min(1.0, max(0.0, pop.life / pop.maxLife))
            let alpha = min(255, max(0, Int(ratio * 255.0)))
            drawCenteredText(
                pop.text,
                centerX: pop.x,
                centerY: pop.y,
                fontSize: 14.5,
                weight: .heavy,
                color: UIColor(argb: ColorMath.withAlpha(pop.color, alpha)),
                shadow: true
            )
        }
    }

    private func drawCenteredText(
        _ text: String,
        centerX: CGFloat,
        centerY: CGFloat,
        fontSize: CGFloat,
        weight: UIFont.Weight = .bold,
        color: UIColor,
        shadow: Bool = false
    ) {
        let font = UIFont.systemFont(ofSize: fontSize, weight: weight)
        var attrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: color
        ]
        if shadow {
            let sh = NSShadow()
            sh.shadowColor = UIColor.black.withAlphaComponent(0.78)
            sh.shadowOffset = CGSize(width: 1.4, height: 1.4)
            sh.shadowBlurRadius = 1.5
            attrs[.shadow] = sh
        }
        let ns = text as NSString
        let sz = ns.size(withAttributes: attrs)
        ns.draw(at: CGPoint(x: centerX - sz.width * 0.5, y: centerY - sz.height * 0.5), withAttributes: attrs)
    }

    private func drawRightText(
        _ text: String,
        rightX: CGFloat,
        centerY: CGFloat,
        fontSize: CGFloat,
        color: UIColor
    ) {
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
            .foregroundColor: color
        ]
        let ns = text as NSString
        let sz = ns.size(withAttributes: attrs)
        ns.draw(at: CGPoint(x: rightX - sz.width, y: centerY - sz.height * 0.5), withAttributes: attrs)
    }
}
