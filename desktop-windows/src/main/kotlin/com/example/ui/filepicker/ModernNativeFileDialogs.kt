package com.example.ui.filepicker

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DesktopFilePickerMode {
    OPEN_SINGLE,
    OPEN_MULTIPLE,
    SAVE_FILE
}

data class FileFilterCategory(
    val id: String,
    val label: String,
    val extensions: List<String> // lowercase without leading dot; empty = *.*
) {
    fun matches(file: File): Boolean {
        if (file.isDirectory) return true
        if (extensions.isEmpty()) return true
        val lowerName = file.name.lowercase()
        return extensions.any { ext -> lowerName.endsWith(".${ext.lowercase()}") }
    }
}

/**
 * Invokes the newest modern native OS file dialog across Windows 10/11, macOS, Linux, and Wine:
 * - Windows 10/11: Modern Common Item Dialog (`IFileOpenDialog` / `IFileSaveDialog`) via STA
 *   `.NET OpenFileDialog / SaveFileDialog` (`AutoUpgradeEnabled = $true`) with real categorized
 *   file-type dropdowns, UTF-8/UTF-16LE clean encoding, and `ComposeWindow` HWND ownership.
 * - Wine on Linux: Automatically bridges to Linux XDG Portal / GTK4 `zenity` when available so
 *   users never see Wine's archaic 1998 `comdlg32` dialog.
 * - macOS: Native Cocoa `NSOpenPanel` / `NSSavePanel` via `osascript` with `of type` filters.
 * - Linux: Modern XDG Desktop Portal / GTK `zenity` or KDE `kdialog` with categorized filters.
 */
object ModernNativeFileDialogs {

    suspend fun showNativeFileDialog(
        ownerFrame: Frame?,
        windowHandle: Long?,
        mode: DesktopFilePickerMode,
        title: String,
        initialDirectory: File,
        suggestedFileName: String,
        filters: List<FileFilterCategory>,
        activeFilterIndex: Int,
        maxSelectionCount: Int = 100
    ): List<File> = withContext(Dispatchers.IO) {
        val osName = System.getProperty("os.name", "").lowercase()
        val startDir = initialDirectory.takeIf { it.exists() && it.isDirectory }
            ?: initialDirectory.parentFile?.takeIf { it.exists() && it.isDirectory }
            ?: File(System.getProperty("user.home", "."))

        val orderedFilters = reorderFiltersWithActiveFirst(filters, activeFilterIndex)

        when {
            osName.contains("win") -> {
                if (isRunningUnderWine()) {
                    showWineLinuxBridgeDialog(mode, title, startDir, suggestedFileName, orderedFilters)
                        ?.let { return@withContext it.take(maxSelectionCount.coerceAtLeast(1)) }
                }
                showWindowsModernCommonItemDialog(
                    windowHandle = windowHandle,
                    mode = mode,
                    title = title,
                    startDir = startDir,
                    suggestedFileName = suggestedFileName,
                    filters = orderedFilters
                )?.let { return@withContext it.take(maxSelectionCount.coerceAtLeast(1)) }
            }

            osName.contains("mac") || osName.contains("darwin") -> {
                showMacCocoaPanel(
                    mode = mode,
                    title = title,
                    startDir = startDir,
                    suggestedFileName = suggestedFileName,
                    activeFilter = orderedFilters.firstOrNull()
                )?.let { return@withContext it.take(maxSelectionCount.coerceAtLeast(1)) }
            }

            else -> {
                showLinuxPortalDialog(
                    mode = mode,
                    title = title,
                    startDir = startDir,
                    suggestedFileName = suggestedFileName,
                    filters = orderedFilters
                )?.let { return@withContext it.take(maxSelectionCount.coerceAtLeast(1)) }
            }
        }

        // Clean parented system-LAF fallback (owned by PhonAcq's ComposeWindow with custom icon)
        showParentedFallbackDialog(
            ownerFrame = ownerFrame,
            mode = mode,
            title = title,
            startDir = startDir,
            suggestedFileName = suggestedFileName,
            filters = orderedFilters,
            maxSelectionCount = maxSelectionCount
        )
    }

    private fun reorderFiltersWithActiveFirst(
        filters: List<FileFilterCategory>,
        activeIndex: Int
    ): List<FileFilterCategory> {
        if (filters.isEmpty()) {
            return listOf(FileFilterCategory("all", "所有文件 (*.*)", emptyList()))
        }
        val validIdx = activeIndex.coerceIn(0, filters.lastIndex)
        if (validIdx == 0) return filters
        val picked = filters[validIdx]
        return listOf(picked) + filters.filterIndexed { idx, _ -> idx != validIdx }
    }

    private fun isRunningUnderWine(): Boolean {
        return !System.getenv("WINEPREFIX").isNullOrBlank() ||
            !System.getenv("WINELOADERNOEXEC").isNullOrBlank() ||
            File("Z:\\usr\\bin\\zenity").exists() ||
            File("Z:\\usr\\bin\\kdialog").exists()
    }

    /**
     * Launches Windows 10/11 modern `IFileOpenDialog` / `IFileSaveDialog` via hidden STA PowerShell.
     * Uses UTF-16LE `-EncodedCommand` so arbitrary Chinese/Unicode characters in titles, filters,
     * and directory names never break escaping or codepages.
     */
    private fun showWindowsModernCommonItemDialog(
        windowHandle: Long?,
        mode: DesktopFilePickerMode,
        title: String,
        startDir: File,
        suggestedFileName: String,
        filters: List<FileFilterCategory>
    ): List<File>? {
        return try {
            val winFilterSpec = buildWindowsFilterSpec(filters)
            val defaultExt = suggestedFileName.substringAfterLast('.', "").lowercase()
            val hwndValue = windowHandle ?: 0L

            val psScript = buildString {
                appendLine("\$ErrorActionPreference = 'Stop'")
                appendLine("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8")
                appendLine("Add-Type -AssemblyName System.Windows.Forms")
                appendLine("[System.Windows.Forms.Application]::EnableVisualStyles()")
                if (hwndValue != 0L) {
                    appendLine(
                        """
                        Add-Type -TypeDefinition @"
                        using System;
                        using System.Windows.Forms;
                        public class PhonAcqWinOwner : IWin32Window {
                            private IntPtr _h;
                            public PhonAcqWinOwner(long h) { _h = new IntPtr(h); }
                            public IntPtr Handle { get { return _h; } }
                        }
"@ -ReferencedAssemblies System.Windows.Forms
                        """.trimIndent()
                    )
                }
                if (mode == DesktopFilePickerMode.SAVE_FILE) {
                    appendLine("\$dlg = New-Object System.Windows.Forms.SaveFileDialog")
                    appendLine("\$dlg.OverwritePrompt = \$true")
                    appendLine("\$dlg.AddExtension = \$true")
                    if (defaultExt.isNotEmpty()) {
                        appendLine("\$dlg.DefaultExt = '${escapePsSingleQuoted(defaultExt)}'")
                    }
                    if (suggestedFileName.isNotBlank()) {
                        appendLine("\$dlg.FileName = '${escapePsSingleQuoted(suggestedFileName)}'")
                    }
                } else {
                    appendLine("\$dlg = New-Object System.Windows.Forms.OpenFileDialog")
                    appendLine("\$dlg.CheckFileExists = \$true")
                    appendLine("\$dlg.Multiselect = ${if (mode == DesktopFilePickerMode.OPEN_MULTIPLE) "\$true" else "\$false"}")
                }
                appendLine("\$dlg.AutoUpgradeEnabled = \$true")
                appendLine("\$dlg.RestoreDirectory = \$true")
                appendLine("\$dlg.Title = '${escapePsSingleQuoted(title)}'")
                appendLine("\$dlg.InitialDirectory = '${escapePsSingleQuoted(startDir.absolutePath)}'")
                appendLine("\$dlg.Filter = '${escapePsSingleQuoted(winFilterSpec)}'")

                if (hwndValue != 0L) {
                    appendLine("\$owner = New-Object PhonAcqWinOwner($hwndValue)")
                    appendLine("\$res = \$dlg.ShowDialog(\$owner)")
                } else {
                    appendLine("\$res = \$dlg.ShowDialog()")
                }
                appendLine("if (\$res -eq [System.Windows.Forms.DialogResult]::OK) {")
                if (mode == DesktopFilePickerMode.OPEN_MULTIPLE) {
                    appendLine("  foreach (\$f in \$dlg.FileNames) { [Console]::Out.WriteLine('PATH:' + \$f) }")
                } else {
                    appendLine("  [Console]::Out.WriteLine('PATH:' + \$dlg.FileName)")
                }
                appendLine("} else {")
                appendLine("  [Console]::Out.WriteLine('CANCELLED')")
                appendLine("}")
            }

            val encoded = Base64.getEncoder().encodeToString(psScript.toByteArray(StandardCharsets.UTF_16LE))
            val proc = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-Sta",
                "-EncodedCommand",
                encoded
            ).redirectErrorStream(true).start()

            val outputLines = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readLines()
            val exitCode = proc.waitFor()
            if (exitCode != 0) return null

            if (outputLines.any { it.trim() == "CANCELLED" }) {
                return emptyList()
            }
            val selected = outputLines
                .filter { it.startsWith("PATH:") }
                .map { File(it.removePrefix("PATH:").trim()) }
                .filter { it.path.isNotBlank() }
            if (selected.isNotEmpty()) selected else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildWindowsFilterSpec(filters: List<FileFilterCategory>): String {
        val entries = mutableListOf<String>()
        for (cat in filters) {
            if (cat.extensions.isEmpty()) {
                entries.add("${cat.label.replace('|', ' ')}|*.*")
            } else {
                val pattern = cat.extensions.joinToString(";") { "*.${it.lowercase()}" }
                val cleanLabel = cat.label.replace('|', ' ')
                entries.add("$cleanLabel|$pattern")
            }
        }
        if (filters.none { it.extensions.isEmpty() }) {
            entries.add("所有文件 (*.*)|*.*")
        }
        return entries.joinToString("|")
    }

    private fun escapePsSingleQuoted(value: String): String {
        return value.replace("'", "''")
    }

    /**
     * When running Windows `.exe` under Wine on Linux, bridges to host `zenity` (GTK4 / XDG Portal)
     * via `Z:\usr\bin\zenity` so Wine users get a modern Linux file dialog instead of Wine's 1998 dialog.
     */
    private fun showWineLinuxBridgeDialog(
        mode: DesktopFilePickerMode,
        title: String,
        startDir: File,
        suggestedFileName: String,
        filters: List<FileFilterCategory>
    ): List<File>? {
        val zenityWin = File("Z:\\usr\\bin\\zenity")
        if (!zenityWin.exists()) return null
        return try {
            val unixDir = winePathToUnix(startDir.absolutePath) ?: return null
            val targetPath = if (mode == DesktopFilePickerMode.SAVE_FILE && suggestedFileName.isNotBlank()) {
                "${unixDir.trimEnd('/')}/$suggestedFileName"
            } else {
                "${unixDir.trimEnd('/')}/"
            }
            val args = buildZenityCommand(
                zenityExecutable = zenityWin.absolutePath,
                mode = mode,
                title = title,
                filenameArg = targetPath,
                filters = filters
            )
            val proc = ProcessBuilder(args).start()
            val stdout = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val code = proc.waitFor()
            if (code != 0) return emptyList()
            if (stdout.isBlank()) return emptyList()
            stdout.split('\n', '|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { unixPathToWineFile(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun winePathToUnix(winPath: String): String? {
        if (winPath.startsWith("Z:\\", ignoreCase = true) || winPath.startsWith("Z:/", ignoreCase = true)) {
            return "/" + winPath.substring(3).replace('\\', '/')
        }
        return try {
            val p = ProcessBuilder("winepath", "-u", winPath).start()
            val out = p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            if (p.waitFor() == 0 && out.isNotEmpty()) out else null
        } catch (_: Exception) {
            null
        }
    }

    private fun unixPathToWineFile(unixPath: String): File? {
        return try {
            val p = ProcessBuilder("winepath", "-w", unixPath).start()
            val out = p.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            if (p.waitFor() == 0 && out.isNotEmpty()) {
                File(out)
            } else {
                File("Z:" + unixPath.replace('/', '\\'))
            }
        } catch (_: Exception) {
            File("Z:" + unixPath.replace('/', '\\'))
        }
    }

    /**
     * Launches native macOS Cocoa `NSOpenPanel` / `NSSavePanel` via `osascript` with safe `argv` passing.
     */
    private fun showMacCocoaPanel(
        mode: DesktopFilePickerMode,
        title: String,
        startDir: File,
        suggestedFileName: String,
        activeFilter: FileFilterCategory?
    ): List<File>? {
        val osascriptBin = File("/usr/bin/osascript")
        if (!osascriptBin.exists()) return null
        return try {
            val exts = activeFilter?.extensions.orEmpty()
            val script = when (mode) {
                DesktopFilePickerMode.SAVE_FILE -> """
                    on run argv
                        set promptText to item 1 of argv
                        set startFolder to POSIX file (item 2 of argv) as alias
                        set defaultName to item 3 of argv
                        try
                            set chosenFile to choose file name with prompt promptText default location startFolder default name defaultName
                            return POSIX path of chosenFile
                        on error number -128
                            return "__CANCELLED__"
                        end try
                    end run
                """.trimIndent()

                DesktopFilePickerMode.OPEN_MULTIPLE -> """
                    on run argv
                        set promptText to item 1 of argv
                        set startFolder to POSIX file (item 2 of argv) as alias
                        set extCsv to item 3 of argv
                        try
                            if extCsv is "" then
                                set chosenList to choose file with prompt promptText default location startFolder with multiple selections allowed
                            else
                                set AppleScript's text item delimiters to ","
                                set typeList to text items of extCsv
                                set chosenList to choose file with prompt promptText default location startFolder of type typeList with multiple selections allowed
                            end if
                            set outLines to ""
                            repeat with f in chosenList
                                set outLines to outLines & (POSIX path of f) & linefeed
                            end repeat
                            return outLines
                        on error number -128
                            return "__CANCELLED__"
                        end try
                    end run
                """.trimIndent()

                DesktopFilePickerMode.OPEN_SINGLE -> """
                    on run argv
                        set promptText to item 1 of argv
                        set startFolder to POSIX file (item 2 of argv) as alias
                        set extCsv to item 3 of argv
                        try
                            if extCsv is "" then
                                set chosenFile to choose file with prompt promptText default location startFolder
                            else
                                set AppleScript's text item delimiters to ","
                                set typeList to text items of extCsv
                                set chosenFile to choose file with prompt promptText default location startFolder of type typeList
                            end if
                            return POSIX path of chosenFile
                        on error number -128
                            return "__CANCELLED__"
                        end try
                    end run
                """.trimIndent()
            }

            val thirdArg = if (mode == DesktopFilePickerMode.SAVE_FILE) {
                suggestedFileName.ifBlank { "phonacq_export.zip" }
            } else {
                exts.joinToString(",")
            }

            val proc = ProcessBuilder(
                osascriptBin.absolutePath,
                "-e",
                script,
                title,
                startDir.absolutePath,
                thirdArg
            ).start()

            val stdout = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val code = proc.waitFor()
            if (code != 0) return null
            if (stdout == "__CANCELLED__" || stdout.isBlank()) return emptyList()
            stdout.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { File(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Launches modern Linux XDG Desktop Portal / GTK (`zenity`) or KDE Plasma (`kdialog`) dialogs.
     */
    private fun showLinuxPortalDialog(
        mode: DesktopFilePickerMode,
        title: String,
        startDir: File,
        suggestedFileName: String,
        filters: List<FileFilterCategory>
    ): List<File>? {
        val zenityPaths = listOf("/usr/bin/zenity", "/bin/zenity", "/usr/local/bin/zenity")
        val zenityBin = zenityPaths.firstOrNull { File(it).canExecute() }
        if (zenityBin != null) {
            try {
                val targetPath = if (mode == DesktopFilePickerMode.SAVE_FILE && suggestedFileName.isNotBlank()) {
                    File(startDir, suggestedFileName).absolutePath
                } else {
                    startDir.absolutePath.trimEnd(File.separatorChar) + File.separator
                }
                val cmd = buildZenityCommand(zenityBin, mode, title, targetPath, filters)
                val proc = ProcessBuilder(cmd).start()
                val stdout = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
                val exit = proc.waitFor()
                return if (exit == 0 && stdout.isNotBlank()) {
                    stdout.split('\n')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { File(it) }
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                // Fall through to kdialog or Swing system LAF
            }
        }

        val kdialogBin = listOf("/usr/bin/kdialog", "/bin/kdialog").firstOrNull { File(it).canExecute() }
        if (kdialogBin != null) {
            try {
                val cmd = mutableListOf(kdialogBin, "--title", title)
                val activeCat = filters.firstOrNull()
                val kFilter = if (activeCat != null && activeCat.extensions.isNotEmpty()) {
                    "${activeCat.label} (${activeCat.extensions.joinToString(" ") { "*.$it" }})"
                } else {
                    "*.*"
                }
                when (mode) {
                    DesktopFilePickerMode.SAVE_FILE -> {
                        cmd += listOf("--getsavefilename", File(startDir, suggestedFileName).absolutePath, kFilter)
                    }
                    DesktopFilePickerMode.OPEN_MULTIPLE -> {
                        cmd += listOf("--getopenfilename", startDir.absolutePath, kFilter, "--multiple", "--separate-output")
                    }
                    DesktopFilePickerMode.OPEN_SINGLE -> {
                        cmd += listOf("--getopenfilename", startDir.absolutePath, kFilter)
                    }
                }
                val proc = ProcessBuilder(cmd).start()
                val stdout = proc.inputStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
                val exit = proc.waitFor()
                return if (exit == 0 && stdout.isNotBlank()) {
                    stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { File(it) }
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun buildZenityCommand(
        zenityExecutable: String,
        mode: DesktopFilePickerMode,
        title: String,
        filenameArg: String,
        filters: List<FileFilterCategory>
    ): List<String> {
        val cmd = mutableListOf(
            zenityExecutable,
            "--file-selection",
            "--modal",
            "--title=$title",
            "--filename=$filenameArg"
        )
        when (mode) {
            DesktopFilePickerMode.SAVE_FILE -> {
                cmd += "--save"
                cmd += "--confirm-overwrite"
            }
            DesktopFilePickerMode.OPEN_MULTIPLE -> {
                cmd += "--multiple"
                cmd += "--separator=\n"
            }
            DesktopFilePickerMode.OPEN_SINGLE -> Unit
        }
        for (cat in filters) {
            if (cat.extensions.isEmpty()) {
                cmd += "--file-filter=${cat.label} | *"
            } else {
                val patterns = cat.extensions.flatMap { ext ->
                    listOf("*.${ext.lowercase()}", "*.${ext.uppercase()}")
                }.distinct().joinToString(" ")
                cmd += "--file-filter=${cat.label} | $patterns"
            }
        }
        if (filters.none { it.extensions.isEmpty() }) {
            cmd += "--file-filter=所有文件 (*.*) | *"
        }
        return cmd
    }

    private fun showParentedFallbackDialog(
        ownerFrame: Frame?,
        mode: DesktopFilePickerMode,
        title: String,
        startDir: File,
        suggestedFileName: String,
        filters: List<FileFilterCategory>,
        maxSelectionCount: Int
    ): List<File> {
        val result = ArrayList<File>()
        try {
            javax.swing.SwingUtilities.invokeAndWait {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                } catch (_: Exception) {
                }

                val osName = System.getProperty("os.name", "").lowercase()
                if (osName.contains("mac") || osName.contains("darwin")) {
                    val dialogMode = if (mode == DesktopFilePickerMode.SAVE_FILE) FileDialog.SAVE else FileDialog.LOAD
                    val dialog = FileDialog(ownerFrame, title, dialogMode)
                    dialog.directory = startDir.absolutePath
                    if (mode == DesktopFilePickerMode.SAVE_FILE && suggestedFileName.isNotBlank()) {
                        dialog.file = suggestedFileName
                    }
                    dialog.isMultipleMode = (mode == DesktopFilePickerMode.OPEN_MULTIPLE)
                    val activeCat = filters.firstOrNull()
                    if (activeCat != null && activeCat.extensions.isNotEmpty()) {
                        dialog.filenameFilter = FilenameFilter { _, name ->
                            val lower = name.lowercase()
                            activeCat.extensions.any { lower.endsWith(".$it") }
                        }
                    }
                    dialog.isVisible = true
                    if (mode == DesktopFilePickerMode.OPEN_MULTIPLE) {
                        result.addAll(dialog.files?.toList().orEmpty().take(maxSelectionCount))
                    } else if (dialog.directory != null && dialog.file != null) {
                        result.add(File(dialog.directory, dialog.file))
                    }
                } else {
                    val chooser = JFileChooser(startDir).apply {
                        dialogTitle = title
                        isAcceptAllFileFilterUsed = true
                        isMultiSelectionEnabled = (mode == DesktopFilePickerMode.OPEN_MULTIPLE)
                        var firstExtFilter: FileNameExtensionFilter? = null
                        for (cat in filters) {
                            if (cat.extensions.isNotEmpty()) {
                                val f = FileNameExtensionFilter(cat.label, *cat.extensions.toTypedArray())
                                addChoosableFileFilter(f)
                                if (firstExtFilter == null) firstExtFilter = f
                            }
                        }
                        if (firstExtFilter != null) {
                            fileFilter = firstExtFilter
                        }
                        if (mode == DesktopFilePickerMode.SAVE_FILE && suggestedFileName.isNotBlank()) {
                            selectedFile = File(startDir, suggestedFileName)
                        }
                    }
                    val status = if (mode == DesktopFilePickerMode.SAVE_FILE) {
                        chooser.showSaveDialog(ownerFrame)
                    } else {
                        chooser.showOpenDialog(ownerFrame)
                    }
                    if (status == JFileChooser.APPROVE_OPTION) {
                        if (mode == DesktopFilePickerMode.OPEN_MULTIPLE) {
                            result.addAll(chooser.selectedFiles?.toList().orEmpty().take(maxSelectionCount))
                        } else {
                            chooser.selectedFile?.let { result.add(it) }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }
}
