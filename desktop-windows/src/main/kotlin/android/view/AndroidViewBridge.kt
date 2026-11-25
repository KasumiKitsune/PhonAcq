@file:Suppress("PackageDirectoryMismatch")

package android.view

class KeyEvent(val keyCode: Int = 0, val repeatCount: Int = 0) {
    companion object {
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
    }
}

object HapticFeedbackConstants {
    const val LONG_PRESS = 0
    const val VIRTUAL_KEY = 1
    const val KEYBOARD_TAP = 3
    const val CLOCK_TICK = 4
    const val CONTEXT_CLICK = 6
    const val TEXT_HANDLE_MOVE = 9
    const val CONFIRM = 16
    const val REJECT = 17
    const val SEGMENT_TICK = 26
    const val SEGMENT_FREQUENT_TICK = 27
}

class View {
    val isInEditMode: Boolean = true
    val context: Any? = null
    var keepScreenOn: Boolean = false

    fun performHapticFeedback(feedbackConstant: Int): Boolean = false
    fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean = false
}
