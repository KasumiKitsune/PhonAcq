package android.content

import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import com.example.i18n.S
import com.example.i18n.withArgs
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
        const val AUDIO_SERVICE = "audio"
        const val RECEIVER_NOT_EXPORTED = 4

        val appDataRootDir: File by lazy {
            val osName = System.getProperty("os.name", "").lowercase()
            val userHome = System.getProperty("user.home", ".")
            val base = when {
                osName.contains("win") -> {
                    System.getenv("APPDATA")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(it) }
                        ?: File(userHome, "AppData/Roaming")
                }
                osName.contains("mac") || osName.contains("darwin") -> {
                    File(userHome, "Library/Application Support")
                }
                else -> {
                    File(userHome, ".phonacq")
                }
            }
            File(base, "PhonAcq").apply { mkdirs() }
        }
    }

    open val applicationContext: Context
        get() = this

    open val packageName: String
        get() = "io.github.kasumikitsune.phonacq.desktop"

    open val cacheDir: File
        get() = File(appDataRootDir, "cache").apply { mkdirs() }

    open val filesDir: File
        get() = File(appDataRootDir, "files").apply { mkdirs() }

    open val contentResolver: ContentResolver = ContentResolver()

    private val prefsCache = ConcurrentHashMap<String, SharedPreferences>()

    open fun getExternalFilesDir(type: String?): File? {
        val base = File(appDataRootDir, "files").apply { mkdirs() }
        return if (type.isNullOrEmpty()) base else File(base, type).apply { mkdirs() }
    }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return prefsCache.getOrPut(name) {
            val prefsDir = File(appDataRootDir, "prefs").apply { mkdirs() }
            DesktopSharedPreferences(File(prefsDir, "$name.properties"))
        }
    }

    open fun getSystemService(name: String): Any? {
        return when (name) {
            CLIPBOARD_SERVICE -> ClipboardManager.instance
            AUDIO_SERVICE -> AudioManager.instance
            else -> null
        }
    }

    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? = null

    open fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = null

    open fun unregisterReceiver(receiver: BroadcastReceiver?) {}

    open fun startActivity(intent: Intent) {
        val targetUri = intent.getParcelableExtraUri(Intent.EXTRA_STREAM)
        val file = targetUri?.toFile()
        if (file != null && file.exists()) {
            try {
                val osName = System.getProperty("os.name", "").lowercase()
                if (osName.contains("win")) {
                    // On Windows, open File Explorer and highlight the exported .teproj / .zip / .TextGrid file
                    ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                    Toast.makeText(this, S.desktop.bridgeLocatedInExplorerToast.withArgs("file" to file.name), Toast.LENGTH_LONG).show()
                } else if (osName.contains("mac") || osName.contains("darwin")) {
                    // On macOS, open Finder and highlight the exported file
                    ProcessBuilder("open", "-R", file.absolutePath).start()
                    Toast.makeText(this, S.desktop.bridgeLocatedInExplorerToast.withArgs("file" to file.name), Toast.LENGTH_LONG).show()
                } else if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file.parentFile ?: file)
                    Toast.makeText(this, S.desktop.bridgeExportedToDirToast.withArgs("path" to file.absolutePath), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, S.desktop.bridgeSavedToFileToast.withArgs("path" to file.absolutePath), Toast.LENGTH_LONG).show()
            }
        }
    }
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context?, intent: Intent?)
}

class IntentFilter(vararg actions: String) {
    private val actionSet = actions.toMutableSet()
    fun addAction(action: String) {
        actionSet.add(action)
    }
    fun hasAction(action: String): Boolean = action in actionSet
}

class ContentResolver {
    fun openInputStream(uri: Uri): InputStream? {
        val file = uri.toFile()
        return if (file != null && file.exists()) FileInputStream(file) else null
    }

    fun openOutputStream(uri: Uri): OutputStream? {
        val file = uri.toFile() ?: return null
        file.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): android.database.Cursor? {
        val file = uri.toFile() ?: return null
        return object : android.database.Cursor {
            private var positioned = false
            override fun moveToFirst(): Boolean {
                positioned = true
                return true
            }
            override fun getString(columnIndex: Int): String? = if (positioned) file.name else null
            override fun getColumnIndex(columnName: String): Int = 0
            override fun close() {}
        }
    }
}

class Intent(val action: String = "") {
    companion object {
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1

        fun createChooser(target: Intent, title: CharSequence?): Intent = target
    }

    var type: String? = null
    private val extras = HashMap<String, Any?>()

    fun putExtra(name: String, value: Any?): Intent {
        extras[name] = value
        return this
    }

    fun addFlags(flags: Int): Intent = this

    fun getParcelableExtraUri(name: String): Uri? = extras[name] as? Uri
}

class ClipData private constructor(private val items: List<Item>) {
    class Item(private val text: CharSequence?) {
        fun coerceToText(context: Context? = null): CharSequence = text ?: ""
    }

    fun getItemAt(index: Int): Item? = items.getOrNull(index)

    val itemCount: Int
        get() = items.size

    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData {
            return ClipData(listOf(Item(text)))
        }
    }
}

class ClipboardManager {
    companion object {
        val instance = ClipboardManager()
    }

    val primaryClip: ClipData?
        get() {
            return try {
                val sysClip = Toolkit.getDefaultToolkit().systemClipboard
                if (sysClip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    val str = sysClip.getData(DataFlavor.stringFlavor) as? String
                    if (str != null) ClipData.newPlainText("clipboard", str) else null
                } else null
            } catch (_: Exception) {
                null
            }
        }

    fun setPrimaryClip(clip: ClipData) {
        try {
            val text = clip.getItemAt(0)?.coerceToText()?.toString() ?: ""
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (_: Exception) {}
    }
}

interface SharedPreferences {
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }

    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
}

private class DesktopSharedPreferences(private val file: File) : SharedPreferences {
    private val props = Properties()

    init {
        synchronized(this) {
            if (file.exists()) {
                try {
                    FileInputStream(file).use { props.load(it) }
                } catch (_: Exception) {}
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = synchronized(this) {
        props.getProperty(key) ?: defValue
    }

    override fun getInt(key: String, defValue: Int): Int = synchronized(this) {
        props.getProperty(key)?.toIntOrNull() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long = synchronized(this) {
        props.getProperty(key)?.toLongOrNull() ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float = synchronized(this) {
        props.getProperty(key)?.toFloatOrNull() ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(this) {
        props.getProperty(key)?.toBooleanStrictOrNull() ?: defValue
    }

    override fun contains(key: String): Boolean = synchronized(this) {
        props.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val staged = HashMap<String, String?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            staged[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            staged[key] = value.toString()
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            staged[key] = value.toString()
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            staged[key] = value.toString()
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            staged[key] = value.toString()
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            staged[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean = synchronized(this@DesktopSharedPreferences) {
            try {
                if (clearRequested) {
                    props.clear()
                }
                for ((k, v) in staged) {
                    if (v == null) props.remove(k) else props.setProperty(k, v)
                }
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { props.store(it, "PhonAcq Desktop Preferences") }
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
