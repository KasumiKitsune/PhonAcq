@file:Suppress("PackageDirectoryMismatch")

package android.database

import java.io.Closeable

interface Cursor : Closeable {
    fun moveToFirst(): Boolean
    fun getString(columnIndex: Int): String?
    fun getColumnIndex(columnName: String): Int
}
