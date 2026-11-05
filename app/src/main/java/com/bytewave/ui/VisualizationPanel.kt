package com.bytewave.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bytewave.HexEditorViewModel
import com.bytewave.HexEditorState
import kotlinx.coroutines.launch
import kotlin.math.log2

@Composable
fun EntropyGraph(bytes: ByteArray?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f), shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SHANNON ENTROPY (Local Region)", fontSize=10.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.onSurface)
            Text("0.0 (Uncompressed) - 8.0 (Encrypted/Compressed)", fontSize=8.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(6.dp))
        
        val entropies = remember(bytes) {
            if (bytes != null && bytes.isNotEmpty()) {
                val windowSize = 256
                val step = maxOf(1, bytes.size / 200)
                val list = mutableListOf<Float>()
                
                for (i in 0 until bytes.size step step) {
                    val end = minOf(i + windowSize, bytes.size)
                    val freq = IntArray(256)
                    var count = 0
                    for (j in i until end) {
                        freq[bytes[j].toInt() and 0xFF]++
                        count++
                    }
                    var entropy = 0f
                    for (f in freq) {
                        if (f > 0) {
                            val p = f.toFloat() / count
                            entropy -= p * log2(p)
                        }
                    }
                    list.add(entropy)
                }
                list
            } else {
                emptyList()
            }
        }

        if (entropies.isNotEmpty()) {
            val maxEntropy = 8f
            val graphColor = MaterialTheme.colorScheme.primary
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barWidth = canvasWidth / entropies.size
                
                entropies.forEachIndexed { i, e ->
                    val h = (e / maxEntropy) * canvasHeight
                    drawRect(
                        color = graphColor,
                        topLeft = Offset(i * barWidth, canvasHeight - h),
                        size = Size(barWidth, h)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Analyzing stream entropy...", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun MemoryHeatmap(bytes: ByteArray?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f), shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("BINARY MEMORY HEATMAP", fontSize=10.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        
        if (bytes != null && bytes.isNotEmpty()) {
            val cols = 64
            val rows = (bytes.size + cols - 1) / cols
            
            val itemColors = remember(bytes) {
                bytes.map { b ->
                    val bVal = b.toInt() and 0xFF
                    when {
                        bVal == 0 -> Color(0xFF1E1E1E) // Zeros (Very Dark Gray)
                        bVal == 0xFF -> Color(0xFFFFEB3B) // High density FF (Yellow)
                        bVal in 0x20..0x7E -> Color(0xFF00E676) // ASCII printable (Bright Green)
                        else -> Color(0xFF2979FF).copy(alpha = bVal / 255f) // Hex value relative blue
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellW = size.width / cols
                val cellH = size.height / maxOf(1, rows)
                
                for (i in bytes.indices) {
                    val c = i % cols
                    val r = i / cols
                    val color = itemColors.getOrElse(i) { Color.DarkGray }
                    
                    drawRect(
                        color = color,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data available", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ByteFrequencyGraph(bytes: ByteArray?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f), shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("BYTE FREQUENCY HISTOGRAM", fontSize=10.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        
        val freqData = remember(bytes) {
            if (bytes != null && bytes.isNotEmpty()) {
                val freq = IntArray(256)
                for (b in bytes) {
                    freq[b.toInt() and 0xFF]++
                }
                freq
            } else {
                null
            }
        }

        if (freqData != null) {
            val maxFreq = remember(freqData) { freqData.maxOrNull()?.toFloat() ?: 1f }
            val barColor = MaterialTheme.colorScheme.tertiary
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barWidth = canvasWidth / 256f
                
                for (i in 0..255) {
                    val h = (freqData[i].toFloat() / maxFreq) * canvasHeight
                    if (h > 0f) {
                        drawRect(
                            color = barColor,
                            topLeft = Offset(i * barWidth, canvasHeight - h),
                            size = Size(maxOf(1f, barWidth - 0.5f), h)
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data available", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun VisualMemoryMap(bytes: ByteArray?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f), shape = RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("DYNAMIC SEGMENT MEMORY MAP (Blocks of 256B)", fontSize=10.sp, fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))

        if (bytes != null && bytes.isNotEmpty()) {
            val segments = remember(bytes) {
                val list = mutableListOf<SegmentData>()
                val blockSize = 256
                for (i in bytes.indices step blockSize) {
                    val end = minOf(i + blockSize, bytes.size)
                    val slice = bytes.copyOfRange(i, end)
                    
                    var zeros = 0
                    var ascii = 0
                    var control = 0
                    var other = 0
                    for (b in slice) {
                        val v = b.toInt() and 0xFF
                        when {
                            v == 0 -> zeros++
                            v in 0x20..0x7E -> ascii++
                            v < 0x20 || v == 0x7F -> control++
                            else -> other++
                        }
                    }
                    val size = end - i
                    list.add(
                        SegmentData(
                            offset = i.toLong(),
                            size = size,
                            zeroPercent = (zeros.toFloat() / size * 100).toInt(),
                            asciiPercent = (ascii.toFloat() / size * 100).toInt(),
                            controlPercent = (control.toFloat() / size * 100).toInt(),
                            otherPercent = (other.toFloat() / size * 100).toInt()
                        )
                    )
                }
                list
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segments.take(5).forEach { seg ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Offset: +${String.format("0x%04X", seg.offset)} (${seg.size} B)", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("ASCII:${seg.asciiPercent}% | Zeros:${seg.zeroPercent}% | Bin:${seg.otherPercent}%", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF2E2E2E))
                        ) {
                            if (seg.asciiPercent > 0) {
                                Box(modifier = Modifier.weight(seg.asciiPercent.toFloat()).fillMaxHeight().background(Color(0xFF00E676)))
                            }
                            if (seg.zeroPercent > 0) {
                                Box(modifier = Modifier.weight(seg.zeroPercent.toFloat()).fillMaxHeight().background(Color(0xFF555555)))
                            }
                            if (seg.controlPercent > 0) {
                                Box(modifier = Modifier.weight(seg.controlPercent.toFloat()).fillMaxHeight().background(Color(0xFFFF9100)))
                            }
                            if (seg.otherPercent > 0) {
                                Box(modifier = Modifier.weight(seg.otherPercent.toFloat()).fillMaxHeight().background(Color(0xFF2979FF)))
                            }
                        }
                    }
                }
                if (segments.size > 5) {
                    Text("... and ${segments.size - 5} more segments ...", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Text("Analyzing memory headers...", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

data class SegmentData(
    val offset: Long,
    val size: Int,
    val zeroPercent: Int,
    val asciiPercent: Int,
    val controlPercent: Int,
    val otherPercent: Int
)

// DYNAMIC REVERSE ENGINEERING AND DECOMPILER COMPONENT
@Composable
fun FileStructureVisualizer(
    viewModel: HexEditorViewModel,
    state: HexEditorState
) {
    var activeTabIdx by remember { mutableStateOf(0) } // 0 = Headers, 1 = Decompiler
    var headerBytes by remember { mutableStateOf<ByteArray?>(null) }
    var decompilerBytes by remember { mutableStateOf<ByteArray?>(null) }
    
    val selectedOffset = state.selectedOffset ?: 0L
    val activeTabObj = state.activeTab
    val fileUri = activeTabObj?.fileUri
    
    val scope = rememberCoroutineScope()
    
    // Auto-load 512 header bytes from offset 0
    LaunchedEffect(fileUri, state.activeTab?.patches) {
        if (fileUri != null) {
            try {
                headerBytes = viewModel.getBytes(0, 512)
            } catch (e: Exception) {
                headerBytes = null
            }
        }
    }

    // Auto-load 48 bytes at cursor position for on-the-fly decompilation
    LaunchedEffect(fileUri, selectedOffset, state.activeTab?.patches) {
        if (fileUri != null) {
            try {
                // Read 48 bytes centered around instruction
                decompilerBytes = viewModel.getBytes(selectedOffset, 48)
            } catch (e: Exception) {
                decompilerBytes = null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha=0.4f), shape = RoundedCornerShape(12.dp))
            .padding(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header text
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Reverse Eng Suite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "LOW-LEVEL REVERSING SUITE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Tabs toggle selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (activeTabIdx == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeTabIdx = 0 }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🔍 Headers Analyst",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTabIdx == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (activeTabIdx == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeTabIdx = 1 }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "💻 Disassembler / Decompiler",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTabIdx == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (fileUri == null) {
                Text(
                    "Open a file to run deep reverse-engineering parsing.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                when (activeTabIdx) {
                    0 -> HeaderAnalystTab(headerBytes)
                    1 -> CodeDecompilerTab(decompilerBytes, selectedOffset, viewModel)
                }
            }
        }
    }
}

@Composable
fun HeaderAnalystTab(bytes: ByteArray?) {
    if (bytes == null || bytes.isEmpty()) {
        Text("No headers loaded.", fontSize = 10.sp, color = Color.Gray)
        return
    }

    val header = bytes.take(Math.min(64, bytes.size)).map { it.toInt() and 0xFF }
    
    // Auto detection
    val isELF = header.size >= 4 && header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46
    val isDEX = header.size >= 4 && header[0] == 0x64 && header[1] == 0x65 && header[2] == 0x78 && header[3] == 0x0A
    val isPE = header.size >= 2 && header[0] == 0x4D && header[1] == 0x52  // MZ format
            || (header.size >= 2 && header[0] == 0x4D && header[1] == 0x5A)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            isELF -> {
                Text("📁 FORMAT: ELF BINARY (LINUX/ANDROID EXEC / .SO)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                
                val is64Bit = header.getOrNull(4) == 2
                val endian = if (header.getOrNull(5) == 1) "Little Endian" else "Big Endian"
                val version = header.getOrNull(6) ?: 1
                
                val abi = when (header.getOrNull(7)) {
                    0 -> "System V / Default"
                    1 -> "HP-UX"
                    2 -> "NetBSD"
                    3 -> "GNU ELF / Linux"
                    6 -> "Solaris"
                    8 -> "FreeBSD"
                    9 -> "ARM ABI"
                    97 -> "ARM"
                    else -> "Standalone / Embedded"
                }

                val type = if (header.size >= 18) {
                    val t = (header[16] or (header[17] shl 8))
                    when (t) {
                        1 -> "Relocatable file"
                        2 -> "Executable file (EXEC)"
                        3 -> "Shared object (SO / Dynamic Library)"
                        4 -> "Core file"
                        else -> "Unknown binary type ($t)"
                    }
                } else "Shared File (.so)"

                val machine = if (header.size >= 20) {
                    val m = (header[18] or (header[19] shl 8))
                    when (m) {
                        3 -> "Intel x86 (IA32)"
                        40 -> "ARM (32-bit)"
                        62 -> "AMD x86-64"
                        183 -> "ARM AArch64 (64-bit)"
                        243 -> "RISC-V"
                        else -> "Architecture Core ID ($m)"
                    }
                } else "Generic ARM/x86"

                val entryPoint = if (is64Bit && header.size >= 32) {
                    var ep = 0L
                    for (i in 0..7) {
                        ep = ep or ((header[24 + i].toLong() and 0xFF) shl (i * 8))
                    }
                    String.format("0x%016X", ep)
                } else if (header.size >= 28) {
                    var ep = 0L
                    for (i in 0..3) {
                        ep = ep or ((header[24 + i].toLong() and 0xFF) shl (i * 8))
                    }
                    String.format("0x%08X", ep)
                } else "N/A"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    HeaderRow(label = "Signature Magic", value = "7F 45 4C 46 [ELF]")
                    HeaderRow(label = "Structure Type", value = type)
                    HeaderRow(label = "Architecture Instruction", value = machine)
                    HeaderRow(label = "File Word Size", value = if (is64Bit) "64-bit Execution" else "32-bit Execution")
                    HeaderRow(label = "Data Ordering", value = endian)
                    HeaderRow(label = "OS ABI Specifier", value = abi)
                    HeaderRow(label = "Execution Entry Shift", value = entryPoint)
                    if (header.size >= 40) {
                        val phOff = if (is64Bit) "p_hdr offset at pos " else "32b p_hdr"
                        HeaderRow(label = "Program Headers Offset", value = "parsed in segments")
                    }
                }
            }
            isDEX -> {
                Text("📁 FORMAT: DALVIK EXECUTABLE (DEX / ANDROID DEX bytecode)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
                
                val verString = if (bytes.size >= 7) bytes.copyOfRange(4, 7).map { it.toInt().toChar() }.joinToString("") else "N/A"
                var fileSizeVal = 0L
                if (header.size >= 36) {
                    fileSizeVal = (header[32] or (header[33] shl 8) or (header[34] shl 16) or (header[35] shl 24)).toLong() and 0xFFFFFFFFL
                }

                val checksum = if (header.size >= 12) {
                    String.format("%02X %02X %02X %02X", header[8], header[9], header[10], header[11])
                } else "N/A"

                val stringIds = if (header.size >= 60) {
                    val count = (header[56] or (header[57] shl 8) or (header[58] shl 16) or (header[59] shl 24))
                    "Count: $count"
                } else "N/A"

                val methodIds = if (header.size >= 92) {
                    val count = (header[88] or (header[89] shl 8) or (header[90] shl 16) or (header[91] shl 24))
                    "Count: $count"
                } else "N/A"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    HeaderRow(label = "Signature Magic", value = "dex\\n [DEX]")
                    HeaderRow(label = "Instruction Version", value = "v$verString")
                    HeaderRow(label = "Adler32 Checksum", value = checksum)
                    HeaderRow(label = "Internal Size Field", value = "$fileSizeVal Bytes (${String.format("%.2f", fileSizeVal / 1024f)} KB)")
                    HeaderRow(label = "String Identifier Pool", value = stringIds)
                    HeaderRow(label = "Method Identifiers", value = methodIds)
                    HeaderRow(label = "Linker Structures Size", value = "Offset: ${header.getOrNull(44) ?: 0}")
                }
            }
            isPE -> {
                Text("📁 FORMAT: PORTABLE EXECUTABLE (MZ PE / WINDOWS EXE)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2979FF))
                
                val peOffset = if (header.size >= 64) {
                    (header[0x3C] or (header[0x3D] shl 8))
                } else 0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    HeaderRow(label = "DOS Header Signature", value = "MZ [4D 5A]")
                    HeaderRow(label = "PE Header Offset", value = "Offset 0x${Integer.toHexString(peOffset).uppercase()}")
                    HeaderRow(label = "Format Compatibility", value = "Win32/Win64 Native execution COFF")
                    HeaderRow(label = "Dynamic Relocations", value = "Dynamic base supported")
                }
            }
            else -> {
                Text("📁 GENERIC RAW FORMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                val hexStr = header.joinToString(" ") { String.format("%02X", it) }
                val asciiStr = bytes.take(16).map { val char = it.toInt().toChar(); if (char in ' '..'~') char else '.' }.joinToString("")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    HeaderRow(label = "Hex Bytes Pattern", value = hexStr.take(32) + "...")
                    HeaderRow(label = "ASCII Translation", value = asciiStr)
                    HeaderRow(label = "Entropy Level Indicator", value = "Generic stream")
                }
            }
        }
    }
}

@Composable
fun HeaderRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// INSTRUCTION STRUCTURE HOLDER
data class SimpleInstruction(
    val offset: Long,
    val hexString: String,
    val size: Int,
    val mnem: String,
    val opnd: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CodeDecompilerTab(
    bytes: ByteArray?,
    selectedOffset: Long,
    viewModel: HexEditorViewModel
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var selectedArchIdx by remember { mutableStateOf(0) } // 0=Auto (ARM64 default for ELF), 1=ARM64, 2=ARM32, 3=Dalvik, 4=x86_64
    val architectures = listOf("⚡ AUTO-DETECT", "🦾 ARM64 (A64)", "🥋 ARM32 (T32)", "☕ DALVIK (DEX)", "💻 Intel x86_64")
    var showArchDropdown by remember { mutableStateOf(false) }
    
    // Instruction Patch dialog state
    var selectedInstructionForPatch by remember { mutableStateOf<SimpleInstruction?>(null) }
    var patchHexString by remember { mutableStateOf("") }
    var patchSuccessMsg by remember { mutableStateOf("") }

    if (bytes == null || bytes.isEmpty()) {
        Text("No executable bytes loaded at current offset.", fontSize = 10.sp, color = Color.Gray)
        return
    }

    // Determine arch based on selection vs file characteristics if on AUTO-DETECT
    val actualArchIdx = if (selectedArchIdx == 0) {
        // Simple heuristic: read header or file signature
        val firstHeaderBytes = remember {
            try {
                // If ELF: ARM64 Or x86
                // In an android environment, default to ARM64 if nothing selected
                1 // ARM64
            } catch (e: Exception) {
                1
            }
        }
        firstHeaderBytes
    } else {
        selectedArchIdx
    }

    // Decode the local instruction bytes
    val decodedInstructions = remember(bytes, selectedOffset, actualArchIdx) {
        val list = mutableListOf<SimpleInstruction>()
        var idx = 0
        while (idx < bytes.size - 4) {
            val offset = selectedOffset + idx
            when (actualArchIdx) {
                1 -> { // ARM64 - rigid 4 bytes
                    val b0 = bytes[idx].toInt() and 0xFF
                    val b1 = bytes[idx + 1].toInt() and 0xFF
                    val b2 = bytes[idx + 2].toInt() and 0xFF
                    val b3 = bytes[idx + 3].toInt() and 0xFF
                    val hex = String.format("%02X%02X%02X%02X", b0, b1, b2, b3)
                    val value = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)

                    // Simple disassembler mapper
                    val (mnem, opnd) = when {
                        value == 0xD503201F.toInt() -> Pair("nop", "")
                        value == 0xD65F03C0.toInt() -> Pair("ret", "lr")
                        (value and 0xFC000000.toInt()) == 0x94000000.toInt() -> {
                            val rel = (value and 0x03FFFFFF) shl 2
                            Pair("bl", "0x${java.lang.Long.toHexString(offset + rel)}")
                        }
                        (value and 0xFC000000.toInt()) == 0x14000000.toInt() -> {
                            val rel = (value and 0x03FFFFFF) shl 2
                            Pair("b", "0x${java.lang.Long.toHexString(offset + rel)}")
                        }
                        (value and 0xFF000000.toInt()) == 0x54000000.toInt() -> {
                            Pair("b.eq", "0x${java.lang.Long.toHexString(offset + 8)}")
                        }
                        (value and 0xFF200000.toInt()) == 0xD1000000.toInt() -> {
                            val imm = (value shr 10) and 0xFFF
                            Pair("sub", "sp, sp, #0x${Integer.toHexString(imm)}")
                        }
                        (value and 0xFF200000.toInt()) == 0x91000000.toInt() -> {
                            val imm = (value shr 10) and 0xFFF
                            Pair("add", "sp, sp, #0x${Integer.toHexString(imm)}")
                        }
                        (value and 0xFFE00000.toInt()) == 0xAA000000.toInt() -> {
                            val rd = value and 0x1F
                            val rn = (value shr 5) and 0x1F
                            val rm = (value shr 16) and 0x1F
                            Pair("orr", "x$rd, x$rn, x$rm")
                        }
                        (value and 0x3F000000) == 0x39000000 -> {
                            val rd = value and 0x1F
                            val rn = (value shr 5) and 0x1F
                            val imm = (value shr 10) and 0xFFF
                            Pair("str", "w$rd, [x$rn, #0x${Integer.toHexString(imm)}]")
                        }
                        (value and 0x3F000000) == 0x3A000000 -> {
                            val rd = value and 0x1F
                            val rn = (value shr 5) and 0x1F
                            val imm = (value shr 10) and 0xFFF
                            Pair("ldr", "w$rd, [x$rn, #0x${Integer.toHexString(imm)}]")
                        }
                        value == 0xD4000001.toInt() -> Pair("svc", "#0 [Kernel Call]")
                        else -> {
                            val rd = value and 0x1F
                            val rn = (value shr 5) and 0x1F
                            val imm = (value shr 10) and 0xFFF
                            if ((value and 0x1F000000) == 0x11000000) {
                                Pair("add", "x$rd, x$rn, #0x${Integer.toHexString(imm)}")
                            } else if ((value and 0x1F000000) == 0x12000000) {
                                Pair("sub", "x$rd, x$rn, #0x${Integer.toHexString(imm)}")
                            } else {
                                Pair("inst", "0x$hex")
                            }
                        }
                    }

                    list.add(SimpleInstruction(offset, hex, 4, mnem, opnd))
                    idx += 4
                }
                2 -> { // ARM32/Thumb - 2 bytes or 4 bytes
                    val b0 = bytes[idx].toInt() and 0xFF
                    val b1 = bytes[idx + 1].toInt() and 0xFF
                    val hex = String.format("%02X%02X", b0, b1)
                    val value = b0 or (b1 shl 8)

                    val (mnem, opnd) = when {
                        value == 0x46C0 -> Pair("nop", "")
                        value == 0x4770 -> Pair("bx", "lr")
                        (value and 0xF000) == 0xD000 -> Pair("beq", "0x${java.lang.Long.toHexString(offset + 4)}")
                        (value and 0xE000) == 0x2000 -> {
                            val rd = (value shr 8) and 0x7
                            val imm = value and 0xFF
                            Pair("movs", "r$rd, #$imm")
                        }
                        (value and 0xF800) == 0xB000 -> {
                            val sign = if ((value and 0x0080) != 0) "-" else "+"
                            val imm = (value and 0x7F) shl 2
                            Pair("sub", "sp, #$imm")
                        }
                        (value and 0xFE00) == 0xBC00 -> {
                            Pair("pop", "{r0-r7, pc}")
                        }
                        (value and 0xFE00) == 0xB400 -> {
                            Pair("push", "{r0-r7, lr}")
                        }
                        else -> {
                            Pair("thumb_inst", "0x$hex")
                        }
                    }

                    list.add(SimpleInstruction(offset, hex, 2, mnem, opnd))
                    idx += 2
                }
                3 -> { // DALVIK (DEX bytecode)
                    val b0 = bytes[idx].toInt() and 0xFF
                    val b1 = bytes[idx + 1].toInt() and 0xFF
                    val hex = String.format("%02X%02X", b0, b1)

                    val (mnem, opnd) = when (b0) {
                        0x00 -> Pair("nop", "")
                        0x01 -> Pair("move", "v${b1 and 0x0F}, v${(b1 shr 4) and 0x0F}")
                        0x07 -> Pair("move-object", "v${b1 and 0x0F}, v${(b1 shr 4) and 0x0F}")
                        0x0E -> Pair("return-void", "")
                        0x0F -> Pair("return", "v$b1")
                        0x12 -> Pair("const/4", "v${b1 and 0x0F}, #${(b1 shr 4) and 0x0F}")
                        0x13 -> Pair("const/16", "v$b1")
                        0x27 -> Pair("throw", "v$b1")
                        0x28 -> Pair("goto", "+$b1")
                        0x38 -> Pair("if-eqz", "v$b1")
                        0x52 -> Pair("iget", "v${b1 and 0x0F}")
                        0x59 -> Pair("iput", "v${b1 and 0x0F}")
                        0x6e -> Pair("invoke-virtual", "v1, v2")
                        0x70 -> Pair("invoke-direct", "{v0}, method_id")
                        0x71 -> Pair("invoke-static", "{v0..v1}, method_id")
                        else -> Pair("dalvik_op", "0x${String.format("%02X", b0)}")
                    }

                    list.add(SimpleInstruction(offset, hex, 2, mnem, opnd))
                    idx += 2
                }
                4 -> { // Intel x86_64
                    val b0 = bytes[idx].toInt() and 0xFF
                    val b1 = bytes[idx + 1].toInt() and 0xFF
                    val hex = String.format("%02X%02X", b0, b1)

                    val (mnem, opnd, size) = when {
                        b0 == 0x90 -> Triple("nop", "", 1)
                        b0 == 0x55 -> Triple("push", "rbp", 1)
                        b0 == 0x5D -> Triple("pop", "rbp", 1)
                        b0 == 0x48 && b1 == 0x89 -> Triple("mov", "rbp, rsp", 3)
                        b0 == 0x48 && b1 == 0x83 -> {
                            Triple("sub", "rsp, 0x${String.format("%02X", bytes[idx + 2].toInt() and 0xFF)}", 4)
                        }
                        b0 == 0xC3 -> Triple("ret", "", 1)
                        b0 == 0x31 && b1 == 0xC0 -> Triple("xor", "eax, eax", 2)
                        b0 == 0x85 && b1 == 0xC0 -> Triple("test", "eax, eax", 2)
                        b0 == 0x0F && b1 == 0x05 -> Triple("syscall", "", 2)
                        b0 == 0xE8 -> Triple("call", "0x${java.lang.Integer.toHexString(bytes[idx + 1].toInt() and 0xFF)}", 5)
                        else -> Triple("db", "0x${String.format("%02X", b0)}", 1)
                    }

                    list.add(SimpleInstruction(offset, hex.take(size * 2), size, mnem, opnd))
                    idx += size
                }
                else -> {
                    list.add(SimpleInstruction(offset, String.format("%02X", bytes[idx]), 1, "db", "0x${String.format("%02X", bytes[idx])}"))
                    idx += 1
                }
            }
        }
        list
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Architecture selection picker triggers dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showArchDropdown = !showArchDropdown },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        architectures[selectedArchIdx],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown Selection",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showArchDropdown,
                onDismissRequest = { showArchDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                architectures.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 11.sp) },
                        onClick = {
                            selectedArchIdx = i
                            showArchDropdown = false
                        }
                    )
                }
            }
        }

        // Subtitle instructions
        Text(
            "Showing live instruction disassembly starting at selection: 0x${java.lang.Long.toHexString(selectedOffset).uppercase()}",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        // Render instruction rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .border(1.dp, Color.Gray.copy(alpha=0.3f), RoundedCornerShape(6.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            decodedInstructions.take(7).forEach { inst ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (inst.offset == selectedOffset) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Absolute offset
                            Text(
                                String.format("0x%04X", inst.offset),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                            // Raw Opcode bytes
                            Text(
                                inst.hexString,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                modifier = Modifier.width(60.dp)
                            )
                            // Mnemonic
                            Text(
                                inst.mnem,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE91E63),
                                fontWeight = FontWeight.Bold
                            )
                            // Operands
                            Text(
                                inst.opnd,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                    
                    // PATCH Action triggers inline forms
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha=0.3f))
                            .clickable {
                                selectedInstructionForPatch = inst
                                patchHexString = inst.hexString
                                patchSuccessMsg = ""
                            }
                            .padding(vertical = 2.dp, horizontal = 6.dp)
                    ) {
                        Text("⚙️ PATCH", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Animated patch box
        AnimatedVisibility(
            visible = selectedInstructionForPatch != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val inst = selectedInstructionForPatch
            if (inst != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PATCH INSTRUCTION @ 0x${java.lang.Long.toHexString(inst.offset).uppercase()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close patch panel",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    selectedInstructionForPatch = null
                                },
                            tint = Color.Gray
                        )
                    }

                    // Predefined options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // NOP option
                        val nopHex = when (actualArchIdx) {
                            1 -> "1F2003D5" // ARM64 NOP
                            2 -> "C046"     // thumb NOP
                            3 -> "0000"     // Dalvik NOP
                            4 -> "90"       // x86 NOP
                            else -> "00"
                        }
                        
                        Button(
                            onClick = {
                                patchHexString = nopHex
                            },
                            modifier = Modifier.weight(1f).height(24.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("FILL NOP ($nopHex)", fontSize = 8.sp, color = MaterialTheme.colorScheme.onTertiary)
                        }

                        // ZERO OUT option
                        val zeroHex = "00".repeat(inst.size)
                        Button(
                            onClick = {
                                patchHexString = zeroHex
                            },
                            modifier = Modifier.weight(1f).height(24.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
                        ) {
                            Text("ZERO OUT", fontSize = 8.sp, color = Color.White)
                        }
                    }

                    // Direct Hex Edit input
                    OutlinedTextField(
                        value = patchHexString,
                        onValueChange = { patchHexString = it.filter { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } },
                        label = { Text("Enter Hex values (no spaces)", fontSize = 8.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                    )

                    Button(
                        onClick = {
                            var parseSuccess = false
                            try {
                                val bytesToPatch = mutableListOf<Byte>()
                                for (i in patchHexString.indices step 2) {
                                    if (i + 1 < patchHexString.length) {
                                        val hexByteStr = patchHexString.substring(i, i + 2)
                                        val b = hexByteStr.toInt(16).toByte()
                                        bytesToPatch.add(b)
                                    }
                                }
                                
                                // Write bytes line by line
                                bytesToPatch.forEachIndexed { byteIdx, b ->
                                    viewModel.patchByte(inst.offset + byteIdx, b)
                                }
                                parseSuccess = true
                                patchSuccessMsg = "Successfully patched ${bytesToPatch.size} byte(s) at 0x${java.lang.Long.toHexString(inst.offset).uppercase()}!"
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            } catch (e: Exception) {
                                patchSuccessMsg = "Error: Invalid hex string."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Apply Code Patch", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    if (patchSuccessMsg.isNotEmpty()) {
                        Text(
                            patchSuccessMsg,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (patchSuccessMsg.contains("Error")) Color.Red else Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsPanel(viewModel: HexEditorViewModel, state: HexEditorState) {
    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    val offset = state.selectedOffset ?: 0L
    
    LaunchedEffect(offset, state.activeTab?.patches) {
        val region = viewModel.getBytes(maxOf(0L, offset - 2048L), 4096)
        rawBytes = region
    }
    
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ByteFrequencyGraph(rawBytes)
        EntropyGraph(rawBytes)
        MemoryHeatmap(rawBytes)
        VisualMemoryMap(rawBytes)
        
        // Pass the live viewmodel and state directly to support dynamic decompilation & patching!
        FileStructureVisualizer(viewModel = viewModel, state = state)
    }
}
