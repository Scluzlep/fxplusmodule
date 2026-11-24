@file:Suppress("DEPRECATION")

package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.WeakHashMap

/**
 * Replaces `TouchImageDisplay`'s `> 2160 px` `LAYER_TYPE_SOFTWARE` downgrade with GPU
 * hardware rendering, adds bounded LRU hardware tiling for oversized bitmaps that exceed
 * canvas limits or safe single-bitmap upload budgets, and upgrades animated GIF playback
 * from 20fps `android.graphics.Movie` (plus blurry preview `prescale`) to VSYNC-driven
 * `AnimatedImageDrawable`.
 */
object TouchImageHardwareHook {

    const val SAFE_SINGLE_BITMAP_BYTES = 96L * 1024L * 1024L
    const val DEFAULT_TILE_SIZE = 2048
    const val MAX_CACHED_TILES_PER_VIEW = 24

    private const val STREAM_BUFFER_BYTES = 65_536
    private const val HTTP_TIMEOUT_MS = 15_000

    internal val TINY_GIF_1X1: ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
        0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0x21, 0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00,
        0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
        0x02, 0x02, 0x44, 0x01, 0x00,
        0x3B
    )

    data class TileSpec(
        val row: Int,
        val col: Int,
        val rawLeft: Int,
        val rawTop: Int,
        val rawRight: Int,
        val rawBottom: Int,
        val srcSubLeft: Int,
        val srcSubTop: Int,
        val srcSubRight: Int,
        val srcSubBottom: Int,
        val dstLeft: Int,
        val dstTop: Int,
        val dstRight: Int,
        val dstBottom: Int
    ) {
        val rawWidth: Int get() = rawRight - rawLeft
        val rawHeight: Int get() = rawBottom - rawTop
    }

    internal class BoundedTileLru<K, V>(
        private val maxEntries: Int
    ) {
        private val map = LinkedHashMap<K, V>(maxEntries + 8, 0.75f, true)

        val size: Int get() = map.size

        fun get(key: K): V? = map[key]

        fun put(
            key: K,
            value: V,
            protectedKeys: Set<K> = emptySet(),
            onEvicted: ((K, V) -> Unit)? = null
        ) {
            map[key] = value
            trimToLimit(protectedKeys, onEvicted)
        }

        fun remove(key: K): V? = map.remove(key)

        fun valuesSnapshot(): List<V> = map.values.toList()

        fun clear() {
            map.clear()
        }

        private fun trimToLimit(
            protectedKeys: Set<K>,
            onEvicted: ((K, V) -> Unit)?
        ) {
            while (map.size > maxEntries) {
                val iterator = map.entries.iterator()
                var evictedEntry: MutableMap.MutableEntry<K, V>? = null
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (candidate.key !in protectedKeys) {
                        evictedEntry = candidate
                        iterator.remove()
                        break
                    }
                }
                if (evictedEntry == null) {
                    break
                }
                onEvicted?.invoke(evictedEntry.key, evictedEntry.value)
            }
        }
    }

    private class TileSession(
        val sourceBitmap: Bitmap,
        val maxDim: Int,
        val specs: List<TileSpec>
    ) {
        val lock = Any()
        val lru = BoundedTileLru<Int, Bitmap>(MAX_CACHED_TILES_PER_VIEW)

        @Volatile
        var cleared: Boolean = false

        private var sliceableBitmap: Bitmap? = null
        private var ownsSliceableBitmap: Boolean = false

        fun ensureSliceableBitmap(): Bitmap? {
            if (cleared || sourceBitmap.isRecycled) return null
            sliceableBitmap?.takeIf { !it.isRecycled }?.let { return it }
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                sourceBitmap.config == Bitmap.Config.HARDWARE
            ) {
                ownsSliceableBitmap = true
                runCatching { sourceBitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            } else {
                ownsSliceableBitmap = false
                sourceBitmap
            }
            sliceableBitmap = resolved
            return resolved
        }

        fun recycleAll() {
            synchronized(lock) {
                cleared = true
                for (bmp in lru.valuesSnapshot()) {
                    if (bmp !== sourceBitmap && bmp !== sliceableBitmap && !bmp.isRecycled) {
                        runCatching { bmp.recycle() }
                    }
                }
                lru.clear()
                val ownSlice = sliceableBitmap
                if (ownsSliceableBitmap && ownSlice != null && ownSlice !== sourceBitmap && !ownSlice.isRecycled) {
                    runCatching { ownSlice.recycle() }
                }
                sliceableBitmap = null
                ownsSliceableBitmap = false
            }
        }
    }

    private class AnimatedGifState(
        val viewRef: WeakReference<View>,
        val drawable: AnimatedImageDrawable,
        val callback: Drawable.Callback
    ) {
        @Volatile
        var active: Boolean = true

        fun stopAndDetach() {
            active = false
            val view = viewRef.get()
            val stopAction = Runnable {
                runCatching { drawable.stop() }
                runCatching { drawable.callback = null }
                view?.invalidate()
            }
            val mainLooper = Looper.getMainLooper()
            if (mainLooper == null || Looper.myLooper() == mainLooper || view == null) {
                stopAction.run()
            } else if (!view.post(stopAction)) {
                stopAction.run()
            }
        }
    }

    private class AnimatedViewCallback(
        view: View,
        private val isActive: () -> Boolean
    ) : Drawable.Callback {
        private val viewRef = WeakReference(view)

        override fun invalidateDrawable(who: Drawable) {
            if (!isActive()) return
            val view = viewRef.get() ?: return
            view.postInvalidateOnAnimation()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            if (!isActive()) return
            val view = viewRef.get() ?: return
            val delayMs = (`when` - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            view.postDelayed(what, delayMs)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            val view = viewRef.get() ?: return
            view.removeCallbacks(what)
        }
    }

    private sealed interface GifDecodeResult {
        class Decoded(val drawable: Drawable) : GifDecodeResult
        data object Cancelled : GifDecodeResult
        data object Fallback : GifDecodeResult
    }

    private data class HardwareRenderBindings(
        val touchClass: Class<*>?,
        val setImageBitmap: Method?,
        val setImageMovie: Method?,
        val setImageNull: Method?,
        val setPrescale: Method?,
        val setTouchEnabled: Method?,
        val zoomFit: Method?,
        val onDraw: Method?,
        val onDetachedFromWindow: Method?,
        val updateBoundsMethod: Method?,
        val enforceLayerOnBitmapMethod: Method?,
        val bitmapField: Field?,
        val movieField: Field?,
        val moviePlayingField: Field?,
        val movieThreadField: Field?,
        val matrixField: Field?,
        val paintField: Field?,
        val cropArField: Field?,
        val cropRectField: Field?,
        val softwareLayerFlagField: Field?,
        val displaySizeField: Field?,
        val displaySizeWidthField: Field?,
        val displaySizeHeightField: Field?,
        val needsZoomFitField: Field?,
        val imageDisplayClass: Class<*>?,
        val deliverBitmapMethod: Method?,
        val deliverMovieMethod: Method?,
        val clearDisplayMethod: Method?,
        val displayBitmapField: Field?,
        val displayMovieField: Field?,
        val displayDisposedField: Field?,
        val displayFullLoadedField: Field?,
        val displayPreviewLoadedField: Field?,
        val viewerClass: Class<*>?,
        val loadMediaMethod: Method?,
        val loadGifMethod: Method?,
        val applyBitmapResultMethod: Method?,
        val imageResultCtor: Constructor<*>?,
        val loadTokenField: Field?,
        val isGifField: Field?,
        val currentHolderField: Field?,
        val enterTransitionField: Field?,
        val transitionBgViewField: Field?,
        val transitionTopBarField: Field?,
        val transitionBottomBarField: Field?,
        val transitionRunningField: Field?,
        val transitionDoneField: Field?,
        val uiHandlerField: Field?,
        val holderFileItemField: Field?,
        val holderUriField: Field?,
        val gifAnimatedCheckMethod: Method?,
        val localPathClass: Class<*>?,
        val localPathMethod: Method?
    )

    private val stateLock = Any()
    private val tileSessions = WeakHashMap<View, TileSession>()
    private val animatedGifStates = WeakHashMap<View, AnimatedGifState>()

    @Volatile
    private var hardwareImagePredicate: () -> Boolean = { true }

    @Volatile
    private var hardwareGifPredicate: () -> Boolean = { true }

    @Volatile
    private var lastImageEnabled: Boolean? = null

    @Volatile
    private var lastGifEnabled: Boolean? = null

    @Volatile
    private var bindings: HardwareRenderBindings? = null

    @Volatile
    private var tinySentinelMovie: Movie? = null

    fun needsHardwareTiling(
        width: Int,
        height: Int,
        byteCount: Long,
        maxCanvasDim: Int
    ): Boolean {
        val effectiveMaxDim = if (maxCanvasDim in 2048..16384) maxCanvasDim else 8192
        return width > effectiveMaxDim || height > effectiveMaxDim || byteCount > SAFE_SINGLE_BITMAP_BYTES
    }

    fun buildTileSpecs(
        bitmapWidth: Int,
        bitmapHeight: Int,
        tileSize: Int = DEFAULT_TILE_SIZE,
        maxDim: Int = 8192
    ): List<TileSpec> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
        val effectiveMaxDim = if (maxDim in 2048..16384) maxDim else 8192
        val requestedTile = if (tileSize > 0) tileSize else DEFAULT_TILE_SIZE
        val effectiveTile = minOf(requestedTile, (effectiveMaxDim - 2).coerceAtLeast(1))
        val cols = (bitmapWidth + effectiveTile - 1) / effectiveTile
        val rows = (bitmapHeight + effectiveTile - 1) / effectiveTile
        val specs = ArrayList<TileSpec>(rows * cols)
        for (row in 0 until rows) {
            val logicalTop = row * effectiveTile
            val logicalBottom = minOf(bitmapHeight, logicalTop + effectiveTile)
            val rawTop = maxOf(0, logicalTop - 1)
            val rawBottom = minOf(bitmapHeight, logicalBottom + 1)
            val srcSubTop = logicalTop - rawTop
            val srcSubBottom = logicalBottom - rawTop
            for (col in 0 until cols) {
                val logicalLeft = col * effectiveTile
                val logicalRight = minOf(bitmapWidth, logicalLeft + effectiveTile)
                val rawLeft = maxOf(0, logicalLeft - 1)
                val rawRight = minOf(bitmapWidth, logicalRight + 1)
                specs += TileSpec(
                    row = row,
                    col = col,
                    rawLeft = rawLeft,
                    rawTop = rawTop,
                    rawRight = rawRight,
                    rawBottom = rawBottom,
                    srcSubLeft = logicalLeft - rawLeft,
                    srcSubTop = srcSubTop,
                    srcSubRight = logicalRight - rawLeft,
                    srcSubBottom = srcSubBottom,
                    dstLeft = logicalLeft,
                    dstTop = logicalTop,
                    dstRight = logicalRight,
                    dstBottom = logicalBottom
                )
            }
        }
        return specs
    }

    fun selectVisibleTiles(
        tiles: List<TileSpec>,
        visibleLeft: Float,
        visibleTop: Float,
        visibleRight: Float,
        visibleBottom: Float
    ): List<TileSpec> {
        if (tiles.isEmpty()) return emptyList()
        if (visibleLeft.isNaN() || visibleTop.isNaN() || visibleRight.isNaN() || visibleBottom.isNaN()) {
            return emptyList()
        }
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return emptyList()
        return tiles.filter { tile ->
            tile.dstRight > visibleLeft &&
                tile.dstLeft < visibleRight &&
                tile.dstBottom > visibleTop &&
                tile.dstTop < visibleBottom
        }
    }

    fun selectVisibleTiles(
        tiles: List<TileSpec>,
        visibleLeft: Int,
        visibleTop: Int,
        visibleRight: Int,
        visibleBottom: Int
    ): List<TileSpec> = selectVisibleTiles(
        tiles = tiles,
        visibleLeft = visibleLeft.toFloat(),
        visibleTop = visibleTop.toFloat(),
        visibleRight = visibleRight.toFloat(),
        visibleBottom = visibleBottom.toFloat()
    )

    fun install(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        isHardwareImageEnabled: () -> Boolean = { true },
        isHardwareGifEnabled: () -> Boolean = { true }
    ) {
        hardwareImagePredicate = isHardwareImageEnabled
        hardwareGifPredicate = isHardwareGifEnabled
        lastImageEnabled = runCatching { isHardwareImageEnabled() }.getOrDefault(true)
        lastGifEnabled = runCatching { isHardwareGifEnabled() }.getOrDefault(true)

        val b = resolveBindings(symbols)
        bindings = b

        installTouchDisplayHooks(symbols, installer, b)
        installGifHooks(symbols, installer, b)
    }

    fun clearOnHotReload() {
        clearAllTileSessions()
        clearAllAnimatedGifStates()
        bindings = null
        tinySentinelMovie = null
        lastImageEnabled = null
        lastGifEnabled = null
        hardwareImagePredicate = { true }
        hardwareGifPredicate = { true }
    }

    fun onConfigChanged(
        hardwareImageEnabled: Boolean,
        hardwareGifEnabled: Boolean
    ) {
        val prevImage = lastImageEnabled
        val prevGif = lastGifEnabled
        lastImageEnabled = hardwareImageEnabled
        lastGifEnabled = hardwareGifEnabled

        if (!hardwareImageEnabled || (prevImage != null && prevImage != hardwareImageEnabled)) {
            clearAllTileSessions()
        }
        if (!hardwareGifEnabled || (prevGif != null && prevGif != hardwareGifEnabled)) {
            clearAllAnimatedGifStates()
        }
    }

    private fun isHardwareImageEnabled(): Boolean = runCatching { hardwareImagePredicate() }.getOrDefault(true)

    private fun isHardwareGifEnabled(): Boolean = runCatching { hardwareGifPredicate() }.getOrDefault(true)

    private fun installTouchDisplayHooks(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        b: HardwareRenderBindings
    ) {
        if (symbols.profile == FxProfile.FX_9012) {
            installer.install("hw:9012:TouchImageDisplay.g", b.enforceLayerOnBitmapMethod) { chain ->
                if (!isHardwareImageEnabled()) {
                    return@install chain.proceed()
                }
                val view = chain.thisObject as? View ?: return@install chain.proceed()
                b.softwareLayerFlagField?.let { runCatching { it.setBoolean(view, false) } }
                if (view.layerType != View.LAYER_TYPE_NONE) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
                null
            }
        }

        installer.install("hw:TouchImageDisplay.setImageBitmap", b.setImageBitmap) { chain ->
            val view = chain.thisObject as? View ?: return@install chain.proceed()
            val newBitmap = chain.getArg(0) as? Bitmap
            val oldBitmap = b.bitmapField?.let { runCatching { it.get(view) as? Bitmap }.getOrNull() }

            clearAnimatedGifState(view)
            val hwImage = isHardwareImageEnabled()
            if (newBitmap == null || newBitmap !== oldBitmap || !hwImage) {
                clearTileSession(view)
            }

            if (!hwImage) {
                return@install chain.proceed()
            }

            if (symbols.profile == FxProfile.FX_9108) {
                val isOversize = newBitmap != null && (newBitmap.width > 2160 || newBitmap.height > 2160)
                b.softwareLayerFlagField?.let { runCatching { it.setBoolean(view, isOversize) } }
            }

            val result = chain.proceed()

            b.softwareLayerFlagField?.let { runCatching { it.setBoolean(view, false) } }
            if (view.layerType != View.LAYER_TYPE_NONE) {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            (b.paintField?.let { runCatching { it.get(view) as? Paint }.getOrNull() })?.isFilterBitmap = true
            result
        }

        installer.install("hw:TouchImageDisplay.setImageMovie", b.setImageMovie) { chain ->
            (chain.thisObject as? View)?.let { view ->
                clearTileSession(view)
                clearAnimatedGifState(view)
            }
            chain.proceed()
        }

        installer.install("hw:TouchImageDisplay.setImageNull", b.setImageNull) { chain ->
            (chain.thisObject as? View)?.let { view ->
                clearTileSession(view)
                clearAnimatedGifState(view)
            }
            chain.proceed()
        }

        installer.install("hw:TouchImageDisplay.onDetachedFromWindow", b.onDetachedFromWindow) { chain ->
            (chain.thisObject as? View)?.let { view ->
                clearTileSession(view)
                clearAnimatedGifState(view)
            }
            chain.proceed()
        }

        installer.install("hw:TouchImageDisplay.onDraw", b.onDraw) { chain ->
            val view = chain.thisObject as? View ?: return@install chain.proceed()
            val canvas = chain.getArg(0) as? Canvas ?: return@install chain.proceed()

            val gifState = getAnimatedGifState(view)
            if (gifState != null && isHardwareGifEnabled()) {
                val matrix = b.matrixField?.let { runCatching { it.get(view) as? Matrix }.getOrNull() }
                if (matrix != null) {
                    val cropClipped = applyCropClipIfNeeded(view, canvas, b)
                    try {
                        canvas.save()
                        canvas.concat(matrix)
                        try {
                            gifState.drawable.draw(canvas)
                        } finally {
                            canvas.restore()
                        }
                    } finally {
                        if (cropClipped) {
                            canvas.restore()
                        }
                    }
                    return@install null
                }
            }

            if (!isHardwareImageEnabled()) {
                return@install chain.proceed()
            }

            val bitmap = b.bitmapField?.let { runCatching { it.get(view) as? Bitmap }.getOrNull() }
                ?: return@install chain.proceed()
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return@install chain.proceed()
            }

            val maxCanvasDim = minOf(canvas.maximumBitmapWidth, canvas.maximumBitmapHeight)
            val paint = b.paintField?.let { runCatching { it.get(view) as? Paint }.getOrNull() }
                ?: Paint(Paint.FILTER_BITMAP_FLAG)
            paint.isFilterBitmap = true

            if (!needsHardwareTiling(bitmap.width, bitmap.height, bitmap.allocationByteCount.toLong(), maxCanvasDim)) {
                return@install chain.proceed()
            }

            val matrix = b.matrixField?.let { runCatching { it.get(view) as? Matrix }.getOrNull() }
                ?: return@install chain.proceed()
            val viewWidth = view.width
            val viewHeight = view.height
            if (viewWidth <= 0 || viewHeight <= 0) {
                return@install null
            }

            val inverse = Matrix()
            if (!matrix.invert(inverse)) {
                return@install chain.proceed()
            }
            val visibleSrc = RectF()
            inverse.mapRect(visibleSrc, RectF(-2f, -2f, viewWidth + 2f, viewHeight + 2f))
            val visLeft = visibleSrc.left.coerceIn(0f, bitmap.width.toFloat())
            val visTop = visibleSrc.top.coerceIn(0f, bitmap.height.toFloat())
            val visRight = visibleSrc.right.coerceIn(0f, bitmap.width.toFloat())
            val visBottom = visibleSrc.bottom.coerceIn(0f, bitmap.height.toFloat())

            val session = getOrCreateTileSession(view, bitmap, maxCanvasDim)
            val visibleTiles = selectVisibleTiles(session.specs, visLeft, visTop, visRight, visBottom)
            if (visibleTiles.isEmpty()) {
                return@install null
            }

            val drawn = drawTiledBitmap(view, canvas, session, visibleTiles, matrix, paint, b)
            if (!drawn) {
                return@install chain.proceed()
            }
            null
        }
    }

    private fun installGifHooks(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        b: HardwareRenderBindings
    ) {
        installer.install("hw:gif:probeBuffered", b.gifAnimatedCheckMethod) { chain ->
            val stream = chain.getArg(0) as? InputStream
            if (stream != null && stream !is BufferedInputStream && isHardwareGifEnabled()) {
                return@install chain.proceed(arrayOf(BufferedInputStream(stream, STREAM_BUFFER_BYTES)))
            }
            chain.proceed()
        }

        when (symbols.profile) {
            FxProfile.FX_9012 -> install9012GifHooks(symbols, installer, b)
            FxProfile.FX_9108 -> install9108GifHooks(symbols, installer, b)
            FxProfile.STRUCTURAL_FALLBACK -> Unit
        }
    }

    private fun install9012GifHooks(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        b: HardwareRenderBindings
    ) {
        installer.install("hw:9012:l1", b.loadGifMethod) { chain ->
            if (!isHardwareGifEnabled()) {
                return@install chain.proceed()
            }
            val activity = chain.thisObject as? Activity ?: return@install chain.proceed()
            val display = chain.getArg(0) as? View ?: return@install chain.proceed()
            val holder = chain.getArg(1) ?: return@install chain.proceed()
            val token = (chain.getArg(2) as? Int) ?: return@install chain.proceed()

            val item = b.holderFileItemField?.let { runCatching { it.get(holder) }.getOrNull() }
            val uri = b.holderUriField?.let { runCatching { it.get(holder) as? Uri }.getOrNull() }
            if (item == null && uri == null) {
                return@install chain.proceed()
            }

            when (val outcome = decodeGifFromItem(activity, symbols, b, item, uri)) {
                is GifDecodeResult.Cancelled -> return@install null
                is GifDecodeResult.Fallback -> return@install chain.proceed()
                is GifDecodeResult.Decoded -> {
                    val posted = deliverDecodedGifOnUi(
                        activity = activity,
                        display = display,
                        holder = holder,
                        token = token,
                        drawable = outcome.drawable,
                        symbols = symbols,
                        b = b,
                        installer = installer
                    )
                    if (!posted) {
                        return@install chain.proceed()
                    }
                    null
                }
            }
        }

        installer.install("hw:9012:j1.uriGif", b.loadMediaMethod) { chain ->
            if (!isHardwareGifEnabled()) {
                return@install chain.proceed()
            }
            val activity = chain.thisObject as? Activity ?: return@install chain.proceed()
            val display = chain.getArg(0) as? View ?: return@install chain.proceed()
            val holder = chain.getArg(1) ?: return@install chain.proceed()
            val flags = (chain.getArg(2) as? Int) ?: 0
            if ((flags and 1) != 0) {
                return@install chain.proceed()
            }

            val item = b.holderFileItemField?.let { runCatching { it.get(holder) }.getOrNull() }
            val uri = b.holderUriField?.let { runCatching { it.get(holder) as? Uri }.getOrNull() }
            if (item != null || uri == null || !isGifUri(activity, uri)) {
                return@install chain.proceed()
            }

            val tokenField = b.loadTokenField ?: return@install chain.proceed()
            val token = runCatching {
                val next = tokenField.getInt(activity) + 1
                tokenField.setInt(activity, next)
                next
            }.getOrNull() ?: return@install chain.proceed()

            when (val outcome = decodeGifFromItem(activity, symbols, b, item = null, uri = uri)) {
                is GifDecodeResult.Cancelled -> return@install null
                is GifDecodeResult.Fallback -> return@install chain.proceed()
                is GifDecodeResult.Decoded -> {
                    val posted = deliverDecodedGifOnUi(
                        activity = activity,
                        display = display,
                        holder = holder,
                        token = token,
                        drawable = outcome.drawable,
                        symbols = symbols,
                        b = b,
                        installer = installer
                    )
                    if (!posted) {
                        return@install chain.proceed()
                    }
                    null
                }
            }
        }
    }

    private fun install9108GifHooks(
        symbols: ResolvedSymbols,
        installer: FxHookInstaller,
        b: HardwareRenderBindings
    ) {
        installer.install("hw:9108:C", b.loadMediaMethod) { chain ->
            if (!isHardwareGifEnabled()) {
                return@install chain.proceed()
            }
            val activity = chain.thisObject as? Activity ?: return@install chain.proceed()
            val display = chain.getArg(0) as? View ?: return@install chain.proceed()
            val holder = chain.getArg(1) ?: return@install chain.proceed()
            val flags = (chain.getArg(2) as? Int) ?: 0
            if ((flags and 1) != 0) {
                return@install chain.proceed()
            }

            val item = b.holderFileItemField?.let { runCatching { it.get(holder) }.getOrNull() }
            val uri = b.holderUriField?.let { runCatching { it.get(holder) as? Uri }.getOrNull() }
            val mime = item?.let { runCatching { symbols.sibling.getMimeType?.invoke(it) as? String }.getOrNull() }
            val isGif = (item != null && "image/gif".equals(mime, ignoreCase = true)) ||
                (item == null && isGifUri(activity, uri))
            if (!isGif) {
                return@install chain.proceed()
            }

            val tokenField = b.loadTokenField ?: return@install chain.proceed()
            val token = runCatching {
                val next = tokenField.getInt(activity) + 1
                tokenField.setInt(activity, next)
                next
            }.getOrNull() ?: return@install chain.proceed()

            when (val outcome = decodeGifFromItem(activity, symbols, b, item, uri)) {
                is GifDecodeResult.Cancelled -> return@install null
                is GifDecodeResult.Fallback -> return@install chain.proceed()
                is GifDecodeResult.Decoded -> {
                    val posted = deliverDecodedGifOnUi(
                        activity = activity,
                        display = display,
                        holder = holder,
                        token = token,
                        drawable = outcome.drawable,
                        symbols = symbols,
                        b = b,
                        installer = installer
                    )
                    if (!posted) {
                        return@install chain.proceed()
                    }
                    null
                }
            }
        }
    }

    private fun decodeGifFromItem(
        activity: Activity,
        symbols: ResolvedSymbols,
        b: HardwareRenderBindings,
        item: Any?,
        uri: Uri?
    ): GifDecodeResult {
        val source = try {
            createImageDecoderSource(activity, symbols, b, item, uri)
        } catch (t: Throwable) {
            val cause = if (t is InvocationTargetException) t.targetException ?: t else t
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                return GifDecodeResult.Cancelled
            }
            return GifDecodeResult.Fallback
        } ?: return GifDecodeResult.Fallback

        return runCatching {
            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_DEFAULT
            }
            when (drawable) {
                is AnimatedImageDrawable -> GifDecodeResult.Decoded(drawable)
                is BitmapDrawable -> {
                    val bmp = drawable.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        GifDecodeResult.Decoded(drawable)
                    } else {
                        GifDecodeResult.Fallback
                    }
                }
                else -> GifDecodeResult.Fallback
            }
        }.getOrElse { GifDecodeResult.Fallback }
    }

    private fun createImageDecoderSource(
        activity: Activity,
        symbols: ResolvedSymbols,
        b: HardwareRenderBindings,
        item: Any?,
        uri: Uri?
    ): ImageDecoder.Source? {
        if (item != null) {
            val cached = ViewerPrefetchHook.peekCachedFileForItem(symbols, item)
            if (cached != null && cached.isFile && cached.canRead() && cached.length() > 0L) {
                return ImageDecoder.createSource(cached)
            }

            if (b.localPathClass?.isInstance(item) == true && b.localPathMethod != null) {
                val localPath = runCatching { b.localPathMethod.invoke(item) as? String }.getOrNull()
                val localFile = localPath?.let(::File)
                if (localFile != null && localFile.isFile && localFile.canRead() && localFile.length() > 0L) {
                    return ImageDecoder.createSource(localFile)
                }
            }

            val stream = symbols.sibling.openInputStream?.invoke(item, activity) as? InputStream ?: return null
            val bytes = stream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            return createSourceFromBytes(bytes)
        }

        if (uri != null) {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "file") {
                val file = uri.path?.let(::File)
                if (file != null && file.isFile && file.canRead() && file.length() > 0L) {
                    return ImageDecoder.createSource(file)
                }
            }
            if (scheme == "http" || scheme == "https") {
                val conn = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = HTTP_TIMEOUT_MS
                    readTimeout = HTTP_TIMEOUT_MS
                    instanceFollowRedirects = true
                }
                return try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        if (bytes.isEmpty()) null else createSourceFromBytes(bytes)
                    }
                } finally {
                    conn.disconnect()
                }
            }
            return ImageDecoder.createSource(activity.contentResolver, uri)
        }

        return null
    }

    private fun createSourceFromBytes(bytes: ByteArray): ImageDecoder.Source {
        val direct = ByteBuffer.allocateDirect(bytes.size)
        direct.put(bytes)
        direct.flip()
        return ImageDecoder.createSource(direct)
    }

    private fun deliverDecodedGifOnUi(
        activity: Activity,
        display: View,
        holder: Any,
        token: Int,
        drawable: Drawable,
        symbols: ResolvedSymbols,
        b: HardwareRenderBindings,
        installer: FxHookInstaller
    ): Boolean {
        val runnable = Runnable {
            if (activity.isDestroyed || activity.isFinishing) {
                (drawable as? AnimatedImageDrawable)?.stop()
                return@Runnable
            }
            val currentToken = runCatching { b.loadTokenField?.getInt(activity) ?: token }.getOrDefault(token)
            if (token != currentToken) {
                (drawable as? AnimatedImageDrawable)?.stop()
                return@Runnable
            }
            val disposed = runCatching { b.displayDisposedField?.getBoolean(display) ?: false }.getOrDefault(false)
            if (disposed) {
                (drawable as? AnimatedImageDrawable)?.stop()
                return@Runnable
            }

            when (drawable) {
                is AnimatedImageDrawable -> {
                    attachAnimatedGifOnUi(activity, display, holder, drawable, b, installer)
                }
                is BitmapDrawable -> {
                    val bmp = drawable.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        deliverStaticGifBitmapOnUi(activity, display, holder, bmp, symbols, b)
                    }
                }
            }
        }

        val handler = b.uiHandlerField?.let { runCatching { it.get(activity) as? Handler }.getOrNull() }
        return if (handler != null) {
            handler.post(runnable)
        } else {
            display.post(runnable)
        }
    }

    private fun attachAnimatedGifOnUi(
        activity: Activity,
        display: View,
        holder: Any,
        drawable: AnimatedImageDrawable,
        b: HardwareRenderBindings,
        installer: FxHookInstaller
    ) {
        clearTileSession(display)
        clearAnimatedGifState(display)

        runCatching {
            val prevPreview = b.displayBitmapField?.get(display) as? Bitmap
            b.displayBitmapField?.set(display, null)
            b.displayMovieField?.set(display, null)
            b.bitmapField?.set(display, null)
            if (prevPreview != null && !prevPreview.isRecycled) {
                prevPreview.recycle()
            }
        }

        runCatching { b.displayFullLoadedField?.setBoolean(display, true) }
        runCatching { b.isGifField?.setBoolean(activity, true) }
        runCatching { b.currentHolderField?.set(activity, holder) }
        settleEnterTransitionIfPending(activity, display, b)

        runCatching { b.setPrescale?.invoke(display, 1.0f) }
        runCatching { b.softwareLayerFlagField?.setBoolean(display, false) }
        if (display.layerType != View.LAYER_TYPE_NONE) {
            display.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        val sentinel = getOrCreateTinyMovieSentinel()
        if (sentinel != null) {
            runCatching { b.movieField?.set(display, sentinel) }
            runCatching { b.displayMovieField?.set(display, sentinel) }
        }
        runCatching { b.moviePlayingField?.setBoolean(display, false) }
        val movieThread = b.movieThreadField?.let { runCatching { it.get(display) as? Runnable }.getOrNull() }
        if (movieThread != null) {
            display.removeCallbacks(movieThread)
        }

        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        runCatching { b.updateBoundsMethod?.invoke(display, width, height, false) }
        runCatching { b.needsZoomFitField?.setBoolean(display, true) }

        drawable.setBounds(0, 0, width, height)
        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        drawable.isFilterBitmap = true

        lateinit var state: AnimatedGifState
        val callback = AnimatedViewCallback(display) {
            state.active && getAnimatedGifState(display) === state
        }
        state = AnimatedGifState(WeakReference(display), drawable, callback)
        synchronized(stateLock) {
            animatedGifStates[display] = state
        }
        drawable.callback = callback
        drawable.start()

        display.requestLayout()
        display.invalidate()
        installer.logDebug("Started VSYNC AnimatedImageDrawable playback ${width}x${height}")
    }

    private fun deliverStaticGifBitmapOnUi(
        activity: Activity,
        display: View,
        holder: Any,
        bitmap: Bitmap,
        symbols: ResolvedSymbols,
        b: HardwareRenderBindings
    ) {
        clearAnimatedGifState(display)
        runCatching { b.isGifField?.setBoolean(activity, false) }
        runCatching { b.setPrescale?.invoke(display, 1.0f) }

        if (symbols.profile == FxProfile.FX_9012 && b.deliverBitmapMethod != null) {
            settleEnterTransitionIfPending(activity, display, b)
            val ok = runCatching {
                b.deliverBitmapMethod.invoke(display, bitmap, false)
            }.isSuccess
            if (ok) return
        }

        if (symbols.profile == FxProfile.FX_9108 &&
            b.applyBitmapResultMethod != null &&
            b.imageResultCtor != null
        ) {
            val delivered = runCatching {
                val resultObj = b.imageResultCtor.newInstance(bitmap, 1.0f, false)
                b.applyBitmapResultMethod.invoke(activity, display, holder, resultObj, false)
            }.isSuccess
            if (delivered) return
        }

        // Fallback direct delivery if helper method could not be invoked
        settleEnterTransitionIfPending(activity, display, b)
        runCatching {
            val prev = b.displayBitmapField?.get(display) as? Bitmap
            b.displayBitmapField?.set(display, bitmap)
            b.displayMovieField?.set(display, null)
            b.displayFullLoadedField?.setBoolean(display, true)
            b.currentHolderField?.set(activity, holder)
            if (prev != null && prev !== bitmap && !prev.isRecycled) {
                prev.recycle()
            }
            b.setImageBitmap?.invoke(display, bitmap)
            display.requestLayout()
        }
    }

    private fun settleEnterTransitionIfPending(
        activity: Activity,
        display: View,
        b: HardwareRenderBindings
    ) {
        val transition = b.enterTransitionField?.let { runCatching { it.get(activity) }.getOrNull() } ?: return
        val running = b.transitionRunningField?.let { runCatching { it.getBoolean(transition) }.getOrDefault(false) }
            ?: false
        if (running) return

        runCatching {
            (b.transitionBgViewField?.get(transition) as? View)?.alpha = 1.0f
            (b.transitionTopBarField?.get(transition) as? View)?.alpha = 1.0f
            (b.transitionBottomBarField?.get(transition) as? View)?.alpha = 1.0f
            b.transitionDoneField?.setBoolean(transition, true)
            b.enterTransitionField.set(activity, null)
            b.cropArField?.setFloat(display, -1.0f)
            b.setTouchEnabled?.invoke(display, true)
        }
    }

    private fun isGifUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if ("image/gif".equals(mime, ignoreCase = true)) {
            return true
        }
        val tail = uri.lastPathSegment ?: uri.path ?: return false
        return tail.endsWith(".gif", ignoreCase = true)
    }

    private fun getOrCreateTinyMovieSentinel(): Movie? {
        tinySentinelMovie?.let { return it }
        return synchronized(stateLock) {
            tinySentinelMovie ?: runCatching {
                Movie.decodeByteArray(TINY_GIF_1X1, 0, TINY_GIF_1X1.size)
            }.getOrNull()?.also { tinySentinelMovie = it }
        }
    }

    private fun applyCropClipIfNeeded(
        view: View,
        canvas: Canvas,
        b: HardwareRenderBindings
    ): Boolean {
        val cropAr = b.cropArField?.let { runCatching { it.getFloat(view) }.getOrNull() } ?: return false
        if (cropAr <= 0f) return false

        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth <= 0 || viewHeight <= 0) return false

        val sizeObj = b.displaySizeField?.let { runCatching { it.get(view) }.getOrNull() } ?: return false
        val displayW = b.displaySizeWidthField?.let { runCatching { it.getInt(sizeObj) }.getOrNull() } ?: return false
        val displayH = b.displaySizeHeightField?.let { runCatching { it.getInt(sizeObj) }.getOrNull() } ?: return false
        if (displayW <= 0 || displayH <= 0) return false

        val viewAr = viewWidth.toFloat() / viewHeight.toFloat()
        val imgAr = displayW.toFloat() / displayH.toFloat()
        val drawnWidth: Int
        val drawnHeight: Int
        if (viewAr > imgAr) {
            drawnWidth = (viewHeight.toFloat() * imgAr).toInt()
            drawnHeight = viewHeight
        } else {
            drawnHeight = (viewWidth.toFloat() / imgAr).toInt()
            drawnWidth = viewWidth
        }

        val rect = (b.cropRectField?.let { runCatching { it.get(view) as? Rect }.getOrNull() }) ?: Rect()
        if (imgAr > cropAr) {
            var left = ((drawnWidth - cropAr * drawnHeight) / 2f).toInt()
            if (drawnWidth < viewWidth) {
                left += (viewWidth - drawnWidth) / 2
            }
            rect.set(left, 0, viewWidth - left, viewHeight)
        } else {
            var top = ((drawnHeight - drawnWidth / cropAr) / 2f).toInt()
            if (drawnHeight < viewHeight) {
                top += (viewHeight - drawnHeight) / 2
            }
            rect.set(0, top, viewWidth, viewHeight - top)
        }
        canvas.save()
        canvas.clipRect(rect)
        return true
    }

    private fun getOrCreateTileSession(
        view: View,
        bitmap: Bitmap,
        maxCanvasDim: Int
    ): TileSession {
        val effectiveMaxDim = if (maxCanvasDim in 2048..16384) maxCanvasDim else 8192
        val existing = synchronized(stateLock) { tileSessions[view] }
        if (existing != null &&
            !existing.cleared &&
            existing.sourceBitmap === bitmap &&
            existing.maxDim == effectiveMaxDim
        ) {
            return existing
        }
        existing?.recycleAll()
        val specs = buildTileSpecs(bitmap.width, bitmap.height, DEFAULT_TILE_SIZE, effectiveMaxDim)
        val created = TileSession(bitmap, effectiveMaxDim, specs)
        synchronized(stateLock) {
            tileSessions[view] = created
        }
        return created
    }

    private fun drawTiledBitmap(
        view: View,
        canvas: Canvas,
        session: TileSession,
        visibleTiles: List<TileSpec>,
        matrix: Matrix,
        paint: Paint,
        b: HardwareRenderBindings
    ): Boolean {
        val tilesToDraw = ArrayList<Pair<TileSpec, Bitmap>>(visibleTiles.size)
        val evictedToRecycle = ArrayList<Bitmap>()
        val transientToRecycle = ArrayList<Bitmap>()

        synchronized(session.lock) {
            if (session.cleared || session.sourceBitmap.isRecycled) return false
            val sliceable = session.ensureSliceableBitmap() ?: return false
            if (sliceable.isRecycled) return false

            val visibleKeys = HashSet<Int>(visibleTiles.size * 2)
            for (spec in visibleTiles) {
                visibleKeys.add(tileKey(spec))
            }

            // Touch cached visible tiles first so none are evicted while building missing tiles.
            val missing = ArrayList<TileSpec>()
            for (spec in visibleTiles) {
                val key = tileKey(spec)
                val cached = session.lru.get(key)
                if (cached != null && !cached.isRecycled) {
                    tilesToDraw.add(spec to cached)
                } else {
                    if (cached != null) {
                        session.lru.remove(key)
                    }
                    missing.add(spec)
                }
            }

            var sliceFailed = false
            for (spec in missing) {
                val w = spec.rawWidth
                val h = spec.rawHeight
                if (w <= 0 || h <= 0) continue
                val tileBmp = runCatching {
                    Bitmap.createBitmap(sliceable, spec.rawLeft, spec.rawTop, w, h)
                }.getOrNull()
                if (tileBmp == null) {
                    sliceFailed = true
                    break
                }
                tilesToDraw.add(spec to tileBmp)

                val key = tileKey(spec)
                if (visibleTiles.size <= MAX_CACHED_TILES_PER_VIEW) {
                    session.lru.put(key, tileBmp, protectedKeys = visibleKeys) { _, evicted ->
                        if (evicted !== session.sourceBitmap &&
                            evicted !== sliceable &&
                            !evicted.isRecycled
                        ) {
                            evictedToRecycle.add(evicted)
                        }
                    }
                } else if (session.lru.size < MAX_CACHED_TILES_PER_VIEW) {
                    session.lru.put(key, tileBmp, protectedKeys = visibleKeys)
                } else if (tileBmp !== session.sourceBitmap && tileBmp !== sliceable) {
                    transientToRecycle.add(tileBmp)
                }
            }
            if (sliceFailed) {
                for (bmp in transientToRecycle) {
                    if (!bmp.isRecycled) runCatching { bmp.recycle() }
                }
                return false
            }

            val cropClipped = applyCropClipIfNeeded(view, canvas, b)
            try {
                canvas.save()
                canvas.concat(matrix)
                try {
                    val srcRect = Rect()
                    val dstRect = RectF()
                    for ((spec, tileBmp) in tilesToDraw) {
                        if (tileBmp.isRecycled) continue
                        srcRect.set(spec.srcSubLeft, spec.srcSubTop, spec.srcSubRight, spec.srcSubBottom)
                        dstRect.set(
                            spec.dstLeft.toFloat(),
                            spec.dstTop.toFloat(),
                            spec.dstRight.toFloat(),
                            spec.dstBottom.toFloat()
                        )
                        canvas.drawBitmap(tileBmp, srcRect, dstRect, paint)
                    }
                } finally {
                    canvas.restore()
                }
            } finally {
                if (cropClipped) {
                    canvas.restore()
                }
            }
        }

        for (bmp in evictedToRecycle) {
            if (!bmp.isRecycled) {
                runCatching { bmp.recycle() }
            }
        }
        if (transientToRecycle.isNotEmpty()) {
            val cleanup = Runnable {
                for (bmp in transientToRecycle) {
                    if (!bmp.isRecycled) {
                        runCatching { bmp.recycle() }
                    }
                }
            }
            if (!view.post(cleanup)) {
                cleanup.run()
            }
        }
        return true
    }

    private fun tileKey(spec: TileSpec): Int = (spec.row shl 16) or (spec.col and 0xFFFF)

    private fun getAnimatedGifState(view: View): AnimatedGifState? {
        return synchronized(stateLock) {
            animatedGifStates[view]?.takeIf { it.active }
        }
    }

    private fun clearTileSession(view: View) {
        val removed = synchronized(stateLock) { tileSessions.remove(view) } ?: return
        removed.cleared = true
        val mainLooper = Looper.getMainLooper()
        if (mainLooper == null || Looper.myLooper() == mainLooper) {
            removed.recycleAll()
        } else if (!view.post { removed.recycleAll() }) {
            removed.recycleAll()
        }
    }

    private fun clearAllTileSessions() {
        val entries = synchronized(stateLock) {
            val copy = tileSessions.entries.map { it.key to it.value }
            tileSessions.clear()
            copy
        }
        val mainLooper = Looper.getMainLooper()
        for ((view, session) in entries) {
            session.cleared = true
            if (mainLooper == null || Looper.myLooper() == mainLooper) {
                session.recycleAll()
            } else if (!view.post { session.recycleAll() }) {
                session.recycleAll()
            }
        }
    }

    private fun clearAnimatedGifState(view: View) {
        val removed = synchronized(stateLock) { animatedGifStates.remove(view) }
        removed?.stopAndDetach()
    }

    private fun clearAllAnimatedGifStates() {
        val states = synchronized(stateLock) {
            val copy = animatedGifStates.values.toList()
            animatedGifStates.clear()
            copy
        }
        for (state in states) {
            state.stopAndDetach()
        }
    }

    private fun resolveBindings(symbols: ResolvedSymbols): HardwareRenderBindings {
        val cl = symbols.classLoader
        val touchClass = loadClass(cl, "nextapp.maui.ui.imageview.TouchImageDisplay")
        val imageDisplayClass = symbols.sibling.imageDisplayClass
            ?: loadClass(cl, "nextapp.fx.ui.viewer.image.ImageDisplay")
        val viewerClass = symbols.sibling.imageViewerActivityClass
            ?: loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity")

        val setImageBitmap = declaredMethod(touchClass, "setImageBitmap", Bitmap::class.java)
        val setImageMovie = declaredMethod(touchClass, "setImageMovie", Movie::class.java)
        val setImageNull = declaredMethod(touchClass, "setImageNull")
        val setPrescale = declaredMethod(touchClass, "setPrescale", Float::class.javaPrimitiveType)
        val setTouchEnabled = declaredMethod(touchClass, "setTouchEnabled", Boolean::class.javaPrimitiveType)
        val zoomFit = declaredMethod(touchClass, "zoomFit")
        val onDraw = declaredMethod(touchClass, "onDraw", Canvas::class.java)
        val onDetachedFromWindow = declaredMethod(touchClass, "onDetachedFromWindow")

        return when (symbols.profile) {
            FxProfile.FX_9012 -> {
                val dimClass = loadClass(cl, "v8.f")
                val holderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.ImageViewerActivity\$j")
                val transitionClass = loadClass(cl, "nextapp.fx.ui.viewer.image.f0")
                val supportClass = loadClass(cl, "nextapp.fx.ui.activitysupport.j")
                val gifProbeClass = loadClass(cl, "a9.e")
                val localInterface = loadClass(cl, "ue.a0")
                val localPathProvider = loadClass(cl, "ue.b0")

                HardwareRenderBindings(
                    touchClass = touchClass,
                    setImageBitmap = setImageBitmap,
                    setImageMovie = setImageMovie,
                    setImageNull = setImageNull,
                    setPrescale = setPrescale,
                    setTouchEnabled = setTouchEnabled,
                    zoomFit = zoomFit,
                    onDraw = onDraw,
                    onDetachedFromWindow = onDetachedFromWindow,
                    updateBoundsMethod = declaredMethod(
                        touchClass,
                        "w",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    ),
                    enforceLayerOnBitmapMethod = declaredMethod(touchClass, "g", Bitmap::class.java),
                    bitmapField = declaredField(touchClass, "Q5"),
                    movieField = declaredField(touchClass, "R5"),
                    moviePlayingField = declaredField(touchClass, "S5"),
                    movieThreadField = declaredField(touchClass, "f"),
                    matrixField = declaredField(touchClass, "V5"),
                    paintField = declaredField(touchClass, "H5"),
                    cropArField = declaredField(touchClass, "f5"),
                    cropRectField = declaredField(touchClass, "c5"),
                    softwareLayerFlagField = declaredField(touchClass, "h5"),
                    displaySizeField = declaredField(touchClass, "k5"),
                    displaySizeWidthField = declaredField(dimClass, "f"),
                    displaySizeHeightField = declaredField(dimClass, "i"),
                    needsZoomFitField = declaredField(touchClass, "l5"),
                    imageDisplayClass = imageDisplayClass,
                    deliverBitmapMethod = declaredMethod(
                        imageDisplayClass,
                        "U",
                        Bitmap::class.java,
                        Boolean::class.javaPrimitiveType
                    ),
                    deliverMovieMethod = declaredMethod(imageDisplayClass, "V", Movie::class.java),
                    clearDisplayMethod = declaredMethod(imageDisplayClass, "W"),
                    displayBitmapField = declaredField(imageDisplayClass, "a6"),
                    displayMovieField = declaredField(imageDisplayClass, "b6"),
                    displayDisposedField = declaredField(imageDisplayClass, "f6"),
                    displayFullLoadedField = declaredField(imageDisplayClass, "g6"),
                    displayPreviewLoadedField = declaredField(imageDisplayClass, "h6"),
                    viewerClass = viewerClass,
                    loadMediaMethod = symbols.viewer.viewerLoadMedia ?: declaredMethod(
                        viewerClass,
                        "j1",
                        imageDisplayClass,
                        holderClass,
                        Int::class.javaPrimitiveType
                    ),
                    loadGifMethod = symbols.viewer.viewerLoadGif ?: declaredMethod(
                        viewerClass,
                        "l1",
                        imageDisplayClass,
                        holderClass,
                        Int::class.javaPrimitiveType
                    ),
                    applyBitmapResultMethod = null,
                    imageResultCtor = null,
                    loadTokenField = declaredField(viewerClass, "x5"),
                    isGifField = declaredField(viewerClass, "P5"),
                    currentHolderField = symbols.sibling.viewerCurrentHolderField
                        ?: declaredField(viewerClass, "v5"),
                    enterTransitionField = declaredField(viewerClass, "y5"),
                    transitionBgViewField = declaredField(transitionClass, "c"),
                    transitionTopBarField = declaredField(transitionClass, "d"),
                    transitionBottomBarField = declaredField(transitionClass, "e"),
                    transitionRunningField = declaredField(transitionClass, "h"),
                    transitionDoneField = declaredField(transitionClass, "g"),
                    uiHandlerField = declaredField(supportClass, "g5"),
                    holderFileItemField = symbols.sibling.itemHolderFileItemField
                        ?: declaredField(holderClass, "a"),
                    holderUriField = symbols.sibling.itemHolderUriField
                        ?: declaredField(holderClass, "b"),
                    gifAnimatedCheckMethod = declaredMethod(gifProbeClass, "a", InputStream::class.java),
                    localPathClass = localInterface,
                    localPathMethod = declaredMethod(localPathProvider, "h")
                )
            }

            FxProfile.FX_9108 -> {
                val dimClass = loadClass(cl, "pa.c")
                val holderClass = loadClass(cl, "nextapp.fx.ui.viewer.image.o")
                val imageResultClass = loadClass(cl, "ua.a")
                val transitionClass = loadClass(cl, "nextapp.fx.ui.viewer.image.u")
                val supportClass = loadClass(cl, "af.h")
                val gifProbeClass = loadClass(cl, "ua.b")
                val localInterface = loadClass(cl, "kh.c0")
                val localPathProvider = loadClass(cl, "kh.d0")

                HardwareRenderBindings(
                    touchClass = touchClass,
                    setImageBitmap = setImageBitmap,
                    setImageMovie = setImageMovie,
                    setImageNull = setImageNull,
                    setPrescale = setPrescale,
                    setTouchEnabled = setTouchEnabled,
                    zoomFit = zoomFit,
                    onDraw = onDraw,
                    onDetachedFromWindow = onDetachedFromWindow,
                    updateBoundsMethod = declaredMethod(
                        touchClass,
                        "h",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    ),
                    enforceLayerOnBitmapMethod = null,
                    bitmapField = declaredField(touchClass, "N2"),
                    movieField = declaredField(touchClass, "O2"),
                    moviePlayingField = declaredField(touchClass, "P2"),
                    movieThreadField = declaredField(touchClass, "f"),
                    matrixField = declaredField(touchClass, "S2"),
                    paintField = declaredField(touchClass, "E2"),
                    cropArField = declaredField(touchClass, "c2"),
                    cropRectField = declaredField(touchClass, "Z1"),
                    softwareLayerFlagField = declaredField(touchClass, "e2"),
                    displaySizeField = declaredField(touchClass, "h2"),
                    displaySizeWidthField = declaredField(dimClass, "f"),
                    displaySizeHeightField = declaredField(dimClass, "i"),
                    needsZoomFitField = declaredField(touchClass, "i2"),
                    imageDisplayClass = imageDisplayClass,
                    deliverBitmapMethod = null,
                    deliverMovieMethod = null,
                    clearDisplayMethod = declaredMethod(imageDisplayClass, "l"),
                    displayBitmapField = declaredField(imageDisplayClass, "W2"),
                    displayMovieField = declaredField(imageDisplayClass, "X2"),
                    displayDisposedField = declaredField(imageDisplayClass, "b3"),
                    displayFullLoadedField = declaredField(imageDisplayClass, "c3"),
                    displayPreviewLoadedField = declaredField(imageDisplayClass, "d3"),
                    viewerClass = viewerClass,
                    loadMediaMethod = symbols.viewer.viewerLoadMediaAndBitmap ?: declaredMethod(
                        viewerClass,
                        "C",
                        imageDisplayClass,
                        holderClass,
                        Int::class.javaPrimitiveType
                    ),
                    loadGifMethod = null,
                    applyBitmapResultMethod = declaredMethod(
                        viewerClass,
                        "F",
                        imageDisplayClass,
                        holderClass,
                        imageResultClass,
                        Boolean::class.javaPrimitiveType
                    ),
                    imageResultCtor = declaredConstructor(
                        imageResultClass,
                        Bitmap::class.java,
                        Float::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    ),
                    loadTokenField = declaredField(viewerClass, "t2"),
                    isGifField = declaredField(viewerClass, "L2"),
                    currentHolderField = symbols.sibling.viewerCurrentHolderField
                        ?: declaredField(viewerClass, "r2"),
                    enterTransitionField = declaredField(viewerClass, "u2"),
                    transitionBgViewField = declaredField(transitionClass, "c"),
                    transitionTopBarField = declaredField(transitionClass, "d"),
                    transitionBottomBarField = declaredField(transitionClass, "e"),
                    transitionRunningField = declaredField(transitionClass, "h"),
                    transitionDoneField = declaredField(transitionClass, "g"),
                    uiHandlerField = declaredField(supportClass, "c2"),
                    holderFileItemField = symbols.sibling.itemHolderFileItemField
                        ?: declaredField(holderClass, "a"),
                    holderUriField = symbols.sibling.itemHolderUriField
                        ?: declaredField(holderClass, "b"),
                    gifAnimatedCheckMethod = declaredMethod(gifProbeClass, "f", InputStream::class.java),
                    localPathClass = localInterface,
                    localPathMethod = declaredMethod(localPathProvider, "d")
                )
            }

            FxProfile.STRUCTURAL_FALLBACK -> {
                val bitmapFields = touchClass?.declaredFields?.filter { it.type == Bitmap::class.java }.orEmpty()
                val matrixFields = touchClass?.declaredFields?.filter { it.type == Matrix::class.java }.orEmpty()
                val rectFields = touchClass?.declaredFields?.filter { it.type == Rect::class.java }.orEmpty()
                val bitmapField = bitmapFields.singleOrNull()?.apply { isAccessible = true }
                val matrixField = matrixFields.singleOrNull()?.apply { isAccessible = true }
                val rectField = rectFields.singleOrNull()?.apply { isAccessible = true }

                HardwareRenderBindings(
                    touchClass = touchClass,
                    setImageBitmap = setImageBitmap,
                    setImageMovie = setImageMovie,
                    setImageNull = setImageNull,
                    setPrescale = setPrescale,
                    setTouchEnabled = setTouchEnabled,
                    zoomFit = zoomFit,
                    onDraw = onDraw,
                    onDetachedFromWindow = onDetachedFromWindow,
                    updateBoundsMethod = null,
                    enforceLayerOnBitmapMethod = null,
                    bitmapField = bitmapField,
                    movieField = null,
                    moviePlayingField = null,
                    movieThreadField = null,
                    matrixField = matrixField,
                    paintField = null,
                    cropArField = null,
                    cropRectField = rectField,
                    softwareLayerFlagField = null,
                    displaySizeField = null,
                    displaySizeWidthField = null,
                    displaySizeHeightField = null,
                    needsZoomFitField = null,
                    imageDisplayClass = imageDisplayClass,
                    deliverBitmapMethod = null,
                    deliverMovieMethod = null,
                    clearDisplayMethod = null,
                    displayBitmapField = null,
                    displayMovieField = null,
                    displayDisposedField = null,
                    displayFullLoadedField = null,
                    displayPreviewLoadedField = null,
                    viewerClass = viewerClass,
                    loadMediaMethod = null,
                    loadGifMethod = null,
                    applyBitmapResultMethod = null,
                    imageResultCtor = null,
                    loadTokenField = null,
                    isGifField = null,
                    currentHolderField = null,
                    enterTransitionField = null,
                    transitionBgViewField = null,
                    transitionTopBarField = null,
                    transitionBottomBarField = null,
                    transitionRunningField = null,
                    transitionDoneField = null,
                    uiHandlerField = null,
                    holderFileItemField = null,
                    holderUriField = null,
                    gifAnimatedCheckMethod = null,
                    localPathClass = null,
                    localPathMethod = null
                )
            }
        }
    }

    private fun loadClass(cl: ClassLoader, name: String): Class<*>? {
        return runCatching { Class.forName(name, false, cl) }.getOrNull()
    }

    private fun declaredMethod(clazz: Class<*>?, name: String, vararg params: Class<*>?): Method? {
        if (clazz == null || params.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        val nonNullParams = params as Array<Class<*>>
        return runCatching {
            clazz.getDeclaredMethod(name, *nonNullParams).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun declaredConstructor(clazz: Class<*>?, vararg params: Class<*>?): Constructor<*>? {
        if (clazz == null || params.any { it == null }) return null
        @Suppress("UNCHECKED_CAST")
        val nonNullParams = params as Array<Class<*>>
        return runCatching {
            clazz.getDeclaredConstructor(*nonNullParams).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun declaredField(clazz: Class<*>?, name: String): Field? {
        if (clazz == null) return null
        return runCatching {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }.getOrNull()
    }
}
