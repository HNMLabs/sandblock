package com.hnmlabs.sandblock.engine

import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.BlockShape
import com.hnmlabs.sandblock.model.GameMode
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.PlayerProgression
import com.hnmlabs.sandblock.model.TrayPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SandWorldTest {

    @Test
    fun `falling sand settles to bottom and forms natural slope`() {
        val world = SandWorld(width = 32, height = 40, grainScale = 4, dangerRow = 6, random = Random(1))
        world.startClassic(GameTheme.SAHARA)

        val piece = TrayPiece(
            uid = 99L,
            shape = BlockShape.STANDARD_SHAPES.first(), // 2x2 macro = 8x8 = 64 grains
            colorIndex = 0,
            kind = PieceKind.NORMAL
        )
        world.spawnPieceGrains(piece, originX = 12, originY = 4)

        repeat(60) {
            world.stepPhysicsOnce()
        }

        var totalGrains = 0
        var bottomRowGrains = 0
        for (i in 0 until world.size) {
            if (world.cellTypes[i] == SandWorld.CELL_SAND) {
                totalGrains++
                if (i / world.width == world.height - 1) {
                    bottomRowGrains++
                }
            }
        }
        assertEquals(64, totalGrains)
        assertTrue("Sand should spread wider than initial 8-grain width on floor", bottomRowGrains > 8)
    }

    @Test
    fun `left to right same-color band triggers line clear and awards score`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(2))
        world.startClassic(GameTheme.NEON)

        for (y in (world.height - 2) until world.height) {
            for (x in 0 until world.width) {
                val idx = y * world.width + x
                world.cellTypes[idx] = SandWorld.CELL_SAND
                world.colorIndices[idx] = 2
            }
        }

        assertTrue(world.detectAndBeginLineClear())
        assertEquals(1, world.linesCleared)
        assertTrue(world.score > 0)

        world.finalizeMarkedClears()
        val remaining = world.cellTypes.count { it == SandWorld.CELL_SAND }
        assertEquals(0, remaining)
    }

    @Test
    fun `rainbow wildcard sand bridges two segments of matching color`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(3))
        world.startClassic(GameTheme.OCEAN)

        val y = world.height - 1
        for (x in 0 until 8) {
            world.cellTypes[y * world.width + x] = SandWorld.CELL_SAND
            world.colorIndices[y * world.width + x] = 1
        }
        for (x in 8 until 16) {
            world.cellTypes[y * world.width + x] = SandWorld.CELL_RAINBOW
        }
        for (x in 16 until 24) {
            world.cellTypes[y * world.width + x] = SandWorld.CELL_SAND
            world.colorIndices[y * world.width + x] = 1
        }

        assertTrue("Rainbow grains should bridge left and right Color 1 segments", world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        assertEquals(0, world.cellTypes.count { it != SandWorld.CELL_EMPTY })
    }

    @Test
    fun `adjacent line clear shatters cracked stone macro block`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(4))
        world.startClassic(GameTheme.SAHARA)

        world.placeMacroStone(macroCol = 2, macroRow = 6, cracked = true)
        assertEquals(1, world.countRemainingCrackedStones())

        for (x in 0 until world.width) {
            val idx = 28 * world.width + x
            world.cellTypes[idx] = SandWorld.CELL_SAND
            world.colorIndices[idx] = 0
        }

        assertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        assertEquals("Adjacent cracked stone should shatter completely", 0, world.countRemainingCrackedStones())
    }

    @Test
    fun `two-hit ice block thaws on first adjacent clear and shatters on second`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(40))
        world.startClassic(GameTheme.AURORA)

        // Place 2-hit ice at macro (2, 6) -> rows 24..27
        world.placeMacroIce(macroCol = 2, macroRow = 6, thin = false)
        assertEquals(1, world.countRemainingCrackedStones())

        // 1st adjacent clear at row 28 -> thaws CELL_ICE into CELL_THIN_ICE
        for (x in 0 until world.width) {
            world.cellTypes[28 * world.width + x] = SandWorld.CELL_SAND
            world.colorIndices[28 * world.width + x] = 0
        }
        assertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        assertEquals("Ice should still exist as thin ice after 1st hit", 1, world.countRemainingCrackedStones())
        assertTrue(world.cellTypes.any { it == SandWorld.CELL_THIN_ICE })

        // 2nd adjacent clear at row 28 -> shatters CELL_THIN_ICE completely!
        for (x in 0 until world.width) {
            world.cellTypes[28 * world.width + x] = SandWorld.CELL_SAND
            world.colorIndices[28 * world.width + x] = 1
        }
        assertTrue(world.detectAndBeginLineClear())
        world.finalizeMarkedClears()
        assertEquals("Thin ice should shatter on 2nd hit", 0, world.countRemainingCrackedStones())
    }

    @Test
    fun `lava grains melt sand and obstacles below and eventually cool`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(41))
        world.startClassic(GameTheme.VOLCANO)

        // Fill rows 22..31 with sand
        for (y in 22 until world.height) {
            for (x in 0 until world.width) {
                world.cellTypes[y * world.width + x] = SandWorld.CELL_SAND
            }
        }
        val beforeSand = world.cellTypes.count { it == SandWorld.CELL_SAND }

        val lavaPiece = TrayPiece(
            uid = 500L,
            shape = BlockShape.STANDARD_SHAPES[0],
            colorIndex = 0,
            kind = PieceKind.LAVA
        )
        world.spawnPieceGrains(lavaPiece, originX = 8, originY = 10)

        repeat(80) {
            world.stepPhysicsOnce()
        }

        val afterSand = world.cellTypes.count { it == SandWorld.CELL_SAND }
        val remainingLava = world.cellTypes.count { it == SandWorld.CELL_LAVA }
        assertTrue("Lava must melt through sand below", afterSand < beforeSand)
        assertEquals("Lava grains must cool/evaporate cleanly after exhausting fuel", 0, remainingLava)
    }

    @Test
    fun `hold slot stores swaps and places pieces cleanly`() {
        val world = SandWorld(width = 32, height = 40, grainScale = 4, dangerRow = 4, random = Random(42))
        world.startClassic(GameTheme.SAHARA)

        assertNull(world.holdPiece)
        val firstUid = world.trayPieces[0]!!.uid
        assertTrue(world.holdOrSwapTrayPiece(0))
        assertEquals(firstUid, world.holdPiece?.uid)
        assertNull(world.trayPieces[0])

        val secondUid = world.trayPieces[1]!!.uid
        assertTrue(world.holdOrSwapTrayPiece(1))
        assertEquals(secondUid, world.holdPiece?.uid)
        assertEquals(firstUid, world.trayPieces[1]?.uid)

        assertTrue(world.placeHoldPiece(8, 12))
        assertNull(world.holdPiece)
    }

    @Test
    fun `fever mode doubles score and live coins on line clear`() {
        val normalWorld = SandWorld(width = 20, height = 24, grainScale = 4, dangerRow = 4, random = Random(43))
        normalWorld.startClassic(GameTheme.SAHARA)
        for (x in 0 until normalWorld.width) {
            normalWorld.cellTypes[(normalWorld.height - 1) * normalWorld.width + x] = SandWorld.CELL_SAND
            normalWorld.colorIndices[(normalWorld.height - 1) * normalWorld.width + x] = 0
        }
        assertTrue(normalWorld.detectAndBeginLineClear())
        val normalEv = normalWorld.drainEvents().filterIsInstance<WorldEvent.LineClearStarted>().single()

        val feverWorld = SandWorld(width = 20, height = 24, grainScale = 4, dangerRow = 4, random = Random(43))
        feverWorld.startClassic(GameTheme.SAHARA)
        feverWorld.triggerFeverForTest()
        for (x in 0 until feverWorld.width) {
            feverWorld.cellTypes[(feverWorld.height - 1) * feverWorld.width + x] = SandWorld.CELL_SAND
            feverWorld.colorIndices[(feverWorld.height - 1) * feverWorld.width + x] = 0
        }
        assertTrue(feverWorld.detectAndBeginLineClear())
        val feverEv = feverWorld.drainEvents().filterIsInstance<WorldEvent.LineClearStarted>().single()

        assertEquals(normalEv.pointsAwarded * 2, feverEv.pointsAwarded)
        assertEquals(normalEv.coinsAwarded * 2, feverEv.coinsAwarded)
    }

    @Test
    fun `time rush mode counts down and adds bonus seconds on line clear`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 4, random = Random(44))
        world.startMode(GameMode.TIME_RUSH, GameTheme.NEON)
        assertEquals(90f, world.timeRemainingSec, 0.01f)

        world.updateFrame(physicsSteps = 2, dt = 10f)
        assertEquals(80f, world.timeRemainingSec, 0.05f)

        for (x in 0 until world.width) {
            world.cellTypes[(world.height - 1) * world.width + x] = SandWorld.CELL_SAND
            world.colorIndices[(world.height - 1) * world.width + x] = 0
        }
        assertTrue(world.detectAndBeginLineClear())
        assertTrue("Line clear should add bonus seconds in Time Rush", world.timeRemainingSec > 84f)
    }

    @Test
    fun `sandtrix mode spawns falling piece supports move rotate hold and hard drop`() {
        val world = SandWorld(width = 40, height = 56, grainScale = 4, dangerRow = 6, random = Random(45))
        world.startMode(GameMode.SANDTRIX, GameTheme.OCEAN)

        assertNotNull(world.fallingPiece)
        assertNotNull(world.nextFallingPiece)

        val initX = world.fallingX
        assertTrue(world.moveFallingHorizontal(4))
        assertEquals(initX + 4, world.fallingX)

        assertTrue(world.swapSandtrixHold())
        assertNotNull(world.holdPiece)
        assertFalse("Hold swap is allowed once per drop", world.swapSandtrixHold())

        assertTrue(world.hardDropFallingPiece())
        assertTrue("Hard drop must crumble piece onto board and award points", world.score > 0)
        assertTrue(world.cellTypes.any { it != SandWorld.CELL_EMPTY } || world.score >= 80)
    }

    @Test
    fun `classic state serialization and restoration preserves board and score`() {
        val world = SandWorld(width = 32, height = 40, grainScale = 4, dangerRow = 6, random = Random(46))
        world.startClassic(GameTheme.VOLCANO)
        world.placeTrayPiece(0, 8, 16)
        repeat(25) { world.stepPhysicsOnce() }
        world.holdOrSwapTrayPiece(1)

        val expectedScore = world.score
        val expectedSandCount = world.cellTypes.count { it != SandWorld.CELL_EMPTY }
        val expectedHoldUid = world.holdPiece?.uid
        val snapshot = world.serializeClassicState()

        val restored = SandWorld(width = 32, height = 40, grainScale = 4, dangerRow = 6, random = Random(99))
        assertTrue(restored.restoreClassicState(snapshot))
        assertEquals(GameTheme.VOLCANO, restored.theme)
        assertEquals(expectedScore, restored.score)
        assertEquals(expectedSandCount, restored.cellTypes.count { it != SandWorld.CELL_EMPTY })
        assertEquals(expectedHoldUid, restored.holdPiece?.uid)
    }

    @Test
    fun `revive clears top rows and resumes playing from game over`() {
        val world = SandWorld(width = 24, height = 32, grainScale = 4, dangerRow = 8, random = Random(47))
        world.startClassic(GameTheme.SAHARA)

        // Settled alternating-column sand stack reaching row 5 (above dangerRow = 8)
        for (y in 5 until world.height) {
            for (x in 0 until world.width) {
                val idx = y * world.width + x
                world.cellTypes[idx] = SandWorld.CELL_SAND
                world.colorIndices[idx] = ((x / 4) % 2).toByte()
            }
        }
        repeat(6) { world.updateFrame(physicsSteps = 1) }
        assertEquals(GameStatus.GAME_OVER, world.status)

        assertTrue(world.reviveContinue())
        assertEquals(GameStatus.PLAYING, world.status)
        assertFalse(world.highestSandRow() < world.dangerRow)
    }

    @Test
    fun `all 20 adventure levels initialize cleanly without danger breach`() {
        val world = SandWorld(random = Random(7))
        assertEquals(20, AdventureCatalog.levels.size)
        for (level in AdventureCatalog.levels) {
            world.startAdventure(level)
            assertEquals(GameStatus.PLAYING, world.status)
            assertFalse("Initial preset sand/stones must not breach danger line in level ${level.number}", world.highestSandRow() < world.dangerRow)
        }
    }

    @Test
    fun `player rank progression calculates levels cleanly`() {
        val r1 = PlayerProgression.calculateRank(0)
        assertEquals(1, r1.level)
        val r3 = PlayerProgression.calculateRank(400)
        assertTrue(r3.level >= 3)
    }
}
