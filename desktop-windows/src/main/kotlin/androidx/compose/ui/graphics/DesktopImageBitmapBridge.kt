@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.graphics

import android.graphics.Bitmap
import java.awt.image.BufferedImage

/**
 * Converts the Desktop `android.graphics.Bitmap` bridge directly into a Compose `ImageBitmap`
 * so `Bitmap.createBitmap(...).asImageBitmap()` compiles and renders identically on Windows.
 */
fun Bitmap.asImageBitmap(): ImageBitmap {
    val safeW = width.coerceAtLeast(1)
    val safeH = height.coerceAtLeast(1)
    val buffered = BufferedImage(safeW, safeH, BufferedImage.TYPE_INT_ARGB)
    if (pixels.size >= safeW * safeH) {
        buffered.setRGB(0, 0, safeW, safeH, pixels, 0, safeW)
    }
    return buffered.toComposeImageBitmap()
}
