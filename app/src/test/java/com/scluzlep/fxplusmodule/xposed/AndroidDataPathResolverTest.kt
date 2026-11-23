package com.scluzlep.fxplusmodule.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDataPathResolverTest {

    @Test
    fun preservesFusePathsVerbatimAndNormalizesSdcardPrefix() {
        val cases = listOf(
            "/storage/emulated/0/Android/data" to "/storage/emulated/0/Android/data",
            "/storage/emulated/0/Android/obb" to "/storage/emulated/0/Android/obb",
            "/storage/emulated/0/Android/data/com.example.app/files/log.txt" to
                "/storage/emulated/0/Android/data/com.example.app/files/log.txt",
            "/storage/emulated/10/Android/obb/com.game/main.1.obb" to
                "/storage/emulated/10/Android/obb/com.game/main.1.obb",
            "/sdcard/Android/data/com.foo/cache" to
                "/storage/emulated/0/Android/data/com.foo/cache",
            "/sdcard//Android/obb/" to
                "/storage/emulated/0/Android/obb"
        )

        val forbiddenPrefix = "/" + "data/" + "media"
        for ((input, expected) in cases) {
            val resolved = PrivilegedStorageClient.normalizeAndValidateFusePath(input)
            assertEquals(expected, resolved)
            assertNotRewriteToForbidden(resolved!!, forbiddenPrefix)
            assertTrue(PrivilegedStorageClient.isScopedRestrictedPath(input))
        }
    }

    @Test
    fun rejectsTraversalAndOutOfScopePaths() {
        val rejected = listOf(
            null,
            "",
            "relative/Android/data",
            "/storage/emulated/0/Android",
            "/storage/emulated/0/Android/media",
            "/storage/emulated/0/Android/media/com.whatsapp",
            "/storage/emulated/0/Download/file.zip",
            "/storage/emulated/0/Android/data/../media",
            "/storage/emulated/0/Android/data/./com.foo",
            "/storage/emulated/0/Android/data/com.foo\\bar",
            "/storage/emulated/0/Android/data/com.foo\nbar",
            "/storage/emulated/0/Android/data/com.foo\u0000bar"
        )

        for (raw in rejected) {
            assertNull("Path should be rejected: $raw", PrivilegedStorageClient.normalizeAndValidateFusePath(raw))
            assertFalse(PrivilegedStorageClient.isScopedRestrictedPath(raw))
        }
    }

    @Test
    fun identifiesRestrictedRootsAndEscapesSingleQuotesSafely() {
        assertTrue(PrivilegedStorageClient.isRestrictedRoot("/storage/emulated/0/Android/data"))
        assertTrue(PrivilegedStorageClient.isRestrictedRoot("/sdcard/Android/obb"))
        assertFalse(PrivilegedStorageClient.isRestrictedRoot("/storage/emulated/0/Android/data/com.pkg"))

        val escaped = PrivilegedStorageClient.escapeShellSingleQuotes("it's a 'test'")
        assertEquals("it'\"'\"'s a '\"'\"'test'\"'\"'", escaped)
    }

    private fun assertNotRewriteToForbidden(resolved: String, forbiddenPrefix: String) {
        assertFalse("Must never rewrite to lowerfs", resolved.startsWith(forbiddenPrefix))
        assertTrue("Must operate on FUSE mount path", resolved.startsWith("/storage/emulated/"))
    }
}
