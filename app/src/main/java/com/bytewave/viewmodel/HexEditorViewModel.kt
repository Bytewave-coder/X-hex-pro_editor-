package com.bytewave

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Environment
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class InspectorData(
    val int8: Byte,
    val uint8: UByte,
    val int16: Short?,
    val uint16: UShort?,
    val int32: Int?,
    val uint32: UInt?,
    val int64: Long?,
    val uint64: ULong?,
    val float32: Float?,
    val float64: Double?,
    val binary: String,
    val octal: String,
    // ADVANCED INTUITIVE DETAILS AS ASKED BY USER:
    val asciiStr: String,
    val utf8Str: String,
    val utf16LeStr: String,
    val utf16BeStr: String,
    val unixTime32Sec: String?,
    val unixTime64Ms: String?,
    val guidBe: String?,
    val guidLe: String?,
    val leb128U: String?,
    val leb128S: String?,
    val magicHeader: String?
)

data class HexRowData(
    val offset: Long,
    val bytes: ByteArray, // up to 16 bytes
    val length: Int, // number of valid bytes in the array
    val isPlaceholder: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HexRowData
        if (offset != other.offset) return false
        if (length != other.length) return false
        if (isPlaceholder != other.isPlaceholder) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + length
        result = 31 * result + isPlaceholder.hashCode()
        return result
    }
}

enum class SearchDataType(val displayName: String) {
    HEX_TEXT("Hex Text"),
    ASCII_STRING("ASCII String"),
    UNICODE_STRING("Unicode String"),
    UTF8_STRING("UTF-8 String"),
    GBK_STRING("GBK String"),
    SIGNED_BYTE("Signed Byte"),
    UNSIGNED_BYTE("Unsigned Byte"),
    SIGNED_SHORT("Signed Short"),
    UNSIGNED_SHORT("Unsigned Short"),
    SIGNED_INT("Signed Int"),
    UNSIGNED_INT("Unsigned Int"),
    SIGNED_LONG("Signed Long"),
    UNSIGNED_LONG("Unsigned Long"),
    FLOAT("Float"),
    DOUBLE("Double"),
    REGEX("Regex (ASCII)"),
    WILDCARD_HEX("Wildcard Hex (?/??)")
}

data class WorkspaceTab(
    val fileUri: Uri,
    val fileName: String,
    val fileSize: Long,
    val patches: Map<Long, Byte> = emptyMap(),
    val undoStack: List<Map<Long, Byte>> = emptyList(),
    val redoStack: List<Map<Long, Byte>> = emptyList(),
    val searchResults: List<Long> = emptyList(),
    val currentSearchIndex: Int = -1,
    val searchQueryLength: Int = 0,
    val selectedOffset: Long? = null,
    val showInspector: Boolean = false,
    val inspectorBigEndian: Boolean = false
)

enum class AppThemeColor { 
    CYBERPUNK, MONOKAI, TERMINAL, DRACULA, NORD, AMOLED;

    val displayName: String get() = when(this) {
        CYBERPUNK -> "Cyberpunk"
        MONOKAI -> "Monokai"
        TERMINAL -> "Terminal"
        DRACULA -> "Dracula"
        NORD -> "Nord"
        AMOLED -> "AMOLED"
    }
}

enum class LogLevel {
    INFO, WARNING, ERROR, CRITICAL
}

data class DiagnosticLog(
    val id: String,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val sourceFile: String?,
    val sourceFunction: String?,
    val lineNumber: Int?,
    val exceptionTrace: String? = null
)

data class FileHistoryRecord(
    val uriString: String,
    val fileName: String,
    val fileSize: Long,
    val lastModified: Long,
    val modificationCount: Int,
    val hasRecovery: Boolean
)

enum class AppLanguage(val displayName: String) {
    EN("English"),
    ZH_CN("Chinese (Simplified)"),
    ZH_TW("Chinese (Traditional)"),
    JA("Japanese"),
    HI("Hindi"),
    ES("Spanish"),
    FR("French"),
    PT("Portuguese"),
    PL("Polish")
}

data class HexEditorState(
    val tabs: List<WorkspaceTab> = emptyList(),
    val activeTabIndex: Int = -1,
    val compareTabIndex: Int? = null,
    val compareIsScanning: Boolean = false,
    val compareDifferencesCount: Int? = null,
    val compareDiffOffsets: List<Long> = emptyList(),
    val compareCurrentDiffIndex: Int = -1,
    val compareSplitVertical: Boolean = false,
    
    val isLoading: Boolean = false,
    val error: String? = null,
    val isExporting: Boolean = false,
    val isSearching: Boolean = false,
    
    val displayMode: DisplayMode = DisplayMode.HEX,
    val encodingMode: EncodingMode = EncodingMode.UTF_8,
    val desktopMode: Boolean = true,
    val showKeyboard: Boolean = false,
    
    val isDarkMode: Boolean = true,
    val themeColor: AppThemeColor = AppThemeColor.CYBERPUNK,
    
    val searchHistory: List<String> = emptyList(),
    
    val sidebarWeight: Float = 0.35f,
    val showCommandPalette: Boolean = false,
    val floatingTools: Set<String> = emptySet(),
    val showPatchVerification: Boolean = false,
    val hasAutosavedBackup: Boolean = false,
    
    val cacheVersion: Int = 0,
    val diagnosticLogs: List<DiagnosticLog> = emptyList(),
    val showLogsDialog: Boolean = false,
    val isRestoreCompleted: Boolean = false,

    val enableSmartNotifications: Boolean = false,
    val allowStorageAccess: Boolean = false,
    val saveImportantFilesAutomatically: Boolean = true,
    val showOtherSettings: Boolean = false,
    val showFileHistory: Boolean = false,
    val fileHistory: List<FileHistoryRecord> = emptyList(),
    val appLanguage: AppLanguage = AppLanguage.EN,
    val showEntropySpectrogram: Boolean = false,
    val showNativeDisassembler: Boolean = false
) {
    val activeTab: WorkspaceTab? get() = tabs.getOrNull(activeTabIndex)

    // Convenient accessors for active tab
    val fileUri: Uri? get() = activeTab?.fileUri
    val fileName: String get() = activeTab?.fileName ?: "No File"
    val fileSize: Long get() = activeTab?.fileSize ?: 0L
    val patches: Map<Long, Byte> get() = activeTab?.patches ?: emptyMap()
    val undoStack: List<Map<Long, Byte>> get() = activeTab?.undoStack ?: emptyList()
    val redoStack: List<Map<Long, Byte>> get() = activeTab?.redoStack ?: emptyList()
    val searchResults: List<Long> get() = activeTab?.searchResults ?: emptyList()
    val currentSearchIndex: Int get() = activeTab?.currentSearchIndex ?: -1
    val searchQueryLength: Int get() = activeTab?.searchQueryLength ?: 0
    val selectedOffset: Long? get() = activeTab?.selectedOffset
    val showInspector: Boolean get() = activeTab?.showInspector ?: false
    val inspectorBigEndian: Boolean get() = activeTab?.inspectorBigEndian ?: false
}

enum class DisplayMode { HEX, BINARY }
enum class EncodingMode { 
    ASCII, UTF_8, UTF_16LE, UTF_16BE, GBK, ISO_8859_1, WINDOWS_1252, SHIFT_JIS, EUC_KR, EUC_JP, UTF_32LE, UTF_32BE;

    val displayName: String get() = when(this) {
        ASCII -> "ASCII"
        UTF_8 -> "UTF-8"
        UTF_16LE -> "UTF-16LE"
        UTF_16BE -> "UTF-16BE"
        GBK -> "GBK"
        ISO_8859_1 -> "ISO-8859-1 (Latin-1)"
        WINDOWS_1252 -> "Windows-1252"
        SHIFT_JIS -> "Shift_JIS"
        EUC_KR -> "EUC-KR"
        EUC_JP -> "EUC-JP"
        UTF_32LE -> "UTF-32LE"
        UTF_32BE -> "UTF-32BE"
    }
}

class HexEditorViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val _state = MutableStateFlow(HexEditorState())
    val state: StateFlow<HexEditorState> = _state.asStateFlow()

    private val openFiles = java.util.concurrent.ConcurrentHashMap<Uri, Pair<ParcelFileDescriptor, FileChannel>>()

    private val fileChannel: FileChannel?
        get() = _state.value.activeTab?.fileUri?.let { uri -> openFiles[uri]?.second }

    private inline fun updateActiveTab(crossinline updater: (WorkspaceTab) -> WorkspaceTab) {
        _state.update {
            if (it.activeTabIndex in it.tabs.indices) {
                val newTabs = it.tabs.toMutableList()
                newTabs[it.activeTabIndex] = updater(newTabs[it.activeTabIndex])
                it.copy(tabs = newTabs)
            } else it
        }
    }

    private val ROW_SIZE
        get() = if (_state.value.desktopMode) {
            if (_state.value.displayMode == DisplayMode.BINARY) 8 else 16
        } else {
            if (_state.value.displayMode == DisplayMode.BINARY) 4 else 8
        }

    fun toggleDisplayMode(mode: DisplayMode) {
        _state.update { it.copy(displayMode = mode) }
    }

    fun toggleDesktopMode(desktop: Boolean) {
        _state.update { it.copy(desktopMode = desktop) }
    }

    fun toggleDesktopMode() {
        _state.update { it.copy(desktopMode = !it.desktopMode) }
    }

    fun toggleEncodingMode(mode: EncodingMode) {
        _state.update { it.copy(encodingMode = mode) }
    }

    fun toggleDarkMode() {
        _state.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun toggleLogsDialog() {
        _state.update { it.copy(showLogsDialog = !it.showLogsDialog) }
    }

    private fun savePersistentLogs(logs: List<DiagnosticLog>) {
        try {
            val sharedPrefs = getApplication<android.app.Application>().getSharedPreferences("hex_editor_logs_pref", Context.MODE_PRIVATE)
            val jsonArray = org.json.JSONArray()
            val logsToSave = if (logs.size > 200) logs.takeLast(200) else logs
            for (log in logsToSave) {
                val jsonObject = org.json.JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("level", log.level.name)
                    put("tag", log.tag)
                    put("message", log.message)
                    put("sourceFile", log.sourceFile ?: "")
                    put("sourceFunction", log.sourceFunction ?: "")
                    put("lineNumber", log.lineNumber ?: -1)
                    put("exceptionTrace", log.exceptionTrace ?: "")
                }
                jsonArray.put(jsonObject)
            }
            sharedPrefs.edit().putString("saved_logs_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPersistentLogs(): List<DiagnosticLog> {
        val list = mutableListOf<DiagnosticLog>()
        try {
            val sharedPrefs = getApplication<android.app.Application>().getSharedPreferences("hex_editor_logs_pref", Context.MODE_PRIVATE)
            val jsonStr = sharedPrefs.getString("saved_logs_json", null) ?: return emptyList()
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.getString("id")
                val timestamp = jsonObject.getString("timestamp")
                val level = try { LogLevel.valueOf(jsonObject.getString("level")) } catch(_: Exception) { LogLevel.INFO }
                val tag = jsonObject.getString("tag")
                val message = jsonObject.getString("message")
                val sourceFile = jsonObject.optString("sourceFile").takeIf { it.isNotEmpty() }
                val sourceFunction = jsonObject.optString("sourceFunction").takeIf { it.isNotEmpty() }
                val lineNumber = jsonObject.optInt("lineNumber", -1).takeIf { it != -1 }
                val exceptionTrace = jsonObject.optString("exceptionTrace").takeIf { it.isNotEmpty() }
                
                list.add(
                    DiagnosticLog(
                        id = id,
                        timestamp = timestamp,
                        level = level,
                        tag = tag,
                        message = message,
                        sourceFile = sourceFile,
                        sourceFunction = sourceFunction,
                        lineNumber = lineNumber,
                        exceptionTrace = exceptionTrace
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun addLog(level: LogLevel, tag: String, message: String, file: String?, method: String?, line: Int?, trace: String? = null) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        val log = DiagnosticLog(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            sourceFile = file,
            sourceFunction = method,
            lineNumber = line,
            exceptionTrace = trace
        )
        _state.update { 
            val updatedLogs = it.diagnosticLogs + log
            savePersistentLogs(updatedLogs)
            it.copy(diagnosticLogs = updatedLogs) 
        }
    }

    fun logInfo(tag: String, message: String) {
        val element = Exception().stackTrace.getOrNull(1)
        addLog(LogLevel.INFO, tag, message, element?.fileName, element?.methodName, element?.lineNumber)
    }

    fun logWarning(tag: String, message: String) {
        val element = Exception().stackTrace.getOrNull(1)
        addLog(LogLevel.WARNING, tag, message, element?.fileName, element?.methodName, element?.lineNumber)
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val element = Exception().stackTrace.getOrNull(1)
        val trace = throwable?.let { android.util.Log.getStackTraceString(it) }
        addLog(LogLevel.ERROR, tag, message, element?.fileName, element?.methodName, element?.lineNumber, trace)
    }

    fun clearLogs() {
        _state.update { 
            savePersistentLogs(emptyList())
            it.copy(diagnosticLogs = emptyList()) 
        }
    }

    init {
        // Load persistent logs upon initialization
        val restoredLogs = loadPersistentLogs()
        _state.update { it.copy(diagnosticLogs = restoredLogs) }

        // Core crash monitor setup
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sharedPrefs = getApplication<android.app.Application>().getSharedPreferences("hex_editor_logs_pref", Context.MODE_PRIVATE)
                val jsonStr = sharedPrefs.getString("saved_logs_json", "[]") ?: "[]"
                val jsonArray = org.json.JSONArray(jsonStr)
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
                val trace = android.util.Log.getStackTraceString(throwable)
                val crashObject = org.json.JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("timestamp", timestamp)
                    put("level", LogLevel.CRITICAL.name)
                    put("tag", "CRASH_RECOVERY")
                    put("message", "Fatal Exception: ${throwable.localizedMessage ?: throwable.message ?: "Unknown Exception"}")
                    put("sourceFile", throwable.stackTrace.firstOrNull()?.fileName ?: "")
                    put("sourceFunction", throwable.stackTrace.firstOrNull()?.methodName ?: "")
                    put("lineNumber", throwable.stackTrace.firstOrNull()?.lineNumber ?: -1)
                    put("exceptionTrace", trace)
                }
                jsonArray.put(crashObject)
                
                val finalArray = if (jsonArray.length() > 200) {
                    val temp = org.json.JSONArray()
                    for (i in (jsonArray.length() - 200) until jsonArray.length()) {
                        temp.put(jsonArray.get(i))
                    }
                    temp
                } else {
                    jsonArray
                }
                sharedPrefs.edit().putString("saved_logs_json", finalArray.toString()).commit()
            } catch (err: Exception) {
                err.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        logInfo("SYSTEM", "Hex Editor Engine Initialized.")
        logInfo("SYSTEM", "Ready to process large-scale byte arrays.")
    }
    
    fun setThemeColor(color: AppThemeColor) {
        _state.update { it.copy(themeColor = color) }
    }

    fun setEnableSmartNotifications(enabled: Boolean) {
        val context = getApplication<android.app.Application>()
        _state.update { it.copy(enableSmartNotifications = enabled) }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("enable_smart_notifications", enabled).apply()
        
        if (enabled) {
            com.bytewave.notification.NotificationReceiver.scheduleSmartNotifications(context)
        } else {
            com.bytewave.notification.NotificationReceiver.cancelSmartNotifications(context)
        }
    }

    fun setAllowStorageAccess(enabled: Boolean) {
        val context = getApplication<android.app.Application>()
        _state.update { it.copy(allowStorageAccess = enabled) }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("allow_storage_access", enabled).apply()
        if (enabled) {
            backupAllActiveFiles()
        }
    }

    fun setSaveImportantFilesAutomatically(enabled: Boolean) {
        val context = getApplication<android.app.Application>()
        _state.update { it.copy(saveImportantFilesAutomatically = enabled) }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("save_important_files_automatically", enabled).apply()
    }

    fun setAppLanguage(language: AppLanguage) {
        val context = getApplication<android.app.Application>()
        _state.update { it.copy(appLanguage = language) }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("app_language", language.name).apply()
    }

    fun toggleOtherSettings(show: Boolean) {
        _state.update { it.copy(showOtherSettings = show) }
    }

    fun toggleFileHistory(show: Boolean) {
        _state.update { it.copy(showFileHistory = show) }
    }

    fun toggleEntropySpectrogram(show: Boolean = !_state.value.showEntropySpectrogram) {
        _state.update { it.copy(showEntropySpectrogram = show) }
    }

    fun toggleNativeDisassembler(show: Boolean = !_state.value.showNativeDisassembler) {
        _state.update { it.copy(showNativeDisassembler = show) }
    }

    suspend fun readPatchedFileBytes(
        context: Context,
        uri: Uri,
        patches: Map<Long, Byte> = emptyMap(),
        maxBytes: Int = 1_048_576
    ): Pair<ByteArray, Long> = withContext(Dispatchers.IO) {
        try {
            // Find matching open channel by URI or URI string
            val openPair = openFiles[uri]
                ?: openFiles.entries.firstOrNull { it.key.toString() == uri.toString() }?.value

            fun readFromChannel(channel: FileChannel): Pair<ByteArray, Long> {
                val totalSize = channel.size()
                if (totalSize <= 0L) return Pair(ByteArray(0), 0L)

                if (totalSize <= maxBytes) {
                    val len = totalSize.toInt()
                    val bytes = ByteArray(len)
                    val buf = ByteBuffer.wrap(bytes)
                    var totalRead = 0
                    while (totalRead < len) {
                        val r = channel.read(buf, totalRead.toLong())
                        if (r <= 0) break
                        totalRead += r
                    }
                    if (patches.isNotEmpty()) {
                        for ((offset, b) in patches) {
                            if (offset in 0 until totalRead) {
                                bytes[offset.toInt()] = b
                            }
                        }
                    }
                    return Pair(if (totalRead == len) bytes else bytes.copyOf(totalRead), totalSize)
                } else {
                    // For large files (e.g. 8.60 MB classes.dex), read first 64KB intact for header/signature accuracy,
                    // and stride evenly across the rest of the entire file so 100% of the binary is represented!
                    val outBytes = ByteArray(maxBytes)
                    val headerSize = minOf(65536, maxBytes / 4)
                    val headerBuf = ByteBuffer.wrap(outBytes, 0, headerSize)
                    var hRead = 0
                    while (hRead < headerSize) {
                        val r = channel.read(headerBuf, hRead.toLong())
                        if (r <= 0) break
                        hRead += r
                    }
                    val numBlocks = 960
                    val remainingOut = maxBytes - headerSize
                    val blockBytes = remainingOut / numBlocks
                    val remainingFileSpan = totalSize - headerSize
                    val tempBuf = ByteBuffer.allocate(blockBytes)

                    var outPos = headerSize
                    for (bIdx in 0 until numBlocks) {
                        val fileOff = headerSize + (bIdx.toLong() * remainingFileSpan) / numBlocks
                        tempBuf.clear()
                        var bRead = 0
                        while (bRead < blockBytes && fileOff + bRead < totalSize) {
                            val r = channel.read(tempBuf, fileOff + bRead)
                            if (r <= 0) break
                            bRead += r
                        }
                        if (bRead > 0) {
                            System.arraycopy(tempBuf.array(), 0, outBytes, outPos, bRead)
                            if (patches.isNotEmpty()) {
                                for (k in 0 until bRead) {
                                    val patched = patches[fileOff + k]
                                    if (patched != null) {
                                        outBytes[outPos + k] = patched
                                    }
                                }
                            }
                            outPos += bRead
                        }
                    }
                    if (patches.isNotEmpty()) {
                        for ((offset, b) in patches) {
                            if (offset in 0 until headerSize) {
                                outBytes[offset.toInt()] = b
                            }
                        }
                    }
                    return Pair(if (outPos == maxBytes) outBytes else outBytes.copyOf(outPos), totalSize)
                }
            }

            if (openPair != null && openPair.second.isOpen) {
                val res = readFromChannel(openPair.second)
                if (res.first.isNotEmpty()) return@withContext res
            }

            // Try internal cache fallback if available
            val cacheFile = java.io.File(context.cacheDir, "file_cache_${uri.hashCode()}")
            if (cacheFile.exists() && cacheFile.length() > 0L) {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                        val res = readFromChannel(ch)
                        if (res.first.isNotEmpty()) return@withContext res
                    }
                }
            }

            // Read directly via ContentResolver FileDescriptor
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                        val res = readFromChannel(ch)
                        if (res.first.isNotEmpty()) return@withContext res
                    }
                }
            } catch (_: Exception) {}

            // Fallback to InputStream
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(32768)
                    var totalRead = 0
                    while (totalRead < maxBytes) {
                        val toRead = minOf(buf.size, maxBytes - totalRead)
                        val r = input.read(buf, 0, toRead)
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        totalRead += r
                    }
                    val bytes = out.toByteArray()
                    if (bytes.isNotEmpty()) {
                        if (patches.isNotEmpty()) {
                            for ((offset, b) in patches) {
                                if (offset in bytes.indices) {
                                    bytes[offset.toInt()] = b
                                }
                            }
                        }
                        val tabSize = _state.value.tabs.find { it.fileUri.toString() == uri.toString() }?.fileSize
                            ?: bytes.size.toLong()
                        return@withContext Pair(bytes, maxOf(tabSize, bytes.size.toLong()))
                    }
                }
            } catch (_: Exception) {}

            // Fallback to already-loaded chunkCache in memory
            val fallbackChunk = cacheGet(Pair(uri, 0L))
            if (fallbackChunk != null && fallbackChunk.isNotEmpty()) {
                val copy = fallbackChunk.copyOf()
                for ((offset, b) in patches) {
                    if (offset in copy.indices) copy[offset.toInt()] = b
                }
                val tabSize = _state.value.tabs.find { it.fileUri.toString() == uri.toString() }?.fileSize
                    ?: copy.size.toLong()
                return@withContext Pair(copy, tabSize)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(ByteArray(0), 0L)
    }

    // HISTORY MANAGER
    fun addOrUpdateFileHistory(uri: String, name: String, size: Long, extraPatches: Int = 0, hasRecovery: Boolean = true) {
        val context = getApplication<android.app.Application>()
        val current = _state.value.fileHistory.toMutableList()
        val index = current.indexOfFirst { it.uriString == uri }
        val lastModified = System.currentTimeMillis()
        
        val record = if (index != -1) {
            val existing = current[index]
            val newModCount = existing.modificationCount + extraPatches
            existing.copy(
                lastModified = lastModified,
                modificationCount = if (extraPatches == 0) existing.modificationCount else newModCount,
                hasRecovery = hasRecovery,
                fileSize = if (size > 0) size else existing.fileSize
            )
        } else {
            FileHistoryRecord(
                uriString = uri,
                fileName = name,
                fileSize = size,
                lastModified = lastModified,
                modificationCount = extraPatches,
                hasRecovery = hasRecovery
            )
        }
        if (index != -1) {
            current.removeAt(index)
        }
        current.add(0, record)
        
        val trimmed = if (current.size > 100) current.take(100) else current
        _state.update { it.copy(fileHistory = trimmed) }
        saveFileHistoryPersistent(context, trimmed)
    }

    private fun saveFileHistoryPersistent(context: Context, list: List<FileHistoryRecord>) {
        val builder = StringBuilder()
        list.forEachIndexed { i, item ->
            if (i > 0) builder.append("|||")
            builder.append(item.uriString).append("##")
            builder.append(item.fileName).append("##")
            builder.append(item.fileSize).append("##")
            builder.append(item.lastModified).append("##")
            builder.append(item.modificationCount).append("##")
            builder.append(item.hasRecovery)
        }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("file_history_records", builder.toString()).apply()
    }

    fun clearFileHistory() {
        val context = getApplication<android.app.Application>()
        _state.update { it.copy(fileHistory = emptyList()) }
        val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
        sharedPrefs.edit().remove("file_history_records").apply()
    }

    fun backupAllActiveFiles() {
        if (!_state.value.allowStorageAccess) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                val appDir = java.io.File(documentsDir, "XHexPro_Backups")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                
                val fallbackDir = getApplication<android.app.Application>().getExternalFilesDir("XHexPro_Backups")
                
                val sourceTabs = _state.value.tabs
                sourceTabs.forEach { tab ->
                    val backupFileName = "backup_${tab.fileName}_${System.currentTimeMillis()}.hex"
                    var targetFile = java.io.File(appDir, backupFileName)
                    
                    try {
                        if (!appDir.canWrite()) {
                            if (fallbackDir != null) {
                                targetFile = java.io.File(fallbackDir, backupFileName)
                            }
                        }
                        
                        writeMergedFile(tab, targetFile)
                        
                        val infoFile = java.io.File(targetFile.parentFile, "backup_info_${tab.fileName}.txt")
                        infoFile.writeText(
                            "File Name: ${tab.fileName}\n" +
                            "File Size: ${tab.fileSize}\n" +
                            "Backup Time: ${System.currentTimeMillis()}\n" +
                            "Patches count: ${tab.patches.size}\n" +
                            "Patches mapping: ${tab.patches}\n"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        try {
                            val internalDir = java.io.File(getApplication<android.app.Application>().filesDir, "XHexPro_Backups")
                            if (!internalDir.exists()) internalDir.mkdirs()
                            val internalTarget = java.io.File(internalDir, backupFileName)
                            writeMergedFile(tab, internalTarget)
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun writeMergedFile(tab: WorkspaceTab, destFile: java.io.File) {
        val uri = tab.fileUri ?: return
        val pfdAndChannel = openFiles[uri] ?: return
        val channel = pfdAndChannel.second
        
        destFile.outputStream().use { outStream ->
            val originalPos = channel.position()
            channel.position(0)
            val buffer = ByteBuffer.allocate(4096)
            var currentOffset = 0L
            while (true) {
                buffer.clear()
                val read = channel.read(buffer)
                if (read <= 0) break
                buffer.flip()
                val bytes = buffer.array()
                for (i in 0 until read) {
                    val off = currentOffset + i
                    val patchedByte = tab.patches[off]
                    if (patchedByte != null) {
                        bytes[i] = patchedByte
                    }
                }
                outStream.write(bytes, 0, read)
                currentOffset += read
            }
            channel.position(originalPos)
        }
    }

    fun switchTab(index: Int) {
        _state.update {
            if (index in it.tabs.indices) {
                it.copy(activeTabIndex = index)
            } else it
        }
    }

    fun selectOffset(offset: Long?) {
        updateActiveTab { it.copy(selectedOffset = offset) }
        if (offset != null) {
            _state.update { it.copy(showKeyboard = true) }
        }
    }

    fun moveCursor(delta: Long) {
        val current = _state.value.activeTab?.selectedOffset ?: return
        val size = _state.value.activeTab?.fileSize ?: return
        val newOffset = (current + delta).coerceIn(0L, maxOf(0L, size - 1))
        updateActiveTab { it.copy(selectedOffset = newOffset) }
    }
    
    fun toggleKeyboard() {
        _state.update { it.copy(showKeyboard = !it.showKeyboard) }
    }

    fun toggleInspector() {
        updateActiveTab { it.copy(showInspector = !it.showInspector) }
    }

    private var compareJob: kotlinx.coroutines.Job? = null

    fun getByteAtTab(tabIndex: Int, offset: Long): Byte? {
        val tab = _state.value.tabs.getOrNull(tabIndex) ?: return null
        return getByteAt(offset, tab)
    }

    fun startComparingTabs() {
        compareJob?.cancel()
        val activeIdx = _state.value.activeTabIndex
        val compareIdx = _state.value.compareTabIndex ?: return
        val tab1 = _state.value.tabs.getOrNull(activeIdx) ?: return
        val tab2 = _state.value.tabs.getOrNull(compareIdx) ?: return

        _state.update { 
            it.copy(
                compareIsScanning = true, 
                compareDifferencesCount = null,
                compareDiffOffsets = emptyList(),
                compareCurrentDiffIndex = -1
            ) 
        }

        compareJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ch1 = openFiles[tab1.fileUri]?.second
                val ch2 = openFiles[tab2.fileUri]?.second
                val patches1 = tab1.patches
                val patches2 = tab2.patches
                
                var diffCount = 0
                val size1 = tab1.fileSize
                val size2 = tab2.fileSize
                val maxSize = maxOf(size1, size2)
                val diffOffsets = mutableListOf<Long>()

                if (ch1 != null && ch2 != null) {
                    val buf1 = java.nio.ByteBuffer.allocate(131072) // 128KB
                    val buf2 = java.nio.ByteBuffer.allocate(131072) // 128KB
                    var offset = 0L

                    while (offset < maxSize) {
                        if (!isActive) break
                        val chunkLen = minOf(131072L, maxSize - offset).toInt()
                        
                        val array1 = ByteArray(chunkLen)
                        val array2 = ByteArray(chunkLen)

                        // Read from Tab 1
                        if (offset < size1) {
                            val len1 = minOf(chunkLen.toLong(), size1 - offset).toInt()
                            buf1.clear()
                            buf1.limit(len1)
                            var read = 0
                            while (read < len1) {
                                val r = ch1.read(buf1, offset + read)
                                if (r <= 0) break
                                read += r
                            }
                            System.arraycopy(buf1.array(), 0, array1, 0, len1)
                        }

                        // Read from Tab 2
                        if (offset < size2) {
                            val len2 = minOf(chunkLen.toLong(), size2 - offset).toInt()
                            buf2.clear()
                            buf2.limit(len2)
                            var read = 0
                            while (read < len2) {
                                val r = ch2.read(buf2, offset + read)
                                if (r <= 0) break
                                read += r
                            }
                            System.arraycopy(buf2.array(), 0, array2, 0, len2)
                        }

                        // Apply patches for this 128KB block
                        for (i in 0 until chunkLen) {
                            val byteOffset = offset + i
                            if (byteOffset < size1 && patches1.containsKey(byteOffset)) {
                                array1[i] = patches1[byteOffset]!!
                            }
                            if (byteOffset < size2 && patches2.containsKey(byteOffset)) {
                                array2[i] = patches2[byteOffset]!!
                            }
                        }

                        // Compare bytes
                        for (i in 0 until chunkLen) {
                            val byteOffset = offset + i
                            val isDiff = if (byteOffset >= size1 || byteOffset >= size2) {
                                true
                            } else {
                                array1[i] != array2[i]
                            }
                            if (isDiff) {
                                diffCount++
                                if (diffOffsets.size < 1000) {
                                    diffOffsets.add(byteOffset)
                                }
                            }
                        }

                        offset += chunkLen
                    }
                }

                _state.update {
                    it.copy(
                        compareIsScanning = false,
                        compareDifferencesCount = diffCount,
                        compareDiffOffsets = diffOffsets,
                        compareCurrentDiffIndex = if (diffOffsets.isNotEmpty()) 0 else -1
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(compareIsScanning = false, compareDifferencesCount = -1)
                }
            }
        }
    }

    fun jumpToNextDiff() {
        val offsets = _state.value.compareDiffOffsets
        if (offsets.isEmpty()) return
        val currentIdx = _state.value.compareCurrentDiffIndex
        val nextIdx = (currentIdx + 1) % offsets.size
        val targetOffset = offsets[nextIdx]
        _state.update { it.copy(compareCurrentDiffIndex = nextIdx) }
        selectOffset(targetOffset)
    }

    fun jumpToPrevDiff() {
        val offsets = _state.value.compareDiffOffsets
        if (offsets.isEmpty()) return
        val currentIdx = _state.value.compareCurrentDiffIndex
        val prevIdx = if (currentIdx <= 0) offsets.size - 1 else currentIdx - 1
        val targetOffset = offsets[prevIdx]
        _state.update { it.copy(compareCurrentDiffIndex = prevIdx) }
        selectOffset(targetOffset)
    }

    fun toggleCompareSplitOrientation() {
        _state.update { it.copy(compareSplitVertical = !it.compareSplitVertical) }
    }

    fun duplicateCurrentTabForCompare() {
        val activeTab = _state.value.activeTab ?: return
        val currentTabs = _state.value.tabs
        val duplicateTab = activeTab.copy(
            fileName = "${activeTab.fileName} (Copy)",
            patches = activeTab.patches.toMap(),
            undoStack = emptyList(),
            redoStack = emptyList(),
            selectedOffset = activeTab.selectedOffset
        )
        val newTabs = currentTabs + duplicateTab
        val newCompareIndex = newTabs.size - 1
        _state.update {
            it.copy(
                tabs = newTabs,
                compareTabIndex = newCompareIndex
            )
        }
        startComparingTabs()
    }

    fun openAndCompareFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            openFiles(context, listOf(uri))
            val newIndex = _state.value.tabs.size - 1
            if (newIndex >= 0 && newIndex != _state.value.activeTabIndex) {
                toggleCompareMode(newIndex)
            }
        }
    }

    fun toggleCompareMode(targetTabIndex: Int?) {
        _state.update { 
            val newCompare = if (it.compareTabIndex == targetTabIndex) null else targetTabIndex
            it.copy(
                compareTabIndex = newCompare,
                compareDiffOffsets = if (newCompare == null) emptyList() else it.compareDiffOffsets,
                compareCurrentDiffIndex = if (newCompare == null) -1 else it.compareCurrentDiffIndex
            )
        }
        if (_state.value.compareTabIndex != null) {
            startComparingTabs()
        } else {
            compareJob?.cancel()
            _state.update { it.copy(compareIsScanning = false, compareDifferencesCount = null) }
        }
    }

    fun cancelCompareMode() {
        toggleCompareMode(null)
    }

    fun searchNext() {
        updateActiveTab { tab ->
            if (tab.searchResults.isNotEmpty()) {
                val nextIdx = (tab.currentSearchIndex + 1) % tab.searchResults.size
                tab.copy(currentSearchIndex = nextIdx, selectedOffset = tab.searchResults[nextIdx])
            } else tab
        }
    }

    fun searchPrev() {
        updateActiveTab { tab ->
            if (tab.searchResults.isNotEmpty()) {
                val nextIdx = if (tab.currentSearchIndex <= 0) tab.searchResults.size - 1 else tab.currentSearchIndex - 1
                tab.copy(currentSearchIndex = nextIdx, selectedOffset = tab.searchResults[nextIdx])
            } else tab
        }
    }

sealed class SearchPattern {
    data class ExactMatch(val bytes: ByteArray) : SearchPattern()
    data class ByteMaskMatch(val mask: Array<Int?>) : SearchPattern() // null means wildcard byte
    data class RegexMatch(val pattern: Regex) : SearchPattern()
}

    fun search(pattern: SearchPattern) {
        val qLen = when(pattern) {
            is SearchPattern.ExactMatch -> pattern.bytes.size
            is SearchPattern.ByteMaskMatch -> pattern.mask.size
            is SearchPattern.RegexMatch -> pattern.pattern.pattern.length // Approximation
        }
        if (qLen == 0) {
            updateActiveTab { it.copy(searchResults = emptyList(), currentSearchIndex = -1, searchQueryLength = 0) }
            return
        }
        val channel = fileChannel ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null) }
            updateActiveTab { it.copy(searchQueryLength = qLen) }
            try {
                val results = withContext(Dispatchers.IO) {
                    val found = mutableListOf<Long>()
                    val bufferSize = 1024 * 1024
                    val byteBuffer = ByteBuffer.allocate(bufferSize)
                    val fileSize = _state.value.fileSize
                    var currentOffset = 0L
                    
                    val maxCarry = when(pattern) {
                        is SearchPattern.ExactMatch -> Math.max(0, pattern.bytes.size - 1)
                        is SearchPattern.ByteMaskMatch -> Math.max(0, pattern.mask.size - 1)
                        is SearchPattern.RegexMatch -> 1024 // max lookbehind for regex
                    }
                    var carryOver = ByteArray(0)
                    
                    while (currentOffset < fileSize) {
                        val readLen = minOf(bufferSize.toLong(), fileSize - currentOffset).toInt()
                        byteBuffer.clear()
                        byteBuffer.limit(readLen)
                        
                        var bytesRead = 0
                        while (bytesRead < readLen) {
                            kotlinx.coroutines.yield()
                            val read = channel.read(byteBuffer, currentOffset + bytesRead.toLong())
                            if (read <= 0) break
                            bytesRead += read
                        }
                        
                        if (bytesRead == 0) break
                        
                        val array = byteBuffer.array()
                        
                        // Apply patches to the chunk (read-only for search)
                        val patches = _state.value.patches
                        if (patches.isNotEmpty()) {
                            for ((patchOffset, byteVal) in patches) {
                                if (patchOffset in currentOffset until (currentOffset + bytesRead)) {
                                    array[(patchOffset - currentOffset).toInt()] = byteVal
                                }
                            }
                        }
                        
                        val chunkToSearch = carryOver + array.copyOfRange(0, bytesRead)
                        
                        when (pattern) {
                            is SearchPattern.ExactMatch -> {
                                val queryBytes = pattern.bytes
                                for (i in 0..chunkToSearch.size - queryBytes.size) {
                                    var match = true
                                    for (j in queryBytes.indices) {
                                        if (chunkToSearch[i + j] != queryBytes[j]) {
                                            match = false
                                            break
                                        }
                                    }
                                    if (match) {
                                        found.add(currentOffset - carryOver.size + i)
                                        if (found.size >= 10000) break
                                    }
                                }
                            }
                            is SearchPattern.ByteMaskMatch -> {
                                val mask = pattern.mask
                                for (i in 0..chunkToSearch.size - mask.size) {
                                    var match = true
                                    for (j in mask.indices) {
                                        val m = mask[j]
                                        if (m != null && (chunkToSearch[i + j].toInt() and 0xFF) != m) {
                                            match = false
                                            break
                                        }
                                    }
                                    if (match) {
                                        found.add(currentOffset - carryOver.size + i)
                                        if (found.size >= 10000) break
                                    }
                                }
                            }
                            is SearchPattern.RegexMatch -> {
                                val str = String(chunkToSearch, Charsets.ISO_8859_1)
                                val matches = pattern.pattern.findAll(str)
                                for (Math in matches) {
                                    found.add(currentOffset - carryOver.size + Math.range.first)
                                    if (found.size >= 10000) break
                                }
                            }
                        }
                        
                        if (found.size >= 10000) {
                            break
                        }
                        
                        val carryLen = minOf(maxCarry, bytesRead)
                        if (carryLen > 0) {
                            carryOver = array.copyOfRange(bytesRead - carryLen, bytesRead)
                        }
                        currentOffset += bytesRead
                    }
                    found
                }
                _state.update { it.copy(isSearching = false) }
                updateActiveTab { 
                    it.copy(
                        searchResults = results, 
                        currentSearchIndex = if(results.isNotEmpty()) 0 else -1,
                        selectedOffset = if(results.isNotEmpty()) results[0] else it.selectedOffset
                    ) 
                }
                logInfo("SEARCH", "Search pattern query (length: $qLen) completed. Found ${results.size} matches.")
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false, error = "Search failed: ${e.message}") }
                logError("SEARCH", "Search routine failed: ${e.message}", e)
            }
        }
    }

    fun openFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val newTabs = mutableListOf<WorkspaceTab>()
                    for (uri in uris) {
                        try {
                            // Request persistent permission from content resolver
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {}

                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                            var name = "unknown"
                            cursor?.use {
                                if (it.moveToFirst()) {
                                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex != -1) {
                                        name = it.getString(nameIndex)
                                    }
                                }
                            }

                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                                ?: throw Exception("Could not open file descriptor")
                            
                            val channel = FileInputStream(pfd.fileDescriptor).channel
                            val size = channel.size()

                            // Store a backup copy in app internal storage so we can always restore this workspace if permissions are lost
                            if (size <= 10_000_000L) { // Limit cache backup to files <= 10MB to prevent OOM/Disk Full crashes on large binaries
                                try {
                                    val cacheFile = java.io.File(context.cacheDir, "file_cache_${uri.hashCode()}")
                                    cacheFile.outputStream().use { outStream ->
                                        val originalPos = channel.position()
                                        channel.position(0)
                                        val buffer = ByteBuffer.allocate(65536)
                                        var read: Int
                                        while (channel.read(buffer).also { read = it } > 0) {
                                            buffer.flip()
                                            outStream.write(buffer.array(), 0, buffer.limit())
                                            buffer.clear()
                                        }
                                        channel.position(originalPos)
                                    }
                                } catch (cacheErr: Exception) {
                                    cacheErr.printStackTrace()
                                }
                            }

                            val old = openFiles.put(uri, Pair(pfd, channel))
                            try { old?.first?.close() } catch(_: Exception) {}
                            try { old?.second?.close() } catch(_: Exception) {}
                            
                            newTabs.add(WorkspaceTab(fileUri = uri, fileName = name, fileSize = size))
                            addOrUpdateFileHistory(uri.toString(), name, size, 0, true)
                            logInfo("IO", "Successfully opened file: \"$name\" ($size bytes) | URI: $uri")
                        } catch (e: Exception) {
                            // skip failed files but continue
                            logError("IO", "Failed to open individual file: ${e.message}", e)
                        }
                    }

                    _state.update {
                        val combinedTabs = it.tabs + newTabs
                        it.copy(
                            tabs = combinedTabs,
                            activeTabIndex = if (combinedTabs.isNotEmpty()) combinedTabs.size - 1 else -1,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
                logError("IO", "Outer file open routine failure: ${e.message}", e)
            }
        }
    }

    fun openFile(context: Context, uri: Uri?) {
        if (uri != null) {
            openFiles(context, listOf(uri))
        }
    }

    fun closeActiveTab() {
        val idx = _state.value.activeTabIndex
        if (idx != -1) {
            closeTab(idx)
        }
    }
    
    fun closeTab(index: Int) {
        val targetUri = _state.value.tabs.getOrNull(index)?.fileUri ?: return

        _state.update { state ->
            val newTabs = state.tabs.toMutableList()
            if (index in newTabs.indices) {
                newTabs.removeAt(index)
            }
            
            val stillInUse = newTabs.any { it.fileUri == targetUri }
            if (!stillInUse) {
                try { openFiles[targetUri]?.second?.close() } catch (_: Exception) {}
                try { openFiles[targetUri]?.first?.close() } catch (_: Exception) {}
                openFiles.remove(targetUri)
            }

            val newActiveIndex = if (state.activeTabIndex == index) {
                if (newTabs.isEmpty()) -1 else minOf(index, newTabs.size - 1)
            } else if (state.activeTabIndex > index) {
                state.activeTabIndex - 1
            } else {
                state.activeTabIndex
            }

            state.copy(
                tabs = newTabs,
                activeTabIndex = newActiveIndex,
                compareTabIndex = if (state.compareTabIndex == index) null else state.compareTabIndex,
                compareIsScanning = if (state.compareTabIndex == index) false else state.compareIsScanning,
                compareDifferencesCount = if (state.compareTabIndex == index) null else state.compareDifferencesCount
            )
        }
    }

    private val CHUNK_SIZE = 1024 * 64L // 64KB chunks
    private val chunkCache = android.util.LruCache<Pair<Uri, Long>, ByteArray>(128) // 128 chunks * 64KB = 8MB cache max
    
    private fun cachePut(key: Pair<Uri, Long>, value: ByteArray) {
        synchronized(chunkCache) {
            chunkCache.put(key, value)
        }
    }

    private fun cacheGet(key: Pair<Uri, Long>): ByteArray? {
        synchronized(chunkCache) {
            return chunkCache.get(key)
        }
    }
    
    private val loadingJobs = java.util.LinkedHashMap<Pair<Uri, Long>, kotlinx.coroutines.Job>()
    private val jobLock = Any()

    private fun requestChunkLoad(uri: Uri, chunkIndex: Long) {
        val key = Pair(uri, chunkIndex)
        synchronized(jobLock) {
            if (loadingJobs.containsKey(key)) return
            
            if (loadingJobs.size >= 16) {
                val iterator = loadingJobs.entries.iterator()
                val eldest = iterator.next()
                eldest.value.cancel()
                iterator.remove()
            }
            
            val job = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                try {
                    // Debounce rapid scrolling to filter out chunks passed by too quickly
                    kotlinx.coroutines.delay(45)
                    
                    val channel = openFiles[uri]?.second
                    if (channel != null) {
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val offset = chunkIndex * CHUNK_SIZE
                                val fileSize = _state.value.tabs.find { it.fileUri == uri }?.fileSize ?: 0L
                                val readLen = minOf(CHUNK_SIZE, fileSize - offset).toInt()
                                if (readLen > 0) {
                                    val bytes = ByteArray(readLen)
                                    val buffer = ByteBuffer.wrap(bytes)
                                    var bytesRead = 0
                                    while (bytesRead < readLen) {
                                        kotlinx.coroutines.yield()
                                        if (!isActive) break
                                        val read = channel.read(buffer, offset + bytesRead.toLong())
                                        if (read <= 0) break
                                        bytesRead += read
                                    }
                                    
                                    if (isActive) {
                                        cachePut(key, bytes)
                                    }
                                } else {
                                    cachePut(key, ByteArray(0))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace() 
                                if (isActive) {
                                    cachePut(key, ByteArray(minOf(CHUNK_SIZE, 1L).toInt()))
                                }
                            }
                        }
                        if (isActive) {
                            _state.update { it.copy(cacheVersion = it.cacheVersion + 1) }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation
                } finally {
                    synchronized(jobLock) {
                        loadingJobs.remove(key)
                    }
                }
            }
            loadingJobs[key] = job
        }
    }

    suspend fun getBytes(offset: Long, length: Int): ByteArray = withContext(Dispatchers.IO) {
        val uri = _state.value.activeTab?.fileUri ?: return@withContext ByteArray(0)
        val fileSize = _state.value.fileSize
        if (offset >= fileSize) return@withContext ByteArray(0)
        
        val actualLength = minOf(length.toLong(), fileSize - offset).toInt()
        val result = ByteArray(actualLength)
        
        var bytesRead = 0
        while (bytesRead < actualLength) {
            kotlinx.coroutines.yield()
            val currentOffset = offset + bytesRead
            val chunkIndex = currentOffset / CHUNK_SIZE
            val chunkOffset = (currentOffset % CHUNK_SIZE).toInt()
            val availableInChunk = minOf(CHUNK_SIZE - chunkOffset, (actualLength - bytesRead).toLong()).toInt()
            
            val key = Pair(uri, chunkIndex)
            val chunk = cacheGet(key)
            if (chunk != null) {
                val copyLen = minOf(availableInChunk, chunk.size - chunkOffset)
                if (copyLen > 0) {
                    System.arraycopy(chunk, chunkOffset, result, bytesRead, copyLen)
                }
            } else {
                requestChunkLoad(uri, chunkIndex)
            }
            bytesRead += availableInChunk
        }
        return@withContext result
    }

    fun getRow(rowIndex: Int): HexRowData? {
        try {
            val currentRowSize = ROW_SIZE
            val uri = _state.value.activeTab?.fileUri ?: return null
            val offset = rowIndex.toLong() * currentRowSize
            val fileSize = _state.value.fileSize
            if (offset >= fileSize) return null

            val bufferLength = minOf(currentRowSize.toLong(), fileSize - offset).toInt()
            val chunkIndex = offset / CHUNK_SIZE
            val chunkOffset = (offset % CHUNK_SIZE).toInt()
            
            val chunk = cacheGet(Pair(uri, chunkIndex))

            if (chunk == null) {
                requestChunkLoad(uri, chunkIndex)
                return HexRowData(offset, ByteArray(bufferLength), bufferLength, true)
            }

            val bytes = ByteArray(bufferLength)
            val availableInChunk = maxOf(0, chunk.size - chunkOffset)
            if (availableInChunk >= bufferLength) {
                System.arraycopy(chunk, chunkOffset, bytes, 0, bufferLength)
            } else {
                if (availableInChunk > 0 && chunkOffset < chunk.size) {
                    System.arraycopy(chunk, chunkOffset, bytes, 0, availableInChunk)
                }
                val nextChunk = cacheGet(Pair(uri, chunkIndex + 1))
                if (nextChunk != null) {
                    val remaining = bufferLength - availableInChunk
                    val nextAvailable = maxOf(0, minOf(nextChunk.size, remaining))
                    if (nextAvailable > 0) {
                        System.arraycopy(nextChunk, 0, bytes, availableInChunk, nextAvailable)
                    }
                } else {
                    requestChunkLoad(uri, chunkIndex + 1)
                    // return what we have, it's partially placeholder
                    return HexRowData(offset, bytes, bufferLength, true)
                }
            }
            
            // Apply patches
            val currentPatches = _state.value.patches
            for (i in bytes.indices) {
                val byteOffset = offset + i
                if (currentPatches.containsKey(byteOffset)) {
                    bytes[i] = currentPatches[byteOffset]!!
                }
            }
                
            return HexRowData(offset, bytes, bufferLength)
        } catch (e: Exception) {
            e.printStackTrace()
            // To prevent crashes, return a row full of zeros on error
            val fallbackBufferLength = minOf(ROW_SIZE, 8)
            return HexRowData(rowIndex.toLong() * ROW_SIZE, ByteArray(fallbackBufferLength), fallbackBufferLength, true)
        }
    }

    fun getRowCount(): Int {
        val size = _state.value.fileSize
        val currentRowSize = ROW_SIZE
        if (currentRowSize == 0) return 0
        val count = (size + currentRowSize - 1) / currentRowSize
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun getRowForTab(tabIndex: Int, rowIndex: Int): HexRowData? {
        try {
            val tab = _state.value.tabs.getOrNull(tabIndex) ?: return null
            val currentRowSize = ROW_SIZE
            val uri = tab.fileUri ?: return null
            val offset = rowIndex.toLong() * currentRowSize
            val fileSize = tab.fileSize
            if (offset >= fileSize) return null

            val bufferLength = minOf(currentRowSize.toLong(), fileSize - offset).toInt()
            val chunkIndex = offset / CHUNK_SIZE
            val chunkOffset = (offset % CHUNK_SIZE).toInt()
            
            val chunk = cacheGet(Pair(uri, chunkIndex))

            if (chunk == null) {
                requestChunkLoad(uri, chunkIndex)
                return HexRowData(offset, ByteArray(bufferLength), bufferLength, true)
            }

            val bytes = ByteArray(bufferLength)
            val availableInChunk = maxOf(0, chunk.size - chunkOffset)
            if (availableInChunk >= bufferLength) {
                System.arraycopy(chunk, chunkOffset, bytes, 0, bufferLength)
            } else {
                if (availableInChunk > 0 && chunkOffset < chunk.size) {
                    System.arraycopy(chunk, chunkOffset, bytes, 0, availableInChunk)
                }
                val nextChunk = cacheGet(Pair(uri, chunkIndex + 1))
                if (nextChunk != null) {
                    val remaining = bufferLength - availableInChunk
                    val nextAvailable = maxOf(0, minOf(nextChunk.size, remaining))
                    if (nextAvailable > 0) {
                        System.arraycopy(nextChunk, 0, bytes, availableInChunk, nextAvailable)
                    }
                } else {
                    requestChunkLoad(uri, chunkIndex + 1)
                    // return what we have, it's partially placeholder
                    return HexRowData(offset, bytes, bufferLength, true)
                }
            }
            
            // Apply patches
            val currentPatches = tab.patches
            for (i in bytes.indices) {
                val byteOffset = offset + i
                if (currentPatches.containsKey(byteOffset)) {
                    bytes[i] = currentPatches[byteOffset]!!
                }
            }
                
            return HexRowData(offset, bytes, bufferLength)
        } catch (e: Exception) {
            e.printStackTrace()
            // To prevent crashes, return a row full of zeros on error
            val fallbackBufferLength = minOf(ROW_SIZE, 8)
            return HexRowData(rowIndex.toLong() * ROW_SIZE, ByteArray(fallbackBufferLength), fallbackBufferLength, true)
        }
    }

    fun getRowCountForTab(tabIndex: Int): Int {
        val tab = _state.value.tabs.getOrNull(tabIndex) ?: return 0
        val size = tab.fileSize
        val currentRowSize = ROW_SIZE
        if (currentRowSize == 0) return 0
        val count = (size + currentRowSize - 1) / currentRowSize
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun patchByte(offset: Long, newByte: Byte) {
        updateActiveTab { tab ->
            val newPatches = tab.patches.toMutableMap()
            newPatches[offset] = newByte
            
            val newUndoStack = tab.undoStack.toMutableList()
            newUndoStack.add(tab.patches)
            if (newUndoStack.size > 100) {
                newUndoStack.removeAt(0)
            }
            
            tab.copy(
                patches = newPatches,
                undoStack = newUndoStack,
                redoStack = emptyList() // Clear redo stack on new edit
            )
        }
        logInfo("EDIT", String.format("Overwrote single byte at physical offset 0x%X (decimal %d) with byte value 0x%02X", offset, offset, newByte))
        
        // Record modification to file history
        val activeT = _state.value.activeTab
        if (activeT != null) {
            addOrUpdateFileHistory(activeT.fileUri.toString(), activeT.fileName, activeT.fileSize, extraPatches = 1, hasRecovery = true)
        }

        if (_state.value.compareTabIndex != null) {
            startComparingTabs()
        }
    }

    fun undo() {
        var didUndo = false
        updateActiveTab { tab ->
            if (tab.undoStack.isNotEmpty()) {
                didUndo = true
                val lastPatches = tab.undoStack.last()
                val newUndoStack = tab.undoStack.dropLast(1)
                
                val newRedoStack = tab.redoStack.toMutableList()
                newRedoStack.add(tab.patches)
                
                tab.copy(patches = lastPatches, undoStack = newUndoStack, redoStack = newRedoStack)
            } else tab
        }
        if (didUndo) {
            logInfo("EDIT", "Action: Undo operations successfully processed.")
            if (_state.value.compareTabIndex != null) {
                startComparingTabs()
            }
        } else {
            logWarning("EDIT", "Action: Undo requested but undo state history stack is empty.")
        }
    }

    fun redo() {
        var didRedo = false
        updateActiveTab { tab ->
            if (tab.redoStack.isNotEmpty()) {
                didRedo = true
                val nextPatches = tab.redoStack.last()
                val newRedoStack = tab.redoStack.dropLast(1)
                
                val newUndoStack = tab.undoStack.toMutableList()
                newUndoStack.add(tab.patches)
                
                tab.copy(patches = nextPatches, undoStack = newUndoStack, redoStack = newRedoStack)
            } else tab
        }
        if (didRedo) {
            logInfo("EDIT", "Action: Redo operations successfully processed.")
            if (_state.value.compareTabIndex != null) {
                startComparingTabs()
            }
        } else {
            logWarning("EDIT", "Action: Redo requested but redo state history stack is empty.")
        }
    }

    fun exportFile(context: Context, destUri: Uri?) {
        if (destUri == null) return
        val currentChannel = fileChannel ?: return
        
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    val pfdOut = context.contentResolver.openFileDescriptor(destUri, "w")
                        ?: throw Exception("Could not open destination for writing")
                    
                    FileOutputStream(pfdOut.fileDescriptor).channel.use { outChannel ->
                        val bufferSize = 1024 * 1024 // 1MB chunks
                        val byteBuffer = ByteBuffer.allocate(bufferSize)
                        val fileSize = _state.value.fileSize
                        var currentOffset = 0L
                        val patches = _state.value.patches

                        while (currentOffset < fileSize) {
                            val readLen = minOf(bufferSize.toLong(), fileSize - currentOffset).toInt()
                            byteBuffer.clear()
                            byteBuffer.limit(readLen)
                            
                            var bytesRead = 0
                            while (bytesRead < readLen) {
                                val read = currentChannel.read(byteBuffer, currentOffset + bytesRead.toLong())
                                if (read <= 0) break
                                bytesRead += read
                            }
                            
                            if (bytesRead == 0) break
                            
                            val array = byteBuffer.array()
                            
                            // Apply patches to the chunk
                            if (patches.isNotEmpty()) {
                                for ((patchOffset, byteVal) in patches) {
                                    if (patchOffset in currentOffset until (currentOffset + bytesRead)) {
                                        array[(patchOffset - currentOffset).toInt()] = byteVal
                                    }
                                }
                            }
                            
                            byteBuffer.flip()
                            while(byteBuffer.hasRemaining()) {
                                outChannel.write(byteBuffer)
                            }
                            
                            currentOffset += bytesRead
                        }
                    }
                    pfdOut.close()
                }
                _state.update { it.copy(isExporting = false) }
                logInfo("IO", "Successfully completed binary export. Applied ${_state.value.patches.size} patches to target file: $destUri")
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, error = "Export failed: ${e.message}") }
                logError("IO", "Export operation failed for $destUri: ${e.message}", e)
            }
        }
    }

    private fun getByteAt(offset: Long, tab: WorkspaceTab): Byte? {
        try {
            if (offset >= tab.fileSize) return null
            if (tab.patches.containsKey(offset)) return tab.patches[offset]

            val chunkIndex = offset / CHUNK_SIZE
            val chunkOffset = (offset % CHUNK_SIZE).toInt()
            val chunk = cacheGet(Pair(tab.fileUri, chunkIndex))

            if (chunk == null) {
                requestChunkLoad(tab.fileUri, chunkIndex)
                return null
            }
            if (chunkOffset < chunk.size) return chunk[chunkOffset]
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun parseSearchQuery(query: String, type: SearchDataType, isBigEndian: Boolean): SearchPattern? {
        if (query.isEmpty()) return null
        return try {
            if (type == SearchDataType.REGEX) {
                return SearchPattern.RegexMatch(Regex(query))
            }
            if (type == SearchDataType.WILDCARD_HEX) {
                // e.g., "AF ?? 1B ?C"
                val cleaned = query.replace(" ", "")
                if (cleaned.length % 2 != 0) return null
                val mask = cleaned.chunked(2).map { chunk ->
                    if (chunk == "??" || chunk == "..") null
                    else if (chunk.contains('?')) {
                        // partial wildcard like "?C" not fully supported yet, fallback
                        null
                    } else {
                        chunk.toIntOrNull(16)
                    }
                }.toTypedArray()
                return SearchPattern.ByteMaskMatch(mask)
            }
            
            val order = if (isBigEndian) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN
            val bytes = when (type) {
                SearchDataType.HEX_TEXT -> {
                    query.filterNot { it.isWhitespace() }.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                }
                SearchDataType.ASCII_STRING -> query.toByteArray(Charsets.US_ASCII)
                SearchDataType.UNICODE_STRING -> query.toByteArray(Charsets.UTF_16LE)
                    .let { if (isBigEndian) query.toByteArray(Charsets.UTF_16BE) else it }
                SearchDataType.UTF8_STRING -> query.toByteArray(Charsets.UTF_8)
                SearchDataType.GBK_STRING -> query.toByteArray(java.nio.charset.Charset.forName("GBK"))
                
                SearchDataType.SIGNED_BYTE, SearchDataType.UNSIGNED_BYTE -> {
                    val v = query.toByteOrNull() ?: query.toUByteOrNull()?.toByte() ?: return null
                    byteArrayOf(v)
                }
                SearchDataType.SIGNED_SHORT, SearchDataType.UNSIGNED_SHORT -> {
                    val v = query.toShortOrNull() ?: query.toUShortOrNull()?.toShort() ?: return null
                    ByteBuffer.allocate(2).order(order).putShort(v).array()
                }
                SearchDataType.SIGNED_INT, SearchDataType.UNSIGNED_INT -> {
                    val v = query.toIntOrNull() ?: query.toUIntOrNull()?.toInt() ?: return null
                    ByteBuffer.allocate(4).order(order).putInt(v).array()
                }
                SearchDataType.SIGNED_LONG, SearchDataType.UNSIGNED_LONG -> {
                    val v = query.toLongOrNull() ?: query.toULongOrNull()?.toLong() ?: return null
                    ByteBuffer.allocate(8).order(order).putLong(v).array()
                }
                SearchDataType.FLOAT -> {
                    val v = query.toFloatOrNull() ?: return null
                    ByteBuffer.allocate(4).order(order).putFloat(v).array()
                }
                SearchDataType.DOUBLE -> {
                    val v = query.toDoubleOrNull() ?: return null
                    ByteBuffer.allocate(8).order(order).putDouble(v).array()
                }
                else -> return null
            }
            return SearchPattern.ExactMatch(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun writeHexDigit(digit: Char) {
        val tab = _state.value.activeTab ?: return
        val offset = tab.selectedOffset ?: return
        
        val currentByte = getByteAt(offset, tab) ?: 0
        val currentHex = String.format("%02X", currentByte.toInt() and 0xFF)
        
        // Push the new digit in. If current is "AB" and we type "C", it becomes "BC". 
        val newHex = currentHex[1].toString() + digit.uppercaseChar()
        val newByte = newHex.toIntOrNull(16)?.toByte() ?: return
        
        patchByte(offset, newByte)
        
        // After fully typing 2 digits? No, just advance after typing the second digit. We can keep it simple: 
        // Just patch it. MT Manager auto advances after 2 digits, but we can skip auto advance for simplicity, 
        // or just let it stay on the byte.
    }
    
    fun writeBinaryDigit(digit: Char) {
        val tab = _state.value.activeTab ?: return
        val offset = tab.selectedOffset ?: return
        
        val currentByte = getByteAt(offset, tab) ?: 0
        val currentBin = String.format("%8s", Integer.toBinaryString(currentByte.toInt() and 0xFF)).replace(' ', '0')
        
        // Push the new digit at the end.
        val newBin = currentBin.substring(1) + digit
        val newByte = newBin.toIntOrNull(2)?.toByte() ?: return
        
        patchByte(offset, newByte)
    }
    
    fun setInspectorBigEndian(isBigEndian: Boolean) {
        updateActiveTab { it.copy(inspectorBigEndian = isBigEndian) }
    }

    fun getInspectorData(): InspectorData? {
        val tab = _state.value.activeTab ?: return null
        val offset = tab.selectedOffset ?: return null
        
        val bytes16 = ByteArray(16)
        for (i in 0 until 16) {
            bytes16[i] = getByteAt(offset + i, tab) ?: 0
        }
        
        val bytes = bytes16.copyOfRange(0, 8)
        
        val byteOrder = if (tab.inspectorBigEndian) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN
        val bb = ByteBuffer.wrap(bytes).order(byteOrder)
        
        val has1 = offset < tab.fileSize
        val has2 = offset + 1 < tab.fileSize
        val has4 = offset + 3 < tab.fileSize
        val has8 = offset + 7 < tab.fileSize

        val int8 = if (has1) bb.get(0) else 0.toByte()
        val uint8 = int8.toUByte()
        
        val int16 = if (has2) bb.getShort(0) else null
        val uint16 = int16?.toUShort()
        
        val int32 = if (has4) bb.getInt(0) else null
        val uint32 = int32?.toUInt()
        
        val int64 = if (has8) bb.getLong(0) else null
        val uint64 = int64?.toULong()
        
        val float32 = if (has4) bb.getFloat(0) else null
        val float64 = if (has8) bb.getDouble(0) else null
        
        val binary = if (has1) Integer.toBinaryString(int8.toInt() and 0xFF).padStart(8, '0') else ""
        val octal = if (has1) Integer.toOctalString(int8.toInt() and 0xFF).padStart(3, '0') else ""

        // Local Helpers for Advanced Inspector Details
        fun formatUnixTime(sec: Long): String? {
            if (sec < 0L || sec > 4102444800L) return null
            return try {
                val date = java.util.Date(sec * 1000)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf.format(date) + " UTC"
            } catch (e: Exception) {
                null
            }
        }

        fun formatUnixTimeMs(ms: Long): String? {
            if (ms < 0L || ms > 4102444800000L) return null
            return try {
                val date = java.util.Date(ms)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf.format(date) + " UTC"
            } catch (e: Exception) {
                null
            }
        }

        fun decodeULEB128(bArray: ByteArray): Pair<ULong, Int>? {
            var result = 0UL
            var shift = 0
            var bytesRead = 0
            for (b in bArray) {
                bytesRead++
                val value = (b.toInt() and 0x7F).toULong()
                result = result or (value shl shift)
                if ((b.toInt() and 0x80) == 0) {
                    return Pair(result, bytesRead)
                }
                shift += 7
                if (shift >= 64) break
            }
            return null
        }

        fun decodeSLEB128(bArray: ByteArray): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var bytesRead = 0
            var b = 0
            for (valByte in bArray) {
                b = valByte.toInt()
                bytesRead++
                result = result or ((b and 0x7F).toLong() shl shift)
                shift += 7
                if ((b and 0x80) == 0) {
                    break
                }
                if (shift >= 64) return null
            }
            if (shift < 64 && (b and 0x40) != 0) {
                result = result or (-1L shl shift)
            }
            return Pair(result, bytesRead)
        }

        fun detectMagicHeader(bArray: ByteArray): String? {
            if (bArray.size < 2) return null
            val b0 = bArray[0].toInt() and 0xFF
            val b1 = bArray[1].toInt() and 0xFF
            
            if (b0 == 0x4D && b1 == 0x5A) return "MZ Windows/DOS Executable (.exe)"
            if (b0 == 0x42 && b1 == 0x4D) return "BMP Bitmap Image (.bmp)"
            if (b0 == 0x1F && b1 == 0x8B) return "GZIP Compressed File (.gz)"
            
            if (bArray.size < 3) return null
            val b2 = bArray[2].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "JPEG Image File (.jpg)"
            if (b0 == 0x49 && b1 == 0x44 && b2 == 0x33) return "MP3 Audio (ID3 Metadata) (.mp3)"
            
            if (bArray.size < 4) return null
            val b3 = bArray[3].toInt() and 0xFF
            val dword = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            
            if (dword == 0x504B0304) return "ZIP / APK / JAR Archive (.zip)"
            if (dword == 0x504B0506) return "ZIP Empty Archive (.zip)"
            if (dword == 0x504B0708) return "ZIP Split Archive (.zip)"
            if (dword == 0x25504446) return "PDF Document File (.pdf)"
            if (dword == 0x7F454C46) return "ELF Binary Executable (.so/.bin)"
            if (dword == (0xCAFEBABEL.toInt())) return "Java Class File / Mach-O Fat (.class)"
            if (dword == 0x6465780A) return "Android Dex Executable (.dex)"
            if (dword == 0x4F676753) return "Ogg Container File (.ogg)"
            if (dword == 0x1A45DFA3) return "Matroska / MKV WebM Video (.mkv)"
            
            if (bArray.size < 6) return null
            val b4 = bArray[4].toInt() and 0xFF
            val b5 = bArray[5].toInt() and 0xFF
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38 && (b4 == 0x37 || b4 == 0x39) && b5 == 0x61) return "GIF Image Header (.gif)"
            if (b0 == 0x37 && b1 == 0x7A && b2 == 0xBC && b3 == 0xAF && b4 == 0x27 && b5 == 0x1C) return "7-Zip Container (.7z)"

            if (bArray.size < 8) return null
            val b6 = bArray[6].toInt() and 0xFF
            val b7 = bArray[7].toInt() and 0xFF
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47 && b4 == 0x0D && b5 == 0x0A && b6 == 0x1A && b7 == 0x0A) return "PNG Image Layout (.png)"
            if (b0 == 0x52 && b1 == 0x61 && b2 == 0x72 && b3 == 0x21 && b4 == 0x1A && b5 == 0x07 && b6 == 0x00) return "RAR Archive File (.rar)"
            
            if (bArray.size < 16) return null
            val sqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            var isSqlite = true
            for (i in 0 until 16) {
                if (bArray[i] != sqliteMagic[i]) {
                    isSqlite = false
                    break
                }
            }
            if (isSqlite) return "SQLite 3 Database (.db)"

            return null
        }

        val asciiStr = bytes.map { b ->
            val i = b.toInt() and 0xFF
            if (i in 32..126) i.toChar() else '.'
        }.joinToString("")

        val utf8Str = try {
            val nonNullBytes = bytes.takeWhile { it != 0.toByte() }.toByteArray()
            if (nonNullBytes.isEmpty()) "" else {
                val decoded = String(nonNullBytes, Charsets.UTF_8)
                decoded.map { c -> if (c.isISOControl()) '.' else c }.joinToString("")
            }
        } catch(e: Exception) {
            "N/A"
        }

        val utf16LeStr = try {
            val decoded = String(bytes, Charsets.UTF_16LE)
            decoded.map { c -> if (c.isISOControl()) '.' else c }.joinToString("")
        } catch(e: Exception) {
            "N/A"
        }

        val utf16BeStr = try {
            val decoded = String(bytes, Charsets.UTF_16BE)
            decoded.map { c -> if (c.isISOControl()) '.' else c }.joinToString("")
        } catch(e: Exception) {
            "N/A"
        }

        val guidBe = try {
            val bb16 = ByteBuffer.wrap(bytes16)
            val msb = bb16.getLong(0)
            val lsb = bb16.getLong(8)
            java.util.UUID(msb, lsb).toString().uppercase()
        } catch(e: Exception) {
            null
        }

        val guidLe = try {
            val bbLe = ByteBuffer.wrap(bytes16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val data1 = bbLe.getInt(0)
            val data2 = bbLe.getShort(4).toInt() and 0xFFFF
            val data3 = bbLe.getShort(6).toInt() and 0xFFFF
            val data4_0 = bytes16[8].toInt() and 0xFF
            val data4_1 = bytes16[9].toInt() and 0xFF
            val data4_2 = bytes16[10].toInt() and 0xFF
            val data4_3 = bytes16[11].toInt() and 0xFF
            val data4_4 = bytes16[12].toInt() and 0xFF
            val data4_5 = bytes16[13].toInt() and 0xFF
            val data4_6 = bytes16[14].toInt() and 0xFF
            val data4_7 = bytes16[15].toInt() and 0xFF
            
            String.format(
                "%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X",
                data1, data2, data3,
                data4_0, data4_1,
                data4_2, data4_3, data4_4, data4_5, data4_6, data4_7
            )
        } catch(e: Exception) {
            null
        }

        val unixTime32Sec = uint32?.toLong()?.let { formatUnixTime(it) }
        val unixTime64Ms = uint64?.toLong()?.let { formatUnixTimeMs(it) }

        val lebUResult = decodeULEB128(bytes16)
        val leb128U = lebUResult?.let { "${it.first} (${it.second} byte${if(it.second > 1) "s" else ""})" }
        
        val lebSResult = decodeSLEB128(bytes16)
        val leb128S = lebSResult?.let { "${it.first} (${it.second} byte${if(it.second > 1) "s" else ""})" }

        val magicHeader = detectMagicHeader(bytes16)

        return InspectorData(
            int8 = int8,
            uint8 = uint8,
            int16 = int16,
            uint16 = uint16,
            int32 = int32,
            uint32 = uint32,
            int64 = int64,
            uint64 = uint64,
            float32 = float32,
            float64 = float64,
            binary = binary,
            octal = octal,
            asciiStr = asciiStr,
            utf8Str = utf8Str,
            utf16LeStr = utf16LeStr,
            utf16BeStr = utf16BeStr,
            unixTime32Sec = unixTime32Sec,
            unixTime64Ms = unixTime64Ms,
            guidBe = guidBe,
            guidLe = guidLe,
            leb128U = leb128U,
            leb128S = leb128S,
            magicHeader = magicHeader
        )
    }

    override fun onCleared() {
        super.onCleared()
        closeAllFiles()
    }
    
    private fun closeAllFiles() {
        for ((_, pair) in openFiles) {
            try { pair.second.close() } catch (_: Exception) {}
            try { pair.first.close() } catch (_: Exception) {}
        }
        openFiles.clear()
    }

    // DESKTOP WORKSPACE & CONTROL ACTIONS

    fun setSidebarWeight(weight: Float) {
        _state.update { it.copy(sidebarWeight = weight.coerceIn(0.15f, 0.85f)) }
    }

    fun toggleCommandPalette() {
        _state.update { it.copy(showCommandPalette = !it.showCommandPalette) }
    }

    fun toggleFloatingTool(tool: String) {
        _state.update { state ->
            val set = state.floatingTools.toMutableSet()
            if (set.contains(tool)) set.remove(tool) else set.add(tool)
            state.copy(floatingTools = set)
        }
    }

    fun togglePatchVerification(show: Boolean) {
        _state.update { it.copy(showPatchVerification = show) }
    }

    fun clearPatches() {
        updateActiveTab { it.copy(patches = emptyMap(), undoStack = emptyList(), redoStack = emptyList()) }
    }
    
    fun dismissAutosaveBanner() {
        _state.update { it.copy(hasAutosavedBackup = false) }
    }

    fun isChannelAvailable(uri: Uri?): Boolean {
        if (uri == null) return false
        return openFiles.containsKey(uri) && openFiles[uri]?.second?.isOpen == true
    }

    fun reassociateFile(context: Context, tabIndex: Int, newUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            newUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val cursor = context.contentResolver.query(newUri, null, null, null, null)
                    var name = "unknown"
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                name = it.getString(nameIndex)
                            }
                        }
                    }

                    val pfd = context.contentResolver.openFileDescriptor(newUri, "r")
                        ?: throw Exception("Could not open file descriptor")
                    
                    val channel = FileInputStream(pfd.fileDescriptor).channel
                    val size = channel.size()

                    val old = openFiles.put(newUri, Pair(pfd, channel))
                    try { old?.first?.close() } catch(_: Exception) {}
                    try { old?.second?.close() } catch(_: Exception) {}

                    _state.update { state ->
                        val tabsList = state.tabs.toMutableList()
                        if (tabIndex in tabsList.indices) {
                            val oldTab = tabsList[tabIndex]
                            if (oldTab.fileUri != newUri) {
                                val otherHasOldUri = tabsList.any { t -> t != oldTab && t.fileUri == oldTab.fileUri }
                                if (!otherHasOldUri) {
                                    try { openFiles[oldTab.fileUri]?.second?.close() } catch (_: Exception) {}
                                    try { openFiles[oldTab.fileUri]?.first?.close() } catch (_: Exception) {}
                                    openFiles.remove(oldTab.fileUri)
                                }
                            }
                            tabsList[tabIndex] = oldTab.copy(
                                fileUri = newUri,
                                fileName = name,
                                fileSize = size
                            )
                        }
                        state.copy(tabs = tabsList, isLoading = false)
                    }
                    logInfo("PERMISSION", "Successfully reassociated file \"$name\" at tab $tabIndex ($size bytes) | New URI: $newUri")
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to link file: ${e.message}") }
                logError("PERMISSION", "Failed to link file on permission reload: ${e.message}", e)
            }
        }
    }

    // SAFE FILE HANDLING: AUTOSAVE & RESTORE ENGINES

    fun triggerContinuousBackup(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
            val editor = sharedPrefs.edit()
            val stateVal = _state.value
            
            // Clear prior records to prevent stale tabs from persisting
            editor.clear()
            
            // Persist setting configs
            editor.putBoolean("is_dark_mode", stateVal.isDarkMode)
            editor.putString("theme_color", stateVal.themeColor.name)
            editor.putString("display_mode", stateVal.displayMode.name)
            editor.putString("encoding_mode", stateVal.encodingMode.name)
            editor.putBoolean("desktop_mode", stateVal.desktopMode)
            editor.putFloat("sidebar_weight", stateVal.sidebarWeight)
            
            // Overall global features
            editor.putInt("compare_tab_index", stateVal.compareTabIndex ?: -1)
            editor.putBoolean("show_patch_verification", stateVal.showPatchVerification)
            editor.putString("search_history", stateVal.searchHistory.toSet().joinToString("\n"))
            editor.putString("floating_tools", stateVal.floatingTools.joinToString(","))

            editor.putBoolean("save_important_files_automatically", stateVal.saveImportantFilesAutomatically)
            editor.putBoolean("enable_smart_notifications", stateVal.enableSmartNotifications)
            editor.putBoolean("allow_storage_access", stateVal.allowStorageAccess)
            editor.putString("app_language", stateVal.appLanguage.name)

            // Persist tab records if autosave is enabled
            if (stateVal.saveImportantFilesAutomatically) {
                editor.putInt("active_tab_index", stateVal.activeTabIndex)
                editor.putInt("tab_count", stateVal.tabs.size)
                
                stateVal.tabs.forEachIndexed { idx, tab ->
                    val patchedStr = tab.patches.entries.joinToString(";") { "${it.key}:${it.value}" }
                    editor.putString("patches_tab_$idx", patchedStr)
                    editor.putString("uri_tab_$idx", tab.fileUri.toString())
                    editor.putString("name_tab_$idx", tab.fileName)
                    editor.putLong("size_tab_$idx", tab.fileSize)
                    
                    // Save tab selections and detail features
                    editor.putLong("selected_offset_tab_$idx", tab.selectedOffset ?: -1L)
                    editor.putBoolean("show_inspector_tab_$idx", tab.showInspector)
                    editor.putBoolean("inspector_big_endian_tab_$idx", tab.inspectorBigEndian)
                }
            } else {
                editor.putInt("tab_count", 0)
                editor.putInt("active_tab_index", -1)
            }
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAutosavedBackup(context: Context) {
        try {
            val sharedPrefs = context.getSharedPreferences("hex_editor_backup", Context.MODE_PRIVATE)
            
            // Load and apply core settings configuration
            val isDarkMode = sharedPrefs.getBoolean("is_dark_mode", true)
            val themeColorStr = sharedPrefs.getString("theme_color", AppThemeColor.CYBERPUNK.name) ?: AppThemeColor.CYBERPUNK.name
            val themeColor = try { AppThemeColor.valueOf(themeColorStr) } catch (_: Exception) { AppThemeColor.CYBERPUNK }
            
            val displayModeStr = sharedPrefs.getString("display_mode", DisplayMode.HEX.name) ?: DisplayMode.HEX.name
            val displayMode = try { DisplayMode.valueOf(displayModeStr) } catch (_: Exception) { DisplayMode.HEX }
            
            val encodingModeStr = sharedPrefs.getString("encoding_mode", EncodingMode.UTF_8.name) ?: EncodingMode.UTF_8.name
            val encodingMode = try { EncodingMode.valueOf(encodingModeStr) } catch (_: Exception) { EncodingMode.UTF_8 }
            
            val desktopMode = sharedPrefs.getBoolean("desktop_mode", false)
            val sidebarWeight = sharedPrefs.getFloat("sidebar_weight", 0.35f)
            
            val appLanguageStr = sharedPrefs.getString("app_language", AppLanguage.EN.name) ?: AppLanguage.EN.name
            val appLanguage = try { AppLanguage.valueOf(appLanguageStr) } catch (_: Exception) { AppLanguage.EN }
            
            val compareIdxRaw = sharedPrefs.getInt("compare_tab_index", -1)
            val showPatchGen = sharedPrefs.getBoolean("show_patch_verification", false)
            val searchHistoryStr = sharedPrefs.getString("search_history", "") ?: ""
            val searchHistory = searchHistoryStr.split("\n").filter { it.isNotEmpty() }
            val floatingToolsStr = sharedPrefs.getString("floating_tools", "") ?: ""
            val floatingTools = floatingToolsStr.split(",").filter { it.isNotEmpty() }.toSet()

            val smartNotif = sharedPrefs.getBoolean("enable_smart_notifications", false)
            val storeAccess = sharedPrefs.getBoolean("allow_storage_access", false)
            val autoSaveFiles = sharedPrefs.getBoolean("save_important_files_automatically", true)

            // Load file history
            val historyStr = sharedPrefs.getString("file_history_records", "") ?: ""
            val records = mutableListOf<FileHistoryRecord>()
            if (historyStr.isNotEmpty()) {
                historyStr.split("|||").forEach { line ->
                    if (line.isNotEmpty()) {
                        val parts = line.split("##")
                        if (parts.size >= 6) {
                            val uri = parts[0]
                            val name = parts[1]
                            val size = parts[2].toLongOrNull() ?: 0L
                            val lastMod = parts[3].toLongOrNull() ?: System.currentTimeMillis()
                            val modCount = parts[4].toIntOrNull() ?: 0
                            val hasRec = parts[5].toBoolean()
                            records.add(FileHistoryRecord(uri, name, size, lastMod, modCount, hasRec))
                        }
                    }
                }
            }

            _state.update {
                it.copy(
                    isDarkMode = isDarkMode,
                    themeColor = themeColor,
                    displayMode = displayMode,
                    encodingMode = encodingMode,
                    desktopMode = desktopMode,
                    sidebarWeight = sidebarWeight,
                    enableSmartNotifications = smartNotif,
                    allowStorageAccess = storeAccess,
                    saveImportantFilesAutomatically = autoSaveFiles,
                    fileHistory = records,
                    appLanguage = appLanguage
                )
            }
            
            val tabCount = if (autoSaveFiles) sharedPrefs.getInt("tab_count", 0) else 0
            if (tabCount == 0) {
                _state.update { 
                    it.copy(
                        compareTabIndex = null,
                        showPatchVerification = showPatchGen,
                        searchHistory = searchHistory,
                        floatingTools = floatingTools,
                        isRestoreCompleted = true, 
                        isLoading = false
                    ) 
                }
                return
            }
            
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val restoredTabs = mutableListOf<WorkspaceTab>()
                
                for (i in 0 until tabCount) {
                    val uriStr = sharedPrefs.getString("uri_tab_$i", null) ?: continue
                    val name = sharedPrefs.getString("name_tab_$i", "recovered_file") ?: "recovered_file"
                    val size = sharedPrefs.getLong("size_tab_$i", 0L)
                    val patchesStr = sharedPrefs.getString("patches_tab_$i", "") ?: ""
                    
                    val uri = Uri.parse(uriStr)
                    val patches = mutableMapOf<Long, Byte>()
                    if (patchesStr.isNotEmpty()) {
                        patchesStr.split(";").forEach { pair ->
                            val parts = pair.split(":")
                            if (parts.size == 2) {
                                val k = parts[0].toLongOrNull()
                                val v = parts[1].toByteOrNull()
                                if (k != null && v != null) {
                                    patches[k] = v
                                }
                            }
                        }
                    }
                    
                    try {
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            val channel = FileInputStream(pfd.fileDescriptor).channel
                            openFiles[uri] = Pair(pfd, channel)
                        } else {
                            throw Exception("FD is null")
                        }
                    } catch (err: Exception) {
                        err.printStackTrace()
                        // Fall back to internal cached file if URI is inaccessible due to system/SAF reboot
                        val cacheFile = java.io.File(context.cacheDir, "file_cache_${uri.hashCode()}")
                        if (cacheFile.exists()) {
                            try {
                                val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                val channel = FileInputStream(pfd.fileDescriptor).channel
                                openFiles[uri] = Pair(pfd, channel)
                            } catch (e2: Exception) {
                                e2.printStackTrace()
                            }
                        }
                    }
                    
                    val selOffRaw = sharedPrefs.getLong("selected_offset_tab_$i", -1L)
                    val selOffset = if (selOffRaw != -1L) selOffRaw else null
                    val showInsp = sharedPrefs.getBoolean("show_inspector_tab_$i", false)
                    val inspBigEndian = sharedPrefs.getBoolean("inspector_big_endian_tab_$i", false)

                    restoredTabs.add(
                        WorkspaceTab(
                            fileUri = uri,
                            fileName = name,
                            fileSize = size,
                            patches = patches,
                            selectedOffset = selOffset,
                            showInspector = showInsp,
                            inspectorBigEndian = inspBigEndian
                        )
                    )
                }
                
                val activeIdx = sharedPrefs.getInt("active_tab_index", -1)
                
                if (restoredTabs.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            tabs = restoredTabs,
                            activeTabIndex = if (activeIdx in restoredTabs.indices) activeIdx else 0,
                            compareTabIndex = if (compareIdxRaw in restoredTabs.indices) compareIdxRaw else null,
                            showPatchVerification = showPatchGen,
                            searchHistory = searchHistory,
                            floatingTools = floatingTools,
                            hasAutosavedBackup = true,
                            isRestoreCompleted = true,
                            isLoading = false
                        )
                    }
                } else {
                    _state.update { 
                        it.copy(
                            compareTabIndex = null,
                            showPatchVerification = showPatchGen,
                            searchHistory = searchHistory,
                            floatingTools = floatingTools,
                            isRestoreCompleted = true, 
                            isLoading = false
                        ) 
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.update { it.copy(isRestoreCompleted = true, isLoading = false) }
        }
    }

    fun createBackupFileBeforeExport(context: Context): Boolean {
        val tab = _state.value.activeTab ?: return false
        try {
            val backupFile = java.io.File(context.filesDir, "${tab.fileName}.bak")
            backupFile.outputStream().use { outStream ->
                val channel = fileChannel ?: return false
                val originalPos = channel.position()
                channel.position(0)
                val buffer = ByteBuffer.allocate(4096)
                while (channel.read(buffer) > 0) {
                    buffer.flip()
                    outStream.write(buffer.array(), 0, buffer.limit())
                    buffer.clear()
                }
                channel.position(originalPos)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
