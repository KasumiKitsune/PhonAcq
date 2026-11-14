@file:Suppress("PackageDirectoryMismatch")

package android.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Toast private constructor(
    private val text: String,
    private val duration: Int
) {
    fun show() {
        DesktopToastHostState.showToast(text, duration)
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast {
            return Toast(text?.toString().orEmpty(), duration)
        }
    }
}

object DesktopToastHostState {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var dismissJob: Job? = null

    private val _currentMessage = MutableStateFlow<String?>(null)
    val currentMessage = _currentMessage.asStateFlow()

    fun showToast(message: String, duration: Int) {
        if (message.isBlank()) return
        dismissJob?.cancel()
        _currentMessage.value = message
        dismissJob = scope.launch {
            delay(if (duration == Toast.LENGTH_LONG) 3800L else 2200L)
            _currentMessage.value = null
        }
    }
}
