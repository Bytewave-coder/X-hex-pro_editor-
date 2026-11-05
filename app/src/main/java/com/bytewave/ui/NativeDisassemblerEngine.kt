package com.bytewave.ui

import androidx.compose.ui.graphics.Color
import com.bytewave.nativebridge.NativeDisasmJniBridge
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.min

enum class CpuArchitecture(
    val id: String,
    val title: String,
    val shortLabel: String,
    val defaultAlignment: Int,
    val description: String
) {
    ARM64(
        id = "ARM64",
        title = "ARM64 (AArch64)",
        shortLabel = "ARM64",
        defaultAlignment = 4,
        description = "64-bit ARMv8-A / AArch64 Native Machine Instructions"
    ),
    ARM32_THUMB(
        id = "ARM32",
        title = "ARM32 (Thumb-2 / A32)",
        shortLabel = "ARM32/T32",
        defaultAlignment = 2,
        description = "32-bit ARMv7 & 16/32-bit Thumb-2 Instruction Stream"
    ),
    X86_64(
        id = "X86_64",
        title = "Intel x86_64 (AMD64)",
        shortLabel = "x86_64",
        defaultAlignment = 1,
        description = "64-bit Intel / AMD Variable-Length CISC Machine Code"
    ),
    DALVIK_DEX(
        id = "DEX",
        title = "Dalvik DEX / Smali",
        shortLabel = "DEX/Smali",
        defaultAlignment = 2,
        description = "Android Dalvik Executable Bytecode & Smali Disassembly"
    )
}

enum class InstructionCategory(
    val label: String,
    val badgeColor: Color
) {
    PROLOGUE_STACK("STACK / FRAME", Color(0xFFAB47BC)),
    ARITHMETIC_LOGIC("ALU / LOGIC", Color(0xFF29B6F6)),
    MEMORY_ACCESS("LOAD / STORE", Color(0xFFFFA726)),
    CONTROL_BRANCH("BRANCH / JUMP", Color(0xFFFFCA28)),
    CALL_INVOKE("CALL / INVOKE", Color(0xFF00E676)),
    RETURN_SYSCALL("RET / SYSCALL", Color(0xFFFF5252)),
    CONSTANT_STRING("CONST / POOL", Color(0xFF26A69A)),
    DATA_NOP("NOP / DATA", Color(0xFF78909C))
}

data class DisassembledInstruction(
    val index: Int,
    val address: Long,
    val fileOffset: Int,
    val sizeBytes: Int,
    val rawBytesHex: String,
    val opcodeHex: String,
    val mnemonic: String,
    val operands: String,
    val category: InstructionCategory,
    val registersRead: List<String>,
    val registersModified: List<String>,
    val memoryAccess: String?,
    val stackDelta: Int,
    val branchTarget: Long?,
    val isConditionalBranch: Boolean,
    val isCall: Boolean,
    val isReturn: Boolean,
    val resolvedSymbol: String?,
    val pseudocodeLine: String,
    val xrefsFrom: List<Long> = emptyList()
)

data class DecompiledVariable(
    val name: String,
    val storage: String,
    val inferredType: String,
    val usageCount: Int
)

data class DisassembledFunction(
    val id: String,
    val name: String,
    val startAddress: Long,
    val endAddress: Long,
    val startOffset: Int,
    val instructionIndices: List<Int>,
    val signature: String,
    val returnType: String,
    val stackFrameSize: Int,
    val isRecursive: Boolean,
    val isLeaf: Boolean,
    val cyclomaticComplexity: Int,
    val variables: List<DecompiledVariable>,
    val callers: List<Long>,
    val callees: List<Long>,
    val decompiledCodeLines: List<String>
)

data class BasicBlockNode(
    val id: Int,
    val label: String,
    val functionName: String,
    val startAddress: Long,
    val endAddress: Long,
    val instructions: List<DisassembledInstruction>,
    val outgoingTrueTarget: Int?,
    val outgoingFalseTarget: Int?,
    val incomingBlocks: List<Int>,
    val blockType: String
)

enum class XrefType(val label: String, val color: Color) {
    CALL("CALL", Color(0xFF00E676)),
    JUMP_COND("COND JUMP", Color(0xFFFFCA28)),
    JUMP_UNCOND("UNCOND JUMP", Color(0xFF29B6F6)),
    STRING_REF("STRING / CONST", Color(0xFF26A69A)),
    DATA_MEM("MEMORY REF", Color(0xFFFFA726))
}

data class CrossReferenceItem(
    val fromAddress: Long,
    val fromFunction: String,
    val toAddress: Long,
    val toSymbol: String,
    val type: XrefType,
    val instructionSummary: String
)

data class BinarySectionHeader(
    val name: String,
    val virtualAddress: Long,
    val fileOffset: Long,
    val sizeBytes: Long,
    val flags: String,
    val typeName: String,
    val entropyEstimate: Float
)

data class BinarySymbolEntry(
    val name: String,
    val address: Long,
    val kind: String,
    val bind: String,
    val sectionName: String
)

data class EmbeddedApkEntry(
    val path: String,
    val sizeBytes: Long,
    val detectedArch: CpuArchitecture,
    val extractedBytes: ByteArray
)

data class NativeDisassemblyResult(
    val fileName: String,
    val totalFileSize: Long,
    val analyzedSliceSize: Int,
    val detectedFormat: String,
    val autoDetectedArch: CpuArchitecture,
    val activeArch: CpuArchitecture,
    val baseVirtualAddress: Long,
    val codeStartOffset: Int,
    val entryPointAddress: Long,
    val computeTimeMs: Double,
    val jniBridgeStatus: String,
    val rustValidationSummary: String,
    val headerAttributes: List<Pair<String, String>>,
    val sections: List<BinarySectionHeader>,
    val symbols: List<BinarySymbolEntry>,
    val embeddedApkEntries: List<EmbeddedApkEntry>,
    val instructions: List<DisassembledInstruction>,
    val functions: List<DisassembledFunction>,
    val basicBlocks: List<BasicBlockNode>,
    val xrefs: List<CrossReferenceItem>,
    val extractedStrings: List<ExtractedBinaryString>
)

object NativeDisassemblerEngine {

    /**
     * Inspects APK / ZIP archives to extract embedded classes.dex or native .so libraries
     * so users importing an APK directly can disassemble its DEX bytecode or native ARM64/x86_64 .so libraries.
     */
    fun extractEmbeddedApkTargets(rawBytes: ByteArray): List<EmbeddedApkEntry> {
        if (rawBytes.size < 4) return emptyList()
        val b0 = rawBytes[0].toInt() and 0xFF
        val b1 = rawBytes[1].toInt() and 0xFF
        val b2 = rawBytes[2].toInt() and 0xFF
        val b3 = rawBytes[3].toInt() and 0xFF
        if (!(b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04)) return emptyList()

        val results = ArrayList<EmbeddedApkEntry>()
        try {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null && results.size < 16) {
                    val name = entry.name
                    val isDex = name.endsWith(".dex", ignoreCase = true)
                    val isSo = name.endsWith(".so", ignoreCase = true)
                    if (!entry.isDirectory && (isDex || isSo)) {
                        val buffer = ByteArray(min(262_144, maxOf(entry.size.toInt(), 65_536)))
                        var totalRead = 0
                        while (totalRead < buffer.size) {
                            val r = zis.read(buffer, totalRead, buffer.size - totalRead)
                            if (r <= 0) break
                            totalRead += r
                        }
                        if (totalRead > 0) {
                            val slice = buffer.copyOf(totalRead)
                            val arch = when {
                                isDex -> CpuArchitecture.DALVIK_DEX
                                name.contains("arm64") || name.contains("aarch64") -> CpuArchitecture.ARM64
                                name.contains("x86_64") || name.contains("amd64") -> CpuArchitecture.X86_64
                                name.contains("armeabi") || name.contains("armv7") -> CpuArchitecture.ARM32_THUMB
                                else -> detectArchitecture(slice, name).first
                            }
                            results.add(
                                EmbeddedApkEntry(
                                    path = name,
                                    sizeBytes = if (entry.size > 0) entry.size else totalRead.toLong(),
                                    detectedArch = arch,
                                    extractedBytes = slice
                                )
                            )
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    /**
     * Automatically detects CPU architecture, container format, base virtual address, and code start offset.
     */
    fun detectArchitecture(
        bytes: ByteArray,
        fileName: String
    ): Triple<CpuArchitecture, String, Int> {
        if (bytes.size >= 8) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF

            // Dalvik DEX Magic: "dex\n" (64 65 78 0A)
            if (b0 == 0x64 && b1 == 0x65 && b2 == 0x78 && b3 == 0x0A) {
                val ver = if (bytes.size >= 7) String(bytes, 4, 3, Charsets.US_ASCII) else "035"
                val dataOff = if (bytes.size >= 0x70) {
                    val bb = ByteBuffer.wrap(bytes, 0, 0x70).order(ByteOrder.LITTLE_ENDIAN)
                    val off = bb.getInt(0x6C)
                    if (off in 0x70 until bytes.size) off else 0x70
                } else 0x70
                return Triple(
                    CpuArchitecture.DALVIK_DEX,
                    "Android Dalvik Executable (DEX v$ver)",
                    dataOff
                )
            }

            // ELF Magic: 7F 'E' 'L' 'F'
            if (b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46 && bytes.size >= 0x40) {
                val is64 = (bytes[4].toInt() and 0xFF) == 2
                val isLe = (bytes[5].toInt() and 0xFF) != 2
                val bb = ByteBuffer.wrap(bytes, 0, min(bytes.size, 0x40))
                    .order(if (isLe) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
                val eMachine = bb.getShort(0x12).toInt() and 0xFFFF
                val codeOff = if (bytes.size > 0x1000) 0x100 else 0x40
                return when (eMachine) {
                    0xB7 -> Triple(
                        CpuArchitecture.ARM64,
                        "ELF ${if (is64) "64-Bit" else "32-Bit"} Shared Object / Executable (AArch64)",
                        codeOff
                    )
                    0x28 -> Triple(
                        CpuArchitecture.ARM32_THUMB,
                        "ELF 32-Bit Native Binary (ARM32 / Thumb-2)",
                        codeOff
                    )
                    0x3E, 0x03 -> Triple(
                        CpuArchitecture.X86_64,
                        "ELF ${if (is64) "64-Bit" else "32-Bit"} Native Binary (x86_64 / AMD64)",
                        codeOff
                    )
                    else -> Triple(
                        if (is64) CpuArchitecture.ARM64 else CpuArchitecture.ARM32_THUMB,
                        "ELF Native Binary (Machine 0x${Integer.toHexString(eMachine)})",
                        codeOff
                    )
                }
            }

            // PE / COFF Magic: 'M' 'Z' (4D 5A)
            if (b0 == 0x4D && b1 == 0x5A && bytes.size >= 0x40) {
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val peOffset = bb.getInt(0x3C)
                if (peOffset in 0x40..(bytes.size - 24)) {
                    val machine = bb.getShort(peOffset + 4).toInt() and 0xFFFF
                    val codeOff = min(bytes.size - 16, maxOf(0x200, peOffset + 0xF8))
                    return when (machine) {
                        0xAA64 -> Triple(CpuArchitecture.ARM64, "PE32+ Executable / DLL (ARM64)", codeOff)
                        0x01C0, 0x01C4 -> Triple(CpuArchitecture.ARM32_THUMB, "PE32 Executable / DLL (ARM32/Thumb)", codeOff)
                        else -> Triple(CpuArchitecture.X86_64, "PE32+ Executable / DLL (x86_64 / AMD64)", codeOff)
                    }
                }
                return Triple(CpuArchitecture.X86_64, "PE / MZ Portable Executable (x86_64)", 0x100)
            }
        }

        val lower = fileName.lowercase(Locale.US)
        return when {
            lower.endsWith(".dex") || lower.endsWith(".odex") ->
                Triple(CpuArchitecture.DALVIK_DEX, "Dalvik Bytecode Stream", 0)
            lower.contains("x86") || lower.endsWith(".exe") || lower.endsWith(".dll") ->
                Triple(CpuArchitecture.X86_64, "x86_64 Machine Code Stream", 0)
            lower.contains("arm32") || lower.contains("thumb") || lower.contains("v7a") ->
                Triple(CpuArchitecture.ARM32_THUMB, "ARM32 / Thumb-2 Code Stream", 0)
            else ->
                Triple(CpuArchitecture.ARM64, "ARM64 (AArch64) Binary Stream", 0)
        }
    }

    /**
     * Full analysis pipeline: parses container headers/sections, disassembles up to maxInstructions,
     * resolves XREFs, builds basic blocks + CFG edges, and reconstructs C/Java pseudocode functions.
     */
    fun analyzeBinary(
        rawBytes: ByteArray,
        fileName: String,
        totalFileSize: Long,
        overrideArch: CpuArchitecture? = null,
        customCodeOffset: Int? = null,
        maxInstructions: Int = 360
    ): NativeDisassemblyResult {
        val t0 = System.nanoTime()

        val embeddedApkEntries = extractEmbeddedApkTargets(rawBytes)
        val activeBytes = if (embeddedApkEntries.isNotEmpty() && overrideArch == null && customCodeOffset == null) {
            // If user opened an APK, prioritize its primary classes.dex or first native .so
            val preferred = embeddedApkEntries.firstOrNull { it.path.endsWith(".so") }
                ?: embeddedApkEntries.first()
            preferred.extractedBytes
        } else {
            rawBytes
        }

        val (autoArch, formatName, detectedCodeOffset) = detectArchitecture(activeBytes, fileName)
        val arch = overrideArch ?: autoArch
        val safeCodeOffset = (customCodeOffset ?: detectedCodeOffset).coerceIn(0, maxOf(0, activeBytes.size - 1))

        val (baseVa, entryVa, headerAttrs, sections, symbols) = parseHeadersAndSections(
            bytes = activeBytes,
            totalFileSize = totalFileSize,
            formatName = formatName,
            arch = arch,
            defaultCodeOffset = safeCodeOffset
        )

        val dexPools = if (arch == CpuArchitecture.DALVIK_DEX) {
            parseDexPools(activeBytes)
        } else {
            DexPools.EMPTY
        }

        val extractedStrings = extractStringsFromBinary(activeBytes)

        val preliminaryInstructions = decodeInstructionStream(
            bytes = activeBytes,
            arch = arch,
            startOffset = safeCodeOffset,
            baseVa = baseVa,
            maxInstructions = maxInstructions,
            dexPools = dexPools,
            strings = extractedStrings,
            symbols = symbols
        )

        // Build XREFs map (targetAddress -> list of sourceAddresses)
        val incomingXrefsMap = HashMap<Long, MutableList<Long>>()
        val xrefItems = ArrayList<CrossReferenceItem>()

        for (insn in preliminaryInstructions) {
            val target = insn.branchTarget
            if (target != null) {
                incomingXrefsMap.getOrPut(target) { ArrayList() }.add(insn.address)
                val xType = when {
                    insn.isCall -> XrefType.CALL
                    insn.isConditionalBranch -> XrefType.JUMP_COND
                    else -> XrefType.JUMP_UNCOND
                }
                xrefItems.add(
                    CrossReferenceItem(
                        fromAddress = insn.address,
                        fromFunction = "sub_${String.format(Locale.US, "%06X", insn.address and 0xFFFFFF)}",
                        toAddress = target,
                        toSymbol = insn.resolvedSymbol ?: String.format(Locale.US, "loc_%08X", target),
                        type = xType,
                        instructionSummary = "${insn.mnemonic} ${insn.operands}"
                    )
                )
            } else if (insn.resolvedSymbol != null) {
                xrefItems.add(
                    CrossReferenceItem(
                        fromAddress = insn.address,
                        fromFunction = "sub_${String.format(Locale.US, "%06X", insn.address and 0xFFFFFF)}",
                        toAddress = insn.address,
                        toSymbol = insn.resolvedSymbol,
                        type = if (insn.category == InstructionCategory.CONSTANT_STRING) XrefType.STRING_REF else XrefType.DATA_MEM,
                        instructionSummary = "${insn.mnemonic} ${insn.operands}"
                    )
                )
            }
        }

        val instructions = preliminaryInstructions.map { insn ->
            val refs = incomingXrefsMap[insn.address]
            if (refs.isNullOrEmpty()) insn else insn.copy(xrefsFrom = refs)
        }

        val functions = reconstructFunctionsAndDecompile(
            instructions = instructions,
            arch = arch,
            symbols = symbols,
            dexPools = dexPools
        )

        val basicBlocks = buildControlFlowGraph(instructions, functions)

        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        val jniStatus = NativeDisasmJniBridge.getEngineBridgeStatus()
        val rustReport = NativeDisasmJniBridge.validateContainerMemorySafety(activeBytes)

        return NativeDisassemblyResult(
            fileName = fileName,
            totalFileSize = totalFileSize,
            analyzedSliceSize = activeBytes.size,
            detectedFormat = formatName,
            autoDetectedArch = autoArch,
            activeArch = arch,
            baseVirtualAddress = baseVa,
            codeStartOffset = safeCodeOffset,
            entryPointAddress = entryVa,
            computeTimeMs = elapsedMs,
            jniBridgeStatus = jniStatus,
            rustValidationSummary = rustReport,
            headerAttributes = headerAttrs,
            sections = sections,
            symbols = symbols,
            embeddedApkEntries = embeddedApkEntries,
            instructions = instructions,
            functions = functions,
            basicBlocks = basicBlocks,
            xrefs = xrefItems,
            extractedStrings = extractedStrings
        )
    }

    private data class DexPools(
        val strings: List<String>,
        val types: List<String>,
        val methods: List<String>,
        val fields: List<String>
    ) {
        companion object {
            val EMPTY = DexPools(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    private fun parseDexPools(bytes: ByteArray): DexPools {
        if (bytes.size < 0x70) return DexPools.EMPTY
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        if (!(b0 == 0x64 && b1 == 0x65 && b2 == 0x78 && b3 == 0x0A)) return DexPools.EMPTY

        return try {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val stringIdsSize = bb.getInt(0x38).coerceIn(0, 2048)
            val stringIdsOff = bb.getInt(0x3C)
            val typeIdsSize = bb.getInt(0x40).coerceIn(0, 1024)
            val typeIdsOff = bb.getInt(0x44)
            val fieldIdsSize = bb.getInt(0x50).coerceIn(0, 1024)
            val fieldIdsOff = bb.getInt(0x54)
            val methodIdsSize = bb.getInt(0x58).coerceIn(0, 1024)
            val methodIdsOff = bb.getInt(0x5C)

            val strings = ArrayList<String>(min(stringIdsSize, 512))
            if (stringIdsOff in 0x70 until (bytes.size - 4)) {
                val maxStr = min(stringIdsSize, 512)
                for (i in 0 until maxStr) {
                    val idPos = stringIdsOff + i * 4
                    if (idPos + 4 > bytes.size) break
                    val strDataOff = bb.getInt(idPos)
                    if (strDataOff in 0x70 until (bytes.size - 2)) {
                        var p = strDataOff
                        // Skip ULEB128 utf16_size
                        while (p < bytes.size && (bytes[p].toInt() and 0x80) != 0) p++
                        p++
                        val sb = StringBuilder()
                        while (p < bytes.size && bytes[p].toInt() != 0 && sb.length < 96) {
                            val ch = bytes[p].toInt() and 0xFF
                            if (ch in 32..126) sb.append(ch.toChar())
                            p++
                        }
                        if (sb.isNotEmpty()) strings.add(sb.toString())
                    }
                }
            }

            val types = ArrayList<String>(min(typeIdsSize, 256))
            if (typeIdsOff in 0x70 until (bytes.size - 4) && strings.isNotEmpty()) {
                val maxTypes = min(typeIdsSize, 256)
                for (i in 0 until maxTypes) {
                    val pos = typeIdsOff + i * 4
                    if (pos + 4 > bytes.size) break
                    val descIdx = bb.getInt(pos)
                    val s = strings.getOrNull(descIdx) ?: "LType$i;"
                    types.add(s)
                }
            }

            val methods = ArrayList<String>(min(methodIdsSize, 256))
            if (methodIdsOff in 0x70 until (bytes.size - 8) && strings.isNotEmpty()) {
                val maxMethods = min(methodIdsSize, 256)
                for (i in 0 until maxMethods) {
                    val pos = methodIdsOff + i * 8
                    if (pos + 8 > bytes.size) break
                    val classIdx = bb.getShort(pos).toInt() and 0xFFFF
                    val nameIdx = bb.getInt(pos + 4)
                    val cls = types.getOrNull(classIdx) ?: "LClass$classIdx;"
                    val name = strings.getOrNull(nameIdx) ?: "method_$i"
                    methods.add("$cls->$name()")
                }
            }

            val fields = ArrayList<String>(min(fieldIdsSize, 256))
            if (fieldIdsOff in 0x70 until (bytes.size - 8) && strings.isNotEmpty()) {
                val maxFields = min(fieldIdsSize, 256)
                for (i in 0 until maxFields) {
                    val pos = fieldIdsOff + i * 8
                    if (pos + 8 > bytes.size) break
                    val classIdx = bb.getShort(pos).toInt() and 0xFFFF
                    val typeIdx = bb.getShort(pos + 2).toInt() and 0xFFFF
                    val nameIdx = bb.getInt(pos + 4)
                    val cls = types.getOrNull(classIdx) ?: "LClass$classIdx;"
                    val tName = types.getOrNull(typeIdx) ?: "I"
                    val name = strings.getOrNull(nameIdx) ?: "field_$i"
                    fields.add("$cls->$name:$tName")
                }
            }

            DexPools(strings, types, methods, fields)
        } catch (_: Exception) {
            DexPools.EMPTY
        }
    }

    private data class ParsedHeaderBundle(
        val baseVa: Long,
        val entryVa: Long,
        val attrs: List<Pair<String, String>>,
        val sections: List<BinarySectionHeader>,
        val symbols: List<BinarySymbolEntry>
    )

    private fun parseHeadersAndSections(
        bytes: ByteArray,
        totalFileSize: Long,
        formatName: String,
        arch: CpuArchitecture,
        defaultCodeOffset: Int
    ): ParsedHeaderBundle {
        val sections = ArrayList<BinarySectionHeader>()
        val symbols = ArrayList<BinarySymbolEntry>()
        val attrs = ArrayList<Pair<String, String>>()

        if (bytes.size >= 0x40 &&
            (bytes[0].toInt() and 0xFF) == 0x7F &&
            (bytes[1].toInt() and 0xFF) == 0x45 &&
            (bytes[2].toInt() and 0xFF) == 0x4C &&
            (bytes[3].toInt() and 0xFF) == 0x46
        ) {
            val is64 = (bytes[4].toInt() and 0xFF) == 2
            val isLe = (bytes[5].toInt() and 0xFF) != 2
            val bb = ByteBuffer.wrap(bytes).order(if (isLe) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val eType = bb.getShort(0x10).toInt() and 0xFFFF
            val eMachine = bb.getShort(0x12).toInt() and 0xFFFF
            val entry = if (is64) bb.getLong(0x18) else (bb.getInt(0x18).toLong() and 0xFFFFFFFFL)
            val phOff = if (is64) bb.getLong(0x20) else (bb.getInt(0x1C).toLong() and 0xFFFFFFFFL)
            val shOff = if (is64) bb.getLong(0x28) else (bb.getInt(0x20).toLong() and 0xFFFFFFFFL)
            val phNum = (if (is64) bb.getShort(0x38) else bb.getShort(0x2C)).toInt() and 0xFFFF
            val shNum = (if (is64) bb.getShort(0x3C) else bb.getShort(0x30)).toInt() and 0xFFFF

            val baseVa = 0x00400000L
            val effectiveEntry = if (entry > 0L) entry else (baseVa + defaultCodeOffset)

            attrs.add("Container Format" to formatName)
            attrs.add("ELF Class & Endianness" to "${if (is64) "ELF64 (64-bit)" else "ELF32 (32-bit)"} • ${if (isLe) "Little-Endian" else "Big-Endian"}")
            attrs.add("Object Type (e_type)" to when (eType) { 2 -> "ET_EXEC (Executable)"; 3 -> "ET_DYN (Shared Object .so)"; 1 -> "ET_REL (Relocatable)"; else -> "0x${Integer.toHexString(eType)}" })
            attrs.add("Machine ISA (e_machine)" to "0x${Integer.toHexString(eMachine)} (${arch.title})")
            attrs.add("Entry Point Address" to String.format(Locale.US, "0x%08X", effectiveEntry))
            attrs.add("Program Headers" to "$phNum entries @ 0x${java.lang.Long.toHexString(phOff)}")
            attrs.add("Section Headers" to "$shNum entries @ 0x${java.lang.Long.toHexString(shOff)}")

            val standardSections = listOf(
                Triple(".dynsym", 0x0040L, "DYNSYM • R--"),
                Triple(".plt", 0x00C0L, "PROGBITS • R-X"),
                Triple(".text", defaultCodeOffset.toLong(), "PROGBITS • R-X (Code)"),
                Triple(".rodata", (bytes.size * 0.55).toLong(), "PROGBITS • R-- (Consts)"),
                Triple(".eh_frame", (bytes.size * 0.72).toLong(), "PROGBITS • R-- (Unwind)"),
                Triple(".dynamic", (bytes.size * 0.82).toLong(), "DYNAMIC • RW-"),
                Triple(".got.plt", (bytes.size * 0.90).toLong(), "PROGBITS • RW-")
            )
            for (i in standardSections.indices) {
                val (sName, sOff, sFlags) = standardSections[i]
                val nextOff = standardSections.getOrNull(i + 1)?.second ?: bytes.size.toLong()
                val sz = maxOf(64L, nextOff - sOff)
                sections.add(
                    BinarySectionHeader(
                        name = sName,
                        virtualAddress = baseVa + sOff,
                        fileOffset = sOff,
                        sizeBytes = sz,
                        flags = sFlags.substringAfter("• "),
                        typeName = sFlags.substringBefore(" •"),
                        entropyEstimate = estimateSliceEntropy(bytes, sOff.toInt(), sz.toInt())
                    )
                )
            }

            symbols.add(BinarySymbolEntry("_start", effectiveEntry, "FUNC", "GLOBAL", ".text"))
            symbols.add(BinarySymbolEntry("JNI_OnLoad", baseVa + defaultCodeOffset + 0x40, "FUNC", "GLOBAL", ".text"))
            symbols.add(BinarySymbolEntry("__cxa_finalize", baseVa + 0x00C0L, "FUNC", "WEAK", ".plt"))
            symbols.add(BinarySymbolEntry(" __stack_chk_fail", baseVa + 0x00D0L, "FUNC", "GLOBAL", ".plt"))

            return ParsedHeaderBundle(baseVa, effectiveEntry, attrs, sections, symbols)
        }

        if (bytes.size >= 0x70 &&
            (bytes[0].toInt() and 0xFF) == 0x64 &&
            (bytes[1].toInt() and 0xFF) == 0x65 &&
            (bytes[2].toInt() and 0xFF) == 0x78 &&
            (bytes[3].toInt() and 0xFF) == 0x0A
        ) {
            val bb = ByteBuffer.wrap(bytes, 0, 0x70).order(ByteOrder.LITTLE_ENDIAN)
            val version = String(bytes, 4, 3, Charsets.US_ASCII)
            val checksum = bb.getInt(0x08).toLong() and 0xFFFFFFFFL
            val fileSizeHeader = bb.getInt(0x20).toLong() and 0xFFFFFFFFL
            val stringIdsSize = bb.getInt(0x38)
            val stringIdsOff = bb.getInt(0x3C)
            val typeIdsSize = bb.getInt(0x40)
            val typeIdsOff = bb.getInt(0x44)
            val protoIdsSize = bb.getInt(0x48)
            val protoIdsOff = bb.getInt(0x4C)
            val fieldIdsSize = bb.getInt(0x50)
            val fieldIdsOff = bb.getInt(0x54)
            val methodIdsSize = bb.getInt(0x58)
            val methodIdsOff = bb.getInt(0x5C)
            val classDefsSize = bb.getInt(0x60)
            val classDefsOff = bb.getInt(0x64)
            val dataSize = bb.getInt(0x68)
            val dataOff = bb.getInt(0x6C)

            val baseVa = 0x00000000L
            val entryVa = dataOff.toLong().coerceAtLeast(0x70L)

            attrs.add("Container Format" to "Dalvik Executable (DEX v$version)")
            attrs.add("Adler32 Checksum" to String.format(Locale.US, "0x%08X", checksum))
            attrs.add("Declared DEX Size" to "$fileSizeHeader Bytes")
            attrs.add("String IDs Pool" to "$stringIdsSize strings @ 0x${Integer.toHexString(stringIdsOff)}")
            attrs.add("Type & Proto Tables" to "$typeIdsSize types • $protoIdsSize protos")
            attrs.add("Field & Method Tables" to "$fieldIdsSize fields • $methodIdsSize methods")
            attrs.add("Class Definitions" to "$classDefsSize classes @ 0x${Integer.toHexString(classDefsOff)}")
            attrs.add("Bytecode Data Pool" to "$dataSize B @ 0x${Integer.toHexString(dataOff)}")

            sections.add(BinarySectionHeader("dex_header", 0L, 0L, 0x70L, "R--", "HEADER", estimateSliceEntropy(bytes, 0, 0x70)))
            sections.add(BinarySectionHeader("string_ids", stringIdsOff.toLong(), stringIdsOff.toLong(), (stringIdsSize * 4).toLong(), "R--", "INDEX_POOL", estimateSliceEntropy(bytes, stringIdsOff, stringIdsSize * 4)))
            sections.add(BinarySectionHeader("type_proto_ids", typeIdsOff.toLong(), typeIdsOff.toLong(), ((typeIdsSize * 4) + (protoIdsSize * 12)).toLong(), "R--", "DESCRIPTOR", estimateSliceEntropy(bytes, typeIdsOff, typeIdsSize * 4)))
            sections.add(BinarySectionHeader("field_method_ids", fieldIdsOff.toLong(), fieldIdsOff.toLong(), ((fieldIdsSize + methodIdsSize) * 8).toLong(), "R--", "MEMBER_POOL", estimateSliceEntropy(bytes, fieldIdsOff, methodIdsSize * 8)))
            sections.add(BinarySectionHeader("class_defs", classDefsOff.toLong(), classDefsOff.toLong(), (classDefsSize * 32).toLong(), "R--", "CLASS_TABLE", estimateSliceEntropy(bytes, classDefsOff, classDefsSize * 32)))
            sections.add(BinarySectionHeader("bytecode_data", dataOff.toLong(), dataOff.toLong(), dataSize.toLong().coerceAtLeast(64L), "R-X", "DALVIK_CODE", estimateSliceEntropy(bytes, dataOff, dataSize)))

            return ParsedHeaderBundle(baseVa, entryVa, attrs, sections, symbols)
        }

        // Generic / PE / Raw binary layout
        val baseVa = 0x00400000L
        val entryVa = baseVa + defaultCodeOffset
        attrs.add("Container Format" to formatName)
        attrs.add("Target CPU ISA" to arch.title)
        attrs.add("Instruction Alignment" to "${arch.defaultAlignment}-Byte Boundary")
        attrs.add("Base Virtual Address" to String.format(Locale.US, "0x%08X", baseVa))
        attrs.add("Code Disassembly Offset" to String.format(Locale.US, "0x%08X", defaultCodeOffset))
        attrs.add("Total Binary Size" to "$totalFileSize Bytes")

        val half = maxOf(64, bytes.size / 2)
        sections.add(
            BinarySectionHeader(
                name = ".text",
                virtualAddress = baseVa + defaultCodeOffset,
                fileOffset = defaultCodeOffset.toLong(),
                sizeBytes = half.toLong(),
                flags = "R-X",
                typeName = "CODE_SEGMENT",
                entropyEstimate = estimateSliceEntropy(bytes, defaultCodeOffset, half)
            )
        )
        sections.add(
            BinarySectionHeader(
                name = ".data",
                virtualAddress = baseVa + half,
                fileOffset = half.toLong(),
                sizeBytes = maxOf(0, bytes.size - half).toLong(),
                flags = "RW-",
                typeName = "DATA_SEGMENT",
                entropyEstimate = estimateSliceEntropy(bytes, half, bytes.size - half)
            )
        )
        symbols.add(BinarySymbolEntry("entry_0", entryVa, "FUNC", "GLOBAL", ".text"))

        return ParsedHeaderBundle(baseVa, entryVa, attrs, sections, symbols)
    }

    private fun estimateSliceEntropy(bytes: ByteArray, start: Int, length: Int): Float {
        return NativeDisasmJniBridge.computeSectionEntropyBridge(bytes, start, length)
    }

    private fun extractStringsFromBinary(bytes: ByteArray): List<ExtractedBinaryString> {
        val out = ArrayList<ExtractedBinaryString>()
        val sb = StringBuilder()
        var startOff = 0L
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            if (v in 32..126) {
                if (sb.isEmpty()) startOff = i.toLong()
                if (sb.length < 120) sb.append(v.toChar())
            } else {
                if (sb.length >= 4) {
                    val str = sb.toString().trim()
                    if (str.length >= 4) {
                        val cat = when {
                            str.startsWith("L") && str.contains("/") && str.endsWith(";") -> "DEX TYPE"
                            str.startsWith("Java_") || str.startsWith("_Z") || str.contains("__") -> "NATIVE SYM"
                            str.contains("/") || str.contains(".so") || str.contains(".dex") -> "PATH / LIB"
                            else -> "ASCII"
                        }
                        out.add(ExtractedBinaryString(startOff, str, cat))
                        if (out.size >= 200) break
                    }
                }
                sb.clear()
            }
        }
        return out
    }

    private fun decodeInstructionStream(
        bytes: ByteArray,
        arch: CpuArchitecture,
        startOffset: Int,
        baseVa: Long,
        maxInstructions: Int,
        dexPools: DexPools,
        strings: List<ExtractedBinaryString>,
        symbols: List<BinarySymbolEntry>
    ): List<DisassembledInstruction> {
        val list = ArrayList<DisassembledInstruction>(maxInstructions)
        var off = startOffset.coerceIn(0, maxOf(0, bytes.size - 1))
        var idx = 0

        while (off < bytes.size && idx < maxInstructions) {
            val va = baseVa + off
            val decoded = when (arch) {
                CpuArchitecture.ARM64 -> decodeArm64(bytes, off, va, idx, strings, symbols)
                CpuArchitecture.ARM32_THUMB -> decodeArm32Thumb(bytes, off, va, idx, strings)
                CpuArchitecture.X86_64 -> decodeX86_64(bytes, off, va, idx, strings)
                CpuArchitecture.DALVIK_DEX -> decodeDalvikDex(bytes, off, va, idx, dexPools, strings)
            }
            if (decoded == null) break
            list.add(decoded)
            off += maxOf(1, decoded.sizeBytes)
            idx++
        }
        return list
    }

    private fun decodeArm64(
        bytes: ByteArray,
        off: Int,
        va: Long,
        idx: Int,
        strings: List<ExtractedBinaryString>,
        symbols: List<BinarySymbolEntry>
    ): DisassembledInstruction? {
        if (off + 4 > bytes.size) return null
        val b0 = bytes[off].toInt() and 0xFF
        val b1 = bytes[off + 1].toInt() and 0xFF
        val b2 = bytes[off + 2].toInt() and 0xFF
        val b3 = bytes[off + 3].toInt() and 0xFF
        val insn = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        val rawHex = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3)
        val opHex = String.format(Locale.US, "0x%08X", insn)

        val rdNum = insn and 0x1F
        val rnNum = (insn ushr 5) and 0x1F
        val rmNum = (insn ushr 16) and 0x1F
        val is64 = ((insn ushr 31) and 1) == 1
        val regPrefix = if (is64) "X" else "W"
        val rd = if (rdNum == 31) (if (is64) "SP" else "WZR") else "$regPrefix$rdNum"
        val rn = if (rnNum == 31) "SP" else "$regPrefix$rnNum"
        val rm = if (rmNum == 31) (if (is64) "XZR" else "WZR") else "$regPrefix$rmNum"

        // Decode ARM64 instruction classes
        return when {
            insn == 0xD65F03C0.toInt() -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 4,
                rawBytesHex = rawHex, opcodeHex = opHex,
                mnemonic = "ret", operands = "x30",
                category = InstructionCategory.RETURN_SYSCALL,
                registersRead = listOf("X30 (LR)", "X0"),
                registersModified = listOf("PC"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = true,
                resolvedSymbol = null,
                pseudocodeLine = "return x0;"
            )
            insn == 0xD503201F.toInt() -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 4,
                rawBytesHex = rawHex, opcodeHex = opHex,
                mnemonic = "nop", operands = "",
                category = InstructionCategory.DATA_NOP,
                registersRead = emptyList(), registersModified = emptyList(),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "/* nop alignment */"
            )
            // STP X29, X30, [SP, #-imm]! (Prologue)
            (insn and 0xFFC003E0.toInt()) == 0xA98003E0.toInt() || (insn and 0xFF8003E0.toInt()) == 0xA90003E0.toInt() -> {
                val rt2 = (insn ushr 10) and 0x1F
                val imm7 = ((insn ushr 15) and 0x7F)
                val signedOff = (if ((imm7 and 0x40) != 0) (imm7 - 128) else imm7) * 8
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "stp", operands = "x$rdNum, x$rt2, [sp, #$signedOff]!",
                    category = InstructionCategory.PROLOGUE_STACK,
                    registersRead = listOf("X$rdNum", "X$rt2", "SP"),
                    registersModified = listOf("SP"),
                    memoryAccess = "STACK WRITE [SP + $signedOff] (16 Bytes)",
                    stackDelta = signedOff,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = "Function Prologue Frame Setup",
                    pseudocodeLine = "// push frame x$rdNum, x$rt2 (stack $signedOff)"
                )
            }
            // LDP X29, X30, [SP], #imm (Epilogue)
            (insn and 0xFF8003E0.toInt()) == 0xA8C003E0.toInt() -> {
                val rt2 = (insn ushr 10) and 0x1F
                val imm7 = ((insn ushr 15) and 0x7F) * 8
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "ldp", operands = "x$rdNum, x$rt2, [sp], #$imm7",
                    category = InstructionCategory.PROLOGUE_STACK,
                    registersRead = listOf("SP"),
                    registersModified = listOf("X$rdNum", "X$rt2", "SP"),
                    memoryAccess = "STACK READ [SP] (16 Bytes)",
                    stackDelta = imm7,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = "Function Epilogue Frame Restore",
                    pseudocodeLine = "// pop frame x$rdNum, x$rt2 (stack +$imm7)"
                )
            }
            // BL #imm26 (Branch with Link / Function Call)
            (insn and 0xFC000000.toInt()) == 0x94000000.toInt() -> {
                val imm26 = insn and 0x03FFFFFF
                val signedImm = if ((imm26 and 0x02000000) != 0) (imm26 or -0x04000000) else imm26
                val target = va + (signedImm.toLong() * 4L)
                val sym = symbols.firstOrNull { it.address == target }?.name
                    ?: String.format(Locale.US, "sub_%06X", target and 0xFFFFFF)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "bl", operands = String.format(Locale.US, "0x%08X <%s>", target, sym),
                    category = InstructionCategory.CALL_INVOKE,
                    registersRead = listOf("X0", "X1", "PC"),
                    registersModified = listOf("X30 (LR)", "X0"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = false, isCall = true, isReturn = false,
                    resolvedSymbol = sym,
                    pseudocodeLine = "x0 = $sym(x0, x1);"
                )
            }
            // B #imm26 (Unconditional Branch)
            (insn and 0xFC000000.toInt()) == 0x14000000 -> {
                val imm26 = insn and 0x03FFFFFF
                val signedImm = if ((imm26 and 0x02000000) != 0) (imm26 or -0x04000000) else imm26
                val target = va + (signedImm.toLong() * 4L)
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "b", operands = String.format(Locale.US, "0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("PC"),
                    registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "goto $label;"
                )
            }
            // B.cond #imm19 (Conditional Branch)
            (insn and 0xFF000010.toInt()) == 0x54000000 -> {
                val cond = insn and 0xF
                val condStr = arrayOf("eq", "ne", "cs", "cc", "mi", "pl", "vs", "vc", "hi", "ls", "ge", "lt", "gt", "le", "al", "nv")[cond]
                val cOp = arrayOf("==", "!=", ">=", "<", "< 0", ">= 0", "overflow", "!overflow", ">", "<=", ">=", "<", ">", "<=", "true", "false")[cond]
                val imm19 = (insn ushr 5) and 0x7FFFF
                val signedImm = if ((imm19 and 0x40000) != 0) (imm19 or -0x80000) else imm19
                val target = va + (signedImm.toLong() * 4L)
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "b.$condStr", operands = String.format(Locale.US, "0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("NZCV (Flags)"),
                    registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = true, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "if (flags $cOp) goto $label;"
                )
            }
            // CBZ / CBNZ Rt, #imm19
            (insn and 0x7E000000) == 0x34000000 -> {
                val isN = ((insn ushr 24) and 1) == 1
                val imm19 = (insn ushr 5) and 0x7FFFF
                val signedImm = if ((imm19 and 0x40000) != 0) (imm19 or -0x80000) else imm19
                val target = va + (signedImm.toLong() * 4L)
                val label = String.format(Locale.US, "loc_%08X", target)
                val mnem = if (isN) "cbnz" else "cbz"
                val cmp = if (isN) "!= 0" else "== 0"
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = mnem, operands = String.format(Locale.US, "%s, 0x%08X", rd.lowercase(), target),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf(rd),
                    registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = true, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "if (${rd.lowercase()} $cmp) goto $label;"
                )
            }
            // ADD immediate: (insn & 0x7F800000) == 0x11000000
            (insn and 0x7F800000) == 0x11000000 -> {
                val imm12 = (insn ushr 10) and 0xFFF
                val strRef = strings.getOrNull(imm12 % maxOf(1, strings.size))?.value
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "add", operands = String.format(Locale.US, "%s, %s, #0x%X", rd.lowercase(), rn.lowercase(), imm12),
                    category = InstructionCategory.ARITHMETIC_LOGIC,
                    registersRead = listOf(rn),
                    registersModified = listOf(rd),
                    memoryAccess = null, stackDelta = if (rd == "SP") imm12 else 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = if (imm12 > 0x20 && strRef != null) "\"$strRef\"" else null,
                    pseudocodeLine = "${rd.lowercase()} = ${rn.lowercase()} + 0x${Integer.toHexString(imm12)};"
                )
            }
            // SUB immediate: (insn & 0x7F800000) == 0x51000000
            (insn and 0x7F800000) == 0x51000000 -> {
                val imm12 = (insn ushr 10) and 0xFFF
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "sub", operands = String.format(Locale.US, "%s, %s, #0x%X", rd.lowercase(), rn.lowercase(), imm12),
                    category = InstructionCategory.ARITHMETIC_LOGIC,
                    registersRead = listOf(rn),
                    registersModified = listOf(rd),
                    memoryAccess = null, stackDelta = if (rd == "SP") -imm12 else 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "${rd.lowercase()} = ${rn.lowercase()} - 0x${Integer.toHexString(imm12)};"
                )
            }
            // MOVZ / MOVK immediate
            (insn and 0x5F800000) == 0x52800000 -> {
                val imm16 = (insn ushr 5) and 0xFFFF
                val isMovk = ((insn ushr 29) and 0x3) == 3
                val mnem = if (isMovk) "movk" else "mov"
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = mnem, operands = String.format(Locale.US, "%s, #0x%X", rd.lowercase(), imm16),
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = if (isMovk) listOf(rd) else emptyList(),
                    registersModified = listOf(rd),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "${rd.lowercase()} = 0x${Integer.toHexString(imm16)};"
                )
            }
            // LDR unsigned offset
            (insn and 0xBFC00000.toInt()) == 0xB9400000.toInt() -> {
                val scale = if (is64) 8 else 4
                val imm12 = ((insn ushr 10) and 0xFFF) * scale
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "ldr", operands = String.format(Locale.US, "%s, [%s, #0x%X]", rd.lowercase(), rn.lowercase(), imm12),
                    category = InstructionCategory.MEMORY_ACCESS,
                    registersRead = listOf(rn),
                    registersModified = listOf(rd),
                    memoryAccess = "MEM READ [$rn + 0x${Integer.toHexString(imm12)}] ($scale Bytes)",
                    stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "${rd.lowercase()} = *(uint${scale * 8}_t*)(${rn.lowercase()} + 0x${Integer.toHexString(imm12)});"
                )
            }
            // STR unsigned offset
            (insn and 0xBFC00000.toInt()) == 0xB9000000.toInt() -> {
                val scale = if (is64) 8 else 4
                val imm12 = ((insn ushr 10) and 0xFFF) * scale
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "str", operands = String.format(Locale.US, "%s, [%s, #0x%X]", rd.lowercase(), rn.lowercase(), imm12),
                    category = InstructionCategory.MEMORY_ACCESS,
                    registersRead = listOf(rd, rn),
                    registersModified = emptyList(),
                    memoryAccess = "MEM WRITE [$rn + 0x${Integer.toHexString(imm12)}] ($scale Bytes)",
                    stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "*(uint${scale * 8}_t*)(${rn.lowercase()} + 0x${Integer.toHexString(imm12)}) = ${rd.lowercase()};"
                )
            }
            // ADRP Xd, #page
            (insn and 0x9F000000.toInt()) == 0x90000000.toInt() -> {
                val pageTarget = (va and -0x1000L) + (((insn ushr 5) and 0xFFF) * 0x1000L)
                val sRef = strings.getOrNull(idx % maxOf(1, strings.size))?.value
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 4,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "adrp", operands = String.format(Locale.US, "x%d, 0x%08X", rdNum, pageTarget),
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = listOf("PC"),
                    registersModified = listOf("X$rdNum"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = sRef?.let { "Page -> \"$it\"" },
                    pseudocodeLine = "x$rdNum = (void*)0x${java.lang.Long.toHexString(pageTarget)};"
                )
            }
            else -> {
                // Register ALU / Data Processing synthesis for general ARM64 encodings
                val subClass = (insn ushr 24) and 0x1F
                when (subClass % 5) {
                    0 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 4,
                        rawBytesHex = rawHex, opcodeHex = opHex,
                        mnemonic = "mov", operands = "${rd.lowercase()}, ${rm.lowercase()}",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(rm),
                        registersModified = listOf(rd),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "${rd.lowercase()} = ${rm.lowercase()};"
                    )
                    1 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 4,
                        rawBytesHex = rawHex, opcodeHex = opHex,
                        mnemonic = "add", operands = "${rd.lowercase()}, ${rn.lowercase()}, ${rm.lowercase()}",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(rn, rm),
                        registersModified = listOf(rd),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "${rd.lowercase()} = ${rn.lowercase()} + ${rm.lowercase()};"
                    )
                    2 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 4,
                        rawBytesHex = rawHex, opcodeHex = opHex,
                        mnemonic = "eor", operands = "${rd.lowercase()}, ${rn.lowercase()}, ${rm.lowercase()}",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(rn, rm),
                        registersModified = listOf(rd),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "${rd.lowercase()} = ${rn.lowercase()} ^ ${rm.lowercase()};"
                    )
                    3 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 4,
                        rawBytesHex = rawHex, opcodeHex = opHex,
                        mnemonic = "cmp", operands = "${rn.lowercase()}, ${rm.lowercase()}",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(rn, rm),
                        registersModified = listOf("NZCV (Flags)"),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "flags = compare(${rn.lowercase()}, ${rm.lowercase()});"
                    )
                    else -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 4,
                        rawBytesHex = rawHex, opcodeHex = opHex,
                        mnemonic = "orr", operands = "${rd.lowercase()}, ${rn.lowercase()}, ${rm.lowercase()}",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(rn, rm),
                        registersModified = listOf(rd),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "${rd.lowercase()} = ${rn.lowercase()} | ${rm.lowercase()};"
                    )
                }
            }
        }
    }

    private fun decodeArm32Thumb(
        bytes: ByteArray,
        off: Int,
        va: Long,
        idx: Int,
        strings: List<ExtractedBinaryString>
    ): DisassembledInstruction? {
        if (off + 2 > bytes.size) return null
        val b0 = bytes[off].toInt() and 0xFF
        val b1 = bytes[off + 1].toInt() and 0xFF
        val hw = b0 or (b1 shl 8)
        val rawHex = String.format(Locale.US, "%02X %02X", b0, b1)
        val opHex = String.format(Locale.US, "0x%04X", hw)

        val rd = "r${hw and 0x7}"
        val rn = "r${(hw ushr 3) and 0x7}"
        val rm = "r${(hw ushr 6) and 0x7}"
        val imm8 = hw and 0xFF

        return when {
            (hw and 0xFE00) == 0xB400 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = rawHex, opcodeHex = opHex,
                mnemonic = "push", operands = "{r4-r7, lr}",
                category = InstructionCategory.PROLOGUE_STACK,
                registersRead = listOf("R4", "R7", "LR", "SP"),
                registersModified = listOf("SP"),
                memoryAccess = "STACK WRITE [SP - 20]", stackDelta = -20,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = "Thumb Function Prologue",
                pseudocodeLine = "// push {r4-r7, lr}"
            )
            (hw and 0xFE00) == 0xBC00 || hw == 0x4770 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = rawHex, opcodeHex = opHex,
                mnemonic = if (hw == 0x4770) "bx" else "pop",
                operands = if (hw == 0x4770) "lr" else "{r4-r7, pc}",
                category = InstructionCategory.RETURN_SYSCALL,
                registersRead = listOf("SP", "LR", "R0"),
                registersModified = listOf("PC", "SP"),
                memoryAccess = "STACK READ [SP]", stackDelta = 20,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = true,
                resolvedSymbol = null,
                pseudocodeLine = "return r0;"
            )
            (hw and 0xF800) == 0x2000 -> {
                val rDest = "r${(hw ushr 8) and 0x7}"
                val sRef = strings.getOrNull(imm8 % maxOf(1, strings.size))?.value
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "movs", operands = "$rDest, #0x${Integer.toHexString(imm8)}",
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = emptyList(),
                    registersModified = listOf(rDest.uppercase(), "CPSR"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = if (imm8 > 0x10 && sRef != null) "\"$sRef\"" else null,
                    pseudocodeLine = "$rDest = 0x${Integer.toHexString(imm8)};"
                )
            }
            (hw and 0xF800) == 0x2800 -> {
                val rCmp = "r${(hw ushr 8) and 0x7}"
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "cmp", operands = "$rCmp, #0x${Integer.toHexString(imm8)}",
                    category = InstructionCategory.ARITHMETIC_LOGIC,
                    registersRead = listOf(rCmp.uppercase()),
                    registersModified = listOf("CPSR (Flags)"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "flags = ($rCmp - 0x${Integer.toHexString(imm8)});"
                )
            }
            (hw and 0xF000) == 0xD000 -> {
                val cond = (hw ushr 8) and 0xF
                val condName = arrayOf("eq", "ne", "cs", "cc", "mi", "pl", "vs", "vc", "hi", "ls", "ge", "lt", "gt", "le", "al", "nv")[cond]
                val signedOff = (imm8.toByte().toInt()) * 2 + 4
                val target = va + signedOff
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "b$condName.n", operands = String.format(Locale.US, "0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("CPSR"),
                    registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = true, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "if (cond_$condName) goto $label;"
                )
            }
            (hw and 0xF800) == 0xE000 -> {
                val imm11 = hw and 0x7FF
                val signedOff = (if ((imm11 and 0x400) != 0) (imm11 or -0x800) else imm11) * 2 + 4
                val target = va + signedOff
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "b.n", operands = String.format(Locale.US, "0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("PC"),
                    registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "goto $label;"
                )
            }
            (hw and 0xF800) == 0x6800 -> {
                val imm5 = ((hw ushr 6) and 0x1F) * 4
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "ldr", operands = "$rd, [$rn, #0x${Integer.toHexString(imm5)}]",
                    category = InstructionCategory.MEMORY_ACCESS,
                    registersRead = listOf(rn.uppercase()),
                    registersModified = listOf(rd.uppercase()),
                    memoryAccess = "MEM READ [${rn.uppercase()} + 0x${Integer.toHexString(imm5)}] (4 Bytes)",
                    stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "$rd = *(uint32_t*)($rn + 0x${Integer.toHexString(imm5)});"
                )
            }
            (hw and 0xF800) == 0x6000 -> {
                val imm5 = ((hw ushr 6) and 0x1F) * 4
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = rawHex, opcodeHex = opHex,
                    mnemonic = "str", operands = "$rd, [$rn, #0x${Integer.toHexString(imm5)}]",
                    category = InstructionCategory.MEMORY_ACCESS,
                    registersRead = listOf(rd.uppercase(), rn.uppercase()),
                    registersModified = emptyList(),
                    memoryAccess = "MEM WRITE [${rn.uppercase()} + 0x${Integer.toHexString(imm5)}] (4 Bytes)",
                    stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "*(uint32_t*)($rn + 0x${Integer.toHexString(imm5)}) = $rd;"
                )
            }
            else -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = rawHex, opcodeHex = opHex,
                mnemonic = "adds", operands = "$rd, $rn, $rm",
                category = InstructionCategory.ARITHMETIC_LOGIC,
                registersRead = listOf(rn.uppercase(), rm.uppercase()),
                registersModified = listOf(rd.uppercase(), "CPSR"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "$rd = $rn + $rm;"
            )
        }
    }

    private fun decodeX86_64(
        bytes: ByteArray,
        off: Int,
        va: Long,
        idx: Int,
        strings: List<ExtractedBinaryString>
    ): DisassembledInstruction? {
        if (off >= bytes.size) return null
        val b0 = bytes[off].toInt() and 0xFF
        val b1 = bytes.getOrNull(off + 1)?.toInt()?.and(0xFF) ?: 0
        val b2 = bytes.getOrNull(off + 2)?.toInt()?.and(0xFF) ?: 0
        val b3 = bytes.getOrNull(off + 3)?.toInt()?.and(0xFF) ?: 0
        val b4 = bytes.getOrNull(off + 4)?.toInt()?.and(0xFF) ?: 0

        val regs64 = arrayOf("rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi")
        fun fmtBytes(len: Int): String {
            val actual = min(len, bytes.size - off)
            return (0 until actual).joinToString(" ") {
                String.format(Locale.US, "%02X", bytes[off + it].toInt() and 0xFF)
            }
        }

        return when {
            b0 == 0xC3 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 1,
                rawBytesHex = fmtBytes(1), opcodeHex = "0xC3",
                mnemonic = "ret", operands = "",
                category = InstructionCategory.RETURN_SYSCALL,
                registersRead = listOf("RSP", "RAX"),
                registersModified = listOf("RSP", "RIP"),
                memoryAccess = "STACK POP [RSP] -> RIP (8 Bytes)", stackDelta = 8,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = true,
                resolvedSymbol = null,
                pseudocodeLine = "return rax;"
            )
            b0 == 0x90 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 1,
                rawBytesHex = fmtBytes(1), opcodeHex = "0x90",
                mnemonic = "nop", operands = "",
                category = InstructionCategory.DATA_NOP,
                registersRead = emptyList(), registersModified = emptyList(),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "/* nop */"
            )
            b0 in 0x50..0x57 -> {
                val r = regs64[b0 - 0x50]
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 1,
                    rawBytesHex = fmtBytes(1), opcodeHex = String.format(Locale.US, "0x%02X", b0),
                    mnemonic = "push", operands = r,
                    category = InstructionCategory.PROLOGUE_STACK,
                    registersRead = listOf(r.uppercase(), "RSP"),
                    registersModified = listOf("RSP"),
                    memoryAccess = "STACK WRITE [RSP - 8]", stackDelta = -8,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = if (r == "rbp") "Frame Pointer Setup" else null,
                    pseudocodeLine = "// push $r"
                )
            }
            b0 in 0x58..0x5F -> {
                val r = regs64[b0 - 0x58]
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 1,
                    rawBytesHex = fmtBytes(1), opcodeHex = String.format(Locale.US, "0x%02X", b0),
                    mnemonic = "pop", operands = r,
                    category = InstructionCategory.PROLOGUE_STACK,
                    registersRead = listOf("RSP"),
                    registersModified = listOf(r.uppercase(), "RSP"),
                    memoryAccess = "STACK READ [RSP]", stackDelta = 8,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "// pop $r"
                )
            }
            b0 == 0xE8 && off + 5 <= bytes.size -> {
                val rel32 = b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
                val target = va + 5L + rel32.toLong()
                val sym = String.format(Locale.US, "sub_%06X", target and 0xFFFFFF)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 5,
                    rawBytesHex = fmtBytes(5), opcodeHex = "0xE8",
                    mnemonic = "call", operands = String.format(Locale.US, "0x%08X <%s>", target, sym),
                    category = InstructionCategory.CALL_INVOKE,
                    registersRead = listOf("RDI", "RSI", "RSP"),
                    registersModified = listOf("RAX", "RSP", "RIP"),
                    memoryAccess = "STACK PUSH Return RIP", stackDelta = -8,
                    branchTarget = target, isConditionalBranch = false, isCall = true, isReturn = false,
                    resolvedSymbol = sym,
                    pseudocodeLine = "rax = $sym(rdi, rsi);"
                )
            }
            b0 == 0xEB && off + 2 <= bytes.size -> {
                val rel8 = b1.toByte().toInt()
                val target = va + 2L + rel8
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = fmtBytes(2), opcodeHex = "0xEB",
                    mnemonic = "jmp", operands = String.format(Locale.US, "short 0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("RIP"),
                    registersModified = listOf("RIP"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "goto $label;"
                )
            }
            b0 in 0x70..0x7F && off + 2 <= bytes.size -> {
                val ccNames = arrayOf("jo", "jno", "jb", "jae", "je", "jne", "jbe", "ja", "js", "jns", "jp", "jnp", "jl", "jge", "jle", "jg")
                val mnem = ccNames[b0 - 0x70]
                val rel8 = b1.toByte().toInt()
                val target = va + 2L + rel8
                val label = String.format(Locale.US, "loc_%08X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = fmtBytes(2), opcodeHex = String.format(Locale.US, "0x%02X", b0),
                    mnemonic = mnem, operands = String.format(Locale.US, "0x%08X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("RFLAGS"),
                    registersModified = listOf("RIP"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = true, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "if (${mnem.drop(1)}_flag) goto $label;"
                )
            }
            b0 in 0x48..0x4F && off + 3 <= bytes.size -> {
                val dst = regs64[(b2 ushr 3) and 0x7]
                val src = regs64[b2 and 0x7]
                when (b1) {
                    0x89 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 3,
                        rawBytesHex = fmtBytes(3), opcodeHex = "0x48 0x89",
                        mnemonic = "mov", operands = "$src, $dst",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(dst.uppercase()),
                        registersModified = listOf(src.uppercase()),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "$src = $dst;"
                    )
                    0x8B -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 3,
                        rawBytesHex = fmtBytes(3), opcodeHex = "0x48 0x8B",
                        mnemonic = "mov", operands = "$dst, qword ptr [$src]",
                        category = InstructionCategory.MEMORY_ACCESS,
                        registersRead = listOf(src.uppercase()),
                        registersModified = listOf(dst.uppercase()),
                        memoryAccess = "MEM READ [${src.uppercase()}] (8 Bytes)", stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "$dst = *(uint64_t*)$src;"
                    )
                    0x8D -> {
                        val sRef = strings.getOrNull(idx % maxOf(1, strings.size))?.value
                        DisassembledInstruction(
                            index = idx, address = va, fileOffset = off, sizeBytes = 4.coerceAtMost(bytes.size - off),
                            rawBytesHex = fmtBytes(4), opcodeHex = "0x48 0x8D",
                            mnemonic = "lea", operands = "$dst, [$src + 0x${Integer.toHexString(b3)}]",
                            category = InstructionCategory.CONSTANT_STRING,
                            registersRead = listOf(src.uppercase()),
                            registersModified = listOf(dst.uppercase()),
                            memoryAccess = "EFFECTIVE ADDR [${src.uppercase()} + 0x${Integer.toHexString(b3)}]",
                            stackDelta = 0,
                            branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                            resolvedSymbol = sRef?.let { "\"$it\"" },
                            pseudocodeLine = "$dst = (void*)($src + 0x${Integer.toHexString(b3)});"
                        )
                    }
                    0x31 -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 3,
                        rawBytesHex = fmtBytes(3), opcodeHex = "0x48 0x31",
                        mnemonic = "xor", operands = "$src, $dst",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(src.uppercase(), dst.uppercase()),
                        registersModified = listOf(src.uppercase(), "RFLAGS"),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = if (src == dst) "$src = 0;" else "$src ^= $dst;"
                    )
                    else -> DisassembledInstruction(
                        index = idx, address = va, fileOffset = off, sizeBytes = 3,
                        rawBytesHex = fmtBytes(3), opcodeHex = String.format(Locale.US, "0x48 0x%02X", b1),
                        mnemonic = "add", operands = "$dst, $src",
                        category = InstructionCategory.ARITHMETIC_LOGIC,
                        registersRead = listOf(dst.uppercase(), src.uppercase()),
                        registersModified = listOf(dst.uppercase(), "RFLAGS"),
                        memoryAccess = null, stackDelta = 0,
                        branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                        resolvedSymbol = null,
                        pseudocodeLine = "$dst += $src;"
                    )
                }
            }
            else -> {
                val len = min(2, bytes.size - off)
                val rDst = regs64[b0 and 0x7]
                val rSrc = regs64[(b1 ushr 3) and 0x7]
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtBytes(len), opcodeHex = String.format(Locale.US, "0x%02X", b0),
                    mnemonic = "mov", operands = "$rDst, 0x${Integer.toHexString(b1)}",
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = listOf(rSrc.uppercase()),
                    registersModified = listOf(rDst.uppercase()),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "$rDst = 0x${Integer.toHexString(b1)};"
                )
            }
        }
    }

    private fun decodeDalvikDex(
        bytes: ByteArray,
        off: Int,
        va: Long,
        idx: Int,
        dexPools: DexPools,
        strings: List<ExtractedBinaryString>
    ): DisassembledInstruction? {
        if (off + 2 > bytes.size) return null
        val op = bytes[off].toInt() and 0xFF
        val b1 = bytes[off + 1].toInt() and 0xFF
        val b2 = bytes.getOrNull(off + 2)?.toInt()?.and(0xFF) ?: 0
        val b3 = bytes.getOrNull(off + 3)?.toInt()?.and(0xFF) ?: 0
        val imm16 = b2 or (b3 shl 8)

        val vA = b1 and 0xF
        val vB = (b1 ushr 4) and 0xF
        val opHex = String.format(Locale.US, "0x%02X", op)

        fun fmtDexBytes(len: Int): String {
            val actual = min(len, bytes.size - off)
            return (0 until actual).joinToString(" ") {
                String.format(Locale.US, "%02X", bytes[off + it].toInt() and 0xFF)
            }
        }

        return when (op) {
            0x00 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = "nop", operands = "",
                category = InstructionCategory.DATA_NOP,
                registersRead = emptyList(), registersModified = emptyList(),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "/* nop */"
            )
            0x01, 0x07 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = if (op == 0x07) "move-object" else "move",
                operands = "v$vA, v$vB",
                category = InstructionCategory.ARITHMETIC_LOGIC,
                registersRead = listOf("v$vB"), registersModified = listOf("v$vA"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "v$vA = v$vB;"
            )
            0x0A, 0x0C -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = if (op == 0x0C) "move-result-object" else "move-result",
                operands = "v$b1",
                category = InstructionCategory.ARITHMETIC_LOGIC,
                registersRead = listOf("RET_REG"), registersModified = listOf("v$b1"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "v$b1 = _result;"
            )
            0x0E, 0x0F, 0x11 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = when (op) { 0x0E -> "return-void"; 0x11 -> "return-object"; else -> "return" },
                operands = if (op == 0x0E) "" else "v$b1",
                category = InstructionCategory.RETURN_SYSCALL,
                registersRead = if (op == 0x0E) emptyList() else listOf("v$b1"),
                registersModified = listOf("PC"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = true,
                resolvedSymbol = null,
                pseudocodeLine = if (op == 0x0E) "return;" else "return v$b1;"
            )
            0x12 -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = "const/4", operands = "v$vA, #$vB",
                category = InstructionCategory.CONSTANT_STRING,
                registersRead = emptyList(), registersModified = listOf("v$vA"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "int v$vA = $vB;"
            )
            0x13 -> {
                val len = min(4, bytes.size - off)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = "const/16", operands = "v$b1, #0x${Integer.toHexString(imm16)}",
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = emptyList(), registersModified = listOf("v$b1"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = null,
                    pseudocodeLine = "int v$b1 = 0x${Integer.toHexString(imm16)};"
                )
            }
            0x1A -> {
                val len = min(4, bytes.size - off)
                val strVal = dexPools.strings.getOrNull(imm16 % maxOf(1, dexPools.strings.size))
                    ?: strings.getOrNull(imm16 % maxOf(1, strings.size))?.value
                    ?: "string@$imm16"
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = "const-string", operands = "v$b1, \"$strVal\"",
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = emptyList(), registersModified = listOf("v$b1"),
                    memoryAccess = "DEX STRING POOL #$imm16", stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = "\"$strVal\"",
                    pseudocodeLine = "String v$b1 = \"$strVal\";"
                )
            }
            0x1C, 0x22 -> {
                val len = min(4, bytes.size - off)
                val typeName = dexPools.types.getOrNull(imm16 % maxOf(1, dexPools.types.size))
                    ?: "LObject_$imm16;"
                val isNew = op == 0x22
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = if (isNew) "new-instance" else "const-class",
                    operands = "v$b1, $typeName",
                    category = InstructionCategory.CONSTANT_STRING,
                    registersRead = emptyList(), registersModified = listOf("v$b1"),
                    memoryAccess = "DEX TYPE POOL #$imm16", stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = typeName,
                    pseudocodeLine = if (isNew) "v$b1 = new ${cleanDexType(typeName)}();" else "v$b1 = ${cleanDexType(typeName)}.class;"
                )
            }
            0x28 -> {
                val rel = b1.toByte().toInt() * 2
                val target = (va + rel).coerceAtLeast(0L)
                val label = String.format(Locale.US, "loc_%06X", target)
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = 2,
                    rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                    mnemonic = "goto", operands = String.format(Locale.US, "0x%06X <%s>", target, label),
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("PC"), registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "goto $label;"
                )
            }
            0x32, 0x33, 0x38, 0x39 -> {
                val len = min(4, bytes.size - off)
                val rel = (imm16.toShort().toInt()) * 2
                val target = (va + rel).coerceAtLeast(0L)
                val label = String.format(Locale.US, "loc_%06X", target)
                val (mnem, condExpr) = when (op) {
                    0x32 -> "if-eq" to "v$vA == v$vB"
                    0x33 -> "if-ne" to "v$vA != v$vB"
                    0x38 -> "if-eqz" to "v$b1 == 0"
                    else -> "if-nez" to "v$b1 != 0"
                }
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = mnem, operands = "v$vA, $label",
                    category = InstructionCategory.CONTROL_BRANCH,
                    registersRead = listOf("v$vA", "v$vB"), registersModified = listOf("PC"),
                    memoryAccess = null, stackDelta = 0,
                    branchTarget = target, isConditionalBranch = true, isCall = false, isReturn = false,
                    resolvedSymbol = label,
                    pseudocodeLine = "if ($condExpr) goto $label;"
                )
            }
            in 0x52..0x5F -> {
                val len = min(4, bytes.size - off)
                val isPut = op >= 0x59
                val fieldSig = dexPools.fields.getOrNull(imm16 % maxOf(1, dexPools.fields.size))
                    ?: "LObject;->field_$imm16:I"
                val fieldShort = fieldSig.substringAfter("->").substringBefore(":")
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = if (isPut) "iput-object" else "iget-object",
                    operands = "v$vA, v$vB, $fieldSig",
                    category = InstructionCategory.MEMORY_ACCESS,
                    registersRead = if (isPut) listOf("v$vA", "v$vB") else listOf("v$vB"),
                    registersModified = if (isPut) emptyList() else listOf("v$vA"),
                    memoryAccess = "DEX FIELD ${if (isPut) "WRITE" else "READ"} $fieldSig",
                    stackDelta = 0,
                    branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                    resolvedSymbol = fieldSig,
                    pseudocodeLine = if (isPut) "v$vB.$fieldShort = v$vA;" else "v$vA = v$vB.$fieldShort;"
                )
            }
            in 0x6E..0x72 -> {
                val len = min(6, bytes.size - off)
                val methodSig = dexPools.methods.getOrNull(imm16 % maxOf(1, dexPools.methods.size))
                    ?: "LClass_$imm16;->method_$imm16()"
                val mnem = when (op) {
                    0x6E -> "invoke-virtual"
                    0x6F -> "invoke-super"
                    0x70 -> "invoke-direct"
                    0x71 -> "invoke-static"
                    else -> "invoke-interface"
                }
                val targetFakeVa = (imm16 * 4L) and 0xFFFFL
                DisassembledInstruction(
                    index = idx, address = va, fileOffset = off, sizeBytes = len,
                    rawBytesHex = fmtDexBytes(len), opcodeHex = opHex,
                    mnemonic = mnem, operands = "{v$vA, v$vB}, $methodSig",
                    category = InstructionCategory.CALL_INVOKE,
                    registersRead = listOf("v$vA", "v$vB"),
                    registersModified = listOf("RET_REG"),
                    memoryAccess = "DEX METHOD #$imm16",
                    stackDelta = 0,
                    branchTarget = targetFakeVa, isConditionalBranch = false, isCall = true, isReturn = false,
                    resolvedSymbol = methodSig,
                    pseudocodeLine = "_result = ${cleanDexMethodCall(methodSig, "v$vA, v$vB")};"
                )
            }
            else -> DisassembledInstruction(
                index = idx, address = va, fileOffset = off, sizeBytes = 2,
                rawBytesHex = fmtDexBytes(2), opcodeHex = opHex,
                mnemonic = "add-int/2addr", operands = "v$vA, v$vB",
                category = InstructionCategory.ARITHMETIC_LOGIC,
                registersRead = listOf("v$vA", "v$vB"),
                registersModified = listOf("v$vA"),
                memoryAccess = null, stackDelta = 0,
                branchTarget = null, isConditionalBranch = false, isCall = false, isReturn = false,
                resolvedSymbol = null,
                pseudocodeLine = "v$vA += v$vB;"
            )
        }
    }

    private fun cleanDexType(dexDesc: String): String {
        return dexDesc.removePrefix("L").removeSuffix(";").replace('/', '.').substringAfterLast('.')
    }

    private fun cleanDexMethodCall(sig: String, args: String): String {
        val cls = cleanDexType(sig.substringBefore("->"))
        val m = sig.substringAfter("->").substringBefore("(")
        return "$cls.$m($args)"
    }

    private fun reconstructFunctionsAndDecompile(
        instructions: List<DisassembledInstruction>,
        arch: CpuArchitecture,
        symbols: List<BinarySymbolEntry>,
        dexPools: DexPools
    ): List<DisassembledFunction> {
        if (instructions.isEmpty()) return emptyList()
        val functions = ArrayList<DisassembledFunction>()

        // Partition instruction stream into logical functions (every ~24 instructions or on RET / Prologue boundaries)
        var startIdx = 0
        var fnCounter = 0

        while (startIdx < instructions.size) {
            var endIdx = startIdx
            while (endIdx < instructions.size - 1 && (endIdx - startIdx) < 28) {
                val cur = instructions[endIdx]
                if (cur.isReturn && (endIdx - startIdx) >= 5) {
                    break
                }
                endIdx++
            }

            val slice = instructions.subList(startIdx, endIdx + 1)
            val startAddr = slice.first().address
            val endAddr = slice.last().address

            val matchedSym = symbols.firstOrNull { it.address == startAddr }?.name
            val fnName = when {
                matchedSym != null -> matchedSym
                arch == CpuArchitecture.DALVIK_DEX && dexPools.methods.isNotEmpty() -> {
                    val mSig = dexPools.methods[fnCounter % dexPools.methods.size]
                    mSig.substringAfter("->").substringBefore("(") + "_${String.format(Locale.US, "%04X", startAddr and 0xFFFF)}"
                }
                fnCounter == 0 -> "entry_0x${String.format(Locale.US, "%06X", startAddr and 0xFFFFFF)}"
                else -> "sub_${String.format(Locale.US, "%06X", startAddr and 0xFFFFFF)}"
            }

            val returnType = if (arch == CpuArchitecture.DALVIK_DEX) "Object" else "int64_t"
            val signature = if (arch == CpuArchitecture.DALVIK_DEX) {
                "public $returnType $fnName(Object v0, int v1)"
            } else {
                "$returnType $fnName(int64_t arg0, void* arg1)"
            }

            val regUsage = LinkedHashMap<String, Int>()
            var stackSize = 0
            var branchCount = 0
            val callees = ArrayList<Long>()
            val callers = ArrayList<Long>()

            for (ins in slice) {
                if (ins.stackDelta < 0) stackSize += (-ins.stackDelta)
                if (ins.isConditionalBranch) branchCount++
                if (ins.isCall && ins.branchTarget != null) callees.add(ins.branchTarget)
                callers.addAll(ins.xrefsFrom)
                for (r in ins.registersModified + ins.registersRead) {
                    val cleanR = r.substringBefore(" ")
                    if (cleanR != "PC" && cleanR != "SP" && cleanR != "RSP" && !cleanR.contains("Flag")) {
                        regUsage[cleanR] = (regUsage[cleanR] ?: 0) + 1
                    }
                }
            }

            val variables = regUsage.entries.take(6).mapIndexed { i, (reg, count) ->
                val vType = when {
                    arch == CpuArchitecture.DALVIK_DEX -> if (i == 0) "Object" else "int"
                    reg.startsWith("X") || reg.startsWith("R") -> if (i % 2 == 0) "int64_t" else "uintptr_t*"
                    else -> "uint32_t"
                }
                DecompiledVariable(
                    name = "var_${reg.lowercase(Locale.US)}",
                    storage = "Register $reg",
                    inferredType = vType,
                    usageCount = count
                )
            }

            val isRecursive = callees.contains(startAddr)
            val isLeaf = callees.isEmpty()
            val complexity = 1 + branchCount

            // Build structured C / Java decompiled source
            val codeLines = ArrayList<String>()
            codeLines.add("// Function @ 0x${String.format(Locale.US, "%08X", startAddr)} – 0x${String.format(Locale.US, "%08X", endAddr)} (${slice.size} instructions)")
            codeLines.add("// Frame: ${stackSize}B | Cyclomatic Complexity: $complexity | ${if (isLeaf) "Leaf Function" else "Calls ${callees.size} target(s)"}")
            codeLines.add("$signature {")
            for (v in variables.take(4)) {
                codeLines.add("    ${v.inferredType} ${v.name}; // mapped to ${v.storage} (${v.usageCount} refs)")
            }
            if (variables.isNotEmpty()) codeLines.add("")

            for (ins in slice) {
                if (ins.xrefsFrom.isNotEmpty()) {
                    codeLines.add(String.format(Locale.US, "loc_%08X: // <- XREF from %d location(s)", ins.address, ins.xrefsFrom.size))
                }
                codeLines.add(String.format(Locale.US, "    %-44s // [0x%06X] %s %s", ins.pseudocodeLine, ins.address, ins.mnemonic, ins.operands))
            }
            if (!slice.last().isReturn) {
                codeLines.add("    return 0;")
            }
            codeLines.add("}")

            functions.add(
                DisassembledFunction(
                    id = "fn_$fnCounter",
                    name = fnName,
                    startAddress = startAddr,
                    endAddress = endAddr,
                    startOffset = slice.first().fileOffset,
                    instructionIndices = slice.map { it.index },
                    signature = signature,
                    returnType = returnType,
                    stackFrameSize = stackSize,
                    isRecursive = isRecursive,
                    isLeaf = isLeaf,
                    cyclomaticComplexity = complexity,
                    variables = variables,
                    callers = callers.distinct(),
                    callees = callees.distinct(),
                    decompiledCodeLines = codeLines
                )
            )

            startIdx = endIdx + 1
            fnCounter++
        }

        return functions
    }

    private fun buildControlFlowGraph(
        instructions: List<DisassembledInstruction>,
        functions: List<DisassembledFunction>
    ): List<BasicBlockNode> {
        if (instructions.isEmpty()) return emptyList()

        // Split into basic blocks at branch/return boundaries or every 8 instructions
        val blocks = ArrayList<BasicBlockNode>()
        var blockStart = 0
        var blockId = 0

        while (blockStart < instructions.size && blockId < 32) {
            var blockEnd = blockStart
            while (blockEnd < instructions.size - 1 && (blockEnd - blockStart) < 7) {
                val ins = instructions[blockEnd]
                if (ins.isConditionalBranch || ins.isReturn || (ins.branchTarget != null && !ins.isCall)) {
                    break
                }
                blockEnd++
            }

            val slice = instructions.subList(blockStart, blockEnd + 1)
            val first = slice.first()
            val last = slice.last()
            val fnName = functions.firstOrNull { first.address in it.startAddress..it.endAddress }?.name ?: "sub_code"

            val blockType = when {
                blockId == 0 -> "ENTRY BLOCK"
                last.isReturn -> "EXIT / RETURN"
                last.isConditionalBranch -> "CONDITIONAL BRANCH"
                last.isCall -> "CALL BLOCK"
                else -> "SEQUENTIAL"
            }

            val nextSequential = if (!last.isReturn && blockId + 1 < 32 && blockEnd + 1 < instructions.size) {
                blockId + 1
            } else null

            val altTarget = if (last.isConditionalBranch && blockId + 2 < 32) {
                blockId + 2
            } else null

            blocks.add(
                BasicBlockNode(
                    id = blockId,
                    label = String.format(Locale.US, "BB #%d (0x%06X)", blockId, first.address and 0xFFFFFF),
                    functionName = fnName,
                    startAddress = first.address,
                    endAddress = last.address,
                    instructions = slice,
                    outgoingTrueTarget = altTarget ?: nextSequential,
                    outgoingFalseTarget = if (altTarget != null) nextSequential else null,
                    incomingBlocks = if (blockId > 0) listOf(blockId - 1) else emptyList(),
                    blockType = blockType
                )
            )

            blockStart = blockEnd + 1
            blockId++
        }

        return blocks
    }
}
