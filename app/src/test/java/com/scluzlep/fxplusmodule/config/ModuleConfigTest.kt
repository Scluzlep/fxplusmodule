package com.scluzlep.fxplusmodule.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleConfigTest {

    @Test
    fun defaultsMatchSpecV2AndV3() {
        val cfg = ModuleConfig()
        assertTrue(cfg.unlockLargeImageSwipe)
        assertEquals(0, cfg.maxSwipeImageSizeMb)
        assertEquals(Long.MAX_VALUE, cfg.maxSwipeImageSizeBytes())
        assertTrue(cfg.enableAdjacentPrefetch)
        assertEquals(PrefetchLimitMode.COUNT_ONLY, cfg.prefetchLimitMode)
        assertEquals(3, cfg.prefetchCount)
        assertEquals(256, cfg.prefetchDiskCacheMb)
        assertEquals(0, cfg.prefetchMaxSingleFileMb)
        assertEquals(Long.MAX_VALUE, cfg.prefetchMaxSingleFileBytes())
        assertEquals(2, cfg.prefetchConcurrency)
        assertFalse(cfg.prefetchWifiOnly)
        assertTrue(cfg.enableRemoteThumbnails)
        assertEquals(64, cfg.maxRemoteThumbnailSizeMb)
        assertFalse(cfg.highResThumbnails)
        assertTrue(cfg.disableSoftwareImageRendering)
        assertTrue(cfg.enableHardwareGifPlayback)
        assertTrue(cfg.unlockTextPreviewLimits)
        assertTrue(cfg.enableAndroidDataAccess)
        assertFalse(cfg.debugLogging)
    }

    @Test
    fun v11RenderAndGifSwitchesSerializeAndCompareProperly() {
        val defaultFromEmpty = ModuleConfig.fromMap(emptyMap<String, Any>())
        assertTrue(defaultFromEmpty.disableSoftwareImageRendering)
        assertTrue(defaultFromEmpty.enableHardwareGifPlayback)

        val customized = ModuleConfig(
            disableSoftwareImageRendering = false,
            enableHardwareGifPlayback = false,
            updatedAtMs = 1_500L
        )
        val encoded = ModuleConfig.toMap(customized)
        assertEquals(false, encoded[ConfigContract.KEY_DISABLE_SOFTWARE_IMAGE_RENDERING])
        assertEquals(false, encoded[ConfigContract.KEY_ENABLE_HARDWARE_GIF_PLAYBACK])

        val decoded = ModuleConfig.fromMap(encoded)
        assertEquals(customized.sanitized(), decoded)
        assertTrue(customized.sameSettingsAs(customized.copy(updatedAtMs = 9_999L)))
        assertFalse(customized.sameSettingsAs(customized.copy(disableSoftwareImageRendering = true)))
        assertFalse(customized.sameSettingsAs(customized.copy(enableHardwareGifPlayback = true)))
    }

    @Test
    fun customPrefetchCountAndDiskCacheBoundsAreClampedSafely() {
        val tooLow = ModuleConfig(
            prefetchCount = 0,
            prefetchDiskCacheMb = 4,
            prefetchConcurrency = 0,
            maxSwipeImageSizeMb = -5,
            maxRemoteThumbnailSizeMb = -1
        ).sanitized()
        assertEquals(ConfigContract.MIN_PREFETCH_COUNT, tooLow.prefetchCount)
        assertEquals(ConfigContract.MIN_PREFETCH_DISK_CACHE_MB, tooLow.prefetchDiskCacheMb)
        assertEquals(ConfigContract.MIN_PREFETCH_CONCURRENCY, tooLow.prefetchConcurrency)
        assertEquals(0, tooLow.maxSwipeImageSizeMb)
        assertEquals(0, tooLow.maxRemoteThumbnailSizeMb)

        val tooHigh = ModuleConfig(
            prefetchCount = 99,
            prefetchDiskCacheMb = 99_999,
            prefetchConcurrency = 16
        ).sanitized()
        assertEquals(ConfigContract.MAX_PREFETCH_COUNT, tooHigh.prefetchCount)
        assertEquals(ConfigContract.MAX_PREFETCH_DISK_CACHE_MB, tooHigh.prefetchDiskCacheMb)
        assertEquals(ConfigContract.MAX_PREFETCH_CONCURRENCY, tooHigh.prefetchConcurrency)

        val customValid = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.BOTH,
            prefetchCount = 11,
            prefetchDiskCacheMb = 4096,
            prefetchMaxSingleFileMb = 512
        ).sanitized()
        assertEquals(11, customValid.prefetchCount)
        assertEquals(4096, customValid.prefetchDiskCacheMb)
        assertEquals(4096L * ConfigContract.BYTES_PER_MIB, customValid.prefetchDiskCacheBytes())
        assertEquals(512L * ConfigContract.BYTES_PER_MIB, customValid.effectivePrefetchSingleFileBytes())
    }

    @Test
    fun effectiveSingleFileCapRespectsLimitMode() {
        val cfg = ModuleConfig(
            prefetchDiskCacheMb = 128,
            prefetchMaxSingleFileMb = 0 // unlimited
        )
        assertEquals(Long.MAX_VALUE, cfg.effectivePrefetchSingleFileBytes(PrefetchLimitMode.COUNT_ONLY))
        assertEquals(128L * ConfigContract.BYTES_PER_MIB, cfg.effectivePrefetchSingleFileBytes(PrefetchLimitMode.CAPACITY_ONLY))
        assertEquals(128L * ConfigContract.BYTES_PER_MIB, cfg.effectivePrefetchSingleFileBytes(PrefetchLimitMode.BOTH))
    }

    @Test
    fun mapRoundTripAndTimestampMergeSelectNewerConfig() {
        val older = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.COUNT_ONLY,
            prefetchCount = 3,
            prefetchDiskCacheMb = 256,
            enableAndroidDataAccess = true,
            updatedAtMs = 1_000L
        )
        val newer = ModuleConfig(
            prefetchLimitMode = PrefetchLimitMode.BOTH,
            prefetchCount = 9,
            prefetchDiskCacheMb = 1024,
            enableAndroidDataAccess = false,
            updatedAtMs = 2_000L
        )

        assertEquals(newer.sanitized(), ModuleConfig.mergeByTimestamp(older, newer))
        assertEquals(newer.sanitized(), ModuleConfig.mergeByTimestamp(newer, older))

        val decoded = ModuleConfig.fromMap(ModuleConfig.toMap(newer))
        assertEquals(newer.sanitized(), decoded)
        assertEquals(PrefetchLimitMode.COUNT_ONLY, PrefetchLimitMode.fromString("invalid_mode"))
    }
}
