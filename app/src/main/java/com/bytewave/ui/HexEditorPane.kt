package com.bytewave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.launch
import com.bytewave.DisplayMode
import com.bytewave.EncodingMode
import com.bytewave.HexEditorState
import com.bytewave.HexEditorViewModel
import com.bytewave.HexRowData

@Composable
fun HexEditorPane(viewModel: HexEditorViewModel, state: HexEditorState, tabIndex: Int, otherTabIndex: Int? = null, modifier: Modifier = Modifier) {
    val tab = state.tabs.getOrNull(tabIndex) ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileAccessLost = !viewModel.isChannelAvailable(tab.fileUri)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (fileAccessLost) {
            val reOpenLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.reassociateFile(context, tabIndex, uri)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 450.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚠️ PERMISSION NEEDED",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your un-saved patches and edits for \"${tab.fileName}\" are safely recovered!\n\nHowever, Android file permission was revoked by the system upon restart. Tap below to re-select the file and restore your workspace perfectly.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Button(
                            onClick = { reOpenLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("RE-OPEN FILE TO RESTORE WORK", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            val containerWidth = maxWidth
            val density = androidx.compose.ui.platform.LocalDensity.current
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            val currentCols = if (state.desktopMode) {
                if (state.displayMode == DisplayMode.BINARY) 8 else 16
            } else {
                if (state.displayMode == DisplayMode.BINARY) 4 else 8
            }
            val isCompact8Hex = !state.desktopMode && state.displayMode == DisplayMode.HEX
            val rowFontSize = if (state.displayMode == DisplayMode.BINARY) 10.sp else if (isCompact8Hex) 12.5.sp else 12.sp
            val baseLetterSpacing = if (state.displayMode == DisplayMode.BINARY) (-0.5).sp else 0.sp

            val offsetChars = 8
            val charsPerVal = if (state.displayMode == DisplayMode.BINARY) 8 else 2
            val middleExtraChars = if (currentCols == 16) 1 else 0
            val hexTotalChars = currentCols * charsPerVal + (currentCols - 1) + middleExtraChars
            val textTotalChars = currentCols
            val totalRowChars = offsetChars + 1 + hexTotalChars + 1 + textTotalChars

            val startPad = 6.dp
            val endPad = 12.dp
            val sepSpacer = 4.dp
            val safetyBufferDp = 6.dp
            val fixedHorizontalDp = startPad + endPad + (sepSpacer * 4) + safetyBufferDp

            val measuredTotalCharsPx = remember(rowFontSize, totalRowChars, density.density, density.fontScale) {
                textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString("0".repeat(totalRowChars)),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = rowFontSize,
                        letterSpacing = 0.sp
                    )
                ).size.width.toFloat().coerceAtLeast(1f)
            }

            val effectiveLetterSpacing = if (isCompact8Hex) {
                val availCharsPx = with(density) { (containerWidth - fixedHorizontalDp).toPx() }
                    .coerceAtLeast(measuredTotalCharsPx)
                val extraPerCharPx = ((availCharsPx - measuredTotalCharsPx) / totalRowChars.toFloat()).coerceAtLeast(0f)
                with(density) { extraPerCharPx.toSp() }
            } else {
                baseLetterSpacing
            }

            val effectiveCharPx = remember(rowFontSize, effectiveLetterSpacing, totalRowChars, density.density, density.fontScale) {
                textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString("0".repeat(totalRowChars)),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing
                    )
                ).size.width.toFloat().coerceAtLeast(1f) / totalRowChars.toFloat()
            }

            val offsetWidth = with(density) { (effectiveCharPx * offsetChars).toDp() + 1.5.dp }
            val pipeWidth = with(density) { effectiveCharPx.toDp() + 1.dp }
            val hexDataWidth = with(density) { (effectiveCharPx * hexTotalChars).toDp() + 2.dp }
            val textDecWidth = with(density) { (effectiveCharPx * textTotalChars).toDp() + 1.5.dp }
            val boxWidth = with(density) { (effectiveCharPx * charsPerVal).toDp() }
            val minRowWidth = startPad + endPad + (sepSpacer * 4) + offsetWidth + (pipeWidth * 2) + hexDataWidth + textDecWidth
            val rowWidth = if (isCompact8Hex) containerWidth else maxOf(minRowWidth, containerWidth)

            val headerHexStr = remember(currentCols, state.displayMode) {
                buildString {
                    for (i in 0 until currentCols) {
                        if (i > 0) append(" ")
                        if (currentCols == 16 && i == 8) append(" ")
                        if (state.displayMode == DisplayMode.BINARY) {
                            append("  BIN${i}  ".take(8).padEnd(8, ' '))
                        } else {
                            append(Integer.toHexString(i).uppercase().padStart(2, ' '))
                        }
                    }
                }
            }

        val hScrollState = rememberScrollState()
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val rowCount = viewModel.getRowCountForTab(tabIndex)
        val coroutineScope = rememberCoroutineScope()
        var trackHeightPx by remember { mutableStateOf(0f) }
        var lastScrolledRow by remember { mutableStateOf(-1) }
        var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Pane Header for Comparison Mode
            if (otherTabIndex != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.activeTabIndex == tabIndex) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) 
                    else 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, 
                        if (state.activeTabIndex == tabIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (state.activeTabIndex == tabIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = if (state.activeTabIndex == tabIndex) "PANE A (ACTIVE)" else "PANE B (COMPARE)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tab.fileName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${tab.fileSize} bytes",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth().horizontalScroll(hScrollState)) {
                // Header
                Row(
                    modifier = Modifier
                        .width(rowWidth)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        .padding(bottom = 4.dp, top = 4.dp, start = startPad, end = endPad),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OFFSET",
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(offsetWidth)
                    )
                    Spacer(modifier = Modifier.width(sepSpacer))
                    Text(
                        text = "|",
                        color = MaterialTheme.colorScheme.outlineVariant,
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(pipeWidth)
                    )
                    Spacer(modifier = Modifier.width(sepSpacer))
                    Text(
                        text = headerHexStr,
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(hexDataWidth)
                    )
                    Spacer(modifier = Modifier.width(sepSpacer))
                    Text(
                        text = "|",
                        color = MaterialTheme.colorScheme.outlineVariant,
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(pipeWidth)
                    )
                    Spacer(modifier = Modifier.width(sepSpacer))
                    Text(
                        text = state.encodingMode.name,
                        fontSize = rowFontSize,
                        letterSpacing = effectiveLetterSpacing,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(textDecWidth)
                    )
                }

                var lastSelectedOffset by remember { mutableStateOf<Long?>(null) }
                val cols = currentCols
                LaunchedEffect(tab.selectedOffset, state.displayMode, state.desktopMode) {
                    if (tab.selectedOffset != null && tab.selectedOffset != lastSelectedOffset) {
                        lastSelectedOffset = tab.selectedOffset
                        val rowIndex = (tab.selectedOffset!! / cols).toInt()
                        val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
                        val isVisible = visibleItemsInfo.any { it.index == rowIndex }
                        val maxRows = ((tab.fileSize + cols - 1) / cols).toInt()
                        if (!isVisible && rowIndex < maxRows) {
                            listState.scrollToItem(rowIndex)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).width(rowWidth).padding(start = startPad, end = endPad),
                    state = listState
                ) {
                    items(
                        count = rowCount
                    ) { index ->
                        val rowData = viewModel.getRowForTab(tabIndex, index)
                        if (rowData != null) {
                            HexRowItem(
                                rowData = rowData,
                                cols = cols,
                                currentCols = currentCols,
                                boxWidth = boxWidth,
                                offsetWidth = offsetWidth,
                                pipeWidth = pipeWidth,
                                hexDataWidth = hexDataWidth,
                                sepSpacer = sepSpacer,
                                rowFontSize = rowFontSize,
                                rowLetterSpacing = effectiveLetterSpacing,
                                displayMode = state.displayMode,
                                encodingMode = state.encodingMode,
                                selectedOffset = tab.selectedOffset,
                                patches = tab.patches,
                                searchResults = tab.searchResults,
                                currentSearchIndex = tab.currentSearchIndex,
                                searchQueryLength = tab.searchQueryLength,
                                textDecWidth = textDecWidth,
                                otherTabIndex = otherTabIndex,
                                viewModel = viewModel,
                                cacheVersion = state.cacheVersion,
                                onSelectOffset = { 
                                    if (state.activeTabIndex == tabIndex) {
                                        viewModel.selectOffset(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Custom Scrollbar Overlay
        val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(22.dp)
                .onGloballyPositioned { coordinates ->
                    trackHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(rowCount, trackHeightPx) {
                    detectTapGestures { offset ->
                        if (trackHeightPx > 0f && rowCount > 0) {
                            val relativeY = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val targetRow = (relativeY * rowCount).toLong().coerceIn(0L, rowCount - 1L).toInt()
                            if (targetRow != lastScrolledRow) {
                                lastScrolledRow = targetRow
                                scrollJob?.cancel()
                                scrollJob = coroutineScope.launch {
                                    listState.scrollToItem(targetRow)
                                }
                            }
                        }
                    }
                }
                .pointerInput(rowCount, trackHeightPx) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (trackHeightPx > 0f && rowCount > 0) {
                                val relativeY = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                                val targetRow = (relativeY * rowCount).toLong().coerceIn(0L, rowCount - 1L).toInt()
                                if (targetRow != lastScrolledRow) {
                                    lastScrolledRow = targetRow
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        listState.scrollToItem(targetRow)
                                    }
                                }
                            }
                        }
                    )
                }
                .drawWithContent {
                    val listInfo = listState.layoutInfo
                    val visibleItems = listInfo.visibleItemsInfo
                    if (rowCount > 0 && visibleItems.isNotEmpty()) {
                        val totalVisible = visibleItems.size
                        val hRatio = (totalVisible.toFloat() / rowCount.coerceAtLeast(1).toFloat()).coerceIn(0.02f, 1f)
                        val firstVisible = listState.firstVisibleItemIndex
                        val pRatio = (firstVisible.toFloat() / maxOf(1, rowCount - totalVisible).toFloat()).coerceIn(0f, 1f)

                        val trackHeight = size.height
                        if (trackHeight > 0f) {
                            val thumbHeight = (trackHeight * hRatio).coerceAtLeast(24.dp.toPx())
                            val scrollableArea = (trackHeight - thumbHeight).coerceAtLeast(0f)
                            val thumbOffsetY = scrollableArea * pRatio
                            
                            if (!thumbHeight.isNaN() && !thumbOffsetY.isNaN() && !thumbHeight.isInfinite() && !thumbOffsetY.isInfinite()) {
                                val barWidth = 6.dp.toPx()
                                val barStartX = (size.width - barWidth) / 2f
                                drawRoundRect(
                                    color = scrollbarColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(barStartX, thumbOffsetY),
                                    size = androidx.compose.ui.geometry.Size(barWidth, thumbHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                }
        )
        }
    }
}
