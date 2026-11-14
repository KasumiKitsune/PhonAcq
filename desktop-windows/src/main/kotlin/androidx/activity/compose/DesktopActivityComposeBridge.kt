@file:Suppress("PackageDirectoryMismatch")

package androidx.activity.compose

import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.example.i18n.S
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

class ManagedActivityResultLauncher<I, O>(
    private val onLaunch: (I) -> Unit
) {
    fun launch(input: I) {
        onLaunch(input)
    }
}

/**
 * Maps Android's `rememberLauncherForActivityResult` directly to native Windows OS File Dialogs
 * (`java.awt.FileDialog` which invokes Windows `IFileDialog` / Explorer dialogs).
 */
@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContracts.Contract<I, O>,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O> {
    val currentOnResult = rememberUpdatedState(onResult)

    return remember(contract) {
        ManagedActivityResultLauncher { input ->
            when (contract) {
                is ActivityResultContracts.RequestPermission -> {
                    @Suppress("UNCHECKED_CAST")
                    currentOnResult.value(true as O)
                }

                is ActivityResultContracts.CreateDocument -> {
                    val suggestedName = (input as? String) ?: "phonacq_export.zip"
                    SwingUtilities.invokeLater {
                        val dialog = FileDialog(null as Frame?, S.desktop.dialogSaveLocationTitle, FileDialog.SAVE)
                        val homeDir = System.getProperty("user.home")?.let { File(it) }
                        val defaultSaveDir = listOfNotNull(
                            homeDir?.let { File(it, "Desktop") },
                            homeDir?.let { File(it, "Documents") },
                            homeDir
                        ).firstOrNull { it.exists() && it.isDirectory }
                        if (defaultSaveDir != null) {
                            dialog.directory = defaultSaveDir.absolutePath
                        }
                        dialog.file = suggestedName
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val file = dialog.file
                        val uri = if (dir != null && file != null) {
                            Uri.fromFile(File(dir, file))
                        } else null
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.GetContent,
                is ActivityResultContracts.OpenDocument -> {
                    SwingUtilities.invokeLater {
                        val wantImageOnly = (input as? String)?.startsWith("image/") == true
                        val dialogTitle = if (wantImageOnly) {
                            S.desktop.dialogPickImageTitle
                        } else {
                            S.desktop.dialogPickFileTitle
                        }
                        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD)
                        dialog.filenameFilter = FilenameFilter { _, name ->
                            val lower = name.lowercase()
                            if (wantImageOnly) {
                                lower.endsWith(".jpg") ||
                                    lower.endsWith(".jpeg") ||
                                    lower.endsWith(".png") ||
                                    lower.endsWith(".webp") ||
                                    lower.endsWith(".bmp") ||
                                    lower.endsWith(".gif")
                            } else {
                                lower.endsWith(".xlsx") ||
                                    lower.endsWith(".docx") ||
                                    lower.endsWith(".csv") ||
                                    lower.endsWith(".tsv") ||
                                    lower.endsWith(".txt") ||
                                    lower.endsWith(".json") ||
                                    lower.endsWith(".zip") ||
                                    lower.endsWith(".teproj") ||
                                    lower.endsWith(".textgrid")
                            }
                        }
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val file = dialog.file
                        val uri = if (dir != null && file != null) {
                            Uri.fromFile(File(dir, file))
                        } else null
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.PickVisualMedia -> {
                    SwingUtilities.invokeLater {
                        val dialog = FileDialog(null as Frame?, S.desktop.dialogPickWordImageTitle, FileDialog.LOAD)
                        dialog.filenameFilter = FilenameFilter { _, name ->
                            val lower = name.lowercase()
                            lower.endsWith(".jpg") ||
                                lower.endsWith(".jpeg") ||
                                lower.endsWith(".png") ||
                                lower.endsWith(".bmp") ||
                                lower.endsWith(".gif")
                        }
                        dialog.isVisible = true
                        val dir = dialog.directory
                        val file = dialog.file
                        val uri = if (dir != null && file != null) {
                            Uri.fromFile(File(dir, file))
                        } else null
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.PickMultipleVisualMedia -> {
                    SwingUtilities.invokeLater {
                        val dialog = FileDialog(null as Frame?, S.desktop.dialogBatchPickImagesTitle, FileDialog.LOAD)
                        dialog.isMultipleMode = true
                        dialog.filenameFilter = FilenameFilter { _, name ->
                            val lower = name.lowercase()
                            lower.endsWith(".jpg") ||
                                lower.endsWith(".jpeg") ||
                                lower.endsWith(".png") ||
                                lower.endsWith(".bmp") ||
                                lower.endsWith(".gif")
                        }
                        dialog.isVisible = true
                        val selectedFiles = dialog.files?.toList().orEmpty()
                        val uris = selectedFiles
                            .take(contract.maxItems.coerceAtLeast(1))
                            .map { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uris as O)
                    }
                }

                is ActivityResultContracts.TakePicture -> {
                    @Suppress("UNCHECKED_CAST")
                    currentOnResult.value(false as O)
                }
            }
        }
    }
}

object DesktopBackDispatcher {
    private val handlers = CopyOnWriteArrayList<() -> Unit>()

    fun register(handler: () -> Unit) {
        handlers.add(handler)
    }

    fun unregister(handler: () -> Unit) {
        handlers.remove(handler)
    }

    fun dispatchBack(): Boolean {
        val last = handlers.lastOrNull() ?: return false
        last.invoke()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val callback: () -> Unit = { currentOnBack.value() }
        if (enabled) {
            DesktopBackDispatcher.register(callback)
        }
        onDispose {
            DesktopBackDispatcher.unregister(callback)
        }
    }
}

@Composable
fun PredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (Flow<BackEventCompat>) -> Unit
) {
    val currentOnBack = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val callback: () -> Unit = {
            kotlinx.coroutines.MainScope().let { scope ->
                scope.launchWithEmptyFlow(currentOnBack.value)
            }
        }
        if (enabled) {
            DesktopBackDispatcher.register(callback)
        }
        onDispose {
            DesktopBackDispatcher.unregister(callback)
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchWithEmptyFlow(
    block: suspend (Flow<BackEventCompat>) -> Unit
) {
    this.launch {
        block(emptyFlow())
    }
}
