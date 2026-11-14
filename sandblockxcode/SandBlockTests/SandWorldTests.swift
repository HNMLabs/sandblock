import XCTest
@testable import SandBlock

final class SandWorldTests: XCTestCase {

    func testFallingSandSettlesToBottomAndFormsNaturalSlope() {
        let world = SandWorld(width: 32, height: 40, grainScale: 4, dangerRow: 6, seed: 1)
        world.startClassic(.sahara)

        let piece = TrayPiece(
            uid: 99,
            shape: BlockShape.standardShapes[0], // 2x2 macro = 8x8 = 64 grains
            colorIndex: 0,
            kind: .NORMAL
        )
        world.spawnPieceGrains(piece, originX: 12, originY: 4)

        for _ in 0..<60 {
            world.stepPhysicsOnce()
        }

        var totalGrains = 0
        var bottomRowGrains = 0
        for i in 0..<world.size where world.cellTypes[i] == SandWorld.cellSand {
            totalGrains += 1
            if i / world.width == world.height - 1 {
                bottomRowGrains += 1
            }
        }
        XCTAssertEqual(64, totalGrains)
        XCTAssertGreaterThan(bottomRowGrains, 8, "Sand should spread wider than initial 8-grain width on floor")
    }

    func testLeftToRightSameColorBandTriggersLineClearAndAwardsScore() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 2)
        world.startClassic(.neon)

        for y in (world.height - 2)..<world.height {
            for x in 0..<world.width {
                let idx = y * world.width + x
                world.cellTypes[idx] = SandWorld.cellSand
                world.colorIndices[idx] = 2
            }
        }

        XCTAssertTrue(world.detectAndBeginLineClear())
        XCTAssertEqual(1, world.linesCleared)
        XCTAssertGreaterThan(world.score, 0)

        world.finalizeMarkedClears()
        let remaining = world.cellTypes.filter { $0 == SandWorld.cellSand }.count
        XCTAssertEqual(0, remaining)
    }

    func testRainbowWildcardSandBridgesTwoSegmentsOfMatchingColor() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 3)
        world.startClassic(.ocean)

        let y = world.height - 1
        for x in 0..<8 {
            world.cellTypes[y * world.width + x] = SandWorld.cellSand
            world.colorIndices[y * world.width + x] = 1
        }
        for x in 8..<16 {
            world.cellTypes[y * world.width + x] = SandWorld.cellRainbow
        }
        for x in 16..<24 {
            world.cellTypes[y * world.width + x] = SandWorld.cellSand
            world.colorIndices[y * world.width + x] = 1
        }

        XCTAssertTrue(world.detectAndBeginLineClear(), "Rainbow grains should bridge left and right Color 1 segments")
        world.finalizeMarkedClears()
        XCTAssertEqual(0, world.cellTypes.filter { $0 != SandWorld.cellEmpty }.count)
    }

    func testAdjacentLineClearShattersCrackedStoneMacroBlock() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 4)
        world.startClassic(.sahara)

        world.placeMacroStone(macroCol: 2, macroRow: 6, cracked: true)
        XCTAssertEqual(1, world.countRemainingCrackedStones())

        for x in 0..<world.width {
            let idx = 28 * world.width + x
            world.cellTypes[idx] = SandWorld.cellSand
            world.colorIndices[idx] = 0
        }

        XCTAssertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        XCTAssertEqual(0, world.countRemainingCrackedStones(), "Adjacent cracked stone should shatter completely")
    }

    func testTwoHitIceBlockThawsOnFirstAdjacentClearAndShattersOnSecond() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 40)
        world.startClassic(.aurora)

        world.placeMacroIce(macroCol: 2, macroRow: 6, thin: false)
        XCTAssertEqual(1, world.countRemainingCrackedStones())

        // 1st adjacent clear at row 28 -> thaws cellIce into cellThinIce
        for x in 0..<world.width {
            world.cellTypes[28 * world.width + x] = SandWorld.cellSand
            world.colorIndices[28 * world.width + x] = 0
        }
        XCTAssertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        XCTAssertEqual(1, world.countRemainingCrackedStones(), "Ice should still exist as thin ice after 1st hit")
        XCTAssertTrue(world.cellTypes.contains(SandWorld.cellThinIce))

        // 2nd adjacent clear at row 28 -> shatters cellThinIce completely
        for x in 0..<world.width {
            world.cellTypes[28 * world.width + x] = SandWorld.cellSand
            world.colorIndices[28 * world.width + x] = 1
        }
        XCTAssertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        XCTAssertEqual(0, world.countRemainingCrackedStones(), "Thin ice should shatter on 2nd hit")
    }

    func testLavaGrainsMeltSandBelowAndEventuallyCool() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 41)
        world.startClassic(.volcano)

        for y in 22..<world.height {
            for x in 0..<world.width {
                world.cellTypes[y * world.width + x] = SandWorld.cellSand
            }
        }
        let beforeSand = world.cellTypes.filter { $0 == SandWorld.cellSand }.count

        let lavaPiece = TrayPiece(
            uid: 500,
            shape: BlockShape.standardShapes[0],
            colorIndex: 0,
            kind: .LAVA
        )
        world.spawnPieceGrains(lavaPiece, originX: 8, originY: 10)

        for _ in 0..<80 {
            world.stepPhysicsOnce()
        }

        let afterSand = world.cellTypes.filter { $0 == SandWorld.cellSand }.count
        let remainingLava = world.cellTypes.filter { $0 == SandWorld.cellLava }.count
        XCTAssertLessThan(afterSand, beforeSand, "Lava must melt through sand below")
        XCTAssertEqual(0, remainingLava, "Lava grains must cool/evaporate cleanly after exhausting fuel")
    }

    func testHoldSlotStoresSwapsAndPlacesPiecesCleanly() {
        let world = SandWorld(width: 32, height: 40, grainScale: 4, dangerRow: 4, seed: 42)
        world.startClassic(.sahara)

        XCTAssertNil(world.holdPiece)
        guard let firstUid = world.trayPieces[0]?.uid else {
            XCTFail("Expected tray slot 0 piece")
            return
        }
        XCTAssertTrue(world.holdOrSwapTrayPiece(0))
        XCTAssertEqual(firstUid, world.holdPiece?.uid)
        XCTAssertNil(world.trayPieces[0])

        guard let secondUid = world.trayPieces[1]?.uid else {
            XCTFail("Expected tray slot 1 piece")
            return
        }
        XCTAssertTrue(world.holdOrSwapTrayPiece(1))
        XCTAssertEqual(secondUid, world.holdPiece?.uid)
        XCTAssertEqual(firstUid, world.trayPieces[1]?.uid)

        XCTAssertTrue(world.placeHoldPiece(rawX: 8, rawY: 12))
        XCTAssertNil(world.holdPiece)
    }

    func testFeverModeDoublesScoreAndLiveCoinsOnLineClear() {
        let normalWorld = SandWorld(width: 20, height: 24, grainScale: 4, dangerRow: 4, seed: 43)
        normalWorld.startClassic(.sahara)
        for x in 0..<normalWorld.width {
            normalWorld.cellTypes[(normalWorld.height - 1) * normalWorld.width + x] = SandWorld.cellSand
            normalWorld.colorIndices[(normalWorld.height - 1) * normalWorld.width + x] = 0
        }
        XCTAssertTrue(normalWorld.detectAndBeginLineClear())
        var normalPts = 0
        var normalCoins = 0
        for ev in normalWorld.drainEvents() {
            if case let .lineClearStarted(_, _, _, _, _, _, pointsAwarded, coinsAwarded, _) = ev {
                normalPts = pointsAwarded
                normalCoins = coinsAwarded
            }
        }

        let feverWorld = SandWorld(width: 20, height: 24, grainScale: 4, dangerRow: 4, seed: 43)
        feverWorld.startClassic(.sahara)
        feverWorld.triggerFeverForTest()
        for x in 0..<feverWorld.width {
            feverWorld.cellTypes[(feverWorld.height - 1) * feverWorld.width + x] = SandWorld.cellSand
            feverWorld.colorIndices[(feverWorld.height - 1) * feverWorld.width + x] = 0
        }
        XCTAssertTrue(feverWorld.detectAndBeginLineClear())
        var feverPts = 0
        var feverCoins = 0
        for ev in feverWorld.drainEvents() {
            if case let .lineClearStarted(_, _, _, _, _, _, pointsAwarded, coinsAwarded, _) = ev {
                feverPts = pointsAwarded
                feverCoins = coinsAwarded
            }
        }

        XCTAssertGreaterThan(normalPts, 0)
        XCTAssertEqual(normalPts * 2, feverPts)
        XCTAssertEqual(normalCoins * 2, feverCoins)
    }

    func testTimeRushModeCountsDownAndAddsBonusSecondsOnLineClear() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 4, seed: 44)
        world.startMode(.timeRush, selectedTheme: .neon)
        XCTAssertEqual(90.0, world.timeRemainingSec, accuracy: 0.01)

        world.updateFrame(physicsSteps: 2, dt: 10.0)
        XCTAssertEqual(80.0, world.timeRemainingSec, accuracy: 0.05)

        for x in 0..<world.width {
            world.cellTypes[(world.height - 1) * world.width + x] = SandWorld.cellSand
            world.colorIndices[(world.height - 1) * world.width + x] = 0
        }
        XCTAssertTrue(world.detectAndBeginLineClear())
        XCTAssertGreaterThan(world.timeRemainingSec, 84.0, "Line clear should add bonus seconds in Time Rush")
    }

    func testSandtrixModeSpawnsFallingPieceSupportsMoveHoldAndHardDrop() {
        let world = SandWorld(width: 40, height: 56, grainScale: 4, dangerRow: 6, seed: 45)
        world.startMode(.sandtrix, selectedTheme: .ocean)

        XCTAssertNotNil(world.fallingPiece)
        XCTAssertNotNil(world.nextFallingPiece)

        let initX = world.fallingX
        XCTAssertTrue(world.moveFallingHorizontal(4))
        XCTAssertEqual(initX + 4, world.fallingX)

        XCTAssertTrue(world.swapSandtrixHold())
        XCTAssertNotNil(world.holdPiece)
        XCTAssertFalse(world.swapSandtrixHold(), "Hold swap is allowed once per drop")

        XCTAssertTrue(world.hardDropFallingPiece())
        XCTAssertGreaterThan(world.score, 0, "Hard drop must crumble piece onto board and award points")
    }

    func testClassicStateSerializationAndRestorationPreservesBoardAndScore() {
        let world = SandWorld(width: 32, height: 40, grainScale: 4, dangerRow: 6, seed: 46)
        world.startClassic(.volcano)
        XCTAssertTrue(world.placeTrayPiece(slot: 0, rawX: 8, rawY: 16))
        for _ in 0..<25 { world.stepPhysicsOnce() }
        XCTAssertTrue(world.holdOrSwapTrayPiece(1))

        let expectedScore = world.score
        let expectedSandCount = world.cellTypes.filter { $0 != SandWorld.cellEmpty }.count
        let expectedHoldUid = world.holdPiece?.uid
        let snapshot = world.serializeClassicState()

        let restored = SandWorld(width: 32, height: 40, grainScale: 4, dangerRow: 6, seed: 99)
        XCTAssertTrue(restored.restoreClassicState(snapshot))
        XCTAssertEqual(GameTheme.volcano, restored.theme)
        XCTAssertEqual(expectedScore, restored.score)
        XCTAssertEqual(expectedSandCount, restored.cellTypes.filter { $0 != SandWorld.cellEmpty }.count)
        XCTAssertEqual(expectedHoldUid, restored.holdPiece?.uid)
    }

    func testReviveClearsTopRowsAndResumesPlayingFromGameOver() {
        let world = SandWorld(width: 24, height: 32, grainScale: 4, dangerRow: 8, seed: 47)
        world.startClassic(.sahara)

        for y in 5..<world.height {
            for x in 0..<world.width {
                let idx = y * world.width + x
                world.cellTypes[idx] = SandWorld.cellSand
                world.colorIndices[idx] = UInt8((x / 4) % 2)
            }
        }
        for _ in 0..<6 { world.updateFrame(physicsSteps: 1) }
        XCTAssertEqual(GameStatus.gameOver, world.status)

        XCTAssertTrue(world.reviveContinue())
        XCTAssertEqual(GameStatus.playing, world.status)
        XCTAssertFalse(world.highestSandRow() < world.dangerRow)
    }

    func testAll20AdventureLevelsInitializeCleanlyWithoutDangerBreach() {
        let world = SandWorld(seed: 7)
        XCTAssertEqual(20, AdventureCatalog.levels.count)
        for level in AdventureCatalog.levels {
            world.startAdventure(level)
            XCTAssertEqual(GameStatus.playing, world.status)
            XCTAssertFalse(
                world.highestSandRow() < world.dangerRow,
                "Initial preset sand/stones must not breach danger line in level \(level.number)"
            )
        }
    }

    func testPlayerRankProgressionCalculatesLevelsCleanly() {
        let r1 = PlayerProgression.calculateRank(0)
        XCTAssertEqual(1, r1.level)
        let r3 = PlayerProgression.calculateRank(400)
        XCTAssertGreaterThanOrEqual(r3.level, 3)
    }
}
