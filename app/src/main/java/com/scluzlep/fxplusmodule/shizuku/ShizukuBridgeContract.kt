package com.scluzlep.fxplusmodule.shizuku

import android.net.Uri
import com.scluzlep.fxplusmodule.config.ConfigContract
import java.io.File

/**
 * Shared IPC contract and strict path/caller validation for the Shizuku storage bridge.
 */
object ShizukuBridgeContract {
    const val AUTHORITY = "com.scluzlep.fxplusmodule.storagebridge"
    const val CONTENT_URI_BASE = "content://$AUTHORITY"
    const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"

    val ALLOWED_CALLER_PACKAGES = setOf(
        ConfigContract.TARGET_FX_PACKAGE,
        ConfigContract.MODULE_PACKAGE
    )

    const val METHOD_PING = "ping"
    const val METHOD_LIST = "list"
    const val METHOD_STAT = "stat"
    const val METHOD_MKDIRS = "mkdirs"
    const val METHOD_DELETE = "delete"
    const val METHOD_RENAME = "rename"

    const val KEY_PATH = "path"
    const val KEY_SRC_PATH = "src_path"
    const val KEY_DST_PATH = "dst_path"
    const val KEY_RECURSIVE = "recursive"

    const val KEY_OK = "ok"
    const val KEY_ERROR = "error"
    const val KEY_ENTRIES_JSON = "entries_json"
    const val KEY_STAT_JSON = "stat_json"
    const val KEY_RESULT = "result"
    const val KEY_SHIZUKU_AVAILABLE = "shizuku_available"
    const val KEY_SHIZUKU_GRANTED = "shizuku_granted"
    const val KEY_SERVICE_BOUND = "service_bound"
    const val KEY_PRIVILEGED_UID = "privileged_uid"

    const val JSON_NAME = "name"
    const val JSON_PATH = "path"
    const val JSON_IS_DIR = "isDir"
    const val JSON_IS_FILE = "isFile"
    const val JSON_EXISTS = "exists"
    const val JSON_SIZE = "size"
    const val JSON_LAST_MODIFIED = "lastModified"
    const val JSON_CAN_READ = "canRead"
    const val JSON_CAN_WRITE = "canWrite"
    const val JSON_IS_HIDDEN = "isHidden"

    private val ANDROID_RESTRICTED_PATH_REGEX =
        Regex("^/(storage/emulated/[0-9]+|sdcard)/Android/(data|obb)(/.*)?$")

    private val ANDROID_RESTRICTED_ROOT_REGEX =
        Regex("^/(storage/emulated/[0-9]+|sdcard)/Android/(data|obb)")

    fun buildFileUri(path: String): Uri {
        return Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendQueryParameter(KEY_PATH, path)
            .build()
    }

    fun isValidAndroidRestrictedPath(rawPath: String?): Boolean {
        return normalizeAndValidateAndroidPath(rawPath) != null
    }

    /**
     * Validates that [rawPath] targets `/storage/emulated/<userId>/Android/(data|obb)` or
     * `/sdcard/Android/(data|obb)` with zero `.` or `..` path traversal segments.
     */
    fun normalizeAndValidateAndroidPath(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        if (rawPath.indexOf('\u0000') >= 0 ||
            rawPath.indexOf('\r') >= 0 ||
            rawPath.indexOf('\n') >= 0 ||
            rawPath.indexOf('\\') >= 0
        ) {
            return null
        }
        if (!rawPath.startsWith('/')) return null

        val rawSegments = rawPath.split('/')
        val cleanSegments = ArrayList<String>(rawSegments.size)
        for (seg in rawSegments) {
            if (seg.isEmpty()) continue
            if (seg == "." || seg == "..") return null
            cleanSegments.add(seg)
        }
        if (cleanSegments.isEmpty()) return null

        val normalized = "/" + cleanSegments.joinToString("/")
        return if (ANDROID_RESTRICTED_PATH_REGEX.matches(normalized)) normalized else null
    }

    fun extractRestrictedRoot(normalizedPath: String): String? {
        return ANDROID_RESTRICTED_ROOT_REGEX.find(normalizedPath)?.value
    }

    /**
     * Resolves canonical filesystem path and verifies it cannot escape the
     * `Android/(data|obb)` root via intermediate symlinks.
     */
    fun resolveSafeFile(rawPath: String?): File? {
        val normalized = normalizeAndValidateAndroidPath(rawPath) ?: return null
        val rootPath = extractRestrictedRoot(normalized) ?: return null
        return runCatching {
            val rootCanonical = File(rootPath).canonicalPath
            val targetFile = File(normalized)
            val targetCanonical = targetFile.canonicalPath
            val insideBoundary = targetCanonical == rootCanonical ||
                targetCanonical.startsWith("$rootCanonical${File.separator}")
            if (insideBoundary) targetFile else null
        }.getOrNull()
    }
}
