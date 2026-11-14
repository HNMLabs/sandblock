import Foundation

struct SeededRNG {
    private var state: UInt64

    init(seed: UInt64? = nil) {
        if let seed = seed {
            self.state = seed == 0 ? 0x9E3779B97F4A7C15 : seed
        } else {
            self.state = UInt64.random(in: 1...UInt64.max)
        }
    }

    mutating func nextUInt64() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }

    mutating func nextInt(_ upperBound: Int) -> Int {
        guard upperBound > 1 else { return 0 }
        return Int(nextUInt64() % UInt64(upperBound))
    }

    mutating func nextInt(in range: Range<Int>) -> Int {
        let span = range.upperBound - range.lowerBound
        guard span > 0 else { return range.lowerBound }
        return range.lowerBound + nextInt(span)
    }

    mutating func nextFloat() -> Float {
        Float(nextUInt64() >> 40) / Float(1 << 24)
    }

    mutating func nextDouble(in range: ClosedRange<Double>) -> Double {
        let u = Double(nextUInt64() >> 11) / Double(1 << 53)
        return range.lowerBound + u * (range.upperBound - range.lowerBound)
    }

    mutating func nextBool() -> Bool {
        (nextUInt64() & 1) == 0
    }
}

struct CellOffset: Hashable, Equatable, Codable {
    let col: Int
    let row: Int
}

struct BlockShape: Equatable {
    let id: String
    let cells: [CellOffset]
    let cols: Int
    let rows: Int

    func rotatedClockwise() -> BlockShape {
        let rotatedRaw = cells.map { CellOffset(col: rows - 1 - $0.row, row: $0.col) }
        return BlockShape.normalize(id: id, rawCells: rotatedRaw)
    }

    static func byId(_ id: String) -> BlockShape {
        if id == bombShape.id { return bombShape }
        return standardShapes.first(where: { $0.id == id }) ?? standardShapes[0]
    }

    static func normalize(id: String, rawCells: [CellOffset]) -> BlockShape {
        guard !rawCells.isEmpty else {
            return BlockShape(id: id, cells: [CellOffset(col: 0, row: 0)], cols: 1, rows: 1)
        }
        let minCol = rawCells.map(\.col).min() ?? 0
        let minRow = rawCells.map(\.row).min() ?? 0
        var seen = Set<CellOffset>()
        var norm: [CellOffset] = []
        for c in rawCells {
            let shifted = CellOffset(col: c.col - minCol, row: c.row - minRow)
            if seen.insert(shifted).inserted {
                norm.append(shifted)
            }
        }
        let cols = (norm.map(\.col).max() ?? 0) + 1
        let rows = (norm.map(\.row).max() ?? 0) + 1
        return BlockShape(id: id, cells: norm, cols: cols, rows: rows)
    }

    private static func fromAscii(_ id: String, _ lines: String...) -> BlockShape {
        var cells: [CellOffset] = []
        for (r, line) in lines.enumerated() {
            for (c, ch) in line.enumerated() where ch == "#" {
                cells.append(CellOffset(col: c, row: r))
            }
        }
        return normalize(id: id, rawCells: cells)
    }

    static let bombShape: BlockShape = fromAscii(
        "BOMB",
        "##",
        "##"
    )

    static let standardShapes: [BlockShape] = [
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
    ]
}

enum PieceKind: String, Codable, CaseIterable {
    case NORMAL
    case RAINBOW
    case BOMB
    case LAVA
}

struct TrayPiece: Equatable, Identifiable {
    let uid: Int64
    var shape: BlockShape
    var colorIndex: Int
    var kind: PieceKind = .NORMAL

    var id: Int64 { uid }

    func rotated() -> TrayPiece {
        if kind == .BOMB { return self }
        var copy = self
        copy.shape = shape.rotatedClockwise()
        return copy
    }
}

final class PieceGenerator {
    private var rng: SeededRNG
    private var nextUid: Int64 = 1
    private var piecesGenerated: Int = 0

    init(rng: SeededRNG = SeededRNG()) {
        self.rng = rng
    }

    func reset() {
        piecesGenerated = 0
    }

    func nextSinglePiece(activeColors: Int, allowSpecialPieces: Bool = true) -> TrayPiece {
        piecesGenerated += 1
        let safeColors = min(5, max(2, activeColors))
        let roll = rng.nextFloat()

        let kind: PieceKind
        if allowSpecialPieces && piecesGenerated >= 5 && roll < 0.055 {
            kind = .RAINBOW
        } else if allowSpecialPieces && piecesGenerated >= 7 && roll >= 0.055 && roll <= 0.095 {
            kind = .BOMB
        } else if allowSpecialPieces && piecesGenerated >= 8 && roll > 0.095 && roll <= 0.135 {
            kind = .LAVA
        } else {
            kind = .NORMAL
        }

        if kind == .BOMB {
            defer { nextUid += 1 }
            return TrayPiece(
                uid: nextUid,
                shape: BlockShape.bombShape,
                colorIndex: 0,
                kind: .BOMB
            )
        }

        let shapePool = kind == .LAVA
            ? Array(BlockShape.standardShapes.prefix(5))
            : BlockShape.standardShapes
        var shape = shapePool[rng.nextInt(shapePool.count)]
        let rotations = rng.nextInt(4)
        for _ in 0..<rotations {
            shape = shape.rotatedClockwise()
        }

        defer { nextUid += 1 }
        return TrayPiece(
            uid: nextUid,
            shape: shape,
            colorIndex: rng.nextInt(safeColors),
            kind: kind
        )
    }

    func nextTray(activeColors: Int, allowSpecialPieces: Bool = true) -> [TrayPiece] {
        let safeColors = min(5, max(2, activeColors))
        var pieces: [TrayPiece] = []
        pieces.reserveCapacity(3)
        let primaryColor = rng.nextInt(safeColors)

        for slot in 0..<3 {
            var base = nextSinglePiece(
                activeColors: safeColors,
                allowSpecialPieces: allowSpecialPieces && slot == 2
            )
            if slot == 0 && base.kind == .NORMAL && rng.nextFloat() < 0.48 {
                base.colorIndex = primaryColor
            }
            pieces.append(base)
        }
        return pieces
    }

    func upgradePieceToRainbow(_ piece: TrayPiece) -> TrayPiece {
        var copy = piece
        if piece.kind == .BOMB {
            copy.shape = BlockShape.standardShapes[1]
        }
        copy.kind = .RAINBOW
        return copy
    }

    func upgradePieceToLava(_ piece: TrayPiece) -> TrayPiece {
        var copy = piece
        if piece.kind == .BOMB {
            copy.shape = BlockShape.standardShapes[0]
        }
        copy.kind = .LAVA
        return copy
    }
}
