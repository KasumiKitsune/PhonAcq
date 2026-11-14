@file:Suppress("PackageDirectoryMismatch")

package android.view

class KeyEvent(val keyCode: Int = 0, val repeatCount: Int = 0) {
    companion object {
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25
    }
}

class View {
    val isInEditMode: Boolean = true
    val context: Any? = null
    var keepScreenOn: Boolean = false
}
