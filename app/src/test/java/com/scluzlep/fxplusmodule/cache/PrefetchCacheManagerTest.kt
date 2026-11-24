package com.scluzlep.fxplusmodule.cache

import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import com.scluzlep.fxplusmodule.config.PrefetchLimitMode
import com.scluzlep.fxplusmodule.xposed.FolderCacheLifecycleHook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PrefetchCacheManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun makeItems(count: Int, sizeEach: Long = 1024L, payloads: Map<Int, ByteArray> = emptyMap()): List<PrefetchItem> {
        return (0 until count).map { idx ->
            val bytes = payloads[idx] ?: ByteArray(sizeEach.toInt()) { (idx + 1).toByte() }
            PrefetchItem(
                catalogIdentity = "smb://nas/photos",
                path = "/photos/img_$idx.jpg",
                lastModifiedMs = 1_700_000_000_000L + idx,
                sizeBytes = bytes.size.toLong(),
                mimeType = "image/jpeg",
                isRemote = true,
                opener = { ByteArrayInputStream(bytes) }
            )
        }
    }

    @Test
    fun defaultCountOnlyInterleavesDirectionAndDownloadsWindow() {
        val baseDir = tempFolder.newFolder("cache_default")
        var cfg = ModuleConfig() // COUNT_ONLY, N+-3
        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            val items = makeItems(12)
            // Forward direction around index 5: N+1(6), N-1(4), N+2(7), N-2(3), N+3(8), N-3(2)
            val forwardIndices = PrefetchCacheManager.buildCandidateIndices(items.size, 5, direction = 1, maxDepth = 3)
            assertEquals(listOf(6, 4, 7, 3, 8, 2), forwardIndices)

            // Backward direction around index 5: N-1(4), N+1(6), N-2(3), N+2(7), N-3(2), N+3(8)
            val backwardIndices = PrefetchCacheManager.buildCandidateIndices(items.size, 5, direction = -1, maxDepth = 3)
            assertEquals(listOf(4, 6, 3, 7, 2, 8), backwardIndices)

            val scheduled = manager.scheduleWindow(items, currentIndex = 5, direction = 1)
            assertEquals(6, scheduled.size)

            waitUntilSettled(manager)
            for (idx in listOf(2, 3, 4, 6, 7, 8)) {
                assertNotNull("Expected index $idx cached", manager.peekCachedFile(items[idx]))
            }
            assertNull(manager.peekCachedFile(items[1]))
            assertNull(manager.peekCachedFile(items[9]))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun countOnlyIgnoresDiskCacheCapForActiveWindowAndEnforcesPhysicalSpaceGuard() {
        val baseDir = tempFolder.newFolder("cache_count_only")
        val smallCapConfig = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY,
            prefetchCount = 2,
            prefetchDiskCacheMb = ConfigContract.MIN_PREFETCH_DISK_CACHE_MB, // 32 MB
            prefetchMaxSingleFileMb = 0 // unlimited
        )
        val largeItemSize = 20L * 1024L * 1024L // 20 MB x 4 = 80 MB > 32 MB cap
        val items = (0 until 7).map { idx ->
            PrefetchItem(
                catalogIdentity = "smb://nas/raw",
                path = "/raw/dng_$idx.jpg",
                lastModifiedMs = 1_700_000_000_000L,
                sizeBytes = largeItemSize,
                isRemote = true,
                opener = { ByteArrayInputStream(ByteArray(16)) }
            )
        }
        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { smallCapConfig },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            val selected = manager.selectCandidates(items, currentIndex = 3, direction = 1, config = smallCapConfig)
            assertEquals(4, selected.size) // 4, 2, 5, 1 all selected regardless of 32 MB cap

            // Low physical free space (< 512 MB + itemSize) must block background download safely.
            val lowSpaceMgr = PrefetchCacheManager(
                baseCacheDir = tempFolder.newFolder("cache_low_space"),
                configProvider = { smallCapConfig },
                storageSpaceProvider = { 400L * 1024L * 1024L } // < 512 MB
            )
            try {
                lowSpaceMgr.scheduleWindow(items, currentIndex = 3, direction = 1)
                waitUntilSettled(lowSpaceMgr)
                assertEquals(0, lowSpaceMgr.stats().entryCount)
            } finally {
                lowSpaceMgr.shutdown()
            }
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun capacityOnlyExpandsBeyondPrefetchCountAndEvictsLru() {
        val baseDir = tempFolder.newFolder("cache_cap_only")
        val mib = ConfigContract.BYTES_PER_MIB
        // Budget 32 MB; each item 8 MB -> fits 4 candidates even though prefetchCount is 1.
        val cfg = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.CAPACITY_ONLY,
            prefetchCount = 1,
            prefetchDiskCacheMb = 32
        )
        val items = (0 until 15).map { idx ->
            PrefetchItem(
                catalogIdentity = "webdav://box",
                path = "/pics/$idx.jpg",
                lastModifiedMs = 1_000L,
                sizeBytes = 8L * mib,
                isRemote = true,
                opener = { ByteArrayInputStream(ByteArray(8)) }
            )
        }
        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            val selected = manager.selectCandidates(items, currentIndex = 7, direction = 1, config = cfg)
            assertEquals(listOf(items[8], items[6], items[9], items[5]), selected)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun bothModeStopsAtWhicheverLimitHitsFirst() {
        val baseDir = tempFolder.newFolder("cache_both")
        val mib = ConfigContract.BYTES_PER_MIB
        val items = (0 until 12).map { idx ->
            PrefetchItem(
                catalogIdentity = "smb://both",
                path = "/img/$idx.jpg",
                lastModifiedMs = 5000L,
                sizeBytes = 12L * mib,
                isRemote = true
            )
        }
        val capTight = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.BOTH,
            prefetchCount = 5, // up to 10 items
            prefetchDiskCacheMb = 32 // fits only 2 items (24 MB <= 32 MB, 36 MB > 32 MB)
        )
        val countTight = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.BOTH,
            prefetchCount = 1, // up to 2 items (24 MB)
            prefetchDiskCacheMb = 256
        )
        val manager = PrefetchCacheManager(baseCacheDir = baseDir, configProvider = { capTight })
        try {
            assertEquals(2, manager.selectCandidates(items, 5, 1, capTight).size)
            assertEquals(2, manager.selectCandidates(items, 5, 1, countTight).size)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun shrinkingDiskCacheBudgetEvictsOldestOutOfWindowFilesImmediately() {
        val baseDir = tempFolder.newFolder("cache_shrink")
        var cfg = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.CAPACITY_ONLY,
            prefetchDiskCacheMb = 64
        )
        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 8L * 1024L * 1024L * 1024L }
        )
        try {
            // Write 3 synthetic 20 MB .bin files directly in cacheRoot with distinct timestamps.
            val chunk = ByteArray(1024 * 1024)
            fun writeSyntheticBin(key: String, sizeMb: Int, mtime: Long): File {
                val f = File(manager.cacheRoot, "$key.bin")
                f.outputStream().use { out ->
                    repeat(sizeMb) { out.write(chunk) }
                }
                f.setLastModified(mtime)
                return f
            }

            val oldest = writeSyntheticBin("key_oldest", 20, 1_000_000L)
            val middle = writeSyntheticBin("key_middle", 20, 2_000_000L)
            val newest = writeSyntheticBin("key_newest", 20, 3_000_000L)

            assertEquals(60L * 1024L * 1024L, manager.stats().totalBytes)

            // Shrink budget to 32 MB -> oldest and middle must be evicted so total <= 32 MB.
            cfg = cfg.copy(prefetchDiskCacheMb = 32)
            manager.onConfigUpdated(cfg)

            assertFalse(oldest.exists())
            assertFalse(middle.exists())
            assertTrue(newest.exists())
            assertTrue(manager.stats().totalBytes <= 32L * 1024L * 1024L)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun singleFlightDedupOpensNetworkStreamOnceAcrossPrefetchAndForeground() {
        val baseDir = tempFolder.newFolder("cache_dedup")
        val cfg = ModuleConfig(prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY, prefetchCount = 1)
        val openCalls = AtomicInteger(0)
        val streamStarted = CountDownLatch(1)
        val allowFinish = CountDownLatch(1)
        val payload = "EXACT_ORIGINAL_JPEG_BYTES".toByteArray()

        val targetItem = PrefetchItem(
            catalogIdentity = "smb://server",
            path = "/a/1.jpg",
            lastModifiedMs = 12345L,
            sizeBytes = payload.size.toLong(),
            isRemote = true,
            opener = {
                openCalls.incrementAndGet()
                streamStarted.countDown()
                allowFinish.await(5, TimeUnit.SECONDS)
                ByteArrayInputStream(payload)
            }
        )
        val currentItem = PrefetchItem(
            catalogIdentity = "smb://server",
            path = "/a/0.jpg",
            lastModifiedMs = 12345L,
            sizeBytes = 4L,
            isRemote = true,
            opener = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }
        )

        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        val pool = Executors.newSingleThreadExecutor()
        try {
            manager.scheduleWindow(listOf(currentItem, targetItem), currentIndex = 0, direction = 1)
            assertTrue(streamStarted.await(5, TimeUnit.SECONDS))

            val fgFuture = pool.submit(Callable { manager.awaitOrDownload(targetItem) })
            Thread.sleep(60)
            allowFinish.countDown()

            val cached = fgFuture.get(5, TimeUnit.SECONDS)
            assertNotNull(cached)
            assertEquals(1, openCalls.get())
            assertEquals(String(payload), String(cached!!.readBytes()))
        } finally {
            pool.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun rapidSwipeCancelsOutOfWindowPrefetchAndCleansPartFiles() {
        val baseDir = tempFolder.newFolder("cache_cancel")
        val orphanPart = File(File(baseDir, ConfigContract.PREFETCH_CACHE_DIR_NAME).apply { mkdirs() }, "stale.part")
        orphanPart.writeText("orphan")

        val cfg = ModuleConfig(prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY, prefetchCount = 1)
        val blockingOpened = CountDownLatch(1)
        val blockedReadCancelled = CountDownLatch(1)

        val slowStream = object : InputStream() {
            @Volatile
            var closed = false
            override fun read(): Int {
                blockingOpened.countDown()
                while (!closed && !Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                blockedReadCancelled.countDown()
                return -1
            }

            override fun close() {
                closed = true
            }
        }

        val items = listOf(
            PrefetchItem("c", "/0.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }),
            PrefetchItem("c", "/1.jpg", 1L, 100L, isRemote = true, opener = { slowStream }),
            PrefetchItem("c", "/2.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(5, 6, 7, 8)) }),
            PrefetchItem("c", "/3.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(9, 10, 11, 12)) }),
            PrefetchItem("c", "/4.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(1, 1, 1, 1)) })
        )

        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            assertFalse("Orphan .part should be deleted on init", orphanPart.exists())

            manager.scheduleWindow(items, currentIndex = 0, direction = 1)
            assertTrue(blockingOpened.await(5, TimeUnit.SECONDS))

            // Rapid swipe to index 3 -> index 1 is outside N+-1 around 3 (which is {3,4,2}) and must cancel!
            manager.scheduleWindow(items, currentIndex = 3, direction = 1)
            assertTrue(blockedReadCancelled.await(5, TimeUnit.SECONDS))
            waitUntilSettled(manager)

            assertNull(manager.peekCachedFile(items[1]))
            assertNotNull(manager.peekCachedFile(items[4]))
            assertNotNull(manager.peekCachedFile(items[2]))

            val leftoverParts = manager.cacheRoot.listFiles()?.filter { ".part" in it.name }.orEmpty()
            assertTrue("No .part files should remain", leftoverParts.isEmpty())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun metadataChangeInvalidatesCacheKeyAndWifiOnlyBlocksBackground() {
        val baseDir = tempFolder.newFolder("cache_invalidation")
        val itemV1 = PrefetchItem("smb://nas", "/a.jpg", 1000L, 10L, isRemote = true)
        val itemV2 = PrefetchItem("smb://nas", "/a.jpg", 2000L, 10L, isRemote = true)
        assertNotEquals(itemV1.cacheKey, itemV2.cacheKey)

        val cfgWifiOnly = ModuleConfig(prefetchWifiOnly = true)
        val items = makeItems(4)
        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfgWifiOnly },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L },
            networkChecker = { false } // metered / non-Wi-Fi
        )
        try {
            val scheduled = manager.scheduleWindow(items, currentIndex = 1, direction = 1)
            assertTrue(scheduled.isEmpty())
            assertEquals(0, manager.stats().entryCount)

            // Explicit foreground awaitOrDownload still works even when background prefetch is paused.
            val fg = manager.awaitOrDownload(items[1])
            assertNotNull(fg)
            assertEquals(1, manager.stats().entryCount)

            manager.clearCache()
            assertEquals(0, manager.stats().entryCount)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun folderPrefixedStorageSupportsColdKeyLookupAndImmediateEvictionWithInFlightCancel() {
        val baseDir = tempFolder.newFolder("cache_folder_evict")
        val cfg = ModuleConfig(prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY, prefetchCount = 2)
        val blockingOpened = CountDownLatch(1)
        val blockedReadCancelled = CountDownLatch(1)

        val slowStream = object : InputStream() {
            @Volatile
            var closed = false
            override fun read(): Int {
                blockingOpened.countDown()
                while (!closed && !Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                blockedReadCancelled.countDown()
                return -1
            }

            override fun close() {
                closed = true
            }
        }

        val folderA = "SmbCatalog:nas|/photos/trip"
        val folderB = "SmbCatalog:nas|/photos/work"
        val itemA1 = PrefetchItem("SmbCatalog:nas", "/photos/trip/1.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }, folderKey = folderA)
        val itemA2 = PrefetchItem("SmbCatalog:nas", "/photos/trip/2.jpg", 1L, 100L, isRemote = true, opener = { slowStream }, folderKey = folderA)
        val itemB1 = PrefetchItem("SmbCatalog:nas", "/photos/work/1.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(9, 8, 7, 6)) }, folderKey = folderB)

        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            val cachedA1 = manager.awaitOrDownload(itemA1)
            val cachedB1 = manager.awaitOrDownload(itemB1)
            assertNotNull(cachedA1)
            assertNotNull(cachedB1)

            val expectedHashA = PrefetchCacheManager.computeFolderHash(folderA)
            assertEquals(16, expectedHashA.length)
            assertEquals("${expectedHashA}_${itemA1.cacheKey}.bin", cachedA1!!.name)

            // Cold cache manager (no keyToFolderHash warmed) resolves *_<key>.bin cleanly.
            val coldManager = PrefetchCacheManager(
                baseCacheDir = baseDir,
                configProvider = { cfg },
                storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
            )
            try {
                val coldHit = coldManager.peekCachedFileByKey(itemA1.cacheKey, itemA1.sizeBytes)
                assertNotNull(coldHit)
                assertEquals(cachedA1.name, coldHit!!.name)
            } finally {
                coldManager.shutdown()
            }

            // Start background prefetch in folderA that blocks on slowStream.
            manager.scheduleWindow(listOf(itemA1, itemA2), currentIndex = 0, direction = 1, sessionId = "sA")
            assertTrue(blockingOpened.await(5, TimeUnit.SECONDS))

            // Leave folderA while folderB remains open -> folderA cached & in-flight must vanish immediately.
            val evicted = manager.retainOnlyFolders(setOf(folderB))
            assertTrue(blockedReadCancelled.await(5, TimeUnit.SECONDS))
            waitUntilSettled(manager)

            assertTrue(itemA1.cacheKey in evicted)
            assertTrue(itemA2.cacheKey in evicted)
            assertFalse(itemB1.cacheKey in evicted)
            assertNull(manager.peekCachedFile(itemA1))
            assertNull(manager.peekCachedFile(itemA2))
            assertNotNull(manager.peekCachedFile(itemB1))
            assertTrue(cachedB1!!.exists())
            assertFalse(cachedA1.exists())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun sameFolderRetentionAcrossViewerCloseAndMultiTabUntilLastTabLeaves() {
        val baseDir = tempFolder.newFolder("cache_multi_tab")
        val cfg = ModuleConfig(prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY, prefetchCount = 2)
        val folderA = "SmbCatalog:nas|albumA"
        val folderB = "SmbCatalog:nas|albumB"
        val folderC = "SmbCatalog:nas|albumC"

        val itemsA = (0 until 3).map { idx ->
            PrefetchItem("SmbCatalog:nas", "/albumA/$idx.jpg", 10L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(1, 2, 3, idx.toByte())) }, folderKey = folderA)
        }
        val itemB = PrefetchItem("SmbCatalog:nas", "/albumB/0.jpg", 10L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(5, 6, 7, 8)) }, folderKey = folderB)

        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            manager.beginSession("viewer-1")
            manager.scheduleWindow(itemsA, currentIndex = 0, direction = 1, sessionId = "viewer-1")
            waitUntilSettled(manager)
            assertNotNull(manager.peekCachedFile(itemsA[1]))

            // Viewer closes while Explorer stays in folderA -> folderA cache is retained.
            manager.endSession("viewer-1")
            val afterViewerClose = manager.retainOnlyFolders(setOf(folderA))
            assertTrue(afterViewerClose.isEmpty())
            assertNotNull(manager.peekCachedFile(itemsA[1]))

            // Multi-tab: Tab 1 navigates to folderB, Tab 2 is still in folderA -> retained!
            manager.awaitOrDownload(itemB)
            val whileTab2Open = manager.retainOnlyFolders(setOf(folderB, folderA))
            assertTrue(whileTab2Open.isEmpty())
            assertNotNull(manager.peekCachedFile(itemsA[1]))
            assertNotNull(manager.peekCachedFile(itemB))

            // Last tab leaves folderA (Tab 2 navigates to folderC) -> folderA immediately evicted!
            val afterLastTabLeaves = manager.retainOnlyFolders(setOf(folderB, folderC))
            assertTrue(itemsA[1].cacheKey in afterLastTabLeaves)
            assertNull(manager.peekCachedFile(itemsA[1]))
            assertNotNull(manager.peekCachedFile(itemB))
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun retainOnlyFoldersPurgesLegacyUnprefixedFilesAndNormalizesCanonicalPaths() {
        val baseDir = tempFolder.newFolder("cache_legacy_purge")
        val cfg = ModuleConfig()
        val activeFolder = "SmbCatalog:nas|photos"
        val activeItem = PrefetchItem("SmbCatalog:nas", "/photos/keep.jpg", 1L, 4L, isRemote = true, opener = { ByteArrayInputStream(byteArrayOf(7, 7, 7, 7)) }, folderKey = activeFolder)

        val manager = PrefetchCacheManager(
            baseCacheDir = baseDir,
            configProvider = { cfg },
            storageSpaceProvider = { 4L * 1024L * 1024L * 1024L }
        )
        try {
            val activeBin = manager.awaitOrDownload(activeItem)
            assertNotNull(activeBin)

            // Write legacy v1.0 unprefixed .bin & stray .part files directly in cacheRoot.
            val legacyShaBin = File(manager.cacheRoot, "a".repeat(64) + ".bin").apply { writeText("v10") }
            val legacyNamedBin = File(manager.cacheRoot, "legacy_unprefixed.bin").apply { writeText("old") }
            val strayPart = File(manager.cacheRoot, "stale_unprefixed.part.12345678").apply { writeText("part") }

            val evicted = manager.retainOnlyFolders(setOf(activeFolder))
            assertFalse(legacyShaBin.exists())
            assertFalse(legacyNamedBin.exists())
            assertFalse(strayPart.exists())
            assertTrue(activeBin!!.exists())
            assertTrue("a".repeat(64) in evicted)
            assertTrue("legacy_unprefixed" in evicted)

            // Canonical FX path normalization checks
            open class FakeCatalog(private val id: String) {
                override fun toString(): String = id
            }
            class FakeIdCatalog(private val id: String) {
                override fun toString(): String = id
            }
            val isCatalog: (Any) -> Boolean = { it is FakeCatalog }
            val smb = FakeCatalog("smb://nas")
            val archive = FakeCatalog("zip://pack")

            val explorerPath = arrayOf<Any>(FakeIdCatalog("network"), smb, "photos", "2026")
            val itemParentPath = arrayOf<Any>(smb, "/photos/", "2026")
            val expectedKey = "${FakeCatalog::class.java.name}:smb://nas|photos/2026"
            assertEquals(expectedKey, FolderCacheLifecycleHook.canonicalFolderKeyFromElements(explorerPath, isCatalog))
            assertEquals(expectedKey, FolderCacheLifecycleHook.canonicalFolderKeyFromElements(itemParentPath, isCatalog))

            // Home / Bookmarks / Network list (no DirectoryCatalog) returns null.
            assertNull(FolderCacheLifecycleHook.canonicalFolderKeyFromElements(arrayOf<Any>(FakeIdCatalog("home")), isCatalog))
            assertNull(FolderCacheLifecycleHook.canonicalFolderKeyFromElements(emptyArray<Any>(), isCatalog))

            // Catalog root and nested catalog anchoring.
            assertEquals("${FakeCatalog::class.java.name}:smb://nas|", FolderCacheLifecycleHook.canonicalFolderKeyFromElements(arrayOf(FakeIdCatalog("network"), smb), isCatalog))
            assertEquals("${FakeCatalog::class.java.name}:zip://pack|inner", FolderCacheLifecycleHook.canonicalFolderKeyFromElements(arrayOf(smb, "a.zip", archive, "inner"), isCatalog))
        } finally {
            manager.shutdown()
        }
    }

    private fun waitUntilSettled(manager: PrefetchCacheManager) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (manager.inFlightCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }
}
