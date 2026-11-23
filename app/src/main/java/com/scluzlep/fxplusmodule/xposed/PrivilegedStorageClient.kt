package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.scluzlep.fxplusmodule.shizuku.ShizukuBridgeContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PrivilegedStat(
    val exists: Boolean,
    val name: String,
    val path: String,
    val isDir: Boolean,
    val isFile: Boolean,
    val size: Long,
    val lastModifiedMs: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val isHidden: Boolean
)

/**
 * Dual-engine storage client for `Android/data` and `Android/obb` inside `nextapp.fx`.
 * Priority 1: Root in the FUSE mount namespace directly on `/storage/emulated/<userId>/Android/(data|obb)`
 * (`su 2000` / `su -u 2000` first, plain `su` fallback; never uses global `-mm` or raw lowerfs).
 * Priority 2: Authorized Shizuku ContentProvider bridge (`content://com.scluzlep.fxplusmodule.storagebridge`).
 */
object PrivilegedStorageClient {

    private const val CACHE_TTL_MS = 3_000L
    private const val ROOT_PROBE_FAIL_TTL_MS = 15_000L
    private const val STREAM_WAIT_TIMEOUT_SEC = 30L

    private val RESTRICTED_FUSE_REGEX =
        Regex("^/storage/emulated/[0-9]+/Android/(data|obb)(/.*)?$")
    private val RESTRICTED_FUSE_ROOT_REGEX =
        Regex("^/storage/emulated/[0-9]+/Android/(data|obb)$")
    private val SDCARD_PREFIX_REGEX =
        Regex("^/sdcard(?=/|$)")

    private val statCache = ConcurrentHashMap<String, Pair<Long, PrivilegedStat>>()
    private val rootShellLock = Any()

    @Volatile
    private var rootShellMode: RootShellMode? = null

    @Volatile
    private var lastRootProbeFailMs: Long = 0L

    @Volatile
    private var persistentShell: PersistentShellSession? = null

    private enum class RootShellMode(val prefixArgs: List<String>) {
        SU_SHELL_UID(listOf("su", "2000")),
        SU_U_FLAG_UID(listOf("su", "-u", "2000")),
        SU_PLAIN(listOf("su"))
    }

    /**
     * Normalizes `/sdcard/...` to `/storage/emulated/0/...` and validates that [rawPath]
     * strictly resides inside `/storage/emulated/<userId>/Android/(data|obb)` on FUSE
     * without traversal (`..`, `.`), backslashes, newlines, or null bytes.
     */
    fun normalizeAndValidateFusePath(rawPath: String?): String? {
        val baseValid = ShizukuBridgeContract.normalizeAndValidateAndroidPath(rawPath) ?: return null
        val fuseNormalized = SDCARD_PREFIX_REGEX.replace(baseValid, "/storage/emulated/0")
        return if (RESTRICTED_FUSE_REGEX.matches(fuseNormalized)) fuseNormalized else null
    }

    fun isScopedRestrictedPath(rawPath: String?): Boolean {
        return normalizeAndValidateFusePath(rawPath) != null
    }

    fun isRestrictedRoot(rawPath: String?): Boolean {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return false
        return RESTRICTED_FUSE_ROOT_REGEX.matches(normalized)
    }

    fun escapeShellSingleQuotes(value: String): String {
        return value.replace("'", "'\"'\"'")
    }

    fun invalidatePath(rawPath: String?) {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return
        statCache.remove(normalized)
        val parent = normalized.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            statCache.remove(parent)
        }
    }

    fun invalidateAllCaches() {
        statCache.clear()
    }

    fun resetOnHotReload() {
        statCache.clear()
        synchronized(rootShellLock) {
            persistentShell?.close()
            persistentShell = null
            rootShellMode = null
            lastRootProbeFailMs = 0L
        }
    }

    fun peekCachedStat(rawPath: String?): PrivilegedStat? {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return null
        val entry = statCache[normalized] ?: return null
        return if (System.currentTimeMillis() - entry.first <= CACHE_TTL_MS) entry.second else null
    }

    fun listEntries(context: Context, rawPath: String): List<PrivilegedStat>? {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return null
        val mode = resolveRootShellMode(normalized)
        if (mode != null) {
            val fromRoot = rootListEntries(mode, normalized)
            if (fromRoot != null) {
                val now = System.currentTimeMillis()
                for (item in fromRoot) {
                    statCache[item.path] = now to item
                }
                return fromRoot
            }
        }
        val fromShizuku = shizukuListEntries(context, normalized) ?: return null
        val now = System.currentTimeMillis()
        for (item in fromShizuku) {
            statCache[item.path] = now to item
        }
        return fromShizuku
    }

    fun stat(context: Context, rawPath: String, useCache: Boolean = true): PrivilegedStat? {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return null
        if (useCache) {
            peekCachedStat(normalized)?.let { return it }
        }
        val mode = resolveRootShellMode(normalized)
        if (mode != null) {
            val fromRoot = rootStat(mode, normalized)
            if (fromRoot != null) {
                statCache[normalized] = System.currentTimeMillis() to fromRoot
                return fromRoot
            }
        }
        val fromShizuku = shizukuStat(context, normalized) ?: return null
        statCache[normalized] = System.currentTimeMillis() to fromShizuku
        return fromShizuku
    }

    fun mkdirs(context: Context, rawPath: String): Boolean {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return false
        invalidatePath(normalized)
        val mode = resolveRootShellMode(normalized)
        if (mode != null && rootMkdirs(mode, normalized)) {
            return true
        }
        return shizukuCallBoolean(context, ShizukuBridgeContract.METHOD_MKDIRS) {
            putString(ShizukuBridgeContract.KEY_PATH, normalized)
        }
    }

    fun deleteEntry(context: Context, rawPath: String, recursive: Boolean): Boolean {
        val normalized = normalizeAndValidateFusePath(rawPath) ?: return false
        if (isRestrictedRoot(normalized)) return false
        invalidatePath(normalized)
        val mode = resolveRootShellMode(normalized)
        if (mode != null && rootDelete(mode, normalized, recursive)) {
            return true
        }
        return shizukuCallBoolean(context, ShizukuBridgeContract.METHOD_DELETE) {
            putString(ShizukuBridgeContract.KEY_PATH, normalized)
            putBoolean(ShizukuBridgeContract.KEY_RECURSIVE, recursive)
        }
    }

    fun renameEntry(context: Context, rawSrc: String, rawDst: String): Boolean {
        val src = normalizeAndValidateFusePath(rawSrc) ?: return false
        val dst = normalizeAndValidateFusePath(rawDst) ?: return false
        if (isRestrictedRoot(src) || isRestrictedRoot(dst)) return false
        invalidatePath(src)
        invalidatePath(dst)
        val mode = resolveRootShellMode(src)
        if (mode != null && rootRename(mode, src, dst)) {
            return true
        }
        return shizukuCallBoolean(context, ShizukuBridgeContract.METHOD_RENAME) {
            putString(ShizukuBridgeContract.KEY_SRC_PATH, src)
            putString(ShizukuBridgeContract.KEY_DST_PATH, dst)
        }
    }

    fun moveCrossingBoundaryWithRoot(srcRaw: String, dstRaw: String): Boolean {
        val srcFuse = SDCARD_PREFIX_REGEX.replace(srcRaw, "/storage/emulated/0")
        val dstFuse = SDCARD_PREFIX_REGEX.replace(dstRaw, "/storage/emulated/0")
        val checkPath = normalizeAndValidateFusePath(srcFuse) ?: normalizeAndValidateFusePath(dstFuse) ?: return false
        val mode = resolveRootShellMode(checkPath) ?: return false
        val escSrc = escapeShellSingleQuotes(srcFuse)
        val escDst = escapeShellSingleQuotes(dstFuse)
        val escParent = escapeShellSingleQuotes(dstFuse.substringBeforeLast('/', ""))
        val cmd = if (escParent.isNotEmpty()) {
            "mkdir -p -- '$escParent' && mv -f -- '$escSrc' '$escDst'"
        } else {
            "mv -f -- '$escSrc' '$escDst'"
        }
        val res = execRootCommand(mode, cmd) ?: return false
        if (res.exitCode == 0) {
            invalidatePath(srcFuse)
            invalidatePath(dstFuse)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    fun openRead(context: Context, rawPath: String): InputStream {
        val normalized = normalizeAndValidateFusePath(rawPath)
            ?: throw FileNotFoundException("Disallowed path: $rawPath")
        val mode = resolveRootShellMode(normalized)
        if (mode != null) {
            val st = rootStat(mode, normalized)
            if (st != null) {
                if (!st.exists || st.isDir) {
                    throw FileNotFoundException("Not a readable file: $normalized")
                }
                return rootOpenRead(mode, normalized)
            }
        }
        return shizukuOpenRead(context, normalized)
    }

    @Throws(IOException::class)
    fun openWrite(context: Context, rawPath: String, append: Boolean): OutputStream {
        val normalized = normalizeAndValidateFusePath(rawPath)
            ?: throw FileNotFoundException("Disallowed path: $rawPath")
        invalidatePath(normalized)
        val mode = resolveRootShellMode(normalized)
        if (mode != null) {
            val opened = runCatching { rootOpenWrite(mode, normalized, append) }.getOrNull()
            if (opened != null) return opened
        }
        return shizukuOpenWrite(context, normalized, append)
    }

    fun hasAnyBackend(context: Context, sampleFusePath: String = "/storage/emulated/0/Android/data"): Boolean {
        if (resolveRootShellMode(sampleFusePath) != null) return true
        return isShizukuReady(context)
    }

    fun isShizukuReady(context: Context): Boolean {
        return runCatching {
            val res = context.contentResolver.call(
                Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                ShizukuBridgeContract.METHOD_PING,
                null,
                null
            ) ?: return false
            res.getBoolean(ShizukuBridgeContract.KEY_OK, false) &&
                res.getBoolean(ShizukuBridgeContract.KEY_SHIZUKU_GRANTED, false)
        }.getOrDefault(false)
    }

    private fun resolveRootShellMode(probeFusePath: String): RootShellMode? {
        rootShellMode?.let { return it }
        val now = System.currentTimeMillis()
        if (now - lastRootProbeFailMs < ROOT_PROBE_FAIL_TTL_MS) return null

        synchronized(rootShellLock) {
            rootShellMode?.let { return it }
            val probeRoot = extractProbeRoot(probeFusePath)
            val escProbe = escapeShellSingleQuotes(probeRoot)
            val candidates = listOf(
                RootShellMode.SU_SHELL_UID,
                RootShellMode.SU_U_FLAG_UID,
                RootShellMode.SU_PLAIN
            )
            for (candidate in candidates) {
                val ok = testRootMode(candidate, escProbe)
                if (ok) {
                    rootShellMode = candidate
                    return candidate
                }
            }
            lastRootProbeFailMs = System.currentTimeMillis()
            return null
        }
    }

    private fun extractProbeRoot(normalizedFusePath: String): String {
        val match = Regex("^(/storage/emulated/[0-9]+/Android/(data|obb))").find(normalizedFusePath)
        return match?.value ?: "/storage/emulated/0/Android/data"
    }

    private fun testRootMode(mode: RootShellMode, escapedProbeRoot: String): Boolean {
        return runCatching {
            val args = mode.prefixArgs + listOf("-c", "id && ls -d -- '$escapedProbeRoot' >/dev/null 2>&1")
            val proc = ProcessBuilder(args).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            val exited = proc.waitFor(4, TimeUnit.SECONDS)
            if (!exited) {
                proc.destroyForcibly()
                return false
            }
            if (proc.exitValue() != 0) return false
            when (mode) {
                RootShellMode.SU_SHELL_UID,
                RootShellMode.SU_U_FLAG_UID -> out.contains("uid=2000(") || out.contains("uid=0(")
                RootShellMode.SU_PLAIN -> out.contains("uid=0(")
            }
        }.getOrDefault(false)
    }

    private fun rootListEntries(mode: RootShellMode, normalizedDir: String): List<PrivilegedStat>? {
        val esc = escapeShellSingleQuotes(normalizedDir)
        val script = """
            d='$esc'
            [ -d "${'$'}d" ] || exit 2
            for f in "${'$'}d"/* "${'$'}d"/.*; do
              [ -e "${'$'}f" ] || [ -L "${'$'}f" ] || continue
              b=${'$'}{f##*/}
              [ "${'$'}b" = "." ] || [ "${'$'}b" = ".." ] && continue
              if [ -d "${'$'}f" ]; then t="D"; else t="F"; fi
              s=${'$'}(stat -L -c '%s|%Y' -- "${'$'}f" 2>/dev/null) || s=${'$'}(stat -c '%s|%Y' -- "${'$'}f" 2>/dev/null) || s="0|0"
              r=0; [ -r "${'$'}f" ] && r=1
              w=0; [ -w "${'$'}f" ] && w=1
              printf '%s\t%s\t%s\t%s\t%s\n' "${'$'}t" "${'$'}s" "${'$'}r" "${'$'}w" "${'$'}b"
            done
        """.trimIndent()
        val result = execRootCommand(mode, script) ?: return null
        if (result.exitCode != 0) return null

        val list = ArrayList<PrivilegedStat>()
        for (line in result.lines) {
            if (line.isEmpty()) continue
            val parts = line.split('\t', limit = 5)
            if (parts.size < 5) continue
            val isDir = parts[0] == "D"
            val sizeAndTime = parts[1].split('|')
            val size = if (!isDir) sizeAndTime.getOrNull(0)?.toLongOrNull() ?: 0L else 0L
            val mtimeMs = (sizeAndTime.getOrNull(1)?.toLongOrNull() ?: 0L) * 1000L
            val canRead = parts[2] == "1"
            val canWrite = parts[3] == "1"
            val name = parts[4]
            if (name.isEmpty()) continue
            list.add(
                PrivilegedStat(
                    exists = true,
                    name = name,
                    path = "$normalizedDir/$name",
                    isDir = isDir,
                    isFile = !isDir,
                    size = size,
                    lastModifiedMs = mtimeMs,
                    canRead = canRead,
                    canWrite = canWrite,
                    isHidden = name.startsWith(".")
                )
            )
        }
        return list
    }

    private fun rootStat(mode: RootShellMode, normalizedPath: String): PrivilegedStat? {
        val esc = escapeShellSingleQuotes(normalizedPath)
        val script = """
            f='$esc'
            if [ ! -e "${'$'}f" ] && [ ! -L "${'$'}f" ]; then
              printf 'MISSING\n'
              exit 0
            fi
            if [ -d "${'$'}f" ]; then t="D"; else t="F"; fi
            s=${'$'}(stat -L -c '%s|%Y' -- "${'$'}f" 2>/dev/null) || s=${'$'}(stat -c '%s|%Y' -- "${'$'}f" 2>/dev/null) || s="0|0"
            r=0; [ -r "${'$'}f" ] && r=1
            w=0; [ -w "${'$'}f" ] && w=1
            printf 'OK\t%s\t%s\t%s\t%s\n' "${'$'}t" "${'$'}s" "${'$'}r" "${'$'}w"
        """.trimIndent()
        val result = execRootCommand(mode, script) ?: return null
        if (result.exitCode != 0) return null
        val line = result.lines.firstOrNull { it == "MISSING" || it.startsWith("OK\t") } ?: return null
        val name = normalizedPath.substringAfterLast('/')
        if (line == "MISSING") {
            return PrivilegedStat(
                exists = false, name = name, path = normalizedPath,
                isDir = false, isFile = false, size = 0L, lastModifiedMs = 0L,
                canRead = false, canWrite = false, isHidden = name.startsWith(".")
            )
        }
        val parts = line.split('\t')
        if (parts.size < 5) return null
        val isDir = parts[1] == "D"
        val sizeAndTime = parts[2].split('|')
        val size = if (!isDir) sizeAndTime.getOrNull(0)?.toLongOrNull() ?: 0L else 0L
        val mtimeMs = (sizeAndTime.getOrNull(1)?.toLongOrNull() ?: 0L) * 1000L
        return PrivilegedStat(
            exists = true,
            name = name,
            path = normalizedPath,
            isDir = isDir,
            isFile = !isDir,
            size = size,
            lastModifiedMs = mtimeMs,
            canRead = parts[3] == "1",
            canWrite = parts[4] == "1",
            isHidden = name.startsWith(".")
        )
    }

    private fun rootMkdirs(mode: RootShellMode, normalizedPath: String): Boolean {
        val esc = escapeShellSingleQuotes(normalizedPath)
        val res = execRootCommand(mode, "mkdir -p -- '$esc'") ?: return false
        return res.exitCode == 0
    }

    private fun rootDelete(mode: RootShellMode, normalizedPath: String, recursive: Boolean): Boolean {
        val esc = escapeShellSingleQuotes(normalizedPath)
        val cmd = if (recursive) {
            "rm -rf -- '$esc'"
        } else {
            "if [ -d '$esc' ]; then rmdir -- '$esc'; else rm -f -- '$esc'; fi"
        }
        val res = execRootCommand(mode, cmd) ?: return false
        return res.exitCode == 0
    }

    private fun rootRename(mode: RootShellMode, src: String, dst: String): Boolean {
        val escSrc = escapeShellSingleQuotes(src)
        val escDst = escapeShellSingleQuotes(dst)
        val escParent = escapeShellSingleQuotes(dst.substringBeforeLast('/', ""))
        val cmd = if (escParent.isNotEmpty()) {
            "mkdir -p -- '$escParent' && mv -f -- '$escSrc' '$escDst'"
        } else {
            "mv -f -- '$escSrc' '$escDst'"
        }
        val res = execRootCommand(mode, cmd) ?: return false
        return res.exitCode == 0
    }

    @Throws(IOException::class)
    private fun rootOpenRead(mode: RootShellMode, normalizedPath: String): InputStream {
        val esc = escapeShellSingleQuotes(normalizedPath)
        val proc = ProcessBuilder(mode.prefixArgs + listOf("-c", "cat -- '$esc'")).start()
        return ProcessBackedInputStream(proc)
    }

    @Throws(IOException::class)
    private fun rootOpenWrite(mode: RootShellMode, normalizedPath: String, append: Boolean): OutputStream {
        val esc = escapeShellSingleQuotes(normalizedPath)
        val escParent = escapeShellSingleQuotes(normalizedPath.substringBeforeLast('/', ""))
        val op = if (append) ">>" else ">"
        val cmd = if (escParent.isNotEmpty()) {
            "mkdir -p -- '$escParent' && cat $op '$esc'"
        } else {
            "cat $op '$esc'"
        }
        val proc = ProcessBuilder(mode.prefixArgs + listOf("-c", cmd)).start()
        return ProcessBackedOutputStream(proc)
    }

    private data class ShellExecResult(val exitCode: Int, val lines: List<String>)

    private fun execRootCommand(mode: RootShellMode, command: String): ShellExecResult? {
        synchronized(rootShellLock) {
            var session = persistentShell
            if (session == null || !session.isAlive() || session.mode != mode) {
                session?.close()
                session = runCatching { PersistentShellSession(mode) }.getOrNull()
                persistentShell = session
            }
            if (session != null) {
                val res = session.run(command)
                if (res != null) return res
                session.close()
                persistentShell = null
            }
            // One-shot retry after re-establishing shell
            val fresh = runCatching { PersistentShellSession(mode) }.getOrNull() ?: return null
            persistentShell = fresh
            val retry = fresh.run(command)
            if (retry == null) {
                fresh.close()
                persistentShell = null
            }
            return retry
        }
    }

    private class PersistentShellSession(val mode: RootShellMode) {
        private val process: Process = ProcessBuilder(mode.prefixArgs).redirectErrorStream(true).start()
        private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        private val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))

        fun isAlive(): Boolean = process.isAlive

        fun run(command: String): ShellExecResult? {
            return try {
                val marker = "__FXPLUS_END_${UUID.randomUUID().toString().replace("-", "")}__"
                writer.write(command)
                writer.newLine()
                writer.write("printf '\\n%s:%d\\n' '$marker' ${'$'}?")
                writer.newLine()
                writer.flush()

                val lines = ArrayList<String>()
                var result: ShellExecResult? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("$marker:")) {
                        val code = line.substringAfter("$marker:").trim().toIntOrNull() ?: 1
                        result = ShellExecResult(code, lines)
                        break
                    }
                    lines.add(line)
                }
                result
            } catch (_: Throwable) {
                null
            }
        }

        fun close() {
            runCatching {
                writer.write("exit\n")
                writer.flush()
            }
            runCatching { writer.close() }
            runCatching { reader.close() }
            runCatching { process.destroy() }
        }
    }

    private class ProcessBackedInputStream(
        private val process: Process
    ) : FilterInputStream(process.inputStream) {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            runCatching { super.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        }
    }

    private class ProcessBackedOutputStream(
        private val process: Process
    ) : FilterOutputStream(process.outputStream) {
        private var closed = false

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }

        override fun close() {
            if (closed) return
            closed = true
            var ioFailure: IOException? = null
            try {
                out.flush()
            } catch (e: IOException) {
                ioFailure = e
            }
            try {
                out.close()
            } catch (e: IOException) {
                if (ioFailure == null) ioFailure = e
            }
            val errText = runCatching {
                process.errorStream.bufferedReader().use { it.readText() }.trim()
            }.getOrDefault("")
            val finished = runCatching {
                process.waitFor(STREAM_WAIT_TIMEOUT_SEC, TimeUnit.SECONDS)
            }.getOrDefault(false)
            if (!finished) {
                process.destroyForcibly()
                throw ioFailure ?: IOException("Timed out waiting for privileged write")
            }
            val code = runCatching { process.exitValue() }.getOrDefault(1)
            runCatching { process.inputStream.close() }
            if (ioFailure != null) throw ioFailure
            if (code != 0) {
                throw IOException("Privileged write failed ($code): $errText")
            }
        }
    }

    private fun shizukuListEntries(context: Context, normalizedPath: String): List<PrivilegedStat>? {
        return runCatching {
            val extras = Bundle().apply {
                putString(ShizukuBridgeContract.KEY_PATH, normalizedPath)
            }
            val reply = context.contentResolver.call(
                Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                ShizukuBridgeContract.METHOD_LIST,
                null,
                extras
            ) ?: return null
            if (!reply.getBoolean(ShizukuBridgeContract.KEY_OK, false)) return null
            val rawJson = reply.getString(ShizukuBridgeContract.KEY_ENTRIES_JSON) ?: return null
            val arr = JSONArray(rawJson)
            val result = ArrayList<PrivilegedStat>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(parseStatJson(obj))
            }
            result
        }.getOrNull()
    }

    private fun shizukuStat(context: Context, normalizedPath: String): PrivilegedStat? {
        return runCatching {
            val extras = Bundle().apply {
                putString(ShizukuBridgeContract.KEY_PATH, normalizedPath)
            }
            val reply = context.contentResolver.call(
                Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                ShizukuBridgeContract.METHOD_STAT,
                null,
                extras
            ) ?: return null
            if (!reply.getBoolean(ShizukuBridgeContract.KEY_OK, false)) return null
            val rawJson = reply.getString(ShizukuBridgeContract.KEY_STAT_JSON) ?: return null
            parseStatJson(JSONObject(rawJson))
        }.getOrNull()
    }

    private fun shizukuCallBoolean(
        context: Context,
        method: String,
        buildExtras: Bundle.() -> Unit
    ): Boolean {
        return runCatching {
            val extras = Bundle().apply(buildExtras)
            val reply = context.contentResolver.call(
                Uri.parse(ShizukuBridgeContract.CONTENT_URI_BASE),
                method,
                null,
                extras
            ) ?: return false
            reply.getBoolean(ShizukuBridgeContract.KEY_OK, false) &&
                reply.getBoolean(ShizukuBridgeContract.KEY_RESULT, false)
        }.getOrDefault(false)
    }

    @Throws(IOException::class)
    private fun shizukuOpenRead(context: Context, normalizedPath: String): InputStream {
        val pfd = context.contentResolver.openFileDescriptor(
            ShizukuBridgeContract.buildFileUri(normalizedPath),
            "r"
        ) ?: throw FileNotFoundException("Unable to open Shizuku read FD: $normalizedPath")
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    @Throws(IOException::class)
    private fun shizukuOpenWrite(context: Context, normalizedPath: String, append: Boolean): OutputStream {
        val mode = if (append) "wa" else "wt"
        val pfd = context.contentResolver.openFileDescriptor(
            ShizukuBridgeContract.buildFileUri(normalizedPath),
            mode
        ) ?: throw FileNotFoundException("Unable to open Shizuku write FD: $normalizedPath")
        return ParcelFileDescriptor.AutoCloseOutputStream(pfd)
    }

    private fun parseStatJson(obj: JSONObject): PrivilegedStat {
        return PrivilegedStat(
            exists = obj.optBoolean(ShizukuBridgeContract.JSON_EXISTS, false),
            name = obj.optString(ShizukuBridgeContract.JSON_NAME, ""),
            path = obj.optString(ShizukuBridgeContract.JSON_PATH, ""),
            isDir = obj.optBoolean(ShizukuBridgeContract.JSON_IS_DIR, false),
            isFile = obj.optBoolean(ShizukuBridgeContract.JSON_IS_FILE, false),
            size = obj.optLong(ShizukuBridgeContract.JSON_SIZE, 0L),
            lastModifiedMs = obj.optLong(ShizukuBridgeContract.JSON_LAST_MODIFIED, 0L),
            canRead = obj.optBoolean(ShizukuBridgeContract.JSON_CAN_READ, false),
            canWrite = obj.optBoolean(ShizukuBridgeContract.JSON_CAN_WRITE, false),
            isHidden = obj.optBoolean(ShizukuBridgeContract.JSON_IS_HIDDEN, false)
        )
    }
}
