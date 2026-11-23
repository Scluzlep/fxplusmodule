package com.scluzlep.fxplusmodule.xposed

import android.app.Activity
import android.os.Handler
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset

/**
 * Opt-in experience limit hooks (`unlockTextPreviewLimits`) lifting line/character caps in
 * `TextViewerActivity`, `TextEdit`, and `ExecActivity` preview across `9012` and `9108`
 * while preserving encoding detection, binary-XML/ELF detection, cancellation, and OOM guards.
 */
object BonusLimitsHook {

    fun install(symbols: ResolvedSymbols, installer: FxHookInstaller) {
        val b = symbols.bonusLimits
        when (symbols.profile) {
            FxProfile.FX_9012 -> {
                installer.install("bonus:9012:TextViewer.w0", b.textViewerLoad) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    handleTextViewer9012(symbols, chain)
                }
                installer.install("bonus:9012:TextEdit.c", b.textEditReadStream) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    handleTextEdit9012(symbols, chain)
                }
                installer.install("bonus:9012:Exec.d2", b.execLoadPreview) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    handleExecPreview9012(symbols, chain)
                }
            }
            FxProfile.FX_9108 -> {
                installer.install("bonus:9108:pg.f0.run", b.textViewerWorkerRun) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    val runner = chain.thisObject ?: return@install chain.proceed()
                    val caseId = runCatching { b.textViewerWorkerCaseField?.getInt(runner) }.getOrNull()
                    if (caseId != 0) return@install chain.proceed()
                    handleTextViewer9108(symbols, runner)
                }
                installer.install("bonus:9108:mg.n.l", b.textEditReadStream) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    handleTextEdit9108(symbols, chain)
                }
                installer.install("bonus:9108:audio.w.run", b.execWorkerRun) { chain ->
                    if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                    val runner = chain.thisObject ?: return@install chain.proceed()
                    val caseId = runCatching { b.execWorkerCaseField?.getInt(runner) }.getOrNull()
                    if (caseId != 19) return@install chain.proceed()
                    handleExecPreview9108(symbols, runner)
                }
            }
            FxProfile.STRUCTURAL_FALLBACK -> Unit
        }
    }

    private fun handleTextViewer9012(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        val v = symbols.viewer
        val activity = chain.thisObject as? Activity ?: return chain.proceed()

        val openStreamMethod = v.viewerBaseClass?.getDeclaredMethod("W")?.apply { isAccessible = true }
            ?: return chain.proceed()
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return chain.proceed()
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(openStreamMethod, activity) as? InputStream
            if (input == null) {
                if (cannotReadId != 0) b.viewerSupportShowError?.invoke(activity, cannotReadId)
                return null
            }

            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return chain.proceed()
            val peekBytes = b.encodingStreamGetBuffer?.invoke(detector) as? ByteArray
            val probeType = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            val textReader: Reader = if (probeType == "x/x-android-binary-xml") {
                b.textViewerOpenBinaryXml?.invoke(null, detector) as? Reader
                    ?: InputStreamReader(detector, Charsets.UTF_8)
            } else {
                var charsetName = (b.encodingStreamGetCharset?.invoke(detector) as? String) ?: "UTF-8"
                if (!Charset.isSupported(charsetName)) {
                    b.textViewerUnsupportedField?.setBoolean(activity, true)
                    charsetName = "UTF-8"
                }
                b.textViewerCharsetField?.set(activity, charsetName)
                InputStreamReader(detector, charsetName)
            }

            reader = BufferedReader(textReader)
            while (true) {
                val line = reader.readLine() ?: break
                lines.add(line)
            }
            b.textViewerTruncatedField?.setBoolean(activity, false)
            b.textViewerLinesField?.set(activity, lines)
            runCatching { reader.close() }
            reader = null
            handler.post {
                runCatching { b.textViewerApply?.invoke(activity) }
            }
            return null
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                runCatching { reader?.close() }
                handler.post { runCatching { b.textViewerApply?.invoke(activity) } }
                return null
            }
            if (cause is OutOfMemoryError) {
                Log.w("nextapp.fx", "Out of memory attempting to display text file.", cause)
                if (oomId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, oomId) }
                runCatching { reader?.close() }
                return null
            }
            if (cause is IOException || symbols.androidData.userExceptionClass?.isInstance(cause) == true) {
                if (cannotReadId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, cannotReadId) }
                runCatching { reader?.close() }
                return null
            }
            runCatching { reader?.close() }
            return chain.proceed()
        }
    }

    private fun handleTextViewer9108(symbols: ResolvedSymbols, runner: Any): Any? {
        val b = symbols.bonusLimits
        val v = symbols.viewer
        val activity = b.textViewerWorkerActivityField?.get(runner) as? Activity ?: return null
        val openStreamMethod = v.viewerBaseClass?.getDeclaredMethod("w")?.apply { isAccessible = true }
            ?: return null
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return null
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(openStreamMethod, activity) as? InputStream
            if (input == null) {
                if (cannotReadId != 0) b.viewerSupportShowError?.invoke(activity, cannotReadId)
                return null
            }
            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return null
            b.encodingStreamDetect?.invoke(detector)
            val peekBytes = b.encodingStreamBufferField?.get(detector) as? ByteArray
            val probeType = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            val textReader: Reader = if (probeType == "x/x-android-binary-xml") {
                b.textViewerOpenBinaryXml?.invoke(null, detector) as? Reader
                    ?: InputStreamReader(detector, Charsets.UTF_8)
            } else {
                b.encodingStreamDetect?.invoke(detector)
                var charsetName = (b.encodingStreamCharsetField?.get(detector) as? String) ?: "UTF-8"
                if (!Charset.isSupported(charsetName)) {
                    b.textViewerUnsupportedField?.setBoolean(activity, true)
                    charsetName = "UTF-8"
                }
                b.textViewerCharsetField?.set(activity, charsetName)
                InputStreamReader(detector, charsetName)
            }

            reader = BufferedReader(textReader)
            while (true) {
                val line = reader.readLine() ?: break
                lines.add(line)
            }
            b.textViewerTruncatedField?.setBoolean(activity, false)
            b.textViewerLinesField?.set(activity, lines)
            runCatching { reader.close() }
            reader = null
            val uiRunnable = b.textViewerWorkerCtor?.newInstance(activity, 1) as? Runnable
            if (uiRunnable != null) handler.post(uiRunnable)
            return null
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                runCatching { reader?.close() }
                val uiRunnable = runCatching { b.textViewerWorkerCtor?.newInstance(activity, 1) as? Runnable }.getOrNull()
                if (uiRunnable != null) handler.post(uiRunnable)
                return null
            }
            if (cause is OutOfMemoryError) {
                Log.w("nextapp.fx", "Out of memory attempting to display text file.", cause)
                if (oomId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, oomId) }
                runCatching { reader?.close() }
                return null
            }
            if (cannotReadId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, cannotReadId) }
            runCatching { reader?.close() }
            return null
        }
    }

    private fun handleTextEdit9012(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        var stream = chain.getArg(0) as? InputStream
        var charset = chain.getArg(1) as? String
        val sb = StringBuilder()
        var finalCharset: String

        if (stream == null) {
            finalCharset = "UTF-8"
        } else {
            if (charset == null) {
                val detector = b.encodingStreamCtor?.newInstance(stream) as? InputStream
                    ?: return chain.proceed()
                try {
                    charset = b.encodingStreamGetCharset?.invoke(detector) as? String
                    stream = detector
                } catch (t: Throwable) {
                    runCatching { detector.close() }
                    throw (t as? InvocationTargetException)?.targetException ?: t
                }
            }
            val resolved = if (charset != null && Charset.isSupported(charset)) charset else "UTF-8"
            finalCharset = resolved
            BufferedReader(InputStreamReader(stream, resolved)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append('\n')
                }
            }
        }
        return b.textEditResultCtor?.newInstance(sb, finalCharset) ?: chain.proceed()
    }

    private fun handleTextEdit9108(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        var stream = chain.getArg(0) as? InputStream
        var charset = chain.getArg(1) as? String
        val sb = StringBuilder()
        var finalCharset: String

        if (stream == null) {
            finalCharset = "UTF-8"
        } else {
            if (charset == null) {
                val detector = b.encodingStreamCtor?.newInstance(stream) as? InputStream
                    ?: return chain.proceed()
                try {
                    b.encodingStreamDetect?.invoke(detector)
                    charset = b.encodingStreamCharsetField?.get(detector) as? String
                    stream = detector
                } catch (t: Throwable) {
                    runCatching { detector.close() }
                    throw (t as? InvocationTargetException)?.targetException ?: t
                }
            }
            val resolved = if (charset != null && Charset.isSupported(charset)) charset else "UTF-8"
            finalCharset = resolved
            BufferedReader(InputStreamReader(stream, resolved)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append('\n')
                }
            }
        }
        return b.textEditResultCtor?.newInstance(sb, 7, finalCharset) ?: chain.proceed()
    }

    private fun handleExecPreview9012(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        val s = symbols.sibling
        val activity = chain.thisObject as? Activity ?: return chain.proceed()
        val item = chain.getArg(0) ?: return chain.proceed()
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return chain.proceed()
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(s.openInputStream!!, item, activity) as? InputStream
                ?: return chain.proceed()
            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return chain.proceed()
            val peekBytes = b.encodingStreamGetBuffer?.invoke(detector) as? ByteArray
            val mime = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            b.execCharsetField?.set(activity, mime)
            if (mime == "x/x-elf") {
                b.execUnsupportedField?.setBoolean(activity, true)
                runCatching { detector.close() }
            } else {
                val rawCs = b.encodingStreamGetCharset?.invoke(detector) as? String
                val cs = if (rawCs != null && Charset.isSupported(rawCs)) rawCs else "UTF-8"
                reader = BufferedReader(InputStreamReader(detector, cs))
                while (true) {
                    val line = reader.readLine() ?: break
                    lines.add(line)
                }
                b.execLinesField?.set(activity, lines)
                runCatching { reader.close() }
                reader = null
            }
            handler.post { runCatching { b.execApplyPreview?.invoke(activity) } }
            return null
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            if (cause is OutOfMemoryError) {
                Log.w("nextapp.fx", "Out of memory attempting to display text file.", cause)
                if (oomId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, oomId) }
            } else if (!FxHookSupport.isFxCancelException(symbols, cause)) {
                if (cannotReadId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, cannotReadId) }
            }
            runCatching { reader?.close() }
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                handler.post { runCatching { b.execApplyPreview?.invoke(activity) } }
            }
            return null
        }
    }

    private fun handleExecPreview9108(symbols: ResolvedSymbols, runner: Any): Any? {
        val b = symbols.bonusLimits
        val s = symbols.sibling
        val activity = b.execWorkerArg1Field?.get(runner) as? Activity ?: return null
        val item = b.execWorkerArg2Field?.get(runner) ?: return null
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return null
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(s.openInputStream!!, item, activity) as? InputStream
                ?: return null
            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return null
            b.encodingStreamDetect?.invoke(detector)
            val peekBytes = b.encodingStreamBufferField?.get(detector) as? ByteArray
            val mime = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            if (mime == "x/x-elf") {
                b.execUnsupportedField?.setBoolean(activity, true)
                runCatching { detector.close() }
            } else {
                b.encodingStreamDetect?.invoke(detector)
                val rawCs = b.encodingStreamCharsetField?.get(detector) as? String
                val cs = if (rawCs != null && Charset.isSupported(rawCs)) rawCs else "UTF-8"
                reader = BufferedReader(InputStreamReader(detector, cs))
                while (true) {
                    val line = reader.readLine() ?: break
                    lines.add(line)
                }
                b.execLinesField?.set(activity, lines)
                runCatching { reader.close() }
                reader = null
            }
            val uiRunnable = b.execUiRunnableCtor?.newInstance(activity, 6) as? Runnable
            if (uiRunnable != null) handler.post(uiRunnable)
            return null
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            if (cause is OutOfMemoryError) {
                Log.w("nextapp.fx", "Out of memory attempting to display text file.", cause)
                if (oomId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, oomId) }
            } else if (!FxHookSupport.isFxCancelException(symbols, cause)) {
                if (cannotReadId != 0) runCatching { b.viewerSupportShowError?.invoke(activity, cannotReadId) }
            }
            runCatching { reader?.close() }
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                val uiRunnable = runCatching { b.execUiRunnableCtor?.newInstance(activity, 6) as? Runnable }.getOrNull()
                if (uiRunnable != null) handler.post(uiRunnable)
            }
            return null
        }
    }

    private fun invokeViewerMethod(method: java.lang.reflect.Method, target: Any?, vararg args: Any?): Any? {
        return try {
            method.invoke(target, *args)
        } catch (ite: InvocationTargetException) {
            throw (ite.targetException ?: ite)
        }
    }
}
