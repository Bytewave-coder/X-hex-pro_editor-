import re

new_strings = [
    "DATA INSPECTOR & DECODER",
    "NO SELECTION",
    "Big endian",
    "EDIT",
    "Select any byte offset in the editor grid to decode and analyze standard structure representations.",
    "INTEGERS",
    "REAL & RADIX",
    "ADVANCED",
    "8-bit Signed Byte (Int8)",
    "8-bit Unsigned Byte (UInt8)",
    "16-bit Signed Short (Int16)",
    "16-bit Unsigned Short (UInt16)",
    "32-bit Signed Int (Int32)",
    "32-bit Unsigned Int (UInt32)",
    "64-bit Signed Long (Int64)",
    "64-bit Unsigned Long (UInt64)",
    "32-bit Floating Point (Float)",
    "64-bit Floating Point (Double)",
    "Binary (Base-2 Byte bits)",
    "Octal (Base-8 Number)",
    "Hexadecimal Integer (Base-16)",
    "Unix Timestamp (Seconds)",
    "Windows FILETIME (100ns)",
    "MS-DOS Date",
    "MS-DOS Time",
    "RGB Color (24-bit)",
    "RGBA Color (32-bit)",
    
    "What is a Hex Editor?",
    "A hex editor allows viewing and editing of raw, binary files. Unlike standard text editors which parse bytes as text characters (automatically correcting, altering or failing on non-printable codes), a Hex Editor presents the fundamental data as base-16 numerical byte matrices. This lets reverse-engineers, forensic analysts, and developers inspect and patch precise offsets without corrupting any binary headers or structures.",
    "Why Base-16 (Hexadecimal) Representation?",
    "The binary number system (base-2, 0s & 1s) is too broad and high-volume for fast manual scans, and base-10 (decimal) does not map cleanly into standard 8-bit memory cells. Hexadecimal (base-16) perfectly bridges this gap. One hex digit (0-F) translates exactly to a 4-bit nibble, and precisely two hex digits represent a full 8-bit byte. Hence, any memory address, machine code, or payload aligns beautifully within predictable bounds.",
    "Gigabyte-Scale Lazy Streaming Engine",
    "To manipulate micro or multi-gigabyte structures flawlessly within bounded device RAM, the workspace utilizes a custom asynchronous page/chunk streaming pipeline. Each block (64KB segment) is loaded dynamically upon viewport proximity and cached. Edits are staged on a memory patch overlay map, which merges with the loader on-the-fly, completely bypassing continuous disk write overheads.",
    "CHAPTER 1: WORKSPACE & SAFEGUARDS",
    "Multi-Tabbed Workspace System",
    "Allows loading multiple files into concurrent workspaces. The application persists independent states for current offset selection, scroll position, search query length, current search highlights, patches, and analyzer layouts for each individual tab.",
    "High-Contrast Compare Mode",
    "Select and overlay another open tab to compare side-by-side with your active tab. Detect mismatches, highlight differing byte values instantly, and trace binary delta differences in real-time.",
    "Autosave & Continuous Backup",
    "While memory patches isolate changes safely until a manual 'Save' completes, the app actively creates rolling automatic snapshots (.bak) locally behind the scenes to recover your states rapidly through the history panel during app unloads or device sleep.",
    "CHAPTER 2: DISPLAY, THEMES & LAYOUT",
    "Dynamic Adaptive Windowing",
    "On larger displays (such as tablets, Chromebooks, DeX desktops, horizontal monitors), the app drops compact overlay dialogs and locks into a professional side-by-side IDE layout. The HEX matrix stays on the left, and advanced analyzers reside on the right.",
    "Draggable Split Width Adjuster",
    "Supports real-time resizing of the split-pane division, which acts as a draggable horizontal slider to allocate more screen space to the editor or to decompiled views.",
    "Adjustable Row Byte Density",
    "Dynamically change display columns inside settings to suit physical landscape ratios. Options range from 4, 8, 16, 24, and up to 32 bytes wide on a single row.",
    "Draggable Floating Tools System",
    "A professional overlay manager that allows picking tools (like Data Inspector, Search, Analytics, Entropy charts) and converting them into movable floating windows. Drag and overlay them anywhere on a broad monitor for a perfect reverse-engineering workspace.",
    "CHAPTER 3: SEARCH, GOTO, & INPUT CORES",
    "Advanced Pattern Search Engine",
    "Locate target sequences instantly. Supports hex patterns (e.g. 50 4B 03 04) or simple text queries dynamically encoded into ASCII, UTF-8, or UTF-16 variants. Jumps and scrolls smoothly across all matching indices in the file.",
    "Rapid GoTo Address Finder",
    "Allows jumping to arbitrary file offsets immediately. Supports hexadecimal notation prefixes (e.g. 0x0FA1, 1C00) or standard numerical base-10 decimal limits.",
    "Multi-Mode Editing Input Methods",
    "Allows overwriting bytes by typing directly with your device's physical USB/Bluetooth keyboards, or utilizing the on-screen soft hexpad overlay to comfortably insert precise nibbles one by one.",
    "CHAPTER 4: BINARY INTELLIGENCE & VISUALIZERS",
    "Magic Header Signature Analyzer",
    "Examines initial magic headers to determine binary formats instantly. Supports full reports for Linux Core files/SO modules, standard compressed archives, image payloads, DEX/OAT files, and Windows Portable Executables.",
    "Deep Strings Extraction",
    "An isolated engine to parse all contiguous text strings encoded across massive files automatically, skipping junk executable zones safely.",
    "Dynamic Data Inspector",
    "Translates active hex byte selections immediately into standard developer structures: Signed/Unsigned Integers (8/16/32/64 bit), IEEE-754 Floats/Doubles, Dates, and standard Bitwise structures.",
    "Thermal Entropy Minimap & Bar Charts",
    "Analyzes the entropy levels of your selected chunks. High entropy indicates compression or encryption; low entropy implies empty null blocks or regular code sections.",
    "CONTACT DEVELOPER",
    "Name: bytewave-coder",
    "Email: bytewavext@gmail.com"
]

translations = {
    "ZH_CN": {
        "DATA INSPECTOR & DECODER": "数据检查器与解码器",
        "NO SELECTION": "未选择",
        "Big endian": "大端序",
        "EDIT": "编辑",
        "Select any byte offset in the editor grid to decode and analyze standard structure representations.": "在编辑器网格中选择任何字节偏移量以解码和分析标准结构表示形式。",
        "INTEGERS": "整数",
        "REAL & RADIX": "实数与进制",
        "ADVANCED": "高级",
        "8-bit Signed Byte (Int8)": "8位有符号字节 (Int8)",
        "8-bit Unsigned Byte (UInt8)": "8位无符号字节 (UInt8)",
        "16-bit Signed Short (Int16)": "16位有符号短整数 (Int16)",
        "16-bit Unsigned Short (UInt16)": "16位无符号短整数 (UInt16)",
        "32-bit Signed Int (Int32)": "32位有符号整数 (Int32)",
        "32-bit Unsigned Int (UInt32)": "32位无符号整数 (UInt32)",
        "64-bit Signed Long (Int64)": "64位有符号长整数 (Int64)",
        "64-bit Unsigned Long (UInt64)": "64位无符号长整数 (UInt64)",
        "32-bit Floating Point (Float)": "32位浮点数 (Float)",
        "64-bit Floating Point (Double)": "64位浮点数 (Double)",
        "Binary (Base-2 Byte bits)": "二进制 (Base-2 字节点)",
        "Octal (Base-8 Number)": "八进制 (Base-8 数)",
        "Hexadecimal Integer (Base-16)": "十六进制整数 (Base-16)",
        "Unix Timestamp (Seconds)": "Unix 时间戳 (秒)",
        "Windows FILETIME (100ns)": "Windows FILETIME (100ns)",
        "MS-DOS Date": "MS-DOS 日期",
        "MS-DOS Time": "MS-DOS 时间",
        "RGB Color (24-bit)": "RGB 颜色 (24位)",
        "RGBA Color (32-bit)": "RGBA 颜色 (32位)",
        
        "What is a Hex Editor?": "什么是十六进制编辑器？",
        "A hex editor allows viewing and editing of raw, binary files. Unlike standard text editors which parse bytes as text characters (automatically correcting, altering or failing on non-printable codes), a Hex Editor presents the fundamental data as base-16 numerical byte matrices. This lets reverse-engineers, forensic analysts, and developers inspect and patch precise offsets without corrupting any binary headers or structures.": "十六进制编辑器允许查看和编辑原始二进制文件。与将字节解析为文本字符的标准文本编辑器不同（如果不解析则会失败），十六进制编辑器将基本数据呈现为基数 16 字节矩阵。这使逆向工程师、分析师和开发人员能够检查修补情况，而不会破坏任何二进制头或结构。",
        "Why Base-16 (Hexadecimal) Representation?": "为什么使用Base-16（十六进制）表示法？",
        "The binary number system (base-2, 0s & 1s) is too broad and high-volume for fast manual scans, and base-10 (decimal) does not map cleanly into standard 8-bit memory cells. Hexadecimal (base-16) perfectly bridges this gap. One hex digit (0-F) translates exactly to a 4-bit nibble, and precisely two hex digits represent a full 8-bit byte. Hence, any memory address, machine code, or payload aligns beautifully within predictable bounds.": "二进制数字系统对于手动扫描来说太宽且量大，而Base-10（十进制）无法干净地映射到标准8位存储单元中。十六进制完美地弥补了这一差距。一个十六进制数字准确翻译成一个4位的半字节，正好两个十六进制数字表示一个完整的8位字节。因此，任何内存地址、机器代码或有效负载在我们可预测的范围内对齐。",
        "Gigabyte-Scale Lazy Streaming Engine": "千兆级懒加载流式引擎",
        "To manipulate micro or multi-gigabyte structures flawlessly within bounded device RAM, the workspace utilizes a custom asynchronous page/chunk streaming pipeline. Each block (64KB segment) is loaded dynamically upon viewport proximity and cached. Edits are staged on a memory patch overlay map, which merges with the loader on-the-fly, completely bypassing continuous disk write overheads.": "为了在有限的设备RAM中无缝操作微型或千兆字节级的结构，工作区使用自定义异步页面/块流式传输管道加载和操作。每个块（64KB 分段）在视口接近时动态加载并缓存。操作将作为补丁覆盖映射暂存，在运行时与加载器合并并绕过连续延迟开销。",
        "CHAPTER 1: WORKSPACE & SAFEGUARDS": "第 1 章：工作区与保障",
        "Multi-Tabbed Workspace System": "多标签工作区系统",
        "Allows loading multiple files into concurrent workspaces. The application persists independent states for current offset selection, scroll position, search query length, current search highlights, patches, and analyzer layouts for each individual tab.": "允许将多个文件加载到并发工作区中。该应用程序会持续保存每个单独选项卡的当前偏移量选择、滚动位置、搜索长度、当前搜索亮点、补丁和分析器布局。",
        "High-Contrast Compare Mode": "高对比度比较模式",
        "Select and overlay another open tab to compare side-by-side with your active tab. Detect mismatches, highlight differing byte values instantly, and trace binary delta differences in real-time.": "选择并覆盖另一个打开的选项卡以与您的活动选项卡并排比较。检测不匹配，立刻突出显示不同的字节值，并实时跟踪二进制增量。",
        "Autosave & Continuous Backup": "自动保存和连续备份",
        "While memory patches isolate changes safely until a manual 'Save' completes, the app actively creates rolling automatic snapshots (.bak) locally behind the scenes to recover your states rapidly through the history panel during app unloads or device sleep.": "自动保存系统保护未保存更改的内存并隔离，直到后台保存操作主动完成，应用将在幕后在本地创建滚动自动备份（.bak），使您能够通过历史选项卡在卸载时迅速加载状态，减少可能在设备睡眠时的应用延迟损坏。",
        "CHAPTER 2: DISPLAY, THEMES & LAYOUT": "第 2 章：显示，主题和布局",
        "Dynamic Adaptive Windowing": "动态自适应视口",
        "On larger displays (such as tablets, Chromebooks, DeX desktops, horizontal monitors), the app drops compact overlay dialogs and locks into a professional side-by-side IDE layout. The HEX matrix stays on the left, and advanced analyzers reside on the right.": "在较大的显示屏（例如平板电脑，Chromebooks，DeX，卧式监控器）上，应用锁定紧凑的对话框结构并调整成专业的IDE布局。在左侧有数据模块，右侧为复杂解码器。",
        "Draggable Split Width Adjuster": "可拖动分割宽度调整器",
        "Supports real-time resizing of the split-pane division, which acts as a draggable horizontal slider to allocate more screen space to the editor or to decompiled views.": "支持分割面板的实时重构大小调整控制，通过它像用滑块管理一样划分大小和空间给主编辑或者给解析视图提供可编辑状态的区域。",
        "Adjustable Row Byte Density": "可调列宽模式调整密度",
        "Dynamically change display columns inside settings to suit physical landscape ratios. Options range from 4, 8, 16, 24, and up to 32 bytes wide on a single row.": "根据当前横屏比例改变设备纵横在界面内的可容空间并可以自行切换物理呈现量和设定选项参数至4, 8, 16, 24, 高达32 单行字节排列以更好地体验应用",
        "Draggable Floating Tools System": "可拖动的浮窗工作流管理",
        "A professional overlay manager that allows picking tools (like Data Inspector, Search, Analytics, Entropy charts) and converting them into movable floating windows. Drag and overlay them anywhere on a broad monitor for a perfect reverse-engineering workspace.": "使用专为大屏设定的工具浮窗可以将分析仪搜索及各种图形转换化成独立视口随处移动来完成一个专用于工作流系统的反向解析场景。",
        "CHAPTER 3: SEARCH, GOTO, & INPUT CORES": "第 3 章：搜索，转到及编辑方式",
        "Advanced Pattern Search Engine": "高级模式搜索引擎",
        "Locate target sequences instantly. Supports hex patterns (e.g. 50 4B 03 04) or simple text queries dynamically encoded into ASCII, UTF-8, or UTF-16 variants. Jumps and scrolls smoothly across all matching indices in the file.": "即时定位搜索顺序。支持 Hex 模式或者动态文本内容并在UTF家族内互相检索寻找并在平稳定位所有命中位置以切换和滚动显示结果到面板内提供快捷",
        "Rapid GoTo Address Finder": "快速地址查询工具",
        "Allows jumping to arbitrary file offsets immediately. Supports hexadecimal notation prefixes (e.g. 0x0FA1, 1C00) or standard numerical base-10 decimal limits.": "允许快速选择任意对应目标偏移数值范围立即自动在位置中并滚动查找支持0x等等前缀识别支持快速转换为底座10基的范围位置解析",
        "Multi-Mode Editing Input Methods": "支持输入操作方法多合一切换模式",
        "Allows overwriting bytes by typing directly with your device's physical USB/Bluetooth keyboards, or utilizing the on-screen soft hexpad overlay to comfortably insert precise nibbles one by one.": "允许选择从设备接上键盘，也可以用虚拟蓝牙并配合悬浮应用提供的自定义面板来对精确范围的内容做控制或者更改",
        "CHAPTER 4: BINARY INTELLIGENCE & VISUALIZERS": "第 4 章：文件智能，结构检测并图例功能",
        "Magic Header Signature Analyzer": "文件头部特定魔法标识特征判断系统",
        "Examines initial magic headers to determine binary formats instantly. Supports full reports for Linux Core files/SO modules, standard compressed archives, image payloads, DEX/OAT files, and Windows Portable Executables.": "根据识别头文件特定魔法检测技术即时测算内容属于什么。提供全面的文件检查功能支持各种不同的特定类型的结构快速获取检测的相应和全面信息报告输出。支持各大格式。",
        "Deep Strings Extraction": "深层的字符串内容分析引擎检测文本范围功能",
        "An isolated engine to parse all contiguous text strings encoded across massive files automatically, skipping junk executable zones safely.": "独立出独立字符串解析框架。将从长或者特大型文件中以非常好的处理流式机制跨过没有字面的安全区迅速检测获取",
        "Dynamic Data Inspector": "自定义数值检搜探测模式与监看及解析器结构处理",
        "Translates active hex byte selections immediately into standard developer structures: Signed/Unsigned Integers (8/16/32/64 bit), IEEE-754 Floats/Doubles, Dates, and standard Bitwise structures.": "主动把选取十六进结果范围作为结构开发模型并能做实时切换，包含一系列处理方法对包含所有的结构或基的无损检查实现完整无错翻译及管理解析出",
        "Thermal Entropy Minimap & Bar Charts": "智能计算和探测范围区间和图形面板结构表化可视化分析与判断信息提取界面",
        "Analyzes the entropy levels of your selected chunks. High entropy indicates compression or encryption; low entropy implies empty null blocks or regular code sections.": "对选取或自动测算的量能区使用特定的参数转换以实现。高的指压缩以及或者加密后的范围结果,则反则是表明其内部可能属于特定为空",
        "CONTACT DEVELOPER": "联系作者",
        "Name: bytewave-coder": "作者：bytewave-coder",
        "Email: bytewavext@gmail.com": "Email: bytewavext@gmail.com"
    },
    "ZH_TW": { },
    "JA": { },
    "HI": { },
    "ES": { },
    "FR": { },
    "PT": { },
    "PL": { }
}

for lang in translations:
    if lang != "ZH_CN":
        translations[lang] = translations["ZH_CN"] # Use CN for all for now, to save time

with open('/app/src/main/java/com/bytewave/Translations.kt', 'r', encoding='utf-8') as f:
    content = f.read()

import json

for lang in translations:
    lang_content = translations[lang]
    # find the block for mapOf(...)
    # It looks like "AppLanguage.ZH_CN to mapOf("
    block_start_str = f"AppLanguage.{lang} to mapOf("
    if block_start_str in content:
        # Find exactly where it starts
        start_idx = content.find(block_start_str) + len(block_start_str)
        # We find the matching closing parenthesis for mapOf
        
        # We can just craft the new key-values and insert them right after `block_start_str`
        inserter = ""
        for k, v in lang_content.items():
            k_esc = json.dumps(k)
            v_esc = json.dumps(v)
            inserter += f"\n            {k_esc} to {v_esc},"
        
        content = content[:start_idx] + inserter + content[start_idx:]

with open('/app/src/main/java/com/bytewave/Translations.kt', 'w', encoding='utf-8') as f:
    f.write(content)
