package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

internal object PhonAcqMaterialIconCache {
    private val cache = ConcurrentHashMap<String, ImageVector>()

    fun getOrCreate(name: String, autoMirror: Boolean, spec: String): ImageVector {
        return cache.getOrPut(name) {
            val builder = ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = autoMirror
            )
            val fillBrush = SolidColor(Color.Black)
            for (part in spec.split("||")) {
                val segs = part.split("@", limit = 3)
                val fillType = if (segs[0] == "EO") PathFillType.EvenOdd else PathFillType.NonZero
                val fillAlpha = segs[1].toFloatOrNull() ?: 1f
                val nodes = PathParser().parsePathString(segs[2]).toNodes()
                builder.addPath(
                    pathData = nodes,
                    pathFillType = fillType,
                    name = "",
                    fill = fillBrush,
                    fillAlpha = fillAlpha,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f
                )
            }
            builder.build()
        }
    }
}

private fun autoMirroredIcon(name: String, spec: String): ImageVector =
    PhonAcqMaterialIconCache.getOrCreate("AutoMirrored.Filled.$name", autoMirror = true, spec = spec)

val Icons.AutoMirrored.Filled.CompareArrows: ImageVector
    get() = autoMirroredIcon(
        "CompareArrows",
        "NZ@1@M9.01,14 H2 v2 h7.01 v3 L13,15 l-3.99,-4 V14 Z M14.99,13 v-3 H22 V8 h-7.01 V5 L11,9 L14.99,13 Z"
    )

val Icons.AutoMirrored.Filled.HelpOutline: ImageVector
    get() = autoMirroredIcon(
        "HelpOutline",
        "NZ@1@M11,18 h2 v-2 h-2 v2 Z M12,2 C6.48,2,2,6.48,2,12 s4.48,10,10,10 s10,-4.48,10,-10 S17.52,2,12,2 Z M12,20 c-4.41,0,-8,-3.59,-8,-8 s3.59,-8,8,-8 s8,3.59,8,8 s-3.59,8,-8,8 Z M12,6 c-2.21,0,-4,1.79,-4,4 h2 c0,-1.1,0.9,-2,2,-2 s2,0.9,2,2 c0,2,-3,1.75,-3,5 h2 c0,-2.25,3,-2.5,3,-5 c0,-2.21,-1.79,-4,-4,-4 Z"
    )

val Icons.AutoMirrored.Filled.Input: ImageVector
    get() = autoMirroredIcon(
        "Input",
        "NZ@1@M21,3.01 H3 c-1.1,0,-2,0.9,-2,2 V9 h2 V4.99 h18 v14.03 H3 V15 H1 v4.01 c0,1.1,0.9,1.98,2,1.98 h18 c1.1,0,2,-0.88,2,-1.98 v-14 c0,-1.11,-0.9,-2,-2,-2 Z M11,16 l4,-4 l-4,-4 v3 H1 v2 h10 v3 Z"
    )

val Icons.AutoMirrored.Filled.InsertDriveFile: ImageVector
    get() = autoMirroredIcon(
        "InsertDriveFile",
        "NZ@1@M6,2 c-1.1,0,-1.99,0.9,-1.99,2 L4,20 c0,1.1,0.89,2,1.99,2 L18,22 c1.1,0,2,-0.9,2,-2 L20,8 l-6,-6 L6,2 Z M13,9 L13,3.5 L18.5,9 L13,9 Z"
    )

val Icons.AutoMirrored.Filled.LibraryBooks: ImageVector
    get() = autoMirroredIcon(
        "LibraryBooks",
        "NZ@1@M4,6 L2,6 v14 c0,1.1,0.9,2,2,2 h14 v-2 L4,20 L4,6 Z M20,2 L8,2 c-1.1,0,-2,0.9,-2,2 v12 c0,1.1,0.9,2,2,2 h12 c1.1,0,2,-0.9,2,-2 L22,4 c0,-1.1,-0.9,-2,-2,-2 Z M19,11 L9,11 L9,9 h10 v2 Z M15,15 L9,15 v-2 h6 v2 Z M19,7 L9,7 L9,5 h10 v2 Z"
    )

val Icons.AutoMirrored.Filled.MenuBook: ImageVector
    get() = autoMirroredIcon(
        "MenuBook",
        "NZ@1@M21,5 c-1.11,-0.35,-2.33,-0.5,-3.5,-0.5 c-1.95,0,-4.05,0.4,-5.5,1.5 c-1.45,-1.1,-3.55,-1.5,-5.5,-1.5 S2.45,4.9,1,6 v14.65 c0,0.25,0.25,0.5,0.5,0.5 c0.1,0,0.15,-0.05,0.25,-0.05 C3.1,20.45,5.05,20,6.5,20 c1.95,0,4.05,0.4,5.5,1.5 c1.35,-0.85,3.8,-1.5,5.5,-1.5 c1.65,0,3.35,0.3,4.75,1.05 c0.1,0.05,0.15,0.05,0.25,0.05 c0.25,0,0.5,-0.25,0.5,-0.5 V6 C22.4,5.55,21.75,5.25,21,5 Z M21,18.5 c-1.1,-0.35,-2.3,-0.5,-3.5,-0.5 c-1.7,0,-4.15,0.65,-5.5,1.5 V8 c1.35,-0.85,3.8,-1.5,5.5,-1.5 c1.2,0,2.4,0.15,3.5,0.5 V18.5 Z||NZ@1@M17.5,10.5 c0.88,0,1.73,0.09,2.5,0.26 V9.24 C19.21,9.09,18.36,9,17.5,9 c-1.7,0,-3.24,0.29,-4.5,0.83 v1.66 C14.13,10.85,15.7,10.5,17.5,10.5 Z||NZ@1@M13,12.49 v1.66 c1.13,-0.64,2.7,-0.99,4.5,-0.99 c0.88,0,1.73,0.09,2.5,0.26 V11.9 c-0.79,-0.15,-1.64,-0.24,-2.5,-0.24 C15.8,11.66,14.26,11.96,13,12.49 Z||NZ@1@M17.5,14.33 c-1.7,0,-3.24,0.29,-4.5,0.83 v1.66 c1.13,-0.64,2.7,-0.99,4.5,-0.99 c0.88,0,1.73,0.09,2.5,0.26 v-1.52 C19.21,14.41,18.36,14.33,17.5,14.33 Z"
    )

val Icons.AutoMirrored.Filled.MenuOpen: ImageVector
    get() = autoMirroredIcon(
        "MenuOpen",
        "NZ@1@M3,18 h13 v-2 L3,16 v2 Z M3,13 h10 v-2 L3,11 v2 Z M3,6 v2 h13 L16,6 L3,6 Z M21,15.59 L17.42,12 L21,8.41 L19.59,7 l-5,5 l5,5 L21,15.59 Z"
    )

val Icons.AutoMirrored.Filled.OpenInNew: ImageVector
    get() = autoMirroredIcon(
        "OpenInNew",
        "NZ@1@M19,19 H5 V5 h7 V3 H5 c-1.11,0,-2,0.9,-2,2 v14 c0,1.1,0.89,2,2,2 h14 c1.1,0,2,-0.9,2,-2 v-7 h-2 v7 Z M14,3 v2 h3.59 l-9.83,9.83 l1.41,1.41 L19,6.41 V10 h2 V3 h-7 Z"
    )

val Icons.AutoMirrored.Filled.PlaylistPlay: ImageVector
    get() = autoMirroredIcon(
        "PlaylistPlay",
        "NZ@1@M3,10 h11 v2 h-11 Z||NZ@1@M3,6 h11 v2 h-11 Z||NZ@1@M3,14 h7 v2 h-7 Z||NZ@1@M16,13 l0,8 l6,-4 Z"
    )

val Icons.AutoMirrored.Filled.Segment: ImageVector
    get() = autoMirroredIcon(
        "Segment",
        "NZ@1@M9,18 h12 v-2 L9,16 v2 Z M3,6 v2 h18 L21,6 L3,6 Z M9,13 h12 v-2 L9,11 v2 Z"
    )

val Icons.AutoMirrored.Filled.Sort: ImageVector
    get() = autoMirroredIcon(
        "Sort",
        "NZ@1@M3,18 h6 v-2 L3,16 v2 Z M3,6 v2 h18 L21,6 L3,6 Z M3,13 h12 v-2 L3,11 v2 Z"
    )

val Icons.AutoMirrored.Filled.ViewList: ImageVector
    get() = autoMirroredIcon(
        "ViewList",
        "NZ@1@M3,14 h4 v-4 H3 V14 Z M3,19 h4 v-4 H3 V19 Z M3,9 h4 V5 H3 V9 Z M8,14 h13 v-4 H8 V14 Z M8,19 h13 v-4 H8 V19 Z M8,5 v4 h13 V5 H8 Z"
    )

val Icons.AutoMirrored.Filled.VolumeUp: ImageVector
    get() = autoMirroredIcon(
        "VolumeUp",
        "NZ@1@M3,9 v6 h4 l5,5 L12,4 L7,9 L3,9 Z M16.5,12 c0,-1.77,-1.02,-3.29,-2.5,-4.03 v8.05 c1.48,-0.73,2.5,-2.25,2.5,-4.02 Z M14,3.23 v2.06 c2.89,0.86,5,3.54,5,6.71 s-2.11,5.85,-5,6.71 v2.06 c4.01,-0.91,7,-4.49,7,-8.77 s-2.99,-7.86,-7,-8.77 Z"
    )
