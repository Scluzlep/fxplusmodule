package com.scluzlep.fxplusmodule.config

enum class PrefetchLimitMode(val displayName: String) {
    COUNT_ONLY("仅按个数"),
    CAPACITY_ONLY("仅按容量上限"),
    BOTH("个数与容量同时生效");

    companion object {
        fun fromString(raw: String?): PrefetchLimitMode {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: COUNT_ONLY
        }
    }
}

/**
 * Shared constants, preference keys, bounds, and presets across the standalone app,
 * libxposed RemotePreferences, and the FX File Explorer in-process bridge.
 */
object ConfigContract {
    const val MODULE_PACKAGE = "com.scluzlep.fxplusmodule"
    const val MAIN_ACTIVITY_CLASS = "com.scluzlep.fxplusmodule.ui.MainActivity"
    const val TARGET_FX_PACKAGE = "nextapp.fx"

    const val FX_VERSION_CODE_9012 = 9012L
    const val FX_VERSION_CODE_9108 = 9108L

    const val PREFS_NAME_REMOTE = "fxplusmodule"
    const val PREFS_NAME_LOCAL = "fxplusmodule_local"
    const val PREFETCH_CACHE_DIR_NAME = "fxplus_prefetch"
    const val BYTES_PER_MIB = 1024L * 1024L

    const val KEY_UNLOCK_LARGE_IMAGE_SWIPE = "unlock_large_image_swipe"
    const val KEY_MAX_SWIPE_IMAGE_SIZE_MB = "max_swipe_image_size_mb"
    const val KEY_ENABLE_ADJACENT_PREFETCH = "enable_adjacent_prefetch"
    const val KEY_PREFETCH_LIMIT_MODE = "prefetch_limit_mode"
    const val KEY_PREFETCH_COUNT = "prefetch_count"
    const val KEY_PREFETCH_DISK_CACHE_MB = "prefetch_disk_cache_mb"
    const val KEY_PREFETCH_MAX_SINGLE_FILE_MB = "prefetch_max_single_file_mb"
    const val KEY_PREFETCH_CONCURRENCY = "prefetch_concurrency"
    const val KEY_PREFETCH_WIFI_ONLY = "prefetch_wifi_only"
    const val KEY_ENABLE_REMOTE_THUMBNAILS = "enable_remote_thumbnails"
    const val KEY_MAX_REMOTE_THUMBNAIL_SIZE_MB = "max_remote_thumbnail_size_mb"
    const val KEY_HIGH_RES_THUMBNAILS = "high_res_thumbnails"
    const val KEY_DISABLE_SOFTWARE_IMAGE_RENDERING = "disable_software_image_rendering"
    const val KEY_ENABLE_HARDWARE_GIF_PLAYBACK = "enable_hardware_gif_playback"
    const val KEY_UNLOCK_TEXT_PREVIEW_LIMITS = "unlock_text_preview_limits"
    const val KEY_ENABLE_ANDROID_DATA_ACCESS = "enable_android_data_access"
    const val KEY_GOOGLE_DRIVE_CLIENT_ID = "google_drive_client_id"
    const val KEY_GOOGLE_DRIVE_CLIENT_SECRET = "google_drive_client_secret"
    const val KEY_DEBUG_LOGGING = "debug_logging"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"

    const val DEFAULT_UNLOCK_LARGE_IMAGE_SWIPE = true
    const val DEFAULT_MAX_SWIPE_IMAGE_SIZE_MB = 0
    const val MIN_MAX_SWIPE_IMAGE_SIZE_MB = 0
    val MAX_SWIPE_IMAGE_SIZE_MB_PRESETS = listOf(0, 16, 32, 64, 128, 256)

    const val DEFAULT_ENABLE_ADJACENT_PREFETCH = true
    val DEFAULT_PREFETCH_LIMIT_MODE = PrefetchLimitMode.COUNT_ONLY

    const val DEFAULT_PREFETCH_COUNT = 3
    const val MIN_PREFETCH_COUNT = 1
    const val MAX_PREFETCH_COUNT = 15
    val PREFETCH_COUNT_PRESETS = listOf(1, 2, 3, 5, 8)

    const val DEFAULT_PREFETCH_DISK_CACHE_MB = 256
    const val MIN_PREFETCH_DISK_CACHE_MB = 32
    const val MAX_PREFETCH_DISK_CACHE_MB = 16_384
    val PREFETCH_DISK_CACHE_MB_PRESETS = listOf(64, 128, 256, 512, 1024, 2048)

    const val DEFAULT_PREFETCH_MAX_SINGLE_FILE_MB = 0
    const val MIN_PREFETCH_MAX_SINGLE_FILE_MB = 0
    val PREFETCH_MAX_SINGLE_FILE_MB_PRESETS = listOf(0, 64, 128, 256, 512)

    const val DEFAULT_PREFETCH_CONCURRENCY = 2
    const val MIN_PREFETCH_CONCURRENCY = 1
    const val MAX_PREFETCH_CONCURRENCY = 4
    val PREFETCH_CONCURRENCY_PRESETS = listOf(1, 2, 3, 4)

    const val DEFAULT_PREFETCH_WIFI_ONLY = false

    const val DEFAULT_ENABLE_REMOTE_THUMBNAILS = true
    const val DEFAULT_MAX_REMOTE_THUMBNAIL_SIZE_MB = 64
    const val MIN_MAX_REMOTE_THUMBNAIL_SIZE_MB = 0
    val MAX_REMOTE_THUMBNAIL_SIZE_MB_PRESETS = listOf(0, 16, 32, 64, 128, 256)

    const val DEFAULT_HIGH_RES_THUMBNAILS = false
    const val DEFAULT_DISABLE_SOFTWARE_IMAGE_RENDERING = true
    const val DEFAULT_ENABLE_HARDWARE_GIF_PLAYBACK = true
    const val DEFAULT_UNLOCK_TEXT_PREVIEW_LIMITS = true
    const val DEFAULT_ENABLE_ANDROID_DATA_ACCESS = true
    const val DEFAULT_GOOGLE_DRIVE_CLIENT_ID = ""
    const val DEFAULT_GOOGLE_DRIVE_CLIENT_SECRET = ""
    const val DEFAULT_DEBUG_LOGGING = false
}
