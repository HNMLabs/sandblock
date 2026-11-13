package com.hnmlabs.sandblock.engine

import com.hnmlabs.sandblock.model.AdventureCatalog
import com.hnmlabs.sandblock.model.BlockShape
import com.hnmlabs.sandblock.model.GameTheme
import com.hnmlabs.sandblock.model.PieceKind
import com.hnmlabs.sandblock.model.TrayPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // All 64 grains must still exist (conservation of sand) and rest on bottom rows
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

        // Fill bottom 2 rows from x=0 to x=width-1 with color 2
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
        // Left third: Color 1, Middle third: Rainbow, Right third: Color 1
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

        // Place cracked stone at macro (2, 6) -> rows 24..27, cols 8..11
        world.placeMacroStone(macroCol = 2, macroRow = 6, cracked = true)
        assertEquals(1, world.countRemainingCrackedStones())

        // Create a sand bridge right underneath at row 28
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
    fun `bomb detonation clears sand and stones within blast radius`() {
        val world = SandWorld(width = 40, height = 48, grainScale = 4, dangerRow = 6, random = Random(5))
        world.startClassic(GameTheme.PASTEL)

        world.placeMacroStone(macroCol = 4, macroRow = 8, cracked = false)
        world.placeMacroStone(macroCol = 5, macroRow = 8, cracked = true)

        val destroyed = world.detonateBomb(centerX = 18, centerY = 34, radius = 10)
        assertTrue(destroyed > 0)
        assertEquals(0, world.countRemainingCrackedStones())
    }

    @Test
    fun `color cleaner removes only targeted sand color`() {
        val world = SandWorld(width = 24, height = 24, grainScale = 4, dangerRow = 4, random = Random(6))
        world.startClassic(GameTheme.SAHARA)

        world.cellTypes[23 * 24 + 2] = SandWorld.CELL_SAND
        world.colorIndices[23 * 24 + 2] = 0

        world.cellTypes[23 * 24 + 10] = SandWorld.CELL_SAND
        world.colorIndices[23 * 24 + 10] = 3

        assertTrue(world.clearAllSandOfColorAt(2, 23))
        assertEquals(SandWorld.CELL_EMPTY, world.cellTypes[23 * 24 + 2])
        assertEquals(SandWorld.CELL_SAND, world.cellTypes[23 * 24 + 10])
    }

    @Test
    fun `block rotation preserves cell count and normalizes origin`() {
        for (shape in BlockShape.STANDARD_SHAPES) {
            val rotated = shape.rotatedClockwise()
            assertEquals(shape.cells.size, rotated.cells.size)
            assertEquals(0, rotated.cells.minOf { it.col })
            assertEquals(0, rotated.cells.minOf { it.row })
        }
    }

    @Test
    fun `all 12 adventure levels initialize cleanly without danger breach`() {
        val world = SandWorld(random = Random(7))
        for (level in AdventureCatalog.levels) {
            world.startAdventure(level)
            assertEquals(GameStatus.PLAYING, world.status)
            assertFalse("Initial preset sand/stones must not breach danger line", world.highestSandRow() < world.dangerRow)
        }
    }
}
