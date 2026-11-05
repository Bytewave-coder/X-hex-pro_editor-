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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.bytewave.HexEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DisassemblerSubTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DISASSEMBLER("Disassembler", Icons.Filled.Memory, Icons.Outlined.Memory),
    DECOMPILER("Decompiler (C/Java)", Icons.Filled.Code, Icons.Outlined.Code),
    CFG_GRAPH("Control Flow (CFG)", Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
    XREFS_SYMBOLS("XREFs & Strings", Icons.Filled.Hub, Icons.Outlined.Hub),
    HEADERS_EXPORT("ELF/DEX & Export", Icons.Filled.Description, Icons.Outlined.Description)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeDisassemblerAppScreen(
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

    var selectedUri by remember { mutableStateOf<Uri?>(initialTab?.fileUri) }
    var selectedFileName by remember { mutableStateOf(initialTab?.fileName ?: "") }
    var selectedExpectedSize by remember { mutableLongStateOf(initialTab?.fileSize ?: 0L) }
    var selectedPatches by remember { mutableStateOf<Map<Long, Byte>>(initialTab?.patches ?: emptyMap()) }

    var overrideArch by remember { mutableStateOf<CpuArchitecture?>(null) }
    var customCodeOffset by remember { mutableStateOf<Int?>(null) }
    var activeEmbeddedEntry by remember { mutableStateOf<EmbeddedApkEntry?>(null) }
    var analysisTrigger by remember { mutableIntStateOf(0) }

    var activeTab by remember { mutableStateOf(DisassemblerSubTab.DISASSEMBLER) }
    var isAnalyzing by remember { mutableStateOf(initialTab?.fileUri != null) }
    var analysisResult by remember { mutableStateOf<NativeDisassemblyResult?>(null) }
    var selectedInstructionIndex by remember { mutableIntStateOf(0) }
    var selectedFunctionIndex by remember { mutableIntStateOf(0) }
    var selectedBasicBlockId by remember { mutableIntStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    var showBookmarksOnly by remember { mutableStateOf(false) }
    var bookmarkedAddresses by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var instructionComments by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var renamedFunctions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var showImportSourceSheet by remember { mutableStateOf(false) }
    var showCommentDialogForAddress by remember { mutableStateOf<Long?>(null) }
    var showRenameDialogForFnId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.activeTabIndex, state.tabs.size) {
        val tab = state.activeTab ?: state.tabs.firstOrNull()
        if (tab != null && selectedUri == null) {
            selectedUri = tab.fileUri
            selectedFileName = tab.fileName
            selectedExpectedSize = tab.fileSize
            selectedPatches = tab.patches
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

            val resolvedName = resolveFileName(context, uri)
            val matchingTab = state.tabs.find { it.fileUri?.toString() == uri.toString() }
            selectedUri = uri
            selectedFileName = resolvedName
            selectedExpectedSize = matchingTab?.fileSize ?: 0L
            selectedPatches = matchingTab?.patches ?: emptyMap()
            overrideArch = null
            customCodeOffset = null
            activeEmbeddedEntry = null
            if (matchingTab == null) {
                viewModel.openFile(context, uri)
            }
            showImportSourceSheet = false
            analysisTrigger++
        }
    }

    LaunchedEffect(selectedUri, selectedPatches, overrideArch, customCodeOffset, activeEmbeddedEntry, analysisTrigger) {
        val targetUri = selectedUri
        if (targetUri == null) {
            analysisResult = null
            isAnalyzing = false
            return@LaunchedEffect
        }

        isAnalyzing = true
        try {
            val embedded = activeEmbeddedEntry
            if (embedded != null) {
                val computed = withContext(Dispatchers.Default) {
                    NativeDisassemblerEngine.analyzeBinary(
                        rawBytes = embedded.extractedBytes,
                        fileName = "${selectedFileName} :: ${embedded.path}",
                        totalFileSize = embedded.sizeBytes,
                        overrideArch = overrideArch ?: embedded.detectedArch,
                        customCodeOffset = customCodeOffset
                    )
                }
                analysisResult = computed
                selectedInstructionIndex = selectedInstructionIndex.coerceIn(0, maxOf(0, computed.instructions.size - 1))
                selectedFunctionIndex = selectedFunctionIndex.coerceIn(0, maxOf(0, computed.functions.size - 1))
                selectedBasicBlockId = selectedBasicBlockId.coerceIn(0, maxOf(0, computed.basicBlocks.size - 1))
                return@LaunchedEffect
            }

            val (readBytes, realSize) = viewModel.readPatchedFileBytes(
                context = context,
                uri = targetUri,
                patches = selectedPatches,
                maxBytes = 524_288
            )
            if (readBytes.isEmpty()) {
                analysisResult = null
                return@LaunchedEffect
            }

            val effectiveSize = when {
                realSize > 0L -> realSize
                selectedExpectedSize > 0L -> selectedExpectedSize
                else -> readBytes.size.toLong()
            }

            val computed = withContext(Dispatchers.Default) {
                NativeDisassemblerEngine.analyzeBinary(
                    rawBytes = readBytes,
                    fileName = selectedFileName.ifEmpty { "binary_stream.bin" },
                    totalFileSize = effectiveSize,
                    overrideArch = overrideArch,
                    customCodeOffset = customCodeOffset
                )
            }
            analysisResult = computed
            selectedInstructionIndex = selectedInstructionIndex.coerceIn(0, maxOf(0, computed.instructions.size - 1))
            selectedFunctionIndex = selectedFunctionIndex.coerceIn(0, maxOf(0, computed.functions.size - 1))
            selectedBasicBlockId = selectedBasicBlockId.coerceIn(0, maxOf(0, computed.basicBlocks.size - 1))
        } catch (e: Exception) {
            e.printStackTrace()
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
                                    .testTag("disassembler_back_button")
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
                                        imageVector = Icons.Default.DeveloperBoard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Native CPU Disassembler",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ARM64 • ARM32/T32 • x86_64 • DEX/Smali • Decompiler",
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
                                    .testTag("disasm_hex_files_button")
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
                                    .testTag("disasm_import_storage_button")
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

                    // Active Binary Status Banner + Architecture Selector Strip
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
                                        imageVector = Icons.Default.Memory,
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
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${formatDisasmBytes(res.totalFileSize)} • ${res.functions.size} funcs • ${res.xrefs.size} XREFs",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                            imageVector = Icons.Default.PrecisionManufacturing,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = res.activeArch.shortLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Multi-Architecture Override FilterChips (Auto + 4 Primary Architectures)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "ISA:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                            val isAuto = overrideArch == null
                            FilterChip(
                                selected = isAuto,
                                onClick = { overrideArch = null },
                                label = {
                                    Text(
                                        text = "Auto (${res.autoDetectedArch.shortLabel})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = if (isAuto) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                            CpuArchitecture.entries.forEach { arch ->
                                val isSel = overrideArch == arch
                                FilterChip(
                                    selected = isSel,
                                    onClick = { overrideArch = arch },
                                    label = {
                                        Text(
                                            text = arch.shortLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    leadingIcon = if (isSel) {
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

                        // Material 3 ScrollableTabRow for the 5 Reverse-Engineering Subsystems
                        ScrollableTabRow(
                            selectedTabIndex = activeTab.ordinal,
                            edgePadding = 12.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                        ) {
                            DisassemblerSubTab.entries.forEach { tab ->
                                val isSelected = activeTab == tab
                                Tab(
                                    selected = isSelected,
                                    onClick = { activeTab = tab },
                                    modifier = Modifier.testTag("disasm_tab_${tab.name.lowercase()}"),
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
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
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
                                text = "Decoding Instructions, CFG & Pseudocode...",
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
                // Clean Material 3 Empty State ("Nothing here!") when no file is imported
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
                        text = "Import an APK, DEX, ELF (.so), PE (.exe/.dll), or binary file from storage, or choose an open Hex Editor tab to disassemble and decompile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 340.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { storageImportLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("disasm_empty_import_storage_button")
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
                                modifier = Modifier.testTag("disasm_empty_hex_files_button")
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
                when (activeTab) {
                    DisassemblerSubTab.DISASSEMBLER -> {
                        DisassemblerInteractiveWorkspaceView(
                            result = res,
                            selectedIndex = selectedInstructionIndex,
                            onSelectInstruction = { selectedInstructionIndex = it },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            showBookmarksOnly = showBookmarksOnly,
                            onToggleBookmarksOnly = { showBookmarksOnly = it },
                            bookmarkedAddresses = bookmarkedAddresses,
                            onToggleBookmark = { addr ->
                                bookmarkedAddresses = if (bookmarkedAddresses.contains(addr)) {
                                    bookmarkedAddresses - addr
                                } else {
                                    bookmarkedAddresses + addr
                                }
                            },
                            instructionComments = instructionComments,
                            onRequestComment = { addr -> showCommentDialogForAddress = addr },
                            renamedFunctions = renamedFunctions,
                            onNopPatchInstruction = { insn ->
                                val nopByte: Byte = when (res.activeArch) {
                                    CpuArchitecture.X86_64 -> 0x90.toByte()
                                    else -> 0x00.toByte()
                                }
                                val updated = selectedPatches.toMutableMap()
                                for (bIdx in 0 until insn.sizeBytes) {
                                    val fileOff = (insn.fileOffset + bIdx).toLong()
                                    updated[fileOff] = nopByte
                                    if (state.activeTab != null && state.activeTab?.fileUri?.toString() == selectedUri?.toString()) {
                                        viewModel.patchByte(fileOff, nopByte)
                                    }
                                }
                                selectedPatches = updated
                                Toast.makeText(
                                    context,
                                    "Patched ${insn.sizeBytes}B at 0x${String.format(Locale.US, "%08X", insn.address)} with NOP",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onJumpToAddress = { targetAddr ->
                                val idx = res.instructions.indexOfFirst { it.address == targetAddr }
                                if (idx != -1) {
                                    selectedInstructionIndex = idx
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Target 0x${String.format(Locale.US, "%08X", targetAddr)} outside current slice",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onJumpToDecompiler = { insnAddr ->
                                val fnIdx = res.functions.indexOfFirst { insnAddr in it.startAddress..it.endAddress }
                                if (fnIdx != -1) selectedFunctionIndex = fnIdx
                                activeTab = DisassemblerSubTab.DECOMPILER
                            },
                            onOpenInHexEditor = { fileOffset ->
                                val tabIdx = state.tabs.indexOfFirst { it.fileUri?.toString() == selectedUri?.toString() }
                                if (tabIdx != -1) {
                                    viewModel.switchTab(tabIdx)
                                }
                                viewModel.selectOffset(fileOffset.toLong())
                                onClose()
                            }
                        )
                    }
                    DisassemblerSubTab.DECOMPILER -> {
                        DecompilerPseudocodeWorkspaceView(
                            result = res,
                            selectedFunctionIndex = selectedFunctionIndex,
                            onSelectFunction = { selectedFunctionIndex = it },
                            renamedFunctions = renamedFunctions,
                            onRequestRenameFunction = { fnId -> showRenameDialogForFnId = fnId },
                            onCopyCode = { label, code ->
                                clipboardManager.setText(AnnotatedString(code))
                                Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onJumpToDisassembly = { startAddr ->
                                val idx = res.instructions.indexOfFirst { it.address == startAddr }
                                if (idx != -1) selectedInstructionIndex = idx
                                activeTab = DisassemblerSubTab.DISASSEMBLER
                            }
                        )
                    }
                    DisassemblerSubTab.CFG_GRAPH -> {
                        ControlFlowGraphWorkspaceView(
                            result = res,
                            selectedBasicBlockId = selectedBasicBlockId,
                            onSelectBasicBlock = { selectedBasicBlockId = it },
                            onJumpToInstruction = { addr ->
                                val idx = res.instructions.indexOfFirst { it.address == addr }
                                if (idx != -1) selectedInstructionIndex = idx
                                activeTab = DisassemblerSubTab.DISASSEMBLER
                            }
                        )
                    }
                    DisassemblerSubTab.XREFS_SYMBOLS -> {
                        XrefsAndSymbolsWorkspaceView(
                            result = res,
                            onJumpToAddress = { addr ->
                                val idx = res.instructions.indexOfFirst { it.address == addr }
                                if (idx != -1) {
                                    selectedInstructionIndex = idx
                                    activeTab = DisassemblerSubTab.DISASSEMBLER
                                }
                            },
                            onJumpToFileOffsetInHex = { off ->
                                val tabIdx = state.tabs.indexOfFirst { it.fileUri?.toString() == selectedUri?.toString() }
                                if (tabIdx != -1) viewModel.switchTab(tabIdx)
                                viewModel.selectOffset(off)
                                onClose()
                            }
                        )
                    }
                    DisassemblerSubTab.HEADERS_EXPORT -> {
                        HeadersApkAndExportWorkspaceView(
                            result = res,
                            activeEmbeddedEntry = activeEmbeddedEntry,
                            onSelectEmbeddedEntry = { entry ->
                                activeEmbeddedEntry = entry
                                overrideArch = entry.detectedArch
                                customCodeOffset = null
                            },
                            onDisassembleSection = { sectionOffset ->
                                customCodeOffset = sectionOffset.toInt()
                                activeTab = DisassemblerSubTab.DISASSEMBLER
                            },
                            onExportReport = { formatLabel, content ->
                                clipboardManager.setText(AnnotatedString(content))
                                Toast.makeText(context, "$formatLabel copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Comment / Annotation Dialog
    showCommentDialogForAddress?.let { targetAddr ->
        var commentDraft by remember(targetAddr) {
            mutableStateOf(instructionComments[targetAddr] ?: "")
        }
        AlertDialog(
            onDismissRequest = { showCommentDialogForAddress = null },
            icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
            title = {
                Text(
                    text = "Annotate 0x${String.format(Locale.US, "%08X", targetAddr)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    label = { Text("Reverse-engineering comment / note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = instructionComments.toMutableMap()
                        if (commentDraft.isBlank()) {
                            updated.remove(targetAddr)
                        } else {
                            updated[targetAddr] = commentDraft.trim()
                        }
                        instructionComments = updated
                        showCommentDialogForAddress = null
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialogForAddress = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Function Dialog
    showRenameDialogForFnId?.let { fnId ->
        val currentFn = analysisResult?.functions?.find { it.id == fnId }
        var nameDraft by remember(fnId) {
            mutableStateOf(renamedFunctions[fnId] ?: currentFn?.name ?: "")
        }
        AlertDialog(
            onDismissRequest = { showRenameDialogForFnId = null },
            icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
            title = {
                Text(
                    text = "Rename Function Symbol",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Symbol identifier (e.g. decrypt_payload)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameDraft.isNotBlank()) {
                            renamedFunctions = renamedFunctions + (fnId to nameDraft.trim())
                        }
                        showRenameDialogForFnId = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogForFnId = null }) {
                    Text("Cancel")
                }
            }
        )
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
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Load Binary into Disassembler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Supports APK, DEX, ELF (.so), PE (.exe/.dll) & Raw Binaries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showImportSourceSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

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
                                text = "Import Binary / APK from Storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Auto-detects ARM64, ARM32/Thumb, x86_64, or Dalvik DEX",
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
                    state.tabs.forEach { tab ->
                        val isCurrent = tab.fileUri?.toString() == selectedUri?.toString() && selectedFileName == tab.fileName
                        OutlinedCard(
                            onClick = {
                                selectedUri = tab.fileUri
                                selectedFileName = tab.fileName
                                selectedExpectedSize = tab.fileSize
                                selectedPatches = tab.patches
                                overrideArch = null
                                customCodeOffset = null
                                activeEmbeddedEntry = null
                                showImportSourceSheet = false
                                analysisTrigger++
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
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
                                        text = "${formatDisasmBytes(tab.fileSize)} • ${tab.patches.size} live hex patches",
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
                            text = "Recent History (${state.fileHistory.size})",
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
                                overrideArch = null
                                customCodeOffset = null
                                activeEmbeddedEntry = null
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
                                        text = "${formatDisasmBytes(record.fileSize)} • ${dateFormat.format(Date(record.lastModified))}",
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
private fun DisassemblerInteractiveWorkspaceView(
    result: NativeDisassemblyResult,
    selectedIndex: Int,
    onSelectInstruction: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showBookmarksOnly: Boolean,
    onToggleBookmarksOnly: (Boolean) -> Unit,
    bookmarkedAddresses: Set<Long>,
    onToggleBookmark: (Long) -> Unit,
    instructionComments: Map<Long, String>,
    onRequestComment: (Long) -> Unit,
    renamedFunctions: Map<String, String>,
    onNopPatchInstruction: (DisassembledInstruction) -> Unit,
    onJumpToAddress: (Long) -> Unit,
    onJumpToDecompiler: (Long) -> Unit,
    onOpenInHexEditor: (Int) -> Unit
) {
    val filteredInstructions = remember(result.instructions, searchQuery, showBookmarksOnly, bookmarkedAddresses, instructionComments) {
        result.instructions.filter { insn ->
            val matchesBookmark = !showBookmarksOnly || bookmarkedAddresses.contains(insn.address)
            val q = searchQuery.trim().lowercase(Locale.US)
            val matchesQuery = q.isEmpty() ||
                insn.mnemonic.lowercase(Locale.US).contains(q) ||
                insn.operands.lowercase(Locale.US).contains(q) ||
                insn.rawBytesHex.lowercase(Locale.US).contains(q) ||
                String.format(Locale.US, "%08x", insn.address).contains(q) ||
                (insn.resolvedSymbol?.lowercase(Locale.US)?.contains(q) == true) ||
                (instructionComments[insn.address]?.lowercase(Locale.US)?.contains(q) == true)
            matchesBookmark && matchesQuery
        }
    }

    val safeSelected = result.instructions.getOrNull(selectedIndex.coerceIn(0, maxOf(0, result.instructions.size - 1)))
    val owningFunction = remember(safeSelected, result.functions) {
        safeSelected?.let { sel ->
            result.functions.firstOrNull { sel.address in it.startAddress..it.endAddress }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search + Bookmarks Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("disasm_search_input"),
                placeholder = {
                    Text(
                        text = "Search addr, mnemonic, reg, symbol...",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            FilterChip(
                selected = showBookmarksOnly,
                onClick = { onToggleBookmarksOnly(!showBookmarksOnly) },
                label = {
                    Text(
                        text = "Bookmarks (${bookmarkedAddresses.size})",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (showBookmarksOnly) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        // Instruction Stream List (Top Weight)
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Header
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADDRESS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(78.dp)
                        )
                        Text(
                            text = "BYTES",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(86.dp)
                        )
                        Text(
                            text = "MNEMONIC & OPERANDS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredInstructions, key = { it.index }) { insn ->
                        val isSelected = insn.index == selectedIndex
                        val isBookmarked = bookmarkedAddresses.contains(insn.address)
                        val userComment = instructionComments[insn.address]
                        val fnStart = result.functions.firstOrNull { it.startAddress == insn.address }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onSelectInstruction(insn.index) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            // Function Boundary Banner if this instruction starts a detected function
                            if (fnStart != null) {
                                val displayFnName = renamedFunctions[fnStart.id] ?: fnStart.name
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Functions,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "FUNCTION $displayFnName (${fnStart.instructionIndices.size} insns • frame ${fnStart.stackFrameSize}B)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Incoming XREFs line if referenced by branches/calls
                            if (insn.xrefsFrom.isNotEmpty()) {
                                Text(
                                    text = "  ; XREF from: " + insn.xrefsFrom.take(3).joinToString(", ") {
                                        String.format(Locale.US, "0x%08X", it)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF00E676),
                                    fontSize = 10.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%08X", insn.address),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isBookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(78.dp)
                                )

                                Text(
                                    text = insn.rawBytesHex,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(86.dp)
                                )

                                Text(
                                    text = insn.mnemonic,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = insn.category.badgeColor,
                                    modifier = Modifier.width(68.dp)
                                )

                                Text(
                                    text = insn.operands,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isBookmarked) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Bookmarked",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (userComment != null || insn.resolvedSymbol != null) {
                                Text(
                                    text = buildString {
                                        if (insn.resolvedSymbol != null) append("  ; ref: ${insn.resolvedSymbol} ")
                                        if (userComment != null) append("  // NOTE: $userComment")
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
        }

        // INTERACTIVE INSTRUCTION ANALYSIS WORKSPACE PANEL (Bottom M3 ElevatedCard)
        safeSelected?.let { insn ->
            val fnDisplayName = owningFunction?.let { renamedFunctions[it.id] ?: it.name } ?: "sub_code"
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row: Address, Category Badge, and Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = insn.category.badgeColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, insn.category.badgeColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = insn.category.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = insn.category.badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "0x${String.format(Locale.US, "%08X", insn.address)} (+0x${Integer.toHexString(insn.fileOffset)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onToggleBookmark(insn.address) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (bookmarkedAddresses.contains(insn.address)) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Bookmark Instruction",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRequestComment(insn.address) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = "Add Comment",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onNopPatchInstruction(insn) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "NOP Patch Instruction",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = { onOpenInHexEditor(insn.fileOffset) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in Hex Editor",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Decoded Opcode, Mnemonic, Operands, and Function Context
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InspectorPill("Bytes", insn.rawBytesHex)
                        InspectorPill("Opcode", insn.opcodeHex)
                        InspectorPill("Function", fnDisplayName)
                        InspectorPill("Read Regs", if (insn.registersRead.isEmpty()) "None" else insn.registersRead.joinToString(", "))
                        InspectorPill("Write Regs", if (insn.registersModified.isEmpty()) "None" else insn.registersModified.joinToString(", "))
                        insn.memoryAccess?.let { mem ->
                            InspectorPill("Memory", mem)
                        }
                    }

                    // Branch Target & XREFs Row (if applicable)
                    if (insn.branchTarget != null || insn.xrefsFrom.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            insn.branchTarget?.let { target ->
                                AssistChip(
                                    onClick = { onJumpToAddress(target) },
                                    label = {
                                        Text(
                                            text = "Branch Target -> 0x${String.format(Locale.US, "%08X", target)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                            insn.xrefsFrom.forEach { callerAddr ->
                                AssistChip(
                                    onClick = { onJumpToAddress(callerAddr) },
                                    label = {
                                        Text(
                                            text = "XREF <- 0x${String.format(Locale.US, "%08X", callerAddr)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CallReceived,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Decompiled C/Java Pseudocode Preview Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToDecompiler(insn.address) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Decompiled Pseudocode ($fnDisplayName):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = insn.pseudocodeLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { onJumpToDecompiler(insn.address) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Full C/Java", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DecompilerPseudocodeWorkspaceView(
    result: NativeDisassemblyResult,
    selectedFunctionIndex: Int,
    onSelectFunction: (Int) -> Unit,
    renamedFunctions: Map<String, String>,
    onRequestRenameFunction: (String) -> Unit,
    onCopyCode: (String, String) -> Unit,
    onJumpToDisassembly: (Long) -> Unit
) {
    val functions = result.functions
    val currentFn = functions.getOrNull(selectedFunctionIndex.coerceIn(0, maxOf(0, functions.size - 1)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Function Selector Strip
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reconstructed Functions (${functions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    currentFn?.let { fn ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { onRequestRenameFunction(fn.id) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rename", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onJumpToDisassembly(fn.startAddress) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ASM", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    functions.forEachIndexed { idx, fn ->
                        val active = idx == selectedFunctionIndex
                        val dispName = renamedFunctions[fn.id] ?: fn.name
                        FilterChip(
                            selected = active,
                            onClick = { onSelectFunction(idx) },
                            label = {
                                Text(
                                    text = dispName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = if (active) {
                                {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            } else null
                        )
                    }
                }
            }
        }

        currentFn?.let { fn ->
            val dispName = renamedFunctions[fn.id] ?: fn.name

            // Function Telemetry & Variable Recovery Card
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Decompiler Type & Variable Recovery — $dispName",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InspectorPill("Address", String.format(Locale.US, "0x%08X", fn.startAddress))
                        InspectorPill("Stack Frame", "${fn.stackFrameSize} Bytes")
                        InspectorPill("Complexity", "M = ${fn.cyclomaticComplexity}")
                        InspectorPill("Classification", if (fn.isRecursive) "Recursive" else if (fn.isLeaf) "Leaf Procedure" else "Standard Caller")
                    }

                    if (fn.variables.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text(
                            text = "Recovered Register & Stack Variables:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fn.variables.forEach { v ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${v.inferredType} ${v.name} (${v.storage})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reconstructed C/C++ or Java Decompiled Code Card
            val fullCodeText = remember(fn, dispName) {
                fn.decompiledCodeLines.joinToString("\n").replace(fn.name, dispName)
            }

            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (result.activeArch == CpuArchitecture.DALVIK_DEX) {
                                    "Reconstructed Java / Decompiled DEX Source"
                                } else {
                                    "Reconstructed C / C++ Pseudocode"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = { onCopyCode("Decompiled function $dispName", fullCodeText) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Code", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A0E17),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            fullCodeText.lines().forEachIndexed { lineIdx, line ->
                                val lineColor = when {
                                    line.trimStart().startsWith("//") -> Color(0xFF78909C)
                                    line.trimStart().startsWith("if ") || line.contains("return ") || line.contains("goto ") -> Color(0xFFFFCA28)
                                    line.endsWith("{") || line == "}" -> Color(0xFF00E676)
                                    line.contains("loc_") -> Color(0xFFFF5252)
                                    else -> Color(0xFFE2E8F0)
                                }
                                Row {
                                    Text(
                                        text = String.format(Locale.US, "%2d  ", lineIdx + 1),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = lineColor,
                                        softWrap = false
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
private fun ControlFlowGraphWorkspaceView(
    result: NativeDisassemblyResult,
    selectedBasicBlockId: Int,
    onSelectBasicBlock: (Int) -> Unit,
    onJumpToInstruction: (Long) -> Unit
) {
    val blocks = result.basicBlocks
    val currentBlock = blocks.getOrNull(selectedBasicBlockId.coerceIn(0, maxOf(0, blocks.size - 1)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Interactive Control Flow Graph (CFG)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${blocks.size} Basic Blocks • Tap any node in the graph to inspect",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 2D Interactive CFG Canvas (displays up to 12 Basic Blocks in a 2-column flow diagram)
                val visibleBlocks = blocks.take(12)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF060A12))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .pointerInput(visibleBlocks.size) {
                            detectTapGestures { pos ->
                                if (visibleBlocks.isEmpty() || size.width <= 0 || size.height <= 0) return@detectTapGestures
                                val cols = 2
                                val rows = (visibleBlocks.size + 1) / 2
                                val col = ((pos.x / size.width) * cols).toInt().coerceIn(0, cols - 1)
                                val row = ((pos.y / size.height) * rows).toInt().coerceIn(0, maxOf(0, rows - 1))
                                val tappedIdx = (row * cols + col).coerceIn(0, visibleBlocks.size - 1)
                                onSelectBasicBlock(tappedIdx)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (visibleBlocks.isEmpty()) return@Canvas
                        val cols = 2
                        val rows = maxOf(1, (visibleBlocks.size + 1) / 2)
                        val cellW = size.width / cols
                        val cellH = size.height / rows

                        fun nodeCenter(idx: Int): Offset {
                            val r = idx / cols
                            val c = idx % cols
                            return Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH)
                        }

                        // Draw CFG edges first
                        for (i in visibleBlocks.indices) {
                            val b = visibleBlocks[i]
                            val start = nodeCenter(i)
                            b.outgoingTrueTarget?.let { t ->
                                if (t in visibleBlocks.indices) {
                                    val end = nodeCenter(t)
                                    drawLine(
                                        color = Color(0xFF00E676),
                                        start = start,
                                        end = end,
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                            b.outgoingFalseTarget?.let { f ->
                                if (f in visibleBlocks.indices) {
                                    val end = nodeCenter(f)
                                    drawLine(
                                        color = Color(0xFFFF5252),
                                        start = start,
                                        end = end,
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                        }

                        // Draw Basic Block Nodes
                        for (i in visibleBlocks.indices) {
                            val b = visibleBlocks[i]
                            val center = nodeCenter(i)
                            val boxW = cellW * 0.78f
                            val boxH = cellH * 0.64f
                            val topLeft = Offset(center.x - boxW / 2f, center.y - boxH / 2f)
                            val isSel = b.id == selectedBasicBlockId

                            val nodeColor = when {
                                b.blockType.contains("ENTRY") -> Color(0xFF1E3A8A)
                                b.blockType.contains("EXIT") -> Color(0xFF7F1D1D)
                                b.blockType.contains("CONDITIONAL") -> Color(0xFF78350F)
                                else -> Color(0xFF1E293B)
                            }
                            val borderColor = if (isSel) Color(0xFF00F0FF) else Color(0xFF64748B)

                            drawRoundRect(
                                color = nodeColor,
                                topLeft = topLeft,
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                            drawRoundRect(
                                color = borderColor,
                                topLeft = topLeft,
                                size = Size(boxW, boxH),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                                style = Stroke(width = if (isSel) 2.5.dp.toPx() else 1.dp.toPx())
                            )
                        }
                    }
                }

                // Basic Block FilterChip selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    blocks.forEach { b ->
                        val active = b.id == selectedBasicBlockId
                        FilterChip(
                            selected = active,
                            onClick = { onSelectBasicBlock(b.id) },
                            label = {
                                Text(
                                    text = b.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }
            }
        }

        // Selected Basic Block Detail Card
        currentBlock?.let { block ->
            OutlinedCard(
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
                        Column {
                            Text(
                                text = "${block.label} — ${block.blockType}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Function: ${block.functionName} • ${block.instructions.size} instructions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { onJumpToInstruction(block.startAddress) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Inspect in ASM", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    block.instructions.forEach { ins ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToInstruction(ins.address) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "0x%08X", ins.address),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(86.dp)
                            )
                            Text(
                                text = ins.mnemonic,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ins.category.badgeColor,
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = ins.operands,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XrefsAndSymbolsWorkspaceView(
    result: NativeDisassemblyResult,
    onJumpToAddress: (Long) -> Unit,
    onJumpToFileOffsetInHex: (Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<XrefType?>(null) }
    val filteredXrefs = remember(result.xrefs, selectedFilter) {
        if (selectedFilter == null) result.xrefs else result.xrefs.filter { it.type == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cross-Reference Analyzer (XREFs — ${result.xrefs.size} links)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All (${result.xrefs.size})", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
                    )
                    XrefType.entries.forEach { xt ->
                        val count = result.xrefs.count { it.type == xt }
                        FilterChip(
                            selected = selectedFilter == xt,
                            onClick = { selectedFilter = xt },
                            label = { Text("${xt.label} ($count)", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
                        )
                    }
                }

                filteredXrefs.take(40).forEach { xref ->
                    OutlinedCard(
                        onClick = { onJumpToAddress(xref.fromAddress) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = xref.type.color.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = xref.type.label,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = xref.type.color,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = String.format(Locale.US, "0x%08X -> %s", xref.fromAddress, xref.toSymbol),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${xref.fromFunction}: ${xref.instructionSummary}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Jump to caller",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Extracted Strings & Symbol Table Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Extracted Binary Strings & Symbols (${result.extractedStrings.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                result.extractedStrings.take(35).forEach { sItem ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJumpToFileOffsetInHex(sItem.offset) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "0x%06X", sItem.offset),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(68.dp)
                            )
                            Text(
                                text = sItem.category,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.width(72.dp)
                            )
                            Text(
                                text = sItem.value,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadersApkAndExportWorkspaceView(
    result: NativeDisassemblyResult,
    activeEmbeddedEntry: EmbeddedApkEntry?,
    onSelectEmbeddedEntry: (EmbeddedApkEntry) -> Unit,
    onDisassembleSection: (Long) -> Unit,
    onExportReport: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // If user opened an APK/ZIP with embedded .dex or .so files, show APK Container Target Switcher!
        if (result.embeddedApkEntries.isNotEmpty()) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "APK Embedded Binaries (${result.embeddedApkEntries.size} DEX / Native .so)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap any DEX or native .so ABI entry inside the APK to disassemble it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    result.embeddedApkEntries.forEach { entry ->
                        val isCurrent = activeEmbeddedEntry?.path == entry.path
                        OutlinedCard(
                            onClick = { onSelectEmbeddedEntry(entry) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.path,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${formatDisasmBytes(entry.sizeBytes)} • ${entry.detectedArch.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hybrid Multi-Language Architecture Card (Kotlin + Java JNI Bridge + C++17 NDK + Rust Validator)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hybrid Reverse-Engineering Stack (Kotlin + Java JNI + C++17 + Rust)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = result.jniBridgeStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                InspectorPill("Rust Memory-Safe Guard", result.rustValidationSummary)
                InspectorPill("C++17 NDK Module", "app/src/main/cpp/bytewave_disasm_jni.cpp (ARM64/T32/x86_64/DEX)")
                InspectorPill("Rust Validator Crate", "app/src/main/rust/src/lib.rs (Zero-Copy Header Bounds)")
                InspectorPill("Java JNI & DEX Bridge", "com.bytewave.nativebridge.NativeDisasmJniBridge")
            }
        }

        // Binary Container Headers Card (ELF / PE / DEX)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Container Header Analysis — ${result.detectedFormat}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                result.headerAttributes.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = k,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = v,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }

        // Section & Segment Table Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sections & Segments (${result.sections.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                result.sections.forEach { sec ->
                    OutlinedCard(
                        onClick = { onDisassembleSection(sec.fileOffset) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sec.name} (${sec.typeName} • ${sec.flags})",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "VA 0x%08X • Off 0x%06X • %s • H = %.2f b/B",
                                        sec.virtualAddress,
                                        sec.fileOffset,
                                        formatDisasmBytes(sec.sizeBytes),
                                        sec.entropyEstimate
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onDisassembleSection(sec.fileOffset) }) {
                                Text("Decode", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Export & Reporting Card (ASM, C Pseudocode, JSON, HTML)
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
                        imageVector = Icons.Default.IosShare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Export Disassembly & Decompilation Reports",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val asmDump = result.instructions.joinToString("\n") { ins ->
                                String.format(Locale.US, "%08X: %-14s %-8s %s", ins.address, ins.rawBytesHex, ins.mnemonic, ins.operands)
                            }
                            onExportReport("ASM Disassembly Listing", asmDump)
                        }
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .ASM")
                    }

                    FilledTonalButton(
                        onClick = {
                            val cDump = result.functions.joinToString("\n\n") { fn ->
                                fn.decompiledCodeLines.joinToString("\n")
                            }
                            onExportReport("Decompiled C/Java Source", cDump)
                        }
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .C / .JAVA")
                    }

                    FilledTonalButton(
                        onClick = {
                            val json = buildString {
                                append("{\n")
                                append("  \"file\": \"${result.fileName}\",\n")
                                append("  \"format\": \"${result.detectedFormat}\",\n")
                                append("  \"architecture\": \"${result.activeArch.id}\",\n")
                                append("  \"instructionsCount\": ${result.instructions.size},\n")
                                append("  \"functionsCount\": ${result.functions.size},\n")
                                append("  \"basicBlocksCount\": ${result.basicBlocks.size},\n")
                                append("  \"xrefsCount\": ${result.xrefs.size}\n")
                                append("}")
                            }
                            onExportReport("JSON Analysis Report", json)
                        }
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .JSON")
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: Context, uri: Uri): String {
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

private fun formatDisasmBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
