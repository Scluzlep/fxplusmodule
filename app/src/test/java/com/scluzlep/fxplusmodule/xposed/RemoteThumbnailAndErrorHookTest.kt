package com.scluzlep.fxplusmodule.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteThumbnailAndErrorHookTest {

    @Suppress("unused")
    private class FakeFxUserException(
        val selectedCode: String,
        cause: Throwable?,
        detail: String
    ) : Exception(detail, cause) {
        companion object {
            @JvmStatic
            fun A(cause: Throwable?, msg: String) = FakeFxUserException("FTP_SSL_REUSE", cause, msg)

            @JvmStatic
            fun f(cause: Exception?, msg: String) = FakeFxUserException("EXISTS", cause, msg)

            @JvmStatic
            fun P(cause: Throwable?, msg: String) = FakeFxUserException("NO_ACCESS", cause, msg)

            @JvmStatic
            fun q(cause: Exception?, msg: String) = FakeFxUserException("NO_ACCESS_9108", cause, msg)

            @JvmStatic
            fun c0(cause: Throwable?, msg: String) = FakeFxUserException("READ_ERROR", cause, msg)

            @JvmStatic
            fun q0(cause: Throwable?, msg: String) = FakeFxUserException("WRITE_ERROR", cause, msg)
        }
    }

    @Suppress("unused")
    private class FakeFxUserException9108(
        val selectedCode: String,
        cause: Throwable?,
        detail: String
    ) : Exception(detail, cause) {
        companion object {
            @JvmStatic
            fun f(cause: Exception?, msg: String) = FakeFxUserException9108("EXISTS_9108", cause, msg)

            @JvmStatic
            fun q(cause: Exception?, msg: String) = FakeFxUserException9108("NO_ACCESS_9108", cause, msg)

            @JvmStatic
            fun v(cause: Exception?, msg: String) = FakeFxUserException9108("READ_ERROR_9108", cause, msg)

            @JvmStatic
            fun y(cause: Exception?, msg: String) = FakeFxUserException9108("WRITE_ERROR_9108", cause, msg)
        }
    }

    @Test
    fun dropboxCloudMarkerUsesRemotePrefKeyAndUnlocksOver20Mb() {
        assertEquals("Remote", RemoteThumbnailHook.computeRemotePrefKey(isRemoteMarker = false, isCloudMarker = true))
        assertEquals("Network", RemoteThumbnailHook.computeRemotePrefKey(isRemoteMarker = true, isCloudMarker = false))
        assertEquals("Network", RemoteThumbnailHook.computeRemotePrefKey(isRemoteMarker = true, isCloudMarker = true))

        assertTrue(
            RemoteThumbnailHook.shouldUnlockRemoteThumbnail(
                isRemoteMarker = false,
                isCloudMarker = true,
                isImage = true,
                itemSize = 25L * 1024L * 1024L,
                maxCapBytes = Long.MAX_VALUE
            )
        )
        assertTrue(
            RemoteThumbnailHook.shouldUnlockRemoteThumbnail(
                isRemoteMarker = true,
                isCloudMarker = false,
                isImage = true,
                itemSize = 5L * 1024L * 1024L,
                maxCapBytes = 10L * 1024L * 1024L
            )
        )
        assertFalse(
            RemoteThumbnailHook.shouldUnlockRemoteThumbnail(
                isRemoteMarker = false,
                isCloudMarker = true,
                isImage = true,
                itemSize = 25L * 1024L * 1024L,
                maxCapBytes = 20L * 1024L * 1024L
            )
        )
        assertFalse(
            RemoteThumbnailHook.shouldUnlockRemoteThumbnail(
                isRemoteMarker = true,
                isCloudMarker = false,
                isImage = true,
                itemSize = 2L * 1024L * 1024L,
                maxCapBytes = Long.MAX_VALUE
            )
        )
    }

    @Test
    fun buildUserExceptionSelectsDeterministicErrorFactoriesInsteadOfFirstDexMethod() {
        val accessEx = FxHookSupport.buildUserException(
            FakeFxUserException::class.java,
            "Protected directory",
            FxUserErrorKind.ACCESS
        ) as FakeFxUserException
        assertEquals("NO_ACCESS", accessEx.selectedCode)

        val readEx = FxHookSupport.buildUserException(
            FakeFxUserException::class.java,
            "Read failed",
            FxUserErrorKind.READ
        ) as FakeFxUserException
        assertEquals("READ_ERROR", readEx.selectedCode)

        val writeEx = FxHookSupport.buildUserException(
            FakeFxUserException::class.java,
            "Write failed",
            FxUserErrorKind.WRITE
        ) as FakeFxUserException
        assertEquals("WRITE_ERROR", writeEx.selectedCode)

        val existsEx = FxHookSupport.buildUserException(
            FakeFxUserException::class.java,
            "Already exists",
            FxUserErrorKind.EXISTS
        ) as FakeFxUserException
        assertEquals("EXISTS", existsEx.selectedCode)

        val access9108 = FxHookSupport.buildUserException(
            FakeFxUserException9108::class.java,
            "Protected directory",
            FxUserErrorKind.ACCESS
        ) as FakeFxUserException9108
        assertEquals("NO_ACCESS_9108", access9108.selectedCode)

        val read9108 = FxHookSupport.buildUserException(
            FakeFxUserException9108::class.java,
            "Read failed",
            FxUserErrorKind.READ
        ) as FakeFxUserException9108
        assertEquals("READ_ERROR_9108", read9108.selectedCode)

        val write9108 = FxHookSupport.buildUserException(
            FakeFxUserException9108::class.java,
            "Write failed",
            FxUserErrorKind.WRITE
        ) as FakeFxUserException9108
        assertEquals("WRITE_ERROR_9108", write9108.selectedCode)

        val exists9108 = FxHookSupport.buildUserException(
            FakeFxUserException9108::class.java,
            "Already exists",
            FxUserErrorKind.EXISTS
        ) as FakeFxUserException9108
        assertEquals("EXISTS_9108", exists9108.selectedCode)
    }
}
