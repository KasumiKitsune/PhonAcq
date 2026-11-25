@file:Suppress("PackageDirectoryMismatch")

package android.util

object Log {
    fun d(tag: String?, msg: String?): Int {
        println("[DEBUG][$tag] $msg")
        return 0
    }

    fun i(tag: String?, msg: String?): Int {
        println("[INFO][$tag] $msg")
        return 0
    }

    fun w(tag: String?, msg: String?, tr: Throwable? = null): Int {
        println("[WARN][$tag] $msg")
        tr?.printStackTrace()
        return 0
    }

    fun e(tag: String?, msg: String?, tr: Throwable? = null): Int {
        System.err.println("[ERROR][$tag] $msg")
        tr?.printStackTrace()
        return 0
    }
}

open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)
    private var size: Int = 0

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val prev = map.put(key, value)
        if (prev != null) {
            size -= safeSizeOf(key, prev)
        }
        size += safeSizeOf(key, value)
        trimToSize(maxSize)
        return prev
    }

    @Synchronized
    fun remove(key: K): V? {
        val prev = map.remove(key)
        if (prev != null) {
            size -= safeSizeOf(key, prev)
        }
        return prev
    }

    @Synchronized
    fun evictAll() {
        trimToSize(-1)
    }

    @Synchronized
    fun size(): Int = size

    @Synchronized
    fun maxSize(): Int = maxSize

    protected open fun sizeOf(key: K, value: V): Int = 1

    private fun safeSizeOf(key: K, value: V): Int = sizeOf(key, value).coerceAtLeast(1)

    private fun trimToSize(targetSize: Int) {
        val iter = map.entries.iterator()
        while (size > targetSize && iter.hasNext()) {
            val entry = iter.next()
            size -= safeSizeOf(entry.key, entry.value)
            iter.remove()
        }
    }
}
