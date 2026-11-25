@file:Suppress("PackageDirectoryMismatch")

package android.os

object Build {
    val MANUFACTURER: String = "Desktop"
    val MODEL: String = System.getProperty("os.name")?.takeIf { it.isNotBlank() } ?: "Windows PC"

    object VERSION {
        const val SDK_INT: Int = 34
    }

    object VERSION_CODES {
        const val M: Int = 23
        const val N: Int = 24
        const val O: Int = 26
        const val P: Int = 28
        const val Q: Int = 29
        const val R: Int = 30
        const val S: Int = 31
        const val TIRAMISU: Int = 33
        const val UPSIDE_DOWN_CAKE: Int = 34
    }
}

class Bundle

class Looper {
    companion object {
        fun getMainLooper(): Looper = Looper()
    }
}

open class Handler(val looper: Looper? = null)

