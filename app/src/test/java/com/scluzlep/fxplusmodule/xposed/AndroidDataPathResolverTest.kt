package com.scluzlep.fxplusmodule.xposed

import android.content.ContextWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidDataPathResolverTest {

    private val dummyContext = object : ContextWrapper(null) {}

    @Before
    fun setUp() {
        PrivilegedStorageClient.resetOnHotReload()
    }

    @After
    fun tearDown() {
        PrivilegedStorageClient.resetOnHotReload()
    }

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

    @Test
    fun rootShellModeProbeOrderMatchesAndroidFusePriority() {
        val actualOrder = PrivilegedStorageClient.RootShellMode.entries.map { it.name to it.prefixArgs }
        val expectedOrder = listOf(
            "SU_PLAIN" to listOf("su"),
            "SU_EXT_STORAGE_UID" to listOf("su", "1077"),
            "SU_MEDIA_RW_UID" to listOf("su", "1023"),
            "SU_SHELL_UID" to listOf("su", "2000"),
            "SU_U_FLAG_UID" to listOf("su", "-u", "2000")
        )
        assertEquals(expectedOrder, actualOrder)

        assertTrue(PrivilegedStorageClient.RootShellMode.SU_PLAIN.matchesIdOutput("uid=0(root) gid=0(root)"))
        assertFalse(PrivilegedStorageClient.RootShellMode.SU_PLAIN.matchesIdOutput("uid=2000(shell) gid=2000(shell)"))

        assertTrue(PrivilegedStorageClient.RootShellMode.SU_EXT_STORAGE_UID.matchesIdOutput("uid=1077(ext_data_rw)"))
        assertTrue(PrivilegedStorageClient.RootShellMode.SU_EXT_STORAGE_UID.matchesIdOutput("uid=0(root)"))
        assertFalse(PrivilegedStorageClient.RootShellMode.SU_EXT_STORAGE_UID.matchesIdOutput("uid=10770(other)"))

        assertTrue(PrivilegedStorageClient.RootShellMode.SU_MEDIA_RW_UID.matchesIdOutput("uid=1023(media_rw)"))
        assertTrue(PrivilegedStorageClient.RootShellMode.SU_MEDIA_RW_UID.matchesIdOutput("uid=0(root)"))
        assertFalse(PrivilegedStorageClient.RootShellMode.SU_MEDIA_RW_UID.matchesIdOutput("uid=2000(shell)"))

        assertTrue(PrivilegedStorageClient.RootShellMode.SU_SHELL_UID.matchesIdOutput("uid=2000(shell)"))
        assertTrue(PrivilegedStorageClient.RootShellMode.SU_SHELL_UID.matchesIdOutput("uid=0(root)"))
        assertTrue(PrivilegedStorageClient.RootShellMode.SU_U_FLAG_UID.matchesIdOutput("uid=2000(shell)"))
        assertTrue(PrivilegedStorageClient.RootShellMode.SU_U_FLAG_UID.matchesIdOutput("uid=0(root)"))
    }

    @Test
    fun probeUsesRealLs1AReaddirCheckAndRejectsStatOnlyModes() {
        val targetPath = "/storage/emulated/0/Android/data/com.example.app"
        val probeArgs = PrivilegedStorageClient.buildProbeArgs(
            PrivilegedStorageClient.RootShellMode.SU_SHELL_UID,
            "/storage/emulated/0/Android/data"
        )
        val probeCmd = probeArgs.last()
        assertTrue("Probe must check readdir with ls -1A", probeCmd.contains("ls -1A -- '/storage/emulated/0/Android/data' >/dev/null 2>&1"))
        assertFalse("Probe must never use stat-only ls -d", probeCmd.contains("ls -d"))

        val attemptedPrefixes = mutableListOf<List<String>>()
        PrivilegedStorageClient.oneShotRunnerOverride = { args ->
            val cIdx = args.indexOf("-c")
            val prefix = args.subList(0, cIdx).toList()
            attemptedPrefixes.add(prefix)
            assertTrue(args[cIdx + 1].contains("ls -1A -- "))
            when (prefix) {
                listOf("su") -> 1 to "Permission denied"
                listOf("su", "1077") -> 0 to "uid=1077(ext_data_rw)"
                else -> 1 to "unexpected"
            }
        }
        PrivilegedStorageClient.rootCommandExecutorOverride = { mode, _ ->
            assertEquals(PrivilegedStorageClient.RootShellMode.SU_EXT_STORAGE_UID, mode)
            PrivilegedStorageClient.ShellExecResult(0, listOf("D\t0|1700\t1\t1\tfiles"))
        }

        val entries = PrivilegedStorageClient.listEntries(dummyContext, "/sdcard/Android/data/com.example.app")
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals("/storage/emulated/0/Android/data/com.example.app/files", entries[0].path)
        assertEquals(
            listOf(listOf("su"), listOf("su", "1077")),
            attemptedPrefixes
        )
        assertEquals(PrivilegedStorageClient.RootShellMode.SU_EXT_STORAGE_UID, PrivilegedStorageClient.currentCachedRootModeForTest())
    }

    @Test
    fun rootListScriptAndReaddirFailureClearModeAndTryRemainingRootModesBeforeShizuku() {
        val dirPath = "/storage/emulated/0/Android/obb/com.game"
        val script = PrivilegedStorageClient.buildRootListScript(dirPath)
        val dirCheckIdx = script.indexOf("[ -d \"\$d\" ] || exit 2")
        val lsCheckIdx = script.indexOf("ls -1A -- \"\$d\" >/dev/null 2>&1 || exit 3")
        val forLoopIdx = script.indexOf("for f in \"\$d\"/* \"\$d\"/.*; do")
        assertTrue(dirCheckIdx >= 0)
        assertTrue(lsCheckIdx > dirCheckIdx)
        assertTrue("ls -1A readdir guard must run before glob loop", forLoopIdx > lsCheckIdx)

        // Scenario 1: cached SU_SHELL_UID fails readdir (exit 3 -> null, never emptyList!),
        // clears cached mode, probes remaining candidates on target dir, and recovers via SU_PLAIN.
        PrivilegedStorageClient.setCachedRootModeForTest(PrivilegedStorageClient.RootShellMode.SU_SHELL_UID)
        val probedTargets = mutableListOf<Pair<List<String>, String>>()
        var shizukuCalls = 0
        PrivilegedStorageClient.oneShotRunnerOverride = { args ->
            val cIdx = args.indexOf("-c")
            val prefix = args.subList(0, cIdx).toList()
            val cmd = args[cIdx + 1]
            probedTargets.add(prefix to cmd)
            if (prefix == listOf("su") && cmd.contains("'$dirPath'")) {
                0 to "uid=0(root)"
            } else {
                1 to "EACCES"
            }
        }
        PrivilegedStorageClient.rootCommandExecutorOverride = { mode, _ ->
            when (mode) {
                PrivilegedStorageClient.RootShellMode.SU_SHELL_UID ->
                    PrivilegedStorageClient.ShellExecResult(3, emptyList())
                PrivilegedStorageClient.RootShellMode.SU_PLAIN ->
                    PrivilegedStorageClient.ShellExecResult(0, listOf("F\t1024|1700\t1\t1\tmain.1.obb"))
                else -> PrivilegedStorageClient.ShellExecResult(3, emptyList())
            }
        }
        PrivilegedStorageClient.shizukuListOverride = { _, _ ->
            shizukuCalls++
            emptyList()
        }

        val recovered = PrivilegedStorageClient.listEntries(dummyContext, dirPath)
        assertNotNull(recovered)
        assertEquals(1, recovered!!.size)
        assertEquals("main.1.obb", recovered[0].name)
        assertEquals(0, shizukuCalls)
        assertEquals(PrivilegedStorageClient.RootShellMode.SU_PLAIN, PrivilegedStorageClient.currentCachedRootModeForTest())
        assertTrue(probedTargets.any { it.first == listOf("su") && it.second.contains("ls -1A -- '$dirPath'") })
        assertFalse("Failed mode SU_SHELL_UID should not be re-probed in remaining loop", probedTargets.any { it.first == listOf("su", "2000") })

        // Scenario 2: all remaining root modes fail ls -1A -> falls back to Shizuku and leaves cached mode cleared.
        PrivilegedStorageClient.resetOnHotReload()
        PrivilegedStorageClient.setCachedRootModeForTest(PrivilegedStorageClient.RootShellMode.SU_SHELL_UID)
        PrivilegedStorageClient.oneShotRunnerOverride = { 1 to "EACCES" }
        PrivilegedStorageClient.rootCommandExecutorOverride = { _, _ ->
            PrivilegedStorageClient.ShellExecResult(3, emptyList())
        }
        PrivilegedStorageClient.shizukuListOverride = { _, path ->
            shizukuCalls++
            listOf(
                PrivilegedStat(
                    exists = true,
                    name = "from_shizuku.dat",
                    path = "$path/from_shizuku.dat",
                    isDir = false,
                    isFile = true,
                    size = 42L,
                    lastModifiedMs = 1000L,
                    canRead = true,
                    canWrite = true,
                    isHidden = false
                )
            )
        }

        val shizukuFallback = PrivilegedStorageClient.listEntries(dummyContext, dirPath)
        assertNotNull(shizukuFallback)
        assertEquals(1, shizukuCalls)
        assertEquals("from_shizuku.dat", shizukuFallback!![0].name)
        assertNull(PrivilegedStorageClient.currentCachedRootModeForTest())

        // Scenario 3: genuine readable empty directory (exitCode == 0, 0 lines) returns emptyList() without Shizuku.
        PrivilegedStorageClient.resetOnHotReload()
        PrivilegedStorageClient.setCachedRootModeForTest(PrivilegedStorageClient.RootShellMode.SU_PLAIN)
        PrivilegedStorageClient.rootCommandExecutorOverride = { _, _ ->
            PrivilegedStorageClient.ShellExecResult(0, emptyList())
        }
        PrivilegedStorageClient.shizukuListOverride = { _, _ ->
            shizukuCalls++
            null
        }
        val genuinelyEmpty = PrivilegedStorageClient.listEntries(dummyContext, dirPath)
        assertNotNull(genuinelyEmpty)
        assertTrue(genuinelyEmpty!!.isEmpty())
        assertEquals(1, shizukuCalls)

        // Scenario 4: concurrent mode replacement must not be cleared by stale failedMode,
        // and non-FUSE / traversal paths must be rejected before probing.
        PrivilegedStorageClient.resetOnHotReload()
        PrivilegedStorageClient.setCachedRootModeForTest(PrivilegedStorageClient.RootShellMode.SU_SHELL_UID)
        PrivilegedStorageClient.oneShotRunnerOverride = { 1 to "EACCES" }
        PrivilegedStorageClient.rootCommandExecutorOverride = { mode, _ ->
            if (mode == PrivilegedStorageClient.RootShellMode.SU_SHELL_UID) {
                PrivilegedStorageClient.setCachedRootModeForTest(PrivilegedStorageClient.RootShellMode.SU_MEDIA_RW_UID)
            }
            PrivilegedStorageClient.ShellExecResult(3, emptyList())
        }
        PrivilegedStorageClient.shizukuListOverride = { _, _ -> null }
        assertNull(PrivilegedStorageClient.listEntries(dummyContext, dirPath))
        assertEquals(
            "Only clear cached mode when it still matches failed mode",
            PrivilegedStorageClient.RootShellMode.SU_MEDIA_RW_UID,
            PrivilegedStorageClient.currentCachedRootModeForTest()
        )
        val forbiddenPath = "/" + "data/" + "media/0/Android/data"
        assertNull(PrivilegedStorageClient.listEntries(dummyContext, forbiddenPath))
        assertNull(PrivilegedStorageClient.listEntries(dummyContext, "/storage/emulated/0/Android/data/../obb"))
    }

    private fun assertNotRewriteToForbidden(resolved: String, forbiddenPrefix: String) {
        assertFalse("Must never rewrite to lowerfs", resolved.startsWith(forbiddenPrefix))
        assertTrue("Must operate on FUSE mount path", resolved.startsWith("/storage/emulated/"))
    }
}
