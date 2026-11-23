package com.scluzlep.fxplusmodule.xposed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.scluzlep.fxplusmodule.cache.PrefetchCacheManager
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.math.sqrt

/**
 * Unlocks FX's 4 MiB remote thumbnail generation cap across `9012` (`dd.h.b`) and
 * `9108` (`wg.e.j`), reuses already-prefetched `.bin` files on flash without double network
 * downloads, and supports OOM-safe `highResThumbnails` (`1024px` / `2097152` pixel cap).
 */
object RemoteThumbnailHook {

    private const val VANILLA_REMOTE_SIZE_GATE_BYTES = 4_194_304L
    private const val HIGH_RES_PIXEL_CAP = 2_097_152
    private const val HIGH_RES_TARGET_DIM = 1024
    private const val HIGH_RES_DIAG_DIM = 1448
    private const val ENCODE_QUALITY = 78

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val rt = symbols.remoteThumb

        installer.install("thumb:loadThumbnail", rt.loadThumbnail) { chain ->
            handleLoadThumbnail(symbols, installer, chain)
        }

        installer.install("thumb:remoteWorker", rt.remoteThumbWorkerRun) { chain ->
            handleWorkerCacheReuse(symbols, installer, chain)
        }

        when (symbols.profile) {
            FxProfile.FX_9012 -> {
                installer.install("thumb:9012:exceedsPixelCap", rt.thumbGeneratorExceedsCap) { chain ->
                    if (!FxConfigBridge.current.highResThumbnails) {
                        return@install chain.proceed()
                    }
                    val dim = chain.getArg(0) ?: return@install chain.proceed()
                    val w = runCatching { dim.javaClass.getField("f").getInt(dim) }.getOrNull()
                        ?: return@install chain.proceed()
                    val h = runCatching { dim.javaClass.getField("i").getInt(dim) }.getOrNull()
                        ?: return@install chain.proceed()
                    w.toLong() * h.toLong() < HIGH_RES_PIXEL_CAP.toLong()
                }
                installer.install("thumb:9012:generateFromStream", rt.thumbGeneratorGenerate) { chain ->
                    if (!FxConfigBridge.current.highResThumbnails) {
                        return@install chain.proceed()
                    }
                    val streamSource = chain.getArg(0) ?: return@install chain.proceed()
                    val outFile = chain.getArg(1) as? File ?: return@install chain.proceed()
                    val srcPath = chain.getArg(2) as? String
                    generateHighResThumb9012(symbols, streamSource, outFile, srcPath)
                        ?: chain.proceed()
                }
            }
            FxProfile.FX_9108 -> {
                installer.install("thumb:9108:generate", rt.thumbGeneratorRun) { chain ->
                    if (!FxConfigBridge.current.highResThumbnails) {
                        return@install chain.proceed()
                    }
                    val gen = chain.thisObject ?: return@install chain.proceed()
                    handleHighRes9108(symbols, gen) ?: chain.proceed()
                }
            }
            FxProfile.STRUCTURAL_FALLBACK -> Unit
        }
    }

    private fun handleLoadThumbnail(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val ctx = chain.getArg(0) as? Context ?: return chain.proceed()
        val item = chain.getArg(1) ?: return chain.proceed()
        val size = (chain.getArg(2) as? Int) ?: return chain.proceed()
        val origCallback = chain.getArg(3) ?: return chain.proceed()
        val cache = chain.getArg(4)
        val force = (chain.getArg(5) as? Boolean) ?: false

        val s = symbols.sibling
        val rt = symbols.remoteThumb
        val isFileItem = s.fileItemClass?.isInstance(item) == true
        val isRemoteMarker = rt.remoteItemMarkerClass?.isInstance(item) == true
        val isCloudMarker = rt.cloudThumbMarkerClass?.isInstance(item) == true
        if (!isFileItem || (!isRemoteMarker && !isCloudMarker)) {
            return chain.proceed()
        }

        val config = FxConfigBridge.current
        val callbackClass = rt.thumbCallbackClass ?: return chain.proceed()
        var vanillaEnqueued = false

        val proxyCallback = Proxy.newProxyInstance(
            symbols.classLoader,
            arrayOf(callbackClass)
        ) { _, method, args ->
            if (method.name == "a" && args != null && args.size == 1) {
                if (!config.enableRemoteThumbnails) {
                    return@newProxyInstance null
                }
                val maxBytes = config.maxRemoteThumbnailSizeBytes()
                val itemSize = runCatching { (s.getSize?.invoke(item) as? Number)?.toLong() ?: 0L }.getOrDefault(0L)
                if (maxBytes < Long.MAX_VALUE && itemSize > maxBytes) {
                    return@newProxyInstance null
                }
                vanillaEnqueued = true
            }
            if (args == null) method.invoke(origCallback) else method.invoke(origCallback, *args)
        }

        val result = chain.proceed(arrayOf(ctx, item, size, proxyCallback, cache, force))

        if (!vanillaEnqueued && config.enableRemoteThumbnails) {
            val mime = runCatching { s.getMimeType?.invoke(item) as? String }.getOrNull()
            val itemSize = runCatching { (s.getSize?.invoke(item) as? Number)?.toLong() ?: -1L }.getOrDefault(-1L)
            val maxBytes = config.maxRemoteThumbnailSizeBytes()

            val isImage = mime != null && mime.startsWith("image/", ignoreCase = true)
            val prefKey = computeRemotePrefKey(isRemoteMarker, isCloudMarker)
            val shouldUnlock = shouldUnlockRemoteThumbnail(
                isRemoteMarker = isRemoteMarker,
                isCloudMarker = isCloudMarker,
                isImage = isImage,
                itemSize = itemSize,
                maxCapBytes = maxBytes
            ) && isFxRemoteThumbsAllowed(symbols, ctx, prefKey)

            if (shouldUnlock) {
                val ctor = rt.remoteThumbTaskCtor
                val enqueueMethod = callbackClass.declaredMethods.firstOrNull {
                    it.name == "a" && it.parameterTypes.size == 1
                }
                if (ctor != null && enqueueMethod != null) {
                    runCatching {
                        val task = ctor.newInstance(ctx, item, size, cache, prefKey)
                        enqueueMethod.invoke(origCallback, task)
                        installer.logDebug("Unlocked $prefKey thumbnail for size=$itemSize mime=$mime")
                    }.onFailure { err ->
                        installer.logWarn("Failed to enqueue unlocked remote thumbnail", err)
                    }
                }
            }
        }
        return result
    }

    internal fun computeRemotePrefKey(isRemoteMarker: Boolean, isCloudMarker: Boolean): String {
        return if (isCloudMarker && !isRemoteMarker) "Remote" else "Network"
    }

    internal fun shouldUnlockRemoteThumbnail(
        isRemoteMarker: Boolean,
        isCloudMarker: Boolean,
        isImage: Boolean,
        itemSize: Long,
        maxCapBytes: Long
    ): Boolean {
        if (!isRemoteMarker && !isCloudMarker) return false
        if (!isImage) return false
        val withinCap = maxCapBytes == Long.MAX_VALUE || itemSize <= maxCapBytes
        if (!withinCap) return false
        return isCloudMarker || itemSize > VANILLA_REMOTE_SIZE_GATE_BYTES
    }

    private fun handleWorkerCacheReuse(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        chain: XposedInterface.Chain
    ): Any? {
        val rt = symbols.remoteThumb
        val worker = chain.thisObject ?: return chain.proceed()

        if (symbols.profile == FxProfile.FX_9108) {
            val caseId = runCatching { rt.remoteThumbWorkerCaseField?.getInt(worker) }.getOrNull() ?: return chain.proceed()
            if (caseId != 2) return chain.proceed()
        }

        val task = runCatching { rt.remoteThumbWorkerOwnerField?.get(worker) }.getOrNull() ?: return chain.proceed()
        val item = runCatching { rt.thumbTaskItemField?.get(task) }.getOrNull() ?: return chain.proceed()
        val ctx = runCatching { rt.thumbTaskContextField?.get(task) as? Context }.getOrNull() ?: return chain.proceed()
        val tag = runCatching { rt.remoteThumbTaskMimeField?.get(task) as? String }.getOrNull() ?: return chain.proceed()

        val cachedSource = ViewerPrefetchHook.peekCachedFileForItem(symbols, item) ?: return chain.proceed()
        if (!cachedSource.exists() || cachedSource.length() <= 0L) return chain.proceed()

        return runCatching {
            val thumbOutFile = rt.thumbTaskGetCacheFile?.invoke(null, ctx, item, null, tag) as? File
                ?: return@runCatching chain.proceed()

            val generatedFile: File? = if (thumbOutFile.exists() && thumbOutFile.length() > 0L) {
                thumbOutFile.setLastModified(System.currentTimeMillis())
                thumbOutFile
            } else {
                val gen = rt.thumbGeneratorCreateForFile?.invoke(null, ctx, cachedSource.absolutePath, thumbOutFile)
                    ?: return@runCatching chain.proceed()
                val curFlags = rt.thumbGeneratorFlagsField?.getInt(gen) ?: 0
                rt.thumbGeneratorFlagsField?.setInt(gen, curFlags or 1)
                val info = rt.thumbGeneratorRun?.invoke(gen)
                if (info != null) thumbOutFile else null
            }

            if (generatedFile != null) {
                val box = rt.remoteThumbWorkerResultField?.get(worker)
                if (box != null) {
                    rt.thumbResultField?.set(box, generatedFile)
                }
                installer.logDebug("Reused prefetch disk cache for remote thumbnail: ${cachedSource.name}")
                null
            } else {
                chain.proceed()
            }
        }.getOrElse { err ->
            installer.logWarn("Cache reuse thumbnail fallback", err)
            chain.proceed()
        }
    }

    private fun isFxRemoteThumbsAllowed(
        symbols: ResolvedSymbols,
        ctx: Context,
        prefKey: String
    ): Boolean {
        val rt = symbols.remoteThumb
        return runCatching {
            val store = rt.settingsGetInstance?.invoke(null, ctx) ?: return true
            val thumbsOn = (rt.settingsThumbsEnabled?.invoke(store) as? Boolean) ?: true
            val scopeOn = (rt.settingsThumbsForPath?.invoke(store, prefKey) as? Boolean) ?: true
            thumbsOn && scopeOn
        }.getOrDefault(true)
    }

    private fun generateHighResThumb9012(
        symbols: ResolvedSymbols,
        streamSource: Any,
        outFile: File,
        srcPath: String?
    ): Any? {
        val rt = symbols.remoteThumb
        val readMethod = rt.thumbGeneratorStreamRead ?: return null
        val infoCtor = rt.thumbImageInfoCtor ?: return null
        return generateHighResThumbCommon(
            openStream = { runCatching { readMethod.invoke(streamSource) as? InputStream }.getOrNull() },
            outFile = outFile,
            srcPath = srcPath,
            buildInfo = { path, w, h, mime -> infoCtor.newInstance(path, w, h, mime) }
        )
    }

    private fun handleHighRes9108(symbols: ResolvedSymbols, gen: Any): Any? {
        val rt = symbols.remoteThumb
        val outFile = runCatching { rt.thumbGeneratorOutFileField?.get(gen) as? File }.getOrNull() ?: return null
        if (outFile.exists() && outFile.length() > 0L) return null
        val streamSource = runCatching { rt.thumbGeneratorStreamField?.get(gen) }.getOrNull() ?: return null
        val srcPath = runCatching { rt.thumbGeneratorSourcePathField?.get(gen) as? String }.getOrNull()
        val flags = runCatching { rt.thumbGeneratorFlagsField?.getInt(gen) ?: 0 }.getOrDefault(0)
        if ((flags and 1) == 0) return null
        val readMethod = rt.thumbGeneratorStreamRead ?: return null
        val infoCtor = rt.thumbImageInfoCtor ?: return null

        return generateHighResThumbCommon(
            openStream = { runCatching { readMethod.invoke(streamSource) as? InputStream }.getOrNull() },
            outFile = outFile,
            srcPath = srcPath,
            buildInfo = { path, w, h, mime -> infoCtor.newInstance(w, h, path, mime) }
        )
    }

    private inline fun generateHighResThumbCommon(
        openStream: () -> InputStream?,
        outFile: File,
        srcPath: String?,
        buildInfo: (path: String, w: Int, h: Int, mime: String) -> Any
    ): Any? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = openStream() ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            if (srcPath != null && rawW.toLong() * rawH.toLong() < HIGH_RES_PIXEL_CAP.toLong()) {
                val mime = guessMime(srcPath)
                if (mime != null) {
                    return buildInfo(srcPath, rawW, rawH, mime)
                }
            }

            val rotation = runCatching {
                openStream()?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            }.getOrDefault(0)

            val aspect = rawW.toDouble() / rawH.toDouble()
            val targetMax: Int
            val metric: Int
            if (aspect > 2.0 || aspect < 0.5) {
                targetMax = HIGH_RES_DIAG_DIM
                metric = sqrt((rawW.toDouble() * rawW) + (rawH.toDouble() * rawH)).toInt()
            } else {
                targetMax = HIGH_RES_TARGET_DIM
                metric = if (aspect < 1.0) rawW else rawH
            }
            var sampleSize = 1
            while (metric / sampleSize > targetMax) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null
            val oriented = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            val isWebp = outFile.absolutePath.endsWith(".webp", ignoreCase = true)
            try {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    val fmt = if (isWebp) {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    oriented.compress(fmt, ENCODE_QUALITY, fos)
                }
                val mime = if (isWebp) "image/webp" else "image/jpeg"
                buildInfo(outFile.absolutePath, oriented.width, oriented.height, mime)
            } finally {
                oriented.recycle()
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun guessMime(path: String): String? {
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> null
        }
    }
}
