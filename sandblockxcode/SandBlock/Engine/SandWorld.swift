import Foundation

enum WorldEvent: Equatable {
    case piecePlaced(centerX: Float, centerY: Float, colorArgb: UInt32, isRainbow: Bool, isLava: Bool)
    case lineClearStarted(
        combo: Int,
        grainCount: Int,
        crackedBlocksBroken: Int,
        centerX: Float,
        centerY: Float,
        colorArgb: UInt32,
        pointsAwarded: Int,
        coinsAwarded: Int,
        feverBonusActive: Bool
    )
    case bombExploded(centerX: Float, centerY: Float, destroyedGrains: Int, pointsAwarded: Int)
    case laserFired(centerY: Float, destroyedGrains: Int, pointsAwarded: Int)
    case colorCleaned(colorIndex: Int, colorArgb: UInt32, destroyedGrains: Int, pointsAwarded: Int)
    case sandStormCleared(topY: Float, destroyedGrains: Int, pointsAwarded: Int)
    case lavaMelted(centerX: Float, centerY: Float, meltedCount: Int, pointsAwarded: Int)
    case feverStarted
    case timeBonusAdded(bonusSeconds: Float)
    case revived
    case levelWon(stars: Int, finalScore: Int)
    case gameLost
}

enum GameStatus: Equatable {
    case playing
    case won
    case gameOver
}

final class SandWorld {
    static let cellEmpty: UInt8 = 0
    static let cellSand: UInt8 = 1
    static let cellRainbow: UInt8 = 2
    static let cellStone: UInt8 = 3
    static let cellCrackedStone: UInt8 = 4
    static let cellIce: UInt8 = 5
    static let cellThinIce: UInt8 = 6
    static let cellLava: UInt8 = 7

    static let clearFlashDuration: Int = 13 // ~215ms at 60fps
    static let bombRadius: Int = 18
    static let feverDurationSec: Float = 10.0
    static let timeRushInitialSec: Float = 90.0
    private static let lavaInitialFuel: Int8 = 3

    let width: Int
    let height: Int
    let grainScale: Int
    let dangerRow: Int
    let size: Int

    var cellTypes: [UInt8]
    var colorIndices: [UInt8]
    var shades: [Int8]
    var lavaFuel: [Int8]
    private var lavaRestTicks: [Int8]
    var clearMask: [Bool]

    private var visited: [Bool]
    private var bfsQueue: [Int]
    private var pendingEvents: [WorldEvent] = []

    private var rng: SeededRNG
    private let pieceGenerator: PieceGenerator

    var trayPieces: [TrayPiece?] = [nil, nil, nil]
    private(set) var holdPiece: TrayPiece?

    private(set) var mode: GameMode = .classic
    private(set) var adventureLevel: AdventureLevel?
    var theme: GameTheme = .sahara

    private(set) var score: Int = 0
    private(set) var linesCleared: Int = 0
    private(set) var combo: Int = 0
    private(set) var maxCombo: Int = 0
    private(set) var movesUsed: Int = 0
    private var bonusMovesGranted: Int = 0
    private(set) var status: GameStatus = .playing

    // Fever state
    private(set) var feverCharge: Float = 0
    private(set) var feverRemainingSec: Float = 0
    var isFeverActive: Bool { feverRemainingSec > 0 }

    // Time Rush state
    private(set) var timeRemainingSec: Float = SandWorld.timeRushInitialSec

    // Sandtrix Real-Time Falling Block state
    private(set) var fallingPiece: TrayPiece?
    private(set) var nextFallingPiece: TrayPiece?
    private(set) var fallingX: Int = 0
    private(set) var fallingY: Float = 0
    private(set) var canSwapHoldThisDrop: Bool = true
    private var fallingLockTimer: Float = 0

    private(set) var tickCount: Int = 0
    private(set) var clearFlashTimer: Int = 0
    private var settleQuietTicks: Int = 0

    private var lavaMeltedAccumCount: Int = 0
    private var lavaMeltedAccumPoints: Int = 0
    private var lavaLastMeltX: Float = 0
    private var lavaLastMeltY: Float = 0

    var isBoardQuiet: Bool {
        settleQuietTicks >= 4 && clearFlashTimer == 0
    }

    var activeColorCount: Int {
        if let adv = adventureLevel {
            return adv.activeColors
        }
        if score >= 4800 { return 5 }
        if score >= 1500 { return 4 }
        return 3
    }

    var movesRemaining: Int? {
        guard let lvl = adventureLevel, lvl.maxMoves > 0 else { return nil }
        return max(0, lvl.maxMoves + bonusMovesGranted - movesUsed)
    }

    init(
        width: Int = 72,
        height: Int = 112,
        grainScale: Int = 8,
        dangerRow: Int = 18,
        seed: UInt64? = nil
    ) {
        self.width = width
        self.height = height
        self.grainScale = grainScale
        self.dangerRow = dangerRow
        self.size = width * height

        self.cellTypes = [UInt8](repeating: 0, count: size)
        self.colorIndices = [UInt8](repeating: 0, count: size)
        self.shades = [Int8](repeating: 0, count: size)
        self.lavaFuel = [Int8](repeating: 0, count: size)
        self.lavaRestTicks = [Int8](repeating: 0, count: size)
        self.clearMask = [Bool](repeating: false, count: size)
        self.visited = [Bool](repeating: false, count: size)
        self.bfsQueue = [Int](repeating: 0, count: size)

        let seeded = SeededRNG(seed: seed)
        self.rng = seeded
        self.pieceGenerator = PieceGenerator(rng: SeededRNG(seed: seed.map { $0 &+ 0xABCDEF }))
        self.lavaLastMeltX = Float(width) * 0.5
        self.lavaLastMeltY = Float(height) * 0.5
    }

    func drainEvents() -> [WorldEvent] {
        guard !pendingEvents.isEmpty else { return [] }
        let copy = pendingEvents
        pendingEvents.removeAll(keepingCapacity: true)
        return copy
    }

    func startClassic(_ selectedTheme: GameTheme) {
        startMode(.classic, selectedTheme: selectedTheme)
    }

    func startMode(_ gameMode: GameMode, selectedTheme: GameTheme) {
        mode = (gameMode == .adventure) ? .classic : gameMode
        theme = selectedTheme
        adventureLevel = nil
        resetBoardState()
        if mode == .timeRush {
            timeRemainingSec = Self.timeRushInitialSec
        }
        if mode == .sandtrix {
            nextFallingPiece = pieceGenerator.nextSinglePiece(activeColors: activeColorCount, allowSpecialPieces: false)
            spawnNextFallingPiece()
        } else {
            refillTray()
        }
    }

    func startAdventure(_ level: AdventureLevel, selectedThemeOverride: GameTheme? = nil) {
        mode = .adventure
        adventureLevel = level
        theme = selectedThemeOverride ?? level.theme
        resetBoardState()
        applyTerrainPreset(level.terrainPreset, colors: level.activeColors)
        refillTray()
    }

    private func resetBoardState() {
        for i in 0..<size {
            cellTypes[i] = Self.cellEmpty
            colorIndices[i] = 0
            shades[i] = 0
            lavaFuel[i] = 0
            lavaRestTicks[i] = 0
            clearMask[i] = false
        }
        pendingEvents.removeAll(keepingCapacity: true)
        pieceGenerator.reset()
        trayPieces = [nil, nil, nil]
        holdPiece = nil

        score = 0
        linesCleared = 0
        combo = 0
        maxCombo = 0
        movesUsed = 0
        bonusMovesGranted = 0
        status = .playing

        feverCharge = 0
        feverRemainingSec = 0
        timeRemainingSec = Self.timeRushInitialSec

        fallingPiece = nil
        nextFallingPiece = nil
        fallingX = 0
        fallingY = 0
        canSwapHoldThisDrop = true
        fallingLockTimer = 0

        tickCount = 0
        clearFlashTimer = 0
        settleQuietTicks = 0
        lavaMeltedAccumCount = 0
        lavaMeltedAccumPoints = 0
    }

    private func refillTray() {
        let next = pieceGenerator.nextTray(activeColors: activeColorCount, allowSpecialPieces: true)
        for i in 0..<3 {
            trayPieces[i] = next[i]
        }
    }

    // MARK: - Tray & Hold Controls

    @discardableResult
    func rotateTrayPiece(_ slot: Int) -> Bool {
        guard (0...2).contains(slot), status == .playing, let piece = trayPieces[slot] else { return false }
        if piece.kind == .BOMB { return false }
        trayPieces[slot] = piece.rotated()
        return true
    }

    @discardableResult
    func rotateHoldPiece() -> Bool {
        guard status == .playing, let piece = holdPiece else { return false }
        if piece.kind == .BOMB { return false }
        holdPiece = piece.rotated()
        return true
    }

    @discardableResult
    func holdOrSwapTrayPiece(_ slot: Int) -> Bool {
        guard (0...2).contains(slot), status == .playing, mode != .sandtrix else { return false }
        guard let incoming = trayPieces[slot] else { return false }
        let previousHold = holdPiece
        holdPiece = incoming
        trayPieces[slot] = previousHold
        if trayPieces.allSatisfy({ $0 == nil }) {
            refillTray()
        }
        return true
    }

    @discardableResult
    func placeHoldPiece(rawX: Int, rawY: Int) -> Bool {
        guard status == .playing, mode != .sandtrix, let piece = holdPiece else { return false }
        guard let placement = findValidPlacement(piece: piece, rawX: rawX, rawY: rawY) else { return false }
        holdPiece = nil
        commitPieceToBoard(piece, originX: placement.0, originY: placement.1)
        return true
    }

    @discardableResult
    func upgradeTrayPieceToRainbow() -> Int {
        guard status == .playing else { return -1 }
        if mode == .sandtrix {
            if let fp = fallingPiece, fp.kind != .RAINBOW {
                fallingPiece = pieceGenerator.upgradePieceToRainbow(fp)
                return 0
            }
            if let np = nextFallingPiece, np.kind != .RAINBOW {
                nextFallingPiece = pieceGenerator.upgradePieceToRainbow(np)
                return 1
            }
            return -1
        }

        let normalSlot = (0...2).first { slot in
            if let p = trayPieces[slot] { return p.kind == .NORMAL }
            return false
        }
        let fallbackSlot = (0...2).first { slot in
            if let p = trayPieces[slot] { return p.kind != .RAINBOW }
            return false
        }
        guard let targetSlot = normalSlot ?? fallbackSlot, let current = trayPieces[targetSlot] else {
            return -1
        }
        trayPieces[targetSlot] = pieceGenerator.upgradePieceToRainbow(current)
        return targetSlot
    }

    @discardableResult
    func rerollTray() -> Bool {
        guard status == .playing else { return false }
        if mode == .sandtrix {
            nextFallingPiece = pieceGenerator.nextSinglePiece(activeColors: activeColorCount, allowSpecialPieces: true)
            spawnNextFallingPiece()
            return true
        }
        refillTray()
        return true
    }

    // MARK: - Sandtrix Real-Time Falling Block Controls

    private func spawnNextFallingPiece() {
        let next = nextFallingPiece ?? pieceGenerator.nextSinglePiece(activeColors: activeColorCount, allowSpecialPieces: true)
        nextFallingPiece = pieceGenerator.nextSinglePiece(activeColors: activeColorCount, allowSpecialPieces: true)
        let pieceW = next.shape.cols * grainScale
        let halfStep = max(1, grainScale / 2)
        let spawnX = (((width - pieceW) / 2) / halfStep) * halfStep
        let spawnY = 2
        fallingPiece = next
        fallingX = min(max(0, width - pieceW), max(0, spawnX))
        fallingY = Float(spawnY)
        fallingLockTimer = 0
        canSwapHoldThisDrop = true

        if next.kind != .BOMB && !canPlaceShape(next.shape, originX: fallingX, originY: spawnY) {
            status = .gameOver
            pendingEvents.append(.gameLost)
        }
    }

    @discardableResult
    func swapSandtrixHold() -> Bool {
        guard mode == .sandtrix, status == .playing, canSwapHoldThisDrop, let current = fallingPiece else {
            return false
        }
        let prevHold = holdPiece
        holdPiece = current
        if let prev = prevHold {
            let pieceW = prev.shape.cols * grainScale
            fallingPiece = prev
            fallingX = min(max(0, width - pieceW), max(0, (width - pieceW) / 2))
            fallingY = 2
            fallingLockTimer = 0
        } else {
            spawnNextFallingPiece()
        }
        canSwapHoldThisDrop = false
        return true
    }

    @discardableResult
    func moveFallingHorizontal(_ deltaGrains: Int) -> Bool {
        guard mode == .sandtrix, status == .playing, let piece = fallingPiece else { return false }
        let pieceW = piece.shape.cols * grainScale
        let targetX = min(max(0, width - pieceW), max(0, fallingX + deltaGrains))
        if targetX == fallingX { return false }

        let intY = Int(fallingY)
        if piece.kind == .BOMB || canPlaceShape(piece.shape, originX: targetX, originY: intY) {
            fallingX = targetX
            return true
        }
        if intY >= 2 && canPlaceShape(piece.shape, originX: targetX, originY: intY - 2) {
            fallingX = targetX
            fallingY = Float(intY - 2)
            return true
        }
        return false
    }

    @discardableResult
    func rotateFallingPiece() -> Bool {
        guard mode == .sandtrix, status == .playing, let piece = fallingPiece else { return false }
        if piece.kind == .BOMB { return false }
        let rotated = piece.rotated()
        let rotW = rotated.shape.cols * grainScale
        let intY = Int(fallingY)
        let kicks = [0, -4, 4, -grainScale, grainScale]
        for dx in kicks {
            let testX = min(max(0, width - rotW), max(0, fallingX + dx))
            if canPlaceShape(rotated.shape, originX: testX, originY: intY) {
                fallingPiece = rotated
                fallingX = testX
                return true
            }
        }
        return false
    }

    func computeFallingGhostY() -> Int {
        guard let piece = fallingPiece else { return 0 }
        let pieceH = piece.shape.rows * grainScale
        var testY = max(0, Int(fallingY))
        if piece.kind == .BOMB {
            let cx = min(width - 1, max(0, fallingX + (piece.shape.cols * grainScale) / 2))
            while testY + pieceH < height && cellTypes[(testY + pieceH) * width + cx] == Self.cellEmpty {
                testY += 1
            }
            return testY
        }
        while testY + 1 <= height - pieceH && canPlaceShape(piece.shape, originX: fallingX, originY: testY + 1) {
            testY += 1
        }
        return testY
    }

    @discardableResult
    func hardDropFallingPiece() -> Bool {
        guard mode == .sandtrix, status == .playing, let piece = fallingPiece else { return false }
        let dropY = computeFallingGhostY()
        let droppedDist = max(0, dropY - Int(fallingY))
        score += max(6, droppedDist / 2)
        fallingPiece = nil
        commitPieceToBoard(piece, originX: fallingX, originY: dropY)
        if status == .playing {
            spawnNextFallingPiece()
        }
        return true
    }

    private func updateSandtrixFalling(dt: Float) {
        guard mode == .sandtrix, status == .playing, clearFlashTimer == 0 else { return }
        guard let piece = fallingPiece else {
            spawnNextFallingPiece()
            return
        }

        let pieceH = piece.shape.rows * grainScale
        let speedGrainsPerSec: Float = 15.0 + min(24.0, Float(score) / 700.0)
        let desiredY = fallingY + speedGrainsPerSec * dt
        var curIntY = Int(fallingY)
        let targetIntY = Int(desiredY)

        var blocked = false
        while curIntY < targetIntY {
            let nextY = curIntY + 1
            let canMove: Bool
            if piece.kind == .BOMB {
                let cx = min(width - 1, max(0, fallingX + (piece.shape.cols * grainScale) / 2))
                canMove = nextY + pieceH <= height && cellTypes[(nextY + pieceH - 1) * width + cx] == Self.cellEmpty
            } else {
                canMove = nextY + pieceH <= height && canPlaceShape(piece.shape, originX: fallingX, originY: nextY)
            }
            if canMove {
                curIntY = nextY
            } else {
                blocked = true
                break
            }
        }

        if !blocked {
            let resting: Bool
            if piece.kind == .BOMB {
                let cx = min(width - 1, max(0, fallingX + (piece.shape.cols * grainScale) / 2))
                resting = curIntY + pieceH >= height || cellTypes[(curIntY + pieceH) * width + cx] != Self.cellEmpty
            } else {
                resting = curIntY + pieceH >= height || !canPlaceShape(piece.shape, originX: fallingX, originY: curIntY + 1)
            }
            fallingY = resting ? Float(curIntY) : desiredY
            if resting {
                fallingLockTimer += dt
            } else {
                fallingLockTimer = 0
            }
        } else {
            fallingY = Float(curIntY)
            fallingLockTimer += dt
        }

        if fallingLockTimer >= 0.22 {
            fallingPiece = nil
            fallingLockTimer = 0
            commitPieceToBoard(piece, originX: fallingX, originY: curIntY)
            if status == .playing {
                spawnNextFallingPiece()
            }
        }
    }

    // MARK: - Board Placement & Spawning

    func canPlaceShape(_ shape: BlockShape, originX: Int, originY: Int) -> Bool {
        let pieceW = shape.cols * grainScale
        let pieceH = shape.rows * grainScale
        if originX < 0 || originY < 0 || originX + pieceW > width || originY + pieceH > height {
            return false
        }
        for cell in shape.cells {
            let startX = originX + cell.col * grainScale
            let startY = originY + cell.row * grainScale
            for gy in startY..<(startY + grainScale) {
                let rowBase = gy * width
                for gx in startX..<(startX + grainScale) {
                    if cellTypes[rowBase + gx] != Self.cellEmpty {
                        return false
                    }
                }
            }
        }
        return true
    }

    func findValidPlacement(piece: TrayPiece, rawX: Int, rawY: Int) -> (Int, Int)? {
        let pieceW = piece.shape.cols * grainScale
        let pieceH = piece.shape.rows * grainScale
        let clampedX = min(max(0, width - pieceW), max(0, rawX))
        let clampedY = min(max(0, height - pieceH), max(0, rawY))

        if piece.kind == .BOMB {
            return (clampedX, clampedY)
        }

        if canPlaceShape(piece.shape, originX: clampedX, originY: clampedY) {
            return (clampedX, clampedY)
        }

        for radius in 1...12 {
            let upY = clampedY - radius
            if upY >= 0 && canPlaceShape(piece.shape, originX: clampedX, originY: upY) {
                return (clampedX, upY)
            }
            for dx in (-radius)...radius where dx != 0 {
                let nx = clampedX + dx
                if nx >= 0 && nx <= (width - pieceW) {
                    if canPlaceShape(piece.shape, originX: nx, originY: clampedY) {
                        return (nx, clampedY)
                    }
                    if upY >= 0 && canPlaceShape(piece.shape, originX: nx, originY: upY) {
                        return (nx, upY)
                    }
                }
            }
        }
        return nil
    }

    @discardableResult
    func placeTrayPiece(slot: Int, rawX: Int, rawY: Int) -> Bool {
        guard (0...2).contains(slot), status == .playing, mode != .sandtrix else { return false }
        guard let piece = trayPieces[slot] else { return false }
        guard let placement = findValidPlacement(piece: piece, rawX: rawX, rawY: rawY) else { return false }
        trayPieces[slot] = nil

        commitPieceToBoard(piece, originX: placement.0, originY: placement.1)

        if trayPieces.allSatisfy({ $0 == nil }) {
            refillTray()
        }
        return true
    }

    private func commitPieceToBoard(_ piece: TrayPiece, originX: Int, originY: Int) {
        movesUsed += 1
        combo = 0
        settleQuietTicks = 0
        addFeverCharge(0.07)

        if piece.kind == .BOMB {
            let pieceW = piece.shape.cols * grainScale
            let pieceH = piece.shape.rows * grainScale
            let cx = min(width - 1, max(0, originX + pieceW / 2))
            var cy = min(height - 1, max(0, originY + pieceH / 2))
            while cy < height - 1 &&
                cellTypes[(cy + 1) * width + cx] == Self.cellEmpty &&
                cellTypes[cy * width + cx] == Self.cellEmpty {
                cy += 1
            }
            detonateBomb(centerX: cx, centerY: cy)
        } else {
            spawnPieceGrains(piece, originX: originX, originY: originY)
            let cx = Float(originX) + Float(piece.shape.cols * grainScale) * 0.5
            let cy = Float(originY) + Float(piece.shape.rows * grainScale) * 0.5
            let palette = theme.sandPalette
            let baseColor: UInt32
            switch piece.kind {
            case .RAINBOW:
                baseColor = 0xFFFFFFFF
            case .LAVA:
                baseColor = 0xFFFF5400
            case .NORMAL, .BOMB:
                baseColor = palette[piece.colorIndex % palette.count]
            }
            pendingEvents.append(
                .piecePlaced(
                    centerX: cx,
                    centerY: cy,
                    colorArgb: baseColor,
                    isRainbow: piece.kind == .RAINBOW,
                    isLava: piece.kind == .LAVA
                )
            )
        }
    }

    private func addFeverCharge(_ delta: Float) {
        if isFeverActive { return }
        feverCharge = min(1.0, max(0.0, feverCharge + delta))
        if feverCharge >= 1.0 {
            feverCharge = 0
            feverRemainingSec = Self.feverDurationSec
            pendingEvents.append(.feverStarted)
        }
    }

    func triggerFeverForTest() {
        feverCharge = 0
        feverRemainingSec = Self.feverDurationSec
    }

    func spawnPieceGrains(_ piece: TrayPiece, originX: Int, originY: Int) {
        let targetType: UInt8
        switch piece.kind {
        case .RAINBOW: targetType = Self.cellRainbow
        case .LAVA: targetType = Self.cellLava
        default: targetType = Self.cellSand
        }
        let cIdx = UInt8(min(4, max(0, piece.colorIndex)))

        for cell in piece.shape.cells {
            let startX = originX + cell.col * grainScale
            let startY = originY + cell.row * grainScale
            let endY = min(height, startY + grainScale)
            let endX = min(width, startX + grainScale)
            guard startY < endY, startX < endX else { continue }

            for gy in startY..<endY {
                let rowBase = gy * width
                let isTopEdge = (gy == startY)
                let isBottomEdge = (gy == startY + grainScale - 1)
                for gx in startX..<endX {
                    let idx = rowBase + gx
                    if cellTypes[idx] == Self.cellEmpty {
                        cellTypes[idx] = targetType
                        colorIndices[idx] = cIdx
                        if targetType == Self.cellLava {
                            lavaFuel[idx] = Self.lavaInitialFuel
                            lavaRestTicks[idx] = 0
                        }
                        let edgeBias = isTopEdge ? 10 : (isBottomEdge ? -10 : 0)
                        let jitter = rng.nextInt(in: -14..<15)
                        shades[idx] = Int8(min(28, max(-28, jitter + edgeBias)))
                    }
                }
            }
        }
    }

    // MARK: - Power-Ups & Special Interventions

    @discardableResult
    func pourLavaShower() -> Int {
        guard status == .playing else { return 0 }
        var spawned = 0
        let streamCols = [8, 22, 36, 50, 63]
        for centerCol in streamCols {
            for gy in 2...7 {
                for gx in (centerCol - 2)...(centerCol + 2) {
                    if gx >= 0 && gx < width && gy >= 0 && gy < height {
                        let idx = gy * width + gx
                        if cellTypes[idx] == Self.cellEmpty {
                            cellTypes[idx] = Self.cellLava
                            lavaFuel[idx] = 4
                            lavaRestTicks[idx] = 0
                            spawned += 1
                        }
                    }
                }
            }
        }
        if spawned > 0 {
            score += 120
            settleQuietTicks = 0
            pendingEvents.append(
                .piecePlaced(
                    centerX: Float(width) * 0.5,
                    centerY: 6.0,
                    colorArgb: 0xFFFF5400,
                    isRainbow: false,
                    isLava: true
                )
            )
        }
        return spawned
    }

    @discardableResult
    func detonateBomb(centerX: Int, centerY: Int, radius: Int = SandWorld.bombRadius) -> Int {
        guard status == .playing else { return 0 }
        var destroyed = 0
        let minX = max(0, centerX - radius)
        let maxX = min(width - 1, centerX + radius)
        let minY = max(0, centerY - radius)
        let maxY = min(height - 1, centerY + radius)
        let rSq = radius * radius

        var breakableMacrosHit = Set<Int>()

        if minY <= maxY && minX <= maxX {
            for y in minY...maxY {
                let dy = y - centerY
                let rowBase = y * width
                for x in minX...maxX {
                    let dx = x - centerX
                    if dx * dx + dy * dy <= rSq {
                        let idx = rowBase + x
                        let type = cellTypes[idx]
                        if type != Self.cellEmpty {
                            if isBreakableObstacle(type) {
                                breakableMacrosHit.insert((y / grainScale) * 100 + (x / grainScale))
                            }
                            clearSingleCell(idx)
                            destroyed += 1
                        }
                    }
                }
            }
        }

        for macroKey in breakableMacrosHit {
            destroyed += destroyBreakableMacroAt(macroCol: macroKey % 100, macroRow: macroKey / 100)
        }

        let points = (destroyed * 2 + 80) * (isFeverActive ? 2 : 1)
        score += points
        settleQuietTicks = 0
        pendingEvents.append(
            .bombExploded(
                centerX: Float(centerX),
                centerY: Float(centerY),
                destroyedGrains: destroyed,
                pointsAwarded: points
            )
        )
        return destroyed
    }

    @discardableResult
    func clearAllSandOfColorAt(gridX: Int, gridY: Int) -> Bool {
        guard status == .playing, (0..<width).contains(gridX), (0..<height).contains(gridY) else {
            return false
        }

        var foundColor: Int?
        searchLoop: for r in 0...5 {
            for dy in (-r)...r {
                let ny = gridY + dy
                guard (0..<height).contains(ny) else { continue }
                for dx in (-r)...r {
                    let nx = gridX + dx
                    guard (0..<width).contains(nx) else { continue }
                    let idx = ny * width + nx
                    if cellTypes[idx] == Self.cellSand {
                        foundColor = Int(colorIndices[idx])
                        break searchLoop
                    }
                }
            }
        }

        guard let target = foundColor else { return false }
        return clearAllOfColor(target) > 0
    }

    @discardableResult
    func clearAllOfColor(_ colorIndex: Int) -> Int {
        guard status == .playing else { return 0 }
        let target = UInt8(min(4, max(0, colorIndex)))
        var destroyed = 0
        for i in 0..<size {
            if cellTypes[i] == Self.cellSand && colorIndices[i] == target {
                clearSingleCell(i)
                destroyed += 1
            }
        }
        if destroyed > 0 {
            let pts = (destroyed * 2 + 100) * (isFeverActive ? 2 : 1)
            score += pts
            settleQuietTicks = 0
            let palette = theme.sandPalette
            let argb = palette[colorIndex % palette.count]
            pendingEvents.append(
                .colorCleaned(
                    colorIndex: colorIndex,
                    colorArgb: argb,
                    destroyedGrains: destroyed,
                    pointsAwarded: pts
                )
            )
        }
        return destroyed
    }

    @discardableResult
    func fireHorizontalLaser(centerY: Int, halfBand: Int = 5) -> Int {
        guard status == .playing else { return 0 }
        let minY = max(0, centerY - halfBand)
        let maxY = min(height - 1, centerY + halfBand)
        var destroyed = 0
        var breakableMacrosHit = Set<Int>()

        if minY <= maxY {
            for y in minY...maxY {
                let rowBase = y * width
                for x in 0..<width {
                    let idx = rowBase + x
                    let t = cellTypes[idx]
                    if t != Self.cellEmpty {
                        if isBreakableObstacle(t) {
                            breakableMacrosHit.insert((y / grainScale) * 100 + (x / grainScale))
                        }
                        clearSingleCell(idx)
                        destroyed += 1
                    }
                }
            }
        }

        for macroKey in breakableMacrosHit {
            destroyed += destroyBreakableMacroAt(macroCol: macroKey % 100, macroRow: macroKey / 100)
        }

        let pts = (destroyed * 2 + 95) * (isFeverActive ? 2 : 1)
        score += pts
        settleQuietTicks = 0
        pendingEvents.append(
            .laserFired(
                centerY: Float(min(height - 1, max(0, centerY))),
                destroyedGrains: destroyed,
                pointsAwarded: pts
            )
        )
        return destroyed
    }

    @discardableResult
    func clearTopDangerSand(rowsToClear: Int = 28) -> Int {
        guard status == .playing else { return 0 }
        let topRow = highestSandRow()
        if topRow >= height { return 0 }
        let endRow = min(height, topRow + rowsToClear)
        var destroyed = 0

        for y in topRow..<endRow {
            let rowBase = y * width
            for x in 0..<width {
                let idx = rowBase + x
                let t = cellTypes[idx]
                if t == Self.cellSand || t == Self.cellRainbow || t == Self.cellLava {
                    clearSingleCell(idx)
                    destroyed += 1
                }
            }
        }

        if destroyed > 0 {
            let pts = destroyed * 2 + 120
            score += pts
            settleQuietTicks = 0
            pendingEvents.append(
                .sandStormCleared(
                    topY: Float(topRow + endRow) * 0.5,
                    destroyedGrains: destroyed,
                    pointsAwarded: pts
                )
            )
        }
        return destroyed
    }

    @discardableResult
    func reviveContinue() -> Bool {
        guard status == .gameOver else { return false }
        let clearUntilRow = min(height, max(dangerRow + 6, Int(Float(height) * 0.58)))
        for y in 0..<clearUntilRow {
            let rBase = y * width
            for x in 0..<width {
                let idx = rBase + x
                let t = cellTypes[idx]
                if t == Self.cellSand || t == Self.cellRainbow || t == Self.cellLava {
                    clearSingleCell(idx)
                }
            }
        }
        if mode == .timeRush && timeRemainingSec < 35.0 {
            timeRemainingSec = 35.0
        }
        if mode == .adventure {
            bonusMovesGranted += 6
        }
        status = .playing
        settleQuietTicks = 0
        if mode == .sandtrix {
            fallingPiece = nil
            spawnNextFallingPiece()
        }
        pendingEvents.append(.revived)
        return true
    }

    // MARK: - Frame Update & Cellular Automata Physics

    func updateFrame(physicsSteps: Int = 4, dt: Float = 1.0 / 60.0) {
        guard status == .playing else { return }
        tickCount &+= 1

        if feverRemainingSec > 0 {
            feverRemainingSec = max(0, feverRemainingSec - dt)
        }

        if clearFlashTimer > 0 {
            clearFlashTimer -= 1
            if clearFlashTimer == 0 {
                finalizeMarkedClears()
            }
            return
        }

        if mode == .timeRush {
            timeRemainingSec = max(0, timeRemainingSec - dt)
            if timeRemainingSec <= 0 {
                status = .gameOver
                pendingEvents.append(.gameLost)
                return
            }
        }

        if mode == .sandtrix {
            updateSandtrixFalling(dt: dt)
            if status != .playing { return }
        }

        var verticalMoves = 0
        var totalMoves = 0
        if physicsSteps > 0 {
            for _ in 0..<physicsSteps {
                let stats = stepPhysicsOnce()
                totalMoves += stats.0
                verticalMoves += stats.1
            }
        }

        flushLavaEventIfReady(force: totalMoves == 0)

        if verticalMoves <= 10 {
            if detectAndBeginLineClear() {
                settleQuietTicks = 0
                return
            }
        }

        if totalMoves == 0 {
            settleQuietTicks += 1
            if settleQuietTicks >= 5 {
                evaluateEndConditions()
            }
        } else {
            settleQuietTicks = 0
        }
    }

    @discardableResult
    func stepPhysicsOnce() -> (Int, Int) {
        tickCount &+= 1
        var moved = 0
        var vertical = 0

        if height >= 2 {
            for y in stride(from: height - 2, through: 0, by: -1) {
                let rowBase = y * width
                let nextRowBase = (y + 1) * width
                let leftToRight = ((tickCount &+ y) & 1) == 0

                for stepX in 0..<width {
                    let x = leftToRight ? stepX : (width - 1 - stepX)
                    let idx = rowBase + x
                    let type = cellTypes[idx]
                    if type != Self.cellSand && type != Self.cellRainbow && type != Self.cellLava {
                        continue
                    }
                    if clearMask[idx] { continue }

                    if type == Self.cellLava {
                        if stepLavaCell(x: x, y: y, idx: idx, nextRowBase: nextRowBase) {
                            moved += 1
                            vertical += 1
                        }
                        continue
                    }

                    let belowIdx = nextRowBase + x
                    if cellTypes[belowIdx] == Self.cellEmpty {
                        moveCell(from: idx, to: belowIdx)
                        moved += 1
                        vertical += 1
                        continue
                    }

                    let dir1 = (((x &* 37 &+ y &* 13 &+ tickCount) & 1) == 0) ? -1 : 1
                    let dir2 = -dir1

                    if canSlideDiagonally(x: x, y: y, dx: dir1) {
                        moveCell(from: idx, to: nextRowBase + (x + dir1))
                        moved += 1
                    } else if canSlideDiagonally(x: x, y: y, dx: dir2) {
                        moveCell(from: idx, to: nextRowBase + (x + dir2))
                        moved += 1
                    }
                }
            }
        }

        let bottomBase = (height - 1) * width
        for x in 0..<width {
            let bIdx = bottomBase + x
            if cellTypes[bIdx] == Self.cellLava {
                if !tryLavaMeltSides(x: x, y: height - 1, lavaIdx: bIdx) {
                    let nextRest = Int(lavaRestTicks[bIdx]) + 1
                    if nextRest >= 28 {
                        clearSingleCell(bIdx)
                    } else {
                        lavaRestTicks[bIdx] = Int8(nextRest)
                    }
                }
            }
        }

        return (moved, vertical)
    }

    private func stepLavaCell(x: Int, y: Int, idx: Int, nextRowBase: Int) -> Bool {
        let belowIdx = nextRowBase + x
        let belowType = cellTypes[belowIdx]

        if belowType == Self.cellEmpty {
            moveCell(from: idx, to: belowIdx)
            return true
        }

        if belowType != Self.cellLava {
            meltTargetWithLava(targetX: x, targetY: y + 1, targetIdx: belowIdx, lavaIdx: idx, moveIntoTarget: true)
            return true
        }

        let dir1 = (((x &* 31 &+ y &* 17 &+ tickCount) & 1) == 0) ? -1 : 1
        let dir2 = -dir1
        for dx in [dir1, dir2] {
            let nx = x + dx
            guard (0..<width).contains(nx) else { continue }
            let diagIdx = nextRowBase + nx
            let diagType = cellTypes[diagIdx]
            if diagType == Self.cellEmpty {
                moveCell(from: idx, to: diagIdx)
                return true
            } else if diagType != Self.cellLava {
                meltTargetWithLava(targetX: nx, targetY: y + 1, targetIdx: diagIdx, lavaIdx: idx, moveIntoTarget: true)
                return true
            }
        }

        if tryLavaMeltSides(x: x, y: y, lavaIdx: idx) {
            return true
        }

        let rest = Int(lavaRestTicks[idx]) + 1
        if rest >= 28 {
            clearSingleCell(idx)
        } else {
            lavaRestTicks[idx] = Int8(rest)
        }
        return false
    }

    private func tryLavaMeltSides(x: Int, y: Int, lavaIdx: Int) -> Bool {
        for dx in [-1, 1] {
            let nx = x + dx
            if (0..<width).contains(nx) {
                let nIdx = y * width + nx
                let t = cellTypes[nIdx]
                if t != Self.cellEmpty && t != Self.cellLava {
                    meltTargetWithLava(targetX: nx, targetY: y, targetIdx: nIdx, lavaIdx: lavaIdx, moveIntoTarget: false)
                    return true
                }
            }
        }
        return false
    }

    private func meltTargetWithLava(
        targetX: Int,
        targetY: Int,
        targetIdx: Int,
        lavaIdx: Int,
        moveIntoTarget: Bool
    ) {
        let targetType = cellTypes[targetIdx]
        var meltedGrains = 1
        var pts = 3

        if isBreakableObstacle(targetType) {
            meltedGrains = max(1, destroyBreakableMacroAt(macroCol: targetX / grainScale, macroRow: targetY / grainScale))
            pts = 95
        } else {
            clearSingleCell(targetIdx)
        }

        score += pts
        lavaMeltedAccumCount += meltedGrains
        lavaMeltedAccumPoints += pts
        lavaLastMeltX = Float(targetX)
        lavaLastMeltY = Float(targetY)

        let remainingFuel = Int(lavaFuel[lavaIdx]) - (meltedGrains > 1 ? 2 : 1)
        if remainingFuel <= 0 {
            clearSingleCell(lavaIdx)
        } else {
            lavaFuel[lavaIdx] = Int8(remainingFuel)
            lavaRestTicks[lavaIdx] = 0
            if moveIntoTarget && cellTypes[targetIdx] == Self.cellEmpty {
                moveCell(from: lavaIdx, to: targetIdx)
            }
        }
    }

    private func flushLavaEventIfReady(force: Bool) {
        if lavaMeltedAccumCount >= 22 || (force && lavaMeltedAccumCount > 0) {
            pendingEvents.append(
                .lavaMelted(
                    centerX: lavaLastMeltX,
                    centerY: lavaLastMeltY,
                    meltedCount: lavaMeltedAccumCount,
                    pointsAwarded: lavaMeltedAccumPoints
                )
            )
            lavaMeltedAccumCount = 0
            lavaMeltedAccumPoints = 0
        }
    }

    private func isSolidObstacle(_ type: UInt8) -> Bool {
        type == Self.cellStone || type == Self.cellCrackedStone || type == Self.cellIce || type == Self.cellThinIce
    }

    private func isBreakableObstacle(_ type: UInt8) -> Bool {
        type == Self.cellCrackedStone || type == Self.cellIce || type == Self.cellThinIce
    }

    private func canSlideDiagonally(x: Int, y: Int, dx: Int) -> Bool {
        let nx = x + dx
        guard (0..<width).contains(nx) else { return false }
        let targetIdx = (y + 1) * width + nx
        if cellTypes[targetIdx] != Self.cellEmpty { return false }
        let sideType = cellTypes[y * width + nx]
        return !isSolidObstacle(sideType)
    }

    private func clearSingleCell(_ idx: Int) {
        cellTypes[idx] = Self.cellEmpty
        colorIndices[idx] = 0
        shades[idx] = 0
        lavaFuel[idx] = 0
        lavaRestTicks[idx] = 0
        clearMask[idx] = false
    }

    private func moveCell(from fromIdx: Int, to toIdx: Int) {
        cellTypes[toIdx] = cellTypes[fromIdx]
        colorIndices[toIdx] = colorIndices[fromIdx]
        shades[toIdx] = shades[fromIdx]
        lavaFuel[toIdx] = lavaFuel[fromIdx]
        lavaRestTicks[toIdx] = 0
        clearSingleCell(fromIdx)
    }

    // MARK: - Line Clear BFS & Obstacle Thawing/Shattering

    @discardableResult
    func detectAndBeginLineClear() -> Bool {
        for i in 0..<size { clearMask[i] = false }
        var anyBridge = false
        var dominantColorArgb = theme.accentColor
        var largestComponent = 0
        let palette = theme.sandPalette

        for color in 0..<5 {
            for i in 0..<size { visited[i] = false }
            let targetByte = UInt8(color)

            for startY in 0..<height {
                let startIdx = startY * width
                if visited[startIdx] { continue }
                if !matchesColorOrRainbow(startIdx, colorByte: targetByte) { continue }

                var head = 0
                var tail = 0
                bfsQueue[tail] = startIdx
                tail += 1
                visited[startIdx] = true

                var reachesRight = false
                var realColorCount = 0

                while head < tail {
                    let cur = bfsQueue[head]
                    head += 1
                    let cx = cur % width
                    let cy = cur / width

                    if cx == width - 1 { reachesRight = true }
                    if cellTypes[cur] == Self.cellSand && colorIndices[cur] == targetByte {
                        realColorCount += 1
                    }

                    let minNy = max(0, cy - 1)
                    let maxNy = min(height - 1, cy + 1)
                    let minNx = max(0, cx - 1)
                    let maxNx = min(width - 1, cx + 1)

                    for ny in minNy...maxNy {
                        let rowOffset = ny * width
                        for nx in minNx...maxNx {
                            let nIdx = rowOffset + nx
                            if !visited[nIdx] && matchesColorOrRainbow(nIdx, colorByte: targetByte) {
                                visited[nIdx] = true
                                bfsQueue[tail] = nIdx
                                tail += 1
                            }
                        }
                    }
                }

                if reachesRight && (realColorCount > 0 || color == 0) {
                    anyBridge = true
                    for i in 0..<tail {
                        clearMask[bfsQueue[i]] = true
                    }
                    if tail > largestComponent {
                        largestComponent = tail
                        dominantColorArgb = realColorCount > 0
                            ? palette[color % palette.count]
                            : 0xFFFFFFFF
                    }
                }
            }
        }

        guard anyBridge else { return false }

        var macrosToShatter = Set<Int>()
        var solidIceMacrosToThaw = Set<Int>()
        var sumX: Int64 = 0
        var sumY: Int64 = 0
        var sandClearCount = 0

        for idx in 0..<size where clearMask[idx] {
            let cx = idx % width
            let cy = idx / width
            sumX += Int64(cx)
            sumY += Int64(cy)
            sandClearCount += 1

            let minNy = max(0, cy - 2)
            let maxNy = min(height - 1, cy + 2)
            let minNx = max(0, cx - 2)
            let maxNx = min(width - 1, cx + 2)
            for ny in minNy...maxNy {
                let rBase = ny * width
                for nx in minNx...maxNx {
                    let neighborType = cellTypes[rBase + nx]
                    let macroKey = (ny / grainScale) * 100 + (nx / grainScale)
                    if neighborType == Self.cellCrackedStone || neighborType == Self.cellThinIce {
                        macrosToShatter.insert(macroKey)
                    } else if neighborType == Self.cellIce {
                        solidIceMacrosToThaw.insert(macroKey)
                    }
                }
            }
        }

        for macroKey in macrosToShatter {
            let mRow = macroKey / 100
            let mCol = macroKey % 100
            let startX = mCol * grainScale
            let startY = mRow * grainScale
            let endY = min(height, startY + grainScale)
            let endX = min(width, startX + grainScale)
            for gy in startY..<endY {
                let rBase = gy * width
                for gx in startX..<endX {
                    let mIdx = rBase + gx
                    if cellTypes[mIdx] == Self.cellCrackedStone || cellTypes[mIdx] == Self.cellThinIce {
                        clearMask[mIdx] = true
                    }
                }
            }
        }

        for macroKey in solidIceMacrosToThaw where !macrosToShatter.contains(macroKey) {
            thawMacroIce(macroCol: macroKey % 100, macroRow: macroKey / 100)
        }

        combo += 1
        if combo > maxCombo { maxCombo = combo }
        linesCleared += 1

        let feverActiveBefore = isFeverActive
        addFeverCharge(0.34 + Float(combo - 1) * 0.18)
        let feverBonusNow = feverActiveBefore || isFeverActive
        let feverMult = feverBonusNow ? 2 : 1

        let comboMultiplier: Float = 1.0 + Float(combo - 1) * 0.65
        let basePoints = sandClearCount * 2 + macrosToShatter.count * 150 + solidIceMacrosToThaw.count * 70
        let awarded = max(100, Int(Float(basePoints) * comboMultiplier)) * feverMult
        score += awarded

        let rawCoins = (6 + (combo - 1) * 5 + macrosToShatter.count * 4 + (sandClearCount / 90)) * feverMult

        if mode == .timeRush {
            let bonusSec: Float = 5.0 + Float(combo - 1) * 2.5
            timeRemainingSec = min(120.0, timeRemainingSec + bonusSec)
            pendingEvents.append(.timeBonusAdded(bonusSeconds: bonusSec))
        }

        let avgX = sandClearCount > 0 ? (Float(sumX) / Float(sandClearCount)) : Float(width) * 0.5
        let avgY = sandClearCount > 0 ? (Float(sumY) / Float(sandClearCount)) : Float(height) * 0.5

        clearFlashTimer = Self.clearFlashDuration
        pendingEvents.append(
            .lineClearStarted(
                combo: combo,
                grainCount: sandClearCount,
                crackedBlocksBroken: macrosToShatter.count,
                centerX: avgX,
                centerY: avgY,
                colorArgb: dominantColorArgb,
                pointsAwarded: awarded,
                coinsAwarded: rawCoins,
                feverBonusActive: feverBonusNow
            )
        )
        return true
    }

    func finalizeMarkedClears() {
        for i in 0..<size where clearMask[i] {
            clearSingleCell(i)
        }
        settleQuietTicks = 0
    }

    private func matchesColorOrRainbow(_ idx: Int, colorByte: UInt8) -> Bool {
        let t = cellTypes[idx]
        return (t == Self.cellSand && colorIndices[idx] == colorByte) || t == Self.cellRainbow
    }

    func countRemainingCrackedStones() -> Int {
        var macros = Set<Int>()
        for i in 0..<size where isBreakableObstacle(cellTypes[i]) {
            let x = i % width
            let y = i / width
            macros.insert((y / grainScale) * 100 + (x / grainScale))
        }
        return macros.count
    }

    func highestSandRow() -> Int {
        for y in 0..<height {
            let rBase = y * width
            for x in 0..<width {
                let t = cellTypes[rBase + x]
                if t == Self.cellSand || t == Self.cellRainbow {
                    return y
                }
            }
        }
        return height
    }

    private func evaluateEndConditions() {
        guard status == .playing, clearFlashTimer == 0 else { return }

        if let lvl = adventureLevel {
            let scoreOk = score >= lvl.targetScore
            let clearsOk = linesCleared >= lvl.targetClears
            let stonesOk = !lvl.requireClearCrackedStones || countRemainingCrackedStones() == 0

            if scoreOk && clearsOk && stonesOk {
                let totalAllowed = lvl.maxMoves + bonusMovesGranted
                let remainingBonus = max(0, totalAllowed - movesUsed) * 60
                score += remainingBonus
                let stars: Int
                if score >= lvl.star3Score {
                    stars = 3
                } else if score >= lvl.star2Score {
                    stars = 2
                } else {
                    stars = 1
                }
                status = .won
                pendingEvents.append(.levelWon(stars: stars, finalScore: score))
                return
            }
        }

        if highestSandRow() < dangerRow {
            status = .gameOver
            pendingEvents.append(.gameLost)
            return
        }

        if let lvl = adventureLevel {
            let totalAllowed = lvl.maxMoves + bonusMovesGranted
            if lvl.maxMoves > 0 && movesUsed >= totalAllowed {
                status = .gameOver
                pendingEvents.append(.gameLost)
                return
            }
        }
    }

    // MARK: - Pixel Buffer Rendering (0xFFRRGGBB UInt32)

    func renderPixels(_ outPixels: inout [UInt32]) {
        let bg = theme.boardBg
        let gridLine = theme.boardGridLine | 0xFF000000
        let gridBgColor = ColorMath.blend(bg, gridLine, 0.08)
        let palette = theme.sandPalette
        let paletteCount = palette.count
        let stoneBase = theme.stoneBaseColor
        let crackedBase = theme.crackedStoneColor
        let flashProgress: Float = clearFlashTimer > 0
            ? (1.0 - Float(clearFlashTimer) / Float(Self.clearFlashDuration))
            : 0.0

        for y in 0..<height {
            let rowBase = y * width
            let isMacroRowBorder = (y % grainScale) == 0
            for x in 0..<width {
                let idx = rowBase + x
                let type = cellTypes[idx]

                if clearMask[idx] && clearFlashTimer > 0 {
                    let sparkle: UInt32 = (((x &+ y &+ tickCount) & 1) == 0) ? 0xFFFFFFFF : theme.accentColor
                    outPixels[idx] = ColorMath.blend(sparkle, 0xFFFFF3B0, flashProgress)
                    continue
                }

                switch type {
                case Self.cellEmpty:
                    let isGrid = isMacroRowBorder || (x % grainScale) == 0
                    outPixels[idx] = isGrid ? gridBgColor : bg

                case Self.cellSand:
                    let cIdx = min(paletteCount - 1, max(0, Int(colorIndices[idx])))
                    let base = palette[cIdx]
                    let crestBoost = (y > 0 && cellTypes[idx - width] == Self.cellEmpty) ? 18 : 0
                    outPixels[idx] = ColorMath.shade(base, Int(shades[idx]) + crestBoost)

                case Self.cellRainbow:
                    let rb = ColorMath.rainbowColor(seed: x &* 3 &+ y &* 5, tick: tickCount)
                    outPixels[idx] = ColorMath.shade(rb, Int(shades[idx]) / 2)

                case Self.cellLava:
                    outPixels[idx] = ColorMath.lavaColor(seed: x &* 5 &+ y &* 7, tick: tickCount)

                case Self.cellStone:
                    let localX = x % grainScale
                    let localY = y % grainScale
                    let bevel: Int
                    if localX == 0 || localY == 0 {
                        bevel = 24
                    } else if localX == grainScale - 1 || localY == grainScale - 1 {
                        bevel = -26
                    } else {
                        bevel = Int(shades[idx]) / 2
                    }
                    outPixels[idx] = ColorMath.shade(stoneBase, bevel)

                case Self.cellCrackedStone:
                    let localX = x % grainScale
                    let localY = y % grainScale
                    let isCrackLine = (localX == localY && (1...6).contains(localX)) ||
                        (localX + localY == grainScale - 1 && (2...5).contains(localX))
                    let bevel: Int
                    if isCrackLine {
                        bevel = -58
                    } else if localX == 0 || localY == 0 {
                        bevel = 28
                    } else if localX == grainScale - 1 || localY == grainScale - 1 {
                        bevel = -22
                    } else {
                        bevel = Int(shades[idx]) / 3
                    }
                    outPixels[idx] = ColorMath.shade(crackedBase, bevel)

                case Self.cellIce:
                    let localX = x % grainScale
                    let localY = y % grainScale
                    let iceBase: UInt32 = 0xFF7BDFF2
                    let isGlint = (localX + localY == 3) || (localX == 2 && localY == 2)
                    let bevel: Int
                    if isGlint {
                        bevel = 68
                    } else if localX == 0 || localY == 0 {
                        bevel = 44
                    } else if localX == grainScale - 1 || localY == grainScale - 1 {
                        bevel = -28
                    } else {
                        bevel = Int(shades[idx]) / 3
                    }
                    outPixels[idx] = ColorMath.shade(iceBase, bevel)

                case Self.cellThinIce:
                    let localX = x % grainScale
                    let localY = y % grainScale
                    let thinIceBase: UInt32 = 0xFFB2F7EF
                    let isFrostCrack = (localX == localY && (1...6).contains(localX)) ||
                        (localX + localY == grainScale - 1 && (1...6).contains(localX))
                    let bevel: Int
                    if isFrostCrack {
                        bevel = -65
                    } else if localX == 0 || localY == 0 {
                        bevel = 34
                    } else if localX == grainScale - 1 || localY == grainScale - 1 {
                        bevel = -24
                    } else {
                        bevel = Int(shades[idx]) / 3
                    }
                    outPixels[idx] = ColorMath.shade(thinIceBase, bevel)

                default:
                    outPixels[idx] = bg
                }
            }
        }
    }

    // MARK: - Obstacles & Terrain Presets

    private func destroyBreakableMacroAt(macroCol: Int, macroRow: Int) -> Int {
        var count = 0
        let startX = macroCol * grainScale
        let startY = macroRow * grainScale
        let endY = min(height, startY + grainScale)
        let endX = min(width, startX + grainScale)
        guard startY < endY, startX < endX else { return 0 }
        for gy in startY..<endY {
            let rBase = gy * width
            for gx in startX..<endX {
                let idx = rBase + gx
                if isBreakableObstacle(cellTypes[idx]) {
                    clearSingleCell(idx)
                    count += 1
                }
            }
        }
        return count
    }

    private func thawMacroIce(macroCol: Int, macroRow: Int) {
        let startX = macroCol * grainScale
        let startY = macroRow * grainScale
        let endY = min(height, startY + grainScale)
        let endX = min(width, startX + grainScale)
        guard startY < endY, startX < endX else { return }
        for gy in startY..<endY {
            let rBase = gy * width
            for gx in startX..<endX {
                let idx = rBase + gx
                if cellTypes[idx] == Self.cellIce {
                    cellTypes[idx] = Self.cellThinIce
                }
            }
        }
    }

    func placeMacroStone(macroCol: Int, macroRow: Int, cracked: Bool = false) {
        let maxCols = width / grainScale
        let maxRows = height / grainScale
        guard (0..<maxCols).contains(macroCol), (0..<maxRows).contains(macroRow) else { return }
        let startX = macroCol * grainScale
        let startY = macroRow * grainScale
        let t = cracked ? Self.cellCrackedStone : Self.cellStone
        for gy in startY..<(startY + grainScale) {
            let rBase = gy * width
            for gx in startX..<(startX + grainScale) {
                let idx = rBase + gx
                cellTypes[idx] = t
                shades[idx] = Int8(rng.nextInt(in: -10..<11))
            }
        }
    }

    func placeMacroIce(macroCol: Int, macroRow: Int, thin: Bool = false) {
        let maxCols = width / grainScale
        let maxRows = height / grainScale
        guard (0..<maxCols).contains(macroCol), (0..<maxRows).contains(macroRow) else { return }
        let startX = macroCol * grainScale
        let startY = macroRow * grainScale
        let t = thin ? Self.cellThinIce : Self.cellIce
        for gy in startY..<(startY + grainScale) {
            let rBase = gy * width
            for gx in startX..<(startX + grainScale) {
                let idx = rBase + gx
                cellTypes[idx] = t
                shades[idx] = Int8(rng.nextInt(in: -8..<9))
            }
        }
    }

    private func pourPresetSand(macroCol: Int, macroRow: Int, colorIdx: Int) {
        let startX = macroCol * grainScale
        let startY = macroRow * grainScale
        let endY = min(height, startY + grainScale)
        let endX = min(width, startX + grainScale)
        guard startY < endY, startX < endX else { return }
        let c = UInt8(min(4, max(0, colorIdx)))
        for gy in startY..<endY {
            let rBase = gy * width
            for gx in startX..<endX {
                let idx = rBase + gx
                if cellTypes[idx] == Self.cellEmpty {
                    cellTypes[idx] = Self.cellSand
                    colorIndices[idx] = c
                    shades[idx] = Int8(rng.nextInt(in: -14..<15))
                }
            }
        }
    }

    private func applyTerrainPreset(_ preset: TerrainPreset, colors: Int) {
        switch preset {
        case .none:
            pourPresetSand(macroCol: 1, macroRow: 13, colorIdx: 0)
            pourPresetSand(macroCol: 7, macroRow: 13, colorIdx: 1 % max(1, colors))
        case .centerStep:
            placeMacroStone(macroCol: 4, macroRow: 10)
            placeMacroStone(macroCol: 3, macroRow: 11)
            placeMacroStone(macroCol: 4, macroRow: 11)
            placeMacroStone(macroCol: 5, macroRow: 11)
        case .crackedBridge:
            placeMacroStone(macroCol: 2, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 4, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 6, macroRow: 10, cracked: true)
        case .sideShelves:
            placeMacroStone(macroCol: 0, macroRow: 8)
            placeMacroStone(macroCol: 1, macroRow: 8)
            placeMacroStone(macroCol: 7, macroRow: 8)
            placeMacroStone(macroCol: 8, macroRow: 8)
        case .oceanFunnel:
            placeMacroStone(macroCol: 1, macroRow: 7)
            placeMacroStone(macroCol: 2, macroRow: 8)
            placeMacroStone(macroCol: 7, macroRow: 7)
            placeMacroStone(macroCol: 6, macroRow: 8)
        case .crackedPyramid:
            placeMacroStone(macroCol: 4, macroRow: 9, cracked: true)
            placeMacroStone(macroCol: 3, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 5, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 2, macroRow: 11, cracked: true)
            placeMacroStone(macroCol: 6, macroRow: 11, cracked: true)
        case .twinPillars:
            placeMacroStone(macroCol: 2, macroRow: 8)
            placeMacroStone(macroCol: 2, macroRow: 9)
            placeMacroStone(macroCol: 6, macroRow: 8)
            placeMacroStone(macroCol: 6, macroRow: 9)
        case .cyberHourglass:
            placeMacroStone(macroCol: 0, macroRow: 7)
            placeMacroStone(macroCol: 1, macroRow: 8)
            placeMacroStone(macroCol: 8, macroRow: 7)
            placeMacroStone(macroCol: 7, macroRow: 8)
            placeMacroStone(macroCol: 4, macroRow: 11, cracked: true)
        case .crackedFortress:
            placeMacroStone(macroCol: 1, macroRow: 9, cracked: true)
            placeMacroStone(macroCol: 3, macroRow: 9, cracked: true)
            placeMacroStone(macroCol: 5, macroRow: 9, cracked: true)
            placeMacroStone(macroCol: 7, macroRow: 9, cracked: true)
            placeMacroStone(macroCol: 4, macroRow: 7, cracked: true)
        case .pastelTerraces:
            placeMacroStone(macroCol: 1, macroRow: 7)
            placeMacroStone(macroCol: 4, macroRow: 9)
            placeMacroStone(macroCol: 7, macroRow: 7)
        case .neonGates:
            placeMacroStone(macroCol: 2, macroRow: 7)
            placeMacroStone(macroCol: 4, macroRow: 7, cracked: true)
            placeMacroStone(macroCol: 6, macroRow: 7)
            placeMacroStone(macroCol: 3, macroRow: 10)
            placeMacroStone(macroCol: 5, macroRow: 10)
        case .crownLabyrinth:
            placeMacroStone(macroCol: 1, macroRow: 7, cracked: true)
            placeMacroStone(macroCol: 4, macroRow: 6, cracked: true)
            placeMacroStone(macroCol: 7, macroRow: 7, cracked: true)
            placeMacroStone(macroCol: 2, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 4, macroRow: 10)
            placeMacroStone(macroCol: 6, macroRow: 10, cracked: true)
        case .volcanoCaldera:
            placeMacroStone(macroCol: 0, macroRow: 8)
            placeMacroStone(macroCol: 1, macroRow: 9)
            placeMacroStone(macroCol: 7, macroRow: 9)
            placeMacroStone(macroCol: 8, macroRow: 8)
            pourPresetSand(macroCol: 4, macroRow: 12, colorIdx: 0)
        case .magmaChambers:
            placeMacroStone(macroCol: 4, macroRow: 8)
            placeMacroStone(macroCol: 2, macroRow: 8, cracked: true)
            placeMacroStone(macroCol: 6, macroRow: 8, cracked: true)
            placeMacroStone(macroCol: 3, macroRow: 11, cracked: true)
            placeMacroStone(macroCol: 5, macroRow: 11, cracked: true)
        case .obsidianArch:
            placeMacroStone(macroCol: 2, macroRow: 8)
            placeMacroStone(macroCol: 3, macroRow: 7)
            placeMacroStone(macroCol: 4, macroRow: 7)
            placeMacroStone(macroCol: 5, macroRow: 7)
            placeMacroStone(macroCol: 6, macroRow: 8)
            placeMacroStone(macroCol: 2, macroRow: 11, cracked: true)
            placeMacroStone(macroCol: 6, macroRow: 11, cracked: true)
        case .eruptionCore:
            placeMacroStone(macroCol: 1, macroRow: 8)
            placeMacroStone(macroCol: 7, macroRow: 8)
            placeMacroStone(macroCol: 3, macroRow: 8, cracked: true)
            placeMacroStone(macroCol: 5, macroRow: 8, cracked: true)
            placeMacroIce(macroCol: 4, macroRow: 8)
            placeMacroStone(macroCol: 4, macroRow: 11, cracked: true)
        case .frostWall:
            placeMacroIce(macroCol: 2, macroRow: 9)
            placeMacroIce(macroCol: 4, macroRow: 9)
            placeMacroIce(macroCol: 6, macroRow: 9)
        case .glacierSteps:
            placeMacroIce(macroCol: 1, macroRow: 8)
            placeMacroIce(macroCol: 3, macroRow: 9)
            placeMacroIce(macroCol: 5, macroRow: 10)
            placeMacroIce(macroCol: 7, macroRow: 11)
            placeMacroStone(macroCol: 4, macroRow: 12)
        case .auroraSanctuary:
            placeMacroStone(macroCol: 1, macroRow: 8)
            placeMacroStone(macroCol: 7, macroRow: 8)
            placeMacroIce(macroCol: 3, macroRow: 7)
            placeMacroIce(macroCol: 5, macroRow: 7)
            placeMacroIce(macroCol: 2, macroRow: 10)
            placeMacroStone(macroCol: 4, macroRow: 10, cracked: true)
            placeMacroIce(macroCol: 6, macroRow: 10)
        case .eternalThrone:
            placeMacroIce(macroCol: 2, macroRow: 7)
            placeMacroIce(macroCol: 4, macroRow: 6)
            placeMacroIce(macroCol: 6, macroRow: 7)
            placeMacroStone(macroCol: 1, macroRow: 10, cracked: true)
            placeMacroIce(macroCol: 4, macroRow: 9)
            placeMacroStone(macroCol: 7, macroRow: 10, cracked: true)
            placeMacroStone(macroCol: 3, macroRow: 11)
            placeMacroStone(macroCol: 5, macroRow: 11)
        }

        for _ in 0..<45 {
            stepPhysicsOnce()
        }
        tickCount = 0
    }

    // MARK: - Auto-Save & Resume Serialization (Classic Mode)

    private func encodePiece(_ piece: TrayPiece?) -> String {
        guard let piece = piece else { return "none" }
        let cellsStr = piece.shape.cells.map { "\($0.col).\($0.row)" }.joined(separator: ",")
        return "\(piece.uid):\(piece.shape.id):\(cellsStr):\(piece.colorIndex):\(piece.kind.rawValue)"
    }

    private func decodePiece(_ token: String) -> TrayPiece? {
        if token == "none" || token.trimmingCharacters(in: .whitespaces).isEmpty { return nil }
        let parts = token.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard parts.count >= 5 else { return nil }
        let uid = Int64(parts[0]) ?? 1
        let id = parts[1]
        let cells: [CellOffset] = parts[2].split(separator: ",").compactMap { pair in
            let cr = pair.split(separator: ".")
            guard cr.count == 2, let c = Int(cr[0]), let r = Int(cr[1]) else { return nil }
            return CellOffset(col: c, row: r)
        }
        guard !cells.isEmpty else { return nil }
        let shape = BlockShape.normalize(id: id, rawCells: cells)
        let colorIdx = Int(parts[3]) ?? 0
        let kind = PieceKind(rawValue: parts[4]) ?? .NORMAL
        return TrayPiece(uid: uid, shape: shape, colorIndex: colorIdx, kind: kind)
    }

    func serializeClassicState() -> String {
        var packed = [UInt8](repeating: 0, count: size * 4)
        for i in 0..<size {
            packed[i] = cellTypes[i]
            packed[size + i] = colorIndices[i]
            packed[size * 2 + i] = UInt8(bitPattern: shades[i])
            packed[size * 3 + i] = UInt8(bitPattern: lavaFuel[i])
        }
        let b64 = Data(packed).base64EncodedString()
        let piecesEncoded = [
            encodePiece(trayPieces[0]),
            encodePiece(trayPieces[1]),
            encodePiece(trayPieces[2]),
            encodePiece(holdPiece)
        ].joined(separator: ";")

        return [
            "SBV2",
            "\(width),\(height)",
            theme.id,
            "\(score)",
            "\(linesCleared)",
            "\(combo)",
            "\(maxCombo)",
            "\(movesUsed)",
            "\(feverCharge)",
            "\(feverRemainingSec)",
            piecesEncoded,
            b64
        ].joined(separator: "|")
    }

    @discardableResult
    func restoreClassicState(_ serialized: String) -> Bool {
        let fields = serialized.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard fields.count >= 12, fields[0] == "SBV2" else { return false }
        let dims = fields[1].split(separator: ",")
        guard dims.count == 2, Int(dims[0]) == width, Int(dims[1]) == height else { return false }
        guard let decodedData = Data(base64Encoded: fields[11]), decodedData.count == size * 4 else {
            return false
        }

        resetBoardState()
        mode = .classic
        adventureLevel = nil
        theme = GameTheme.fromId(fields[2])
        score = Int(fields[3]) ?? 0
        linesCleared = Int(fields[4]) ?? 0
        combo = Int(fields[5]) ?? 0
        maxCombo = Int(fields[6]) ?? 0
        movesUsed = Int(fields[7]) ?? 0
        feverCharge = min(1.0, max(0.0, Float(fields[8]) ?? 0))
        feverRemainingSec = max(0.0, Float(fields[9]) ?? 0)

        let pieceTokens = fields[10].split(separator: ";", omittingEmptySubsequences: false).map(String.init)
        if pieceTokens.count >= 4 {
            trayPieces[0] = decodePiece(pieceTokens[0])
            trayPieces[1] = decodePiece(pieceTokens[1])
            trayPieces[2] = decodePiece(pieceTokens[2])
            holdPiece = decodePiece(pieceTokens[3])
        }
        if trayPieces.allSatisfy({ $0 == nil }) {
            refillTray()
        }

        let bytes = [UInt8](decodedData)
        for i in 0..<size {
            cellTypes[i] = bytes[i]
            colorIndices[i] = bytes[size + i]
            shades[i] = Int8(bitPattern: bytes[size * 2 + i])
            lavaFuel[i] = Int8(bitPattern: bytes[size * 3 + i])
        }
        status = .playing
        return true
    }
}
