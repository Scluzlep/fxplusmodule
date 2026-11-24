package com.scluzlep.fxplusmodule.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class TouchImageHardwareHookTest {

    @Test
    fun constantsAndTinyGifSentinelAreValid() {
        assertEquals(96L * 1024L * 1024L, TouchImageHardwareHook.SAFE_SINGLE_BITMAP_BYTES)
        assertEquals(2048, TouchImageHardwareHook.DEFAULT_TILE_SIZE)
        assertEquals(24, TouchImageHardwareHook.MAX_CACHED_TILES_PER_VIEW)

        assertEquals(43, TouchImageHardwareHook.TINY_GIF_1X1.size)
        val decoded = ImageIO.read(ByteArrayInputStream(TouchImageHardwareHook.TINY_GIF_1X1))
        assertNotNull(decoded)
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }

    @Test
    fun needsHardwareTilingHonorsDimensionsByteBudgetAndCanvasBounds() {
        assertFalse(TouchImageHardwareHook.needsHardwareTiling(2160, 2160, 18_662_400L, 8192))
        assertFalse(
            TouchImageHardwareHook.needsHardwareTiling(
                8192,
                4096,
                TouchImageHardwareHook.SAFE_SINGLE_BITMAP_BYTES,
                8192
            )
        )

        assertTrue(TouchImageHardwareHook.needsHardwareTiling(8193, 2000, 1024L, 8192))
        assertTrue(TouchImageHardwareHook.needsHardwareTiling(2000, 8193, 1024L, 8192))
        assertTrue(
            TouchImageHardwareHook.needsHardwareTiling(
                6000,
                5000,
                TouchImageHardwareHook.SAFE_SINGLE_BITMAP_BYTES + 1L,
                8192
            )
        )

        // maxCanvasDim inside 2048..16384 is honored directly
        assertTrue(TouchImageHardwareHook.needsHardwareTiling(4097, 1000, 1024L, 4096))
        assertFalse(TouchImageHardwareHook.needsHardwareTiling(12000, 1000, 48_000_000L, 16384))

        // Out-of-range canvas limits fall back safely to 8192
        assertFalse(TouchImageHardwareHook.needsHardwareTiling(8192, 1000, 32_768_000L, 0))
        assertFalse(TouchImageHardwareHook.needsHardwareTiling(8192, 1000, 32_768_000L, 1024))
        assertTrue(TouchImageHardwareHook.needsHardwareTiling(8193, 1000, 32_772_000L, 32766))
    }

    @Test
    fun buildTileSpecsPartitionsImageWithSeamlessOnePixelGutter() {
        val width = 5000
        val height = 3000
        val tiles = TouchImageHardwareHook.buildTileSpecs(
            bitmapWidth = width,
            bitmapHeight = height,
            tileSize = 2048,
            maxDim = 8192
        )

        // 3 columns (0..2048, 2048..4096, 4096..5000) x 2 rows (0..2048, 2048..3000)
        assertEquals(6, tiles.size)

        val tile00 = tiles.first { it.row == 0 && it.col == 0 }
        assertEquals(0, tile00.rawLeft)
        assertEquals(0, tile00.rawTop)
        assertEquals(2049, tile00.rawRight)
        assertEquals(2049, tile00.rawBottom)
        assertEquals(0, tile00.srcSubLeft)
        assertEquals(0, tile00.srcSubTop)
        assertEquals(2048, tile00.srcSubRight)
        assertEquals(2048, tile00.srcSubBottom)
        assertEquals(0, tile00.dstLeft)
        assertEquals(0, tile00.dstTop)
        assertEquals(2048, tile00.dstRight)
        assertEquals(2048, tile00.dstBottom)

        val tile11 = tiles.first { it.row == 1 && it.col == 1 }
        assertEquals(2047, tile11.rawLeft)
        assertEquals(2047, tile11.rawTop)
        assertEquals(4097, tile11.rawRight)
        assertEquals(3000, tile11.rawBottom)
        assertEquals(1, tile11.srcSubLeft)
        assertEquals(1, tile11.srcSubTop)
        assertEquals(2049, tile11.srcSubRight)
        assertEquals(953, tile11.srcSubBottom)
        assertEquals(2048, tile11.dstLeft)
        assertEquals(2048, tile11.dstTop)
        assertEquals(4096, tile11.dstRight)
        assertEquals(3000, tile11.dstBottom)

        verifyPartitionInvariants(tiles, width, height)
    }

    @Test
    fun buildTileSpecsClampsTileSizeSoGutterNeverExceedsMaxDim() {
        val tiles = TouchImageHardwareHook.buildTileSpecs(
            bitmapWidth = 5000,
            bitmapHeight = 2500,
            tileSize = 2048,
            maxDim = 2048
        )
        assertTrue(tiles.isNotEmpty())
        for (tile in tiles) {
            assertTrue("rawWidth ${tile.rawWidth} exceeded 2048", tile.rawWidth <= 2048)
            assertTrue("rawHeight ${tile.rawHeight} exceeded 2048", tile.rawHeight <= 2048)
        }
        verifyPartitionInvariants(tiles, 5000, 2500)

        val tallStrip = TouchImageHardwareHook.buildTileSpecs(1000, 12000, 2048, 8192)
        assertEquals(6, tallStrip.size)
        verifyPartitionInvariants(tallStrip, 1000, 12000)

        assertTrue(TouchImageHardwareHook.buildTileSpecs(0, 100, 2048, 8192).isEmpty())
        assertTrue(TouchImageHardwareHook.buildTileSpecs(100, -5, 2048, 8192).isEmpty())
    }

    @Test
    fun selectVisibleTilesFiltersExactIntersectingTiles() {
        val tiles = TouchImageHardwareHook.buildTileSpecs(5000, 3000, 2048, 8192)

        val firstOnly = TouchImageHardwareHook.selectVisibleTiles(tiles, 0, 0, 2048, 2048)
        assertEquals(1, firstOnly.size)
        assertEquals(0 to 0, firstOnly[0].row to firstOnly[0].col)

        val crossAllFour = TouchImageHardwareHook.selectVisibleTiles(tiles, 2040f, 2040f, 2060f, 2060f)
        assertEquals(4, crossAllFour.size)

        val rightEdgeOnly = TouchImageHardwareHook.selectVisibleTiles(tiles, 4096f, 2048f, 5000f, 3000f)
        assertEquals(1, rightEdgeOnly.size)
        assertEquals(1 to 2, rightEdgeOnly[0].row to rightEdgeOnly[0].col)

        assertTrue(TouchImageHardwareHook.selectVisibleTiles(tiles, 100f, 100f, 100f, 500f).isEmpty())
        assertTrue(TouchImageHardwareHook.selectVisibleTiles(tiles, 500f, 100f, 100f, 500f).isEmpty())
        assertTrue(TouchImageHardwareHook.selectVisibleTiles(tiles, Float.NaN, 0f, 100f, 100f).isEmpty())
        assertTrue(TouchImageHardwareHook.selectVisibleTiles(tiles, 6000f, 0f, 7000f, 1000f).isEmpty())
    }

    @Test
    fun boundedTileLruEnforcesMaxEntriesAndProtectsRecentlyUsedAndVisibleKeys() {
        val lru = TouchImageHardwareHook.BoundedTileLru<Int, String>(
            TouchImageHardwareHook.MAX_CACHED_TILES_PER_VIEW
        )
        val evictedKeys = mutableListOf<Int>()

        for (i in 0 until TouchImageHardwareHook.MAX_CACHED_TILES_PER_VIEW) {
            lru.put(i, "tile-$i") { k, _ -> evictedKeys += k }
        }
        assertEquals(24, lru.size)
        assertTrue(evictedKeys.isEmpty())

        // Touch key 0 so key 1 becomes eldest
        assertEquals("tile-0", lru.get(0))

        // Protect key 1 as currently visible -> key 2 must be evicted instead
        lru.put(24, "tile-24", protectedKeys = setOf(1, 24)) { k, _ -> evictedKeys += k }
        assertEquals(24, lru.size)
        assertEquals(listOf(2), evictedKeys)
        assertEquals("tile-0", lru.get(0))
        assertEquals("tile-1", lru.get(1))
        assertEquals("tile-24", lru.get(24))
    }

    @Test
    fun configChangesAndHotReloadResetAreSafeOnJvm() {
        TouchImageHardwareHook.onConfigChanged(hardwareImageEnabled = true, hardwareGifEnabled = true)
        TouchImageHardwareHook.onConfigChanged(hardwareImageEnabled = false, hardwareGifEnabled = false)
        TouchImageHardwareHook.onConfigChanged(hardwareImageEnabled = true, hardwareGifEnabled = false)
        TouchImageHardwareHook.clearOnHotReload()

        val cl = ClassLoader.getSystemClassLoader()
        val symbols9012 = FxSymbolResolver.resolve(cl, 9012L)
        val symbols9108 = FxSymbolResolver.resolve(cl, 9108L)
        assertEquals(FxProfile.FX_9012, symbols9012.profile)
        assertEquals(FxProfile.FX_9108, symbols9108.profile)
    }

    private fun verifyPartitionInvariants(
        tiles: List<TouchImageHardwareHook.TileSpec>,
        width: Int,
        height: Int
    ) {
        var totalDstArea = 0L
        for (i in tiles.indices) {
            val a = tiles[i]
            assertEquals(a.dstRight - a.dstLeft, a.srcSubRight - a.srcSubLeft)
            assertEquals(a.dstBottom - a.dstTop, a.srcSubBottom - a.srcSubTop)
            assertTrue(a.rawLeft >= 0 && a.rawTop >= 0)
            assertTrue(a.rawRight <= width && a.rawBottom <= height)
            assertTrue(a.srcSubLeft >= 0 && a.srcSubTop >= 0)
            assertTrue(a.srcSubRight <= a.rawWidth && a.srcSubBottom <= a.rawHeight)
            totalDstArea += (a.dstRight - a.dstLeft).toLong() * (a.dstBottom - a.dstTop).toLong()

            for (j in i + 1 until tiles.size) {
                val b = tiles[j]
                val overlapX = a.dstLeft < b.dstRight && b.dstLeft < a.dstRight
                val overlapY = a.dstTop < b.dstBottom && b.dstTop < a.dstBottom
                assertFalse("Tile dst rects overlap: $a vs $b", overlapX && overlapY)
            }
        }
        assertEquals(width.toLong() * height.toLong(), totalDstArea)
    }
}
