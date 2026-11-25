@file:Suppress("PackageDirectoryMismatch")

package android.net

import java.io.File

class Uri private constructor(
    private val fileRef: File?,
    private val rawUriString: String
) {
    val scheme: String?
        get() = when {
            fileRef != null -> "file"
            rawUriString.contains("://") -> rawUriString.substringBefore("://")
            else -> null
        }

    val path: String?
        get() = fileRef?.absolutePath ?: rawUriString.removePrefix("file://").takeIf { it.isNotBlank() }

    fun toFile(): File? = fileRef ?: if (rawUriString.isNotBlank()) File(rawUriString) else null

    override fun toString(): String = fileRef?.absolutePath ?: rawUriString

    companion object {
        fun fromFile(file: File): Uri = Uri(file, file.toURI().toString())
        fun parse(uriString: String): Uri {
            val cleaned = uriString.removePrefix("file://")
            return Uri(File(cleaned), uriString)
        }
    }
}
