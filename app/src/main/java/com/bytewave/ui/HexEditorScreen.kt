package com.bytewave.ui

import com.bytewave.bounceClick
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.net.Uri
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytewave.HexEditorViewModel
import com.bytewave.DisplayMode
import com.bytewave.EncodingMode
import com.bytewave.ui.theme.*

data class PaletteCommand(
    val title: String,
    val shortcut: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: () -> Unit
)

@Composable
fun CopyableInspectorItem(label: String, value: String, categoryColor: androidx.compose.ui.graphics.Color) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(width = 0.5.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
            .clickable {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(value))
                android.widget.Toast.makeText(context, "$label Copied!", android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = categoryColor,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "COPY",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun DataInspectorPanel(viewModel: HexEditorViewModel, state: com.bytewave.HexEditorState) {
    val inspectorData = viewModel.getInspectorData()
    var selectedInspCategory by remember { mutableStateOf(0) } // 0: Integers, 1: Radix & Real, 2: Advanced Decoding

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = com.bytewave.Translations.get("DATA INSPECTOR & DECODER", state.appLanguage),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.selectedOffset?.let { "${com.bytewave.Translations.get("ADDR:", state.appLanguage)} ${String.format("%08X", it)} " } ?: "${com.bytewave.Translations.get("NO SELECTION", state.appLanguage)} ",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.Checkbox(
                    checked = state.inspectorBigEndian,
                    onCheckedChange = { viewModel.setInspectorBigEndian(it) },
                    modifier = Modifier.size(24.dp)
                )
                Text(" " + com.bytewave.Translations.get("Big endian", state.appLanguage), fontSize = 10.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = { viewModel.toggleKeyboard() },
                    modifier = Modifier.height(26.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(com.bytewave.Translations.get("EDIT", state.appLanguage), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (inspectorData == null) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = com.bytewave.Translations.get("Select any byte offset in the editor grid to decode and analyze standard structure representations.", state.appLanguage),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            // Category Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("INTEGERS", "REAL & RADIX", "ADVANCED").forEachIndexed { idx, title ->
                    val isSelected = selectedInspCategory == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedInspCategory = idx }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = com.bytewave.Translations.get(title, state.appLanguage),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val accentGreen = Color(0xFF2E7D32)
            val accentBlue = Color(0xFF1565C0)
            val accentOrange = Color(0xFFD84315)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                when (selectedInspCategory) {
                    0 -> { // Integers
                        CopyableInspectorItem(com.bytewave.Translations.get("8-bit Signed Byte (Int8)", state.appLanguage), inspectorData.int8.toString(), accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("8-bit Unsigned Byte (UInt8)", state.appLanguage), inspectorData.uint8.toString(), accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("16-bit Signed Short (Int16)", state.appLanguage), inspectorData.int16?.toString() ?: "N/A", accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("16-bit Unsigned Short (UInt16)", state.appLanguage), inspectorData.uint16?.toString() ?: "N/A", accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("32-bit Signed Int (Int32)", state.appLanguage), inspectorData.int32?.toString() ?: "N/A", accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("32-bit Unsigned Int (UInt32)", state.appLanguage), inspectorData.uint32?.toString() ?: "N/A", accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("64-bit Signed Long (Int64)", state.appLanguage), inspectorData.int64?.toString() ?: "N/A", accentBlue)
                        CopyableInspectorItem(com.bytewave.Translations.get("64-bit Unsigned Long (UInt64)", state.appLanguage), inspectorData.uint64?.toString() ?: "N/A", accentBlue)
                    }
                    1 -> { // Radix & Real
                        CopyableInspectorItem(com.bytewave.Translations.get("32-bit Floating Point (Float)", state.appLanguage), inspectorData.float32?.toString() ?: "N/A", accentGreen)
                        CopyableInspectorItem(com.bytewave.Translations.get("64-bit Floating Point (Double)", state.appLanguage), inspectorData.float64?.toString() ?: "N/A", accentGreen)
                        CopyableInspectorItem(com.bytewave.Translations.get("Binary (Base-2 Byte bits)", state.appLanguage), inspectorData.binary, accentGreen)
                        CopyableInspectorItem(com.bytewave.Translations.get("Octal (Base-8 notation)", state.appLanguage), inspectorData.octal, accentGreen)
                        CopyableInspectorItem(com.bytewave.Translations.get("Identified Magic Block Signature", state.appLanguage), inspectorData.magicHeader ?: com.bytewave.Translations.get("No file signature match detected", state.appLanguage), accentGreen)
                    }
                    2 -> { // Advanced / Strings / Timestamps / Varints
                        CopyableInspectorItem(com.bytewave.Translations.get("ASCII string", state.appLanguage), inspectorData.asciiStr, accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("UTF-8 unicode string", state.appLanguage), inspectorData.utf8Str.ifEmpty { "N/A" }, accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("UTF-16 Little Endian", state.appLanguage), inspectorData.utf16LeStr, accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("UTF-16 Big Endian", state.appLanguage), inspectorData.utf16BeStr, accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Unix Timestamp 32-bit (Seconds)", state.appLanguage), inspectorData.unixTime32Sec ?: com.bytewave.Translations.get("Out of scale (not a valid date)", state.appLanguage), accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Unix Timestamp 64-bit (Ms)", state.appLanguage), inspectorData.unixTime64Ms ?: com.bytewave.Translations.get("Out of scale (not a valid date)", state.appLanguage), accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Microsoft GUID (LITTLE ENDIAN)", state.appLanguage), inspectorData.guidLe ?: "N/A", accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Standard UUID (BIG ENDIAN)", state.appLanguage), inspectorData.guidBe ?: "N/A", accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Variable Unsigned Int (ULEB128)", state.appLanguage), inspectorData.leb128U ?: "N/A", accentOrange)
                        CopyableInspectorItem(com.bytewave.Translations.get("Variable Signed Int (SLEB128)", state.appLanguage), inspectorData.leb128S ?: "N/A", accentOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun HexKeyboardPanel(viewModel: HexEditorViewModel, state: com.bytewave.HexEditorState) {
    if (!state.showKeyboard) return
    val offset = state.selectedOffset ?: return
    
    val cols = if (state.desktopMode) {
        if (state.displayMode == com.bytewave.DisplayMode.BINARY) 8 else 16
    } else {
        if (state.displayMode == com.bytewave.DisplayMode.BINARY) 4 else 8
    }
    val selectedByte = state.activeTab?.let { tab ->
        val rowIdx = (offset / cols).toInt()
        val colIdx = (offset % cols).toInt()
        tab.patches[offset] ?: viewModel.getRow(rowIdx)?.bytes?.getOrNull(colIdx) ?: 0.toByte()
    } ?: 0.toByte()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        if (state.displayMode == com.bytewave.DisplayMode.BINARY) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("0", "1").forEach { char ->
                    HexKey(char) {
                        viewModel.writeBinaryDigit(char[0])
                    }
                }
            }
        } else {
            val row1 = listOf("0", "1", "2", "3", "4", "5", "6", "7")
            val row2 = listOf("8", "9", "A", "B", "C", "D", "E", "F")
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row1.forEach { char ->
                    HexKey(char) {
                        viewModel.writeHexDigit(char[0])
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row2.forEach { char ->
                    HexKey(char) {
                        viewModel.writeHexDigit(char[0])
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val desktopKeys = listOf("UND", "RED", "UP", "DN", "LT", "RT")
            desktopKeys.forEach { key ->
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(8.dp))
                        .clickable { 
                            when(key) {
                                "UND" -> viewModel.undo()
                                "RED" -> viewModel.redo()
                                "UP" -> viewModel.moveCursor(-cols.toLong())
                                "DN" -> viewModel.moveCursor(cols.toLong())
                                "LT" -> viewModel.moveCursor(-1L)
                                "RT" -> viewModel.moveCursor(1L)
                            }
                         },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleKeyboard() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Code, contentDescription = "Close Keyboard", tint = Color.Gray)
            }
            Text(
                text = "Val: ${String.format("%02X", selectedByte.toInt() and 0xFF)}h   Pos: ${offset.toString(16).uppercase()}h   Size: ${state.fileSize.toString(16).uppercase()}h [${if(state.displayMode == com.bytewave.DisplayMode.BINARY) "BIN" else "HEX"}]",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RowScope.HexKey(char: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 2.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = char, fontSize = 18.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}
@Composable
fun InspectorRow(label1: String, val1: String, label2: String, val2: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label1, fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
            Text(text = val1, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label2, fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
            Text(text = val2, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DrawerSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
fun DrawerToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    shortcut: String? = null,
    isActive: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val onSurfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = androidx.compose.material3.MaterialTheme.colorScheme.outline

    val itemShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(itemShape)
            .then(
                if (isActive) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.20f),
                                    primaryColor.copy(alpha = 0.06f)
                                )
                            )
                        )
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.45f),
                                    primaryColor.copy(alpha = 0.12f)
                                )
                            ),
                            shape = itemShape
                        )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Frosted Glass Icon Container
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            when {
                                !enabled -> onSurfaceColor.copy(alpha = 0.03f)
                                isActive -> primaryColor.copy(alpha = 0.22f)
                                else -> onSurfaceColor.copy(alpha = 0.06f)
                            }
                        )
                        .border(
                            width = 0.6.dp,
                            brush = Brush.linearGradient(
                                colors = if (isActive) {
                                    listOf(
                                        primaryColor.copy(alpha = 0.55f),
                                        primaryColor.copy(alpha = 0.15f)
                                    )
                                } else {
                                    listOf(
                                        onSurfaceColor.copy(alpha = if (enabled) 0.14f else 0.05f),
                                        outlineColor.copy(alpha = 0.05f)
                                    )
                                }
                            ),
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = when {
                            !enabled -> onSurfaceVariantColor.copy(alpha = 0.35f)
                            isActive -> primaryColor
                            else -> onSurfaceVariantColor.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        !enabled -> onSurfaceVariantColor.copy(alpha = 0.38f)
                        isActive -> primaryColor
                        else -> onSurfaceColor.copy(alpha = 0.92f)
                    }
                )
            }

            if (badgeText != null && badgeColor != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (shortcut != null) {
                // Frosted Glass Keycap Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isActive) primaryColor.copy(alpha = 0.18f)
                            else onSurfaceColor.copy(alpha = if (enabled) 0.06f else 0.03f)
                        )
                        .border(
                            width = 0.6.dp,
                            color = if (isActive) primaryColor.copy(alpha = 0.38f)
                            else onSurfaceColor.copy(alpha = if (enabled) 0.12f else 0.05f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortcut,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) primaryColor else onSurfaceVariantColor.copy(alpha = if (enabled) 0.78f else 0.35f)
                    )
                }
            } else if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = onSurfaceVariantColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HexEditorScreen(viewModel: HexEditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var showToolsDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showGotoDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var gotoOffset by remember { mutableStateOf("") }
    var gotoMode by remember { mutableStateOf("HEX") } // "HEX", "DEC", "BIN"
    
    LaunchedEffect(Unit) {
        viewModel.loadAutosavedBackup(context)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    LaunchedEffect(
        state.tabs,
        state.activeTabIndex,
        state.isDarkMode,
        state.themeColor,
        state.displayMode,
        state.encodingMode,
        state.desktopMode,
        state.sidebarWeight,
        state.isRestoreCompleted
    ) {
        if (state.isRestoreCompleted) {
            viewModel.triggerContinuousBackup(context)
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.openFiles(context, uris)
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    val openCompareFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.openAndCompareFile(context, uris.first())
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        viewModel.exportFile(context, uri)
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    if (state.showEntropySpectrogram) {
        EntropySpectrogramAppScreen(
            viewModel = viewModel,
            onClose = { viewModel.toggleEntropySpectrogram(false) }
        )
        return
    }

    if (state.showNativeDisassembler) {
        NativeDisassemblerAppScreen(
            viewModel = viewModel,
            onClose = { viewModel.toggleNativeDisassembler(false) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.isCtrlPressed) {
                        when (event.key) {
                            Key.F -> { showSearchDialog = true; true }
                            Key.G -> { showGotoDialog = true; true }
                            Key.H -> { viewModel.toggleInspector(); true }
                            Key.P -> { viewModel.toggleCommandPalette(); true }
                            Key.S -> { viewModel.togglePatchVerification(true); true }
                            Key.Z -> { 
                                if (event.isShiftPressed) viewModel.redo() else viewModel.undo()
                                true
                            }
                            else -> false
                        }
                    } else false
                } else false
            }
    ) {
        // App Header (h-14, #0A0A0A)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Top-Left XH Brand Button (Toggles Tools Sidebar)
            Row(
                modifier = Modifier
                    .bounceClick()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (showToolsDrawer) {
                            androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { showToolsDrawer = !showToolsDrawer }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // XH Icon Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = if (showToolsDrawer) {
                                    listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                                } else {
                                    listOf(Color(0xFF0284C7), Color(0xFF1D4ED8)) // cyan-600 to blue-700
                                }
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (showToolsDrawer) {
                                Color.White.copy(alpha = 0.65f)
                            } else {
                                Color.White.copy(alpha = 0.22f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "XH",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "X-HEX ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "PRO",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(modifier = Modifier.bounceClick(), onClick = { viewModel.undo() }, enabled = state.undoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (state.undoStack.isNotEmpty()) Color.LightGray else Color.DarkGray)
                }
                IconButton(modifier = Modifier.bounceClick(), onClick = { viewModel.redo() }, enabled = state.redoStack.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (state.redoStack.isNotEmpty()) Color.LightGray else Color.DarkGray)
                }
                IconButton(modifier = Modifier.bounceClick(), onClick = { openFileLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FileOpen, contentDescription = "Open", tint = Color.LightGray)
                }
                IconButton(
                    modifier = Modifier.bounceClick(),
                    onClick = { exportFileLauncher.launch(state.fileName) },
                    enabled = state.fileUri != null
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save",
                        tint = if (state.fileUri != null) Color.LightGray else Color.DarkGray
                    )
                }
            }
        }

        // Secondary Nav (Tabs) - only shown when tabs are open
        if (state.tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                    .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.Bottom
            ) {
                state.tabs.forEachIndexed { index, tab ->
                    val isActive = index == state.activeTabIndex
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant else androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.outline else Color.Transparent,
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                            .clickable { viewModel.switchTab(index) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.fileName + if(tab.patches.isNotEmpty()) " *" else "",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).clickable { viewModel.closeTab(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }

        // Main Body (Full-Width Content + Splashing Tools Drawer)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Central Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                    .padding(8.dp)
            ) {
                if (state.isLoading || state.isExporting || state.isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                } else if (state.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = state.error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                } else if (state.fileUri == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Image(
                                painter = painterResource(id = com.bytewave.R.drawable.ic_empty_folder),
                                contentDescription = "Nothing here!",
                                modifier = Modifier
                                    .sizeIn(maxWidth = 240.dp, maxHeight = 200.dp)
                                    .fillMaxWidth(0.65f),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nothing here!",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (state.desktopMode) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                val isSidebarVisible = state.showInspector && !state.floatingTools.contains("inspector")

                                // Left Panel: Hex Editor and splits
                                Column(modifier = Modifier.weight(if (isSidebarVisible) 1f - state.sidebarWeight else 1f)) {
                                    val currentCompareTab = state.compareTabIndex
                                    if (currentCompareTab != null) {
                                        if (state.compareSplitVertical) {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = state.activeTabIndex,
                                                    otherTabIndex = currentCompareTab,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(androidx.compose.material3.MaterialTheme.colorScheme.outline))
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = currentCompareTab,
                                                    otherTabIndex = state.activeTabIndex,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = state.activeTabIndex,
                                                    otherTabIndex = currentCompareTab,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.outline))
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = currentCompareTab,
                                                    otherTabIndex = state.activeTabIndex,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        HexEditorPane(
                                            viewModel = viewModel,
                                            state = state,
                                            tabIndex = state.activeTabIndex,
                                            otherTabIndex = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                
                                if (isSidebarVisible) {
                                    // Splitter handle control
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(28.dp)
                                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(
                                            onClick = { viewModel.setSidebarWeight(state.sidebarWeight + 0.05f) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("◀", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        IconButton(
                                            onClick = { viewModel.setSidebarWeight(state.sidebarWeight - 0.05f) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("▶", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Text("PIN", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                        IconButton(
                                            onClick = { viewModel.toggleFloatingTool("inspector") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("ℹ️", fontSize = 11.sp)
                                        }
                                    }

                                    // Right Panel: Sidebar
                                    Column(
                                        modifier = Modifier
                                            .weight(state.sidebarWeight)
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(start = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isSidebarVisible) {
                                            DataInspectorPanel(viewModel, state)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Non-desktop Layout: Weighted distribution
                            val showInsp = state.showInspector
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    val currentCompareTab = state.compareTabIndex
                                    if (currentCompareTab != null) {
                                        if (state.compareSplitVertical) {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = state.activeTabIndex,
                                                    otherTabIndex = currentCompareTab,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(1.dp).fillMaxHeight().background(androidx.compose.material3.MaterialTheme.colorScheme.outline))
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = currentCompareTab,
                                                    otherTabIndex = state.activeTabIndex,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = state.activeTabIndex,
                                                    otherTabIndex = currentCompareTab,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.outline))
                                                HexEditorPane(
                                                    viewModel = viewModel,
                                                    state = state,
                                                    tabIndex = currentCompareTab,
                                                    otherTabIndex = state.activeTabIndex,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    } else {
                                        HexEditorPane(
                                            viewModel = viewModel,
                                            state = state,
                                            tabIndex = state.activeTabIndex,
                                            otherTabIndex = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                
                                if (showInsp) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .padding(top = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                                            DataInspectorPanel(viewModel, state)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Overlay Detached/Floating Tools on Canvas
                        if (state.floatingTools.isNotEmpty()) {
                            state.floatingTools.forEach { tool ->
                                key(tool) {
                                    var offset by remember { mutableStateOf(Offset(50f, 50f)) }
                                    Box(
                                        modifier = Modifier
                                            .offset { androidx.compose.ui.unit.IntOffset(offset.x.toInt(), offset.y.toInt()) }
                                            .pointerInput(Unit) {
                                                detectTapGestures(onPress = {})
                                            }
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    offset += dragAmount
                                                }
                                            }
                                            .width(310.dp)
                                            .height(280.dp)
                                            .shadow(8.dp, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                                            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "ℹ️ DETACHED INSPECTOR",
                                                    modifier = Modifier.testTag("detached_title"),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                IconButton(
                                                    onClick = { viewModel.toggleFloatingTool(tool) },
                                                    modifier = Modifier.size(16.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Close, contentDescription = "Dock Window", modifier = Modifier.size(12.dp))
                                                }
                                            }
                                            Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp)) {
                                                DataInspectorPanel(viewModel, state)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Analysis or Comparison Pane
                    Spacer(modifier = Modifier.height(8.dp))
                    val currentCompare = state.compareTabIndex
                    if (currentCompare != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                                        ) {
                                            Text(
                                                "DIFF / COMPARE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val otherTabName = state.tabs.getOrNull(currentCompare)?.fileName ?: "Tab"
                                        Text(
                                            text = "Comparing vs $otherTabName",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Prev Diff Button
                                        OutlinedButton(
                                            onClick = { viewModel.jumpToPrevDiff() },
                                            enabled = state.compareDiffOffsets.isNotEmpty(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("◀ Prev", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Diff Index Counter
                                        if (state.compareDiffOffsets.isNotEmpty()) {
                                            Text(
                                                text = "${(state.compareCurrentDiffIndex + 1).coerceAtLeast(1)}/${state.compareDiffOffsets.size}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                        }

                                        // Next Diff Button
                                        OutlinedButton(
                                            onClick = { viewModel.jumpToNextDiff() },
                                            enabled = state.compareDiffOffsets.isNotEmpty(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Next ▶", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Split orientation switch
                                        IconButton(
                                            onClick = { viewModel.toggleCompareSplitOrientation() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text(
                                                if (state.compareSplitVertical) "⚏" else "◫",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Stop Diff
                                        IconButton(
                                            onClick = { viewModel.cancelCompareMode() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Close,
                                                contentDescription = "Cancel comparison",
                                                modifier = Modifier.size(16.dp),
                                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Column {
                                            Text("DIFFERING BYTES", fontSize = 8.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (state.compareIsScanning) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    androidx.compose.material3.CircularProgressIndicator(
                                                        modifier = Modifier.size(10.dp),
                                                        strokeWidth = 1.5.dp,
                                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Scanning...", fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            } else {
                                                val diffVal = state.compareDifferencesCount
                                                val diffText = if (diffVal == null) "Computing..." else if (diffVal < 0) "Error" else "$diffVal mismatch bytes"
                                                Text(
                                                    text = diffText,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (diffVal != null && diffVal > 0) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Column {
                                            Text("ACTIVE MODIFICATIONS", fontSize = 8.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${state.patches.size} patches", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                                        }
                                        Column {
                                            Text("MATCH STATUS", fontSize = 8.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                            val statusText = if (state.compareDifferencesCount == 0) "100% Identical" else if (state.compareDifferencesCount != null) "${state.compareDifferencesCount} differences" else "Computing..."
                                            Text(
                                                statusText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (state.compareDifferencesCount == 0) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Similarity Bar
                                val total = state.fileSize.coerceAtLeast(1L)
                                val diffs = state.compareDifferencesCount ?: 0
                                val matchPercentage = ((total - diffs).coerceAtLeast(0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(matchPercentage)
                                            .fillMaxHeight()
                                            .background(
                                                if (matchPercentage == 1f) androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                else androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .shadow(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "ANALYSIS: BINARY FILE", 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "VALID BINARY", 
                                    fontSize = 9.sp, 
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("FILE SIZE", fontSize = 8.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${state.fileSize} Bytes", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                }
                                Column {
                                    Text("PATCHES", fontSize = 8.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${state.patches.size} modifications", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface, CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.66f)
                                        .fillMaxHeight()
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // Soft Atmospheric Glass Scrim Overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showToolsDrawer,
                enter = fadeIn(tween(240)),
                exit = fadeOut(tween(180))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.62f),
                                    Color.Black.copy(alpha = 0.38f)
                                )
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showToolsDrawer = false }
                )
            }

            // Modern Translucent Glassmorphic Sidebar with Smooth Specular Edges
            androidx.compose.animation.AnimatedVisibility(
                visible = showToolsDrawer,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(200)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 190, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(150))
            ) {
                val isDark = state.isDarkMode
                val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                val glassShape = RoundedCornerShape(22.dp)

                val glassBackgroundBrush = if (isDark) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF161C26).copy(alpha = 0.84f),
                            Color(0xFF0E131B).copy(alpha = 0.88f),
                            Color(0xFF0A0D14).copy(alpha = 0.91f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF).copy(alpha = 0.86f),
                            Color(0xFFF8FAFC).copy(alpha = 0.90f),
                            Color(0xFFF1F5F9).copy(alpha = 0.92f)
                        )
                    )
                }

                val glassRimBrush = if (isDark) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            primaryColor.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            primaryColor.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.08f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                        .width(290.dp)
                        .shadow(
                            elevation = 28.dp,
                            shape = glassShape,
                            ambientColor = Color.Black.copy(alpha = 0.65f),
                            spotColor = primaryColor.copy(alpha = 0.25f)
                        )
                        .clip(glassShape)
                        .background(glassBackgroundBrush)
                        .border(
                            width = 1.dp,
                            brush = glassRimBrush,
                            shape = glassShape
                        )
                ) {
                    // Subtle Top-Left Ambient Glass Specular Sheen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = if (isDark) 0.10f else 0.07f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Pinned Glass Drawer Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Glowing Glass Brand Badge
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    primaryColor.copy(alpha = 0.28f),
                                                    primaryColor.copy(alpha = 0.10f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 0.8.dp,
                                            color = primaryColor.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Workspace & Tools",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (state.fileUri != null) state.fileName else "Ready • No active file",
                                        fontSize = 11.sp,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Frosted Glass Close Button
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                                    )
                                    .border(
                                        width = 0.6.dp,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    )
                                    .clickable { showToolsDrawer = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Sidebar",
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Luminous Glass Hairline Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                            primaryColor.copy(alpha = 0.25f),
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Smooth Scrollable Menu Body
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            // DIFF & COMPARE SECTION
                            DrawerSectionHeader("DIFF & COMPARE")

                            if (state.compareTabIndex != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    primaryColor.copy(alpha = 0.16f),
                                                    primaryColor.copy(alpha = 0.05f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 0.8.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    primaryColor.copy(alpha = 0.45f),
                                                    primaryColor.copy(alpha = 0.15f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val otherTab = state.tabs.getOrNull(state.compareTabIndex!!)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "vs ${otherTab?.fileName ?: "Tab"}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = primaryColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (state.compareDifferencesCount != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        text = "${state.compareDifferencesCount} diffs",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.jumpToPrevDiff() },
                                                enabled = state.compareDiffOffsets.isNotEmpty(),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("◀ Prev", fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.jumpToNextDiff() },
                                                enabled = state.compareDiffOffsets.isNotEmpty(),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Next ▶", fontSize = 11.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.toggleCompareSplitOrientation() },
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    if (state.compareSplitVertical) "Side-by-Side" else "Stacked",
                                                    fontSize = 10.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.cancelCompareMode()
                                                    showToolsDrawer = false
                                                },
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(0.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Stop Diff", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                DrawerToolItem(
                                    icon = Icons.Default.CompareArrows,
                                    title = "Open 2nd File to Compare",
                                    onClick = {
                                        openCompareFileLauncher.launch(arrayOf("*/*"))
                                        showToolsDrawer = false
                                    }
                                )

                                DrawerToolItem(
                                    icon = Icons.Default.Code,
                                    title = "Duplicate Tab to Diff",
                                    enabled = state.fileUri != null,
                                    onClick = {
                                        viewModel.duplicateCurrentTabForCompare()
                                        showToolsDrawer = false
                                    }
                                )

                                if (state.tabs.size > 1) {
                                    var showCompareDropdown by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        DrawerToolItem(
                                            icon = Icons.Default.CompareArrows,
                                            title = "Compare with Open Tab",
                                            trailingIcon = Icons.Default.ArrowDropDown,
                                            onClick = { showCompareDropdown = true }
                                        )
                                        DropdownMenu(
                                            expanded = showCompareDropdown,
                                            onDismissRequest = { showCompareDropdown = false }
                                        ) {
                                            state.tabs.forEachIndexed { idx, tab ->
                                                if (idx != state.activeTabIndex) {
                                                    DropdownMenuItem(
                                                        text = { Text("Compare with ${tab.fileName}", fontSize = 12.sp) },
                                                        onClick = {
                                                            viewModel.toggleCompareMode(idx)
                                                            showCompareDropdown = false
                                                            showToolsDrawer = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // WORKSPACE & COLUMNS SECTION
                            DrawerSectionHeader("WORKSPACE & COLUMNS")

                            DrawerToolItem(
                                icon = Icons.Default.Tune,
                                title = if (state.desktopMode) "16 Columns (Full Space)" else "8 Columns (Compact)",
                                shortcut = if (state.desktopMode) "16 B" else "8 B",
                                isActive = state.desktopMode,
                                onClick = { viewModel.toggleDesktopMode() }
                            )

                            // ANALYSIS SECTION
                            DrawerSectionHeader("ANALYSIS")

                            DrawerToolItem(
                                icon = Icons.Default.Analytics,
                                title = "Shannon's Entropy Spectrogram",
                                badgeText = "STUDIO",
                                badgeColor = Color(0xFFFF2E93),
                                isActive = state.showEntropySpectrogram,
                                onClick = {
                                    showToolsDrawer = false
                                    viewModel.toggleEntropySpectrogram(true)
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.Memory,
                                title = "CPU Disassembler & Decompiler",
                                badgeText = "4-ISA",
                                badgeColor = Color(0xFF00E676),
                                isActive = state.showNativeDisassembler,
                                onClick = {
                                    showToolsDrawer = false
                                    viewModel.toggleNativeDisassembler(true)
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.Search,
                                title = "Find & Replace",
                                shortcut = "Ctrl+F",
                                onClick = {
                                    showSearchDialog = true
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                title = "Go to Offset",
                                shortcut = "Ctrl+G",
                                enabled = state.fileUri != null,
                                onClick = {
                                    showGotoDialog = true
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.Info,
                                title = "Data Inspector",
                                shortcut = "Ctrl+H",
                                isActive = state.showInspector,
                                enabled = state.fileUri != null,
                                onClick = {
                                    viewModel.toggleInspector()
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.Code,
                                title = "Virtual Hex Keypad",
                                isActive = state.showKeyboard,
                                onClick = {
                                    viewModel.toggleKeyboard()
                                }
                            )

                            // WORKSPACE & SYSTEM SECTION
                            DrawerSectionHeader("WORKSPACE & SYSTEM")

                            DrawerToolItem(
                                icon = Icons.Default.Settings,
                                title = "Preferences & Encodings",
                                onClick = {
                                    showSettingsDialog = true
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.History,
                                title = "File History & Backups",
                                onClick = {
                                    viewModel.toggleFileHistory(true)
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.Analytics,
                                title = "Command Palette",
                                shortcut = "Ctrl+P",
                                onClick = {
                                    viewModel.toggleCommandPalette()
                                    showToolsDrawer = false
                                }
                            )

                            val hasErrors = remember(state.diagnosticLogs) {
                                state.diagnosticLogs.any { it.level == com.bytewave.LogLevel.ERROR || it.level == com.bytewave.LogLevel.CRITICAL }
                            }
                            DrawerToolItem(
                                icon = Icons.Default.Warning,
                                title = "Diagnostic Logs",
                                badgeText = if (hasErrors) "ERR" else null,
                                badgeColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                onClick = {
                                    viewModel.toggleLogsDialog()
                                    showToolsDrawer = false
                                }
                            )

                            DrawerToolItem(
                                icon = Icons.Default.HelpOutline,
                                title = "Shortcuts & Guide",
                                shortcut = "?",
                                onClick = {
                                    showGuideDialog = true
                                    showToolsDrawer = false
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Luminous Glass Footer Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                            primaryColor.copy(alpha = 0.25f),
                                            androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            DrawerToolItem(
                                icon = if (state.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                title = if (state.isDarkMode) "Appearance: Dark" else "Appearance: Light",
                                shortcut = if (state.isDarkMode) "DARK" else "LIGHT",
                                onClick = {
                                    viewModel.toggleDarkMode()
                                }
                            )
                        }
                    }
                }
            }
        }
        
        HexKeyboardPanel(viewModel, state)

        // Footer (h-8, #0A0A0A)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TOTAL ROWS: ${if (state.fileUri != null) viewModel.getRowCount() else 0}", 
                fontSize = 9.sp, 
                fontFamily = FontFamily.Monospace, 
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            
            if (state.searchResults.isNotEmpty()) {
                Text(
                    "MATCH ${state.currentSearchIndex + 1}/${state.searchResults.size}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                TextButton(onClick = { viewModel.searchPrev() }, contentPadding = PaddingValues(0.dp)) {
                    Text("PREV", fontSize = 9.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { viewModel.searchNext() }, contentPadding = PaddingValues(0.dp)) {
                    Text("NEXT", fontSize = 9.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.encodingMode.displayName, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text("READY", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            }
        }
    }
    
    if (showSettingsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        text = com.bytewave.Translations.get("Settings", state.appLanguage), 
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, 
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(com.bytewave.Translations.get("Display Mode", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Row {
                        RadioButton(selected = state.displayMode == DisplayMode.HEX, onClick = { viewModel.toggleDisplayMode(DisplayMode.HEX) }, colors = RadioButtonDefaults.colors(selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary))
                        Text("HEX", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterVertically).clickable { viewModel.toggleDisplayMode(DisplayMode.HEX) })
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = state.displayMode == DisplayMode.BINARY, onClick = { viewModel.toggleDisplayMode(DisplayMode.BINARY) }, colors = RadioButtonDefaults.colors(selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary))
                        Text("BINARY", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterVertically).clickable { viewModel.toggleDisplayMode(DisplayMode.BINARY) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(com.bytewave.Translations.get("Text Encoding", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    var expandedEncoding by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedEncoding = !expandedEncoding }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.encodingMode.displayName,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (expandedEncoding) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Encoding List",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = expandedEncoding) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            ) {
                                com.bytewave.EncodingMode.entries.forEach { mode ->
                                    val isSelected = state.encodingMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.toggleEncodingMode(mode)
                                                expandedEncoding = false
                                            }
                                            .background(
                                                if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = mode.displayName,
                                            color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(com.bytewave.Translations.get("Theme Color", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Interactive 2-column grid of Theme Color styles (works reliably inside Dialog without PopupWindow issues)
                    val themeRows = com.bytewave.AppThemeColor.entries.chunked(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeRows.forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowThemes.forEach { colorStyle ->
                                    val isSelected = state.themeColor == colorStyle
                                    val swatchColor = when (colorStyle) {
                                        com.bytewave.AppThemeColor.CYBERPUNK -> Color(0xFF06B6D4)
                                        com.bytewave.AppThemeColor.MONOKAI -> Color(0xFFFD971F)
                                        com.bytewave.AppThemeColor.TERMINAL -> Color(0xFF22C55E)
                                        com.bytewave.AppThemeColor.DRACULA -> Color(0xFFBD93F9)
                                        com.bytewave.AppThemeColor.NORD -> Color(0xFF88C0D0)
                                        com.bytewave.AppThemeColor.AMOLED -> if (state.isDarkMode) Color(0xFFE2E2E2) else Color(0xFF1E293B)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                } else {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                } else {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.outline
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                viewModel.setThemeColor(colorStyle)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor)
                                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = colorStyle.displayName,
                                                color = if (isSelected) {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                } else {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(com.bytewave.Translations.get("Layout", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.desktopMode,
                            onCheckedChange = { viewModel.toggleDesktopMode(it) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.bytewave.Translations.get("Desktop Mode (16 Columns)", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    androidx.compose.material3.Button(
                        onClick = {
                            showSettingsDialog = false
                            viewModel.toggleOtherSettings(true)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("open_other_settings_button"),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Other Settings",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(com.bytewave.Translations.get("Open Other Settings", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSettingsDialog = false }) { 
                            Text(com.bytewave.Translations.get("Close", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary) 
                        }
                    }
                }
            }
        }
    }

    if (showGotoDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showGotoDialog = false
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = com.bytewave.Translations.get("Goto", state.appLanguage), 
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, 
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = gotoOffset,
                        onValueChange = { gotoOffset = it },
                        modifier = Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
                        textStyle = androidx.compose.ui.text.TextStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("HEX" to "Hex (16)", "DEC" to "Dec (10)", "BIN" to "Bin (2)").forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (gotoMode == mode) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .clickable { gotoMode = mode }
                                    .background(if (gotoMode == mode) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = gotoMode == mode,
                                    onClick = { gotoMode = mode },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = com.bytewave.Translations.get(label, state.appLanguage), 
                                    fontSize = 12.sp, 
                                    fontWeight = if (gotoMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (gotoMode == mode) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            showGotoDialog = false
                        }) {
                            Text(com.bytewave.Translations.get("Cancel", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.TextButton(onClick = {
                            try {
                                val offsetStr = gotoOffset.trim()
                                val radix = when (gotoMode) {
                                    "HEX" -> 16
                                    "BIN" -> 2
                                    else -> 10
                                }
                                val rawOffset = offsetStr.toLongOrNull(radix)
                                if (rawOffset != null) {
                                    viewModel.selectOffset(rawOffset)
                                }
                            } catch (_: Exception) {}
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            showGotoDialog = false
                        }) {
                            Text(com.bytewave.Translations.get("OK", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // EXTRA DIALOGS & OVERLAY SCREENS

    if (state.showCommandPalette) {
        var commandQuery by remember { mutableStateOf("") }
        val filteredCommands = remember(commandQuery) {
            val q = commandQuery.lowercase()
            listOf(
                PaletteCommand("Go To Offset / Address Jump", "Ctrl + G", Icons.AutoMirrored.Filled.ArrowForward) {
                    showGotoDialog = true
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Find Bytes or Text", "Ctrl + F", Icons.Default.Search) {
                    showSearchDialog = true
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Verify current modifications", "Ctrl + S", Icons.Default.Save) {
                    viewModel.togglePatchVerification(true)
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Toggle Desktop Workspace UI", "Mode", Icons.Default.Code) {
                    viewModel.toggleDesktopMode(!state.desktopMode)
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Create Backup copy (.bak)", "Backup", Icons.Default.Save) {
                    viewModel.createBackupFileBeforeExport(context)
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Discard current modifications", "Clear", Icons.Outlined.Close) {
                    viewModel.clearPatches()
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Toggle Dark / Light Mode", "Theme", Icons.Default.Info) {
                    viewModel.toggleDarkMode()
                    viewModel.toggleCommandPalette()
                },
                PaletteCommand("Cycle Theme Accent Color", "Theme", Icons.Default.Settings) {
                    val nextTheme = when (state.themeColor) {
                        com.bytewave.AppThemeColor.CYBERPUNK -> com.bytewave.AppThemeColor.MONOKAI
                        com.bytewave.AppThemeColor.MONOKAI -> com.bytewave.AppThemeColor.TERMINAL
                        com.bytewave.AppThemeColor.TERMINAL -> com.bytewave.AppThemeColor.DRACULA
                        com.bytewave.AppThemeColor.DRACULA -> com.bytewave.AppThemeColor.NORD
                        com.bytewave.AppThemeColor.NORD -> com.bytewave.AppThemeColor.AMOLED
                        com.bytewave.AppThemeColor.AMOLED -> com.bytewave.AppThemeColor.CYBERPUNK
                    }
                    viewModel.setThemeColor(nextTheme)
                    viewModel.toggleCommandPalette()
                }
            ).filter { it.title.lowercase().contains(q) }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.toggleCommandPalette()
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 480.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COMMAND PALETTE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = commandQuery,
                        onValueChange = { commandQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("command_palette_search"),
                        placeholder = { Text("Search settings or actions...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCommands) { cmd ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        cmd.action()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(cmd.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(cmd.title, fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(cmd.shortcut, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showPatchVerification) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.togglePatchVerification(false) }
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 420.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PATCH VERIFICATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Below is the listing of current unsaved changes that will be applied to the binary:",
                        fontSize = 11.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val patchesList = state.patches.toList().sortedBy { it.first }
                    if (patchesList.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No changes made yet.", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(patchesList) { (offset, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "+${String.format("0x%08X", offset)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "PATCHED: ${String.format("0x%02X", value)}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.clearPatches()
                                viewModel.togglePatchVerification(false)
                            }
                        ) {
                            Text("CLEAR ALL", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.togglePatchVerification(false) }
                        ) {
                            Text("DONE", color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    if (state.hasAutosavedBackup && !state.saveImportantFilesAutomatically) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.dismissAutosaveBanner() }
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 16.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🛡️ CRASH RECOVERY ACTIVATED",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All workspace tabs, file configurations, and unsaved byte patches from your last session have been restored automatically.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { viewModel.dismissAutosaveBanner() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text("RESUME WORK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (state.showOtherSettings) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.toggleOtherSettings(false) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                OtherSettingsScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.toggleOtherSettings(false) }
                )
            }
        }
    }

    if (state.showFileHistory) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.toggleFileHistory(false) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background
            ) {
                FileHistoryScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.toggleFileHistory(false) },
                    onFileSelected = { showSettingsDialog = false }
                )
            }
        }
    }

    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(com.bytewave.SearchDataType.HEX_TEXT) }
        var isBigEndian by remember { mutableStateOf(false) }
        var showTypeDropdown by remember { mutableStateOf(false) }
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                keyboardController?.hide()
                focusManager.clearFocus()
                showSearchDialog = false
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = com.bytewave.Translations.get("Search", state.appLanguage), 
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, 
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(com.bytewave.Translations.get("Text to find", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        ),
                        placeholder = { Text(com.bytewave.Translations.get("Please enter the content", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    val hexPreview = viewModel.parseSearchQuery(query, selectedType, isBigEndian)
                    if (hexPreview != null) {
                        val previewText = when (hexPreview) {
                            is com.bytewave.HexEditorViewModel.SearchPattern.ExactMatch -> hexPreview.bytes.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
                            is com.bytewave.HexEditorViewModel.SearchPattern.ByteMaskMatch -> hexPreview.mask.joinToString(" ") { it?.let { String.format("%02X", it) } ?: "??" }
                            is com.bytewave.HexEditorViewModel.SearchPattern.RegexMatch -> "Regex: ${hexPreview.pattern.pattern}"
                        }
                        Text(
                            text = previewText,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (query.isNotEmpty()) {
                        Text(
                            text = "Invalid format",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(com.bytewave.Translations.get("Data type", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTypeDropdown = !showTypeDropdown }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedType.displayName,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (showTypeDropdown) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Data Type List",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = showTypeDropdown) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                            ) {
                                com.bytewave.SearchDataType.entries.forEach { type ->
                                    val isSelected = selectedType == type
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedType = type
                                                showTypeDropdown = false
                                            }
                                            .background(
                                                if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (type.name == "HEX") com.bytewave.Translations.get("Hex Text", state.appLanguage) else type.displayName,
                                            color = if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isBigEndian, 
                            onCheckedChange = { isBigEndian = it }, 
                            colors = CheckboxDefaults.colors(checkedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary, uncheckedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(com.bytewave.Translations.get("Big endian", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            showSearchDialog = false
                        }) {
                            Text(com.bytewave.Translations.get("Cancel", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val pattern = viewModel.parseSearchQuery(query, selectedType, isBigEndian)
                            if (pattern != null) {
                                viewModel.search(pattern)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                showSearchDialog = false
                            }
                        }) {
                            Text(com.bytewave.Translations.get("Search", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    
    if (showGuideDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showGuideDialog = false }
                ),
            contentAlignment = Alignment.Center
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .padding(8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Do nothing on internal click */ }
                    ),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.bytewave.Translations.get("Hex Editor Guide", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.IconButton(onClick = { showGuideDialog = false }) {
                            Text("X", fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Introduction Section
                        Text(
                            text = "🧠 " + com.bytewave.Translations.get("INTRODUCTION TO LOW-LEVEL HEX SYSTEMS", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = com.bytewave.Translations.get("What is a Hex Editor?", state.appLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = com.bytewave.Translations.get("A hex editor allows viewing and editing of raw, binary files. Unlike standard text editors which parse bytes as text characters (automatically correcting, altering or failing on non-printable codes), a Hex Editor presents the fundamental data as base-16 numerical byte matrices. This lets reverse-engineers, forensic analysts, and developers inspect and patch precise offsets without corrupting any binary headers or structures.", state.appLanguage),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = com.bytewave.Translations.get("Why Base-16 (Hexadecimal) Representation?", state.appLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = com.bytewave.Translations.get("The binary number system (base-2, 0s & 1s) is too broad and high-volume for fast manual scans, and base-10 (decimal) does not map cleanly into standard 8-bit memory cells. Hexadecimal (base-16) perfectly bridges this gap. One hex digit (0-F) translates exactly to a 4-bit nibble, and precisely two hex digits represent a full 8-bit byte. Hence, any memory address, machine code, or payload aligns beautifully within predictable bounds.", state.appLanguage),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = com.bytewave.Translations.get("Gigabyte-Scale Lazy Streaming Engine", state.appLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = com.bytewave.Translations.get("To manipulate micro or multi-gigabyte structures flawlessly within bounded device RAM, the workspace utilizes a custom asynchronous page/chunk streaming pipeline. Each block (64KB segment) is loaded dynamically upon viewport proximity and cached. Edits are staged on a memory patch overlay map, which merges with the loader on-the-fly, completely bypassing continuous disk write overheads.", state.appLanguage),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Chapter 1
                        Text(
                            text = "📁 " + com.bytewave.Translations.get("CHAPTER 1: WORKSPACE & SAFEGUARDS", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                GuideItem("🗂️ Multi-Tabbed Workspace System", "Allows loading multiple files into concurrent workspaces. The application persists independent states for current offset selection, scroll position, search query length, current search highlights, patches, and analyzer layouts for each individual tab.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("⚖️ High-Contrast Compare Mode", "Select and overlay another open tab to compare side-by-side with your active tab. Detect mismatches, highlight differing byte values instantly, and trace binary delta differences in real-time.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🛡️ Autosave & Continuous Backup", "Instantly backs up modified bytes (the patches overlay) locally. If Android kills the app to claim RAM, your active files, cursor positions, and staged changes are recovered completely on the next launch.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🔑 Revoked Permission Relinker", "Android revokes persistable open file permissions upon device reboots. Instead of breaking your tab state, this system detects the loss, pops up a specialized permit helper, and lets you re-open the source file to stitch and resume your work immediately.", state.appLanguage)
                            }
                        }

                        // Chapter 2
                        Text(
                            text = "🧱 " + com.bytewave.Translations.get("CHAPTER 2: EDITING MATRIX & INPUTS", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                GuideItem("🖱️ Interactive Grid Selection", "Tap any byte cell to target an address. Highlights the selected anchor, active search query overlaps, and patched edits in high-contrast custom UI colors.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🔤 Realtime Text Decode Layout", "An adjacent column displays decoded text representation (ASCII/UTF) side-by-side. Safe decoding filters non-printable control byte ranges into miniature spacing periods (.) to maintain grid boundaries.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("💡 Double-Endian Data Inspector", "Parses selected byte arrays on the fly. Interprets binary as signed/unsigned types (Int8, UInt8, Int16, UInt16, Int32, UInt32, Int64, UInt64) and floating-point real formats (Float, Double). Toggle between Little Endian or Big Endian byte ordering instantly.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🛠️ Precise Bit-Level Toggler", "Visible right inside the data inspector. Expands the chosen byte into its 8 fundamental constituent bit positions. Direct tapping flips a bit (0 ⇄ 1), automatically rebuilding the Hex byte and updating the matrix immediately.", state.appLanguage)
                            }
                        }

                        // Chapter 3
                        Text(
                            text = "🔍 " + com.bytewave.Translations.get("CHAPTER 3: SEARCH, GOTO, & INPUT CORES", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                GuideItem("🔭 Advanced Pattern Search Engine", "Locate target sequences instantly. Supports hex patterns (e.g. 50 4B 03 04) or simple text queries dynamically encoded into ASCII, UTF-8, or UTF-16 variants. Jumps and scrolls smoothly across all matching indices in the file.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🚀 Rapid GoTo Address Finder", "Allows jumping to arbitrary file offsets immediately. Supports hexadecimal notation prefixes (e.g. 0x0FA1, 1C00) or standard numerical base-10 decimal limits.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("⌨️ Multi-Mode Editing Input Methods", "Allows overwriting bytes by typing directly with your device's physical USB/Bluetooth keyboards, or utilizing the on-screen soft hexpad overlay to comfortably insert precise nibbles one by one.", state.appLanguage)
                            }
                        }

                        // Chapter 4
                        Text(
                            text = "🔬 " + com.bytewave.Translations.get("CHAPTER 4: BINARY INTELLIGENCE & VISUALIZERS", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                GuideItem("🏷️ Magic Header Signature Analyzer", "Examines initial magic headers to determine binary formats instantly. Supports full reports for Linux Core files/SO libraries (ELF), Android VM bytecode (DEX), Windows Executables/DLLs (MZ PE), ZIP/APK packaging, and common multimedia assets (PNG, JPEG, GIF, PDF).", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("📶 Shannon's Entropy Spectrogram", "Measures random information density within the binary. Near 0.0 means homogeneous data (lots of repeating zeroes or null values), while values close to 8.0 reveal heavily compressed, encrypted sections, or hidden malware payloads.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🦾 Native CPU Disassembler & Decompiler", "Decodes executable machine bytecode back into assembly statements, listing mnemonics, registers, opcodes, and branches. Select between Intel x86_64, ARM64 (A64), ARM32 (Thumb/T32), and Dalvik DEX CPU decompiler engines.", state.appLanguage)
                            }
                        }

                        // Chapter 5
                        Text(
                            text = "🖥️ " + com.bytewave.Translations.get("CHAPTER 5: DESKTOP MODE ARCHITECTURE", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                GuideItem("📊 Dual-Pane Screen Partitioning", "On larger displays (such as tablets, Chromebooks, DeX desktops, horizontal monitors), the app drops compact overlay dialogs and locks into a professional side-by-side IDE layout. The HEX matrix stays on the left, and advanced analyzers reside on the right.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("📏 Draggable Split Width Adjuster", "Supports real-time resizing of the split-pane division, which acts as a draggable horizontal slider to allocate more screen space to the editor or to decompiled views.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("🏷️ Adjustable Row Byte Density", "Dynamically change display columns inside settings to suit physical landscape ratios. Options range from 4, 8, 16, 24, and up to 32 bytes wide on a single row.", state.appLanguage)
                                Spacer(modifier = Modifier.height(6.dp))
                                GuideItem("📑 Draggable Floating Tools System", "A professional overlay manager that allows picking tools (like Data Inspector, Search, Analytics, Entropy charts) and converting them into movable floating windows. Drag and overlay them anywhere on a broad monitor for a perfect reverse-engineering workspace.", state.appLanguage)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "📞 " + com.bytewave.Translations.get("CONTACT DEVELOPER", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = com.bytewave.Translations.get("Name: bytewave-coder", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = com.bytewave.Translations.get("Email: bytewavext@gmail.com", state.appLanguage),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (state.showLogsDialog) {
        var selectedLogId by remember { mutableStateOf<String?>(null) }
        var filterByErrorsOnly by remember { mutableStateOf(false) }
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        
        val filteredLogs = remember(state.diagnosticLogs, filterByErrorsOnly) {
            if (filterByErrorsOnly) {
                state.diagnosticLogs.filter { it.level == com.bytewave.LogLevel.ERROR || it.level == com.bytewave.LogLevel.CRITICAL }
            } else {
                state.diagnosticLogs
            }
        }
        
        val selectedLog = remember(filteredLogs, selectedLogId) {
            filteredLogs.find { it.id == selectedLogId } ?: filteredLogs.firstOrNull()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.toggleLogsDialog() }
                ),
            contentAlignment = Alignment.Center
        ) {
            com.bytewave.AnimatedDialogSurface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
                    .padding(8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Do nothing on internal click */ }
                    ),
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🩺 " + com.bytewave.Translations.get("ENGINE DIAGNOSTICS & SYSTEM LOGS", state.appLanguage),
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = com.bytewave.Translations.get("Monitor real-time system activities, memory operations, and trace errors.", state.appLanguage),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        androidx.compose.material3.IconButton(onClick = { viewModel.toggleLogsDialog() }) {
                            Text("X", fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Filters & Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DiagnosticFilterChip(
                                selected = !filterByErrorsOnly,
                                onClick = { filterByErrorsOnly = false },
                                label = com.bytewave.Translations.get("All Logs (${state.diagnosticLogs.size})", state.appLanguage)
                            )
                            val errorCount = state.diagnosticLogs.count { it.level == com.bytewave.LogLevel.ERROR || it.level == com.bytewave.LogLevel.CRITICAL }
                            DiagnosticFilterChip(
                                selected = filterByErrorsOnly,
                                onClick = { filterByErrorsOnly = true },
                                label = com.bytewave.Translations.get("Errors/Crashes Only ($errorCount)", state.appLanguage),
                                selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        
                        Row {
                            TextButton(
                                onClick = { viewModel.clearLogs() },
                                colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error)
                            ) {
                                Text("🗑️ " + com.bytewave.Translations.get("Clear Buffer", state.appLanguage))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✨ " + com.bytewave.Translations.get("No logs captured in this thread yet.", state.appLanguage), fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(com.bytewave.Translations.get("Perform load, edit, search, or patch operations to invoke trace events.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        // Master-Detail logs viewer.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Master List on left
                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.55f)
                                    .fillMaxHeight()
                                    .border(
                                        1.dp, 
                                        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(4.dp)
                            ) {
                                items(filteredLogs) { log ->
                                    val isSelected = log.id == selectedLog?.id
                                    val logColor = when (log.level) {
                                        com.bytewave.LogLevel.INFO -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        com.bytewave.LogLevel.WARNING -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                                        com.bytewave.LogLevel.ERROR, com.bytewave.LogLevel.CRITICAL -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                    }
                                    
                                    val backgroundToken = if (isSelected) {
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    } else {
                                        Color.Transparent
                                    }
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(backgroundToken)
                                            .clickable { selectedLogId = log.id }
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = when(log.level) {
                                                        com.bytewave.LogLevel.INFO -> "ℹ️ [${log.tag}]"
                                                        com.bytewave.LogLevel.WARNING -> "⚠️ [${log.tag}]"
                                                        com.bytewave.LogLevel.ERROR, com.bytewave.LogLevel.CRITICAL -> "🚨 [${log.tag}]"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = logColor
                                                )
                                            }
                                            Text(
                                                text = log.timestamp,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = log.message,
                                            fontSize = 12.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (log.sourceFile != null) {
                                            Text(
                                                text = "at ${log.sourceFile}:${log.lineNumber}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                            
                            // Detail Panel on right
                            Column(
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight()
                                    .border(
                                        1.dp, 
                                        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                if (selectedLog != null) {
                                    val lvlColor = when (selectedLog.level) {
                                        com.bytewave.LogLevel.INFO -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        com.bytewave.LogLevel.WARNING -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                                        com.bytewave.LogLevel.ERROR, com.bytewave.LogLevel.CRITICAL -> androidx.compose.material3.MaterialTheme.colorScheme.error
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedLog.level.name + " " + com.bytewave.Translations.get("LEVEL EVENT", state.appLanguage).replace("LEVEL EVENT", "LEVEL EVENT"), // Quick patch to keep it simple
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = lvlColor
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                val detailsToCopy = buildString {
                                                    appendLine("=== BYTEMAESTRO TRACE LOG ===")
                                                    appendLine("Timestamp: ${selectedLog.timestamp}")
                                                    appendLine("Level: ${selectedLog.level}")
                                                    appendLine("Module/Tag: ${selectedLog.tag}")
                                                    appendLine("Message: ${selectedLog.message}")
                                                    if (selectedLog.sourceFile != null) {
                                                        appendLine("Location: ${selectedLog.sourceFile}:${selectedLog.lineNumber} (Function: ${selectedLog.sourceFunction})")
                                                    }
                                                    if (selectedLog.exceptionTrace != null) {
                                                        appendLine("Stack Trace:")
                                                        appendLine(selectedLog.exceptionTrace)
                                                    }
                                                }
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(detailsToCopy))
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("📋", fontSize = 14.sp)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = selectedLog.message,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = "LOCATION INFORMATION:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "📂 Path: /app/src/main/java/com/bytewave/viewmodel/${selectedLog.sourceFile ?: "Unknown"}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "🧩 Function: ${selectedLog.sourceFunction ?: "Unknown"}()",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "📏 Line Number: ${selectedLog.lineNumber ?: -1}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    if (selectedLog.exceptionTrace != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(text = "CRASH EXCEPTION STACKTRACE:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(vertical = 4.dp)
                                                .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.error.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .background(androidx.compose.material3.MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                                                .padding(6.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                                Text(
                                                    text = selectedLog.exceptionTrace,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = com.bytewave.Translations.get("No exception traces associated with this entry. Operation completed cleanly.", state.appLanguage),
                                                fontSize = 11.sp,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(com.bytewave.Translations.get("Select a log item to inspect code context.", state.appLanguage), fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.outline)
                                    }
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
fun DiagnosticFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    selectedColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) selectedColor else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (selected) selectedColor else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) selectedLabelColor else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GuideItem(title: String, desc: String, lang: com.bytewave.AppLanguage) {
    val textStartIndex = title.indexOfFirst { it.isLetter() || it.isDigit() }
    val emoji = if (textStartIndex > 0) title.substring(0, textStartIndex) else ""
    val textPart = if (textStartIndex > 0) title.substring(textStartIndex).trim() else title
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = emoji + com.bytewave.Translations.get(textPart, lang), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        Text(text = com.bytewave.Translations.get(desc, lang), fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HexRowItem(
    rowData: com.bytewave.HexRowData,
    cols: Int,
    currentCols: Int,
    boxWidth: androidx.compose.ui.unit.Dp,
    offsetWidth: androidx.compose.ui.unit.Dp = 64.dp,
    pipeWidth: androidx.compose.ui.unit.Dp = 8.dp,
    hexDataWidth: androidx.compose.ui.unit.Dp = 166.dp,
    sepSpacer: androidx.compose.ui.unit.Dp = 4.dp,
    rowFontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    rowLetterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    displayMode: com.bytewave.DisplayMode,
    encodingMode: com.bytewave.EncodingMode,
    selectedOffset: Long?,
    patches: Map<Long, Byte>,
    searchResults: List<Long>,
    currentSearchIndex: Int,
    searchQueryLength: Int,
    textDecWidth: androidx.compose.ui.unit.Dp,
    otherTabIndex: Int? = null,
    viewModel: com.bytewave.HexEditorViewModel,
    cacheVersion: Int = 0,
    onSelectOffset: (Long) -> Unit
) {
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val tertiaryColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    val errorColor = androidx.compose.material3.MaterialTheme.colorScheme.error
    val errorContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
    val onSurfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Offset
        Text(
            text = String.format("%08X", rowData.offset),
            fontFamily = FontFamily.Monospace,
            color = if (rowData.isPlaceholder) primaryColor.copy(alpha=0.3f) else primaryColor,
            fontSize = rowFontSize,
            letterSpacing = rowLetterSpacing,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(offsetWidth)
        )
        Spacer(modifier = Modifier.width(sepSpacer))
        Text(
            text = "|",
            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            fontSize = rowFontSize,
            letterSpacing = rowLetterSpacing,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(pipeWidth)
        )
        Spacer(modifier = Modifier.width(sepSpacer))
        
        // Hex Data (Optimized via AnnotatedString to prevent multi-node lag)
        var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
        val charsPerVal = if (displayMode == com.bytewave.DisplayMode.BINARY) 8 else 2
        val charsPerCol = charsPerVal + 1 // space
        
        val hexString = remember(rowData, cols, displayMode, selectedOffset, patches, searchResults, currentSearchIndex, primaryColor, tertiaryColor, errorColor, errorContainerColor, onSurfaceColor, otherTabIndex, cacheVersion) {
            androidx.compose.ui.text.buildAnnotatedString {
                for (i in 0 until cols) {
                    if (i > 0) append(" ")
                    if (cols == 16 && i == 8) append(" ")
                    val startIdx = length
                    if (i < rowData.length) {
                        val b = rowData.bytes[i]
                        val byteOffset = rowData.offset + i
                        val isPatched = patches.containsKey(byteOffset)
                        
                        val isMatch = if (searchResults.isNotEmpty() && currentSearchIndex in searchResults.indices) {
                            val matchStart = searchResults[currentSearchIndex]
                            val matchEnd = matchStart + searchQueryLength
                            byteOffset in matchStart until matchEnd
                        } else false
                        
                        val isDifferent = if (otherTabIndex != null) {
                            val otherByte = viewModel.getByteAtTab(otherTabIndex, byteOffset)
                            otherByte == null || otherByte != b
                        } else false
                        
                        val strValue = if (rowData.isPlaceholder) {
                            if (displayMode == com.bytewave.DisplayMode.BINARY) "........" else ".."
                        } else if (displayMode == com.bytewave.DisplayMode.BINARY) {
                            String.format("%8s", Integer.toBinaryString(b.toInt() and 0xFF)).replace(' ', '0')
                        } else {
                            Integer.toHexString(b.toInt() and 0xFF).padStart(2, '0').uppercase()
                        }
                        
                        append(strValue)
                        val endIdx = length
                        val isSelected = selectedOffset == byteOffset
                        val isSpecial = rowData.isPlaceholder || isMatch || isPatched || isSelected || isDifferent
                        
                        if (isSpecial) {
                            val textColor = if (rowData.isPlaceholder) onSurfaceColor.copy(alpha=0.2f) else if (isSelected) onSurfaceColor else if (isMatch) tertiaryColor else if (isPatched) errorColor else if (isDifferent) errorColor else onSurfaceColor
                            val bgColor = if (isSelected) primaryColor.copy(alpha=0.2f) else if (isMatch) tertiaryColor.copy(alpha=0.2f) else if (isPatched) errorColor.copy(alpha=0.2f) else if (isDifferent) errorContainerColor.copy(alpha = 0.25f) else Color.Transparent
                            val textWeight = if (isPatched || isMatch || isDifferent) FontWeight.Bold else FontWeight.Normal
                            
                            addStyle(
                                style = androidx.compose.ui.text.SpanStyle(
                                    color = textColor,
                                    background = bgColor,
                                    fontWeight = textWeight
                                ),
                                start = startIdx,
                                end = endIdx
                            )
                        }
                    } else {
                        append(" ".repeat(charsPerVal)) // Pad
                    }
                }
            }
        }
        
        val currentOnSelectOffset by rememberUpdatedState(onSelectOffset)
        val currentRowData by rememberUpdatedState(rowData)
        val currentCharsPerCol by rememberUpdatedState(charsPerCol)
        val currentColsState by rememberUpdatedState(cols)

        Text(
            text = hexString,
            color = onSurfaceColor,
            fontFamily = FontFamily.Monospace,
            fontSize = rowFontSize,
            letterSpacing = rowLetterSpacing,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(hexDataWidth)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        textLayoutResult?.let { layout ->
                            val offset = layout.getOffsetForPosition(pos)
                            val adjustedOffset = if (currentColsState == 16 && offset >= 8 * currentCharsPerCol) offset - 1 else offset
                            val byteIndex = (adjustedOffset / currentCharsPerCol).coerceIn(0, currentColsState - 1)
                            if (byteIndex < currentRowData.length && !currentRowData.isPlaceholder) {
                                currentOnSelectOffset(currentRowData.offset + byteIndex)
                            }
                        }
                    }
                },
            onTextLayout = { textLayoutResult = it }
        )
        
        Spacer(modifier = Modifier.width(sepSpacer))
        Text(
            text = "|",
            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            fontSize = rowFontSize,
            letterSpacing = rowLetterSpacing,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(pipeWidth)
        )
        Spacer(modifier = Modifier.width(sepSpacer))
        
        // Text decoding (Optimized via remember to prevent heavy re-allocation on scroll/render passes)
        var decodedTextLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
        val textStr = remember(rowData, encodingMode, otherTabIndex, patches, selectedOffset, searchResults, currentSearchIndex, primaryColor, tertiaryColor, errorColor, errorContainerColor, onSurfaceColor, cacheVersion) {
            androidx.compose.ui.text.buildAnnotatedString {
                if (rowData.isPlaceholder) {
                    append(".".repeat(rowData.length))
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(color = onSurfaceColor.copy(alpha = 0.2f)),
                        start = 0,
                        end = rowData.length
                    )
                } else {
                    val rawStr = if (encodingMode == com.bytewave.EncodingMode.ASCII) {
                        buildString {
                            for (i in 0 until rowData.length) {
                                val c = rowData.bytes[i].toInt().toChar()
                                if (c in ' '..'~') append(c) else append('.')
                            }
                        }
                    } else {
                        val charset = when (encodingMode) {
                            com.bytewave.EncodingMode.UTF_8 -> Charsets.UTF_8
                            com.bytewave.EncodingMode.UTF_16LE -> Charsets.UTF_16LE
                            com.bytewave.EncodingMode.UTF_16BE -> Charsets.UTF_16BE
                            com.bytewave.EncodingMode.GBK -> try { java.nio.charset.Charset.forName("GBK") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.ISO_8859_1 -> try { java.nio.charset.Charset.forName("ISO-8859-1") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.WINDOWS_1252 -> try { java.nio.charset.Charset.forName("windows-1252") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.SHIFT_JIS -> try { java.nio.charset.Charset.forName("Shift_JIS") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.EUC_KR -> try { java.nio.charset.Charset.forName("EUC-KR") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.EUC_JP -> try { java.nio.charset.Charset.forName("EUC-JP") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.UTF_32LE -> try { java.nio.charset.Charset.forName("UTF-32LE") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            com.bytewave.EncodingMode.UTF_32BE -> try { java.nio.charset.Charset.forName("UTF-32BE") } catch (_: Exception) { Charsets.ISO_8859_1 }
                            else -> Charsets.US_ASCII
                        }
                        val decoded = String(rowData.bytes, 0, rowData.length, charset).map { 
                            if (it.isISOControl() || it == '\uFFFD' || it == '\n' || it == '\r' || it == '\t') '.' else it 
                        }.joinToString("")
                        if (encodingMode == com.bytewave.EncodingMode.UTF_8 && decoded.length < rowData.length) {
                            decoded.padEnd(rowData.length, '.')
                        } else {
                            decoded
                        }
                    }
                    
                    append(rawStr)
                    val isOneToOne = rawStr.length == rowData.length
                    for (i in 0 until rawStr.length) {
                        val byteOffset = if (isOneToOne) {
                            rowData.offset + i
                        } else {
                            rowData.offset + (i * rowData.length / rawStr.length.coerceAtLeast(1))
                        }
                        
                        val isPatched = patches.containsKey(byteOffset)
                        val isSelected = selectedOffset == byteOffset
                        val isMatch = if (searchResults.isNotEmpty() && currentSearchIndex in searchResults.indices) {
                            val matchStart = searchResults[currentSearchIndex]
                            val matchEnd = matchStart + searchQueryLength
                            byteOffset in matchStart until matchEnd
                        } else false
                        
                        val isDifferent = if (otherTabIndex != null) {
                            val otherByte = viewModel.getByteAtTab(otherTabIndex, byteOffset)
                            otherByte == null || otherByte != (rowData.bytes.getOrNull((byteOffset - rowData.offset).toInt()) ?: 0)
                        } else false
                        
                        val isSpecial = isMatch || isPatched || isSelected || isDifferent
                        if (isSpecial) {
                            val textColor = if (isMatch) tertiaryColor else if (isPatched) errorColor else if (isDifferent) errorColor else onSurfaceColor
                            val bgColor = if (isSelected) primaryColor.copy(alpha=0.15f) else if (isMatch) tertiaryColor.copy(alpha=0.15f) else if (isPatched) errorColor.copy(alpha=0.15f) else if (isDifferent) errorContainerColor.copy(alpha = 0.25f) else Color.Transparent
                            val textWeight = if (isPatched || isMatch || isDifferent) FontWeight.Bold else FontWeight.Normal
                            
                            addStyle(
                                style = androidx.compose.ui.text.SpanStyle(
                                    color = textColor,
                                    background = bgColor,
                                    fontWeight = textWeight
                                ),
                                start = i,
                                end = i + 1
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = textStr,
            fontFamily = FontFamily.Monospace,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = rowFontSize,
            letterSpacing = rowLetterSpacing,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            modifier = Modifier
                .width(textDecWidth)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        decodedTextLayoutResult?.let { layout ->
                            val charIndex = layout.getOffsetForPosition(pos).coerceIn(0, currentRowData.length - 1)
                            if (charIndex < currentRowData.length && !currentRowData.isPlaceholder) {
                                currentOnSelectOffset(currentRowData.offset + charIndex)
                            }
                        }
                    }
                },
            onTextLayout = { decodedTextLayoutResult = it }
        )
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun OtherSettingsScreen(
    viewModel: com.bytewave.HexEditorViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    var languageMenuExpanded by remember { mutableStateOf(false) }
    
    // Permission launcher for POST_NOTIFICATIONS
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setEnableSmartNotifications(isGranted)
        if (isGranted) {
            com.bytewave.notification.NotificationReceiver.showNotification(context)
        }
    }

    // Storage permission launcher
    val storageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        viewModel.setAllowStorageAccess(granted)
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(com.bytewave.Translations.get("Other Settings", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padValues ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.bytewave.Translations.get("UTILITY SYSTEMS & PROTOCOLS", state.appLanguage),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            item {
                androidx.compose.material3.ElevatedCard(
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Language,
                                contentDescription = "Language",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.bytewave.Translations.get("App Language", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(com.bytewave.Translations.get("Select the primary language for the editor functionality.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            Box {
                                androidx.compose.material3.TextButton(onClick = { languageMenuExpanded = true }) {
                                    Text(state.appLanguage.displayName, fontSize = 13.sp)
                                    Icon(androidx.compose.material.icons.Icons.Default.ArrowDropDown, contentDescription = "Select")
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = languageMenuExpanded,
                                    onDismissRequest = { languageMenuExpanded = false }
                                ) {
                                    com.bytewave.AppLanguage.values().forEach { lang ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(lang.displayName) },
                                            onClick = {
                                                viewModel.setAppLanguage(lang)
                                                languageMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Notification system Card
            item {
                androidx.compose.material3.ElevatedCard(
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.bytewave.Translations.get("Smart Socket Notifications", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(com.bytewave.Translations.get("Receive randomized, motivational, and technical system alerts hourly.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = state.enableSmartNotifications,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            viewModel.setEnableSmartNotifications(true)
                                            com.bytewave.notification.NotificationReceiver.showNotification(context)
                                        }
                                    } else {
                                        viewModel.setEnableSmartNotifications(false)
                                    }
                                }
                            )
                        }
                        if (state.enableSmartNotifications) {
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    com.bytewave.notification.NotificationReceiver.showNotification(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("🔔 TRIGGER SMART ALERT DEMO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. Storage System Card
            item {
                androidx.compose.material3.ElevatedCard(
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.FolderOpen,
                                contentDescription = "Storage",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.bytewave.Translations.get("Storage & File Manager Access", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(com.bytewave.Translations.get("Silently mirror workspace patches inside root system repository Documents/XHexPro_Backups.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = state.allowStorageAccess,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (android.os.Build.VERSION.SDK_INT >= 29) {
                                            // Modern Android uses Scoped Storage. No broad storage permissions are needed
                                            // for app-specific folder backups or SAF access.
                                            viewModel.setAllowStorageAccess(true)
                                            android.widget.Toast.makeText(context, "Storage integration enabled!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Classic Android
                                            val reqList = mutableListOf<String>()
                                            reqList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                            reqList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                                            storageLauncher.launch(reqList.toTypedArray())
                                        }
                                    } else {
                                        viewModel.setAllowStorageAccess(false)
                                    }
                                }
                            )
                        }
                        if (state.allowStorageAccess) {
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    viewModel.backupAllActiveFiles()
                                    android.widget.Toast.makeText(context, "Workspace backed up to Documents/XHexPro_Backups/", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("💾 MANUALLY EXPORT WORKSPACE BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. AutoSave Card
            item {
                androidx.compose.material3.ElevatedCard(
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                contentDescription = "AutoSave",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.bytewave.Translations.get("Save Important Files Automatically", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(com.bytewave.Translations.get("Automatically saves history configurations and restores tab spaces instantly on restart without recovery popups.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = state.saveImportantFilesAutomatically,
                                onCheckedChange = { checked ->
                                    viewModel.setSaveImportantFilesAutomatically(checked)
                                }
                            )
                        }
                    }
                }
            }

            // 4. File History Button Card
            item {
                androidx.compose.material3.ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    onClick = { viewModel.toggleFileHistory(true) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.History,
                            contentDescription = "History",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(com.bytewave.Translations.get("File History Record Registry", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(com.bytewave.Translations.get("Review, filter, search, and manage recently modified files, sizes, and active recovery slots.", state.appLanguage), fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FileHistoryScreen(
    viewModel: com.bytewave.HexEditorViewModel,
    onBack: () -> Unit,
    onFileSelected: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("date") } // "date", "name", "size", "patches"
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredList = state.fileHistory.filter {
        it.fileName.contains(searchQuery, ignoreCase = true)
    }

    val sortedList = remember(filteredList, sortBy) {
        when (sortBy) {
            "name" -> filteredList.sortedBy { it.fileName }
            "size" -> filteredList.sortedByDescending { it.fileSize }
            "patches" -> filteredList.sortedByDescending { it.modificationCount }
            else -> filteredList.sortedByDescending { it.lastModified }
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(com.bytewave.Translations.get("File History Record Registry", state.appLanguage), fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.fileHistory.isNotEmpty()) {
                        androidx.compose.material3.TextButton(
                            onClick = { showClearConfirm = true }
                        ) {
                            Text(com.bytewave.Translations.get("Clear All", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(com.bytewave.Translations.get("Search", state.appLanguage) + "...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                leadingIcon = {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.Search, contentDescription = "Search")
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            // Sorting bar
            Text(
                text = com.bytewave.Translations.get("SORT REGISTRY BY", state.appLanguage),
                fontSize = 10.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "date" to "Date",
                    "name" to "Name",
                    "size" to "Size",
                    "patches" to "Edits"
                ).forEach { (key, label) ->
                    val isSelected = sortBy == key
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = { sortBy = key },
                        label = { Text(com.bytewave.Translations.get(label, state.appLanguage), fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            if (sortedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) com.bytewave.Translations.get("No matches found", state.appLanguage) else com.bytewave.Translations.get("History registry is empty", state.appLanguage),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedList) { record ->
                        androidx.compose.material3.OutlinedCard(
                            onClick = {
                                try {
                                    viewModel.openFile(context, Uri.parse(record.uriString))
                                    viewModel.toggleFileHistory(false)
                                    viewModel.toggleOtherSettings(false)
                                    onFileSelected()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open: file slot locked or unavailable.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Code,
                                        contentDescription = "File",
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = record.fileName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    
                                    // modifications count badge
                                    if (record.modificationCount > 0) {
                                        androidx.compose.material3.Surface(
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "🔧 ${record.modificationCount} patches",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Size: ${formatHistorySize(record.fileSize)}",
                                            fontSize = 11.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Last open: ${formatHistoryDate(record.lastModified)}",
                                            fontSize = 10.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f)
                                        )
                                    }
                                    
                                    // Recovery state indicator
                                    androidx.compose.material3.Surface(
                                        color = if (record.hasRecovery) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (record.hasRecovery) "Active" else "Stateless",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.hasRecovery) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(com.bytewave.Translations.get("Erase Registry?", state.appLanguage)) },
            text = { Text(com.bytewave.Translations.get("Are you absolutely sure you want to clear your entire file editing history? This cannot be undone.", state.appLanguage)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.clearFileHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text(com.bytewave.Translations.get("YES, CLEAR ALL", state.appLanguage), color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearConfirm = false }) {
                    Text(com.bytewave.Translations.get("Cancel", state.appLanguage))
                }
            }
        )
    }
}

fun formatHistorySize(size: Long): String {
    return if (size < 1024) "$size B"
    else if (size < 1024 * 1024) String.format("%.1f KB", size / 1024f)
    else String.format("%.1f MB", size / (1024f * 1024f))
}

fun formatHistoryDate(ms: Long): String {
    val date = java.util.Date(ms)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(date)
}



