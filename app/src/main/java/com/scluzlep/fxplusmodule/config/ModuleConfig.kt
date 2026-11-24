package com.scluzlep.fxplusmodule.config

import android.content.SharedPreferences

/**
 * Immutable module configuration persisted in FX's in-process
 * `SharedPreferences("fxplusmodule_local", MODE_PRIVATE)`.
 */
data class ModuleConfig(
    val unlockLargeImageSwipe: Boolean = ConfigContract.DEFAULT_UNLOCK_LARGE_IMAGE_SWIPE,
    val maxSwipeImageSizeMb: Int = ConfigContract.DEFAULT_MAX_SWIPE_IMAGE_SIZE_MB,
    val enableAdjacentPrefetch: Boolean = ConfigContract.DEFAULT_ENABLE_ADJACENT_PREFETCH,
    val prefetchLimitMode: PrefetchLimitMode = ConfigContract.DEFAULT_PREFETCH_LIMIT_MODE,
    val prefetchCount: Int = ConfigContract.DEFAULT_PREFETCH_COUNT,
    val prefetchDiskCacheMb: Int = ConfigContract.DEFAULT_PREFETCH_DISK_CACHE_MB,
    val prefetchMaxSingleFileMb: Int = ConfigContract.DEFAULT_PREFETCH_MAX_SINGLE_FILE_MB,
    val prefetchConcurrency: Int = ConfigContract.DEFAULT_PREFETCH_CONCURRENCY,
    val prefetchWifiOnly: Boolean = ConfigContract.DEFAULT_PREFETCH_WIFI_ONLY,
    val enableRemoteThumbnails: Boolean = ConfigContract.DEFAULT_ENABLE_REMOTE_THUMBNAILS,
    val maxRemoteThumbnailSizeMb: Int = ConfigContract.DEFAULT_MAX_REMOTE_THUMBNAIL_SIZE_MB,
    val highResThumbnails: Boolean = ConfigContract.DEFAULT_HIGH_RES_THUMBNAILS,
    val disableSoftwareImageRendering: Boolean = ConfigContract.DEFAULT_DISABLE_SOFTWARE_IMAGE_RENDERING,
    val enableHardwareGifPlayback: Boolean = ConfigContract.DEFAULT_ENABLE_HARDWARE_GIF_PLAYBACK,
    val unlockTextPreviewLimits: Boolean = ConfigContract.DEFAULT_UNLOCK_TEXT_PREVIEW_LIMITS,
    val enableAndroidDataAccess: Boolean = ConfigContract.DEFAULT_ENABLE_ANDROID_DATA_ACCESS,
    val customGoogleDriveClientId: String = ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_ID,
    val customGoogleDriveClientSecret: String = ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_SECRET,
    val debugLogging: Boolean = ConfigContract.DEFAULT_DEBUG_LOGGING,
    val updatedAtMs: Long = 0L
) {
    fun sanitized(): ModuleConfig = copy(
        maxSwipeImageSizeMb = maxSwipeImageSizeMb.coerceAtLeast(ConfigContract.MIN_MAX_SWIPE_IMAGE_SIZE_MB),
        prefetchCount = prefetchCount.coerceIn(
            ConfigContract.MIN_PREFETCH_COUNT,
            ConfigContract.MAX_PREFETCH_COUNT
        ),
        prefetchDiskCacheMb = prefetchDiskCacheMb.coerceIn(
            ConfigContract.MIN_PREFETCH_DISK_CACHE_MB,
            ConfigContract.MAX_PREFETCH_DISK_CACHE_MB
        ),
        prefetchMaxSingleFileMb = prefetchMaxSingleFileMb.coerceAtLeast(
            ConfigContract.MIN_PREFETCH_MAX_SINGLE_FILE_MB
        ),
        prefetchConcurrency = prefetchConcurrency.coerceIn(
            ConfigContract.MIN_PREFETCH_CONCURRENCY,
            ConfigContract.MAX_PREFETCH_CONCURRENCY
        ),
        maxRemoteThumbnailSizeMb = maxRemoteThumbnailSizeMb.coerceAtLeast(
            ConfigContract.MIN_MAX_REMOTE_THUMBNAIL_SIZE_MB
        ),
        customGoogleDriveClientId = customGoogleDriveClientId.trim(),
        customGoogleDriveClientSecret = customGoogleDriveClientSecret.trim(),
        updatedAtMs = updatedAtMs.coerceAtLeast(0L)
    )

    fun maxSwipeImageSizeBytes(): Long {
        val mb = maxSwipeImageSizeMb.coerceAtLeast(0)
        return if (mb == 0) Long.MAX_VALUE else mb.toLong() * ConfigContract.BYTES_PER_MIB
    }

    fun prefetchDiskCacheBytes(): Long {
        val mb = prefetchDiskCacheMb.coerceIn(
            ConfigContract.MIN_PREFETCH_DISK_CACHE_MB,
            ConfigContract.MAX_PREFETCH_DISK_CACHE_MB
        )
        return mb.toLong() * ConfigContract.BYTES_PER_MIB
    }

    /**
     * Single-file byte cap configured by the user. `0` means unlimited (`Long.MAX_VALUE`)
     * so COUNT_ONLY never skips large images inside the N+-k window.
     */
    fun prefetchMaxSingleFileBytes(): Long {
        val mb = prefetchMaxSingleFileMb.coerceAtLeast(0)
        return if (mb == 0) Long.MAX_VALUE else mb.toLong() * ConfigContract.BYTES_PER_MIB
    }

    fun effectivePrefetchSingleFileBytes(mode: PrefetchLimitMode = prefetchLimitMode): Long {
        val configuredCap = prefetchMaxSingleFileBytes()
        return when (mode) {
            PrefetchLimitMode.COUNT_ONLY -> configuredCap
            PrefetchLimitMode.CAPACITY_ONLY,
            PrefetchLimitMode.BOTH -> minOf(configuredCap, prefetchDiskCacheBytes())
        }
    }

    fun maxRemoteThumbnailSizeBytes(): Long {
        val mb = maxRemoteThumbnailSizeMb.coerceAtLeast(0)
        return if (mb == 0) Long.MAX_VALUE else mb.toLong() * ConfigContract.BYTES_PER_MIB
    }

    fun withMonotonicTimestamp(
        previousMs: Long = updatedAtMs,
        nowMs: Long = System.currentTimeMillis()
    ): ModuleConfig {
        return sanitized().copy(updatedAtMs = nextTimestamp(previousMs, nowMs))
    }

    fun sameSettingsAs(other: ModuleConfig): Boolean {
        val a = sanitized()
        val b = other.sanitized()
        return a.unlockLargeImageSwipe == b.unlockLargeImageSwipe &&
            a.maxSwipeImageSizeMb == b.maxSwipeImageSizeMb &&
            a.enableAdjacentPrefetch == b.enableAdjacentPrefetch &&
            a.prefetchLimitMode == b.prefetchLimitMode &&
            a.prefetchCount == b.prefetchCount &&
            a.prefetchDiskCacheMb == b.prefetchDiskCacheMb &&
            a.prefetchMaxSingleFileMb == b.prefetchMaxSingleFileMb &&
            a.prefetchConcurrency == b.prefetchConcurrency &&
            a.prefetchWifiOnly == b.prefetchWifiOnly &&
            a.enableRemoteThumbnails == b.enableRemoteThumbnails &&
            a.maxRemoteThumbnailSizeMb == b.maxRemoteThumbnailSizeMb &&
            a.highResThumbnails == b.highResThumbnails &&
            a.disableSoftwareImageRendering == b.disableSoftwareImageRendering &&
            a.enableHardwareGifPlayback == b.enableHardwareGifPlayback &&
            a.unlockTextPreviewLimits == b.unlockTextPreviewLimits &&
            a.enableAndroidDataAccess == b.enableAndroidDataAccess &&
            a.customGoogleDriveClientId == b.customGoogleDriveClientId &&
            a.customGoogleDriveClientSecret == b.customGoogleDriveClientSecret &&
            a.debugLogging == b.debugLogging
    }

    companion object {
        fun nextTimestamp(previousMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
            return maxOf(nowMs, previousMs + 1L).coerceAtLeast(1L)
        }

        fun mergeByTimestamp(first: ModuleConfig, second: ModuleConfig): ModuleConfig {
            return if (second.updatedAtMs > first.updatedAtMs) {
                second.sanitized()
            } else {
                first.sanitized()
            }
        }

        fun fromMap(values: Map<String, *>): ModuleConfig {
            return ModuleConfig(
                unlockLargeImageSwipe = readBool(
                    values[ConfigContract.KEY_UNLOCK_LARGE_IMAGE_SWIPE],
                    ConfigContract.DEFAULT_UNLOCK_LARGE_IMAGE_SWIPE
                ),
                maxSwipeImageSizeMb = readInt(
                    values[ConfigContract.KEY_MAX_SWIPE_IMAGE_SIZE_MB],
                    ConfigContract.DEFAULT_MAX_SWIPE_IMAGE_SIZE_MB
                ),
                enableAdjacentPrefetch = readBool(
                    values[ConfigContract.KEY_ENABLE_ADJACENT_PREFETCH],
                    ConfigContract.DEFAULT_ENABLE_ADJACENT_PREFETCH
                ),
                prefetchLimitMode = PrefetchLimitMode.fromString(
                    values[ConfigContract.KEY_PREFETCH_LIMIT_MODE]?.toString()
                ),
                prefetchCount = readInt(
                    values[ConfigContract.KEY_PREFETCH_COUNT],
                    ConfigContract.DEFAULT_PREFETCH_COUNT
                ),
                prefetchDiskCacheMb = readInt(
                    values[ConfigContract.KEY_PREFETCH_DISK_CACHE_MB],
                    ConfigContract.DEFAULT_PREFETCH_DISK_CACHE_MB
                ),
                prefetchMaxSingleFileMb = readInt(
                    values[ConfigContract.KEY_PREFETCH_MAX_SINGLE_FILE_MB],
                    ConfigContract.DEFAULT_PREFETCH_MAX_SINGLE_FILE_MB
                ),
                prefetchConcurrency = readInt(
                    values[ConfigContract.KEY_PREFETCH_CONCURRENCY],
                    ConfigContract.DEFAULT_PREFETCH_CONCURRENCY
                ),
                prefetchWifiOnly = readBool(
                    values[ConfigContract.KEY_PREFETCH_WIFI_ONLY],
                    ConfigContract.DEFAULT_PREFETCH_WIFI_ONLY
                ),
                enableRemoteThumbnails = readBool(
                    values[ConfigContract.KEY_ENABLE_REMOTE_THUMBNAILS],
                    ConfigContract.DEFAULT_ENABLE_REMOTE_THUMBNAILS
                ),
                maxRemoteThumbnailSizeMb = readInt(
                    values[ConfigContract.KEY_MAX_REMOTE_THUMBNAIL_SIZE_MB],
                    ConfigContract.DEFAULT_MAX_REMOTE_THUMBNAIL_SIZE_MB
                ),
                highResThumbnails = readBool(
                    values[ConfigContract.KEY_HIGH_RES_THUMBNAILS],
                    ConfigContract.DEFAULT_HIGH_RES_THUMBNAILS
                ),
                disableSoftwareImageRendering = readBool(
                    values[ConfigContract.KEY_DISABLE_SOFTWARE_IMAGE_RENDERING],
                    ConfigContract.DEFAULT_DISABLE_SOFTWARE_IMAGE_RENDERING
                ),
                enableHardwareGifPlayback = readBool(
                    values[ConfigContract.KEY_ENABLE_HARDWARE_GIF_PLAYBACK],
                    ConfigContract.DEFAULT_ENABLE_HARDWARE_GIF_PLAYBACK
                ),
                unlockTextPreviewLimits = readBool(
                    values[ConfigContract.KEY_UNLOCK_TEXT_PREVIEW_LIMITS],
                    ConfigContract.DEFAULT_UNLOCK_TEXT_PREVIEW_LIMITS
                ),
                enableAndroidDataAccess = readBool(
                    values[ConfigContract.KEY_ENABLE_ANDROID_DATA_ACCESS],
                    ConfigContract.DEFAULT_ENABLE_ANDROID_DATA_ACCESS
                ),
                customGoogleDriveClientId = values[ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_ID]
                    ?.toString()
                    ?: ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_ID,
                customGoogleDriveClientSecret = values[ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_SECRET]
                    ?.toString()
                    ?: ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_SECRET,
                debugLogging = readBool(
                    values[ConfigContract.KEY_DEBUG_LOGGING],
                    ConfigContract.DEFAULT_DEBUG_LOGGING
                ),
                updatedAtMs = readLong(
                    values[ConfigContract.KEY_UPDATED_AT_MS],
                    0L
                )
            ).sanitized()
        }

        fun toMap(config: ModuleConfig): Map<String, Any> {
            val clean = config.sanitized()
            return linkedMapOf(
                ConfigContract.KEY_UNLOCK_LARGE_IMAGE_SWIPE to clean.unlockLargeImageSwipe,
                ConfigContract.KEY_MAX_SWIPE_IMAGE_SIZE_MB to clean.maxSwipeImageSizeMb,
                ConfigContract.KEY_ENABLE_ADJACENT_PREFETCH to clean.enableAdjacentPrefetch,
                ConfigContract.KEY_PREFETCH_LIMIT_MODE to clean.prefetchLimitMode.name,
                ConfigContract.KEY_PREFETCH_COUNT to clean.prefetchCount,
                ConfigContract.KEY_PREFETCH_DISK_CACHE_MB to clean.prefetchDiskCacheMb,
                ConfigContract.KEY_PREFETCH_MAX_SINGLE_FILE_MB to clean.prefetchMaxSingleFileMb,
                ConfigContract.KEY_PREFETCH_CONCURRENCY to clean.prefetchConcurrency,
                ConfigContract.KEY_PREFETCH_WIFI_ONLY to clean.prefetchWifiOnly,
                ConfigContract.KEY_ENABLE_REMOTE_THUMBNAILS to clean.enableRemoteThumbnails,
                ConfigContract.KEY_MAX_REMOTE_THUMBNAIL_SIZE_MB to clean.maxRemoteThumbnailSizeMb,
                ConfigContract.KEY_HIGH_RES_THUMBNAILS to clean.highResThumbnails,
                ConfigContract.KEY_DISABLE_SOFTWARE_IMAGE_RENDERING to clean.disableSoftwareImageRendering,
                ConfigContract.KEY_ENABLE_HARDWARE_GIF_PLAYBACK to clean.enableHardwareGifPlayback,
                ConfigContract.KEY_UNLOCK_TEXT_PREVIEW_LIMITS to clean.unlockTextPreviewLimits,
                ConfigContract.KEY_ENABLE_ANDROID_DATA_ACCESS to clean.enableAndroidDataAccess,
                ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_ID to clean.customGoogleDriveClientId,
                ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_SECRET to clean.customGoogleDriveClientSecret,
                ConfigContract.KEY_DEBUG_LOGGING to clean.debugLogging,
                ConfigContract.KEY_UPDATED_AT_MS to clean.updatedAtMs
            )
        }

        fun fromSharedPreferences(prefs: SharedPreferences): ModuleConfig {
            return ModuleConfig(
                unlockLargeImageSwipe = readPrefBool(
                    prefs,
                    ConfigContract.KEY_UNLOCK_LARGE_IMAGE_SWIPE,
                    ConfigContract.DEFAULT_UNLOCK_LARGE_IMAGE_SWIPE
                ),
                maxSwipeImageSizeMb = readPrefInt(
                    prefs,
                    ConfigContract.KEY_MAX_SWIPE_IMAGE_SIZE_MB,
                    ConfigContract.DEFAULT_MAX_SWIPE_IMAGE_SIZE_MB
                ),
                enableAdjacentPrefetch = readPrefBool(
                    prefs,
                    ConfigContract.KEY_ENABLE_ADJACENT_PREFETCH,
                    ConfigContract.DEFAULT_ENABLE_ADJACENT_PREFETCH
                ),
                prefetchLimitMode = PrefetchLimitMode.fromString(
                    readPrefString(
                        prefs,
                        ConfigContract.KEY_PREFETCH_LIMIT_MODE,
                        ConfigContract.DEFAULT_PREFETCH_LIMIT_MODE.name
                    )
                ),
                prefetchCount = readPrefInt(
                    prefs,
                    ConfigContract.KEY_PREFETCH_COUNT,
                    ConfigContract.DEFAULT_PREFETCH_COUNT
                ),
                prefetchDiskCacheMb = readPrefInt(
                    prefs,
                    ConfigContract.KEY_PREFETCH_DISK_CACHE_MB,
                    ConfigContract.DEFAULT_PREFETCH_DISK_CACHE_MB
                ),
                prefetchMaxSingleFileMb = readPrefInt(
                    prefs,
                    ConfigContract.KEY_PREFETCH_MAX_SINGLE_FILE_MB,
                    ConfigContract.DEFAULT_PREFETCH_MAX_SINGLE_FILE_MB
                ),
                prefetchConcurrency = readPrefInt(
                    prefs,
                    ConfigContract.KEY_PREFETCH_CONCURRENCY,
                    ConfigContract.DEFAULT_PREFETCH_CONCURRENCY
                ),
                prefetchWifiOnly = readPrefBool(
                    prefs,
                    ConfigContract.KEY_PREFETCH_WIFI_ONLY,
                    ConfigContract.DEFAULT_PREFETCH_WIFI_ONLY
                ),
                enableRemoteThumbnails = readPrefBool(
                    prefs,
                    ConfigContract.KEY_ENABLE_REMOTE_THUMBNAILS,
                    ConfigContract.DEFAULT_ENABLE_REMOTE_THUMBNAILS
                ),
                maxRemoteThumbnailSizeMb = readPrefInt(
                    prefs,
                    ConfigContract.KEY_MAX_REMOTE_THUMBNAIL_SIZE_MB,
                    ConfigContract.DEFAULT_MAX_REMOTE_THUMBNAIL_SIZE_MB
                ),
                highResThumbnails = readPrefBool(
                    prefs,
                    ConfigContract.KEY_HIGH_RES_THUMBNAILS,
                    ConfigContract.DEFAULT_HIGH_RES_THUMBNAILS
                ),
                disableSoftwareImageRendering = readPrefBool(
                    prefs,
                    ConfigContract.KEY_DISABLE_SOFTWARE_IMAGE_RENDERING,
                    ConfigContract.DEFAULT_DISABLE_SOFTWARE_IMAGE_RENDERING
                ),
                enableHardwareGifPlayback = readPrefBool(
                    prefs,
                    ConfigContract.KEY_ENABLE_HARDWARE_GIF_PLAYBACK,
                    ConfigContract.DEFAULT_ENABLE_HARDWARE_GIF_PLAYBACK
                ),
                unlockTextPreviewLimits = readPrefBool(
                    prefs,
                    ConfigContract.KEY_UNLOCK_TEXT_PREVIEW_LIMITS,
                    ConfigContract.DEFAULT_UNLOCK_TEXT_PREVIEW_LIMITS
                ),
                enableAndroidDataAccess = readPrefBool(
                    prefs,
                    ConfigContract.KEY_ENABLE_ANDROID_DATA_ACCESS,
                    ConfigContract.DEFAULT_ENABLE_ANDROID_DATA_ACCESS
                ),
                customGoogleDriveClientId = readPrefString(
                    prefs,
                    ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_ID,
                    ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_ID
                ),
                customGoogleDriveClientSecret = readPrefString(
                    prefs,
                    ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_SECRET,
                    ConfigContract.DEFAULT_GOOGLE_DRIVE_CLIENT_SECRET
                ),
                debugLogging = readPrefBool(
                    prefs,
                    ConfigContract.KEY_DEBUG_LOGGING,
                    ConfigContract.DEFAULT_DEBUG_LOGGING
                ),
                updatedAtMs = readPrefLong(
                    prefs,
                    ConfigContract.KEY_UPDATED_AT_MS,
                    0L
                )
            ).sanitized()
        }

        fun writeToEditor(
            editor: SharedPreferences.Editor,
            config: ModuleConfig
        ): SharedPreferences.Editor {
            val clean = config.sanitized()
            return editor
                .putBoolean(ConfigContract.KEY_UNLOCK_LARGE_IMAGE_SWIPE, clean.unlockLargeImageSwipe)
                .putInt(ConfigContract.KEY_MAX_SWIPE_IMAGE_SIZE_MB, clean.maxSwipeImageSizeMb)
                .putBoolean(ConfigContract.KEY_ENABLE_ADJACENT_PREFETCH, clean.enableAdjacentPrefetch)
                .putString(ConfigContract.KEY_PREFETCH_LIMIT_MODE, clean.prefetchLimitMode.name)
                .putInt(ConfigContract.KEY_PREFETCH_COUNT, clean.prefetchCount)
                .putInt(ConfigContract.KEY_PREFETCH_DISK_CACHE_MB, clean.prefetchDiskCacheMb)
                .putInt(ConfigContract.KEY_PREFETCH_MAX_SINGLE_FILE_MB, clean.prefetchMaxSingleFileMb)
                .putInt(ConfigContract.KEY_PREFETCH_CONCURRENCY, clean.prefetchConcurrency)
                .putBoolean(ConfigContract.KEY_PREFETCH_WIFI_ONLY, clean.prefetchWifiOnly)
                .putBoolean(ConfigContract.KEY_ENABLE_REMOTE_THUMBNAILS, clean.enableRemoteThumbnails)
                .putInt(ConfigContract.KEY_MAX_REMOTE_THUMBNAIL_SIZE_MB, clean.maxRemoteThumbnailSizeMb)
                .putBoolean(ConfigContract.KEY_HIGH_RES_THUMBNAILS, clean.highResThumbnails)
                .putBoolean(ConfigContract.KEY_DISABLE_SOFTWARE_IMAGE_RENDERING, clean.disableSoftwareImageRendering)
                .putBoolean(ConfigContract.KEY_ENABLE_HARDWARE_GIF_PLAYBACK, clean.enableHardwareGifPlayback)
                .putBoolean(ConfigContract.KEY_UNLOCK_TEXT_PREVIEW_LIMITS, clean.unlockTextPreviewLimits)
                .putBoolean(ConfigContract.KEY_ENABLE_ANDROID_DATA_ACCESS, clean.enableAndroidDataAccess)
                .putString(ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_ID, clean.customGoogleDriveClientId)
                .putString(ConfigContract.KEY_GOOGLE_DRIVE_CLIENT_SECRET, clean.customGoogleDriveClientSecret)
                .putBoolean(ConfigContract.KEY_DEBUG_LOGGING, clean.debugLogging)
                .putLong(ConfigContract.KEY_UPDATED_AT_MS, clean.updatedAtMs)
        }

        fun saveToSharedPreferences(
            prefs: SharedPreferences,
            config: ModuleConfig,
            commitSync: Boolean = true
        ): Boolean {
            val editor = writeToEditor(prefs.edit(), config)
            return if (commitSync) {
                editor.commit()
            } else {
                editor.apply()
                true
            }
        }

        private fun readBool(raw: Any?, defaultValue: Boolean): Boolean = when (raw) {
            is Boolean -> raw
            is String -> raw.toBooleanStrictOrNull() ?: defaultValue
            else -> defaultValue
        }

        private fun readInt(raw: Any?, defaultValue: Int): Int = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull() ?: defaultValue
            else -> defaultValue
        }

        private fun readLong(raw: Any?, defaultValue: Long): Long = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: defaultValue
            else -> defaultValue
        }

        private fun readPrefBool(prefs: SharedPreferences, key: String, defaultValue: Boolean): Boolean {
            return runCatching {
                if (prefs.contains(key)) prefs.getBoolean(key, defaultValue) else defaultValue
            }.getOrElse {
                runCatching { readBool(prefs.all[key], defaultValue) }.getOrDefault(defaultValue)
            }
        }

        private fun readPrefInt(prefs: SharedPreferences, key: String, defaultValue: Int): Int {
            return runCatching {
                if (prefs.contains(key)) prefs.getInt(key, defaultValue) else defaultValue
            }.getOrElse {
                runCatching { readInt(prefs.all[key], defaultValue) }.getOrDefault(defaultValue)
            }
        }

        private fun readPrefLong(prefs: SharedPreferences, key: String, defaultValue: Long): Long {
            return runCatching {
                if (prefs.contains(key)) prefs.getLong(key, defaultValue) else defaultValue
            }.getOrElse {
                runCatching { readLong(prefs.all[key], defaultValue) }.getOrDefault(defaultValue)
            }
        }

        private fun readPrefString(prefs: SharedPreferences, key: String, defaultValue: String): String {
            return runCatching {
                if (prefs.contains(key)) prefs.getString(key, defaultValue) ?: defaultValue else defaultValue
            }.getOrElse {
                runCatching { prefs.all[key]?.toString() ?: defaultValue }.getOrDefault(defaultValue)
            }
        }
    }
}
