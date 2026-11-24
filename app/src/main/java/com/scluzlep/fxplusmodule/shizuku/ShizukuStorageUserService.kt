package com.scluzlep.fxplusmodule.shizuku

import android.content.Context
import android.os.Parcel
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.system.exitProcess

/**
 * Shizuku UserService executing scoped filesystem operations strictly inside
 * `/storage/emulated/<userId>/Android/(data|obb)` or `/sdcard/Android/(data|obb)`.
 */
class ShizukuStorageUserService() : IPrivilegedStorageService.Stub() {

    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == SHIZUKU_TRANSACTION_DESTROY) {
            data.enforceInterface(DESCRIPTOR)
            destroy()
            reply?.writeNoException()
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun listEntriesJson(path: String?): String {
        val normalized = ShizukuBridgeContract.normalizeAndValidateAndroidPath(path) ?: return "[]"
        val dir = ShizukuBridgeContract.resolveSafeFile(normalized) ?: return "[]"
        if (!dir.exists() || !dir.isDirectory) return "[]"

        val children = dir.listFiles() ?: return "[]"
        val array = JSONArray()
        for (child in children) {
            val childLogicalPath = "$normalized/${child.name}"
            array.put(buildStatObject(child, childLogicalPath, exists = true))
        }
        return array.toString()
    }

    override fun statJson(path: String?): String {
        val normalized = ShizukuBridgeContract.normalizeAndValidateAndroidPath(path)
        val file = if (normalized != null) ShizukuBridgeContract.resolveSafeFile(normalized) else null
        if (normalized == null || file == null || !file.exists()) {
            return JSONObject()
                .put(ShizukuBridgeContract.JSON_EXISTS, false)
                .toString()
        }
        return buildStatObject(file, normalized, exists = true).toString()
    }

    override fun openRead(path: String?): ParcelFileDescriptor? {
        val file = ShizukuBridgeContract.resolveSafeFile(path)
            ?: throw SecurityException("Invalid or restricted path")
        if (!file.exists() || !file.isFile) {
            return null
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openWrite(path: String?, append: Boolean): ParcelFileDescriptor? {
        val file = ShizukuBridgeContract.resolveSafeFile(path)
            ?: throw SecurityException("Invalid or restricted path")
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val modeFlags = ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_WRITE_ONLY or
            if (append) ParcelFileDescriptor.MODE_APPEND else ParcelFileDescriptor.MODE_TRUNCATE
        return ParcelFileDescriptor.open(file, modeFlags)
    }

    override fun mkdirs(path: String?): Boolean {
        val dir = ShizukuBridgeContract.resolveSafeFile(path) ?: return false
        return dir.isDirectory || dir.mkdirs()
    }

    override fun deleteEntry(path: String?, recursive: Boolean): Boolean {
        val normalized = ShizukuBridgeContract.normalizeAndValidateAndroidPath(path) ?: return false
        val root = ShizukuBridgeContract.extractRestrictedRoot(normalized) ?: return false
        if (normalized == root) return false

        val target = ShizukuBridgeContract.resolveSafeFile(normalized) ?: return false
        if (!target.exists()) return false
        return if (recursive && target.isDirectory) {
            target.deleteRecursively()
        } else {
            target.delete()
        }
    }

    override fun renameEntry(srcPath: String?, dstPath: String?): Boolean {
        val srcNormalized = ShizukuBridgeContract.normalizeAndValidateAndroidPath(srcPath) ?: return false
        val dstNormalized = ShizukuBridgeContract.normalizeAndValidateAndroidPath(dstPath) ?: return false
        val srcRoot = ShizukuBridgeContract.extractRestrictedRoot(srcNormalized) ?: return false
        val dstRoot = ShizukuBridgeContract.extractRestrictedRoot(dstNormalized) ?: return false
        if (srcNormalized == srcRoot || dstNormalized == dstRoot) return false

        val src = ShizukuBridgeContract.resolveSafeFile(srcNormalized) ?: return false
        val dst = ShizukuBridgeContract.resolveSafeFile(dstNormalized) ?: return false
        if (!src.exists()) return false

        val dstParent = dst.parentFile
        if (dstParent != null && !dstParent.exists()) {
            dstParent.mkdirs()
        }
        return src.renameTo(dst)
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun buildStatObject(file: File, logicalPath: String, exists: Boolean): JSONObject {
        val isDir = file.isDirectory
        val isFile = file.isFile
        return JSONObject()
            .put(ShizukuBridgeContract.JSON_EXISTS, exists)
            .put(ShizukuBridgeContract.JSON_NAME, file.name)
            .put(ShizukuBridgeContract.JSON_PATH, logicalPath)
            .put(ShizukuBridgeContract.JSON_IS_DIR, isDir)
            .put(ShizukuBridgeContract.JSON_IS_FILE, isFile)
            .put(ShizukuBridgeContract.JSON_SIZE, if (isFile) file.length() else 0L)
            .put(ShizukuBridgeContract.JSON_LAST_MODIFIED, file.lastModified())
            .put(ShizukuBridgeContract.JSON_CAN_READ, file.canRead())
            .put(ShizukuBridgeContract.JSON_CAN_WRITE, file.canWrite())
            .put(ShizukuBridgeContract.JSON_IS_HIDDEN, file.isHidden)
    }

    companion object {
        // Matches rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy
        private const val SHIZUKU_TRANSACTION_DESTROY = 16777115
    }
}
