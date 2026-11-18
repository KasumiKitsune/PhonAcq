@file:Suppress("PackageDirectoryMismatch")

package androidx.activity.compose

import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.i18n.S
import com.example.ui.filepicker.DesktopFilePickerController
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

class ManagedActivityResultLauncher<I, O>(
    private val onLaunch: (I) -> Unit
) {
    fun launch(input: I) {
        onLaunch(input)
    }
}

/**
 * Maps Android's `rememberLauncherForActivityResult` on Desktop to PhonAcq's built-in
 * Material 3 Studio File Picker (with instant 1-click handoff to the latest native OS
 * Common Item Dialog / Cocoa NSOpenPanel / XDG Portal dialog).
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
                    DesktopFilePickerController.launchSaveFile(
                        title = S.desktop.dialogSaveLocationTitle,
                        suggestedFileName = suggestedName,
                        mimeType = contract.mimeType
                    ) { file ->
                        val uri = file?.let { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.GetContent,
                is ActivityResultContracts.OpenDocument -> {
                    val wantImageOnly = (input as? String)?.startsWith("image/") == true
                    val dialogTitle = if (wantImageOnly) {
                        S.desktop.dialogPickImageTitle
                    } else {
                        S.desktop.dialogPickFileTitle
                    }
                    DesktopFilePickerController.launchOpenSingle(
                        title = dialogTitle,
                        wantImageOnly = wantImageOnly
                    ) { file ->
                        val uri = file?.let { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.OpenMultipleDocuments -> {
                    DesktopFilePickerController.launchOpenMultiple(
                        title = "选择待导入文件（可多选字表、录音、配图或项目包）",
                        wantImageOnly = false,
                        maxItems = 2500
                    ) { files ->
                        val uris = files.map { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uris as O)
                    }
                }

                is ActivityResultContracts.OpenDocumentTree -> {
                    DesktopFilePickerController.launchOpenDirectory(
                        title = "选择包含字表或录音的文件夹（自动递归扫描）"
                    ) { dir ->
                        val uri = dir?.let { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.PickVisualMedia -> {
                    DesktopFilePickerController.launchOpenSingle(
                        title = S.desktop.dialogPickWordImageTitle,
                        wantImageOnly = true
                    ) { file ->
                        val uri = file?.let { Uri.fromFile(it) }
                        @Suppress("UNCHECKED_CAST")
                        currentOnResult.value(uri as O)
                    }
                }

                is ActivityResultContracts.PickMultipleVisualMedia -> {
                    DesktopFilePickerController.launchOpenMultiple(
                        title = S.desktop.dialogBatchPickImagesTitle,
                        wantImageOnly = true,
                        maxItems = contract.maxItems.coerceAtLeast(1)
                    ) { files ->
                        val uris = files
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
