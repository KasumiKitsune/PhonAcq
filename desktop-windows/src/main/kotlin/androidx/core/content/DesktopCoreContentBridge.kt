@file:Suppress("PackageDirectoryMismatch")

package androidx.core.content

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.io.File

object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int {
        return PackageManager.PERMISSION_GRANTED
    }
}

object FileProvider {
    fun getUriForFile(context: Context, authority: String, file: File): Uri {
        return Uri.fromFile(file)
    }
}
