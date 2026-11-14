@file:Suppress("PackageDirectoryMismatch")

package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest

class ActivityResultContracts {
    open class Contract<I, O>

    class CreateDocument(val mimeType: String = "*/*") : Contract<String, Uri?>()
    class GetContent : Contract<String, Uri?>()
    class OpenDocument : Contract<Array<String>, Uri?>()
    class RequestPermission : Contract<String, Boolean>()

    class PickVisualMedia : Contract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
    }

    class PickMultipleVisualMedia(val maxItems: Int = 100) : Contract<PickVisualMediaRequest, List<Uri>>()

    class TakePicture : Contract<Uri, Boolean>()
}
