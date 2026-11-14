@file:Suppress("PackageDirectoryMismatch")

package android.app

import android.content.Context

open class Application : Context() {
    companion object {
        val instance = Application()
    }
}

open class Activity : Context() {
    val window: Any? = null
}
