package com.scluzlep.fxplusmodule.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileNotFoundException

/**
 * Authorized ContentProvider bridge exposing scoped Android/data & Android/obb access
 * backed by Shizuku UserService exclusively to `nextapp.fx` and `com.scluzlep.fxplusmodule`.
 */
class FxStorageBridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { ShizukuStatusHelper.init(it) }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        verifyAuthorizedCaller()
        return when (method) {
            ShizukuBridgeContract.METHOD_PING -> handlePing()
            ShizukuBridgeContract.METHOD_LIST -> handleList(extras?.getString(ShizukuBridgeContract.KEY_PATH) ?: arg)
            ShizukuBridgeContract.METHOD_STAT -> handleStat(extras?.getString(ShizukuBridgeContract.KEY_PATH) ?: arg)
            ShizukuBridgeContract.METHOD_MKDIRS -> handleMkdirs(extras?.getString(ShizukuBridgeContract.KEY_PATH) ?: arg)
            ShizukuBridgeContract.METHOD_DELETE -> handleDelete(
                rawPath = extras?.getString(ShizukuBridgeContract.KEY_PATH) ?: arg,
                recursive = extras?.getBoolean(ShizukuBridgeContract.KEY_RECURSIVE, true) ?: true
            )
            ShizukuBridgeContract.METHOD_RENAME -> handleRename(
                rawSrc = extras?.getString(ShizukuBridgeContract.KEY_SRC_PATH)
                    ?: extras?.getString(ShizukuBridgeContract.KEY_PATH)
                    ?: arg,
                rawDst = extras?.getString(ShizukuBridgeContract.KEY_DST_PATH)
            )
            else -> errorBundle("Unsupported method: $method")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        verifyAuthorizedCaller()
        val rawPath = uri.getQueryParameter(ShizukuBridgeContract.KEY_PATH) ?: uri.path
        val normalizedPath = requireValidRestrictedPath(rawPath)

        val isRead = mode == "r"
        val isWrite = mode == "w" || mode == "wt" || mode == "wa" || mode == "rw" || mode == "rwt"
        if (!isRead && !isWrite) {
            throw IllegalArgumentException("Unsupported openFile mode: $mode")
        }
        val append = mode.contains('a')

        val pfd = executeWithService { service ->
            if (isRead) {
                service.openRead(normalizedPath)
            } else {
                service.openWrite(normalizedPath, append)
            }
        }
        return pfd ?: throw FileNotFoundException("Unable to open path via Shizuku: $normalizedPath")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        verifyAuthorizedCaller()
        return null
    }

    override fun getType(uri: Uri): String? {
        verifyAuthorizedCaller()
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        verifyAuthorizedCaller()
        throw UnsupportedOperationException("Insert is not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        verifyAuthorizedCaller()
        throw UnsupportedOperationException("Content delete is not supported; use call(delete)")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        verifyAuthorizedCaller()
        throw UnsupportedOperationException("Update is not supported")
    }

    private fun handlePing(): Bundle {
        ShizukuStatusHelper.refresh()
        val service = ShizukuStatusHelper.getOrBindService(timeoutMs = 2_500L)
        ShizukuStatusHelper.refresh()
        val snap = ShizukuStatusHelper.status.value
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putBoolean(ShizukuBridgeContract.KEY_SHIZUKU_AVAILABLE, snap.binderAlive)
            putBoolean(ShizukuBridgeContract.KEY_SHIZUKU_GRANTED, snap.permissionGranted)
            putBoolean(ShizukuBridgeContract.KEY_SERVICE_BOUND, service != null && snap.serviceBound)
            putInt(ShizukuBridgeContract.KEY_PRIVILEGED_UID, snap.privilegedUid)
        }
    }

    private fun handleList(rawPath: String?): Bundle {
        val path = requireValidRestrictedPath(rawPath)
        val entriesJson = executeWithService { it.listEntriesJson(path) }
            ?: return errorBundle("Shizuku storage service unavailable")
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putString(ShizukuBridgeContract.KEY_ENTRIES_JSON, entriesJson)
        }
    }

    private fun handleStat(rawPath: String?): Bundle {
        val path = requireValidRestrictedPath(rawPath)
        val statJson = executeWithService { it.statJson(path) }
            ?: return errorBundle("Shizuku storage service unavailable")
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putString(ShizukuBridgeContract.KEY_STAT_JSON, statJson)
        }
    }

    private fun handleMkdirs(rawPath: String?): Bundle {
        val path = requireValidRestrictedPath(rawPath)
        val created = executeWithService { it.mkdirs(path) }
            ?: return errorBundle("Shizuku storage service unavailable")
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putBoolean(ShizukuBridgeContract.KEY_RESULT, created)
        }
    }

    private fun handleDelete(rawPath: String?, recursive: Boolean): Bundle {
        val path = requireValidRestrictedPath(rawPath)
        val deleted = executeWithService { it.deleteEntry(path, recursive) }
            ?: return errorBundle("Shizuku storage service unavailable")
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putBoolean(ShizukuBridgeContract.KEY_RESULT, deleted)
        }
    }

    private fun handleRename(rawSrc: String?, rawDst: String?): Bundle {
        val srcPath = requireValidRestrictedPath(rawSrc)
        val dstPath = requireValidRestrictedPath(rawDst)
        val renamed = executeWithService { it.renameEntry(srcPath, dstPath) }
            ?: return errorBundle("Shizuku storage service unavailable")
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, true)
            putBoolean(ShizukuBridgeContract.KEY_RESULT, renamed)
        }
    }

    private fun <T> executeWithService(block: (IPrivilegedStorageService) -> T): T? {
        val first = ShizukuStatusHelper.getOrBindService() ?: return null
        return try {
            block(first)
        } catch (_: DeadObjectException) {
            ShizukuStatusHelper.invalidateService()
            val rebound = ShizukuStatusHelper.getOrBindService() ?: return null
            runCatching { block(rebound) }.getOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    private fun verifyAuthorizedCaller() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return

        val ctx = context ?: throw SecurityException("Provider context unavailable")
        val packages = ctx.packageManager.getPackagesForUid(callingUid)
        val allowed = packages != null &&
            packages.isNotEmpty() &&
            packages.all { it in ShizukuBridgeContract.ALLOWED_CALLER_PACKAGES }

        if (!allowed) {
            throw SecurityException("Unauthorized caller uid=$callingUid")
        }
    }

    private fun requireValidRestrictedPath(rawPath: String?): String {
        return ShizukuBridgeContract.normalizeAndValidateAndroidPath(rawPath)
            ?: throw SecurityException("Invalid or disallowed path")
    }

    private fun errorBundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(ShizukuBridgeContract.KEY_OK, false)
            putString(ShizukuBridgeContract.KEY_ERROR, message)
        }
    }
}
