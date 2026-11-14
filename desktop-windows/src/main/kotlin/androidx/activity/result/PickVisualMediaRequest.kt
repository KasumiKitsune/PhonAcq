@file:Suppress("PackageDirectoryMismatch")

package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContracts

/**
 * Desktop bridge for Android's zero-permission Photo Picker request (`PickVisualMediaRequest`).
 */
class PickVisualMediaRequest(
    val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType =
        ActivityResultContracts.PickVisualMedia.ImageOnly
)
