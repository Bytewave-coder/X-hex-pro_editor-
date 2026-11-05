package com.bytewave.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytewave.FileHistoryRecord
import com.bytewave.HexEditorViewModel
import com.bytewave.WorkspaceTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Adler32
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

enum class SpectraColorPalette(val title: String) {
    PLASMA("Plasma"),
    CYBER_NEON("Cyber"),
    VIRIDIS("Emerald"),
    THERMAL("Thermal")
}

enum class SpectraToolTab(
    val title: String,
    val badge: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SPECTROGRAM("Spectrogram", "2D MAP", Icons.Filled.GridOn, Icons.Outlined.GridOn),
    PHASE_SPACE("Phase & Bits", "256-BIN", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    ANOMALIES("Anomalies", "ZONES", Icons.Filled.Radar, Icons.Outlined.Radar),
    FORENSICS("Headers & Hash", "CRYPTO", Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint),
    STRINGS_ASM("Strings & ASM", "DISASM", Icons.Filled.Memory, Icons.Outlined.Memory)
}

data class EntropyBlockInfo(
    val index: Int,
    val startOffset: Long,
    val endOffset: Long,
    val bufferOffset: Int,
    val shannonEntropy: Float,
    val minEntropy: Float,
    val printableRatio: Float,
    val zeroRatio: Float,
    val highByteRatio: Float,
    val meanByte: Float
)

data class EntropyRegionAnomaly(
    val startOffset: Long,
    val endOffset: Long,
    val avgEntropy: Float,
    val peakEntropy: Float,
    val category: String,
    val description: String,
    val severityColor: Color
)

data class ExtractedBinaryString(
    val offset: Long,
    val value: String,
    val category: String
)

data class SpectraAnalysisResult(
    val fileName: String,
    val sourceLabel: String,
    val totalFileSize: Long,
    val analyzedBytesCount: Int,
    val patchCount: Int,
    val computeTimeMs: Double,
    val throughputMbPerSec: Double,
    val overallEntropy: Float,
    val minEntropy: Float,
    val renyiEntropy: Float,
    val indexOfCoincidence: Double,
    val chiSquare: Double,
    val monteCarloPi: Double,
    val monteCarloErrorPct: Double,
    val serialCorrelation: Double,
    val arithmeticMean: Double,
    val theoreticalCompressionPct: Int,
    val bitPlaneOnesPct: FloatArray,
    val byteHistogram: IntArray,
    val digramMatrix: IntArray,
    val maxDigramCount: Int,
    val blocks: List<EntropyBlockInfo>,
    val anomalies: List<EntropyRegionAnomaly>,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val crc32: String,
    val adler32: String,
    val magicSignature: String,
    val magicDetails: List<Pair<String, String>>,
    val extractedStrings: List<ExtractedBinaryString>
)

/**
 * High-speed C++-style LUT (Lookup Table) Vector Entropy Core.
 */
private object NativeLutEntropyCore {
    private const val MAX_LUT = 65536
    val LOG2_TABLE = FloatArray(MAX_LUT + 1)
    val INV_LN2 = (1.0 / ln(2.0)).toFloat()

    init {
        LOG2_TABLE[0] = 0f
        for (i in 1..MAX_LUT) {
            LOG2_TABLE[i] = (ln(i.toDouble()) * INV_LN2).toFloat()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun fastLog2(n: Int): Float {
        return if (n <= MAX_LUT) LOG2_TABLE[n] else (ln(n.toDouble()) * INV_LN2).toFloat()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntropySpectrogramAppScreen(
    viewModel: HexEditorViewModel,
    onClose: () -> Unit
) {
    BackHandler {
        onClose()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val initialTab = state.activeTab ?: state.tabs.firstOrNull()

    var selectedUri by remember {
        mutableStateOf<Uri?>(initialTab?.fileUri)
    }
    var selectedFileName by remember {
        mutableStateOf(initialTab?.fileName ?: "")
    }
    var selectedExpectedSize by remember {
        mutableLongStateOf(initialTab?.fileSize ?: 0L)
    }
    var selectedSourceLabel by remember {
        mutableStateOf(
            if (initialTab != null) "Active Hex Tab • Native LUT Core" else ""
        )
    }
    var selectedPatches by remember {
        mutableStateOf<Map<Long, Byte>>(initialTab?.patches ?: emptyMap())
    }
    var analysisTrigger by remember { mutableIntStateOf(0) }

    var windowSize by remember { mutableIntStateOf(256) }
    var colorPalette by remember { mutableStateOf(SpectraColorPalette.PLASMA) }
    var activeToolTab by remember { mutableStateOf(SpectraToolTab.SPECTROGRAM) }
    var selectedBlockIndex by remember { mutableIntStateOf(0) }
    var isAnalyzing by remember { mutableStateOf(initialTab?.fileUri != null) }
    var rawAnalyzedBytes by remember { mutableStateOf(ByteArray(0)) }
    var analysisResult by remember { mutableStateOf<SpectraAnalysisResult?>(null) }
    var showImportSourceSheet by remember { mutableStateOf(false) }
    var stringSearchQuery by remember { mutableStateOf("") }
    var minStringLength by remember { mutableIntStateOf(4) }
    var disasmArch by remember { mutableStateOf("DEX") }
    var phaseSpaceBitPlaneMode by remember { mutableStateOf(false) }

    LaunchedEffect(state.activeTabIndex, state.tabs.size) {
        val tab = state.activeTab ?: state.tabs.firstOrNull()
        if (tab != null && selectedUri == null) {
            selectedUri = tab.fileUri
            selectedFileName = tab.fileName
            selectedExpectedSize = tab.fileSize
            selectedPatches = tab.patches
            selectedSourceLabel = "Active Hex Tab (${tab.patches.size} patches)"
            analysisTrigger++
        }
    }

    val storageImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val resolvedName = resolveDisplayName(context, uri)
            val matchingTab = state.tabs.find { it.fileUri.toString() == uri.toString() }
            selectedUri = uri
            selectedFileName = resolvedName
            selectedExpectedSize = matchingTab?.fileSize ?: 0L
            selectedPatches = matchingTab?.patches ?: emptyMap()
            selectedSourceLabel = "Imported from Storage • Native LUT Core"
            disasmArch = if (resolvedName.endsWith(".dex", ignoreCase = true)) "DEX" else "ARM64"
            if (matchingTab == null) {
                viewModel.openFile(context, uri)
            }
            showImportSourceSheet = false
            analysisTrigger++
        }
    }

    LaunchedEffect(selectedUri, selectedPatches, windowSize, minStringLength, analysisTrigger) {
        val targetUri = selectedUri
        if (targetUri == null) {
            rawAnalyzedBytes = ByteArray(0)
            analysisResult = null
            isAnalyzing = false
            return@LaunchedEffect
        }

        isAnalyzing = true
        try {
            val (readBytes, realSize) = viewModel.readPatchedFileBytes(
                context = context,
                uri = targetUri,
                patches = selectedPatches,
                maxBytes = 1_048_576
            )
            if (readBytes.isEmpty()) {
                rawAnalyzedBytes = ByteArray(0)
                analysisResult = null
                return@LaunchedEffect
            }

            val effectiveTotalSize = when {
                realSize > 0L -> realSize
                selectedExpectedSize > 0L -> selectedExpectedSize
                else -> readBytes.size.toLong()
            }

            rawAnalyzedBytes = readBytes
            if (selectedFileName.endsWith(".dex", ignoreCase = true)) {
                disasmArch = "DEX"
            }

            val computed = withContext(Dispatchers.Default) {
                computeSpectraForensicsNativeLut(
                    bytes = readBytes,
                    fileName = selectedFileName.ifEmpty { "binary_file.bin" },
                    sourceLabel = selectedSourceLabel.ifEmpty { "Imported Binary • Native LUT Core" },
                    totalFileSize = effectiveTotalSize,
                    patchCount = selectedPatches.size,
                    windowSize = windowSize,
                    minStringLen = minStringLength
                )
            }
            analysisResult = computed
            selectedBlockIndex = selectedBlockIndex.coerceIn(0, maxOf(0, computed.blocks.size - 1))
        } catch (e: Exception) {
            e.printStackTrace()
            rawAnalyzedBytes = ByteArray(0)
            analysisResult = null
        } finally {
            isAnalyzing = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Material 3 Top App Bar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            FilledTonalIconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("entropy_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Hex Editor"
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Entropy Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Shannon Spectrogram & Forensics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { showImportSourceSheet = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("entropy_import_workspace_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Hex Files",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hex Files",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = { storageImportLauncher.launch(arrayOf("*/*")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("entropy_import_storage_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Import from Storage",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Storage",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Active File Telemetry Banner (Material 3 Surface Container)
                    analysisResult?.let { res ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showImportSourceSheet = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = res.fileName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 150.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${formatByteCount(res.totalFileSize)} • ${String.format(Locale.US, "%.1f ms", res.computeTimeMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoGraph,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = String.format(Locale.US, "H = %.4f b/B", res.overallEntropy),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Open Hex Workspace FilterChips Row
                    if (state.tabs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Workspaces,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            state.tabs.forEachIndexed { idx, tab ->
                                val isSelected = tab.fileUri?.toString() == selectedUri?.toString() && selectedFileName == tab.fileName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedUri = tab.fileUri
                                        selectedFileName = tab.fileName
                                        selectedExpectedSize = tab.fileSize
                                        selectedPatches = tab.patches
                                        selectedSourceLabel = "Hex Workspace Tab #${idx + 1} (${tab.patches.size} patches)"
                                        analysisTrigger++
                                    },
                                    label = {
                                        Text(
                                            text = "${tab.fileName} (${formatByteCount(tab.fileSize)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Description,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Material 3 ScrollableTabRow with Material Icons (shown when a file is analyzed)
                    if (analysisResult != null) {
                        ScrollableTabRow(
                            selectedTabIndex = activeToolTab.ordinal,
                            edgePadding = 12.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                        ) {
                            SpectraToolTab.entries.forEach { tab ->
                                val isSelected = activeToolTab == tab
                                Tab(
                                    selected = isSelected,
                                    onClick = { activeToolTab = tab },
                                    modifier = Modifier.testTag("spectra_tab_${tab.name.lowercase()}"),
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = tab.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val res = analysisResult
            if (isAnalyzing && res == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ElevatedCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Computing Shannon Entropy Spectrogram...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedFileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else if (res == null) {
                // Clean Material 3 Empty State when no file is imported
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = com.bytewave.R.drawable.ic_empty_folder),
                        contentDescription = "Nothing here!",
                        modifier = Modifier
                            .sizeIn(maxWidth = 220.dp, maxHeight = 180.dp)
                            .fillMaxWidth(0.6f),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nothing here!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import a binary or DEX file from device storage, or choose a file from your Hex Editor workspace to analyze its Shannon entropy spectrogram.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { storageImportLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("empty_state_import_storage_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Import from Storage",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.tabs.isNotEmpty() || state.fileHistory.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { showImportSourceSheet = true },
                                modifier = Modifier.testTag("empty_state_hex_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Hex Files",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                when (activeToolTab) {
                    SpectraToolTab.SPECTROGRAM -> {
                        SpectrogramMainView(
                            result = res,
                            rawBytes = rawAnalyzedBytes,
                            windowSize = windowSize,
                            onWindowSizeChange = { windowSize = it },
                            colorPalette = colorPalette,
                            onPaletteChange = { colorPalette = it },
                            selectedBlockIndex = selectedBlockIndex,
                            onSelectBlock = { selectedBlockIndex = it },
                            onJumpToHexOffset = { offset ->
                                val tabIdx = state.tabs.indexOfFirst { it.fileUri?.toString() == selectedUri?.toString() }
                                if (tabIdx != -1) {
                                    viewModel.switchTab(tabIdx)
                                }
                                viewModel.selectOffset(offset)
                                onClose()
                            }
                        )
                    }
                    SpectraToolTab.PHASE_SPACE -> {
                        PhaseSpaceAndDistributionView(
                            result = res,
                            rawBytes = rawAnalyzedBytes,
                            bitPlaneMode = phaseSpaceBitPlaneMode,
                            onToggleBitPlaneMode = { phaseSpaceBitPlaneMode = it },
                            colorPalette = colorPalette
                        )
                    }
                    SpectraToolTab.ANOMALIES -> {
                        AnomalyScannerView(
                            result = res,
                            onJumpToHexOffset = { offset ->
                                val tabIdx = state.tabs.indexOfFirst { it.fileUri?.toString() == selectedUri?.toString() }
                                if (tabIdx != -1) {
                                    viewModel.switchTab(tabIdx)
                                }
                                viewModel.selectOffset(offset)
                                onClose()
                            }
                        )
                    }
                    SpectraToolTab.FORENSICS -> {
                        ForensicHashesAndSignaturesView(
                            result = res,
                            onCopyText = { label, value ->
                                clipboardManager.setText(AnnotatedString(value))
                                Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    SpectraToolTab.STRINGS_ASM -> {
                        val currentBlock = res.blocks.getOrNull(selectedBlockIndex)
                        val inspectOffset = currentBlock?.bufferOffset ?: 0
                        val displayOffset = currentBlock?.startOffset ?: 0L
                        StringsAndDisassemblyView(
                            result = res,
                            rawBytes = rawAnalyzedBytes,
                            bufferOffset = inspectOffset,
                            displayOffset = displayOffset,
                            stringQuery = stringSearchQuery,
                            onStringQueryChange = { stringSearchQuery = it },
                            minStringLen = minStringLength,
                            onMinStringLenChange = { minStringLength = it },
                            disasmArch = disasmArch,
                            onDisasmArchChange = { disasmArch = it },
                            onJumpToHexOffset = { offset ->
                                val tabIdx = state.tabs.indexOfFirst { it.fileUri?.toString() == selectedUri?.toString() }
                                if (tabIdx != -1) {
                                    viewModel.switchTab(tabIdx)
                                }
                                viewModel.selectOffset(offset)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }

    // Material 3 ModalBottomSheet for Selecting Workspace Files, History, or Device Storage
    if (showImportSourceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showImportSourceSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Select Binary Stream",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Load from Hex Editor tabs, history, or device storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showImportSourceSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Import from Storage Elevated Card CTA
                ElevatedCard(
                    onClick = { storageImportLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Import File from Device Storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Analyze DEX, APK, SO, ELF, BIN, ZIP, or Firmware",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                HorizontalDivider()

                // Open Hex Workspace Files Section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Workspaces,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Hex Editor Tabs (${state.tabs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.tabs.isEmpty()) {
                    OutlinedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No files currently open in Hex Editor tabs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    state.tabs.forEachIndexed { idx, tab ->
                        val isCurrent = tab.fileUri?.toString() == selectedUri?.toString() && selectedFileName == tab.fileName
                        OutlinedCard(
                            onClick = {
                                selectedUri = tab.fileUri
                                selectedFileName = tab.fileName
                                selectedExpectedSize = tab.fileSize
                                selectedPatches = tab.patches
                                selectedSourceLabel = "Hex Workspace Tab #${idx + 1} (${tab.patches.size} patches)"
                                showImportSourceSheet = false
                                analysisTrigger++
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = tab.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${formatByteCount(tab.fileSize)} • ${tab.patches.size} live hex modifications",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }

                // Previous Modified & History Files Section
                if (state.fileHistory.isNotEmpty()) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recent & Modified History (${state.fileHistory.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
                    state.fileHistory.take(15).forEach { record ->
                        OutlinedCard(
                            onClick = {
                                val uri = Uri.parse(record.uriString)
                                val matchingTab = state.tabs.find { it.fileUri?.toString() == record.uriString }
                                selectedUri = uri
                                selectedFileName = record.fileName
                                selectedExpectedSize = record.fileSize
                                selectedPatches = matchingTab?.patches ?: emptyMap()
                                selectedSourceLabel = "Previous Hex History (${record.modificationCount} edits)"
                                showImportSourceSheet = false
                                analysisTrigger++
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = record.fileName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = "${formatByteCount(record.fileSize)} • ${record.modificationCount} edits • ${dateFormat.format(Date(record.lastModified))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.RestorePage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrogramMainView(
    result: SpectraAnalysisResult,
    rawBytes: ByteArray,
    windowSize: Int,
    onWindowSizeChange: (Int) -> Unit,
    colorPalette: SpectraColorPalette,
    onPaletteChange: (SpectraColorPalette) -> Unit,
    selectedBlockIndex: Int,
    onSelectBlock: (Int) -> Unit,
    onJumpToHexOffset: (Long) -> Unit
) {
    val blocks = result.blocks
    val safeIndex = selectedBlockIndex.coerceIn(0, maxOf(0, blocks.size - 1))
    val currentBlock = blocks.getOrNull(safeIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top M3 Telemetry Strip with Material Icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            M3TelemetryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Analytics,
                label = "Shannon H₁",
                value = String.format(Locale.US, "%.3f b/B", result.overallEntropy),
                accentColor = MaterialTheme.colorScheme.primary
            )
            M3TelemetryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                label = "Min H∞",
                value = String.format(Locale.US, "%.3f b/B", result.minEntropy),
                accentColor = MaterialTheme.colorScheme.tertiary
            )
            M3TelemetryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Functions,
                label = "Rényi H₂",
                value = String.format(Locale.US, "%.3f b/B", result.renyiEntropy),
                accentColor = MaterialTheme.colorScheme.secondary
            )
            M3TelemetryStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bolt,
                label = "Speed",
                value = String.format(Locale.US, "%.0f MB/s", result.throughputMbPerSec),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // M3 Control Card: Window Size & Spectral Colormap FilterChips
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Window Size",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(64 to "64 B", 256 to "256 B", 1024 to "1 KB", 4096 to "4 KB").forEach { (size, label) ->
                            val active = windowSize == size
                            FilterChip(
                                selected = active,
                                onClick = { onWindowSizeChange(size) },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = if (active) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Palette",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpectraColorPalette.entries.forEach { pal ->
                            val active = colorPalette == pal
                            FilterChip(
                                selected = active,
                                onClick = { onPaletteChange(pal) },
                                label = {
                                    Text(
                                        text = pal.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = if (active) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.ColorLens,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // 2D SHANNON ENTROPY SPECTROGRAM MATRIX CARD
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "2D Shannon Entropy Spectrogram",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Touch or drag across ${blocks.size} blocks (0x00000000 – 0x${String.format(Locale.US, "%08X", result.totalFileSize)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                val gridCols = 32
                val gridRows = maxOf(1, (blocks.size + gridCols - 1) / gridCols)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040710))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .pointerInput(blocks.size, gridRows) {
                            detectTapGestures { pos ->
                                if (blocks.isNotEmpty() && size.width > 0 && size.height > 0) {
                                    val c = ((pos.x / size.width) * gridCols).toInt().coerceIn(0, gridCols - 1)
                                    val r = ((pos.y / size.height) * gridRows).toInt().coerceIn(0, gridRows - 1)
                                    val idx = (r * gridCols + c).coerceIn(0, blocks.size - 1)
                                    onSelectBlock(idx)
                                }
                            }
                        }
                        .pointerInput(blocks.size, gridRows) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val pos = change.position
                                if (blocks.isNotEmpty() && size.width > 0 && size.height > 0) {
                                    val c = ((pos.x / size.width) * gridCols).toInt().coerceIn(0, gridCols - 1)
                                    val r = ((pos.y / size.height) * gridRows).toInt().coerceIn(0, gridRows - 1)
                                    val idx = (r * gridCols + c).coerceIn(0, blocks.size - 1)
                                    onSelectBlock(idx)
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (blocks.isEmpty()) return@Canvas
                        val cellW = size.width / gridCols
                        val cellH = size.height / gridRows

                        for (i in blocks.indices) {
                            val r = i / gridCols
                            val c = i % gridCols
                            val block = blocks[i]
                            val norm = (block.shannonEntropy / 8.0f).coerceIn(0f, 1f)
                            val color = mapEntropyToColor(norm, colorPalette)
                            drawRect(
                                color = color,
                                topLeft = Offset(c * cellW, r * cellH),
                                size = Size(cellW + 0.6f, cellH + 0.6f)
                            )
                        }

                        val selR = safeIndex / gridCols
                        val selC = safeIndex % gridCols
                        val cx = (selC + 0.5f) * cellW
                        val cy = (selR + 0.5f) * cellH

                        drawLine(
                            color = Color.White.copy(alpha = 0.65f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.65f),
                            start = Offset(0f, cy),
                            end = Offset(size.width, cy),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(selC * cellW, selR * cellH),
                            size = Size(maxOf(cellW, 5f), maxOf(cellH, 5f)),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Colorbar Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.0b (Null)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = (0..10).map { step ->
                                        mapEntropyToColor(step / 10f, colorPalette)
                                    }
                                )
                            )
                    )
                    Text(
                        text = "8.0b (Encrypted)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1D SLIDING-WINDOW SHANNON ENTROPY + MIN-ENTROPY DUAL CURVE CARD
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sliding-Window Entropy Curve",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dual H₁(x) Shannon & H∞(x) Min-Entropy profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("── H₁", color = Color(0xFF00F0FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("── H∞", color = Color(0xFFFF2E93), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040710))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .pointerInput(blocks.size) {
                            detectTapGestures { pos ->
                                if (blocks.isNotEmpty() && size.width > 0) {
                                    val idx = ((pos.x / size.width) * blocks.size).toInt().coerceIn(0, blocks.size - 1)
                                    onSelectBlock(idx)
                                }
                            }
                        }
                        .pointerInput(blocks.size) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (blocks.isNotEmpty() && size.width > 0) {
                                    val idx = ((change.position.x / size.width) * blocks.size).toInt().coerceIn(0, blocks.size - 1)
                                    onSelectBlock(idx)
                                }
                            }
                        }
                ) {
                    val accentCyan = Color(0xFF00F0FF)
                    val accentMagenta = Color(0xFFFF2E93)
                    val accentEmerald = Color(0xFF10B981)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (blocks.isEmpty()) return@Canvas
                        val w = size.width
                        val h = size.height

                        val y72 = h * (1f - 7.2f / 8f)
                        val y50 = h * (1f - 5.0f / 8f)
                        drawLine(
                            color = accentMagenta.copy(alpha = 0.28f),
                            start = Offset(0f, y72),
                            end = Offset(w, y72),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = accentEmerald.copy(alpha = 0.25f),
                            start = Offset(0f, y50),
                            end = Offset(w, y50),
                            strokeWidth = 1.dp.toPx()
                        )

                        val shannonPath = Path()
                        val minEntropyPath = Path()
                        val fillPath = Path()
                        fillPath.moveTo(0f, h)

                        val stepCount = blocks.size
                        for (i in 0 until stepCount) {
                            val x = if (stepCount > 1) (i.toFloat() / (stepCount - 1)) * w else w / 2f
                            val yS = h * (1f - (blocks[i].shannonEntropy / 8f).coerceIn(0f, 1f))
                            val yM = h * (1f - (blocks[i].minEntropy / 8f).coerceIn(0f, 1f))
                            if (i == 0) {
                                shannonPath.moveTo(x, yS)
                                minEntropyPath.moveTo(x, yM)
                            } else {
                                shannonPath.lineTo(x, yS)
                                minEntropyPath.lineTo(x, yM)
                            }
                            fillPath.lineTo(x, yS)
                        }
                        fillPath.lineTo(w, h)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentCyan.copy(alpha = 0.32f),
                                    accentMagenta.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        drawPath(
                            path = minEntropyPath,
                            color = accentMagenta.copy(alpha = 0.75f),
                            style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = shannonPath,
                            color = accentCyan,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        val selX = if (stepCount > 1) (safeIndex.toFloat() / (stepCount - 1)) * w else w / 2f
                        val selY = h * (1f - (blocks[safeIndex].shannonEntropy / 8f).coerceIn(0f, 1f))
                        drawLine(
                            color = Color.White,
                            start = Offset(selX, 0f),
                            end = Offset(selX, h),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawCircle(
                            color = accentMagenta,
                            radius = 5.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(selX, selY)
                        )
                    }
                }
            }
        }

        // SELECTED BLOCK DEEP INSPECTOR + LIVE HEX SLICE PREVIEW (M3 OutlinedCard)
        if (currentBlock != null) {
            val classification = classifyEntropyValue(
                currentBlock.shannonEntropy,
                currentBlock.printableRatio,
                currentBlock.zeroRatio
            )
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, classification.second.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = classification.second.copy(alpha = 0.18f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = classification.second,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Block #%d • 0x%08X – 0x%08X",
                                        currentBlock.index,
                                        currentBlock.startOffset,
                                        currentBlock.endOffset
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = classification.first,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = classification.second,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { onJumpToHexOffset(currentBlock.startOffset) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("jump_to_hex_offset_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open in Hex",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        M3TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.GraphicEq,
                            label = "Block H₁",
                            value = String.format(Locale.US, "%.3f b/B", currentBlock.shannonEntropy),
                            accentColor = classification.second
                        )
                        M3TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Security,
                            label = "Min H∞",
                            value = String.format(Locale.US, "%.3f b/B", currentBlock.minEntropy),
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                        M3TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TextFields,
                            label = "ASCII %",
                            value = String.format(Locale.US, "%.0f%%", currentBlock.printableRatio * 100f),
                            accentColor = MaterialTheme.colorScheme.secondary
                        )
                        M3TelemetryStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.ExposureZero,
                            label = "Null 0x00",
                            value = String.format(Locale.US, "%.0f%%", currentBlock.zeroRatio * 100f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    val previewStart = currentBlock.bufferOffset.coerceIn(0, maxOf(0, rawBytes.size - 1))
                    val previewLen = min(48, rawBytes.size - previewStart)
                    if (previewLen > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Live Block Hex + ASCII Slice",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                for (rowStart in 0 until previewLen step 8) {
                                    val rowEnd = min(rowStart + 8, previewLen)
                                    val slice = rawBytes.sliceArray((previewStart + rowStart) until (previewStart + rowEnd))
                                    val hexStr = slice.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
                                    val asciiStr = slice.map { b ->
                                        val v = b.toInt() and 0xFF
                                        if (v in 32..126) v.toChar() else '.'
                                    }.joinToString("")
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%08X  %-23s  |%s|",
                                            currentBlock.startOffset + rowStart,
                                            hexStr,
                                            asciiStr
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseSpaceAndDistributionView(
    result: SpectraAnalysisResult,
    rawBytes: ByteArray,
    bitPlaneMode: Boolean,
    onToggleBitPlaneMode: (Boolean) -> Unit,
    colorPalette: SpectraColorPalette
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Statistical Randomness & Cryptanalysis Telemetry Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shannon, Chi-Square & Monte Carlo Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Functions,
                        label = "Chi-Square χ²",
                        value = String.format(Locale.US, "%.1f", result.chiSquare),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DataUsage,
                        label = "Monte Carlo π",
                        value = String.format(Locale.US, "%.4f", result.monteCarloPi),
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Hub,
                        label = "Coincidence IC",
                        value = String.format(Locale.US, "%.5f", result.indexOfCoincidence),
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CompareArrows,
                        label = "Serial Correl.",
                        value = String.format(Locale.US, "%.5f", result.serialCorrelation),
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Calculate,
                        label = "Mean / 127.5",
                        value = String.format(Locale.US, "%.2f", result.arithmeticMean),
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    M3TelemetryStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Compress,
                        label = "Compressible",
                        value = "${result.theoreticalCompressionPct}%",
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // 8-Bit Plane Entropy & Bit Bias Analyzer Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "8-Bit Plane Balance & Hamming Density",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bit-0 (LSB) to Bit-7 (MSB) 1-bit distribution (ideal random = 50%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (bit in 0 until 8) {
                        val pct = result.bitPlaneOnesPct[bit]
                        val dev = abs(pct - 50f)
                        val barColor = when {
                            dev < 2.5f -> MaterialTheme.colorScheme.primary
                            dev < 10f -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight((pct / 100f).coerceIn(0.04f, 1f))
                                        .background(barColor)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.0f%%", pct),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "B$bit",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2D Digram Phase-Space Attractor / Raw Byte Pixel Matrix Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BlurOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (bitPlaneMode) "Raw Byte-Plane Pixel Map" else "2D Digram Phase-Space Attractor",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (bitPlaneMode) "Direct 2D spatial byte intensity rendering" else "Adjacent byte-pair transition density B[i] vs B[i+1]",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    FilterChip(
                        selected = bitPlaneMode,
                        onClick = { onToggleBitPlaneMode(!bitPlaneMode) },
                        label = {
                            Text(
                                text = if (bitPlaneMode) "Digram View" else "Byte Map",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (bitPlaneMode) Icons.Default.ScatterPlot else Icons.Default.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040710))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val dim = 64
                        val cellW = size.width / dim
                        val cellH = size.height / dim

                        if (!bitPlaneMode) {
                            val maxCount = maxOf(1, result.maxDigramCount)
                            val logMax = NativeLutEntropyCore.fastLog2(maxCount + 1).coerceAtLeast(1f)
                            for (y in 0 until dim) {
                                for (x in 0 until dim) {
                                    val count = result.digramMatrix[y * dim + x]
                                    if (count > 0) {
                                        val intensity = (NativeLutEntropyCore.fastLog2(count + 1) / logMax).coerceIn(0f, 1f)
                                        drawRect(
                                            color = mapEntropyToColor(intensity, colorPalette),
                                            topLeft = Offset(x * cellW, y * cellH),
                                            size = Size(cellW + 0.5f, cellH + 0.5f)
                                        )
                                    }
                                }
                            }
                        } else {
                            val totalCells = dim * dim
                            val stride = maxOf(1, rawBytes.size / totalCells)
                            for (i in 0 until totalCells) {
                                val byteIdx = i * stride
                                if (byteIdx >= rawBytes.size) break
                                val r = i / dim
                                val c = i % dim
                                val bVal = (rawBytes[byteIdx].toInt() and 0xFF) / 255f
                                drawRect(
                                    color = mapEntropyToColor(bVal, colorPalette),
                                    topLeft = Offset(c * cellW, r * cellH),
                                    size = Size(cellW + 0.5f, cellH + 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 256-Bin Byte Frequency Histogram Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "256-Byte Frequency Spectrum Histogram (0x00 – 0xFF)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                val maxFreq = remember(result.byteHistogram) {
                    maxOf(1, result.byteHistogram.maxOrNull() ?: 1)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040710))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barW = size.width / 256f
                        val logMax = NativeLutEntropyCore.fastLog2(maxFreq + 1).coerceAtLeast(1f)
                        for (b in 0 until 256) {
                            val count = result.byteHistogram[b]
                            if (count > 0) {
                                val norm = (NativeLutEntropyCore.fastLog2(count + 1) / logMax).coerceIn(0f, 1f)
                                val barH = norm * size.height
                                val barColor = when {
                                    b == 0x00 -> Color(0xFFFFB800)
                                    b in 0x20..0x7E -> Color(0xFF10B981)
                                    b == 0xFF -> Color(0xFFFF2E93)
                                    else -> Color(0xFF00F0FF)
                                }
                                drawRect(
                                    color = barColor,
                                    topLeft = Offset(b * barW, size.height - barH),
                                    size = Size(maxOf(1.2f, barW), barH)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("■ 0x00 Null", color = Color(0xFFFFB800), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("■ 0x20..7E ASCII", color = Color(0xFF10B981), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("■ Binary/Opcodes", color = Color(0xFF00F0FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("■ 0xFF Pad", color = Color(0xFFFF2E93), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun AnomalyScannerView(
    result: SpectraAnalysisResult,
    onJumpToHexOffset: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Automated Entropy Region Segmentation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Segmented ${result.anomalies.size} structural & cryptographic zones across ${formatByteCount(result.totalFileSize)}. Tap any zone to inspect in Hex.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(result.anomalies) { anomaly ->
            OutlinedCard(
                onClick = { onJumpToHexOffset(anomaly.startOffset) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, anomaly.severityColor.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = anomaly.severityColor.copy(alpha = 0.18f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WaterfallChart,
                                        contentDescription = null,
                                        tint = anomaly.severityColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = anomaly.category,
                                        color = anomaly.severityColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "0x%08X – 0x%08X",
                                    anomaly.startOffset,
                                    anomaly.endOffset
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = anomaly.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "Size: %s • Avg H₁: %.3f b/B • Peak H₁: %.3f b/B",
                                formatByteCount(anomaly.endOffset - anomaly.startOffset),
                                anomaly.avgEntropy,
                                anomaly.peakEntropy
                            ),
                            color = anomaly.severityColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalIconButton(
                        onClick = { onJumpToHexOffset(anomaly.startOffset) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Jump to Hex"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForensicHashesAndSignaturesView(
    result: SpectraAnalysisResult,
    onCopyText: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Deep Binary Container & Header Dissection Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeveloperBoard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Container & Header Dissection",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.magicSignature,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                result.magicDetails.forEach { (k, v) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = k,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = v,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Cryptographic Digests & Checksums Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cryptographic Digests & Checksums",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap any digest card to copy to clipboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                listOf(
                    "SHA-256" to result.sha256,
                    "SHA-1" to result.sha1,
                    "MD5" to result.md5,
                    "CRC-32" to result.crc32,
                    "ADLER-32" to result.adler32
                ).forEach { (label, digest) ->
                    OutlinedCard(
                        onClick = { onCopyText(label, digest) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = digest,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { onCopyText(label, digest) }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy $label",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StringsAndDisassemblyView(
    result: SpectraAnalysisResult,
    rawBytes: ByteArray,
    bufferOffset: Int,
    displayOffset: Long,
    stringQuery: String,
    onStringQueryChange: (String) -> Unit,
    minStringLen: Int,
    onMinStringLenChange: (Int) -> Unit,
    disasmArch: String,
    onDisasmArchChange: (String) -> Unit,
    onJumpToHexOffset: (Long) -> Unit
) {
    val filteredStrings = remember(result.extractedStrings, stringQuery) {
        if (stringQuery.isBlank()) result.extractedStrings
        else result.extractedStrings.filter { it.value.contains(stringQuery, ignoreCase = true) }
    }

    val disassembledLines = remember(rawBytes, bufferOffset, displayOffset, disasmArch) {
        disassembleSlice(
            bytes = rawBytes,
            startBufferOffset = bufferOffset.coerceIn(0, maxOf(0, rawBytes.size - 1)),
            baseDisplayOffset = displayOffset,
            arch = disasmArch
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = String.format(Locale.US, "Disassembler @ 0x%08X", displayOffset),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Live opcode decode at spectrogram crosshair",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("DEX", "ARM64", "x86_64").forEach { arch ->
                                val active = disasmArch == arch
                                FilterChip(
                                    selected = active,
                                    onClick = { onDisasmArchChange(arch) },
                                    label = {
                                        Text(
                                            text = arch,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            disassembledLines.forEach { (off, hexBytes, mnemonic) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToHexOffset(off) }
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "0x%08X", off),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = hexBytes,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = mnemonic,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TextSnippet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Extracted Strings (${filteredStrings.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(4, 6, 8, 12).forEach { len ->
                                val active = minStringLen == len
                                FilterChip(
                                    selected = active,
                                    onClick = { onMinStringLenChange(len) },
                                    label = {
                                        Text(
                                            text = "≥${len}c",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = stringQuery,
                        onValueChange = onStringQueryChange,
                        placeholder = { Text("Filter classes, methods, URLs, symbols...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (stringQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onStringQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(filteredStrings.take(150)) { item ->
            OutlinedCard(
                onClick = { onJumpToHexOffset(item.offset) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.US, "0x%08X", item.offset),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = item.category,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Jump to Offset",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun M3TelemetryStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun mapEntropyToColor(norm: Float, palette: SpectraColorPalette): Color {
    val t = norm.coerceIn(0f, 1f)
    return when (palette) {
        SpectraColorPalette.PLASMA -> {
            when {
                t < 0.25f -> lerpColor(Color(0xFF090628), Color(0xFF4B0C6B), t / 0.25f)
                t < 0.50f -> lerpColor(Color(0xFF4B0C6B), Color(0xFFB63679), (t - 0.25f) / 0.25f)
                t < 0.75f -> lerpColor(Color(0xFFB63679), Color(0xFFFB8861), (t - 0.50f) / 0.25f)
                else -> lerpColor(Color(0xFFFB8861), Color(0xFFFCFDBF), (t - 0.75f) / 0.25f)
            }
        }
        SpectraColorPalette.CYBER_NEON -> {
            when {
                t < 0.33f -> lerpColor(Color(0xFF050B1A), Color(0xFF0284C7), t / 0.33f)
                t < 0.66f -> lerpColor(Color(0xFF0284C7), Color(0xFF00F0FF), (t - 0.33f) / 0.33f)
                else -> lerpColor(Color(0xFF00F0FF), Color(0xFFFF2E93), (t - 0.66f) / 0.34f)
            }
        }
        SpectraColorPalette.VIRIDIS -> {
            when {
                t < 0.33f -> lerpColor(Color(0xFF0D0829), Color(0xFF1F6E8C), t / 0.33f)
                t < 0.66f -> lerpColor(Color(0xFF1F6E8C), Color(0xFF10B981), (t - 0.33f) / 0.33f)
                else -> lerpColor(Color(0xFF10B981), Color(0xFFFDE725), (t - 0.66f) / 0.34f)
            }
        }
        SpectraColorPalette.THERMAL -> {
            when {
                t < 0.33f -> lerpColor(Color(0xFF08080C), Color(0xFF7F1D1D), t / 0.33f)
                t < 0.66f -> lerpColor(Color(0xFF7F1D1D), Color(0xFFF97316), (t - 0.33f) / 0.33f)
                else -> lerpColor(Color(0xFFF97316), Color(0xFFFFFFFF), (t - 0.66f) / 0.34f)
            }
        }
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = 1f
    )
}

private fun classifyEntropyValue(entropy: Float, printableRatio: Float, zeroRatio: Float): Pair<String, Color> {
    return when {
        zeroRatio > 0.80f -> "ZERO / SPARSE PADDING REGION" to Color(0xFFFFB800)
        entropy >= 7.25f -> "ENCRYPTED / PACKED / COMPRESSED PAYLOAD" to Color(0xFFFF2E93)
        printableRatio >= 0.60f -> "ASCII / UTF-8 STRING & SYMBOL POOL" to Color(0xFF10B981)
        entropy >= 4.75f -> "EXECUTABLE BYTECODE / NATIVE CODE" to Color(0xFF00F0FF)
        else -> "STRUCTURED HEADER / OFFSET TABLE" to Color(0xFFA855F7)
    }
}

private fun computeSpectraForensicsNativeLut(
    bytes: ByteArray,
    fileName: String,
    sourceLabel: String,
    totalFileSize: Long,
    patchCount: Int,
    windowSize: Int,
    minStringLen: Int
): SpectraAnalysisResult {
    val t0 = System.nanoTime()
    val n = bytes.size
    val hist = IntArray(256)
    val digram = IntArray(64 * 64)
    val bitCounts = IntArray(8)
    var maxDigram = 0
    var byteSum = 0L

    for (i in 0 until n) {
        val b = bytes[i].toInt() and 0xFF
        hist[b]++
        byteSum += b
        if (i + 1 < n) {
            val bNext = bytes[i + 1].toInt() and 0xFF
            val idx = ((bNext ushr 2) shl 6) or (b ushr 2)
            val c = digram[idx] + 1
            digram[idx] = c
            if (c > maxDigram) maxDigram = c
        }
    }

    for (b in 0 until 256) {
        val count = hist[b]
        if (count > 0) {
            if ((b and 0x01) != 0) bitCounts[0] += count
            if ((b and 0x02) != 0) bitCounts[1] += count
            if ((b and 0x04) != 0) bitCounts[2] += count
            if ((b and 0x08) != 0) bitCounts[3] += count
            if ((b and 0x10) != 0) bitCounts[4] += count
            if ((b and 0x20) != 0) bitCounts[5] += count
            if ((b and 0x40) != 0) bitCounts[6] += count
            if ((b and 0x80) != 0) bitCounts[7] += count
        }
    }
    val bitPlaneOnesPct = FloatArray(8) { bit ->
        if (n > 0) (bitCounts[bit].toFloat() * 100f) / n.toFloat() else 0f
    }

    var overallH = 0.0
    var maxProb = 0.0
    var sumProbSq = 0.0
    var icNumerator = 0.0
    val invLn2 = 1.0 / ln(2.0)
    val expectedPerBin = n.toDouble() / 256.0
    var chiSq = 0.0

    for (count in hist) {
        if (count > 0) {
            val p = count.toDouble() / n
            overallH -= p * ln(p) * invLn2
            if (p > maxProb) maxProb = p
            sumProbSq += p * p
            icNumerator += count.toDouble() * (count - 1).toDouble()
        }
        if (expectedPerBin > 0.0) {
            val diff = count - expectedPerBin
            chiSq += (diff * diff) / expectedPerBin
        }
    }

    val minEntropy = if (maxProb > 0.0) (-ln(maxProb) * invLn2).toFloat() else 0f
    val renyiEntropy = if (sumProbSq > 0.0) (-ln(sumProbSq) * invLn2).toFloat() else 0f
    val indexOfCoincidence = if (n > 1) icNumerator / (n.toDouble() * (n - 1).toDouble()) else 0.0

    var insideCircle = 0
    var totalPairs = 0
    val radiusSq = 256.0 * 256.0
    var k = 0
    while (k + 1 < n) {
        val x = (bytes[k].toInt() and 0xFF).toDouble()
        val y = (bytes[k + 1].toInt() and 0xFF).toDouble()
        if (x * x + y * y <= radiusSq) insideCircle++
        totalPairs++
        k += 2
    }
    val mcPi = if (totalPairs > 0) 4.0 * insideCircle.toDouble() / totalPairs else 0.0
    val mcErr = if (totalPairs > 0) abs(mcPi - PI) / PI * 100.0 else 0.0

    val mean = if (n > 0) byteSum.toDouble() / n else 0.0
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        val d0 = (bytes[i].toInt() and 0xFF) - mean
        val d1 = (bytes[(i + 1) % n].toInt() and 0xFF) - mean
        num += d0 * d1
        den += d0 * d0
    }
    val serialCorr = if (den > 1e-9) num / den else 0.0

    val effectiveStep = maxOf(windowSize, (n / 1024).coerceAtLeast(64))
    val blocks = ArrayList<EntropyBlockInfo>(1024)
    val localCounts = IntArray(256)
    var blockIdx = 0
    var bufOff = 0
    val scaleFactor = if (n > 0) totalFileSize.toDouble() / n.toDouble() else 1.0

    while (bufOff < n) {
        val end = min(n, bufOff + effectiveStep)
        val len = end - bufOff
        java.util.Arrays.fill(localCounts, 0)
        var printable = 0
        var zeros = 0
        var highBytes = 0
        var localSum = 0

        for (i in bufOff until end) {
            val v = bytes[i].toInt() and 0xFF
            localCounts[v]++
            localSum += v
            if (v == 0) zeros++
            else if (v in 32..126) printable++
            else if (v >= 128) highBytes++
        }

        val logLen = NativeLutEntropyCore.fastLog2(len)
        var sumCLogC = 0f
        var maxCountInBlock = 0
        for (c in localCounts) {
            if (c > 0) {
                sumCLogC += c * NativeLutEntropyCore.fastLog2(c)
                if (c > maxCountInBlock) maxCountInBlock = c
            }
        }
        val h1 = (logLen - (sumCLogC / len)).coerceIn(0f, 8f)
        val hMin = if (maxCountInBlock > 0) {
            (logLen - NativeLutEntropyCore.fastLog2(maxCountInBlock)).coerceIn(0f, 8f)
        } else 0f

        val physStart = (bufOff * scaleFactor).toLong()
        val physEnd = minOf(totalFileSize, (end * scaleFactor).toLong())

        blocks.add(
            EntropyBlockInfo(
                index = blockIdx++,
                startOffset = physStart,
                endOffset = maxOf(physStart + len, physEnd),
                bufferOffset = bufOff,
                shannonEntropy = h1,
                minEntropy = hMin,
                printableRatio = printable.toFloat() / len,
                zeroRatio = zeros.toFloat() / len,
                highByteRatio = highBytes.toFloat() / len,
                meanByte = localSum.toFloat() / len
            )
        )
        bufOff += effectiveStep
    }

    val anomalies = ArrayList<EntropyRegionAnomaly>()
    if (blocks.isNotEmpty()) {
        var curStart = blocks[0].startOffset
        var curEnd = blocks[0].endOffset
        var curCat = classifyEntropyValue(blocks[0].shannonEntropy, blocks[0].printableRatio, blocks[0].zeroRatio)
        var sumH = blocks[0].shannonEntropy
        var peakH = blocks[0].shannonEntropy
        var countB = 1

        for (i in 1 until blocks.size) {
            val b = blocks[i]
            val cat = classifyEntropyValue(b.shannonEntropy, b.printableRatio, b.zeroRatio)
            if (cat.first == curCat.first) {
                curEnd = b.endOffset
                sumH += b.shannonEntropy
                if (b.shannonEntropy > peakH) peakH = b.shannonEntropy
                countB++
            } else {
                anomalies.add(
                    EntropyRegionAnomaly(
                        startOffset = curStart,
                        endOffset = curEnd,
                        avgEntropy = sumH / countB,
                        peakEntropy = peakH,
                        category = curCat.first,
                        description = describeCategory(curCat.first),
                        severityColor = curCat.second
                    )
                )
                curStart = b.startOffset
                curEnd = b.endOffset
                curCat = cat
                sumH = b.shannonEntropy
                peakH = b.shannonEntropy
                countB = 1
            }
        }
        anomalies.add(
            EntropyRegionAnomaly(
                startOffset = curStart,
                endOffset = curEnd,
                avgEntropy = sumH / countB,
                peakEntropy = peakH,
                category = curCat.first,
                description = describeCategory(curCat.first),
                severityColor = curCat.second
            )
        )
    }

    val md5 = hashDigest(bytes, "MD5")
    val sha1 = hashDigest(bytes, "SHA-1")
    val sha256 = hashDigest(bytes, "SHA-256")
    val crc = CRC32().apply { update(bytes) }.value
    val adler = Adler32().apply { update(bytes) }.value

    val (magicSig, magicDetails) = detectDeepContainerSignature(bytes, totalFileSize)
    val extractedStrings = extractBinaryStrings(bytes, minStringLen)
    val compressionPct = ((1.0 - (overallH / 8.0)) * 100.0).toInt().coerceIn(0, 100)

    val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
    val throughputMb = if (elapsedMs > 0.01) (n / (1024.0 * 1024.0)) / (elapsedMs / 1000.0) else 500.0

    return SpectraAnalysisResult(
        fileName = fileName,
        sourceLabel = sourceLabel,
        totalFileSize = totalFileSize,
        analyzedBytesCount = n,
        patchCount = patchCount,
        computeTimeMs = elapsedMs,
        throughputMbPerSec = throughputMb,
        overallEntropy = overallH.toFloat(),
        minEntropy = minEntropy,
        renyiEntropy = renyiEntropy,
        indexOfCoincidence = indexOfCoincidence,
        chiSquare = chiSq,
        monteCarloPi = mcPi,
        monteCarloErrorPct = mcErr,
        serialCorrelation = serialCorr,
        arithmeticMean = mean,
        theoreticalCompressionPct = compressionPct,
        bitPlaneOnesPct = bitPlaneOnesPct,
        byteHistogram = hist,
        digramMatrix = digram,
        maxDigramCount = maxDigram,
        blocks = blocks,
        anomalies = anomalies,
        md5 = md5,
        sha1 = sha1,
        sha256 = sha256,
        crc32 = String.format(Locale.US, "0x%08X (%d)", crc, crc),
        adler32 = String.format(Locale.US, "0x%08X (%d)", adler, adler),
        magicSignature = magicSig,
        magicDetails = magicDetails,
        extractedStrings = extractedStrings
    )
}

private fun describeCategory(cat: String): String = when {
    cat.contains("ENCRYPTED") -> "High byte randomness (H ≥ 7.25). Characteristic of AES/ChaCha ciphertext, DEFLATE/LZMA compressed streams, or packed payloads."
    cat.contains("STRING") -> "High printable UTF-8/ASCII density. Contains DEX string pools, class/method descriptors, or manifest tables."
    cat.contains("ZERO") -> "Contiguous null-byte alignment padding or uninitialized memory cave."
    cat.contains("EXECUTABLE") -> "Moderate-high entropy characteristic of compiled Dalvik bytecode or native ARM64/x86_64 instructions."
    else -> "Structured binary header, index offsets, or relocation tables."
}

private fun hashDigest(bytes: ByteArray, algorithm: String): String {
    return try {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        digest.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
    } catch (_: Exception) {
        "N/A"
    }
}

private fun detectDeepContainerSignature(bytes: ByteArray, totalSize: Long): Pair<String, List<Pair<String, String>>> {
    if (bytes.size < 4) return "Raw Binary Stream" to listOf("File Size" to "$totalSize Bytes")
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    val magicHex = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3)

    if (b0 == 0x64 && b1 == 0x65 && b2 == 0x78 && b3 == 0x0A && bytes.size >= 0x70) {
        val bb = ByteBuffer.wrap(bytes, 0, 0x70).order(ByteOrder.LITTLE_ENDIAN)
        val versionStr = String(bytes, 4, 3, Charsets.US_ASCII)
        val checksum = bb.getInt(0x08).toLong() and 0xFFFFFFFFL
        val sigBytes = bytes.sliceArray(0x0C until 0x20).joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
        val dexFileSize = bb.getInt(0x20).toLong() and 0xFFFFFFFFL
        val headerSize = bb.getInt(0x24).toLong() and 0xFFFFFFFFL
        val endianTag = bb.getInt(0x28).toLong() and 0xFFFFFFFFL
        val stringIdsSize = bb.getInt(0x38).toLong() and 0xFFFFFFFFL
        val stringIdsOff = bb.getInt(0x3C).toLong() and 0xFFFFFFFFL
        val typeIdsSize = bb.getInt(0x40).toLong() and 0xFFFFFFFFL
        val protoIdsSize = bb.getInt(0x48).toLong() and 0xFFFFFFFFL
        val fieldIdsSize = bb.getInt(0x50).toLong() and 0xFFFFFFFFL
        val methodIdsSize = bb.getInt(0x58).toLong() and 0xFFFFFFFFL
        val classDefsSize = bb.getInt(0x60).toLong() and 0xFFFFFFFFL
        val dataSize = bb.getInt(0x68).toLong() and 0xFFFFFFFFL
        val dataOff = bb.getInt(0x6C).toLong() and 0xFFFFFFFFL

        return "Android Dalvik Executable (DEX v$versionStr)" to listOf(
            "Magic & Version" to "dex\\n$versionStr\\0 ($magicHex)",
            "Adler32 Header Checksum" to String.format(Locale.US, "0x%08X", checksum),
            "Embedded SHA-1 Signature" to "${sigBytes.take(20)}...",
            "Declared DEX File Size" to "${formatByteCount(dexFileSize)} ($dexFileSize B)",
            "Header Size & Endian Tag" to String.format(Locale.US, "0x%X B • Tag 0x%08X", headerSize, endianTag),
            "String IDs Pool" to String.format(Locale.US, "%d strings @ 0x%08X", stringIdsSize, stringIdsOff),
            "Type & Proto Descriptors" to "$typeIdsSize types • $protoIdsSize protos",
            "Fields & Methods Count" to "$fieldIdsSize fields • $methodIdsSize methods",
            "Class Definitions Count" to "$classDefsSize classes",
            "Bytecode & Data Section" to String.format(Locale.US, "%s @ 0x%08X", formatByteCount(dataSize), dataOff)
        )
    }

    if (b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46 && bytes.size >= 0x40) {
        val is64 = (bytes[4].toInt() and 0xFF) == 2
        val isLe = (bytes[5].toInt() and 0xFF) == 1
        val bb = ByteBuffer.wrap(bytes, 0, min(bytes.size, 64))
            .order(if (isLe) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val eType = bb.getShort(0x10).toInt() and 0xFFFF
        val eMachine = bb.getShort(0x12).toInt() and 0xFFFF
        val machineName = when (eMachine) {
            0xB7 -> "ARM64 (AArch64)"
            0x28 -> "ARM32"
            0x3E -> "x86_64 (AMD64)"
            0x03 -> "x86 (i386)"
            0xF3 -> "RISC-V"
            else -> "ISA 0x${Integer.toHexString(eMachine)}"
        }
        val entryPoint = if (is64) bb.getLong(0x18) else (bb.getInt(0x18).toLong() and 0xFFFFFFFFL)
        return "ELF ${if (is64) "64-Bit" else "32-Bit"} Native Binary ($machineName)" to listOf(
            "Magic Bytes" to magicHex,
            "Class & Endianness" to "${if (is64) "ELF64" else "ELF32"} • ${if (isLe) "Little-Endian" else "Big-Endian"}",
            "Target Instruction Set" to machineName,
            "Object File Type" to when (eType) { 2 -> "ET_EXEC (Executable)"; 3 -> "ET_DYN (Shared Object .so)"; else -> "Type $eType" },
            "Entry Point Address" to String.format(Locale.US, "0x%08X", entryPoint),
            "Total File Size" to "${formatByteCount(totalSize)} ($totalSize B)"
        )
    }

    if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
        return "ZIP / APK / JAR Archive Container" to listOf(
            "Magic Signature" to "PK\\x03\\x04 ($magicHex)",
            "Container Type" to "DEFLATE / Stored Archive Stream",
            "Total File Size" to "${formatByteCount(totalSize)} ($totalSize B)"
        )
    }

    return "Binary Data Stream ($magicHex)" to listOf(
        "First 4 Magic Bytes" to magicHex,
        "Total File Size" to "${formatByteCount(totalSize)} ($totalSize B)"
    )
}

private fun extractBinaryStrings(bytes: ByteArray, minLen: Int): List<ExtractedBinaryString> {
    val list = ArrayList<ExtractedBinaryString>()
    val sb = StringBuilder()
    var startOffset = 0L
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xFF
        if (v in 32..126) {
            if (sb.isEmpty()) startOffset = i.toLong()
            if (sb.length < 160) sb.append(v.toChar())
        } else {
            if (sb.length >= minLen) {
                val s = sb.toString().trim()
                if (s.length >= minLen) {
                    val category = when {
                        s.startsWith("L") && s.contains("/") && s.endsWith(";") -> "DEX CLASS"
                        s.contains("http://") || s.contains("https://") || s.contains("/") -> "PATH / URI"
                        s.contains("_") || s.startsWith("Java_") || s.startsWith("lib") -> "SYMBOL"
                        else -> "ASCII"
                    }
                    list.add(ExtractedBinaryString(startOffset, s, category))
                    if (list.size >= 300) break
                }
            }
            sb.clear()
        }
    }
    return list
}

private fun disassembleSlice(
    bytes: ByteArray,
    startBufferOffset: Int,
    baseDisplayOffset: Long,
    arch: String
): List<Triple<Long, String, String>> {
    val rows = ArrayList<Triple<Long, String, String>>()
    val maxBytes = min(bytes.size - startBufferOffset, 48)
    if (maxBytes <= 0) return rows

    var off = startBufferOffset
    val end = startBufferOffset + maxBytes
    while (off < end) {
        val dispOff = baseDisplayOffset + (off - startBufferOffset)
        when (arch) {
            "DEX" -> {
                if (off + 2 > bytes.size) break
                val op = bytes[off].toInt() and 0xFF
                val arg = bytes[off + 1].toInt() and 0xFF
                val hex = String.format(Locale.US, "%02X %02X", op, arg)
                val mnemonic = when (op) {
                    0x00 -> "nop"
                    0x01 -> "move v${arg and 0xF}, v${arg ushr 4}"
                    0x0A -> "move-result v$arg"
                    0x0C -> "move-result-object v$arg"
                    0x0E -> "return-void"
                    0x0F -> "return v$arg"
                    0x11 -> "return-object v$arg"
                    0x12 -> "const/4 v${arg and 0xF}, #${arg ushr 4}"
                    0x13 -> "const/16 v$arg, #imm16"
                    0x1A -> "const-string v$arg, string@idx"
                    0x1C -> "const-class v$arg, type@idx"
                    0x22 -> "new-instance v$arg, type@idx"
                    0x28 -> "goto +$arg"
                    0x38 -> "if-eqz v$arg, +branch"
                    0x39 -> "if-nez v$arg, +branch"
                    0x54 -> "iget-object v${arg and 0xF}, v${arg ushr 4}, field@idx"
                    0x5B -> "iput-object v${arg and 0xF}, v${arg ushr 4}, field@idx"
                    0x6E -> "invoke-virtual {v${arg and 0xF}}, method@idx"
                    0x70 -> "invoke-direct {v${arg and 0xF}}, method@idx"
                    0x71 -> "invoke-static {v${arg and 0xF}}, method@idx"
                    else -> String.format(Locale.US, "dalvik.op 0x%02X, v%d", op, arg and 0xF)
                }
                rows.add(Triple(dispOff, hex, mnemonic))
                off += 2
            }
            "ARM64" -> {
                if (off + 4 > bytes.size) break
                val b0 = bytes[off].toInt() and 0xFF
                val b1 = bytes[off + 1].toInt() and 0xFF
                val b2 = bytes[off + 2].toInt() and 0xFF
                val b3 = bytes[off + 3].toInt() and 0xFF
                val insn = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                val hex = String.format(Locale.US, "%02X %02X %02X %02X", b0, b1, b2, b3)
                val rd = insn and 0x1F
                val rn = (insn ushr 5) and 0x1F
                val mnemonic = when {
                    insn == 0xD65F03C0.toInt() -> "RET"
                    insn == 0xD503201F.toInt() -> "NOP"
                    (insn and 0x7F000000) == 0x14000000 -> String.format(Locale.US, "B       #+0x%X", (insn and 0x03FFFFFF) shl 2)
                    (insn and 0xFF000000.toInt()) == 0x94000000.toInt() -> String.format(Locale.US, "BL      #+0x%X", (insn and 0x03FFFFFF) shl 2)
                    (insn and 0x7F800000) == 0x11000000 -> String.format(Locale.US, "ADD     X%d, X%d, #0x%X", rd, rn, (insn ushr 10) and 0xFFF)
                    (insn and 0x7F800000) == 0x51000000 -> String.format(Locale.US, "SUB     X%d, X%d, #0x%X", rd, rn, (insn ushr 10) and 0xFFF)
                    (insn and 0xFFC00000.toInt()) == 0xF9400000.toInt() -> String.format(Locale.US, "LDR     X%d, [X%d]", rd, rn)
                    (insn and 0xFFC00000.toInt()) == 0xF9000000.toInt() -> String.format(Locale.US, "STR     X%d, [X%d]", rd, rn)
                    else -> String.format(Locale.US, "DCD     0x%08X", insn)
                }
                rows.add(Triple(dispOff, hex, mnemonic))
                off += 4
            }
            else -> {
                val b0 = bytes[off].toInt() and 0xFF
                val b1 = bytes.getOrNull(off + 1)?.toInt()?.and(0xFF) ?: 0
                val (len, mnemonic) = when (b0) {
                    0x90 -> 1 to "nop"
                    0xC3 -> 1 to "ret"
                    0x55 -> 1 to "push    rbp"
                    0x5D -> 1 to "pop     rbp"
                    0xE8 -> 2 to String.format(Locale.US, "call    rel+0x%02X", b1)
                    0xEB -> 2 to String.format(Locale.US, "jmp     short +0x%02X", b1)
                    0x48 -> 2 to String.format(Locale.US, "rex.W   op 0x%02X", b1)
                    else -> 2 to String.format(Locale.US, "db      0x%02X, 0x%02X", b0, b1)
                }
                val actualLen = min(len, bytes.size - off)
                val hex = (0 until actualLen).joinToString(" ") {
                    String.format(Locale.US, "%02X", bytes[off + it].toInt() and 0xFF)
                }
                rows.add(Triple(dispOff, hex, mnemonic))
                off += maxOf(1, actualLen)
            }
        }
    }
    return rows
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
    } catch (_: Exception) {}
    return uri.lastPathSegment ?: "binary_file.bin"
}

private fun formatByteCount(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
