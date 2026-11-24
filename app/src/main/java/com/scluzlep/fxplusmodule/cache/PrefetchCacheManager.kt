package com.scluzlep.fxplusmodule.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.scluzlep.fxplusmodule.config.ConfigContract
import com.scluzlep.fxplusmodule.config.ModuleConfig
import com.scluzlep.fxplusmodule.config.PrefetchLimitMode
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class PrefetchItem(
    val catalogIdentity: String,
    val path: String,
    val lastModifiedMs: Long,
    val sizeBytes: Long,
    val mimeType: String? = null,
    val isRemote: Boolean = true,
    val opener: (() -> InputStream?)? = null,
    val folderKey: String = defaultFolderKey(catalogIdentity, path)
) {
    val cacheKey: String by lazy {
        PrefetchCacheManager.computeCacheKey(catalogIdentity, path, lastModifiedMs, sizeBytes)
    }

    val folderHash: String by lazy {
        PrefetchCacheManager.computeFolderHash(folderKey)
    }

    companion object {
        fun defaultFolderKey(catalogIdentity: String, path: String): String {
            return "$catalogIdentity|${path.substringBeforeLast('/', "")}"
        }
    }
}

data class CacheStats(
    val entryCount: Int,
    val totalBytes: Long,
    val cacheDirPath: String
)

fun interface StorageSpaceProvider {
    fun usableBytes(dir: File): Long
}

fun interface NetworkPolicyChecker {
    fun isUnmeteredOrWifi(): Boolean
}

/**
 * Bounded disk-only raw-byte LRU cache (`cacheDir/fxplus_prefetch`) with single-flight
 * deduplication, atomic `.part -> .bin` writes, direction-aware `N+-k` / capacity / both
 * window planning, rapid-swipe cancellation, and zero background `Bitmap` decoding.
 */
class PrefetchCacheManager(
    baseCacheDir: File,
    private val configProvider: () -> ModuleConfig = { ModuleConfig() },
    private val storageSpaceProvider: StorageSpaceProvider = StorageSpaceProvider { dir ->
        if (!dir.exists()) dir.mkdirs()
        dir.usableSpace
    },
    private val networkChecker: NetworkPolicyChecker = NetworkPolicyChecker { true },
    private val executorFactory: (Int) -> ExecutorService = { threads ->
        val seq = AtomicInteger(0)
        Executors.newFixedThreadPool(
            threads.coerceIn(ConfigContract.MIN_PREFETCH_CONCURRENCY, ConfigContract.MAX_PREFETCH_CONCURRENCY),
            ThreadFactory { r ->
                Thread(r, "fxplus-prefetch-${seq.incrementAndGet()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }
        )
    }
) {

    private class InFlightDownload(
        val key: String,
        val folderHash: String,
        val generation: Long,
        val future: CompletableFuture<File?>
    ) {
        val cancelled = AtomicBoolean(false)
        val foregroundWaiters = AtomicInteger(0)
        val activeStream = AtomicReference<Closeable?>(null)
        @Volatile
        var taskFuture: Future<*>? = null

        fun requestCancelIfNotForeground() {
            if (foregroundWaiters.get() > 0) return
            if (cancelled.compareAndSet(false, true)) {
                runCatching { activeStream.getAndSet(null)?.close() }
                taskFuture?.cancel(true)
            }
        }

        fun forceCancel() {
            if (cancelled.compareAndSet(false, true)) {
                runCatching { activeStream.getAndSet(null)?.close() }
                taskFuture?.cancel(true)
            }
        }
    }

    val cacheRoot: File = File(baseCacheDir, ConfigContract.PREFETCH_CACHE_DIR_NAME)

    private val inFlight = ConcurrentHashMap<String, InFlightDownload>()
    private val accessOrder = ConcurrentHashMap<String, Long>()
    private val keyToFolderHash = ConcurrentHashMap<String, String>()
    private val accessClock = AtomicLong(0L)
    private val generationCounter = AtomicLong(0L)
    private val activeSession = AtomicReference<String?>(null)

    private val ioLock = Any()
    private val executorLock = Any()

    @Volatile
    private var protectedKeys: Set<String> = emptySet()

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var executorThreads: Int = 0

    init {
        ensureCacheDir()
        cleanupOrphanPartFiles()
    }

    fun cleanupOrphanPartFiles() {
        ensureCacheDir()
        val files = cacheRoot.listFiles() ?: return
        for (file in files) {
            if (file.isFile && (file.name.endsWith(".part") || file.name.contains(".part."))) {
                runCatching { file.delete() }
            }
        }
    }

    fun beginSession(sessionId: String) {
        activeSession.set(sessionId)
    }

    fun endSession(sessionId: String) {
        if (activeSession.compareAndSet(sessionId, null)) {
            cancelAllPending()
            protectedKeys = emptySet()
        }
    }

    fun peekCachedFile(item: PrefetchItem): File? {
        val hash = item.folderHash
        keyToFolderHash[item.cacheKey] = hash
        return peekCachedFileInternal(item.cacheKey, item.sizeBytes, hash)
    }

    fun peekCachedFileByKey(key: String, expectedSizeBytes: Long): File? {
        return peekCachedFileInternal(key, expectedSizeBytes, knownFolderHash = null)
    }

    private fun peekCachedFileInternal(
        key: String,
        expectedSizeBytes: Long,
        knownFolderHash: String?
    ): File? {
        val file = binFileForKey(key, knownFolderHash)
        if (!file.exists() || !file.isFile) return null
        val len = file.length()
        if (len <= 0L || (expectedSizeBytes > 0L && len != expectedSizeBytes)) {
            runCatching { file.delete() }
            accessOrder.remove(key)
            keyToFolderHash.remove(key)
            return null
        }
        touchEntry(key, file)
        return file
    }

    /**
     * Foreground viewer path: returns cached `.bin` instantaneously if present, joins an
     * existing in-flight prefetch without duplicate network transfer, or downloads
     * directly on the caller thread while registering in [inFlight] for dedup.
     */
    fun awaitOrDownload(item: PrefetchItem, openerOverride: (() -> InputStream?)? = null): File? {
        peekCachedFile(item)?.let { return it }

        val key = item.cacheKey
        val folderHash = item.folderHash
        keyToFolderHash[key] = folderHash
        val existing = inFlight[key]
        if (existing != null && !existing.cancelled.get()) {
            existing.foregroundWaiters.incrementAndGet()
            return try {
                existing.future.get()
            } catch (_: Throwable) {
                null
            } finally {
                existing.foregroundWaiters.decrementAndGet()
            }
        }

        val opener = openerOverride ?: item.opener ?: return null
        val gen = generationCounter.get()
        val future = CompletableFuture<File?>()
        val entry = InFlightDownload(key, folderHash, gen, future)

        val prev = inFlight.putIfAbsent(key, entry)
        if (prev != null && !prev.cancelled.get()) {
            prev.foregroundWaiters.incrementAndGet()
            return try {
                prev.future.get()
            } catch (_: Throwable) {
                null
            } finally {
                prev.foregroundWaiters.decrementAndGet()
            }
        }
        entry.foregroundWaiters.incrementAndGet()
        if (prev != null) {
            inFlight[key] = entry
        }

        return try {
            val file = downloadToAtomicFile(item, opener, entry, checkStorageGuard = false)
            future.complete(file)
            file
        } catch (t: Throwable) {
            future.complete(null)
            null
        } finally {
            entry.foregroundWaiters.decrementAndGet()
            inFlight.remove(key, entry)
        }
    }

    /**
     * Schedules background prefetch candidates around [currentIndex] according to the active
     * [PrefetchLimitMode], direction `dir` (`+1` forward, `-1` backward), single-file cap,
     * Wi-Fi guard, and storage space guard. Returns the list of candidate keys selected.
     */
    fun scheduleWindow(
        items: List<PrefetchItem>,
        currentIndex: Int,
        direction: Int = 1,
        sessionId: String? = null
    ): List<String> {
        if (sessionId != null) {
            activeSession.set(sessionId)
        }
        val cfg = configProvider().sanitized()
        if (!cfg.enableAdjacentPrefetch || items.isEmpty() || currentIndex !in items.indices) {
            cancelAllPending()
            protectedKeys = emptySet()
            return emptyList()
        }

        val currentKey = items[currentIndex].cacheKey
        val selected = selectCandidates(items, currentIndex, direction, cfg)
        val windowKeys = LinkedHashSet<String>(selected.size + 1)
        windowKeys.add(currentKey)
        for (item in selected) {
            windowKeys.add(item.cacheKey)
        }
        protectedKeys = windowKeys

        // Cancel stale out-of-window prefetches on rapid swipe unless foreground is awaiting them.
        for ((key, entry) in inFlight) {
            if (key !in windowKeys) {
                entry.requestCancelIfNotForeground()
            }
        }

        if (cfg.prefetchWifiOnly && !networkChecker.isUnmeteredOrWifi()) {
            for (entry in inFlight.values) {
                entry.requestCancelIfNotForeground()
            }
            enforceCacheBudget(cfg, windowKeys)
            return emptyList()
        }

        val gen = generationCounter.incrementAndGet()
        val scheduledKeys = ArrayList<String>(selected.size)
        for (candidate in selected) {
            scheduledKeys.add(candidate.cacheKey)
            if (peekCachedFile(candidate) != null) continue
            enqueueBackground(candidate, gen, cfg)
        }

        enforceCacheBudget(cfg, windowKeys)
        return scheduledKeys
    }

    fun selectCandidates(
        items: List<PrefetchItem>,
        currentIndex: Int,
        direction: Int,
        config: ModuleConfig = configProvider().sanitized()
    ): List<PrefetchItem> {
        if (items.isEmpty() || currentIndex !in items.indices) return emptyList()
        val mode = config.prefetchLimitMode
        val effectiveSingleCap = config.effectivePrefetchSingleFileBytes(mode)
        val diskCapBytes = config.prefetchDiskCacheBytes()
        val maxDepth = when (mode) {
            PrefetchLimitMode.COUNT_ONLY,
            PrefetchLimitMode.BOTH -> config.prefetchCount.coerceIn(
                ConfigContract.MIN_PREFETCH_COUNT,
                ConfigContract.MAX_PREFETCH_COUNT
            )
            PrefetchLimitMode.CAPACITY_ONLY -> CAPACITY_SCAN_MAX_DEPTH
        }

        val orderedIndices = buildCandidateIndices(items.size, currentIndex, direction, maxDepth)
        val selected = ArrayList<PrefetchItem>(orderedIndices.size)
        var cumulativeBytes = 0L

        for (idx in orderedIndices) {
            val item = items[idx]
            if (!item.isRemote) continue
            val itemSize = item.sizeBytes.coerceAtLeast(0L)
            if (itemSize > 0L && itemSize > effectiveSingleCap) continue

            when (mode) {
                PrefetchLimitMode.COUNT_ONLY -> {
                    selected.add(item)
                }
                PrefetchLimitMode.CAPACITY_ONLY,
                PrefetchLimitMode.BOTH -> {
                    if (cumulativeBytes + itemSize > diskCapBytes) {
                        break
                    }
                    cumulativeBytes += itemSize
                    selected.add(item)
                }
            }
        }
        return selected
    }

    fun onConfigUpdated(newConfig: ModuleConfig) {
        val clean = newConfig.sanitized()
        if (!clean.enableAdjacentPrefetch || (clean.prefetchWifiOnly && !networkChecker.isUnmeteredOrWifi())) {
            cancelAllPending()
        }
        synchronized(executorLock) {
            val currentExec = executor
            if (currentExec != null && executorThreads != clean.prefetchConcurrency) {
                currentExec.shutdownNow()
                executor = null
                executorThreads = 0
            }
        }
        enforceCacheBudget(clean, protectedKeys)
    }

    fun enforceCacheBudget(
        config: ModuleConfig = configProvider().sanitized(),
        protected: Set<String> = protectedKeys
    ) {
        synchronized(ioLock) {
            ensureCacheDir()
            val binFiles = cacheRoot.listFiles { file ->
                file.isFile && file.name.endsWith(".bin")
            }?.toMutableList() ?: return

            if (binFiles.isEmpty()) return

            val maxBytes = config.prefetchDiskCacheBytes()
            var totalBytes = binFiles.sumOf { it.length() }
            var totalEntries = binFiles.size

            val needsTrim: () -> Boolean = when (config.prefetchLimitMode) {
                PrefetchLimitMode.COUNT_ONLY -> {
                    val maxHistory = maxOf(12, 4 * config.prefetchCount)
                    ({ totalEntries > maxHistory || totalBytes > maxBytes })
                }
                PrefetchLimitMode.CAPACITY_ONLY,
                PrefetchLimitMode.BOTH -> {
                    ({ totalBytes > maxBytes })
                }
            }

            if (!needsTrim()) return

            binFiles.sortWith(compareBy<File>(
                { it.lastModified() },
                { accessOrder[keyFromFile(it)] ?: 0L },
                { it.name }
            ))

            for (file in binFiles) {
                if (!needsTrim()) break
                val key = keyFromFile(file)
                if (key in protected) continue
                val fileSize = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) {
                    totalBytes = (totalBytes - fileSize).coerceAtLeast(0L)
                    totalEntries = (totalEntries - 1).coerceAtLeast(0)
                    accessOrder.remove(key)
                    keyToFolderHash.remove(key)
                }
            }
        }
    }

    fun invalidate(key: String) {
        inFlight[key]?.forceCancel()
        synchronized(ioLock) {
            accessOrder.remove(key)
            val knownHash = keyToFolderHash.remove(key)
            runCatching { binFileForKey(key, knownHash).delete() }
            cacheRoot.listFiles()?.forEach { file ->
                if (file.isFile && (file.name == "$key.bin" || file.name.endsWith("_$key.bin"))) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    fun evictFolder(folderKey: String): Set<String> {
        if (folderKey.isBlank()) return emptySet()
        val targetHash = computeFolderHash(folderKey)
        return evictWhere { hash -> hash != null && hash == targetHash }
    }

    fun retainOnlyFolders(activeFolderKeys: Set<String>): Set<String> {
        val activeHashes = activeFolderKeys
            .filter { it.isNotBlank() }
            .mapTo(HashSet()) { computeFolderHash(it) }
        return evictWhere { hash -> hash == null || hash !in activeHashes }
    }

    private fun evictWhere(shouldEvictHash: (String?) -> Boolean): Set<String> {
        val evictedKeys = LinkedHashSet<String>()

        fun cancelMatchingInFlight() {
            for ((key, entry) in inFlight) {
                if (shouldEvictHash(entry.folderHash)) {
                    entry.forceCancel()
                    evictedKeys.add(key)
                }
            }
        }

        cancelMatchingInFlight()
        synchronized(ioLock) {
            cancelMatchingInFlight()
            ensureCacheDir()
            cacheRoot.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val name = file.name
                if (!isCacheFileName(name)) return@forEach
                val hash = folderHashFromFileName(name)
                if (shouldEvictHash(hash)) {
                    val key = keyFromCacheFileName(name)
                    runCatching { file.delete() }
                    if (key != null) {
                        evictedKeys.add(key)
                    }
                }
            }
            for ((key, hash) in keyToFolderHash) {
                if (shouldEvictHash(hash)) {
                    evictedKeys.add(key)
                }
            }
            purgeTrackedKeys(evictedKeys)
        }
        return evictedKeys
    }

    private fun purgeTrackedKeys(evictedKeys: Set<String>) {
        if (evictedKeys.isEmpty()) return
        for (key in evictedKeys) {
            accessOrder.remove(key)
            keyToFolderHash.remove(key)
        }
        if (protectedKeys.isNotEmpty()) {
            protectedKeys = protectedKeys - evictedKeys
        }
    }

    fun clearCache() {
        cancelAllPending()
        synchronized(ioLock) {
            accessOrder.clear()
            keyToFolderHash.clear()
            protectedKeys = emptySet()
            cacheRoot.listFiles()?.forEach { file ->
                if (file.isFile) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    fun stats(): CacheStats {
        ensureCacheDir()
        var count = 0
        var bytes = 0L
        cacheRoot.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".bin")) {
                count++
                bytes += file.length()
            }
        }
        return CacheStats(count, bytes, cacheRoot.absolutePath)
    }

    fun cancelAllPending() {
        for (entry in inFlight.values) {
            entry.forceCancel()
        }
    }

    fun shutdown() {
        cancelAllPending()
        synchronized(executorLock) {
            executor?.shutdownNow()
            executor = null
            executorThreads = 0
        }
        protectedKeys = emptySet()
    }

    internal fun inFlightCount(): Int = inFlight.size

    private fun enqueueBackground(item: PrefetchItem, generation: Long, config: ModuleConfig) {
        val opener = item.opener ?: return
        val key = item.cacheKey
        val folderHash = item.folderHash
        keyToFolderHash[key] = folderHash
        val existing = inFlight[key]
        if (existing != null && !existing.cancelled.get()) return

        val future = CompletableFuture<File?>()
        val entry = InFlightDownload(key, folderHash, generation, future)
        val prev = inFlight.putIfAbsent(key, entry)
        if (prev != null && !prev.cancelled.get()) return
        if (prev != null) {
            inFlight[key] = entry
        }

        val exec = getOrCreateExecutor(config.prefetchConcurrency)
        try {
            entry.taskFuture = exec.submit {
                try {
                    if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
                        future.complete(null)
                        return@submit
                    }
                    val file = downloadToAtomicFile(item, opener, entry, checkStorageGuard = true)
                    future.complete(file)
                    if (file != null) {
                        enforceCacheBudget(configProvider().sanitized(), protectedKeys)
                    }
                } catch (_: Throwable) {
                    future.complete(null)
                } finally {
                    inFlight.remove(key, entry)
                }
            }
        } catch (_: Throwable) {
            inFlight.remove(key, entry)
            future.complete(null)
        }
    }

    private fun downloadToAtomicFile(
        item: PrefetchItem,
        opener: () -> InputStream?,
        entry: InFlightDownload,
        checkStorageGuard: Boolean
    ): File? {
        ensureCacheDir()
        val key = item.cacheKey
        val folderHash = item.folderHash
        keyToFolderHash[key] = folderHash
        peekCachedFileInternal(key, item.sizeBytes, folderHash)?.let { return it }

        if (checkStorageGuard) {
            val usable = runCatching { storageSpaceProvider.usableBytes(cacheRoot) }.getOrDefault(0L)
            val required = MIN_PHYSICAL_FREE_BYTES + item.sizeBytes.coerceAtLeast(0L)
            if (usable < required) {
                return null
            }
        }

        val stream = try {
            opener()
        } catch (_: Throwable) {
            null
        } ?: return null

        entry.activeStream.set(stream)
        val partFile = File(cacheRoot, "${folderHash}_${key}.part.${UUID.randomUUID().toString().substring(0, 8)}")
        var succeeded = false
        var written = 0L

        try {
            stream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
                            return null
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
                            return null
                        }
                        output.write(buffer, 0, read)
                        written += read
                    }
                    output.fd.sync()
                }
            }
            if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
                return null
            }
            if (written <= 0L) {
                return null
            }
            if (item.sizeBytes > 0L && written != item.sizeBytes) {
                return null
            }

            val target = binFileForKey(key, folderHash)
            synchronized(ioLock) {
                if (entry.cancelled.get() || Thread.currentThread().isInterrupted || !partFile.exists()) {
                    return null
                }
                if (target.exists() && target.length() == written) {
                    touchEntry(key, target)
                    succeeded = true
                    return target
                }
                if (target.exists()) {
                    target.delete()
                }
                if (!partFile.renameTo(target)) {
                    throw IOException("Failed to rename part file to ${target.name}")
                }
                touchEntry(key, target)
                succeeded = true
                return target
            }
        } catch (_: Throwable) {
            return null
        } finally {
            entry.activeStream.compareAndSet(stream, null)
            if (!succeeded) {
                runCatching { partFile.delete() }
            }
        }
    }

    private fun getOrCreateExecutor(concurrency: Int): ExecutorService {
        val targetThreads = concurrency.coerceIn(
            ConfigContract.MIN_PREFETCH_CONCURRENCY,
            ConfigContract.MAX_PREFETCH_CONCURRENCY
        )
        val current = executor
        if (current != null && !current.isShutdown && !current.isTerminated && executorThreads == targetThreads) {
            return current
        }
        synchronized(executorLock) {
            val again = executor
            if (again != null && !again.isShutdown && !again.isTerminated && executorThreads == targetThreads) {
                return again
            }
            again?.shutdownNow()
            val created = executorFactory(targetThreads)
            executor = created
            executorThreads = targetThreads
            return created
        }
    }

    private fun ensureCacheDir() {
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs()
        }
    }

    private fun touchEntry(key: String, file: File) {
        val clock = accessClock.incrementAndGet()
        accessOrder[key] = clock
        folderHashFromFileName(file.name)?.let { keyToFolderHash[key] = it }
        runCatching { file.setLastModified(System.currentTimeMillis()) }
    }

    private fun binFileForKey(key: String, folderHash: String? = null): File {
        val knownHash = folderHash ?: keyToFolderHash[key]
        if (knownHash != null) {
            return File(cacheRoot, "${knownHash}_$key.bin")
        }
        val existing = cacheRoot.listFiles()?.firstOrNull { file ->
            file.isFile && file.name.endsWith("_$key.bin") && folderHashFromFileName(file.name) != null
        }
        if (existing != null) {
            folderHashFromFileName(existing.name)?.let { keyToFolderHash[key] = it }
            return existing
        }
        return File(cacheRoot, "$key.bin")
    }

    private fun keyFromFile(file: File): String {
        return keyFromCacheFileName(file.name) ?: file.name.removeSuffix(".bin")
    }

    companion object {
        private const val COPY_BUFFER_BYTES = 65_536
        private const val FOLDER_HASH_HEX_LEN = 16
        const val MIN_PHYSICAL_FREE_BYTES = 512L * 1024L * 1024L
        const val CAPACITY_SCAN_MAX_DEPTH = 50

        @Volatile
        private var sharedInstance: PrefetchCacheManager? = null
        private val sharedLock = Any()

        fun getOrCreate(
            context: Context,
            configProvider: () -> ModuleConfig
        ): PrefetchCacheManager {
            val appCtx = context.applicationContext ?: context
            sharedInstance?.let { return it }
            synchronized(sharedLock) {
                sharedInstance?.let { return it }
                val created = PrefetchCacheManager(
                    baseCacheDir = appCtx.cacheDir,
                    configProvider = configProvider,
                    networkChecker = NetworkPolicyChecker { isUnmeteredOrWifi(appCtx) }
                )
                sharedInstance = created
                return created
            }
        }

        fun peekInstance(): PrefetchCacheManager? = sharedInstance

        fun shutdownShared() {
            sharedInstance?.shutdown()
        }

        fun buildCandidateIndices(
            total: Int,
            currentIndex: Int,
            direction: Int,
            maxDepth: Int
        ): List<Int> {
            if (total <= 1 || currentIndex !in 0 until total || maxDepth <= 0) return emptyList()
            val dir = if (direction >= 0) 1 else -1
            val result = ArrayList<Int>(maxDepth * 2)
            val seen = HashSet<Int>(maxDepth * 2)
            for (d in 1..maxDepth) {
                val primary = currentIndex + dir * d
                if (primary in 0 until total && primary != currentIndex && seen.add(primary)) {
                    result.add(primary)
                }
                val secondary = currentIndex - dir * d
                if (secondary in 0 until total && secondary != currentIndex && seen.add(secondary)) {
                    result.add(secondary)
                }
            }
            return result
        }

        fun computeCacheKey(
            catalogIdentity: String,
            path: String,
            lastModifiedMs: Long,
            sizeBytes: Long
        ): String {
            return sha256Hex("$catalogIdentity|$path|$lastModifiedMs|$sizeBytes")
        }

        fun computeFolderHash(folderKey: String): String {
            return sha256Hex(folderKey).substring(0, FOLDER_HASH_HEX_LEN)
        }

        private fun sha256Hex(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            val hex = StringBuilder(digest.size * 2)
            for (b in digest) {
                val v = b.toInt() and 0xFF
                hex.append(HEX_CHARS[v ushr 4])
                hex.append(HEX_CHARS[v and 0x0F])
            }
            return hex.toString()
        }

        internal fun folderHashFromFileName(name: String): String? {
            if (name.length <= FOLDER_HASH_HEX_LEN + 1 || name[FOLDER_HASH_HEX_LEN] != '_') {
                return null
            }
            for (i in 0 until FOLDER_HASH_HEX_LEN) {
                val c = name[i]
                val isLowerHex = (c in '0'..'9') || (c in 'a'..'f')
                if (!isLowerHex) return null
            }
            return name.substring(0, FOLDER_HASH_HEX_LEN)
        }

        private fun isCacheFileName(name: String): Boolean {
            return name.endsWith(".bin") || name.endsWith(".part") || ".part." in name
        }

        private fun keyFromCacheFileName(name: String): String? {
            val stem = when {
                name.endsWith(".bin") -> name.removeSuffix(".bin")
                ".part." in name -> name.substringBefore(".part.")
                name.endsWith(".part") -> name.removeSuffix(".part")
                else -> return null
            }
            if (stem.isEmpty()) return null
            return if (folderHashFromFileName(name) != null) {
                stem.substring(FOLDER_HASH_HEX_LEN + 1).ifEmpty { null }
            } else {
                stem
            }
        }

        private fun isUnmeteredOrWifi(context: Context): Boolean {
            return runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return true
                val active = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(active) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }.getOrDefault(true)
        }

        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
