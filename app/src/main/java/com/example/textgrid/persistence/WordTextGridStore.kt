package com.example.textgrid.persistence

import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.model.TextGridParseResult
import java.io.File

/**
 * Lightweight read-only file helper for external `.TextGrid` / `.Collection` inspection
 * and same-directory audio pairing (`.wav` / `.flac` / `.m4a` / `.mp3` / `.ogg`).
 * Pure JVM I/O with zero Android dependencies.
 */
object WordTextGridStore {

    private val AUDIO_EXTENSIONS_PRIORITY = listOf("wav", "flac", "m4a", "mp3", "ogg")

    /**
     * Parses an external `.TextGrid`, `.TextGrid.gz`, or `.Collection` file in read-only mode.
     */
    fun parseExternalFile(file: File): TextGridParseResult {
        return TextGridCodec.parseFile(file)
    }

    /**
     * Given an external `.TextGrid` (or `.TextGrid.gz` / `.Collection`) file, probes its parent
     * directory case-insensitively for a matching audio file with the same stem (`.wav` preferred).
     */
    fun findPairedAudioForTextGrid(textGridFile: File): File? {
        val parent = textGridFile.parentFile ?: return null
        if (!parent.isDirectory) return null
        val rawName = textGridFile.name
        val baseWithoutGz = if (rawName.endsWith(".gz", ignoreCase = true)) {
            rawName.dropLast(3)
        } else {
            rawName
        }
        val stem = baseWithoutGz.substringBeforeLast('.', baseWithoutGz)
        if (stem.isEmpty()) return null

        val directWav = File(parent, "$stem.wav")
        if (directWav.isFile) return directWav

        val siblings = parent.listFiles() ?: return null
        for (ext in AUDIO_EXTENSIONS_PRIORITY) {
            val match = siblings.firstOrNull { candidate ->
                candidate.isFile &&
                    candidate.extension.equals(ext, ignoreCase = true) &&
                    candidate.nameWithoutExtension.equals(stem, ignoreCase = true)
            }
            if (match != null) return match
        }
        return null
    }
}
