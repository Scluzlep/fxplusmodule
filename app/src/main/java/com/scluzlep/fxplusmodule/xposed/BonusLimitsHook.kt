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

        if (b.textViewerLoad != null) {
            installer.install("bonus:textViewer:load", b.textViewerLoad) { chain ->
                if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                handleTextViewerDirect(symbols, chain)
            }
        } else if (b.textViewerWorkerRun != null) {
            installer.install("bonus:textViewer:workerRun", b.textViewerWorkerRun) { chain ->
                if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                val runner = chain.thisObject ?: return@install chain.proceed()
                val caseId = runCatching { b.textViewerWorkerCaseField?.getInt(runner) }.getOrNull()
                if (caseId != b.textViewerWorkerCaseId) return@install chain.proceed()
                handleTextViewerWorker(symbols, runner)
            }
        }

        val textEditParamCount = b.textEditResultCtor?.parameterTypes?.size
        if (textEditParamCount == 2 || textEditParamCount == 3) {
            installer.install("bonus:textEdit:readStream", b.textEditReadStream) { chain ->
                if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                handleTextEdit(symbols, chain)
            }
        }

        if (b.execLoadPreview != null) {
            installer.install("bonus:exec:loadPreview", b.execLoadPreview) { chain ->
                if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                handleExecPreviewDirect(symbols, chain)
            }
        } else if (b.execWorkerRun != null) {
            installer.install("bonus:exec:workerRun", b.execWorkerRun) { chain ->
                if (!FxConfigBridge.current.unlockTextPreviewLimits) return@install chain.proceed()
                val runner = chain.thisObject ?: return@install chain.proceed()
                val caseId = runCatching { b.execWorkerCaseField?.getInt(runner) }.getOrNull()
                if (caseId != b.execWorkerCaseId) return@install chain.proceed()
                handleExecPreviewWorker(symbols, runner)
            }
        }
    }

    private fun handleTextViewerDirect(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        val v = symbols.viewer
        val activity = chain.thisObject as? Activity ?: return chain.proceed()

        val openStreamMethod = v.viewerOpenStreamMethod ?: return chain.proceed()
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
            val peekBytes = readEncodingBuffer(b, detector)
            val probeType = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            val textReader: Reader = if (probeType == "x/x-android-binary-xml") {
                b.textViewerOpenBinaryXml?.invoke(null, detector) as? Reader
                    ?: InputStreamReader(detector, Charsets.UTF_8)
            } else {
                var charsetName = readEncodingCharset(b, detector) ?: "UTF-8"
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

    private fun handleTextViewerWorker(symbols: ResolvedSymbols, runner: Any): Any? {
        val b = symbols.bonusLimits
        val v = symbols.viewer
        val activity = b.textViewerWorkerActivityField?.get(runner) as? Activity ?: return null
        val openStreamMethod = v.viewerOpenStreamMethod ?: return null
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
            val peekBytes = readEncodingBuffer(b, detector)
            val probeType = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            val textReader: Reader = if (probeType == "x/x-android-binary-xml") {
                b.textViewerOpenBinaryXml?.invoke(null, detector) as? Reader
                    ?: InputStreamReader(detector, Charsets.UTF_8)
            } else {
                var charsetName = readEncodingCharset(b, detector) ?: "UTF-8"
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
            postTextViewerApply(b, activity, handler)
            return null
        } catch (t: Throwable) {
            val cause = (t as? InvocationTargetException)?.targetException ?: t
            if (FxHookSupport.isFxCancelException(symbols, cause)) {
                runCatching { reader?.close() }
                postTextViewerApply(b, activity, handler)
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

    private fun postTextViewerApply(b: BonusLimitsBindings, activity: Activity, handler: Handler) {
        val uiRunnable = runCatching { b.textViewerWorkerCtor?.newInstance(activity, 1) as? Runnable }.getOrNull()
        if (uiRunnable != null) {
            handler.post(uiRunnable)
        } else if (b.textViewerApply != null) {
            handler.post { runCatching { b.textViewerApply.invoke(activity) } }
        }
    }

    private fun handleTextEdit(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
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
                    charset = readEncodingCharset(b, detector)
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
        val ctor = b.textEditResultCtor ?: return chain.proceed()
        return if (ctor.parameterTypes.size == 3) {
            ctor.newInstance(sb, b.textEditResultCaseId, finalCharset)
        } else {
            ctor.newInstance(sb, finalCharset)
        } ?: chain.proceed()
    }

    private fun handleExecPreviewDirect(symbols: ResolvedSymbols, chain: XposedInterface.Chain): Any? {
        val b = symbols.bonusLimits
        val s = symbols.sibling
        val activity = chain.thisObject as? Activity ?: return chain.proceed()
        val item = chain.getArg(0) ?: return chain.proceed()
        val openInput = s.openInputStream ?: return chain.proceed()
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return chain.proceed()
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(openInput, item, activity) as? InputStream
                ?: return chain.proceed()
            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return chain.proceed()
            val peekBytes = readEncodingBuffer(b, detector)
            val mime = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            b.execCharsetField?.set(activity, mime)
            if (mime == "x/x-elf") {
                b.execUnsupportedField?.setBoolean(activity, true)
                runCatching { detector.close() }
            } else {
                val rawCs = readEncodingCharset(b, detector)
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

    private fun handleExecPreviewWorker(symbols: ResolvedSymbols, runner: Any): Any? {
        val b = symbols.bonusLimits
        val s = symbols.sibling
        val activity = b.execWorkerArg1Field?.get(runner) as? Activity ?: return null
        val item = b.execWorkerArg2Field?.get(runner) ?: return null
        val openInput = s.openInputStream ?: return null
        val handler = b.viewerSupportHandlerField?.get(activity) as? Handler ?: return null
        val cannotReadId = FxHookSupport.resolveStringId(activity, "viewer_error_cannot_read")
        val oomId = FxHookSupport.resolveStringId(activity, "viewer_error_out_of_memory")

        var reader: BufferedReader? = null
        try {
            val lines = ArrayList<String>()
            val input = invokeViewerMethod(openInput, item, activity) as? InputStream
                ?: return null
            val detector = b.encodingStreamCtor?.newInstance(input) as? InputStream ?: return null
            val peekBytes = readEncodingBuffer(b, detector)
            val mime = peekBytes?.let { b.mimeProbeBytes?.invoke(null, it) as? String }
            if (mime == "x/x-elf") {
                b.execUnsupportedField?.setBoolean(activity, true)
                runCatching { detector.close() }
            } else {
                val rawCs = readEncodingCharset(b, detector)
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
            postExecPreviewApply(b, activity, handler)
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
                postExecPreviewApply(b, activity, handler)
            }
            return null
        }
    }

    private fun postExecPreviewApply(b: BonusLimitsBindings, activity: Activity, handler: Handler) {
        val uiRunnable = runCatching {
            b.execUiRunnableCtor?.newInstance(activity, b.execUiRunnableCaseId) as? Runnable
        }.getOrNull()
        if (uiRunnable != null) {
            handler.post(uiRunnable)
        } else if (b.execApplyPreview != null) {
            handler.post { runCatching { b.execApplyPreview.invoke(activity) } }
        }
    }

    private fun readEncodingBuffer(b: BonusLimitsBindings, detector: Any): ByteArray? {
        if (b.encodingStreamGetBuffer != null) {
            return b.encodingStreamGetBuffer.invoke(detector) as? ByteArray
        }
        b.encodingStreamDetect?.invoke(detector)
        return b.encodingStreamBufferField?.get(detector) as? ByteArray
    }

    private fun readEncodingCharset(b: BonusLimitsBindings, detector: Any): String? {
        if (b.encodingStreamGetCharset != null) {
            return b.encodingStreamGetCharset.invoke(detector) as? String
        }
        b.encodingStreamDetect?.invoke(detector)
        return b.encodingStreamCharsetField?.get(detector) as? String
    }

    private fun invokeViewerMethod(method: java.lang.reflect.Method, target: Any?, vararg args: Any?): Any? {
        return try {
            method.invoke(target, *args)
        } catch (ite: InvocationTargetException) {
            throw (ite.targetException ?: ite)
        }
    }
}
