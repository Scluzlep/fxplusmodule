package com.scluzlep.fxplusmodule.xposed

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/**
 * Pure-Kotlin ZIP + DEX binary verifier ensuring every class, method descriptor,
 * and field type referenced by [FxSymbolResolver] exists verbatim inside the real
 * FX `9012` (`apk/base.apk`) and `9108` (`FX Explorer_v9.1.0.8_9108.apk`) APKs.
 */
class DualApkDexContractTest {

    @Test
    fun verify9012ContractsAgainstRealApk() {
        val apkFile = resolveRepoRelativeFile("../apk/base.apk")
        assumeTrue("FX 9012 APK missing at ${apkFile.absolutePath}; skipping in CI", apkFile.isFile)
        verifyApkContract(apkFile, FxDexContracts.contract9012, label = "9012")
    }

    @Test
    fun verify9108ContractsAgainstRealApk() {
        val apkFile = resolveRepoRelativeFile("../FX Explorer_v9.1.0.8_9108.apk")
        assumeTrue("FX 9108 APK missing at ${apkFile.absolutePath}; skipping in CI", apkFile.isFile)
        verifyApkContract(apkFile, FxDexContracts.contract9108, label = "9108")
    }

    @Test
    fun v11ContractsIncludeHardwareRenderAndFolderLifecycleSymbols() {
        val c9012 = FxDexContracts.contract9012
        val c9108 = FxDexContracts.contract9108

        assertTrue(c9012.classes.containsAll(listOf(
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.r",
            "nextapp.fx.ui.content.h0",
            "nextapp.fx.ui.content.e2",
            "nextapp.fx.ui.content.e0",
            "v8.f"
        )))
        assertTrue(c9108.classes.containsAll(listOf(
            "nextapp.fx.ui.ExplorerActivity",
            "nextapp.fx.ui.content.k",
            "nextapp.fx.ui.content.w",
            "nextapp.fx.ui.content.c1",
            "nextapp.fx.ui.content.p",
            "pa.c"
        )))
        assertTrue(c9012.methods.any { it.className == "nextapp.maui.ui.imageview.TouchImageDisplay" && it.methodName == "onDraw" })
        assertTrue(c9012.methods.any { it.className == "se.f" && it.methodName == "C" })
        assertTrue(c9108.methods.any { it.className == "nextapp.maui.ui.imageview.TouchImageDisplay" && it.methodName == "onDraw" })
        assertTrue(c9108.methods.any { it.className == "hh.f" && it.methodName == "G" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.content.r" && it.fieldName == "r5" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.content.k" && it.fieldName == "n2" })
    }

    @Test
    fun v13ContractsIncludeThemeHomeMenuAndGoogleDriveSymbols() {
        val c9012 = FxDexContracts.contract9012
        val c9108 = FxDexContracts.contract9108

        assertTrue(c9012.classes.containsAll(listOf(
            "xc.f",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "nextapp.fx.ui.homecontent.HomeContentView\$d",
            "nextapp.fx.ui.content.j0",
            "le.t",
            "le.f0",
            "le.r",
            "le.b\$a",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "nextapp.fx.plus.dirimpl.googledrive.b\$a",
            "ic.e\$a",
            "i9.e"
        )))
        assertTrue(c9108.classes.containsAll(listOf(
            "ef.g",
            "nextapp.fx.ui.homecontent.HomeContentView",
            "be.p",
            "nextapp.fx.ui.content.b0",
            "yg.r",
            "yg.y",
            "yg.p",
            "yg.b",
            "nextapp.fx.ui.res.ActionIcons",
            "nextapp.fx.plus.ui.net.cloud.GoogleDriveAuthActivity",
            "gg.d",
            "jh.b",
            "bb.d"
        )))

        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "e" && it.isStatic })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "P" })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "N" })
        assertTrue(c9012.methods.any { it.className == "xc.f" && it.methodName == "O" })
        assertTrue(c9012.methods.any { it.className == "i9.e" && it.methodName == "<init>" })
        assertTrue(c9012.methods.any { it.className == "nextapp.fx.ui.homecontent.HomeContentView\$d" && it.methodName == "p" })
        assertTrue(c9012.methods.any { it.className == "nextapp.fx.plus.dirimpl.googledrive.b\$a" && it.methodName == "c" && it.isStatic })
        assertTrue(c9012.methods.any { it.className == "ic.e\$a" && it.methodName == "<init>" })

        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "b" && it.isStatic })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "w" })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "u" })
        assertTrue(c9108.methods.any { it.className == "ef.g" && it.methodName == "v" })
        assertTrue(c9108.methods.any { it.className == "bb.d" && it.methodName == "<init>" })
        assertTrue(c9108.methods.any { it.className == "be.p" && it.methodName == "m" })
        assertTrue(c9108.methods.any { it.className == "gg.d" && it.methodName == "<init>" })
        assertTrue(c9108.methods.any { it.className == "jh.b" && it.methodName == "<init>" })

        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.viewer.image.ImageViewerActivity" && it.fieldName == "R5" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.widget.k" && it.fieldName == "ui" })
        assertTrue(c9012.fields.any { it.className == "nextapp.fx.ui.homecontent.HomeContentView\$d" && it.fieldName == "d" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.viewer.image.ImageViewerActivity" && it.fieldName == "N2" })
        assertTrue(c9108.fields.any { it.className == "nextapp.fx.ui.widget.n" && it.fieldName == "ui" })
        assertTrue(c9108.fields.any { it.className == "be.p" && it.fieldName == "c" })
        assertTrue(c9108.fields.any { it.className == "gg.d" && it.fieldName == "a" })
        assertTrue(c9108.fields.any { it.className == "jh.b" && it.fieldName == "a" })
        assertTrue(c9108.fields.any { it.className == "jh.b" && it.fieldName == "b" })
    }

    private fun resolveRepoRelativeFile(relFromRepoRoot: String): File {
        val direct = File(relFromRepoRoot)
        if (direct.isFile) return direct
        val fromModule = File("../$relFromRepoRoot")
        if (fromModule.isFile) return fromModule
        return direct
    }

    private fun verifyApkContract(apkFile: File, contract: VersionContract, label: String) {
        val index = parseApkDexIndex(apkFile)
        val errors = mutableListOf<String>()

        for (javaClass in contract.classes) {
            val desc = toDexDescriptor(javaClass)
            if (desc !in index.definedClasses) {
                errors += "[$label] Missing DEX class_def: $desc"
            }
        }

        for (spec in contract.methods) {
            val classDesc = toDexDescriptor(spec.className)
            val clsMethods = index.classMethods[classDesc]
            if (clsMethods == null) {
                errors += "[$label] Missing owner class for method ${spec.className}.${spec.methodName}${spec.descriptor}"
                continue
            }
            val key = "${spec.methodName}${spec.descriptor}"
            val flags = clsMethods[key]
            if (flags == null) {
                errors += "[$label] Missing DEX method: $classDesc->$key"
            } else {
                val actualStatic = (flags and ACC_STATIC) != 0
                if (actualStatic != spec.isStatic) {
                    errors += "[$label] Static mismatch for $classDesc->$key (expected isStatic=${spec.isStatic}, actual=$actualStatic)"
                }
            }
        }

        for (spec in contract.fields) {
            val classDesc = toDexDescriptor(spec.className)
            val clsFields = index.classFields[classDesc]
            if (clsFields == null) {
                errors += "[$label] Missing owner class for field ${spec.className}.${spec.fieldName}:${spec.typeDescriptor}"
                continue
            }
            val key = "${spec.fieldName}:${spec.typeDescriptor}"
            val flags = clsFields[key]
            if (flags == null) {
                errors += "[$label] Missing DEX field: $classDesc->$key"
            } else {
                val actualStatic = (flags and ACC_STATIC) != 0
                if (actualStatic != spec.isStatic) {
                    errors += "[$label] Static mismatch for $classDesc->$key (expected isStatic=${spec.isStatic}, actual=$actualStatic)"
                }
            }
        }

        assertTrue(
            "DEX contract failures for FX $label (${errors.size}):\n${errors.joinToString("\n")}",
            errors.isEmpty()
        )
    }

    private fun toDexDescriptor(javaName: String): String {
        return "L${javaName.replace('.', '/')};"
    }

    private class DexIndex {
        val definedClasses = HashSet<String>(4096)
        val classMethods = HashMap<String, MutableMap<String, Int>>(4096)
        val classFields = HashMap<String, MutableMap<String, Int>>(4096)
    }

    private fun parseApkDexIndex(apkFile: File): DexIndex {
        val index = DexIndex()
        ZipFile(apkFile).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && DEX_NAME_REGEX.matches(it.name) }
                .sortedBy { it.name }
                .toList()
            require(entries.isNotEmpty()) { "No classes*.dex entries in ${apkFile.name}" }
            for (entry in entries) {
                val dexBytes = zip.getInputStream(entry).use { it.readBytes() }
                parseSingleDex(dexBytes, index)
            }
        }
        return index
    }

    private fun parseSingleDex(bytes: ByteArray, index: DexIndex) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        require(magic == "dex\n") { "Invalid DEX magic: $magic" }

        val stringIdsSize = buf.getInt(0x38)
        val stringIdsOff = buf.getInt(0x3C)
        val typeIdsSize = buf.getInt(0x40)
        val typeIdsOff = buf.getInt(0x44)
        val protoIdsSize = buf.getInt(0x48)
        val protoIdsOff = buf.getInt(0x4C)
        val fieldIdsSize = buf.getInt(0x50)
        val fieldIdsOff = buf.getInt(0x54)
        val methodIdsSize = buf.getInt(0x58)
        val methodIdsOff = buf.getInt(0x5C)
        val classDefsSize = buf.getInt(0x60)
        val classDefsOff = buf.getInt(0x64)

        val strings = Array(stringIdsSize) { i ->
            val strDataOff = buf.getInt(stringIdsOff + i * 4)
            readMutf8String(bytes, strDataOff)
        }

        val types = Array(typeIdsSize) { i ->
            val descriptorIdx = buf.getInt(typeIdsOff + i * 4)
            strings[descriptorIdx]
        }

        val protos = Array(protoIdsSize) { i ->
            val base = protoIdsOff + i * 12
            val returnTypeIdx = buf.getInt(base + 4)
            val paramsOff = buf.getInt(base + 8)
            val sb = StringBuilder("(")
            if (paramsOff != 0) {
                val count = buf.getInt(paramsOff)
                for (p in 0 until count) {
                    val paramTypeIdx = buf.getShort(paramsOff + 4 + p * 2).toInt() and 0xFFFF
                    sb.append(types[paramTypeIdx])
                }
            }
            sb.append(')').append(types[returnTypeIdx])
            sb.toString()
        }

        val fieldKeys = Array(fieldIdsSize) { i ->
            val base = fieldIdsOff + i * 8
            val typeIdx = buf.getShort(base + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(base + 4)
            "${strings[nameIdx]}:${types[typeIdx]}"
        }

        val methodKeys = Array(methodIdsSize) { i ->
            val base = methodIdsOff + i * 8
            val protoIdx = buf.getShort(base + 2).toInt() and 0xFFFF
            val nameIdx = buf.getInt(base + 4)
            "${strings[nameIdx]}${protos[protoIdx]}"
        }

        for (c in 0 until classDefsSize) {
            val base = classDefsOff + c * 32
            val classIdx = buf.getInt(base)
            val classDataOff = buf.getInt(base + 24)
            val classDesc = types[classIdx]
            index.definedClasses.add(classDesc)

            if (classDataOff == 0) continue

            val fieldMap = index.classFields.getOrPut(classDesc) { HashMap() }
            val methodMap = index.classMethods.getOrPut(classDesc) { HashMap() }

            val cursor = IntArray(1) { classDataOff }
            val staticFieldsSize = readUleb128(bytes, cursor)
            val instanceFieldsSize = readUleb128(bytes, cursor)
            val directMethodsSize = readUleb128(bytes, cursor)
            val virtualMethodsSize = readUleb128(bytes, cursor)

            var fieldIdx = 0
            for (i in 0 until staticFieldsSize) {
                fieldIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                fieldMap[fieldKeys[fieldIdx]] = accessFlags
            }

            fieldIdx = 0
            for (i in 0 until instanceFieldsSize) {
                fieldIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                fieldMap[fieldKeys[fieldIdx]] = accessFlags
            }

            var methodIdx = 0
            for (i in 0 until directMethodsSize) {
                methodIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                readUleb128(bytes, cursor) // code_off
                methodMap[methodKeys[methodIdx]] = accessFlags
            }

            methodIdx = 0
            for (i in 0 until virtualMethodsSize) {
                methodIdx += readUleb128(bytes, cursor)
                val accessFlags = readUleb128(bytes, cursor)
                readUleb128(bytes, cursor) // code_off
                methodMap[methodKeys[methodIdx]] = accessFlags
            }
        }
    }

    private fun readUleb128(bytes: ByteArray, cursor: IntArray): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = bytes[cursor[0]++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }

    private fun readMutf8String(bytes: ByteArray, offset: Int): String {
        val cursor = IntArray(1) { offset }
        readUleb128(bytes, cursor) // utf16_size
        val out = ByteArrayOutputStream()
        var pos = cursor[0]
        while (true) {
            val b = bytes[pos++].toInt() and 0xFF
            if (b == 0) break
            out.write(b)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    companion object {
        private const val ACC_STATIC = 0x0008
        private val DEX_NAME_REGEX = Regex("^classes[0-9]*\\.dex$")
    }
}
